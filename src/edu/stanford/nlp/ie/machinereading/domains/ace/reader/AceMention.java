/**
 * Superclass for all ACE mentions (events, entities, values, etc)
 */

package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

public class AceMention extends AceElement {
  protected AceCharSeq mExtent;

  protected AceMention(String id,
		       AceCharSeq extent) {
    super(id);
    mExtent = extent;
  }

  public AceCharSeq getExtent() { return mExtent; }

  public String toXml(int offset) { return ""; }
}
