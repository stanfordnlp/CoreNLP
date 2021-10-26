package edu.stanford.nlp.graph;

import java.util.*;

import edu.stanford.nlp.util.CollectionUtils;
import junit.framework.TestCase;

public class DirectedMultiGraphTest extends TestCase {

  DirectedMultiGraph<Integer, String> graph =
    new DirectedMultiGraph<Integer, String>();

  public void setUp() {
    graph.clear();
    graph.add(1, 2, "1->2");
    graph.add(2, 3, "2->3");
    graph.add(1, 4, "1->4");
    // cyclic
    graph.add(4, 1, "4->1");
    graph.addVertex(5);
    graph.add(5, 6, "5->6");
    graph.add(7, 6, "7->6");
    graph.addVertex(7);
    graph.addVertex(8);
    graph.add(9, 10, "9->10");
  }

  /**
   * Check that the graph's incoming and outgoing edges are consistent.
   */
  public <V, E> void checkGraphConsistency(DirectedMultiGraph<V, E> graph) {
    Map<V, Map<V, List<E>>> incoming = graph.incomingEdges;
    Map<V, Map<V, List<E>>> outgoing = graph.outgoingEdges;

    for (V source : incoming.keySet()) {
      for (V target : incoming.get(source).keySet()) {
        assertTrue(outgoing.containsKey(target));
        assertTrue(outgoing.get(target).containsKey(source));
        assertEquals(incoming.get(source).get(target),
                     outgoing.get(target).get(source));
      }
    }
  }

  public void testNumVertices() {
    System.out.println("Graph is \n" + graph.toString());

    assertEquals(graph.getNumVertices(), 10);
  }

  public void testNumEdges() {
    System.out.println("Graph is \n" + graph.toString());

    assertEquals(graph.getNumEdges(), 7);
  }

  public void testRemove() {
    graph.removeVertex(2);
    System.out.println("after deleting 2\n" + graph.toString());
    assertEquals(graph.getNumVertices(), 9);
    assertEquals(graph.getNumEdges(), 5);
    // vertex 11 doesn't exist in the graph and thus this function should return
    // false
    assertFalse(graph.removeVertex(11));

    setUp();
    assertTrue(graph.removeEdges(2, 3));
    System.out.println("after deleting 2->3 edge\n" + graph.toString());
    assertEquals(graph.getNumVertices(), 10);
    assertEquals(graph.getNumEdges(), 6);
    assertFalse(graph.removeEdges(2, 3));
  }

  public void testDelZeroDegreeNodes() {
    graph.removeVertex(2);
    graph.removeZeroDegreeNodes();
    System.out.println("after deleting 2, and then zero deg nodes \n" + graph.toString());
    assertEquals(graph.getNumVertices(), 7);
    assertEquals(graph.getNumEdges(), 5);
  }

  public void testShortestPathDirectionSensitiveNodes() {
    List<Integer> nodes = graph.getShortestPath(1, 3, true);
    System.out.println("directed path nodes btw 1 and 3 is " + nodes);
    assertEquals(3, nodes.size());
    assertEquals(1, nodes.get(0).intValue());
    assertEquals(2, nodes.get(1).intValue());
    assertEquals(3, nodes.get(2).intValue());

    nodes = graph.getShortestPath(2, 4, true);
    System.out.println("directed path nodes btw 2 and 4 is " + nodes);
    assertEquals(null, nodes);

    nodes = graph.getShortestPath(1, 5, true);
    System.out.println("directed path nodes btw 1 and 5 is " + nodes);
    assertEquals(null, nodes);
  }

  public void testShortedPathDirectionSensitiveEdges() {
    List<String> edges = graph.getShortestPathEdges(1, 3, true);
    System.out.println("directed path edges btw 1 and 3 is " + edges);
    assertEquals(2, edges.size());
    assertEquals("1->2", edges.get(0));
    assertEquals("2->3", edges.get(1));

    edges = graph.getShortestPathEdges(2, 4, true);
    System.out.println("directed path edges btw 2 and 4 is " + edges);
    assertEquals(null, edges);

    edges = graph.getShortestPathEdges(1, 5, true);
    System.out.println("directed path edges btw 1 and 5 is " + edges);
    assertEquals(null, edges);
  }

  public void testShortestPathDirectionInsensitiveNodes() {
    List<Integer> nodes = graph.getShortestPath(1, 3);
    System.out.println("undirected nodes btw 1 and 3 is " + nodes);
    assertEquals(3, nodes.size());
    assertEquals(1, nodes.get(0).intValue());
    assertEquals(2, nodes.get(1).intValue());
    assertEquals(3, nodes.get(2).intValue());

    nodes = graph.getShortestPath(2, 4);
    System.out.println("undirected nodes btw 2 and 4 is " + nodes);
    assertEquals(3, nodes.size());
    assertEquals(2, nodes.get(0).intValue());
    assertEquals(1, nodes.get(1).intValue());
    assertEquals(4, nodes.get(2).intValue());

    nodes = graph.getShortestPath(1, 5);
    System.out.println("undirected nodes btw 1 and 5 is " + nodes);
    assertEquals(null, nodes);
  }

  public void testShortestPathDirectionInsensitiveEdges() {
    List<String> edges = graph.getShortestPathEdges(1, 3, false);
    System.out.println("undirected edges btw 1 and 3 is " + edges);
    assertEquals(2, edges.size());
    assertEquals("1->2", edges.get(0));
    assertEquals("2->3", edges.get(1));

    edges = graph.getShortestPathEdges(2, 4, false);
    System.out.println("undirected edges btw 2 and 4 is " + edges);
    assertEquals(2, edges.size());
    assertEquals("1->2", edges.get(0));
    assertEquals("1->4", edges.get(1));

    edges = graph.getShortestPathEdges(1, 5, false);
    System.out.println("undirected edges btw 2 and 4 is " + edges);
    assertEquals(null, edges);
  }

  public void testConnectedComponents() {

    System.out.println("graph is " + graph.toString());
    Set<Set<Integer>> ccs = new HashSet<>(graph.getConnectedComponents());
    for (Set<Integer> cc : ccs) {
      System.out.println("Connected component: " + cc);
    } 
    Set<Integer> edge1 = new HashSet<>(Arrays.asList(1, 2, 3, 4));
    Set<Integer> edge2 = new HashSet<>(Arrays.asList(5, 6, 7));
    Set<Integer> edge3 = new HashSet<>(Arrays.asList(8));
    Set<Integer> edge4 = new HashSet<>(Arrays.asList(9,10));
    Set<Set<Integer>> expectedCcs = new HashSet<>(Arrays.asList(edge1,edge2,edge3,edge4));
    assertEquals(expectedCcs, ccs);
  }

  public void testEdgesNodes() {
    assertTrue(graph.isEdge(1, 2));
    assertFalse(graph.isEdge(2, 1));
    assertTrue(graph.isNeighbor(2, 1));

    List<String> incomingEdges = graph.getEdges(4, 1);
    assertEquals(CollectionUtils.sorted(incomingEdges),
                 Arrays.asList("4->1"));

    Set<Integer> neighbors = graph.getNeighbors(2);
    assertEquals(CollectionUtils.sorted(neighbors),
                 CollectionUtils.sorted(Arrays.asList(1, 3)));

    Set<Integer> parents = graph.getParents(4);
    assertEquals(CollectionUtils.sorted(parents), CollectionUtils.sorted(Arrays.asList(1)));

    parents = graph.getParents(1);
    assertEquals(CollectionUtils.sorted(parents), CollectionUtils.sorted(Arrays.asList(4)));

    parents = graph.getParents(6);
    assertEquals(CollectionUtils.sorted(parents), CollectionUtils.sorted(Arrays.asList(5,7)));
  }


  public void testAdd() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    assertEquals(0, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    g.addVertex(1);
    assertEquals(1, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    g.addVertex(2);
    assertEquals(2, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    g.add(1, 2, "foo");
    assertEquals(2, g.getNumVertices());
    assertEquals(1, g.getNumEdges());

    g.add(1, 2, "bar");
    assertEquals(2, g.getNumVertices());
    assertEquals(2, g.getNumEdges());

    // repeated adds should not clobber vertices or edges
    g.addVertex(2);
    assertEquals(2, g.getNumVertices());
    assertEquals(2, g.getNumEdges());

    g.addVertex(3);
    assertEquals(3, g.getNumVertices());
    assertEquals(2, g.getNumEdges());

    // regardless of what our overwriting edges policy is, this really
    // ought to be allowed
    g.add(1, 3, "bar");
    assertEquals(3, g.getNumVertices());
    assertEquals(3, g.getNumEdges());

    g.add(2, 3, "foo");
    assertEquals(3, g.getNumVertices());
    assertEquals(4, g.getNumEdges());

    g.add(2, 3, "baz");
    assertEquals(3, g.getNumVertices());
    assertEquals(5, g.getNumEdges());

    g.add(2, 4, "baz");
    assertEquals(4, g.getNumVertices());
    assertEquals(6, g.getNumEdges());
  }

  public void testSmallAddRemove() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    g.add(1, 2, "foo");
    assertEquals(2, g.getNumVertices());
    assertEquals(1, g.getNumEdges());

    assertTrue(g.isEdge(1, 2));
    g.removeEdge(1, 2, "foo");
    assertFalse(g.isEdge(1, 2));

    g.add(1, 2, "foo");
    g.add(1, 2, "bar");
    assertTrue(g.isEdge(1, 2));
    assertEquals(2, g.getNumVertices());
    assertEquals(2, g.getNumEdges());

    g.removeEdge(1, 2, "foo");
    assertTrue(g.isEdge(1, 2));
    assertEquals(2, g.getNumVertices());
    assertEquals(1, g.getNumEdges());

    g.removeEdge(1, 2, "bar");
    assertFalse(g.isEdge(1, 2));
    assertEquals(2, g.getNumVertices());
    assertEquals(0, g.getNumEdges());
  }

  public void testSmallRemoveVertex() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    g.add(1, 2, "foo");
    g.removeVertex(2);
    assertEquals(1, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    g.addVertex(2);
    assertEquals(2, g.getNumVertices());
    assertEquals(0, g.getNumEdges());
    assertFalse(g.isEdge(1, 2));
    assertFalse(g.isEdge(2, 1));
  }

  /**
   * specifically test the method "containsVertex".  if previous tests
   * passed, then containsVertex is the only new thing tested here
   */
  public void testSmallContains() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    g.add(1, 2, "foo");
    assertTrue(g.containsVertex(1));
    assertTrue(g.containsVertex(2));
    assertFalse(g.containsVertex(3));

    g.removeEdge(1, 2, "foo");
    assertTrue(g.containsVertex(1));
    assertTrue(g.containsVertex(2));
    assertFalse(g.containsVertex(3));

    g.removeVertex(2);
    assertEquals(1, g.getNumVertices());
    assertTrue(g.containsVertex(1));
    assertFalse(g.containsVertex(2));
    assertFalse(g.containsVertex(3));
  }

  public void testAddRemove() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    g.add(1, 2, "foo");
    g.add(1, 2, "bar");
    g.addVertex(3);
    g.add(1, 3, "bar");
    g.add(2, 3, "foo");
    g.add(2, 3, "baz");
    g.add(2, 4, "baz");

    assertEquals(4, g.getNumVertices());
    assertEquals(6, g.getNumEdges());

    assertTrue(g.isEdge(2, 3));
    g.removeEdges(2, 3);
    assertFalse(g.isEdge(2, 3));
    assertEquals(4, g.getNumVertices());
    assertEquals(4, g.getNumEdges());

    assertTrue(g.isEdge(1, 2));
    g.removeEdge(1, 2, "foo");
    assertTrue(g.isEdge(1, 2));
    assertEquals(4, g.getNumVertices());
    assertEquals(3, g.getNumEdges());
    g.removeEdge(1, 2, "bar");
    assertFalse(g.isEdge(1, 2));
    assertEquals(4, g.getNumVertices());
    assertEquals(2, g.getNumEdges());

    assertFalse(g.removeEdge(3, 1, "bar"));
    assertEquals(4, g.getNumVertices());
    assertEquals(2, g.getNumEdges());

    assertTrue(g.removeEdge(1, 3, "bar"));
    assertEquals(4, g.getNumVertices());
    assertEquals(1, g.getNumEdges());

    assertFalse(g.removeEdge(2, 4, "arg"));
    assertTrue(g.removeEdge(2, 4, "baz"));
    assertEquals(4, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    assertFalse(g.removeVertex(5));
    assertEquals(4, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    assertTrue(g.removeVertex(4));
    assertEquals(3, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    assertFalse(g.removeVertex(4));
    assertEquals(3, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    List<Integer> vertices = Arrays.asList(3, 4);
    assertTrue(g.removeVertices(vertices));
    assertEquals(2, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    assertFalse(g.removeVertices(vertices));
    assertEquals(2, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    g.clear();
    assertEquals(0, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    // reuse the graph, run some more tests
    g.addVertex(1);
    g.addVertex(2);
    g.add(1, 2, "foo");
    g.add(1, 2, "bar");
    g.addVertex(3);
    g.add(1, 3, "bar");
    g.add(2, 3, "foo");
    g.add(2, 3, "baz");
    g.add(2, 4, "baz");

    assertEquals(4, g.getNumVertices());
    assertEquals(6, g.getNumEdges());

    assertTrue(g.removeVertices(vertices));
    assertEquals(2, g.getNumVertices());
    assertEquals(2, g.getNumEdges());
  }

  public void testAddRemove2() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.clear();
    g.addVertex(1);
    g.addVertex(2);
    g.add(1, 2, "foo");
    g.add(1, 2, "bar");
    g.addVertex(3);
    g.add(1, 3, "bar");
    g.add(2, 3, "foo");
    g.add(2, 3, "baz");
    g.add(2, 4, "baz");

    assertEquals(4, g.getNumVertices());
    assertEquals(6, g.getNumEdges());

    List<Integer> vertices = Arrays.asList(2, 4);
    assertTrue(g.removeVertices(vertices));
    assertEquals(2, g.getNumVertices());
    assertEquals(1, g.getNumEdges());
  }

  public void testAddRemove3() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.clear();
    g.addVertex(1);
    g.addVertex(2);
    g.add(1, 2, "foo");
    g.add(1, 2, "bar");
    g.addVertex(3);
    g.add(1, 3, "bar");
    g.add(2, 3, "foo");
    g.add(2, 3, "baz");
    g.add(2, 4, "baz");
    g.removeEdges(2, 3);
    g.removeEdge(1, 2, "foo");
    g.removeEdge(1, 2, "bar");
    g.removeEdge(1, 3, "bar");
    g.removeEdge(2, 4, "baz");
    assertEquals(4, g.getNumVertices());
    assertEquals(0, g.getNumEdges());
    g.removeVertex(4);
    assertEquals(3, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    List<Integer> vertices = Arrays.asList(2, 4);

    assertTrue(g.removeVertices(vertices));
    assertEquals(2, g.getNumVertices());
    assertEquals(0, g.getNumEdges());

    assertFalse(g.isEmpty());
    g.removeVertex(1);
    assertFalse(g.isEmpty());
    g.removeVertex(3);
    assertTrue(g.isEmpty());
  }

  public void testGetAllVertices() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getAllVertices());
    g.addVertex(2);
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2)),
                 g.getAllVertices());
    g.add(1, 2, "foo");
    g.add(1, 2, "bar");
    g.addVertex(3);
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2, 3)),
                 g.getAllVertices());
    g.add(1, 3, "bar");
    g.add(2, 3, "foo");
    g.add(2, 3, "baz");
    g.add(2, 4, "baz");
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2, 3, 4)),
                 g.getAllVertices());
    g.removeEdges(2, 3);
    g.removeEdge(1, 2, "foo");
    g.removeEdge(1, 2, "bar");
    g.removeEdge(1, 3, "bar");
    g.removeEdge(2, 4, "baz");
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2, 3, 4)),
                 g.getAllVertices());
    g.removeVertex(4);
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2, 3)),
                 g.getAllVertices());
    g.add(1, 4, "blah");
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2, 3, 4)),
                 g.getAllVertices());
    g.removeZeroDegreeNodes();
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 4)),
                 g.getAllVertices());
  }

  /**
   * Test the methods that return the sets of neighbors, parents &amp;
   * children for a variety of add and remove cases
   */
  @SuppressWarnings("AssertEqualsBetweenInconvertibleTypes")
  public void testNeighbors() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(Collections.emptySet(), g.getChildren(1));
    assertEquals(Collections.emptySet(), g.getNeighbors(1));
    assertEquals(null, g.getParents(2));
    assertEquals(null, g.getChildren(2));
    assertEquals(null, g.getNeighbors(2));

    g.addVertex(2);
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(Collections.emptySet(), g.getChildren(1));
    assertEquals(Collections.emptySet(), g.getNeighbors(1));
    assertEquals(Collections.emptySet(), g.getParents(2));
    assertEquals(Collections.emptySet(), g.getChildren(2));
    assertEquals(Collections.emptySet(), g.getNeighbors(2));

    g.add(1, 2, "foo");
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)), g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)), g.getNeighbors(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)), g.getParents(2));
    assertEquals(Collections.emptySet(), g.getChildren(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)), g.getNeighbors(2));

    g.add(1, 2, "bar");
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)), g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)), g.getNeighbors(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)), g.getParents(2));
    assertEquals(Collections.emptySet(), g.getChildren(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)), g.getNeighbors(2));

    g.addVertex(3);
    g.add(1, 3, "bar");
    g.add(2, 3, "foo");
    g.add(2, 3, "baz");
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getNeighbors(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(3)),
                 g.getChildren(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 3)),
                 g.getNeighbors(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2)),
                 g.getParents(3));
    assertEquals(Collections.emptySet(), g.getChildren(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2)),
                 g.getNeighbors(3));

    g.add(2, 4, "baz");
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getNeighbors(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(3, 4)),
                 g.getChildren(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 3, 4)),
                 g.getNeighbors(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2)),
                 g.getParents(3));
    assertEquals(Collections.emptySet(), g.getChildren(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 2)),
                 g.getNeighbors(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getParents(4));
    assertEquals(Collections.emptySet(), g.getChildren(4));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getNeighbors(4));

    g.removeEdges(2, 3);
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getNeighbors(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(4)),
                 g.getChildren(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 4)),
                 g.getNeighbors(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getNeighbors(3));
    assertEquals(Collections.emptySet(), g.getChildren(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getParents(4));
    assertEquals(Collections.emptySet(), g.getChildren(4));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getNeighbors(4));

    g.removeEdge(1, 2, "foo");
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getNeighbors(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(4)),
                 g.getChildren(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 4)),
                 g.getNeighbors(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getNeighbors(3));
    assertEquals(Collections.emptySet(), g.getChildren(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getParents(4));
    assertEquals(Collections.emptySet(), g.getChildren(4));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getNeighbors(4));

    g.removeEdge(1, 2, "bar");
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(3)),
                 g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(3)),
                 g.getNeighbors(1));
    assertEquals(Collections.emptySet(), g.getParents(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(4)),
                 g.getChildren(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(4)),
                 g.getNeighbors(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(3));
    assertEquals(Collections.emptySet(), g.getChildren(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getNeighbors(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getParents(4));
    assertEquals(Collections.emptySet(), g.getChildren(4));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getNeighbors(4));

    g.add(1, 2, "bar");
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(2, 3)),
                 g.getNeighbors(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(4)),
                 g.getChildren(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1, 4)),
                 g.getNeighbors(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getNeighbors(3));
    assertEquals(Collections.emptySet(), g.getChildren(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getParents(4));
    assertEquals(Collections.emptySet(), g.getChildren(4));
    assertEquals(new HashSet<Integer>(Arrays.asList(2)),
                 g.getNeighbors(4));

    g.removeVertex(2);
    assertEquals(Collections.emptySet(), g.getParents(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(3)),
                 g.getChildren(1));
    assertEquals(new HashSet<Integer>(Arrays.asList(3)),
                 g.getNeighbors(1));
    assertEquals(null, g.getParents(2));
    assertEquals(null, g.getChildren(2));
    assertEquals(null, g.getNeighbors(2));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getParents(3));
    assertEquals(Collections.emptySet(), g.getChildren(3));
    assertEquals(new HashSet<Integer>(Arrays.asList(1)),
                 g.getNeighbors(3));
    assertEquals(Collections.emptySet(), g.getParents(4));
    assertEquals(Collections.emptySet(), g.getChildren(4));
    assertEquals(Collections.emptySet(), g.getNeighbors(4));
  }

  public void testIsNeighbor() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    assertFalse(g.isNeighbor(1, 2));
    assertFalse(g.isNeighbor(2, 1));

    g.add(1, 2, "foo");
    assertTrue(g.isNeighbor(1, 2));
    assertTrue(g.isNeighbor(2, 1));

    g.add(1, 2, "bar");
    assertTrue(g.isNeighbor(1, 2));
    assertTrue(g.isNeighbor(2, 1));

    g.addVertex(3);
    assertTrue(g.isNeighbor(1, 2));
    assertTrue(g.isNeighbor(2, 1));
    assertFalse(g.isNeighbor(1, 3));
    assertFalse(g.isNeighbor(3, 1));
    assertFalse(g.isNeighbor(2, 3));
    assertFalse(g.isNeighbor(3, 2));

    g.add(1, 3, "bar");
    assertTrue(g.isNeighbor(1, 2));
    assertTrue(g.isNeighbor(2, 1));
    assertTrue(g.isNeighbor(1, 3));
    assertTrue(g.isNeighbor(3, 1));
    assertFalse(g.isNeighbor(2, 3));
    assertFalse(g.isNeighbor(3, 2));

    g.add(2, 3, "foo");
    g.add(2, 3, "baz");
    assertTrue(g.isNeighbor(1, 2));
    assertTrue(g.isNeighbor(2, 1));
    assertTrue(g.isNeighbor(1, 3));
    assertTrue(g.isNeighbor(3, 1));
    assertTrue(g.isNeighbor(2, 3));
    assertTrue(g.isNeighbor(3, 2));

    g.removeEdge(1, 2, "foo");
    assertTrue(g.isNeighbor(1, 2));
    assertTrue(g.isNeighbor(2, 1));
    assertTrue(g.isNeighbor(1, 3));
    assertTrue(g.isNeighbor(3, 1));
    assertTrue(g.isNeighbor(2, 3));
    assertTrue(g.isNeighbor(3, 2));

    g.removeEdge(1, 2, "bar");
    assertFalse(g.isNeighbor(1, 2));
    assertFalse(g.isNeighbor(2, 1));
    assertTrue(g.isNeighbor(1, 3));
    assertTrue(g.isNeighbor(3, 1));
    assertTrue(g.isNeighbor(2, 3));
    assertTrue(g.isNeighbor(3, 2));

    g.add(1, 2, "foo");
    g.add(1, 2, "bar");
    assertTrue(g.isNeighbor(1, 2));
    assertTrue(g.isNeighbor(2, 1));
    assertTrue(g.isNeighbor(1, 3));
    assertTrue(g.isNeighbor(3, 1));
    assertTrue(g.isNeighbor(2, 3));
    assertTrue(g.isNeighbor(3, 2));

    g.removeEdges(1, 2);
    assertFalse(g.isNeighbor(1, 2));
    assertFalse(g.isNeighbor(2, 1));
    assertTrue(g.isNeighbor(1, 3));
    assertTrue(g.isNeighbor(3, 1));
    assertTrue(g.isNeighbor(2, 3));
    assertTrue(g.isNeighbor(3, 2));

    g.removeVertex(2);
    assertFalse(g.isNeighbor(1, 2));
    assertFalse(g.isNeighbor(2, 1));
    assertTrue(g.isNeighbor(1, 3));
    assertTrue(g.isNeighbor(3, 1));
    assertFalse(g.isNeighbor(2, 3));
    assertFalse(g.isNeighbor(3, 2));
  }

  /**
   * Test the getInDegree() and getOutDegree() methods using a couple
   * different graph shapes
   */
  public void testDegree() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    assertEquals(0, g.getOutDegree(1));
    assertEquals(0, g.getInDegree(1));
    assertEquals(0, g.getOutDegree(2));
    assertEquals(0, g.getInDegree(2));

    g.add(1, 2, "foo");
    assertEquals(1, g.getOutDegree(1));
    assertEquals(0, g.getInDegree(1));
    assertEquals(0, g.getOutDegree(2));
    assertEquals(1, g.getInDegree(2));

    g.add(1, 2, "bar");
    assertEquals(2, g.getOutDegree(1));
    assertEquals(0, g.getInDegree(1));
    assertEquals(0, g.getOutDegree(2));
    assertEquals(2, g.getInDegree(2));

    g.add(1, 3, "foo");
    assertEquals(3, g.getOutDegree(1));
    assertEquals(0, g.getInDegree(1));
    assertEquals(0, g.getOutDegree(2));
    assertEquals(2, g.getInDegree(2));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(1, g.getInDegree(3));

    g.add(2, 3, "foo");
    assertEquals(3, g.getOutDegree(1));
    assertEquals(0, g.getInDegree(1));
    assertEquals(1, g.getOutDegree(2));
    assertEquals(2, g.getInDegree(2));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(2, g.getInDegree(3));

    g.removeVertex(2);
    assertEquals(1, g.getOutDegree(1));
    assertEquals(0, g.getInDegree(1));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(1, g.getInDegree(3));

    g.add(2, 1, "foo");
    assertEquals(1, g.getOutDegree(1));
    assertEquals(1, g.getInDegree(1));
    assertEquals(1, g.getOutDegree(2));
    assertEquals(0, g.getInDegree(2));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(1, g.getInDegree(3));

    g.add(2, 1, "bar");
    assertEquals(1, g.getOutDegree(1));
    assertEquals(2, g.getInDegree(1));
    assertEquals(2, g.getOutDegree(2));
    assertEquals(0, g.getInDegree(2));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(1, g.getInDegree(3));

    g.add(2, 1, "baz");
    assertEquals(1, g.getOutDegree(1));
    assertEquals(3, g.getInDegree(1));
    assertEquals(3, g.getOutDegree(2));
    assertEquals(0, g.getInDegree(2));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(1, g.getInDegree(3));

    g.removeEdge(2, 1, "blah");
    assertEquals(1, g.getOutDegree(1));
    assertEquals(3, g.getInDegree(1));
    assertEquals(3, g.getOutDegree(2));
    assertEquals(0, g.getInDegree(2));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(1, g.getInDegree(3));

    g.removeEdge(2, 1, "bar");
    assertEquals(1, g.getOutDegree(1));
    assertEquals(2, g.getInDegree(1));
    assertEquals(2, g.getOutDegree(2));
    assertEquals(0, g.getInDegree(2));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(1, g.getInDegree(3));

    g.removeEdges(2, 1);
    assertEquals(1, g.getOutDegree(1));
    assertEquals(0, g.getInDegree(1));
    assertEquals(0, g.getOutDegree(2));
    assertEquals(0, g.getInDegree(2));
    assertEquals(0, g.getOutDegree(3));
    assertEquals(1, g.getInDegree(3));

    g.add(2, 3, "bar");
    g.add(3, 4, "bar");
    g.add(3, 5, "bar");
    g.add(3, 6, "bar");
    g.add(3, 7, "bar");
    g.add(3, 8, "bar");
    g.add(3, 9, "bar");
    g.add(3, 10, "bar");
    g.add(3, 10, "foo");
    assertEquals(1, g.getOutDegree(1));
    assertEquals(0, g.getInDegree(1));
    assertEquals(1, g.getOutDegree(2));
    assertEquals(0, g.getInDegree(2));
    assertEquals(8, g.getOutDegree(3));
    assertEquals(2, g.getInDegree(3));
  }

  public <E> void checkIterator(Iterable<E> edges, E ... expected) {
    Set<E> expectedSet = new HashSet<E>(Arrays.asList(expected));
    Set<E> foundSet = new HashSet<E>();
    for (E edge : edges) {
      if (foundSet.contains(edge)) {
        throw new AssertionError("Received two copies of " + edge +
                                 " when running an edge iterator");
      }
      foundSet.add(edge);
    }
    assertEquals(expectedSet, foundSet);
  }

  public void testIterables() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1));
    checkIterator(g.incomingEdgeIterable(2));
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.edgeIterable());

    g.add(1, 2, "1-2");
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1), "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.edgeIterable(), "1-2");

    g.add(1, 2, "1-2b");
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1), "1-2", "1-2b");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.edgeIterable(), "1-2", "1-2b");

    g.add(1, 3, "1-3");
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1), "1-2", "1-2b", "1-3");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.incomingEdgeIterable(3), "1-3");
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(), "1-2", "1-2b", "1-3");
    checkIterator(g.getEdges(1, 2), "1-2", "1-2b");
    checkIterator(g.getEdges(1, 3), "1-3");

    g.add(1, 3, "1-3b");
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1), "1-2", "1-2b", "1-3", "1-3b");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.incomingEdgeIterable(3), "1-3", "1-3b");
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(), "1-2", "1-2b", "1-3", "1-3b");

    g.removeEdge(1, 3, "1-3b");
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1), "1-2", "1-2b", "1-3");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.incomingEdgeIterable(3), "1-3");
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(), "1-2", "1-2b", "1-3");

    g.removeEdge(1, 3, "1-3b");
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1), "1-2", "1-2b", "1-3");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.incomingEdgeIterable(3), "1-3");
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(), "1-2", "1-2b", "1-3");

    g.removeEdge(1, 3, "1-3");
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1), "1-2", "1-2b");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.incomingEdgeIterable(3));
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(), "1-2", "1-2b");

    g.add(1, 3, "1-3");
    g.add(1, 3, "1-3b");
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1), "1-2", "1-2b", "1-3", "1-3b");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.incomingEdgeIterable(3), "1-3", "1-3b");
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(), "1-2", "1-2b", "1-3", "1-3b");

    g.add(1, 1, "1-1");
    checkIterator(g.incomingEdgeIterable(1), "1-1");
    checkIterator(g.outgoingEdgeIterable(1),
                  "1-1", "1-2", "1-2b", "1-3", "1-3b");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.incomingEdgeIterable(3), "1-3", "1-3b");
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(), "1-1", "1-2", "1-2b", "1-3", "1-3b");

    g.add(2, 1, "2-1");
    checkIterator(g.incomingEdgeIterable(1), "1-1", "2-1");
    checkIterator(g.outgoingEdgeIterable(1),
                  "1-1", "1-2", "1-2b", "1-3", "1-3b");
    checkIterator(g.incomingEdgeIterable(2), "1-2", "1-2b");
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.incomingEdgeIterable(3), "1-3", "1-3b");
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(),
                  "1-1", "1-2", "1-2b", "1-3", "1-3b", "2-1");
    checkIterator(g.getEdges(1, 1), "1-1");
    checkIterator(g.getEdges(1, 2), "1-2", "1-2b");
    checkIterator(g.getEdges(1, 3), "1-3", "1-3b");
    checkIterator(g.getEdges(3, 1));

    g.removeVertex(2);
    checkIterator(g.incomingEdgeIterable(1), "1-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-1", "1-3", "1-3b");
    checkIterator(g.incomingEdgeIterable(3), "1-3", "1-3b");
    checkIterator(g.outgoingEdgeIterable(3));
    checkIterator(g.edgeIterable(), "1-1", "1-3", "1-3b");
  }

  /** Test the behavior of the copy constructor; namely, make sure it's doing a deep copy */
  public void testCopyConstructor() {
    DirectedMultiGraph<Integer, String> g =
        new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    g.addVertex(3);
    g.add(1, 2, "1-2a");
    g.add(1, 2, "1-2b");
    g.add(1, 2, "1-2c");
    g.add(1, 3, "1-3a");
    g.add(1, 3, "1-3b");
    g.add(2, 3, "2-3a");
    g.add(2, 3, "2-3b");
    g.add(3, 1, "3-1a");
    g.add(3, 1, "3-1b");

    DirectedMultiGraph<Integer, String> copy = new DirectedMultiGraph<Integer, String>(g);
    assertEquals(g.getNumEdges(), copy.getNumEdges());
    int originalSize = g.getNumEdges();
    assertEquals(originalSize, g.getNumEdges());

    copy.removeEdge(1, 2, "1-2b");
    assertEquals(originalSize - 1, copy.getNumEdges());
    assertEquals(originalSize, g.getNumEdges());
    copy.removeVertex(3);
    assertEquals(originalSize - 7, copy.getNumEdges());
    assertEquals(originalSize, g.getNumEdges());
  }

  /** Check to make sure {@link edu.stanford.nlp.graph.DirectedMultiGraph#edgeIterator()}.remove() works as expected */
  public void testIteratorRemove() {
    DirectedMultiGraph<Integer, String> g =
        new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    g.addVertex(3);
    g.add(1, 2, "1-2a");
    g.add(1, 2, "1-2b");
    g.add(1, 2, "1-2c");
    g.add(1, 3, "1-3a");
    g.add(1, 3, "1-3b");
    g.add(2, 3, "2-3a");
    g.add(2, 3, "2-3b");
    g.add(3, 1, "3-1a");
    g.add(3, 1, "3-1b");

    checkGraphConsistency(g);

    for (String edge : g.getAllEdges()) {
      // Create copy and remove edge from copy manually
      int originalSize = g.getNumEdges();
      DirectedMultiGraph<Integer, String> gold = new DirectedMultiGraph<Integer, String>(g);
      DirectedMultiGraph<Integer, String> guess = new DirectedMultiGraph<Integer, String>(g);
      gold.removeEdge(Integer.parseInt(edge.substring(0, 1)), Integer.parseInt(edge.substring(2, 3)), edge);
      assertEquals(originalSize, g.getNumEdges());
      assertEquals(originalSize - 1, gold.getAllEdges().size());
      // Use iter.remove()
      Iterator<String> iter = guess.edgeIterator();
      int iterations = 0;
      while (iter.hasNext()) {
        ++iterations;
        if (iter.next().equals(edge)) {
          iter.remove();
          checkGraphConsistency(guess);
        }
      }
      assertEquals(9, iterations);
      checkGraphConsistency(guess);
      // Assert that they're the same
      assertEquals(gold, guess);
    }
  }

  /**
   * A few loops get tested in testIterables; this exercises them more
   */
  public void testLoops() {
    DirectedMultiGraph<Integer, String> g =
      new DirectedMultiGraph<Integer, String>();
    g.addVertex(1);
    g.addVertex(2);
    g.add(1, 1, "1-1");
    g.add(1, 2, "1-2");
    g.add(2, 1, "2-1");
    checkIterator(g.incomingEdgeIterable(1), "1-1", "2-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-1", "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.edgeIterable(), "1-1", "1-2", "2-1");

    g.removeVertex(1);
    checkIterator(g.incomingEdgeIterable(2));
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.edgeIterable());

    g.addVertex(1);
    checkIterator(g.incomingEdgeIterable(1));
    checkIterator(g.outgoingEdgeIterable(1));
    checkIterator(g.incomingEdgeIterable(2));
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.edgeIterable());

    g.add(1, 1, "1-1");
    g.add(1, 2, "1-2");
    g.add(2, 1, "2-1");
    checkIterator(g.incomingEdgeIterable(1), "1-1", "2-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-1", "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.edgeIterable(), "1-1", "1-2", "2-1");

    g.removeEdge(1, 1, "1-1");
    checkIterator(g.incomingEdgeIterable(1), "2-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.edgeIterable(), "1-2", "2-1");

    g.add(1, 1, "1-1");
    checkIterator(g.incomingEdgeIterable(1), "1-1", "2-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-1", "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.edgeIterable(), "1-1", "1-2", "2-1");

    g.removeEdges(1, 1);
    checkIterator(g.incomingEdgeIterable(1), "2-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.edgeIterable(), "1-2", "2-1");

    g.add(1, 1, "1-1");
    checkIterator(g.incomingEdgeIterable(1), "1-1", "2-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-1", "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.edgeIterable(), "1-1", "1-2", "2-1");

    g.removeEdges(1, 2);
    checkIterator(g.incomingEdgeIterable(1), "1-1", "2-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-1");
    checkIterator(g.incomingEdgeIterable(2));
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.edgeIterable(), "1-1", "2-1");

    g.add(1, 2, "1-2");
    checkIterator(g.incomingEdgeIterable(1), "1-1", "2-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-1", "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2), "2-1");
    checkIterator(g.edgeIterable(), "1-1", "1-2", "2-1");

    g.removeEdges(2, 1);
    checkIterator(g.incomingEdgeIterable(1), "1-1");
    checkIterator(g.outgoingEdgeIterable(1), "1-1", "1-2");
    checkIterator(g.incomingEdgeIterable(2), "1-2");
    checkIterator(g.outgoingEdgeIterable(2));
    checkIterator(g.edgeIterable(), "1-1", "1-2");
  }
}
