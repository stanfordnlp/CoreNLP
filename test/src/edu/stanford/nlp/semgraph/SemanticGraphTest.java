package edu.stanford.nlp.semgraph;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.util.Generics;

/**
 *
 * @author David McClosky
 */
public class SemanticGraphTest {

  private SemanticGraph graph;

  @Before
  public void setUp() {
    graph = makeGraph();
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

  @Test
  public void testRemoveVertex() {
    IndexedWord word = graph.getNodeByIndex(10);
    Assert.assertTrue(graph.containsVertex(word));

    int numNodes = graph.vertexSet().size();
    graph.removeVertex(word);
    Assert.assertEquals(graph.vertexSet().size(), numNodes - 1);
    Assert.assertFalse(graph.containsVertex(word));

    try {
      Set<IndexedWord> desc = graph.descendants(word);
      throw new AssertionError("Expected an UnknownVertexException");
    } catch(UnknownVertexException e) {
      // pass
    }
  }

  @Test
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
    Assert.assertNotNull(edges);

    List<IndexedWord> nodes =
      graph.getShortestUndirectedPathNodes(word1, word2);
    // System.out.println("path: " + nodes);
    Assert.assertNotNull(nodes);
    Assert.assertEquals(word1, nodes.get(0));
    Assert.assertEquals(word2, nodes.get(nodes.size() - 1));

    edges = graph.getShortestUndirectedPathEdges(word1, word1);
    // System.out.println("path: " + edges);
    Assert.assertNotNull(edges);
    Assert.assertEquals(0, edges.size());

    nodes = graph.getShortestUndirectedPathNodes(word1, word1);
    // System.out.println("path: " + nodes);
    Assert.assertNotNull(nodes);
    Assert.assertEquals(1, nodes.size());
    Assert.assertEquals(word1, nodes.get(0));
  }

  @Test
  public void testGetCommonAncestor(){
    IndexedWord common = graph.getCommonAncestor(graph.getNodeByIndex(43), graph.getNodeByIndex(44));
    Assert.assertEquals(45, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(41), graph.getNodeByIndex(39));
    Assert.assertEquals(41, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(39), graph.getNodeByIndex(41));
    Assert.assertEquals(41, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(42));
    Assert.assertEquals(41, common.index());

    // too far for this method
    common = graph.getCommonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(42));
    Assert.assertEquals(null, common);

    common = graph.getCommonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(10));
    Assert.assertEquals(10, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(40));
    Assert.assertEquals(40, common.index());

    // a couple tests at the top of the graph
    common = graph.getCommonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(1));
    Assert.assertEquals(10, common.index());

    common = graph.getCommonAncestor(graph.getNodeByIndex(1), graph.getNodeByIndex(10));
    Assert.assertEquals(10, common.index());
  }

  @Test
  public void testCommonAncestor(){
    Assert.assertEquals(1, graph.commonAncestor(graph.getNodeByIndex(43), graph.getNodeByIndex(44)));

    Assert.assertEquals(1, graph.commonAncestor(graph.getNodeByIndex(41), graph.getNodeByIndex(39)));

    Assert.assertEquals(1, graph.commonAncestor(graph.getNodeByIndex(39), graph.getNodeByIndex(41)));

    Assert.assertEquals(2, graph.commonAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(42)));

    Assert.assertEquals(2, graph.commonAncestor(graph.getNodeByIndex(42), graph.getNodeByIndex(40)));

    // too far for this method
    Assert.assertEquals(-1, graph.commonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(42)));
    // Assert.assertEquals(null, common);

    Assert.assertEquals(0, graph.commonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(10)));

    Assert.assertEquals(0, graph.commonAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(40)));
    // Assert.assertEquals(40, common.index());

    // a couple tests at the top of the graph
    Assert.assertEquals(2, graph.commonAncestor(graph.getNodeByIndex(10), graph.getNodeByIndex(1)));

    Assert.assertEquals(2, graph.commonAncestor(graph.getNodeByIndex(1), graph.getNodeByIndex(10)));
  }

  @Test
  public void testTopologicalSort() {
    SemanticGraph gr = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    verifyTopologicalSort(gr);

    List<IndexedWord> vertices = gr.vertexListSorted();
    gr.addEdge(vertices.get(1), vertices.get(2), UniversalEnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    verifyTopologicalSort(gr);

    gr = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    vertices = gr.vertexListSorted();
    gr.addEdge(vertices.get(2), vertices.get(1), UniversalEnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    verifyTopologicalSort(gr);

    gr = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
    vertices = gr.vertexListSorted();
    gr.addEdge(vertices.get(1), vertices.get(3), UniversalEnglishGrammaticalRelations.DIRECT_OBJECT, 1.0, false);
    verifyTopologicalSort(gr);

    // now create a graph with a directed loop, which we should not
    // be able to topologically sort
    gr = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");
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
      Assert.assertTrue(indices.containsKey(parent));
      int parentIndex = indices.get(parent);
      for (IndexedWord child : graph.getChildren(parent)) {
        Assert.assertTrue(indices.containsKey(child));
        int childIndex = indices.get(child);
        Assert.assertTrue(parentIndex < childIndex);
      }
    }
  }

  @Test
  public void testGetPathToRoot() {
    verifyPath(graph.getPathToRoot(graph.getNodeByIndex(1)), 4, 10);
    verifyPath(graph.getPathToRoot(graph.getNodeByIndex(10))); // empty path
    verifyPath(graph.getPathToRoot(graph.getNodeByIndex(34)), 35, 28, 10);
  }

  private static void verifyPath(List<IndexedWord> path, int ... expected) {
    Assert.assertEquals(expected.length, path.size());
    for (int i = 0; i < expected.length; ++i) {
      Assert.assertEquals(expected[i], path.get(i).index());
    }
  }

  @Test
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
    Assert.assertEquals(expectedIndices, results);
  }

  @Test
  public void testIsAncestor() {
    //System.err.println(graph.toString(CoreLabel.VALUE_TAG_INDEX_FORMAT));
    Assert.assertEquals(1, graph.isAncestor(graph.getNodeByIndex(42), graph.getNodeByIndex(45)));
    Assert.assertEquals(2, graph.isAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(37)));
    Assert.assertEquals(-1, graph.isAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(38)));
    Assert.assertEquals(-1, graph.isAncestor(graph.getNodeByIndex(40), graph.getNodeByIndex(10)));
    Assert.assertEquals(-1, graph.isAncestor(graph.getNodeByIndex(45), graph.getNodeByIndex(42)));
  }

  @Test
  public void testHasChildren() {
    SemanticGraph gr = SemanticGraph.valueOf("[ate subj>Bill obj>[muffins compound>blueberry]]");

    List<IndexedWord> vertices = gr.vertexListSorted();
    for (IndexedWord word : vertices) {
      if (word.word().equals("ate") || word.word().equals("muffins")) {
        Assert.assertTrue(gr.hasChildren(word));
      } else {
        Assert.assertFalse(gr.hasChildren(word));
      }
    }
  }

  /**
   * Test the vertices and edges of a very simple valueOf graph
   */
  @Test
  public void testValueOfSimple() {
    SemanticGraph sg = SemanticGraph.valueOf("[A/foo obj> B/bar obj> C/foo nsubj> [D/bar obj> E/baz]]");

    List<IndexedWord> words = sg.vertexListSorted();
    Assert.assertEquals(words.size(), 5);

    for (int i = 0; i < 5; ++i) {
      Assert.assertEquals(words.get(i).index(), i);
    }
    IndexedWord A = words.get(0);
    IndexedWord B = words.get(1);
    IndexedWord C = words.get(2);
    IndexedWord D = words.get(3);
    IndexedWord E = words.get(4);

    Assert.assertEquals(A.word(), "A");
    Assert.assertEquals(A.tag(),  "foo");
    Assert.assertEquals(B.word(), "B");
    Assert.assertEquals(B.tag(),  "bar");
    Assert.assertEquals(C.word(), "C");
    Assert.assertEquals(C.tag(),  "foo");
    Assert.assertEquals(D.word(), "D");
    Assert.assertEquals(D.tag(),  "bar");
    Assert.assertEquals(E.word(), "E");
    Assert.assertEquals(E.tag(),  "baz");

    Assert.assertEquals(sg.getAllEdges(A, B).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(B, "obj").size(), 1);

    Assert.assertEquals(sg.getAllEdges(A, C).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(C, "obj").size(), 1);

    Assert.assertEquals(sg.getAllEdges(A, D).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(D, "nsubj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(D, "obj").size(), 0);
    Assert.assertEquals(sg.getParentsWithReln(D, "dep").size(), 0);

    Assert.assertEquals(sg.getAllEdges(A, E).size(), 0);
    Assert.assertEquals(sg.getAllEdges(D, E).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(E, "obj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(E, "dep").size(), 0);
  }

  /**
   * Test that dashes as the word work as expected with indices
   */
  @Test
  public void testValueOfDashes() {
    SemanticGraph sg = SemanticGraph.valueOf("[--3 obj> -/bar-1 obj> C-4 nsubj> [D-2 obj> E-0]]");

    List<IndexedWord> words = sg.vertexListSorted();
    Assert.assertEquals(words.size(), 5);

    for (int i = 0; i < 5; ++i) {
      Assert.assertEquals(words.get(i).index(), i);
    }
    IndexedWord A = words.get(3);
    IndexedWord B = words.get(1);
    IndexedWord C = words.get(4);
    IndexedWord D = words.get(2);
    IndexedWord E = words.get(0);

    Assert.assertEquals(A.word(), "-");
    Assert.assertEquals(B.word(), "-");
    Assert.assertEquals(B.tag(),  "bar");
    Assert.assertEquals(C.word(), "C");
    Assert.assertEquals(D.word(), "D");
    Assert.assertEquals(E.word(), "E");
  }

  /**
   * Test the vertices and edges of a very simple valueOf graph with indices added
   */
  @Test
  public void testValueOfIndices() {
    // test some with tags and some without
    SemanticGraph sg = SemanticGraph.valueOf("[A/foo-3 obj> B/bar-1 obj> C-4 nsubj> [D-2 obj> E-0]]");

    List<IndexedWord> words = sg.vertexListSorted();
    Assert.assertEquals(words.size(), 5);

    for (int i = 0; i < 5; ++i) {
      Assert.assertEquals(words.get(i).index(), i);
    }
    IndexedWord A = words.get(3);
    IndexedWord B = words.get(1);
    IndexedWord C = words.get(4);
    IndexedWord D = words.get(2);
    IndexedWord E = words.get(0);

    Assert.assertEquals(A.word(), "A");
    Assert.assertEquals(A.tag(),  "foo");
    Assert.assertEquals(B.word(), "B");
    Assert.assertEquals(B.tag(),  "bar");
    Assert.assertEquals(C.word(), "C");
    Assert.assertEquals(D.word(), "D");
    Assert.assertEquals(E.word(), "E");

    Assert.assertEquals(sg.getAllEdges(A, B).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(B, "obj").size(), 1);

    Assert.assertEquals(sg.getAllEdges(A, C).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(C, "obj").size(), 1);

    Assert.assertEquals(sg.getAllEdges(A, D).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(D, "nsubj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(D, "obj").size(), 0);
    Assert.assertEquals(sg.getParentsWithReln(D, "dep").size(), 0);

    Assert.assertEquals(sg.getAllEdges(A, E).size(), 0);
    Assert.assertEquals(sg.getAllEdges(D, E).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(E, "obj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(E, "dep").size(), 0);
  }

  /**
   * Test the vertices and edges of a very simple valueOf graph with indices added
   */
  @Test
  public void testValueOfEmptyIndices() {
    // test some with tags and some without
    SemanticGraph sg = SemanticGraph.valueOf("[A/foo-2 obj> B/bar-1 obj> C-1.2 nsubj> [D-1.1 obj> E-0]]");

    List<IndexedWord> words = sg.vertexListSorted();
    Assert.assertEquals(words.size(), 5);
    IndexedWord A = words.get(4);
    IndexedWord B = words.get(1);
    IndexedWord C = words.get(3);
    IndexedWord D = words.get(2);
    IndexedWord E = words.get(0);

    Assert.assertEquals(A.word(), "A");
    Assert.assertEquals(A.tag(),  "foo");
    Assert.assertEquals(B.word(), "B");
    Assert.assertEquals(B.tag(),  "bar");
    Assert.assertEquals(C.word(), "C");
    Assert.assertEquals(D.word(), "D");
    Assert.assertEquals(E.word(), "E");

    Assert.assertEquals(sg.getAllEdges(A, B).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(B, "obj").size(), 1);

    Assert.assertEquals(sg.getAllEdges(A, C).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(C, "obj").size(), 1);

    Assert.assertEquals(sg.getAllEdges(A, D).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(D, "nsubj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(D, "obj").size(), 0);
    Assert.assertEquals(sg.getParentsWithReln(D, "dep").size(), 0);

    Assert.assertEquals(sg.getAllEdges(A, E).size(), 0);
    Assert.assertEquals(sg.getAllEdges(D, E).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(E, "obj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(E, "dep").size(), 0);    

    Assert.assertEquals(A.index(), 2);
    Assert.assertEquals(A.getEmptyIndex(), 0);
    Assert.assertEquals(B.index(), 1);
    Assert.assertEquals(B.getEmptyIndex(), 0);
    Assert.assertEquals(C.index(), 1);
    Assert.assertEquals(C.getEmptyIndex(), 2);
    Assert.assertEquals(D.index(), 1);
    Assert.assertEquals(D.getEmptyIndex(), 1);
    Assert.assertEquals(E.index(), 0);
    Assert.assertEquals(E.getEmptyIndex(), 0);
  }

  /**
   * Test the vertices and edges if we reuse some indices in valueOf
   */
  @Test
  public void testValueOfReuseIndices() {
    SemanticGraph sg = SemanticGraph.valueOf("[A/foo-0 obj> B/bar-1 obj> C/foo-2 obj> -2 dep> B/bar-1 nsubj> [D/bar-3 obj> E/baz-4]]");

    List<IndexedWord> words = sg.vertexListSorted();
    Assert.assertEquals(words.size(), 5);

    for (int i = 0; i < 5; ++i) {
      Assert.assertEquals(words.get(i).index(), i);
    }
    IndexedWord A = words.get(0);
    IndexedWord B = words.get(1);
    IndexedWord C = words.get(2);
    IndexedWord D = words.get(3);
    IndexedWord E = words.get(4);

    Assert.assertEquals(A.word(), "A");
    Assert.assertEquals(A.tag(),  "foo");
    Assert.assertEquals(B.word(), "B");
    Assert.assertEquals(B.tag(),  "bar");
    Assert.assertEquals(C.word(), "C");
    Assert.assertEquals(C.tag(),  "foo");
    Assert.assertEquals(D.word(), "D");
    Assert.assertEquals(D.tag(),  "bar");
    Assert.assertEquals(E.word(), "E");
    Assert.assertEquals(E.tag(),  "baz");

    Assert.assertEquals(sg.getAllEdges(A, B).size(), 2);
    Assert.assertEquals(sg.getParentsWithReln(B, "obj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(B, "dep").size(), 1);

    Assert.assertEquals(sg.getAllEdges(A, C).size(), 2);
    Assert.assertEquals(sg.getParentsWithReln(C, "obj").size(), 1);

    Assert.assertEquals(sg.getAllEdges(A, D).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(D, "nsubj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(D, "obj").size(), 0);
    Assert.assertEquals(sg.getParentsWithReln(D, "dep").size(), 0);

    Assert.assertEquals(sg.getAllEdges(A, E).size(), 0);
    Assert.assertEquals(sg.getAllEdges(D, E).size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(E, "obj").size(), 1);
    Assert.assertEquals(sg.getParentsWithReln(E, "dep").size(), 0);

    Assert.assertEquals(A.index(), 0);
    Assert.assertEquals(B.index(), 1);
    Assert.assertEquals(C.index(), 2);
    Assert.assertEquals(D.index(), 3);
    Assert.assertEquals(E.index(), 4);
  }

  @Test
  public void testXMLString() {
    SemanticGraph sg = SemanticGraph.valueOf("[A/foo-0 obj> B/bar-1 obj> C/foo-2 obj> -2 dep> B/bar-1 nsubj> [D/bar-3 obj> E/baz-4]]");
    String text = sg.toString(SemanticGraph.OutputFormat.XML);
    String expected = ("<dependencies style=\"typed\">\n" +
                       "  <dep type=\"dep\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"1\">B</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"obj\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"1\">B</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"obj\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"2\">C</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"obj\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"2\">C</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"nsubj\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"3\">D</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"obj\">\n" +
                       "    <governor idx=\"3\">D</governor>\n" +
                       "    <dependent idx=\"4\">E</dependent>\n" +
                       "  </dep>\n" +
                       "</dependencies>\n");

    Assert.assertEquals(text, expected);
  }

  @Test
  public void testXMLStringWithEmpties() {
    SemanticGraph sg = SemanticGraph.valueOf("[A/foo-0 obj> B/bar-1.1 obj> C/foo-2 obj> -2 dep> B/bar-1 nsubj> [D/bar-3 obj> E/baz-4]]");
    String text = sg.toString(SemanticGraph.OutputFormat.XML);
    String expected = ("<dependencies style=\"typed\">\n" +
                       "  <dep type=\"dep\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"1\">B</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"obj\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"1\" emptyIdx=\"1\">B</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"obj\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"2\">C</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"obj\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"2\">C</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"nsubj\">\n" +
                       "    <governor idx=\"0\">A</governor>\n" +
                       "    <dependent idx=\"3\">D</dependent>\n" +
                       "  </dep>\n" +
                       "  <dep type=\"obj\">\n" +
                       "    <governor idx=\"3\">D</governor>\n" +
                       "    <dependent idx=\"4\">E</dependent>\n" +
                       "  </dep>\n" +
                       "</dependencies>\n");

    Assert.assertEquals(text, expected);
  }
}
