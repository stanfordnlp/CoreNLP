package edu.stanford.nlp.loglinear.learning; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import edu.stanford.nlp.util.RuntimeInterruptedException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created on 8/26/15.
 * @author keenon
 * <p>
 * Abstract base of all the different kinds of optimizers. This exists to both facilitate sharing test between optimizers
 * and to share certain basic bits of functionality useful for batch optimizers, like intelligent multi-thread management
 * and user interrupt handling.
 */
public abstract class AbstractBatchOptimizer  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AbstractBatchOptimizer.class);
  public <T> ConcatVector optimize(T[] dataset, AbstractDifferentiableFunction<T> fn) {
    return optimize(dataset, fn, new ConcatVector(0), 0.0, 1.0e-5, false);
  }

  public <T> ConcatVector optimize(T[] dataset,
                                   AbstractDifferentiableFunction<T> fn,
                                   ConcatVector initialWeights,
                                   double l2regularization,
                                   double convergenceDerivativeNorm,
                                   boolean quiet) {
    if (!quiet) log.info("\n**************\nBeginning training\n");
    else log.info("[Beginning quiet training]");

    TrainingWorker<T> mainWorker = new TrainingWorker<>(dataset, fn, initialWeights, l2regularization, convergenceDerivativeNorm, quiet);
    new Thread(mainWorker).start();

    BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

    if (!quiet) {
      log.info("NOTE: you can press any key (and maybe ENTER afterwards to jog stdin) to terminate learning early.");
      log.info("The convergence criteria are quite aggressive if left uninterrupted, and will run for a while");
      log.info("if left to their own devices.\n");

      while (true) {
        if (mainWorker.isFinished) {
          log.info("training completed without interruption");
          return mainWorker.weights;
        }
        try {
          if (br.ready()) {
            log.info("received quit command: quitting");
            log.info("training completed by interruption");
            mainWorker.isFinished = true;
            return mainWorker.weights;
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    } else {
      while (!mainWorker.isFinished) {
        synchronized (mainWorker.naturalTerminationBarrier) {
          try {
            mainWorker.naturalTerminationBarrier.wait();
          } catch (InterruptedException e) {
            throw new RuntimeInterruptedException(e);
          }
        }
      }
      log.info("[Quiet training complete]");
      return mainWorker.weights;
    }
  }

  List<Constraint> constraints = new ArrayList<>();

  /**
   * This adds a constraint on the weight vector, that a certain component must be set to a sparse index=value
   *
   * @param component the component to fix
   * @param index     the index of the fixed sparse component
   * @param value     the value to fix at
   */
  public void addSparseConstraint(int component, int index, double value) {
    constraints.add(new Constraint(component, index, value));
  }

  /**
   * This adds a constraint on the weight vector, that a certain component must be set to a dense array
   *
   * @param component the component to fix
   * @param arr       the dense array to set
   */
  public void addDenseConstraint(int component, double[] arr) {
    constraints.add(new Constraint(component, arr));
  }

  /**
   * A way to record a constraint on the weight vector
   */
  private static class Constraint {
    int component;
    boolean isSparse;

    int index;
    double value;

    double[] arr;

    public Constraint(int component, int index, double value) {
      isSparse = true;
      this.component = component;
      this.index = index;
      this.value = value;
    }

    public Constraint(int component, double[] arr) {
      isSparse = false;
      this.component = component;
      this.arr = arr;
    }

    public void applyToWeights(ConcatVector weights) {
      if (isSparse) {
        weights.setSparseComponent(component, index, value);
      } else {
        weights.setDenseComponent(component, arr);
      }
    }

    public void applyToDerivative(ConcatVector derivative) {
      if (isSparse) {
        derivative.setSparseComponent(component, index, 0.0);
      } else {
        derivative.setDenseComponent(component, new double[]{0.0});
      }
    }
  }

  /**
   * This is the hook for subclassing batch optimizers to override in order to have their optimizer work.
   *
   * @param weights       the current weights (update these in place)
   * @param gradient      the gradient at these weights
   * @param logLikelihood the log likelihood at these weights
   * @param state         any saved state the optimizer wants to keep and pass around during each optimization run
   * @param quiet         whether or not to dump output about progress to the console
   * @return whether or not we've converged
   */
  public abstract boolean updateWeights(ConcatVector weights, ConcatVector gradient, double logLikelihood, OptimizationState state, boolean quiet);

  /**
   * This is subclassed by children to store any state they need to perform optimization
   */
  protected abstract class OptimizationState {
  }

  /**
   * This is called at the beginning of each batch optimization. It should return a fresh OptimizationState object that
   * will then be handed to updateWeights() on each update.
   *
   * @param initialWeights the initial weights for the optimizer to use
   * @return a fresh OptimizationState
   */
  protected abstract OptimizationState getFreshOptimizationState(ConcatVector initialWeights);

  private static class GradientWorker<T> implements Runnable {
    ConcatVector localDerivative;
    double localLogLikelihood = 0.0;

    TrainingWorker mainWorker;
    int threadIdx;
    int numThreads;
    List<T> queue;
    AbstractDifferentiableFunction<T> fn;
    ConcatVector weights;

    long jvmThreadId = 0;

    // This is to help the dynamic re-balancing of work queues
    long finishedAtTime = 0;
    long cpuTimeRequired = 0;

    public GradientWorker(TrainingWorker<T> mainWorker, int threadIdx, int numThreads, List<T> queue, AbstractDifferentiableFunction<T> fn, ConcatVector weights) {
      this.mainWorker = mainWorker;
      this.threadIdx = threadIdx;
      this.numThreads = numThreads;
      this.queue = queue;
      this.fn = fn;
      this.weights = weights;

      localDerivative = weights.newEmptyClone();
    }

    @Override
    public void run() {
      long startTime = ManagementFactory.getThreadMXBean().getThreadCpuTime(jvmThreadId);

      for (T datum : queue) {
        localLogLikelihood += fn.getSummaryForInstance(datum, weights, localDerivative);
        // Check for user interrupt
        if (mainWorker.isFinished) return;
      }

      finishedAtTime = System.currentTimeMillis();

      long endTime = ManagementFactory.getThreadMXBean().getThreadCpuTime(jvmThreadId);
      cpuTimeRequired = endTime - startTime;
    }
  }

  private class TrainingWorker<T> implements Runnable {
    ConcatVector weights;
    OptimizationState optimizationState;

    boolean isFinished = false;

    boolean useThreads = Runtime.getRuntime().availableProcessors() > 1;

    T[] dataset;
    AbstractDifferentiableFunction<T> fn;
    double l2regularization;
    double convergenceDerivativeNorm;
    boolean quiet;

    final Object naturalTerminationBarrier = new Object();

    public TrainingWorker(T[] dataset, AbstractDifferentiableFunction<T> fn, ConcatVector initialWeights, double l2regularization, double convergenceDerivativeNorm, boolean quiet) {
      optimizationState = getFreshOptimizationState(initialWeights);
      weights = initialWeights.deepClone();

      this.dataset = dataset;
      this.fn = fn;
      this.l2regularization = l2regularization;
      this.convergenceDerivativeNorm = convergenceDerivativeNorm;
      this.quiet = quiet;
    }

    /**
     * This lets the system allocate work to threads evenly, which reduces the amount of blocking and can improve
     * runtimes by 20% or more.
     *
     * @param datum the datum to estimate work for
     * @return a work estimate, on a relative scale of single cpu wall time, for getting the gradient and log-likelihood
     */
    private int estimateRelativeRuntime(T datum) {
      if (datum instanceof GraphicalModel) {
        int cost = 0;
        GraphicalModel model = (GraphicalModel) datum;
        for (GraphicalModel.Factor f : model.factors) {
          cost += f.featuresTable.combinatorialNeighborStatesCount();
        }
        return cost;
      } else return 1;
    }

    @Override
    public void run() {

      // Multithreading stuff

      int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors());
      @SuppressWarnings("unchecked")
      List<T>[] queues = (List<T>[]) (new List[numThreads]);
      Random r = new Random();

      // Allocate work to make estimated cost of work per thread as even as possible

      if (useThreads) {
        for (int i = 0; i < numThreads; i++) {
          queues[i] = new ArrayList<>();
        }
        int[] queueEstimatedTotalCost = new int[numThreads];

        for (T datum : dataset) {
          int datumEstimatedCost = estimateRelativeRuntime(datum);

          int minCostQueue = 0;
          for (int i = 0; i < numThreads; i++) {
            if (queueEstimatedTotalCost[i] < queueEstimatedTotalCost[minCostQueue]) minCostQueue = i;
          }

          queueEstimatedTotalCost[minCostQueue] += datumEstimatedCost;
          queues[minCostQueue].add(datum);
        }
      }

      while (!isFinished) {

        // Collect log-likelihood and derivatives

        long startTime = System.currentTimeMillis();
        long threadWaiting = 0;

        ConcatVector derivative = weights.newEmptyClone();
        double logLikelihood = 0.0;

        if (useThreads) {
          GradientWorker[] workers = new GradientWorker[numThreads];
          Thread[] threads = new Thread[numThreads];
          for (int i = 0; i < workers.length; i++) {
            workers[i] = new GradientWorker(this, i, numThreads, queues[i], fn, weights);
            threads[i] = new Thread(workers[i]);
            workers[i].jvmThreadId = threads[i].getId();
            threads[i].start();
          }

          // This is for logging
          long minFinishTime = Long.MAX_VALUE;
          long maxFinishTime = Long.MIN_VALUE;

          // This is for re-balancing
          long minCPUTime = Long.MAX_VALUE;
          long maxCPUTime = Long.MIN_VALUE;
          int slowestWorker = 0;
          int fastestWorker = 0;

          for (int i = 0; i < workers.length; i++) {
            try {
              threads[i].join();
            } catch (InterruptedException e) {
              throw new RuntimeInterruptedException(e);
            }
            logLikelihood += workers[i].localLogLikelihood;
            derivative.addVectorInPlace(workers[i].localDerivative, 1.0);

            if (workers[i].finishedAtTime < minFinishTime) {
              minFinishTime = workers[i].finishedAtTime;
            }
            if (workers[i].finishedAtTime > maxFinishTime) {
              maxFinishTime = workers[i].finishedAtTime;
            }

            if (workers[i].cpuTimeRequired < minCPUTime) {
              fastestWorker = i;
              minCPUTime = workers[i].cpuTimeRequired;
            }
            if (workers[i].cpuTimeRequired > maxCPUTime) {
              slowestWorker = i;
              maxCPUTime = workers[i].cpuTimeRequired;
            }
          }
          threadWaiting = maxFinishTime - minFinishTime;

          // Try to reallocate work dynamically to minimize waiting on subsequent rounds

          // Figure out the percentage of work represented by the waiting
          double waitingPercentage = (double) (maxCPUTime - minCPUTime) / (double) maxCPUTime;
          int needTransferItems = (int) Math.floor(queues[slowestWorker].size() * waitingPercentage * 0.5);
          for (int i = 0; i < needTransferItems; i++) {
            int toTransfer = r.nextInt(queues[slowestWorker].size());
            T datum = queues[slowestWorker].get(toTransfer);
            queues[slowestWorker].remove(toTransfer);
            queues[fastestWorker].add(datum);
          }

          // Check for user interrupt
          if (isFinished) return;
        } else {
          for (T datum : dataset) {
            assert (datum != null);
            logLikelihood += fn.getSummaryForInstance(datum, weights, derivative);
            // Check for user interrupt
            if (isFinished) return;
          }
        }

        logLikelihood /= dataset.length;
        derivative.mapInPlace((d) -> d / dataset.length);

        long gradientComputationTime = System.currentTimeMillis() - startTime;

        // Regularization

        logLikelihood = logLikelihood - (l2regularization * weights.dotProduct(weights));
        derivative.addVectorInPlace(weights, -2 * l2regularization);

        // Zero out the derivative on the components we're holding fixed

        for (Constraint constraint : constraints) {
          constraint.applyToDerivative(derivative);
        }

        // If our derivative is sufficiently small, we've converged

        double derivativeNorm = derivative.dotProduct(derivative);
        if (derivativeNorm < convergenceDerivativeNorm) {
          if (!quiet)
            log.info("Derivative norm " + derivativeNorm + " < " + convergenceDerivativeNorm + ": quitting");
          break;
        }

        // Do the actual computation

        if (!quiet) {
          log.info("[" + gradientComputationTime + " ms, threads waiting " + threadWaiting + " ms]");
        }
        boolean converged = updateWeights(weights, derivative, logLikelihood, optimizationState, quiet);

        // Apply constraints to the weights vector

        for (Constraint constraint : constraints) {
          constraint.applyToWeights(weights);
        }

        if (converged) {
          break;
        }
      }

      synchronized (naturalTerminationBarrier) {
        naturalTerminationBarrier.notifyAll();
      }
      isFinished = true;
    }
  }
}
