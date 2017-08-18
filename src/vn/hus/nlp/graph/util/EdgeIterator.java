/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph.util;

import vn.hus.nlp.graph.Edge;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 27, 2007, 10:46:23 PM
 *         <p>
 *         An edge iterator is used to iterate over a set of edges of a graph.
 */
public interface EdgeIterator {
	/**
	 * Get the next edge of the iteration. Note that this method must be called
	 * after checking with {@link #hasNext()}.
	 * 
	 * @return <code>null</code> or the next edge.
	 */
	public Edge next();

	/**
	 * Returns <tt>true</tt> if the iteration has more edges.
	 * 
	 * @return <code>true/false</code>
	 */
	public boolean hasNext();

}
