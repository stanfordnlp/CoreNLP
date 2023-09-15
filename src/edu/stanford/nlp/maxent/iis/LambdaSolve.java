package edu.stanford.nlp.maxent.iis; 

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.maxent.*;
import edu.stanford.nlp.util.MutableDouble;
import edu.stanford.nlp.util.logging.Redwood;

import java.text.NumberFormat;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;


/**
 * This is the main class that does the core computation in IIS.
 * (Parts of it still get invoked in the POS tagger, even when not using IIS.)
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class LambdaSolve  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(LambdaSolve.class);

  /**
   * These are the model parameters that have to be learned.
   * This field is used at runtime in all tagger and other IIS/Kristina code.
   */
  public double[] lambda;

  /** Only allocated and used in the IIS optimization routines. */
  private boolean[] lambda_converged;

  /** Only used in the IIS optimization routines. Convergence threshold / allowed "newtonErr" */
  protected double eps;
  // protected double newtonerr;

  /**
   * This flag is true if all (x,y) have the same f# in which case the newton equation solving is avoided.
   */
  private boolean fixedFnumXY;

  protected Problem p;

  /**
   * Conditional probabilities.
   */
  protected double[][] probConds;

  /**
   * Normalization factors, one for each x.  (CDM questions 2008: Are these
   * only at training time?  Can we not allocate it at test time (unlike
   * what LambdaSolveTagger now does)?  Is the place where it is set from
   * ySize wrong?
   */
  protected double[] zlambda;

  /**
   * This contains the number of features active for each pair (x,y)
   */

  protected byte[][] fnumArr;

  /**
   * This is an array of empirical expectations for the features
   */
  protected double[] ftildeArr;

  private static final boolean smooth = false;
  private static final boolean VERBOSE = false;

  /**
   * If this is true, assume that active features are binary, and one
   * does not have to multiply in a feature value.
   */
  private boolean ASSUME_BINARY = false;

  private double[] aux;  // auxiliary array used by some procedures for computing objective functions and their derivatives
  private double[][] sum;// auxiliary array
  private double[][] sub;// auxiliary array
  public boolean weightRanks = false;
  private boolean convertValues = false;


  public LambdaSolve(Problem p1, double eps1, double nerr1) {
    p = p1;
    eps = eps1;
    // newtonerr = nerr1;
    // lambda = new double[p.fSize];
    probConds = new double[p.data.xSize][];
    log.info("xSize is " + p.data.xSize);

    for (int i = 0; i < p.data.xSize; i++) {
      probConds[i] = new double[p.data.numY(i)];
    }
    fnumArr = new byte[p.data.xSize][];
    for (int i = 0; i < p.data.xSize; i++) {
      fnumArr[i] = new byte[p.data.numY(i)];
    }

    zlambda = new double[p.data.xSize];
    ftildeArr = new double[p.fSize];
    initCondsZlambdaEtc();
    if (convertValues) {
      transformValues();
    }
  }

  /**
   * Reads the lambda parameters from a file.
   *
   * @param filename File to read from
   */
  public LambdaSolve(String filename) {
    this.readL(filename);
  }

  public LambdaSolve() {
  }

  public void setNonBinary() {
    ASSUME_BINARY = false;
  }

  public void setBinary() {
    ASSUME_BINARY = true;
  }


  /**
   * This is a specialized procedure to change the values
   * of parses for semantic ranking.
   * The highest value is changed to 2/3
   * and values of 1 are changed to 1/(3*numones). 0 is unchanged
   * this is used to rank higher the ordering for the best parse
   * values are in p.data.values
   */
  public void transformValues() {
    for (int x = 0; x < p.data.values.length; x++) {
      double highest = p.data.values[x][0];
      double sumhighest = 0;
      double sumrest = 0;
      for (int y = 0; y < p.data.values[x].length; y++) {
        if (p.data.values[x][y] > highest) {
          highest = p.data.values[x][y];
        }
      }

      for (int y = 0; y < p.data.values[x].length; y++) {
        if (p.data.values[x][y] == highest) {
          sumhighest += highest;
        } else {
          sumrest += p.data.values[x][y];
        }
      }

      if (sumrest == 0) {
        continue;
      } // do not change , makes no difference

      //now change them
      for (int y = 0; y < p.data.values[x].length; y++) {
        if (p.data.values[x][y] == highest) {
          p.data.values[x][y] = .7 * highest / sumhighest;
        } else {
          p.data.values[x][y] = .3 * p.data.values[x][y] / sumrest;
        }
      }
    }
  }


  /**
   * Initializes the model parameters, empirical expectations of the
   * features, and f#(x,y).
   */
  void initCondsZlambdaEtc() {
    // init pcond
    for (int x = 0; x < p.data.xSize; x++) {
      for (int y = 0; y < probConds[x].length; y++) {
        probConds[x][y] = 1.0 / probConds[x].length;
      }
    }

    // init zlambda
    for (int x = 0; x < p.data.xSize; x++) {
      zlambda[x] = probConds[x].length;
    }

    // init ftildeArr
    for (int i = 0; i < p.fSize; i++) {
      ftildeArr[i] = p.functions.get(i).ftilde();
      p.functions.get(i).setSum();

      // if the expectation of a feature is zero make sure we are not
      // trying to find a lambda for it
      // if (ftildeArr[i] == 0) {
      //   lambda_converged[i]=true;
      //   lambda[i]=0;
      // }

      //dumb smoothing that is not sound and doesn't seem to work
      if (smooth) {
        double alfa = .015;
        for (int j = 0; j < p.fSize; j++) {
          ftildeArr[j] = (ftildeArr[j] * p.data.xSize + alfa) / p.data.xSize;
        }
      }

      Feature f = p.functions.get(i);
      //collecting f#(x,y)
      for (int j = 0; j < f.len(); j++) {
        int x = f.getX(j);
        int y = f.getY(j);
        fnumArr[x][y] += f.getVal(j);
      }//j
    }//i
    int constAll = fnumArr[0][0];
    fixedFnumXY = true;
    for (int x = 0; x < p.data.xSize; x++) {
      for (int y = 0; y < fnumArr[x].length; y++) {
        if (fnumArr[x][y] != constAll) {
          fixedFnumXY = false;
          break;
        }
      }
    }//x
    if (VERBOSE) {
      log.info(" pcond, zlamda, ftildeArr " + (fixedFnumXY ? "(fixed sum) " : "") + "initialized ");
    }
  }


  /**
   * Iterate until convergence.  I usually use the other method that
   * does a fixed number of iterations.
   */
  public void improvedIterative() {
    boolean flag;
    int iterations = 0;
    lambda_converged = new boolean[p.fSize];
    int numNConverged = p.fSize;
    do {
      if (VERBOSE) {
        log.info(iterations);
      }
      flag = false;
      iterations++;
      for (int i = 0; i < lambda.length; i++) {
        if (lambda_converged[i]) {
          continue;
        }
        MutableDouble deltaI = new MutableDouble();
        boolean fl = iterate(i, eps, deltaI);
        if (fl) {
          flag = true;
          updateConds(i, deltaI.doubleValue());
          // checkCorrectness();
        } else {
          //lambda_converged[i]=true;
          numNConverged--;
        }
      }
    } while ((flag) && (iterations < 1000));
  }


  /**
   * Does a fixed number of IIS iterations.
   *
   * @param iters Number of iterations to run
   */
  public void improvedIterative(int iters) {
    int iterations = 0;
    lambda_converged = new boolean[p.fSize];
    int numNConverged = p.fSize;
    //double lOld=logLikelihood();
    do {
      iterations++;
      for (int i = 0; i < lambda.length; i++) {
        if (lambda_converged[i]) {
          continue;
        }
        MutableDouble deltaI = new MutableDouble();
        boolean fl = iterate(i, eps, deltaI);
        if (fl) {
          updateConds(i, deltaI.doubleValue());
          // checkCorrectness();
        } else {
          //lambda_converged[i]=true;
          numNConverged--;
        }
      }

      /*
        double lNew=logLikelihood();
        double gain=(lNew-lOld);
        if(gain<0) {
        log.info(" Likelihood decreased by "+ (-gain));
        System.exit(1);
        }
        if(Math.abs(gain)<eps){
        log.info("Converged");
        break;
        }

        if(VERBOSE)
        log.info("Likelihood "+lNew+" "+" gain "+gain);
        lOld=lNew;
      */

      if (iterations % 100 == 0) {
        save_lambdas(iterations + ".lam");
      }
      log.info(iterations);
    } while (iterations < iters);
  }


  /**
   * Iteration for lambda[index].
   * Returns true if this lambda hasn't converged. A lambda is deemed
   * converged if the change found for it is smaller then the parameter eps.
   */
  boolean iterate(int index, double err, MutableDouble ret) {
    double deltaL = 0.0;
    deltaL = newton(deltaL, index, err);
    //log.info("delta is "+deltaL+" feature "+index+" expectation "+ftildeArr[index]);

    if (Math.abs(deltaL + lambda[index]) > 200) {
      if ((deltaL + lambda[index]) > 200) {
        deltaL = 200 - lambda[index];
      } else {
        deltaL = -lambda[index] - 200;
      }

      log.info("set delta to smth " + deltaL);
    }
    lambda[index] = lambda[index] + deltaL;
    if (Double.isNaN(deltaL)) {
      log.info(" NaN " + index + ' ' + deltaL);
    }
    ret.set(deltaL);
    return (Math.abs(deltaL) >= eps);
  }


  /*
   * Finds the root of an equation by Newton's method.
   * This is my implementation. It might be improved
   * if we looked at some official library for numerical methods.
   */
  double newton(double lambda0, int index, double err) {
    double lambdaN = lambda0;
    int i = 0;
    if (fixedFnumXY) {
      double plambda = fExpected(p.functions.get(index));
      return (1 / (double) fnumArr[0][0]) * (Math.log(this.ftildeArr[index]) - Math.log(plambda));
    }
    do {
      i++;
      double lambdaP = lambdaN;
      double gPrimeVal = gprime(lambdaP, index);
      if (Double.isNaN(gPrimeVal)) {
        log.info("gPrime of " + lambdaP + " " + index + " is NaN " + gPrimeVal);
        //lambda_converged[index]=true;
        //   System.exit(1);
      }
      double gVal = g(lambdaP, index);
      if (gPrimeVal == 0.0) {
        return 0.0;
      }
      lambdaN = lambdaP - gVal / gPrimeVal;
      if (Double.isNaN(lambdaN)) {
        log.info("the division of " + gVal + " " + gPrimeVal + " " + index + " is NaN " + lambdaN);
        //lambda_converged[index]=true;
        return 0;
      }
      if (Math.abs(lambdaN - lambdaP) < err) {
        return lambdaN;
      }
      if (i > 100) {
        if (Math.abs(gVal) > 0.01) {
          return 0;
        }
        return lambdaN;
      }
    } while (true);
  }


  /**
   * This method updates the conditional probabilities in the model, resulting from the
   * update of lambda[index] to lambda[index]+deltaL .
   */
  void updateConds(int index, double deltaL) {
    //  for each x that (x,y)=true / exists y
    //  recalculate pcond(y,x) for all y
    for (int i = 0; i < p.functions.get(index).len(); i++) {
      // update for this x
      double s = 0;
      int x = p.functions.get(index).getX(i);
      int y = p.functions.get(index).getY(i);
      double val = p.functions.get(index).getVal(i);
      double zlambdaX = zlambda[x] + pcond(y, x) * zlambda[x] * (Math.exp(deltaL * val) - 1);
      for (int y1 = 0; y1 < probConds[x].length; y1++) {
        probConds[x][y1] = (probConds[x][y1] * zlambda[x]) / zlambdaX;
        s = s + probConds[x][y1];
      }
      s = s - probConds[x][y];
      probConds[x][y] = probConds[x][y] * Math.exp(deltaL * val);
      s = s + probConds[x][y];
      zlambda[x] = zlambdaX;
      if (Math.abs(s - 1) > 0.001) {
        //log.info(x+" index "+i+" deltaL " +deltaL+" tag "+yTag+" zlambda "+zlambda[x]);
      }
    }
  }


  public double pcond(int y, int x) {
    return probConds[x][y];
  }


  protected double fnum(int x, int y) {
    return fnumArr[x][y];
  }

  double g(double lambdaP, int index) {
    double s = 0.0;

    for (int i = 0; i < p.functions.get(index).len(); i++) {
      int y = p.functions.get(index).getY(i);
      int x = p.functions.get(index).getX(i);
      double exponent = Math.exp(lambdaP * fnum(x, y));
      s = s + p.data.ptildeX(x) * pcond(y, x) * p.functions.get(index).getVal(i) * exponent;
    }
    s = s - ftildeArr[index];

    return s;
  }


  double gprime(double lambdaP, int index) {
    double s = 0.0;

    for (int i = 0; i < p.functions.get(index).len(); i++) {
      int y = ((p.functions.get(index))).getY(i);
      int x = p.functions.get(index).getX(i);
      s = s + p.data.ptildeX(x) * pcond(y, x) * p.functions.get(index).getVal(i) * Math.exp(lambdaP * fnum(x, y)) * fnum(x, y);
    }
    return s;
  }


  /**
   * Computes the expected value of a feature for the current model.
   *
   * @param f a feature
   * @return The expectation of f according to p(y|x)
   */
  double fExpected(Feature f) {
    double s = 0.0;
    for (int i = 0; i < f.len(); i++) {
      int x = f.getX(i);
      int y = f.getY(i);
      s += p.data.ptildeX(x) * pcond(y, x) * f.getVal(i);
    }//for

    return s;
  }

  /**
   * Check whether the constraints are satisfied, the probabilities sum to one, etc. Prints out a message
   * if there is something wrong.
   */
  public boolean checkCorrectness() {
    boolean flag = true;
    for (int f = 0; f < lambda.length; f++) {
      if (Math.abs(lambda[f]) > 100) {
        log.info("lambda " + f + " too big " + lambda[f]);
        log.info("empirical " + ftildeArr[f] + " expected " + fExpected(p.functions.get(f)));
      }
    }
    log.info(" x size" + p.data.xSize + " " + " ysize " + p.data.ySize);
    double summAllExp = 0;
    for (int i = 0; i < ftildeArr.length; i++) {
      double exp = Math.abs(ftildeArr[i] - fExpected(p.functions.get(i)));
      summAllExp += ftildeArr[i];
      if (exp > 0.001)
      //if(true)
      {
        flag = false;
        log.info("Constraint not satisfied  " + i + " " + fExpected(p.functions.get(i)) + " " + ftildeArr[i] + " lambda " + lambda[i]);
      }
    }

    log.info(" The sum of all empirical expectations is " + summAllExp);
    for (int x = 0; x < p.data.xSize; x++) {
      double s = 0.0;
      for (int y = 0; y < probConds[x].length; y++) {
        s = s + probConds[x][y];
      }
      if (Math.abs(s - 1) > 0.0001) {
        for (int y = 0; y < probConds[x].length; y++)
            //log.info(y+" : "+ probConds[x][y]);
        {
          log.info("probabilities do not sum to one " + x + " " + (float) s);
        }
      }
    }
    return flag;
  }


  double ZAlfa(double alfa, Feature f, int x) {
    double s = 0.0;
    for (int y = 0; y < probConds[x].length; y++) {
      s = s + pcond(y, x) * Math.exp(alfa * f.getVal(x, y));
    }
    return s;
  }


  double GSF(double alfa, Feature f, int index) {
    double s = 0.0;
    for (int x = 0; x < p.data.xSize; x++) {

      s = s - p.data.ptildeX(x) * Math.log(ZAlfa(alfa, f, x));
    }
    return s + alfa * ftildeArr[index];

  }


  double GSF(double alfa, Feature f) {
    double s = 0.0;
    for (int x = 0; x < p.data.xSize; x++) {

      s = s - p.data.ptildeX(x) * Math.log(ZAlfa(alfa, f, x));
    }
    return s + alfa * f.ftilde();

  }


  double pcondFAlfa(double alfa, int x, int y, Feature f) {
    double s;
    s = (1 / ZAlfa(alfa, f, x)) * pcond(y, x) * Math.exp(alfa * f.getVal(x, y));
    return s;
  }


  double GSFPrime(double alfa, Feature f, int index) {
    double s = 0.0;
    s = s + ftildeArr[index];
    for (int x1 = 0; x1 < f.indexedValues.length; x1++) {
      double s1 = 0.0;
      int x = f.getX(x1);
      int y = f.getY(x1);
      s1 = s1 + pcondFAlfa(alfa, x, y, f) * f.getVal(x1);
      s = s - p.data.ptildeX(x) * s1;
    }
    return s;
  }


  double GSFPrime(double alfa, Feature f) {
    double s = 0.0;
    s = s + f.ftilde();

    for (int x1 = 0; x1 < f.indexedValues.length; x1++) {
      double s1 = 0.0;
      int x = f.getX(x1);
      int y = f.getY(x1);
      s1 = s1 + pcondFAlfa(alfa, x, y, f) * f.getVal(x1);
      s = s - p.data.ptildeX(x) * s1;
    }
    return s;
  }


  double GSFSecond(double alfa, Feature f) {
    double s = 0.0;
    for (int x = 0; x < p.data.xSize; x++) {
      double s1 = 0.0;
      double psff = 0.0;
      for (int y1 = 0; y1 < p.data.ySize; y1++) {
        psff = psff + pcondFAlfa(alfa, x, y1, f) * f.getVal(x, y1);
      }
      for (int y = 0; y < probConds[x].length; y++) {
        s1 = s1 + pcondFAlfa(alfa, x, y, f) * (f.getVal(x, y) - psff) * (f.getVal(x, y) - psff);
      }
      s = s - s1 * p.data.ptildeX(x);
    }
    return s;
  }


  /**
   * Computes the gain from a feature. Used for feature selection.
   */

  public double GainCompute(Feature f, double errorGain) {
    double r = (f.ftilde() > fExpected(f) ? 1.0 : -1.0);
    f.initHashVals();
    int iterations = 0;
    double alfa = 0.0;
    GSF(alfa, f);
    double gsfValNew = 0.0;
    while (iterations < 30) {
      iterations++;
      double alfanext = alfa + r * Math.log(1 - r * GSFPrime(alfa, f) / GSFSecond(alfa, f));
      gsfValNew = GSF(alfanext, f);
      if (Math.abs(alfanext - alfa) < errorGain) {
        return gsfValNew;
      }
      alfa = alfanext;
    }
    return gsfValNew;
  }


  /**
   * Print out p(y|x) for all pairs to the standard output.
   */
  public void print() {
    for (int i = 0; i < p.data.xSize; i++) {
      for (int j = 0; j < probConds[i].length; j++) {
        System.out.println("P(" + j + " | " + i + ") = " + pcond(j, i));
      }
    }
  }


  /**
   * Writes the lambda feature weights to the file.
   * Can be read later with readL.
   * This method opens a new file and closes it after writing it.
   *
   * @param filename The file to write the weights to.
   */
  public void save_lambdas(String filename) {
    try {
      DataOutputStream rf = IOUtils.getDataOutputStream(filename);
      save_lambdas(rf, lambda);
      rf.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /**
   * Writes the lambdas to a stream.
   */
  public static void save_lambdas(DataOutputStream rf, double[] lambdas) {
    try {
      ObjectOutputStream oos = new ObjectOutputStream(rf);
      oos.writeObject(lambdas);
      oos.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }


  /**
   * Read the lambdas from the file.
   * The file contains the number of lambda weights (int) followed by
   * the weights.
   * <i>Historical note:</i> The file does not contain
   * xSize and ySize as for the method read(String).
   *
   * @param filename The file to read from
   */
  public void readL(String filename) {
    try {
      DataInputStream rf = IOUtils.getDataInputStream(filename);
      lambda = read_lambdas(rf);
      rf.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * Read the lambdas from the file.
   *
   * @param modelFilename A filename. It will be read and closed
   * @return An array of lambda values read from the file.
   */
  static double[] read_lambdas(String modelFilename) {
    try {
      DataInputStream rf = IOUtils.getDataInputStream(modelFilename);
      double[] lamb = read_lambdas(rf);
      rf.close();
      return lamb;
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }


  /**
   * Read the lambdas from the stream.
   *
   * @param rf Stream to read from.
   * @return An array of lambda values read from the stream.
   */
  public static double[] read_lambdas(DataInputStream rf) {
    if (VERBOSE) {
      log.info("Entering read_lambdas");
    }
    try {
      ObjectInputStream ois = new ObjectInputStream(rf);
      Object o = ois.readObject();
      if (o instanceof double[]) {
        return (double[]) o;
      }
      throw new RuntimeIOException("Failed to read lambdas from given input stream");
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
  }


  /**
   * This method writes the problem data into a file, which is good for reading
   * with MatLab.  It could also have other applications,
   * like reducing the memory requirements
   */

  void save_problem(String filename) {
    try {
      PrintFile pf = new PrintFile(filename);
      int N = p.data.xSize;
      int M = p.data.ySize;
      int F = p.fSize;
      // byte[] nl = "\n".getBytes();
      // byte[] dotsp = ". ".getBytes();
      // int space = (int) ' ';
      // write the sizes of X, Y, and F( number of features );
      pf.println(N);
      pf.println(M);
      pf.println(F);
      // save the objective vector like 1.c0, ... ,N*M. cN*M-1
      for (int i = 0; i < N * M; i++) {
        pf.print(i + 1);
        pf.print(". ");
        pf.println(p.data.ptildeX(i / M));
      }// for i

      // save the constraints matrix B
      // for each feature , save its row
      for (int i = 0; i < p.fSize; i++) {
        int[] values = p.functions.get(i).indexedValues;
        for (int value : values) {
          pf.print(i + 1);
          pf.print(". ");
          pf.print(value);
          pf.print(" ");
          pf.println(1);
        }// k

      }// i

      // save the constraints vector
      // for each feature, save its empirical expectation

      for (int i = 0; i < p.fSize; i++) {
        pf.print(i + 1);
        pf.print(". ");
        pf.println(ftildeArr[i]);
      }// end
      pf.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  /**
   * @return The loglikelihood of the empirical distribution as predicted by the model p.
   */
  public double logLikelihood() {
    //L=sumx,y log(p(y|x))*#x,y
    double sum = 0.0;
    int sz = p.data.size();
    for (int index = 0; index < sz; index++) {
      int[] example = p.data.get(index);
      sum += Math.log(pcond(example[1], example[0]));
    }// index
    return sum / sz;
  }


  /**
   * Given a numerator and denominator in log form, this calculates
   * the conditional model probabilities.
   *
   * @return Math.exp(first)/Math.exp(second);
   */
  public static double divide(double first, double second) {
    return Math.exp(first - second);  // cpu samples #3,#14: 5.3%
  }


  /**
   * With arguments, this will print out the lambda parameters of a
   * bunch of .lam files (which are assumed to all be the same size).
   * (Without arguments, it does some creaky old self-test.)
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    if (args.length > 0) {
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMaximumFractionDigits(6);
      nf.setMinimumFractionDigits(6);
      LambdaSolve[] lambdas = new LambdaSolve[args.length];
      System.out.print("           ");
      for (int i = 0; i < args.length; i++) {
        lambdas[i] = new LambdaSolve();
        lambdas[i].readL(args[i]);
        System.out.print("  " + args[i]);
      }
      System.out.println();

      int numLambda = lambdas[0].lambda.length;
      for (int j = 0; j < numLambda; j++) {
        System.out.print("lambda[" + j + "] = ");
        for (int i = 0; i < args.length; i++) {
          System.out.print(nf.format(lambdas[i].lambda[j]) + "  ");
        }
        System.out.println();
      }
    } else {
      LambdaSolve prob = new LambdaSolve("trainhuge.txt.holder.prob");
      prob.save_lambdas("trainhuge.txt.holder.prob");
      prob.readL("trainhuge.txt.holder.prob");
    }
  }

  /**
   * Calculate the log-likelihood from scratch, hashing the conditional
   * probabilities in pcond, which we will use later. This is for
   * a different model, in which all features effectively get negative weights
   * this model is easier to use for heauristic search
   * p(ti|s)=exp(sum_j{-(e^lambda_j)*f_j(ti)})
   *
   * @return The negative log likelihood of the data
   */
  public double logLikelihoodNeg() {
    // zero all the variables
    double s = 0;
    for (int i = 0; i < probConds.length; i++) {
      for (int j = 0; j < probConds[i].length; j++) {
        probConds[i][j] = 0;
      }
      zlambda[i] = 0;
    }

    //add up in pcond y|x the unnormalized scores

    for (int fNo = 0, fSize = p.fSize; fNo < fSize; fNo++) {
      // add for all occurences of the function the values to probConds
      Feature f = p.functions.get(fNo);
      double fLambda = -Math.exp(lambda[fNo]);
      double sum = ftildeArr[fNo];

      //if(sum==0){continue;}
      sum *= p.data.getNumber();
      s -= sum * fLambda;

      if (Math.abs(fLambda) > 200) {   // was 50
        log.info("lambda " + fNo + " too big: " + fLambda);
      }

      for (int i = 0, length = f.len(); i < length; i++) {
        int x = f.getX(i);
        int y = f.getY(i);
        if (ASSUME_BINARY) {
          probConds[x][y] += fLambda;
        } else {
          double val = f.getVal(i);
          probConds[x][y] += (val * fLambda);
        }
      } //for

    } //for fNo

    for (int x = 0; x < probConds.length; x++) {
      //again
      zlambda[x] = ArrayMath.logSum(probConds[x]); // cpu samples #4,#15: 4.5%
      //log.info("zlambda "+x+" "+zlambda[x]);
      s += zlambda[x] * p.data.ptildeX(x) * p.data.getNumber();

      for (int y = 0; y < probConds[x].length; y++) {
        probConds[x][y] = divide(probConds[x][y], zlambda[x]); // cpu samples #13: 1.6%
        //log.info("prob "+x+" "+y+" "+probConds[x][y]);
      } //y

    }//x

    if (s < 0) {
      throw new IllegalStateException("neg log lik smaller than 0: " + s);
    }

    return s;
  }

  // -- stuff for CG version below -------

  /**
   * calculate the log likelihood from scratch, hashing the conditional
   * probabilities in pcond which we will use for the derivative later.
   *
   * @return The log likelihood of the data
   */
  public double logLikelihoodScratch() {
    // zero all the variables
    double s = 0;
    for (int i = 0; i < probConds.length; i++) {
      for (int j = 0; j < probConds[i].length; j++) {
        probConds[i][j] = 0;
      }
      zlambda[i] = 0;
    }

    //add up in pcond y|x the unnormalized scores

    Experiments exp = p.data;
    for (int fNo = 0, fSize = p.fSize; fNo < fSize; fNo++) {
      // add for all occurences of the function the values to probConds
      Feature f = p.functions.get(fNo);
      double fLambda = lambda[fNo];
      double sum = ftildeArr[fNo];

      //if(sum==0){continue;}
      sum *= exp.getNumber();
      s -= sum * fLambda;

      if (Math.abs(fLambda) > 200) {   // was 50
        log.info("lambda " + fNo + " too big: " + fLambda);
      }

      for (int i = 0, length = f.len(); i < length; i++) {
        int x = f.getX(i);
        int y = f.getY(i);
        if (ASSUME_BINARY) {
          probConds[x][y] += fLambda;
        } else {
          double val = f.getVal(i);
          probConds[x][y] += (val * fLambda);
        }
      } //for

    } //for fNo

    for (int x = 0; x < probConds.length; x++) {
      //again
      zlambda[x] = ArrayMath.logSum(probConds[x]); // cpu samples #4,#15: 4.5%
      //log.info("zlambda "+x+" "+zlambda[x]);
      s += zlambda[x] * exp.ptildeX(x) * exp.getNumber();

      for (int y = 0; y < probConds[x].length; y++) {
        probConds[x][y] = divide(probConds[x][y], zlambda[x]); // cpu samples #13: 1.6%
        //log.info("prob "+x+" "+y+" "+probConds[x][y]);
      } //y

    }//x

    if (s < 0) {
      throw new IllegalStateException("neg log lik smaller than 0: " + s);
    }

    return s;
  }


  /**
   * assuming we have the lambdas in the array and we need only the
   * derivatives now.
   */
  public double[] getDerivatives() {

    double[] drvs = new double[lambda.length];
    Experiments exp = p.data;

    for (int fNo = 0; fNo < drvs.length; fNo++) {  // cpu samples #2,#10,#12: 27.3%
      Feature f = p.functions.get(fNo);
      double sum = ftildeArr[fNo] * exp.getNumber();
      drvs[fNo] = -sum;
      for (int index = 0, length = f.len(); index < length; index++) {
        int x = f.getX(index);
        int y = f.getY(index);
        if (ASSUME_BINARY) {
          drvs[fNo] += probConds[x][y] * exp.ptildeX(x) * exp.getNumber();
        } else {
          double val = f.getVal(index);
          drvs[fNo] += probConds[x][y] * val * exp.ptildeX(x) * exp.getNumber();
        }
      }//for
      //if(sum==0){drvs[fNo]=0;}
    }
    return drvs;
  }


  /**
   * assuming we have the lambdas in the array and we need only the
   * derivatives now.
   * this is for the case where the model is parameterezied such that all weights are negative
   * see also logLikelihoodNeg
   */
  public double[] getDerivativesNeg() {

    double[] drvs = new double[lambda.length];
    Experiments exp = p.data;
    for (int fNo = 0; fNo < drvs.length; fNo++) {  // cpu samples #2,#10,#12: 27.3%
      Feature f = p.functions.get(fNo);
      double sum = ftildeArr[fNo] * exp.getNumber();
      double lam = -Math.exp(lambda[fNo]);
      drvs[fNo] = -sum * lam;
      for (int index = 0, length = f.len(); index < length; index++) {
        int x = f.getX(index);
        int y = f.getY(index);
        if (ASSUME_BINARY) {
          drvs[fNo] += probConds[x][y] * exp.ptildeX(x) * exp.getNumber() * lam;
        } else {
          double val = f.getVal(index);
          drvs[fNo] += probConds[x][y] * val * exp.ptildeX(x) * exp.getNumber() * lam;
        }
      }//for
      //if(sum==0){drvs[fNo]=0;}
    }
    return drvs;
  }


  /**
   * Each pair x,y has a value in p.data.values[x][y]
   *
   * @return - expected value of corpus -sum_xy (ptilde(x,y)*value(x,y)*pcond(x,y))
   */
  public double expectedValue() {
    // zero all the variables
    double s = 0;

    aux = new double[probConds.length];
    for (int i = 0; i < probConds.length; i++) {
      for (int j = 0; j < probConds[i].length; j++) {
        probConds[i][j] = 0;
      }
      zlambda[i] = 0;
    }

    //add up in pcond y|x the unnormalized scores

    for (int fNo = 0, fSize = p.fSize; fNo < fSize; fNo++) {
      // add for all occurrences of the function the values to probConds
      Feature f = p.functions.get(fNo);
      double fLambda = lambda[fNo];


      if (Math.abs(fLambda) > 200) {   // was 50
        log.info("lambda " + fNo + " too big: " + fLambda);
      }

      for (int i = 0, length = f.len(); i < length; i++) {
        int x = f.getX(i);
        int y = f.getY(i);
        if (ASSUME_BINARY) {
          probConds[x][y] += fLambda;
        } else {
          double val = f.getVal(i);
          probConds[x][y] += (val * fLambda);
        }
      } //for

    } //for fNo

    Experiments exp = p.data;
    for (int x = 0; x < probConds.length; x++) {
      //again
      zlambda[x] = ArrayMath.logSum(probConds[x]); // cpu samples #4,#15: 4.5%
      //log.info("zlambda "+x+" "+zlambda[x]);


      for (int y = 0; y < probConds[x].length; y++) {
        probConds[x][y] = divide(probConds[x][y], zlambda[x]); // cpu samples #13: 1.6%
        //log.info("prob "+x+" "+y+" "+probConds[x][y]);

        s -= exp.values[x][y] * probConds[x][y] * exp.ptildeX(x) * exp.getNumber();
        aux[x] += exp.values[x][y] * probConds[x][y];
      }
    }//x

    return s;
  }


  /**
   * assuming we have the probConds[x][y] , compute the derivatives for the expectedValue function
   *
   * @return The derivatives of the expected
   */
  public double[] getDerivativesExpectedValue() {

    double[] drvs = new double[lambda.length];
    Experiments exp = p.data;
    for (int fNo = 0; fNo < drvs.length; fNo++) {  // cpu samples #2,#10,#12: 27.3%
      Feature f = p.functions.get(fNo);
      //double sum = ftildeArr[fNo] * exp.getNumber();
      //drvs[fNo] = -sum;
      for (int index = 0, length = f.len(); index < length; index++) {
        int x = f.getX(index);
        int y = f.getY(index);

        double val = f.getVal(index);
        double mult = val * probConds[x][y] * exp.ptildeX(x) * exp.getNumber();
        drvs[fNo] -= mult * exp.values[x][y];
        drvs[fNo] += mult * aux[x];

      }//for
      //if(sum==0){drvs[fNo]=0;}
    }
    return drvs;
  }


  /**
   * calculate the loss for Dom ranking
   * using the numbers in p.data.values to determine domination relationships in the graphs
   *<br>
   * if {@code values[x][y] > values[x][y']} then there is an edge {@code (x,y)->(x,y')}
   *
   * @return The loss
   */
  public double lossDomination() {
    // zero all the variables
    double s = 0;
    for (int i = 0; i < probConds.length; i++) {
      for (int j = 0; j < probConds[i].length; j++) {
        probConds[i][j] = 0;
      }
      zlambda[i] = 0;
    }

    //add up in pcond y|x the unnormalized scores

    for (int fNo = 0, fSize = p.fSize; fNo < fSize; fNo++) {
      // add for all occurrences of the function the values to probConds
      Feature f = p.functions.get(fNo);
      double fLambda = lambda[fNo];

      //if(sum==0){continue;}

      if (Math.abs(fLambda) > 200) {   // was 50
        log.info("lambda " + fNo + " too big: " + fLambda);
      }

      for (int i = 0, length = f.len(); i < length; i++) {
        int x = f.getX(i);
        int y = f.getY(i);
        if (ASSUME_BINARY) {
          probConds[x][y] += fLambda;
        } else {
          double val = f.getVal(i);
          probConds[x][y] += (val * fLambda);
        }
      } //for

    } //for fNo

    //will use zlambda[x] for the number of domination graphs for x
    // keeping track of other arrays as well - sum[x][y], and sub[x][y]

    //now two double loops over (x,y) to collect zlambda[x], sum[x][y], and sub[x][y];

    sum = new double[probConds.length][];
    sub = new double[probConds.length][];

    for (int x = 0; x < probConds.length; x++) {
      sum[x] = new double[probConds[x].length];
      sub[x] = new double[probConds[x].length];
      double localloss = 0;

      for (int u = 0; u < sum[x].length; u++) {
        boolean hasgraph = false;

        for (int v = 0; v < sum[x].length; v++) {
          //see if u dominates v
          if (p.data.values[x][u] > p.data.values[x][v]) {
            hasgraph = true;
            sum[x][u] += Math.exp(probConds[x][v] - probConds[x][u]);
          }
        }
        sum[x][u] += 1;

        double weight = 1;
        if (weightRanks) {
          weight = p.data.values[x][u];
        }
        if (hasgraph) {
          zlambda[x] += weight;
        }
        localloss += weight * Math.log(sum[x][u]);
      }

      //another loop to get the sub[x][y]

      for (int u = 0; u < sum[x].length; u++) {

        for (int v = 0; v < sum[x].length; v++) {
          //see if u dominates v
          if (p.data.values[x][u] > p.data.values[x][v]) {

            double weight = 1;
            if (weightRanks) {
              weight = p.data.values[x][u];
            }

            sub[x][v] += weight * Math.exp(probConds[x][v] - probConds[x][u]) / sum[x][u];
          }
        }
      }

      log.info(" for x " + x + " number graphs " + zlambda[x]);

      if (zlambda[x] > 0) {
        localloss /= zlambda[x];
        s += p.data.ptildeX(x) * p.data.getNumber() * localloss;
      }

    }//x

    return s;
  }


  /**
   * Using the arrays calculated when computing the loss, it should not be
   * too hard to get the derivatives.
   *
   * @return The derivative of the loss
   */
  public double[] getDerivativesLossDomination() {

    double[] drvs = new double[lambda.length];

    for (int fNo = 0; fNo < drvs.length; fNo++) {  // cpu samples #2,#10,#12: 27.3%
      Feature f = p.functions.get(fNo);

      for (int index = 0, length = f.len(); index < length; index++) {
        int x = f.getX(index);
        int y = f.getY(index);

        double val = f.getVal(index);
        //add the sub and sum components
        if (zlambda[x] == 0) {
          continue;
        }
        double mult = val * p.data.ptildeX(x) * p.data.getNumber() * (1 / zlambda[x]);
        double weight = 1;
        if (weightRanks) {
          weight = p.data.values[x][y];
        }
        drvs[fNo] += mult * sub[x][y];
        drvs[fNo] -= mult * weight * (sum[x][y] - 1) / sum[x][y];

      }//for
      //if(sum==0){drvs[fNo]=0;}
    }
    return drvs;
  }

}
