package edu.stanford.nlp.parser.lexparser;

import java.util.List;

import edu.stanford.nlp.parser.metrics.Eval;

/**
 * An interface which indicates the class involved can return
 * ParserQuery objects.  Useful for something that wants to use
 * ParserQueries in a multithreaded manner with more than one possible
 * ParserQuery source.  For example, 
 * {@link edu.stanford.nlp.parser.lexparser.EvaluateTreebank} 
 * does this.
 * <br>
 * TODO: perhaps this should actually just be an AbstractParser or something like that
 *
 * @author John Bauer
 */
public interface ParserQueryFactory {
  ParserQuery parserQuery();

  /**
   * Returns a list of extra Eval objects to use when scoring the parser.
   * TODO: perhaps this should go elsewhere, or perhaps we should make
   * this an "AbstractParser" interface of some kind
   */
  List<Eval> getExtraEvals();
}
