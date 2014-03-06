package edu.stanford.nlp.semgraph;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import junit.framework.TestCase;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;

/**
 * 
 * @author David McClosky
 */
public class SemanticGraphTest extends TestCase {

  SemanticGraph graph;
  
  @Override
    public void setUp() {
    synchronized(SemanticGraphTest.class) {
      if (graph == null) {
        graph = makeGraph();
      }
    }
  }

  public SemanticGraph makeGraph() {
    Tree tree;

    try {
      tree = new PennTreeReader(new StringReader("(S1 (S (S (S (NP (DT The) (NN CD14) (NN LPS) (NN receptor)) (VP (VBZ is) (, ,) (ADVP (RB however)) (, ,) (ADVP (RB up)) (VP (VBN regulated) (PRN (-LRB- -LRB-) (FRAG (RB not) (ADJP (RB down) (VBN regulated))) (-RRB- -RRB-)) (PP (IN in) (NP (JJ tolerant) (NNS cells)))))) (, ,) (CC and) (S (NP (NN LPS)) (VP (MD can) (, ,) (PP (IN in) (NP (NN fact))) (, ,) (ADVP (RB still)) (VP (VB lead) (PP (TO to) (NP (NP (NN activation)) (PP (IN of) (NP (JJ tolerant) (NNS cells))))) (SBAR (IN as) (S (VP (VBN evidenced) (PP (IN by) (NP (NP (NN mobilization)) (PP (IN of) (NP (DT the) (NN transcription) (NN factor) (NP (NP (JJ nuclear) (NN factor) (NN kappa) (NN B)) (PRN (-LRB- -LRB-) (NP (NN NF-kappa) (NN B)) (-RRB- -RRB-)))))))))))))) (. .)))"),
                                new LabeledScoredTreeFactory()).readTree();
    } catch (IOException e) {
      // the tree should parse correctly
      throw new RuntimeException(e);
    }
    
    return SemanticGraphFactory.makeFromTree(tree, SemanticGraphFactory.Mode.BASIC, true, true);
  }

  public void testShortestPath() {
    
    graph.prettyPrint();
    IndexedWord word1 = graph.getNodeByIndex(10);
    IndexedWord word2 = graph.getNodeByIndex(14);
    System.out.println("word1: " + word1);
    System.out.println("word1: " + word1.hashCode());
    System.out.println("word2: " + word2);
    System.out.println("word2: " + word2.hashCode());
    System.out.println("word eq: " + word1.equals(word2));
    System.out.println("word eq: " + (word1.hashCode() == word2.hashCode()));
    System.out.println("word eq: " + (word1.toString().equals(word2.toString())));

    List<SemanticGraphEdge> edges = 
      graph.getShortestUndirectedPathEdges(word1, word2);
    System.out.println("path: " + edges);
    assertNotNull(edges);

    List<IndexedWord> nodes = 
      graph.getShortestUndirectedPathNodes(word1, word2);
    System.out.println("path: " + nodes);
    assertNotNull(nodes);
    assertEquals(word1, nodes.get(0));
    assertEquals(word2, nodes.get(nodes.size() - 1));

    edges = graph.getShortestUndirectedPathEdges(word1, word1);
    System.out.println("path: " + edges);
    assertNotNull(edges);
    assertEquals(0, edges.size());

    nodes = graph.getShortestUndirectedPathNodes(word1, word1);
    System.out.println("path: " + nodes);
    assertNotNull(nodes);
    assertEquals(1, nodes.size());
    assertEquals(word1, nodes.get(0));
  }
  
  public void testCommonAncestor(){
    IndexedWord word1 = graph.getNodeByIndex(43);
    IndexedWord word2 = graph.getNodeByIndex(44);
    IndexedWord common = graph.getCommonAncestor(word1, word2);
    System.out.println("word1: " + word1);
    System.out.println("word2: " + word2);
    System.out.println("common: " + common);
    System.out.println("common ancestor between  " + word1.value()+"-"+word1.index() + " and " + word2.value()+"-"+word2.index() + " is " + common.value()+"-"+common.index());
    assertEquals(45,common.index());
  }
}
