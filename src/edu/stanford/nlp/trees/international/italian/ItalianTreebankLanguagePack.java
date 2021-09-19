package edu.stanford.nlp.trees.international.italian;

import edu.stanford.nlp.trees.AbstractTreebankLanguagePack;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.LeftHeadFinder;

/**
 * Treebank language pack suitable for the Italian Turin treebank.
 *<br>
 * Note that the original Turin dataset had quite a few oddities which
 * made it unsuitable for directly using it.  Stanza has a treebank
 * prep script which greatly simplifies it, though
 */
public class ItalianTreebankLanguagePack extends AbstractTreebankLanguagePack {

  private static final long serialVersionUID = -235378253615245L;

  // original treebank has PUNCT for some things, like -, but in general
  // the tags are ,.:
  // the UD tagger will redo the tags to be PUNCT
  private static final String[] punctTags = { "-LRB-", "-RRB-", ",", ".", ":", "\"", "PUNCT" };

  private static final String[] SFPunctTags = { ".", ":", "PUNCT" };

  private static final String[] punctWords = { "!", "\"", "&", "'", "§", "(", ")", "[", "]", "+", ",", "-", ".", "...", "/", "—", ":", ";", "==", "?" };
  
  private static final String[] startSymbols = { "ROOT" };

  // weirdly ... doesn't end sentences
  private static final String[] SFPunctWords = {":", ".", "!", "?", ";" };

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
    return SFPunctTags;
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
    return "mrg";
  }

  /** {@inheritDoc} */
  @Override
  public HeadFinder headFinder() {
    return new LeftHeadFinder();
  }

  /** {@inheritDoc} */
  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return new LeftHeadFinder();
  }

}
