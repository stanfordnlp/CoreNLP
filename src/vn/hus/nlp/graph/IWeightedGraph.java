/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph;

import vn.hus.nlp.graph.util.EdgeIterator;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 27, 2007, 10:41:28 PM
 *         <p>
 *         Interface of a weighted graph.
 */
public interface IWeightedGraph extends IGraph {
	
	/**
	 * Get the edge determined by two vertices.
	 * @param u
	 * @param v
	 * @return the edge or <tt>null</tt> if there does not a such edge.
	 */
	public Edge getEdge(int u, int v);
	
	/**
	 * Get an iterator of edges that go out from a vertex
	 * @param u a vertex
	 * @return an iterator of edges.
	 */
	public EdgeIterator edgeIterator(int u);
}
