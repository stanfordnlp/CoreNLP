/**
 * (C) LE HONG Phuong, phuonglh@gmail.com
 */
package vn.hus.nlp.graph.search;

import vn.hus.nlp.graph.IGraph;
import vn.hus.nlp.graph.IWeightedGraph;
import vn.hus.nlp.graph.io.GraphIO;

/**
 * @author LE HONG Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 18, 2008, 11:27:41 PM
 *         <p>
 *         Test class of the {@link FloydWarshall} class.
 */
public class FloydWarshallTest {

	/**
	 * A sample GRAPH.TXT file
	 */
	public static final String INPUT_FILE = "samples/weighted/GRAPH.TXT";

	/**
	 * Create a test object given an input data file.
	 * @param inputFilename an input data file.
	 */
	public FloydWarshallTest(String inputFilename) {
		// scan the graph from a text file
		//
		IGraph graph = GraphIO.scanAdjacencyListWeighted(inputFilename);
		// print out the graph
		//
		GraphIO.print(graph);
		// cast to a weighted graph if it is and do the trick
		// 
		if (graph instanceof IWeightedGraph) {
			IWeightedGraph weightedGraph = (IWeightedGraph) graph;
			// create a FW object
			FloydWarshall fw = new FloydWarshall(weightedGraph);
			// run the FW algorithm on the graph to get the cost matrix
			double[][] cost = fw.algorithmFloydWarshall();
			int n = weightedGraph.getNumberOfVertices();
			// print out the cost matrix
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < n; j++)
					System.out.print(cost[i][j] + "\t");
				System.out.println();
			}
		} else {
			System.out.println("You don't provide me a weighted graph!");
		}

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		new FloydWarshallTest(INPUT_FILE);
	}

}
