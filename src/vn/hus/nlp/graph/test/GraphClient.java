/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph.test;

import java.io.Reader;
import java.io.StringReader;

import vn.hus.nlp.graph.IGraph;
import vn.hus.nlp.graph.io.GraphIO;
import vn.hus.nlp.graph.search.GraphBFS;
import vn.hus.nlp.graph.search.GraphDFS;
import vn.hus.nlp.graph.util.GraphConnectivity;
import vn.hus.nlp.graph.util.GraphDegree;
import vn.hus.nlp.graph.util.GraphUtilities;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 21, 2007, 8:47:18 PM
 *         <p>
 *         A client code to test the package.
 */
public class GraphClient {

	public static void testAdjacencyListGraph() {
		// create an adjacency list graph from a data file
		IGraph graph = GraphIO.scanAdjacencyList("samples/list0.txt");
		// print out the graph
		GraphIO.print(graph);
	}
	
	public static void testAdjacencyMatrixGraph() {
		// create an adjacency matrix graph from a data file
		IGraph graph = GraphIO.scanAdjacencyMatrix("samples/matrix0.txt");
		// print out the graph
		GraphIO.print(graph);
	}
	
	/**
	 * Print degrees of vertices.
	 */
	public static void testDegrees() {
		// create an adjacency list graph from a data file
		IGraph graph = GraphIO.scanAdjacencyList("samples/list0.txt");
		GraphDegree gd = new GraphDegree(graph);
		gd.printDegrees();
	}
	
	/**
	 * Print orders of a visit of the DFS algorithm from a vertex u. 
	 * @param u
	 */
	public static void testDFS(int u) {
		// create an adjacency list graph from a data file
		IGraph graph = GraphIO.scanAdjacencyList("samples/list0.txt");
		// search the graph from the vertex u
		GraphDFS graphDFS = new GraphDFS(graph, u);
		graphDFS.printOrder();
	}
	
	/**
	 * Test the method that counts for number of components 
	 * of a graph.
	 */
	public static void testComponents() {
		// create an adjacency list graph from a data file
		IGraph graph = GraphIO.scanAdjacencyList("samples/list2.txt");
		System.out.println("# of connected components = " + GraphConnectivity.countComponents(graph));
		int n = graph.getNumberOfVertices();
		n--; // the end vertex
		if (GraphConnectivity.isConnected(graph, 0, n)) {
			System.out.println("There is a path from vertex 0 to vertex " + n + ".");
		} else {
			System.out.println("Vertex 0 and vertex " + n + " is not connected.");
		}
	}
	/**
	 * Print orders of a visit of the BFS algorithm from a vertex u. 
	 * @param u
	 */
	public static void testBFS(int u) {
		// create an adjacency list graph from a data file
		IGraph graph = GraphIO.scanAdjacencyList("samples/list3.txt");
		// search the graph from the vertex u
		GraphBFS graphBFS = new GraphBFS(graph, u);
		System.out.println("Order: ");
		graphBFS.printOrder();
		System.out.println("Spanning tree: ");
		graphBFS.printSpanningTree();
		System.out.println("A shortest path from the start vertex to the end vertex:");
		graphBFS.shortestPath(0, graph.getNumberOfVertices() - 1);
	}
	
	public static void testTransitiveClosure() {
		// create an adjacency list graph from a data file
		IGraph graph = GraphIO.scanAdjacencyList("samples/list5.txt");
		IGraph tc = GraphUtilities.getTransitiveClosure(graph);
		// print out the transitive closure
		GraphIO.print(tc);
	}

	public static void testAdjacencyListWeightedGraph() {
		// create an adjacency list weighted graph from a data file
		IGraph graph = GraphIO.scanAdjacencyListWeighted("samples/weighted/list0.txt");
		// print out the graph
		GraphIO.print(graph);
	}

	public static void testIsolatedVertices() {
		// create an adjacency list graph from a data file
		IGraph graph = GraphIO.scanAdjacencyList("samples/list6.txt");
		int[] isolatedVertices = GraphConnectivity.getIsolatedVertices(graph);
		System.out.println("All isolated vertices:");
		for (int i = 0; i < isolatedVertices.length; i++) {
			System.out.println(isolatedVertices[i]);
		}
	}
	
	public static void testProjectivity() {
		StringBuffer sb = new StringBuffer();
		sb.append(10);
		sb.append("\n");
		sb.append("0 3\n");
		sb.append("2 1\n");
		sb.append("3 2\n");
		sb.append("3 5\n");
		sb.append("3 9\n");
		sb.append("5 4\n");
		sb.append("5 6\n");
		sb.append("6 8\n");
		sb.append("8 7\n");
		Reader reader = new StringReader(sb.toString());
		IGraph graph = GraphIO.scanAdjacencyList(reader);
		System.out.println("Is this graph projective? " + GraphUtilities.isProjective(graph));
		//
		sb = new StringBuffer();
		sb.append(9);
		sb.append("\n");
		sb.append("0 3\n");
		sb.append("0 8\n");
		sb.append("1 2\n");
		sb.append("3 5\n");
		sb.append("3 6\n");
		sb.append("5 1\n");
		sb.append("5 4\n");
		sb.append("6 7\n");
		reader = new StringReader(sb.toString());
		graph = GraphIO.scanAdjacencyList(reader);
		System.out.println("Is this graph projective? " + GraphUtilities.isProjective(graph));
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
//		testAdjacencyListGraph();
//		testAdjacencyMatrixGraph();
//		testDegrees();
//		testDFS(0);
//		testDFS(1);
//		testDFS(0);
//		testComponents();
//		testBFS(0);
//		testTransitiveClosure();
//		testAdjacencyListWeightedGraph();
//		testIsolatedVertices();
		testProjectivity();
	}

}
