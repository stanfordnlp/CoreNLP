package edu.stanford.nlp.sequences;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.ConjugatePrior;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.Dirichlet;
import edu.stanford.nlp.stats.GaussianMeanPrior;
import edu.stanford.nlp.stats.Multinomial;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import no.uib.cipr.matrix.*;

/**
 * A general class implementing the infinite HMM, which uses the direct assignment sampling
 * scheme for inference, as described in:
 *
 * Y.W. Teh, M.I. Jordan, M.J. Beal and D.M. Blei. Hierarchical Dirichlet Processes.
 * To appear in JASA, 2006.
 *
 * Designed to be general enough to work with different likelihood functions.
 *
 * Currently, the self-loop probability is not used.
 *
 * @author grenager
 *
 */
public class InfiniteHMM<P extends ConjugatePrior, E> {

  static int UNSEEN = -1;
  static Random random = new Random();

  int verbose;

  // free params
  double gamma;                             // chance of creating new dish overall
  double alpha;                             // chance of creating new table in restaurant
  double sigma;                             // probability of transitioning to self
  ConjugatePrior prior;                     // this is the prior that the emission distributions are drawn from
  int numIterations;

  // model params
  Map<Integer,ConjugatePrior> posteriors;
  TwoDimensionalCounter<Integer,E> observationCounts;   // "emission" counts, indexed by dish
  TwoDimensionalCounter<Integer,Integer> n;             // "transition" counts, indexed by "from" dish, then "to" dish

  // bookkeeping
  int newK;
  int boundaryK = 0;

  // observations
  List<E> observations;
  E boundarySymbol;

  // hidden states that are sampled
  int[] z;                                  // dish choice, indexed by observation
  TwoDimensionalCounter<Integer,Integer> m; // the number of tables, indexed by restaurant, then dish
  ClassicCounter<Integer> beta;                    // multinomial from which restaurants sample new dishes


  public int[] train(List<E> observations, E boundarySymbol, int numIterations) {
    this.observations = observations;
    this.boundarySymbol = boundarySymbol;
    if (verbose>3) System.err.println("Got observations: "+observations.size());
    init();
    for (int iter=1; iter<=numIterations; iter++) {
      System.err.println("Starting iteration " + iter);
      // sample
      sampleMs();
      sampleBeta();
      sampleZs(false);

      System.err.println("Completed iteration " + iter);
      // print
      checkModel();
      printModelParams();
    }

    return z;
  }


  private void printModelParams() {
    List<Integer> sorted = Counters.toSortedList(beta);
    int numClusters = 0;
    System.err.println("Transition Counts");
    for (int k : sorted) {
      if (k==newK || k==UNSEEN) continue;
      ClassicCounter<Integer> counts = n.getCounter(k);
      double totalCount = counts.totalCount();
      if (totalCount==0.0) continue;
      numClusters++;
      System.err.println("Cluster " + k + " ("+totalCount+"): " + Counters.toBiggestValuesFirstString(counts));
    }
    System.err.println("Number of clusters: " + numClusters);
    System.err.println();
    System.err.println("Observation Counts");
    for (int k : sorted) {
      if (k==newK || k==UNSEEN || k==0) continue;
      ClassicCounter<E> counts = observationCounts.getCounter(k);
      double totalCount = counts.totalCount();
      if (totalCount==0.0) continue;
      P posterior = (P) prior.getPosteriorDistribution(counts); // TODO: how to avoid this cast?
      System.err.println("Cluster " + k + ": " + posterior.toString());
    }
    System.err.println();
  }

  /**
   * Check the posteriors against the evidence.
   *
   */
  private void checkModel() {
    for (int k : posteriors.keySet()) {
      ConjugatePrior p1 = posteriors.get(k);
      ClassicCounter<double[]> o = (ClassicCounter<double[]>) observationCounts.getCounter(k);
      ConjugatePrior p2 = prior.getPosteriorDistribution(o);
      checkSimilarity(p1, p2);
    }
  }


  private void checkSimilarity(ConjugatePrior p1, ConjugatePrior p2) {
    Vector mean1 = ((GaussianMeanPrior)p1).getMean();
    Vector mean2 = ((GaussianMeanPrior)p2).getMean();
    Matrix var1 = ((GaussianMeanPrior)p1).getVar();
    Matrix var2 = ((GaussianMeanPrior)p2).getVar();
    for (int i=0; i<mean1.size(); i++) {
      if (Math.abs(mean1.get(i)-mean2.get(i))>1e-6) throw new RuntimeException();
      if (Math.abs(var1.get(i,i)-var2.get(i,i))>1e-6) throw new RuntimeException();
    }
  }


  private void init() {
    z = new int[observations.size()];
    // initialize beta
    beta = new ClassicCounter<Integer>();
    beta.setCount(UNSEEN, 1.0); // no tables and no dishes!
    getBeta(0); // make sure it can produce the boundary class
    // initialize counts
    n = new TwoDimensionalCounter<Integer, Integer>();
    posteriors = new HashMap<Integer,ConjugatePrior>();
    observationCounts = new TwoDimensionalCounter<Integer, E>();
    newK = 1;
    // now initialize z's
    sampleZs(true);
    // initialize m
    m = new TwoDimensionalCounter<Integer, Integer>();
  }


  /**
   * Sample a new beta from a multinomial defined by the m's.
   *
   */
  private void sampleBeta() {
    if (verbose>1) System.err.println("Sampling beta");
    ClassicCounter<Integer> params = new ClassicCounter<Integer>();
    for (int parent : m.firstKeySet()) {
      ClassicCounter<Integer> tableCounts = m.getCounter(parent);
      for (int dish : tableCounts.keySet()) {
        double numTables = tableCounts.getCount(dish);
        params.incrementCount(dish, numTables);
      }
    }
    params.incrementCount(UNSEEN, gamma);
    Multinomial<Integer> dist = Dirichlet.drawSample(random, params);
    // turn the Multinomial object back into a counter
    beta = new ClassicCounter<Integer>();
    for (int k : params.keySet()) {
      beta.setCount(k, dist.probabilityOf(k));
    }
    if (verbose>1) System.err.println("done. beta=" + beta);
  }


  /**
   * Run through the observations, assigning new dish to each.
   */
  private void sampleZs(boolean firstPass) {
    for (int i=1; i<observations.size()-1; i++) {
      E e = observations.get(i);
//      System.err.println("Sampling z at pos "+i+" with length " + ((double[])e).length);
      if (e==boundarySymbol) {
        // do nothing
        if (firstPass) updateCounts(i, 1.0, false, false);
      } else {
        if (!firstPass) updateCounts(i, -1.0, true, true);
        sampleZ(i, firstPass);
        updateCounts(i, 1.0, !firstPass, true);
      }
      if (i%100==0 && verbose>0) System.err.print(".");
    }
    if (verbose>0) System.err.println();
  }


  private int sampleZ(int i, boolean firstPass) {
    if (verbose>1) System.err.println("sampling " + i);
    int parentK = z[i-1];
    int childK = z[i+1];
    E obs = observations.get(i);
    // compute sampling distribution
    Counter<Integer> logDist = new ClassicCounter<Integer>();
    Counter<Integer> emissDist = new ClassicCounter<Integer>();
    Counter<Integer> transInDist = new ClassicCounter<Integer>();
    Counter<Integer> transOutDist = new ClassicCounter<Integer>();
    for (int k : beta.keySet()) { // the known k's
      if (k==UNSEEN || k==0) continue; // handled below
      double logEmissionProb = getLogEmissionProb(k, obs);
      emissDist.setCount(k, logEmissionProb);
      double transitionInProb = getTransitionProb(parentK, k);
      transInDist.setCount(k, transitionInProb);
      double transitionOutProb = 1.0;
      if (!firstPass) transitionOutProb = getTransitionProb(k, childK);
      transOutDist.setCount(k, transitionOutProb);
      if (verbose>3) System.err.println("k="+k + " emiss=" + logEmissionProb + " transIn=" + transitionInProb + " transOut=" + transitionOutProb);
      logDist.setCount(k, logEmissionProb+Math.log(transitionInProb)+Math.log(transitionOutProb));
    }
    if (!logDist.keySet().contains(newK)) { // sometimes it's already explicitly in beta, sometimes not
      // now the prob of the globally unseen cluster
      double emissionProb = getLogEmissionProb(newK, obs);
      emissDist.setCount(newK, emissionProb);
      double transitionInProb = getTransitionProb(parentK, newK);
      transInDist.setCount(newK, transitionInProb);
      double transitionOutProb = 1.0;
      if (!firstPass) transitionOutProb = getTransitionProb(newK, childK);
      transOutDist.setCount(newK, transitionOutProb);
      if (verbose>3) System.err.println("k="+newK + " emiss=" + emissionProb + " transIn=" + transitionInProb + " transOut=" + transitionOutProb);
      logDist.setCount(newK, emissionProb+Math.log(transitionInProb)+Math.log(transitionOutProb));
    }
    // now sample from it
    if (verbose>2) {
      System.err.println("emission probs: " + emissDist);
      System.err.println("transition in probs: " + transInDist);
      System.err.println("transition out probs: " + transOutDist);
    }
    Counters.logNormalizeInPlace(logDist);
    logDist = Counters.exp(logDist);
    if (verbose>2) System.err.println("sampling dist: " + Counters.toBiggestValuesFirstString(logDist));
    int k = Counters.sample(logDist);
    z[i] = k;
    if (k==newK) {
      newK = k+1;
    }
    if (verbose>1) System.err.println("sampled " + i + "=" + k);
    return k;
  }

  private double getLogEmissionProb(int k, E obs) {
    ConjugatePrior posterior = posteriors.get(k);
    if (posterior==null) {
      posterior = prior;
      posteriors.put(k, prior);
    }
    double emissionProb = posterior.getPredictiveLogProbability(obs); // posterior, conditioned on evidence
    if (Double.isNaN(emissionProb)) throw new RuntimeException("emissionProb="+emissionProb+" "+Arrays.toString((double[])obs) + " " + posterior);
    if (emissionProb==Double.NEGATIVE_INFINITY) System.err.println("Zero emission prob!!");
    return emissionProb;
  }

  // TODO: this is wrong, unseen prob is really rare
  private double getTransitionProb(int parentK, int k) {
    double b = getBeta(k);
    double numer = n.getCount(parentK, k) + b*alpha;
    double denom = n.totalCount(parentK) + alpha;
    if (verbose>3) System.err.println("trans "+parentK+" to "+k+": b=" + b + " alpha=" + alpha +
                       " count=" + n.getCount(parentK, k) + " total=" + n.totalCount(parentK) +
                       " numer=" + numer + " denom=" + denom);
    double result = (numer/denom);
    return result;
  }


  private double getBeta(int k) {
    double count = beta.getCount(k);
    if (count>0.0) return count;
    // otherwise, stickbreak beta and return the value
    double betaU = beta.getCount(UNSEEN);
    double sample = Dirichlet.sampleBeta(1.0, gamma, random); // a breakpoint, in [0, 1]
    if (verbose>3) System.err.println("gamma is " + gamma + " and sampled " + sample);
    double betaK = betaU*sample;
    beta.setCount(k, betaK);
    beta.setCount(UNSEEN, betaU-betaK); // remainder
    if (verbose>1) System.err.println("Making new beta: sample=" + sample +" beta=" + beta);
    // now beta should still sum to 1.0
    if (!SloppyMath.isCloseTo(beta.totalCount(),1.0)) throw new RuntimeException("beta total: " + beta.totalCount());
    return betaK;
  }

  /**
   * Count may be negative, in which case we're removing all counts associated with
   * position i from the model.
   *
   */
  private void updateCounts(int i, double count, boolean countOutbound, boolean countEmissions) {
    // first, add to the observations
    if (countEmissions) {
//      if (count<0.0) System.err.println("Negative update!");
      // add to observation counts
      observationCounts.incrementCount(z[i], observations.get(i), count);
      // add to the posterior over the likelihood
      ClassicCounter<E> newObs = new ClassicCounter<E>();
      newObs.incrementCount(observations.get(i), count);
      ConjugatePrior oldDist = posteriors.get(z[i]);
      if (oldDist==null) {
        oldDist = prior;
      }
      ConjugatePrior posterior = oldDist.getPosteriorDistribution(newObs);
      posteriors.put(z[i], posterior);
    }
    // next, add inbound transition
    n.incrementCount(z[i-1], z[i], count);
    // next, add outbound transition
    if (countOutbound) {
      n.incrementCount(z[i], z[i+1], count);
    }
  }


  /**
   * Figure out how many tables there are for each dish in each restaurant.
   * This is needed for resampling beta.
   *
   */
  private void sampleMs() {
    if (verbose>1) System.err.println("sampling m");
    m = new TwoDimensionalCounter<Integer, Integer>();
    for (int parent : n.firstKeySet()) {
      ClassicCounter<Integer> kidCounts = n.getCounter(parent);
      for (int child : kidCounts.keySet()) {
        int numKids = (int) kidCounts.getCount(child);
        // now walk through these kids, and seat them at a table!
        int numTables = 1; // there must be at least one table
        for (int i=1; i<numKids; i++) { // skip the first guy
          int newTable = 0;
          if (random.nextDouble() < (alpha / (alpha + i))) {
            newTable = 1;
          }
          numTables += newTable;
        }
        // now we have the number of tables
        m.setCount(parent, child, numTables);
      }
    }
  }

  public int[] getBestSequence(List<E> observations) {
    System.err.print("Getting best sequence using Viterbi");
    int[] fields = new int[observations.size()];
    // compute log delta values, and put in the logForwardProbs[] array
    int[][] backPointers = new int[observations.size()][newK];
    double[] thisProbs = new double[newK];
    double[] lastProbs = new double[newK];
    // put all prob in first position on state 0
    for (int i = 0; i < observations.size(); i++) { // go through all positions forwards
      E e = observations.get(i);
//    System.err.println(i+": " + word);
      Arrays.fill(thisProbs, 0.0); // clear the probs
      if (e == boundarySymbol) {
        Arrays.fill(thisProbs, Double.NEGATIVE_INFINITY);
        thisProbs[boundaryK] = 0.0;
        int maxIndex = ArrayMath.argmax(lastProbs);
        backPointers[i][boundaryK] = maxIndex; // we only need a back pointer for beginDoc, the rest won't be looked at
      } else {
        for (int curr = 0; curr < newK; curr++) { // go through all fields for this position
//        System.err.println("curr=" + curr);
          if (curr == boundaryK) {
            thisProbs[curr] = Double.NEGATIVE_INFINITY; // shouldn't matter!
          } else {
            double maxProb = Double.NEGATIVE_INFINITY;
            int maxIndex = -1;
            double logEmissionProb = getLogEmissionProb(curr, e);
            for (int prev = 0; prev < newK; prev++) { // max over all fields for last position
              double logTransitionProb = Math.log(getTransitionProb(prev, curr));
              double prob = lastProbs[prev] + logTransitionProb + logEmissionProb;
//              System.err.println(prev+"->"+curr+": "+prob + " = " + lastProbs[prev] + " + " +logTransitionProb +" + "+logEmissionProb);
              if (prob > maxProb) {
                maxProb = prob;
                maxIndex = prev;
              }
            }
//            System.err.println("logEmissionProb="+logEmissionProb);
            backPointers[i][curr] = maxIndex; // cast is safe because numFields < 256
            thisProbs[curr] = maxProb;
          }
        } // end loop over curr field
      } // end else is a good word
      double max = ArrayMath.max(thisProbs);
      ArrayMath.addInPlace(thisProbs, -max);  // do a pseudo log normalize to keep values near zero
//      System.err.println(i + ": " + ArrayMath.toString(thisProbs) + "\t" + ArrayMath.toString(backPointers[i]) + "\n");
      // switch the pointers without allocating more memory
      double[] temp = lastProbs;
      lastProbs = thisProbs; // hold on to this one so we keep a record of what happened
      thisProbs = temp; // will be overwritten next time
      if (i%100==0) System.err.print(".");
    } // end loop over all positions
    // we know the field of the last word because it's the end of a doc
    fields[fields.length - 1] = ArrayMath.argmax(lastProbs);

    // now reconstruct the best sequence by following backpointers backwards
    for (int i = fields.length - 2; i >= 0; i--) {
      fields[i] = backPointers[i + 1][fields[i + 1]];
      if (fields[i] < 0 || fields[i] >= newK) {
        System.err.println("bad backpointer at " + i);
        for (int j = i; j < observations.size(); j++) {
          System.err.println(j + ": " + observations.get(j) + " " + ArrayMath.toString(backPointers[j]));
        }
        throw new RuntimeException();
      }
    }
    System.err.println("done.");
    return fields;
  }


  /**
   */
  public InfiniteHMM(P g, double gamma, double alpha, double sigma, int verbose) {
    this.prior = g;
    this.gamma = gamma;
    this.alpha = alpha;
    this.sigma = sigma;
    this.verbose = verbose;
  }


}
