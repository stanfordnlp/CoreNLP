package edu.stanford.nlp.trees;

import java.io.Serializable;

import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.WhitespaceTokenizer;
import java.util.function.Predicate;
import edu.stanford.nlp.util.Filters;

import java.util.function.Function;


/**
 * This provides an implementation of parts of the TreebankLanguagePack
 * API to reduce the load on fresh implementations.  Only the abstract
 * methods below need to be implemented to give a reasonable solution for
 * a new language.
 *
 * @author Christopher Manning
 * @version 1.1
 */
public abstract class AbstractTreebankLanguagePack implements TreebankLanguagePack {

  /**
   * So changed versions deserialize correctly.
   */
  private static final long serialVersionUID = -6506749780512708352L;


  //Grammatical function parameters
  /**
   * Default character for indicating that something is a grammatical fn; probably should be overridden by
   * lang specific ones
   */
  protected char gfCharacter;
  protected static final char DEFAULT_GF_CHAR = '-';


  /**
   * Use this as the default encoding for Readers and Writers of
   * Treebank data.
   */
  public static final String DEFAULT_ENCODING = "UTF-8";


  /**
   * For languages where a Universal Dependency converter
   * exists this variable determines whether the original
   * or the Universal converter will be used.
   */
  protected boolean generateOriginalDependencies;


  /**
   * Gives a handle to the TreebankLanguagePack.
   */
  public AbstractTreebankLanguagePack() {
    this(DEFAULT_GF_CHAR);
  }


  /**
   * Gives a handle to the TreebankLanguagePack.
   *
   * @param gfChar The character that sets of grammatical functions in node labels.
   */
  public AbstractTreebankLanguagePack(char gfChar) {
    this.gfCharacter = gfChar;
  }

  /**
   * Returns a String array of punctuation tags for this treebank/language.
   *
   * @return The punctuation tags
   */
  @Override
  public abstract String[] punctuationTags();

  /**
   * Returns a String array of punctuation words for this treebank/language.
   *
   * @return The punctuation words
   */
  @Override
  public abstract String[] punctuationWords();

  /**
   * Returns a String array of sentence final punctuation tags for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public abstract String[] sentenceFinalPunctuationTags();

  /**
   * Returns a String array of sentence final punctuation words for
   * this treebank/language.
   *
   * @return The punctuation words
   */
  @Override
  public abstract String[] sentenceFinalPunctuationWords();

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
    return punctuationTags();
  }


  /**
   * Accepts a String that is a punctuation
   * tag name, and rejects everything else.
   *
   * @return Whether this is a punctuation tag
   */
  @Override
  public boolean isPunctuationTag(String str) {
    return punctTagStringAcceptFilter.test(str);
  }


  /**
   * Accepts a String that is a punctuation
   * word, and rejects everything else.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * maks the best guess that it can.
   *
   * @return Whether this is a punctuation word
   */
  @Override
  public boolean isPunctuationWord(String str) {
    return punctWordStringAcceptFilter.test(str);
  }


  /**
   * Accepts a String that is a sentence end
   * punctuation tag, and rejects everything else.
   *
   * @return Whether this is a sentence final punctuation tag
   */
  @Override
  public boolean isSentenceFinalPunctuationTag(String str) {
    return sFPunctTagStringAcceptFilter.test(str);
  }


  /**
   * Accepts a String that is a punctuation
   * tag that should be ignored by EVALB-style evaluation,
   * and rejects everything else.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @return Whether this is a EVALB-ignored punctuation tag
   */
  @Override
  public boolean isEvalBIgnoredPunctuationTag(String str) {
    return eIPunctTagStringAcceptFilter.test(str);
  }


  /**
   * Return a filter that accepts a String that is a punctuation
   * tag name, and rejects everything else.
   *
   * @return The filter
   */
  @Override
  public Predicate<String> punctuationTagAcceptFilter() {
    return punctTagStringAcceptFilter;
  }


  /**
   * Return a filter that rejects a String that is a punctuation
   * tag name, and rejects everything else.
   *
   * @return The filter
   */
  @Override
  public Predicate<String> punctuationTagRejectFilter() {
    return Filters.notFilter(punctTagStringAcceptFilter);
  }


  /**
   * Returns a filter that accepts a String that is a punctuation
   * word, and rejects everything else.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * makes the best guess that it can.
   *
   * @return The Filter
   */
  @Override
  public Predicate<String> punctuationWordAcceptFilter() {
    return punctWordStringAcceptFilter;
  }


  /**
   * Returns a filter that accepts a String that is not a punctuation
   * word, and rejects punctuation.
   * If one can't tell for sure (as for ' in the Penn Treebank), it
   * makes the best guess that it can.
   *
   * @return The Filter
   */
  @Override
  public Predicate<String> punctuationWordRejectFilter() {
    return Filters.notFilter(punctWordStringAcceptFilter);
  }


  /**
   * Returns a filter that accepts a String that is a sentence end
   * punctuation tag, and rejects everything else.
   *
   * @return The Filter
   */
  @Override
  public Predicate<String> sentenceFinalPunctuationTagAcceptFilter() {
    return sFPunctTagStringAcceptFilter;
  }


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
  @Override
  public Predicate<String> evalBIgnoredPunctuationTagAcceptFilter() {
    return eIPunctTagStringAcceptFilter;
  }


  /**
   * Returns a filter that accepts everything except a String that is a
   * punctuation tag that should be ignored by EVALB-style evaluation.
   * Traditionally, EVALB has ignored a subset of the total set of
   * punctuation tags in the English Penn Treebank (quotes and
   * period, comma, colon, etc., but not brackets)
   *
   * @return The Filter
   */
  @Override
  public Predicate<String> evalBIgnoredPunctuationTagRejectFilter() {
    return Filters.notFilter(eIPunctTagStringAcceptFilter);
  }


  /**
   * Return the input Charset encoding for the Treebank.
   * See documentation for the {@code Charset} class.
   *
   * @return Name of Charset
   */
  @Override
  public String getEncoding() {
    return DEFAULT_ENCODING;
  }


  private static final char[] EMPTY_CHAR_ARRAY = new char[0];

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
    return EMPTY_CHAR_ARRAY;
  }


  /**
   * Returns the index of the first character that is after the basic
   * label.  That is, if category is "NP-LGS", it returns 2.
   * This routine assumes category != null.
   * This routine returns 0 iff the String is of length 0.
   * This routine always returns a number &lt;= category.length(), and
   * so it is safe to pass it as an argument to category.substring().
   * <p>
   * NOTE: the routine should never allow the first character of a label
   * to be taken as the annotation introducing character, because in the
   * Penn Treebank, "-" is a valid tag, but also the character used to
   * set off functional and co-indexing annotations. If the first letter is
   * such a character then a matched character is also not used, for
   * -LRB- etc., iff there is an intervening character (so --PU becomes -).
   *
   * @param category Phrasal category
   * @return The index of the first character that is after the basic
   *     label
   */
  private int postBasicCategoryIndex(String category) {
    boolean sawAtZero = false;
    char seenAtZero = '\u0000';
    int i = 0;
    for (int leng = category.length(); i < leng; i++) {
      char ch = category.charAt(i);
      if (isLabelAnnotationIntroducingCharacter(ch)) {
        if (i == 0) {
          sawAtZero = true;
          seenAtZero = ch;
        } else if (sawAtZero && i > 1 && ch == seenAtZero) {
          sawAtZero = false;
        } else {
          // still skip past identical ones for weird negra-penn "---CJ" (should we just delete it?)
          // if (i + 1 < leng && category.charAt(i + 1) == ch) {
            // keep looping
          // } else {
          break;
          // }
        }
      }
    }
    return i;
  }

  /**
   * Returns the basic syntactic category of a String.
   * This implementation basically truncates
   * stuff after an occurrence of one of the
   * {@code labelAnnotationIntroducingCharacters()}.
   * However, there is also special case stuff to deal with
   * labelAnnotationIntroducingCharacters in category labels:
   * (i) if the first char is in this set, it's never truncated
   * (e.g., '-' or '=' as a token), and (ii) if it starts with
   * one of this set, a second instance of the same item from this set is
   * also excluded (to deal with '-LLB-', '-RCB-', etc.).
   *
   * @param category The whole String name of the label
   * @return The basic category of the String
   */
  @Override
  public String basicCategory(String category) {
    if (category == null) {
      return null;
    }
    return category.substring(0, postBasicCategoryIndex(category));
  }


  @Override
  public String stripGF(String category) {
    if(category == null) {
      return null;
    }
    int index = category.lastIndexOf(gfCharacter);
    if(index > 0) {
      category = category.substring(0, index);
    }
    return category;
  }

  /**
   * Returns a {@link Function Function} object that maps Strings to Strings according
   * to this TreebankLanguagePack's basicCategory() method.
   *
   * @return The String-&gt;String Function object
   */
  @Override
  public Function<String,String> getBasicCategoryFunction() {
    return new BasicCategoryStringFunction(this);
  }


  private static class BasicCategoryStringFunction implements Function<String,String>, Serializable {

    private static final long serialVersionUID = 1L;

    private TreebankLanguagePack tlp;

    BasicCategoryStringFunction(TreebankLanguagePack tlp) {
      this.tlp = tlp;
    }

    @Override
    public String apply(String in) {
      return tlp.basicCategory(in);
    }

  }


  private static class CategoryAndFunctionStringFunction implements Function<String,String>, Serializable {

    private static final long serialVersionUID = 1L;

    private TreebankLanguagePack tlp;

    CategoryAndFunctionStringFunction(TreebankLanguagePack tlp) {
      this.tlp = tlp;
    }

    @Override
    public String apply(String in) {
      return tlp.categoryAndFunction(in);
    }

  }


  /**
   * Returns the syntactic category and 'function' of a String.
   * This normally involves truncating numerical coindexation
   * showing coreference, etc.  By 'function', this means
   * keeping, say, Penn Treebank functional tags or ICE phrasal functions,
   * perhaps returning them as {@code category-function}.
   * <br>
   * This implementation strips numeric tags after label introducing
   * characters (assuming that non-numeric things are functional tags).
   *
   * @param category The whole String name of the label
   * @return A String giving the category and function
   */
  @Override
  public String categoryAndFunction(String category) {
    if (category == null) {
      return null;
    }
    String catFunc = category;
    int i = lastIndexOfNumericTag(catFunc);
    while (i >= 0) {
      catFunc = catFunc.substring(0, i);
      i = lastIndexOfNumericTag(catFunc);
    }
    return catFunc;
  }

  /**
   * Returns the index within this string of the last occurrence of a
   * isLabelAnnotationIntroducingCharacter which is followed by only
   * digits, corresponding to a numeric tag at the end of the string.
   * Example: {@code lastIndexOfNumericTag("NP-TMP-1") returns
   * 6}.
   *
   * @param category A String category
   * @return The index within this string of the last occurrence of a
   *     isLabelAnnotationIntroducingCharacter which is followed by only
   *     digits
   */
  private int lastIndexOfNumericTag(String category) {
    if (category == null) {
      return -1;
    }
    int last = -1;
    for (int i = category.length() - 1; i >= 0; i--) {
      if (isLabelAnnotationIntroducingCharacter(category.charAt(i))) {
        boolean onlyDigitsFollow = false;
        for (int j = i + 1; j < category.length(); j++) {
          onlyDigitsFollow = true;
          if (!(Character.isDigit(category.charAt(j)))) {
            onlyDigitsFollow = false;
            break;
          }
        }
        if (onlyDigitsFollow) {
          last = i;
        }
      }
    }
    return last;
  }

  /**
   * Returns a {@link Function Function} object that maps Strings to Strings according
   * to this TreebankLanguagePack's categoryAndFunction() method.
   *
   * @return The String-&gt;String Function object
   */
  @Override
  public Function<String,String> getCategoryAndFunctionFunction() {
    return new CategoryAndFunctionStringFunction(this);
  }


  /**
   * Say whether this character is an annotation introducing
   * character.
   *
   * @param ch The character to check
   * @return Whether it is an annotation introducing character
   */
  @Override
  public boolean isLabelAnnotationIntroducingCharacter(char ch) {
    char[] cutChars = labelAnnotationIntroducingCharacters();
    for (char cutChar : cutChars) {
      if (ch == cutChar) {
        return true;
      }
    }
    return false;
  }


  /**
   * Accepts a String that is a start symbol of the treebank.
   *
   * @return Whether this is a start symbol
   */
  @Override
  public boolean isStartSymbol(String str) {
    return startSymbolAcceptFilter.test(str);
  }


  /**
   * Return a filter that accepts a String that is a start symbol
   * of the treebank, and rejects everything else.
   *
   * @return The filter
   */
  @Override
  public Predicate<String> startSymbolAcceptFilter() {
    return startSymbolAcceptFilter;
  }


  /**
   * Returns a String array of treebank start symbols.
   *
   * @return The start symbols
   */
  @Override
  public abstract String[] startSymbols();


  /**
   * Returns a String which is the first (perhaps unique) start symbol
   * of the treebank, or null if none is defined.
   *
   * @return The start symbol
   */
  @Override
  public String startSymbol() {
    String[] ssyms = startSymbols();
    if (ssyms == null || ssyms.length == 0) {
      return null;
    }
    return ssyms[0];
  }

  /** {@inheritDoc} */
  @Override
  public abstract String treebankFileExtension();


  private final Predicate<String> punctTagStringAcceptFilter = Filters.collectionAcceptFilter(punctuationTags());

  private final Predicate<String> punctWordStringAcceptFilter = Filters.collectionAcceptFilter(punctuationWords());

  private final Predicate<String> sFPunctTagStringAcceptFilter = Filters.collectionAcceptFilter(sentenceFinalPunctuationTags());

  private final Predicate<String> eIPunctTagStringAcceptFilter = Filters.collectionAcceptFilter(evalBIgnoredPunctuationTags());

  private final Predicate<String> startSymbolAcceptFilter = Filters.collectionAcceptFilter(startSymbols());

  /**
   * Return a tokenizer which might be suitable for tokenizing text that
   * will be used with this Treebank/Language pair, without tokenizing carriage returns (i.e., treating them as white space).  The implementation in AbstractTreebankLanguagePack
   * returns a factory for {@link WhitespaceTokenizer}.
   *
   * @return A tokenizer
   */
  @Override
  public TokenizerFactory<? extends HasWord> getTokenizerFactory() {
    return WhitespaceTokenizer.factory(false);
  }

  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   * (To be overridden in subclasses.)
   *
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  @Override
  public GrammaticalStructureFactory grammaticalStructureFactory() {
    throw new UnsupportedOperationException(
            "No GrammaticalStructureFactory (typed dependencies) available for language/treebank " +
                    getClass().getName());
  }

  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   * (To be overridden in subclasses.)
   *
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  @Override
  public GrammaticalStructureFactory grammaticalStructureFactory(Predicate<String> puncFilt) {
    return grammaticalStructureFactory();
  }

  /**
   * Return a GrammaticalStructureFactory suitable for this language/treebank.
   * (To be overridden in subclasses.)
   *
   * @return A GrammaticalStructureFactory suitable for this language/treebank
   */
  @Override
  public GrammaticalStructureFactory grammaticalStructureFactory(Predicate<String> puncFilt, HeadFinder typedDependencyHeadFinder) {
    return grammaticalStructureFactory();
  }

  @Override
  public boolean supportsGrammaticalStructures() {
    return false;
  }

  public char getGfCharacter() {
    return gfCharacter;
  }


  @Override
  public void setGfCharacter(char gfCharacter) {
    this.gfCharacter = gfCharacter;
  }

  /** {@inheritDoc} */
  @Override
  public TreeReaderFactory treeReaderFactory() {
    return new PennTreeReaderFactory();
  }

  /** {@inheritDoc} */
  @Override
  public TokenizerFactory<Tree> treeTokenizerFactory() {
    return new TreeTokenizerFactory(treeReaderFactory());
  }

  /** {@inheritDoc} */
  @Override
  public abstract HeadFinder headFinder();


  /** {@inheritDoc} */
  @Override
  public abstract HeadFinder typedDependencyHeadFinder();

  /**
   * Returns a morphological feature specification for words in this language.
   */
  @Override
  public MorphoFeatureSpecification morphFeatureSpec() {
    return null;
  }

  @Override
  public void setGenerateOriginalDependencies(boolean generateOriginalDependencies) {
    this.generateOriginalDependencies = generateOriginalDependencies;
  }

  @Override
  public boolean generateOriginalDependencies() {
    return this.generateOriginalDependencies;
  }

}
