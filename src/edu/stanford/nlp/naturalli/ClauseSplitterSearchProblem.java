package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.international.Language;
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
import edu.stanford.nlp.util.logging.Redwood;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 *   A search problem for finding clauses in a sentence.
 *
 * <p>
 *   For usage at test time, load a model from
 *   {@link ClauseSplitter#load(String)}, and then take the top clauses of a given tree
 *   with {@link ClauseSplitterSearchProblem#topClauses(double, int)}, yielding a list of
 *   {@link edu.stanford.nlp.naturalli.SentenceFragment}s.
 * <p>
 * <pre>
 *   {@code
 *     ClauseSearcher searcher = ClauseSearcher.factory("/model/path/");
 *     List<SentenceFragment> sentences = searcher.topClauses(threshold);
 *   }
 * </pre>
 *
 * <p>
 *   For training, see {@link ClauseSplitter#train(Stream, File, File)}.
 *
 * @author Gabor Angeli
 */
public class ClauseSplitterSearchProblem  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ClauseSplitterSearchProblem.class);

  /**
   * A specification for clause splits we _always_ want to do. The format is a map from the edge label we are splitting, to
   * the preference for the type of split we should do. The most preferred is at the front of the list, and then it backs off
   * to the less and less preferred split types.
   */
  protected static final Map<String, List<String>> HARD_SPLITS = Collections.unmodifiableMap(new HashMap<String, List<String>>() {{
    put("comp", new ArrayList<String>() {{
      add("simple");
    }});
    put("ccomp", new ArrayList<String>() {{
      add("simple");
    }});
    put("xcomp", new ArrayList<String>() {{
      add("clone_obj");
      add("clone_nsubj");
      add("simple");
    }});
    put("vmod", new ArrayList<String>() {{
      add("clone_nsubj");
      add("simple");
    }});
    put("csubj", new ArrayList<String>() {{
      add("clone_obj");
      add("simple");
    }});
    put("advcl", new ArrayList<String>() {{
      add("clone_nsubj");
      add("simple");
    }});
    put("advcl:*", new ArrayList<String>() {{
      add("clone_nsubj");
      add("simple");
    }});
    put("conj:*", new ArrayList<String>() {{
      add("clone_nsubj");
      add("clone_obj");
      add("simple");
    }});
    put("acl:relcl", new ArrayList<String>() {{  // no doubt (-> that cats have tails <-)
      add("simple");
    }});
    put("parataxis", new ArrayList<String>() {{  // no doubt (-> that cats have tails <-)
      add("simple");
    }});
  }});

  /**
   * A set of words which indicate that the complement clause is not factual, or at least not necessarily factual.
   */
  protected static final Set<String> INDIRECT_SPEECH_LEMMAS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("report"); add("say"); add("told"); add("claim"); add("assert"); add("think"); add("believe"); add("suppose");
  }});

  /**
   * The tree to search over.
   */
  public final SemanticGraph tree;
  /**
   * The assumed truth of the original clause.
   */
  public final boolean assumedTruth;
  /**
   * The length of the sentence, as determined from the tree.
   */
  public final int sentenceLength;
  /**
   * A mapping from a word to the extra edges that come out of it.
   */
  private final Map<IndexedWord, Collection<SemanticGraphEdge>> extraEdgesByGovernor = new HashMap<>();
  /**
   * A mapping from a word to the extra edges that to into it.
   */
  private final Map<IndexedWord, Collection<SemanticGraphEdge>> extraEdgesByDependent = new HashMap<>();
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
    public final SemanticGraphEdge objectOrNull;
    public final Consumer<SemanticGraph> thunk;
    public boolean isDone;

    public State(SemanticGraphEdge edge, SemanticGraphEdge subjectOrNull, int distanceFromSubj, SemanticGraphEdge objectOrNull,
                 Consumer<SemanticGraph> thunk, boolean isDone) {
      this.edge = edge;
      this.edgeIndex = edgeToIndex.indexOf(edge);
      this.subjectOrNull = subjectOrNull;
      this.distanceFromSubj = distanceFromSubj;
      this.objectOrNull = objectOrNull;
      this.thunk = thunk;
      this.isDone = isDone;
    }

    public State(State source, boolean isDone) {
      this.edge = source.edge;
      this.edgeIndex = edgeToIndex.indexOf(edge);
      this.subjectOrNull = source.subjectOrNull;
      this.distanceFromSubj = source.distanceFromSubj;
      this.objectOrNull = source.objectOrNull;
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
  public interface Action {
    /**
     * The name of this action.
     */
    String signature();

    /**
     * A check to make sure this is actually a valid action to take, in the context of the given tree.
     * @param originalTree The _original_ tree we are searching over. This is before any clauses are split off.
     * @param edge The edge that we are traversing with this clause.
     * @return True if this is a valid action.
     */
    @SuppressWarnings("UnusedParameters")
    default boolean prerequisitesMet(SemanticGraph originalTree, SemanticGraphEdge edge) {
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
    Optional<State> applyTo(SemanticGraph tree, State source,
                            SemanticGraphEdge outgoingEdge,
                            SemanticGraphEdge subjectOrNull,
                            SemanticGraphEdge ppOrNull);
  }

  /**
   * The options used for training the clause searcher.
   */
  public static class TrainingOptions {
    @ArgumentParser.Option(name = "negativeSubsampleRatio", gloss = "The percent of negative datums to take")
    public double negativeSubsampleRatio = 1.00;
    @ArgumentParser.Option(name = "positiveDatumWeight", gloss = "The weight to assign every positive datum.")
    public float positiveDatumWeight = 100.0f;
    @ArgumentParser.Option(name = "unknownDatumWeight", gloss = "The weight to assign every unknown datum (everything extracted with an unconfirmed relation).")
    public float unknownDatumWeight = 1.0f;
    @ArgumentParser.Option(name = "clauseSplitWeight", gloss = "The weight to assign for clause splitting datums. Higher values push towards higher recall.")
    public float clauseSplitWeight = 1.0f;
    @ArgumentParser.Option(name = "clauseIntermWeight", gloss = "The weight to assign for intermediate splits. Higher values push towards higher recall.")
    public float clauseIntermWeight = 2.0f;
    @ArgumentParser.Option(name = "seed", gloss = "The random seed to use")
    public int seed = 42;
    @SuppressWarnings("unchecked")
    @ArgumentParser.Option(name = "classifierFactory", gloss = "The class of the classifier factory to use for training the various classifiers")
    public Class<? extends ClassifierFactory<ClauseSplitter.ClauseClassifierLabel, String, Classifier<ClauseSplitter.ClauseClassifierLabel, String>>> classifierFactory = (Class<? extends ClassifierFactory<ClauseSplitter.ClauseClassifierLabel, String, Classifier<ClauseSplitter.ClauseClassifierLabel, String>>>) ((Object) LinearClassifierFactory.class);
  }

  /**
   * Mostly just an alias, but make sure our featurizer is serializable!
   */
  public interface Featurizer extends Function<Triple<ClauseSplitterSearchProblem.State, ClauseSplitterSearchProblem.Action, ClauseSplitterSearchProblem.State>, Counter<String>>, Serializable {
    boolean isSimpleSplit(Counter<String> feats);
  }

  /**
   * Create a searcher manually, suppling a dependency tree, an optional classifier for when to split clauses,
   * and a featurizer for that classifier.
   * You almost certainly want to use {@link ClauseSplitter#load(String)} instead of this
   * constructor.
   *
   * @param tree               The dependency tree to search over.
   * @param assumedTruth       The assumed truth of the tree (relevant for natural logic inference). If in doubt, pass in true.
   * @param isClauseClassifier The classifier for whether a given dependency arc should be a new clause. If this is not given, all arcs are treated as clause separators.
   * @param featurizer         The featurizer for the classifier. If no featurizer is given, one should be given in {@link ClauseSplitterSearchProblem#search(java.util.function.Predicate, Classifier, Map, java.util.function.Function, int)}, or else the classifier will be useless.
   * @see ClauseSplitter#load(String)
   */
  protected ClauseSplitterSearchProblem(SemanticGraph tree, boolean assumedTruth,
                                        Optional<Classifier<ClauseSplitter.ClauseClassifierLabel,String>> isClauseClassifier,
                                        Optional<Function<Triple<ClauseSplitterSearchProblem.State,ClauseSplitterSearchProblem.Action,ClauseSplitterSearchProblem.State>,Counter<String>>> featurizer
  ) {
    this.tree = new SemanticGraph(tree);
    this.assumedTruth = assumedTruth;
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
      extraEdgesByDependent.put(vertex, new ArrayList<>());
    }
    SemanticGraph originalTree = new SemanticGraph(this.tree);
    List<SemanticGraphEdge> extraEdges = Util.cleanTree(this.tree, originalTree);
    assert Util.isTree(this.tree);
    for (SemanticGraphEdge edge : extraEdges) {
      extraEdgesByGovernor.get(edge.getGovernor()).add(edge);
      extraEdgesByDependent.get(edge.getDependent()).add(edge);
    }
  }

  /**
   * Create a clause searcher which searches naively through every possible subtree as a clause.
   * For an end-user, this is almost certainly not what you want.
   * However, it is very useful for training time.
   *
   * @param tree The dependency tree to search over.
   * @param assumedTruth The truth of the premise. Almost always True.
   */
  public ClauseSplitterSearchProblem(SemanticGraph tree, boolean assumedTruth) {
    this(tree, assumedTruth, Optional.empty(), Optional.empty());
  }

  /**
   * The basic method for splitting off a clause of a tree.
   * This modifies the tree in place.
   *
   * @param tree The tree to split a clause from.
   * @param toKeep The edge representing the clause to keep.
   */
  static void splitToChildOfEdge(SemanticGraph tree, SemanticGraphEdge toKeep) {
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
    if (toKeep.getRelation().toString().startsWith("conj")) {
      // A conj may be connected to a cc below it, but
      // keeping that would result in weird / incorrect fragments
      // such as
      // "he and taught constitutional law..."
      // so we remove the "and" / cc relation here
      for (SemanticGraphEdge out : tree.outgoingEdgeIterable(toKeep.getDependent())) {
        if (out.getRelation().toString().equals("cc")) {
          nodesToRemove.add(out.getDependent());
        }
      }
    }
    // Remove nodes
    nodesToRemove.forEach(tree::removeVertex);
    // Set new root
    tree.setRoot(toKeep.getDependent());

  }

  /**
   * The basic method for splitting off a clause of a tree.
   * This modifies the tree in place.
   * This method additionally follows ref edges.
   *
   * @param tree The tree to split a clause from.
   * @param toKeep The edge representing the clause to keep.
   */
  @SuppressWarnings("unchecked")
  private void simpleClause(SemanticGraph tree, SemanticGraphEdge toKeep) {
    splitToChildOfEdge(tree, toKeep);

    // Follow 'ref' edges
    Map<IndexedWord, IndexedWord> refReplaceMap = new HashMap<>();
    // (find replacements)
    for (IndexedWord vertex : tree.vertexSet()) {
      for (SemanticGraphEdge edge : extraEdgesByDependent.get(vertex)) {
        if ("ref".equals(edge.getRelation().toString()) &&  // it's a ref edge...
            !tree.containsVertex(edge.getGovernor())) {     // ...that doesn't already exist in the tree.
          refReplaceMap.put(vertex, edge.getGovernor());
        }
      }
    }
    // (do replacements)
    for (Map.Entry<IndexedWord, IndexedWord> entry : refReplaceMap.entrySet()) {
      Iterator<SemanticGraphEdge> iter = tree.incomingEdgeIterator(entry.getKey());
      if (!iter.hasNext()) { continue; }
      SemanticGraphEdge incomingEdge = iter.next();
      IndexedWord governor = incomingEdge.getGovernor();
      tree.removeVertex(entry.getKey());
      addSubtree(tree, governor, incomingEdge.getRelation().toString(),
          this.tree, entry.getValue(), this.tree.incomingEdgeList(tree.getFirstRoot()));
    }

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
    toModify.addEdge(root, dependent, GrammaticalRelation.valueOf(Language.English, rel), Double.NEGATIVE_INFINITY, false);
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
    toModify.addEdge(root, subject, GrammaticalRelation.valueOf(Language.English, rel), Double.NEGATIVE_INFINITY, false);

    // (add nodes)
    wordsToAdd.forEach(toModify::addVertex);
    // (add edges)
    for (SemanticGraphEdge edge : edgesToAdd) {
      assert !toModify.incomingEdgeIterator(edge.getDependent()).hasNext();
      toModify.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
  }

  /**
   * Strips aux and mark edges when we are splitting into a clause.
   *
   * @param toModify The tree we are stripping the edges from.
   */
  private static void stripAuxMark(SemanticGraph toModify) {
    List<SemanticGraphEdge> toClean = new ArrayList<>();
    for (SemanticGraphEdge edge : toModify.outgoingEdgeIterable(toModify.getFirstRoot())) {
      String rel = edge.getRelation().toString();
      if (("aux".equals(rel) || "mark".equals(rel)) && !toModify.outgoingEdgeIterator(edge.getDependent()).hasNext()) {
        toClean.add(edge);
      }
    }
    for (SemanticGraphEdge edge : toClean) {
      toModify.removeEdge(edge);
      toModify.removeVertex(edge.getDependent());
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
   *
   * @param thresholdProbability The threshold under which to stop returning clauses. This should be between 0 and 1.
   * @param maxClauses A hard limit on the number of clauses to return.
   *
   * @return The resulting {@link edu.stanford.nlp.naturalli.SentenceFragment} objects, representing the top clauses of the sentence.
   */
  public List<SentenceFragment> topClauses(double thresholdProbability, int maxClauses) {
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
   * though {@link ClauseSplitterSearchProblem#topClauses(double, int)} may be a more convenient method for
   * an end user.
   *
   * @param candidateFragments The callback function for results. The return value defines whether to continue searching.
   */
  public void search(final Predicate<Triple<Double, List<Counter<String>>, Supplier<SentenceFragment>>> candidateFragments) {
    if (!isClauseClassifier.isPresent()) {
      search(candidateFragments,
          new LinearClassifier<>(new ClassicCounter<>()),
          HARD_SPLITS,
          this.featurizer.orElse(DEFAULT_FEATURIZER),
          1000);
    } else {
      if (!(isClauseClassifier.get() instanceof LinearClassifier)) {
        throw new IllegalArgumentException("For now, only linear classifiers are supported");
      }
      search(candidateFragments,
          isClauseClassifier.get(),
          HARD_SPLITS,
          this.featurizer.get(),
          1000);
    }
  }

  /**
   * Search from the root of the tree.
   * This function also defines the default action space to use during search.
   * This is NOT recommended to be used at test time.
   *
   * @see edu.stanford.nlp.naturalli.ClauseSplitterSearchProblem#search(Predicate)
   *
   * @param candidateFragments The callback function.
   * @param classifier The classifier for whether an arc should be on the path to a clause split, a clause split itself, or neither.
   * @param featurizer The featurizer to use during search, to be dot producted with the weights.
   */
  public void search(
      // The output specs
      final Predicate<Triple<Double, List<Counter<String>>, Supplier<SentenceFragment>>> candidateFragments,
      // The learning specs
      final Classifier<ClauseSplitter.ClauseClassifierLabel, String> classifier,
      final Map<String, List<String>> hardCodedSplits,
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
      public boolean prerequisitesMet(SemanticGraph originalTree, SemanticGraphEdge edge) {
        char tag = edge.getDependent().tag().charAt(0);
        return !(tag != 'V' && tag != 'N' && tag != 'J' && tag != 'P' && tag != 'D');
      }

      @Override
      public Optional<State> applyTo(SemanticGraph tree, State source, SemanticGraphEdge outgoingEdge, SemanticGraphEdge subjectOrNull, SemanticGraphEdge objectOrNull) {
        return Optional.of(new State(
            outgoingEdge,
            subjectOrNull == null ? source.subjectOrNull : subjectOrNull,
            subjectOrNull == null ? (source.distanceFromSubj + 1) : 0,
            objectOrNull == null ? source.objectOrNull : objectOrNull,
            source.thunk.andThen(toModify -> {
              assert Util.isTree(toModify);
              simpleClause(toModify, outgoingEdge);
              if (outgoingEdge.getRelation().toString().endsWith("comp")) {
                stripAuxMark(toModify);
              }
              assert Util.isTree(toModify);
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
      public boolean prerequisitesMet(SemanticGraph originalTree, SemanticGraphEdge edge) {
        // Only valid if there's a single nontrivial outgoing edge from a node. Otherwise it's a whole can of worms.
        Iterator<SemanticGraphEdge> iter =  originalTree.outgoingEdgeIterable(edge.getGovernor()).iterator();
        if (!iter.hasNext()) {
          return false; // what?
        }
        boolean nontrivialEdge = false;
        while (iter.hasNext()) {
          SemanticGraphEdge outEdge = iter.next();
          switch (outEdge.getRelation().toString()) {
            case "nn":
            case "amod":
              break;
            default:
              if (nontrivialEdge) { return false; }
              nontrivialEdge = true;
          }
        }
        return true;
      }

      @Override
      public Optional<State> applyTo(SemanticGraph tree, State source, SemanticGraphEdge outgoingEdge, SemanticGraphEdge subjectOrNull, SemanticGraphEdge objectOrNull) {
        return Optional.of(new State(
            outgoingEdge,
            subjectOrNull == null ? source.subjectOrNull : subjectOrNull,
            subjectOrNull == null ? (source.distanceFromSubj + 1) : 0,
            objectOrNull == null ? source.objectOrNull : objectOrNull,
            source.thunk.andThen(toModify -> {
              assert Util.isTree(toModify);
              simpleClause(toModify, outgoingEdge);
              addSubtree(toModify, outgoingEdge.getDependent(), "nsubjpass", tree, outgoingEdge.getGovernor(), Collections.singleton(outgoingEdge));
//              addWord(toModify, outgoingEdge.getDependent(), "auxpass", mockNode(outgoingEdge.getDependent().backingLabel(), "is", "VBZ"));
              assert Util.isTree(toModify);
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
      public boolean prerequisitesMet(SemanticGraph originalTree, SemanticGraphEdge edge) {
        // Don't split into anything but verbs or nouns
        char tag = edge.getDependent().tag().charAt(0);
        if (tag != 'V' && tag != 'N') { return false; }
        for (SemanticGraphEdge grandchild : originalTree.outgoingEdgeIterable(edge.getDependent())) {
          if (grandchild.getRelation().toString().contains("subj")) { return false; }
        }
        return true;
      }

      @Override
      public Optional<State> applyTo(SemanticGraph tree, State source, SemanticGraphEdge outgoingEdge, SemanticGraphEdge subjectOrNull, SemanticGraphEdge objectOrNull) {
        if (subjectOrNull != null && !outgoingEdge.equals(subjectOrNull)) {
          return Optional.of(new State(
              outgoingEdge,
              subjectOrNull,
              0,
              objectOrNull == null ? source.objectOrNull : objectOrNull,
              source.thunk.andThen(toModify -> {
                assert Util.isTree(toModify);
                simpleClause(toModify, outgoingEdge);
                addSubtree(toModify, outgoingEdge.getDependent(), "nsubj", tree,
                    subjectOrNull.getDependent(), Collections.singleton(outgoingEdge));
                assert Util.isTree(toModify);
                stripAuxMark(toModify);
                assert Util.isTree(toModify);
              }), false
          ));
        } else {
          return Optional.empty();
        }
      }
    });

    // COPY OBJECT
    actionSpace.add(new Action() {
      @Override
      public String signature() {
        return "clone_obj";
      }

      @Override
      public boolean prerequisitesMet(SemanticGraph originalTree, SemanticGraphEdge edge) {
        // Don't split into anything but verbs or nouns
        char tag = edge.getDependent().tag().charAt(0);
        if (tag != 'V' && tag != 'N') { return false; }
        for (SemanticGraphEdge grandchild : originalTree.outgoingEdgeIterable(edge.getDependent())) {
          if (grandchild.getRelation().toString().contains("subj")) { return false; }
        }
        return true;
      }

      @Override
      public Optional<State> applyTo(SemanticGraph tree, State source, SemanticGraphEdge outgoingEdge, SemanticGraphEdge subjectOrNull, SemanticGraphEdge objectOrNull) {
        if (objectOrNull != null && !outgoingEdge.equals(objectOrNull)) {
          return Optional.of(new State(
              outgoingEdge,
              subjectOrNull == null ? source.subjectOrNull : subjectOrNull,
              subjectOrNull == null ? (source.distanceFromSubj + 1) : 0,
              objectOrNull,
              source.thunk.andThen(toModify -> {
                assert Util.isTree(toModify);
                // Split the clause
                simpleClause(toModify, outgoingEdge);
                // Attach the new subject
                addSubtree(toModify, outgoingEdge.getDependent(), "nsubj", tree,
                    objectOrNull.getDependent(), Collections.singleton(outgoingEdge));
                // Strip bits we don't want
                assert Util.isTree(toModify);
                stripAuxMark(toModify);
                assert Util.isTree(toModify);
              }), false
          ));
        } else {
          return Optional.empty();
        }
      }
    });

    for (IndexedWord root : tree.getRoots()) {
      search(root, candidateFragments, classifier, hardCodedSplits, featurizer, actionSpace, maxTicks);
    }
  }

  /**
   * Re-order the action space based on the specified order of names.
   */
  private static Collection<Action> orderActions(Collection<Action> actionSpace, List<String> order) {
    List<Action> tmp = new ArrayList<>(actionSpace);
    List<Action> out = new ArrayList<>();
    for (String key : order) {
      Iterator<Action> iter = tmp.iterator();
      while (iter.hasNext()) {
        Action a = iter.next();
        if (a.signature().equals(key)) {
          out.add(a);
          iter.remove();
        }
      }
    }
    out.addAll(tmp);
    return out;
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
      Map<String, ? extends List<String>> hardCodedSplits,
      final Function<Triple<State, Action, State>, Counter<String>> featurizer,
      final Collection<Action> actionSpace,
      final int maxTicks
  ) {
    // (the fringe)
    PriorityQueue<Pair<State, List<Counter<String>>>> fringe = new FixedPrioritiesPriorityQueue<>();
    // (avoid duplicate work)
    Set<IndexedWord> seenWords = new HashSet<>();

    State firstState = new State(null, null, -9000, null, x -> {
    }, true);  // First state is implicitly "done"
    fringe.add(Pair.makePair(firstState, new ArrayList<>(0)), -0.0);
    int ticks = 0;

    while (!fringe.isEmpty()) {
      if (++ticks > maxTicks) {
//        log.info("WARNING! Timed out on search with " + ticks + " ticks");
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
              if (newTreeRoot != null && extraEdgesByGovernor.containsKey(newTreeRoot)) {  // what a strange thing to have happen...
                for (SemanticGraphEdge extraEdge : extraEdgesByGovernor.get(newTreeRoot)) {
                  assert Util.isTree(x);
                  //noinspection unchecked
                  addSubtree(x, newTreeRoot, extraEdge.getRelation().toString(), tree, extraEdge.getDependent(), tree.getIncomingEdgesSorted(newTreeRoot));
                  assert Util.isTree(x);
                }
              }
            }
          }).accept(copy);
          return new SentenceFragment(copy, assumedTruth, false);
        }))) {
          break;
        }
      }

      // Find relevant auxiliary terms
      SemanticGraphEdge subjOrNull = null;
      SemanticGraphEdge objOrNull = null;
      for (SemanticGraphEdge auxEdge : tree.outgoingEdgeIterable(rootWord)) {
        String relString = auxEdge.getRelation().toString();
        if (relString.contains("obj")) {
          objOrNull = auxEdge;
        } else if (relString.contains("subj")) {
          subjOrNull = auxEdge;
        }
      }

      // Iterate over children
      // For each outgoing edge...
      for (SemanticGraphEdge outgoingEdge : tree.outgoingEdgeIterable(rootWord)) {
        // Prohibit indirect speech verbs from splitting off clauses
        // (e.g., 'said', 'think')
        // This fires if the governor is an indirect speech verb, and the outgoing edge is a ccomp
        if ( outgoingEdge.getRelation().toString().equals("ccomp") &&
             ( (outgoingEdge.getGovernor().lemma() != null && INDIRECT_SPEECH_LEMMAS.contains(outgoingEdge.getGovernor().lemma())) ||
                INDIRECT_SPEECH_LEMMAS.contains(outgoingEdge.getGovernor().word())) ) {
          continue;
        }
        // Get some variables
        String outgoingEdgeRelation = outgoingEdge.getRelation().toString();
        List<String> forcedArcOrder = hardCodedSplits.get(outgoingEdgeRelation);
        if (forcedArcOrder == null && outgoingEdgeRelation.contains(":")) {
          forcedArcOrder = hardCodedSplits.get(outgoingEdgeRelation.substring(0, outgoingEdgeRelation.indexOf(':')) + ":*");
        }
        boolean doneForcedArc = false;
        // For each action...
        for (Action action : (forcedArcOrder == null ? actionSpace : orderActions(actionSpace, forcedArcOrder))) {
          // Check the prerequisite
          if (!action.prerequisitesMet(tree, outgoingEdge)) {
            continue;
          }
          if (forcedArcOrder != null && doneForcedArc) {
            break;
          }
          // 1. Compute the child state
          Optional<State> candidate = action.applyTo(tree, lastState,
              outgoingEdge, subjOrNull,
              objOrNull);
          if (candidate.isPresent()) {
            double logProbability;
            ClauseClassifierLabel bestLabel;
            Counter<String> features = featurizer.apply(Triple.makeTriple(lastState, action, candidate.get()));
            if (forcedArcOrder != null && !doneForcedArc) {
              logProbability = 0.0;
              bestLabel = ClauseClassifierLabel.CLAUSE_SPLIT;
              doneForcedArc = true;
            } else if (features.containsKey("__undocumented_junit_no_classifier")) {
              logProbability = Double.NEGATIVE_INFINITY;
              bestLabel = ClauseClassifierLabel.CLAUSE_INTERM;
            } else {
              Counter<ClauseClassifierLabel> scores = classifier.scoresOf(new RVFDatum<>(features));
              if (scores.size() > 0) {
                Counters.logNormalizeInPlace(scores);
              }
              String rel = outgoingEdge.getRelation().toString();
              if ("nsubj".equals(rel) || "obj".equals(rel)) {
                scores.remove(ClauseClassifierLabel.NOT_A_CLAUSE);  // Always at least yield on nsubj and dobj
              }
              logProbability = Counters.max(scores, Double.NEGATIVE_INFINITY);
              bestLabel = Counters.argmax(scores, (x, y) -> 0, ClauseClassifierLabel.CLAUSE_SPLIT);
            }

            if (bestLabel != ClauseClassifierLabel.NOT_A_CLAUSE) {
              Pair<State, List<Counter<String>>> childState = Pair.makePair(candidate.get().withIsDone(bestLabel), new ArrayList<Counter<String>>(featuresSoFar) {{
                add(features);
              }});
              // 2. Register the child state
              if (!seenWords.contains(childState.first.edge.getDependent())) {
//            log.info("  pushing " + action.signature() + " with " + argmax.first.edge);
                fringe.add(childState, logProbability);
              }
            }
          }
        }
      }

      seenWords.add(rootWord);
    }
//    log.info("Search finished in " + ticks + " ticks and " + classifierEvals + " classifier evaluations.");
  }



  /**
   * The default featurizer to use during training.
   */
  public static final Featurizer DEFAULT_FEATURIZER = new Featurizer() {
    private static final long serialVersionUID = 4145523451314579506L;
    @Override
    public boolean isSimpleSplit(Counter<String> feats) {
      for (String key : feats.keySet()) {
        if (key.startsWith("simple&")) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Counter<String> apply(Triple<State, Action, State> triple) {
      // Variables
      State from = triple.first;
      Action action = triple.second;
      State to = triple.third;
      String signature = action.signature();
      String edgeRelTaken = to.edge == null ? "root" : to.edge.getRelation().toString();
      String edgeRelShort = to.edge == null ?  "root"  : to.edge.getRelation().getShortName();
      if (edgeRelShort.contains("_")) {
        edgeRelShort = edgeRelShort.substring(0, edgeRelShort.indexOf('_'));
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
          lastRelShort = lastRelShort.substring(0, lastRelShort.indexOf('_'));
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
    }
  };

}
