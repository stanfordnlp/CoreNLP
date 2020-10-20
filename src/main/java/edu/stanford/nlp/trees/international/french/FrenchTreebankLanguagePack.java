package edu.stanford.nlp.trees.international.french;

import edu.stanford.nlp.international.french.FrenchMorphoFeatureSpecification;
import edu.stanford.nlp.international.french.process.FrenchTokenizer;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.AbstractTreebankLanguagePack;
import edu.stanford.nlp.trees.HeadFinder;


/**
 * Language pack for the French treebank.
 *
 * @author mcdm
 */
public class FrenchTreebankLanguagePack extends AbstractTreebankLanguagePack {

  private static final long serialVersionUID = -7338244949063822519L;

  //wsg2011: The distributed treebank is encoding in ISO8859_1, but
  //the current FrenchTreebankParserParams is currently configured to
  //read UTF-8, PTB style trees that have been extracted from the XML
  //files.
  public static final String FTB_ENCODING = "UTF-8";

  //The raw treebank uses "PONCT". Change to LDC convention.
  private static final String[] frenchPunctTags = {"PUNC"};

  private static final String[] frenchSFPunctTags = {"PUNC"};

  // new tokenizers should return (), old tokenizers return -LRB- -RRB-.  so we anticipate both
  private static final String[] frenchPunctWords = {"=","*","/","\\","]","[","\"","''", "'", "``", "`", "-LRB-", "-RRB-", "(", ")", "-LCB-", "-RCB-", ".", "?", "!", ",", ":", "-", "--", "...", ";", "&quot;"};

  private static final String[] frenchSFPunctWords = {".", "!", "?"};

  private static final char[] annotationIntroducingChars = {'-', '=', '|', '#', '^', '~'};

  private static final String[] frenchStartSymbols = {"ROOT"};

  @Override
  public TokenizerFactory<? extends HasWord> getTokenizerFactory() {
    return FrenchTokenizer.ftbFactory();
  }
  
  @Override
  public String getEncoding() {
    return FTB_ENCODING;
  }
  
  /**
   * Returns a String array of punctuation tags for this treebank/language.
   *
   * @return The punctuation tags
   */
  @Override
  public String[] punctuationTags() {
    return frenchPunctTags;
  }


  /**
   * Returns a String array of punctuation words for this treebank/language.
   *
   * @return The punctuation words
   */
  @Override
  public String[] punctuationWords() {
    return frenchPunctWords;
  }


  /**
   * Returns a String array of sentence final punctuation tags for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public String[] sentenceFinalPunctuationTags() {
    return frenchSFPunctTags;
  }

  /**
   * Returns a String array of sentence final punctuation words for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public String[] sentenceFinalPunctuationWords() {
    return frenchSFPunctWords;
  }


  /**
   * Return an array of characters at which a String should be
   * truncated to give the basic syntactic category of a label.
   * The idea here is that French treebank style labels follow a syntactic
   * category with various functional and crossreferencing information
   * introduced by special characters (such as "NP-SUBJ").  This would
   * be truncated to "NP" by the array containing '-'.
   *
   * @return An array of characters that set off label name suffixes
   */
  @Override
  public char[] labelAnnotationIntroducingCharacters() {
    return annotationIntroducingChars;
  }


  /**
   * Returns a String array of treebank start symbols.
   *
   * @return The start symbols
   */
  @Override
  public String[] startSymbols() {
    return frenchStartSymbols;
  }


  /**
   * Returns the extension of treebank files for this treebank.
   */
  @Override
  public String treebankFileExtension() {
    return "xml";
  }

  /** {@inheritDoc} */
  @Override
  public HeadFinder headFinder() {
    return new FrenchHeadFinder(this);
  }
  
  /** {@inheritDoc} */
  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return new FrenchHeadFinder(this);
  }
  
  @Override
  public MorphoFeatureSpecification morphFeatureSpec() {
    return new FrenchMorphoFeatureSpecification();
  }

}
