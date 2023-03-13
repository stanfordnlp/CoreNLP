package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * Given a named edge, reconnect that edge elsewhere in the graph, changing either the gov and/or dep.
 *<br>
 * If an edge already exists with the new named relation,
 * <i>that</i> edge is deleted permanently.  That way, named
 * references to the first edge should still work.
 *
 * @author John Bauer
 *
 */
public class ReattachNamedEdge extends SsurgeonEdit {
  public static final String LABEL = "reattachNamedEdge";

  protected final String edgeName; // Name of the matched edge in the SemgrexPattern
  protected final String govNodeName; // Where to put the new gov.  If null, will not edit
  protected final String depNodeName; // Where to put the new dep.  If null, will not edit

  public ReattachNamedEdge(String edgeName, String gov, String dep) {
    if (edgeName == null) {
      throw new SsurgeonParseException("ReattachNamedEdge created with no edge name!");
    }
    if (gov == null && dep == null) {
      throw new SsurgeonParseException("ReattachNamedEdge created with both gov and dep missing!");
    }

    this.edgeName = edgeName;
    this.govNodeName = gov;
    this.depNodeName = dep;
  }

  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");

    buf.write(Ssurgeon.EDGE_NAME_ARG);buf.write(" ");
    buf.write(edgeName);

    if (govNodeName != null) {
      buf.write("\t");
      buf.write(Ssurgeon.GOV_NODENAME_ARG);buf.write(" ");
      buf.write(govNodeName);
    }
    if (depNodeName != null) {
      buf.write("\t");
      buf.write(Ssurgeon.DEP_NODENAME_ARG);buf.write(" ");
      buf.write(depNodeName);
    }

    return buf.toString();
  }

  /**
   * "Reattach" the named edge by removing it and then recreating it with the new gov and/or dep
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    SemanticGraphEdge edge = sm.getEdge(edgeName);

    if (edge != null) {
      final IndexedWord gov = (govNodeName != null) ? sm.getNode(govNodeName) : edge.getSource();
      final IndexedWord dep = (depNodeName != null) ? sm.getNode(depNodeName) : edge.getTarget();
      if (gov == edge.getSource() && dep == edge.getTarget()) {
        // we were asked to point the edge to the same nodes it already pointed to
        // nothing to do
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
          if (existingEdge.getRelation().equals(edge.getRelation())) {
            newEdge = existingEdge;
            break found;
          }
        }
        newEdge = new SemanticGraphEdge(gov,
                                        dep,
                                        edge.getRelation(),
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
