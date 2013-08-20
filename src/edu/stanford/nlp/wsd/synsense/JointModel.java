package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.TwoDimensionalCounter;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * The JointModel uses bag of words for sense and
 * parse probability estimation for subcat, also
 * keeping track of the correlation between sense & subcat.
 *
 * @author Teg Grenager (grenager@cs.stanford.edu)
 */
public class JointModel extends BasicModel {


  // so we don't have to normalize over and over
  protected Map<String, Distribution<Subcategory>> subcatGivenSense;
  protected Map<Subcategory, Distribution<String>> senseGivenSubcat;

  protected double logSenseLikelihood;
  protected double logSubcatLikelihood;

  protected static NumberFormat formatter = new DecimalFormat("0.00");
  private double jointSmoothingParam = 1.0;
  private int EMsteps = 6;

  public JointModel() {
  }

  public JointModel(double[] modifiableBucketWeights, double senseEvidenceMult, double subcatEvidenceMult, double senseGivenSubcatMult, double subcatGivenSenseMult, double jointSmoothingParam) {
    super(modifiableBucketWeights, senseEvidenceMult, subcatEvidenceMult);
    this.senseGivenSubcatMult = senseGivenSubcatMult;
    this.subcatGivenSenseMult = subcatGivenSenseMult;
    this.jointSmoothingParam = jointSmoothingParam;
  }

  /** Reestimate parameters from data.
   */
  protected void constructSubcatAndSenseModel(List<Instance> data) {
    ClassicCounter<Subcategory> subcatCounter = new ClassicCounter<Subcategory>();
    TwoDimensionalCounter<String,Subcategory> senseAndSubcatCounts = new TwoDimensionalCounter<String, Subcategory>();
    for (Instance ins : data) {
      if (ins.subcat.equals(Subcategory.UNASSIGNED)) {
        continue; // don't use it to build our model
      }
      if (!ins.subcat.equals(Subcategory.DISTRIBUTION)) {
        // count prior only over all subcats that are known
        subcatCounter.incrementCount(ins.subcat);
      }
      if (ins.sense[0].equals(Instance.UNASSIGNED)) {
        continue; // don't use it to build our model
      }
      if (ins.sense[0].equals(Instance.DISTRIBUTION)) {
        // this has known subcat and distribution over sense
        for (String sense : allSenses) {
          double senseWeight = ins.senseDist.probabilityOf(sense);
          senseAndSubcatCounts.incrementCount(sense, ins.subcat, senseWeight);
        }
      } else {
        // this has known sense and distribution over subcat
        if (ins.subcat.equals(Subcategory.DISTRIBUTION)) {
          for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
            double subcatProb = ins.subcatDist.probabilityOf(subcat) / ins.sense.length;
            for (int i = 0; i < ins.sense.length; i++) {
              senseAndSubcatCounts.incrementCount(ins.sense[i], subcat, subcatProb);
            }
          }
        } else {
          throw new RuntimeException("marked for both sense and subcat");
          //          for (int i = 0; i < ins.sense.length; i++)
          //            senseAndSubcatCounts.incrementCount(ins.sense[i], ins.subcat, 1.0 / ins.sense.length);
        }
      }
    }
    // now we should have final counts over subcat given sense and over subcat marginal
    if (subcatCounter.isEmpty()) {
      throw new RuntimeException();
    }
    subcatPrior = Distribution.laplaceSmoothedDistribution(subcatCounter, Subcategory.SUBCATEGORIES.size(), 0.0001);
    if (senseAndSubcatCounts.isEmpty()) {
      // if we have no subcat/sense joint data, we initialize the conditional to be subcat marginal
      for (String sense : allSenses) {
        // for each sense initialize expectations to uniform
        for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
          senseAndSubcatCounts.incrementCount(sense, subcat, 1.0);
        }
        //       senseAndSubcatCounts.incrementCount(senseInt, Subcategory.ILLEGAL, 1.0);
      }
    } else {
      // do smoothing of the counts
      for (String sense : allSenses) {
        // TODO: we get this counter and don't use it -- a bug?
        ClassicCounter<Subcategory> c = senseAndSubcatCounts.getCounter(sense);
        for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
          senseAndSubcatCounts.incrementCount(sense, subcat, jointSmoothingParam);
        }
        //        senseAndSubcatCounts.incrementCount(senseInt, Subcategory.ILLEGAL, jointSmoothingParam);
      }
    }
    // build conditional distributions
    // now build the subcatGivenSense distribution
    subcatGivenSense = new HashMap<String, Distribution<Subcategory>>();
    for (Iterator<String> senseI = senseAndSubcatCounts.firstKeySet().iterator(); senseI.hasNext();) {
      String sense = senseI.next();
      ClassicCounter<Subcategory> c = senseAndSubcatCounts.getCounter(sense);
      Distribution<Subcategory> dist = Distribution.getDistribution(c);
      subcatGivenSense.put(sense, dist);
    }
    // now build the senseGivenSubcat distribution
    TwoDimensionalCounter<Subcategory,String> subcatAndSenseCounts = TwoDimensionalCounter.reverseIndexOrder(senseAndSubcatCounts);
    senseGivenSubcat = new HashMap<Subcategory, Distribution<String>>();
    for (Iterator<Subcategory> subcatI = subcatAndSenseCounts.firstKeySet().iterator(); subcatI.hasNext();) {
      Subcategory subcat = subcatI.next();
      ClassicCounter<String> c = subcatAndSenseCounts.getCounter(subcat);
      Distribution<String> dist = Distribution.getDistribution(c);
      senseGivenSubcat.put(subcat, dist);
    }
  }

  @Override
  public void init(List<Instance> data) {
    super.init(data);
    logSenseLikelihood = Double.NEGATIVE_INFINITY;
    logSubcatLikelihood = Double.NEGATIVE_INFINITY;
  }


  @Override
  public void train(List<Instance> data) {
    init(data);

    clearExpectations(data);
    constructSenseAndWordModel(data);
    constructSubcatAndSenseModel(data);

    // EM loop
    for (int i = 0; i < EMsteps; i++) {
      giveExpectations(data);
      constructSenseAndWordModel(data);
      constructSubcatAndSenseModel(data);
    }

    // display the joint for debugging purposes
    if (verbose) {
      TwoDimensionalCounter<String, Subcategory> joint = new TwoDimensionalCounter<String, Subcategory>();
      for (String sense : allSenses) {
        double senseProb = sensePrior.probabilityOf(sense);
        for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
          double jointProb = senseProb * Math.exp(logP_subcatGivenSense(subcat, sense));
          joint.incrementCount(sense, subcat, jointProb);
        }
      }
      printJoint(sensePrior, joint);
    }
  }

  private void printJoint(Distribution<String> marginal, TwoDimensionalCounter<String, Subcategory> m) {
    System.out.print("                                        \t");
    if (marginal != null) {
      System.out.print("ALL      \t");
    }
    for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
      System.out.print(StringUtils.pad(subcat, 9) + "\t");
    }
    System.out.println();

    for (String sense : allSenses) {
      System.out.print(StringUtils.pad(sense, 40) + "\t");
      if (marginal != null) {
        System.out.print(StringUtils.pad(formatter.format(marginal.probabilityOf(sense)), 9) + "\t");
      }
      for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
        double jointProb = m.getCount(sense, subcat);
        System.out.print(StringUtils.pad(formatter.format(jointProb), 9) + "\t");
      }
      System.out.println();
    }
  }

  /*
    protected double computeSenseMarkedLogLikelihood(Instance ins) {
      // take expectation over possible soft-assigned subcats
      // but do whole thing in log space
      double[] probs = new double[Subcategory.SUBCATEGORIES.size()];
      int i=0;
      for (Iterator subcatIterator = Subcategory.SUBCATEGORIES.iterator(); subcatIterator.hasNext();) {
        Subcategory subcat = (Subcategory) subcatIterator.next();
        double subcatAssm = Math.log(ins.subcatDist.probabilityOf(subcat));

        // sorry the following is a bit confusing: we need an expectation in linear
        // space, but we're in log space, so we log_add stuff and then, where we
        // would divide by the number of items in linear space, we subtract the
        // log of the number of items.  Pretty sure that's right...
        // for reference- here's what it used to be:
        // double subGivenSense = logP_subcatGivenSense(subcat, ins.sense);
        double subGivenSense = Double.NEGATIVE_INFINITY;
        for (int j=0; j<ins.sense.length; j++) {
          subGivenSense = SloppyMath.logAdd(subGivenSense, logP_subcatGivenSense(subcat, ins.sense[j]));
        }
        subGivenSense -= Math.log(ins.sense.length);

        double seqGivenSub = logP_seqGivenSub(ins, subcat);
        probs[i++] = subcatAssm + subGivenSense + seqGivenSub;
  //      System.out.println("subcat=" + subcat + " subcatAss=" +subcatAssm + " subGivenSense=" + subGivenSense + " seqGivenSub=" + seqGivenSub);
      }
      double logResult = SloppyMath.logSum(probs);

      // same confusing thing as above...
      // double logPsense = logP_seqGivenSense(ins, ins.sense) + Math.log(sensePrior.probabilityOf(new Integer(ins.sense)));
      double logPsense = Double.NEGATIVE_INFINITY;
      for (int j = 0; j < ins.sense.length; j++) {
        logPsense = SloppyMath.logAdd(logPsense, logP_seqGivenSense(ins, ins.sense[j]));
        logPsense = SloppyMath.logAdd(logPsense, Math.log(sensePrior.probabilityOf(ins.sense[j]))); // independent of subcat
      }
      logPsense -= Math.log(ins.sense.length);

      logResult += logPsense;
      if (Double.isNaN(logResult) || logResult == Double.NEGATIVE_INFINITY)
        throw new RuntimeException("result = " + logResult+" ins:\n" + ins);
      return logResult;
    }

    protected double computeSubcatMarkedLogLikelihood(Instance ins) {
      // take expectation over possible soft-assigned subcats
      // but do whole thing in log space
      double[] probs = new double[allSenses.size()];
      for (Iterator senseI = allSenses.iterator(); senseI.hasNext(); ) {
        String sense = (String) senseI.next();
        double senseAssm = Math.log(ins.senseDist.probabilityOf(sense));

        // see above paragraph...
        double seqGivenSense = Double.NEGATIVE_INFINITY;
        for (int j=0; j<ins.sense.length; j++) {
          seqGivenSense = SloppyMath.logAdd(seqGivenSense, logP_seqGivenSense(ins, sense));
        }
        double senseGivenSubcat = logP_senseGivenSubcat(ins.subcat, sense);
        double pSubcat = Math.log(subcatPrior.probabilityOf(ins.subcat));
        probs[allSenses.indexOf(sense)] = senseAssm + seqGivenSense + pSubcat + senseGivenSubcat; // expectation wrt this distribution
  //      System.out.println("i=" + i + " senseAss=" +senseAssm + " seqGivenSense=" + seqGivenSense + " senseGivenSubcat=" + senseGivenSubcat);
      }
      double logResult = SloppyMath.logSum(probs);

      double seqGivenSub = logP_seqGivenSub(ins, ins.subcat);
      if (seqGivenSub == Double.NEGATIVE_INFINITY) {
        // this happens if the parser can't parse to the real subcat...
        // TODO: is that the right thing to do?  hopefully it doesn't really happen that often
        seqGivenSub = 0.0; // so we ignore it
      }
      logResult += seqGivenSub;
      if (Double.isNaN(logResult) || logResult == Double.NEGATIVE_INFINITY)
        throw new RuntimeException("result="+ logResult+" ins:\n" + ins);
      return logResult;
    }
  */

  protected double logP_subcatGivenSense(Subcategory subcat, String sense) {
    Distribution<Subcategory> normSubcatGivenThisSense = subcatGivenSense.get(sense);
    double result = normSubcatGivenThisSense.probabilityOf(subcat);
    return Math.log(result) * subcatGivenSenseMult;
  }

  protected double logP_senseGivenSubcat(Subcategory subcat, String sense) {
    Distribution<String> normSenseGivenThisSubcat = senseGivenSubcat.get(subcat);
    double result = normSenseGivenThisSubcat.probabilityOf(sense);
    return Math.log(result) * senseGivenSubcatMult;
  }

  /*
  private double computeDataLikelihood(List data) {
    double oldSenseLogLikelihood = logSenseLikelihood;
    double oldSubcatLogLikelihood = logSubcatLikelihood;
    logSenseLikelihood = 0;
    logSubcatLikelihood = 0;
    for (Iterator iter = data.iterator(); iter.hasNext();) {
      Instance ins = (Instance) iter.next();
      if (ins.sense[0].equals(Instance.DISTRIBUTION) ||
          ins.sense[0].equals(Instance.UNASSIGNED)) {
        double d = computeSubcatMarkedLogLikelihood(ins);
        logSubcatLikelihood += d;
      } else if (ins.subcat.equals(Subcategory.DISTRIBUTION) ||
              ins.subcat.equals(Subcategory.UNASSIGNED)) {
        double d = computeSenseMarkedLogLikelihood(ins);
        logSenseLikelihood += d;
      } else {
        throw new RuntimeException("Error: did not expect completely marked instance.");
      }
    }
//    System.out.println("Change to sense expectations: " + logSenseLikelihood + " - " + oldSenseLogLikelihood + " = " + (logSenseLikelihood - oldSenseLogLikelihood));
//    System.out.println("Change to subcat expectations:" + logSubcatLikelihood + " - " + oldSubcatLogLikelihood + " = "  + (logSubcatLikelihood - oldSubcatLogLikelihood));
    return (logSenseLikelihood + logSubcatLikelihood - oldSenseLogLikelihood - oldSubcatLogLikelihood);
  }
  */

  private void giveExpectations(List<Instance> data) {
    for (Instance ins : data) {
      if (ins.sense[0].equals(Instance.DISTRIBUTION) || ins.sense[0].equals(Instance.UNASSIGNED)) {
        giveSenseExpectations(ins);
      } else
      if (ins.subcat.equals(Subcategory.DISTRIBUTION) || ins.subcat.equals(Subcategory.UNASSIGNED)) {
        giveSubcatExpectations(ins);
      } else {
        throw new RuntimeException("Did not expect entirely unmarked instance.");
      }
    }
  }

  protected void giveSenseExpectations(Instance ins) {
    /* We assume here that the instance is marked
    * for subcat and use:
    * P(sense|words,subcat,seq) = \alpha *
    * P(sense) * \Pi_i P(word_i|sense) * P(subcat|sense)
    */
    if (ins.subcat == Subcategory.DISTRIBUTION || ins.subcat == Subcategory.UNASSIGNED) {
      throw new RuntimeException();
    }
    ClassicCounter<String> logSense = new ClassicCounter<String>(); // for debugging
    for (String sense : allSenses) {
      // all done in log space
      double prob = logP_seqGivenSense(ins, sense) + Math.log(sensePrior.probabilityOf(sense)) + logP_subcatGivenSense(ins.subcat, sense);
      logSense.setCount(sense, prob);
    }
    ins.senseDist = Distribution.getDistributionFromLogValues(logSense);
    // this shouldn't be possible...
    if (ins.senseDist == null) {
      throw new RuntimeException();
    }
    ins.sense[0] = Instance.DISTRIBUTION;
  }

  protected void giveSubcatExpectations(Instance ins) {
    /* Assume instance is marked for sense
    * and use the following formula:
    *
    * P(subcat|words,sense,seq) = \alpha *
    * P(subcat|sense) * P(seq | subcat)
    */
    boolean foundPositive = false;
    if (ins.sense[0].equals(Instance.DISTRIBUTION) || ins.sense[0].equals(Instance.UNASSIGNED)) {
      throw new RuntimeException();
    }
    ClassicCounter<Subcategory> newSubcat = new ClassicCounter<Subcategory>();
    for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
      double subcatProb = Math.log(subcatPrior.probabilityOf(subcat));

      // another average in log space -- maybe we should put something in SloppyMath to handle this...
      double senseGivenSubcat = Double.NEGATIVE_INFINITY;
      for (int j = 0; j < ins.sense.length; j++) {
        senseGivenSubcat = SloppyMath.logAdd(senseGivenSubcat, logP_senseGivenSubcat(subcat, ins.sense[j]));
      }
      senseGivenSubcat -= Math.log(ins.sense.length);

      double seqGivenSubcat = logP_seqGivenSub(ins, subcat);
      double logProb = subcatProb + senseGivenSubcat + seqGivenSubcat;
      if (logProb > Double.NEGATIVE_INFINITY) {
        foundPositive = true;
      }
      newSubcat.setCount(subcat, logProb);
    }
    if (!foundPositive) {
      System.out.println(ins.logSequenceGivenSubcat);
      throw new RuntimeException(subcatPrior.toString());
    }
    ins.subcatDist = Distribution.getDistributionFromLogValues(newSubcat);
    if (ins.subcatDist == null) {
      throw new RuntimeException();
    }
    ins.subcat = Subcategory.DISTRIBUTION;
  }

  @Override
  protected InstanceMarking markInstance(Instance ins) {
    if (!ins.sense[0].equals(Instance.UNASSIGNED) && !ins.sense[0].equals(Instance.DISTRIBUTION)) {
      return markInstanceForSense(ins);
    } else if (!ins.subcat.equals(Subcategory.UNASSIGNED) && !ins.subcat.equals(Subcategory.DISTRIBUTION)) {
      return markInstanceForSubcat(ins);
    } else {
      throw new RuntimeException("Did not expect completely unmarked instance.");
    }
  }

  private static boolean hasNonInfVal(ClassicCounter<Object> c) {
    for (Iterator<Object> keyiter = c.keySet().iterator(); keyiter.hasNext();) {
      Object key = keyiter.next();
      double count = c.getCount(key);
      if (Double.isNaN(count) || count == Double.NEGATIVE_INFINITY) {
        return true;
      }
    }
    return false;
  }

  protected InstanceMarking markInstanceForSense(Instance ins) {
    double logHighestScore = Double.NEGATIVE_INFINITY;
    String bestSense = Instance.UNASSIGNED;
    TwoDimensionalCounter<String, Subcategory> joint = new TwoDimensionalCounter<String, Subcategory>();
    ClassicCounter<String> sensePosterior = new ClassicCounter<String>();
    for (String sense : allSenses) {
      double logSensePart = logP_seqGivenSense(ins, sense) + Math.log(sensePrior.probabilityOf(sense));
      sensePosterior.setCount(sense, logSensePart);
      double[] posteriors = new double[Subcategory.SUBCATEGORIES.size()];
      int j = 0;
      for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
        double thisSubcatGivenThisSense = logP_subcatGivenSense(subcat, sense); // normalized
        if (thisSubcatGivenThisSense == Double.NEGATIVE_INFINITY) {
          throw new RuntimeException("got -infty");
        }
        double thisSequenceGivenThisSubcat = logP_seqGivenSub(ins, subcat);
        double logThisScore = logSensePart + thisSubcatGivenThisSense + thisSequenceGivenThisSubcat;
        joint.incrementCount(sense, subcat, logThisScore);
        posteriors[j++] = logThisScore;
      }
      double totalThisSense = ArrayMath.logSum(posteriors);
      if (totalThisSense > logHighestScore) {
        logHighestScore = totalThisSense;
        bestSense = sense;
      }
    }


    //    if (bestSense != super.markInstance(ins).getSense()) {
    //      System.out.println("senses DISAGREE subSenMult:" + subcatGivenSenseMult);
    ////      System.out.println("Sense post: " + sensePosterior);
    ////      System.out.println("Arg max:" + sensePosterior.argmax());
    //    }
    // for debugging
    if (verbose) {
      System.out.println("instance:");
      System.out.println(ins);
      System.out.println("joint:");
      printJoint(null, joint);
      System.out.println("logPSense: " + Counters.toString(sensePosterior, formatter));
      System.out.println("bestSense=" + bestSense);
      System.out.println();
    }
    return new InstanceMarking(ins, bestSense, null, null, null);
  }

  protected InstanceMarking markInstanceForSubcat(Instance ins) {
    double logHighestScore = Double.NEGATIVE_INFINITY;
    Subcategory bestSubcat = Subcategory.UNASSIGNED;
    TwoDimensionalCounter<String, Subcategory> joint = new TwoDimensionalCounter<String, Subcategory>();
    for (Subcategory subcat : Subcategory.SUBCATEGORIES) {
      double thisSequenceGivenThisSubcat = logP_seqGivenSub(ins, subcat);
      double probSubcat = Math.log(subcatPrior.probabilityOf(subcat));
      double[] posteriors = new double[allSenses.size()];
      for (String sense : allSenses) {
        double logSensePart = logP_seqGivenSense(ins, sense);
        double thisSenseGivenThisSubcat = logP_senseGivenSubcat(subcat, sense); // normalized
        if (thisSenseGivenThisSubcat == Double.NEGATIVE_INFINITY) {
          throw new RuntimeException("got -infty");
        }
        double logThisScore = thisSequenceGivenThisSubcat + probSubcat + thisSenseGivenThisSubcat + logSensePart;
        joint.incrementCount(sense, subcat, logThisScore);
        posteriors[allSenses.indexOf(sense)] = logThisScore;
      }
      double totalThisSubcat = ArrayMath.logSum(posteriors);
      if (totalThisSubcat > logHighestScore) {
        logHighestScore = totalThisSubcat;
        bestSubcat = subcat;
      }
    }

    //    if (bestSubcat != super.markInstance(ins).getSubcat()) {
    //      System.out.println("Subcats DISAGREE senSubMult:" + senseGivenSubcatMult);
    ////      System.out.println("SubcatPrior: " + subcatPrior);
    //     }

    // for debugging
    if (verbose) {
      System.out.println("instance:");
      System.out.println(ins);
      System.out.println("joint:");
      printJoint(null, joint);
      System.out.println("bestSubcat=" + bestSubcat);
      System.out.println();
    }
    return new InstanceMarking(ins, Instance.UNASSIGNED, bestSubcat, null, null);
  }

}
