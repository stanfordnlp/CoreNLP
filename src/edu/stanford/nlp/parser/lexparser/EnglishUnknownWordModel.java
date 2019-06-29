// Stanford Parser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002, 2003, 2004, 2005, 2008 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    parser-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/lex-parser.html

package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.process.DistSimClassifier;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * This is a basic unknown word model for English.  It supports 5 different
 * types of feature modeling; see {@link #getSignature(String, int)}.
 *
 * <i>Implementation note</i>: the contents of this class tend to overlap somewhat
 * with {@link ArabicUnknownWordModel} and were originally included in {@link BaseLexicon}.
 *
 * @author Dan Klein
 * @author Galen Andrew
 * @author Christopher Manning
 * @author Anna Rafferty
 */
public class EnglishUnknownWordModel extends BaseUnknownWordModel  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(EnglishUnknownWordModel.class);

  private static final long serialVersionUID = 4825624957364628770L;

  private static final boolean DEBUG_UWM = false;

  protected final boolean smartMutation;

  protected final int unknownSuffixSize;
  protected final int unknownPrefixSize;

  protected final String wordClassesFile;

  private static final int MIN_UNKNOWN = 0;
  private static final int MAX_UNKNOWN = 8;

  public EnglishUnknownWordModel(Options op, Lexicon lex,
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
    wordClassesFile = op.lexOptions.wordClassesFile;
  }

  /**
   * This constructor creates an UWM with empty data structures.  Only
   * use if loading in the data separately, such as by reading in text
   * lines containing the data.
   */
  public EnglishUnknownWordModel(Options op, Lexicon lex,
                                 Index<String> wordIndex,
                                 Index<String> tagIndex) {
    this(op, lex, wordIndex, tagIndex, new ClassicCounter<>());
  }

  @Override
  public float score(IntTaggedWord iTW, int loc, double c_Tseen, double total, double smooth, String word) {
    double pb_T_S = scoreProbTagGivenWordSignature(iTW, loc, smooth, word);
    double p_T = (c_Tseen / total);
    double p_W = 1.0 / total;
    double pb_W_T = Math.log(pb_T_S * p_W / p_T);

    if (pb_W_T > -100.0) {
      if (DEBUG_UWM) {
        log.info(iTW + " tagging has probability " + pb_W_T);
      }
      return (float) pb_W_T;
    }
    if (DEBUG_UWM) {
      log.info(iTW + " tagging is impossible.");
    }
    return Float.NEGATIVE_INFINITY;
  } // end score()


  /** Calculate P(Tag|Signature) with Bayesian smoothing via just P(Tag|Unknown) */
  @Override
  public double scoreProbTagGivenWordSignature(IntTaggedWord iTW, int loc, double smooth, String word) {
    // iTW.tag = nullTag;
    // double c_W = ((BaseLexicon) l).getCount(iTW);
    // iTW.tag = tag;

    // unknown word model for P(T|S)

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
    return (c_TS + smooth * p_T_U) / (c_S + smooth);
  }


  /**
   * Returns the index of the signature of the word numbered wordIndex, where
   * the signature is the String representation of unknown word features.
   */
  @Override
  public int getSignatureIndex(int index, int sentencePosition, String word) {
    String uwSig = getSignature(word, sentencePosition);
    int sig = wordIndex.addToIndex(uwSig);
    if (DEBUG_UWM) {
      log.info("Signature (" + unknownLevel + "): mapped " + word +
                         " (" + index + ") to " + uwSig + " (" + sig + ')');
    }
    return sig;
  }

  /**
   * This routine returns a String that is the "signature" of the class of a
   * word. For, example, it might represent whether it is a number of ends in
   * -s. The strings returned by convention matches the pattern UNK(-.+)? ,
   * which is just assumed to not match any real word. Behavior depends on the
   * unknownLevel (-uwm flag) passed in to the class. The recognized numbers are
   * 1-5: 5 is fairly English-specific; 4, 3, and 2 look for various word
   * features (digits, dashes, etc.) which are only vaguely English-specific; 1
   * uses the last two characters combined with a simple classification by
   * capitalization.
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
      case 8:
        getSignature8(word, sb);
        break;
      case 7:
        getSignature7(word, loc, sb);
        break;
      case 6:
        getSignature6(word, loc, sb);
        break;
      case 5:
        getSignature5(word, loc, sb);
        break;
      case 4:
        getSignature4(word, loc, sb);
        break;
      case 3:
        getSignature3(word, loc, sb);
        break;
      case 2:
        getSignature2(word, loc, sb);
        break;
      case 1:
        getSignature1(word, loc, sb);
        break;
      default:
        // 0 = do nothing so it just stays as "UNK"
    } // end switch (unknownLevel)
    // log.info("Summarized " + word + " to " + sb.toString());
    return sb.toString();
  } // end getSignature()


  private static void getSignature7(String word, int loc, StringBuilder sb) {
    // New Sep 2008. Like 2 but rely more on Caps somewhere than initial Caps
    // {-ALLC, -INIT, -UC somewhere, -LC, zero} +
    // {-DASH, zero} +
    // {-NUM, -DIG, zero} +
    // {lowerLastChar, zeroIfShort}
    boolean hasDigit = false;
    boolean hasNonDigit = false;
    boolean hasLower = false;
    boolean hasUpper = false;
    boolean hasDash = false;
    int wlen = word.length();
    for (int i = 0; i < wlen; i++) {
      char ch = word.charAt(i);
      if (Character.isDigit(ch)) {
        hasDigit = true;
      } else {
        hasNonDigit = true;
        if (Character.isLetter(ch)) {
          if (Character.isLowerCase(ch) || Character.isTitleCase(ch)) {
            hasLower = true;
          } else {
            hasUpper = true;
          }
        } else if (ch == '-') {
          hasDash = true;
        }
      }
    }
    if (wlen > 0 && hasUpper) {
      if ( ! hasLower) {
        sb.append("-ALLC");
      } else if (loc == 0) {
        sb.append("-INIT");
      } else {
        sb.append("-UC");
      }
    } else if (hasLower) { // if (Character.isLowerCase(word.charAt(0))) {
      sb.append("-LC");
    }
    // no suffix = no (lowercase) letters
    if (hasDash) {
      sb.append("-DASH");
    }
    if (hasDigit) {
      if (!hasNonDigit) {
        sb.append("-NUM");
      } else {
        sb.append("-DIG");
      }
    } else if (wlen > 3) {
      // don't do for very short words: "yes" isn't an "-es" word
      // try doing to lower for further densening and skipping digits
      char ch = word.charAt(word.length() - 1);
      sb.append(Character.toLowerCase(ch));
    }
    // no suffix = short non-number, non-alphabetic
  }


  private void getSignature6(String word, int loc, StringBuilder sb) {
    // New Sep 2008. Like 5 but rely more on Caps somewhere than initial Caps
    // { -INITC, -CAPS, (has) -CAP, -LC lowercase, 0 } +
    // { -KNOWNLC, 0 } + [only for INITC]
    // { -NUM, 0 } +
    // { -DASH, 0 } +
    // { -last lowered char(s) if known discriminating suffix, 0}
    int wlen = word.length();
    int numCaps = 0;
    boolean hasDigit = false;
    boolean hasDash = false;
    boolean hasLower = false;
    for (int i = 0; i < wlen; i++) {
      char ch = word.charAt(i);
      if (Character.isDigit(ch)) {
        hasDigit = true;
      } else if (ch == '-') {
        hasDash = true;
      } else if (Character.isLetter(ch)) {
        if (Character.isLowerCase(ch)) {
          hasLower = true;
        } else if (Character.isTitleCase(ch)) {
          hasLower = true;
          numCaps++;
        } else {
          numCaps++;
        }
      }
    }
    String lowered = word.toLowerCase();
    if (numCaps > 1) {
      sb.append("-CAPS");
    } else if (numCaps > 0) {
      if (loc == 0) {
        sb.append("-INITC");
        if (getLexicon().isKnown(lowered)) {
          sb.append("-KNOWNLC");
        }
      } else {
        sb.append("-CAP");
      }
    } else if (hasLower) { // (Character.isLowerCase(ch0)) {
      sb.append("-LC");
    }
    if (hasDigit) {
      sb.append("-NUM");
    }
    if (hasDash) {
      sb.append("-DASH");
    }
    if (lowered.endsWith("s") && wlen >= 3) {
      // here length 3, so you don't miss out on ones like 80s
      char ch2 = lowered.charAt(wlen - 2);
      // not -ess suffixes or greek/latin -us, -is
      if (ch2 != 's' && ch2 != 'i' && ch2 != 'u') {
        sb.append("-s");
      }
    } else if (word.length() >= 5 && !hasDash && !(hasDigit && numCaps > 0)) {
      // don't do for very short words;
      // Implement common discriminating suffixes
      if (lowered.endsWith("ed")) {
        sb.append("-ed");
      } else if (lowered.endsWith("ing")) {
        sb.append("-ing");
      } else if (lowered.endsWith("ion")) {
        sb.append("-ion");
      } else if (lowered.endsWith("er")) {
        sb.append("-er");
      } else if (lowered.endsWith("est")) {
        sb.append("-est");
      } else if (lowered.endsWith("ly")) {
        sb.append("-ly");
      } else if (lowered.endsWith("ity")) {
        sb.append("-ity");
      } else if (lowered.endsWith("y")) {
        sb.append("-y");
      } else if (lowered.endsWith("al")) {
        sb.append("-al");
        // } else if (lowered.endsWith("ble")) {
        // sb.append("-ble");
        // } else if (lowered.endsWith("e")) {
        // sb.append("-e");
      }
    }
  }


  private void getSignature5(String word, int loc, StringBuilder sb) {
    // Reformed Mar 2004 (cdm); hopefully better now.
    // { -CAPS, -INITC ap, -LC lowercase, 0 } +
    // { -KNOWNLC, 0 } + [only for INITC]
    // { -NUM, 0 } +
    // { -DASH, 0 } +
    // { -last lowered char(s) if known discriminating suffix, 0}
    int wlen = word.length();
    int numCaps = 0;
    boolean hasDigit = false;
    boolean hasDash = false;
    boolean hasLower = false;
    for (int i = 0; i < wlen; i++) {
      char ch = word.charAt(i);
      if (Character.isDigit(ch)) {
        hasDigit = true;
      } else if (ch == '-') {
        hasDash = true;
      } else if (Character.isLetter(ch)) {
        if (Character.isLowerCase(ch)) {
          hasLower = true;
        } else if (Character.isTitleCase(ch)) {
          hasLower = true;
          numCaps++;
        } else {
          numCaps++;
        }
      }
    }
    char ch0 = word.charAt(0);
    String lowered = word.toLowerCase();
    if (Character.isUpperCase(ch0) || Character.isTitleCase(ch0)) {
      if (loc == 0 && numCaps == 1) {
        sb.append("-INITC");
        if (getLexicon().isKnown(lowered)) {
          sb.append("-KNOWNLC");
        }
      } else {
        sb.append("-CAPS");
      }
    } else if (!Character.isLetter(ch0) && numCaps > 0) {
      sb.append("-CAPS");
    } else if (hasLower) { // (Character.isLowerCase(ch0)) {
      sb.append("-LC");
    }
    if (hasDigit) {
      sb.append("-NUM");
    }
    if (hasDash) {
      sb.append("-DASH");
    }
    if (lowered.endsWith("s") && wlen >= 3) {
      // here length 3, so you don't miss out on ones like 80s
      char ch2 = lowered.charAt(wlen - 2);
      // not -ess suffixes or greek/latin -us, -is
      if (ch2 != 's' && ch2 != 'i' && ch2 != 'u') {
        sb.append("-s");
      }
    } else if (word.length() >= 5 && !hasDash && !(hasDigit && numCaps > 0)) {
      // don't do for very short words;
      // Implement common discriminating suffixes
      if (lowered.endsWith("ed")) {
        sb.append("-ed");
      } else if (lowered.endsWith("ing")) {
        sb.append("-ing");
      } else if (lowered.endsWith("ion")) {
        sb.append("-ion");
      } else if (lowered.endsWith("er")) {
        sb.append("-er");
      } else if (lowered.endsWith("est")) {
        sb.append("-est");
      } else if (lowered.endsWith("ly")) {
        sb.append("-ly");
      } else if (lowered.endsWith("ity")) {
        sb.append("-ity");
      } else if (lowered.endsWith("y")) {
        sb.append("-y");
      } else if (lowered.endsWith("al")) {
        sb.append("-al");
        // } else if (lowered.endsWith("ble")) {
        // sb.append("-ble");
        // } else if (lowered.endsWith("e")) {
        // sb.append("-e");
      }
    }
  }


  private static void getSignature4(String word, int loc, StringBuilder sb) {
    boolean hasDigit = false;
    boolean hasNonDigit = false;
    boolean hasLetter = false;
    boolean hasLower = false;
    boolean hasDash = false;
    boolean hasPeriod = false;
    boolean hasComma = false;
    for (int i = 0; i < word.length(); i++) {
      char ch = word.charAt(i);
      if (Character.isDigit(ch)) {
        hasDigit = true;
      } else {
        hasNonDigit = true;
        if (Character.isLetter(ch)) {
          hasLetter = true;
          if (Character.isLowerCase(ch) || Character.isTitleCase(ch)) {
            hasLower = true;
          }
        } else {
          if (ch == '-') {
            hasDash = true;
          } else if (ch == '.') {
            hasPeriod = true;
          } else if (ch == ',') {
            hasComma = true;
          }
        }
      }
    }
    // 6 way on letters
    if (Character.isUpperCase(word.charAt(0)) || Character.isTitleCase(word.charAt(0))) {
      if (!hasLower) {
        sb.append("-AC");
      } else if (loc == 0) {
        sb.append("-SC");
      } else {
        sb.append("-C");
      }
    } else if (hasLower) {
      sb.append("-L");
    } else if (hasLetter) {
      sb.append("-U");
    } else {
      // no letter
      sb.append("-S");
    }
    // 3 way on number
    if (hasDigit && !hasNonDigit) {
      sb.append("-N");
    } else if (hasDigit) {
      sb.append("-n");
    }
    // binary on period, dash, comma
    if (hasDash) {
      sb.append("-H");
    }
    if (hasPeriod) {
      sb.append("-P");
    }
    if (hasComma) {
      sb.append("-C");
    }
    if (word.length() > 3) {
      // don't do for very short words: "yes" isn't an "-es" word
      // try doing to lower for further densening and skipping digits
      char ch = word.charAt(word.length() - 1);
      if (Character.isLetter(ch)) {
        sb.append('-');
        sb.append(Character.toLowerCase(ch));
      }
    }
  }


  private static void getSignature3(String word, int loc, StringBuilder sb) {
    // This basically works right, except note that 'S' is applied to all
    // capitalized letters in first word of sentence, not just first....
    sb.append('-');
    char lastClass = '-'; // i.e., nothing
    int num = 0;
    for (int i = 0; i < word.length(); i++) {
      char ch = word.charAt(i);
      char newClass;
      if (Character.isUpperCase(ch) || Character.isTitleCase(ch)) {
        if (loc == 0) {
          newClass = 'S';
        } else {
          newClass = 'L';
        }
      } else if (Character.isLetter(ch)) {
        newClass = 'l';
      } else if (Character.isDigit(ch)) {
        newClass = 'd';
      } else if (ch == '-') {
        newClass = 'h';
      } else if (ch == '.') {
        newClass = 'p';
      } else {
        newClass = 's';
      }
      if (newClass != lastClass) {
        lastClass = newClass;
        sb.append(lastClass);
        num = 1;
      } else {
        if (num < 2) {
          sb.append('+');
        }
        num++;
      }
    }
    if (word.length() > 3) {
      // don't do for very short words: "yes" isn't an "-es" word
      // try doing to lower for further densening and skipping digits
      char ch = Character.toLowerCase(word.charAt(word.length() - 1));
      sb.append('-');
      sb.append(ch);
    }
  }


  private static void getSignature2(String word, int loc, StringBuilder sb) {
    // {-ALLC, -INIT, -UC, -LC, zero} +
    // {-DASH, zero} +
    // {-NUM, -DIG, zero} +
    // {lowerLastChar, zeroIfShort}
    boolean hasDigit = false;
    boolean hasNonDigit = false;
    boolean hasLower = false;
    int wlen = word.length();
    for (int i = 0; i < wlen; i++) {
      char ch = word.charAt(i);
      if (Character.isDigit(ch)) {
        hasDigit = true;
      } else {
        hasNonDigit = true;
        if (Character.isLetter(ch)) {
          if (Character.isLowerCase(ch) || Character.isTitleCase(ch)) {
            hasLower = true;
          }
        }
      }
    }
    if (wlen > 0
            && (Character.isUpperCase(word.charAt(0)) || Character.isTitleCase(word.charAt(0)))) {
      if (!hasLower) {
        sb.append("-ALLC");
      } else if (loc == 0) {
        sb.append("-INIT");
      } else {
        sb.append("-UC");
      }
    } else if (hasLower) { // if (Character.isLowerCase(word.charAt(0))) {
      sb.append("-LC");
    }
    // no suffix = no (lowercase) letters
    if (word.indexOf('-') >= 0) {
      sb.append("-DASH");
    }
    if (hasDigit) {
      if (!hasNonDigit) {
        sb.append("-NUM");
      } else {
        sb.append("-DIG");
      }
    } else if (wlen > 3) {
      // don't do for very short words: "yes" isn't an "-es" word
      // try doing toLower for further densening and skipping digits
      char ch = word.charAt(word.length() - 1);
      sb.append(Character.toLowerCase(ch));
    }
    // no suffix = short non-number, non-alphabetic
  }


  private static void getSignature1(String word, int loc, StringBuilder sb) {
    sb.append('-');
    sb.append(word.substring(Math.max(word.length() - 2, 0), word.length()));
    sb.append('-');
    if (Character.isLowerCase(word.charAt(0))) {
      sb.append("LOWER");
    } else {
      if (Character.isUpperCase(word.charAt(0))) {
        if (loc == 0) {
          sb.append("INIT");
        } else {
          sb.append("UPPER");
        }
      } else {
        sb.append("OTHER");
      }
    }
  }


  private void getSignature8(String word, StringBuilder sb) {
    sb.append('-');
    boolean digit = true;
    for (int i = 0; i < word.length(); i++) {
      char c = word.charAt(i);
      if ( ! (Character.isDigit(c) || c == '.' || c == ',' || (i == 0 && (c == '-' || c == '+')))) {
        digit = false;
      }
    }
    // digit = false;  // todo: Just turned off while we test it.
    if (digit) {
      sb.append("NUMBER");
    } else {
      if (distSim == null) {
        distSim = new DistSimClassifier(wordClassesFile, false, true);
        // todo XXXX booleans depend on distsim file; need more options
      }

      String cluster = distSim.distSimClass(word);
      if (cluster == null) {
        cluster = "NULL";
      }
      sb.append(cluster);
    }
  }

  private transient DistSimClassifier distSim;

} // end class
