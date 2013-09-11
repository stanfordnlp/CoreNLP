package edu.stanford.nlp.process;


import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.objectbank.TokenizerFactory;


/**
 * Transforms a Document of Words into a Document of Sentences by grouping the
 * Words.
 *
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels
 * @param <F> The type of the features
 */
public class SentenceRetokenizingProcessor<IN extends HasWord, OUT extends HasWord, L, F> extends AbstractListProcessor<ArrayList<IN>, ArrayList<OUT>, L, F> {

  /**
   * The tokenizer to use for retokenizing sentences.
   */
  private TokenizerFactory<OUT> tokenizerFactory;

  /**
   * Returns a new <code>List</code> where each element is a Sentence from the
   * <code>List</code> input,
   * retokenized with the tokenizer provided at construction.  Input must be
   * of class <code>List</code>. Specifically, concatenates the old
   * Sentences with whitespace, and then applies the tokenizer anew.
   */
  public List<ArrayList<OUT>> process(List<? extends ArrayList<IN>> input) {

    List<ArrayList<OUT>> output = new ArrayList<ArrayList<OUT>>();

    for (ArrayList<IN> oldS : input) {
      output.add(retokenize(oldS));
    }

    return output;


  }

  public ArrayList<OUT> retokenize(ArrayList<IN> oldS) {
    String sString = Sentence.listToString(oldS);
    Tokenizer<OUT> toke = tokenizerFactory.getTokenizer(new StringReader(sString));
    ArrayList<OUT> newS = new ArrayList<OUT>(toke.tokenize());
    return newS;
  }


  /**
   * Create a <code>SentenceRetokenizingProcessor</code> that uses the
   * <code>TokenizerFactory</code> tokenizerFactory.
   */
  public SentenceRetokenizingProcessor(TokenizerFactory<OUT> tokenizerFactory) {
    this.tokenizerFactory = tokenizerFactory;
  }

}
