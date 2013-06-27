package edu.stanford.nlp.parser;

import edu.stanford.nlp.trees.Tree;

/**
 * The interface for Viterbi parsers.  Viterbi parsers support
 * getBestParse, which returns a best parse of the input, or
 * <code>null</code> if no parse exists.
 *
 * @author Dan Klein
 */

public interface ViterbiParser extends Parser {

  /**
   * Returns a best parse of the last sentence on which <code>parse</code> was
   * called, or null if none exists.
   *
   * @return The tree for the best parse
   */
  public Tree getBestParse();

}
