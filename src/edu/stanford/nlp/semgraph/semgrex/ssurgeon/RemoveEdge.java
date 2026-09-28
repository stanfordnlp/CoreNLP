package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

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
  /** The name of this edit in an Ssurgeon script */
  public static final String LABEL = "removeEdge";

  /** The relation to remove, or null to remove edges of any relation */
  protected GrammaticalRelation relation; // Name of the matched relation type
  /** Name of the governor in the match, or {@link #WILDCARD_NODE} */
  protected String govName; // Name of governor of this reln, in match
  /** Name of the dependent in the match, or {@link #WILDCARD_NODE} */
  protected String depName; // Name of the dependent in this reln, in match

  /**
   * Creates an edit which removes the edges between two named nodes.
   *
   * @param relation The relation to remove, or null to remove edges of any relation
   * @param govName Name of the governor, or {@link #WILDCARD_NODE}
   * @param depName Name of the dependent, or {@link #WILDCARD_NODE}
   */
  public RemoveEdge(GrammaticalRelation relation, String govName, String depName) {
    this.relation = relation;
    this.govName = govName;
    this.depName = depName;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    if (relation != null) {
      buf.write(Ssurgeon.RELN_ARG);buf.write(" ");
      buf.write(relation.toString()); buf.write("\t");
    }
    buf.write(Ssurgeon.GOV_NODENAME_ARG);buf.write(" ");
    buf.write(govName); buf.write("\t");
    buf.write(Ssurgeon.DEP_NODENAME_ARG);buf.write(" ");
    buf.write(depName);
    return buf.toString();
  }

  /** Used in place of the gov or dep name to match any node */
  public static final String WILDCARD_NODE = "**WILDNODE**";

  /**
   * Remove all edges from gov to dep that match the relation name.
   *<br>
   * Either gov or dep can be **WILDNODE**, representing that all
   * edges to or from the other node of that type should be removed.
   *<br>
   * You cannot set both gov and dep to **WILDNODE**, though.
   *<br>
   * This will not update anything the second time executed on the
   * same graph.
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    boolean govWild = govName.equals(WILDCARD_NODE);
    boolean depWild = depName.equals(WILDCARD_NODE);
    IndexedWord govNode = getNamedNode(govName, sm);
    IndexedWord depNode = getNamedNode(depName, sm);
    boolean success = false;

    List<SemanticGraphEdge> edgesToDelete = null;
    if (govNode != null && depNode != null) {
      if (relation == null) {
        edgesToDelete = new ArrayList<>(sg.getAllEdges(govNode, depNode));
      } else {
        SemanticGraphEdge edge = sg.getEdge(govNode, depNode, relation);
        while (edge != null) {
          if (!sg.removeEdge(edge)) {
            throw new IllegalStateException("Found an edge and tried to delete it, but somehow this didn't work!  " + edge);
          }
          edge = sg.getEdge(govNode, depNode, relation);
          success = true;
        }
      }
    } else if (depNode != null && govWild) {
      // dep known, wildcard gov
      if (relation == null) {
        edgesToDelete = new ArrayList<>();
        sg.incomingEdgeIterable(depNode).forEach(edgesToDelete::add);
      } else {
        edgesToDelete = new ArrayList<>();
        for (SemanticGraphEdge edge : sg.incomingEdgeIterable(depNode)) {
          if (edge.getRelation().equals(relation)) {
            edgesToDelete.add(edge);
          }
        }
      }
    } else if (govNode != null && depWild) {
      // gov known, wildcard dep
      if (relation == null) {
        edgesToDelete = new ArrayList<>();
        sg.outgoingEdgeIterable(govNode).forEach(edgesToDelete::add);
      } else {
        edgesToDelete = new ArrayList<>();
        for (SemanticGraphEdge edge : sg.outgoingEdgeIterable(govNode)) {
          if (edge.getRelation().equals(relation)) {
            edgesToDelete.add(edge);
          }
        }
      }
    }

    if (edgesToDelete != null) {
      for (SemanticGraphEdge edge : edgesToDelete) {
        if (!sg.removeEdge(edge)) {
          throw new IllegalStateException("Found an edge and tried to delete it, but somehow this didn't work!  " + edge);
        }
        success = true;
      }
    }

    // will be true if at least one edge was removed
    return success;
  }


    /**
     * Returns the name of the dependent node.
     *
     * @return The dependent name
     */
    public String getDepName() {
      return depName;
    }


    /**
     * Returns the name of the governor node.
     *
     * @return The governor name
     */
    public String getGovName() {
      return govName;
    }

    /**
     * Returns the name of the relation to remove.
     *
     * @return The relation name.  Throws NullPointerException if no relation was given
     */
    public String getRelationName() {
      return relation.toString();
    }
  }
