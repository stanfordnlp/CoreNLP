package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.*;
import java.io.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
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
  /**
   * Creates an edit which sets the roots to the named nodes.
   *
   * @param newRootNames The names of the nodes to use as roots.  The list is kept, not copied.
   */
  public SetRoots(List<String> newRootNames) {
    this.newRootNames = newRootNames;
  }

  /**
   * If executed twice on the same graph, the second time there
   * will be no further updates
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    Set<IndexedWord> newRoots = new LinkedHashSet<>();
    for (String name : newRootNames) {
      IndexedWord root = getNamedNode(name, sm);
      if (root == null) {
        throw new SsurgeonRuntimeException("Ssurgeon rule tried to set root to " + name + " but that name does not exist in the semgrex results");
      }
      newRoots.add(root);
    }
    if (newRoots.equals(sg.getRoots()))
      return false;
    sg.setRoots(newRoots);
    return true;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL);
    for (String name : newRootNames) {
      buf.write("\t");
      buf.write(name);
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
