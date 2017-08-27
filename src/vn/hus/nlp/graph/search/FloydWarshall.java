/**
 * (C) LE HONG Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.graph.search;

import vn.hus.nlp.graph.Edge;
import vn.hus.nlp.graph.IGraph;
import vn.hus.nlp.graph.IWeightedGraph;
import vn.hus.nlp.graph.util.EdgeIterator;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 18, 2008, 10:47:02 PM
 *         <p>
 *         Implementation of the Floyd-Warshall algorithm to find all points
 *         shortest path problem for a directed graph. 
 */
public class FloydWarshall {
	/**
	 * The weighted graph we operate on.
	 */
	private IWeightedGraph graph;
	
	/**
	 * The cost matrix.
	 */
	private double[][] cost;
	
	/**
	 * The number of vertices of the graph
	 */
	private int n;
	
	/**
	 * Copy constructor.
	 * @param graph a weighted graph
	 */
	public FloydWarshall(IWeightedGraph graph) {
		// initialize the graph
		//
		this.graph = graph;
	}
	
	/**
	 * Get the graph.
	 * @return the graph
	 */
	public IGraph getGraph() {
		return graph;
	}

	/**
	 * Initialize the cost matrix.
	 */
	public void initialize() {
		// initialize the cost matrix 
		//
		// get the number of vertices of the graph
		n = graph.getNumberOfVertices();
		// create the cost matrix
		cost = new double[n][n];
		int i, j;
		// initialize the cost matrix
		for (i = 0; i < n; i++) 
			for (j = 0; j < n; j++) {
				cost[i][j] = Double.POSITIVE_INFINITY;
			}
		// initialize elements on the diagonal of the matrix 
		//
		for (i = 0; i < n; i++)
			cost[i][i] = 0;
		
		// for each vertex i, we iterate through all the edges (i,j) that go out from i
		// and update the element (i,j) of the cost matrix 
		for (i = 0; i < n; i++) {
			EdgeIterator edgeIterator = graph.edgeIterator(i);
			while (edgeIterator.hasNext()) {
				Edge edge = edgeIterator.next();
				cost[i][edge.getV()] = edge.getWeight();
			}
		}
		
	}
	
	/**
	 * Get the cost matrix.
	 * @return the cost matrix
	 */
	public double[][] getCost() {
		return cost;
	}
	
	/**
	 * Implementation of the Floyd-Warshall algorithm. 
	 * @return the cost matrix.
	 */
	public double[][] algorithmFloydWarshall() {
		// first, initialize the cost matrix:
		//
		initialize();
		// 
		int i, j, k;
		// then implement the FW algorithm.
		for (k = 0; k < n; k++) {
			for (i = 0; i < n; i++)
				for (j = 0; j < n; j++)
					cost[i][j] = Math.min(cost[i][j], cost[i][k] + cost[k][j]);
		}
		// return the cost matrix.
		return cost;
	}
}
