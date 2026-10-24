package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.io.StringWriter;
import java.util.Collections;
import java.util.Set;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphName;
import edu.stanford.nlp.semgraph.semgrex.SemgrexGraphs;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatch;
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
  /** Type of relation to add between these edges, which may depend on the match */
  protected final SsurgeonRelation relation;
  /** Weight of the new edge */
  protected final double weight;
  /** Name to give the new edge in the match, or null */
  protected final String edgeName;
  /** The graph this edit works on */
  protected final SemgrexGraphName graph;
  
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
   * @param edgeName name to give the new edge in the match, or null
   */
  public AddEdge(String govName, String depName, GrammaticalRelation relation, double weight, String edgeName) {
    this(govName, depName, relation, weight, edgeName, SemgrexGraphName.BASIC);
  }

  /**
   * Creates an AddEdge which adds its edge to the given graph.
   *
   * @param govName name of the governor node in the match
   * @param depName name of the dependent node in the match
   * @param relation the relation of the new edge
   * @param weight the weight of the new edge
   * @param edgeName name to give the new edge in the match, or null
   * @param graph the graph to add the edge to
   */
  public AddEdge(String govName, String depName, GrammaticalRelation relation, double weight, String edgeName, SemgrexGraphName graph) {
    this(govName, depName, SsurgeonRelation.of(relation), weight, edgeName, graph);
  }

  /**
   * Creates an AddEdge whose relation may refer to the variables of the match.
   * This is a factory rather than a constructor, so a null relation given
   * to a public constructor is not ambiguous.
   *
   * @param govName name of the governor node in the match
   * @param depName name of the dependent node in the match
   * @param relation the relation of the new edge
   * @param weight the weight of the new edge
   * @param edgeName name to give the new edge in the match, or null
   * @param graph the graph to add the edge to
   * @return the edit
   */
  static AddEdge withRelation(String govName, String depName, SsurgeonRelation relation, double weight, String edgeName, SemgrexGraphName graph) {
    return new AddEdge(govName, depName, relation, weight, edgeName, graph);
  }

  private AddEdge(String govName, String depName, SsurgeonRelation relation, double weight, String edgeName, SemgrexGraphName graph) {
    this.govName = govName;
    this.depName = depName;
    this.relation = relation;
    this.weight = weight;
    this.edgeName = edgeName;
    this.graph = graph;
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
    if (graph != SemgrexGraphName.BASIC) {
      buf.write("\t");
      buf.write(Ssurgeon.GRAPH_ARG); buf.write(" ");
      buf.write(graph.lowerName);
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
   * Adds the edge to this edit's graph.
   * If the edge already exists in the graph,
   * a new edge is not added.
   *<br>
   * Nor is one added if either node is not in the graph, such as an empty
   * word when adding to the basic graph, or a word an earlier edit deleted,
   * or if the relation refers to a variable which is not set.
   */
  @Override
  public boolean evaluate(SemgrexGraphs graphs, SemgrexMatch sm) {
    SemanticGraph sg = graphs.get(graph);
    IndexedWord govNode = sm.getNode(govName);
    IndexedWord depNode =  sm.getNode(depName);
    if (sg == null || govNode == null || depNode == null || !sg.containsVertex(govNode) || !sg.containsVertex(depNode)) {
      return false;
    }
    GrammaticalRelation relation = this.relation.get(sm);
    if (relation == null) {
      return false;
    }
    SemanticGraphEdge existingEdge = sg.getEdge(govNode, depNode, relation);
    if (existingEdge == null) {
      SemanticGraphEdge newEdge = sg.addEdge(govNode, depNode, relation, weight, false);
      if (edgeName != null) {
        sm.putNamedEdge(edgeName, newEdge, graph);
      }
      return true;
    }
    return false;
  }

  /**
   * The graph this edit works on, if it is not the basic graph.
   */
  @Override
  public Set<SemgrexGraphName> requiredGraphs() {
    return graph == SemgrexGraphName.BASIC ? Collections.emptySet() : Collections.singleton(graph);
  }

}
