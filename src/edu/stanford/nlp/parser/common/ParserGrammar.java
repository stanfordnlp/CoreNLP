package edu.stanford.nlp.parser.common;

import java.util.List;

import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.parser.metrics.ParserQueryEval;
import edu.stanford.nlp.trees.TreebankLanguagePack;
// TODO: it would be nice to move these to common, but that would
// wreck all existing models
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;

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
   */
  List<Eval> getExtraEvals();

  /**
   * Return a list of Eval-style objects which care about the whole
   * ParserQuery, not just the finished tree
   */
  List<ParserQueryEval> getParserQueryEvals();

  Options getOp();

  TreebankLangParserParams getTLPParams();

  TreebankLanguagePack treebankLanguagePack();
}
