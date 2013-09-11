package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.stats.*;

import java.util.*;

/**
 * The BasicModel uses bag of words for sense and
 * parse probability estimation for subcat, assuming
 * the sub-models are independent.
 *
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class BasicModel implements Model {

  protected static double baselineSenseAccuracy, baselineSubcatAccuracy;

  protected Distribution<String> sensePrior;
  protected Distribution<Subcategory> subcatPrior;

  protected Distribution<String> wordPrior; // to do Bayesian smoothing of word|sense
  protected TwoDimensionalCounter<String,String> wordsGivenSenseCounts; // maps senses (ints) to word
  protected double numInstances;
  protected double numWords;
  protected String lexElt;

  // for weighting words by proximity
  protected IntegerPartition bucketing = new IntegerPartition(new int[]{-5, -2, -1, 0, 1, 2, 3, 6}); // TODO
  protected double[] bucketWeights = new double[]{1.0, 2.4, 8.0, 28.0, 0.0, 11.1, 12.0, 1.5, 1.0}; // TODO

  // so we don't have to normalize over and over
  protected HashMap<String, Pair<ClassicCounter<String>, Double>> wordsGivenSenseCache;

  protected boolean verbose = true;

  // important weights used in evidence combination
  protected double senseEvidenceMult = 1.666;
  protected double subcatEvidenceMult = 0.75;
  protected double senseGivenSubcatMult = 1.0;
  protected double subcatGivenSenseMult = 1.0;
  protected double seqGivenSenseTestLenPow = -0.486;

  // smoothing settings
  protected boolean subcatBayesSmoothing = true;
  protected boolean wordBayesSmoothing = true; // choose between smoothing paradigms
  protected double bayesSmoothingParam = 34700.0; // works best with seqGivenSenseMUlt = 1.6667
  protected double linearSmoothingParam = 0.12; // works best with seqGivenSenseMult=0.955
  protected Index<String> allSenses;

  // for training hyperparams (6 modifiableBucketWeights)
  public BasicModel(double[] modifiableBucketWeights, double senseEvidenceMult, double subcatEvidenceMult) {
    // set modifiable bucket weights
    for (int i = 0; i < 3; i++) {
      bucketWeights[i + 1] = modifiableBucketWeights[i];
      bucketWeights[i + 5] = modifiableBucketWeights[i + 3];
    }
    this.senseEvidenceMult = senseEvidenceMult;
    this.subcatEvidenceMult = subcatEvidenceMult;
  }

  public BasicModel() {
  }

  protected static void clearExpectations(List<Instance> data) {
    for (Instance ins : data) {
      if (ins.sense[0].equals(Instance.DISTRIBUTION)) {
        //        ins.senseDist = Distribution.getUniformDistribution(ins.senseDist.keySet());
        ins.sense[0] = Instance.UNASSIGNED;
        ins.senseDist = null;
      }
      if (ins.subcat == Subcategory.DISTRIBUTION) {
        //        ins.subcatDist = Distribution.getUniformDistribution(ins.subcatDist.keySet());
        ins.subcat = Subcategory.UNASSIGNED;
        ins.subcatDist = null;
      }
    }
  }

  protected void constructSenseAndWordModel(List<Instance> data) {
    if (verbose) {
      System.out.println("Constructing sense and word model.");
    }
    wordsGivenSenseCounts = new TwoDimensionalCounter<String,String>();
    Counter<String> senseCounts = new ClassicCounter<String>();
    Counter<String> wordCounter = new ClassicCounter<String>();
    allSenses = new HashIndex<String>();
    numWords = 0.0;
    lexElt = data.get(0).getLexicalElement();
    for (Instance ins : data) {
      if (ins.sense[0].equals(Instance.UNASSIGNED)) {
        continue; // don't use it to build our model
      }
      // the following prevents us from training the P(sense) and P(w|sense) on Expectation data
      if (ins.sense[0].equals(Instance.DISTRIBUTION)) {
        continue; // don't use it to build our model
      }
      // first get sense stats
      if (ins.sense[0].equals(Instance.DISTRIBUTION)) {
        // the following means that we train P(sense) on Expectation data, not good
        //        for (Iterator senseI = ins.senseDist.keySet().iterator(); senseI.hasNext(); ) {
        //          String sense = (String)senseI.next();
        //          double senseWeight = ins.senseDist.probabilityOf(sense);
        //          senseCounts.incrementCount(sense, senseWeight);
        //        }
      } else {
        for (int j = 0; j < ins.sense.length; j++) {
          String sense = ins.sense[j];
          if (sense.equals("UNASSIGNED")) {
            throw new RuntimeException();
          }
          allSenses.add(sense);
          senseCounts.incrementCount(sense, 1.0 / ins.sense.length);
        }
      }
      // next get word stats
      for (int i = 0; i < ins.allWords.size(); i++) {
        if (i == ins.index) {
          continue;
        }
        int bucket = bucketing.getBucket(ins.index - i);
        String word = ins.allWords.get(i).word().toLowerCase(); // + "::" + bucket;
        if (ins.sense[0].equals(Instance.DISTRIBUTION)) {
          for (Iterator<String> senseI = ins.senseDist.keySet().iterator(); senseI.hasNext();) {
            String sense = senseI.next();
            double senseWeight = ins.senseDist.probabilityOf(sense);
            double wordWeight = senseWeight * bucketWeights[bucket];
            wordsGivenSenseCounts.incrementCount(sense, word, wordWeight);
            numWords += wordWeight;
          }
        } else {
          for (int j = 0; j < ins.sense.length; j++) {
            String sense = ins.sense[j];
            double wordWeight = bucketWeights[bucket] / ins.sense.length;
            wordsGivenSenseCounts.incrementCount(sense, word, wordWeight);
            numWords += wordWeight;
          }
        }
        wordCounter.incrementCount(word);
      }
    }
    numInstances = senseCounts.totalCount();
    wordPrior = Distribution.getDistribution(wordCounter); // no smoothing here, will ignore unseen words
    sensePrior = Distribution.getDistribution(senseCounts);
    wordsGivenSenseCache.clear();
  }

  protected double logP_seqGivenSub(Instance ins, Subcategory subcat) {
    return ins.logSequenceGivenSubcat.getCount(subcat) * subcatEvidenceMult;
  }

  private double logP_senseGivenSeq(Instance ins, String sense) {
    double logPrior = Math.log(sensePrior.probabilityOf(sense));
    double seqProb = logP_seqGivenSense(ins, sense);
    return logPrior + seqProb;
  }

  protected double logP_seqGivenSense(Instance ins, String sense) {
    ClassicCounter<String> wordCountsGivenThisSense;
    double totalWordsGivenThisSense;
    if (wordsGivenSenseCache.containsKey(sense)) {
      Pair<ClassicCounter<String>, Double> p = wordsGivenSenseCache.get(sense);
      wordCountsGivenThisSense = p.first;
      totalWordsGivenThisSense = p.second;
    } else {
      wordCountsGivenThisSense = wordsGivenSenseCounts.getCounter(sense);
      totalWordsGivenThisSense = wordCountsGivenThisSense.totalCount();
      wordsGivenSenseCache.put(sense, new Pair<ClassicCounter<String>, Double>(wordCountsGivenThisSense, new Double(totalWordsGivenThisSense)));
    }
    double totalLogWordProb = 0.0;
    double totalWeight = 0.0;
    for (int i = 0; i < ins.allWords.size(); i++) {
      int bucket = bucketing.getBucket(ins.index - i);
      String word = ins.allWords.get(i).word().toLowerCase(); // + "::" + bucket;
      double wordCount = wordCountsGivenThisSense.getCount(word);
      double wordPriorProb = wordPrior.probabilityOf(word);
      double smoothedWordProb;
      if (wordBayesSmoothing) {
        // use Bayesian dirichlet prior smoothing
        smoothedWordProb = (wordCount + (wordPriorProb * bayesSmoothingParam)) / (totalWordsGivenThisSense + bayesSmoothingParam);
      } else {
        // use linear interpolation with prior
        smoothedWordProb = ((1.0 - linearSmoothingParam) * (wordCount / totalWordsGivenThisSense)) + ((linearSmoothingParam) * (wordPriorProb));
      }
      double logWordProb = Math.log(smoothedWordProb) * bucketWeights[bucket];
      totalWeight += bucketWeights[bucket];
      if (i == ins.index) {
        //        System.out.println("word: " + ins.allWords.get(i));
        continue;
      }
      if (logWordProb == Double.NEGATIVE_INFINITY) {
        continue; // ignore unseen words
      }
      totalLogWordProb += logWordProb;
    }
    double seqProb = (totalLogWordProb * Math.pow(totalWeight, seqGivenSenseTestLenPow));
    return seqProb * senseEvidenceMult;
  }

  protected InstanceMarking markInstance(Instance ins) {
    String senseJustification = null;
    String bestSense = Instance.UNASSIGNED;
    // if we know the true sense, then guess it
    if (!ins.sense[0].equals(Instance.UNASSIGNED)) {
      Counter<String> sensePosterior = new ClassicCounter<String>();
      List[] justLists = new List[allSenses.size()];
      for (String sense : allSenses) {
        List justProbs = new ArrayList();
        double logThisScore;
        logThisScore = logP_senseGivenSeq(ins, sense);
        justLists[allSenses.indexOf(sense)] = justProbs;
        sensePosterior.setCount(sense, logThisScore);
      }
      bestSense = Counters.argmax(sensePosterior);
    }

    Subcategory bestSubcat = Subcategory.UNASSIGNED;
    String subcatJustification = null;
    // if we know the true subcat, then guess it
    Counter<Subcategory> subcatPosterior = new ClassicCounter<Subcategory>();
    if (ins.subcat != Subcategory.UNASSIGNED && ins.subcat != Subcategory.DISTRIBUTION) {
      for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
        double posterior = logP_seqGivenSub(ins, subcat) + Math.log(subcatPrior.probabilityOf(subcat));
        subcatPosterior.setCount(subcat, posterior);
      }
      bestSubcat = Counters.argmax(subcatPosterior);
    }

    //    bestSense = ((Integer)sensePrior.argmax()).intValue(); // TODO this only for testing baseline
    //    bestSubcat = ((Subcategory)subcatPrior.argmax()); // TODO remove!

    return new InstanceMarking(ins, bestSense, bestSubcat, senseJustification, subcatJustification);
  }


  public void train(List<Instance> data) {
    init(data);
    clearExpectations(data);
    constructSenseAndWordModel(data);
    constructSubcatPrior(data);
  }

  private void constructSubcatPrior(List<Instance> data) {
    Counter<Subcategory> c = new ClassicCounter<Subcategory>();
    for (Instance ins : data) {
      if (ins.subcat != Subcategory.UNASSIGNED && ins.subcat != Subcategory.DISTRIBUTION) {
        c.incrementCount(ins.subcat);
      }
    }
    subcatPrior = Distribution.laplaceSmoothedDistribution(c, Subcategory.SUBCATEGORIES.size(), 0.0001);
  }

  public void init(List<Instance> data) {
    wordsGivenSenseCounts = new TwoDimensionalCounter<String,String>();
    wordsGivenSenseCache = new HashMap<String, Pair<ClassicCounter<String>, Double>>();
  }

  public List<InstanceMarking> test(List<Instance> data) {
    List<InstanceMarking> guesses = new ArrayList<InstanceMarking>();
    for (Instance ins : data) {
      InstanceMarking m = markInstance(ins);
      guesses.add(m);
    }
    return guesses;
  }


}


