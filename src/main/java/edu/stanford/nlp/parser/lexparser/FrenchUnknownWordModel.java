package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.international.french.FrenchUnknownWordSignatures;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;


public class FrenchUnknownWordModel extends BaseUnknownWordModel  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FrenchUnknownWordModel.class);

  private static final long serialVersionUID = -776564693549194424L;

  protected final boolean smartMutation;

  protected final int unknownSuffixSize;
  protected final int unknownPrefixSize;

  public FrenchUnknownWordModel(Options op, Lexicon lex,
                                Index<String> wordIndex,
                                Index<String> tagIndex,
                                ClassicCounter<IntTaggedWord> unSeenCounter) {
    super(op, lex, wordIndex, tagIndex, unSeenCounter, null, null, null);
    this.smartMutation = op.lexOptions.smartMutation;
    this.unknownSuffixSize = op.lexOptions.unknownSuffixSize;
    this.unknownPrefixSize = op.lexOptions.unknownPrefixSize;
  }

  /**
   * This constructor creates an UWM with empty data structures.  Only
   * use if loading in the data separately, such as by reading in text
   * lines containing the data.
   */
  public FrenchUnknownWordModel(Options op, Lexicon lex,
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
   * TODO Can add various signatures, setting the signature via Options.
   *
   * @param word The word to make a signature for
   * @param loc Its position in the sentence (mainly so sentence-initial
   *          capitalized words can be treated differently)
   * @return A String that is its signature (equivalence class)
   */
  @Override
  public String getSignature(String word, int loc) {
    final String BASE_LABEL = "UNK";
    StringBuilder sb = new StringBuilder(BASE_LABEL);
    switch (unknownLevel) {
      case 1: //Marie's initial attempt
        sb.append(FrenchUnknownWordSignatures.nounSuffix(word));
        if(sb.toString().equals(BASE_LABEL)) {
          sb.append(FrenchUnknownWordSignatures.adjSuffix(word));
          if(sb.toString().equals(BASE_LABEL)) {
            sb.append(FrenchUnknownWordSignatures.verbSuffix(word));
            if(sb.toString().equals(BASE_LABEL)) {
              sb.append(FrenchUnknownWordSignatures.advSuffix(word));
            }
          }
        }

        sb.append(FrenchUnknownWordSignatures.possiblePlural(word));

        String hasDigit = FrenchUnknownWordSignatures.hasDigit(word);
        String isDigit = FrenchUnknownWordSignatures.isDigit(word);

        if( ! hasDigit.equals("")) {
          if(isDigit.equals("")) {
            sb.append(hasDigit);
          } else {
            sb.append(isDigit);
          }
        }

//        if(FrenchUnknownWordSignatures.isPunc(word).equals(""))
          sb.append(FrenchUnknownWordSignatures.hasPunc(word));
//        else
//          sb.append(FrenchUnknownWordSignatures.isPunc(word));

        sb.append(FrenchUnknownWordSignatures.isAllCaps(word));

        if(loc > 0) {
          if(FrenchUnknownWordSignatures.isAllCaps(word).equals(""))
            sb.append(FrenchUnknownWordSignatures.isCapitalized(word));
        }

        //Backoff to suffix if we haven't matched anything else
        if(unknownSuffixSize > 0 && sb.toString().equals(BASE_LABEL)) {
          int min = word.length() < unknownSuffixSize ? word.length(): unknownSuffixSize;
          sb.append('-').append(word.substring(word.length() - min));
        }

        break;

      default:
        System.err.printf("%s: Invalid unknown word signature! (%d)%n", this.getClass().getName(),unknownLevel);
    }

    return sb.toString();
  }
}
