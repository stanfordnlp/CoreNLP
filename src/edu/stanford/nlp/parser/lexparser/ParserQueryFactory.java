package edu.stanford.nlp.parser.lexparser;

/**
 * An interface which indicates the class involved can return
 * ParserQuery objects.  Useful for something that wants to use
 * ParserQueries in a multithreaded manner with more than one possible
 * ParserQuery source.  For example, {@link
 * {edu.stanford.nlp.parser.lexparser.EvaluateTreebank} does this.
 *
 * @author John Bauer
 */
public interface ParserQueryFactory {
  ParserQuery parserQuery();
}
