package edu.stanford.nlp.trees.international.tuebadz;

import edu.stanford.nlp.trees.AbstractTreebankLanguagePack;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.HeadFinder;


/** Language pack for the Tuebingen Treebank of Written German (TueBa-D/Z).
 *  http://www.sfs.nphil.uni-tuebingen.de/en_tuebadz.shtml
 *  This treebank is in utf-8.
 *
 *  @author Roger Levy (rog@stanford.edu)
 */
public class TueBaDZLanguagePack extends AbstractTreebankLanguagePack {
  private boolean limitedGF = false;

  private static String[] gfToKeepArray = {"ON", "OA", "OD"};

  private static String[] tuebadzPunctTags = {"$.","$,","$-LRB"};

  private static String[] tuebadzSFPunctTags = {"$."};


  private static String[] tuebadzPunctWords = { "`", "-", ",", ";", ":", "!", "?", "/", ".", "...","'", "\"", "[", "]", "*"};

  private static String[] tuebadzSFPunctWords = {".", "!", "?"};

  /**
   * The first one is used by the TueBaDZ Treebank, and the rest are used by Klein's lexparser.
   */
  private static char[] annotationIntroducingChars = {':', '^', '~', '%', '#', '='};


  /**
   * Gives a handle to the TreebankLanguagePack
   */
  public TueBaDZLanguagePack() {
    this(false);
  }

  /**
   * Make a new language pack with grammatical functions used based on the value of leaveGF
   */
  public TueBaDZLanguagePack(boolean leaveGF) {
    this(leaveGF, AbstractTreebankLanguagePack.DEFAULT_GF_CHAR);
  }

  /**
   * Make a new language pack with grammatical functions used based on the value of leaveGF
   * and marked with the character gfChar.  gfChar should *not* be an annotation introducing character.
   */
  public TueBaDZLanguagePack(boolean leaveGF, char gfChar) {
    this(false, leaveGF, gfChar);
  }

  /**
   * Make a new language pack with grammatical functions used based on the value of leaveGF
   * and marked with the character gfChar.  gfChar should *not* be an annotation introducing character.
   */
  public TueBaDZLanguagePack(boolean useLimitedGF, boolean leaveGF, char gfChar) {
    super(gfChar);
    this.leaveGF  = leaveGF;
    this.limitedGF = useLimitedGF;
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

  @Override
  public String[] punctuationTags() {
    return tuebadzPunctTags;
  }

  @Override
  public String[] punctuationWords() {
    return tuebadzPunctWords;
  }

  @Override
  public String[] sentenceFinalPunctuationTags() {
    return tuebadzSFPunctTags;
  }

  @Override
  public String[] startSymbols() {
    return new String[] {"TOP"};
  }

  public String[] sentenceFinalPunctuationWords() {
    return tuebadzSFPunctWords;
  }

  public String treebankFileExtension() {
    return ".penn";
  }

  private boolean leaveGF = false;

  @Override
  public String basicCategory(String category) {
    String basicCat = super.basicCategory(category);
    if(!leaveGF) {
      basicCat = stripGF(basicCat);
    }
    return basicCat;
  }

  @Override
  public String stripGF(String category) {
    if(category == null) {
      return null;
    }
    int index = category.lastIndexOf(gfCharacter);
    if(index > 0) {
      if(!limitedGF || !containsKeptGF(category, index))
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
        if(category.substring(index+1).equals(gf))//category.substring(index+1, index+1+gfLength).equals(gf))
          return true;
      }
    }
    return false;
  }

  public boolean isLeaveGF() {
    return leaveGF;
  }

  public void setLeaveGF(boolean leaveGF) {
    this.leaveGF = leaveGF;
  }


  /**
   * Return the input Charset encoding for the Treebank.
   * See documentation for the <code>Charset</code> class.
   *
   * @return Name of Charset
   */
  @Override
  public String getEncoding() {
    return "iso-8859-15";
  }

  /** Prints a few aspects of the TreebankLanguagePack, just for debugging.
   */
  public static void main(String[] args) {
    TreebankLanguagePack tlp = new TueBaDZLanguagePack();
    System.out.println("Start symbol: " + tlp.startSymbol());
    String start = tlp.startSymbol();
    System.out.println("Should be true: " + (tlp.isStartSymbol(start)));
    String[] strs = new String[]{"-", "-LLB-", "NP-2", "NP=3", "NP-LGS", "NP-TMP=3", "CARD-HD"};
    for (String str : strs) {
      System.out.println("String: " + str + " basic: " + tlp.basicCategory(str) + " basicAndFunc: " + tlp.categoryAndFunction(str));
    }
  }

  private static final long serialVersionUID = 2697418320262700673L;


  public boolean isLimitedGF() {
    return limitedGF;
  }

  public void setLimitedGF(boolean limitedGF) {
    this.limitedGF = limitedGF;
  }

  @Override
  public TreeReaderFactory treeReaderFactory() {
    return new TueBaDZTreeReaderFactory(this);
  }

  /** {@inheritDoc} */
  public HeadFinder headFinder() {
    return new TueBaDZHeadFinder();
  }

  /** {@inheritDoc} */
  public HeadFinder typedDependencyHeadFinder() {
    return new TueBaDZHeadFinder();
  }

}
