package edu.stanford.nlp.optimization;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Timing;

import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import edu.stanford.nlp.util.Pair;

/**
 * Stochastic Gradient Descent Minimizer.
 * Note: If you want a fast SGD minimizer, then you probably want to use
 * StochasticInPlaceMinimizer, not this class!
 *
 * The basic way to use the minimizer is with a null constructor, then
 * the simple minimize method:
 * <br>
 * <p><code>Minimizer smd = new SGDMinimizer();</code>
 * <br><code>DiffFunction df = new SomeDiffFunction(); //Note that it must be a incidence of AbstractStochasticCachingDiffFunction</code>
 * <br><code>double tol = 1e-4;</code>
 * <br><code>double[] initial = getInitialGuess();</code>
 * <br><code>int maxIterations = someSafeNumber;</code>
 * <br><code>double[] minimum = qnm.minimize(df,tol,initial,maxIterations);</code>
 * <br>
 * Constructing with a null constructor will use the default values of
 * <p>
 * <br><code>batchSize = 15;</code>
 * <br><code>initialGain = 0.1;</code>
 * <br>
 *
 * @author <a href="mailto:akleeman@stanford.edu">Alex Kleeman</a>
 * @version 1.0
 * @since 1.0
 */
public abstract class StochasticMinimizer<T extends Function> implements Minimizer<T>, HasEvaluators  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(StochasticMinimizer.class);

  public boolean outputIterationsToFile = false;
  public int outputFrequency = 1000;
  public double gain = 0.1;

  protected double[] x, newX, grad, newGrad,v;
  protected int numBatches;
  protected int k;
  protected int bSize = 15;
  protected boolean quiet = false;
  protected List<double[]> gradList = null;
  protected int memory = 10;
  protected int numPasses = -1;
  protected Random gen = new Random(1);
  protected PrintWriter file = null;
  protected PrintWriter infoFile = null;
  protected long maxTime = Long.MAX_VALUE;

  private int evaluateIters = 0;    // Evaluate every x iterations (0 = no evaluation)
  private Evaluator[] evaluators;  // separate set of evaluators to check how optimization is going

  public void shutUp() {
    this.quiet = true;
  }

  protected static final NumberFormat nf = new DecimalFormat("0.000E0");


  protected abstract String getName();

  protected abstract void takeStep(AbstractStochasticCachingDiffFunction dfunction);

  public void setEvaluators(int iters, Evaluator[] evaluators)
  {
    this.evaluateIters = iters;
    this.evaluators = evaluators;
  }

  /*
    This is the scaling factor for the gains to ensure convergence
  */
  protected static double gainSchedule(int it, double tau){
    return (tau / (tau + it));
  }

  /*
   * This is used to smooth the gradients, providing a more robust calculation which
   * generally leads to a better routine.
   */

  protected static double[] smooth(List<double[]> toSmooth){
    double[] smoothed = new double[toSmooth.get(0).length];

    for(double[] thisArray:toSmooth){
      ArrayMath.pairwiseAddInPlace(smoothed,thisArray);
    }

    ArrayMath.multiplyInPlace(smoothed,1/((double) toSmooth.size() ));
    return smoothed;
  }


  private void initFiles() {
    if (outputIterationsToFile) {

      String fileName = getName() + ".output";
      String infoName = getName() + ".info";

      try {
        file = new PrintWriter(new FileOutputStream(fileName),true);
        infoFile = new PrintWriter(new FileOutputStream(infoName),true);
      }
      catch (IOException e) {
        log.info("Caught IOException outputting data to file: " + e.getMessage());
        System.exit(1);
      }
    }
  }


  public abstract Pair<Integer,Double> tune(Function function,double[] initial, long msPerTest);

  public double tuneDouble(edu.stanford.nlp.optimization.Function function, double[] initial, long msPerTest,PropertySetter<Double> ps,double lower,double upper){
    return this.tuneDouble(function, initial, msPerTest, ps, lower, upper, 1e-3*Math.abs(upper-lower));
  }

  public double tuneDouble(edu.stanford.nlp.optimization.Function function, double[] initial, long msPerTest,PropertySetter<Double> ps,double lower,double upper,double TOL){

    double[] xtest = new double[initial.length];
    this.maxTime = msPerTest;
    // check for stochastic derivatives
    if (!(function instanceof AbstractStochasticCachingDiffFunction)) {
      throw new UnsupportedOperationException();
    }
    AbstractStochasticCachingDiffFunction dfunction = (AbstractStochasticCachingDiffFunction) function;

    List<Pair<Double,Double>> res = new ArrayList<>();
    Pair<Double,Double> best = new Pair<>(lower, Double.POSITIVE_INFINITY); //this is set to lower because the first it will always use the lower first, so it has to be best
    Pair<Double,Double> low = new Pair<>(lower, Double.POSITIVE_INFINITY);
    Pair<Double,Double> high = new Pair<>(upper, Double.POSITIVE_INFINITY);
    Pair<Double,Double> cur = new Pair<>();
    Pair<Double,Double> tmp = new Pair<>();

    List<Double> queue = new ArrayList<>();
    queue.add(lower);
    queue.add(upper);
    //queue.add(0.5* (lower + upper));

    boolean  toContinue = true;
    this.numPasses = 10000;

    do{
      System.arraycopy(initial, 0, xtest, 0, initial.length);
      if(queue.size() != 0){
        cur.first = queue.remove(0);
      }else{
        cur.first = 0.5*( low.first() + high.first() );
      }

      ps.set(cur.first() );

      log.info("");
      log.info("About to test with batch size:  " + bSize +
              "  gain: "  + gain + " and  " +
              ps.toString() + " set to  " + cur.first());

      xtest = this.minimize(function, 1e-100, xtest);

      if(Double.isNaN( xtest[0] ) ){
        cur.second = Double.POSITIVE_INFINITY;
      } else {
        cur.second = dfunction.valueAt(xtest);
      }

      if( cur.second() < best.second() ){

        copyPair(best,tmp);
        copyPair(cur,best);

        if(tmp.first() > best.first()){
          copyPair(tmp,high); // The old best is now the upper bound
        }else{
          copyPair(tmp,low);  // The old best is now the lower bound
        }
        queue.add( 0.5 * ( cur.first() + high.first()  ) ); // check in the right interval next
      } else if ( cur.first() < best.first() ){
        copyPair(cur,low);
      } else if( cur.first() > best.first()){
        copyPair(cur,high);
      }

      if( Math.abs( low.first() - high.first() ) < TOL   ) {
        toContinue = false;
      }

      res.add(new Pair<>(cur.first(), cur.second()));

      log.info("");
      log.info("Final value is: " + nf.format(cur.second()));
      log.info("Optimal so far using " + ps.toString() + " is: "+ best.first() );
    } while(toContinue);


    //output the results to screen.
    log.info("-------------");
    log.info(" RESULTS          ");
    log.info(ps.getClass().toString());
    log.info("-------------");
    log.info("  val    ,    function after " + msPerTest + " ms");
    for (Pair<Double, Double> re : res) {
      log.info(re.first() + "    ,    " + re.second());
    }
    log.info("");
    log.info("");

    return best.first();

  }

  private static void copyPair(Pair<Double,Double> from, Pair<Double,Double> to) {
    to.first = from.first();
    to.second = from.second();
  }


  private class setGain implements PropertySetter<Double>{
    StochasticMinimizer<T> parent = null;

    public setGain(StochasticMinimizer<T> min) {
      parent = min;
    }

    public void set(Double in) {
      gain = in ;
    }
  }


  public double tuneGain(Function function, double[] initial, long msPerTest, double lower, double upper){

    return tuneDouble(function,initial,msPerTest,new setGain(this),lower,upper);
  }


  // [cdm 2012: The version that used to be here was clearly buggy;
  // I changed it a little, but didn't test it. It's now more correct, but
  // I think it is still conceptually faulty, since it will keep growing the
  // batch size so long as any minute improvement in the function value is
  // obtained, whereas the whole point of using a small batch is to get speed
  // at the cost of small losses.]
  public int tuneBatch(Function function, double[] initial, long msPerTest, int bStart) {

    double[] xTest = new double[initial.length];
    int bOpt = 0;
    double min = Double.POSITIVE_INFINITY;
    this.maxTime = msPerTest;
    double prev = Double.POSITIVE_INFINITY;

    // check for stochastic derivatives
    if (!(function instanceof AbstractStochasticCachingDiffFunction)) {
      throw new UnsupportedOperationException();
    }
    AbstractStochasticCachingDiffFunction dFunction = (AbstractStochasticCachingDiffFunction) function;

    int b = bStart;
    boolean  toContinue = true;

    do {
      System.arraycopy(initial, 0, xTest, 0, initial.length);
      log.info("");
      log.info("Testing with batch size:  " + b );
      bSize = b;
      shutUp();
      this.minimize(function, 1e-5, xTest);
      double result = dFunction.valueAt(xTest);

      if (result < min) {
        min = result;
        bOpt = bSize;
        b *= 2;
        prev = result;
      } else if(result < prev) {
        b *= 2;
        prev = result;
      } else if (result > prev) {
        toContinue = false;
      }

      log.info("");
      log.info("Final value is: " + nf.format(result));
      log.info("Optimal so far is:  batch size: " + bOpt );
    } while (toContinue);

    return bOpt;
  }

  public Pair<Integer,Double> tune(Function function, double[] initial, long msPerTest,List<Integer> batchSizes, List<Double> gains){

    double[] xtest = new double[initial.length];
    int bOpt = 0;
    double gOpt = 0.0;
    double min = Double.POSITIVE_INFINITY;

    double[][] results = new double[batchSizes.size()][gains.size()];

    this.maxTime = msPerTest;

    for( int b=0;b<batchSizes.size();b++){
      for(int g=0;g<gains.size();g++){
        System.arraycopy(initial, 0, xtest, 0, initial.length);
        bSize = batchSizes.get(b);
        gain = gains.get(g);
        log.info("");
        log.info("Testing with batch size: " + bSize + "    gain:  " + nf.format(gain) );
        this.quiet = true;
        this.minimize(function, 1e-100, xtest);
        results[b][g] = function.valueAt(xtest);

        if( results[b][g] < min ){
          min = results[b][g];
          bOpt = bSize;
          gOpt = gain;
        }

        log.info("");
        log.info("Final value is: " + nf.format(results[b][g]));
        log.info("Optimal so far is:  batch size: " + bOpt + "   gain:  " + nf.format(gOpt) );

      }
    }

    return new Pair<>(bOpt, gOpt);
  }


  //This can be filled if an extending class needs to initialize things.
  protected void init(AbstractStochasticCachingDiffFunction func){
  }


  private void doEvaluation(double[] x) {
    // Evaluate solution
    if (evaluators == null) return;
    for (Evaluator eval:evaluators) {
      sayln("  Evaluating: " + eval.toString());
      eval.evaluate(x);
    }
  }

  public double[] minimize(Function function, double functionTolerance, double[] initial) {
    return minimize(function, functionTolerance, initial, -1);
  }


  public double[] minimize(Function function, double functionTolerance, double[] initial, int maxIterations) {

    // check for stochastic derivatives
    if (!(function instanceof AbstractStochasticCachingDiffFunction)) {
      throw new UnsupportedOperationException();
    }
    AbstractStochasticCachingDiffFunction dfunction = (AbstractStochasticCachingDiffFunction) function;

    dfunction.method = StochasticCalculateMethods.GradientOnly;

    /* ---
      StochasticDiffFunctionTester sdft = new StochasticDiffFunctionTester(dfunction);
      ArrayMath.add(initial, gen.nextDouble() ); // to make sure that priors are working.
      sdft.testSumOfBatches(initial, 1e-4);
      System.exit(1);
    --- */

    x = initial;
    grad = new double[x.length];
    newX = new double[x.length];
    gradList = new ArrayList<>();
    numBatches =  dfunction.dataDimension()/ bSize;
    outputFrequency = (int) Math.ceil( ((double) numBatches) /( (double) outputFrequency) )  ;

    init(dfunction);
    initFiles();

    boolean have_max = (maxIterations > 0 || numPasses > 0);

    if (!have_max){
      throw new UnsupportedOperationException("No maximum number of iterations has been specified.");
    }else{
      maxIterations = Math.max(maxIterations, numPasses)*numBatches;
    }

    sayln("       Batchsize of: " + bSize);
    sayln("       Data dimension of: " + dfunction.dataDimension() );
    sayln("       Batches per pass through data:  " + numBatches );
    sayln("       Max iterations is = " + maxIterations);

    if (outputIterationsToFile) {
      infoFile.println(function.domainDimension() + "; DomainDimension " );
      infoFile.println(bSize + "; batchSize ");
      infoFile.println(maxIterations + "; maxIterations");
      infoFile.println(numBatches + "; numBatches ");
      infoFile.println(outputFrequency  + "; outputFrequency");
    }

    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //            Loop
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    Timing total = new Timing();
    Timing current = new Timing();
    total.start();
    current.start();
    for (k = 0; k<maxIterations ; k++)  {
      try{
        boolean doEval = (k > 0 && evaluateIters > 0 && k % evaluateIters == 0);
        if (doEval) {
          doEvaluation(x);
        }
        int pass = k/numBatches;
        int batch = k%numBatches;
        say("Iter: " + k + " pass " + pass + " batch " + batch);

        // restrict number of saved gradients
        //  (recycle memory of first gradient in list for new gradient)
        if(k > 0 && gradList.size() >= memory){
          newGrad = gradList.remove(0);
        }else{
          newGrad = new double[grad.length];
        }

        dfunction.hasNewVals = true;
        System.arraycopy(dfunction.derivativeAt(x,v,bSize),0,newGrad,0,newGrad.length);
        ArrayMath.assertFinite(newGrad,"newGrad");
        gradList.add(newGrad);
        grad = smooth(gradList);

        //Get the next X
        takeStep(dfunction);

        ArrayMath.assertFinite(newX,"newX");

        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // THIS IS FOR DEBUG ONLY
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        if(outputIterationsToFile && (k%outputFrequency == 0) && k!=0 ) {
          double curVal = dfunction.valueAt(x);
          say(" TrueValue{ " + curVal + " } ");
          file.println(k + " , " + curVal + " , " + total.report() );
        }
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // END OF DEBUG STUFF
        //!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

        if (k >= maxIterations) {
          sayln("Stochastic Optimization complete.  Stopped after max iterations");
          x = newX;
          break;
        }

        if (total.report() >= maxTime){
          sayln("Stochastic Optimization complete.  Stopped after max time");
          x = newX;
          break;
        }

        System.arraycopy(newX, 0, x, 0, x.length);

        say("[" + ( total.report() )/1000.0 + " s " );
        say("{" + (current.restart()/1000.0) + " s}] ");
        say(" "+dfunction.lastValue());

        if (quiet) {
          log.info(".");
        }else{
          sayln("");
        }

      }catch(ArrayMath.InvalidElementException e){
        log.info(e.toString());
        for(int i=0;i<x.length;i++){ x[i]=Double.NaN; }
        break;
      }

    }
    if (evaluateIters > 0) {
      // do final evaluation
      doEvaluation(x);
    }

    if(outputIterationsToFile){
      infoFile.println(k + "; Iterations");
      infoFile.println(( total.report() )/1000.0 + "; Completion Time");
      infoFile.println(dfunction.valueAt(x) + "; Finalvalue");

      infoFile.close();
      file.close();
      log.info("Output Files Closed");
      //System.exit(1);
    }

    say("Completed in: " + ( total.report() )/1000.0 + " s");

    return x;
  }


  public interface PropertySetter <T1> {
    void set(T1 in);
  }

  protected void sayln(String s) {
    if (!quiet) {
      log.info(s);
    }
  }

  protected void say(String s) {
    if (!quiet) {
      log.info(s);
    }
  }

}
