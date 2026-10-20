package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;

/**
 * Static utility methods for Ssurgeon edits which renumber nodes in a graph.
 */
public class SsurgeonUtils {
  /** Creates a SsurgeonUtils.  All of the methods are static, so there is no need to. */
  public SsurgeonUtils() { }

  // TODO: make the updating of named nodes & edges faster,
  // possibly by building a cache from node/edge to name?
  /**
   * Replaces word in the graph with a new IndexedWord at newIndex,
   * as {@link #moveNode(SemgrexGraphs, SemgrexMatcher, IndexedWord, int)} does
   * for a sentence with only this graph.
   *
   * @param sg the graph to edit
   * @param sm the matcher whose named nodes and edges are updated
   * @param word the node to move
   * @param newIndex the index for the node
   */
  public static void moveNode(SemanticGraph sg, SemgrexMatcher sm, IndexedWord word, int newIndex) {
    moveNode(new SemgrexGraphs(sg), sm, word, newIndex);
  }

  /**
   * Replaces word, in every graph of the sentence which has it, with a new
   * IndexedWord at newIndex.
   *<br>
   * The new node is the same IndexedWord in every graph, and shares word's
   * backing CoreLabel, so word's index changes as well.  It keeps word's
   * copy count.  In each graph, the edges to and from word are replaced
   * with equivalent edges to and from the new node, and the new node
   * replaces word as a root if word was one.  Any named nodes and edges in
   * sm which referred to word or its edges are updated, each edge in the
   * graph it was found in.  Nothing checks whether newIndex is already in use.
   *<br>
   * Another node backed by the same CoreLabel, such as a copy of word,
   * sees the index change as well, so it has to be moved along with word.
   * {@link #moveNodes(SemgrexGraphs, SemgrexMatcher, Function, Function)}
   * does that, as it moves every node with the indices it selects.
   *
   * @param graphs the graphs of the sentence
   * @param sm the matcher whose named nodes and edges are updated
   * @param word the node to move
   * @param newIndex the index for the node
   * @return the new node
   */
  public static IndexedWord moveNode(SemgrexGraphs graphs, SemgrexMatcher sm, IndexedWord word, int newIndex) {
    Map<IndexedWord, Integer> destinations = new HashMap<>();
    destinations.put(word, newIndex);
    return moveWords(graphs, sm, destinations).get(word);
  }

  /**
   * Moves each node whose index satisfies shouldMove to the index given by
   * destination, as {@link #moveNodes(SemgrexGraphs, SemgrexMatcher, Function, Function)}
   * does for a sentence with only this graph.
   *<br>
   * The nodes are moved together, so the order they are moved in does not
   * matter, and reverse is not used.
   *
   * @param sg the graph to edit
   * @param sm the matcher whose named nodes and edges are updated
   * @param shouldMove given a node's index, whether to move that node
   * @param destination given a node's index, the index to move it to
   * @param reverse not used
   */
  public static void moveNodes(SemanticGraph sg, SemgrexMatcher sm, Function<Integer, Boolean> shouldMove, Function<Integer, Integer> destination, boolean reverse) {
    moveNodes(new SemgrexGraphs(sg), sm, shouldMove, destination);
  }

  /**
   * Moves each node, in every graph of the sentence, whose index satisfies
   * shouldMove to the index given by destination.
   *<br>
   * Every node is moved as {@link #moveNode(SemgrexGraphs, SemgrexMatcher, IndexedWord, int)}
   * moves one, but all at once, so no node is ever left at an index another
   * one is about to leave.  An empty node moves with the word it follows:
   * its index is that word's, and its empty index stays the same, so
   * moving the words from 8 down by one makes 8.1 into 7.1.
   *
   * @param graphs the graphs of the sentence
   * @param sm the matcher whose named nodes and edges are updated
   * @param shouldMove given a node's index, whether to move that node
   * @param destination given a node's index, the index to move it to
   */
  public static void moveNodes(SemgrexGraphs graphs, SemgrexMatcher sm, Function<Integer, Boolean> shouldMove, Function<Integer, Integer> destination) {
    Map<IndexedWord, Integer> destinations = new HashMap<>();
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph graph = graphs.get(name);
      if (graph == null) {
        continue;
      }
      for (IndexedWord word : graph.vertexSet()) {
        if (!destinations.containsKey(word) && shouldMove.apply(word.index())) {
          destinations.put(word, destination.apply(word.index()));
        }
      }
    }
    moveWords(graphs, sm, destinations);
  }

  /** What is needed to put the words moved out of one graph back in */
  private static class RemovedWords {
    final SemanticGraph graph;
    final SemgrexGraphName name;
    /** the new nodes for the words moved out of the graph */
    final List<IndexedWord> newWords = new ArrayList<>();
    /** the edges moved out of the graph */
    final List<SemanticGraphEdge> edges = new ArrayList<>();
    /** the governor and dependent of each new edge, in the order of edges */
    final List<IndexedWord> governors = new ArrayList<>();
    final List<IndexedWord> dependents = new ArrayList<>();
    /** the roots with the moved words replaced, or null if no root moved */
    List<IndexedWord> roots = null;

    RemovedWords(SemanticGraph graph, SemgrexGraphName name) {
      this.graph = graph;
      this.name = name;
    }
  }

  /**
   * Moves each word to its destination in every graph which has it.
   *<br>
   * An IndexedWord caches its hash code, and its index is kept in its
   * CoreLabel, so every word to be moved leaves every graph before any
   * index changes.  Otherwise a graph could be left holding a word whose
   * index, and so whose place in the graph's maps, had changed underneath it.
   * For the same reason, every word is looked up in moved before any
   * index changes, so the lookups compare the words as they were.
   *<br>
   * The work is proportional to the number of words moved, their edges,
   * and the names in sm, so moving one word costs the same however long
   * the sentence is.
   *
   * @return the new node for each word moved, keyed by the old node
   */
  private static Map<IndexedWord, IndexedWord> moveWords(SemgrexGraphs graphs, SemgrexMatcher sm, Map<IndexedWord, Integer> destinations) {
    // the new nodes.  their indices are set once the old nodes are out of every graph
    Map<IndexedWord, IndexedWord> moved = new HashMap<>();
    List<IndexedWord> newWords = new ArrayList<>();
    List<Integer> newIndices = new ArrayList<>();
    for (Map.Entry<IndexedWord, Integer> entry : destinations.entrySet()) {
      IndexedWord word = entry.getKey();
      // the copy count is kept on the IndexedWord rather than the CoreLabel.
      // a pseudo-position, which SemanticGraph.valueOf sets to the old
      // index, would describe where the word used to be, so it is not kept
      IndexedWord newWord = new IndexedWord(word.backingLabel());
      newWord.setCopyCount(word.copyCount());
      moved.put(word, newWord);
      newWords.add(newWord);
      newIndices.add(entry.getValue());
    }

    // take the moved words out of each graph, remembering their edges and roots
    List<RemovedWords> removed = new ArrayList<>();
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph graph = graphs.get(name);
      if (graph == null) {
        continue;
      }
      List<IndexedWord> words = new ArrayList<>();
      boolean rootMoved = false;
      for (IndexedWord word : destinations.keySet()) {
        if (graph.containsVertex(word)) {
          words.add(word);
          rootMoved = rootMoved || graph.isRoot(word);
        }
      }
      if (words.isEmpty()) {
        continue;
      }
      RemovedWords graphWords = new RemovedWords(graph, name);
      for (IndexedWord word : words) {
        graphWords.newWords.add(moved.get(word));
        // an edge between two moved words is kept once, as an outgoing edge of its governor
        graphWords.edges.addAll(graph.outgoingEdgeList(word));
        for (SemanticGraphEdge edge : graph.incomingEdgeList(word)) {
          if (!destinations.containsKey(edge.getGovernor())) {
            graphWords.edges.add(edge);
          }
        }
      }
      for (SemanticGraphEdge edge : graphWords.edges) {
        graphWords.governors.add(moved.getOrDefault(edge.getGovernor(), edge.getGovernor()));
        graphWords.dependents.add(moved.getOrDefault(edge.getDependent(), edge.getDependent()));
      }
      if (rootMoved) {
        graphWords.roots = new ArrayList<>();
        for (IndexedWord root : graph.getRoots()) {
          graphWords.roots.add(moved.getOrDefault(root, root));
        }
      }
      for (IndexedWord word : words) {
        graph.removeVertex(word);
      }
      removed.add(graphWords);
    }

    // the named nodes to rebind
    List<String> nodeNames = new ArrayList<>();
    List<IndexedWord> nodeWords = new ArrayList<>();
    for (String nodeName : sm.getNodeNames()) {
      IndexedWord newWord = moved.get(sm.getNode(nodeName));
      if (newWord != null) {
        nodeNames.add(nodeName);
        nodeWords.add(newWord);
      }
    }

    for (int i = 0; i < newWords.size(); ++i) {
      newWords.get(i).setIndex(newIndices.get(i));
    }

    // put the new nodes and their edges back, giving each named edge the edge now in the graph
    for (RemovedWords graphWords : removed) {
      SemanticGraph graph = graphWords.graph;
      for (IndexedWord newWord : graphWords.newWords) {
        graph.addVertex(newWord);
      }
      for (int i = 0; i < graphWords.edges.size(); ++i) {
        SemanticGraphEdge oldEdge = graphWords.edges.get(i);
        SemanticGraphEdge newEdge = graph.addEdge(graphWords.governors.get(i), graphWords.dependents.get(i),
                                                  oldEdge.getRelation(), oldEdge.getWeight(), oldEdge.isExtra());
        for (String edgeName : sm.getEdgeNames()) {
          if (sm.getEdge(edgeName) == oldEdge && sm.getEdgeGraph(edgeName) == graphWords.name) {
            sm.putNamedEdge(edgeName, newEdge, graphWords.name);
          }
        }
      }
      if (graphWords.roots != null) {
        graph.setRoots(graphWords.roots);
      }
    }

    for (int i = 0; i < nodeNames.size(); ++i) {
      sm.putNode(nodeNames.get(i), nodeWords.get(i));
    }
    return moved;
  }
}
