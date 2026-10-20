package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.GrammaticalRelation;

/** Tests moving nodes with {@link SsurgeonUtils} */
public class SsurgeonUtilsTest {

  /** The relations of the edges from gov to dep */
  static List<String> relations(SemanticGraph graph, IndexedWord gov, IndexedWord dep) {
    List<String> relations = new ArrayList<>();
    for (SemanticGraphEdge edge : graph.getAllEdges(gov, dep)) {
      relations.add(edge.getRelation().toString());
    }
    Collections.sort(relations);
    return relations;
  }

  /** Whether the edge is one of the edges in the graph, rather than merely equal to one */
  static boolean inGraph(SemanticGraph graph, SemanticGraphEdge edge) {
    for (SemanticGraphEdge other : graph.getAllEdges(edge.getSource(), edge.getTarget())) {
      if (other == edge) {
        return true;
      }
    }
    return false;
  }

  /** A named edge refers to the edge in the graph after one of its words moves */
  @Test
  public void namedEdgeAfterMove() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate-1 obj> cake-2]");
    SemgrexMatcher matcher = SemgrexPattern.compile("{}=a >obj=e {}=b").matcher(graph);
    assertTrue(matcher.find());

    SsurgeonUtils.moveNode(graph, matcher, matcher.getNode("b"), 5);
    assertEquals(5, matcher.getNode("b").index());
    assertTrue(inGraph(graph, matcher.getEdge("e")));
    assertEquals(5, matcher.getEdge("e").getTarget().index());
  }

  /**
   * When both words of a named edge move, the edge is replaced twice, and
   * the name still refers to the edge in the graph afterwards
   */
  @Test
  public void namedEdgeAfterMovingBothWords() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate-1 obj> cake-2]");
    SemgrexMatcher matcher = SemgrexPattern.compile("{}=a >obj=e {}=b").matcher(graph);
    assertTrue(matcher.find());

    SsurgeonUtils.moveNodes(graph, matcher, x -> x >= 1, x -> x + 1, true);
    SemanticGraphEdge edge = matcher.getEdge("e");
    assertTrue(inGraph(graph, edge));
    assertEquals(2, edge.getSource().index());
    assertEquals(3, edge.getTarget().index());
  }

  /** A copy node keeps its copy count when it moves, rather than becoming equal to the word it copies */
  @Test
  public void copyCountKept() {
    SemanticGraph graph = SemanticGraph.valueOf("[ate-1 obj> cake-2]");
    IndexedWord ate = graph.getNodeByIndex(1);
    IndexedWord cake = graph.getNodeByIndex(2);
    IndexedWord copy = new IndexedWord(new CoreLabel(cake.backingLabel()));
    copy.setCopyCount(1);
    graph.addEdge(ate, copy, GrammaticalRelation.valueOf(Language.UniversalEnglish, "dep"), 1.0, false);
    assertEquals(3, graph.size());

    SemgrexMatcher matcher = SemgrexPattern.compile("{word:ate}").matcher(graph);
    assertTrue(matcher.find());
    SsurgeonUtils.moveNode(graph, matcher, copy, 5);

    assertEquals(3, graph.size());
    IndexedWord moved = null;
    for (IndexedWord word : graph.vertexSet()) {
      if (word.index() == 5) {
        moved = word;
      }
    }
    assertNotNull(moved);
    assertEquals(1, moved.copyCount());
    assertEquals(Collections.singletonList("dep"), relations(graph, ate, moved));
    assertEquals(Collections.singletonList("obj"), relations(graph, ate, cake));
  }
}
