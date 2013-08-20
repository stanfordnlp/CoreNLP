package edu.stanford.nlp.trees.international.arabic;

import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.international.arabic.ArabicMorphoFeatureSpecification;
import edu.stanford.nlp.international.arabic.process.ArabicTokenizer;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.HasWord;

/**
 * Specifies the treebank/language specific components needed for
 * parsing the Penn Arabic Treebank (ATB). This language pack has been updated for
 * ATB1v4, ATB2v3, and ATB3v3.2
 * <p>
 * The encoding for the ATB is the default UTF-8 specified in AbstractTreebankLanguagePack.
 *
 * @author Christopher Manning
 * @author Mona Diab
 * @author Roger Levy
 * @author Spence Green
 *
 */
public class ArabicTreebankLanguagePack extends AbstractTreebankLanguagePack {

  private static final long serialVersionUID = 9081305982861675328L;

  private static final String[] collinsPunctTags = {"PUNC"};

  private static final String[] pennPunctTags = {"PUNC"};

  private static final String[] pennPunctWords = {".","\"",",","-LRB-","-RRB-","-",":","/","?","_","*","%","!",">","-PLUS-","...",";","..","&","=","ر","'","\\","`","......"};

  private static final String[] pennSFPunctTags = {"PUNC"};

  private static final String[] pennSFPunctWords = {".", "!", "?"};

  /**
   * The first 3 are used by the Penn Treebank; # is used by the
   * BLLIP corpus, and ^ and ~ are used by Klein's lexparser.
   * Chris deleted '_' for Arabic as it appears in tags (NO_FUNC).
   * June 2006: CDM tested _ again with true (new) Treebank tags to see if it
   * was useful for densening up the tag space, but the results were negative.
   * Roger added + for Arabic but Chris deleted it again, since unless you've
   * recoded determiners, it screws up DET+NOUN, etc.  (That is, it would only be useful if
   * you always wanted to cut at the first '+', but in practice that is not viable, certainly
   * not with the IBM ATB processing either.)
   */
  private static final char[] annotationIntroducingChars = {'-', '=', '|', '#', '^', '~'};

  /**
   * This is valid for "BobChrisTreeNormalizer" conventions only.
   * wsg: "ROOT" should always be the first value. See {@link #startSymbol} in
   * the parent class.
   */
  private static final String[] pennStartSymbols = {"ROOT"};


  /**
   * Returns a String array of punctuation tags for this treebank/language.
   *
   * @return The punctuation tags
   */
  @Override
  public String[] punctuationTags() {
    return pennPunctTags;
  }


  /**
   * Returns a String array of punctuation words for this treebank/language.
   *
   * @return The punctuation words
   */
  @Override
  public String[] punctuationWords() {
    return pennPunctWords;
  }


  /**
   * Returns a String array of sentence final punctuation tags for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public String[] sentenceFinalPunctuationTags() {
    return pennSFPunctTags;
  }

  /**
   * Returns a String array of sentence final punctuation words for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  public String[] sentenceFinalPunctuationWords() {
    return pennSFPunctWords;
  }

  /**
   * Returns a String array of punctuation tags that EVALB-style evaluation
   * should ignore for this treebank/language.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @return Whether this is a EVALB-ignored punctuation tag
   */
  @Override
  public String[] evalBIgnoredPunctuationTags() {
    return collinsPunctTags;
  }


  /**
   * Return an array of characters at which a String should be
   * truncated to give the basic syntactic category of a label.
   * The idea here is that Penn treebank style labels follow a syntactic
   * category with various functional and crossreferencing information
   * introduced by special characters (such as "NP-SBJ=1").  This would
   * be truncated to "NP" by the array containing '-' and "=".
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
    return pennStartSymbols;
  }

  /**
   * TODO: there is no way to change this using options.
   */
  private TokenizerFactory<? extends HasWord> tf =
    ArabicTokenizer.atbFactory();

  /**
   * Return a tokenizer which might be suitable for tokenizing text
   * that will be used with this Treebank/Language pair.  We tokenize
   * the Arabic using the ArabicTokenizer class.
   *
   * @return A tokenizer
   */
  @Override
  public TokenizerFactory<? extends HasWord> getTokenizerFactory() {
    return tf;
  }

  /**
   * Returns the extension of treebank files for this treebank.
   * This is "tree".
   */
  public String treebankFileExtension() {
    return "tree";
  }

  @Override
  public TreeReaderFactory treeReaderFactory() {
    return new ArabicTreeReaderFactory();
  }

  @Override
  public String toString() {
    return "ArabicTreebankLanguagePack";
  }

  /** {@inheritDoc} */
  public HeadFinder headFinder() {
    return new ArabicHeadFinder(this);
  }

  /** {@inheritDoc} */
  public HeadFinder typedDependencyHeadFinder() {
    return new ArabicHeadFinder(this);
  }

  @Override
  public MorphoFeatureSpecification morphFeatureSpec() {
    return new ArabicMorphoFeatureSpecification();
  }


  /**
   *
   * @param args
   */
  public static void main(String[] args) {
    TreebankLanguagePack tlp = new PennTreebankLanguagePack();
    System.out.println("Start symbol: " + tlp.startSymbol());
    String start = tlp.startSymbol();
    System.out.println("Should be true: " + (tlp.isStartSymbol(start)));
    String[] strs = new String[]{"-", "-LLB-", "NP-2", "NP=3", "NP-LGS", "NP-TMP=3"};
    for (String str : strs) {
      System.out.println("String: " + str + " basic: " + tlp.basicCategory(str) + " basicAndFunc: " + tlp.categoryAndFunction(str));
    }
  }
}
