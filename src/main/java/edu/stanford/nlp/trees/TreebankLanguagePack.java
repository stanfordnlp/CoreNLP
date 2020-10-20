package edu.stanford.nlp.trees;

import edu.stanford.nlp.process.TokenizerFactory;
import java.util.function.Function;
import java.util.function.Predicate;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.HasWord;

import java.io.Serializable;


/**
 * This interface specifies language/treebank specific information for a
 * Treebank, which a parser or other treebank user might need to know.
 *
 * Some of this is fixed for a (treebank,language) pair, but some of it
 * reflects feature extraction decisions, so it can be sensible to have
 * multiple implementations of this interface for the same
 * (treebank,language) pair.
 *
 * So far this covers punctuation, character encodings, and characters
 * reserved for label annotations.  It should probably be expanded to
 * cover other stuff (unknown words?).
 *
 * Various methods in this class return arrays.  You should treat them
 * as read-only, even though one cannot enforce that in Java.
 *
 * Implementations in this class do not call basicCategory() on arguments
 * before testing them, so if needed, you should explicitly call
 * basicCategory() yourself before passing arguments to these routines for
 * testing.
 *
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
  String DEFAULT_ENCODING = "UTF-8";


  /**
   * Accepts a String that is a punctuation
   * tag name, and rejects everything else.
   *
   * @param str The string to check
   * @return Whether this is a punctuation tag
   */
  boolean isPunctuationTag(String str);


  /**
   * Accepts a String that is a punctuation
   * word, and rejects everything else.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * maks the best guess that it can.
   *
   * @param str The string to check
   * @return Whether this is a punctuation word
   */
  boolean isPunctuationWord(String str);


  /**
   * Accepts a String that is a sentence end
   * punctuation tag, and rejects everything else.
   *
   * @param str The string to check
   * @return Whether this is a sentence final punctuation tag
   */
  boolean isSentenceFinalPunctuationTag(String str);


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
  boolean isEvalBIgnoredPunctuationTag(String str);


  /**
   * Return a filter that accepts a String that is a punctuation
   * tag name, and rejects everything else.
   *
   * @return The filter
   */
  Predicate<String> punctuationTagAcceptFilter();


  /**
   * Return a filter that rejects a String that is a punctuation
   * tag name, and accepts everything else.
   *
   * @return The filter
   */
  Predicate<String> punctuationTagRejectFilter();

  /**
   * Returns a filter that accepts a String that is a punctuation
   * word, and rejects everything else.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * maks the best guess that it can.
   *
   * @return The Filter
   */
  Predicate<String> punctuationWordAcceptFilter();


  /**
   * Returns a filter that accepts a String that is not a punctuation
   * word, and rejects punctuation.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * makes the best guess that it can.
   *
   * @return The Filter
   */
  Predicate<String> punctuationWordRejectFilter();


  /**
   * Returns a filter that accepts a String that is a sentence end
   * punctuation tag, and rejects everything else.
   *
   * @return The Filter
   */
  Predicate<String> sentenceFinalPunctuationTagAcceptFilter();


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
  Predicate<String> evalBIgnoredPunctuationTagAcceptFilter();


  /**
   * Returns a filter that accepts everything except a String that is a
   * punctuation tag that should be ignored by EVALB-style evaluation.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @return The Filter
   */
  Predicate<String> evalBIgnoredPunctuationTagRejectFilter();


  /**
   * Returns a String array of punctuation tags for this treebank/language.
   *
   * @return The punctuation tags
   */
  String[] punctuationTags();


  /**
   * Returns a String array of punctuation words for this treebank/language.
   *
   * @return The punctuation words
   */
  String[] punctuationWords();


  /**
   * Returns a String array of sentence final punctuation tags for this
   * treebank/language.  The first in the list is assumed to be the most
   * basic one.
   *
   * @return The sentence final punctuation tags
   */
  String[] sentenceFinalPunctuationTags();


  /**
   * Returns a String array of sentence final punctuation words for
   * this treebank/language.
   *
   * @return The punctuation words
   */
  String[] sentenceFinalPunctuationWords();

  /**
   * Returns a String array of punctuation tags that EVALB-style evaluation
   * should ignore for this treebank/language.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @return Whether this is a EVALB-ignored punctuation tag
   */
  String[] evalBIgnoredPunctuationTags();


  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   *
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  GrammaticalStructureFactory grammaticalStructureFactory();


  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   *
   * @param puncFilter A filter which should reject punctuation words (as Strings)
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  GrammaticalStructureFactory grammaticalStructureFactory(Predicate<String> puncFilter);


  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   *
   * @param puncFilter A filter which should reject punctuation words (as Strings)
   * @param typedDependencyHF A HeadFinder which finds heads for typed dependencies
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  GrammaticalStructureFactory grammaticalStructureFactory(Predicate<String> puncFilter, HeadFinder typedDependencyHF);

  /**
   * Whether or not we have typed dependencies for this language.  If
   * this method returns false, a call to grammaticalStructureFactory
   * will cause an exception.
   */
  boolean supportsGrammaticalStructures();

  /**
   * Return the charset encoding of the Treebank.  See
   * documentation for the {@code Charset} class.
   *
   * @return Name of Charset
   */
  String getEncoding();


  /**
   * Return a tokenizer factory which might be suitable for tokenizing text
   * that will be used with this Treebank/Language pair.  This is for
   * real text of this language pair, not for reading stuff inside the
   * treebank files.
   *
   * @return A tokenizer
   */
  TokenizerFactory<? extends HasWord> getTokenizerFactory();

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
  char[] labelAnnotationIntroducingCharacters();


  /**
   * Say whether this character is an annotation introducing
   * character.
   *
   * @param ch A char
   * @return Whether this char introduces functional annotations
   */
  boolean isLabelAnnotationIntroducingCharacter(char ch);


  /**
   * Returns the basic syntactic category of a String by truncating
   * stuff after a (non-word-initial) occurrence of one of the
   * {@code labelAnnotationIntroducingCharacters()}.  This
   * function should work on phrasal category and POS tag labels,
   * but needn't (and couldn't be expected to) work on arbitrary
   * Word strings.
   *
   * @param category The whole String name of the label
   * @return The basic category of the String
   */
  String basicCategory(String category);

  /**
   * Returns the category for a String with everything following
   * the gf character (which may be language specific) stripped.
   *
   * @param category The String name of the label (may previously have had basic category called on it)
   * @return The String stripped of grammatical functions
   */
  String stripGF(String category);


  /**
   * Returns a {@link Function Function} object that maps Strings to Strings according
   * to this TreebankLanguagePack's basicCategory method.
   *
   * @return the String-&gt;String Function object
   */
  Function<String,String> getBasicCategoryFunction();

  /**
   * Returns the syntactic category and 'function' of a String.
   * This normally involves truncating numerical coindexation
   * showing coreference, etc.  By 'function', this means
   * keeping, say, Penn Treebank functional tags or ICE phrasal functions,
   * perhaps returning them as {@code category-function}.
   *
   * @param category The whole String name of the label
   * @return A String giving the category and function
   */
  String categoryAndFunction(String category);

  /**
   * Returns a {@link Function Function} object that maps Strings to Strings according
   * to this TreebankLanguagePack's categoryAndFunction method.
   *
   * @return the String-&gt;String Function object
   */
  Function<String,String> getCategoryAndFunctionFunction();

  /**
   * Accepts a String that is a start symbol of the treebank.
   *
   * @param str The str to test
   * @return Whether this is a start symbol
   */
  boolean isStartSymbol(String str);


  /**
   * Return a filter that accepts a String that is a start symbol
   * of the treebank, and rejects everything else.
   *
   * @return The filter
   */
  Predicate<String> startSymbolAcceptFilter();


  /**
   * Returns a String array of treebank start symbols.
   *
   * @return The start symbols
   */
  String[] startSymbols();

  /**
   * Returns a String which is the first (perhaps unique) start symbol
   * of the treebank, or null if none is defined.
   *
   * @return The start symbol
   */
  String startSymbol();


  /**
   * Returns the extension of treebank files for this treebank.
   * This should be passed as an argument to Treebank loading classes.
   * It might be "mrg" or "fid" or whatever.  Don't include the period.
   *
   * @return the extension on files for this treebank
   */
  String treebankFileExtension();

  /**
   * Sets the grammatical function indicating character to gfCharacter.
   *
   * @param gfCharacter Sets the character in label names that sets of
   *         grammatical function marking (from the phrase label).
   */
  void setGfCharacter(char gfCharacter);

  /** Returns a TreeReaderFactory suitable for general purpose use
   *  with this language/treebank.
   *
   *  @return A TreeReaderFactory suitable for general purpose use
   *  with this language/treebank.
   */
  TreeReaderFactory treeReaderFactory();

  /** Return a TokenizerFactory for Trees of this language/treebank.
   *
   * @return A TokenizerFactory for Trees of this language/treebank.
   */
  TokenizerFactory<Tree> treeTokenizerFactory();

  /**
   * The HeadFinder to use for your treebank.
   *
   * @return A suitable HeadFinder
   */
  HeadFinder headFinder();


  /**
   * The HeadFinder to use when making typed dependencies.
   *
   * @return A suitable HeadFinder
   */
  HeadFinder typedDependencyHeadFinder();


  /**
   * The morphological feature specification for the language.
   *
   * @return A language-specific MorphoFeatureSpecification
   */
  MorphoFeatureSpecification morphFeatureSpec();

  /**
   * Used for languages where an original Stanford Dependency
   * converter and a Universal Dependency converter exists.
   */
  void setGenerateOriginalDependencies(boolean generateOriginalDependencies);

  /**
   * Used for languages where an original Stanford Dependency
   * converter and a Universal Dependency converter exists.
   */
  boolean generateOriginalDependencies();

}
