package edu.stanford.nlp.parser.lexparser;

import java.io.Serializable;
import java.util.Collection;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.stats.Counter;


public interface UnknownWordModel extends Serializable {

  /** One unknown word model may allow different options to be set; for example,
   *  several models of unknown words for a given language could be included in one
   *  class.  The unknown level can be used to set the model one would like.  Effects
   *  of the level will vary based on the implementing class.  If a given class only
   *  includes one model, setting the unknown level should have no effect.
   *
   *  @param unknownLevel Provides a choice between different unknown word
   *         processing schemes
   */
  void setUnknownLevel(int unknownLevel);


  /**
   * Get the level of equivalence classing for the model.
   *
   * @return The current level of unknown word equivalence classing
   */
  int getUnknownLevel();


  /**
   * Returns the lexicon used by this unknown word model;
   * lexicon is used to check information about words being seen/unseen
   *
   * @return The lexicon used by this unknown word model
   */
  Lexicon getLexicon();


  /**
   * Get the score of this word with this tag (as an IntTaggedWord) at this
   * loc.
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

  
  /** Calculate P(Tag|Signature) with Bayesian smoothing via just P(Tag|Unknown) */
  public double scoreProbTagGivenWordSignature(IntTaggedWord iTW, int loc, double smooth, String word);


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
  public String getSignature(String word, int loc);

  public int getSignatureIndex(int wordIndex, int sentencePosition, String word);


  /**
   * Adds the tagging with count to the data structures in this Lexicon.
   *
   * @param seen Whether tagging is seen
   * @param itw The tagging
   * @param count Its weight
   */
  public void addTagging(boolean seen, IntTaggedWord itw, double count);

  public Counter<IntTaggedWord> unSeenCounter();

}
