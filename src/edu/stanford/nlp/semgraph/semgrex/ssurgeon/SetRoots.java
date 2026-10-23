package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.semgraph.SemanticGraph;

/**
 * Forcibly sets the named nodes to be the new roots. 
 * @author Eric Yeh
 *
 */
public class SetRoots extends SsurgeonEdit {
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL = "setRoots";
  List<String> newRootNames;
  /** The graph whose roots are set */
  protected final SemgrexGraphName graph;

  /**
   * Creates an edit which sets the roots to the named nodes.
   *
   * @param newRootNames The names of the nodes to use as roots.  The list is kept, not copied.
   */
  public SetRoots(List<String> newRootNames) {
    this(newRootNames, SemgrexGraphName.BASIC);
  }

  /**
   * Creates an edit which sets the roots of the given graph to the named nodes.
   *
   * @param newRootNames The names of the nodes to use as roots.  The list is kept, not copied.
   * @param graph The graph whose roots are set
   */
  public SetRoots(List<String> newRootNames, SemgrexGraphName graph) {
    this.newRootNames = newRootNames;
    this.graph = graph;
  }

  /**
   * Sets the roots of this edit's graph.
   * If executed twice on the same graph, the second time there
   * will be no further updates
   *<br>
   * The new roots must already be in the graph.  A word which another
   * graph of the sentence has and this one does not, such as an empty
   * word when setting the roots of the basic graph, leaves the graph as
   * it is.
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatch sm) {
    SemanticGraph sg = graphs.get(graph);
    Set<IndexedWord> newRoots = new LinkedHashSet<>();
    for (String name : newRootNames) {
      IndexedWord root = sm.getNode(name);
      if (root == null) {
        throw new SsurgeonRuntimeException("Ssurgeon rule tried to set root to " + name + " but that name does not exist in the semgrex results");
      }
      newRoots.add(root);
    }
    if (sg == null) {
      return false;
    }
    for (IndexedWord root : newRoots) {
      if (!sg.containsVertex(root)) {
        return false;
      }
    }
    if (newRoots.equals(sg.getRoots()))
      return false;
    sg.setRoots(newRoots);
    return true;
  }

  /**
   * The graph whose roots are set, if it is not the basic graph.
   */
  @Override
  public Set<SemgrexGraphName> requiredGraphs() {
    return graph == SemgrexGraphName.BASIC ? Collections.emptySet() : Collections.singleton(graph);
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    for (String name : newRootNames) {
      buf.write("\t");
      buf.write(name);
    }
    if (graph != SemgrexGraphName.BASIC) {
      buf.write("\t");
      buf.write(Ssurgeon.GRAPH_ARG); buf.write(" ");
      buf.write(graph.lowerName);
    }
    return buf.toString();
  }

  /**
   * Does nothing.
   *
   * @param args Ignored
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub

  }

}
