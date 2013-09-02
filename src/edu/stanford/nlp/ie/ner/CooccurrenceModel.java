package edu.stanford.nlp.ie.ner;

import edu.stanford.nlp.stats.ClassicCounter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a joint probability distribution over pairwise ID co-occurrences.
 *
 * @author Huy Nguyen (<a href="mailto:htnguyen@cs.stanford.edu">htnguyen@cs.stanford.edu</a>)
 */
public class CooccurrenceModel {
  private ClassicCounter<UnorderedPair> pairwiseJointModel;

  /**
   * Returns the joint probability P(id1, id2)
   */
  public double get(String id1, String id2) {
    return pairwiseJointModel.getCount(new UnorderedPair(id1, id2));
  }

  /**
   * Initializes the pairwise joint model with the counts of pairwise ID occurrences within
   * each document in the training file.
   *
   * @param file - The name of the training file used to obtain the co-occurrence counts.  This
   *             file should contain one file-ID pair per line, separated by a tab.
   */
  public void initialize(String file) {
    ClassicCounter<UnorderedPair> counter = new ClassicCounter<UnorderedPair>();
    List<String> ids = new ArrayList<String>();
    String line;
    String curDoc = null;
    try {
      // first pass, gather all gene names
      BufferedReader br = new BufferedReader(new FileReader(file));
      while ((line = br.readLine()) != null) {
        String[] cols = line.split("\t");
        if (cols.length >= 2) {
          if (!cols[0].equals(curDoc)) {
            curDoc = cols[0];
            incrementAllPairs(counter, ids);
            ids = new ArrayList<String>();

          }
          ids.add(cols[1]);
        }
      }
      incrementAllPairs(counter, ids);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    //pairwiseJointModel = Counters.goodTuringSmoothedCounter(counter,
    //numIDs * numIDs);
    pairwiseJointModel = counter;
  }

  /**
   * Increments the count of all pairs within the list of ids.  Including self pairs.
   */
  private void incrementAllPairs(ClassicCounter<UnorderedPair> counter, List<String> ids) {
    for (int i = 0; i < ids.size(); i++) {
      for (int j = i; j < ids.size(); j++) {
        counter.incrementCount(new UnorderedPair(ids.get(i), ids.get(j)));
      }
    }
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    CooccurrenceModel cm = new CooccurrenceModel();
    ClassicCounter<UnorderedPair> counter = new ClassicCounter<UnorderedPair>();
    List<String> list = Arrays.asList(new String[]{"test1", "test2", "test3"});
    cm.incrementAllPairs(counter, list);
    list = Arrays.asList(new String[]{"test1", "test2"});
    cm.incrementAllPairs(counter, list);
    System.err.println(counter.getCount(new UnorderedPair("test1", "test2")));
    System.err.println(counter.getCount(new UnorderedPair("test2", "test3")));
    System.err.println(counter.getCount(new UnorderedPair("test2", "test2")));
  }

  private static class UnorderedPair {
    String s1;
    String s2;
    int hashCode;

    public UnorderedPair(String s1, String s2) {
      if (s1.compareTo(s2) < 0) {
        this.s1 = s1;
        this.s2 = s2;
      } else {
        this.s2 = s1;
        this.s1 = s2;
      }
      hashCode = (s1 + s2).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      UnorderedPair other = (UnorderedPair) obj;
      return (s1.equals(other.s1) && s2.equals(other.s2));
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }
}
