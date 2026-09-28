package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;

/**
 * This adds a given GrammaticalRelation between
 * two named nodes in the graph.
 * 
 * If one already exists, does not add. 
 * 
 * TODO: add position (a la Tregex)
 * TODO: determine consistent and intuitive arguments
 * TODO: figure out a way of ordering edges, so constituents are moved into proper 
 * place s.t. a vertexList() will return the correct ordering.
 * @author yeh1
 *
 */
public class AddEdge extends SsurgeonEdit {
  /** The name of this operation in an Ssurgeon edit string */
  public static final String LABEL = "addEdge";
  /** Name of governor of this reln, in match */
  protected final String govName;
  /** Name of the dependent in this reln, in match */
  protected final String depName;
  /** Type of relation to add between these edges */
  protected final GrammaticalRelation relation;
  /** Weight of the new edge */
  protected final double weight;
  /** Name to give the new edge in the matcher, or null */
  protected final String edgeName;
  
  /**
   * Creates an AddEdge with a weight of 0 and no edge name.
   *
   * @param govName name of the governor node in the match
   * @param depName name of the dependent node in the match
   * @param relation the relation of the new edge
   */
  public AddEdge(String govName, String depName, GrammaticalRelation relation) {
    this(govName, depName, relation, 0.0);
  }
  
  /**
   * Creates an AddEdge with no edge name.
   * Note that the weight argument is not used: the edge weight is 0.
   *
   * @param govName name of the governor node in the match
   * @param depName name of the dependent node in the match
   * @param relation the relation of the new edge
   * @param weight ignored
   */
  public AddEdge(String govName, String depName, GrammaticalRelation relation, double weight) {
    this(govName, depName, relation, 0.0, null);
  }

  /**
   * Creates an AddEdge.
   *
   * @param govName name of the governor node in the match
   * @param depName name of the dependent node in the match
   * @param relation the relation of the new edge
   * @param weight the weight of the new edge
   * @param edgeName name to give the new edge in the matcher, or null
   */
  public AddEdge(String govName, String depName, GrammaticalRelation relation, double weight, String edgeName) {
    this.govName = govName;
    this.depName = depName;
    this.relation = relation;
    this.weight = weight;
    this.edgeName = edgeName;
  }
  
  @Override
  public String toEditString() {
    StringWriter buf = new StringWriter();
    buf.write(LABEL); buf.write("\t");
    buf.write(Ssurgeon.GOV_NODENAME_ARG);buf.write(" ");
    buf.write(govName); buf.write("\t");
    buf.write(Ssurgeon.DEP_NODENAME_ARG);buf.write(" ");
    buf.write(depName); buf.write("\t");
    buf.write(Ssurgeon.RELN_ARG);buf.write(" ");
    buf.write(relation.toString()); buf.write("\t");
    buf.write(Ssurgeon.WEIGHT_ARG);buf.write(" ");
    buf.write(String.valueOf(weight));
    // the edge name has to be printed too: a later edit in the same list
    // may refer to it, and Ssurgeon will refuse to read that edit if the
    // name was never introduced
    if (edgeName != null) {
      buf.write("\t");
      buf.write(Ssurgeon.EDGE_NAME_ARG);buf.write(" ");
      buf.write(edgeName);
    }
    return buf.toString();
  }
  
  /**
   * Creates an AddEdge using an English relation looked up by name.
   *
   * @param govName name of the governor node in the match
   * @param depName name of the dependent node in the match
   * @param engRelnName the name of the relation, as for {@link EnglishGrammaticalRelations#valueOf(String)}
   * @return the new AddEdge
   */
  public static AddEdge createEngAddEdge(String govName, String depName, String engRelnName) {
    GrammaticalRelation reln = EnglishGrammaticalRelations.valueOf(engRelnName);
    return new AddEdge(govName, depName, reln);
  }

  /**
   * Creates an AddEdge using an English relation looked up by name.
   * This uses {@link #AddEdge(String, String, GrammaticalRelation, double)},
   * so the weight is not used.
   *
   * @param govName name of the governor node in the match
   * @param depName name of the dependent node in the match
   * @param engRelnName the name of the relation, as for {@link EnglishGrammaticalRelations#valueOf(String)}
   * @param weight ignored
   * @return the new AddEdge
   */
  public static AddEdge createEngAddEdge(String govName, String depName, String engRelnName, double weight) {
    GrammaticalRelation reln = EnglishGrammaticalRelations.valueOf(engRelnName);
    return new AddEdge(govName, depName, reln, weight);
  }


  /**
   * If the edge already exists in the graph,
   * a new edge is not added.
   */
  @Override
  public boolean evaluate(SemanticGraph sg, SemgrexMatcher sm) {
    IndexedWord govNode = getNamedNode(govName, sm);
    IndexedWord depNode =  getNamedNode(depName, sm);
    SemanticGraphEdge existingEdge = sg.getEdge(govNode, depNode, relation);
    if (existingEdge == null) {
      // When adding the edge, check to see if the gov/dep nodes are presently in the graph.
      if (!sg.containsVertex(govNode)) 
        sg.addVertex(govNode);
      if (!sg.containsVertex(depNode)) 
        sg.addVertex(depNode);
      SemanticGraphEdge newEdge = sg.addEdge(govNode, depNode, relation, weight, false);
      if (edgeName != null) {
        sm.putNamedEdge(edgeName, newEdge);
      }
      return true;
    }
    return false;
  }

}
