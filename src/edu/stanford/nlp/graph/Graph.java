package edu.stanford.nlp.graph;

import java.io.Serializable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Sonal Gupta
 * @param <V> Type of the vertices
 * @param <E> Type of the edges
 */
public interface Graph<V,E> extends Serializable {

  /**
   * Adds vertices (if not already in the graph) and the edge between them.
   * (If the graph is undirected, the choice of which vertex to call
   * source and dest is arbitrary.)
   *
   * @param source
   * @param dest
   * @param data
   */
  public abstract void add(V source, V dest, E data);
  /**
   * For adding a zero degree vertex
   *
   * @param v
   */

  public abstract boolean addVertex(V v);



  public abstract boolean removeEdges(V source, V dest);

  public abstract boolean removeEdge(V source, V dest, E data);

  /**
   * remove a vertex (and its edges) from the graph.
   *
   * @param vertex
   * @return true if successfully removes the node
   */
  public abstract boolean removeVertex(V vertex);

  public abstract boolean removeVertices(Collection<V> vertices);

  public abstract int getNumVertices();

  /**
   * for undirected graph, it is just the edges from the node
   * @param v
   */
  public abstract List<E> getOutgoingEdges(V v);

  /**
   * for undirected graph, it is just the edges from the node
   * @param v
   */
  public abstract List<E> getIncomingEdges(V v);

  public abstract int getNumEdges();

  /**
   * for undirected graph, it is just the neighbors
   * @param vertex
   */
  public abstract Set<V> getParents(V vertex);

  /**
   * for undirected graph, it is just the neighbors
   * @param vertex
   */

  public abstract Set<V> getChildren(V vertex);

  public abstract Set<V> getNeighbors(V v);

  /**
   * clears the graph, removes all edges and nodes
   */
  public abstract void clear();

  public abstract boolean containsVertex(V v);

  /**
   * only checks if there is an edge from source to dest. To check if it is
   * connected in either direction, use isNeighbor
   *
   * @param source
   * @param dest
   */
  public abstract boolean isEdge(V source, V dest);

  public abstract boolean isNeighbor(V source, V dest);

  public abstract Set<V> getAllVertices();

  public abstract List<E> getAllEdges();

  /**
   * False if there are any vertices in the graph, true otherwise. Does not care
   * about the number of edges.
   */
  public abstract boolean isEmpty();

  /**
   * Deletes nodes with zero incoming and zero outgoing edges
   */
  public abstract void removeZeroDegreeNodes();

  public abstract List<E> getEdges(V source, V dest);


  /**
   * for undirected graph, it should just be the degree
   * @param vertex
   */
  public abstract int getInDegree(V vertex);

  public abstract int getOutDegree(V vertex);

  public abstract List<Set<V>> getConnectedComponents();

}
