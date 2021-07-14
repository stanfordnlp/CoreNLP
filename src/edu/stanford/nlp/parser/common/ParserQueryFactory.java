package edu.stanford.nlp.parser.common;

/**
 * Interface for the eval tool.
 * <br>
 * A thing which implements this can produce parser queries
 *
 * @author John Bauer
 */
public interface ParserQueryFactory {
  public abstract ParserQuery parserQuery();
}
