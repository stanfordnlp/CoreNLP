package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;

/**
 * This is a basic unknown word model for Arabic.  It supports 4 different
 * types of feature modeling; see {@link #getSignature(String, int)}.
 *
 * <i>Implementation note</i>: the contents of this class tend to overlap somewhat
 * with {@link EnglishUnknownWordModel} and were originally included in {@link BaseLexicon}.
 *
 * @author Roger Levy
 * @author Christopher Manning
 * @author Anna Rafferty
 */
public class ArabicUnknownWordModel extends BaseUnknownWordModel  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ArabicUnknownWordModel.class);

  private static final long serialVersionUID = 4825624957364628771L;

  private static final int MIN_UNKNOWN = 6;

  private static final int MAX_UNKNOWN = 10;

  protected final boolean smartMutation;
  protected final int unknownSuffixSize;
  protected final int unknownPrefixSize;


  public ArabicUnknownWordModel(Options op, Lexicon lex,
                                Index<String> wordIndex,
                                Index<String> tagIndex,
                                ClassicCounter<IntTaggedWord> unSeenCounter) {
    super(op, lex, wordIndex, tagIndex, unSeenCounter, null, null, null);
    if (unknownLevel < MIN_UNKNOWN || unknownLevel > MAX_UNKNOWN) {
      throw new IllegalArgumentException("Invalid value for useUnknownWordSignatures: " + unknownLevel);
    }
    this.smartMutation = op.lexOptions.smartMutation;
    this.unknownSuffixSize = op.lexOptions.unknownSuffixSize;
    this.unknownPrefixSize = op.lexOptions.unknownPrefixSize;
  }

  /**
   * This constructor creates an UWM with empty data structures.  Only
   * use if loading in the data separately, such as by reading in text
   * lines containing the data.
   */
  public ArabicUnknownWordModel(Options op, Lexicon lex,
                                Index<String> wordIndex,
                                Index<String> tagIndex) {
    this(op, lex, wordIndex, tagIndex, new ClassicCounter<>());
  }

  @Override
  public float score(IntTaggedWord iTW, int loc, double c_Tseen, double total, double smooth, String word) {
    double pb_W_T; // always set below

    //  unknown word model for P(T|S)

    int wordSig = getSignatureIndex(iTW.word, loc, word);
    IntTaggedWord temp = new IntTaggedWord(wordSig, iTW.tag);
    double c_TS = unSeenCounter.getCount(temp);
    temp = new IntTaggedWord(wordSig, nullTag);
    double c_S = unSeenCounter.getCount(temp);
    double c_U = unSeenCounter.getCount(NULL_ITW);
    temp = new IntTaggedWord(nullWord, iTW.tag);
    double c_T = unSeenCounter.getCount(temp);

    double p_T_U = c_T / c_U;

    if (unknownLevel == 0) {
      c_TS = 0;
      c_S = 0;
    }
    double pb_T_S = (c_TS + smooth * p_T_U) / (c_S + smooth);

    double p_T = (c_Tseen / total);
    double p_W = 1.0 / total;
    pb_W_T = Math.log(pb_T_S * p_W / p_T);

    return (float) pb_W_T;
  }


  /**
   * Returns the index of the signature of the word numbered wordIndex, where
   * the signature is the String representation of unknown word features.
   */
  @Override
  public int getSignatureIndex(int index, int sentencePosition, String word) {
    String uwSig = getSignature(word, sentencePosition);
    int sig = wordIndex.addToIndex(uwSig);
    return sig;
  }

  /**
   *  6-9 were added for Arabic. 6 looks for the prefix Al- (and
   * knows that Buckwalter uses various symbols as letters), while 7 just looks
   * for numbers and last letter. 8 looks for Al-, looks for several useful
   * suffixes, and tracks the first letter of the word. (note that the first
   * letter seems a bit more informative than the last letter, overall.)
   * 9 tries to build on 8, but avoiding some of its perceived flaws: really it
   * was using the first AND last letter.
   *
   * @param word The word to make a signature for
   * @param loc Its position in the sentence (mainly so sentence-initial
   *          capitalized words can be treated differently)
   * @return A String that is its signature (equivalence class)
   */
  @Override
  public String getSignature(String word, int loc) {
    StringBuilder sb = new StringBuilder("UNK");
    switch (unknownLevel) {
    case 10://Anna's attempt at improving Chris' attempt, April 2008
    {
      boolean allDigitPlus = ArabicUnknownWordSignatures.allDigitPlus(word);
      int leng = word.length();
      if (allDigitPlus) {
        sb.append("-NUM");
      } else if (word.startsWith("Al") || word.startsWith("\u0627\u0644")) {
        sb.append("-Al");
      } else {
        // the first letters of a word seem more informative overall than the
        // last letters.
        // Alternatively we could add on the first two letters, if there's
        // enough data.
        if (unknownPrefixSize > 0) {
          int min = leng < unknownPrefixSize ? leng: unknownPrefixSize;
          sb.append('-').append(word.substring(0, min));
        }
      }
      if(word.length() == 1) {
        //add in the unicode type for the char
        sb.append(Character.getType(word.charAt(0)));
      }
      sb.append(ArabicUnknownWordSignatures.likelyAdjectivalSuffix(word));
      sb.append(ArabicUnknownWordSignatures.pastTenseVerbNumberSuffix(word));
      sb.append(ArabicUnknownWordSignatures.presentTenseVerbNumberSuffix(word));
      String ans = ArabicUnknownWordSignatures.abstractionNounSuffix(word);
      if (! "".equals(ans)) {
        sb.append(ans);
      } else {
        sb.append(ArabicUnknownWordSignatures.taaMarbuuTaSuffix(word));
      }
      if (unknownSuffixSize > 0 && ! allDigitPlus) {
        int min = leng < unknownSuffixSize ? leng: unknownSuffixSize;
        sb.append('-').append(word.substring(word.length() - min));
      }
      break;
    }
    case 9: // Chris' attempt at improving Roger's Arabic attempt, Nov 2006.
    {
      boolean allDigitPlus = ArabicUnknownWordSignatures.allDigitPlus(word);
      int leng = word.length();
      if (allDigitPlus) {
        sb.append("-NUM");
      } else if (word.startsWith("Al") || word.startsWith("\u0627\u0644")) {
        sb.append("-Al");
      } else {
        // the first letters of a word seem more informative overall than the
        // last letters.
        // Alternatively we could add on the first two letters, if there's
        // enough data.
        if (unknownPrefixSize > 0) {
          int min = leng < unknownPrefixSize ? leng: unknownPrefixSize;
          sb.append('-').append(word.substring(0, min));
        }
      }

      sb.append(ArabicUnknownWordSignatures.likelyAdjectivalSuffix(word));
      sb.append(ArabicUnknownWordSignatures.pastTenseVerbNumberSuffix(word));
      sb.append(ArabicUnknownWordSignatures.presentTenseVerbNumberSuffix(word));
      String ans = ArabicUnknownWordSignatures.abstractionNounSuffix(word);
      if (! "".equals(ans)) {
        sb.append(ans);
      } else {
        sb.append(ArabicUnknownWordSignatures.taaMarbuuTaSuffix(word));
      }
      if (unknownSuffixSize > 0 && ! allDigitPlus) {
        int min = leng < unknownSuffixSize ? leng: unknownSuffixSize;
        sb.append('-').append(word.substring(word.length() - min));
      }
      break;
    }

    case 8: // Roger's attempt at an Arabic UWM, May 2006.
    {
      if (word.startsWith("Al")) {
        sb.append("-Al");
      }
      boolean allDigitPlus = ArabicUnknownWordSignatures.allDigitPlus(word);
      if (allDigitPlus) {
        sb.append("-NUM");
      } else {
        // the first letters of a word seem more informative overall than the
        // last letters.
        // Alternatively we could add on the first two letters, if there's
        // enough data.
        sb.append('-').append(word.charAt(0));
      }
      sb.append(ArabicUnknownWordSignatures.likelyAdjectivalSuffix(word));
      sb.append(ArabicUnknownWordSignatures.pastTenseVerbNumberSuffix(word));
      sb.append(ArabicUnknownWordSignatures.presentTenseVerbNumberSuffix(word));
      sb.append(ArabicUnknownWordSignatures.taaMarbuuTaSuffix(word));
      sb.append(ArabicUnknownWordSignatures.abstractionNounSuffix(word));
      break;
    }

    case 7: {
      // For Arabic with Al's separated off (cdm, May 2006)
      // { -NUM, -lastChar }
      boolean allDigitPlus = ArabicUnknownWordSignatures.allDigitPlus(word);
      if (allDigitPlus) {
        sb.append("-NUM");
      } else {
        sb.append(word.charAt(word.length() - 1));
      }
      break;
    }

    case 6: {
      // For Arabic (cdm, May 2006), with Al- as part of word
      // { -Al, 0 } +
      // { -NUM, -last char(s) }
      if (word.startsWith("Al")) {
        sb.append("-Al");
      }
      boolean allDigitPlus = ArabicUnknownWordSignatures.allDigitPlus(word);
      if (allDigitPlus) {
        sb.append("-NUM");
      } else {
        sb.append(word.charAt(word.length() - 1));
      }
      break;
    }
    default:
      // 0 = do nothing so it just stays as "UNK"
    } // end switch (unknownLevel)
    // log.info("Summarized " + word + " to " + sb.toString());
    return sb.toString();
  } // end getSignature()

  @Override
  public int getUnknownLevel() {
    return unknownLevel;
  }

}
