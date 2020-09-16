package edu.stanford.nlp.scenegraph.image;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

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
    SceneGraphImage img = new SceneGraphImage();

    JSONObject obj = (JSONObject) JSONValue.parse(json);

    JSONArray regions = (JSONArray) obj.get("regions");
    if (regions != null) {
      for (JSONObject region : (List<JSONObject>) regions) {
        img.regions.add(SceneGraphImageRegion.fromJSONObject(img, region));
      }
    }

    JSONArray objects = (JSONArray) obj.get("objects");
    for (JSONObject object: (List<JSONObject>) objects) {
      img.objects.add(SceneGraphImageObject.fromJSONObject(img, object));
    }

    JSONArray attributes = (JSONArray) obj.get("attributes");
    for (JSONObject object: (List<JSONObject>) attributes) {
      img.addAttribute(SceneGraphImageAttribute.fromJSONObject(img, object));
    }

    JSONArray relationships = (JSONArray) obj.get("relationships");
    for (JSONObject relation: (List<JSONObject>) relationships) {
      img.addRelationship(SceneGraphImageRelationship.fromJSONObject(img, relation));
    }

    if (obj.get("id") instanceof Number) {
      img.id = ((Number) obj.get("id")).intValue();
    } else {
      img.id = Integer.parseInt(((String) obj.get("id")));
    }
    img.height = ((Number) obj.get("height")).intValue();
    img.width = ((Number) obj.get("width")).intValue();

    img.url = (String) obj.get("url");

    return img;
    } catch (Exception e) {
      System.err.println("Couldn't parse " + json);
      e.printStackTrace();
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public String toJSON() {
   JSONObject json = new JSONObject();
   json.put("id", this.id);
   json.put("height", this.height);
   json.put("width", this.width);
   json.put("url", this.url);

   JSONArray attributes = new JSONArray();
   for (SceneGraphImageAttribute attr : this.attributes) {
     attributes.add(attr.toJSONObject(this));
   }

   json.put("attributes", attributes);

   JSONArray objects = new JSONArray();
   for (SceneGraphImageObject obj : this.objects) {
     objects.add(obj.toJSONObject(this));
   }

   json.put("objects", objects);

   JSONArray regions = new JSONArray();
   for (SceneGraphImageRegion region : this.regions) {
     regions.add(region.toJSONObject(this));
   }

   json.put("regions", regions);

   JSONArray relationships = new JSONArray();
   for (SceneGraphImageRelationship relation : this.relationships) {
     relationships.add(relation.toJSONObject(this));
   }

   json.put("relationships", relationships);

   return json.toJSONString();
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
