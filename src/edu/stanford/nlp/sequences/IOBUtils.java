package edu.stanford.nlp.sequences;

import java.util.List;
import java.util.Locale;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.PaddedList;
import edu.stanford.nlp.util.TypesafeMap;


/** A static class with functions to convert lists of tokens between
 *  different IOB-style representations.
 *
 *  @author Christopher Manning
 */
public class IOBUtils {

  private IOBUtils() {} // static methods

  /**
   * This can be used to map from any IOB-style (i.e., "I-PERS" style labels)
   * or just categories representation to any other.
   * It can read and change any representation to other representations:
   * a 4 way representation of all entities, like S-PERS, B-PERS,
   * I-PERS, E-PERS for single word, beginning, internal, and end of entity
   * (IOBES or SBIEO); always marking the first word of an entity (IOB2 or BIO);
   * only marking specially the beginning of non-first
   * items of an entity sequences with B-PERS (IOB1);
   * the reverse IOE1 and IOE2; IO where everything is I-tagged; and
   * NOPREFIX, where no prefixes are written on category labels.
   * The last two representations are deficient in not allowing adjacent
   * entities of the same class to be represented, but nevertheless
   * convenient.  Note that the background label is never given a prefix.
   * This code is very specific to the particular CoNLL way of labeling
   * classes for IOB-style encoding, but this notation is quite widespread.
   * It will work on any of these styles of input.
   * If the labels are not of the form "C-Y+", where C is a single character,
   * then they will be regarded as NOPREFIX labels.
   * This method updates the List tokens in place.
   *
   * @param tokens List of tokens (each a CoreLabel) in some style
   * @param key The key in the CoreLabel to change, commonly CoreAnnotations.AnswerAnnotation.class
   * @param backgroundLabel The background label, which gets special treatment
   * @param style Output style; one of iob[12], ioe[12], io, sbieo/iobes, noprefix
   * @param intern Whether to String-intern the new labels (may as well, small number!)
   */
  @SuppressWarnings("StringContatenationInLoop")
  public static void entitySubclassify(List<CoreLabel> tokens,
                                 Class<? extends TypesafeMap.Key<String>> key,
                                 String backgroundLabel,
                                 String style,
                                 boolean intern) {
    int how;
    String lowerStyle = style.toLowerCase(Locale.ENGLISH);
    switch (lowerStyle) {
      case "iob1":
        how = 0;
        break;
      case "iob2":
      case "bio":
        how = 1;
        break;
      case "ioe1":
        how = 2;
        break;
      case "ioe2":
        how = 3;
        break;
      case "io":
        how = 4;
        break;
      case "sbieo":
      case "iobes":
        how = 5;
        break;
      case "noprefix":
        how = 6;
        break;
      default:
        throw new IllegalArgumentException("entitySubclassify: unknown style: " + style);
    }
    List<CoreLabel> paddedTokens = new PaddedList<>(tokens, new CoreLabel());
    int size = paddedTokens.size();
    String[] newAnswers = new String[size];
    for (int i = 0; i < size; i++) {
      CoreLabel c = paddedTokens.get(i);
      CoreLabel p = paddedTokens.get(i - 1);
      CoreLabel n = paddedTokens.get(i + 1);
      String cAns = c.get(key);
      String pAns = p.get(key);
      if (pAns == null) {
        pAns = backgroundLabel;
      }
      String nAns = n.get(key);
      if (nAns == null) {
        nAns = backgroundLabel;
      }
      String base;
      char prefix;
      if (cAns.length() > 2 && cAns.charAt(1) == '-') {
        base = cAns.substring(2, cAns.length());
        prefix = cAns.charAt(0);
      } else {
        base = cAns;
        prefix = ' ';
      }
      String pBase;
      char pPrefix;
      if (pAns.length() > 2 && pAns.charAt(1) == '-') {
        pBase = pAns.substring(2, pAns.length());
        pPrefix = pAns.charAt(0);
      } else {
        pBase = pAns;
        pPrefix = ' ';
      }
      String nBase;
      char nPrefix;
      if (nAns.length() > 2 && nAns.charAt(1) == '-') {
        nBase = nAns.substring(2, nAns.length());
        nPrefix = nAns.charAt(0);
      } else {
        nBase = nAns;
        nPrefix = ' ';
      }

      boolean isStartAdjacentSame = base.equals(pBase) &&
              (prefix == 'B' || prefix == 'S' || pPrefix == 'E' || pPrefix == 'S');
      boolean isEndAdjacentSame = base.equals(nBase) &&
              (prefix == 'E' || prefix == 'S' || nPrefix == 'B' || pPrefix == 'S');
      boolean isFirst = !base.equals(pBase) || isStartAdjacentSame;
      boolean isLast = !base.equals(nBase) || isEndAdjacentSame;
      String newAnswer = base;
      if ( ! base.equals(backgroundLabel)) {
        switch (how) {
          case 0: // iob1, only B if adjacent
            if (isStartAdjacentSame) {
              newAnswer = "B-" + base;
            } else {
              newAnswer = "I-" + base;
            }
            break;
          case 1: // iob2 always B at start
            if (isFirst) {
              newAnswer = "B-" + base;
            } else {
              newAnswer = "I-" + base;
            }
            break;
          case 2: // ioe1
            if (isEndAdjacentSame) {
              newAnswer = "E-" + base;
            } else {
              newAnswer = "I-" + base;
            }
            break;
          case 3: // ioe2
            if (isLast) {
              newAnswer = "E-" + base;
            } else {
              newAnswer = "I-" + base;
            }
            break;
          case 4:
            newAnswer = "I-" + base;
            break;
          case 5:
            if (isFirst && isLast) {
              newAnswer = "S-" + base;
            } else if ( ( ! isFirst) && isLast) {
              newAnswer = "E-" + base;
            } else if (isFirst && ( ! isLast)) {
              newAnswer = "B-" + base;
            } else {
              newAnswer = "I-" + base;
            }
          // nothing to do on case 6 as it's just base
        }
      }
      if (intern) {
        newAnswer = newAnswer.intern();
      }
      newAnswers[i] = newAnswer;
    }
    for (int i = 0; i < size; i++) {
      CoreLabel c = tokens.get(i);
      c.set(CoreAnnotations.AnswerAnnotation.class, newAnswers[i]);
    }
  }

  /** Converts entity representation of a file. */
  public static void main(String[] args) {
    if (args.length == 0) {

    } else {
      for (String arg : args) {

      }
    }
  }


}
