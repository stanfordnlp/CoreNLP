package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;


import edu.stanford.nlp.ling.Word;


/**
 * <code>Processor</code> whose <code>process</code> method converts a
 * numbers to the word "*NUMBER*"
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 */


public class NumberProcessor<L, F> extends FunctionProcessor<Word, Word, L, F> {

  public NumberProcessor() {
    super(new NumberFunction());
  }

}

/**
 * Returns <tt>in</tt> or a new Word with "*NUMBER*" if it was a number.
 */

class NumberFunction implements Function<Word, Word> {

  public Word apply(Word in) {
    try {
      Double.parseDouble(in.word());
      return (new Word("*NUMBER*"));
    } catch (NumberFormatException e) {
      return in;
    }
  }
}



