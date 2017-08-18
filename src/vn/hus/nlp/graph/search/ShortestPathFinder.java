/**
 * (C) Le Hong Phuong, phuonglh@gmail.com
 *  Vietnam National University, Hanoi, Vietnam.
 */
package vn.hus.nlp.graph.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import vn.hus.nlp.graph.Edge;
import vn.hus.nlp.graph.IWeightedGraph;
import vn.hus.nlp.graph.Node;
import vn.hus.nlp.graph.util.EdgeIterator;
import vn.hus.nlp.graph.util.GraphUtilities;

/**
 * @author Le Hong Phuong, phuonglh@gmail.com
 *         <p>
 *         Oct 28, 2007, 2:35:43 PM
 *         <p>
 *         The shortest path finder that constructs shortest paths from a given
 *         vertex to other vertices of a weighted graph. By default, the given
 *         vertex is the vertex 0.
 */
public class ShortestPathFinder {
	/**
	 * The weigted graph the finder operates on.
	 */
	private final IWeightedGraph graph;
	/**
	 * The weights of vertices.
	 */
	private final double[] weights;
	/**
	 * The spanning trees of paths.
	 */
	private final Edge[] spanningTree;

	/**
	 * An array that stores a shortest path.
	 */
	private int[] path;

	private int k;

	private Set<Node> shortestPaths;
	
	private int startVertex = 0; 
	
	private double epsilon = 0.0001d;

	/**
	 * Constructor 
	 * @param graph a weighted graph
	 */
	public ShortestPathFinder(IWeightedGraph graph) {
		// Init the graph
		//
		this.graph = graph;
		// init the weights of vertices
		// 
		int n = graph.getNumberOfVertices();
		weights = new double[n];
		double max = maxWeight();
		for (int i = 0; i < n; i++) {
			weights[i] = max;
		}
		// init the spanning tree
		//
		spanningTree = new Edge[n];
		// Do the job
		//
		dijkstra();
	}
	
	
	public ShortestPathFinder(IWeightedGraph graph, int startVertex) {
		this.startVertex = startVertex;
		// Init the graph
		//
		this.graph = graph;
		// init the weights of vertices
		// 
		int n = graph.getNumberOfVertices();
		weights = new double[n];
		double max = maxWeight();
		for (int i = 0; i < n; i++) {
			weights[i] = max;
		}
		// init the spanning tree
		//
		spanningTree = new Edge[n];
		// Do the job
		//
		dijkstra();
	}

	/**
	 * Perform Dijkstra algorithm on the vertex 0.
	 * 
	 * @see #dijkstra(int)
	 */
	public void dijkstra() {
		dijkstra(startVertex);
	}

	/**
	 * Perform the Dijkstra algorithm on the graph to find minimal weights of
	 * paths from a vertex <tt>u</tt> to other vertices of the graph. The weight
	 * of a path is the sum of weights on its edges. The result of this method
	 * is saved to two internal private arrays. To get the minimal weight of
	 * paths from <tt>u</tt> to some vertex <tt>v</tt>, use the method
	 * {@link #getWeight(int)}. To get the last spanning edge of a shortest path
	 * that leads to <tt>v</tt>, use the method {@link #getSpanningEdge(int)}.
	 * 
	 * @param u
	 *            the source vertex.
	 */
	public void dijkstra(int u) {
		int cE = graph.getNumberOfEdges();
		weights[u] = 0;
		// create a queue with a fixed size to hold edges of the graph.
		// The size of the queue is not larger than number of edges of
		// the graph.
		Queue<Edge> queue = new ArrayBlockingQueue<Edge>(cE);
		// add a fake edge to start the loop.
		queue.add(new Edge(u, u));
		while (!queue.isEmpty()) {
			// get the head of the queue
			Edge edge = queue.remove();
			int uu = edge.getU();
			int vv = edge.getV();
			double w = edge.getWeight();
			// update the weights of vertices
			// and the spanning tree.
			if (weights[uu] + w <= weights[vv]) {
				weights[vv] = weights[uu] + w;
				spanningTree[vv] = edge;
			}
			// examine edges adjacent to vertex vv
			EdgeIterator iterator = graph.edgeIterator(vv);
			while (iterator.hasNext()) {
				Edge e = iterator.next();
				// insert into the queue only better edges
				if (weights[vv] + e.getWeight() < weights[e.getV()])
					queue.add(e);
			}
		}
	}

	/**
	 * Find the maximal possible weight of a path of the graph. This value is
	 * used in the Dijkstra algorithm. In a graph, there are at most n edges in
	 * each path.
	 * 
	 * @return
	 */
	private double maxWeight() {
		Edge[] edges = GraphUtilities.getWeightedEdges(graph);
		double max = 0;
		for (int i = 0; i < edges.length; i++) {
			if (max < edges[i].getWeight())
				max = edges[i].getWeight();
		}
		return max * graph.getNumberOfVertices();
	}

	/**
	 * Get the last spanning edge of a shortest path that leads to a vertex
	 * <tt>v</tt>.
	 * 
	 * @param v
	 * @return the last edge of a shortest path that leads to <tt>v</tt>.
	 * @see #dijkstra(int)
	 */
	public Edge getSpanningEdge(int v) {
		return spanningTree[v];
	}

	/**
	 * Get all the minimal spanning tree.
	 * 
	 * @return all the minimal spanning tree.
	 */
	public Edge[] getSpanningTree() {
		return spanningTree;
	}

	/**
	 * Get the minimal weight of the path from vertex zero to vertex u.
	 * 
	 * @param u
	 * @return the weight of shortest paths to <tt>u</tt>.
	 */
	public double getWeight(int u) {
		return weights[u];
	}

	/**
	 * Get a shortest path that leads to a vertex <tt>v</tt>. This method builds
	 * only a shortest path, it does not examine all possible shortest paths to
	 * <tt>v</tt>. A shortest path can be easily constructed from the minimal
	 * spanning tree. To find all possible shortest paths to <tt>v</tt>, use the
	 * method {@link #getAllShortestPaths(int)} instead.
	 * 
	 * @param v
	 * @return an array of vertex indices from the start vertex to <tt>v</tt>.
	 */
	public int[] getAShortestPath(int v) {
		// calculate number of edges
		// on the shortest path to v
		int nEdges = 0;
		Edge e = getSpanningEdge(v);
		while (e.getV() != e.getU()) {
			nEdges++;
			e = getSpanningEdge(e.getU());
		}
		// create the index array
		int shortestPath[] = new int[nEdges + 1];

		int k = nEdges;
		Edge ee = getSpanningEdge(v);
		while (ee.getV() != ee.getU()) {
			shortestPath[k] = ee.getV();
			ee = getSpanningEdge(ee.getU());
			k--;
		}
		// ok, return the result.
		//
		return shortestPath;
	}

	/**
	 * Get all shortest paths that lead to a vertex <tt>v</tt>. A shortest path
	 * is a linked-list of nodes from vertex startVertex
	 * 
	 * @see #getAShortestPath(int)
	 * @param v
	 * @return all shortest paths.
	 */
	public Node[] getAllShortestPaths(int v) {
		// init the path array
		path = new int[graph.getNumberOfVertices()];
		for (int i = 0; i < path.length; i++) {
			path[i] = -1;
		}
		k = 0;
		shortestPaths = new HashSet<Node>();
		// find all shortest path to v
		backtrack(v, weights[v]);
		return shortestPaths.toArray(new Node[shortestPaths.size()]);
	}

	private void backtrack(int v, double weight) {
		path[k] = v;
		if ((v == startVertex) && (Math.abs(weight - 0) < epsilon)) {
			// get a shortest path
			shortestPaths.add(getPath(path, k + 1));
		} else {
			k++;
			// get incoming edges of vertex v
			// and try
			Edge[] edges = getIncomingEdges(v);
			for (int u = 0; u < edges.length; u++) {
				Edge e = edges[u];
				double newWeight = weight - e.getWeight();
				if (newWeight >= 0) {
					backtrack(e.getU(), newWeight);
				}
			}
			// backtrack
			k--;
			path[k] = -1;
		}
	}

	/**
	 * Create a list of vertices from an array of size m of vertices.
	 * 
	 * @param a
	 * @param m
	 * @return
	 */
	private Node getPath(int[] a, int m) {
		Node list = new Node();
		for (int i = 0; i < m; i++) {
			// System.out.println("a[" + i + "] = " + a[i]); // DEBUG
			list = new Node(a[i], list);
		}
		// System.out.println(list); // DEBUG
		return list;
	}

	/**
	 * Get incoming edges to a vertex.
	 * 
	 * @param v
	 * @return an array of edges
	 */
	private Edge[] getIncomingEdges(int v) {
		List<Edge> edgeList = new ArrayList<Edge>();
		for (int u = 0; u < graph.getNumberOfVertices(); u++) {
			EdgeIterator iterator = graph.edgeIterator(u);
			while (iterator.hasNext()) {
				Edge e = iterator.next();
				if (e.getV() == v) {
					edgeList.add(e);
				}
			}
		}
		return edgeList.toArray(new Edge[edgeList.size()]);
	}
}
