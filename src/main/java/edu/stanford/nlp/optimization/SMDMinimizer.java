package edu.stanford.nlp.optimization; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Pair;

/**
 * <br>
 * Stochastic Meta Descent Minimizer based on
 *
 * <br>
 * Accelerated training of conditional random fields with stochastic gradient methods
 * S. V. N. Vishwanathan, Nicol N. Schraudolph, Mark W. Schmidt, Kevin P. Murphy
 * June 2006 	 	Proceedings of the 23rd international conference on Machine learning ICML '06
 * Publisher: ACM Press
 * <br>
 * The basic way to use the minimizer is with a null constructor, then
 * the simple minimize method:
 * <br>
 * <p><code>Minimizer smd = new SMDMinimizer();</code>
 * <br><code>DiffFunction df = new SomeDiffFunction();</code>
 * <br><code>double tol = 1e-4;</code>
 * <br><code>double[] initial = getInitialGuess();</code>
 * <br><code>int maxIterations = someSafeNumber;</code>
 * <br><code>double[] minimum = qnm.minimize(df,tol,initial,maxIterations);</code>
 * <br>
 * Constructing with a null constructor will use the default values of
 * <p>
 * <br><code>batchSize = 15;</code>
 * <br><code>initialGain = 0.1;</code>
 * <br><code>useAlgorithmicDifferentiation = true;</code>
 * <br>
 * <br>
 *
 * @author <a href="mailto:akleeman@stanford.edu">Alex Kleeman</a>
 * @version 1.0
 * @since 1.0
 */
public class SMDMinimizer<T extends Function> extends StochasticMinimizer<T>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SMDMinimizer.class);



  public double mu = 0.01;
  public double lam = 1.0;
  public double cPosDef = 0.00;
  public double meta;
  //DEBUG ONLY
  public boolean printMinMax = false;
  private double[] Hv,gains;
  StochasticCalculateMethods method; // = null;

  @Override
  public void shutUp() {
    this.quiet = true;
  }

  public void setBatchSize(int batchSize) {
    bSize = batchSize;
  }


  public SMDMinimizer() {
  }

  public SMDMinimizer(double initialSMDGain, int batchSize, StochasticCalculateMethods method, int passes) {
    this(initialSMDGain, batchSize, method, passes, false);
  }

  public SMDMinimizer(double initGain, int batchSize,StochasticCalculateMethods method, int passes, boolean outputToFile){
    bSize = batchSize;
    gain = initGain;
    this.method = method;
    this.numPasses = passes;
    this.outputIterationsToFile = outputToFile;
  }


  @Override
  public double[] minimize(Function function, double functionTolerance, double[] initial) {
    return minimize(function, functionTolerance, initial, -1);
  }


  @Override
  protected void init(AbstractStochasticCachingDiffFunction func){
    func.method = this.method;
    gains = new double[x.length];
    v = new double[x.length];
    Hv = new double[x.length];
    for(int i = 0; i<v.length;i++){
      gains[i] = gain;
    }
  }


  private class setMu implements PropertySetter<Double>{
    SMDMinimizer<T> parent; // = null;

    public setMu(SMDMinimizer<T> smd){parent = smd;}

    @Override
    public void set(Double in){
      parent.mu = in ;
    }
  }

  private class setLam implements PropertySetter<Double>{
    SMDMinimizer<T> parent; // = null;

    public setLam(SMDMinimizer<T> smd){parent = smd;}

    @Override
    public void set(Double in){
      parent.lam = in ;
    }
  }



  @Override
  public Pair<Integer,Double> tune( edu.stanford.nlp.optimization.Function function,double[] initial, long msPerTest){

    this.quiet = true;
    this.lam = 0.9;
    this.mu = tuneDouble(function,initial,msPerTest,new setMu(this),1e-8,1e-2);
    this.lam = tuneDouble(function,initial,msPerTest,new setLam(this),0.1,1.0);
    gain = tuneGain(function, initial, msPerTest, 1e-8,1.0);
    bSize = tuneBatch(function,initial,msPerTest,1);

    log.info("Results:  gain: " + nf.format(gain) + "  batch " + bSize  + "   mu" + nf.format(this.mu) + "  lam" + nf.format(this.lam));

    return new Pair<>(bSize, gain);
  }


  @Override
  protected void takeStep(AbstractStochasticCachingDiffFunction dfunction){
    dfunction.returnPreviousValues = true;

    System.arraycopy(dfunction.HdotVAt(x,v,grad,bSize), 0, Hv, 0, Hv.length);

    //Update the weights
    for(int i = 0; i < x.length; i++){
      meta = 1-mu*grad[i]*v[i];
      if(0.5 > meta){
        gains[i] = gains[i]*0.5;
      }else{
        gains[i] = gains[i]*meta;
      }
      //Update gain history
      v[i] = lam*(1+cPosDef*gains[i])*v[i] - gains[i]*(grad[i] + lam*Hv[i]);
      //Get the next X
      newX[i] = x[i] - gains[i]*grad[i];
    }

    if(printMinMax){
      say("vMin = " + ArrayMath.min(v) + "  ");
      say("vMax = " + ArrayMath.max(v) + "  ");
      say("gainMin = " + ArrayMath.min(gains) + "  ");
      say("gainMax = " + ArrayMath.max(gains) + "  ");
    }

  }


  @Override
  protected String getName(){
    int m = (int) (mu*1000);
    int l = (int) (lam * 1000);
    int g = (int) (gain*10000);
    return "SMD" + bSize +"_mu" + m + "_lam" + l + "_g" + g ;
  }


  public static void main(String[] args) {
    // optimizes test function using doubles and floats
    // test function is (0.5 sum(x_i^2 * var_i)) ^ PI
    // where var is a vector of random nonnegative numbers
    // dimensionality is variable.
    final int dim = 500000;
    final double maxVar = 5;
    final double[] var = new double[dim];
    double[] init = new double[dim];

    for (int i = 0; i < dim; i++) {
      init[i] = ((i + 1) / (double) dim - 0.5);//init[i] = (Math.random() - 0.5);
      var[i] = maxVar * (i + 1) / dim;
    }

    final DiffFunction f = new DiffFunction() {

      @Override
      public double[] derivativeAt(double[] x) {
        double val = Math.PI * valuePow(x, Math.PI - 1);
        final double[] grads = new double[dim];
        for (int i = 0; i < dim; i++) {
          grads[i] = x[i] * var[i] * val;
        }
        return grads;
      }

      @Override
      public double valueAt(double[] x) {
        return 1.0 + valuePow(x, Math.PI);
      }

      private double valuePow(double[] x, double pow) {
        double val = 0.0;
        for (int i = 0; i < dim; i++) {
          val += x[i] * x[i] * var[i];
        }
        return Math.pow(val * 0.5, pow);
      }

      @Override
      public int domainDimension() {
        return dim;
      }
    };

    SMDMinimizer<DiffFunction> min = new SMDMinimizer<>();

    min.minimize(f, 1.0E-4, init);
  }

}


