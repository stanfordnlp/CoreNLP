package edu.stanford.nlp.ie.ner;

import edu.stanford.nlp.stats.PrecisionRecallStats;
import edu.stanford.nlp.util.IntPair;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

/**
 * A class for programmatically scoring the results of NER for the BioCreative task.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class BioCreativeScorer {
  Map<String, List<IntPair>> gsMap;
  Map<String, List<IntPair>> altMap;

  /**
   * Construct a BioCreativeScorer
   *
   * @param gsFile  the Gold Standard file
   * @param altFile the alternate answers file
   */
  public BioCreativeScorer(String gsFile, String altFile) {
    gsMap = new HashMap<String, List<IntPair>>();
    altMap = new HashMap<String, List<IntPair>>();

    loadAnswerFile(gsMap, gsFile);
    loadAnswerFile(altMap, altFile);
  }

  /**
   * Scores a set of answers, and returns the PrecisionRecallStats.
   *
   * @param answers a Map from id (String) -> answers (List of IntPair)
   */
  public PrecisionRecallStats score(Map<String, List<IntPair>> answers) {
    PrecisionRecallStats prStats = new PrecisionRecallStats();

    List<String> keySet = new ArrayList<String>(gsMap.keySet());
    Collections.sort(keySet);
    for (Iterator<String> iter = keySet.iterator(); iter.hasNext();) {
      String id = iter.next();

      List<IntPair> answerList = answers.get(id);
      List<IntPair> goldList = gsMap.get(id);

      // no answers found for this document -> all false negatives
      if (answerList == null) {
        prStats.addFN(goldList.size());
        continue;
      }

      List<IntPair> altList = altMap.get(id);

      for (Iterator<IntPair> iter2 = goldList.iterator(); iter2.hasNext();) {
        IntPair goldAnswer = iter2.next();
        boolean found = false;

        for (int i = answerList.size() - 1; i >= 0; i--) {
          IntPair answer = answerList.get(i);
          if (goldAnswer.equals(answer)) {
            //System.err.println(id + "|" + answer);
            if (!found) {
              prStats.incrementTP();
            }
            found = true;
            continue;
          }
          if (altList == null) {
            continue; // no alternate answers for this doc
          }
          // could speed things up by pre-associating alternatives with gold standard answer, but probably not too important
          for (Iterator<IntPair> iter3 = altList.iterator(); iter3.hasNext();) {
            IntPair altAnswer = iter3.next();

            if ((altAnswer.getSource() >= goldAnswer.getSource() && altAnswer.getTarget() <= goldAnswer.getTarget()) // alternate is a substring of gold standard
                    || (altAnswer.getSource() <= goldAnswer.getSource() && altAnswer.getTarget() >= goldAnswer.getTarget()) // alternate is a superstring of gold standard
                    || (goldAnswer.getSource() <= altAnswer.getSource() && altAnswer.getSource() <= goldAnswer.getTarget() && goldAnswer.getTarget() <= altAnswer.getTarget()) // partial overlap
                    || (altAnswer.getSource() <= goldAnswer.getSource() && goldAnswer.getSource() <= altAnswer.getTarget() && altAnswer.getTarget() <= goldAnswer.getTarget())) // partial overlap
            {
              if (altAnswer.equals(answer)) {
                //System.err.println(id + "|" + altAnswer);
                if (!found) {
                  prStats.incrementTP();
                }
                found = true;
                break;
              }
            }
          }
        }
        if (!found) {
          prStats.incrementFN();
        }
      }
    }

    // check for false positives
    for (Iterator<String> iter = answers.keySet().iterator(); iter.hasNext();) {
      String id = iter.next();

      List<IntPair> answerList = answers.get(id);
      List<IntPair> goldList = gsMap.get(id);

      // no real answers for this document -> all false positives
      if (goldList == null) {
        prStats.addFP(answerList.size());
        continue;
      }
      List<IntPair> altList = altMap.get(id);

      for (Iterator<IntPair> iter2 = answerList.iterator(); iter2.hasNext();) {
        IntPair answer = iter2.next();
        boolean found = false;

        for (int i = goldList.size() - 1; i >= 0; i--) {
          IntPair goldAnswer = goldList.get(i);
          if (goldAnswer.equals(answer)) {
            found = true;
            continue;
          }
          if (altList == null) {
            continue; // no alternate answers for this doc
          }
          // could speed things up by pre-associating alternatives with gold standard answer, but probably not too important
          for (Iterator<IntPair> iter3 = altList.iterator(); iter3.hasNext();) {
            IntPair altAnswer = iter3.next();

            if ((altAnswer.getSource() >= goldAnswer.getSource() && altAnswer.getTarget() <= goldAnswer.getTarget()) // alternate is a substring of gold standard
                    || (altAnswer.getSource() <= goldAnswer.getSource() && altAnswer.getTarget() >= goldAnswer.getTarget()) // alternate is a superstring of gold standard
                    || (goldAnswer.getSource() <= altAnswer.getSource() && altAnswer.getSource() <= goldAnswer.getTarget() && goldAnswer.getTarget() <= altAnswer.getTarget()) // partial overlap
                    || (altAnswer.getSource() <= goldAnswer.getSource() && goldAnswer.getSource() <= altAnswer.getTarget() && altAnswer.getTarget() <= goldAnswer.getTarget())) // partial overlap
            {
              if (altAnswer.equals(answer)) {
                found = true;
                break;
              }
            }
          }
        }
        if (!found) {
          //System.err.println(id+"|"+answer);
          prStats.incrementFP();
        }
      }
    }

    return prStats;
  }

  /**
   * Reads in and parses an answer file.
   */
  private static void loadAnswerFile(Map<String, List<IntPair>> map, String file) {
    if (file == null) {
      return;
    }
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      while ((line = br.readLine()) != null) {
        String[] cols = line.split("\\|");
        if (cols.length < 3) {
          continue;
        }
        // get the id of the data file (format @@id)
        String id = cols[0].substring(2);
        String[] indices = cols[1].split(" ");
        IntPair pair = new IntPair(Integer.parseInt(indices[0]), Integer.parseInt(indices[1]));
        List<IntPair> answers = map.get(id);
        if (answers == null) {
          answers = new ArrayList<IntPair>();
          map.put(id, answers);
        }
        answers.add(pair);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("Usage: BioCreativeScorer Gold.format Test.format Correct.Data");
      System.exit(1);
    }

    Map<String, List<IntPair>> answerMap = new HashMap<String, List<IntPair>>();
    BioCreativeScorer bcs = new BioCreativeScorer(args[0], args[2]);
    loadAnswerFile(answerMap, args[1]);
    System.err.println(bcs.score(answerMap));
  }
}
