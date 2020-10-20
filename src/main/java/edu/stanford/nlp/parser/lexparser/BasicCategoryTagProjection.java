package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.trees.TreebankLanguagePack;


/** @author Dan Klein */
public class BasicCategoryTagProjection implements TagProjection  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(BasicCategoryTagProjection.class);

  private static final long serialVersionUID = -2322431101811335089L;

  TreebankLanguagePack tlp;

  public BasicCategoryTagProjection(TreebankLanguagePack tlp) {
    this.tlp = tlp;
  }

  public String project(String tagStr) {
    // return tagStr;
    String ret = tlp.basicCategory(tagStr);
    // log.info("BCTP mapped " + tagStr + " to " + ret);
    return ret;
  }

}
