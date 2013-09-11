package edu.stanford.nlp.trees.international.icegb;

import edu.stanford.nlp.trees.AbstractTreebankLanguagePack;
import edu.stanford.nlp.trees.HeadFinder;


/**
 * This is a language pack from the ICE-GB corpus. UNDER CONSTRUCTION!
 *
 * @author Pi-Chuan Chang
 */
public class ICEGBLanguagePack extends AbstractTreebankLanguagePack {

  private static final long serialVersionUID = 7302291465126255411L;

  public ICEGBLanguagePack() {
  }

  private static final String[] startSymbols = {"ROOT"}; //??

  private static final String[] punctTags = {"PUNC"};
  private static final String[] punctWords = {"=", ">", "-", ",", ";", ":", "!", "?", "/", ".", "..", "...", "....", ".....", "........", "..........", "'", "(", ")", "[", "]", "&approximate-sign;", "&arrow;", "&arrowhead;", "&black-square;", "&bullet;", "&caret;", "&curved-dash;", "&dagger;", "&dot;", "&dotted-line;", "&down-arrow;", "&ldquo;", "&long-dash;", "&lsquo;", "&rdquo;", "&right-arrow;", "&rsquo;", "&semi;", "&smaller-than;", "&square;", "&star;"};


  /**
   * Returns a String array of treebank start symbols.
   *
   * @return The start symbols
   */
  @Override
  public String[] startSymbols() {
    return startSymbols;
  }


  /**
   * Returns a String array of punctuation tags for the ICE-GB corpus.
   *
   * @return The punctuation tags
   */
  @Override
  public String[] punctuationTags() {
    return punctTags;
  }

  /**
   * Returns a String array of punctuation words for the ICE-GB corpus.
   *
   * @return The punctuation words
   */
  @Override
  public String[] punctuationWords() {
    return punctWords;
  }


  /**
   * Returns a String array of sentence final punctuation tags for the
   * ICE-GB corpus.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public String[] sentenceFinalPunctuationTags() {
    return punctTags;
  }

  /**
   * Returns a String array of sentence final punctuation words for the
   * ICE-GB corpus.
   *
   * @return The sentence final punctuation words
   */
  public String[] sentenceFinalPunctuationWords() {
    // TODO: put "final" punctuation words here
    return punctWords;
  }

  /**
   * Return an array of characters at which a String should be
   * truncated to give the basic syntactic category of a label.
   *
   * @return An array of characters that set off label name suffixes
   */
  @Override
  public char[] labelAnnotationIntroducingCharacters() {
    return annotationIntroducingChars;
  }

  private static char[] annotationIntroducingChars = {'-', '=', '|', '#', '^', '~'};

  /**
   * Returns the extension of treebank files for this treebank.
   * This is "COR".
   */
  public String treebankFileExtension() {
    return "COR";
  }

  /** {@inheritDoc} */
  public HeadFinder headFinder() {
    return new ICEGBHeadFinder(this);
  }

  /** {@inheritDoc} */
  public HeadFinder typedDependencyHeadFinder() {
    return new ICEGBHeadFinder(this);
  }

}
