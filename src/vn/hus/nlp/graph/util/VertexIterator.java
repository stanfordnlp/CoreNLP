/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.graph.util;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 18, 2007, 9:50:34 PM
 *         <p>
 *         An iterator that examines a list of vertices of a graph.
 */
public interface VertexIterator {
	/**
	 * Get the next vertex of the iteration. Note that this method must be
	 * called after checking with {@link #hasNext()}.
	 * 
	 * @return <code>-1</code> or the next vertex index.
	 */
	public int next();

	/**
	 * Returns <tt>true</tt> if the iteration has more vertices.
	 * 
	 * @return <code>true/false</code>
	 */
	public boolean hasNext();
}
