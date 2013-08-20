package edu.stanford.nlp.tagger.maxent;

import edu.stanford.nlp.io.PrintFile;
import edu.stanford.nlp.ling.WordTag;


import java.util.Arrays;

public class BeamTestSentence extends TestSentence {

  protected String[][] nBest;
  protected double[] pBest;

  private int startSizePairs = pairs.getSize();
  private int endSizePairs = startSizePairs;

  private static final double sigma = 1.1;

  public BeamTestSentence(MaxentTagger maxentTagger) {
    super(maxentTagger);
  }

  /** Do tagging using beam search.
   *  @return The likely best tags for the sentence
   */
  private String[] testBeam() {
    for (int j = 0; j < kBestSize; j++) {
      for (int i = 0; i < size; i++) {
        nBest[j][i] = naTag;
        pBest[j] = 1.0;
      }
    }

    for (int current = 0; current < size; current++) {
      if (VERBOSE) {
        System.err.println("current is " + current + " word " + sent.get(current));
      }
      String[][] hCurrent = new String[kBestSize][size];
      double[] pCurrent = new double[kBestSize];
      for (int j = 0; j < pCurrent.length; j++) {
        pCurrent[j] = Double.NEGATIVE_INFINITY;
      }
      insertTags(current, hCurrent, pCurrent);
      if (VERBOSE) {
        System.err.println("current is " + current + " word " + sent.get(current));
      }

      for (int i = 0; i < kBestSize; i++) {
        if (hCurrent[i][0] == null) {
          // fill it and to the end with hCurrent[i-1]
          for (int s = i; s < kBestSize; s++) {
            System.arraycopy(hCurrent[i - 1], 0, hCurrent[s], 0, current + 1);
            pCurrent[s] = pCurrent[i - 1];
          }
          break;
        }  // end if
      }
      nBest = hCurrent;
      pBest = pCurrent;
    }

//  for(int i = 0; i < size - 1; i++) {
//    System.out.print(sent.get(i) + delimiter + nBest[0][i] + ' ');
//  }
//  System.out.println();

    return nBest[0];  // [0] is best
  }

  /** Do tagging using beam search.
   *
   *  @param pf Stream to write errors to
   */
  void testAndWriteErrors(PrintFile pf) {
    String[] chosenTags = testBeam();
    writeTagsAndErrors(chosenTags, pf, true);
  }

  /**
   * Tokenize s into words, and dump unknown word activations.
   *
   * @param s Sentence to print unknown word features of
   */
  void dumpActivations(String s) {
    this.origWords = null;
    this.sent = Arrays.asList(s.split("\\s+"));
    init();

    for (int i = 0; i < size; i++) {
      WordTag wT = new WordTag(sent.get(i), naTag);
      pairs.add(wT);
    }

    int start = endSizePairs;
    int end = endSizePairs + size - 1;
    endSizePairs = endSizePairs + size;
    // iterate over the sentence
    for (int current = 0; current < size; current++) {
      History h = new History(start, end, current + start, pairs, maxentTagger.extractors);
      printActivations(h);

    } // for current

    revert(0);
  }

  public boolean reliable(int current) {
    int numTags = maxentTagger.tags.getSize();
    double[][][] probabilities = new double[size][kBestSize][numTags];
    calculateProbs(probabilities);
    String tag = nBest[0][current];
    int y = maxentTagger.tags.getIndex(tag);
    double max = 0;
    int maxInd = -1;
    double p = probabilities[current][0][y];
    if (!maxentTagger.dict.isUnknown(sent.get(current))) {
      System.out.println(" known " + sent.get(current));
      String[] tags = maxentTagger.dict.getTags(sent.get(current));
      String[] tags1 = maxentTagger.tags.deterministicallyExpandTags(tags);
      for (String aTags1 : tags1) {
        int i = maxentTagger.tags.getIndex(aTags1);
        if (i == y) {
          continue;
        }
        if ((probabilities[current][0][i] >= max) && (probabilities[current][0][i] <= p)) {
          max = probabilities[current][0][i];
          maxInd = i;
        }
      }
    }// if known
    else {
      System.out.println(" unknown " + sent.get(current));
      for (int i = 0; i < maxentTagger.ySize; i++) {
        if (i == y) {
          continue;
        }
        if ((probabilities[current][0][i] >= max) && (probabilities[current][0][i] <= p)) {
          max = probabilities[current][0][i];
          maxInd = i;
        }
      }
    }// else
    if (maxInd == -1) {
      return true;
    }
    String tag1 = maxentTagger.tags.getTag(maxInd);
    System.out.println(tag + ' ' + maxentTagger.tags.getTag(maxInd) + ' ' + p / max);
    if (maxentTagger.dict.isUnknown(sent.get(current)) && (maxentTagger.tags.isClosed(tag1))) {
      return true;
    } else {
      return (p / max) > sigma;
    }
  }

  void insertTags(int current, String[][] hCurrent, double[] pCurrent) {
    History h = new History(pairs, maxentTagger.extractors);
    double sum = 0.0;
    boolean hasHistory = true;
    double[] histories = null;

    boolean unknown = maxentTagger.dict.isUnknown(sent.get(current));
    // if (unknown) {
      //numUnknown++;
    // }
    for (int hyp = 0; hyp < kBestSize; hyp++) {
      // Find the history
      try {
        getHistory(current, h, hyp); // sets History h
      } catch (Exception e) {
        System.err.println("Error: num hyp " + hyp);
        System.err.println(sent.get(current));
      }

      //if (true) {
        // System.err.println("Exception "+current);
        hasHistory = false;
        //return;
      //}
      if (!hasHistory) {
        String[] tags = stringTagsAt(h.current - h.start + leftWindow());
        histories = getHistories(tags, h);

        sum = 0;
        for (double history1 : histories) {
          sum = sum + history1;
        }
      }
      // Generate tags for current
      // Should implement dictionary later
      int x = 0;
      if (unknown) {
        for (int y = 0; y < maxentTagger.ySize; y++) {
          String tag = maxentTagger.tags.getTag(y);
          if (maxentTagger.tags.isClosed(tag)) {
            continue;
          }
          insertArray(current, hyp, x, y, hCurrent, pCurrent, tag, hasHistory, histories, sum);
        }
      } else { // the word is known and cut the search

        String[] tags1 = maxentTagger.dict.getTags(sent.get(current));
        String[] tags = maxentTagger.tags.deterministicallyExpandTags(tags1);

        for (String tag : tags) {
          int y = maxentTagger.tags.getIndex(tag);
          //if(y==-1)
          // System.out.println(" y is " +y+" "+ tag);
          insertArray(current, hyp, x, y, hCurrent, pCurrent, tag, hasHistory, histories, sum);
        }

      }//else
    }//hyp
  }

  /**
   * Print out the unknown word feature values of the features in ExtractorFramesRare.
   */
  private void printActivations(History h) {
    String word = h.pairs.getWord(h, 0);
    //FeatureKey s = new FeatureKey();

    System.out.println("features for word " + word);
    Extractors ext = maxentTagger.extractorsRare;
    for (int j = 0; j < ext.getSize(); j++) {
      String key = ext.extract(j, h);
      System.out.println(j + '\t' + ext.toString() + " value " + key);
    }
    System.out.println();
  }

  void insertArray(int current, int hyp, int x, int y, String[][] hCurrent, double[] pCurrent, String tag, boolean hasHistory, double[] histories, double sum) {
    if (DBG) {
      System.err.println(" inserting tag " + tag + " for word " + sent.get(current));
    }

    double p; // initialized below
    if (hasHistory) {
      //p=(pBest[hyp]>0?prob.pcond(y,x)*pBest[hyp]:prob.pcond(y,x));
      p = maxentTagger.getLambdaSolve().pcond(y, x) * pBest[hyp];
    } else {
      //p=(histories[y]/sum)*pBest[hyp]; * chqange back to *
      p = Math.log(histories[y]) - Math.log(sum) + pBest[hyp];
    }//else no history
    if (Double.isNaN(p)) {
      System.err.println(" p ia NaN " + pBest[hyp] + ' ' + p + " current is" + current + " tag " + tag);
      return;
    }
    if (p == 0.0) {
      System.err.println(" p is 0 inside insertArray");
      //smooth them
      return;
    }

    // this is acrobatics to avoid putting equal hypotheses on the beam
    for (int i = 0; i < kBestSize; i++) {
      //here is the comparison
      if (p == pCurrent[i]) {
        boolean isDifferent = false;
        for (int j = 0; j < current; j++) {
          if (!(hCurrent[i][j].equals(nBest[hyp][j]))) {
            isDifferent = true;
          }
        }
        if (!(hCurrent[i][current].equals(tag))) {
          isDifferent = true;
        }
        if (!isDifferent) {
          return; // do not put the same thing twice
        }
      }
    }

    if (p < pCurrent[kBestSize - 1]) {
      if (DBG) {
        System.err.println(" for word " + current + " did not insert tag " + tag + " prob " + (Math.log(histories[y]) - Math.log(sum)) + " total " + p);

      }
      return;
    }

    int i = kBestSize - 2;
    while (true) {
      if ((i == -1) || (p < pCurrent[i])) {
        //Put the element at i+1
        pCurrent[i + 1] = p;
        hCurrent[i + 1][current] = tag;
        if (DBG) {
          System.err.println(" added for word " + current + " tag " + tag + " prob " + (Math.log(histories[y]) - Math.log(sum)) + " total " + p + " at place " + (i + 1));
        }

        System.arraycopy(nBest[hyp], 0, hCurrent[i + 1], 0, current);
        return;
      }
      // p>=p i
      // Write the p and the string for i on place i+1
      pCurrent[i + 1] = pCurrent[i];
      System.arraycopy(hCurrent[i], 0, hCurrent[i + 1], 0, current + 1);
      i--;
    } // end while
  }

  void getHistory(int current, History h, int hyp) {
    for (int i = 0; i < current; i++) {
      WordTag wT = new WordTag(sent.get(i), nBest[hyp][i]);
      pairs.add(wT);
    }
    for (int i = current; i < size; i++) {
      WordTag wT = new WordTag(sent.get(i), finalTags[i]);
      pairs.add(wT);
    }

    h.set(endSizePairs, endSizePairs + size - 1, current + endSizePairs);
    this.endSizePairs = endSizePairs + size;
  }

}
