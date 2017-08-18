/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.graph.util;

import vn.hus.nlp.graph.AdjacencyListGraph;
import vn.hus.nlp.graph.Node;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 * <p>
 * Oct 18, 2007, 9:50:34 PM
 * <p>
 * An iterator that examines a list of vertices of a graph.
 */
public class AdjacencyListVertexIterator implements VertexIterator {
	
	/**
	 * The underlying graph that this iterator operates on.
	 */
	private final AdjacencyListGraph graph;
	
	private Node next = null;
	
	/**
	 * Construct the iterator over vertices adjacent to vertex u.
	 * @param g
	 * @param u
	 */
	public AdjacencyListVertexIterator(AdjacencyListGraph g, int u) {
		this.graph = g;
		
		// get the number of vertices of the graph
		int n = graph.getNumberOfVertices();
		// range checking
		new AssertionError(u < 0 || u >= n);
		next =  graph.getAdj()[u];
	}
	/* (non-Javadoc)
	 * @see vn.hus.graph.util.VertexIterator#next()
	 */
	public int next() {
		// get the next vertex
		int v = next.getV();
		// update the next pointer
		next = next.getNext();
		return v;
	}

	/* (non-Javadoc)
	 * @see vn.hus.graph.util.VertexIterator#hasNext()
	 */
	public boolean hasNext() {
		return (next != null);
	}
}
