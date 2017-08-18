/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.graph;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 18, 2007, 9:48:57 PM
 *         <p>
 *         An edge of a graph. It contains two vertices and a weight.
 */
public class Edge implements Comparable<Edge> {
	private final int u;
	private final int v;
	private double weight;

	/**
	 * Constructor.
	 * 
	 * @param u
	 * @param v
	 */
	public Edge(int u, int v) {
		this.u = u;
		this.v = v;
		this.weight = 0;
	}

	/**
	 * Constructor
	 * 
	 * @param u
	 * @param v
	 * @param weight
	 */
	public Edge(int u, int v, double weight) {
		this(u, v);
		this.weight = weight;
	}

	/**
	 * Get the source vertex.
	 * 
	 * @return source vertex
	 */
	public int getU() {
		return u;
	}

	/**
	 * Get the target vertex.
	 * 
	 * @return the target vertex.
	 */
	public int getV() {
		return v;
	}

	/**
	 * Get the weight of the edge.
	 * 
	 * @return the weight.
	 */
	public double getWeight() {
		return weight;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Edge) {
			Edge e = (Edge) obj;
			return (u == e.getU() && v == e.getV());
		}
		return false;
	}

	@Override
	public String toString() {
		return getU() + " - " + getV() + ": " + getWeight();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Edge o) {
		double diff = getWeight() - o.getWeight();
		if (diff == 0)
			return 0;
		else if (diff > 0)
			return 1;
		else
			return -1;
	}
}
