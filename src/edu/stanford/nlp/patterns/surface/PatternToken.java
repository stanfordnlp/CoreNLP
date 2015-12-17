package edu.stanford.nlp.patterns.surface;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.patterns.ConstantsAndVariables;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Class to represent a target phrase. Note that you can give additional negative constraints 
 * in getTokenStr(List) but those are not used by toString, hashCode and equals functions
 * 
 * Author: Sonal Gupta (sonalg@stanford.edu)
 */

public class PatternToken implements Serializable {

  private static final long serialVersionUID = 1L;
  String tag;
  boolean useTag;
  int numWordsCompound;
  boolean useNER = false;
  String nerTag = null;
  boolean useTargetParserParentRestriction = false;
  String grandparentParseTag;

  public PatternToken(String tag, boolean useTag, boolean getCompoundPhrases,
      int numWordsCompound, String nerTag, boolean useNER,
      boolean useTargetParserParentRestriction, String grandparentParseTag) {
    if(useNER && nerTag == null){
      throw new RuntimeException("NER tag is null and using NER restriction is true. Check your data.");
    }
    this.tag = tag;
    this.useTag = useTag;
    this.numWordsCompound = numWordsCompound;
    if (!getCompoundPhrases)
      this.numWordsCompound = 1;
    this.nerTag = nerTag;
    this.useNER = useNER;
    this.useTargetParserParentRestriction = useTargetParserParentRestriction;
    if(useTargetParserParentRestriction){
      if(grandparentParseTag == null){
        Redwood.log(ConstantsAndVariables.extremedebug,"Grand parent parse tag null ");
        this.grandparentParseTag = "null";
      }
      else
        this.grandparentParseTag = grandparentParseTag;
    }
  }

  // static public PatternToken parse(String str) {
  // String[] t = str.split("#");
  // String tag = t[0];
  // boolean usetag = Boolean.parseBoolean(t[1]);
  // int num = Integer.parseInt(t[2]);
  // boolean useNER = false;
  // String ner = "";
  // if(t.length > 3){
  // useNER = true;
  // ner = t[4];
  // }
  //
  // return new PatternToken(tag, usetag, true, num, ner, useNER);
  // }

  public String toStringToWrite() {
    String s = "X";
    if (useTag)
      s += ":" + tag;
    if (useNER)
      s += ":" + nerTag;
    if (useTargetParserParentRestriction)
      s += ":" + grandparentParseTag;
    // if(notAllowedClasses !=null && notAllowedClasses.size() > 0){
    // s+= ":!(";
    // s+= StringUtils.join(notAllowedClasses,"|")+")";
    // }
    if (numWordsCompound > 1)
      s += "{" + numWordsCompound + "}";
    return s;
  }

  String getTokenStr(List<String> notAllowedClasses) {
    String str = " (?$term ";
    List<String> restrictions = new ArrayList<>();
    if (useTag) {
      restrictions.add("{tag:/" + tag + ".*/}");
    }

    if (useNER) {
      restrictions.add("{ner:" + nerTag + "}");
    }

    if (useTargetParserParentRestriction) {
      restrictions.add("{grandparentparsetag:\"" + grandparentParseTag + "\"}");
    }

    if (notAllowedClasses != null && notAllowedClasses.size() > 0) {
      for (String na : notAllowedClasses)
        restrictions.add("!{" + na + ":" + na +"}");
    }
    str += "[" + StringUtils.join(restrictions, " & ") + "]{1,"
        + numWordsCompound + "}";

    str += ")";

    str = StringUtils.toAscii(str);
    return str;
  }


  @Override
  public boolean equals(Object b) {
    if (!(b instanceof PatternToken))
      return false;
    PatternToken t = (PatternToken) b;
    if(this.useNER != t.useNER || this.useTag != t.useTag || this.useTargetParserParentRestriction != t.useTargetParserParentRestriction || this.numWordsCompound != t.numWordsCompound)
      return false;
      
    if (useTag && ! this.tag.equals(t.tag)) {
      return false;
    }

    if (useNER && ! this.nerTag.equals(t.nerTag)){
      return false;
    }

    if (useTargetParserParentRestriction && ! this.grandparentParseTag.equals(t.grandparentParseTag))
      return false;
    
    return true;
  }

  @Override
  public int hashCode() {
    return getTokenStr(null).hashCode();
  }

  public PatternToken copy() {
    PatternToken t = new PatternToken(tag, useTag, numWordsCompound > 1, numWordsCompound, nerTag, useNER, useTargetParserParentRestriction, grandparentParseTag);
    return t;
  }
}
