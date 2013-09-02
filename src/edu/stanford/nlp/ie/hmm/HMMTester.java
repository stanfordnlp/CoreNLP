package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.AnswerChecker;
import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.stats.PrecisionRecallStats;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.*;


/**
 * Programmatically tests the quality of an HMM on a Corpus. To simply get F1
 * on a corpus, call {@link #test}. After a call to <tt>test</tt>, more
 * fine-grained stats can be retrieved via {@link #getAggregateStats}
 * and {@link #getTargetFieldStats}.
 *
 * @author Huy Nguyen (htnguyen@stanford.edu):wq
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class HMMTester {
  private static final int PRINT_NUM_SEQUENCES = 0;
  private static final int PRINT_TYPED_WORDS = 1;

  private HMM hmm; // HMM to test
  private HashMap<String,Integer> targetIndexMap; // a map from Target name (String) ->
  // Index within targetField array (Integer) in test Corpus.  Used to
  // convert the integer type values given by the HMM to match those in
  // the Corpus
  String[] hmmTargetFields; // the targetField array for the HMM (the
  // index of the target in the array is the type of that target)

  // stored after a call to test to get more detailed info
  String[] corpusTargetFields; // types in statsByType are corpus types
  HashMap<Integer,PrecisionRecallStats> statsByType; // Integer (type) -> PrecisionRecallStats across all docs for this type
  PrecisionRecallStats aggregateStats; // stats aggregated over all fields and docs

  /**
   * Constructs a new tester for the given HMM.
   */
  public HMMTester(HMM hmm) {
    this.hmm = hmm;
    hmmTargetFields = hmm.getTargetFields();
    corpusTargetFields = null;
    statsByType = null;
    aggregateStats = null;
  }

  /**
   * Returns {@link #test(edu.stanford.nlp.ie.Corpus,Properties,boolean,boolean,boolean) test(testDocs,null,false,false,false)}.
   */
  public double test(Corpus testDocs) {
    return (test(testDocs, null, false, false, false));
  }

  /**
   * Returns {@link #test(edu.stanford.nlp.ie.Corpus,Properties,boolean,boolean,boolean) test(testDocs,null,false,false,verbose)}.
   */
  public double test(Corpus testDocs, boolean verbose) {
    return (test(testDocs, null, false, false, verbose));
  }

  /**
   * Returns the F1 measure for the HMM on the given corpus. Prints a bunch
   * of debug info to stderr if <tt>verbose</tt> is true. If <tt>bestAnswerOnly</tt>
   * is true, only the highest probability answer for each type is considered,
   * otherwise all generated answers are considered. If <tt>useFirstAnswer</tt>
   * is true, the earliest match for each field is returned, otherwise the
   * highest-prob field is returned. After calling this method, call
   * {@link #getAggregateStats} or {@link #getTargetFieldStats} to get
   * more fine-grained statistics.
   */
  public double test(Corpus testDocs, Properties props, boolean bestAnswerOnly, boolean useFirstAnswer, boolean verbose) {
    if (props == null) {
      props = Extractor.getDefaultProperties();
    }
    int printMode = ("num_sequences".equals(props.getProperty("viterbiPrintMode"))) ? PRINT_NUM_SEQUENCES : PRINT_TYPED_WORDS;

    // initialize the targetIndexMap
    targetIndexMap = new HashMap<String,Integer>();
    corpusTargetFields = testDocs.getTargetFields();
    for (int i = 0; i < corpusTargetFields.length; i++) {
      targetIndexMap.put(corpusTargetFields[i], Integer.valueOf(i));
    }

    statsByType = new HashMap<Integer,PrecisionRecallStats>(); // Integer (type) -> PrecisionRecallStats across all docs for this type
    aggregateStats = new PrecisionRecallStats();
    for (Iterator iter = testDocs.iterator(); iter.hasNext();) {
      TypedTaggedDocument doc = (TypedTaggedDocument) iter.next(); // current doc to test
      AnswerChecker ac = new AnswerChecker(doc);
      if (verbose) {
        System.err.println("Document: " + doc.presentableText());
        System.err.println("True answers in document:");
        ac.printAnswers();
      }

      Map<Integer,PrecisionRecallStats> stats; // precision/recall stats for this document (by type)
      int[] bestStateSequence = hmm.viterbiSequence(doc);
      int[] guessedTypeSequence = hmm.getLabelsForSequence(bestStateSequence);
      // convert types given by HMM to the corresponding Corpus types

      for (int i = 0; i < guessedTypeSequence.length; i++) {
        Integer corpusType = null;
        // guessedTypeSequence[i] could be negative for special state
        if (guessedTypeSequence[i] >= 0) {
          corpusType = targetIndexMap.get(hmmTargetFields[guessedTypeSequence[i]]);
        }
        if (corpusType != null) {
          // if the targetField is recognized by the corpus,
          // replace with the corpus index
          guessedTypeSequence[i] = corpusType.intValue();
        } else {
          // else we say it's emitted by a background state
          // (type 0)
          guessedTypeSequence[i] = 0;
        }
      }

      if (verbose) {
        System.err.println("Guessed answers:");
        ac.printAnswerWordSequences(guessedTypeSequence);
      }

      if (bestAnswerOnly) {
        // change the types to match the corpus type
        HashMap<Integer, List<String>> bestAnswers = useFirstAnswer ? hmm.firstAnswers(doc, bestStateSequence) : hmm.bestAnswers(doc, bestStateSequence);
        HashMap<Integer, List<String>> convertedBestAnswers = new HashMap<Integer, List<String>>();
        for (Integer hmmType : bestAnswers.keySet()) {
          Integer corpusType = targetIndexMap.get(hmmTargetFields[hmmType.intValue()]);
          if (corpusType != null) {
            convertedBestAnswers.put(corpusType, bestAnswers.get(hmmType));
          }
        }
        bestAnswers = convertedBestAnswers;

        if (verbose) {
          System.err.println("Best answers: " + bestAnswers);
        }
        stats = ac.checkBestAnswers(bestAnswers); // assumes slot-filling and only uses best guess for each type
      } else {
        stats = ac.checkAnswers(guessedTypeSequence, false); // using slot-filling score -- you only need to extract each unique answer once to get credit for all instances in doc
      }
      if (verbose) {
        int[] correctTypeSequence = doc.getTypeSequence();

        // Presents the results of viterbi decoding by displaying each
        // document, and appending to each word the correct type
        // guessed type (if different from correct type), and generating state
        // in the following format:
        // <word> (<correct type> [/ <guessed type>]:<state>
        if (printMode == PRINT_TYPED_WORDS) {
          for (int i = 0; i < doc.size(); i++) {
            StringBuilder buffer = new StringBuilder();
            buffer.append(((TypedTaggedWord) doc.get(i)).word());
            buffer.append(" (");
            buffer.append(correctTypeSequence[i]);
            if (correctTypeSequence[i] != guessedTypeSequence[i]) {
              buffer.append("/");
              buffer.append(guessedTypeSequence[i]);
            }
            buffer.append(":");
            // state sequence is offset by 1 because of start state
            buffer.append(bestStateSequence[i + 1]);
            buffer.append(") ");
            System.err.print(buffer.toString());
          }
          System.err.println();
        } else {
          // Presents the results of the viterbi decoding as separate
          // number sequence for correct types, guessed types, and
          // generating state
          System.err.print("Correct type sequence:");
          printTypeSequence(correctTypeSequence, corpusTargetFields.length);
          System.err.print("Guessed type sequence:");
          printTypeSequence(guessedTypeSequence, corpusTargetFields.length);
          System.err.print("Guessed state sequence:");
          printTypeSequence(bestStateSequence, corpusTargetFields.length);
        }
        // evaluate per-token accuracy
        if (correctTypeSequence.length != guessedTypeSequence.length) {
          System.err.println("Error: type sequence length mismatch");
        }
        int correct = 0;
        for (int i = 0; i < correctTypeSequence.length; i++) {
          if (guessedTypeSequence[i] == correctTypeSequence[i]) {
            correct++;
          }
        }
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(2);
        System.err.println("Per-token accuracy: " + correct + "/" + correctTypeSequence.length + " (" + nf.format(((double) correct) / correctTypeSequence.length) + ")");
        System.err.println();
        System.err.println("Document stats (by type): " + stats); // displays tp, fp, fn counts for each type in this document
        System.err.println();
      }
      for (Integer type : stats.keySet()) {
        PrecisionRecallStats prs = stats.get(type);

        // aggregates stats for each type across all docs
        PrecisionRecallStats typePRS = statsByType.get(type);
        if (typePRS == null) {
          typePRS = new PrecisionRecallStats();
        }
        typePRS.addCounts(prs);
        statsByType.put(type, typePRS);

        // aggregates stats across every type and every doc
        aggregateStats.addCounts(prs);
      }
    }

    if (verbose) {
      if (printMode == PRINT_TYPED_WORDS) {
        System.err.println("OUTPUT FORMAT: <word> (<correct type> [/ <guessed type>]:<state>");
      }

      System.err.println("Aggregate stats by type:");
      for (Integer type : statsByType.keySet()) {
        PrecisionRecallStats prs = statsByType.get(type);
        String targetName = corpusTargetFields[type.intValue()];
        System.err.println();
        System.err.println("Precision on " + targetName + ": " + prs.getPrecisionDescription(2));
        System.err.println("Recall on " + targetName + ": " + prs.getRecallDescription(2));
        System.err.println("F1 on " + targetName + ": " + prs.getF1Description(2));
      }
      System.err.println();
      //}{//TAKE THIS LINE OUT
      // prints overall aggregate stats
      System.err.println("Overall aggregate stats: " + aggregateStats);
      System.err.println("Overall Precision: " + aggregateStats.getPrecision());
      System.err.println("Overall Recall: " + aggregateStats.getRecall());
      System.err.println("Overall F1: " + aggregateStats.getFMeasure());
    }

    // returns F1 as a single figure of merit for this test
    return (aggregateStats.getFMeasure());
  }

  /**
   * Returns the aggregate stats over all fields and docs from the last call
   * to {@link #test(edu.stanford.nlp.ie.Corpus,Properties,boolean,boolean,boolean) test}. Returns <tt>null</tt>
   * if test has not yet been called. Subsequent calls to <tt>test</tt>
   * will overwrite the state.
   */
  public PrecisionRecallStats getAggregateStats() {
    return (aggregateStats);
  }

  /**
   * Returns the stats for the given field over all docs from the last call
   * to {@link #test(edu.stanford.nlp.ie.Corpus,Properties,boolean,boolean,boolean) test}. Returns <tt>null</tt>
   * if test has not yet been called. Subsequent calls to <tt>test</tt>
   * will overwrite the state.
   */
  public PrecisionRecallStats getTargetFieldStats(String targetField) {
    if (corpusTargetFields == null) {
      return (null); // call test first
    }
    for (int i = 0; i < corpusTargetFields.length; i++) {
      if (corpusTargetFields[i].equals(targetField)) {
        return statsByType.get(Integer.valueOf(i));
      }
    }
    return null;
  }

  public static void printTypeSequence(int[] typeSeq, int numTypes) {
    for (int i = 0; i < typeSeq.length; i++) {
      System.err.print(" ");
      if (numTypes >= 10 && typeSeq[i] < 10) {
        System.err.print(" ");
      }
      System.err.print(typeSeq[i]);
    }
    System.err.println();
  }


  public static void main(String[] args) {
    int curArg = 0; // index of current argument
    if (args.length < 3) {
      dieUsage();
      return;
    }

    String hmmFile = args[curArg++];
    System.err.println("Input HMM : " + hmmFile);
    String testFile = args[curArg++];

    int remainnum = args.length - curArg;
    String[] targetFields = new String[remainnum];
    for (int i = curArg; i < args.length; i++) {
      targetFields[i - curArg] = args[i];
    }

    try {

      FileInputStream fis = new FileInputStream(hmmFile);
      ObjectInputStream in = new ObjectInputStream(fis);
      HMM hmm = (HMM) in.readObject();
      in.close();
      HMMTester hmmtest = new HMMTester(hmm);
      Corpus c;
      c = new Corpus(testFile, targetFields);
      hmmtest.test(c);
    } catch (IOException ex) {
      ex.printStackTrace();
    } catch (Exception e) {
      System.err.println(e.getMessage());
      e.printStackTrace();
    }

  }

  private static void dieUsage() {
    System.err.println("Usage: java edu.stanford.nlp.ie.hmm." + "HMMTester HMMFile TestFile field1 ...");

  }

}
