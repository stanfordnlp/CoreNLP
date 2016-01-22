package edu.stanford.nlp.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.util.BinaryHeapPriorityQueue;
import edu.stanford.nlp.util.Generics;

public class DijkstraShortestPath {
  private DijkstraShortestPath() {} // static method only

  public static <V, E> List<V> getShortestPath(Graph<V, E> graph,
                                               V node1, V node2, 
                                               boolean directionSensitive) {
    if (node1.equals(node2)) {
      return Collections.singletonList(node2);
    }

    Set<V> visited = Generics.newHashSet();
    
    Map<V, V> previous = Generics.newHashMap();
    
    BinaryHeapPriorityQueue<V> unsettledNodes =
            new BinaryHeapPriorityQueue<>();

    unsettledNodes.add(node1, 0);

    while (unsettledNodes.size() > 0) {
      double distance = unsettledNodes.getPriority();
      V u = unsettledNodes.removeFirst();
      visited.add(u);

      if (u.equals(node2))
        break;

      unsettledNodes.remove(u);

      Set<V> candidates = ((directionSensitive) ? 
                           graph.getChildren(u) : graph.getNeighbors(u));
      for (V candidate : candidates) {
        double alt = distance - 1;
        // nodes not already present will have a priority of -inf
        if (alt > unsettledNodes.getPriority(candidate) &&
            !visited.contains(candidate)) {
          unsettledNodes.relaxPriority(candidate, alt);
          previous.put(candidate, u);
        }
      }
    }
    if (!previous.containsKey(node2))
      return null;
    ArrayList<V> path = new ArrayList<>();
    path.add(node2);
    V n = node2;
    while (previous.containsKey(n)) {
      path.add(previous.get(n));
      n = previous.get(n);
    }
    Collections.reverse(path);
    return path;
  }

}
