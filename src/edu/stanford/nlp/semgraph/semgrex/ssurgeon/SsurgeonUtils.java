package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
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
   * Replaces word in the graph with a new IndexedWord at newIndex.
   * The new node shares word's backing CoreLabel, so word's index changes as well.
   * The edges to and from word are replaced with equivalent edges to and from the
   * new node, the new node replaces word as a root if word was a root, and any
   * named nodes and edges in sm which referred to word or its edges are updated.
   * Nothing checks whether newIndex is already in use.
   *
   * @param sg the graph to edit
   * @param sm the matcher whose named nodes and edges are updated
   * @param word the node to move
   * @param newIndex the index for the node
   */
  public static void moveNode(SemanticGraph sg, SemgrexMatcher sm, IndexedWord word, int newIndex) {
    List<SemanticGraphEdge> outgoing = sg.outgoingEdgeList(word);
    List<SemanticGraphEdge> incoming = sg.incomingEdgeList(word);
    boolean isRoot = sg.isRoot(word);
    sg.removeVertex(word);

    IndexedWord newWord = new IndexedWord(word.backingLabel());
    newWord.setIndex(newIndex);

    // could be more expensive than necessary if we move multiple roots,
    // but the expectation is there is usually only the 1 root
    if (isRoot) {
      Set<IndexedWord> newRoots = new HashSet<>(sg.getRoots());
      newRoots.remove(word);
      newRoots.add(newWord);
      sg.setRoots(newRoots);
    }

    for (String name : sm.getNodeNames()) {
      if (sm.getNode(name) == word) {
        sm.putNode(name, newWord);
      }
    }

    for (SemanticGraphEdge oldEdge : outgoing) {
      SemanticGraphEdge newEdge = new SemanticGraphEdge(newWord, oldEdge.getTarget(), oldEdge.getRelation(), oldEdge.getWeight(), oldEdge.isExtra());

      for (String name : sm.getEdgeNames()) {
        if (sm.getEdge(name) == oldEdge) {
          sm.putNamedEdge(name, newEdge);
        }
      }

      sg.addEdge(newEdge);
    }

    for (SemanticGraphEdge oldEdge : incoming) {
      SemanticGraphEdge newEdge = new SemanticGraphEdge(oldEdge.getSource(), newWord, oldEdge.getRelation(), oldEdge.getWeight(), oldEdge.isExtra());

      for (String name : sm.getEdgeNames()) {
        if (sm.getEdge(name) == oldEdge) {
          sm.putNamedEdge(name, newEdge);
        }
      }

      sg.addEdge(newEdge);
    }
  }

  /**
   * Moves each node whose index satisfies shouldMove to the index given by destination,
   * using {@link #moveNode}.
   * <br>
   * reverse: operate in reverse order, highest index to first.  You want true if moving indices up, false if moving indices down
   *
   * @param sg the graph to edit
   * @param sm the matcher whose named nodes and edges are updated
   * @param shouldMove given a node's index, whether to move that node
   * @param destination given a node's index, the index to move it to
   * @param reverse whether to move the nodes from the highest index to the lowest
   */
  public static void moveNodes(SemanticGraph sg, SemgrexMatcher sm, Function<Integer, Boolean> shouldMove, Function<Integer, Integer> destination, boolean reverse) {
    // iterate first, then move, so that we don't screw up the graph while iterating
    List<IndexedWord> toMove = sg.vertexSet().stream().filter(x -> shouldMove.apply(x.index())).collect(Collectors.toList());
    Collections.sort(toMove);
    if (reverse) {
      Collections.reverse(toMove);
    }
    for (IndexedWord word : toMove) {
      moveNode(sg, sm, word, destination.apply(word.index()));
    }
  }
}
