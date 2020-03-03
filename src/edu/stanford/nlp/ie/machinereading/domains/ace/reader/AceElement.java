package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

/**
 * Base class for all ACE annotation elements.
 */
public class AceElement {

  /** Unique identifier for this element/ */
  protected final String mId;

  public AceElement(String id) {
    mId = id;
  }

  public String getId() { return mId; }

  public static void appendOffset(StringBuilder builder,
				  int offset) {
    for(int i = 0; i < offset; i ++){
      builder.append(' ');
    }
  }

}
