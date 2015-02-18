package edu.stanford.nlp.ling.tokensregex.types;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.util.Generics;

import java.io.Serializable;
import java.util.Collection;
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
      this.tags = Generics.newHashMap();
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
    if (tags == null) { tags = Generics.newHashMap(1); }
    tags.put(tag, v);
  }

  public void removeTag(String tag) {
    if (tags != null) { tags.remove(tag); }
  }

  public Value getTag(String tag) {
    return (tags != null)? tags.get(tag): null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Tags)) return false;

    Tags tags1 = (Tags) o;

    if (tags != null ? !tags.equals(tags1.tags) : tags1.tags != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return tags != null ? tags.hashCode() : 0;
  }

  private static final long serialVersionUID = 2;
}
