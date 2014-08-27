package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * This action removes all incoming edges for the given node.
 * @author lumberjack
 *
 */
public class KillAllIncomingEdges extends SsurgeonEdit {
  public static final String LABEL = "killAllIncomingEdges";
  protected String nodeName; // name of this node

  public KillAllIncomingEdges(String nodeName) {
    this.nodeName = nodeName;
  }

  @Override
  public void evaluate(SemanticGraph sg, SemgrexMatcher sm) {
   IndexedWord tgtNode = getNamedNode(nodeName, sm);
   for (SemanticGraphEdge edge : sg.incomingEdgeIterable(tgtNode)) {
     sg.removeEdge(edge);
   }
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.NODENAME_ARG); buf.write("\t"); buf.write(nodeName);
    return buf.toString();
  }

}
