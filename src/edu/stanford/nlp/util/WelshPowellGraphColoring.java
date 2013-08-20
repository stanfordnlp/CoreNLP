package edu.stanford.nlp.util;

import java.util.*;

/**
 * This class implements the Welsh-Powell heuristics for graph coloring
 * It's guaranteed that the number of colors will not be greater than the
 * maximum node degree plus one.
 *
 * @author Mengqiu Wang
 */

public class WelshPowellGraphColoring {
  
  private static boolean VERBOSE = false;
  /**
   * Colors the graph using Welsh-Powell heuristics
   *
   * @param graph a map where each key is a node id, and the mapping value is
   * a list of edges represented by node ids 
   * @return a list where each member is a collection of nodes that share the
   * same color
   */
  public static List<List<Integer>> colorGraph(final Map<Integer, Set<Integer>> graph) {
    List<List<Integer>> partition = new ArrayList<List<Integer>>();
    List<Integer> nodes = new ArrayList<Integer>(graph.keySet());
    Collections.sort(nodes, new Comparator<Integer>() {
      public int compare(Integer a, Integer b) {
        return graph.get(b).size() - graph.get(a).size();
      }
    });
    Set<Integer> contigent = new HashSet<Integer>();
    Set<Integer> colored = new HashSet<Integer>();
    while (!nodes.isEmpty()) {
      contigent.clear();
      colored.clear();
      for (Integer node: nodes) {
        if (!contigent.contains(node)) {
          colored.add(node);
          contigent.addAll(graph.get(node));
        }
      }
      partition.add(new ArrayList<Integer>(colored));
      nodes.removeAll(colored);
    }

    if (VERBOSE) {
      System.err.println("Graph successfully colored using " + partition.size() + " colors");
    }
    return partition;
  }
}
