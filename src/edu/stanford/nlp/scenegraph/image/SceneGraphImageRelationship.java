package edu.stanford.nlp.scenegraph.image;

import java.io.PrintStream;
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

public class SceneGraphImageRelationship {

  public SceneGraphImageObject subject;
  public SceneGraphImageObject object;
  public SceneGraphImageRegion region;

  public String predicate;

  public String[] text;

  public List<CoreLabel> subjectGloss;
  public List<CoreLabel> objectGloss;
  public List<CoreLabel> predicateGloss;

  public SceneGraphImage image;

  @SuppressWarnings("unchecked")
  public static SceneGraphImageRelationship fromJSONObject(SceneGraphImage img, JsonObject obj) {
    SceneGraphImageRelationship reln = new SceneGraphImageRelationship();

    reln.predicate = obj.getString("predicate");
    if (reln.predicate == null && obj.get("relationship") != null) {
      reln.predicate = obj.getString("relationship");
    }

    if (obj.get("region") != null) {
      int regionId = obj.getInt("region") - 1;
      reln.region = img.regions.get(regionId);
    }

    int subjectId = obj.getInt("subject");
    reln.subject = img.objects.get(subjectId);

    int objectId = obj.getInt("object");
    reln.object = img.objects.get(objectId);

    List<String> textList = SceneGraphImageUtils.getJsonStringList(obj, "text");
    reln.text = textList.toArray(new String[textList.size()]);

    if (obj.containsKey("subjectGloss")) {
      List<String> subjectGlossStrings = SceneGraphImageUtils.getJsonStringList(obj, "subjectGloss");
      reln.subjectGloss = Generics.newArrayList(subjectGlossStrings.size());
      for (String str : subjectGlossStrings) {
        reln.subjectGloss.add(SceneGraphImageUtils.labelFromString(str));
      }
    }

    if (obj.containsKey("objectGloss")) {
      List<String> objectGlossStrings = SceneGraphImageUtils.getJsonStringList(obj, "objectGloss");
      reln.objectGloss = Generics.newArrayList(objectGlossStrings.size());
      for (String str : objectGlossStrings) {
        reln.objectGloss.add(SceneGraphImageUtils.labelFromString(str));
      }
    }

    if (obj.containsKey("predicateGloss")) {
      List<String> predicateGlossStrings = SceneGraphImageUtils.getJsonStringList(obj, "predicateGloss");
      reln.predicateGloss = Generics.newArrayList(predicateGlossStrings.size());
      for (String str : predicateGlossStrings) {
        reln.predicateGloss.add(SceneGraphImageUtils.labelFromString(str));
      }
    }

    reln.image = img;

    return reln;
  }

  @SuppressWarnings("unchecked")
  public JsonObject toJSONObject(SceneGraphImage img) {
    JsonObjectBuilder obj = Json.createObjectBuilder();

    obj.add("predicate", this.predicate);
    if (this.region != null) {
      obj.add("region", img.regions.indexOf(this.region) + 1);
    }
    obj.add("subject", img.objects.indexOf(this.subject));
    obj.add("object", img.objects.indexOf(this.object));

    JsonArrayBuilder text = Json.createArrayBuilder();
    for (String word : this.text) {
      text.add(word);
    }
    obj.add("text", text.build());


    if (this.subjectGloss != null) {
      JsonArrayBuilder subjectGloss = Json.createArrayBuilder();
      for (CoreLabel lbl : this.subjectGloss) {
        subjectGloss.add(SceneGraphImageUtils.labelToString(lbl));
      }
      obj.add("subjectGloss", subjectGloss.build());
      obj.add("subjectLemmaGloss", this.subjectLemmaGloss());
    }

    if (this.objectGloss != null) {
      JsonArrayBuilder objectGloss = Json.createArrayBuilder();
      for (CoreLabel lbl : this.objectGloss) {
        objectGloss.add(SceneGraphImageUtils.labelToString(lbl));
      }
      obj.add("objectGloss", objectGloss.build());
      obj.add("objectLemmaGloss", this.objectLemmaGloss());
    }

    if (this.predicateGloss != null) {
      JsonArrayBuilder predicateGloss = Json.createArrayBuilder();
      for (CoreLabel lbl : this.predicateGloss) {
        predicateGloss.add(SceneGraphImageUtils.labelToString(lbl));
      }
      obj.add("predicateGloss", predicateGloss.build());
      obj.add("predicateLemmaGloss", this.predicateLemmaGloss());
    }

    return obj.build();
  }

  public String subjectGloss() {
    if (this.subjectGloss == null) return this.text[0];
    return StringUtils.join(this.subjectGloss.stream().map(CoreLabel::word), " ");
  }

  public String subjectLemmaGloss() {
    if (this.subjectGloss == null) return this.text[0];
    return StringUtils.join(this.subjectGloss.stream().map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  public String objectGloss() {
    if (this.objectGloss == null) return this.text[2];
    return StringUtils.join(this.objectGloss.stream().map(CoreLabel::word), " ");
  }

  public String objectLemmaGloss() {
    if (this.objectGloss == null) return this.text[2];
    return StringUtils.join(this.objectGloss.stream().map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  public String predicateGloss() {
    if (this.predicateGloss == null) return this.text[1];
    return StringUtils.join(this.predicateGloss.stream().map(CoreLabel::word), " ");
  }

  public String predicateLemmaGloss() {
    if (this.predicateGloss == null) return this.text[1];
    return StringUtils.join(this.predicateGloss.stream().map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  public void print(PrintStream out) {
    out.printf("%s\t%s\t%s%n", this.text[0], this.text[1], this.text[2]);

   }


  @Override
  public int hashCode() {
    int[] arr = {this.image.regions.indexOf(this.region),
        subjectLemmaGloss().hashCode(), predicateLemmaGloss().hashCode(), objectLemmaGloss().hashCode()};
    return arr.hashCode();
  }

  @Override
  public boolean equals(Object otherObj) {
    if (otherObj == null) return false;
    if ( ! (otherObj instanceof SceneGraphImageRelationship)) return false;

    SceneGraphImageRelationship other = (SceneGraphImageRelationship) otherObj;

    if (other.region != this.region) {
      return false;
    }

    if ( ! other.subjectLemmaGloss().equals(subjectLemmaGloss())) {
      return false;
    }

    if ( ! other.predicateLemmaGloss().equals(predicateLemmaGloss())) {
      return false;
    }

    if ( ! other.objectLemmaGloss().equals(objectLemmaGloss())) {
      return false;
    }

    return true;
  }


  @Override
  public SceneGraphImageRelationship clone() {
    SceneGraphImageRelationship reln = new SceneGraphImageRelationship();
    reln.image = this.image;
    reln.object = this.object;
    reln.subject = this.subject;
    reln.predicate = this.predicate;
    reln.text = this.text;
    reln.predicateGloss = this.predicateGloss;
    reln.subjectGloss = this.subjectGloss;
    reln.objectGloss = this.objectGloss;
    reln.region = this.region;


    return reln;
  }

}
