package edu.stanford.nlp.semgraph;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.trees.*;
import junit.framework.TestCase;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.util.Generics;

/**
 *
 * @author David McClosky
 */
public class SemanticGraphTest extends TestCase {

  private SemanticGraph graph;

  @Override
    public void setUp() {
    synchronized(SemanticGraphTest.class) {
      if (graph == null) {
        graph = makeGraph();
      }
    }
  }

  private static SemanticGraph makeGraph() {
    Tree tree;

    try {
      tree = new PennTreeReader(new StringReader("(S1 (S (S (S (NP (DT The) (NN CD14) (NN LPS) (NN receptor)) (VP (VBZ is) (, ,) (ADVP (RB however)) (, ,) (ADVP (RB up)) (VP (VBN regulated) (PRN (-LRB- -LRB-) (FRAG (RB not) (ADJP (RB down) (VBN regulated))) (-RRB- -RRB-)) (PP (IN in) (NP (JJ tolerant) (NNS cells)))))) (, ,) (CC and) (S (NP (NN LPS)) (VP (MD can) (, ,) (PP (IN in) (NP (NN fact))) (, ,) (ADVP (RB still)) (VP (VB lead) (PP (TO to) (NP (NP (NN activation)) (PP (IN of) (NP (JJ tolerant) (NNS cells))))) (SBAR (IN as) (S (VP (VBN evidenced) (PP (IN by) (NP (NP (NN mobilization)) (PP (IN of) (NP (DT the) (NN transcription) (NN factor) (NP (NP (JJ nuclear) (NN factor) (NN kappa) (NN B)) (PRN (-LRB- -LRB-) (NP (NN NF-kappa) (NN B)) (-RRB- -RRB-)))))))))))))) (. .)))"),
                                new LabeledScoredTreeFactory()).readTree();
    } catch (IOException e) {
      // the tree should parse correctly
      throw new RuntimeException(e);
    }

    return SemanticGraphFactory.makeFromTree(tree, SemanticGraphFactory.Mode.BASIC, GrammaticalStructure.Extras.MAXIMAL);
  }

  public void testShortestPath() {

    //graph.prettyPrint();
    IndexedWord word1 = graph.getNodeByIndex(10);
    IndexedWord word2 = graph.getNodeByIndex(14);
    // System.out.println("word1: " + word1);
    // System.out.println("word1: " + word1.hashCode());
    // System.out.println("word2: " + word2);
    // System.out.println("word2: " + word2.hashCode());
    // System.out.println("word eq: " + word1.equals(word2));
    // System.out.println("word eq: " + (word1.hashCode() == word2.hashCode()));
    // System.out.println("word eq: " + (word1.toString().equals(word2.toString())));

    List<SemanticGraphEdge> edges =
      graph.getShortestUndirectedPathEdges(word1, word2);
    // System.out.println("path: " + edges);
    assertNotNull(edges);

    List<IndexedWord> nodes =
      graph.getShortestUndirectedPathNodes(word1, word2);
    // System.out.println("path: " + nodes);
    assertNotNull(nodes);
    assertEquals(word1, nodes.get(0));
    assertEquals(word2, nodes.get(nodes.size() - 1));

    edges = graph.getShortestUndirectedPathEdges(word1, word1);
    // System.out.println("path: " + edges);
    assertNotNull(edges);
    assertEquals(0, edges.size());

    nodes = graph.getShortestUndirectedPathNodes(word1, word1);
    // System.out.println("path: " + nodes);
    assertNotNull(nodes);
    assertEquals(1, nodes.size());
    assertEquals(word1, nodes.get(0));
  }

  public void testGetCommonAncestor(){
    IndexedWord common = graph.getCommonAncestor(graph.getNodeByIndex(43), graph.getNodeByIndex(44));
    assertEquals(45, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(41), graph.getNodeByIndex(39));
    assertEquals(41, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(39), graph.getNodeByIndex(41));
    assertEquals(41, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(42));
    assertEquals(41, common.index());

    // too far for this method
    common = graph.getCommonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(42));
    assertEquals(null, common);

    common = graph.getCommonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(10));
    assertEquals(10, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(40));
    assertEquals(40, common.index());

    // a couple tests at the top of the graph
    common = graph.getCommonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(1));
    assertEquals(10, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(1), graph.getNodeByIndex(10));
    assertEquals(10, common.index());
  }

  public void testCommonAncestor(){
    assertEquals(1, graph.commonAncestor(graph.getNodeByIndex(43), graph.getNodeByIndex(44)));

    assertEquals(1, graph.commonAncestor(graph.getNodeByIndex(41), graph.getNodeByIndex(39)));

    assertEquals(1, graph.commonAncestor(graph.getNodeByIndex(39), graph.getNodeByIndex(41)));

    assertEquals(2, graph.commonAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(42)));

    assertEquals(2, graph.commonAncestor(graph.getNodeByIndex(42), graph.getNodeByIndex(40)));

    // too far for this method
    assertEquals(-1, graph.commonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(42)));
    // assertEquals(null, common);

    assertEquals(0, graph.commonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(10)));

    assertEquals(0, graph.commonAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(40)));
    // assertEquals(40, common.index());

    // a couple tests at the top of the graph
    assertEquals(2, graph.commonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(1)));

    assertEquals(2, graph.commonAncestor(graph.getNodeByIndex(1), graph.getNodeByIndex(10)));
  }

  public void testTopologicalSort() {
    SemanticGraph gr = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
    verifyTopologicalSort(gr);

    List<IndexedWord> vertices = gr.vertexListSorted();
    gr.addEdge(vertices.get(1), vertices.get(2), UniversalEnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    verifyTopologicalSort(gr);

    gr = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
    vertices = gr.vertexListSorted();
    gr.addEdge(vertices.get(2), vertices.get(1), UniversalEnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    verifyTopologicalSort(gr);

    gr = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
    vertices = gr.vertexListSorted();
    gr.addEdge(vertices.get(1), vertices.get(3), UniversalEnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    verifyTopologicalSort(gr);

    // now create a graph with a directed loop, which we should not
    // be able to topologically sort
    gr = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");
    vertices = gr.vertexListSorted();
    gr.addEdge(vertices.get(3), vertices.get(0), UniversalEnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    try {
      verifyTopologicalSort(gr);
      throw new RuntimeException("Expected to fail");
    } catch (IllegalStateException e) {
      // yay, correctly caught error
    }
  }

  /**
   * Tests that a particular topological sort is correct by verifying
   * for each node that it appears in the sort and all of its children
   * occur later in the sort
   */
  private static void verifyTopologicalSort(SemanticGraph graph) {
    List<IndexedWord> sorted = graph.topologicalSort();

    Map<IndexedWord, Integer> indices = Generics.newHashMap();
    for (int index = 0; index < sorted.size(); ++index) {
      indices.put(sorted.get(index), index);
    }

    for (IndexedWord parent : graph.vertexSet()) {
      assertTrue(indices.containsKey(parent));
      int parentIndex = indices.get(parent);
      for (IndexedWord child : graph.getChildren(parent)) {
        assertTrue(indices.containsKey(child));
        int childIndex = indices.get(child);
        assertTrue(parentIndex < childIndex);
      }
    }
  }

  public void testGetPathToRoot() {
    verifyPath(graph.getPathToRoot(graph.getNodeByIndex(1)), 4, 10);
    verifyPath(graph.getPathToRoot(graph.getNodeByIndex(10))); // empty path
    verifyPath(graph.getPathToRoot(graph.getNodeByIndex(34)), 35, 28, 10);
  }

  private static void verifyPath(List<IndexedWord> path, int ... expected) {
    assertEquals(expected.length, path.size());
    for (int i = 0; i < expected.length; ++i) {
      assertEquals(expected[i], path.get(i).index());
    }
  }

  public void testGetSiblings() {
    verifySet(graph.getSiblings(graph.getNodeByIndex(43)), 42, 44, 48);
    verifySet(graph.getSiblings(graph.getNodeByIndex(10))); // empty set
    verifySet(graph.getSiblings(graph.getNodeByIndex(42)), 43, 44, 48);
  }

  private static void verifySet(Collection<IndexedWord> nodes, int ... expected) {
    Set<Integer> results = Generics.newTreeSet();
    for (IndexedWord node : nodes) {
      results.add(node.index());
    }
    Set<Integer> expectedIndices = Generics.newTreeSet();
    for (Integer index : expected) {
      expectedIndices.add(index);
    }
    assertEquals(expectedIndices, results);
  }

  public void testIsAncestor() {
    //System.err.println(graph.toString(CoreLabel.VALUE_TAG_INDEX_FORMAT));
    assertEquals(1, graph.isAncestor(graph.getNodeByIndex(42), graph.getNodeByIndex(45)));
    assertEquals(2, graph.isAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(37)));
    assertEquals(-1, graph.isAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(38)));
    assertEquals(-1, graph.isAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(10)));
    assertEquals(-1, graph.isAncestor(graph.getNodeByIndex(45), graph.getNodeByIndex(42)));
  }

  public void testHasChildren() {
    SemanticGraph gr = SemanticGraph.valueOf("[ate subj>Bill dobj>[muffins compound>blueberry]]");

    List<IndexedWord> vertices = gr.vertexListSorted();
    for (IndexedWord word : vertices) {
      if (word.word().equals("ate") || word.word().equals("muffins")) {
        assertTrue(gr.hasChildren(word));
      } else {
        assertFalse(gr.hasChildren(word));
      }
    }
  }

}
