package edu.stanford.nlp.semgraph.semgrex.ssurgeon;
import java.io.*;
import java.util.*;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.*;

/**
 * This is used to clean up a graph, removing nodes that cannot possibly reach a root.
 * The intended usage is for the user to be able to perform cuts, select the nodes to 
 * keep by manually choosing the root, and then dropping the nodes that cannot
 * reach those new nodes.  
 * @author Eric Yeh
 *
 */
public class KillNonRootedNodes extends SsurgeonEdit {
  public static final String LABEL = "killNonRooted"; 
  
  @Override
  public void evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    List<IndexedWord> nodes = new ArrayList<>(sg.vertexSet());
    for (IndexedWord node : nodes) {
      List<IndexedWord> rootPath = sg.getPathToRoot(node);
      if (rootPath == null) {
        sg.removeVertex(node);
      }
    }
  }
  
  @Override
  public String toEditString() {
     StringWriter buf = new StringWriter();
     buf.append(LABEL);     
     buf.append("\t");         
     buf.append(LABEL);     
     
     return buf.toString();
   }
}
