package edu.stanford.nlp.ie;

import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.stats.PrecisionRecallStats;

import java.util.*;

/**
 * Utility class for comparing the answers in a TypedTaggedDocument to those
 * extracted by an HMM. Scoring is either in a strict mode (in which every
 * proposed answer is compared to every real asnwer) or in a slot-filling
 * mode in which the best answer for each field type is checked for in the
 * list of correct answers.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu) [based on code by Jim McFadden]
 */
public class AnswerChecker {
  /**
   * Document with the real answers that gets checked against
   */
  private TypedTaggedDocument<Integer> source;
  /**
   * Map of answer ranges from the source document's answers: Integer (type) -> List of Ranges
   */
  private Map<Integer,List<Range>> answerRangesByType;

  /**
   * Constructs a new AnswerChecker to check answers for the given TypedTaggedDocument.
   * The list of answer types is taken from {@link edu.stanford.nlp.ie.TypedTaggedDocument#getTypeSequence
   * source.getTypeSequence()}.
   *
   * @param source Document over which the AnswerChecker is built
   */
  public AnswerChecker(TypedTaggedDocument<Integer> source) {
    this.source = source;
    answerRangesByType = getAnswerRanges(source.getTypeSequence());
  }

  /**
   * Takes the given list of answer types and converts them into a map from
   * type to a list of Ranges (start/end) indices. The Ranges within each
   * state type are guaranteed to be in linear order. No ranges overlap.
   * All answer types (keys) present in the returned map will have at least
   * one range in the value list.
   *
   * @param answers An array of answer types
   * @return A map from type to a list of Ranges (start/end) indices
   */
  public static HashMap<Integer,List<Range>> getAnswerRanges(int[] answers) {
    HashMap<Integer,List<Range>> allRanges = new HashMap<Integer,List<Range>>(); // Integer (state) -> List of Ranges
    int currentType = 0; // type of last state (assumes we start in background state)
    int currentStart = -1; // start of current range, or -1 if not in a current range
    for (int i = 0; i < answers.length; i++) {
      if (answers[i] != currentType) {
        // changed states -> either started or ended a range
        if (currentStart == -1) {
          currentStart = i; // mark start index
        } else {
          // add a new range to the list for this
          List<Range> ranges = allRanges.get(Integer.valueOf(currentType));
          if (ranges == null) {
            ranges = new ArrayList<Range>();
          }
          ranges.add(new Range(currentStart, i));
          allRanges.put(Integer.valueOf(currentType), ranges);

          // either resets the current start or marks the current location
          currentStart = (answers[i] == 0 ? -1 : i);
        }
      }
      currentType = answers[i]; // remember last state
    }
    // catch final range in case it doesn't end in background state
    if (currentStart != -1) {
      // add a new range to the list for this
      List<Range> ranges = allRanges.get(Integer.valueOf(currentType));
      if (ranges == null) {
        ranges = new ArrayList<Range>();
      }
      ranges.add(new Range(currentStart, answers.length));
      allRanges.put(Integer.valueOf(currentType), ranges);
    }
    return allRanges;
  }

  /**
   * Takes the given map from types to lists of answer ranges and converts it
   * to a map from types to Sets of the unique answer word lists for that type.
   * Each word list is a List of Strings for each word in the answer.
   * If <tt>ignoreCase</tt> is true, all strings are converted to lower case.
   */
  public Map<Integer,Set<List<String>>> getWordSequences(Map<Integer,List<Range>> answerRanges, boolean ignoreCase) {
    HashMap<Integer,Set<List<String>>> wordSequencesByType = new HashMap<Integer,Set<List<String>>>();
    for (Integer type : answerRanges.keySet()) {
      // pulls out all unique answer word sequences for this type
      List<Range> ranges = answerRanges.get(type);
      Set<List<String>> wordSequences = new HashSet<List<String>>();
      for (Range r : ranges) {
        // pulls out all words from source document in this range
        List<String> wordSequence = new ArrayList<String>(r.getTo() - r.getFrom());
        for (int i = r.getFrom(); i < r.getTo(); i++) {
          String word = source.get(i).word();
          wordSequence.add(ignoreCase ? word.toLowerCase() : word);
        }
        wordSequences.add(wordSequence);
      }
      wordSequencesByType.put(type, wordSequences);
    }
    return wordSequencesByType;
  }

  /**
   * Calls <tt>getWordSequences</tt> using the true answers in the source doc.
   */
  public Map<Integer,Set<List<String>>> getAnswerWordSequences(boolean ignoreCase) {
    return getWordSequences(answerRangesByType, ignoreCase);
  }

  /**
   * Takes a Map from Integer (type) -> List of Strings for best guess word sequence
   * and returns a Map from type to PrecisionRecallStats for that state type.
   * For each type, if the best answer is among the correct answers, you get full credit
   * for the type. Otherwise you get a false positive for guessing a bad answer and
   * a false negative for not guessing the right answer. You also get a false
   * negative for each answer type you didn't guess at.
   */
  public Map<Integer,PrecisionRecallStats> checkBestAnswers(Map<Integer,List<String>> bestGuessWordSequenceByType) {
    HashMap<Integer,PrecisionRecallStats> statsByType = new HashMap<Integer,PrecisionRecallStats>();
    Map<Integer,Set<List<String>>> answerWordSequencesByType = getWordSequences(answerRangesByType, false); // CHANGE TO TRUE TO IGNORE CASE
    for (Integer type : bestGuessWordSequenceByType.keySet()) {
      PrecisionRecallStats stats = new PrecisionRecallStats();
      List<String> bestGuessWordSequence = bestGuessWordSequenceByType.get(type);
      // converts the best guess words to lowercase since the answers are stored that way
      List<String> lcBestGuessWordSequence = new ArrayList<String>(bestGuessWordSequence.size());
      for (String str : bestGuessWordSequence) {
        lcBestGuessWordSequence.add(str); // ADD .toLowerCase() TO IGNORE CASE
      }
      // looks in the set of answers for the best guess
      Set<List<String>> answerWordSequences = answerWordSequencesByType.get(type);
      if (answerWordSequences == null) {
        stats.incrementFP(); // no answers of this type -> best guess is a false positive
      } else {
        // NOTE: CONTAINS SEEMS TO BE TRUE 4 MORE TIMES WITH CASE THAN WITHOUT CASE -- FIX
        if (answerWordSequences.contains(lcBestGuessWordSequence)) {
          stats.incrementTP(); // this answer exists somewhere -> tp
        } else {
          stats.incrementFP(); // this answer doesn't exist anywhere -> fp
          stats.incrementFN(); // failed to find the answer -> fn
        }
      }
      statsByType.put(type, stats);
    }

    // answer types that weren't guessed contain all false negatives by definition
    for (Integer type : answerWordSequencesByType.keySet()) {
      if (bestGuessWordSequenceByType.get(type) == null) {
        // failed to guess the answer for the type -> false negative
        PrecisionRecallStats stats = new PrecisionRecallStats();
        stats.incrementFN();
        statsByType.put(type, stats);
      }
    }

    return statsByType;
  }


  /**
   * Returns a map from Integer (state type) -> PrecisionRecallStats for that
   * state type. <tt>guesses</tt> is a sequence of types (e.g. from {@link edu.stanford.nlp.ie.hmm.HMM#getLabelsForSequence}).
   * If a state type is not in the map, it means no states of that
   * type were among the given given guesses or answers. If <tt>strict</tt> is false,
   * each unique answer need only be extracted once (i.e. slot-filling mode--if
   * the same answer appears in two places and is only extracted once, no false
   * negative is counted). If <tt>strict</tt> is true, every instance of every answer is
   * scored as either a true positive or a false negative.
   */
  public Map<Integer,PrecisionRecallStats> checkAnswers(int[] guesses, boolean strict) {
    if (strict) {
      return checkAnswersStrict(guesses);
    }

    HashMap<Integer,PrecisionRecallStats> statsByType = new HashMap<Integer,PrecisionRecallStats>();
    Map<Integer,Set<List<String>>> guessWordSequencesByType = getWordSequences(getAnswerRanges(guesses), true); // ignores case
    Map<Integer,Set<List<String>>> answerWordSequencesByType = getWordSequences(answerRangesByType, true); // ignores case
    for (Integer type : guessWordSequencesByType.keySet()) {
      PrecisionRecallStats stats = new PrecisionRecallStats(); // stats for this type
      Set<List<String>> guessWordSequences = guessWordSequencesByType.get(type);
      Set<List<String>> answerWordSequences = answerWordSequencesByType.get(type);
      if (answerWordSequences == null) {
        stats.addFP(guessWordSequences.size()); // no answers of this type -> every unique guess is a false positive
      } else {
        for (List<String> guessWordSequence : guessWordSequences) {
          if (answerWordSequences.contains(guessWordSequence)) {
            stats.incrementTP(); // this answer exists somewhere -> tp
          } else {
            stats.incrementFP(); // this answer doesn't exist anywhere -> fp
          }
        }
        stats.addFN(answerWordSequences.size() - stats.getTP()); // rest of unique answers were missed -> false negatives
      }
      statsByType.put(type, stats);
    }

    // answer types that weren't guessed contain all false negatives by definition
    for (Integer type: answerWordSequencesByType.keySet()) {
      if (guessWordSequencesByType.get(type) == null) {
        // entire set of answers is false negatives
        PrecisionRecallStats stats = new PrecisionRecallStats();
        stats.addFN(answerWordSequencesByType.get(type).size());
        statsByType.put(type, stats);
      }
    }

    return statsByType;
  }

  /**
   * Checks answers assuming that every instance of every answer must be matched exactly.
   *
   * @see #checkAnswers(int[],boolean)
   */
  private Map<Integer,PrecisionRecallStats> checkAnswersStrict(int[] guesses) {
    Map<Integer,PrecisionRecallStats> statsByType = new HashMap<Integer,PrecisionRecallStats>();
    Map<Integer,List<Range>> guessRanges = getAnswerRanges(guesses);
    for (Integer type : answerRangesByType.keySet()) {
      PrecisionRecallStats stats = new PrecisionRecallStats(); // stats for this type
      List<Range> aRanges = answerRangesByType.get(type); // answer ranges for this type
      List<Range> gRanges = guessRanges.get(type); // guess ranges for this type
      if (gRanges == null) {
        stats.addFN(aRanges.size()); // no guesses -> every instance is a false negatives
      } else {
        for (Object aRange : aRanges) {
          Range curAnswer = (Range) aRange;

          // this range must be exactly mached to get credit
          boolean found = false; // has this answer been found
          for (Object gRange : gRanges) {
            if (curAnswer.equals(gRange)) {
              // found current answer -> stop looking
              found = true;
              break;
            }
          }
          if (found) {
            stats.incrementTP(); // found answer -> tp
          } else {
            stats.incrementFN(); // failed to generate answer -> fn
          }
        }
        stats.addFP(gRanges.size() - stats.getTP()); // rest of guesses were wrong -> false positives
      }
      statsByType.put(type, stats);
    }

    // guessed types that aren't in answers at all contain all false positives by definition
    for (Integer type : guessRanges.keySet()) {
      if (answerRangesByType.get(type) == null) {
        // entire list is false positives
        PrecisionRecallStats stats = new PrecisionRecallStats();
        stats.addFP(guessRanges.get(type).size());
        statsByType.put(type, stats);
      }
    }

    return statsByType;
  }

  /**
   * Prints answer word sequences for the correct answers for this document.
   *
   * @see #printAnswerWordSequences
   */
  public void printAnswers() {
    printAnswerWordSequences(source.getTypeSequence());
  }

  /**
   * Prints the list of unique answers for each type determined by the given type sequence.
   * Each line is a type, and all the answers are separated by commas.
   * The line is prefixed with the type number.
   */
  public void printAnswerWordSequences(int[] answers) {
    Map<Integer,Set<List<String>>> answerWordSequencesByType = getWordSequences(getAnswerRanges(answers), false);
    for (Integer type : answerWordSequencesByType.keySet()) {
      Set<List<String>> answerWordSequences = answerWordSequencesByType.get(type);
      System.err.print(type + ":");
      boolean first = true; // is this the first element
      for (List<String> answerWordSequence : answerWordSequences) {
        if (!first) {
          System.err.print(",");
        }
        for (String str : answerWordSequence) {
          System.err.print(" " + str);
        }
        first = false;
      }
      System.err.println();
    }
  }

  /**
   * Reprsents a range [from,to) (same semantics as substring).
   */
  public static class Range {
    /**
     * Index of first element
     */
    private int from;
    /**
     * Index after last element
     */
    private int to;

    /**
     * Constructs a new range with the given span.
     */
    public Range(int from, int to) {
      this.from = from;
      this.to = to;
    }

    /**
     * Returns the List of words (Strings) within the given doc represented
     * by this range.
     */
    public List<String> extractRange(Document<? extends HasWord,?,?> doc) {
      List<String> words = new ArrayList<String>(to - from);
      for (int i = from; i < to; i++) {
        words.add(((HasWord) doc.get(i)).word());
      }
      return words;
    }

    /**
     * Returns true iff the given object is a Range with the same from and
     * to as this Range.
     */
    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Range)) {
        return (false);
      }
      Range r = (Range) o;
      return (from == r.getFrom() && to == r.getTo());
    }

    @Override
    public int hashCode() {
      return from << 16 + to;
    }

    /**
     * Returns the index of the first element spanned by this Range.
     */
    public int getFrom() {
      return (from);
    }

    /**
     * Returns the index immediately after the last element spanned by this Range.
     */
    public int getTo() {
      return (to);
    }

    /**
     * Returns a string reprsentation of this Range, indicating the from and to.
     */
    @Override
    public String toString() {
      return ("Range[from=" + from + ",to=" + to + "]");
    }
  }
}
