package edu.stanford.nlp.optimization;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.CallbackFunction;
import edu.stanford.nlp.util.logging.Redwood;


/**
 *
 * An implementation of L-BFGS for Quasi Newton unconstrained minimization.
 * Also now has support for OWL-QN (Orthant-Wise Limited memory Quasi Newton)
 * for L1 regularization.
 *
 * The general outline of the algorithm is taken from:
 * <blockquote>
 * <i>Numerical Optimization</i> (second edition) 2006
 * Jorge Nocedal and Stephen J. Wright
 * </blockquote>
 * A variety of different options are available.
 *
 * <h3>LINESEARCHES</h3>
 *
 * BACKTRACKING: This routine
 * simply starts with a guess for step size of 1. If the step size doesn't
 * supply a sufficient decrease in the function value the step is updated
 * through step = 0.1*step. This method is certainly simpler, but doesn't allow
 * for an increase in step size, and isn't well suited for Quasi Newton methods.
 *
 * MINPACK: This routine is based off of the implementation used in MINPACK.
 * This routine finds a point satisfying the Wolfe conditions, which state that
 * a point must have a sufficiently smaller function value, and a gradient of
 * smaller magnitude. This provides enough to prove theoretically quadratic
 * convergence. In order to find such a point the line search first finds an
 * interval which must contain a satisfying point, and then progressively
 * reduces that interval all using cubic or quadratic interpolation.
 *
 * SCALING: L-BFGS allows the initial guess at the hessian to be updated at each
 * step. Standard BFGS does this by approximating the hessian as a scaled
 * identity matrix. To use this method set the scaleOpt to SCALAR. A better way
 * of approximate the hessian is by using a scaling diagonal matrix. The
 * diagonal can then be updated as more information comes in. This method can be
 * used by setting scaleOpt to DIAGONAL.
 *
 * CONVERGENCE: Previously convergence was gauged by looking at the average
 * decrease per step dividing that by the current value and terminating when
 * that value because smaller than TOL. This method fails when the function
 * value approaches zero, so two other convergence criteria are used. The first
 * stores the initial gradient norm |g0|, then terminates when the new gradient
 * norm, |g| is sufficiently smaller: i.e., |g| &lt; eps*|g0| the second checks if
 * |g| &lt; eps*max( 1 , |x| ) which is essentially checking to see if the gradient
 * is numerically zero.
 * Another convergence criteria is added where termination is triggered if no
 * improvements are observed after X (set by terminateOnEvalImprovementNumOfEpoch)
 * iterations over some validation test set as evaluated by Evaluator
 *
 * Each of these convergence criteria can be turned on or off by setting the
 * flags:
 * <blockquote><code>
 * private boolean useAveImprovement = true;
 * private boolean useRelativeNorm = true;
 * private boolean useNumericalZero = true;
 * private boolean useEvalImprovement = false;
 * </code></blockquote>
 *
 * To use the QNMinimizer first construct it using
 * <blockquote><code>
 * QNMinimizer qn = new QNMinimizer(mem, true)
 * </code></blockquote>
 * mem - the number of previous estimate vector pairs to
 * store, generally 15 is plenty. true - this tells the QN to use the MINPACK
 * linesearch with DIAGONAL scaling. false would lead to the use of the criteria
 * used in the old QNMinimizer class.
 *
 * Then call:
 * <blockquote><code>
 * qn.minimize(dfunction,convergenceTolerance,initialGuess,maxFunctionEvaluations);
 * </code></blockquote>
 *
 * @author akleeman
 */

public class QNMinimizer implements Minimizer<DiffFunction>, HasEvaluators  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(QNMinimizer.class);

  private int fevals = 0; // the number of function evaluations
  private int maxFevals = -1;
  private int mem = 10; // the number of s,y pairs to retain for BFGS
  private int its; // = 0; // the number of iterations through the main do-while loop of L-BFGS's minimize()
  private final Function monitor;
  private boolean quiet; // = false
  private static final NumberFormat nf = new DecimalFormat("0.000E0");
  private static final NumberFormat nfsec = new DecimalFormat("0.00"); // for times
  private static final double ftol = 1e-4; // Linesearch parameters
  private double gtol = 0.9;
  private static final double aMin = 1e-12; // Min step size
  private static final double aMax = 1e12; // Max step size
  private static final double p66 = 0.66; // used to check getting more than 2/3 of width improvement
  private static final double p5 = 0.5; // Some other magic constant
  private static final int a = 0;  // used as array index
  private static final int f = 1;  // used as array index
  private static final int g = 2;  // used as array index
  public boolean outputToFile = false;
  private boolean success = false;
  private boolean bracketed = false; // used for linesearch
  private QNInfo presetInfo = null;
  private boolean noHistory = true;

  // parameters for OWL-QN (L-BFGS with L1-regularization)
  private boolean useOWLQN = false;
  private double lambdaOWL = 0;

  private boolean useAveImprovement = true;
  private boolean useRelativeNorm = true;
  private boolean useNumericalZero = true;
  private boolean useEvalImprovement = false;
  private boolean useMaxItr = false;
  private int maxItr = 0;

  private boolean suppressTestPrompt = false;
  private int terminateOnEvalImprovementNumOfEpoch = 1;

  private int evaluateIters = 0;    // Evaluate every x iterations (0 = no evaluation)
  private int startEvaluateIters = 0; // starting evaluation after x iterations
  private Evaluator[] evaluators;  // separate set of evaluators to check how optimization is going

  private transient CallbackFunction iterCallbackFunction = null;

  private enum eState {
    TERMINATE_MAXEVALS, TERMINATE_RELATIVENORM, TERMINATE_GRADNORM, TERMINATE_AVERAGEIMPROVE, CONTINUE, TERMINATE_EVALIMPROVE, TERMINATE_MAXITR
  }

  private enum eLineSearch {
    BACKTRACK, MINPACK
  }

  private enum eScaling {
    DIAGONAL, SCALAR
  }

  private eLineSearch lsOpt = eLineSearch.MINPACK;
  private eScaling scaleOpt = eScaling.DIAGONAL;


  public QNMinimizer() {
    this((Function) null);
  }

  public QNMinimizer(int m) {
    this(null, m);
  }

  public QNMinimizer(int m, boolean useRobustOptions) {
    this(null, m, useRobustOptions);
  }

  public QNMinimizer(Function monitor) {
    this.monitor = monitor;
  }

  public QNMinimizer(Function monitor, int m) {
    this(monitor, m, false);
  }

  public QNMinimizer(Function monitor, int m, boolean useRobustOptions) {
    this.monitor = monitor;
    mem = m;
    if (useRobustOptions) {
      this.setRobustOptions();
    }
  }

  public QNMinimizer(FloatFunction monitor) {
    throw new UnsupportedOperationException("Doesn't support floats yet");
  }

  public void setOldOptions() {
    useAveImprovement = true;
    useRelativeNorm = false;
    useNumericalZero = false;
    lsOpt = eLineSearch.BACKTRACK;
    scaleOpt = eScaling.SCALAR;
  }

  public final void setRobustOptions() {
    useAveImprovement = true;
    useRelativeNorm = true;
    useNumericalZero = true;
    lsOpt = eLineSearch.MINPACK;
    scaleOpt = eScaling.DIAGONAL;
  }

  @Override
  public void setEvaluators(int iters, Evaluator[] evaluators) {
    this.evaluateIters = iters;
    this.evaluators = evaluators;
  }

  public void setEvaluators(int iters, int startEvaluateIters, Evaluator[] evaluators) {
    this.evaluateIters = iters;
    this.startEvaluateIters = startEvaluateIters;
    this.evaluators = evaluators;
  }

  public void setIterationCallbackFunction(CallbackFunction func){
    iterCallbackFunction = func;
  }

  public void terminateOnRelativeNorm(boolean toTerminate) {
    useRelativeNorm = toTerminate;
  }

  public void terminateOnNumericalZero(boolean toTerminate) {
    useNumericalZero = toTerminate;
  }

  public void terminateOnAverageImprovement(boolean toTerminate) {
    useAveImprovement = toTerminate;
  }

  public void terminateOnEvalImprovement(boolean toTerminate) {
    useEvalImprovement = toTerminate;
  }

  public void terminateOnMaxItr(int maxItr) {
    if (maxItr > 0) {
      useMaxItr = true;
      this.maxItr = maxItr;
    }
  }

  public void suppressTestPrompt(boolean suppressTestPrompt) {
    this.suppressTestPrompt = suppressTestPrompt;
  }

  public void setTerminateOnEvalImprovementNumOfEpoch(int terminateOnEvalImprovementNumOfEpoch) {
    this.terminateOnEvalImprovementNumOfEpoch = terminateOnEvalImprovementNumOfEpoch;
  }

  public void useMinPackSearch() {
    lsOpt = eLineSearch.MINPACK;
  }

  public void useBacktracking() {
    lsOpt = eLineSearch.BACKTRACK;
  }

  public void useDiagonalScaling() {
    scaleOpt = eScaling.DIAGONAL;
  }

  public void useScalarScaling() {
    scaleOpt = eScaling.SCALAR;
  }

  public boolean wasSuccessful() {
    return success;
  }

  public void shutUp() {
    this.quiet = true;
  }
  public void setM(int m) {
    mem = m;
  }

  public static class SurpriseConvergence extends Exception {

    private static final long serialVersionUID = 4290178321643529559L;

    public SurpriseConvergence(String s) {
      super(s);
    }
  }

  private static class MaxEvaluationsExceeded extends Exception {

    private static final long serialVersionUID = 8044806163343218660L;

    public MaxEvaluationsExceeded(String s) {
      super(s);
    }
  }

  /**
   * The Record class is used to collect information about the function value
   * over a series of iterations. This information is used to determine
   * convergence, and to (attempt to) ensure numerical errors are not an issue.
   * It can also be used for plotting the results of the optimization routine.
   *
   * @author akleeman
   */
  class Record {
    // convergence options.
    // have average difference like before
    // zero gradient.

    // for convergence test
    private final List<Double> evals = new ArrayList<>(100);
    private final List<Double> values = new ArrayList<>(100);
    private List<Double> gNorms = new ArrayList<>(100);
    // List<Double> xNorms = new ArrayList<Double>(100);
    private final List<Integer> funcEvals = new ArrayList<>(100);
    private final List<Double> time = new ArrayList<>(100);
    // gNormInit: This makes it so that if for some reason
    // you try and divide by the initial norm before it's been
    // initialized you don't get a NAN but you will also never
    // get false convergence.
    private double gNormInit = Double.MIN_VALUE;
    private double relativeTOL = 1e-8;
    private double TOL = 1e-6;
    private double EPS = 1e-6;
    private long startTime;
    private double gNormLast; // This is used for convergence.
    private double[] xLast;
    private int maxSize = 100; // This will control the number of func values /
    // gradients to retain.
    private Function mon = null;
    private boolean memoryConscious = true;
    private PrintWriter outputFile = null;

    // private int noImproveItrCount = 0;
    private double[] xBest;

    Record(Function monitor, double tolerance, PrintWriter output) {
      this.mon = monitor;
      this.TOL = tolerance;
      this.outputFile = output;
    }

    Record(Function monitor, double tolerance, double eps) {
      this.mon = monitor;
      this.TOL = tolerance;
      this.EPS = eps;
    }

    void setEPS(double eps) {
      EPS = eps;
    }

    void setTOL(double tolerance) {
      TOL = tolerance;
    }

    void start(double val, double[] grad) {
      start(val, grad, null);
    }

    /*
     * Initialize the class, this starts the timer, and initiates the gradient
     * norm for use with convergence.
     */
    void start(double val, double[] grad, double[] x) {
      startTime = System.currentTimeMillis();
      gNormInit = ArrayMath.norm(grad);
      xLast = x;
      writeToFile(1, val, gNormInit, 0.0);

      if (x != null) {
        monitorX(x);
      }
    }

    private void writeToFile(double fevals, double val, double gNorm,
                             double time) {
      if (outputFile != null) {
        outputFile.println(fevals + "," + val + ',' + gNorm + ',' + time);
      }
    }

    private void add(double val, double[] grad, double[] x, int fevals, double evalScore, StringBuilder sb) {

      if (!memoryConscious) {
        if (gNorms.size() > maxSize) {
          gNorms.remove(0);
        }
        if (time.size() > maxSize) {
          time.remove(0);
        }
        if (funcEvals.size() > maxSize) {
          funcEvals.remove(0);
        }
        gNorms.add(gNormLast);
        time.add(howLong());
        funcEvals.add(fevals);
      } else {
        maxSize = 10;
      }

      gNormLast = ArrayMath.norm(grad);
      if (values.size() > maxSize) {
        values.remove(0);
      }

      values.add(val);

      if (evalScore != Double.NEGATIVE_INFINITY)
        evals.add(evalScore);

      writeToFile(fevals, val, gNormLast, howLong());

      sb.append(nf.format(val)).append(' ').append(nfsec.format(howLong())).append('s');

      xLast = x;
      monitorX(x);
    }

    void monitorX(double[] x) {
      if (this.mon != null) {
        this.mon.valueAt(x);
      }
    }

    /**
     * This function checks for convergence through first
     * order optimality,  numerical convergence (i.e., zero numerical
     * gradient), and also by checking the average improvement.
     *
     * @return A value of the enumeration type <b>eState</b> which tells the
     *   state of the optimization routine indicating whether the routine should
     *   terminate, and if so why.
     */
    private eState toContinue(StringBuilder sb) {

      double relNorm = gNormLast / gNormInit;
      int size = values.size();
      double newestVal = values.get(size - 1);
      double previousVal = (size >= 10 ? values.get(size - 10) : values.get(0));
      double averageImprovement = (previousVal - newestVal) / (size >= 10 ? 10 : size);
      int evalsSize = evals.size();

      if (useMaxItr && its >= maxItr)
        return eState.TERMINATE_MAXITR;

      if (useEvalImprovement) {
        int bestInd = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < evalsSize; i++) {
          if (evals.get(i) >= bestScore) {
            bestScore = evals.get(i);
            bestInd = i;
          }
        }
        if (bestInd == evalsSize-1) { // copy xBest
          if (xBest == null)
            xBest = Arrays.copyOf(xLast, xLast.length);
          else
            System.arraycopy( xLast, 0, xBest, 0, xLast.length );
        }
        if ((evalsSize - bestInd) >= terminateOnEvalImprovementNumOfEpoch)
          return eState.TERMINATE_EVALIMPROVE;
      }

      // This is used to be able to reproduce results that were trained on the
      // QNMinimizer before
      // convergence criteria was updated.
      if (useAveImprovement
          && (size > 5 && Math.abs(averageImprovement / newestVal) < TOL)) {
        return eState.TERMINATE_AVERAGEIMPROVE;
      }

      // Check to see if the gradient is sufficiently small
      if (useRelativeNorm && relNorm <= relativeTOL) {
        return eState.TERMINATE_RELATIVENORM;
      }

      if (useNumericalZero) {
        // This checks if the gradient is sufficiently small compared to x that
        // it is treated as zero.
        if (gNormLast < EPS * Math.max(1.0, ArrayMath.norm_1(xLast))) {
          // |g| < |x|_1
          // First we do the one norm, because that's easiest, and always bigger.
          if (gNormLast < EPS * Math.max(1.0, ArrayMath.norm(xLast))) {
            // |g| < max(1,|x|)
            // Now actually compare with the two norm if we have to.
            log.warn("Gradient is numerically zero, stopped on machine epsilon.");
            return eState.TERMINATE_GRADNORM;
          }
        }
        // give user information about the norms.
      }

      sb.append(" |").append(nf.format(gNormLast)).append("| {").append(nf.format(relNorm)).append("} ");
      sb.append(nf.format(Math.abs(averageImprovement / newestVal))).append(' ');
      sb.append(evalsSize > 0 ? evals.get(evalsSize - 1).toString() : "-").append(' ');
      return eState.CONTINUE;
    }

    /**
     *  Return the time in seconds since this class was created.
     *  @return The time in seconds since this class was created.
     */
    double howLong() {
      return (System.currentTimeMillis() - startTime) / 1000.0;
    }

    double[] getBest() {
      return xBest;
    }

  } // end class Record

  /**
   * The QNInfo class is used to store information about the Quasi Newton
   * update. it holds all the s,y pairs, updates the diagonal and scales
   * everything as needed.
   * <br>
   * This is kept as an abstract class as experimentally the optimizer
   * does a slightly better job of optimizing this than it does a
   * switch statement containing whichever branch is currently unused
   * (SCALAR vs DIAGONAL).  2019-10-03 experiments showed about a 1%
   * speedup.  Thanks to 
   * Erich Schubert &lt;schubert@informatik.uni-heidelberg.de&gt;
   */
  abstract class QNInfo {
    // Diagonal Options
    // Line search Options
    // Memory stuff
    protected double[][] s = null;
    protected double[][] y = null;
    protected double[] rho = null;
    protected double gamma;
    public double[] d = null;
    protected int mem = 20, used = 0;

    QNInfo(int size) {
      mem = size > 0 ? size : 20;
      s = new double[mem][];
      y = new double[mem][];
      rho = new double[mem];
      gamma = 1;
    }

    QNInfo(List<double[]> sList, List<double[]> yList) {
      s = new double[mem][];
      y = new double[mem][];
      rho = new double[mem];
      gamma = 1;
      setHistory(sList, yList);
    }

    int size() {
      return used;
    }

    double getRho(int ind) {
      return rho[ind];
    }

    double[] getS(int ind) {
      return s[ind];
    }

    double[] getY(int ind) {
      return y[ind];
    }

    void removeFirst() {
      // This looks expensive, but it is what the old ArrayList code
      // would also do.  Ultimately it is just a few reference copies
      // per iteration.  A circular buffer would save on that, but is
      // probably not worth the effort.
      System.arraycopy(s, 1, s, 0, s.length - 1);
      s[s.length - 1] = null;
      System.arraycopy(y, 1, y, 0, y.length - 1);
      y[y.length - 1] = null;
      System.arraycopy(rho, 1, rho, 0, rho.length - 1);
      --used;
    }

    /**
     * Free up that memory.
     */
    void free() {
      s = null;
      y = null;
      rho = null;
      d = null;
    }

    void clear() {
      // Fill the arrays with null in order to free the objects for GC
      used = 0;
      Arrays.fill(s, null);
      Arrays.fill(y, null);
      // Arrays.fill(rho, Double.NaN);
      d = null;
    }

    /**
     * This function {@code applyInitialHessian(double[] x)}
     * takes the vector {@code x}, and applies the best guess at the
     * initial hessian to this vector, based off available information from
     * previous updates.
     */
    void setHistory(List<double[]> sList, List<double[]> yList) {
      int size = sList.size();

      for (int i = 0; i < size; i++) {
        update(sList.get(i), yList.get(i), ArrayMath.innerProduct(yList.get(i),
            yList.get(i)), ArrayMath.innerProduct(sList.get(i), yList.get(i)),
            0, 1.0);
      }
    }

    abstract double[] applyInitialHessian(double[] x, StringBuilder sb);

    /*
     * The update function is used to update the hessian approximation used by
     * the quasi newton optimization routine.
     *
     * If everything has behaved nicely, this involves deciding on a new initial
     * hessian through scaling or diagonal update, and then storing of the
     * secant pairs s = x - previousX and y = grad - previousGrad.
     *
     * Things can go wrong, if any non convex behavior is detected (s^T y &lt; 0)
     * or numerical errors are likely the update is skipped.
     */
    int update(double[] newX, double[] x, double[] newGrad,
        double[] grad, double step) throws SurpriseConvergence {
      // todo: add OutOfMemory error.

      // allocate arrays for new s,y pairs (or reuse if the list is already full)
      double[] newS, newY;
      if (used == mem) {
        newS = s[0];
        newY = y[0];
        removeFirst();
      } else {
        newS = new double[x.length];
        newY = new double[x.length];
      }

      // Here we construct the new pairs, and check for positive definiteness.
      double sy = 0, yy = 0, sg = 0;
      for (int i = 0; i < x.length; i++) {
        double nSi = newS[i] = newX[i] - x[i];
        double nYi = newY[i] = newGrad[i] - grad[i];
        sy += nSi * nYi;
        yy += nYi * nYi;
        sg += nSi * newGrad[i];
      }

      // Apply the updates used for the initial hessian.

      return update(newS, newY, yy, sy, sg, step);
    }

    abstract int update(double[] newS, double[] newY, double yy, double sy, double sg, double step);
  } // end class QNInfo

  class ScalarQNInfo extends QNInfo {
    ScalarQNInfo(int size) {
      super(size);
    }

    ScalarQNInfo(List<double[]> sList, List<double[]> yList) {
      super(sList, yList);
    }

    double[] applyInitialHessian(double[] x, StringBuilder sb) {
      sb.append('I');
      ArrayMath.multiplyInPlace(x, gamma);
      return x;
    }

    int update(double[] newS, double[] newY, double yy, double sy, double sg, double step) {
      if(sy < 0) {
        // NOTE: if applying QNMinimizer to a non convex problem, we would still
        // like to update the matrix
        // or we could get stuck in a series of skipped updates.
        if(!quiet)
          log.info(" Negative curvature detected, update skipped ");
        return used;
      }
      if(yy == 0.0) {
        if(!quiet)
          log.info(" Either convergence, or floating point errors combined with extremely linear region ");
        return used;
      }

      gamma = sy / yy;

      // If s is already of size mem, remove the oldest vector and free it up.
      if(used == mem)
        removeFirst();

      // Actually add the pair.
      s[used] = newS;
      y[used] = newY;
      rho[used] = 1 / sy;
      ++used;

      return used;
    }
  } // end class ScalarQNInfo

  class DiagonalQNInfo extends QNInfo {
    DiagonalQNInfo(int size) {
      super(size);
    }

    DiagonalQNInfo(List<double[]> sList, List<double[]> yList) {
      super(sList, yList);
    }

    double[] applyInitialHessian(double[] x, StringBuilder sb) {
      sb.append('D');
      if(d != null) {
        // Check sizes
        if(x.length != d.length)
          throw new IllegalArgumentException("Vector of incorrect size passed to applyInitialHessian in QNInfo class");
        // Scale element-wise
        for(int i = 0; i < x.length; i++)
          x[i] /= d[i];
      }
      return x;
    }

    int update(double[] newS, double[] newY, double yy, double sy, double sg, double step) {
      if(sy < 0) {
        // NOTE: if applying QNMinimizer to a non convex problem, we would still
        // like to update the matrix
        // or we could get stuck in a series of skipped updates.
        if(!quiet)
          log.info(" Negative curvature detected, update skipped ");
        return used;
      }
      if(yy == 0.0) {
        if(!quiet)
          log.info(" Either convergence, or floating point errors combined with extremely linear region ");
        return used;
      }

      // Initialize diagonal to the identity
      if(d == null) {
        d = new double[newS.length];
        Arrays.fill(d, 1.0);
      }

      // Gamma is designed to scale such that a step length of one is
      // generally accepted.
      gamma = sy / (step * (sy - sg));
      double sDs = 0.0;
      for(int i = 0; i < d.length; i++) {
        final double newSi = newS[i];
        sDs += newSi * (d[i] *= gamma) * newSi;
      }
      // This diagonal update was introduced by Andrew Bradley
      for(int i = 0; i < d.length; i++) {
        final double di = d[i], newSi = newS[i], newYi = newY[i];
        d[i] = (1 - di * newSi * newSi / sDs) * di + newYi * newYi / sy;
      }
      // Here we make sure that the diagonal is alright
      double minD = d[0], maxD = minD;
      for(int i = 1; i < d.length; i++) {
        final double v = d[i];
        minD = v < minD ? v : minD;
        maxD = v > maxD ? v : maxD;
      }

      // If things have gone bad, just fill with the SCALAR approx.
      if(minD <= 0 || Double.isInfinite(maxD) || maxD / minD > 1e12) {
        log.warn("QNInfo:update() : PROBLEM WITH DIAGONAL UPDATE");
        Arrays.fill(d, yy / sy);
      }

      // If s is already of size mem, remove the oldest vector and free it up.
      if(used == mem)
        removeFirst();

      // Actually add the pair.
      s[used] = newS;
      y[used] = newY;
      rho[used] = 1 / sy;
      ++used;

      return used;
    } // end update
  } // end class DiagonalQNInfo

  public void setHistory(List<double[]> s, List<double[]> y) {
    presetInfo = newQNInfo(s, y);
  }

  public QNInfo newQNInfo(List<double[]> s, List<double[]> y) {
    return scaleOpt == eScaling.SCALAR ? new ScalarQNInfo(s, y) : new DiagonalQNInfo(s, y);
  }

  /**
   * computeDir()
   *
   * This function will calculate an approximation of the inverse hessian based
   * off the seen s,y vector pairs. This particular approximation uses the BFGS
   * update.
   */
  private void computeDir(double[] dir, double[] fg, double[] x, QNInfo qn, Function func, StringBuilder sb)
      throws SurpriseConvergence {
    System.arraycopy(fg, 0, dir, 0, fg.length);

    int mmm = qn.size();
    double[] as = new double[mmm];

    for (int i = mmm - 1; i >= 0; i--) {
      double v = as[i] = qn.getRho(i) * ArrayMath.innerProduct(qn.getS(i), dir);
      plusAndConstMult(dir, qn.getY(i), -v, dir);
    }

    // multiply by hessian approximation
    qn.applyInitialHessian(dir, sb);

    for (int i = 0; i < mmm; i++) {
      double b = qn.getRho(i) * ArrayMath.innerProduct(qn.getY(i), dir);
      plusAndConstMult(dir, qn.getS(i), as[i] - b, dir);
    }

    ArrayMath.multiplyInPlace(dir, -1);

    if (useOWLQN) { // step (2) in Galen & Gao 2007
      constrainSearchDir(dir, fg, x, func);
    }
  }

  // computes d = a + b * c
  private static double[] plusAndConstMult(double[] a, double[] b, double c,
      double[] d) {
    for (int i = 0; i < a.length; i++) {
      d[i] = a[i] + c * b[i];
    }
    return d;
  }

  private double doEvaluation(double[] x) {
    // Evaluate solution
    if (evaluators == null) return Double.NEGATIVE_INFINITY;
    double score = 0;
    for (Evaluator eval:evaluators) {
      if (!suppressTestPrompt && !quiet)
        log.info("  Evaluating: " + eval.toString());
      score = eval.evaluate(x);
    }
    return score;
  }

  public float[] minimize(DiffFloatFunction function, float functionTolerance,
      float[] initial) {
    throw new UnsupportedOperationException("Float not yet supported for QN");
  }

  @Override
  public double[] minimize(DiffFunction function, double functionTolerance,
      double[] initial) {
    return minimize(function, functionTolerance, initial, -1);
  }

  @Override
  public double[] minimize(DiffFunction dFunction, double functionTolerance,
      double[] initial, int maxFunctionEvaluations) {
    return minimize(dFunction, functionTolerance, initial,
        maxFunctionEvaluations, null);
  }

  public double[] minimize(DiffFunction dFunction, double functionTolerance,
      double[] initial, int maxFunctionEvaluations, QNInfo qn) {

    if (!quiet) {
      log.info("QNMinimizer called on double function of "
            + dFunction.domainDimension() + " variables, using " +
            (mem > 0 ? "M = " + mem : "dynamic settings of M") + '.');
    }

    if (qn == null && presetInfo == null) {
      qn = scaleOpt == eScaling.SCALAR ? new ScalarQNInfo(mem) : new DiagonalQNInfo(mem);
      noHistory = true;
    } else if (presetInfo != null) {
      qn = presetInfo;
      noHistory = false;
    } else if (qn != null) {
      noHistory = false;
    }

    its = 0;
    fevals = 0;
    success = false;

    // initialize weights
    double[] x = initial;

    // initialize gradient
    double[] rawGrad = new double[x.length];
    double[] newGrad = new double[x.length];
    double[] newX = new double[x.length];
    double[] dir = new double[x.length];

    // initialize function value and gradient (gradient is stored in grad inside
    // evaluateFunction)
    double value = evaluateFunction(dFunction, x, rawGrad);
    double[] grad;
    if (useOWLQN) {
      double norm = l1NormOWL(x, dFunction);
      value += norm * lambdaOWL;
      // step (1) in Galen & Gao except we are not computing v yet
      grad = pseudoGradientOWL(x, rawGrad, dFunction);
    } else {
      grad = rawGrad;
    }

    PrintWriter outFile = null;
    PrintWriter infoFile = null;

    if (outputToFile) {
      try {
        String baseName = "QN_m" + mem + '_' + lsOpt.toString() + '_'
            + scaleOpt.toString();
        outFile = new PrintWriter(new FileOutputStream(baseName + ".output"),
            true);
        infoFile = new PrintWriter(new FileOutputStream(baseName + ".info"),
            true);
        infoFile.println(dFunction.domainDimension() + "; DomainDimension ");
        infoFile.println(mem + "; memory");
      } catch (IOException e) {
        throw new RuntimeIOException("Caught IOException outputting QN data to file", e);
      }
    }

    Record rec = new Record(monitor, functionTolerance, outFile);
    // sets the original gradient and x. Also stores the monitor.
    rec.start(value, rawGrad, x);

    // Check if max Evaluations and Iterations have been provided.
    maxFevals = (maxFunctionEvaluations > 0) ? maxFunctionEvaluations
        : Integer.MAX_VALUE;
    // maxIterations = (maxIterations > 0) ? maxIterations : Integer.MAX_VALUE;

    if (!quiet) {
      log.info("               An explanation of the output:");
      log.info("Iter           The number of iterations");
      log.info("evals          The number of function evaluations");
      log.info("SCALING        <D> Diagonal scaling was used; <I> Scaled Identity");
      log.info("LINESEARCH     [## M steplength]  Minpack linesearch");
      log.info("                   1-Function value was too high");
      log.info("                   2-Value ok, gradient positive, positive curvature");
      log.info("                   3-Value ok, gradient negative, positive curvature");
      log.info("                   4-Value ok, gradient negative, negative curvature");
      log.info("               [.. B]  Backtracking");
      log.info("VALUE          The current function value");
      log.info("TIME           Total elapsed time");
      log.info("|GNORM|        The current norm of the gradient");
      log.info("{RELNORM}      The ratio of the current to initial gradient norms");
      log.info("AVEIMPROVE     The average improvement / current value");
      log.info("EVALSCORE      The last available eval score");
      log.info(" ");
      log.info("Iter ## evals ## <SCALING> [LINESEARCH] VALUE TIME |GNORM| {RELNORM} AVEIMPROVE EVALSCORE");
    }

    StringBuilder sb = new StringBuilder(100);
    eState state = eState.CONTINUE;

    // Beginning of the loop.
    do {
      try {
        if ( ! quiet) {
          log.info(sb.toString());
        }
        sb.setLength(0);
        boolean doEval = (its >= 0 && its >= startEvaluateIters && evaluateIters > 0 && its % evaluateIters == 0);
        its += 1;
        double newValue;
        sb.append("Iter ").append(its).append(" evals ").append(fevals).append(' ');

        // Compute the search direction
        sb.append('<');
        computeDir(dir, grad, x, qn, dFunction, sb);
        sb.append("> ");

        // sanity check dir
        boolean hasNaNDir = false;
        boolean hasNaNGrad = false;
        for (int i = 0; i < dir.length; i++) {
          if (dir[i] != dir[i]) hasNaNDir = true;
          if (grad[i] != grad[i]) hasNaNGrad = true;
        }
        if (hasNaNDir && !hasNaNGrad) {
          if (!quiet) log.info("(NaN dir likely due to Hessian approx - resetting) ");
          qn.clear();
          // re-compute the search direction
          sb.append('<');
          computeDir(dir, grad, x, qn, dFunction, sb);
          sb.append("> ");
        }

        // perform line search
        sb.append('[');

        double[] newPoint; // initialized in if/else/switch below
        if (useOWLQN) {
          // only linear search is allowed for OWL-QN
          newPoint = lineSearchBacktrackOWL(dFunction, dir, x, newX, grad, value, sb);
          sb.append('B');
        } else {
          // switch between line search options.
          switch (lsOpt) {
          case BACKTRACK:
            newPoint = lineSearchBacktrack(dFunction, dir, x, newX, grad, value, sb);
            sb.append('B');
            break;
          case MINPACK:
            newPoint = lineSearchMinPack(dFunction, dir, x, newX, grad, value,
                functionTolerance, sb);
            sb.append('M');
            break;
          default:
            throw new IllegalArgumentException("Invalid line search option for QNMinimizer.");
          }
        }

        newValue = newPoint[f];
        sb.append(' ').append(nf.format(newPoint[a])).append("] ");

        // This shouldn't actually evaluate anything since that should have been
        // done in the lineSearch.
        System.arraycopy(dFunction.derivativeAt(newX), 0, newGrad, 0, newGrad.length);

        // This is where all the s, y updates are applied.
        qn.update(newX, x, newGrad, rawGrad, newPoint[a]); // step (4) in Galen & Gao 2007

        if (useOWLQN) {
          System.arraycopy(newGrad, 0, rawGrad, 0, newGrad.length);
          // pseudo gradient
          newGrad = pseudoGradientOWL(newX, newGrad, dFunction);
        }

        double evalScore = Double.NEGATIVE_INFINITY;
        if (doEval) {
          evalScore = doEvaluation(newX);
        }

        // Add the current value and gradient to the records, this also monitors
        // X and writes to output
        rec.add(newValue, newGrad, newX, fevals, evalScore, sb);

        // If you want to call a function and do whatever with the information ...
        if (iterCallbackFunction != null) {
          iterCallbackFunction.callback(newX, its, newValue, newGrad);
        }

        // shift
        value = newValue;
        // double[] temp = x;
        // x = newX;
        // newX = temp;
        System.arraycopy(newX, 0, x, 0, x.length);
        System.arraycopy(newGrad, 0, grad, 0, newGrad.length);

        if (fevals > maxFevals) {
          throw new MaxEvaluationsExceeded("Exceeded in minimize() loop.");
        }
      } catch (SurpriseConvergence s) {
        if (!quiet) log.info("QNMinimizer aborted due to surprise convergence");
        break;
      } catch (MaxEvaluationsExceeded m) {
        if (!quiet) {
          log.info("QNMinimizer aborted due to maximum number of function evaluations");
          log.info(m.toString());
          log.info("** This is not an acceptable termination of QNMinimizer, consider");
          log.info("** increasing the max number of evaluations, or safeguarding your");
          log.info("** program by checking the QNMinimizer.wasSuccessful() method.");
        }
        break;
      } catch (OutOfMemoryError oome) {
        if (qn.used > 1) {
          qn.removeFirst();
          sb.append("{Caught OutOfMemory, changing m from ").append(qn.mem).append(" to ").append(qn.used).append("}]");
          qn.mem = qn.used;
        } else {
          throw oome;
        }
      }
    } while ((state = rec.toContinue(sb)) == eState.CONTINUE); // end do while

    if (evaluateIters > 0) {
      // do final evaluation
      double evalScore = (useEvalImprovement ? doEvaluation(rec.getBest()) : doEvaluation(x));
      if (!quiet) log.info("final evalScore is: " + evalScore);
    }

    //
    // Announce the reason minimization has terminated.
    //
    switch (state) {
    case TERMINATE_GRADNORM:
      if (!quiet) log.info("QNMinimizer terminated due to numerically zero gradient: |g| < EPS  max(1,|x|) ");
      success = true;
      break;
    case TERMINATE_RELATIVENORM:
      if (!quiet) log.info("QNMinimizer terminated due to sufficient decrease in gradient norms: |g|/|g0| < TOL ");
      success = true;
      break;
    case TERMINATE_AVERAGEIMPROVE:
      if (!quiet) log.info("QNMinimizer terminated due to average improvement: | newest_val - previous_val | / |newestVal| < TOL ");
      success = true;
      break;
    case TERMINATE_MAXITR:
      if (!quiet) log.info("QNMinimizer terminated due to reached max iteration " + maxItr);
      success = true;
      break;
    case TERMINATE_EVALIMPROVE:
      if (!quiet) log.info("QNMinimizer terminated due to no improvement on eval ");
      success = true;
      x = rec.getBest();
      break;
    default:
      log.warn("QNMinimizer terminated without converging");
      success = false;
      break;
    }

    double completionTime = rec.howLong();
    if (!quiet) log.info("Total time spent in optimization: " + nfsec.format(completionTime) + 's');

    if (outputToFile) {
      infoFile.println(completionTime + "; Total Time ");
      infoFile.println(fevals + "; Total evaluations");
      infoFile.close();
      outFile.close();
    }

    qn.free();
    return x;

  } // end minimize()

  // todo [cdm 2013]: Can this be sped up by returning a Pair rather than copying array?
  private double evaluateFunction(DiffFunction dfunc, double[] x, double[] grad) {
    System.arraycopy(dfunc.derivativeAt(x), 0, grad, 0, grad.length);
    fevals += 1;
    return dfunc.valueAt(x);
  }

  /** To set QNMinimizer to use L1 regularization, call this method before use,
   *  with the boolean set true, and the appropriate lambda parameter.
   *
   *  @param use Whether to use Orthant-wise optimization
   *  @param lambda The L1 regularization parameter.
   */
  public void useOWLQN(boolean use, double lambda) {
    this.useOWLQN = use;
    this.lambdaOWL = lambda;
  }

  private static double[] projectOWL(double[] x, double[] orthant, Function func) {
    if (func instanceof HasRegularizerParamRange) {
      Set<Integer> paramRange = ((HasRegularizerParamRange)func).getRegularizerParamRange(x);
      for (int i : paramRange) {
        if (x[i] * orthant[i] <= 0.0) {
          x[i] = 0.0;
        }
      }
    } else {
      for (int i = 0; i < x.length; i++) {
        if (x[i] * orthant[i] <= 0.0) {
          x[i] = 0.0;
        }
      }
    }
    return x;
  }

  private static double l1NormOWL(double[] x, Function func) {
    double sum = 0.0;
    if (func instanceof HasRegularizerParamRange) {
      Set<Integer> paramRange = ((HasRegularizerParamRange)func).getRegularizerParamRange(x);
      for (int i : paramRange) {
        sum += Math.abs(x[i]);
      }
    } else {
      for (double v : x) {
        sum += Math.abs(v);
      }
    }
    return sum;
  }

  private static void constrainSearchDir(double[] dir, double[] fg, double[] x, Function func) {
    if (func instanceof HasRegularizerParamRange) {
      Set<Integer> paramRange = ((HasRegularizerParamRange)func).getRegularizerParamRange(x);
      for (int i : paramRange) {
        if (dir[i] * fg[i] >= 0.0) {
          dir[i] = 0.0;
        }
      }
    } else {
      for (int i = 0; i < x.length; i++) {
        if (dir[i] * fg[i] >= 0.0) {
          dir[i] = 0.0;
        }
      }
    }
  }

  private double[] pseudoGradientOWL(double[] x, double[] grad, Function func) {
    Set<Integer> paramRange = func instanceof HasRegularizerParamRange ?
        ((HasRegularizerParamRange)func).getRegularizerParamRange(x) : null ;
    double[] newGrad = new double[grad.length];

    // compute pseudo gradient
    for (int i = 0; i < x.length; i++) {
      if (paramRange == null || paramRange.contains(i)) {
        if (x[i] < 0.0) {
          // Differentiable
          newGrad[i] = grad[i] - lambdaOWL;
        } else if (x[i] > 0.0) {
          // Differentiable
          newGrad[i] = grad[i] + lambdaOWL;
        } else {
          if (grad[i] < -lambdaOWL) {
            // Take the right partial derivative
            newGrad[i] = grad[i] + lambdaOWL;
          } else if (grad[i] > lambdaOWL) {
            // Take the left partial derivative
            newGrad[i] = grad[i] - lambdaOWL;
          } else {
            newGrad[i] = 0.0;
          }
        }
      } else {
        newGrad[i] = grad[i];
      }
    }

    return newGrad;
  }


  /**
   * lineSearchBacktrackOWL is the linesearch used for L1 regularization.
   * it only satisfies sufficient descent not the Wolfe conditions.
   */
  private double[] lineSearchBacktrackOWL(Function func, double[] dir, double[] x,
      double[] newX, double[] grad, double lastValue, StringBuilder sb)
      throws MaxEvaluationsExceeded {

    /* Choose the orthant for the new point. */
    double[] orthant = new double[x.length];
    for (int i = 0; i < orthant.length; i++) {
      orthant[i] = (x[i] == 0.0) ? -grad[i] : x[i];
    }

    // c1 can be anything between 0 and 1, exclusive (usu. 1/10 - 1/2)
    double step, c1;

    // for first few steps, we have less confidence in our initial step-size a
    // so scale back quicker
    if (its <= 2) {
      step = 0.1;
      c1 = 0.1;
    } else {
      step = 1.0;
      c1 = 0.1;
    }

    // should be small e.g. 10^-5 ... 10^-1
    double c = 0.01;

    // c = c * normGradInDir;

    double[] newPoint = new double[3];

    while (true) {
      plusAndConstMult(x, dir, step, newX);

      // The current point is projected onto the orthant
      projectOWL(newX, orthant, func); // step (3) in Galen & Gao 2007

      // Evaluate the function and gradient values
      double value  =  func.valueAt(newX);

      // Compute the L1 norm of the variables and add it to the object value
      double norm = l1NormOWL(newX, func);
      value += norm * lambdaOWL;

      newPoint[f] = value;

      double dgtest = 0.0;
      for (int i = 0;i < x.length ;i++) {
        dgtest += (newX[i] - x[i]) * grad[i];
      }

      if (newPoint[f] <= lastValue + c * dgtest)
        break;
      else {
        if (newPoint[f] < lastValue) {
          // an improvement, but not good enough... suspicious!
          sb.append('!');
        } else {
          sb.append('.');
        }
      }

      step = c1 * step;
    }

    newPoint[a] = step;
    fevals += 1;
    if (fevals > maxFevals) {
      throw new MaxEvaluationsExceeded("Exceeded during linesearch() Function.");
    }

    return newPoint;
  }


  /*
   * lineSearchBacktrack is the original line search used for the first version
   * of QNMinimizer. It only satisfies sufficient descent not the Wolfe conditions.
   */
  private double[] lineSearchBacktrack(Function func, double[] dir, double[] x,
      double[] newX, double[] grad, double lastValue, StringBuilder sb)
      throws MaxEvaluationsExceeded {

    double normGradInDir = ArrayMath.innerProduct(dir, grad);
    sb.append('(').append(nf.format(normGradInDir)).append(')');
    if (normGradInDir > 0 && !quiet) {
      log.info("{WARNING--- direction of positive gradient chosen!}");
    }

    // c1 can be anything between 0 and 1, exclusive (usu. 1/10 - 1/2)
    double step, c1;

    // for first few steps, we have less confidence in our initial step-size a
    // so scale back quicker
    if (its <= 2) {
      step = 0.1;
      c1 = 0.1;
    } else {
      step = 1.0;
      c1 = 0.1;
    }

    // should be small e.g. 10^-5 ... 10^-1
    double c = 0.01;

    // double v = func.valueAt(x);
    // c = c * mult(grad, dir);
    c = c * normGradInDir;

    double[] newPoint = new double[3];

    while ((newPoint[f] = func.valueAt((plusAndConstMult(x, dir, step, newX)))) > lastValue
        + c * step) {
      fevals += 1;
      if (newPoint[f] < lastValue) {
        // an improvement, but not good enough... suspicious!
        sb.append('!');
      } else {
        sb.append('.');
      }
      step = c1 * step;
    }

    newPoint[a] = step;
    fevals += 1;
    if (fevals > maxFevals) {
      throw new MaxEvaluationsExceeded("Exceeded during lineSearch() Function.");
    }

    return newPoint;
  }

  private double[] lineSearchMinPack(DiffFunction dfunc, double[] dir,
      double[] x, double[] newX, double[] grad, double f0, double tol, StringBuilder sb)
      throws MaxEvaluationsExceeded {
    double xtrapf = 4.0;
    int info = 0;
    int infoc = 1;
    bracketed = false;
    boolean stage1 = true;
    double width = aMax - aMin;
    double width1 = 2 * width;
    // double[] wa = x;

    // Should check input parameters

    double g0 = ArrayMath.innerProduct(grad, dir);
    if (g0 >= 0) {
      // We're looking in a direction of positive gradient. This won't work.
      // set dir = -grad
      for (int i = 0; i < x.length; i++) {
        dir[i] = -grad[i];
      }
      g0 = ArrayMath.innerProduct(grad, dir);
    }
    double gTest = ftol * g0;

    double[] newPt = new double[3];
    double[] bestPt = new double[3];
    double[] endPt = new double[3];

    newPt[a] = 1.0; // Always guess 1 first, this should be right if the
                    // function is "nice" and BFGS is working.

    if (its == 1 && noHistory) {
      newPt[a] = 1e-1;
    }

    bestPt[a] = 0.0;
    bestPt[f] = f0;
    bestPt[g] = g0;
    endPt[a] = 0.0;
    endPt[f] = f0;
    endPt[g] = g0;

    // int cnt = 0;

    do {

      double stpMin; // = aMin; [cdm: this initialization was always overridden below]
      double stpMax; // = aMax; [cdm: this initialization was always overridden below]
      if (bracketed) {
        stpMin = Math.min(bestPt[a], endPt[a]);
        stpMax = Math.max(bestPt[a], endPt[a]);
      } else {
        stpMin = bestPt[a];
        stpMax = newPt[a] + xtrapf * (newPt[a] - bestPt[a]);
      }

      newPt[a] = Math.max(newPt[a], aMin);
      newPt[a] = Math.min(newPt[a], aMax);

      // Use the best point if we have some sort of strange termination
      // conditions.
      if ((bracketed && (newPt[a] <= stpMin || newPt[a] >= stpMax))
          || fevals >= maxFevals || infoc == 0
          || (bracketed && stpMax - stpMin <= tol * stpMax)) {
        // todo: below..
        plusAndConstMult(x, dir, bestPt[a], newX);
        newPt[f] = bestPt[f];
        newPt[a] = bestPt[a];
      }

      newPt[f] = dfunc.valueAt((plusAndConstMult(x, dir, newPt[a], newX)));
      newPt[g] = ArrayMath.innerProduct(dfunc.derivativeAt(newX), dir);
      double fTest = f0 + newPt[a] * gTest;
      fevals += 1;

      // Check and make sure everything is normal.
      if ((bracketed && (newPt[a] <= stpMin || newPt[a] >= stpMax))
          || infoc == 0) {
        info = 6;
        if (!quiet) log.info(" line search failure: bracketed but no feasible found ");
      }
      if (newPt[a] == aMax && newPt[f] <= fTest && newPt[g] <= gTest) {
        info = 5;
        if (!quiet) log.info(" line search failure: sufficient decrease, but gradient is more negative ");
      }
      if (newPt[a] == aMin && (newPt[f] > fTest || newPt[g] >= gTest)) {
        info = 4;
        if (!quiet) log.info(" line search failure: minimum step length reached ");
      }
      if (fevals >= maxFevals) {
        // info = 3;
        throw new MaxEvaluationsExceeded("Exceeded during lineSearchMinPack() Function.");
      }
      if (bracketed && stpMax - stpMin <= tol * stpMax) {
        info = 2;
        if (!quiet) log.info(" line search failure: interval is too small ");
      }
      if (newPt[f] <= fTest && Math.abs(newPt[g]) <= -gtol * g0) {
        info = 1;
      }

      if (info != 0) {
        return newPt;
      }

      // this is the first stage where we look for a point that is lower and
      // increasing

      if (stage1 && newPt[f] <= fTest && newPt[g] >= Math.min(ftol, gtol) * g0) {
        stage1 = false;
      }

      // A modified function is used to predict the step only if
      // we have not obtained a step for which the modified
      // function has a non-positive function value and non-negative
      // derivative, and if a lower function value has been
      // obtained but the decrease is not sufficient.

      if (stage1 && newPt[f] <= bestPt[f] && newPt[f] > fTest) {
        newPt[f] = newPt[f] - newPt[a] * gTest;
        bestPt[f] = bestPt[f] - bestPt[a] * gTest;
        endPt[f] = endPt[f] - endPt[a] * gTest;

        newPt[g] = newPt[g] - gTest;
        bestPt[g] = bestPt[g] - gTest;
        endPt[g] = endPt[g] - gTest;

        infoc = getStep(/* x, dir, newX, f0, g0, */
                        newPt, bestPt, endPt, stpMin, stpMax, sb);

        bestPt[f] = bestPt[f] + bestPt[a] * gTest;
        endPt[f] = endPt[f] + endPt[a] * gTest;

        bestPt[g] = bestPt[g] + gTest;
        endPt[g] = endPt[g] + gTest;
      } else {
        infoc = getStep(/* x, dir, newX, f0, g0, */
                        newPt, bestPt, endPt, stpMin, stpMax, sb);
      }

      if (bracketed) {
        if (Math.abs(endPt[a] - bestPt[a]) >= p66 * width1) {
          newPt[a] = bestPt[a] + p5 * (endPt[a] - bestPt[a]);
        }
        width1 = width;
        width = Math.abs(endPt[a] - bestPt[a]);
      }

    } while (true);
  }


  /**
   * getStep()
   *
   * THIS FUNCTION IS A TRANSLATION OF A TRANSLATION OF THE MINPACK SUBROUTINE
   * cstep(). Dianne O'Leary July 1991
   *
   * It was then interpreted from the implementation supplied by Andrew
   * Bradley. Modifications have been made for this particular application.
   *
   * This function is used to find a new safe guarded step to be used for
   * line search procedures.
   *
   */
  private int getStep(
          /* double[] x, double[] dir, double[] newX, double f0,
          double g0, // None of these were used */
          double[] newPt, double[] bestPt, double[] endPt,
          double stpMin, double stpMax, StringBuilder sb) throws MaxEvaluationsExceeded {

    // Should check for input errors.
    int info; // = 0; always set in the if below
    boolean bound; // = false; always set in the if below
    double theta, gamma, p, q, r, s, stpc, stpq, stpf;
    double signG = newPt[g] * bestPt[g] / Math.abs(bestPt[g]);

    //
    // First case. A higher function value.
    // The minimum is bracketed. If the cubic step is closer
    // to stx than the quadratic step, the cubic step is taken,
    // else the average of the cubic and quadratic steps is taken.
    //
    if (newPt[f] > bestPt[f]) {
      info = 1;
      bound = true;
      theta = 3 * (bestPt[f] - newPt[f]) / (newPt[a] - bestPt[a]) + bestPt[g]
          + newPt[g];
      s = Math.max(Math.max(theta, newPt[g]), bestPt[g]);
      final double theta_s = theta / s;
      gamma = s
          * Math.sqrt(theta_s * theta_s - (bestPt[g] / s)
              * (newPt[g] / s));
      if (newPt[a] < bestPt[a]) {
        gamma = -gamma;
      }
      p = (gamma - bestPt[g]) + theta;
      q = ((gamma - bestPt[g]) + gamma) + newPt[g];
      r = p / q;
      stpc = bestPt[a] + r * (newPt[a] - bestPt[a]);
      stpq = bestPt[a]
          + ((bestPt[g] / ((bestPt[f] - newPt[f]) / (newPt[a] - bestPt[a]) + bestPt[g])) / 2)
          * (newPt[a] - bestPt[a]);

      if (Math.abs(stpc - bestPt[a]) < Math.abs(stpq - bestPt[a])) {
        stpf = stpc;
      } else {
        stpf = stpq;
        // stpf = stpc + (stpq - stpc)/2;
      }
      bracketed = true;
      if (newPt[a] < 0.1) {
        stpf = 0.01 * stpf;
      }

    } else if (signG < 0.0) {
      //
      // Second case. A lower function value and derivatives of
      // opposite sign. The minimum is bracketed. If the cubic
      // step is closer to stx than the quadratic (secant) step,
      // the cubic step is taken, else the quadratic step is taken.
      //
      info = 2;
      bound = false;
      theta = 3 * (bestPt[f] - newPt[f]) / (newPt[a] - bestPt[a]) + bestPt[g]
          + newPt[g];
      s = Math.max(Math.max(theta, bestPt[g]), newPt[g]);
      final double theta_s = theta / s;
      gamma = s
          * Math.sqrt(theta_s * theta_s - (bestPt[g] / s)
              * (newPt[g] / s));
      if (newPt[a] > bestPt[a]) {
        gamma = -gamma;
      }
      p = (gamma - newPt[g]) + theta;
      q = ((gamma - newPt[g]) + gamma) + bestPt[g];
      r = p / q;
      stpc = newPt[a] + r * (bestPt[a] - newPt[a]);
      stpq = newPt[a] + (newPt[g] / (newPt[g] - bestPt[g]))
          * (bestPt[a] - newPt[a]);
      if (Math.abs(stpc - newPt[a]) > Math.abs(stpq - newPt[a])) {
        stpf = stpc;
      } else {
        stpf = stpq;
      }
      bracketed = true;

    } else if (Math.abs(newPt[g]) < Math.abs(bestPt[g])) {
      //
      // Third case. A lower function value, derivatives of the
      // same sign, and the magnitude of the derivative decreases.
      // The cubic step is only used if the cubic tends to infinity
      // in the direction of the step or if the minimum of the cubic
      // is beyond stp. Otherwise the cubic step is defined to be
      // either stpmin or stpmax. The quadratic (secant) step is also
      // computed and if the minimum is bracketed then the the step
      // closest to stx is taken, else the step farthest away is taken.
      //
      info = 3;
      bound = true;
      theta = 3 * (bestPt[f] - newPt[f]) / (newPt[a] - bestPt[a]) + bestPt[g]
          + newPt[g];
      s = Math.max(Math.max(theta, bestPt[g]), newPt[g]);
      gamma = s * Math.sqrt(Math.max(0.0, (theta / s) * (theta / s) - (bestPt[g] / s)
              * (newPt[g] / s)));
      if (newPt[a] < bestPt[a]) {
        gamma = -gamma;
      }
      p = (gamma - bestPt[g]) + theta;
      q = ((gamma - bestPt[g]) + gamma) + newPt[g];
      r = p / q;
      if (r < 0.0 && gamma != 0.0) {
        stpc = newPt[a] + r * (bestPt[a] - newPt[a]);
      } else if (newPt[a] > bestPt[a]) {
        stpc = stpMax;
      } else {
        stpc = stpMin;
      }
      stpq = newPt[a] + (newPt[g] / (newPt[g] - bestPt[g]))
          * (bestPt[a] - newPt[a]);

      if (bracketed) {
        if (Math.abs(newPt[a] - stpc) < Math.abs(newPt[a] - stpq)) {
          stpf = stpc;
        } else {
          stpf = stpq;
        }
      } else {
        if (Math.abs(newPt[a] - stpc) > Math.abs(newPt[a] - stpq)) {
          stpf = stpc;
        } else {
          stpf = stpq;
        }
      }

    } else {
      //
      // Fourth case. A lower function value, derivatives of the
      // same sign, and the magnitude of the derivative does
      // not decrease. If the minimum is not bracketed, the step
      // is either stpmin or stpmax, else the cubic step is taken.
      //
      info = 4;
      bound = false;

      if (bracketed) {
        theta = 3 * (bestPt[f] - newPt[f]) / (newPt[a] - bestPt[a]) + bestPt[g]
            + newPt[g];
        s = Math.max(Math.max(theta, bestPt[g]), newPt[g]);
        final double theta_s = theta / s;
        gamma = s
            * Math.sqrt(theta_s * theta_s - (bestPt[g] / s)
                * (newPt[g] / s));
        if (newPt[a] > bestPt[a]) {
          gamma = -gamma;
        }
        p = (gamma - newPt[g]) + theta;
        q = ((gamma - newPt[g]) + gamma) + bestPt[g];
        r = p / q;
        stpc = newPt[a] + r * (bestPt[a] - newPt[a]);
        stpf = stpc;
      } else if (newPt[a] > bestPt[a]) {
        stpf = stpMax;
      } else {
        stpf = stpMin;
      }

    }

    //
    // Update the interval of uncertainty. This update does not
    // depend on the new step or the case analysis above.
    //
    if (newPt[f] > bestPt[f]) {
      copy(newPt, endPt);
    } else {
      if (signG < 0.0) {
        copy(bestPt, endPt);
      }
      copy(newPt, bestPt);
    }

    sb.append(String.valueOf(info));

    //
    // Compute the new step and safeguard it.
    //
    stpf = Math.min(stpMax, stpf);
    stpf = Math.max(stpMin, stpf);
    newPt[a] = stpf;

    if (bracketed && bound) {
      if (endPt[a] > bestPt[a]) {
        newPt[a] = Math.min(bestPt[a] + p66 * (endPt[a] - bestPt[a]), newPt[a]);
      } else {
        newPt[a] = Math.max(bestPt[a] + p66 * (endPt[a] - bestPt[a]), newPt[a]);
      }
    }

    return info;
  }

  private static void copy(double[] src, double[] dest) {
    System.arraycopy(src, 0, dest, 0, src.length);
  }

  //
  //
  //
  // private double[] lineSearchNocedal(DiffFunction dfunc, double[] dir,
  // double[] x, double[] newX, double[] grad, double f0) throws
  // MaxEvaluationsExceeded {
  //
  //
  // double g0 = ArrayMath.innerProduct(grad,dir);
  // if(g0 > 0){
  // //We're looking in a direction of positive gradient. This wont' work.
  // //set dir = -grad
  // plusAndConstMult(new double[x.length],grad,-1,dir);
  // g0 = ArrayMath.innerProduct(grad,dir);
  // }
  // say("(" + nf.format(g0) + ")");
  //
  //
  // double[] newPoint = new double[3];
  // double[] prevPoint = new double[3];
  // newPoint[a] = 1.0; //Always guess 1 first, this should be right if the
  // function is "nice" and BFGS is working.
  //
  // //Special guess for the first iteration.
  // if(its == 1){
  // double aLin = - f0 / (ftol*g0);
  // //Keep aLin within aMin and 1 for the first guess. But make a more
  // intelligent guess based off the gradient
  // aLin = Math.min(1.0, aLin);
  // aLin = Math.max(aMin, aLin);
  // newPoint[a] = aLin; // Guess low at first since we have no idea of scale at
  // first.
  // }
  //
  // prevPoint[a] = 0.0;
  // prevPoint[f] = f0;
  // prevPoint[g] = g0;
  //
  // int cnt = 0;
  //
  // do{
  // newPoint[f] = dfunc.valueAt((plusAndConstMult(x, dir, newPoint[a], newX)));
  // newPoint[g] = ArrayMath.innerProduct(dfunc.derivativeAt(newX),dir);
  // fevals += 1;
  //
  // //If fNew > f0 + small*aNew*g0 or fNew > fPrev
  // if( (newPoint[f] > f0 + ftol*newPoint[a]*g0) || newPoint[f] > prevPoint[f]
  // ){
  // //We know there must be a point that satisfies the strong wolfe conditions
  // between
  // //the previous and new point, so search between these points.
  // say("->");
  // return zoom(dfunc,x,dir,newX,f0,g0,prevPoint,newPoint);
  // }
  //

  // //Here we check if the magnitude of the gradient has decreased, if
  // //it is more negative we can expect to find a much better point
  // //by stepping a little farther.
  //
  // //If |gNew| < 0.9999 |g0|
  // if( Math.abs(newPoint[g]) <= -gtol*g0 ){
  // //This is exactly what we wanted
  // return newPoint;
  // }
  //
  // if (newPoint[g] > 0){
  // //Hmm, our step is too big to be a satisfying point, lets look backwards.
  // say("<-");//say("^");
  //
  // return zoom(dfunc,x,dir,newX,f0,g0,newPoint,prevPoint);
  // }
  //
  // //if we made it here, our function value has decreased enough, but the
  // gradient is more negative.
  // //we should increase our step size, since we have potential to decrease the
  // function
  // //value a lot more.
  // newPoint[a] *= 10; // this is stupid, we should interpolate it. since we
  // already have info for quadratic at least.
  // newPoint[f] = Double.NaN;
  // newPoint[g] = Double.NaN;
  // cnt +=1;
  // say("*");
  //
  // //if(cnt > 10 || fevals > maxFevals){
  // if(fevals > maxFevals){ throw new MaxEvaluationsExceeded(" Exceeded during
  // zoom() Function ");}
  //
  // if(newPoint[a] > aMax){
  // log.info(" max stepsize reached. This is unusual. ");
  // System.exit(1);
  // }
  //
  // }while(true);
  //
  // }

  // private double interpolate( double[] point0, double[] point1){
  // double newAlpha;

  // double intvl = Math.abs(point0[a] -point1[a]);

  // //if(point2 == null){
  // if( Double.isNaN(point0[g]) ){
  // //We dont know the gradient at aLow so do bisection
  // newAlpha = 0.5*(point0[a] + point1[a]);
  // }else{
  // //We know the gradient so do Quadratic 2pt
  // newAlpha = interpolateQuadratic2pt(point0,point1);
  // }

  // //If the newAlpha is outside of the bounds just do bisection.

  // if( ((newAlpha > point0[a]) && (newAlpha > point1[a])) ||
  // ((newAlpha < point0[a]) && (newAlpha < point1[a])) ){

  // //bisection.
  // return 0.5*(point0[a] + point1[a]);
  // }

  // //If we aren't moving fast enough, revert to bisection.
  // if( ((newAlpha/intvl) < 1e-6) || ((newAlpha/intvl) > (1- 1e-6)) ){
  // //say("b");
  // return 0.5*(point0[a] + point1[a]);
  // }

  // return newAlpha;
  // }

  /*
   * private double interpolate( List<double[]> pointList ,) {
   *
   * int n = pointList.size(); double newAlpha = 0.0;
   *
   * if( n > 2){ newAlpha =
   * interpolateCubic(pointList.get(0),pointList.get(n-2),pointList.get(n-1));
   * }else if(n == 2){
   *
   * //Only have two points
   *
   * if( Double.isNaN(pointList.get(0)[gInd]) ){ // We don't know the gradient at
   * aLow so do bisection newAlpha = 0.5*(pointList.get(0)[aInd] +
   * pointList.get(1)[aInd]); }else{ // We know the gradient so do Quadratic 2pt
   * newAlpha = interpolateQuadratic2pt(pointList.get(0),pointList.get(1)); }
   *
   * }else { //not enough info to interpolate with!
   * log.info("QNMinimizer:interpolate() attempt to interpolate with
   * only one point."); System.exit(1); }
   *
   * return newAlpha;
   *  }
   */

  // Returns the minimizer of a quadratic running through point (a0,f0) with
  // derivative g0 and passing through (a1,f1).
  // private double interpolateQuadratic2pt(double[] pt0, double[] pt1){
  // if( Double.isNaN(pt0[g]) ){
  // log.info("QNMinimizer:interpolateQuadratic - Gradient at point
  // zero doesn't exist, interpolation failed");
  // System.exit(1);
  // }
  // double aDif = pt1[a]-pt0[a];
  // double fDif = pt1[f]-pt0[f];
  // return (- pt0[g]*aDif*aDif)/(2*(fDif-pt0[g]*aDif)) + pt0[a];
  // }
  // private double interpolateCubic(double[] pt0, double[] pt1, double[] pt2){
  // double a0 = pt1[a]-pt0[a];
  // double a1 = pt2[a]-pt0[a];
  // double f0 = pt1[f]-pt0[f];
  // double f1 = pt2[f]-pt0[f];
  // double g0 = pt0[g];
  // double[][] mat = new double[2][2];
  // double[] rhs = new double[2];
  // double[] coefs = new double[2];
  // double scale = 1/(a0*a0*a1*a1*(a1-a0));
  // mat[0][0] = a0*a0;
  // mat[0][1] = -a1*a1;
  // mat[1][0] = -a0*a0*a0;
  // mat[1][1] = a1*a1*a1;
  // rhs[0] = f1 - g0*a1;
  // rhs[1] = f0 - g0*a0;
  // for(int i=0;i<2;i++){
  // for(int j=0;j<2;j++){
  // coefs[i] += mat[i][j]*rhs[j];
  // }
  // coefs[i] *= scale;
  // }
  // double a = coefs[0];
  // double b = coefs[1];
  // double root = b*b-3*a*g0;
  // if( root < 0 ){
  // log.info("QNminimizer:interpolateCubic - interpolate failed");
  // System.exit(1);
  // }
  // return (-b+Math.sqrt(root))/(3*a);
  // }

  // private double[] zoom(DiffFunction dfunc, double[] x, double[] dir,
  // double[] newX, double f0, double g0, double[] bestPoint, double[] endPoint)
  // throws MaxEvaluationsExceeded {
  // return zoom(dfunc,x, dir, newX,f0,g0, bestPoint, endPoint,null);
  // }

  // private double[] zoom(DiffFunction dfunc, double[] x, double[] dir,
  // double[] newX, double f0, double g0, double[] bestPt, double[] endPt,
  // double[] newPt) throws MaxEvaluationsExceeded {
  // double width = Math.abs(bestPt[a] - endPt[a]);
  // double reduction = 1.0;
  // double p66 = 0.66;
  // int info = 0;
  // double stpf;
  // double theta,gamma,s,p,q,r,stpc,stpq;
  // boolean bound = false;
  // boolean bracketed = false;
  // int cnt = 1;
  // if(newPt == null){ newPt = new double[3]; newPt[a] =
  // interpolate(bestPt,endPt);}// quadratic interp

  // do{
  // say(".");
  // newPt[f] = dfunc.valueAt((plusAndConstMult(x, dir, newPt[a] , newX)));
  // newPt[g] = ArrayMath.innerProduct(dfunc.derivativeAt(newX),dir);
  // fevals += 1;
  // //If we have satisfied Wolfe...
  // //fNew <= f0 + small*aNew*g0
  // //|gNew| <= 0.9999*|g0|
  // //return the point.
  // if( (newPt[f] <= f0 + ftol*newPt[a]*g0) && Math.abs(newPt[g]) <= -gtol*g0
  // ){
  // //Sweet, we found a point that satisfies the strong wolfe conditions!!!
  // lets return it.
  // return newPt;
  // }else{

  // double signG = newPt[g]*bestPt[g]/Math.abs(bestPt[g]);
  // //Our new point has a higher function value
  // if( newPt[f] > bestPt[f]){
  // info = 1;
  // bound = true;
  // theta = 3*(bestPt[f] - newPt[f])/(newPt[a] - bestPt[a]) + bestPt[g] +
  // newPt[g];
  // s = Math.max(Math.max(theta,newPt[g]), bestPt[g]);
  // gamma = s*Math.sqrt( (theta/s)*(theta/s) - (bestPt[g]/s)*(newPt[g]/s) );
  // if (newPt[a] < bestPt[a]){
  // gamma = -gamma;
  // }
  // p = (gamma - bestPt[g]) + theta;
  // q = ((gamma-bestPt[g]) + gamma) + newPt[g];
  // r = p/q;
  // stpc = bestPt[a] + r*(newPt[a] - bestPt[a]);
  // stpq = bestPt[a] +
  // ((bestPt[g]/((bestPt[f]-newPt[f])/(newPt[a]-bestPt[a])+bestPt[g]))/2)*(newPt[a]
  // - bestPt[a]);
  // if ( Math.abs(stpc-bestPt[a]) < Math.abs(stpq - bestPt[a] )){
  // stpf = stpc;
  // } else{
  // stpf = stpq;
  // //stpf = stpc + (stpq - stpc)/2;
  // }
  // bracketed = true;
  // if (newPt[a] < 0.1){
  // stpf = 0.01*stpf;
  // }

  // } else if (signG < 0.0){
  // info = 2;
  // bound = false;
  // theta = 3*(bestPt[f] - newPt[f])/(newPt[a] - bestPt[a]) + bestPt[g] +
  // newPt[g];
  // s = Math.max(Math.max(theta,bestPt[g]),newPt[g]);
  // gamma = s*Math.sqrt((theta/s)*(theta/s) - (bestPt[g]/s)*(newPt[g]/s));
  // if (newPt[a] > bestPt[a]) {
  // gamma = -gamma;
  // }
  // p = (gamma - newPt[g]) + theta;
  // q = ((gamma - newPt[g]) + gamma) + bestPt[g];
  // r = p/q;
  // stpc = newPt[a] + r*(bestPt[a] - newPt[a]);
  // stpq = newPt[a] + (newPt[g]/(newPt[g]-bestPt[g]))*(bestPt[a] - newPt[a]);
  // if (Math.abs(stpc-newPt[a]) > Math.abs(stpq-newPt[a])){
  // stpf = stpc;
  // } else {
  // stpf = stpq;
  // }
  // bracketed = true;
  // } else if ( Math.abs(newPt[g]) < Math.abs(bestPt[g])){
  // info = 3;
  // bound = true;
  // theta = 3*(bestPt[f] - newPt[f])/(newPt[a] - bestPt[a]) + bestPt[g] +
  // newPt[g];
  // s = Math.max(Math.max(theta,bestPt[g]),newPt[g]);
  // gamma = s*Math.sqrt(Math.max(0.0,(theta/s)*(theta/s) -
  // (bestPt[g]/s)*(newPt[g]/s)));
  // if (newPt[a] < bestPt[a]){
  // gamma = -gamma;
  // }
  // p = (gamma - bestPt[g]) + theta;
  // q = ((gamma-bestPt[g]) + gamma) + newPt[g];
  // r = p/q;
  // if (r < 0.0 && gamma != 0.0){
  // stpc = newPt[a] + r*(bestPt[a] - newPt[a]);
  // } else if (newPt[a] > bestPt[a]){
  // stpc = aMax;
  // } else{
  // stpc = aMin;
  // }
  // stpq = newPt[a] + (newPt[g]/(newPt[g]-bestPt[g]))*(bestPt[a] - newPt[a]);
  // if(bracketed){
  // if (Math.abs(newPt[a]-stpc) < Math.abs(newPt[a]-stpq)){
  // stpf = stpc;
  // } else {
  // stpf = stpq;
  // }
  // } else{
  // if (Math.abs(newPt[a]-stpc) > Math.abs(newPt[a]-stpq)){
  // stpf = stpc;
  // } else {
  // stpf = stpq;
  // }
  // }

  // }else{
  // info = 4;
  // bound = false;
  // if (bracketed){
  // theta = 3*(bestPt[f] - newPt[f])/(newPt[a] - bestPt[a]) + bestPt[g] +
  // newPt[g];
  // s = Math.max(Math.max(theta,bestPt[g]),newPt[g]);
  // gamma = s*Math.sqrt((theta/s)*(theta/s) - (bestPt[g]/s)*(newPt[g]/s));
  // if (newPt[a] > bestPt[a]) {
  // gamma = -gamma;
  // }
  // p = (gamma - newPt[g]) + theta;
  // q = ((gamma - newPt[g]) + gamma) + bestPt[g];
  // r = p/q;
  // stpc = newPt[a] + r*(bestPt[a] - newPt[a]);
  // stpf = stpc;
  // }else if( newPt[a] > bestPt[a]){
  // stpf = aMax;
  // } else {
  // stpf = aMin;
  // }
  // }
  // //Reduce the interval of uncertainty
  // if (newPt[f] > bestPt[f]) {
  // copy(newPt,endPt);
  // }else{
  // if (signG < 0.0){
  // copy(bestPt,endPt);
  // }
  // copy(newPt,bestPt);
  // }
  // say("" + info );
  // newPt[a] = stpf;
  // if(bracketed && bound){
  // if (endPt[a] > bestPt[a]){
  // newPt[a] = Math.min(bestPt[a]+p66*(endPt[a]-bestPt[a]),newPt[a]);
  // }else{
  // newPt[a] = Math.max(bestPt[a]+p66*(endPt[a]-bestPt[a]),newPt[a]);
  // }
  // }
  // }
  // //Check to see if the step has reached an extreme.
  // newPt[a] = Math.max(aMin, newPt[a]);
  // newPt[a] = Math.min(aMax,newPt[a]);
  // if( newPt[a] == aMin || newPt[a] == aMax){
  // return newPt;
  // }
  // cnt +=1;
  // if(fevals > maxFevals){
  // throw new MaxEvaluationsExceeded(" Exceeded during zoom() Function ");}
  // }while(true);
  // }
  // private double[] zoom2(DiffFunction dfunc, double[] x, double[] dir,
  // double[] newX, double f0, double g0, double[] bestPoint, double[] endPoint)
  // throws MaxEvaluationsExceeded {
  //
  // double[] newPoint = new double[3];
  // double width = Math.abs(bestPoint[a] - endPoint[a]);
  // double reduction = 0.0;
  //
  // int cnt = 1;
  //
  // //make sure the interval reduces enough.
  // //if(reduction >= 0.66){
  // //say(" |" + nf.format(reduction)+"| ");
  // //newPoint[a] = 0.5*(bestPoint[a]+endPoint[a]);
  // //} else{
  // newPoint[a] = interpolate(bestPoint,endPoint);// quadratic interp
  // //}
  //
  // do{
  // //Check to see if the step has reached an extreme.
  // newPoint[a] = Math.max(aMin, newPoint[a]);
  // newPoint[a] = Math.min(aMax,newPoint[a]);
  //
  // newPoint[f] = dfunc.valueAt((plusAndConstMult(x, dir, newPoint[a] ,
  // newX)));
  // newPoint[g] = ArrayMath.innerProduct(dfunc.derivativeAt(newX),dir);
  // fevals += 1;
  //
  // //fNew > f0 + small*aNew*g0 or fNew > fLow
  // if( (newPoint[f] > f0 + ftol*newPoint[a]*g0) || newPoint[f] > bestPoint[f]
  // ){
  // //Our new point didn't beat the best point, so just reduce the interval
  // copy(newPoint,endPoint);
  // say(".");//say("l");
  // }else{
  //
  // //if |gNew| <= 0.9999*|g0| If gNew is slightly smaller than g0
  // if( Math.abs(newPoint[g]) <= -gtol*g0 ){
  // //Sweet, we found a point that satisfies the strong wolfe conditions!!!
  // lets return it.
  // return newPoint;
  // }
  //
  // //If we made it this far, we've found a point that has satisfied descent,
  // but hasn't satsified
  // //the decrease in gradient. if the new gradient is telling us >0 we need to
  // look behind us
  // //if the new gradient is negative still we can increase the step.
  // if(newPoint[g]*(endPoint[a] - bestPoint[a] ) >= 0){
  // //Get going the right way.
  // say(".");//say("f");
  // copy(bestPoint,endPoint);
  // }
  //
  // if( (Math.abs(newPoint[a]-bestPoint[a]) < 1e-6) ||
  // (Math.abs(newPoint[a]-endPoint[a]) < 1e-6) ){
  // //Not moving fast enough.
  // sayln("had to improvise a bit");
  // newPoint[a] = 0.5*(bestPoint[a] + endPoint[a]);
  // }
  //
  // say(".");//say("r");
  // copy(newPoint,bestPoint);
  // }
  //
  //
  // if( newPoint[a] == aMin || newPoint[a] == aMax){
  // return newPoint;
  // }
  //
  // reduction = Math.abs(bestPoint[a] - endPoint[a]) / width;
  // width = Math.abs(bestPoint[a] - endPoint[a]);
  //
  // cnt +=1;
  //
  //
  // //if(Math.abs(bestPoint[a] -endPoint[a]) < 1e-12 ){
  // //sayln();
  // //sayln("!!!!!!!!!!!!!!!!!!");
  // //sayln("points are too close");
  // //sayln("!!!!!!!!!!!!!!!!!!");
  // //sayln("f0 " + nf.format(f0));
  // //sayln("f0+crap " + nf.format(f0 + cVal*bestPoint[a]*g0));
  // //sayln("g0 " + nf.format(g0));
  // //sayln("ptLow");
  // //printPt(bestPoint);
  // //sayln();
  // //sayln("ptHigh");
  // //printPt(endPoint);
  // //sayln();
  //
  // //DiffFunctionTester.test(dfunc, x,1e-4);
  // //System.exit(1);
  // ////return dfunc.valueAt((plusAndConstMult(x, dir, aMin , newX)));
  // //}
  //
  // //if( (cnt > 20) ){
  //
  // //sayln("!!!!!!!!!!!!!!!!!!");
  // //sayln("! " + cnt + " iterations. I think we're out of luck");
  // //sayln("!!!!!!!!!!!!!!!!!!");
  // //sayln("f0" + nf.format(f0));
  // //sayln("f0+crap" + nf.format(f0 + cVal*bestPoint[a]*g0));
  // //sayln("g0 " + nf.format(g0));
  // //sayln("bestPoint");
  // //printPt(bestPoint);
  // //sayln();
  // //sayln("ptHigh");
  // //printPt(endPoint);
  // //sayln();
  //
  //
  //
  // ////if( cnt > 25 || fevals > maxFevals){
  // ////log.info("Max evaluations exceeded.");
  // ////System.exit(1);
  // ////return dfunc.valueAt((plusAndConstMult(x, dir, aMin , newX)));
  // ////}
  // //}
  //
  // if(fevals > maxFevals){ throw new MaxEvaluationsExceeded(" Exceeded during
  // zoom() Function ");}
  //
  // }while(true);
  //
  // }
  //
  // private double lineSearchNocedal(DiffFunction dfunc, double[] dir, double[]
  // x, double[] newX, double[] grad, double f0, int maxEvals){
  //
  // boolean bracketed = false;
  // boolean stage1 = false;
  // double width = aMax - aMin;
  // double width1 = 2*width;
  // double stepMin = 0.0;
  // double stepMax = 0.0;
  // double xtrapf = 4.0;
  // int nFevals = 0;
  // double TOL = 1e-4;
  // double X_TOL = 1e-8;
  // int info = 0;
  // int infoc = 1;
  //
  // double g0 = ArrayMath.innerProduct(grad,dir);
  // if(g0 > 0){
  // //We're looking in a direction of positive gradient. This wont' work.
  // //set dir = -grad
  // plusAndConstMult(new double[x.length],grad,-1,dir);
  // g0 = ArrayMath.innerProduct(grad,dir);
  // log.info("Searching in direction of positive gradient.");
  // }
  // say("(" + nf.format(g0) + ")");
  //
  //
  // double[] newPt = new double[3];
  // double[] bestPt = new double[3];
  // double[] endPt = new double[3];
  //
  // newPt[a] = 1.0; //Always guess 1 first, this should be right if the
  // function is "nice" and BFGS is working.
  //
  // if(its == 1){
  // newPt[a] = 1e-6; // Guess low at first since we have no idea of scale.
  // }
  //
  // bestPt[a] = 0.0;
  // bestPt[f] = f0;
  // bestPt[g] = g0;
  //
  // endPt[a] = 0.0;
  // endPt[f] = f0;
  // endPt[g] = g0;
  //
  // int cnt = 0;
  //
  // do{
  // //Determine the max and min step size given what we know already.
  // if(bracketed){
  // stepMin = Math.min(bestPt[a], endPt[a]);
  // stepMax = Math.max(bestPt[a], endPt[a]);
  // } else{
  // stepMin = bestPt[a];
  // stepMax = newPt[a] + xtrapf*(newPt[a] - bestPt[a]);
  // }
  //
  // //Make sure our next guess is within the bounds
  // newPt[a] = Math.max(newPt[a], stepMin);
  // newPt[a] = Math.min(newPt[a], stepMax);
  //
  // if( (bracketed && (newPt[a] <= stepMin || newPt[a] >= stepMax) )
  // || nFevals > maxEvals || (bracketed & (stepMax-stepMin) <= TOL*stepMax)){
  // log.info("Linesearch for QN, Need to make srue that newX is set
  // before returning bestPt. -akleeman");
  // System.exit(1);
  // return bestPt[f];
  // }
  //
  //
  // newPt[f] = dfunc.valueAt((plusAndConstMult(x, dir, newPt[a], newX)));
  // newPt[g] = ArrayMath.innerProduct(dfunc.derivativeAt(newX),dir);
  // nFevals += 1;
  //
  // double fTest = f0 + newPt[a]*g0;
  //
  // log.info("fTest " + fTest + " new" + newPt[a] + " newf" +
  // newPt[f] + " newg" + newPt[g] );
  //
  // if( ( bracketed && (newPt[a] <= stepMin | newPt[a] >= stepMax )) || infoc
  // == 0){
  // info = 6;
  // }
  //
  // if( newPt[a] == stepMax && ( newPt[f] <= fTest || newPt[g] >= ftol*g0 )){
  // info = 5;
  // }
  //
  // if( (newPt[a] == stepMin && ( newPt[f] > fTest || newPt[g] >= ftol*g0 ) )){
  // info = 4;
  // }
  //
  // if( (nFevals >= maxEvals)){
  // info = 3;
  // }
  //
  // if( bracketed && stepMax-stepMin <= X_TOL*stepMax){
  // info = 2;
  // }
  //
  // if( (newPt[f] <= fTest) && (Math.abs(newPt[g]) <= - gtol*g0) ){
  // info = 1;
  // }
  //
  // if(info != 0){
  // return newPt[f];
  // }
  //
  // if(stage1 && newPt[f]< fTest && newPt[g] >= ftol*g0){
  // stage1 = false;
  // }
  //
  //
  // if( stage1 && f<= bestPt[f] && f > fTest){
  //
  // double[] newPtMod = new double[3];
  // double[] bestPtMod = new double[3];
  // double[] endPtMod = new double[3];
  //
  // newPtMod[f] = newPt[f] - newPt[a]*ftol*g0;
  // newPtMod[g] = newPt[g] - ftol*g0;
  // bestPtMod[f] = bestPt[f] - bestPt[a]*ftol*g0;
  // bestPtMod[g] = bestPt[g] - ftol*g0;
  // endPtMod[f] = endPt[f] - endPt[a]*ftol*g0;
  // endPtMod[g] = endPt[g] - ftol*g0;
  //
  // //this.cstep(newPtMod, bestPtMod, endPtMod, bracketed);
  //
  // bestPt[f] = bestPtMod[f] + bestPt[a]*ftol*g0;
  // bestPt[g] = bestPtMod[g] + ftol*g0;
  // endPt[f] = endPtMod[f] + endPt[a]*ftol*g0;
  // endPt[g] = endPtMod[g] + ftol*g0;
  //
  // }else{
  // //this.cstep(newPt, bestPt, endPt, bracketed);
  // }
  //
  // double p66 = 0.66;
  // double p5 = 0.5;
  //
  // if(bracketed){
  // if ( Math.abs(endPt[a] - bestPt[a]) >= p66*width1){
  // newPt[a] = bestPt[a] + p5*(endPt[a]-bestPt[a]);
  // }
  // width1 = width;
  // width = Math.abs(endPt[a]-bestPt[a]);
  // }
  //
  //
  //
  // }while(true);
  //
  // }
  //
  // private double cstepBackup( double[] newPt, double[] bestPt, double[]
  // endPt, boolean bracketed ){
  //
  // double p66 = 0.66;
  // int info = 0;
  // double stpf;
  // double theta,gamma,s,p,q,r,stpc,stpq;
  // boolean bound = false;
  //
  // double signG = newPt[g]*bestPt[g]/Math.abs(bestPt[g]);
  //
  //
  // //Our new point has a higher function value
  // if( newPt[f] > bestPt[f]){
  // info = 1;
  // bound = true;
  // theta = 3*(bestPt[f] - newPt[f])/(newPt[a] - bestPt[a]) + bestPt[g] +
  // newPt[g];
  // s = Math.max(Math.max(theta,newPt[g]), bestPt[g]);
  // gamma = s*Math.sqrt( (theta/s)*(theta/s) - (bestPt[g]/s)*(newPt[g]/s) );
  // if (newPt[a] < bestPt[a]){
  // gamma = -gamma;
  // }
  // p = (gamma - bestPt[g]) + theta;
  // q = ((gamma-bestPt[g]) + gamma) + newPt[g];
  // r = p/q;
  // stpc = bestPt[a] + r*(newPt[a] - bestPt[a]);
  // stpq = bestPt[a] +
  // ((bestPt[g]/((bestPt[f]-newPt[f])/(newPt[a]-bestPt[a])+bestPt[g]))/2)*(newPt[a]
  // - bestPt[a]);
  //
  // if ( Math.abs(stpc-bestPt[a]) < Math.abs(stpq - bestPt[a] )){
  // stpf = stpc;
  // } else{
  // stpf = stpc + (stpq - stpc)/2;
  // }
  // bracketed = true;
  //
  // } else if (signG < 0.0){
  //
  // info = 2;
  // bound = false;
  // theta = 3*(bestPt[f] - newPt[f])/(newPt[a] - bestPt[a]) + bestPt[g] +
  // newPt[g];
  // s = Math.max(Math.max(theta,bestPt[g]),newPt[g]);
  // gamma = s*Math.sqrt((theta/s)*(theta/s) - (bestPt[g]/s)*(newPt[g]/s));
  // if (newPt[a] > bestPt[a]) {
  // gamma = -gamma;
  // }
  // p = (gamma - newPt[g]) + theta;
  // q = ((gamma - newPt[g]) + gamma) + bestPt[g];
  // r = p/q;
  // stpc = newPt[a] + r*(bestPt[a] - newPt[a]);
  // stpq = newPt[a] + (newPt[g]/(newPt[g]-bestPt[g]))*(bestPt[a] - newPt[a]);
  // if (Math.abs(stpc-newPt[a]) > Math.abs(stpq-newPt[a])){
  // stpf = stpc;
  // } else {
  // stpf = stpq;
  // }
  // bracketed = true;
  // } else if ( Math.abs(newPt[g]) < Math.abs(bestPt[g])){
  // info = 3;
  // bound = true;
  // theta = 3*(bestPt[f] - newPt[f])/(newPt[a] - bestPt[a]) + bestPt[g] +
  // newPt[g];
  // s = Math.max(Math.max(theta,bestPt[g]),newPt[g]);
  // gamma = s*Math.sqrt(Math.max(0.0,(theta/s)*(theta/s) -
  // (bestPt[g]/s)*(newPt[g]/s)));
  // if (newPt[a] < bestPt[a]){
  // gamma = -gamma;
  // }
  // p = (gamma - bestPt[g]) + theta;
  // q = ((gamma-bestPt[g]) + gamma) + newPt[g];
  // r = p/q;
  // if (r < 0.0 && gamma != 0.0){
  // stpc = newPt[a] + r*(bestPt[a] - newPt[a]);
  // } else if (newPt[a] > bestPt[a]){
  // stpc = aMax;
  // } else{
  // stpc = aMin;
  // }
  // stpq = newPt[a] + (newPt[g]/(newPt[g]-bestPt[g]))*(bestPt[a] - newPt[a]);
  // if (bracketed){
  // if (Math.abs(newPt[a]-stpc) < Math.abs(newPt[a]-stpq)){
  // stpf = stpc;
  // } else {
  // stpf = stpq;
  // }
  // } else {
  // if (Math.abs(newPt[a]-stpc) > Math.abs(newPt[a]-stpq)){
  // log.info("modified to take only quad");
  // stpf = stpq;
  // }else{
  // stpf = stpq;
  // }
  // }
  //
  //
  // }else{
  // info = 4;
  // bound = false;
  //
  // if(bracketed){
  // theta = 3*(bestPt[f] - newPt[f])/(newPt[a] - bestPt[a]) + bestPt[g] +
  // newPt[g];
  // s = Math.max(Math.max(theta,bestPt[g]),newPt[g]);
  // gamma = s*Math.sqrt((theta/s)*(theta/s) - (bestPt[g]/s)*(newPt[g]/s));
  // if (newPt[a] > bestPt[a]) {
  // gamma = -gamma;
  // }
  // p = (gamma - newPt[g]) + theta;
  // q = ((gamma - newPt[g]) + gamma) + bestPt[g];
  // r = p/q;
  // stpc = newPt[a] + r*(bestPt[a] - newPt[a]);
  // stpf = stpc;
  // }else if (newPt[a] > bestPt[a]){
  // stpf = aMax;
  // }else{
  // stpf = aMin;
  // }
  //
  // }
  //
  //
  // if (newPt[f] > bestPt[f]) {
  // copy(newPt,endPt);
  // }else{
  // if (signG < 0.0){
  // copy(bestPt,endPt);
  // }
  // copy(newPt,bestPt);
  // }
  //
  // stpf = Math.min(aMax,stpf);
  // stpf = Math.max(aMin,stpf);
  // newPt[a] = stpf;
  // if (bracketed & bound){
  // if (endPt[a] > bestPt[a]){
  // newPt[a] = Math.min(bestPt[a]+p66*(endPt[a]-bestPt[a]),newPt[a]);
  // }else{
  // newPt[a] = Math.max(bestPt[a]+p66*(endPt[a]-bestPt[a]),newPt[a]);
  // }
  // }
  //
  // //newPt[f] =
  // log.info("cstep " + nf.format(newPt[a]) + " info " + info);
  // return newPt[a];
  //
  // }

}
