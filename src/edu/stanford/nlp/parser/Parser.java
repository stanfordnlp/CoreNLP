package edu.stanford.nlp.parser;

import java.util.List;

import edu.stanford.nlp.ling.HasWord;


/**
 * The interface for parser objects.  The only responsibility of a
 * parser is to return the parsability of input sentences.  That is,
 * parsers need only actually be recognizers/acceptors.
 * <p/>
 * Specification of the grammar or model
 * parameters is meant to be done by implementing classes' constructors.
 * If there is no specification of a goal, then this will also be determined
 * by the grammar or implementing class.
 *
 * @author Dan Klein
 * @author Christopher Manning
 */
public interface Parser {

  /**
   * Parses the given sentence.  For any words in the sentence which
   * implement HasTag, the tag will be believed.  The return value
   * will be false if the sentence is not parseable.  Acceptance is with
   * respect to some goal category, which may be specified by the grammar,
   * or may be a parser default (for instance, <code>S</code>).
   *
   * @param sentence A <code>List&lt;HasWord&gt;</code> to be parsed
   * @return true iff the sentence is recognized
   * @throws UnsupportedOperationException This will be thrown if for
   *           any reason the parser can't complete parsing this sentence, for
   *           example if the sentence is too long
   */
  public boolean parse(List<? extends HasWord> sentence);

}
