package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.TreebankLanguagePack;


/** @author Dan Klein */
class BasicCategoryTagProjection implements TagProjection {

  private static final long serialVersionUID = -2322431101811335089L;

  TreebankLanguagePack tlp;

  public BasicCategoryTagProjection(TreebankLanguagePack tlp) {
    this.tlp = tlp;
  }

  public String project(String tagStr) {
    // return tagStr;
    String ret = tlp.basicCategory(tagStr);
    // System.err.println("BCTP mapped " + tagStr + " to " + ret);
    return ret;
  }

}
