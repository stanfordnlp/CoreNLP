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

public class SsurgeonUtils {
  // TODO: make the updating of named nodes & edges faster,
  // possibly by building a cache from node/edge to name?
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
   * reverse: operate in reverse order, highest index to first.  You want true if moving indices up, false if moving indices down
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
