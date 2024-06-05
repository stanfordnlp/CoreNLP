package edu.stanford.nlp.graph;

import java.util.*;

import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.MapFactory;

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

  final MapFactory<V, Map<V, List<E>>> outerMapFactory;
  final MapFactory<V, List<E>> innerMapFactory;

  public DirectedMultiGraph() {
    this(MapFactory.<V, Map<V, List<E>>>hashMapFactory(), MapFactory.<V, List<E>>hashMapFactory());
  }

  public DirectedMultiGraph(MapFactory<V, Map<V, List<E>>> outerMapFactory, MapFactory<V, List<E>> innerMapFactory) {
    this.outerMapFactory = outerMapFactory;
    this.innerMapFactory = innerMapFactory;
    this.outgoingEdges = outerMapFactory.newMap();
    this.incomingEdges = outerMapFactory.newMap();
  }

  /**
   * Creates a copy of the given graph. This will copy the entire data
   * structure (this may be slow!), but will not copy any of the edge
   * or vertex objects.
   *
   * @param graph The graph to copy into this object.
   */
  public DirectedMultiGraph(DirectedMultiGraph<V,E> graph) {
    this(graph.outerMapFactory, graph.innerMapFactory);
    for (Map.Entry<V, Map<V, List<E>>> map : graph.outgoingEdges.entrySet()) {
      Map<V, List<E>> edgesCopy = innerMapFactory.newMap();
      for (Map.Entry<V, List<E>> entry : map.getValue().entrySet()) {
        edgesCopy.put(entry.getKey(), Generics.newArrayList(entry.getValue()));
      }
      this.outgoingEdges.put(map.getKey(), edgesCopy);
    }
    for (Map.Entry<V, Map<V, List<E>>> map : graph.incomingEdges.entrySet()) {
      Map<V, List<E>> edgesCopy = innerMapFactory.newMap();
      for (Map.Entry<V, List<E>> entry : map.getValue().entrySet()) {
        edgesCopy.put(entry.getKey(), Generics.newArrayList(entry.getValue()));
      }
      this.incomingEdges.put(map.getKey(), edgesCopy);
    }
  }

  /**
   * Be careful hashing these. They are mutable objects, and changing the object
   * will throw off the hash code, messing up your hash table
   */
  public int hashCode() {
    return outgoingEdges.hashCode();
  }

  public boolean equals(Object that) {
    if (that == this)
      return true;
    if (!(that instanceof DirectedMultiGraph))
      return false;
    return outgoingEdges.equals(((DirectedMultiGraph<?, ?>) that).outgoingEdges);
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
    outgoingEdges.put(v, innerMapFactory.newMap());
    incomingEdges.put(v, innerMapFactory.newMap());
    return true;
  }

  private Map<V, List<E>> getOutgoingEdgesMap(V v) {
    Map<V, List<E>> map = outgoingEdges.get(v);
    if (map == null) {
      map = innerMapFactory.newMap();
      outgoingEdges.put(v, map);
      incomingEdges.put(v, innerMapFactory.newMap());
    }
    return map;
  }

  private Map<V, List<E>> getIncomingEdgesMap(V v) {
    Map<V, List<E>> map = incomingEdges.get(v);
    if (map == null) {
      outgoingEdges.put(v, innerMapFactory.newMap());
      map = innerMapFactory.newMap();
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
      outgoingList = new ArrayList<>();
      outgoingMap.put(dest, outgoingList);
    }

    List<E> incomingList = incomingMap.get(source);
    if (incomingList == null) {
      incomingList = new ArrayList<>();
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
    boolean foundOut = outgoingEdges.containsKey(source) && outgoingEdges.get(source).containsKey(dest) &&
        outgoingEdges.get(source).get(dest).remove(data);
    boolean foundIn = incomingEdges.containsKey(dest) && incomingEdges.get(dest).containsKey(source) &&
        incomingEdges.get(dest).get(source).remove(data);
    if (foundOut && !foundIn) {
      throw new AssertionError("Edge found in outgoing but not incoming");
    }
    if (foundIn && !foundOut) {
      throw new AssertionError("Edge found in incoming but not outgoing");
    }
    // TODO: cut down the number of .get calls
    if (outgoingEdges.containsKey(source) && (!outgoingEdges.get(source).containsKey(dest) || outgoingEdges.get(source).get(dest).size() == 0)) {
      outgoingEdges.get(source).remove(dest);
    }
    if (incomingEdges.containsKey(dest) && (!incomingEdges.get(dest).containsKey(source) || incomingEdges.get(dest).get(source).size() == 0)) {
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
    if (!outgoingEdges.containsKey(v)) { //noinspection unchecked
      return Collections.emptyList();
    }
    return CollectionUtils.flatten(outgoingEdges.get(v).values());
  }

  @Override
  public List<E> getIncomingEdges(V v) {
    if (!incomingEdges.containsKey(v)) { //noinspection unchecked
      return Collections.emptyList();
    }
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
    Set<V> neighbors = innerMapFactory.newSet();
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
    List<E> edges = new ArrayList<>();
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
    List<V> toDelete = new ArrayList<>();
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

    List<E> path = new ArrayList<>();
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
      return 0;
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
      return 0;
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

  /**
   * Deletes all duplicate edges.
   */
  public void deleteDuplicateEdges() {
    for (V vertex : getAllVertices()) {
      for (V vertex2 : outgoingEdges.get(vertex).keySet()) {
        List<E> data = outgoingEdges.get(vertex).get(vertex2);
        Set<E> deduplicatedData = new TreeSet<>(data);
        data.clear();
        data.addAll(deduplicatedData);
      }
      for (V vertex2 : incomingEdges.get(vertex).keySet()) {
        List<E> data = incomingEdges.get(vertex).get(vertex2);
        Set<E> deduplicatedData = new TreeSet<>(data);
        data.clear();
        data.addAll(deduplicatedData);
      }
    }
  }

  
  public Iterator<E> incomingEdgeIterator(final V vertex) {
    return new EdgeIterator<>(vertex, incomingEdges, outgoingEdges);
  }

  public Iterable<E> incomingEdgeIterable(final V vertex) {
    return () -> new EdgeIterator<>(vertex, incomingEdges, outgoingEdges);
  }

  public Iterator<E> outgoingEdgeIterator(final V vertex) {
    return new EdgeIterator<>(vertex, outgoingEdges, incomingEdges);
  }

  public Iterable<E> outgoingEdgeIterable(final V vertex) {
    return () -> new EdgeIterator<>(vertex, outgoingEdges, incomingEdges);
  }

  public Iterator<E> edgeIterator() {
    return new EdgeIterator<>(this);
  }

  public Iterable<E> edgeIterable() {
    return () -> new EdgeIterator<>(DirectedMultiGraph.this);
  }
  

  /**
   * This class handles either iterating over a single vertex's
   * connections or over all connections in a graph.
   */
  static class EdgeIterator<V, E> implements Iterator<E> {
    private final Map<V, Map<V, List<E>>> reverseEdges;
    /** when iterating over the whole graph, this iterates over nodes */
    private Iterator<Map.Entry<V, Map<V, List<E>>>> vertexIterator;
    /** for a given node, this iterates over its neighbors */
    private Iterator<Map.Entry<V, List<E>>> connectionIterator;
    /** given the neighbor of a node, this iterates over all its connections */
    private Iterator<E> edgeIterator;

    private V currentSource = null;
    private V currentTarget = null;
    private E currentEdge = null;
    private boolean hasNext = true;


    public EdgeIterator(DirectedMultiGraph<V, E> graph) {
      vertexIterator = graph.outgoingEdges.entrySet().iterator();
      reverseEdges = graph.incomingEdges;
    }

    public EdgeIterator(V startVertex, Map<V, Map<V, List<E>>> source,
                        Map<V, Map<V, List<E>>> reverseEdges) {
      currentSource = startVertex;
      Map<V, List<E>> neighbors = source.get(startVertex);
      if (neighbors != null) {
        vertexIterator = null;
        connectionIterator = neighbors.entrySet().iterator();
      }
      this.reverseEdges = reverseEdges;
    }

    @Override
    public boolean hasNext() {
      primeIterator();
      return hasNext;
    }

    @Override
    public E next() {
      if (!hasNext()) {
        throw new NoSuchElementException("Graph edge iterator exhausted.");
      }
      currentEdge = edgeIterator.next();
      return currentEdge;
    }

    private void primeIterator() {
      while (true) {
        if (edgeIterator != null && edgeIterator.hasNext()) {
          hasNext = true;  // technically, we shouldn't need to put this here, but let's be safe
          return;
        } else if (connectionIterator != null && connectionIterator.hasNext()) {
          Map.Entry<V, List<E>> nextConnection = connectionIterator.next();
          edgeIterator = nextConnection.getValue().iterator();
          currentTarget = nextConnection.getKey();
        } else if (vertexIterator != null && vertexIterator.hasNext()) {
          Map.Entry<V, Map<V, List<E>>> nextVertex = vertexIterator.next();
          connectionIterator = nextVertex.getValue().entrySet().iterator();
          currentSource = nextVertex.getKey();
        } else {
          hasNext = false;
          return;
        }
      }
    }

    @Override
    public void remove() {
      if (currentEdge != null) {
        reverseEdges.get(currentTarget).get(currentSource).remove(currentEdge);
        edgeIterator.remove();

        if (reverseEdges.get(currentTarget).get(currentSource) != null &&
            reverseEdges.get(currentTarget).get(currentSource).size() == 0) {
          connectionIterator.remove();
          reverseEdges.get(currentTarget).remove(currentSource);
          // TODO: may not be necessary to set this to null
          edgeIterator = null;
        }
      }
    }
  }

  /**
   * Topological sort of the graph.
   * <br>
   * This method uses the depth-first search implementation of
   * topological sort.
   * Topological sorting only works if the graph is acyclic.
   *
   * @return A sorted list of the vertices
   * @throws CyclicGraphException (a subtype of IllegalStateException) if this graph is not a DAG
   */
  public List<V> topologicalSort() {
    List<V> result = Generics.newArrayList();
    Set<V> temporary = outerMapFactory.newSet();
    Set<V> permanent = outerMapFactory.newSet();
    for (V vertex : getAllVertices()) {
      if (!temporary.contains(vertex)) {
        topologicalSortHelper(vertex, temporary, permanent, result);
      }
    }
    Collections.reverse(result);
    return result;
  }

  private void topologicalSortHelper(V vertex, Set<V> temporary, Set<V> permanent, List<V> result) {
    temporary.add(vertex);
    Map<V, List<E>> neighborMap = outgoingEdges.get(vertex);
    if (neighborMap != null) {
      for (V neighbor : neighborMap.keySet()) {
        if (permanent.contains(neighbor)) {
          continue;
        }
        if (temporary.contains(neighbor)) {
          throw new CyclicGraphException("This graph has cycles. Topological sort not possible", this);
        }
        topologicalSortHelper(neighbor, temporary, permanent, result);
      }
    }
    result.add(vertex);
    permanent.add(vertex);
  }

  /**
   * Cast this multi-graph as a map from vertices, to the outgoing data along edges out of those vertices.
   *
   * @return A map representation of the graph.
   */
  public Map<V, List<E>> toMap() {
    Map<V, List<E>> map = innerMapFactory.newMap();
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

    List<String> lines = new ArrayList<>();
    for (V vertex : outgoingEdges.keySet()) {
      lines.add("  " + vertex + '\n');
    }
    Collections.sort(lines);
    for (String line : lines) {
      s.append(line);
    }

    s.append("Edges:\n");
    lines = new ArrayList<>();
    for (V source : outgoingEdges.keySet()) {
      for (V dest : outgoingEdges.get(source).keySet()) {
        for (E edge : outgoingEdges.get(source).get(dest)) {
          lines.add("  " + source + " -> " + dest + " : " + edge + "\n");
        }
      }
    }
    Collections.sort(lines);
    for (String line : lines) {
      s.append(line);
    }

    s.append('}');
    return s.toString();
  }

  private static final long serialVersionUID = 609823567298345145L;

}
