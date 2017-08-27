/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph;

import vn.hus.nlp.graph.util.AdjacencyMatrixVertexIterator;
import vn.hus.nlp.graph.util.VertexIterator;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 21, 2007, 10:34:03 PM
 *         <p>
 *         The adjacency list representation is suitable for dense graph.
 */
public class AdjacencyMatrixGraph extends Graph {

	private final boolean adj[][];
	/**
	 * @param n
	 * @param directed
	 */
	public AdjacencyMatrixGraph(int n, boolean directed) {
		super(n, directed);
		adj = new boolean[n][n];
	}

	/* (non-Javadoc)
	 * @see vn.hus.graph.Graph#insert(vn.hus.graph.Edge)
	 */
	@Override
	public void insert(Edge edge) {
		int u = edge.getU();
		int v = edge.getV();
		if (!adj[u][v]) {
			cE++;
		}
		adj[u][v] = true;
		if (!directed) {
			adj[v][u] = true;
		}
	}

	/* (non-Javadoc)
	 * @see vn.hus.graph.Graph#iterator(int)
	 */
	@Override
	public VertexIterator vertexIterator(int u) {
		return new AdjacencyMatrixVertexIterator(this, u);
	}

	/* (non-Javadoc)
	 * @see vn.hus.graph.Graph#remove(vn.hus.graph.Edge)
	 */
	@Override
	public void remove(Edge edge) {
		int u = edge.getU();
		int v = edge.getV();
		if (adj[u][v]) {
			cE--;
		}
		adj[u][v] = false;
		if (!directed) {
			adj[v][u] = false;
		}
	}

	@Override
	public boolean edge(int u, int v) {
		return adj[u][v];
	}

	/**
	 * Get the adjacency matrix that represents the graph.
	 * @return the adjacency matrix that represents the graph.
	 */
	public boolean[][] getAdj() {
		return adj;
	}

	/* (non-Javadoc)
	 * @see vn.hus.graph.Graph#dispose()
	 */
	@Override
	protected void dispose() {
	}
	
}
