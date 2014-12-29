package edu.stanford.nlp.sequences;

import java.util.List;
import java.util.Locale;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
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
   * This will also recognize BILOU format (B=B, I=I, L=E, O=O, U=S).
   * It also works with lowercased names like i-org.
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
  public static <TOK extends CoreMap> void entitySubclassify(List<TOK> tokens,
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
      case "bilou":
        how = 7;
        break;
      default:
        throw new IllegalArgumentException("entitySubclassify: unknown style: " + style);
    }
    List<TOK> paddedTokens = new PaddedList<>(tokens, (TOK) new CoreLabel());
    int size = paddedTokens.size();
    String[] newAnswers = new String[size];
    for (int i = 0; i < size; i++) {
      TOK c = paddedTokens.get(i);
      TOK p = paddedTokens.get(i - 1);
      TOK n = paddedTokens.get(i + 1);
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
        prefix = Character.toUpperCase(cAns.charAt(0));
      } else {
        base = cAns;
        prefix = ' ';
      }
      String pBase;
      char pPrefix;
      if (pAns.length() > 2 && pAns.charAt(1) == '-') {
        pBase = pAns.substring(2, pAns.length());
        pPrefix = Character.toUpperCase(pAns.charAt(0));
      } else {
        pBase = pAns;
        pPrefix = ' ';
      }
      String nBase;
      char nPrefix;
      if (nAns.length() > 2 && nAns.charAt(1) == '-') {
        nBase = nAns.substring(2, nAns.length());
        nPrefix = Character.toUpperCase(nAns.charAt(0));
      } else {
        nBase = nAns;
        nPrefix = ' ';
      }

      boolean isStartAdjacentSame = base.equals(pBase) &&
              (prefix == 'B' || prefix == 'S' || prefix == 'U' || pPrefix == 'E' || pPrefix == 'S' || pPrefix == 'U');
      boolean isEndAdjacentSame = base.equals(nBase) &&
              (prefix == 'E' || prefix == 'L' || prefix == 'S' || prefix == 'U' || nPrefix == 'B' || nPrefix == 'S' || nPrefix == 'U');
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
            break;
          // nothing to do on case 6 as it's just base
          case 7:
            if (isFirst && isLast) {
              newAnswer = "U-" + base;
            } else if ( ( ! isFirst) && isLast) {
              newAnswer = "L-" + base;
            } else if (isFirst && ( ! isLast)) {
              newAnswer = "B-" + base;
            } else {
              newAnswer = "I-" + base;
            }
        }
      }
      if (intern) {
        newAnswer = newAnswer.intern();
      }
      newAnswers[i] = newAnswer;
    }
    for (int i = 0; i < size; i++) {
      TOK c = tokens.get(i);
      c.set(CoreAnnotations.AnswerAnnotation.class, newAnswers[i]);
    }
  }

  /** For a sequence labeling task with multi-token entities, like NER,
   *  this works out TP, FN, FP counts that can be used for entity-level
   *  F1 results. This works with any kind of prefixed IOB labeling, or
   *  just with simply entity names (also treated as IO labeling).
   *
   * @param doc The document (with Answer and GoldAnswer annotations) to score
   * @param entityTP Counter from entity type to count of true positives
   * @param entityFP Counter from entity type to count of false positives
   * @param entityFN Counter from entity type to count of false negatives
   * @param background The background symbol. Normally it isn't counted in entity-level
   *                   F1 scores. If you want it counted, pass in null for this.
   * @return Whether scoring was successful (it'll only be unsuccessful if information
   *         is missing or ill-formed in the doc).
   */
  public static boolean countEntityResults(List<? extends CoreMap> doc,
                                         Counter<String> entityTP,
                                         Counter<String> entityFP,
                                         Counter<String> entityFN,
                                         String background) {
    boolean entityCorrect = true;
    // the annotations
    String previousGold = background;
    String previousGuess = background;
    // the part after the I- or B- in the annotation
    String previousGoldEntity = "";
    String previousGuessEntity = "";
    char previousGoldPrefix = ' ';
    char previousGuessPrefix = ' ';

    for (CoreMap word : doc) {
      String gold = word.get(CoreAnnotations.GoldAnswerAnnotation.class);
      String guess = word.get(CoreAnnotations.AnswerAnnotation.class);
      String goldEntity;
      String guessEntity;
      char goldPrefix;
      char guessPrefix;
      if (gold == null || gold.isEmpty()) {
        System.err.println("Missing gold entity");
        return false;
      } else if (gold.length() > 2 && gold.charAt(1) == '-') {
        goldEntity = gold.substring(2, gold.length());
        goldPrefix = Character.toUpperCase(gold.charAt(0));
      } else {
        goldEntity = gold;
        goldPrefix = ' ';
      }
      if (guess == null || guess.isEmpty()) {
        System.err.println("Missing guess entity");
        return false;
      } else if (guess.length() > 2 && guess.charAt(1) == '-') {
        guessEntity = guess.substring(2, guess.length());
        guessPrefix = Character.toUpperCase(guess.charAt(0));
      } else {
        guessEntity = guess;
        guessPrefix = ' ';
      }

      //System.out.println("Gold: " + gold + " (" + goldPrefix + ' ' + goldEntity + "); " +
      //        "Guess: " + guess + " (" + guessPrefix + ' ' + guessEntity + ')');

      boolean goldIsStartAdjacentSame = goldEntity.equals(previousGoldEntity) &&
              (goldPrefix == 'B' || goldPrefix == 'S' || goldPrefix == 'U' || previousGoldPrefix == 'E' || previousGoldPrefix == 'S' || previousGoldPrefix == 'S');
      boolean newGold = ! gold.equals(background) &&
              ( ! goldEntity.equals(previousGoldEntity) || goldIsStartAdjacentSame);
      boolean guessIsStartAdjacentSame = guessEntity.equals(previousGuessEntity) &&
              (guessPrefix == 'B' || guessPrefix == 'S' || guessPrefix == 'U' || previousGuessPrefix == 'E' || previousGuessPrefix == 'L' || previousGuessPrefix == 'S' || previousGuessPrefix == 'U');
      boolean newGuess = ! guess.equals(background) &&
              ( ! guessEntity.equals(previousGuessEntity) || guessIsStartAdjacentSame);

      boolean goldEnded = ! previousGold.equals(background) &&
              ( ! goldEntity.equals(previousGoldEntity) || goldIsStartAdjacentSame);
      boolean guessEnded = ! previousGuess.equals(background) &&
              ( ! guessEntity.equals(previousGuessEntity) || guessIsStartAdjacentSame);

      // System.out.println("  newGold " + newGold + "; newGuess " + newGuess +
      //        "; goldEnded:" + goldEnded + "; guessEnded: " + guessEnded);

      if (goldEnded && ! guessEnded) {
        entityFN.incrementCount(previousGoldEntity);
        entityCorrect = gold.equals(background) && guess.equals(background);
      }
      if (goldEnded && guessEnded) {
        if (entityCorrect) {
          entityTP.incrementCount(previousGoldEntity);
        } else {
          // same span but wrong label
          entityFN.incrementCount(previousGoldEntity);
          entityFP.incrementCount(previousGuessEntity);
        }
        entityCorrect = goldEntity.equals(guessEntity);
      }
      if (! goldEnded && guessEnded) {
        entityCorrect = false;
        entityFP.incrementCount(previousGuessEntity);
      }
      // nothing to do if neither gold nor guess have ended

      if (newGold && ! newGuess) {
        entityCorrect = false;
      }
      if (newGold && newGuess) {
        entityCorrect = guessEntity.equals(goldEntity);
      }
      if ( ! newGold && newGuess) {
        entityCorrect = false;
      }

      previousGold = gold;
      previousGuess = guess;
      previousGoldEntity = goldEntity;
      previousGuessEntity = guessEntity;
    }

    // At the end, we need to check the last entity
    if ( ! previousGold.equals(background)) {
      if (entityCorrect) {
        entityTP.incrementCount(previousGoldEntity);
      } else {
        entityFN.incrementCount(previousGoldEntity);
      }
    }
    if ( ! previousGuess.equals(background)) {
      if ( ! entityCorrect) {
        entityFP.incrementCount(previousGuessEntity);
      }
    }

    return true;
  }



  /** Converts entity representation of a file. */
  public static void main(String[] args) {
    // todo!
    if (args.length == 0) {

    } else {
      for (String arg : args) {

      }
    }
  }

}
