package edu.stanford.nlp.scenegraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import edu.stanford.nlp.graph.DirectedMultiGraph;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.util.MapFactory;

/**
 *
 * @author Sebastian Schuster
 *
 */

public class SceneGraph {

  private final DirectedMultiGraph<SceneGraphNode, SceneGraphRelation> graph;

  private static final MapFactory<SceneGraphNode, Map<SceneGraphNode, List<SceneGraphRelation>>> outerMapFactory = MapFactory.hashMapFactory();
  private static final MapFactory<SceneGraphNode, List<SceneGraphRelation>> innerMapFactory = MapFactory.hashMapFactory();

  public SemanticGraph sg;


  public SceneGraph() {
    graph = new DirectedMultiGraph<SceneGraphNode, SceneGraphRelation>(outerMapFactory, innerMapFactory);
  }


  public void addEdge(SceneGraphNode source, SceneGraphNode target, String relation) {

    relation = relation.replaceAll("^be ", "");

    SceneGraphRelation edge = new SceneGraphRelation(source, target, relation);
    graph.add(source, target, edge);

  }

  public List<SceneGraphRelation> relationListSorted() {
    ArrayList<SceneGraphRelation> relations = new ArrayList<SceneGraphRelation>(this.graph.getAllEdges());
    Collections.sort(relations);
    return relations;
  }

  public List<SceneGraphNode> nodeListSorted() {
    ArrayList<SceneGraphNode> nodes = new ArrayList<SceneGraphNode>(this.graph.getAllVertices());
    Collections.sort(nodes);
    return nodes;
  }

  public String toReadableString() {
    StringBuilder buf = new StringBuilder();
    buf.append(String.format("%-20s%-20s%-20s%n", "source", "reln", "target"));
    buf.append(String.format("%-20s%-20s%-20s%n", "---", "----", "---"));
    for (SceneGraphRelation edge : this.relationListSorted()) {
      buf.append(String.format("%-20s%-20s%-20s%n", edge.getSource(), edge.getRelation(), edge.getTarget()));
    }

    buf.append(String.format("%n%n"));
    buf.append(String.format("%-20s%n", "Nodes"));
    buf.append(String.format("%-20s%n", "---"));

    for (SceneGraphNode node : this.nodeListSorted()) {
      buf.append(String.format("%-20s%n", node));
      for (SceneGraphAttribute attr : node.getAttributes()) {
        buf.append(String.format("  -%-20s%n", attr));

      }
    }

    return buf.toString();
  }

  public static void main(String args[]) {
    SceneGraph sg = new SceneGraph();

    //SceneGraphNode node1 = new SceneGraphNode("horse1");
    //SceneGraphNode node2 = new SceneGraphNode("horse2");
    //SceneGraphNode node3 = new SceneGraphNode("buildings");
    //SceneGraphNode node4 = new SceneGraphNode("background");

    //sg.addEdge(node1, node3, "in front of");
    //sg.addEdge(node2, node3, "in front of");
    //sg.addEdge(node3, node4, "in");

    //node1.addAttribute(new SceneGraphAttribute("brown"));
    //node2.addAttribute(new SceneGraphAttribute("white"));

    System.out.println(sg.toReadableString());

  }


  public void addNode(IndexedWord value) {
    this.addNode(new SceneGraphNode(value));
  }

  public void addNode(SceneGraphNode node) {
    this.graph.addVertex(node);
  }

  public SceneGraphNode getOrAddNode(IndexedWord value) {
    SceneGraphNode node = new SceneGraphNode(value);
    for (SceneGraphNode node2 : this.graph.getAllVertices()) {
      if (node2.equals(node)) {
        return node2;
      }
    }
    this.graph.addVertex(node);
    return node;
  }

  @SuppressWarnings("unchecked")
  public String toJSON(int imageID, String url, String phrase) {
    JsonObjectBuilder obj = Json.createObjectBuilder();
    obj.add("id", imageID);
    obj.add("url", url);
    obj.add("phrase", phrase);

    List<SceneGraphNode> objects = this.nodeListSorted();

    JsonArrayBuilder attrs = Json.createArrayBuilder();
    for (SceneGraphNode node : objects) {
      for (SceneGraphAttribute attr : node.getAttributes()) {
        JsonObjectBuilder attrObj = Json.createObjectBuilder();
        attrObj.add("attribute", attr.toString());
        attrObj.add("object", attr.toString());
        attrObj.add("predicate", "is");
        attrObj.add("subject", objects.indexOf(node));
        JsonArrayBuilder text = Json.createArrayBuilder();
        text.add(node.toJSONString());
        text.add("is");
        text.add(attr.toString());
        attrObj.add("text", text.build());
        attrs.add(attrObj.build());
      }
    }

    obj.add("attributes", attrs.build());

    JsonArrayBuilder relns = Json.createArrayBuilder();

    for (SceneGraphRelation reln : this.relationListSorted()) {
      JsonObjectBuilder relnObj = Json.createObjectBuilder();
      relnObj.add("predicate", reln.getRelation());
      relnObj.add("subject", objects.indexOf(reln.getSource()));
      relnObj.add("object", objects.indexOf(reln.getTarget()));
      JsonArrayBuilder text = Json.createArrayBuilder();
      text.add(reln.getSource().toJSONString());
      text.add(reln.getRelation());
      text.add(reln.getTarget().toJSONString());
      relnObj.add("text", text.build());
      relns.add(relnObj.build());
    }

    obj.add("relationships", relns.build());


    JsonArrayBuilder objs = Json.createArrayBuilder();
    for (SceneGraphNode node : objects) {
      JsonObjectBuilder objObj = Json.createObjectBuilder();
      JsonArrayBuilder names = Json.createArrayBuilder();
      names.add(node.toJSONString());
      objObj.add("names", names.build());
      objs.add(objObj.build());
    }

    obj.add("objects", objs.build());


    return obj.build().toString();
  }


}
