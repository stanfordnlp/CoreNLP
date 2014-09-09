package edu.stanford.nlp.trees;

import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.HasWord;

import java.io.Serializable;


/**
 * This interface specifies language/treebank specific information for a
 * Treebank, which a parser or other treebank user might need to know. <p>
 * <p/>
 * Some of this is fixed for a (treebank,language) pair, but some of it
 * reflects feature extraction decisions, so it can be sensible to have
 * multiple implementations of this interface for the same
 * (treebank,language) pair. <p>
 * <p/>
 * So far this covers punctuation, character encodings, and characters
 * reserved for label annotations.  It should probably be expanded to
 * cover other stuff (unknown words?). <p>
 * <p/>
 * Various methods in this class return arrays.  You should treat them
 * as read-only, even though one cannot enforce that in Java. <p>
 * <p/>
 * Implementations in this class do not call basicCategory() on arguments
 * before testing them, so if needed, you should explicitly call
 * basicCategory() yourself before passing arguments to these routines for
 * testing.
 * <p/>
 * This class should be able to be an immutable singleton.  It contains
 * data on various things, but no state.  At some point we should make it
 * a real immutable singleton.
 *
 * @author Christopher Manning
 * @version 1.1, Mar 2003
 */
public interface TreebankLanguagePack extends Serializable {

  /**
   * Use this as the default encoding for Readers and Writers of
   * Treebank data.
   */
  public static final String DEFAULT_ENCODING = "UTF-8";


  /**
   * Accepts a String that is a punctuation
   * tag name, and rejects everything else.
   *
   * @param str The string to check
   * @return Whether this is a punctuation tag
   */
  public boolean isPunctuationTag(String str);


  /**
   * Accepts a String that is a punctuation
   * word, and rejects everything else.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * maks the best guess that it can.
   *
   * @param str The string to check
   * @return Whether this is a punctuation word
   */
  public boolean isPunctuationWord(String str);


  /**
   * Accepts a String that is a sentence end
   * punctuation tag, and rejects everything else.
   *
   * @param str The string to check
   * @return Whether this is a sentence final punctuation tag
   */
  public boolean isSentenceFinalPunctuationTag(String str);


  /**
   * Accepts a String that is a punctuation
   * tag that should be ignored by EVALB-style evaluation,
   * and rejects everything else.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @param str The string to check
   * @return Whether this is a EVALB-ignored punctuation tag
   */
  public boolean isEvalBIgnoredPunctuationTag(String str);


  /**
   * Return a filter that accepts a String that is a punctuation
   * tag name, and rejects everything else.
   *
   * @return The filter
   */
  public Filter<String> punctuationTagAcceptFilter();


  /**
   * Return a filter that rejects a String that is a punctuation
   * tag name, and accepts everything else.
   *
   * @return The filter
   */
  public Filter<String> punctuationTagRejectFilter();

  /**
   * Returns a filter that accepts a String that is a punctuation
   * word, and rejects everything else.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * maks the best guess that it can.
   *
   * @return The Filter
   */
  public Filter<String> punctuationWordAcceptFilter();


  /**
   * Returns a filter that accepts a String that is not a punctuation
   * word, and rejects punctuation.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * makes the best guess that it can.
   *
   * @return The Filter
   */
  public Filter<String> punctuationWordRejectFilter();


  /**
   * Returns a filter that accepts a String that is a sentence end
   * punctuation tag, and rejects everything else.
   *
   * @return The Filter
   */
  public Filter<String> sentenceFinalPunctuationTagAcceptFilter();


  /**
   * Returns a filter that accepts a String that is a punctuation
   * tag that should be ignored by EVALB-style evaluation,
   * and rejects everything else.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @return The Filter
   */
  public Filter<String> evalBIgnoredPunctuationTagAcceptFilter();


  /**
   * Returns a filter that accepts everything except a String that is a
   * punctuation tag that should be ignored by EVALB-style evaluation.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @return The Filter
   */
  public Filter<String> evalBIgnoredPunctuationTagRejectFilter();


  /**
   * Returns a String array of punctuation tags for this treebank/language.
   *
   * @return The punctuation tags
   */
  public String[] punctuationTags();


  /**
   * Returns a String array of punctuation words for this treebank/language.
   *
   * @return The punctuation words
   */
  public String[] punctuationWords();


  /**
   * Returns a String array of sentence final punctuation tags for this
   * treebank/language.  The first in the list is assumed to be the most
   * basic one.
   *
   * @return The sentence final punctuation tags
   */
  public String[] sentenceFinalPunctuationTags();


  /**
   * Returns a String array of sentence final punctuation words for
   * this treebank/language.
   *
   * @return The punctuation words
   */
  public String[] sentenceFinalPunctuationWords();

  /**
   * Returns a String array of punctuation tags that EVALB-style evaluation
   * should ignore for this treebank/language.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @return Whether this is a EVALB-ignored punctuation tag
   */
  public String[] evalBIgnoredPunctuationTags();


  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   *
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  public GrammaticalStructureFactory grammaticalStructureFactory();


  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   *
   * @param puncFilter A filter which should reject punctuation words (as Strings)
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  public GrammaticalStructureFactory grammaticalStructureFactory(Filter<String> puncFilter);


  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   *
   * @param puncFilter A filter which should reject punctuation words (as Strings)
   * @param typedDependencyHF A HeadFinder which finds heads for typed dependencies
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  public GrammaticalStructureFactory grammaticalStructureFactory(Filter<String> puncFilter, HeadFinder typedDependencyHF);

  /**
   * Whether or not we have typed dependencies for this language.  If
   * this method returns false, a call to grammaticalStructureFactory
   * will cause an exception.
   */
  public boolean supportsGrammaticalStructures();

  /**
   * Return the charset encoding of the Treebank.  See
   * documentation for the <code>Charset</code> class.
   *
   * @return Name of Charset
   */
  public String getEncoding();


  /**
   * Return a tokenizer factory which might be suitable for tokenizing text
   * that will be used with this Treebank/Language pair.  This is for
   * real text of this language pair, not for reading stuff inside the
   * treebank files.
   *
   * @return A tokenizer
   */
  public TokenizerFactory<? extends HasWord> getTokenizerFactory();

  /**
   * Return an array of characters at which a String should be
   * truncated to give the basic syntactic category of a label.
   * The idea here is that Penn treebank style labels follow a syntactic
   * category with various functional and crossreferencing information
   * introduced by special characters (such as "NP-SBJ=1").  This would
   * be truncated to "NP" by the array containing '-' and "=". <br>
   * Note that these are never deleted as the first character as a label
   * (so they are okay as one character tags, etc.), but only when
   * subsequent characters.
   *
   * @return An array of characters that set off label name suffixes
   */
  public char[] labelAnnotationIntroducingCharacters();


  /**
   * Say whether this character is an annotation introducing
   * character.
   *
   * @param ch A char
   * @return Whether this char introduces functional annotations
   */
  public boolean isLabelAnnotationIntroducingCharacter(char ch);


  /**
   * Returns the basic syntactic category of a String by truncating
   * stuff after a (non-word-initial) occurrence of one of the
   * <code>labelAnnotationIntroducingCharacters()</code>.  This
   * function should work on phrasal category and POS tag labels,
   * but needn't (and couldn't be expected to) work on arbitrary
   * Word strings.
   *
   * @param category The whole String name of the label
   * @return The basic category of the String
   */
  public String basicCategory(String category);

  /**
   * Returns the category for a String with everything following
   * the gf character (which may be language specific) stripped.
   *
   * @param category The String name of the label (may previously have had basic category called on it)
   * @return The String stripped of grammatical functions
   */
  public String stripGF(String category);


  /**
   * Returns a {@link Function Function} object that maps Strings to Strings according
   * to this TreebankLanguagePack's basicCategory method.
   *
   * @return the String->String Function object
   */
  public Function<String,String> getBasicCategoryFunction();

  /**
   * Returns the syntactic category and 'function' of a String.
   * This normally involves truncating numerical coindexation
   * showing coreference, etc.  By 'function', this means
   * keeping, say, Penn Treebank functional tags or ICE phrasal functions,
   * perhaps returning them as <code>category-function</code>.
   *
   * @param category The whole String name of the label
   * @return A String giving the category and function
   */
  public String categoryAndFunction(String category);

  /**
   * Returns a {@link Function Function} object that maps Strings to Strings according
   * to this TreebankLanguagePack's categoryAndFunction method.
   *
   * @return the String->String Function object
   */
  public Function<String,String> getCategoryAndFunctionFunction();

  /**
   * Accepts a String that is a start symbol of the treebank.
   *
   * @param str The str to test
   * @return Whether this is a start symbol
   */
  public boolean isStartSymbol(String str);


  /**
   * Return a filter that accepts a String that is a start symbol
   * of the treebank, and rejects everything else.
   *
   * @return The filter
   */
  public Filter<String> startSymbolAcceptFilter();


  /**
   * Returns a String array of treebank start symbols.
   *
   * @return The start symbols
   */
  public String[] startSymbols();

  /**
   * Returns a String which is the first (perhaps unique) start symbol
   * of the treebank, or null if none is defined.
   *
   * @return The start symbol
   */
  public String startSymbol();


  /**
   * Returns the extension of treebank files for this treebank.
   * This should be passed as an argument to Treebank loading classes.
   * It might be "mrg" or "fid" or whatever.  Don't include the period.
   *
   * @return the extension on files for this treebank
   */
  public String treebankFileExtension();

  /**
   * Sets the grammatical function indicating character to gfCharacter.
   *
   * @param gfCharacter Sets the character in label names that sets of
   *         grammatical function marking (from the phrase label).
   */
  public void setGfCharacter(char gfCharacter);

  /** Returns a TreeReaderFactory suitable for general purpose use
   *  with this language/treebank.
   *
   *  @return A TreeReaderFactory suitable for general purpose use
   *  with this language/treebank.
   */
  public TreeReaderFactory treeReaderFactory();

  /** Return a TokenizerFactory for Trees of this language/treebank.
   *
   * @return A TokenizerFactory for Trees of this language/treebank.
   */
  public TokenizerFactory<Tree> treeTokenizerFactory();

  /**
   * The HeadFinder to use for your treebank.
   *
   * @return A suitable HeadFinder
   */
  public abstract HeadFinder headFinder();


  /**
   * The HeadFinder to use when making typed dependencies.
   *
   * @return A suitable HeadFinder
   */
  public abstract HeadFinder typedDependencyHeadFinder();


  /**
   * The morphological feature specification for the language.
   *
   * @return A language-specific MorphoFeatureSpecification
   */
  public abstract MorphoFeatureSpecification morphFeatureSpec();
}
