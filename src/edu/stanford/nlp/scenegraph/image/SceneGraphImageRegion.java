package edu.stanford.nlp.scenegraph.image;

import java.util.List;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphFactory;
import edu.stanford.nlp.semgraph.SemanticGraphFactory.Mode;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructure.Extras;
import edu.stanford.nlp.util.Generics;

public class SceneGraphImageRegion {

  public int h;
  public int w;
  public int x;
  public int y;

  public String phrase;

  public List<CoreLabel> tokens;

  public Set<SceneGraphImageAttribute> attributes = Generics.newHashSet();
  public Set<SceneGraphImageRelationship> relationships = Generics.newHashSet();

  public GrammaticalStructure gs;

  @SuppressWarnings("unchecked")
  public static SceneGraphImageRegion fromJSONObject(SceneGraphImage img, JsonObject obj) {

    SceneGraphImageRegion region = new SceneGraphImageRegion();
    region.h = obj.getInt("h");
    region.w = obj.getInt("w");
    region.x = obj.getInt("x");
    region.y = obj.getInt("y");

    region.phrase = obj.getString("phrase");

    if (obj.get("tokens") != null) {
      JsonArray tokenStrings = obj.getJsonArray("tokens");
      region.tokens = Generics.newArrayList(tokenStrings.size());
      for (JsonString str : tokenStrings.getValuesAs(JsonString.class)) {
        region.tokens.add(SceneGraphImageUtils.labelFromString(str.getString()));
      }
    }

    if (region.tokens != null && obj.get("gs") != null) {
      JsonArray depTriplets = obj.getJsonArray("gs");
      region.gs = SceneGraphImageUtils.getSemanticGraph(depTriplets, region.tokens);
    }

    return region;
  }





  @SuppressWarnings("unchecked")
  public JsonObject toJSONObject(SceneGraphImage sceneGraphImage) {
    JsonObjectBuilder obj = Json.createObjectBuilder();

    obj.add("h", this.h);
    obj.add("w", this.w);
    obj.add("x", this.x);
    obj.add("y", this.y);

    obj.add("phrase", this.phrase);

    if (this.tokens != null && ! this.tokens.isEmpty()) {
      JsonArrayBuilder tokens = Json.createArrayBuilder();
      for (CoreLabel lbl : this.tokens) {
        tokens.add(SceneGraphImageUtils.labelToString(lbl));
      }
      obj.add("tokens", tokens.build());
    }

    if (this.tokens != null && this.gs != null) {
      obj.add("gs", SceneGraphImageUtils.grammaticalStructureToJSON(this.gs));
    }

    return obj.build();
  }

  public SemanticGraph getBasicSemanticGraph() {
    return SemanticGraphFactory.makeFromTree(gs);
  }

  public SemanticGraph getEnhancedSemanticGraph() {
    return SemanticGraphFactory.makeFromTree(gs, Mode.CCPROCESSED, Extras.MAXIMAL, null);
  }

  public String toReadableString() {
    StringBuilder buf = new StringBuilder();
    buf.append(String.format("%-20s%-20s%-20s%n", "source", "reln", "target"));
    buf.append(String.format("%-20s%-20s%-20s%n", "---", "----", "---"));
    for (SceneGraphImageRelationship reln : this.relationships) {
      buf.append(String.format("%-20s%-20s%-20s%n", reln.subjectLemmaGloss(), reln.predicateLemmaGloss(), reln.objectLemmaGloss()));
    }

    for (SceneGraphImageAttribute attr: this.attributes) {
      buf.append(String.format("%-20s%-20s%-20s%n", attr.subjectLemmaGloss(), "is", attr.attributeLemmaGloss()));
    }
    return buf.toString();
  }

}

