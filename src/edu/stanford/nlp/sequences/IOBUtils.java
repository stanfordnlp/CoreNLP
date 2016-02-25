package edu.stanford.nlp.sequences; 
import edu.stanford.nlp.util.logging.Redwood;

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
public class IOBUtils  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(IOBUtils.class);

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

      boolean isStartAdjacentSame = isSameEntityBoundary(pBase, pPrefix, base, prefix);
      boolean isEndAdjacentSame = isSameEntityBoundary(base, prefix, nBase, nPrefix);
      boolean isFirst = isDifferentEntityBoundary(pBase, base) || isStartAdjacentSame;
      boolean isLast = isDifferentEntityBoundary(base, nBase) || isEndAdjacentSame;
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

  public static boolean isEntityBoundary(String beforeEntity, char beforePrefix, String afterEntity, char afterPrefix) {
    return ! beforeEntity.equals(afterEntity) ||
            afterPrefix == 'B' || afterPrefix == 'S' || afterPrefix == 'U' ||
            beforePrefix == 'E' || beforePrefix == 'L' || beforePrefix == 'S' || beforePrefix == 'U';

  }

  public static boolean isSameEntityBoundary(String beforeEntity, char beforePrefix, String afterEntity, char afterPrefix) {
    return beforeEntity.equals(afterEntity) &&
            (afterPrefix == 'B' || afterPrefix == 'S' || afterPrefix == 'U' ||
            beforePrefix == 'E' || beforePrefix == 'L' || beforePrefix == 'S' || beforePrefix == 'U');

  }

  public static boolean isDifferentEntityBoundary(String beforeEntity, String afterEntity) {
    return  ! beforeEntity.equals(afterEntity);
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
        log.info("Missing gold entity");
        return false;
      } else if (gold.length() > 2 && gold.charAt(1) == '-') {
        goldEntity = gold.substring(2, gold.length());
        goldPrefix = Character.toUpperCase(gold.charAt(0));
      } else {
        goldEntity = gold;
        goldPrefix = ' ';
      }
      if (guess == null || guess.isEmpty()) {
        log.info("Missing guess entity");
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

      boolean newGold = ! gold.equals(background) && isEntityBoundary(previousGoldEntity, previousGoldPrefix, goldEntity, goldPrefix);
      boolean newGuess = ! guess.equals(background) && isEntityBoundary(previousGuessEntity, previousGuessPrefix, guessEntity, guessPrefix);

      boolean goldEnded = ! previousGold.equals(background) && isEntityBoundary(previousGoldEntity, previousGoldPrefix, goldEntity, goldPrefix);
      boolean guessEnded = ! previousGuess.equals(background) && isEntityBoundary(previousGuessEntity, previousGuessPrefix, guessEntity, guessPrefix);

      // System.out.println("  newGold " + newGold + "; newGuess " + newGuess +
      //        "; goldEnded:" + goldEnded + "; guessEnded: " + guessEnded);

      if (goldEnded) {
        if (guessEnded) {
          if (entityCorrect) {
            entityTP.incrementCount(previousGoldEntity);
          } else {
            // same span but wrong label
            entityFN.incrementCount(previousGoldEntity);
            entityFP.incrementCount(previousGuessEntity);
          }
          entityCorrect = goldEntity.equals(guessEntity);
        } else {
          entityFN.incrementCount(previousGoldEntity);
          entityCorrect = gold.equals(background) && guess.equals(background);
        }
      } else if (guessEnded) {
        entityCorrect = false;
        entityFP.incrementCount(previousGuessEntity);
      }
      // nothing to do if neither gold nor guess have ended (a category change signals an end)

      if (newGold) {
        if (newGuess) {
          entityCorrect = guessEntity.equals(goldEntity);
        } else {
          entityCorrect = false;
        }
      } else if (newGuess) {
        entityCorrect = false;
      }

      previousGold = gold;
      previousGuess = guess;
      previousGoldEntity = goldEntity;
      previousGuessEntity = guessEntity;
      previousGoldPrefix = goldPrefix;
      previousGuessPrefix = guessPrefix;
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
