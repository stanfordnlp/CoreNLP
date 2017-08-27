/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph;

import vn.hus.nlp.graph.util.EdgeIterator;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 27, 2007, 10:51:13 PM
 *         <p>
 *         Basic implementation of weighted graphs.
 */
public abstract class WeightedGraph extends Graph implements IWeightedGraph {

	/**
	 * Default constructor.
	 * @param n
	 * @param directed
	 */
	public WeightedGraph(int n, boolean directed) {
		super(n, directed);
	}
	/* (non-Javadoc)
	 * @see vn.hus.graph.IWeightedGraph#edgeIterator(int)
	 */
	public abstract EdgeIterator edgeIterator(int u);

	/* (non-Javadoc)
	 * @see vn.hus.graph.IWeightedGraph#getEdge(int, int)
	 */
	public abstract Edge getEdge(int u, int v);

}
