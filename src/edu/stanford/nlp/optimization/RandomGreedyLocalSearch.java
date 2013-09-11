package edu.stanford.nlp.optimization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * @author Galen Andrew (galand@cs.stanford.edu) Date: Apr 26, 2004
 *         For optimizing functions with ranges 0...infty
 *         Takes random multiplicative steps with distribution N(o,r^2)
 *         Where r is the learn rate
 *         stops when (dim+2)*perserverence steps have been taken with no improvement
 *         functionTolerance is ignored
 */
public class RandomGreedyLocalSearch implements Minimizer {

  double learnRate = 0.5;
  double decayRate = 0.99;
  double curTime;
  double updateRate = 1.0;
  int maxIterations = -1;
  Random random = new Random();
  protected int perserverence = 6;
  boolean tryZeros = false;


  public RandomGreedyLocalSearch(double learnRate, int perserverence, int maxIterations) {
    this.learnRate = learnRate;
    this.perserverence = perserverence;
    this.maxIterations = maxIterations;
  }

  public RandomGreedyLocalSearch(double learnRate, int perserverence) {
    this.learnRate = learnRate;
    this.perserverence = perserverence;
  }

    /**
       ignores maxIterations for now.
    **/
    public double[] minimize(Function function, double functionTolerance, double[] initial, int maxIterations) {
	return minimize(function, functionTolerance, initial);
    }

  public double[] minimize(Function function, double functionTolerance, double[] initial) {
    RandomFunction randFunc = null;
    if (function instanceof RandomFunction) {
      randFunc = (RandomFunction) function;
    }
    int dim = function.domainDimension();
    List<double[]> current = new ArrayList<double[]>();
    current.add(initial.clone());
    double currentVal = 0;
    if (randFunc == null) {
      currentVal = function.valueAt(initial);
    }
    int nextPos = 0;

    int noPosStep = 0;
    int it=-1;
    boolean done = false;
    do {
      boolean verbose = false;
      if(++it == maxIterations) {
        System.err.printf("Reached maximum number of iterations: %d\n", maxIterations);
        verbose = done = true;
      }

      if((System.currentTimeMillis() - curTime)/1000.0 > updateRate) {
        curTime = System.currentTimeMillis();
        verbose = true;
      }

      if (randFunc != null) {
        randFunc.randomize();
        currentVal = randFunc.valueAt(current.get(nextPos));
      }

      double[] next = current.get(nextPos);
      double[] guess = next.clone();
      // pick a direction
      double[] step = new double[dim];
      for (int i = 0; i < dim; i++) {
        step[i] = learnRate * random.nextGaussian();
      }

      double val;

      for (int i = 0; i < guess.length; i++) {
        guess[i] = guess[i] * Math.exp(step[i]);
      }
      val = function.valueAt(guess);

      if(verbose) {
        System.err.printf("value=%.3f at [",val);
        for(int i=0; i<guess.length; ++i) {
          if(i>0)
            System.err.print(",");
          System.err.print((float)guess[i]);
        }
        System.err.printf("]\n");
      }

      if (val <= currentVal) {
        if (val < currentVal) {
          if (verbose) {
            System.out.println("RGLS: New best score: " + val);
          }
          noPosStep = 0;
          current.clear();
          nextPos = 0;
        }

        current.add(guess.clone());
        currentVal = val;
        if (verbose) {
          System.out.println("RGLS: Added new point " + Arrays.toString(guess) + ". Total: " + current.size());
        }
      }
      if (verbose) {
        System.out.println("RGLS: No positive steps in " + noPosStep + " iterations.");
      }
      noPosStep++;
      if (++nextPos >= current.size()) {
        nextPos = 0;
      }
      learnRate *= decayRate;
    } while (noPosStep < (dim + 2) * perserverence && !done);

    if (tryZeros) {
      double[] best = current.get(0).clone();
      for (int i = 0; i < dim; i++) {
        double[] guess = best.clone();
        double val = function.valueAt(guess);
        if (val < currentVal) {
          current.clear();
          currentVal = val;
          current.add(guess.clone());
        }
      }
    }

    double[] best = current.get(0);
    System.out.println("RGLS: returning point " + Arrays.toString(best) + " with score " + currentVal);
    return best;
  }
}
