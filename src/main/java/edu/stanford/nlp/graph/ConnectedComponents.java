package edu.stanford.nlp.graph;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;

/**
 * Finds connected components in the graph, currently uses inefficient list for
 * variable 'verticesLeft'. It might give a problem for big graphs
 *
 * @author sonalg 08/08/11
 */
public class ConnectedComponents<V, E> {

  public static <V, E> List<Set<V>> getConnectedComponents(Graph<V, E> graph) {
    List<Set<V>> ccs = new ArrayList<>();
    LinkedList<V> todo = new LinkedList<>();
    // TODO: why not a set?
    List<V> verticesLeft = CollectionUtils.toList(graph.getAllVertices());
    while (verticesLeft.size() > 0) {
      todo.add(verticesLeft.get(0));
      verticesLeft.remove(0);
      ccs.add(bfs(todo, graph, verticesLeft));
    }
    return ccs;
  }

  private static <V, E> Set<V> bfs(LinkedList<V> todo, Graph<V, E> graph, List<V> verticesLeft) {
    Set<V> cc = Generics.newHashSet();
    while (todo.size() > 0) {
      V node = todo.removeFirst();
      cc.add(node);
      for (V neighbor : graph.getNeighbors(node)) {
        if (verticesLeft.contains(neighbor)) {
          cc.add(neighbor);
          todo.add(neighbor);
          verticesLeft.remove(neighbor);
        }
      }
    }

    return cc;
  }

}
