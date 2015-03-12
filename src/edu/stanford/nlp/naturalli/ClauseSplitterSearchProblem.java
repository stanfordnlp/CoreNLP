package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.naturalli.ClauseSplitter.ClauseClassifierLabel;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * <p>
 *   A search problem for finding clauses in a sentence.
 * </p>
 *
 * <p>
 *   For usage at test time, load a model from
 *   {@link ClauseSplitter#load(String)}, and then take the top clauses of a given tree
 *   with {@link ClauseSplitterSearchProblem#topClauses(double)}, yielding a list of
 *   {@link edu.stanford.nlp.naturalli.SentenceFragment}s.
 * </p>
 * <pre>
 *   {@code
 *     ClauseSearcher searcher = ClauseSearcher.factory("/model/path/");
 *     List<SentenceFragment> sentences = searcher.topClauses(threshold);
 *   }
 * </pre>
 *
 * <p>
 *   For training, see {@link ClauseSplitter#train(Stream, File, File)}.
 * </p>
 *
 * @author Gabor Angeli
 */
public class ClauseSplitterSearchProblem {

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
  private final Optional<Classifier<ClauseSplitter.ClauseClassifierLabel, String>> isClauseClassifier;
  /**
   * An optional featurizer to use with the clause classifier ({@link ClauseSplitterSearchProblem#isClauseClassifier}).
   * If that classifier is defined, this should be as well.
   */
  private final Optional<Function<Triple<ClauseSplitterSearchProblem.State, ClauseSplitterSearchProblem.Action, ClauseSplitterSearchProblem.State>, Counter<String>>> featurizer;

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
    public boolean isDone;

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
      return ClauseSplitterSearchProblem.this.tree;
    }

    public State withIsDone(ClauseClassifierLabel argmax) {
      if (argmax == ClauseClassifierLabel.CLAUSE_SPLIT) {
        isDone = true;
      } else if (argmax == ClauseClassifierLabel.CLAUSE_INTERM) {
        isDone = false;
      } else {
        throw new IllegalStateException("Invalid classifier label for isDone: " + argmax);
      }
      return this;
    }
  }

  /**
   * An action being taken; that is, the type of clause splitting going on.
   */
  public static interface Action {
    /**
     * The name of this action.
     */
    public String signature();

    /**
     * A check to make sure this is actually a valid action to take, in the context of the given tree.
     * @param originalTree The _original_ tree we are searching over. This is before any clauses are split off.
     * @param edge The edge that we are traversing with this clause.
     * @return True if this is a valid action.
     */
    @SuppressWarnings("UnusedParameters")
    public default boolean prerequisitesMet(SemanticGraph originalTree, SemanticGraphEdge edge) {
      return true;
    }

    /**
     * Apply this action to the given state.
     * @param tree The original tree we are applying the action to.
     * @param source The source state we are mutating from.
     * @param outgoingEdge The edge we are splitting off as a clause.
     * @param subjectOrNull The subject of the parent tree, if there is one.
     * @param ppOrNull The preposition attachment of the parent tree, if there is one.
     * @return A new state, or {@link Optional#empty()} if this action was not successful.
     */
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
    public double negativeSubsampleRatio = 1.00;
    @Execution.Option(name = "positiveDatumWeight", gloss = "The weight to assign every positive datum.")
    public float positiveDatumWeight = 100.0f;
    @Execution.Option(name = "unknownDatumWeight", gloss = "The weight to assign every unknown datum (everything extracted with an unconfirmed relation).")
    public float unknownDatumWeight = 1.0f;
    @Execution.Option(name = "clauseSplitWeight", gloss = "The weight to assign for clause splitting datums. Higher values push towards higher recall.")
    public float clauseSplitWeight = 1.0f;
    @Execution.Option(name = "clauseIntermWeight", gloss = "The weight to assign for intermediate splits. Higher values push towards higher recall.")
    public float clauseIntermWeight = 2.0f;
    @Execution.Option(name = "seed", gloss = "The random seed to use")
    public int seed = 42;
    @SuppressWarnings("unchecked")
    @Execution.Option(name = "classifierFactory", gloss = "The class of the classifier factory to use for training the various classifiers")
    public Class<? extends ClassifierFactory<ClauseSplitter.ClauseClassifierLabel, String, Classifier<ClauseSplitter.ClauseClassifierLabel, String>>> classifierFactory = (Class<? extends ClassifierFactory<ClauseSplitter.ClauseClassifierLabel, String, Classifier<ClauseSplitter.ClauseClassifierLabel, String>>>) ((Object) LinearClassifierFactory.class);
  }

  /**
   * Mostly just an alias, but make sure our featurizer is serializable!
   */
  public static interface Featurizer extends Function<Triple<ClauseSplitterSearchProblem.State, ClauseSplitterSearchProblem.Action, ClauseSplitterSearchProblem.State>, Counter<String>>, Serializable { }

  /**
   * Create a searcher manually, suppling a dependency tree, an optional classifier for when to split clauses,
   * and a featurizer for that classifier.
   * You almost certainly want to use {@link ClauseSplitter#load(String)} instead of this
   * constructor.
   *
   * @param tree               The dependency tree to search over.
   * @param isClauseClassifier The classifier for whether a given dependency arc should be a new clause. If this is not given, all arcs are treated as clause separators.
   * @param featurizer         The featurizer for the classifier. If no featurizer is given, one should be given in {@link ClauseSplitterSearchProblem#search(java.util.function.Predicate, Classifier, java.util.function.Function, int)}, or else the classifier will be useless.
   * @see ClauseSplitter#load(String)
   */
  protected ClauseSplitterSearchProblem(SemanticGraph tree,
                                        Optional<Classifier<ClauseSplitter.ClauseClassifierLabel, String>> isClauseClassifier,
                                        Optional<Function<Triple<ClauseSplitterSearchProblem.State, ClauseSplitterSearchProblem.Action, ClauseSplitterSearchProblem.State>, Counter<String>>> featurizer
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
    List<SemanticGraphEdge> extraEdges = Util.cleanTree(this.tree);
    assert Util.isTree(this.tree);
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
  protected ClauseSplitterSearchProblem(SemanticGraph tree) {
    this(tree, Optional.empty(), Optional.empty());
  }

  /**
   * The basic method for splitting off a clause of a tree.
   * This modifies the tree in place.
   *
   * @param tree The tree to split a clause from.
   * @param toKeep The edge representing the clause to keep.
   */
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
    nodesToRemove.forEach(tree::removeVertex);
    // Set new root
    tree.setRoot(toKeep.getDependent());
  }

  /**
   * A helper to add a single word to a given dependency tree
   * @param toModify The tree to add the word to.
   * @param root The root of the tree where we should be adding the word.
   * @param rel The relation to add the word with.
   * @param coreLabel The word to add.
   */
  @SuppressWarnings("UnusedDeclaration")
  private static void addWord(SemanticGraph toModify, IndexedWord root, String rel, CoreLabel coreLabel) {
    IndexedWord dependent = new IndexedWord(coreLabel);
    toModify.addVertex(dependent);
    toModify.addEdge(root, dependent, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, rel), Double.NEGATIVE_INFINITY, false);
  }

  /**
   * A helper to add an entire subtree to a given dependency tree.
   *
   * @param toModify The tree to add the subtree to.
   * @param root The root of the tree where we should be adding the subtree.
   * @param rel The relation to add the subtree with.
   * @param originalTree The orignal tree (i.e., {@link ClauseSplitterSearchProblem#tree}).
   * @param subject The root of the clause to add.
   * @param ignoredEdges The edges to ignore adding when adding this subtree.
   */
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
    wordsToAdd.forEach(toModify::addVertex);
    // (add edges)
    for (SemanticGraphEdge edge : edgesToAdd) {
      assert !toModify.incomingEdgeIterator(edge.getDependent()).hasNext();
      toModify.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
  }


  /**
   * Create a mock node, to be added to the dependency tree but which is not part of the original sentence.
   *
   * @param toCopy The CoreLabel to copy from initially.
   * @param word   The new word to add.
   * @param POS    The new part of speech to add.
   *
   * @return A CoreLabel copying most fields from toCopy, but with a new word and POS tag (as well as a new index).
   */
  @SuppressWarnings("UnusedDeclaration")
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
   * Get the top few clauses from this searcher, cutting off at the given minimum
   * probability.
   * @param thresholdProbability The threshold under which to stop returning clauses. This should be between 0 and 1.
   * @return The resulting {@link edu.stanford.nlp.naturalli.SentenceFragment} objects, representing the top clauses of the sentence.
   */
  public List<SentenceFragment> topClauses(double thresholdProbability) {
    List<SentenceFragment> results = new ArrayList<>();
    search(triple -> {
      assert triple.first <= 0.0;
      double prob = Math.exp(triple.first);
      assert prob <= 1.0;
      assert prob >= 0.0;
      assert !Double.isNaN(prob);
      if (prob >= thresholdProbability) {
        SentenceFragment fragment = triple.third.get();
        fragment.score = prob;
        results.add(fragment);
        return true;
      } else {
        return false;
      }
    });
    return results;
  }

  /**
   * Search, using the default weights / featurizer. This is the most common entry method for the raw search,
   * though {@link ClauseSplitterSearchProblem#topClauses(double)} may be a more convenient method for
   * an end user.
   *
   * @param candidateFragments The callback function for results. The return value defines whether to continue searching.
   */
  public void search(final Predicate<Triple<Double, List<Counter<String>>, Supplier<SentenceFragment>>> candidateFragments) {
    if (!isClauseClassifier.isPresent() ||
        !(isClauseClassifier.get() instanceof LinearClassifier)) {
      throw new IllegalArgumentException("For now, only linear classifiers are supported");
    }
    search(candidateFragments,
        isClauseClassifier.get(),
        this.featurizer.get(),
        10000);
  }

  /**
   * Search from the root of the tree.
   * This function also defines the default action space to use during search.
   *
   * @param candidateFragments The callback function.
   * @param classifier The classifier for whether an arc should be on the path to a clause split, a clause split itself, or neither.
   * @param featurizer The featurizer to use during search, to be dot producted with the weights.
   */
  protected void search(
      // The output specs
      final Predicate<Triple<Double, List<Counter<String>>, Supplier<SentenceFragment>>> candidateFragments,
      // The learning specs
      final Classifier<ClauseSplitter.ClauseClassifierLabel, String> classifier,
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
              assert Util.isTree(toModify);
              simpleClause(toModify, outgoingEdge);
              assert Util.isTree(toModify);
            }), false
        ));
      }
    });

    /*
    // CLONE ROOT
    actionSpace.add(new Action() {
      @Override
      public String signature() {
        return "clone_root_as_nsubjpass";
      }

      @Override
      public boolean prerequisitesMet(SemanticGraph originalTree, SemanticGraphEdge edge) {
        // Only valid if there's a single outgoing edge from a node. Otherwise it's a whole can of worms.
        Iterator<SemanticGraphEdge> iter =  originalTree.outgoingEdgeIterable(edge.getGovernor()).iterator();
        if (!iter.hasNext()) {
          return false; // what?
        }
        iter.next();
        return !iter.hasNext();
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
    */

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
                assert Util.isTree(toModify);
                simpleClause(toModify, outgoingEdge);
                addSubtree(toModify, outgoingEdge.getDependent(), "nsubj", tree,
                    subjectOrNull.getDependent(), Collections.singleton(outgoingEdge));
                assert Util.isTree(toModify);
              }), false
          ));
        } else {
          return Optional.empty();
        }
      }
    });

    for (IndexedWord root : tree.getRoots()) {
      search(root, candidateFragments, classifier, featurizer, actionSpace, maxTicks);
    }
  }

  /**
   * The core implementation of the search.
   *
   * @param root The root word to search from. Traditionally, this is the root of the sentence.
   * @param candidateFragments The callback for the resulting sentence fragments.
   *                           This is a predicate of a triple of values.
   *                           The return value of the predicate determines whether we should continue searching.
   *                           The triple is a triple of
   *                           <ol>
   *                             <li>The log probability of the sentence fragment, according to the featurizer and the weights</li>
   *                             <li>The features along the path to this fragment. The last element of this is the features from the most recent step.</li>
   *                             <li>The sentence fragment. Because it is relatively expensive to compute the resulting tree, this is returned as a lazy {@link Supplier}.</li>
   *                           </ol>
   * @param classifier The classifier for whether an arc should be on the path to a clause split, a clause split itself, or neither.
   * @param featurizer The featurizer to use. Make sure this matches the weights!
   * @param actionSpace The action space we are allowed to take. Each action defines a means of splitting a clause on a dependency boundary.
   */
  protected void search(
      // The root to search from
      IndexedWord root,
      // The output specs
      final Predicate<Triple<Double, List<Counter<String>>, Supplier<SentenceFragment>>> candidateFragments,
      // The learning specs
      final Classifier<ClauseSplitter.ClauseClassifierLabel,String> classifier,
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
    }, true);  // First state is implicitly "done"
    fringe.add(Pair.makePair(firstState, new ArrayList<>(0)), -0.0);
    int ticks = 0;

    while (!fringe.isEmpty()) {
      if (++ticks > maxTicks) {
        System.err.println("WARNING! Timed out on search with " + ticks + " ticks");
        return;
      }
      // Useful variables
      double logProbSoFar = fringe.getPriority();
      assert logProbSoFar <= 0.0;
      Pair<State, List<Counter<String>>> lastStatePair = fringe.removeFirst();
      State lastState = lastStatePair.first;
      List<Counter<String>> featuresSoFar = lastStatePair.second;
      IndexedWord rootWord = lastState.edge == null ? root : lastState.edge.getDependent();

      // Register thunk
      if (lastState.isDone) {
        if (!candidateFragments.test(Triple.makeTriple(logProbSoFar, featuresSoFar, () -> {
          SemanticGraph copy = new SemanticGraph(tree);
          lastState.thunk.andThen(x -> {
            // Add the extra edges back in, if they don't break the tree-ness of the extraction
            for (IndexedWord newTreeRoot : x.getRoots()) {
              for (SemanticGraphEdge extraEdge : extraEdgesByGovernor.get(newTreeRoot)) {
                assert Util.isTree(x);
                //noinspection unchecked
                addSubtree(x, newTreeRoot, extraEdge.getRelation().toString(), tree, extraEdge.getDependent(), tree.getIncomingEdgesSorted(newTreeRoot));
                assert Util.isTree(x);
              }
            }
          }).accept(copy);
          return new SentenceFragment(copy, false);
        }))) {
          break;
        }
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
          // Check the prerequisite
          if (!action.prerequisitesMet(tree, outgoingEdge)) {
            continue;
          }
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
              Counter<ClauseClassifierLabel> scores = classifier.scoresOf(new RVFDatum<>(features));
              if (scores.size() > 0) {
                Counters.logNormalizeInPlace(scores);
              }
              scores.remove(ClauseClassifierLabel.NOT_A_CLAUSE);
              double logProbability = Counters.max(scores, Double.NEGATIVE_INFINITY);
              if (logProbability >= max) {
                max = logProbability;
                argmax = Pair.makePair(candidate.get().withIsDone(Counters.argmax(scores, (x, y) -> 0, ClauseClassifierLabel.CLAUSE_SPLIT)), new ArrayList<Counter<String>>(featuresSoFar) {{
                  add(features);
                }});
                assert logProbability <= 0.0;
              }
            }
          }
          // 2. Register the child state
          if (argmax != null && !seenWords.contains(argmax.first.edge.getDependent())) {
//            System.err.println("  pushing " + action.signature() + " with " + argmax.first.edge);
            fringe.add(argmax, max);
          }
        }
      }

      seenWords.add(rootWord);
    }
  }



  /**
   * The default featurizer to use during training.
   */
  public static final Featurizer DEFAULT_FEATURIZER = triple -> {
    // Variables
    State from = triple.first;
    Action action = triple.second;
    State to = triple.third;
    String signature = action.signature();
    String edgeRelTaken = to.edge == null ? "root" : to.edge.getRelation().toString();
    String edgeRelShort = to.edge == null ?  "root"  : to.edge.getRelation().getShortName();
    if (edgeRelShort.contains("_")) {
      edgeRelShort = edgeRelShort.substring(0, edgeRelShort.indexOf("_"));
    }

    // -- Featurize --
    // Variables to aggregate
    boolean parentHasSubj = false;
    boolean parentHasObj = false;
    boolean childHasSubj = false;
    boolean childHasObj = false;
    Counter<String> feats = new ClassicCounter<>();

    // 1. edge taken
    feats.incrementCount(signature + "&edge:" + edgeRelTaken);
    feats.incrementCount(signature + "&edge_type:" + edgeRelShort);

    // 2. last edge taken
    if (from.edge == null) {
      assert to.edge == null || to.originalTree().getRoots().contains(to.edge.getGovernor());
      feats.incrementCount(signature + "&at_root");
      feats.incrementCount(signature + "&at_root&root_pos:" + to.originalTree().getFirstRoot().tag());
    } else {
      feats.incrementCount(signature + "&not_root");
      String lastRelShort = from.edge.getRelation().getShortName();
      if (lastRelShort.contains("_")) {
        lastRelShort = lastRelShort.substring(0, lastRelShort.indexOf("_"));
      }
      feats.incrementCount(signature + "&last_edge:" + lastRelShort);
    }

    if (to.edge != null) {
      // 3. other edges at parent
      for (SemanticGraphEdge parentNeighbor : from.originalTree().outgoingEdgeIterable(to.edge.getGovernor())) {
        if (parentNeighbor != to.edge) {
          String parentNeighborRel = parentNeighbor.getRelation().toString();
          if (parentNeighborRel.contains("subj")) {
            parentHasSubj = true;
          }
          if (parentNeighborRel.contains("obj")) {
            parentHasObj = true;
          }
          // (add feature)
          feats.incrementCount(signature + "&parent_neighbor:" + parentNeighborRel);
          feats.incrementCount(signature + "&edge_type:" + edgeRelShort + "&parent_neighbor:" + parentNeighborRel);
        }
      }

      // 4. Other edges at child
      int childNeighborCount = 0;
      for (SemanticGraphEdge childNeighbor : from.originalTree().outgoingEdgeIterable(to.edge.getDependent())) {
        String childNeighborRel = childNeighbor.getRelation().toString();
        if (childNeighborRel.contains("subj")) {
          childHasSubj = true;
        }
        if (childNeighborRel.contains("obj")) {
          childHasObj = true;
        }
        childNeighborCount += 1;
        // (add feature)
        feats.incrementCount(signature + "&child_neighbor:" + childNeighborRel);
        feats.incrementCount(signature + "&edge_type:" + edgeRelShort + "&child_neighbor:" + childNeighborRel);
      }
      // 4.1 Number of other edges at child
      feats.incrementCount(signature + "&child_neighbor_count:" + (childNeighborCount < 3 ? childNeighborCount : ">2"));
      feats.incrementCount(signature + "&edge_type:" + edgeRelShort + "&child_neighbor_count:" + (childNeighborCount < 3 ? childNeighborCount : ">2"));


      // 5. Subject/Object stats
      feats.incrementCount(signature + "&parent_neighbor_subj:" + parentHasSubj);
      feats.incrementCount(signature + "&parent_neighbor_obj:" + parentHasObj);
      feats.incrementCount(signature + "&child_neighbor_subj:" + childHasSubj);
      feats.incrementCount(signature + "&child_neighbor_obj:" + childHasObj);

      // 6. POS tag info
      feats.incrementCount(signature + "&parent_pos:" + to.edge.getGovernor().tag());
      feats.incrementCount(signature + "&child_pos:" + to.edge.getDependent().tag());
      feats.incrementCount(signature + "&pos_signature:" + to.edge.getGovernor().tag() + "_" + to.edge.getDependent().tag());
      feats.incrementCount(signature + "&edge_type:" + edgeRelShort + "&pos_signature:" + to.edge.getGovernor().tag() + "_" + to.edge.getDependent().tag());
    }
    return feats;
  };

}
