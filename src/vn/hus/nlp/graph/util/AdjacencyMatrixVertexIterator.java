/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph.util;

import vn.hus.nlp.graph.AdjacencyMatrixGraph;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * Oct 21, 2007, 10:46:48 PM
 * <p>
 * An iterator that examines the vertices of an adjacency matrix graph.
 */
public class AdjacencyMatrixVertexIterator implements VertexIterator {

	private final AdjacencyMatrixGraph graph;
	
	private final int n;
	
	private final int u;
	
	private int v = -1;
	
	private final boolean[][] adj;
	
	/**
	 * Constructor.
	 * @param g
	 * @param u
	 */
	public AdjacencyMatrixVertexIterator(AdjacencyMatrixGraph g, int u) {
		this.graph = g;
		this.u = u;
		// get the number of vertices of the graph
		n = graph.getNumberOfVertices();
		// range checking
		new AssertionError(u < 0 || u >= n);
		adj = graph.getAdj();
	}

	/* (non-Javadoc)
	 * @see vn.hus.graph.util.VertexIterator#hasNext()
	 */
	public boolean hasNext() {
		// increase the current vertex v.
		v++;
		for (int i = v; i < n; i++) 
			if (adj[u][i]) return true;
		return false;
	}

	/* (non-Javadoc)
	 * @see vn.hus.graph.util.VertexIterator#next()
	 */
	public int next() {
		while (v < n) {
			if (adj[u][v]) return v; else v++;
		}
		return -1;
	}

}
