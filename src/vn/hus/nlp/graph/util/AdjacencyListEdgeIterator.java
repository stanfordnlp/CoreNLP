/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.graph.util;

import vn.hus.nlp.graph.AdjacencyListWeightedGraph;
import vn.hus.nlp.graph.Edge;
import vn.hus.nlp.graph.EdgeNode;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 18, 2007, 9:50:34 PM
 *         <p>
 *         An iterator that examines a list of outcoming edges of a vertex in an
 *         adjacency list graph.
 */
public class AdjacencyListEdgeIterator implements EdgeIterator {

	/**
	 * The underlying graph that this iterator operates on.
	 */
	private final AdjacencyListWeightedGraph graph;

	private EdgeNode next = null;

	/**
	 * Construct the iterator over vertices adjacent to vertex u.
	 * 
	 * @param g
	 * @param u
	 */
	public AdjacencyListEdgeIterator(AdjacencyListWeightedGraph g, int u) {
		this.graph = g;

		// get the number of vertices of the graph
		int n = graph.getNumberOfVertices();
		// range checking
		new AssertionError(u < 0 || u >= n);
		next = graph.getAdj()[u];
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see vn.hus.graph.util.EdgeIterator#next()
	 */
	public Edge next() {
		// get the next edge
		Edge e = next.getEdge();
		// update the next pointer
		next = next.getNext();
		return e;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see vn.hus.graph.util.VertexIterator#hasNext()
	 */
	public boolean hasNext() {
		return (next != null);
	}
}
