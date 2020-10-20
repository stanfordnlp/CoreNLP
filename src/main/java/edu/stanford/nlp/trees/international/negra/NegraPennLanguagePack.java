package edu.stanford.nlp.trees.international.negra; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.AbstractTreebankLanguagePack;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.HeadFinder;

/**
 * Language pack for Negra and Tiger treebanks <em>after</em> conversion to
 * PTB format.
 *
 * @author Roger Levy
 * @author Spence Green
 */
public class NegraPennLanguagePack extends AbstractTreebankLanguagePack  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(NegraPennLanguagePack.class);

  private static final long serialVersionUID = 9081305982861675328L;

  /** Grammatical function parameters.  If this is true, keep subj, obj, iobj functional tags, only. */
  private boolean leaveGF = false;

  private static String[] gfToKeepArray = {"SB", "OA", "DA"};


  /**
   * Gives a handle to the TreebankLanguagePack
   */
  public NegraPennLanguagePack() {
    this(false, AbstractTreebankLanguagePack.DEFAULT_GF_CHAR);
  }

  /**
   * Gives a handle to the TreebankLanguagePack
   */
  public NegraPennLanguagePack(boolean leaveGF) {
    this(leaveGF, AbstractTreebankLanguagePack.DEFAULT_GF_CHAR);
  }

  /**
   * Make a new language pack with grammatical functions used based on the value of leaveGF
   * and marked with the character gfChar.  gfChar should *not* be an annotation introducing character.
   */
  public NegraPennLanguagePack(boolean leaveGF, char gfChar) {
    super(gfChar);
    this.leaveGF  = leaveGF;
  }

  private static final String NEGRA_ENCODING = "ISO-8859-1";


  private static final String[] evalBignoredTags = {"$.", "$,"};

  private static final String[] negraSFPunctTags = {"$."};

  private static final String[] negraSFPunctWords = {".", "!", "?"};

  private static final String[] negraPunctTags = {"$.", "$,", "$*LRB*"};

  /**
   * The unicode escape is for a middle dot character
   */
  private static final String[] negraPunctWords = {"-", ",", ";", ":", "!", "?", "/", ".", "...", "\u00b7", "'", "\"", "(", ")", "*LRB*", "*RRB*"};

  /**
   * The first 3 are used by the Penn Treebank; # is used by the
   * BLLIP corpus, and ^ and ~ are used by Klein's lexparser.
   */
  private static char[] annotationIntroducingChars = {'-', '%', '=', '|', '#', '^', '~'};

  /**
   * This is valid for "BobChrisTreeNormalizer" conventions only.
   */
  private static String[] pennStartSymbols = {"ROOT"};


  /**
   * Returns a String array of punctuation tags for this treebank/language.
   *
   * @return The punctuation tags
   */
  @Override
  public String[] punctuationTags() {
    return negraPunctTags;
  }


  /**
   * Returns a String array of punctuation words for this treebank/language.
   *
   * @return The punctuation words
   */
  @Override
  public String[] punctuationWords() {
    return negraPunctWords;
  }


  /**
   * Returns a String array of sentence final punctuation tags for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  @Override
  public String[] sentenceFinalPunctuationTags() {
    return negraSFPunctTags;
  }

  /**
   * Returns a String array of sentence final punctuation words for this
   * treebank/language.
   *
   * @return The sentence final punctuation tags
   */
  public String[] sentenceFinalPunctuationWords() {
    return negraSFPunctWords;
  }

//wsg2010: Disabled limited grammatical functions for now, which decrease F1 by ~10.0.
  @Override
  public String basicCategory(String category) {
    String basicCat;
    if (leaveGF) {
      basicCat = stripGF(category);
    } else {
      basicCat = super.basicCategory(category);
    }
    // log.info("NPLP stripping " + category + " with leaveGF = " + leaveGF + " gives " + basicCat);
    return basicCat;
  }

  @Override
  public String stripGF(String category) {
    if(category == null) {
      return null;
    }
    int index = category.lastIndexOf(gfCharacter);
    if(index > 0) {
      if(!containsKeptGF(category, index))
        category = category.substring(0, index);
    }
    return category;
  }

  /**
   * Helper method for determining if the gf in category
   * is one of those in the array gfToKeepArray.  Index is the
   * index where the gfCharacter appears.
   */
  private static boolean containsKeptGF(String category, int index) {
    for(String gf : gfToKeepArray) {
      int gfLength = gf.length();
      if(gfLength < (category.length() - index)) {
        if(category.substring(index+1, index+1+gfLength).equals(gf))
          return true;
      }
    }
    return false;
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
    return evalBignoredTags;
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
   * Return the input Charset encoding for the Treebank.
   * See documentation for the <code>Charset</code> class.
   *
   * @return Name of Charset
   */
  @Override
  public String getEncoding() {
    return NEGRA_ENCODING;
  }

  /**
   * Returns the extension of treebank files for this treebank.
   * This is "mrg".
   */
  public String treebankFileExtension() {
    return "mrg";
  }

  public boolean isLeaveGF() {
    return leaveGF;
  }

  public void setLeaveGF(boolean leaveGF) {
    this.leaveGF = leaveGF;
  }


  @Override
  public TreeReaderFactory treeReaderFactory() {
    return new NegraPennTreeReaderFactory(this);
  }

  /** {@inheritDoc} */
  public HeadFinder headFinder() {
    return new NegraHeadFinder(this);
  }

  /** {@inheritDoc} */
  public HeadFinder typedDependencyHeadFinder() {
    return new NegraHeadFinder(this);
  }

  /**
   * Return a tokenizer which might be suitable for tokenizing text that
   * will be used with this Treebank/Language pair, without tokenizing carriage
   * returns (i.e., treating them as white space).  For German (Negra) we used
   * to only provide a {@link edu.stanford.nlp.process.WhitespaceTokenizer},
   * but people didn't much like that.
   * So now we provide {@link PTBTokenizer}. It's not customized to German, but
   * will nevertheless do better than WhitespaceTokenizer at tokenizing German!
   *
   * @return A tokenizer
   */
  @Override
  public TokenizerFactory<Word> getTokenizerFactory() {
    return PTBTokenizer.factory();
  }

}
