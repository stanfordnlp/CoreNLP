package edu.stanford.nlp.ie;

import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.stats.PrecisionRecallStats;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Evaluates the performance of a FieldExtractor on a test Corpus, and reports
 * the precision/recall/f1 stats.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public class FieldExtractorTester {
  /**
   * Should not be instantiated
   */
  private FieldExtractorTester() {
  }

  /**
   * Tests the given extractor on the documents in the testFile by calling
   * {@link #testFieldExtractor(FieldExtractor,String,boolean)
   * testFieldExtractor(extractor,new Corpus(testFile,extractor.getExtractableFields()),verbose)}.
   *
   * @param extractor the extractor to test
   * @param testFile  the file containing the test corpus
   * @param verbose   whether to display verbose output during testing
   * @return a Map from String (fields) -> PrecisionRecallStats for that field.
   */
  public static Map<String, PrecisionRecallStats> testFieldExtractor(FieldExtractor extractor, String testFile, boolean verbose) {
    return (testFieldExtractor(extractor, new Corpus(testFile, extractor.getExtractableFields()), verbose));
  }

  /**
   * Tests the given extractor on the given corpus.  Uses <tt>extractor</tt> to
   * extract the single best answer from the document, and checks to see if
   * that answer is among any of the correct answers.  Since
   * {@link FieldExtractor#extractFields} takes a String argument, uses
   * {@link PTBTokenizer#ptb2Text} to "detokenize" the document before extracting.
   *
   * @param extractor the extractor to test
   * @param testDocs  the test corpus
   * @param verbose   whether to display verbose output during testing
   * @return a Map from String (fields) -> PrecisionRecallStats for that field.
   */
  public static Map<String, PrecisionRecallStats> testFieldExtractor(FieldExtractor extractor, Corpus testDocs, boolean verbose) {
    // String (type) -> PrecisionRecallStats across all docs for this type
    HashMap<String, PrecisionRecallStats> statsByField = new HashMap<String, PrecisionRecallStats>();
    PrecisionRecallStats aggregateStats = new PrecisionRecallStats();

    for (Iterator<TypedTaggedDocument> iter = testDocs.iterator(); iter.hasNext();) {
      TypedTaggedDocument doc = iter.next();
      AnswerChecker ac = new AnswerChecker(doc);

      Map<String, Set<String>> answersByField = collapseAnswerWordSequences(typeKeysToFieldKeys(ac.getAnswerWordSequences(false), testDocs));

      String docText = PTBTokenizer.labelList2Text(doc);
      Map guessedAnswersByField = extractor.extractFields(docText);

      // precision/recall stats for this document (by field)
      Map<String, PrecisionRecallStats> stats = checkAnswers(answersByField, guessedAnswersByField);

      if (verbose) {
        System.err.println("Document: " + docText);
        System.err.println("True answers in document:" + answersByField);
        System.err.println("Guessed answers:" + guessedAnswersByField);
        System.err.println("Document stats: " + stats);
        System.err.println();
      }

      Iterator<String> statsIter = stats.keySet().iterator();
      while (statsIter.hasNext()) {
        String field = statsIter.next();
        PrecisionRecallStats prs = stats.get(field);

        // aggregates stats for each type across all docs
        PrecisionRecallStats fieldPRS = statsByField.get(field);
        if (fieldPRS == null) {
          fieldPRS = new PrecisionRecallStats();
        }
        fieldPRS.addCounts(prs);
        statsByField.put(field, fieldPRS);

        // aggregates stats across every type and every doc
        aggregateStats.addCounts(prs);
      }
    }

    if (verbose) {
      System.err.println("Aggregate stats by field:");
      for (Iterator<String> iter = statsByField.keySet().iterator(); iter.hasNext();) {
        String field = iter.next();
        PrecisionRecallStats prs = statsByField.get(field);
        System.err.println();
        System.err.println("Precision on " + field + ": " + prs.getPrecisionDescription(2));
        System.err.println("Recall on " + field + ": " + prs.getRecallDescription(2));
        System.err.println("F1 on " + field + ": " + prs.getF1Description(2));
      }
      System.err.println();

      // prints overall aggregate stats
      System.err.println("Overall aggregate stats: " + aggregateStats);
      System.err.println("Overall Precision: " + aggregateStats.getPrecision());
      System.err.println("Overall Recall: " + aggregateStats.getRecall());
      System.err.println("Overall F1: " + aggregateStats.getFMeasure());
    }

    // returns F1 as a single figure of merit for this test
    return (statsByField);
  }

  /**
   * Takes a map from Integer (type) -> value and using the targetFields
   * array in Corpus, converts it to a map from String (field) -> value
   */
  public static Map<String, Object> typeKeysToFieldKeys(Map typeIndexedMap, Corpus corpus) {
    HashMap<Integer, String> fieldsByType = new HashMap<Integer, String>();
    String[] corpusTargetFields = corpus.getTargetFields();
    for (int i = 0; i < corpusTargetFields.length; i++) {
      fieldsByType.put(Integer.valueOf(i), corpusTargetFields[i]);
    }
    HashMap<String, Object> fieldIndexedMap = new HashMap<String, Object>();
    for (Iterator iter = typeIndexedMap.keySet().iterator(); iter.hasNext();) {
      Integer type = (Integer) iter.next();
      // index by the field instead of the type
      fieldIndexedMap.put(fieldsByType.get(type), typeIndexedMap.get(type));
    }
    return (fieldIndexedMap);
  }

  /**
   * Uses {@link PTBTokenizer#ptb2Text} to collapse <tt>answers</tt>, a Map from
   * Integer types to (Sets of Lists of Words) to a Map from Integers to Sets of
   * Strings.
   */
  public static Map<String, Set<String>> collapseAnswerWordSequences(Map<String,Object> answers) {
    HashMap<String, Set<String>> collapsedAnswers = new HashMap<String, Set<String>>();
    for (String type : answers.keySet()) {
      Set<String> answerStrings = new HashSet<String>();

      // convert word sequences to Strings
      Set answerSequences = (Set) answers.get(type);
      for (Iterator iter2 = answerSequences.iterator(); iter2.hasNext();) {
        answerStrings.add(PTBTokenizer.labelList2Text((List) iter2.next()));
      }
      collapsedAnswers.put(type, answerStrings);
    }
    return (collapsedAnswers);
  }

  /**
   * Takes a Map from String (field) -> List of Strings for the correct answers
   * and a Map from String (field) -> String for the best answer
   * and returns a Map from type to PrecisionRecallStats for that state type.
   * For each type, if the best answer is among the correct answers, you get full credit
   * for the type. Otherwise you get a false positive for guessing a bad answer and
   * a false negative for not guessing the right answer. You also get a false
   * negative for each answer type you didn't guess at.
   */
  public static Map<String, PrecisionRecallStats> checkAnswers(Map<String, Set<String>> answersByField, Map guessedAnswersByField) {
    HashMap<String, PrecisionRecallStats> statsByField = new HashMap<String, PrecisionRecallStats>();
    for (Iterator<String> iter = answersByField.keySet().iterator(); iter.hasNext();) {
      PrecisionRecallStats stats = new PrecisionRecallStats();
      String field = iter.next();
      String guessedAnswer = (String) guessedAnswersByField.get(field);

      // looks in the set of answers for the best guess
      Set answerStrings = answersByField.get(field);
      // no answers of this type -> best guess is a false positive
      if (answerStrings == null && guessedAnswer.length() > 0) {
        stats.incrementFP();
      } else {
        if (answerStrings.contains(guessedAnswer)) {
          stats.incrementTP(); // this answer exists somewhere -> tp
        } else {
          stats.incrementFP(); // this answer doesn't exist anywhere -> fp
          stats.incrementFN(); // failed to find the answer -> fn
        }
      }
      statsByField.put(field, stats);
    }
    return (statsByField);
  }


  /**
   * Test or dump information about a {@link FieldExtractor}. <p>
   * Usage: <code>java edu.stanford.nlp.ie.FieldExtractorTester
   * serializedFieldExtractor [testFile]
   */
  public static void main(String[] args) {
    if (args.length < 1 || args.length > 2) {
      System.err.println("Usage: FieldExtractorTester serializedFieldExtractor [testFile");
      System.exit(1);
    }
    FieldExtractor extractor = null;
    try {
      ObjectInputStream in = new ObjectInputStream(new FileInputStream(args[0]));
      extractor = (FieldExtractor) in.readObject();
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (args.length == 1) {
      System.out.println(extractor);
    } else {
      FieldExtractorTester.testFieldExtractor(extractor, args[1], true);
    }
  }

}
