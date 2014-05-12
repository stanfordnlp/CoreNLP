package edu.stanford.nlp.patterns.surface;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.util.StringUtils;


/**
 * Class to represent a target phrase. The class is not completely kosher - toString, hashCode and equals to do not notAllowedClasses, which can be given as input to getTokenStr()
 * 
 * Author: Sonal Gupta (sonalg@stanford.edu)
 */

public class PatternToken implements Serializable {

  private static final long serialVersionUID = 1L;
  String tag;
  boolean useTag;
  int numWordsCompound;
  String prevContext = "", nextContext = "";
  boolean useNER = false;
  String nerTag = null;
  
  public PatternToken(String tag, boolean useTag, boolean getCompoundPhrases, int numWordsCompound, String nerTag, boolean useNER) {
    this.tag = tag;
    this.useTag = useTag;
    this.numWordsCompound = numWordsCompound;
    if (!getCompoundPhrases)
      numWordsCompound = 1;
    this.nerTag = nerTag;
    this.useNER = useNER;
  }

//  static public PatternToken parse(String str) {
//    String[] t = str.split("#");
//    String tag = t[0];
//    boolean usetag = Boolean.parseBoolean(t[1]);
//    int num = Integer.parseInt(t[2]);
//    boolean useNER = false;
//    String ner = "";
//    if(t.length > 3){
//      useNER = true;
//      ner = t[4];
//    }
//    
//    return new PatternToken(tag, usetag, true, num, ner, useNER);
//  }

  public String toStringToWrite() {
    String s = "X";
    if(useTag)
      s+=":"+tag;
    if(useNER)
      s+=":"+nerTag;
//    if(notAllowedClasses !=null && notAllowedClasses.size() > 0){
//      s+= ":!(";
//      s+= StringUtils.join(notAllowedClasses,"|")+")"; 
//    }
    if(numWordsCompound > 1)
      s+="{"+numWordsCompound+"}";
    return s;
  }

  String getTokenStr(List<String> notAllowedClasses) {
    String str = " (?$term ";
    if (!prevContext.isEmpty()) {
      str += prevContext + " ";
    }

    List<String> restrictions = new ArrayList<String>();
    if (useTag) {
      restrictions.add("{tag:/" + tag + ".*/}");
    }
    
    if(useNER){
      restrictions.add("{ner:"+nerTag+"}");
    }
    
    if(notAllowedClasses != null && notAllowedClasses.size() > 0){
      for(String na: notAllowedClasses)
        restrictions.add("!{"+na+"}");
    }
    str += "["+ StringUtils.join(restrictions, " & ") + "]{1," + numWordsCompound + "}";

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

    if (t.getTokenStr(null).equals(this.getTokenStr(null)))
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
    return getTokenStr(null).hashCode();
  }
}
