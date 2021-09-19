package edu.stanford.nlp.trees.international.hungarian;

import edu.stanford.nlp.trees.AbstractTreebankLanguagePack;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.RightHeadFinder;

/**
 * Treebank language pack suitable for the Hungarian section of SPMRL
 */
public class HungarianTreebankLanguagePack extends AbstractTreebankLanguagePack {

  private static final long serialVersionUID = -7982635612452142L;

  // both sentence final and mid-sentence punctuation use PUNC
  // the UD tagger will redo the tags to be PUNCT
  private static final String[] punctTags = { "PUNC", "PUNCT" };

  private static final String[] punctWords = { "!", "\"", "&", "'", "§", "(", ")", "+", ",", "-", ".", "...", "/", "—", ":", ";", "==", "?" };
  
  private static final String[] startSymbols = { "ROOT" };

  private static final String[] SFPunctWords = {".", "!", "?"};

  @Override
  public String[] punctuationTags() {
    return punctTags;
  }

  @Override
  public String[] punctuationWords() {
    return punctWords;
  }

  @Override
  public String[] sentenceFinalPunctuationTags() {
    return punctTags;
  }

  @Override
  public String[] sentenceFinalPunctuationWords() {
    return SFPunctWords;
  }

  @Override
  public String[] startSymbols() {
    return startSymbols;
  }
  
  /** {@inheritDoc} */
  @Override
  public String treebankFileExtension() {
    return "ptb";
  }

  /** {@inheritDoc} */
  @Override
  public HeadFinder headFinder() {
    return new RightHeadFinder();
  }

  /** {@inheritDoc} */
  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return new RightHeadFinder();
  }

}
