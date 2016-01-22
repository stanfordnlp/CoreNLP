package edu.stanford.nlp.classify;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.io.File;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;


import edu.stanford.nlp.util.logging.Redwood;

/**
 * A class to read some UCI datasets into RVFDatum. Will incrementally add formats.
 *
 * @author Kristina Toutanova
 *         Sep 14, 2004
 *
 * Made type-safe by Sarah Spikes (sdspikes@cs.stanford.edu)
 */
public class NominalDataReader {
  Map<String, Index<String>> indices = Generics.newHashMap(); // an Index for each feature so that its values are coded as integers

  final static Redwood.RedwoodChannels logger = Redwood.channels(NominalDataReader.class);

  /**
   * the class is the last column and it skips the next-to-last column because it is a unique id in the audiology data
   *
   */
  static RVFDatum<String, Integer> readDatum(String line, String separator, Map<Integer, Index<String>> indices) {
    StringTokenizer st = new StringTokenizer(line, separator);
    //int fno = 0;
    ArrayList<String> tokens = new ArrayList<>();
    while (st.hasMoreTokens()) {
      String token = st.nextToken();
      tokens.add(token);
    }
    String[] arr = tokens.toArray(new String[tokens.size()]);
    Set<Integer> skip = Generics.newHashSet();
    skip.add(Integer.valueOf(arr.length - 2));
    return readDatum(arr, arr.length - 1, skip, indices);
  }

  static RVFDatum<String, Integer> readDatum(String[] values, int classColumn, Set<Integer> skip, Map<Integer, Index<String>> indices) {
    ClassicCounter<Integer> c = new ClassicCounter<>();
    RVFDatum<String, Integer> d = new RVFDatum<>(c);
    int attrNo = 0;
    for (int index = 0; index < values.length; index++) {
      if (index == classColumn) {
        d.setLabel(values[index]);
        continue;
      }
      if (skip.contains(Integer.valueOf(index))) {
        continue;
      }
      Integer featKey = Integer.valueOf(attrNo);
      Index<String> ind = indices.get(featKey);
      if (ind == null) {
        ind = new HashIndex<>();
        indices.put(featKey, ind);
      }
      // MG: condition on isLocked is useless, since add(E) contains such a condition:
      //if (!ind.isLocked()) {
        ind.add(values[index]);
      //}
      int valInd = ind.indexOf(values[index]);
      if (valInd == -1) {
        valInd = 0;
        logger.info("unknown attribute value " + values[index] + " of attribute " + attrNo);
      }
      c.incrementCount(featKey, valInd);
      attrNo++;

    }
    return d;
  }

  /**
   * Read the data as a list of RVFDatum objects. For the test set we must reuse the indices from the training set
   *
   */
  static ArrayList<RVFDatum<String, Integer>> readData(String filename, Map<Integer, Index<String>> indices) {
    try {
      String sep = ", ";
      ArrayList<RVFDatum<String, Integer>> examples = new ArrayList<>();
      for(String line : ObjectBank.getLineIterator(new File(filename))) {
        RVFDatum<String, Integer> next = readDatum(line, sep, indices);
        examples.add(next);
      }
      return examples;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

}
