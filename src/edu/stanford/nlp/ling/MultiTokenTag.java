package edu.stanford.nlp.ling;

import java.io.Serializable;

/**
 * Represents a tag for a multi token expression
 * Can be used to annotate individual tokens without
 *   having nested annotations
 *
 * @author Angel Chang
 */
public class MultiTokenTag implements Serializable {
  private static final long serialVersionUID = 1;

  public Tag tag;
  public int index;

  public static class Tag implements  Serializable {
    private static final long serialVersionUID = 1;

    public String name;
    public String tag;

    public int length;  // total length of expression

    public Tag(String name, String tag, int length) {
      this.name = name;
      this.tag = tag;
      this.length = length;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Tag tag1 = (Tag) o;

      if (length != tag1.length) return false;
      if (!name.equals(tag1.name)) return false;
      if (!tag.equals(tag1.tag)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = name.hashCode();
      result = 31 * result + tag.hashCode();
      result = 31 * result + length;
      return result;
    }
  }

  public MultiTokenTag(Tag tag, int index) {
    this.tag = tag;
    this.index = index;
  }

  public boolean isStart() {
    return index == 0;
  }

  public boolean isEnd() {
    return index == tag.length - 1;
  }

  public String toString() {
    return  tag.name + "/" + tag.tag +  "(" + index + "/" + tag.length + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    MultiTokenTag that = (MultiTokenTag) o;

    if (index != that.index) return false;
    if (!tag.equals(that.tag)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = tag.hashCode();
    result = 31 * result + index;
    return result;
  }
}
