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
  public static final String LABEL = "addEdge";
  protected final String govName; // Name of governor of this reln, in match
  protected final String depName; // Name of the dependent in this reln, in match
  protected final GrammaticalRelation relation; // Type of relation to add between these edges
  protected final double weight;
  
  public AddEdge(String govName, String depName, GrammaticalRelation relation) {
    this(govName, depName, relation, 0.0);
  }
  
  public AddEdge(String govName, String depName, GrammaticalRelation relation, double weight) {
    this.govName = govName;
    this.depName = depName;
    this.relation = relation;
    this.weight = weight;
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
    return buf.toString();
  }
  
  public static AddEdge createEngAddEdge(String govName, String depName, String engRelnName) {
    GrammaticalRelation reln = EnglishGrammaticalRelations.valueOf(engRelnName);
    return new AddEdge(govName, depName, reln);
  }

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
      sg.addEdge(govNode, depNode, relation, weight,false );
      return true;
    }
    return false;
  }

}
