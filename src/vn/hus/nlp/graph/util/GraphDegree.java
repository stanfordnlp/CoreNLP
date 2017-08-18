/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph.util;

import vn.hus.nlp.graph.IGraph;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 21, 2007, 11:56:01 PM
 *         <p>
 *         This class provides a way for client to find the degree of a 
 *         vertex in a graph in constant time, after linear-time preprocessing
 *         in the constructor. We use a vertex-indexed array to do the trick.
 */
public final class GraphDegree {
	private final IGraph graph;
	private final int[] deg;

	/**
	 * Constructor.
	 * 
	 * @param g
	 */
	public GraphDegree(IGraph g) {
		this.graph = g;
		int n = graph.getNumberOfVertices();
		deg = new int[n];
		for (int u = 0; u < n; u++) {
			deg[u] = 0;
			VertexIterator iterator = graph.vertexIterator(u);
			while (iterator.hasNext()) {
				iterator.next();
				deg[u]++;
			}
		}
	}

	/**
	 * Get the degree of a vertex.
	 * 
	 * @param u
	 * @return the degree of a vertex
	 */
	public int degree(int u) {
		return deg[u];
	}

	/**
	 * Print degrees of all the vertices.
	 */
	public void printDegrees() {
		int n = graph.getNumberOfVertices();
		for (int u = 0; u < n; u++) {
			int d = degree(u);
			// for testing only:
			System.out.println("deg(" + u + ") = " + d);
		}

	}
}
