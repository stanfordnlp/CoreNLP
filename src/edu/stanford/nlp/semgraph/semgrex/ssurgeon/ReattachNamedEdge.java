package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
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
  /** The name of this edit in an Ssurgeon script */
  public static final String LABEL = "reattachNamedEdge";

  /** Name of the matched edge in the SemgrexPattern */
  protected final String edgeName; // Name of the matched edge in the SemgrexPattern
  /** Name of the node to use as the new gov.  If null, the gov is not changed */
  protected final String govNodeName; // Where to put the new gov.  If null, will not edit
  /** Name of the node to use as the new dep.  If null, the dep is not changed */
  protected final String depNodeName; // Where to put the new dep.  If null, will not edit

  /**
   * Creates an edit which moves the named edge to a new gov and/or dep.
   *
   * @param edgeName Name of the edge in the Semgrex pattern
   * @param gov Name of the node to use as the new gov, or null to keep the gov
   * @param dep Name of the node to use as the new dep, or null to keep the dep
   * @throws SsurgeonParseException if {@code edgeName} is null or both {@code gov} and {@code dep} are null
   */
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
   * Removes {@code edge} from the graph and adds an edge with the same relation,
   * weight, and extra flag from {@code gov} to {@code dep}.  If {@code edgeName}
   * is not null, that name in the matcher is updated to refer to the new edge.
   * If an edge with the same relation already goes from {@code gov} to
   * {@code dep}, no edge is added and the name refers to that edge.
   *<br>
   * Moving an edge does not change which words the graph has, so both
   * {@code gov} and {@code dep} must already be in it.  A word from
   * another graph of the sentence which this one does not have, such as
   * an empty word from the enhanced graph when editing the basic graph,
   * leaves the graph as it is.
   *
   * @param sg The graph to edit
   * @param sm The matcher whose named edge is updated
   * @param edge The edge to move
   * @param edgeName The name of the edge in the matcher, or null to not update any name
   * @param gov The new gov
   * @param dep The new dep
   * @return false if the edge already goes from {@code gov} to {@code dep}, could not be removed,
   *   or would join a word the graph does not have; true otherwise
   */
  public static boolean reattachEdge(SemanticGraph sg, SemgrexMatcher sm,
                                     SemanticGraphEdge edge, String edgeName, IndexedWord gov, IndexedWord dep) {
    if (gov == edge.getSource() && dep == edge.getTarget()) {
      // we were asked to point the edge to the same nodes it already pointed to
      // nothing to do
      return false;
    }
    if (gov == null || dep == null || !sg.containsVertex(gov) || !sg.containsVertex(dep)) {
      // addEdge would add the missing word to the graph
      return false;
    }
    boolean success = sg.removeEdge(edge);
    if (!success) {
      // maybe it was already removed somehow by a previous operation
      return false;
    }
    final SemanticGraphEdge newEdge;
    found: {
      for (SemanticGraphEdge existingEdge : sg.getAllEdges(gov, dep)) {
        if (existingEdge.getRelation().equals(edge.getRelation())) {
          newEdge = existingEdge;
          break found;
        }
      }
      // the graph holds a copy of an edge given to addEdge, so this is
      // the edge to give the name to
      newEdge = sg.addEdge(gov, dep, edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
    // whether we recreated a new edge with the new relation,
    // or found an existing edge with the relation we wanted,
    // update the named edge in the SemgrexMatcher so future
    // iterations have the name connected to the edge.  the name
    // stays bound in the graph it was found in, which is this one
    // TODO: if an existing edge was clobbered, perhaps we need to
    // update anything that named it
    if (edgeName != null) {
      sm.putNamedEdge(edgeName, newEdge);
    }
    return true;
  }

  /**
   * Reattaches the named edge, as {@link #evaluate(SemgrexGraphs, SemgrexMatcher)} does.
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    return evaluate(new SemgrexGraphs(sg), sm);
  }

  /**
   * "Reattach" the named edge by removing it and then recreating it with the new gov and/or dep
   *<br>
   * The edge is moved within the graph it was matched in.  The graphs
   * share their words, so the new gov or dep can be a node matched in
   * any of them.
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatcher sm) {
    SemanticGraphEdge edge = sm.getEdge(edgeName);
    SemgrexGraphName graphName = sm.getEdgeGraph(edgeName);
    SemanticGraph sg = graphName == null ? null : graphs.get(graphName);

    if (edge != null && sg != null) {
      final IndexedWord gov = (govNodeName != null) ? sm.getNode(govNodeName) : edge.getSource();
      final IndexedWord dep = (depNodeName != null) ? sm.getNode(depNodeName) : edge.getTarget();
      return reattachEdge(sg, sm, edge, edgeName, gov, dep);
    }
    return false;
  }
}
