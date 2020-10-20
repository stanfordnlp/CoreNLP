package edu.stanford.nlp.parser;

/**
 * The interface for Viterbi parsers with options.  Viterbi parsers support
 * getBestParse, which returns a best parse of the input, or
 * <code>null</code> if no parse exists.
 *
 * @author Christopher Manning
 */
public interface ViterbiParserWithOptions extends ViterbiParser {

  /**
   * This will set options to a parser, in a way generally equivalent to
   * passing in the same sequence of command-line arguments.  This is a useful
   * convenience method when building a parser programmatically. The options
   * passed in should
   * be specified like command-line arguments, including with an initial
   * minus sign.
   *
   * @param flags Arguments to the parser, for example,
   *              {"-outputFormat", "typedDependencies", "-maxLength", "70"}
   * @throws IllegalArgumentException If an unknown flag is passed in
   */
  public void setOptionFlags(String... flags);

}
