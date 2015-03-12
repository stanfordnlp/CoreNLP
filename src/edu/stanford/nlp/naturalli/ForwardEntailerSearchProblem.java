package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A particular instance of a search problem for finding entailed sentences.
 * This problem already specifies the options for the search, as well as the sentence to search from.
 *
 * Note, again, that this only searches for deletions and not insertions or mutations.
 *
 * @author Gabor Angeli
 */
public class ForwardEntailerSearchProblem {
  /**
   * The parse of this fragment. The vertices in the parse tree should be a subset
   * (possibly not strict) of the tokens above.
   */
  public final SemanticGraph   parseTree;

  /**
   * The maximum number of ticks top search for. Otherwise, the search will be exhaustive.
   */
  public final int maxTicks;

  /**
   * The maximum number of results to return from a single search.
   */
  public final int maxResults;

  /**
   * The weights to use for entailment.
   */
  public final NaturalLogicWeights weights;

  /**
   * A mapping from the actual index of a vertex in a sentence, to its index in the deletion
   * mask.
   */
  private final byte[] indexToMaskIndex;


  /**
   * A result from the search over possible shortenings of the sentence.
   */
  private static class SearchResult {
    public final SemanticGraph tree;
    public final List<String> deletedEdges;
    public final double confidence;

    private SearchResult(SemanticGraph tree, List<String> deletedEdges, double confidence) {
      this.tree = tree;
      this.deletedEdges = deletedEdges;
      this.confidence = confidence;
    }

    @Override
    public String toString() {
      return StringUtils.join(tree.vertexListSorted().stream().map(IndexedWord::word), " ");
    }
  }

  /**
   * A state in the search, denoting a partial shortening of the sentence.
   */
  private static class SearchState {
    public final long deletionMask;
    public final int currentIndex;
    public final SemanticGraph tree;
    public final String lastDeletedEdge;
    public final SearchState source;
    public final double score;

    private SearchState(long deletionMask, int currentIndex, SemanticGraph tree, String lastDeletedEdge, SearchState source, double score) {
      this.deletionMask = deletionMask;
      this.currentIndex = currentIndex;
      this.tree = tree;
      this.lastDeletedEdge = lastDeletedEdge;
      this.source = source;
      this.score = score;
    }
  }

  /**
   * Create a new search problem, fully specified.
   * @see edu.stanford.nlp.naturalli.ForwardEntailer
   */
  protected ForwardEntailerSearchProblem(SemanticGraph parseTree,
                                         int maxResults, int maxTicks,
                                         NaturalLogicWeights weights
                                      ) {
    this.parseTree = parseTree;
    this.maxResults = maxResults;
    this.maxTicks = maxTicks;
    this.weights = weights;
    List<IndexedWord> vertices = this.parseTree.vertexListSorted();
    indexToMaskIndex = new byte[vertices.get(vertices.size() - 1).index()];
    byte i = 0;
    for (IndexedWord vertex : vertices) {
      indexToMaskIndex[vertex.index() - 1] = i;
      i += 1;
    }
  }


  /**
   * Run a search from this entailer. This will return a list of sentence fragments
   * that are entailed by the original sentence / fragment.
   *
   * @return A list of entailed fragments.
   */
  @SuppressWarnings("unchecked")
  public List<SentenceFragment> search() {
    if (parseTree.vertexSet().size() > 63) {
      return Collections.EMPTY_LIST;
    } else {
      return searchImplementation().stream().map(x -> new SentenceFragment(x.tree, false).changeScore(x.confidence)).collect(Collectors.toList());
    }
  }

  /**
   * The search algorithm, starting with a full sentence and iteratively shortening it to its entailed sentences.
   *
   * @return A list of search results, corresponding to shortenings of the sentence.
   */
  private List<SearchResult> searchImplementation() {
    // Pre-process the tree
    SemanticGraph parseTree = new SemanticGraph(this.parseTree);
    // (remove common determiners)
    List<String> determinerRemovals = new ArrayList<>();
    parseTree.getLeafVertices().stream().filter(vertex -> vertex.word().equalsIgnoreCase("the") || vertex.word().equalsIgnoreCase("a") ||
        vertex.word().equalsIgnoreCase("an")).forEach(vertex -> {
      parseTree.removeVertex(vertex);
      determinerRemovals.add("det");
    });
    // (cut conj_and nodes)
    Set<SemanticGraphEdge> andsToAdd = new HashSet<>();
    for (IndexedWord vertex : parseTree.vertexSet()) {
      if( parseTree.inDegree(vertex) > 1 ) {
        SemanticGraphEdge conjAnd = null;
        for (SemanticGraphEdge edge : parseTree.incomingEdgeIterable(vertex)) {
          if (edge.getRelation().toString().equals("conj_and")) {
            conjAnd = edge;
          }
        }
        if (conjAnd != null) {
          parseTree.removeEdge(conjAnd);
          andsToAdd.add(conjAnd);
        }
      }
    }

    // Outputs
    List<SearchResult> results = new ArrayList<>();
    if (!determinerRemovals.isEmpty()) {
      if (andsToAdd.isEmpty()) {
        double score = Math.pow(weights.deletionProbability("det"), (double) determinerRemovals.size());
        assert !Double.isNaN(score);
        assert !Double.isInfinite(score);
        results.add(new SearchResult(parseTree, determinerRemovals, score));
      } else {
        SemanticGraph treeWithAnds = new SemanticGraph(parseTree);
        for (SemanticGraphEdge and : andsToAdd) {
          treeWithAnds.addEdge(and.getGovernor(), and.getDependent(), and.getRelation(), Double.NEGATIVE_INFINITY, false);
        }
        results.add(new SearchResult(treeWithAnds, determinerRemovals,
            Math.pow(weights.deletionProbability("det"), (double) determinerRemovals.size())));
      }
    }

    // Initialize the search
    List<IndexedWord> topologicalVertices = parseTree.topologicalSort();
    if (topologicalVertices.isEmpty()) {
      return results;
    }
    Stack<SearchState> fringe = new Stack<>();
    fringe.push(new SearchState(0l, 0, parseTree, null, null, 1.0));

    // Start the search
    int numTicks = 0;
    while (!fringe.isEmpty()) {
      // Overhead with popping a node.
      if (numTicks >= maxTicks) { return results; }
      numTicks += 1;
      if (results.size() >= maxResults) { return results; }
      SearchState state = fringe.pop();
      IndexedWord currentWord = topologicalVertices.get(state.currentIndex);

      // Push the case where we don't delete
      int nextIndex = state.currentIndex + 1;
      while (nextIndex < topologicalVertices.size()) {
        IndexedWord nextWord = topologicalVertices.get(nextIndex);
        if (  ((state.deletionMask >>> (indexToMaskIndex[nextWord.index() - 1])) & 0x1l) == 0) {
          fringe.push(new SearchState(state.deletionMask, nextIndex, state.tree, null, state, state.score));
          break;
        } else {
          nextIndex += 1;
        }
      }

      // Check if we can delete this subtree
      boolean canDelete = state.tree.getFirstRoot() != currentWord;
      for (SemanticGraphEdge edge : state.tree.incomingEdgeIterable(currentWord)) {
        Polarity tokenPolarity = Polarity.DEFAULT;
        // Get token information
        CoreLabel token = edge.getDependent().backingLabel();
        tokenPolarity = token.get(NaturalLogicAnnotations.PolarityAnnotation.class);
        if (tokenPolarity == null) {
          tokenPolarity = Polarity.DEFAULT;
        }
        // Get the relation for this deletion
        NaturalLogicRelation lexicalRelation = NaturalLogicRelation.forDependencyDeletion(edge.getRelation().toString());
        NaturalLogicRelation projectedRelation = tokenPolarity.projectLexicalRelation(lexicalRelation);
        // Make sure this is a valid entailment
        if (!projectedRelation.isEntailed) { canDelete = false; }
      }

      if (canDelete) {
        // Register the deletion
        long newMask = state.deletionMask;
        SemanticGraph treeWithDeletions = new SemanticGraph(state.tree);
        for (IndexedWord vertex : state.tree.descendants(currentWord)) {
          treeWithDeletions.removeVertex(vertex);
          newMask |= (0x1l << (indexToMaskIndex[vertex.index() - 1]));
          assert indexToMaskIndex[vertex.index() - 1] < 64;
          assert ((newMask >>> (indexToMaskIndex[vertex.index() - 1])) & 0x1l) == 1;
        }
        SemanticGraph resultTree = new SemanticGraph(treeWithDeletions);
        andsToAdd.stream().filter(edge -> resultTree.containsVertex(edge.getGovernor()) && resultTree.containsVertex(edge.getDependent()))
            .forEach(edge -> resultTree.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), Double.NEGATIVE_INFINITY, false));
        // Compute the score of the sentence
        double newScore = state.score;
        for (SemanticGraphEdge edge : state.tree.incomingEdgeIterable(currentWord)) {
          double multiplier = weights.deletionProbability(edge, state.tree.outgoingEdgeIterable(edge.getGovernor()));
          assert !Double.isNaN(multiplier);
          assert !Double.isInfinite(multiplier);
          newScore *= multiplier;
        }
        // Register the result
        results.add(new SearchResult(resultTree,
            aggregateDeletedEdges(state, state.tree.incomingEdgeIterable(currentWord), determinerRemovals),
            newScore));

        // Push the state with this subtree deleted
        nextIndex = state.currentIndex + 1;
        while (nextIndex < topologicalVertices.size()) {
          IndexedWord nextWord = topologicalVertices.get(nextIndex);
          if (  ((newMask >>> (indexToMaskIndex[nextWord.index() - 1])) & 0x1l) == 0) {
            assert treeWithDeletions.containsVertex(topologicalVertices.get(nextIndex));
            fringe.push(new SearchState(newMask, nextIndex, treeWithDeletions, null, state, newScore));
            break;
          } else {
            nextIndex += 1;
          }
        }
      }
    }

    // Return
    return results;
  }

  /**
   * Backtrace from a search state, collecting all of the deleted edges used to get there.
   * @param state The final search state.
   * @param justDeleted  The edges we have just deleted.
   * @param otherEdges  Other deletions we want to register
   * @return A list of deleted edges for that search state.
   */
  private static List<String> aggregateDeletedEdges(SearchState state, Iterable<SemanticGraphEdge> justDeleted, Iterable<String> otherEdges) {
    List<String> rtn = new ArrayList<>();
    for (SemanticGraphEdge edge : justDeleted) {
      rtn.add(edge.getRelation().toString());
    }
    for (String edge : otherEdges) {
      rtn.add(edge);
    }
    while (state != null) {
      if (state.lastDeletedEdge != null) {
        rtn.add(state.lastDeletedEdge);
      }
      state = state.source;
    }
    return rtn;
  }
}
