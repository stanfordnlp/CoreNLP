package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.sequences.SequenceListener;
import edu.stanford.nlp.ling.CoreAnnotations;

import java.io.*;
import java.util.*;

/**
 * @author Mengqiu Wang
 **/
public class BisequenceAlignmentPrior<IN extends CoreMap> implements SequenceModel, SequenceListener {

  protected int[] sequence;
  protected int backgroundSymbol;
  protected int numClasses;
  protected int[] possibleValues;
  protected Index<String> classIndexEn;
  protected Index<String> classIndexCh;
  protected int enDocSize, chDocSize, sequenceSize;
  // in log scale
  protected double currScore = 0.0;
  protected int[] internalSeq;
  public static final int hardPrior = 1;
  public static final int softPrior = 2;
  private SeqClassifierFlags flags;
  private double smallProb = Math.log(1e-8);
  private double oneMinusSmallProb = Math.log(1-(1e-8*4));
  private Map<String, Map<String, Double>> softPriorMapCh, softPriorMapEn;

  protected List<Triple<Integer, Integer, Double>> alignment;
  private Map<Integer, List<Pair<Integer, Double>>> alignmentMap = null;

  public BisequenceAlignmentPrior(String backgroundSymbol, Index<String> classIndexEn, Index<String> classIndexCh,
      List<Triple<Integer, Integer, Double>> alignment, int enDocSize, int chDocSize, SeqClassifierFlags flags, Map<String, Map<String, Double>> softPriorMapEn, Map<String, Map<String, Double>> softPriorMapCh) {
    this.softPriorMapEn = softPriorMapEn;
    this.softPriorMapCh = softPriorMapCh;
    this.classIndexEn = classIndexEn;
    this.classIndexCh = classIndexCh;
    this.backgroundSymbol = classIndexEn.indexOf(backgroundSymbol);
    this.numClasses = classIndexEn.size();
    this.possibleValues = new int[numClasses];
    for (int i=0; i<numClasses; i++) {
      possibleValues[i] = i;
    }
    this.alignment = alignment;
    this.flags = flags;
    this.enDocSize = enDocSize;
    this.chDocSize = chDocSize;
    this.sequenceSize = enDocSize + chDocSize;
    this.alignmentMap = new HashMap<Integer, List<Pair<Integer, Double>>>(alignment.size() * 2);
    for (Triple<Integer, Integer, Double> wordPair: alignment) {
      int enInd = wordPair.first();
      int chInd = wordPair.second();
      double prob = wordPair.third();
      chInd += enDocSize;
      if (!alignmentMap.containsKey(enInd)) {
        List<Pair<Integer, Double>> list = new ArrayList<Pair<Integer, Double>>();
        list.add(new Pair<Integer, Double>(chInd, prob));
        alignmentMap.put(enInd, list);
      } else {
        List<Pair<Integer, Double>> list = alignmentMap.get(enInd);
        list.add(new Pair<Integer, Double>(chInd, prob));
      }
      if (!alignmentMap.containsKey(chInd)) {
        List<Pair<Integer, Double>> list = new ArrayList<Pair<Integer, Double>>();
        list.add(new Pair<Integer, Double>(enInd, prob));
        alignmentMap.put(chInd, list);
      } else {
        List<Pair<Integer, Double>> list = alignmentMap.get(chInd);
        list.add(new Pair<Integer, Double>(enInd, prob));
      }
    }
  }

  private boolean VERBOSE = false;
  //TODO(mengqiu) turn this off
  private boolean checkSequence = false;

  public int leftWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  public int rightWindow() {
    return Integer.MAX_VALUE; // not Markovian!
  }

  public int[] getPossibleValues(int position) {
    return possibleValues;
  }

  /**
   * @return the length of the sequence
   */
  public int length() {
    return sequenceSize;
  }

  /**
   * get the number of classes in the sequence model.
   */
  public int getNumClasses() {
    return classIndexEn.size();
  }

  public  double[] getConditionalDistribution(int[] sequence, int position) {
    double[] probs = scoresOf(sequence, position);
    ArrayMath.logNormalize(probs);
    probs = ArrayMath.exp(probs);
    //System.out.println(this);
    return probs;
  }

  /** BEGIN SequenceModel Impl */
  public double scoreOf(int[] sequence, int pos) {
    return scoresOf(sequence, pos)[sequence[pos]];
  }

  public double scoreOf(int[] sequence) {
    return calcScore(sequence); 
    /*
    if (flags.useChromaticSampling) {
      return calcScore(sequence); 
    } else {
      if (internalSeq == null || (checkSequence && !Arrays.equals(sequence, internalSeq))) {
        calcScore(sequence); 
      }
      return currScore;
    }
    */
  }

  public double[] scoresOf(int[] sequence, int position) {
    double[] probs = new double[numClasses];
    int origClass = sequence[position];
    /* Temporary disabled because we only care about conditional probs */
    // double originalScore = scoreOf(sequence);  
    double originalScore = calcScore(sequence, position);
    probs[origClass] = originalScore;
    for (int label = 0; label < numClasses; label++) {
      if (label != origClass) {
        probs[label] = scoreDiff(sequence, position, label, origClass, originalScore);
      }
    }
    //System.out.println(this);
    return probs;
  }
  /** END SequenceModel Impl */

  /** BEGIN SequenceListener Impl */
  public void setInitialSequence(int[] initialSequence) {
    // if (!flags.useChromaticSampling) {
    //   this.internalSeq = initialSequence;
    //   calcScore(initialSequence);
    // }
  }

  public void updateSequenceElement(int[] sequence, int position, int oldVal) {
    // if (!flags.useChromaticSampling) {
    //   this.internalSeq = sequence;
    //   currScore = scoreDiff(sequence, position, sequence[position], oldVal, currScore);
    // }
  }
  /** END SequenceListener Impl */

  private double scoreDiff(int[] sequence, int position, int newLabel, int oldLabel, double score) {
    double newScore = score;
    if (alignmentMap.containsKey(position)) {
      List<Pair<Integer, Double>> posProbList = alignmentMap.get(position);
      // if (posProbList.size() > 1)
      //   System.err.println(posProbList.size());
      for (Pair<Integer, Double> posProb: posProbList) {
        int alignedPos = posProb.first();
        double alignedProb = posProb.second();
        int alignedLabel = sequence[alignedPos];
        double probFactor = 1.0;
        if (flags.factorInAlignmentProb) {
          probFactor = alignedProb;
        }
        newScore -= probFactor * getAlignedWordPairScore(oldLabel, alignedLabel, position);
        newScore += probFactor * getAlignedWordPairScore(newLabel, alignedLabel, position);
      }
    }
    return newScore;
  }

  /**
   * If <code>pos</code> is less than enDocSize, then tag1 is the enTag
   */ 
  private double getAlignedWordPairScore(int tag1, int tag2, int pos) {
    int enTag = tag1;
    int chTag = tag2;
    if (pos >= enDocSize) {
      enTag = tag2;
      chTag = tag1;
    }
    String enLabel = classIndexEn.get(enTag);
    String chLabel = classIndexCh.get(chTag);
    String[] enLabelParts = enLabel.split("-");
    String[] chLabelParts = chLabel.split("-");
    String enRawLabel = enLabelParts[enLabelParts.length-1];
    String chRawLabel = chLabelParts[chLabelParts.length-1];

    if (flags.bisequencePriorType == hardPrior) {
      if (enRawLabel.equals(chRawLabel)) {
        return oneMinusSmallProb;
      } else {
        return smallProb;
      }
    } else if (flags.bisequencePriorType == softPrior) {
      double score = 0;
      if (softPriorMapCh != null)
        score += softPriorMapCh.get(chRawLabel).get(enRawLabel);
      if (softPriorMapEn != null)
        score += softPriorMapEn.get(enRawLabel).get(chRawLabel);

      return score;
    } else {
      throw new RuntimeException("Prior type "+ flags.bisequencePriorType +" not recognized");
    }
  }

  public static Map<String, Map<String, Double>> loadSoftPriorMap(String alignmentPriorPenaltyFile, boolean chFirst) {
    String line = null;
    Map<String, Map<String, Double>> softPriorMap = new HashMap<String, Map<String, Double>>();
    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(alignmentPriorPenaltyFile)))); 

      while( (line = br.readLine()) != null) {
        line = line.trim();
        if (line.length() > 0) {
          String[] parts = line.split("\t");
          String chPart = parts[0];
          String enPart = parts[1];
          if (!chFirst) {
            String temp = chPart;
            chPart = enPart;
            enPart = temp;
          }
          double prob = Math.log(Double.parseDouble(parts[2]));
          if (!softPriorMap.containsKey(chPart)) {
            Map<String, Double> innerMap = new HashMap<String, Double>();
            innerMap.put(enPart, prob);
            softPriorMap.put(chPart, innerMap);
          } else {
            Map<String, Double> innerMap = softPriorMap.get(chPart);
            innerMap.put(enPart, prob);
          }
        }
      }
      System.err.println("loaded softPriorMap" + (chFirst ? "Ch" : "En") +":\n" + softPriorMap.toString());
    } catch (Exception ex) {
      ex.printStackTrace();
      throw new RuntimeException("Reading soft prior penalty error in line:\n" + line);
    }

    return softPriorMap;
  }

  private double calcScore(int[] sequence) {
    if (VERBOSE)
      System.err.println("re-calculating score for sequence " + ArrayMath.toString(sequence));

    int enInd, chInd = 0;
    double prob = 1.0;
    double newScore = 0.0;
    double probFactor = 1.0;
    for (Triple<Integer, Integer, Double> wordPair: alignment) {
      enInd = wordPair.first();
      chInd = wordPair.second();
      prob = wordPair.third();
      chInd += enDocSize;
      probFactor = 1.0;
      if (flags.factorInAlignmentProb) {
        probFactor = prob;
      }  
      try {  
        newScore += probFactor * getAlignedWordPairScore(sequence[enInd], sequence[chInd], 0);
      } catch (Exception ex) {
        System.err.println("sequence.length == " + sequence.length+", enInd: " + enInd + ", chInd: " + chInd+", enDocSize = " + enDocSize);
        ex.printStackTrace();
      }
    }
    currScore = newScore;
    return newScore;
  }

  private double calcScore(int[] sequence, int position) {
    double newScore = 0;
    if (alignmentMap.containsKey(position)) {
      List<Pair<Integer, Double>> posProbList = alignmentMap.get(position);
      if (BisequenceEmpiricalNERPrior.DEBUG && BisequenceEmpiricalNERPrior.debugIndices.indexOf(position) != -1)
        System.err.println("alignment size: " + posProbList.size());
      for (Pair<Integer, Double> posProb: posProbList) {
        int alignedPos = posProb.first();
        double alignedProb = posProb.second();
        int alignedLabel = sequence[alignedPos];
        double probFactor = 1.0;
        if (flags.factorInAlignmentProb) {
          probFactor = alignedProb;
        }
        String alignedLabelStr = "";
        if (position >= enDocSize) {
          alignedLabelStr = classIndexEn.get(alignedLabel);
        } else {
          alignedLabelStr = classIndexCh.get(alignedLabel);
        }
        double alignScore = getAlignedWordPairScore(sequence[position], alignedLabel, position);
        double addPart = probFactor * alignScore;
        if (BisequenceEmpiricalNERPrior.DEBUG && BisequenceEmpiricalNERPrior.debugIndices.indexOf(position) != -1)
          System.err.println("alignment sequence[" + position + "] to "+ alignedPos+":"+alignedLabelStr +", has alignScore:" + alignScore+", final score:" + addPart);
        // System.err.println("addPart="+addPart+", probFactor="+probFactor);
        newScore += addPart;
      }
    } else {
      // System.err.println("position="+position+" not found in sequence of size " + sequence.length+", alignmentMap.size()="+alignmentMap.size());
    }
    // System.err.println("newScore: " + newScore);
    return newScore;
  }
}
