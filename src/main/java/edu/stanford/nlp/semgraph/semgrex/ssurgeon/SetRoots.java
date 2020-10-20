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
  public static final String LABEL = "setRoots";
  List<String> newRootNames;
  public SetRoots(List<String> newRootNames) {
    this.newRootNames = newRootNames;
  }
  
  @Override
  public void evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    List<IndexedWord> newRoots = new ArrayList<>();
    for (String name : newRootNames)
      newRoots.add(getNamedNode(name, sm));
    sg.setRoots(newRoots);    
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
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub

  }

}
