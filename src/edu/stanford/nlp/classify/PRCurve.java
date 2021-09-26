package edu.stanford.nlp.classify;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import edu.stanford.nlp.util.BinaryHeapPriorityQueue;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;


import edu.stanford.nlp.util.logging.Redwood;

/** A class to create recall-precision curves given scores
 *  used to fit the best monotonic function for logistic regression and SVMs.
 *
 *  @author Kristina Toutanova
 *  @version May 23, 2005
 */
public class PRCurve {

  double[] scores; //sorted scores
  int[] classes; // the class of example i
  int[] guesses; // the guess of example i according to the argmax
  int[] numpositive; // number positive in the i-th highest scores
  int[] numnegative; // number negative in the i-th lowest scores

  final static Redwood.RedwoodChannels logger = Redwood.channels(PRCurve.class);

  /**
   * reads scores with classes from a file, sorts by score and creates the arrays
   *
   */
  public PRCurve(String filename) {
    try {
      ArrayList<Pair<Double, Integer>> dataScores = new ArrayList<>();
      for(String line : ObjectBank.getLineIterator(new File(filename))) {
        List<String> elems = StringUtils.split(line);
        Pair<Double, Integer> p = new Pair<>(Double.valueOf(elems.get(0)), Integer.valueOf(elems.get(1)));
        dataScores.add(p);
      }
      init(dataScores);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }


  /**
   * reads scores with classes from a file, sorts by score and creates the arrays
   *
   */
  public PRCurve(String filename, boolean svm) {
    try {

      ArrayList<Pair<Double, Integer>> dataScores = new ArrayList<>();
      for(String line : ObjectBank.getLineIterator(new File(filename))) {
        List<String> elems = StringUtils.split(line);
        int cls = Double.valueOf(elems.get(0)).intValue();
        if (cls == -1) {
          cls = 0;
        }
        double score = Double.valueOf(elems.get(1)) + 0.5;
        Pair<Double, Integer> p = new Pair<>(Double.valueOf(score), Integer.valueOf(cls));
        dataScores.add(p);
      }
      init(dataScores);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public double optimalAccuracy() {
    return precision(numSamples()) / (double) numSamples();
  }

  public double accuracy() {
    return logPrecision(numSamples()) / (double) numSamples();
  }


  public PRCurve(List<Pair<Double, Integer>> dataScores) {
    init(dataScores);
  }

  public void init(List<Pair<Double, Integer>> dataScores) {
    PriorityQueue<Pair<Integer, Pair<Double, Integer>>> q = new BinaryHeapPriorityQueue<>();
    for (int i = 0; i < dataScores.size(); i++) {
      q.add(new Pair<>(Integer.valueOf(i), dataScores.get(i)), -dataScores.get(i).first().doubleValue());
    }
    List<Pair<Integer, Pair<Double, Integer>>> sorted = q.toSortedList();
    scores = new double[sorted.size()];
    classes = new int[sorted.size()];
    logger.info("incoming size " + dataScores.size() + " resulting " + sorted.size());

    for (int i = 0; i < sorted.size(); i++) {
      Pair<Double, Integer> next = sorted.get(i).second();
      scores[i] = next.first().doubleValue();
      classes[i] = next.second().intValue();
    }
    init();
  }


  public void initMC(ArrayList<Triple<Double, Integer, Integer>> dataScores) {
    PriorityQueue<Pair<Integer, Triple<Double, Integer, Integer>>> q = new BinaryHeapPriorityQueue<>();
    for (int i = 0; i < dataScores.size(); i++) {
      q.add(new Pair<>(Integer.valueOf(i), dataScores.get(i)), -dataScores.get(i).first().doubleValue());
    }
    List<Pair<Integer, Triple<Double, Integer, Integer>>> sorted = q.toSortedList();
    scores = new double[sorted.size()];
    classes = new int[sorted.size()];
    guesses = new int[sorted.size()];
    logger.info("incoming size " + dataScores.size() + " resulting " + sorted.size());

    for (int i = 0; i < sorted.size(); i++) {
      Triple<Double, Integer, Integer> next = sorted.get(i).second();
      scores[i] = next.first().doubleValue();
      classes[i] = next.second().intValue();
      guesses[i] = next.third().intValue();
    }
    init();
  }


  /**
   * initialize the numpositive and the numnegative arrays
   */
  void init() {
    numnegative = new int[numSamples() + 1];
    numpositive = new int[numSamples() + 1];
    numnegative[0] = 0;
    numpositive[0] = 0;
    int num = numSamples();
    for (int i = 1; i <= num; i++) {
      numnegative[i] = numnegative[i - 1] + (classes[i - 1] == 0 ? 1 : 0);
    }
    for (int i = 1; i <= num; i++) {
      numpositive[i] = numpositive[i - 1] + (classes[num - i] == 0 ? 0 : 1);
    }
    logger.info("total positive " + numpositive[num] + " total negative " + numnegative[num] + " total " + num);
    // for (int i = 1; i < numpositive.length; i++) {
      // System.out.println(i + " positive " + numpositive[i] + " negative " + numnegative[i] + " classes " + classes[i - 1] + " " + classes[num - i]);
    // }
  }

  int numSamples() {
    return scores.length;
  }

  /**
   * what is the best precision at the given recall
   *
   */
  public int precision(int recall) {
    int optimum = 0;
    for (int right = 0; right <= recall; right++) {
      int candidate = numpositive[right] + numnegative[recall - right];
      if (candidate > optimum) {
        optimum = candidate;
      }
    }
    return optimum;
  }

  public static double f1(int tp, int fp, int fn) {
    double prec = 1;
    double recall = 1;
    if (tp + fp > 0) {
      prec = tp / (double) (tp + fp);
    }
    if (tp + fn > 0) {
      recall = tp / (double) (tp + fn);
    }
    return 2 * prec * recall / (prec + recall);
  }

  /**
   * the f-measure if we just guess as negative the first numleft and guess as positive the last numright
   *
   */
  public double fmeasure(int numleft, int numright) {
    int tp = numpositive[numright];
    int fp = numright - tp;
    int fn = numleft - numnegative[numleft];
    return f1(tp, fp, fn);
  }


  /**
   * what is the precision at this recall if we look at the score as the probability of class 1 given x
   * as if coming from logistic regression
   *
   */
  public int logPrecision(int recall) {
    int totaltaken = 0;
    int rightIndex = numSamples() - 1; //next right candidate
    int leftIndex = 0; //next left candidate
    int totalcorrect = 0;

    while (totaltaken < recall) {
      double confr = Math.abs(scores[rightIndex] - .5);
      double confl = Math.abs(scores[leftIndex] - .5);
      int chosen = leftIndex;
      if (confr > confl) {
        chosen = rightIndex;
        rightIndex--;
      } else {
        leftIndex++;
      }
      //logger.info("chose "+chosen+" score "+scores[chosen]+" class "+classes[chosen]+" correct "+correct(scores[chosen],classes[chosen]));
      if ((scores[chosen] >= .5) && (classes[chosen] == 1)) {
        totalcorrect++;
      }
      if ((scores[chosen] < .5) && (classes[chosen] == 0)) {
        totalcorrect++;
      }
      totaltaken++;
    }

    return totalcorrect;
  }

  /**
   * what is the optimal f-measure we can achieve given recall guesses
   * using the optimal monotonic function
   *
   */
  public double optFmeasure(int recall) {
    double max = 0;
    for (int i = 0; i < (recall + 1); i++) {
      double f = fmeasure(i, recall - i);
      if (f > max) {
        max = f;
      }
    }
    return max;
  }

  public double opFmeasure() {
    return optFmeasure(numSamples());
  }

  /**
   * what is the f-measure at this recall if we look at the score as the probability of class 1 given x
   * as if coming from logistic regression same as logPrecision but calculating f-measure
   *
   * @param recall make this many guesses for which we are most confident
   */
  public double fmeasure(int recall) {
    int totaltaken = 0;
    int rightIndex = numSamples() - 1; //next right candidate
    int leftIndex = 0; //next left candidate
    int tp = 0, fp = 0, fn = 0;
    while (totaltaken < recall) {
      double confr = Math.abs(scores[rightIndex] - .5);
      double confl = Math.abs(scores[leftIndex] - .5);
      int chosen = leftIndex;
      if (confr > confl) {
        chosen = rightIndex;
        rightIndex--;
      } else {
        leftIndex++;
      }
      //logger.info("chose "+chosen+" score "+scores[chosen]+" class "+classes[chosen]+" correct "+correct(scores[chosen],classes[chosen]));
      if ((scores[chosen] >= .5)) {
        if (classes[chosen] == 1) {
          tp++;
        } else {
          fp++;
        }
      }
      if ((scores[chosen] < .5)) {
        if (classes[chosen] == 1) {
          fn++;
        }
      }
      totaltaken++;
    }

    return f1(tp, fp, fn);

  }


  /**
   * assuming the scores are probability of 1 given x
   *
   */
  public double logLikelihood() {
    double loglik = 0;
    for (int i = 0; i < scores.length; i++) {
      loglik += Math.log(classes[i] == 0 ? 1 - scores[i] : scores[i]);
    }
    return loglik;
  }

  /**
   * confidence weighted accuracy assuming the scores are probabilities and using .5 as treshold
   *
   */
  public double cwa() {
    double acc = 0;
    for (int recall = 1; recall <= numSamples(); recall++) {
      acc += logPrecision(recall) / (double) recall;
    }
    return acc / numSamples();
  }

  /**
   * confidence weighted accuracy assuming the scores are probabilities and using .5 as treshold
   *
   */
  public int[] cwaArray() {
    int[] arr = new int[numSamples()];
    for (int recall = 1; recall <= numSamples(); recall++) {
      arr[recall - 1] = logPrecision(recall);
    }
    return arr;
  }

  /**
   * confidence weighted accuracy assuming the scores are probabilities and using .5 as threshold
   *
   */
  public int[] optimalCwaArray() {
    int[] arr = new int[numSamples()];
    for (int recall = 1; recall <= numSamples(); recall++) {
      arr[recall - 1] = precision(recall);
    }
    return arr;
  }

  /**
   * optimal confidence weighted accuracy assuming for each recall we can fit an optimal monotonic function
   *
   */
  public double optimalCwa() {
    double acc = 0;
    for (int recall = 1; recall <= numSamples(); recall++) {
      acc += precision(recall) / (double) recall;
    }
    return acc / numSamples();
  }


  public static boolean correct(double score, int cls) {
    return ((score >= .5) && (cls == 1)) || ((score < .5) && (cls == 0));
  }

  public static void main(String[] args) {

    PriorityQueue<String> q = new BinaryHeapPriorityQueue<>();
    q.add("bla", 2);
    q.add("bla3", 2);
    logger.info("size of q " + q.size());

    PRCurve pr = new PRCurve("c:/data0204/precsvm", true);
    logger.info("acc " + pr.accuracy() + " opt " + pr.optimalAccuracy() + " cwa " + pr.cwa() + " optcwa " + pr.optimalCwa());
    for (int r = 1; r <= pr.numSamples(); r++) {
      logger.info("optimal precision at recall " + r + " " + pr.precision(r));
      logger.info("model precision at recall " + r + " " + pr.logPrecision(r));
    }
  }


}
