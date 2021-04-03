package edu.stanford.nlp.scenegraph.image;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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
  public static SceneGraphImageObject fromJSONObject(SceneGraphImage img, JSONObject obj) {

    List<String> names = (List<String>) obj.get("names");
    List<JSONArray> labelArrays = (List<JSONArray>) obj.get("labels");
    List<List<CoreLabel>> labelsList = null;
    if (labelArrays != null) {
      labelsList = Generics.newArrayList(labelArrays.size());
      for (JSONArray arr : labelArrays) {
        List<CoreLabel> tokens = Generics.newArrayList(arr.size());
        for (String str : (List<String>) arr) {
          tokens.add(SceneGraphImageUtils.labelFromString(str));
        }
        labelsList.add(tokens);
      }
    }
    JSONObject boundingBoxObj = (JSONObject) obj.get("bbox");

    int h = ((Number) boundingBoxObj.get("h")).intValue();
    int w = ((Number) boundingBoxObj.get("w")).intValue();
    int x = ((Number) boundingBoxObj.get("x")).intValue();
    int y = ((Number) boundingBoxObj.get("y")).intValue();

    SceneGraphImageBoundingBox boundingBox = new SceneGraphImageBoundingBox(h, w, x, y);

    return new SceneGraphImageObject(boundingBox, names, labelsList);
  }

  @SuppressWarnings("unchecked")
  public JSONObject toJSONObject(SceneGraphImage sceneGraphImage) {
    JSONObject obj = new JSONObject();

    JSONObject bbox = new JSONObject();
    bbox.put("h", this.boundingBox.h);
    bbox.put("w", this.boundingBox.w);
    bbox.put("x", this.boundingBox.x);
    bbox.put("y", this.boundingBox.y);

    obj.put("bbox", bbox);

    JSONArray names = new JSONArray();
    for (String name : this.names) {
      names.add(name);
    }

    obj.put("names", names);


    if (this.labels != null && ! this.labels.isEmpty()) {
      JSONArray labelsList = new JSONArray();
      JSONArray lemmataList = new JSONArray();
      for (List<CoreLabel> list : this.labels) {
        JSONArray labels = new JSONArray();
        for (CoreLabel lbl : list) {
          labels.add(SceneGraphImageUtils.labelToString(lbl));
        }
        labelsList.add(labels);
        lemmataList.add(StringUtils.join(list.stream().map(x -> x.lemma() != null ? x.lemma() : x.word()), " "));
      }
      obj.put("labels", labelsList);
      obj.put("lemmata", lemmataList);
    }



    return obj;
  }

}
