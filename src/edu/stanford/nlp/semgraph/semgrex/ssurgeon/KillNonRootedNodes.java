package edu.stanford.nlp.semgraph.semgrex.ssurgeon;
import java.io.*;
import java.util.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.*;

/**
 * This is used to clean up a graph, removing nodes that cannot possibly reach a root.
 * The intended usage is for the user to be able to perform cuts, select the nodes to 
 * keep by manually choosing the root, and then dropping the nodes that cannot
 * reach those new nodes.  
 *<br>
 * A node is deleted from every graph of the sentence which has it, if it
 * has no path to a root in any of them.  With "-graph", that graph alone
 * decides, so a cut made only in the basic graph can be cleaned up in
 * every graph.  A graph's last root is never deleted: if every root of
 * a graph has no path to a root in the deciding graph, those roots are
 * kept.  The words which are left keep their indices; reindexGraph
 * closes the gaps.
 * @author Eric Yeh
 *
 */
public class KillNonRootedNodes extends SsurgeonEdit {
  /** The command name of this edit in an Ssurgeon script */
  public static final String LABEL = "killNonRooted"; 

  /** The graph which decides which nodes have no path to a root, or null for every graph */
  final SemgrexGraphName graph;

  /** Creates an edit which removes the nodes with no path to a root in any graph. */
  public KillNonRootedNodes() {
    this(null);
  }

  /**
   * Creates an edit which removes the nodes with no path to a root in the given graph.
   *
   * @param graph the graph which decides, or null for a node to need no path in any graph
   */
  public KillNonRootedNodes(SemgrexGraphName graph) {
    this.graph = graph;
  }

  /**
   * Removes from every graph each node with no path to a root, in any
   * graph which has it, or in the graph this edit names.
   *<br>
   * If executed twice on the same graphs, the second time there
   * will be no further updates
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    List<SemanticGraph> present = new ArrayList<>();
    for (SemgrexGraphName name : SemgrexGraphName.values()) {
      SemanticGraph sg = graphs.get(name);
      if (sg != null) {
        present.add(sg);
      }
    }

    List<SemanticGraph> deciding;
    if (graph == null) {
      deciding = present;
    } else {
      SemanticGraph sg = graphs.get(graph);
      if (sg == null) {
        return false;
      }
      deciding = Collections.singletonList(sg);
    }

    // a node is kept if any deciding graph which has it gives it a path
    // to a root.  every node of the deciding graphs is checked
    Set<IndexedWord> rooted = new HashSet<>();
    Set<IndexedWord> unrooted = new LinkedHashSet<>();
    for (SemanticGraph sg : deciding) {
      for (IndexedWord node : sg.vertexSet()) {
        if (sg.getPathToRoot(node) != null) {
          rooted.add(node);
        } else {
          unrooted.add(node);
        }
      }
    }
    unrooted.removeAll(rooted);
    // the roots of the deciding graphs are rooted already.  a root of
    // another graph is deleted along with the rest, unless it would leave
    // that graph with no root at all
    for (SemanticGraph sg : present) {
      Collection<IndexedWord> roots = sg.getRoots();
      if (!roots.isEmpty() && unrooted.containsAll(roots)) {
        unrooted.removeAll(roots);
      }
    }

    // removeVertex leaves the roots alone, so a deleted root is taken
    // out of the roots here
    boolean changed = false;
    for (IndexedWord node : unrooted) {
      for (SemanticGraph sg : present) {
        if (sg.removeVertex(node)) {
          sg.getRoots().remove(node);
          changed = true;
        }
      }
    }
    return changed;
  }

  /**
   * The graph this edit checks for paths to a root, if it names one other than the basic graph.
   */
  @Override
  public Set<SemgrexGraphName> requiredGraphs() {
    return graph == null || graph == SemgrexGraphName.BASIC ? Collections.emptySet() : Collections.singleton(graph);
  }

  @Override
  public String toEditString() {
     StringWriter buf = new StringWriter();
     buf.append(LABEL);
     if (graph != null) {
       buf.append("\t");
       buf.append(Ssurgeon.GRAPH_ARG);
       buf.append(" ");
       buf.append(graph.lowerName);
     }
     return buf.toString();
   }
}
