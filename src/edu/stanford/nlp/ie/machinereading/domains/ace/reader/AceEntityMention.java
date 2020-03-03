package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the ACE {@code <entity_mention>} construct.
 *
 * @author David McClosky
 */
public class AceEntityMention extends AceMention {
  @Override
  public String toString() {
    return "AceEntityMention [mHead=" + mHead + ", mLdctype=" + mLdctype
        + ", mType=" + mType + "]";
  }

  private String mType;

  private String mLdctype;

  private AceCharSeq mHead;

  /** Position of the head word of this mention */
  private int mHeadTokenPosition;

  /** The parent entity */
  private AceEntity mParent;

  /** The set of relation mentions that contain this entity mention */
  private List<AceRelationMention> mRelationMentions;

  /** The set of event mentions that contain this entity mention */
  private List<AceEventMention> mEventMentions;

  public AceEntityMention(String id,
			  String type,
			  String ldctype,
			  AceCharSeq extent,
			  AceCharSeq head) {
    super(id, extent);
    mType = type;
    mLdctype = ldctype;
    mHead = head;
    mExtent = extent;
    mHeadTokenPosition = -1;
    mParent = null;
    mRelationMentions = new ArrayList<>();
    mEventMentions = new ArrayList<>();
  }

  public String getMention() { return mType; }

  public void setParent(AceEntity e) { mParent = e; }
  public AceEntity getParent() { return mParent; }

  public AceCharSeq getHead() { return mHead; }
  public AceCharSeq getExtent() { return mExtent; }
  public int getHeadTokenPosition() { return mHeadTokenPosition; }

  public void setType(String s) { mType = s; }
  public String getType() { return mType; }
  public void setLdctype(String s) { mLdctype = s; }
  public String getLdctype() { return mLdctype; }

  public void addRelationMention(AceRelationMention rm) {
    mRelationMentions.add(rm);
  }
  public List<AceRelationMention> getRelationMentions() {
    return mRelationMentions;
  }

  public void addEventMention(AceEventMention rm) {
    mEventMentions.add(rm);
  }
  public List<AceEventMention> getEventMentions() {
    return mEventMentions;
  }

  public String toXml(int offset) {
    StringBuilder buffer = new StringBuilder();
    String mentionType = mType;

    appendOffset(buffer, offset);
    buffer.append("<entity_mention ID=\"" + getId() + "\" TYPE =\"" +
		  mentionType +
		  "\" LDCTYPE=\"" + mLdctype + "\">\n");

    buffer.append(mExtent.toXml("extent", offset + 2));
    buffer.append("\n");
    buffer.append(mHead.toXml("head", offset + 2));
    buffer.append("\n");

    appendOffset(buffer, offset);
    buffer.append("</entity_mention>");

    if(mentionType.equals("NAM")){
      // XXX: <entity_attributes> should be in Entity.toXml()
      buffer.append("\n");
      appendOffset(buffer, offset);
      buffer.append("<entity_attributes>\n");

      appendOffset(buffer, offset + 2);
      buffer.append("<name NAME=\"" + mHead.getText() + "\">\n");
      buffer.append(mHead.toXml(offset + 4) + "\n");
      appendOffset(buffer, offset + 2);
      buffer.append("</name>\n");

      appendOffset(buffer, offset);
      buffer.append("</entity_attributes>");
    }

    return buffer.toString();
  }

  private static boolean contains(ArrayList<Integer> set,
				  int elem) {
    for (Integer aSet : set) {
      if (elem == aSet) return true;
    }
    return false;
  }

  /**
   * Detects the head word of this mention
   * Heuristic:
   *   (a) the last token in mHead, if there are no prepositions
   *   (b) the last word before the first preposition
   * Note: the mHead must be already matched against tokens!
   */
  public void detectHeadToken(AceDocument doc) {
    ArrayList<Integer> preps = new ArrayList<>();
    preps.add(AceToken.OTHERS.get("IN"));

    for(int i = mHead.getTokenStart(); i <= mHead.getTokenEnd(); i ++){
      // found a prep
      if(contains(preps, doc.getToken(i).getPos()) &&
	 i > mHead.getTokenStart()){
	mHeadTokenPosition = i - 1;
	return;
      }
    }

    // set as the last word in mHead
    mHeadTokenPosition = mHead.getTokenEnd();
  }

  /** Verifies if this mention appears before the parameter in textual order */
  public boolean before(AceEntityMention em) {
    if(mHead.getByteEnd() < em.mHead.getByteStart()) return true;
    return false;
  }

  /** Verifies if this mention appears after the parameter in textual order */
  public boolean after(AceEntityMention em) {
    if(mHead.getByteStart() > em.mHead.getByteEnd()) return true;
    return false;
  }
}
