package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.PriorityQueue;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPOutputStream;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * A search problem for finding clauses in a sentence.
 *
 * @author Gabor Angeli
 */
public class ClauseSearcher {

  /**
   * The tree to search over.
   */
  public final SemanticGraph tree;
  /**
   * The length of the sentence, as determined from the tree.
   */
  public final int sentenceLength;
  /**
   * A mapping from a word to the extra edges that come out of it.
   */
  private final Map<IndexedWord, Collection<SemanticGraphEdge>> extraEdgesByGovernor = new HashMap<>();
  /**
   * The classifier for whether a particular dependency edge defines a clause boundary.
   */
  private final Optional<Classifier<Boolean, String>> isClauseClassifier;
  /**
   * An optional featurizer to use with the clause classifier ({@link ClauseSearcher#isClauseClassifier}).
   * If that classifier is defined, this should be as well.
   */
  private final Optional<Function<Triple<ClauseSearcher.State, ClauseSearcher.Action, ClauseSearcher.State>, Counter<String>>> featurizer;

  /**
   * A mapping from edges in the tree, to an index.
   */
  @SuppressWarnings("Convert2Diamond")  // It's lying -- type inference times out with a diamond
  private final Index<SemanticGraphEdge> edgeToIndex = new HashIndex<SemanticGraphEdge>(ArrayList::new, IdentityHashMap::new);

  /**
   * A search state.
   */
  public class State {
    public final SemanticGraphEdge edge;
    public final int edgeIndex;
    public final SemanticGraphEdge subjectOrNull;
    public final int distanceFromSubj;
    public final SemanticGraphEdge ppOrNull;
    public final Consumer<SemanticGraph> thunk;
    public final boolean isDone;

    public State(SemanticGraphEdge edge, SemanticGraphEdge subjectOrNull, int distanceFromSubj, SemanticGraphEdge ppOrNull,
                 Consumer<SemanticGraph> thunk, boolean isDone) {
      this.edge = edge;
      this.edgeIndex = edgeToIndex.indexOf(edge);
      this.subjectOrNull = subjectOrNull;
      this.distanceFromSubj = distanceFromSubj;
      this.ppOrNull = ppOrNull;
      this.thunk = thunk;
      this.isDone = isDone;
    }

    public State(State source, boolean isDone) {
      this.edge = source.edge;
      this.edgeIndex = edgeToIndex.indexOf(edge);
      this.subjectOrNull = source.subjectOrNull;
      this.distanceFromSubj = source.distanceFromSubj;
      this.ppOrNull = source.ppOrNull;
      this.thunk = source.thunk;
      this.isDone = isDone;
    }

    public SemanticGraph originalTree() {
      return ClauseSearcher.this.tree;
    }
  }

  /**
   * An action being taken; that is, the type of clause splitting going on.
   */
  public static interface Action {
    public String signature();

    public Optional<State> applyTo(SemanticGraph tree, State source,
                                   SemanticGraphEdge outgoingEdge,
                                   SemanticGraphEdge subjectOrNull,
                                   SemanticGraphEdge ppOrNull);
  }

  /**
   * The options used for training the clause searcher.
   */
  public static class TrainingOptions {
    @Execution.Option(name = "negativeSubsampleRatio", gloss = "The percent of negative datums to take")
    public double negativeSubsampleRatio = 0.05;
    @Execution.Option(name = "positiveDatumWeight", gloss = "The weight to assign every positive datum.")
    public float positiveDatumWeight = 10.0f;
    @Execution.Option(name = "seed", gloss = "The random seed to use")
    public int seed = 42;
    @Execution.Option(name = "classifierFactory", gloss = "The class of the classifier factory to use for training the various classifiers")
    public Class<? extends ClassifierFactory<Boolean, String, Classifier<Boolean, String>>> classifierFactory = (Class<? extends ClassifierFactory<Boolean, String, Classifier<Boolean, String>>>) ((Object) LinearClassifierFactory.class);
  }

  /**
   * Mostly just an alias, but make sure our featurizer is serializable!
   */
  public static interface Featurizer extends Function<Triple<ClauseSearcher.State, ClauseSearcher.Action, ClauseSearcher.State>, Counter<String>>, Serializable { }

  /**
   * Create a searcher manually, suppling a dependency tree, an optional classifier for when to split clauses,
   * and a featurizer for that classifier.
   * You almost certainly want to use {@link edu.stanford.nlp.naturalli.ClauseSearcher#factory(java.io.File)} instead of this
   * constructor.
   *
   * @param tree               The dependency tree to search over.
   * @param isClauseClassifier The classifier for whether a given dependency arc should be a new clause. If this is not given, all arcs are treated as clause separators.
   * @param featurizer         The featurizer for the classifier. If no featurizer is given, one should be given in {@link ClauseSearcher#search(java.util.function.Predicate, edu.stanford.nlp.stats.Counter, java.util.function.Function, int)}, or else the classifier will be useless.
   * @see edu.stanford.nlp.naturalli.ClauseSearcher#factory(java.io.File)
   */
  public ClauseSearcher(SemanticGraph tree,
                        Optional<Classifier<Boolean, String>> isClauseClassifier,
                        Optional<Function<Triple<ClauseSearcher.State, ClauseSearcher.Action, ClauseSearcher.State>, Counter<String>>> featurizer
  ) {
    this.tree = new SemanticGraph(tree);
    this.isClauseClassifier = isClauseClassifier;
    this.featurizer = featurizer;
    // Index edges
    this.tree.edgeIterable().forEach(edgeToIndex::addToIndex);
    // Get length
    List<IndexedWord> sortedVertices = tree.vertexListSorted();
    sentenceLength = sortedVertices.get(sortedVertices.size() - 1).index();
    // Register extra edges
    for (IndexedWord vertex : sortedVertices) {
      extraEdgesByGovernor.put(vertex, new ArrayList<>());
    }
    List<SemanticGraphEdge> extraEdges = cleanTree(this.tree);
    assert isTree(this.tree);
    for (SemanticGraphEdge edge : extraEdges) {
      extraEdgesByGovernor.get(edge.getGovernor()).add(edge);
    }
  }

  /**
   * Create a clause searcher which searches naively through every possible subtree as a clause.
   * For an end-user, this is almost certainly not what you want.
   * However, it is very useful for training time.
   *
   * @param tree The dependency tree to search over.
   */
  protected ClauseSearcher(SemanticGraph tree) {
    this(tree, Optional.empty(), Optional.empty());
  }


  /**
   * Fix some bizarre peculiarities with certain trees.
   * So far, these include:
   * <ul>
   * <li>Sometimes there's a node from a word to itself. This seems wrong.</li>
   * </ul>
   *
   * @param tree The tree to clean (in place!).
   * @return A list of extra edges, which are valid but were removed.
   */
  private static List<SemanticGraphEdge> cleanTree(SemanticGraph tree) {
    // Clean nodes
    List<IndexedWord> toDelete = new ArrayList<>();
    for (IndexedWord vertex : tree.vertexSet()) {
      // Clean punctuation
      char tag = vertex.backingLabel().tag().charAt(0);
      if (tag == '.' || tag == ',' || tag == '(' || tag == ')' || tag == ':') {
        if (!tree.outgoingEdgeIterator(vertex).hasNext()) {  // This should really never happen, but it does.
          toDelete.add(vertex);
        }
      }
    }
    for (IndexedWord v : toDelete) {
      tree.removeVertex(v);
    }

    // Clean edges
    Iterator<SemanticGraphEdge> iter = tree.edgeIterable().iterator();
    while (iter.hasNext()) {
      SemanticGraphEdge edge = iter.next();
      if (edge.getDependent().index() == edge.getGovernor().index()) {
        // Clean self-edges
        iter.remove();
      } else if (edge.getRelation().toString().equals("punct")) {
        // Clean punctuation (again)
        if (!tree.outgoingEdgeIterator(edge.getDependent()).hasNext()) {  // This should really never happen, but it does.
          iter.remove();
        }
      }
    }

    // Remove extra edges
    List<SemanticGraphEdge> extraEdges = new ArrayList<>();
    for (SemanticGraphEdge edge : tree.edgeIterable()) {
      if (edge.isExtra()) {
        if (tree.incomingEdgeList(edge.getDependent()).size() > 1) {
          extraEdges.add(edge);
        }
      }
    }
    for (SemanticGraphEdge edge : extraEdges) {
      tree.removeEdge(edge);
    }
    // Add apposition edges (simple coref)
    for (SemanticGraphEdge extraEdge : new ArrayList<>(extraEdges)) {  // note[gabor] prevent concurrent modification exception
      for (SemanticGraphEdge candidateAppos : tree.incomingEdgeIterable(extraEdge.getDependent())) {
        if (candidateAppos.getRelation().toString().equals("appos")) {
          extraEdges.add(new SemanticGraphEdge(extraEdge.getGovernor(), candidateAppos.getGovernor(), extraEdge.getRelation(), extraEdge.getWeight(), extraEdge.isExtra()));
        }
      }
      for (SemanticGraphEdge candidateAppos : tree.outgoingEdgeIterable(extraEdge.getDependent())) {
        if (candidateAppos.getRelation().toString().equals("appos")) {
          extraEdges.add(new SemanticGraphEdge(extraEdge.getGovernor(), candidateAppos.getDependent(), extraEdge.getRelation(), extraEdge.getWeight(), extraEdge.isExtra()));
        }
      }
    }

    // Brute force ensure tree
    // Remove incoming edges from roots
    List<SemanticGraphEdge> rootIncomingEdges = new ArrayList<>();
    for (IndexedWord root : tree.getRoots()) {
      for (SemanticGraphEdge incomingEdge : tree.incomingEdgeIterable(root)) {
        rootIncomingEdges.add(incomingEdge);
      }
    }
    for (SemanticGraphEdge edge : rootIncomingEdges) {
      tree.removeEdge(edge);
    }
    // Loop until it becomes a tree.
    boolean changed = true;
    while (changed) {  // I just want trees to be trees; is that so much to ask!?
      changed = false;
      List<IndexedWord> danglingNodes = new ArrayList<>();
      List<SemanticGraphEdge> invalidEdges = new ArrayList<>();

      for (IndexedWord vertex : tree.vertexSet()) {
        // Collect statistics
        boolean hasOutgoing = tree.outgoingEdgeIterator(vertex).hasNext();
        Iterator<SemanticGraphEdge> incomingIter = tree.incomingEdgeIterator(vertex);
        boolean hasIncoming = incomingIter.hasNext();
        boolean hasMultipleIncoming = false;
        if (hasIncoming) {
          incomingIter.next();
          hasMultipleIncoming = incomingIter.hasNext();
        }

        // Register actions
        if (!hasIncoming && !tree.getRoots().contains(vertex)) {
          danglingNodes.add(vertex);
        } else {
          if (hasMultipleIncoming) {
            for (SemanticGraphEdge edge : new IterableIterator<>(incomingIter)) {
              invalidEdges.add(edge);
            }
          }
        }
      }

      // Perform actions
      for (IndexedWord vertex : danglingNodes) {
        tree.removeVertex(vertex);
        changed = true;
      }
      for (SemanticGraphEdge edge : invalidEdges) {
        tree.removeEdge(edge);
        changed = true;
      }
    }

    // Return
    assert isTree(tree);
    return extraEdges;
  }

  private static void simpleClause(SemanticGraph tree, SemanticGraphEdge toKeep) {
    Queue<IndexedWord> fringe = new LinkedList<>();
    List<IndexedWord> nodesToRemove = new ArrayList<>();
    // Find nodes to remove
    // (from the root)
    for (IndexedWord root : tree.getRoots()) {
      nodesToRemove.add(root);
      for (SemanticGraphEdge out : tree.outgoingEdgeIterable(root)) {
        if (!out.equals(toKeep)) {
          fringe.add(out.getDependent());
        }
      }
    }
    // (recursively)
    while (!fringe.isEmpty()) {
      IndexedWord node = fringe.poll();
      nodesToRemove.add(node);
      for (SemanticGraphEdge out : tree.outgoingEdgeIterable(node)) {
        if (!out.equals(toKeep)) {
          fringe.add(out.getDependent());
        }
      }
    }
    // Remove nodes
    for (IndexedWord node : nodesToRemove) {
      tree.removeVertex(node);
    }
    // Set new root
    tree.setRoot(toKeep.getDependent());
  }

  private static void addWord(SemanticGraph toModify, IndexedWord root, String rel, CoreLabel coreLabel) {
    IndexedWord dependent = new IndexedWord(coreLabel);
    toModify.addVertex(dependent);
    toModify.addEdge(root, dependent, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, rel), Double.NEGATIVE_INFINITY, false);
  }

  private static void addSubtree(SemanticGraph toModify, IndexedWord root, String rel, SemanticGraph originalTree, IndexedWord subject, Collection<SemanticGraphEdge> ignoredEdges) {
    if (toModify.containsVertex(subject)) {
      return;  // This subtree already exists.
    }
    Queue<IndexedWord> fringe = new LinkedList<>();
    Collection<IndexedWord> wordsToAdd = new ArrayList<>();
    Collection<SemanticGraphEdge> edgesToAdd = new ArrayList<>();
    // Search for subtree to add
    for (SemanticGraphEdge edge : originalTree.outgoingEdgeIterable(subject)) {
      if (!ignoredEdges.contains(edge)) {
        if (toModify.containsVertex(edge.getDependent())) {
          // Case: we're adding a subtree that's not disjoint from toModify. This is bad news.
          return;
        }
        edgesToAdd.add(edge);
        fringe.add(edge.getDependent());
      }
    }
    while (!fringe.isEmpty()) {
      IndexedWord node = fringe.poll();
      wordsToAdd.add(node);
      for (SemanticGraphEdge edge : originalTree.outgoingEdgeIterable(node)) {
        if (!ignoredEdges.contains(edge)) {
          if (toModify.containsVertex(edge.getDependent())) {
            // Case: we're adding a subtree that's not disjoint from toModify. This is bad news.
            return;
          }
          edgesToAdd.add(edge);
          fringe.add(edge.getDependent());
        }
      }
    }
    // Add subtree
    // (add subject)
    toModify.addVertex(subject);
    toModify.addEdge(root, subject, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, rel), Double.NEGATIVE_INFINITY, false);

    // (add nodes)
    for (IndexedWord node : wordsToAdd) {
      toModify.addVertex(node);
    }
    // (add edges)
    for (SemanticGraphEdge edge : edgesToAdd) {
      assert !toModify.incomingEdgeIterator(edge.getDependent()).hasNext();
      toModify.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
  }

  /**
   * A little utility function to make sure a SemanticGraph is a tree.
   */
  private static boolean isTree(SemanticGraph tree) {
    for (IndexedWord vertex : tree.vertexSet()) {
      if (tree.getRoots().contains(vertex)) {
        if (tree.incomingEdgeIterator(vertex).hasNext()) {
          return false;
        }
      } else {
        Iterator<SemanticGraphEdge> iter = tree.incomingEdgeIterator(vertex);
        if (!iter.hasNext()) {
          return false;
        }
        iter.next();
        if (iter.hasNext()) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Create a mock node, to be added to the dependency tree but which is not part of the original sentence.
   *
   * @param toCopy The CoreLabel to copy from initially.
   * @param word   The new word to add.
   * @param POS    The new part of speech to add.
   * @return
   */
  private CoreLabel mockNode(CoreLabel toCopy, String word, String POS) {
    CoreLabel mock = new CoreLabel(toCopy);
    mock.setWord(word);
    mock.setLemma(word);
    mock.setValue(word);
    mock.setNER("O");
    mock.setTag(POS);
    mock.setIndex(sentenceLength + 5);
    return mock;
  }

  /**
   * TODO(gabor) JavaDoc
   * @param thresholdProbability
   * @return
   */
  public List<SentenceFragment> topClauses(double thresholdProbability) {
    List<SentenceFragment> results = new ArrayList<>();
    search(triple -> {
      if (triple.first >= thresholdProbability) {
        results.add(triple.third.get());
        return true;
      } else {
        return false;
      }
    });
    return results;
  }

  /**
   * TODO(gabor) JavaDoc
   * @param candidateFragments
   */
  public void search(final Predicate<Triple<Double, List<Counter<String>>, Supplier<SentenceFragment>>> candidateFragments) {
    if (!isClauseClassifier.isPresent() ||
        !(isClauseClassifier.get() instanceof LinearClassifier)) {
      throw new IllegalArgumentException("For now, only linear classifiers are supported");
    }
    search(candidateFragments,
        ((LinearClassifier<Boolean,String>) isClauseClassifier.get()).weightsAsMapOfCounters().get(true),
        this.featurizer.get(),
        10000);
  }

  /**
   * TODO(gabor) JavaDoc
   *
   * @param candidateFragments
   * @param weights
   * @param featurizer
   */
  public void search(
      // The output specs
      final Predicate<Triple<Double, List<Counter<String>>, Supplier<SentenceFragment>>> candidateFragments,
      // The learning specs
      final Counter<String> weights,
      final Function<Triple<State, Action, State>, Counter<String>> featurizer,
      final int maxTicks
  ) {
    Collection<Action> actionSpace = new ArrayList<>();

    // SIMPLE SPLIT
    actionSpace.add(new Action() {
      @Override
      public String signature() {
        return "simple";
      }

      @Override
      public Optional<State> applyTo(SemanticGraph tree, State source, SemanticGraphEdge outgoingEdge, SemanticGraphEdge subjectOrNull, SemanticGraphEdge ppOrNull) {
        return Optional.of(new State(
            outgoingEdge,
            subjectOrNull == null ? source.subjectOrNull : subjectOrNull,
            subjectOrNull == null ? (source.distanceFromSubj + 1) : 0,
            ppOrNull,
            source.thunk.andThen(toModify -> {
              assert isTree(toModify);
              simpleClause(toModify, outgoingEdge);
              assert isTree(toModify);
            }), false
        ));
      }
    });

    // CLONE ROOT
    actionSpace.add(new Action() {
      @Override
      public String signature() {
        return "clone_root_as_nsubjpass";
      }

      @Override
      public Optional<State> applyTo(SemanticGraph tree, State source, SemanticGraphEdge outgoingEdge, SemanticGraphEdge subjectOrNull, SemanticGraphEdge ppOrNull) {
        return Optional.of(new State(
            outgoingEdge,
            subjectOrNull == null ? source.subjectOrNull : subjectOrNull,
            subjectOrNull == null ? (source.distanceFromSubj + 1) : 0,
            ppOrNull,
            source.thunk.andThen(toModify -> {
              assert isTree(toModify);
              simpleClause(toModify, outgoingEdge);
              addSubtree(toModify, outgoingEdge.getDependent(), "nsubjpass", tree, outgoingEdge.getGovernor(), Collections.singleton(outgoingEdge));
//              addWord(toModify, outgoingEdge.getDependent(), "auxpass", mockNode(outgoingEdge.getDependent().backingLabel(), "is", "VBZ"));
              assert isTree(toModify);
            }), true
        ));
      }
    });

    // COPY SUBJECT
    actionSpace.add(new Action() {
      @Override
      public String signature() {
        return "clone_nsubj";
      }

      @Override
      public Optional<State> applyTo(SemanticGraph tree, State source, SemanticGraphEdge outgoingEdge, SemanticGraphEdge subjectOrNull, SemanticGraphEdge ppOrNull) {
        if (subjectOrNull != null && !outgoingEdge.equals(subjectOrNull)) {
          return Optional.of(new State(
              outgoingEdge,
              subjectOrNull,
              0,
              ppOrNull,
              source.thunk.andThen(toModify -> {
                assert isTree(toModify);
                simpleClause(toModify, outgoingEdge);
                addSubtree(toModify, outgoingEdge.getDependent(), "nsubj", tree,
                    subjectOrNull.getDependent(), Collections.singleton(outgoingEdge));
                assert isTree(toModify);
              }), true
          ));
        } else {
          return Optional.empty();
        }
      }
    });

    for (IndexedWord root : tree.getRoots()) {
      search(root, candidateFragments, weights, featurizer, actionSpace, maxTicks);
    }
  }

  /**
   * TODO(gabor) JavaDoc
   *
   * @param root
   * @param candidateFragments
   * @param weights
   * @param featurizer
   * @param actionSpace
   */
  public void search(
      // The root to search from
      IndexedWord root,
      // The output specs
      final Predicate<Triple<Double, List<Counter<String>>, Supplier<SentenceFragment>>> candidateFragments,
      // The learning specs
      final Counter<String> weights,
      final Function<Triple<State, Action, State>, Counter<String>> featurizer,
      final Collection<Action> actionSpace,
      final int maxTicks
  ) {
    // (the fringe)
    PriorityQueue<Pair<State, List<Counter<String>>>> fringe = new FixedPrioritiesPriorityQueue<>();
    // (a helper list)
    List<SemanticGraphEdge> ppEdges = new ArrayList<>();
    // (avoid duplicate work)
    Set<IndexedWord> seenWords = new HashSet<>();

    State firstState = new State(null, null, -9000, null, x -> {
    }, false);
    fringe.add(Pair.makePair(firstState, new ArrayList<>(0)), -0.0);
    int ticks = 0;

    while (!fringe.isEmpty()) {
      if (++ticks > maxTicks) {
        System.err.println("WARNING! Timed out on search with " + ticks + " ticks");
        return;
      }
      // Useful variables
      double logProbSoFar = fringe.getPriority();
      Pair<State, List<Counter<String>>> lastStatePair = fringe.removeFirst();
      State lastState = lastStatePair.first;
      List<Counter<String>> featuresSoFar = lastStatePair.second;
      IndexedWord rootWord = lastState.edge == null ? root : lastState.edge.getDependent();
//      System.err.println("Looking at " + rootWord);

      // Register thunk
      if (!candidateFragments.test(Triple.makeTriple(logProbSoFar, featuresSoFar, () -> {
        SemanticGraph copy = new SemanticGraph(tree);
        lastState.thunk.andThen(x -> {
          // Add the extra edges back in, if they don't break the tree-ness of the extraction
          for (IndexedWord newTreeRoot : x.getRoots()) {
            for (SemanticGraphEdge extraEdge : extraEdgesByGovernor.get(newTreeRoot)) {
              assert isTree(x);
              //noinspection unchecked
              addSubtree(x, newTreeRoot, extraEdge.getRelation().toString(), tree, extraEdge.getDependent(), tree.getIncomingEdgesSorted(newTreeRoot));
              assert isTree(x);
            }
          }
        }).accept(copy);
        return new SentenceFragment(copy, false);
      }))) {
        break;
      }

      // Find relevant auxilliary terms
      ppEdges.clear();
      ppEdges.add(null);
      SemanticGraphEdge subjOrNull = null;
      for (SemanticGraphEdge auxEdge : tree.outgoingEdgeIterable(rootWord)) {
        String relString = auxEdge.getRelation().toString();
        if (relString.startsWith("prep")) {
          ppEdges.add(auxEdge);
        } else if (relString.contains("subj")) {
          subjOrNull = auxEdge;
        }
      }

      // Iterate over children
      for (Action action : actionSpace) {
        // For each action...
        for (SemanticGraphEdge outgoingEdge : tree.outgoingEdgeIterable(rootWord)) {
          // For each outgoing edge...
          // 1. Find the best aux information to carry along
          double max = Double.NEGATIVE_INFINITY;
          Pair<State, List<Counter<String>>> argmax = null;
          for (SemanticGraphEdge ppEdgeOrNull : ppEdges) {
            Optional<State> candidate = action.applyTo(tree, lastState,
                outgoingEdge, subjOrNull,
                ppEdgeOrNull);
            if (candidate.isPresent()) {
              Counter<String> features = featurizer.apply(Triple.makeTriple(lastState, action, candidate.get()));
              double probability = SloppyMath.sigmoid(Counters.dotProduct(features, weights));
              if (probability > max) {
                max = probability;
                argmax = Pair.makePair(candidate.get(), new ArrayList<Counter<String>>(featuresSoFar) {{
                  add(features);
                }});
              }
            }
          }
          // 2. Register the child state
          if (argmax != null && !seenWords.contains(argmax.first.edge.getDependent())) {
//            System.err.println("  pushing " + action.signature() + " with " + argmax.first.edge);
            fringe.add(argmax, Math.log(max));
          }
        }
      }

      seenWords.add(rootWord);
    }
  }



  /**
   * TODO(gabor) JavaDoc
   * @param classifier
   * @param dataset
   */
  private static void dumpAccuracy(Classifier<Boolean, String> classifier, GeneralDataset<Boolean, String> dataset) {
    DecimalFormat df = new DecimalFormat("0.000");
    log("size:       " + dataset.size());
    log("true count: " + StreamSupport.stream(dataset.spliterator(), false).filter(RVFDatum::label).collect(Collectors.toList()).size());
    Pair<Double, Double> pr = classifier.evaluatePrecisionAndRecall(dataset, true);
    log("precision:  " + df.format(pr.first));
    log("recall:     " + df.format(pr.second));
    log("f1:         " + df.format(2 * pr.first * pr.second / (pr.first + pr.second)));
  }

  /**
   * TODO(gabor) JavaDoc
   *
   * @param trainingData
   * @param featurizer
   * @param options
   * @param modelPath
   * @param trainingDataDump
   * @return
   */
  public static Function<SemanticGraph, ClauseSearcher> trainFactory(
      Stream<Triple<CoreMap, Span, Span>> trainingData,
      Featurizer featurizer,
      TrainingOptions options,
      Optional<File> modelPath,
      Optional<File> trainingDataDump) {
    // Parse options
    ClassifierFactory<Boolean, String, Classifier<Boolean,String>> classifierFactory = MetaClass.create(options.classifierFactory).createInstance();
    // Generally useful objects
    OpenIE openie = new OpenIE();
    Random rand = new Random(options.seed);
    WeightedDataset<Boolean, String> dataset = new WeightedDataset<>();
    AtomicInteger numExamplesProcessed = new AtomicInteger(0);
    final Optional<PrintWriter> datasetDumpWriter = trainingDataDump.map(file -> {
      try {
        return new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(trainingDataDump.get()))));
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    });

    // Step 1: Inference over training sentences
    forceTrack("Training inference");
    trainingData.forEach(triple -> {
      // Parse training datum
      CoreMap sentence = triple.first;
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      Span subjectSpan = Util.extractNER(tokens, triple.second);
      Span objectSpan = Util.extractNER(tokens, triple.third);
//      log("inference on " + StringUtils.join(tokens.subList(0, Math.min(10, tokens.size())).stream().map(CoreLabel::word), " "));
      // Create raw clause searcher (no classifier)
      SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
      ClauseSearcher problem = new ClauseSearcher(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
      Pointer<Boolean> anyCorrect = new Pointer<>(false);

      // Run search
      problem.search(fragmentAndScore -> {
        // Parse the search output
        double score = fragmentAndScore.first;
        List<Counter<String>> features = fragmentAndScore.second;
        Supplier<SentenceFragment> fragmentSupplier = fragmentAndScore.third;
        SentenceFragment fragment = fragmentSupplier.get();
        // Search for extractions
        List<RelationTriple> extractions = openie.relationInClause(fragment.parseTree);
        boolean correct = false;
        RelationTriple bestExtraction = null;
        for (RelationTriple extraction : extractions) {
          // Clean up the guesses
          Span subjectGuess = Util.extractNER(tokens, Span.fromValues(extraction.subject.get(0).index() - 1, extraction.subject.get(extraction.subject.size() - 1).index()));
          Span objectGuess = Util.extractNER(tokens, Span.fromValues(extraction.object.get(0).index() - 1, extraction.object.get(extraction.object.size() - 1).index()));
          // Check if it matches
          if ((subjectGuess.equals(subjectSpan) && objectGuess.equals(objectSpan)) ||
              (subjectGuess.equals(objectSpan) && objectGuess.equals(subjectSpan))
              ) {
            correct = true;
            anyCorrect.set(true);
            bestExtraction = extraction;
          } else if ((subjectGuess.contains(subjectSpan) && objectGuess.contains(objectSpan)) ||
              (subjectGuess.contains(objectSpan) && objectGuess.contains(subjectSpan))
              ) {
            correct = true;
            anyCorrect.set(true);
            if (bestExtraction == null) {
              bestExtraction = extraction;
            }
          } else {
            if (bestExtraction == null && !correct) {
              bestExtraction = extraction;
            }
            correct = false;
          }
        }
        // Dump the datum
        if (bestExtraction != null || fragment.length() == 1) {
          if (correct || rand.nextDouble() > (1.0 - options.negativeSubsampleRatio)) {  // Subsample
            for (Counter<String> decision : features) {
              // Add datum to dataset
              RVFDatum<Boolean, String> datum = new RVFDatum<>(decision);
              datum.setLabel(correct);
              dataset.add(datum, correct ? options.positiveDatumWeight : 1.0f);
              // Dump datum to debug log
              if (datasetDumpWriter.isPresent()) {
                datasetDumpWriter.get().println("" + correct + "\t" + StringUtils.join(decision.entrySet().stream().map(entry -> "" + entry.getKey() + "->" + entry.getValue()), ";"));
              }
            }
          }
        }
        return true;
      }, new ClassicCounter<>(), featurizer, 10000);
      // Debug info
      if (numExamplesProcessed.incrementAndGet() % 100 == 0) {
        log("processed " + numExamplesProcessed + " training sentences: " + dataset.size() + " datums");
      }
    });
    // Close dataset dump
    datasetDumpWriter.ifPresent(PrintWriter::close);
    endTrack("Training inference");

    // Step 2: Train classifier
    forceTrack("Training");
    Classifier<Boolean,String> fullClassifier = classifierFactory.trainClassifier(dataset);
    endTrack("Training");
    if (modelPath.isPresent()) {
      Pair<Classifier<Boolean,String>, Featurizer> toSave = Pair.makePair(fullClassifier, featurizer);
      try {
        IOUtils.writeObjectToFile(toSave, modelPath.get());
        log("SUCCESS: wrote model to " + modelPath.get().getPath());
      } catch (IOException e) {
        log("ERROR: failed to save model to path: " + modelPath.get().getPath());
        err(e);
      }
    }

    // Step 3: Check accuracy of classifier
    forceTrack("Training accuracy");
    dataset.randomize(options.seed);
    dumpAccuracy(fullClassifier, dataset);
    endTrack("Training accuracy");

    int numFolds = 5;
    forceTrack("" + numFolds + " fold cross-validation");
    for (int fold = 0; fold < numFolds; ++fold) {
      forceTrack("Fold " + (fold + 1));
      forceTrack("Training");
      Pair<GeneralDataset<Boolean, String>, GeneralDataset<Boolean, String>> foldData = dataset.splitOutFold(fold, numFolds);
      Classifier<Boolean, String> classifier = classifierFactory.trainClassifier(foldData.first);
      endTrack("Training");
      forceTrack("Test");
      dumpAccuracy(classifier, foldData.second);
      endTrack("Test");
      endTrack("Fold " + (fold + 1));
    }
    endTrack("" + numFolds + " fold cross-validation");


    // Step 5: return factory
    return tree -> new ClauseSearcher(tree, Optional.of(fullClassifier), Optional.of(featurizer));
  }


  /**
   * TODO(gabor) JavaDoc
   * @param trainingData
   * @param modelPath
   * @param trainingDataDump
   * @return
   */
  public static Function<SemanticGraph, ClauseSearcher> trainFactory(
      Stream<Triple<CoreMap, Span, Span>> trainingData,
      File modelPath,
      File trainingDataDump) {
    // Featurizer
    Featurizer featurizer = triple -> {
      // Variables
      ClauseSearcher.State from = triple.first;
      ClauseSearcher.Action action = triple.second;
      ClauseSearcher.State to = triple.third;
      String signature = action.signature();
      String edgeRelTaken = to.edge == null ? "root" : to.edge.getRelation().toString();
      String edgeRelShort = to.edge == null ?  "root"  : to.edge.getRelation().getShortName();
      if (edgeRelShort.contains("_")) {
        edgeRelShort = edgeRelShort.substring(0, edgeRelShort.indexOf("_"));
      }
      String edgeRelSpecific = to.edge == null ? null : to.edge.getRelation().getSpecific();

      // -- Featurize --
      // Variables to aggregate
      boolean parentHasSubj = false;
      boolean parentHasObj = false;
      boolean childHasSubj = false;
      boolean childHasObj = false;

      // 1. edge taken
      Counter<String> feats = new ClassicCounter<>();
      feats.incrementCount(signature + "&edge:" + edgeRelTaken);
      feats.incrementCount(signature + "&edge_type:" + edgeRelShort);

      if (to.edge != null) {
        // 2. other edges at parent
        for (SemanticGraphEdge parentNeighbor : from.originalTree().outgoingEdgeIterable(to.edge.getGovernor())) {
          if (parentNeighbor != to.edge) {
            String parentNeighborRel = parentNeighbor.getRelation().toString();
            if (parentNeighborRel.contains("subj")) { parentHasSubj = true; }
            if (parentNeighborRel.contains("obj")) { parentHasObj = true; }
            // (add feature)
            feats.incrementCount(signature + "&parent_neighbor:" + parentNeighborRel);
            feats.incrementCount(signature + "&edge_type:" + edgeRelShort + "&parent_neighbor:" + parentNeighborRel);
          }
        }

        // 3. Other edges at child
        for (SemanticGraphEdge childNeighbor : from.originalTree().outgoingEdgeIterable(to.edge.getDependent())) {
            String childNeighborRel = childNeighbor.getRelation().toString();
            if (childNeighborRel.contains("subj")) { childHasSubj = true; }
            if (childNeighborRel.contains("obj")) { childHasObj = true; }
            // (add feature)
            feats.incrementCount(signature + "&child_neighbor:" + childNeighborRel);
            feats.incrementCount(signature + "&edge_type:" + edgeRelShort + "&child_neighbor:" + childNeighborRel);
        }

        // 4. Subject/Object stats
        feats.incrementCount(signature + "&parent_neighbor_subj:" + parentHasSubj);
        feats.incrementCount(signature + "&parent_neighbor_obj:" + parentHasObj);
        feats.incrementCount(signature + "&child_neighbor_subj:" + childHasSubj);
        feats.incrementCount(signature + "&child_neighbor_obj:" + childHasObj);
      }

      // Return
      return feats;
    };
    // Train
    return trainFactory(trainingData, featurizer, new TrainingOptions(), Optional.of(modelPath), Optional.of(trainingDataDump));
  }


  /**
   * TODO(gabor) JavaDoc
   * @return
   */
  public static Function<SemanticGraph, ClauseSearcher> factory(File serializedModel) throws IOException {
    try {
      System.err.println("Loading clause searcher from " + serializedModel.getPath() + " ...");
      Pair<Classifier<Boolean,String>, Featurizer> data = IOUtils.readObjectFromFile(serializedModel);
      return tree -> new ClauseSearcher(tree, Optional.of(data.first), Optional.of(data.second));
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Invalid model at path: " + serializedModel.getPath(), e);
    }
  }

}
