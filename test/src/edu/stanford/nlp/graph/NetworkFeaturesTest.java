package edu.stanford.nlp.graph;

import junit.framework.TestCase;

public class NetworkFeaturesTest extends TestCase {
  DirectedMultiGraph<Integer, String> graph = new DirectedMultiGraph<Integer, String>();

  public void setUp() {
    graph.clear();
    graph.add(1, 2, "1->2");
    graph.add(2, 3, "2->3");
    graph.add(1, 4, "1->4");
    // cyclic
    graph.add(4, 1, "4->1");
    graph.add(4, 3, "4->3");
    graph.addVertex(5);
    graph.add(5, 6, "5->6");
    graph.add(6, 7, "6->7");
    graph.addVertex(7);
    graph.addVertex(8);
    graph.add(7, 8, "7->8");
    graph.add(9, 10, "9->10");
    graph.add(10,3,"10->3");
  }

  public void testNumMutualFriends() {
    assertEquals(NetworkFeatures.numMutualFriends(graph, 1, 3, true), 2);
  }
  
  public void testIfPathExists(){
    assertEquals(NetworkFeatures.ifPathExists(graph, 1, 5, 4, true), false);
    assertEquals(NetworkFeatures.ifPathExists(graph, 5, 8, 2, true), false);
    assertEquals(NetworkFeatures.ifPathExists(graph, 5, 8, 3, true), true);
  }
  
  public void testIfCoSupported(){
    assertEquals(NetworkFeatures.ifCoSupported(graph, 1, 3, true),true);
  }
  
  public void testJaccardCoefficient(){
    assertEquals("not equal",NetworkFeatures.jaccardScore(graph, 1 , 3, true), (double)2/3);
    
  }
  
  public void testAdamicAdarScore(){

  }
  
  public void testKatzScore(){
    
  }
  
  
}
