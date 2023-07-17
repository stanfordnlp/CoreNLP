package edu.stanford.nlp.scenegraph.image;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

/**
 *
 * @author Sebastian Schuster
 *
 */
public class SceneGraphImageObject {

  public SceneGraphImageBoundingBox boundingBox;
  public Set<String> names;
  public Set<List<CoreLabel>> labels;


  public SceneGraphImageObject(SceneGraphImageBoundingBox boundingBox, List<String> names, List<List<CoreLabel>> labels) {
    this.boundingBox = boundingBox;
    this.names = new HashSet<String>(names);
    if (labels != null) {
      this.labels = new HashSet<List<CoreLabel>>(labels);
    } else {
      this.labels = new HashSet<List<CoreLabel>>();
    }

  }

  @SuppressWarnings("unchecked")
  public static SceneGraphImageObject fromJSONObject(SceneGraphImage img, JsonObject obj) {

    List<String> names = SceneGraphImageUtils.getJsonStringList(obj, "names");
    JsonArray labelArrays = obj.getJsonArray("labels");
    List<List<CoreLabel>> labelsList = null;
    if (labelArrays != null) {
      labelsList = Generics.newArrayList(labelArrays.size());
      for (JsonArray arr : labelArrays.getValuesAs(JsonArray.class)) {
        List<CoreLabel> tokens = Generics.newArrayList(arr.size());
        for (JsonString str : arr.getValuesAs(JsonString.class)) {
          tokens.add(SceneGraphImageUtils.labelFromString(str.getString()));
        }
        labelsList.add(tokens);
      }
    }
    JsonObject boundingBoxObj = obj.getJsonObject("bbox");
    if (boundingBoxObj == null) {
      throw new NullPointerException("object did not have bbox field");
    }

    int h = boundingBoxObj.getInt("h");
    int w = boundingBoxObj.getInt("w");
    int x = boundingBoxObj.getInt("x");
    int y = boundingBoxObj.getInt("y");

    SceneGraphImageBoundingBox boundingBox = new SceneGraphImageBoundingBox(h, w, x, y);

    return new SceneGraphImageObject(boundingBox, names, labelsList);
  }

  @SuppressWarnings("unchecked")
  public JsonObject toJSONObject(SceneGraphImage sceneGraphImage) {
    JsonObjectBuilder obj = Json.createObjectBuilder();

    JsonObjectBuilder bbox = Json.createObjectBuilder();
    bbox.add("h", this.boundingBox.h);
    bbox.add("w", this.boundingBox.w);
    bbox.add("x", this.boundingBox.x);
    bbox.add("y", this.boundingBox.y);

    obj.add("bbox", bbox.build());

    JsonArrayBuilder names = Json.createArrayBuilder();
    for (String name : this.names) {
      names.add(name);
    }

    obj.add("names", names.build());


    if (this.labels != null && ! this.labels.isEmpty()) {
      JsonArrayBuilder labelsList = Json.createArrayBuilder();
      JsonArrayBuilder lemmataList = Json.createArrayBuilder();
      for (List<CoreLabel> list : this.labels) {
        JsonArrayBuilder labels = Json.createArrayBuilder();
        for (CoreLabel lbl : list) {
          labels.add(SceneGraphImageUtils.labelToString(lbl));
        }
        labelsList.add(labels.build());
        lemmataList.add(StringUtils.join(list.stream().map(x -> x.lemma() != null ? x.lemma() : x.word()), " "));
      }
      obj.add("labels", labelsList.build());
      obj.add("lemmata", lemmataList.build());
    }



    return obj.build();
  }

  public boolean equals(Object other) {
    if (!(other instanceof SceneGraphImageObject)) {
      return false;
    }

    SceneGraphImageObject obj = (SceneGraphImageObject) other;
    if (!(this.boundingBox.equals(obj.boundingBox)))
      return false;
    if (!(this.names.equals(obj.names)))
      return false;
    if (!(this.labels.equals(obj.labels)))
      return false;

    return true;
  }
}
