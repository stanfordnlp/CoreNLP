package edu.stanford.nlp.optimization; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Pair;

/**
 * Online Limited-Memory Quasi-Newton BFGS implementation based on the algorithms in
 * <br>
 * Nocedal, Jorge, and Stephen J. Wright.  2000.  Numerical Optimization.  Springer.  pp. 224--
 * <br>
 * and modified to the online version presented in
 * <br>
 * A Stocahstic Quasi-Newton Method for Online Convex Optimization
 * Schraudolph, Yu, Gunter (2007)
 * <br>
 * As of now, it requires a
 * Stochastic differentiable function (AbstractStochasticCachingDiffFunction) as input.
 * <br>
 * The basic way to use the minimizer is with a null constructor, then
 * the simple minimize method:
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * THIS IS NOT UPDATE FOR THE STOCHASTIC VERSION YET.
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * <br>
 * <p><code>Minimizer qnm = new QNMinimizer();</code>
 * <br><code>DiffFunction df = new SomeDiffFunction();</code>
 * <br><code>double tol = 1e-4;</code>
 * <br><code>double[] initial = getInitialGuess();</code>
 * <br><code>double[] minimum = qnm.minimize(df,tol,initial);</code>
 * <br>
 * <br>
 * If you do not choose a value of M, it will use the max amount of memory
 * available, up to M of 20.  This will slow things down a bit at first due
 * to forced garbage collection, but is probably faster overall b/c you are
 * guaranteed the largest possible M.
 *
 * The Stochastic version was written by Alex Kleeman, but about 95% of the code
 * was taken directly from the previous QNMinimizer written mostly by Jenny.
 *
 * @author <a href="mailto:jrfinkel@stanford.edu">Jenny Finkel</a>
 * @author Galen Andrew
 * @author <a href="mailto:akleeman@stanford.edu">Alex Kleeman</a>
 * @version 1.0
 * @since 1.0
 */
public class SQNMinimizer<T extends Function> extends StochasticMinimizer<T>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SQNMinimizer.class);

  private int M = 0;
  private double lambda = 1.0;

  private double cPosDef = 1;
  private double epsilon = 1e-10;

  private List<double[]> sList = new ArrayList<>();
  private List<double[]> yList = new ArrayList<>();
  private List<Double> roList = new ArrayList<>();

  double[] dir, s,y;
  double ro;

  public void setM(int m) {
    M = m;
  }

  public SQNMinimizer(int m) {
    M = m;
  }

  public SQNMinimizer() {
  }

  public SQNMinimizer(int mem,double initialGain, int batchSize,boolean output) {
    gain = initialGain;
    bSize = batchSize;
    this.M = mem;
    this.outputIterationsToFile = output;
  }


  @Override
  public String getName(){
    int g = (int) (gain*1000.0);
    return "SQN" + bSize + "_g" + g ;
  }

  // computes d = a + b * c
  private static double[] plusAndConstMult(double[] a, double[] b, double c, double[] d) {
    for (int i = 0; i < a.length; i++) {
      d[i] = a[i] + c * b[i];
    }
    return d;
  }

  @Override
  public Pair<Integer,Double> tune( edu.stanford.nlp.optimization.Function function,double[] initial, long msPerTest){
    log.info("No tuning set yet");
    return new Pair<>(bSize, gain);
  }

  private void computeDir(double[] dir, double[] fg) throws SQNMinimizer.SurpriseConvergence {
    System.arraycopy(fg, 0, dir, 0, fg.length);

    int mmm = sList.size();
    double[] as = new double[mmm];
    double[] factors = new double[dir.length];

    for (int i = mmm - 1; i >= 0; i--) {
      as[i] = roList.get(i) * ArrayMath.innerProduct(sList.get(i), dir);
      plusAndConstMult(dir, yList.get(i), -as[i], dir);
    }

    // multiply by hessian approximation
    if (mmm != 0) {
      double[] y = yList.get(mmm - 1);
      double yDotY = ArrayMath.innerProduct(y, y);
      if (yDotY == 0) {
        throw new SQNMinimizer.SurpriseConvergence("Y is 0!!");
      }
      double gamma = ArrayMath.innerProduct(sList.get(mmm - 1), y) / yDotY;
      ArrayMath.multiplyInPlace(dir, gamma);
    }else if(mmm == 0){
      //This is a safety feature preventing too large of an initial step (see Yu Schraudolph Gunter)
      ArrayMath.multiplyInPlace(dir,epsilon);
    }

    for (int i = 0; i < mmm; i++) {
      double b = roList.get(i) * ArrayMath.innerProduct(yList.get(i), dir);
      plusAndConstMult(dir, sList.get(i), cPosDef*as[i] - b, dir);
      plusAndConstMult(ArrayMath.pairwiseMultiply(yList.get(i),sList.get(i)),factors,1,factors);
    }

    ArrayMath.multiplyInPlace(dir, -1);
  }


  @Override
  protected void init(AbstractStochasticCachingDiffFunction func){

    sList = new ArrayList<>();
    yList = new ArrayList<>();
   dir = new double[func.domainDimension()];
  }


  @Override
  protected void takeStep(AbstractStochasticCachingDiffFunction dfunction){

    try {
      computeDir(dir, newGrad);
    } catch (SQNMinimizer.SurpriseConvergence s) {
      clearStuff();
    }

    double thisGain = gain*gainSchedule(k,5*numBatches);
    for(int i = 0; i < x.length; i++){
      newX[i] = x[i] + thisGain*dir[i];
    }

    //Get a new pair...
    say(" A ");
    if (M > 0 && sList.size() == M || sList.size() == M) {
      s = sList.remove(0);
      y = yList.remove(0);
    } else {
      s = new double[x.length];
      y = new double[x.length];
    }


    dfunction.recalculatePrevBatch = true;
    System.arraycopy(dfunction.derivativeAt(newX,bSize),0,y,0,grad.length);


    // compute s_k, y_k
    ro = 0;
    for(int i=0;i<x.length;i++){
      s[i] = newX[i] - x[i];
      y[i] = y[i] - newGrad[i] + lambda*s[i];
      ro += s[i]*y[i];
    }

    ro = 1.0 / ro;
    sList.add(s);
    yList.add(y);
    roList.add(ro);

  }







  private void clearStuff() {
    sList = null;
    yList = null;
    roList = null;
  }

  private static class SurpriseConvergence extends Throwable {
    /**
     *
     */
    private static final long serialVersionUID = -4377976289620760327L;

    public SurpriseConvergence(String s) {
      super(s);
    }
  }




}
