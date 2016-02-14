package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.international.spanish.SpanishUnknownWordSignatures;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.StringUtils;


public class SpanishUnknownWordModel extends BaseUnknownWordModel  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SpanishUnknownWordModel.class);

  protected final boolean smartMutation;

  protected final int unknownSuffixSize;
  protected final int unknownPrefixSize;

  public SpanishUnknownWordModel(Options op, Lexicon lex,
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
  public SpanishUnknownWordModel(Options op, Lexicon lex,
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
      case 1:
        if (StringUtils.isNumeric(word)) {
          sb.append('#');
          break;
        } else if (StringUtils.isPunct(word)) {
          sb.append('!');
          break;
        }

        // Mutually exclusive patterns
        sb.append(SpanishUnknownWordSignatures.conditionalSuffix(word));
        sb.append(SpanishUnknownWordSignatures.imperfectSuffix(word));
        sb.append(SpanishUnknownWordSignatures.infinitiveSuffix(word));
        sb.append(SpanishUnknownWordSignatures.adverbSuffix(word));

        // Broad coverage patterns -- only apply if we haven't yet matched at all
        if (sb.toString().equals(BASE_LABEL)) {
          if (SpanishUnknownWordSignatures.hasVerbFirstPersonPluralSuffix(word)) {
            sb.append("-vb1p");
          } else if (SpanishUnknownWordSignatures.hasGerundSuffix(word)) {
            sb.append("-ger");
          } else if (word.endsWith("s")) {
            sb.append("-s");
          }
        }

        // Backoff to suffix if we haven't matched anything else
        if (unknownSuffixSize > 0 && sb.toString().equals(BASE_LABEL)) {
          int min = word.length() < unknownSuffixSize ? word.length() : unknownSuffixSize;
          sb.append('-').append(word.substring(word.length() - min));
        }

        char first = word.charAt(0);
        if ((Character.isUpperCase(first) || Character.isTitleCase(first)) && !isUpperCase(word)) {
          sb.append("-C");
        } else {
          sb.append("-c");
        }

        break;

      default:
        log.error(String.format("%s: Invalid unknown word signature! (%d)%n", this.getClass().getName(),unknownLevel));
    }

    return sb.toString();
  }

  private static boolean isUpperCase(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isLowerCase(s.charAt(i)))
        return false;
    }
    return true;
  }

  private static final long serialVersionUID = 5370429530690606644L;

}
