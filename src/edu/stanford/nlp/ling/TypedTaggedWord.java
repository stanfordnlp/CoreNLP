package edu.stanford.nlp.ling;


/**
 * A <code>TypedTaggedWord</code> object contains a word, it's tag, and it's type.
 * The <code>value()</code> of a TypedTaggedWord is its Word. The tag and type are
 * secondary.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class TypedTaggedWord extends TaggedWord implements HasType {
  /**
   * 
   */
  private static final long serialVersionUID = -1116081900490995750L;
  private int type; // type for this word

  public TypedTaggedWord() {
    super();
  }

  public TypedTaggedWord(Label oldLabel) {
    super(oldLabel.value());
    if (oldLabel instanceof HasTag) {
      setTag(((HasTag) oldLabel).tag());
    }
    if (oldLabel instanceof HasType) {
      setType(((HasType) oldLabel).type());
    }
  }

  public TypedTaggedWord(String word) {
    super(word);
  }

  public TypedTaggedWord(String word, int type) {
    super(word);
    setType(type);
  }

  public TypedTaggedWord(String word, String tag, int type) {
    super(word, tag);
    setType(type);
  }

  public int type() {
    return (type);
  }

  public void setType(int type) {
    this.type = type;
  }

  /**
   * Returns a String representing this TypedTaggedWord of the form "word/tag (type)".
   */
  @Override
  public String toString() {
    return (word() + (tag() == null ? "" : "/" + tag()) + " (" + type() + ")");
  }

}

