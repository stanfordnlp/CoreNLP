/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.graph;

import vn.hus.nlp.graph.util.AdjacencyListEdgeIterator;
import vn.hus.nlp.graph.util.AdjacencyListVertexIterator;
import vn.hus.nlp.graph.util.EdgeIterator;
import vn.hus.nlp.graph.util.VertexIterator;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 27, 2007, 10:41:04 PM
 *         <p>
 *         The adjacency list representation is suitable for sparse weighted
 *         graph. We usually use an edge iterator instead of a vertex iterator
 *         to iterate through the graph, since an edge contains not only
 *         vertices but also weight information.
 */
public class AdjacencyListWeightedGraph extends WeightedGraph {

	private final EdgeNode adj[];

	/**
	 * Constructor.
	 * 
	 * @param n
	 *            number of vertices of the graph.
	 * @param directed
	 *            <code>true/false</code>
	 */
	public AdjacencyListWeightedGraph(int n, boolean directed) {
		super(n, directed);
		adj = new EdgeNode[n];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see vn.hus.graph.Graph#edge(int, int)
	 */
	@Override
	public boolean edge(int u, int v) {
		EdgeIterator iterator = edgeIterator(u);
		while (iterator.hasNext()) {
			Edge e = iterator.next();
			if (v == e.getV())
				return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see vn.hus.graph.WeightedGraph#edgeIterator(int)
	 */
	@Override
	public EdgeIterator edgeIterator(int u) {
		return new AdjacencyListEdgeIterator(this, u);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see vn.hus.graph.Graph#insert(vn.hus.graph.Edge)
	 */
	@Override
	public void insert(Edge edge) {
		int u = edge.getU();
		int v = edge.getV();
		// add the edge (u,v)
		adj[u] = new EdgeNode(edge, adj[u]);
		// add the edge (v,u) if the graph
		// is not directed
		if (!directed) {
			adj[v] = new EdgeNode(edge, adj[v]);
		}
		// increase the number of edges
		cE++;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see vn.hus.graph.IGraph#remove(vn.hus.graph.Edge)
	 */
	@Override
	public void remove(Edge edge) {
		// TODO
	}

	/**
	 * Get the adjacency list.
	 * 
	 * @return the adjacency list.
	 */
	public EdgeNode[] getAdj() {
		return adj;
	}

	/**
	 * @see vn.hus.nlp.graph.WeightedGraph#getEdge(int, int)
	 * @see #edge(int, int)
	 */
	@Override
	public Edge getEdge(int u, int v) {
		EdgeIterator iterator = edgeIterator(u);
		while (iterator.hasNext()) {
			Edge e = iterator.next();
			if (v == e.getV())
				return e;
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see vn.hus.graph.Graph#vertexIterator(int)
	 */
	@Override
	public VertexIterator vertexIterator(int u) {
		// build the graph2 from graph
		int nV = getNumberOfVertices();
		AdjacencyListGraph graph2 = new AdjacencyListGraph(nV, isDirected());
		// copy the edges of graph to graph2
		for (int v = 0; v < nV; v++) {
			EdgeIterator edgeIterator = edgeIterator(v);
			while (edgeIterator.hasNext()) {
				Edge edge = edgeIterator.next();
				graph2.insert(edge);
			}
		}
		// return the vertex iterator of graph2
		return new AdjacencyListVertexIterator(graph2, u);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see vn.hus.graph.Graph#dispose()
	 */
	@Override
	protected void dispose() {
		// delete the array of linked-list.
		for (int u = 0; u < adj.length; u++) {
			dispose(adj[u]);
		}
	}

	/**
	 * Dispose a LIFO linked list headed by a node.
	 * 
	 * @param node
	 *            the top node of the list.
	 */
	private void dispose(EdgeNode node) {
		if (node != null) {
			dispose(node.getNext());
		}
		node = null;
	}

}
