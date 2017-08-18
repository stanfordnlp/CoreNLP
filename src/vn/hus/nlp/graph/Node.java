package vn.hus.nlp.graph;

import java.util.ArrayList;
import java.util.List;

import vn.hus.nlp.graph.util.VertexIterator;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 18, 2007, 10:46:37 PM
 *         <p>
 *         The singly-linked list that represents the adjacency list of nodes.
 *         This list is to be used in representation of unweighted graph.
 *         @see Graph
 *         @see VertexIterator
 */
public class Node {
	private final int v;
	private final Node next;

	/**
	 * Default constructor.
	 */
	public Node() {
		v = -1;
		next = null;
	}

	/**
	 * Construct a node.
	 * @param v current vertex data of the node.
	 * @param next the next node.
	 */
	public Node(int v, Node next) {
		this.v = v;
		this.next = next;
	}

	/**
	 * Get next node.
	 * @return the next node.
	 */
	public Node getNext() {
		return next;
	}
	
	/**
	 * Get the vertex.
	 * @return the vertex
	 */
	public int getV() {
		return v;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		String s = "";
		Node node = this;
		while (node.getV() != -1) {
			s = s + node.getV();
			node = node.getNext();
			if (node.getV() != -1) {
				s += "->";
			}
		}
		return s;
	}
	
	/**
	 * Convert the list to an array of integers.
	 * @return an array of integers
	 */
	public int[] toArray() {
		List<Integer> list = new ArrayList<Integer>();
		
		Node node = this;
		while (node.getV() != -1) {
			list.add(node.getV());
			node = node.getNext();
		}
		int[] a = new int[list.size()];
		for (int i = 0; i < a.length; i++) {
			a[i] = list.get(i).intValue();
		}
		return a;
	}
}
