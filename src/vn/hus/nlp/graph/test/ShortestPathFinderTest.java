/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph.test;

import junit.framework.TestCase;
import vn.hus.nlp.graph.Edge;
import vn.hus.nlp.graph.IWeightedGraph;
import vn.hus.nlp.graph.Node;
import vn.hus.nlp.graph.io.GraphIO;
import vn.hus.nlp.graph.search.ShortestPathFinder;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * Oct 28, 2007, 5:26:24 PM
 * <p>
 */
public class ShortestPathFinderTest extends TestCase {

	IWeightedGraph graph = null;
	
	ShortestPathFinder shortestPathFinder = null;
	
	/**
	 * @param name
	 */
	public ShortestPathFinderTest(String name) {
		super(name);
	}

	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		// create a graph
//		graph = GraphIO.scanAdjacencyListWeighted("samples/weighted/list1.txt");
//		graph = GraphIO.scanAdjacencyListWeighted("samples/weighted/list2.txt");
		graph = GraphIO.scanAdjacencyListWeighted("samples/weighted/list3.txt");
		shortestPathFinder  = new ShortestPathFinder(graph);
	}

	/**
	 * Test method for {@link vn.hus.nlp.graph.search.ShortestPathFinder#getWeight(int)}.
	 */
	public void testWeights() {
//		// test weights of graph in list1.txt
//		double[] expectedWeights = {0, 0.41, 0.82, 0.86, 0.5, 0.29};
//		
//		for (int u = 0; u < expectedWeights.length; u++) {
//			double weight = shortestPathFinder.getWeight(u);
//			assertEquals(expectedWeights[u], weight, 0.001);
//		}

		// test weights of graph in list2.txt
		double[] expectedWeights = {0, 2, 1, 3, 5};
		
		for (int u = 0; u < expectedWeights.length; u++) {
			double weight = shortestPathFinder.getWeight(u);
			assertEquals(expectedWeights[u], weight, 0.001);
		}
		
	}
	
	/**
	 * Test method for {@link ShortestPathFinder#getSpanningTree()}.
	 */
	public void testSpanningTree() {
		// Expected values for spanning trees of the graph
		// given in list2.txt
		Edge[] expected = {new Edge(0, 0), new Edge(0, 1), new Edge(0, 2),
				new Edge(1, 3), new Edge(3, 4)};
		Edge[] spanningTree = shortestPathFinder.getSpanningTree();
		for (int u = 0; u < spanningTree.length; u++) {
			Edge e = spanningTree[u];
			assertEquals(expected[u], e);
		}
	}
	

	/**
	 * Test method for {@link ShortestPathFinder#getAShortestPath(int)}.
	 */
	public void testAShortestPath() {
		int[] shortestPath = shortestPathFinder.getAShortestPath(2);
		int[] expected = {0, 5, 4, 2};
		assertEquals(expected.length, shortestPath.length);
		for (int i = 0; i < expected.length; i++) {
			assertEquals(expected[i], shortestPath[i]);
		}
	}
	
	/**
	 * Test method for {@link ShortestPathFinder#getAllShortestPaths(int)}
	 */
	public void testAllShortestPath() {
		/// Test for list2.txt
		///
//		Node[] allShortestPaths = shortestPathFinder.getAllShortestPaths(0);
//		assertEquals(1, allShortestPaths.length);
//		assertEquals("0", allShortestPaths[0].toString());
		
//		Node[] allShortestPaths = shortestPathFinder.getAllShortestPaths(1);
//		assertEquals(1, allShortestPaths.length);
//		assertEquals("0->1", allShortestPaths[0].toString());


//		Node[] allShortestPaths = shortestPathFinder.getAllShortestPaths(2);
//		assertEquals(1, allShortestPaths.length);
//		assertEquals("0->2", allShortestPaths[0].toString());

//		Node[] allShortestPaths = shortestPathFinder.getAllShortestPaths(3);
//		assertEquals(2, allShortestPaths.length);

//		Node[] allShortestPaths = shortestPathFinder.getAllShortestPaths(4);
//		assertEquals(2, allShortestPaths.length);
		
		//// Test for list3.txt
//		Node[] allShortestPaths = shortestPathFinder.getAllShortestPaths(4);
//		assertEquals(3, allShortestPaths.length);

		Node[] allShortestPaths = shortestPathFinder.getAllShortestPaths(6);
		assertEquals(6, allShortestPaths.length);
		
	}
}
