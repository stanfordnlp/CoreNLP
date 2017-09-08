/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph.search;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import vn.hus.nlp.graph.Edge;
import vn.hus.nlp.graph.IGraph;
import vn.hus.nlp.graph.util.VertexIterator;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 25, 2007, 9:57:40 PM
 *         <p>
 *         Implementation of Breadth First Search algorithm on the graph
 *         interface.
 */
public class GraphBFS {
	private IGraph graph;
	private int count;
	private int[] order;
	private int[] spanningTree;

	/**
	 * Search all vertices of the graph.
	 * 
	 * @param g
	 */
	public GraphBFS(IGraph g) {
		init(g);
		for (int u = 0; u < graph.getNumberOfVertices(); u++) {
			if (order[u] == -1)
				search(new Edge(u, u));
		}
	}

	/**
	 * Search the graph starting from vertex <tt>u</tt>.
	 * 
	 * @param g
	 * @param u
	 */
	public GraphBFS(IGraph g, int u) {
		init(g);
		// search the graph from u
		search(new Edge(u, u));
	}

	/**
	 * Search the graph starting at an edge.
	 * 
	 * @param edge
	 */
	private void search(Edge edge) {
		// the capacity of the edge queue is at most
		// the number of vertices of the graph since
		// the queue hold only vertices that have not been
		// visited (those marked with order -1)
		int n = graph.getNumberOfVertices();
		Queue<Edge> queue = new ArrayBlockingQueue<Edge>(n);
		queue.add(edge);
		order[edge.getV()] = count++;
		while (!queue.isEmpty()) {
			// get an edge from the queue
			Edge e = queue.remove();
			int u = e.getU(); // the start vertex of e
			int v = e.getV(); // the end vertex of e
			// u is the parent of v in the spanning tree.
			spanningTree[v] = u;
			// add all unvisited vertices incident v to the
			// queue with a dummy loop edge.
			VertexIterator iterator = graph.vertexIterator(v);
			while (iterator.hasNext()) {
				int w = iterator.next();
				if (order[w] == -1) {
					queue.add(new Edge(v, w));
					// update the order of which w is visited
					order[w] = count++;
				}
			}
		}
	}

	/**
	 * Init the data.
	 * 
	 * @param g
	 */
	private void init(IGraph g) {
		this.graph = g;
		count = 0;
		// get the number of vertices of the graph
		int n = graph.getNumberOfVertices();
		// init the order and the spanning tree array
		order = new int[n];
		spanningTree = new int[n];
		for (int v = 0; v < n; v++) {
			order[v] = -1;
			spanningTree[v] = -1;
		}
	}

	/**
	 * Get the order in which the search visited a vertex.
	 * 
	 * @param u
	 *            a vertex
	 * @return the order
	 */
	public int order(int u) {
		return order[u];
	}

	/**
	 * Get the parent vertex of a vertex in the spanning tree.
	 * 
	 * @param u
	 * @return the parent vertex of <code>u</code>.
	 */
	public int spanningTree(int u) {
		return spanningTree[u];
	}

	public void printOrder() {
		for (int u = 0; u < graph.getNumberOfVertices(); u++) {
			int o = order[u];
			System.out.println(u + ": " + o);
		}
	}

	public void printSpanningTree() {
		for (int u = 0; u < graph.getNumberOfVertices(); u++) {
			int o = spanningTree[u];
			System.out.println(u + ": " + o);
		}
	}

	/**
	 * Print out a shortest path from vertex <tt>u</tt> to vertex <tt>v</tt>.
	 * 
	 * @param u
	 * @param v
	 */
	public void shortestPath(int u, int v) {
		// backtracking up to the spanning tree from
		// the path from v to u
		//
		for (int t = v; t != u; t = spanningTree(t)) {
			System.out.print(t + "-");
		}
		System.out.println(u);
	}
}
