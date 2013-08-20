package edu.stanford.nlp.process;


import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.Function;

/**
 * <code>Processor</code> whose <code>process</code> method converts a
 * collection of mixed-case Words to a collection of lowercase Words.
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 * @param <F> The type of the features
 */

public class LowercaseProcessor<L, F> extends FunctionProcessor<Word,Word, L, F> {

  public LowercaseProcessor() {
    super(new LowercaseFunction());
  }

  /**
   * Takes a Word and returns a lowercase version of the Word.
   */
  public static class LowercaseFunction implements Function<Word,Word> {

    private static final long serialVersionUID = 1L;

    /**
     * Lowercases the Word coming in
     */
    public Word apply(Word in) {
      return (new Word(in.word().toLowerCase()));
    }

  }
  
}
