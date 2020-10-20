package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Removes the edge with the given relation type (string name), between
 * two named nodes in a graph match.
 * @author yeh1
 *
 */
public class RemoveEdge extends SsurgeonEdit {
  public static final String LABEL = "removeEdge";

  protected GrammaticalRelation relation; // Name of the matched relation type
  protected String govName; // Name of governor of this reln, in match
  protected String depName; // Name of the dependent in this reln, in match

  public RemoveEdge(GrammaticalRelation relation, String govName, String depName) {
    this.relation = relation;
    this.govName = govName;
    this.depName = depName;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.RELN_ARG);buf.write(" ");
    buf.write(relation.toString()); buf.write("\t");
    buf.write(Ssurgeon.GOV_NODENAME_ARG);buf.write(" ");
    buf.write(govName); buf.write("\t");
    buf.write(Ssurgeon.DEP_NODENAME_ARG);buf.write(" ");
    buf.write(depName);
    return buf.toString();
  }

  public static final String WILDCARD_NODE = "**WILDNODE**";

  @Override
  public void evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    boolean govWild = govName.equals(WILDCARD_NODE);
    boolean depWild = depName.equals(WILDCARD_NODE);
    IndexedWord govNode = getNamedNode(govName, sm);
    IndexedWord depNode =getNamedNode(depName, sm);

    if (govNode != null && depNode != null) {
      SemanticGraphEdge edge = sg.getEdge(govNode, depNode, relation);
      if (edge != null) {
        @SuppressWarnings("unused")
        boolean successFlag = sg.removeEdge(edge);
      }
    } else if (depNode != null && govWild) {
      // dep known, wildcard gov
      for (SemanticGraphEdge edge : sg.incomingEdgeIterable(depNode)) {
        if (edge.getRelation().equals(relation) && sg.containsEdge(edge) ) {
          sg.removeEdge(edge);
        }
      }
    }  else if (govNode != null && depWild) {
      // gov known, wildcard dep
      for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(govNode)) {
        if (edge.getRelation().equals(relation) && sg.containsEdge(edge) ) {
          sg.removeEdge(edge);
        }
      }
    }
  }


    public String getDepName() {
      return depName;
    }


    public String getGovName() {
      return govName;
    }

    public String getRelationName() {
      return relation.toString();
    }
  }
