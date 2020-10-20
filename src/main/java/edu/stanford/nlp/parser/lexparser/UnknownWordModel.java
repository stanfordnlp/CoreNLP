package edu.stanford.nlp.parser.lexparser;

import java.io.Serializable;

import edu.stanford.nlp.stats.Counter;


/** This class defines the runtime interface for unknown words
 *  in lexparser. See UnknownWordModelTrainer for how unknown
 *  word models are built from training data.
 *
 *  @author Anna Rafferty
 *  @author Christopher Manning
 */
public interface UnknownWordModel extends Serializable {

  /**
   * Get the level of equivalence classing for the model.
   * One unknown word model may allow different options to be set; for example,
   * several models of unknown words for a given language could be included in one
   *  class.  The unknown level can be queried with this method.
   *
   * @return The current level of unknown word equivalence classing
   */
  int getUnknownLevel();


  /**
   * Returns the lexicon used by this unknown word model. The
   * lexicon is used to check information about words being seen/unseen.
   *
   * @return The lexicon used by this unknown word model
   */
  Lexicon getLexicon();


  /**
   * Get the score of this word with this tag (as an IntTaggedWord) at this
   * location loc in a sentence.
   * (Presumably an estimate of P(word | tag), usually calculated as
   * P(signature | tag).)
   * Assumes the word is unknown.
   *
   * @param iTW An IntTaggedWord pairing a word and POS tag
   * @param loc The position in the sentence.  <i>In the default implementation
   *               this is used only for unknown words to change their
   *               probability distribution when sentence initial.  Now,
   *               a negative value </i>
   * @param c_Tseen Total count of this tag (on seen words) in training
   * @param total Total count of word tokens in training
   * @param smooth Weighting on prior P(T|U) in estimate
   * @param word The word itself; useful so we don't look it up in the index
   * @return A double valued score, usually - log P(word|tag)
   */
  float score(IntTaggedWord iTW, int loc, double c_Tseen, double total, double smooth, String word);


  /** Calculate P(Tag|Signature) with Bayesian smoothing via just P(Tag|Unknown). */
  double scoreProbTagGivenWordSignature(IntTaggedWord iTW, int loc, double smooth, String word);


  /**
   * This routine returns a String that is the "signature" of the class of a
   * word. For, example, it might represent whether it is a number of ends in
   * -s. The strings returned by convention match the pattern UNK or UNK-.* ,
   * which is just assumed to not match any real word. Behavior depends on the
   * unknownLevel (-uwm flag) passed in to the class.
   *
   * @param word The word to make a signature for
   * @param loc Its position in the sentence (mainly so sentence-initial
   *          capitalized words can be treated differently)
   * @return A String that is its signature (equivalence class)
   */
  String getSignature(String word, int loc);

  /** Returns an unknown word signature as an integer index rather than as a String. */
  int getSignatureIndex(int wordIndex, int sentencePosition, String word);


  /**
   * Adds the tagging with count to the data structures in this Lexicon.
   *
   * @param seen Whether tagging is seen
   * @param itw The tagging
   * @param count Its weight
   */
  void addTagging(boolean seen, IntTaggedWord itw, double count);

  /** Returns a Counter from IntTaggedWord to how often they have been seen. */
  Counter<IntTaggedWord> unSeenCounter();

}
