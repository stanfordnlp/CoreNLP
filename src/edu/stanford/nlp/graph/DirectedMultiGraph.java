package edu.stanford.nlp.graph;

import java.util.*;

import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;

/**
 * Simple graph library; this is directed for now. This class focuses on time
 * efficiency rather than memory efficiency.
 *
 * @author sonalg
 * @author John Bauer
 *
 * @param <V>
 *          Type of vertices
 * @param <E>
 *          Type of edges.
 */

public class DirectedMultiGraph<V, E> implements Graph<V, E> /* Serializable */{

  final Map<V, Map<V, List<E>>> outgoingEdges;

  final Map<V, Map<V, List<E>>> incomingEdges;

  public DirectedMultiGraph() {
    outgoingEdges = Generics.newHashMap();
    incomingEdges = Generics.newHashMap();
  }

  public DirectedMultiGraph(DirectedMultiGraph<V,E> graph) {
    outgoingEdges = Generics.newHashMap(graph.outgoingEdges);
    incomingEdges = Generics.newHashMap(graph.incomingEdges);
  }

  /**
   * Be careful hashing these. They are mutable objects, and changing the object
   * will throw off the hash code, messing up your hash table
   */
  public int hashCode() {
    return outgoingEdges.hashCode();
  }

  @SuppressWarnings("unchecked")
  public boolean equals(Object that) {
    if (that == this)
      return true;
    if (!(that instanceof DirectedMultiGraph))
      return false;
    return outgoingEdges.equals(((DirectedMultiGraph) that).outgoingEdges);
  }

  /**
   * For adding a zero degree vertex
   *
   * @param v
   */
  @Override
  public boolean addVertex(V v) {
    if (outgoingEdges.containsKey(v))
      return false;
    outgoingEdges.put(v, Generics.<V, List<E>>newHashMap());
    incomingEdges.put(v, Generics.<V, List<E>>newHashMap());
    return true;
  }

  private Map<V, List<E>> getOutgoingEdgesMap(V v) {
    Map<V, List<E>> map = outgoingEdges.get(v);
    if (map == null) {
      map = Generics.<V, List<E>>newHashMap();
      outgoingEdges.put(v, map);
      incomingEdges.put(v, Generics.<V, List<E>>newHashMap());
    }
    return map;
  }

  private Map<V, List<E>> getIncomingEdgesMap(V v) {
    Map<V, List<E>> map = incomingEdges.get(v);
    if (map == null) {
      outgoingEdges.put(v, Generics.<V, List<E>>newHashMap());
      map = Generics.<V, List<E>>newHashMap();
      incomingEdges.put(v, map);
    }
    return map;
  }

  /**
   * adds vertices (if not already in the graph) and the edge between them
   *
   * @param source
   * @param dest
   * @param data
   */
  @Override
  public void add(V source, V dest, E data) {
    Map<V, List<E>> outgoingMap = getOutgoingEdgesMap(source);
    Map<V, List<E>> incomingMap = getIncomingEdgesMap(dest);

    List<E> outgoingList = outgoingMap.get(dest);
    if (outgoingList == null) {
      outgoingList = new ArrayList<E>();
      outgoingMap.put(dest, outgoingList);
    }

    List<E> incomingList = incomingMap.get(source);
    if (incomingList == null) {
      incomingList = new ArrayList<E>();
      incomingMap.put(source, incomingList);
    }

    outgoingList.add(data);
    incomingList.add(data);
  }

  @Override
  public boolean removeEdges(V source, V dest) {
    if (!outgoingEdges.containsKey(source)) {
      return false;
    }
    if (!incomingEdges.containsKey(dest)) {
      return false;
    }
    if (!outgoingEdges.get(source).containsKey(dest)) {
      return false;
    }
    outgoingEdges.get(source).remove(dest);
    incomingEdges.get(dest).remove(source);
    return true;
  }

  @Override
  public boolean removeEdge(V source, V dest, E data) {
    if (!outgoingEdges.containsKey(source)) {
      return false;
    }
    if (!incomingEdges.containsKey(dest)) {
      return false;
    }
    if (!outgoingEdges.get(source).containsKey(dest)) {
      return false;
    }
    boolean foundOut = outgoingEdges.get(source).get(dest).remove(data);
    boolean foundIn = incomingEdges.get(dest).get(source).remove(data);
    if (foundOut && !foundIn) {
      throw new AssertionError("Edge found in outgoing but not incoming");
    }
    if (foundIn && !foundOut) {
      throw new AssertionError("Edge found in incoming but not outgoing");
    }
    // TODO: cut down the number of .get calls
    if (outgoingEdges.get(source).get(dest).size() == 0) {
      outgoingEdges.get(source).remove(dest);
    }
    if (incomingEdges.get(dest).get(source).size() == 0) {
      incomingEdges.get(dest).remove(source);
    }
    return foundOut;
  }

  /**
   * remove a vertex (and its edges) from the graph.
   *
   * @param vertex
   * @return true if successfully removes the node
   */
  @Override
  public boolean removeVertex(V vertex) {
    if (!outgoingEdges.containsKey(vertex)) {
      return false;
    }
    for (V other : outgoingEdges.get(vertex).keySet()) {
      incomingEdges.get(other).remove(vertex);
    }
    for (V other : incomingEdges.get(vertex).keySet()) {
      outgoingEdges.get(other).remove(vertex);
    }
    outgoingEdges.remove(vertex);
    incomingEdges.remove(vertex);
    return true;
  }

  @Override
  public boolean removeVertices(Collection<V> vertices) {
    boolean changed = false;
    for (V v : vertices) {
      if (removeVertex(v)) {
        changed = true;
      }
    }
    return changed;
  }

  @Override
  public int getNumVertices() {
    return outgoingEdges.size();
  }

  @Override
  public List<E> getOutgoingEdges(V v) {
    return CollectionUtils.flatten(outgoingEdges.get(v).values());
  }

  @Override
  public List<E> getIncomingEdges(V v) {
    return CollectionUtils.flatten(incomingEdges.get(v).values());
  }

  @Override
  public int getNumEdges() {
    int count = 0;
    for (Map.Entry<V, Map<V, List<E>>> sourceEntry : outgoingEdges.entrySet()) {
      for (Map.Entry<V, List<E>> destEntry : sourceEntry.getValue().entrySet()) {
        count += destEntry.getValue().size();
      }
    }
    return count;
  }

  @Override
  public Set<V> getParents(V vertex) {
    Map<V, List<E>> parentMap = incomingEdges.get(vertex);
    if (parentMap == null)
      return null;
    return Collections.unmodifiableSet(parentMap.keySet());
  }

  @Override
  public Set<V> getChildren(V vertex) {
    Map<V, List<E>> childMap = outgoingEdges.get(vertex);
    if (childMap == null)
      return null;
    return Collections.unmodifiableSet(childMap.keySet());
  }

  /**
   * Gets both parents and children nodes
   *
   * @param v
   */
  @Override
  public Set<V> getNeighbors(V v) {
    // TODO: pity we have to copy the sets... is there a combination set?
    Set<V> children = getChildren(v);
    Set<V> parents = getParents(v);

    if (children == null && parents == null)
      return null;
    Set<V> neighbors = Generics.newHashSet();
    neighbors.addAll(children);
    neighbors.addAll(parents);
    return neighbors;
  }

  /**
   * clears the graph, removes all edges and nodes
   */
  @Override
  public void clear() {
    incomingEdges.clear();
    outgoingEdges.clear();
  }

  @Override
  public boolean containsVertex(V v) {
    return outgoingEdges.containsKey(v);
  }

  /**
   * only checks if there is an edge from source to dest. To check if it is
   * connected in either direction, use isNeighbor
   *
   * @param source
   * @param dest
   */
  @Override
  public boolean isEdge(V source, V dest) {
    Map<V, List<E>> childrenMap = outgoingEdges.get(source);
    if (childrenMap == null || childrenMap.isEmpty())
      return false;
    List<E> edges = childrenMap.get(dest);
    if (edges == null || edges.isEmpty())
      return false;
    return edges.size() > 0;
  }

  @Override
  public boolean isNeighbor(V source, V dest) {
    return isEdge(source, dest) || isEdge(dest, source);
  }

  @Override
  public Set<V> getAllVertices() {
    return Collections.unmodifiableSet(outgoingEdges.keySet());
  }

  @Override
  public List<E> getAllEdges() {
    List<E> edges = new ArrayList<E>();
    for (Map<V, List<E>> e : outgoingEdges.values()) {
      for (List<E> ee : e.values()) {
        edges.addAll(ee);
      }
    }
    return edges;
  }

  /**
   * False if there are any vertices in the graph, true otherwise. Does not care
   * about the number of edges.
   */
  @Override
  public boolean isEmpty() {
    return outgoingEdges.isEmpty();
  }

  /**
   * Deletes nodes with zero incoming and zero outgoing edges
   */
  @Override
  public void removeZeroDegreeNodes() {
    List<V> toDelete = new ArrayList<V>();
    for (V vertex : outgoingEdges.keySet()) {
      if (outgoingEdges.get(vertex).isEmpty() && incomingEdges.get(vertex).isEmpty()) {
        toDelete.add(vertex);
      }
    }
    for (V vertex : toDelete) {
      outgoingEdges.remove(vertex);
      incomingEdges.remove(vertex);
    }
  }

  @Override
  public List<E> getEdges(V source, V dest) {
    Map<V, List<E>> childrenMap = outgoingEdges.get(source);
    if (childrenMap == null) {
      return Collections.emptyList();
    }
    List<E> edges = childrenMap.get(dest);
    if (edges == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(edges);
  }

  /**
   * direction insensitive (the paths can go "up" or through the parents)
   */
  public List<V> getShortestPath(V node1, V node2) {
    if (!outgoingEdges.containsKey(node1) || !outgoingEdges.containsKey(node2)) {
      return null;
    }
    return getShortestPath(node1, node2, false);
  }

  public List<E> getShortestPathEdges(V node1, V node2) {
    return convertPath(getShortestPath(node1, node2), false);
  }

  /**
   * can specify the direction sensitivity
   *
   * @param node1
   * @param node2
   * @param directionSensitive
   *          - whether the path can go through the parents
   * @return the list of nodes you get through to get there
   */
  public List<V> getShortestPath(V node1, V node2, boolean directionSensitive) {
    if (!outgoingEdges.containsKey(node1) || !outgoingEdges.containsKey(node2)) {
      return null;
    }
    return DijkstraShortestPath.getShortestPath(this, node1, node2, directionSensitive);
  }

  public List<E> getShortestPathEdges(V node1, V node2, boolean directionSensitive) {
    return convertPath(getShortestPath(node1, node2, directionSensitive), directionSensitive);
  }

  public List<E> convertPath(List<V> nodes, boolean directionSensitive) {
    if (nodes == null)
      return null;

    if (nodes.size() <= 1)
      return Collections.emptyList();

    List<E> path = new ArrayList<E>();
    Iterator<V> nodeIterator = nodes.iterator();
    V previous = nodeIterator.next();
    while (nodeIterator.hasNext()) {
      V next = nodeIterator.next();
      E connection = null;
      List<E> edges = getEdges(previous, next);
      if (edges.size() == 0 && !directionSensitive) {
        edges = getEdges(next, previous);
      }
      if (edges.size() > 0) {
        connection = edges.get(0);
      } else {
        throw new IllegalArgumentException("Path given with missing " + "edge connection");
      }
      path.add(connection);
      previous = next;
    }
    return path;
  }

  @Override
  public int getInDegree(V vertex) {
    if (!containsVertex(vertex)) {
      throw new IllegalArgumentException("Unknown vertex " + vertex);
    }
    int result = 0;
    Map<V, List<E>> incoming = incomingEdges.get(vertex);
    for (List<E> edges : incoming.values()) {
      result += edges.size();
    }
    return result;
  }

  @Override
  public int getOutDegree(V vertex) {
    int result = 0;
    Map<V, List<E>> outgoing = outgoingEdges.get(vertex);
    if (outgoing == null) {
      throw new IllegalArgumentException("Unknown vertex " + vertex);
    }
    for (List<E> edges : outgoing.values()) {
      result += edges.size();
    }
    return result;
  }

  @Override
  public List<Set<V>> getConnectedComponents() {
    return ConnectedComponents.getConnectedComponents(this);
  }

  public Iterator<E> incomingEdgeIterator(final V vertex) {
    return new EdgeIterator<V, E>(incomingEdges, vertex);
  }

  public Iterable<E> incomingEdgeIterable(final V vertex) {
    return new Iterable<E>() {
      @Override
      public Iterator<E> iterator() {
        return new EdgeIterator<V, E>(incomingEdges, vertex);
      }
    };
  }

  public Iterator<E> outgoingEdgeIterator(final V vertex) {
    return new EdgeIterator<V, E>(outgoingEdges, vertex);
  }

  public Iterable<E> outgoingEdgeIterable(final V vertex) {
    return new Iterable<E>() {
      @Override
      public Iterator<E> iterator() {
        return new EdgeIterator<V, E>(outgoingEdges, vertex);
      }
    };
  }

  public Iterator<E> edgeIterator() {
    return new EdgeIterator<V, E>(this);
  }

  public Iterable<E> edgeIterable() {
    return new Iterable<E>() {
      @Override
      public Iterator<E> iterator() {
        return new EdgeIterator<V, E>(DirectedMultiGraph.this);
      }
    };
  }

  static class EdgeIterator<V, E> implements Iterator<E> {
    private Iterator<Map<V, List<E>>> vertexIterator;
    private Iterator<List<E>> connectionIterator;
    private Iterator<E> edgeIterator;

    E next;

    public EdgeIterator(DirectedMultiGraph<V, E> graph) {
      vertexIterator = graph.outgoingEdges.values().iterator();
      primeNext();
    }

    public EdgeIterator(Map<V, Map<V, List<E>>> source, V startVertex) {
      Map<V, List<E>> neighbors = source.get(startVertex);
      if (neighbors == null) {
        return;
      }
      connectionIterator = neighbors.values().iterator();
      primeNext();
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public E next() {
      if (next == null) {
        throw new NoSuchElementException("Graph edge iterator exhausted.");
      }
      E value = next;
      primeNext();
      return value;
    }

    private void primeNext() {
      while (true) {
        if (edgeIterator != null && edgeIterator.hasNext()) {
          next = edgeIterator.next();
          break;
        }

        if (connectionIterator != null && connectionIterator.hasNext()) {
          edgeIterator = connectionIterator.next().iterator();
          continue;
        }

        if (vertexIterator != null && vertexIterator.hasNext()) {
          connectionIterator = vertexIterator.next().values().iterator();
          continue;
        }

        next = null;
        break;
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Cast this multi-graph as a map from vertices, to the outgoing data along edges out of those vertices.
   *
   * @return A map representation of the graph.
   */
  public Map<V, List<E>> toMap() {
    Map<V, List<E>> map = new HashMap<V, List<E>>();
    for (V vertex : getAllVertices()) {
      map.put(vertex, getOutgoingEdges(vertex));
    }
    return map;
  }

  @Override
  public String toString() {
    StringBuilder s = new StringBuilder();
    s.append("{\n");
    s.append("Vertices:\n");
    for (V vertex : outgoingEdges.keySet()) {
      s.append("  ").append(vertex).append('\n');
    }
    s.append("Edges:\n");
    for (V source : outgoingEdges.keySet()) {
      for (V dest : outgoingEdges.get(source).keySet()) {
        for (E edge : outgoingEdges.get(source).get(dest)) {
          s.append("  ").append(source).append(" -> ").append(dest).append(" : ").append(edge).append('\n');
        }
      }
    }
    s.append('}');
    return s.toString();
  }

  private static final long serialVersionUID = 609823567298345145L;

}
