package edu.stanford.nlp.parser.common;

import java.util.List;

import edu.stanford.nlp.parser.metrics.Eval;

/**
 * An interface for the classes which store the data for a parser.
 * Objects which inherit this interface have a way to produce
 * ParserQuery objects, have a general Options object, and return a
 * list of Evals to perform on a parser.  This helps classes such as
 * {@link edu.stanford.nlp.parser.lexparser.EvaluateTreebank} 
 * analyze the performance of a parser.
 *
 * @author John Bauer
 */
public interface ParserGrammar {
  ParserQuery parserQuery();

  /**
   * Returns a list of extra Eval objects to use when scoring the parser.
   * TODO: perhaps this should go elsewhere, or perhaps we should make
   * this an "AbstractParser" interface of some kind
   */
  List<Eval> getExtraEvals();
}
