package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Given a named edge, governor, and dependent, removes that edge
 * from the SemanticGraph.
 *
 * NOTE: you should manually reassign roots for dangling subtrees,
 * or delete them outright.  This does not perform any new root 
 * assignments.
 * 
 * TODO: implement logging functionality
 * @author yeh1
 *
 */
public class RemoveNamedEdge extends SsurgeonEdit {  
  public static final String LABEL = "removeNamedEdge";

  protected String edgeName; // Name of the matched edge in the SemgrexPattern
  protected String govName; // Name of governor of this reln, in match
  protected String depName; // Name of the dependent in this reln, in match
  
  public RemoveNamedEdge(String edgeName, String govName, String depName) {
    this.edgeName = edgeName;
    this.govName = govName;
    this.depName = depName;
  }
  
  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.EDGE_NAME_ARG);buf.write(" ");    
    buf.write(edgeName); buf.write("\t");
    buf.write(Ssurgeon.GOV_NODENAME_ARG);buf.write(" ");    
    buf.write(govName); buf.write("\t");
    buf.write(Ssurgeon.DEP_NODENAME_ARG);buf.write(" ");    
    buf.write(depName);
    return buf.toString();
  }
  
  @Override
  public void evaluate(SemanticGraph sg, SemgrexMatcher sm) {    
    String relation = sm.getRelnString(edgeName);
    IndexedWord govNode = getNamedNode(govName, sm);
    IndexedWord depNode = getNamedNode(depName, sm);
    SemanticGraphEdge edge = sg.getEdge(govNode, depNode, GrammaticalRelation.valueOf(relation));
    
    if (edge != null) {
      sg.removeEdge(edge);
    }
  }

  public String getDepName() {
    return depName;
  }

  public String getEdgeName() {
    return edgeName;
  }

  public String getGovName() {
    return govName;
  }

}
