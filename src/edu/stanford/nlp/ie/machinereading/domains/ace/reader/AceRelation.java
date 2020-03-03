
package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.util.List;
import java.util.ArrayList;

/**
 * Stores one ACE relation
 */
public class AceRelation extends AceElement {
  private String mType;

  private String mSubtype;

  private String mModality;

  private String mTense;

  /** The list of mentions for this event */
  private List<AceRelationMention> mMentions;

  public static final String NIL_LABEL = "nil";

  public AceRelation(String id,
		     String type,
		     String subtype,
		     String modality,
		     String tense) {
    super(id);
    mType = type;
    mSubtype = subtype;
    mModality = modality;
    mTense = tense;
    mMentions = new ArrayList<>();
  }

  public void addMention(AceRelationMention m) { 
    mMentions.add(m); 
    m.setParent(this);
  }
  
  public AceRelationMention getMention(int which) { 
    return mMentions.get(which); 
  }
  public int getMentionCount() { return mMentions.size(); }

  public String getType() { return mType; }
  public void setType(String s) { mType = s; }
  public String getSubtype() { return mSubtype; }
  public void setSubtype(String s) { mSubtype = s; }

  public String toXml(int offset) {
    StringBuilder buffer = new StringBuilder();
    appendOffset(buffer, offset);
    buffer.append("<relation ID=\"" + getId() + "\" TYPE =\"" + mType +
		  "\" SUBTYPE=\"" + mSubtype + "\" MODALITY=\"" + mModality + 
		  "\" TENSE=\"" + mTense + "\">\n");

    AceRelationMentionArgument arg1 = mMentions.get(0).getArgs()[0];
    AceRelationMentionArgument arg2 = mMentions.get(0).getArgs()[1];
    if(arg1.getRole().equals("Arg-1")){ // left to right
      buffer.append(arg1.toXmlShort(offset + 2) + "\n");
      buffer.append(arg2.toXmlShort(offset + 2) + "\n");  
    } else { // right to left
      buffer.append(arg2.toXmlShort(offset + 2) + "\n");
      buffer.append(arg1.toXmlShort(offset + 2) + "\n");  
    }  

    for(AceRelationMention m: mMentions){
      buffer.append(m.toXml(offset + 2));
      buffer.append("\n");
    }

    appendOffset(buffer, offset);
    buffer.append("</relation>");
    return buffer.toString();
  }
}
