package edu.stanford.nlp.scenegraph.image;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;

public class SceneGraphImageAttribute {

  public String attribute;
  public String object;
  public String predicate = "is";
  public SceneGraphImageRegion region;
  public SceneGraphImageObject subject;
  public String[] text;
  public List<CoreLabel> attributeGloss;
  public List<CoreLabel> subjectGloss;

  public SceneGraphImage image;


   @SuppressWarnings("unchecked")
  public static SceneGraphImageAttribute fromJSONObject(SceneGraphImage img, JsonObject obj) {
    SceneGraphImageAttribute attr = new SceneGraphImageAttribute();

    attr.image = img;
    attr.attribute = obj.getString("attribute");
    attr.object = obj.getString("object");
    attr.predicate = obj.getString("predicate");

    if (obj.get("region") != null) {
      int regionId = obj.getInt("region") - 1;
      attr.region = img.regions.get(regionId);
    }

    int subjectId = obj.getInt("subject");
    attr.subject = img.objects.get(subjectId);

    List<String> textList = SceneGraphImageUtils.getJsonStringList(obj, "text");
    attr.text = textList.toArray(new String[textList.size()]);

    if (obj.containsKey("attributeGloss")) {
      List<String> attributeGlossList = SceneGraphImageUtils.getJsonStringList(obj, "attributeGloss");
      attr.attributeGloss = Generics.newArrayList(attributeGlossList.size());
      for (String str : attributeGlossList) {
        attr.attributeGloss.add(SceneGraphImageUtils.labelFromString(str));
      }
    }

    if (obj.containsKey("subjectGloss")) {
      List<String> subjectGlossList = SceneGraphImageUtils.getJsonStringList(obj, "subjectGloss");
      attr.subjectGloss = Generics.newArrayList(subjectGlossList.size());
      for (String str : subjectGlossList) {
        attr.subjectGloss.add(SceneGraphImageUtils.labelFromString(str));
      }
    }

    return attr;
  }


  @SuppressWarnings("unchecked")
  public JsonObject toJSONObject(SceneGraphImage img) {
    JsonObjectBuilder obj = Json.createObjectBuilder();
    obj.add("attribute", this.attribute);
    obj.add("object", this.object);
    obj.add("predicate", this.predicate);
    if (this.region != null) {
      obj.add("region", img.regions.indexOf(this.region) + 1);
    }
    obj.add("subject", img.objects.indexOf(this.subject));

    JsonArrayBuilder text = Json.createArrayBuilder();
    for (String word : this.text) {
      text.add(word);
    }
    obj.add("text", text.build());


    if (this.attributeGloss != null) {
      JsonArrayBuilder attributeGloss = Json.createArrayBuilder();
      for (CoreLabel lbl : this.attributeGloss) {
        attributeGloss.add(SceneGraphImageUtils.labelToString(lbl));
      }
      obj.add("attributeGloss", attributeGloss.build());
      obj.add("attributeLemmaGloss", attributeLemmaGloss());
    }

    if (this.subjectGloss != null) {
      JsonArrayBuilder subjectGloss = Json.createArrayBuilder();
      for (CoreLabel lbl : this.subjectGloss) {
        subjectGloss.add(SceneGraphImageUtils.labelToString(lbl));
      }
      obj.add("subjectGloss", subjectGloss.build());
      obj.add("subjectLemmaGloss", subjectLemmaGloss());
    }

    return obj.build();
  }

  @Override
  public SceneGraphImageAttribute clone() {
    SceneGraphImageAttribute attr = new SceneGraphImageAttribute();
    attr.attribute = this.attribute;
    attr.object = this.object;
    attr.predicate = this.predicate;
    attr.region = this.region;
    attr.subject = this.subject;
    attr.text = Arrays.copyOf(this.text, this.text.length);
    attr.image = this.image;
    if (this.subjectGloss != null) {
      attr.subjectGloss = Generics.newArrayList(this.subjectGloss.size());
      for (CoreLabel lbl : this.subjectGloss) {
        attr.subjectGloss.add(new CoreLabel(lbl));
      }
    }

    if (this.attributeGloss != null) {
      attr.attributeGloss = Generics.newArrayList(this.attributeGloss.size());
      for (CoreLabel lbl : this.attributeGloss) {
        attr.attributeGloss.add(new CoreLabel(lbl));
      }
    }

    attr.image = this.image;

    return attr;
  }


  public String subjectGloss() {
    if (this.subjectGloss == null) return this.text[0];
    return StringUtils.join(this.subjectGloss.stream().map(CoreLabel::word), " ");
  }

  public String subjectLemmaGloss() {
    if (this.subjectGloss == null) return this.text[0];
    return StringUtils.join(this.subjectGloss.stream().map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  public String attributeGloss() {
    if (this.attributeGloss == null) return this.text[2];
    return StringUtils.join(this.attributeGloss.stream().map(CoreLabel::word), " ");
  }

  public String attributeLemmaGloss() {
    if (this.attributeGloss == null) return this.text[2];
    return StringUtils.join(this.attributeGloss.stream().map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  public void print(PrintStream out) {
   out.printf("%s\tis\t%s%n", this.text[0], this.text[2]);

  }

  @Override
  public boolean equals(Object otherObj) {
    if (otherObj == null) return false;
    if ( ! (otherObj instanceof SceneGraphImageAttribute)) return false;

    SceneGraphImageAttribute other = (SceneGraphImageAttribute) otherObj;

    if (other.region != this.region) {
      return false;
    }

    if ( ! other.attributeLemmaGloss().equals(attributeLemmaGloss())) {
      return false;
    }

    if ( ! other.subjectLemmaGloss().equals(subjectLemmaGloss())) {
      return false;
    }

    return true;
  }


  @Override
  public int hashCode() {
    int[] arr = {this.image.regions.indexOf(this.region),
        attributeLemmaGloss().hashCode(), subjectLemmaGloss().hashCode()};
    return arr.hashCode();
  }

}
