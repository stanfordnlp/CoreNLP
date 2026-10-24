package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

import edu.stanford.nlp.trees.GrammaticalRelation;

/**
 * Given a named edge, change that edge from the SemanticGraph,
 * then put it back with the updated relation.
 *<br>
 * If an edge already exists with the new named relation,
 * <i>that</i> edge is deleted permanently.  That way, named
 * references to the first edge should still work.
 *
 * @author John Bauer
 *
 */
public class RelabelNamedEdge extends SsurgeonEdit {  
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL = "relabelNamedEdge";

  /** The name of the edge to relabel */
  protected final String edgeName; // Name of the matched edge in the SemgrexPattern

  /** The new relation for the edge, which may depend on the match */
  protected final SsurgeonRelation relation;

  /**
   * Creates an edit which changes the relation of a named edge.
   *
   * @param edgeName The name of the edge in the Semgrex pattern
   * @param relation The new relation for the edge
   * @throws SsurgeonParseException if either argument is null
   */
  public RelabelNamedEdge(String edgeName, GrammaticalRelation relation) {
    this(edgeName, SsurgeonRelation.of(relation));
  }

  /**
   * Creates an edit which changes the relation of a named edge to one
   * which may refer to the variables of the match, such as
   * {@code ${etype}:pass}.  This is a factory rather than a constructor,
   * so a null relation given to the public constructor is not ambiguous.
   *
   * @param edgeName The name of the edge in the Semgrex pattern
   * @param relation The new relation for the edge
   * @return the edit
   * @throws SsurgeonParseException if either argument is null
   */
  static RelabelNamedEdge withRelation(String edgeName, SsurgeonRelation relation) {
    return new RelabelNamedEdge(edgeName, relation);
  }

  private RelabelNamedEdge(String edgeName, SsurgeonRelation relation) {
    if (edgeName == null) {
      throw new SsurgeonParseException("RelabelNamedEdge created with no edge name!");
    }
    if (relation == null) {
      throw new SsurgeonParseException("RelabelNamedEdge created with no relation!");
    }

    this.edgeName = edgeName;
    this.relation = relation;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");

    buf.write(Ssurgeon.EDGE_NAME_ARG);buf.write(" ");    
    buf.write(edgeName); buf.write("\t");

    buf.write(Ssurgeon.RELN_ARG);buf.write(" ");
    buf.write(relation.toString()); buf.write("\t");

    return buf.toString();
  }

  /**
   * "Rename" the named edge by removing it and then recreating it
   *<br>
   * The edge is relabeled in the graph it was matched in.  An edge with
   * the same endpoints and relation in another graph is left alone.
   *<br>
   * If the new relation refers to a variable which is not set,
   * nothing changes.
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatch sm) {
    SemanticGraphEdge edge = sm.getEdge(edgeName);
    SemgrexGraphName graphName = sm.getEdgeGraph(edgeName);
    SemanticGraph sg = graphName == null ? null : graphs.get(graphName);

    GrammaticalRelation relation = this.relation.get(sm);
    if (edge != null && sg != null && relation != null) {
      // if the edge is already named what we want, then our work here is done
      // this bomb-proofs the operation in the event someone writes an edit that
      // generically changes any edge to nsubj, for example
      if (edge.getRelation().equals(relation)) {
        return false;
      }
      boolean success = sg.removeEdge(edge);
      if (!success) {
        // maybe it was already removed somehow by a previous operation
        return false;
      }
      final SemanticGraphEdge newEdge;
      found: {
        for (SemanticGraphEdge existingEdge : sg.getAllEdges(edge.getSource(), edge.getTarget())) {
          if (existingEdge.getRelation().equals(relation)) {
            newEdge = existingEdge;
            break found;
          }
        }
        // the graph holds a copy of an edge given to addEdge, so this is
        // the edge to give the name to
        newEdge = sg.addEdge(edge.getSource(), edge.getTarget(), relation, edge.getWeight(), edge.isExtra());
      }
      // whether we recreated a new edge with the new relation,
      // or found an existing edge with the relation we wanted,
      // update the named edge in the match so future
      // iterations have the name connected to the edge
      // TODO: if an existing edge was clobbered, perhaps we need to
      // update anything that named it
      sm.putNamedEdge(edgeName, newEdge, graphName);
      return true;
    }
    return false;
  }
}
