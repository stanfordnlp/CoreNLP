package edu.stanford.nlp.scenegraph.image;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.List;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonValue;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.scenegraph.SceneGraphImageCleaner;
import edu.stanford.nlp.util.Generics;

/**
 * Data structure to store information for a single image.
 *
 * Includes methods to read and write from and to a JSON file.
 *
 * @author Sebastian Schuster
 *
 */

public class SceneGraphImage implements Serializable {

  private static final long serialVersionUID = 1L;

  public int id;
  public String url;

  public int height;
  public int width;

  public List<SceneGraphImageObject> objects;
  public List<SceneGraphImageRegion> regions;
  public List<SceneGraphImageRelationship> relationships;
  public List<SceneGraphImageAttribute> attributes;


  public SceneGraphImage() {
    this.objects = Generics.newArrayList();
    this.regions = Generics.newArrayList();
    this.relationships = Generics.newArrayList();
    this.attributes = Generics.newArrayList();
  }


  @SuppressWarnings("unchecked")
  public static SceneGraphImage readFromJSON(String json) {
    try {
      StringReader reader = new StringReader(json);
      JsonReader parser = Json.createReader(reader);
      JsonObject obj = parser.readObject();

      SceneGraphImage img = new SceneGraphImage();

      JsonArray regions = obj.getJsonArray("regions");
      if (regions != null) {
        for (JsonObject region : regions.getValuesAs(JsonObject.class)) {
          img.regions.add(SceneGraphImageRegion.fromJSONObject(img, region));
        }
      }

      JsonArray objects = obj.getJsonArray("objects");
      for (JsonObject object: objects.getValuesAs(JsonObject.class)) {
        img.objects.add(SceneGraphImageObject.fromJSONObject(img, object));
      }

      JsonArray attributes = obj.getJsonArray("attributes");
      for (JsonObject object: attributes.getValuesAs(JsonObject.class)) {
        img.addAttribute(SceneGraphImageAttribute.fromJSONObject(img, object));
      }

      JsonArray relationships = obj.getJsonArray("relationships");
      for (JsonObject relation: relationships.getValuesAs(JsonObject.class)) {
        img.addRelationship(SceneGraphImageRelationship.fromJSONObject(img, relation));
      }

      img.id = obj.getInt("id");
      Number height = obj.getInt("height");
      Number width = obj.getInt("width");
      if (height == null) {
        throw new NullPointerException("Image does not have height");
      }
      if (width == null) {
        throw new NullPointerException("Image does not have width");
      }
      img.height = height.intValue();
      img.width = width.intValue();

      img.url = obj.getString("url");

      return img;
    } catch (RuntimeException e) {
      throw new RuntimeException("Couldn't parse \n" + json, e);
    }
  }

  @SuppressWarnings("unchecked")
  public String toJSON() {
    JsonObjectBuilder json = Json.createObjectBuilder();
    json.add("id", this.id);
    json.add("height", this.height);
    json.add("width", this.width);
    json.add("url", this.url);

    JsonArrayBuilder attributes = Json.createArrayBuilder();
    for (SceneGraphImageAttribute attr : this.attributes) {
      attributes.add(attr.toJSONObject(this));
    }

    json.add("attributes", attributes.build());

    JsonArrayBuilder objects = Json.createArrayBuilder();
    for (SceneGraphImageObject obj : this.objects) {
      objects.add(obj.toJSONObject(this));
    }

    json.add("objects", objects.build());

    JsonArrayBuilder regions = Json.createArrayBuilder();
    for (SceneGraphImageRegion region : this.regions) {
      regions.add(region.toJSONObject(this));
    }

    json.add("regions", regions.build());

    JsonArrayBuilder relationships = Json.createArrayBuilder();
    for (SceneGraphImageRelationship relation : this.relationships) {
      relationships.add(relation.toJSONObject(this));
    }

    json.add("relationships", relationships.build());

    return json.build().toString();
  }


  /*
   * Just some basic tests; doesn't do anything useful.
   *
   */
  public static void main(String[] args) throws IOException {

    String filename = args[0];
    BufferedReader reader = IOUtils.readerFromString(filename);

    SceneGraphImageCleaner cleaner = new SceneGraphImageCleaner();

    List<SceneGraphImage> images = Generics.newLinkedList();

    for (String line = reader.readLine(); line != null; line = reader.readLine()) {
      SceneGraphImage img = readFromJSON(line);
      if (img == null) {
        continue;
      }
      cleaner.cleanupImage(img);
      images.add(img);
    }

    SceneGraphImageCleaner.extractAllAttributes(images);

    for (SceneGraphImage img : images) {
      cleaner.splitAttributeConjunctions(img);
      cleaner.trimFunctionWords(img);
      System.out.println(img.toJSON());
    }
  }

  public void addAttribute(SceneGraphImageAttribute attr) {
    this.attributes.add(attr);
    if (attr.region != null) {
      attr.region.attributes.add(attr);
    }
  }

  public void addRelationship(SceneGraphImageRelationship reln) {
    this.relationships.add(reln);
    if (reln.region != null) {
      reln.region.relationships.add(reln);
    }
  }

  public void removeRegion(SceneGraphImageRegion region) {
    this.regions.remove(region);
    for (SceneGraphImageRelationship reln : region.relationships) {
      this.relationships.remove(reln);
    }
    for (SceneGraphImageAttribute attr : region.attributes) {
      this.attributes.remove(attr);
    }
  }

}
