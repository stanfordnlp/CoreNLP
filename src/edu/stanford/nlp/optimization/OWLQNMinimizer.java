package edu.stanford.nlp.optimization;

import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.math.ArrayMath;

import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;

/**
 * Class implementing the Orthant-Wise Limited-memory Quasi-Newton
 * algorithm (OWL-QN). OWN-QN is a numerical optimization procedure for
 * finding the optimum of an objective of the form smooth function plus
 * L1-norm of the parameters. It has been used for training log-linear
 * models (such as logistic regression) with L1-regularization. The
 * algorithm is described in "Scalable training of L1-regularized
 * log-linear models" by Galen Andrew and Jianfeng Gao. This
 * implementation includes built-in capacity to train logistic regression
 * or least-squares models with L1 regularization. It is also possible to
 * use OWL-QN to optimize any arbitrary smooth convex loss plus L1
 * regularization by defining the function and its gradient using the
 * supplied "DifferentiableFunction" class, and passing an instance of
 * the function to the OWLQN object. For more information, please read
 * the included file README.txt. Also included in the distribution are
 * the ICML paper and slide presentation.
 *
 * Significant portions of this code are taken from
 * <a href="http://research.microsoft.com/en-us/downloads/b1eb1016-1738-4bd5-83a9-370c9d498a03/default.aspx">Galen Andrew's implementation</a>
 *
 * @author Michel Galley
 */
public class OWLQNMinimizer implements Minimizer<DiffFunction> {

  public static final boolean DEBUG = false;

  private final int m;
  private final double regweight;
  private boolean quiet = false;

  public OWLQNMinimizer() {
    this(1.0);
  }

  public OWLQNMinimizer(double regweight) {
    this(regweight, 10);
  }

  public OWLQNMinimizer(double regweight, int m) {
    this.regweight = regweight;
    this.m = m;
  }

  // makes it more convenient to load by reflection, since you can
  // pass around Double as Object but can't pass double that way
  public OWLQNMinimizer(Double regweight) {
    this(regweight, 10);
  }

  public OWLQNMinimizer shutUp() {
    this.quiet = true;
    return this;
  }

  public double[] minimize(DiffFunction function, double functionTolerance, double[] initial) {
    return minimize(function, functionTolerance, initial, m);
  }

  public double[] minimize(DiffFunction function, double functionTolerance, double[] initial, int maxIterations) {
    OWLQN qn = new OWLQN(quiet);
    return qn.minimize(function, initial, regweight, functionTolerance, maxIterations);
  }

  public static void main(String[] args) {

    if(args.length != 5) {

      System.err.println("Usage: OWLQNMinimizer (x-values) (y-values) (tol) (regweight) (l2weight)");

    } else {

      LogisticRegressionProblem problem = new LogisticRegressionProblem(args[0], args[1]);

      double tol = Double.parseDouble(args[2]);
      double regweight = Double.parseDouble(args[3]);
      double l2weight = Double.parseDouble(args[4]);

      int size = problem.numFeats();
      System.err.println("num features: "+size);

      double[] init = new double[size];

      LogisticRegressionObjective obj = new LogisticRegressionObjective(problem, l2weight);
      OWLQNMinimizer minimizer = new OWLQNMinimizer(regweight);
      double[] opt = minimizer.minimize(obj, tol, init);

      System.err.println(Arrays.toString(opt));

    }
  }

  interface TerminationCriterion {
    double getValue(OptimizerState state, StringBuilder out);
  }

  static class RelativeMeanImprovementCriterion implements TerminationCriterion {
    private final int numItersToAvg;
    private Queue<Double> prevVals;

    RelativeMeanImprovementCriterion() {
      this(5);
    }

    RelativeMeanImprovementCriterion(int numItersToAvg) {
      this.numItersToAvg = numItersToAvg;
      this.prevVals = new LinkedList<Double>();
    }

    public double getValue(OptimizerState state, StringBuilder out) {

      double retVal = Double.POSITIVE_INFINITY;

      if (prevVals.size() > 5) {
        double prevVal = prevVals.peek();
        if (prevVals.size() == 10) prevVals.poll();
        double averageImprovement = (prevVal - state.getValue()) / prevVals.size();
        double relAvgImpr = averageImprovement / Math.abs(state.getValue());
        String relAvgImprStr = String.format("%.4e",relAvgImpr);
        out.append("  (").append(relAvgImprStr).append(") ");
        retVal = relAvgImpr;
      } else {
        out.append("  (wait for five iters) ");
      }

      prevVals.offer(state.getValue());
      return retVal;
    }
  } // end static class RelativeMeanImprovementCriterion

  static class OWLQN {

    boolean quiet;
    boolean responsibleForTermCrit;

    TerminationCriterion termCrit;

    OWLQN(boolean quiet) {
      this.quiet = quiet;
      this.termCrit = new RelativeMeanImprovementCriterion(5);
      this.responsibleForTermCrit = true;
    }

    OWLQN() {
      this(false);
    }

    OWLQN(TerminationCriterion termCrit, boolean quiet) {
      this.quiet = quiet;
      this.termCrit = termCrit;
      this.responsibleForTermCrit = false;
    }

    void setQuiet(boolean q) {
      quiet = q;
    }

    void minimize(DiffFunction function, double[] initial) {
      minimize(function, initial, 1.0);
    }

    void minimize(DiffFunction function, double[] initial, double l1weight) {
      minimize(function, initial, l1weight, 1e-4);
    }

    void minimize(DiffFunction function, double[] initial, double l1weight, double tol) {
      minimize(function, initial, l1weight, tol, 10);
    }

    double[] minimize(DiffFunction function, double[] initial, double l1weight, double tol, int m) {

      OptimizerState state = new OptimizerState(function, initial, m, l1weight, quiet);

      if (!quiet) {
        System.err.printf("Optimizing function of %d variables with OWL-QN parameters:\n", state.dim);
        System.err.printf("   l1 regularization weight: %f.\n", l1weight);
        System.err.printf("   L-BFGS memory parameter (m): %d\n", m);
        System.err.printf("   Convergence tolerance: %f\n\n", tol);
        System.err.printf("Iter    n:  new_value    (conv_crit)   line_search\n");
        System.err.printf("Iter    0:  %.4e  (***********) ", state.value);
      }

      StringBuilder buf = new StringBuilder();
      termCrit.getValue(state, buf);

      while (true) {
        buf.setLength(0);
        state.updateDir();
        state.backTrackingLineSearch();

        double termCritVal = termCrit.getValue(state, buf);
        if (!quiet) {
          System.err.printf("Iter %4d:  %.4e", state.iter, state.value);
          System.err.print(" "+ buf.toString());
        }

        if (termCritVal < tol)
          break;

        state.shift();
      }

      if (!quiet) {
        System.err.println();
        System.err.printf("Finished with optimization.  %d/%d non-zero weights.\n",
                ArrayMath.countNonZero(state.newX), state.newX.length);
        //System.err.println(Arrays.toString(state.newX));
      }

      return state.newX;
    }
  } // end static class OWLQN

  static class OptimizerState {

    double[] x, grad, newX, newGrad, dir;
    double[] steepestDescDir;
    LinkedList<double[]> sList = new LinkedList<double[]>();
    LinkedList<double[]> yList = new LinkedList<double[]>();
    LinkedList<Double> roList = new LinkedList<Double>();
    double[] alphas;
    double value;
    int iter, m;
    int dim;
    DiffFunction func;
    double l1weight;
    boolean quiet;


    void mapDirByInverseHessian() {
      int count = sList.size();

      if (count != 0) {
        for (int i = count - 1; i >= 0; i--) {
          alphas[i] = -ArrayMath.innerProduct(sList.get(i), dir) / roList.get(i);
          ArrayMath.addMultInPlace(dir, yList.get(i), alphas[i]);
        }

        double[] lastY = yList.get(count - 1);
        double yDotY = ArrayMath.innerProduct(lastY, lastY);
        double scalar = roList.get(count - 1) / yDotY;
        ArrayMath.multiplyInPlace(dir, scalar);

        for (int i = 0; i < count; i++) {
          double beta = ArrayMath.innerProduct(yList.get(i), dir) / roList.get(i);
          ArrayMath.addMultInPlace(dir, sList.get(i), -alphas[i] - beta);
        }
      }
    }

    void makeSteepestDescDir() {
      if (l1weight == 0) {
        ArrayMath.multiplyInto(dir, grad, -1);
      } else {

        for (int i=0; i<dim; i++) {
          if (x[i] < 0) {
            dir[i] = -grad[i] + l1weight;
          } else if (x[i] > 0) {
            dir[i] = -grad[i] - l1weight;
          } else {
            if (grad[i] < -l1weight) {
              dir[i] = -grad[i] - l1weight;
            } else if (grad[i] > l1weight) {
              dir[i] = -grad[i] + l1weight;
            } else {
              dir[i] = 0;
            }
          }
        }
      }
      steepestDescDir = dir.clone(); // deep copy needed
    }

    void fixDirSigns() {
      if (l1weight > 0) {
        for (int i = 0; i<dim; i++) {
          if (dir[i] * steepestDescDir[i] <= 0) {
            dir[i] = 0;
          }
        }
      }
    }

    void updateDir() {
      makeSteepestDescDir();
      mapDirByInverseHessian();
      fixDirSigns();
      if(DEBUG)
        testDirDeriv();
    }

    void testDirDeriv() {
      double dirNorm = Math.sqrt(ArrayMath.innerProduct(dir, dir));
      double eps = 1.05e-8 / dirNorm;
      getNextPoint(eps);
      double val2 = evalL1();
      double numDeriv = (val2 - value) / eps;
      double deriv = dirDeriv();
      if (!quiet) System.err.print("  Grad check: " + numDeriv + " vs. " + deriv + "  ");
    }

    double dirDeriv() {
      if (l1weight == 0) {
        return ArrayMath.innerProduct(dir, grad);
      } else {
        double val = 0.0;
        for (int i = 0; i < dim; i++) {
          if (dir[i] != 0) {
            if (x[i] < 0) {
              val += dir[i] * (grad[i] - l1weight);
            } else if (x[i] > 0) {
              val += dir[i] * (grad[i] + l1weight);
            } else if (dir[i] < 0) {
              val += dir[i] * (grad[i] - l1weight);
            } else if (dir[i] > 0) {
              val += dir[i] * (grad[i] + l1weight);
            }
          }
        }
        return val;
      }
    }

    void getNextPoint(double alpha) {
      ArrayMath.addMultInto(newX, x, dir, alpha);
      if (l1weight > 0) {
        for (int i=0; i<dim; i++) {
          if (x[i] * newX[i] < 0.0) {
            newX[i] = 0.0;
          }
        }
      }
    }

    double evalL1() {

      double val = func.valueAt(newX);
      // Don't remove clone(), otherwise newGrad and grad may end up referencing the same vector
      // (that's the case with LogisticObjectiveFunction)
      newGrad = func.derivativeAt(newX).clone();
      if (l1weight > 0) {
        for (int i=0; i<dim; i++) {
          val += Math.abs(newX[i]) * l1weight;
        }
      }

      return val;
    }

    void backTrackingLineSearch() {

      double origDirDeriv = dirDeriv();
      // if a non-descent direction is chosen, the line search will break anyway, so throw here
      // The most likely reason for this is a bug in your function's gradient computation
      if (origDirDeriv >= 0) {
        throw new RuntimeException("L-BFGS chose a non-descent direction: check your gradient!");
      }

      double alpha = 1.0;
      double backoff = 0.5;
      if (iter == 1) {
        double normDir = Math.sqrt(ArrayMath.innerProduct(dir, dir));
        alpha = (1 / normDir);
        backoff = 0.1;
      }

      double c1 = 1e-4;
      double oldValue = value;

      while (true) {
        getNextPoint(alpha);
        value = evalL1();

        if (value <= oldValue + c1 * origDirDeriv * alpha)
          break;

        if (!quiet) System.err.print(".");

        alpha *= backoff;
      }

      if (!quiet) System.err.println();
    }

    void shift() {
      double[] nextS = null, nextY = null;

      int listSize = sList.size();

      if (listSize < m) {
        try {
          nextS = new double[dim];
          nextY = new double[dim];
        } catch (OutOfMemoryError e) {
          m = listSize;
          nextS = null;
        }
      }

      if (nextS == null) {
        nextS = sList.poll();
        nextY = yList.poll();
        roList.poll();
      }

      ArrayMath.addMultInto(nextS, newX, x, -1);
      ArrayMath.addMultInto(nextY, newGrad, grad, -1);

      double ro = ArrayMath.innerProduct(nextS, nextY);
      assert(ro != 0.0);

      sList.offer(nextS);
      yList.offer(nextY);
      roList.offer(ro);

      double[] tmpX = newX;
      newX = x;
      x = tmpX;

      double[] tmpGrad = newGrad;
      newGrad = grad;
      grad = tmpGrad;

      ++iter;
    }

    double getValue() { return value; }

    OptimizerState(DiffFunction f, double[] init, int m, double l1weight, boolean quiet) {
      this.x = init;
      this.grad = new double[init.length];
      this.newX = init.clone();
      this.newGrad = new double[init.length];
      this.dir = new double[init.length];
      this.steepestDescDir = newGrad.clone();
      this.alphas = new double[m];
      this.iter = 1;
      this.m = m;
      this.dim = init.length;
      this.func = f;
      this.l1weight = l1weight;
      this.quiet = quiet;

      if (m <= 0)
        throw new RuntimeException("m must be an integer greater than zero.");

      value = evalL1();
      grad = newGrad.clone();
    }
  } // end static class OptimizerState


// No longer really needed (OWLQNMinimizer now works fine with LogisticClassifier, etc.), but used in main().

  static class LogisticRegressionProblem {

    List<Integer> indices;
    List<Double> values;
    List<Integer> instance_starts;
    List<Boolean> labels;

    int numFeats;

    @SuppressWarnings("unchecked")
    private Pair<List<Integer>[],List<Double>[]> readMatrixFile(String filename, boolean coordinate) {

      int lineNb=0;

      List<Integer>[] rowInds = null;
      List<Double>[] rowVals = null;

      int numIns=-1, finalNumNonZero=-1, numNonZero=0;

      // Parse data:
      for(String line : ObjectBank.getLineIterator(filename)) {

        line = line.trim();

        // First line should contain %%MatrixMarket:
        if(lineNb == 0) {
          if(!line.startsWith("%%MatrixMarket"))
            throw new RuntimeException("Matrix file must be in MatrixMarket format.");
        } else {
          line = line.replaceAll("%.*","");
          // Matrix size:
          if(lineNb == 1 && line.length() > 0) {
            String[] sizes = line.split("\\s+");
            if(sizes.length != 2 && sizes.length != 3)
              throw new RuntimeException("Bad size specification in MatrixMarket file: "+line);
            numIns = Integer.parseInt(sizes[0]);
            numFeats = Integer.parseInt(sizes[1]);
            finalNumNonZero = (sizes.length == 3) ? Integer.parseInt(sizes[2]) : numIns*numFeats;

            rowInds = new List[numIns];
            rowVals = new List[numIns];
            for(int i=0; i<numIns; ++i) {
              rowInds[i] = new ArrayList<Integer>();
              rowVals[i] = new ArrayList<Double>();
            }
          } else
          // Subsequent lines may contain comments:
          if(line.length() == 0 || line.startsWith("%")) {
            continue;
          // Read data:
          } else {
            if(rowInds == null)
              throw new RuntimeException("File lacks matrix size.");
            String[] data = line.split("\\s+");
            if(coordinate) {
              assert(data.length == 3);
              int row = Integer.parseInt(data[0]);
              int col = Integer.parseInt(data[1]);
              double val = Double.parseDouble(data[2]);
              row--;
              col--;
              rowInds[row].add(col);
              rowVals[row].add(val);
              ++numNonZero;
            } else {
              assert(data.length == 1);
              int row = numNonZero / numFeats;
              int col = numNonZero % numIns;
              rowInds[row].add(col);
              rowVals[row].add(Double.parseDouble(data[0]));
              ++numNonZero;
            }
          }
        }
        ++lineNb;
      }

      if(numNonZero != finalNumNonZero) {
        throw new RuntimeException(String.format("Bad number of non-zero elements: %d != %d\n", numNonZero, finalNumNonZero));
      }
      assert(rowInds.length == rowVals.length);
      return new Pair<List<Integer>[],List<Double>[]>(rowInds,rowVals);
    }

    public LogisticRegressionProblem(String matFilename, String labelFilename) {

      indices = new ArrayList<Integer>();
      values = new ArrayList<Double>();
      labels = new ArrayList<Boolean>();
      instance_starts = new ArrayList<Integer>();
      instance_starts.add(0);

      Pair<List<Integer>[],List<Double>[]> y = readMatrixFile(labelFilename, false);
      Pair<List<Integer>[],List<Double>[]> x = readMatrixFile(matFilename, true);
      assert(x.first().length == y.first().length);

      for(int i=0; i<x.first().length; ++i) {
        List<Double> yi = y.second()[i];
        assert(yi.size() == 1);
        double yiVal = yi.get(0);
        assert(Math.abs(yiVal) == 1.0);
        addInstance(x.first()[i], x.second()[i], yiVal == 1);
      }
    }

    void addInstance(List<Integer> inds, List<Double> vals, boolean label) {
      for (int i=0; i<inds.size(); i++) {
        indices.add(inds.get(i));
        values.add(vals.get(i));
      }
      instance_starts.add(indices.size());
      labels.add(label);
    }

    void addInstance(List<Double> vals, boolean label) {
      for(double val : vals) {
        values.add(val);
      }
      instance_starts.add(values.size());
      labels.add(label);
    }

    double scoreOf(int i, double[] weights) {
      double score = 0;
      for (int j=instance_starts.get(i); j < instance_starts.get(i+1); j++) {
        double value = values.get(j);
        int index = (indices.size() > 0) ? indices.get(j) : j - instance_starts.get(i);
        score += weights[index] * value;
      }
      if (!labels.get(i)) score *= -1;
      return score;
    }

    boolean labelOf(int i) {
      return labels.get(i);
    }

    void addMultTo(int i, double mult, double[] vec) {
      if (labels.get(i)) mult *= -1;
      for (int j=instance_starts.get(i); j < instance_starts.get(i+1); j++) {
        int index = (indices.size() > 0) ? indices.get(j) : j - instance_starts.get(i);
        vec[index] += mult * values.get(j);
      }
    }

    int numInstances() {
      return labels.size();
    }

    int numFeats() {
      return numFeats;
    }
  }

  static class LogisticRegressionObjective implements DiffFunction {

    LogisticRegressionProblem problem;

    double l2weight;

    LogisticRegressionObjective(LogisticRegressionProblem p) {
      this(p, 0.0);
    }

    LogisticRegressionObjective(LogisticRegressionProblem p, double l2weight) {
      this.problem = p;
      this.l2weight = l2weight;
    }

    public double valueAt(double[] input) {

      double loss = 1.0;

      for (double anInput : input)
        loss += 0.5 * anInput * anInput * l2weight;

      for (int i=0; i<problem.numInstances(); i++) {
        double score = problem.scoreOf(i, input), insLoss;
        if (score < -30) {
          insLoss = -score;
        } else if (score > 30) {
          insLoss = 0;
        } else {
          double temp = 1.0 + Math.exp(-score);
          insLoss = Math.log(temp);
        }
        loss += insLoss;
      }

      return loss;
    }

    public double[] derivativeAt(double[] input) {
      double[] gradient = new double[input.length];

      for (int i=0; i<input.length; i++)
        gradient[i] = l2weight * input[i];

      for (int i=0; i<problem.numInstances(); i++) {
        double score = problem.scoreOf(i, input);
        double insProb;
        if (score < -30) {
          insProb = 0;
        } else if (score > 30) {
          insProb = 1;
        } else {
          double temp = 1.0 + Math.exp(-score);
          insProb = 1.0/temp;
        }
        problem.addMultTo(i, 1.0 - insProb, gradient);
      }
      return gradient;
    }

    public int domainDimension() {
      return problem.numFeats();
    }

  } // end static class LogisticRegressionObjective

} // end class OWLQNMinimizer
