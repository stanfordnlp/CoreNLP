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

  // todo [cdm 2014]: Change this to using StringBuilder or Appendable or similar
  public static void appendOffset(StringBuffer buffer,
				  int offset) {
    for(int i = 0; i < offset; i ++){
      buffer.append(' ');
    }
  }

}
