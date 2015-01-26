/**
 * Base class for all ACE annotation elements
 */

package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

public class AceElement {
  /** Unique identifier for this element */
  protected String mId;

  public AceElement(String id) {
    mId = id;
  }

  public String getId() { return mId; }

  public static void appendOffset(StringBuffer buffer,
				  int offset) {
    for(int i = 0; i < offset; i ++){
      buffer.append(" ");
    }
  }
}
