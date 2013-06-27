package edu.stanford.nlp.parser.lexparser;

import java.io.Serializable;


/**
 * An interface for projecting POS tags onto a reduced
 * set for the dependency grammar.
 *
 * @author Dan Klein
 */
public interface TagProjection extends Serializable {

  /** Project more split dependency space onto less split space.
   *
   *  @param tagStr The full name of the tag
   *  @return A name for the  tag in a reduced tag space
   */
  String project(String tagStr);

}

