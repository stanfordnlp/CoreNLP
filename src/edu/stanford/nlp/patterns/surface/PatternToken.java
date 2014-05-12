package edu.stanford.nlp.patterns.surface;

import java.io.Serializable;

import edu.stanford.nlp.util.StringUtils;

public class PatternToken implements Serializable {

  private static final long serialVersionUID = 1L;
  String tag;
  boolean useTag;
  int numWordsCompound;
  String prevContext = "", nextContext = "";

  public PatternToken(String tag, boolean useTag, boolean getCompoundPhrases, int numWordsCompound) {
    this.tag = tag;
    this.useTag = useTag;
    this.numWordsCompound = numWordsCompound;
    if (!getCompoundPhrases)
      numWordsCompound = 1;
  }

  static public PatternToken parse(String str) {
    String[] t = str.split("#");
    String tag = t[0];
    boolean usetag = Boolean.parseBoolean(t[1]);
    int num = Integer.parseInt(t[2]);
    return new PatternToken(tag, usetag, true, num);
  }

  public String toStringToWrite() {
    String s = "X";
    if(useTag)
      s+=":"+tag;
    if(numWordsCompound > 1)
      s+="{"+numWordsCompound+"}";
    return s;
  }

  String getTokenStr() {
    String str = " (?$term ";
    if (!prevContext.isEmpty()) {
      str += prevContext + " ";
    }

    str += "[";
    if (useTag) {
      str += "{tag:/" + tag + ".*/}";
    }

    str += "]{1," + numWordsCompound + "}";
    if (!nextContext.isEmpty())
      str += " " + nextContext;

    str += ")";

    str = StringUtils.toAscii(str);
    return str;
  }

  void setPreviousContext(String str) {
    this.prevContext = str;
  }

  void setNextContext(String str) {
    this.nextContext = str;
  }

  @Override
  public boolean equals(Object b) {
    if (!(b instanceof PatternToken))
      return false;
    PatternToken t = (PatternToken) b;

    if (t.getTokenStr().equals(this.getTokenStr()))
      return true;
    // if (useTag != t.useTag)
    // return false;
    // else if (useTag && !tag.equals(t.tag)) {
    // return false;
    // }
    // if (numWordsCompound != t.numWordsCompound)
    // return false;

    return false;
  }

  @Override
  public int hashCode() {
    return getTokenStr().hashCode();
  }
}
