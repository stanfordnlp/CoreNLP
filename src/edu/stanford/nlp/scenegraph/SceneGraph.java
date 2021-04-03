package edu.stanford.nlp.scenegraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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
    JSONObject obj = new JSONObject();
    obj.put("id", imageID);
    obj.put("url", url);
    obj.put("phrase", phrase);

    List<SceneGraphNode> objects = this.nodeListSorted();

    JSONArray attrs = new JSONArray();
    for (SceneGraphNode node : objects) {
      for (SceneGraphAttribute attr : node.getAttributes()) {
        JSONObject attrObj = new JSONObject();
        attrObj.put("attribute", attr.toString());
        attrObj.put("object", attr.toString());
        attrObj.put("predicate", "is");
        attrObj.put("subject", objects.indexOf(node));
        JSONArray text = new JSONArray();
        text.add(node.toJSONString());
        text.add("is");
        text.add(attr.toString());
        attrObj.put("text", text);
        attrs.add(attrObj);
      }
    }

    obj.put("attributes", attrs);

    JSONArray relns = new JSONArray();

    for (SceneGraphRelation reln : this.relationListSorted()) {
      JSONObject relnObj = new JSONObject();
      relnObj.put("predicate", reln.getRelation());
      relnObj.put("subject", objects.indexOf(reln.getSource()));
      relnObj.put("object", objects.indexOf(reln.getTarget()));
      JSONArray text = new JSONArray();
      text.add(reln.getSource().toJSONString());
      text.add(reln.getRelation());
      text.add(reln.getTarget().toJSONString());
      relnObj.put("text", text);
      relns.add(relnObj);
    }

    obj.put("relationships", relns);


    JSONArray objs = new JSONArray();
    for (SceneGraphNode node : objects) {
      JSONObject objObj = new JSONObject();
      JSONArray names = new JSONArray();
      names.add(node.toJSONString());
      objObj.put("names", names);
      objs.add(objObj);
    }

    obj.put("objects", objs);


    return obj.toJSONString();
  }


}
