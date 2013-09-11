package edu.stanford.nlp.parser.lexparser;

/** This tag projection just returns the same tag space.
 *  @author Dan Klein
 */
class IdentityTagProjection implements TagProjection {

  private static final long serialVersionUID = 6432670180464681120L;

  public String project(String tagStr) {
    return tagStr;
  }

}
