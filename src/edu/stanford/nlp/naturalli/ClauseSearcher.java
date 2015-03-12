package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.PriorityQueue;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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
  }

  public static interface Action {
    public String signature();

    public Optional<State> applyTo(SemanticGraph tree, State source,
                         SemanticGraphEdge outgoingEdge,
                         SemanticGraphEdge subjectOrNull,
                         SemanticGraphEdge ppOrNull);
  }

  public ClauseSearcher(SemanticGraph tree) {
    this.tree = new SemanticGraph(tree);
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
    for (SemanticGraphEdge edge : extraEdges) {
      extraEdgesByGovernor.get(edge.getGovernor()).add(edge);
    }
  }


  /**
   * Fix some bizarre peculiarities with certain trees.
   * So far, these include:
   * <ul>
   *   <li>Sometimes there's a node from a word to itself. This seems wrong.</li>
   * </ul>
   *
   * @param tree The tree to clean (in place!).
   *
   * @return A list of extra edges, which are valid but were removed.
   */
  private static List<SemanticGraphEdge> cleanTree(SemanticGraph tree) {
    // Clean nodes
    List<IndexedWord> toDelete = new ArrayList<>();
    for (IndexedWord vertex : tree.vertexSet()) {
      // Clean punctuation
      char tag = vertex.backingLabel().tag().charAt(0);
      if (tag == '.' || tag == ',' || tag == '(' || tag == ')' || tag == ':') {
        toDelete.add(vertex);
      }
    }
    for (IndexedWord v : toDelete) { tree.removeVertex(v); }

    // Clean edges
    Iterator<SemanticGraphEdge> iter = tree.edgeIterable().iterator();
    while (iter.hasNext()) {
      SemanticGraphEdge edge = iter.next();
      if (edge.getDependent().index() == edge.getGovernor().index()) {
        // Clean self-edges
        iter.remove();
      } else if (edge.getRelation().toString().equals("punct")) {
        // Clean punctuation (again)
        iter.remove();
      }
    }

    // Remove extra edges
    List<SemanticGraphEdge> extraEdges = new ArrayList<>();
    for (SemanticGraphEdge edge : tree.edgeIterable()) {
      if (edge.isExtra()) {
        extraEdges.add(edge);
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
    Queue<IndexedWord> fringe = new LinkedList<>();
    Collection<IndexedWord> wordsToAdd = new ArrayList<>();
    Collection<SemanticGraphEdge> edgesToAdd = new ArrayList<>();
    // Search for subtree to add
    for (SemanticGraphEdge edge : originalTree.outgoingEdgeIterable(subject)) {
      if (!ignoredEdges.contains(edge)) {
        edgesToAdd.add(edge);
        fringe.add(edge.getDependent());
      }
    }
    while (!fringe.isEmpty()) {
      IndexedWord node = fringe.poll();
      wordsToAdd.add(node);
      for (SemanticGraphEdge edge : originalTree.outgoingEdgeIterable(node)) {
        if (!ignoredEdges.contains(edge)) {
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
      toModify.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
  }

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
   * A dummy action denoting that we're done trying to find a clause.
   */
  private final Action STOP = new Action() {
    @Override
    public String signature() {
      return "$STOP$";
    }
    @Override
    public Optional<State> applyTo(SemanticGraph tree, State source, SemanticGraphEdge outgoingEdge, SemanticGraphEdge subjectOrNull, SemanticGraphEdge ppOrNull) {
      return Optional.of(new State(source, true));
    }
  };

  public void search(
                     // The output specs
                     final Consumer<Triple<Double,Counter<String>,Supplier<SentenceFragment>>> candidateFragments,
                     // The learning specs
                     final Counter<String> weights,
                     final Function<Triple<State, Action, State>, Counter<String>> featurizer
                     ) {
    Collection<Action> actionSpace = new ArrayList<>();
    actionSpace.add(STOP);

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
            source.thunk.andThen(toModify -> simpleClause(toModify, outgoingEdge)), false
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
              simpleClause(toModify, outgoingEdge);
              addSubtree(toModify, outgoingEdge.getDependent(), "nsubjpass", tree, outgoingEdge.getGovernor(), Collections.singleton(outgoingEdge));
//              addWord(toModify, outgoingEdge.getDependent(), "auxpass", mockNode(outgoingEdge.getDependent().backingLabel(), "is", "VBZ"));
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
                simpleClause(toModify, outgoingEdge);
                addSubtree(toModify, outgoingEdge.getDependent(), "nsubj", tree,
                    subjectOrNull.getDependent(), Collections.singleton(outgoingEdge));
              }), true
          ));
        } else {
          return Optional.empty();
        }
      }
    });

    for (IndexedWord root : tree.getRoots()) {
      search(root, candidateFragments, weights, featurizer, actionSpace);
    }
  }

  public void search(
                     // The root to search from
                     IndexedWord root,
                     // The output specs
                     final Consumer<Triple<Double,Counter<String>,Supplier<SentenceFragment>>> candidateFragments,
                     // The learning specs
                     final Counter<String> weights,
                     final Function<Triple<State, Action, State>, Counter<String>> featurizer,
                     final Collection<Action> actionSpace
                     ) {
    // (the fringe)
    PriorityQueue<Pair<State, Counter<String>>> fringe = new FixedPrioritiesPriorityQueue<>();
    // (a helper list)
    List<SemanticGraphEdge> ppEdges = new ArrayList<>();

    State firstState = new State(null, null, -9000, null, x -> { }, false);
    fringe.add(Pair.makePair(firstState, new ClassicCounter<>()), -0.0);

    while (!fringe.isEmpty()) {
      // Useful variables
      double logProbSoFar = fringe.getPriority();
      Pair<State, Counter<String>> lastStatePair = fringe.removeFirst();
      State lastState = lastStatePair.first;
      Counter<String> featuresSoFar = lastStatePair.second;
      IndexedWord rootWord = lastState.edge == null ? root : lastState.edge.getDependent();

      // Register thunk
      if (lastState.isDone) {
        candidateFragments.accept(Triple.makeTriple(logProbSoFar, featuresSoFar, () -> {
          SemanticGraph copy = new SemanticGraph(tree);
          lastState.thunk.andThen( x -> {
            // Add the extra edges back in, if they don't break the tree-ness of the extraction
            for (IndexedWord newTreeRoot : x.getRoots()) {
              for (SemanticGraphEdge extraEdge : extraEdgesByGovernor.get(newTreeRoot)) {
                if (!x.containsVertex(extraEdge.getDependent())) {
                  //noinspection unchecked
                  addSubtree(x, newTreeRoot, extraEdge.getRelation().toString(), tree, extraEdge.getDependent(), tree.getIncomingEdgesSorted(newTreeRoot));
                }
              }
            }
          }).accept(copy);
          return new SentenceFragment(copy, false);
        }));
      } else {

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
          if (action == STOP) {
            // Special case the STOP action
            State candidate = action.applyTo(tree, lastState,
                lastState.edge, lastState.subjectOrNull, lastState.ppOrNull).get();
            Counter<String> features = featurizer.apply(Triple.makeTriple(lastState, action, candidate));
            features.addAll(featuresSoFar);
            double probability = SloppyMath.sigmoid(Counters.dotProduct(features, weights));
            fringe.add(Pair.makePair(candidate, features), Math.log(probability));
          } else {
            // All other actions can apply to any edge
            for (SemanticGraphEdge outgoingEdge : tree.outgoingEdgeIterable(rootWord)) {
              // For each outgoing edge...
              // 1. Find the best aux information to carry along
              double max = Double.NEGATIVE_INFINITY;
              Pair<State, Counter<String>> argmax = null;
              for (SemanticGraphEdge ppEdgeOrNull : ppEdges) {
                Optional<State> candidate = action.applyTo(tree, lastState,
                    outgoingEdge, subjOrNull,
                    ppEdgeOrNull);
                if (candidate.isPresent()) {
                  Counter<String> features = featurizer.apply(Triple.makeTriple(lastState, action, candidate.get()));
                  double probability = SloppyMath.sigmoid(Counters.dotProduct(features, weights));
                  if (probability > max) {
                    max = probability;
                    argmax = Pair.makePair(candidate.get(), features);
                  }
                }
              }
              // 2. Register the child state
              if (argmax != null) {
                argmax.second.addAll(featuresSoFar);
                fringe.add(argmax, Math.log(max));
              }
            }
          }
        }
      }
    }
  }
}
