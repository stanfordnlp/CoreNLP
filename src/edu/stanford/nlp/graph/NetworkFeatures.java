package edu.stanford.nlp.graph;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;

/**
 * get some network statistics
 * 
 * @author sonalg
 * 
 */
public class NetworkFeatures {

  /**
   * For nodes A, B, C, when A is trying to make an edge with C (A->C), what
   * type of edges do A to B and B to C have transitive: TRANSITIVE: A-> B, B->C
   * REVERSE-TRANSITIVE: B->A, C-> B COSUPPORTED: B->A, B->C COSUPPORTING: A->B,
   * C->B POPULARITY: B->C GENEROSITY: A->B
   * 
   * @author sonalg
   * 
   */
  public enum ThreeNodeStat {
    TRANSITIVE, REVTRANSITIVE, COSUPPORTED, COSUPPORTING, POPULARITY, GENEROSITY;
  };

  public static <V, E> Set<ThreeNodeStat> find3NodeStats(Graph<V, E> graph, V a, V b, V c) {
    Set<ThreeNodeStat> stats = Generics.newHashSet();

    if (graph.isEdge(a, b)) {
      stats.add(ThreeNodeStat.GENEROSITY);
      if (graph.isEdge(b, c)) {
        stats.add(ThreeNodeStat.TRANSITIVE);
      } else if (graph.isEdge(c, b)) {
        stats.add(ThreeNodeStat.COSUPPORTING);
      } else
        stats.add(ThreeNodeStat.GENEROSITY);
    }

    if (graph.isEdge(b, a)) {

      if (graph.isEdge(c, b))
        stats.add(ThreeNodeStat.REVTRANSITIVE);
      else if (graph.isEdge(b, c))
        stats.add(ThreeNodeStat.COSUPPORTED);
    }

    if (graph.isEdge(b, c))
      stats.add(ThreeNodeStat.POPULARITY);
    return stats;
  }

  public static <V, E> int numRevTransitive(DirectedMultiGraph<V, E> graph, V source, V dest) {
    return CollectionUtils.intersection(graph.getParents(source), graph.getChildren(dest)).size();
  }

  public static <V, E> int numCoSupporting(DirectedMultiGraph<V, E> graph, V source, V dest) {
    return CollectionUtils.intersection(graph.getChildren(source), graph.getChildren(dest)).size();
  }

  public static <V, E> int numCoSupported(DirectedMultiGraph<V, E> graph, V source, V dest) {
    return CollectionUtils.intersection(graph.getParents(source), graph.getParents(dest)).size();
  }

  /**
   * this assumes undirected edges i.e. directionality this function is
   * basically transtivity score of directionSensitive is true
   * 
   * @param <V>
   * @param <E>
   * @param graph
   * @param node1
   * @param node2
   */
  public static <V, E> int numMutualFriends(Graph<V, E> graph, V node1, V node2, boolean directionSensitive) {
    if(!graph.containsVertex(node1))
      System.out.println("how come graph does not contain " + node1);
    
    if(!graph.containsVertex(node2))
      System.out.println("how come graph does not contain " + node2);
    
    if (directionSensitive) {
      return CollectionUtils.intersection(graph.getChildren(node1), graph.getParents(node2)).size();
    } else
      return CollectionUtils.intersection(graph.getNeighbors(node1), graph.getNeighbors(node2)).size();
  }

  public static <V, E> boolean ifPathExists(Graph<V, E> graph, V node1, V node2, int maxDepth, boolean directionSensitive) {
    if (graph.getChildren(node1).contains(node2))
      return true;

    if (directionSensitive) {
      Queue<V> nodes = new LinkedList<V>();
      V vertex = node1;
      int i = 0;
      nodes.addAll(graph.getChildren(vertex));

      while (!nodes.isEmpty()) {
        V child = nodes.poll();
        i++;
        if (i > maxDepth - 1)
          break;
        if (child.equals(node2))
          return true;
        boolean changed = nodes.addAll(graph.getChildren(child));
        if (changed == false)
          return false;
        if (nodes.contains(node2))
          return true;
        vertex = child;
      }
    } else {
      Queue<V> nodes = new LinkedList<V>();
      V vertex = node1;
      int i = 0;
      nodes.addAll(graph.getNeighbors(vertex));

      while (!nodes.isEmpty()) {
        V child = nodes.poll();
        i++;
        if (i > maxDepth - 1)
          break;
        if (child.equals(node2))
          return true;
        boolean changed = nodes.addAll(graph.getNeighbors(child));
        if (changed == false)
          return false;
        if (nodes.contains(node2))
          return true;
        vertex = child;
      }

    }
    return false;
  }

  public static <V, E> int numCoSupported(Graph<V, E> graph, V node1, V node2, boolean directionSensitive) {
    // TODO
    int numCoSup = 0;
    if (directionSensitive) {
      for (V parent : graph.getParents(node1)) {
        if (!parent.equals(node2) && graph.getChildren(parent).contains(node2))
          numCoSup++;
      }
    } else {
      // this is like asking if there is any mutual friend
      for (V parent : graph.getNeighbors(node1)) {
        if (!parent.equals(node2) && graph.getNeighbors(parent).contains(node2))
          numCoSup++;
      }
    }
    return numCoSup;
  }

  public static <V, E> boolean ifCoSupported(Graph<V, E> graph, V node1, V node2, boolean directionSensitive) {
    // TODO
    if (graph.isEdge(node1, node2))
      return false;
    if (directionSensitive) {
      for (V parent : graph.getParents(node1)) {
        if (!parent.equals(node2) && graph.getChildren(parent).contains(node2))
          return true;
      }
    } else {
      // this is like asking if there is any mutual friend
      for (V parent : graph.getNeighbors(node1)) {
        if (!parent.equals(node2) && graph.getNeighbors(parent).contains(node2))
          return true;
      }
    }
    return false;
  }

  public static <V, E> double jaccardScore(Graph<V, E> graph, V node1, V node2, boolean directionSensitive) {
    Set<V> neighbors1 = null;
    Set<V> neighbors2 = null;
    if (directionSensitive) {
      neighbors1 = graph.getChildren(node1);
      neighbors2 = graph.getParents(node2);
    } else {
      neighbors1 = graph.getNeighbors(node1);
      neighbors2 = graph.getNeighbors(node2);
    }
    return (CollectionUtils.intersection(neighbors1, neighbors2).size() / ((double) CollectionUtils.unionAsSet(neighbors1, neighbors2).size()));
  }

  public static <V, E> double adamicAdarScore(Graph<V, E> graph, V node1, V node2, boolean directionSensitive) throws Exception {
    if (!directionSensitive)
      throw new Exception("not implemented");
    double score = 0;
    for (V v : CollectionUtils.intersection(graph.getChildren(node1), graph.getParents(node2))) {
      score += -Math.log(graph.getOutDegree(v));
    }
    return score;
  }

  public static <V, E> double katzScore(Graph<V, E> graph, V node1, V node2, boolean directionSensitive) throws Exception {
    int numOneHop = 0;
    int numTwoHops = 0;
    int numThreeHops = 0;
    double beta = 0.5;
    if (directionSensitive) {
      for (V child : graph.getChildren(node1)) {
        for (V grand : graph.getChildren(child)) {
          if (grand.equals(node2)) {
            numOneHop++;
            continue;
          }
          if (grand.equals(node1) || grand.equals(child))
            continue;
          for (V gg : graph.getChildren(grand)) {
            if (gg.equals(node2)) {
              numTwoHops++;
              continue;
            }
            if (gg.equals(node1) || gg.equals(grand) || gg.equals(child))
              continue;
            for (V ggg : graph.getChildren(gg)) {
              if (ggg.equals(node2)) {
                numThreeHops++;
                break;
              }
            }
          }

        }
      }
    } else
      throw new Exception("not implemented");

    return numOneHop * beta + numTwoHops * beta * beta + numThreeHops * beta * beta * beta;
  }

  public void get() {

  }
}
