package edu.stanford.nlp.trees.international.pennchinese;

import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.Filter;
import edu.stanford.nlp.util.Filters;
import edu.stanford.nlp.ling.HasWord;


/**
 * Language pack for the UPenn/Colorado Chinese treebank.
 * The native character set for the Chinese Treebank is GB18030.
 * This file (like the rest of JavaNLP) is in UTF-8.
 *
 * @author Roger Levy
 */

public class ChineseTreebankLanguagePack extends AbstractTreebankLanguagePack {

  private static final long serialVersionUID = 5757403475523638802L;

  private TokenizerFactory<? extends HasWord> tf;

  public void setTokenizerFactory(TokenizerFactory<? extends HasWord> tf) {
    this.tf = tf;
  }

  @Override
  public TokenizerFactory<? extends HasWord> getTokenizerFactory() {
    if (tf != null) {
      return tf;
    } else {
      return super.getTokenizerFactory();
    }
  }

  public static final String ENCODING = "utf-8";

  /**
   * Return the input Charset encoding for the Treebank.
   * See documentation for the <code>Charset</code> class.
   *
   * @return Name of Charset
   */
  @Override
  public String getEncoding() {
    return ENCODING;
  }

  /**
   * Accepts a String that is a punctuation
   * tag name, and rejects everything else.
   *
   * @return Whether this is a punctuation tag
   */
  @Override
  public boolean isPunctuationTag(String str) {
    return str.equals("PU");
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
    return chineseCommaAcceptFilter().accept(str) || chineseEndSentenceAcceptFilter().accept(str) || chineseDouHaoAcceptFilter().accept(str) || chineseQuoteMarkAcceptFilter().accept(str) || chineseParenthesisAcceptFilter().accept(str) || chineseColonAcceptFilter().accept(str) || chineseDashAcceptFilter().accept(str) || chineseOtherAcceptFilter().accept(str);

  }


  /**
   * Accepts a String that is a sentence end
   * punctuation tag, and rejects everything else.
   *
   * @return Whether this is a sentence final punctuation tag
   */
  @Override
  public boolean isSentenceFinalPunctuationTag(String str) {
    return chineseEndSentenceAcceptFilter().accept(str);
  }


  /**
   * Returns a String array of punctuation tags for this treebank/language.
   *
   * @return The punctuation tags
   */
  @Override
  public String[] punctuationTags() {
    return tags;
  }


  /**
   * Returns a String array of punctuation words for this treebank/language.
   *
   * @return The punctuation words
   */
  @Override
  public String[] punctuationWords() {
    return punctWords;
  }


  /**
   * Returns a String array of sentence final punctuation tags for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public String[] sentenceFinalPunctuationTags() {
    return tags;
  }

  /**
   * Returns a String array of sentence final punctuation words for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public String[] sentenceFinalPunctuationWords() {
    return endSentence;
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
    return Filters.collectionAcceptFilter(tags).accept(str);
  }


  /**
   * The first 3 are used by the Penn Treebank; # is used by the
   * BLLIP corpus, and ^ and ~ are used by Klein's
   * lexparser. Identical to PennTreebankLanguagePack.
   */
  private static final char[] annotationIntroducingChars = {'-', '=', '|', '#', '^', '~'};


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
   * This is valid for "BobChrisTreeNormalizer" conventions
   * only. Again, identical to PennTreebankLanguagePack.
   */
  private static final String[] startSymbols = {"ROOT"};

  /**
   * Returns a String array of treebank start symbols.
   *
   * @return The start symbols
   */
  @Override
  public String[] startSymbols() {
    return startSymbols;
  }


  private static final String[] tags = {"PU"};
  private static final String[] comma = {",", "，", "　"};  // 　last is an "ideographic space"...?
  private static final String[] endSentence = {"。", "．", "！", "？", "?", "!", "."};
  private static final String[] douHao = {"、"};
  private static final String[] quoteMark = {"“", "”", "‘", "’", "《", "》", "『", "』", "〈", "〉",
          "「", "」", "＂", "＜", "＞", "'", "`", "＇", "｀", "｢", "｣"};
  private static final String[] parenthesis = {"（", "）", "［", "］", "｛", "｝", "-LRB-", "-RRB-", "【", "】",
          "〔", "〖", "〘", "〚", "｟", "〕", "〗", "〙", "〛", "｠" };  // ( and ) still must be escaped
  private static final String[] colon = {"：", "；", "∶", ":"};
  private static final String[] dash = {"…", "―", "——", "———", "————", "—", "——", "———", 
          "－", "--", "---", "－－", "－－－", "－－－－", "－－－－－", "－－－－－－", 
          "──", "━", "━━", "—－", "-", "----", "~", "~~", "~~~", "~~~~", "~~~~~", "……", "～",
          "．．．" /* 3 full width dots as ellipsis */ };
  private static final String[] other = {"·", "／", "／", "＊", "＆", "/", "//", "*", "※", "■", "●", "｜" };  // slashes are used in urls

  // Note that these next four should contain only things in quoteMark and parenthesis.  All such things are there but straight quotes
  private static final String[] leftQuoteMark = {"“", "‘", "《", "『", "〈", "「", "＜", "`", "｀", "｢"};
  private static final String[] rightQuoteMark = {"”", "’", "》", "』", "〉", "」", "＞", "＇", "｣"};
  private static final String[] leftParenthesis = {"（", "-LRB-", "［", "｛", "【", "〔", "〖", "〘", "〚", "｟"};
  private static final String[] rightParenthesis = {"）", "-RRB-", "］", "｝", "】", "〕", "〗", "〙", "〛", "｠"};
// "〔", "〖", "〘", "〚", "｟", "〕", "〗", "〙", "〛", "｠"

  private static final String[] punctWords;

  static {
    final int n = comma.length + endSentence.length + douHao.length + quoteMark.length + parenthesis.length + colon.length + dash.length +
            other.length + leftQuoteMark.length + rightQuoteMark.length + leftParenthesis.length + rightParenthesis.length;
    punctWords = new String[n];
    int m = 0;
    System.arraycopy(comma, 0, punctWords, m, comma.length);
    m += comma.length;
    System.arraycopy(endSentence, 0, punctWords, m, endSentence.length);
    m += endSentence.length;
    System.arraycopy(douHao, 0, punctWords, m, douHao.length);
    m += douHao.length;
    System.arraycopy(quoteMark, 0, punctWords, m, quoteMark.length);
    m += quoteMark.length;
    System.arraycopy(parenthesis, 0, punctWords, m, parenthesis.length);
    m += parenthesis.length;
    System.arraycopy(colon, 0, punctWords, m, colon.length);
    m += colon.length;
    System.arraycopy(dash, 0, punctWords, m, dash.length);
    m += dash.length;
    System.arraycopy(other, 0, punctWords, m, other.length);
    m += other.length;
  }

  public static Filter<String> chineseCommaAcceptFilter() {
    return Filters.collectionAcceptFilter(comma);
  }

  public static Filter<String> chineseEndSentenceAcceptFilter() {
    return Filters.collectionAcceptFilter(endSentence);
  }

  public static Filter<String> chineseDouHaoAcceptFilter() {
    return Filters.collectionAcceptFilter(douHao);
  }

  public static Filter<String> chineseQuoteMarkAcceptFilter() {
    return Filters.collectionAcceptFilter(quoteMark);
  }

  public static Filter<String> chineseParenthesisAcceptFilter() {
    return Filters.collectionAcceptFilter(parenthesis);
  }

  public static Filter<String> chineseColonAcceptFilter() {
    return Filters.collectionAcceptFilter(colon);
  }

  public static Filter<String> chineseDashAcceptFilter() {
    return Filters.collectionAcceptFilter(dash);
  }

  public static Filter<String> chineseOtherAcceptFilter() {
    return Filters.collectionAcceptFilter(other);
  }


  public static Filter<String> chineseLeftParenthesisAcceptFilter() {
    return Filters.collectionAcceptFilter(leftParenthesis);
  }

  public static Filter<String> chineseRightParenthesisAcceptFilter() {
    return Filters.collectionAcceptFilter(rightParenthesis);
  }

  public static Filter<String> chineseLeftQuoteMarkAcceptFilter() {
    return Filters.collectionAcceptFilter(leftQuoteMark);
  }

  public static Filter<String> chineseRightQuoteMarkAcceptFilter() {
    return Filters.collectionAcceptFilter(rightQuoteMark);
  }

  /**
   * Returns the extension of treebank files for this treebank.
   * This is "fid".
   */
  @Override
  public String treebankFileExtension() {
    return "fid";
  }

  @Override
  public GrammaticalStructureFactory grammaticalStructureFactory() {
    return new ChineseGrammaticalStructureFactory();
  }

  @Override
  public GrammaticalStructureFactory grammaticalStructureFactory(Filter<String> puncFilt) {
    return new ChineseGrammaticalStructureFactory(puncFilt);
  }

  @Override
  public GrammaticalStructureFactory grammaticalStructureFactory(Filter<String> puncFilt, HeadFinder hf) {
    return new ChineseGrammaticalStructureFactory(puncFilt, hf);
  }

  @Override
  public boolean supportsGrammaticalStructures() {
    return true;
  }

  @Override
  public TreeReaderFactory treeReaderFactory() {
    final TreeNormalizer tn = new BobChrisTreeNormalizer();
    return new CTBTreeReaderFactory(tn);
  }

  /** {@inheritDoc} */
  @Override
  public HeadFinder headFinder() {
    return new ChineseHeadFinder(this);
  }

  /** {@inheritDoc} */
  @Override
  public HeadFinder typedDependencyHeadFinder() {
    return new ChineseSemanticHeadFinder(this);
  }

}
