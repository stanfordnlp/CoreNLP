
package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.util.List;
import java.util.ArrayList;

/**
 * Stores one ACE event
 */
public class AceEvent extends AceElement {
  private String mType;

  private String mSubtype;

  private String mModality;
  
  private String mPolarity;
  
  private String mGenericity;
  
  private String mTense;

  /** The list of mentions for this event */
  private List<AceEventMention> mMentions;

  public static final String NIL_LABEL = "nil";

  public AceEvent(String id,
		     String type,
		     String subtype,
		     String modality,
		     String polarity,
		     String genericity,
		     String tense) {
    super(id);
    mType = type;
    mSubtype = subtype;
    mModality = modality;
    mPolarity = polarity;
    mGenericity = genericity;
    mTense = tense;
    mMentions = new ArrayList<>();
  }

  public void addMention(AceEventMention m) { 
    mMentions.add(m); 
    m.setParent(this);
  }
  
  public AceEventMention getMention(int which) { 
    return mMentions.get(which); 
  }
  public int getMentionCount() { return mMentions.size(); }

  public String getType() { return mType; }
  public void setType(String s) { mType = s; }
  public String getSubtype() { return mSubtype; }
  public void setSubtype(String s) { mSubtype = s; }
  public String getModality() { return mModality; }
  public void setModality(String modality) { this.mModality = modality; }
  public String getmPolarity() { return mPolarity; }
  public void setmPolarity(String mPolarity) { this.mPolarity = mPolarity; }
  public String getGenericity() { return mGenericity; }
  public void setGenericity(String genericity) { this.mGenericity = genericity; }
  public String getTense() { return mTense; }
  public void setTense(String tense) { this.mTense = tense; }

  // TODO: didn't implement toXml
}