package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
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
  public static final String LABEL = "relabelNamedEdge";

  protected final String edgeName; // Name of the matched edge in the SemgrexPattern

  protected final GrammaticalRelation relation; // Type of relation to add between these edges

  public RelabelNamedEdge(String edgeName, GrammaticalRelation relation) {
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
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    SemanticGraphEdge edge = sm.getEdge(edgeName);

    if (edge != null) {
      // if the edge is already named what we want, then our work here is done
      // this bomb-proofs the operation in the event someone writes an edit that
      // generically changes any edge to nsubj, for example
      if (edge.getRelation().equals(this.relation)) {
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
          if (existingEdge.getRelation().equals(this.relation)) {
            newEdge = existingEdge;
            break found;
          }
        }
        newEdge = new SemanticGraphEdge(edge.getSource(),
                                        edge.getTarget(),
                                        this.relation,
                                        edge.getWeight(),
                                        edge.isExtra());
        sg.addEdge(newEdge);
      }
      // whether we recreated a new edge with the new relation,
      // or found an existing edge with the relation we wanted,
      // update the named edge in the SemgrexMatcher so future
      // iterations have the name connected to the edge
      // TODO: if an existing edge was clobbered, perhaps we need to
      // update anything that named it
      sm.putNamedEdge(edgeName, newEdge);
      return true;
    }
    return false;
  }
}
