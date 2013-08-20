package edu.stanford.nlp.ling.tokensregex.types;

import edu.stanford.nlp.ling.CoreAnnotation;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Tags that can be added to values or annotations
 */
public class Tags implements Serializable {
  public static class TagsAnnotation implements CoreAnnotation<Tags> {
    public Class<Tags> getType() {
      return Tags.class;
    }
  }

  Map<String, Value> tags;

  public Tags(String... tags) {
    if (tags != null) {
      this.tags = new HashMap<String,Value>();
      for (String tag:tags) {
        this.tags.put(tag, null);
      }
    }
  }

  public Collection<String> getTags() {
    return tags.keySet();
  }

  public boolean hasTag(String tag) {
    return (tags != null)? tags.containsKey(tag): false;
  }

  public void addTag(String tag) {
    addTag(tag, null);
  }

  public void addTag(String tag, Value v) {
    if (tags == null) { tags = new HashMap<String, Value>(1); }
    tags.put(tag, v);
  }

  public void removeTag(String tag) {
    if (tags != null) { tags.remove(tag); }
  }

  public Value getTag(String tag) {
    return (tags != null)? tags.get(tag): null;
  }
  
  private static final long serialVersionUID = 2;
}
