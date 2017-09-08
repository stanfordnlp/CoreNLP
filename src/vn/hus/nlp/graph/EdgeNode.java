package vn.hus.nlp.graph;

import vn.hus.nlp.graph.util.EdgeIterator;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 27, 2007, 10:46:37 PM
 *         <p>
 *         The singly-linked list that represents the adjacency list of edges.
 *         This list is used in the representation of weighted graph.
 *         @see WeightedGraph
 *         @see EdgeIterator
 */
public class EdgeNode {
	private final Edge e;
	private final EdgeNode next;

	/**
	 * Default constructor.
	 */
	public EdgeNode() {
		e = null;
		next = null;
	}

	/**
	 * Constructor an edge node given an edge and next edge node. 
	 * @param e an edge
	 * @param next next edge node
	 */
	public EdgeNode(Edge e, EdgeNode next) {
		this.e = e;
		this.next = next;
	}

	/**
	 * Get the next edge node.
	 * @return the next edge node.
	 */
	public EdgeNode getNext() {
		return next;
	}
	
	/**
	 * Get the edge in the current node.
	 * @return the edge.
	 */
	public Edge getEdge() {
		return e;
	}

}
