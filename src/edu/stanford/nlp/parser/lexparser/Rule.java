package edu.stanford.nlp.parser.lexparser;

import java.io.Serializable;

/**
 * Interface for int-format grammar rules.
 * This replaces the class that used to be a superclass for UnaryRule and BinaryRule.
 *
 * @author Christopher Manning
 */
public interface Rule {

  public float score();

  public int parent();

}
