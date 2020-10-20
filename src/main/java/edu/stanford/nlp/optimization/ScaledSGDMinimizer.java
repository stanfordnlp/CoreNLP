package edu.stanford.nlp.optimization;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Stochastic Gradient Descent To Quasi Newton Minimizer.
 *
 *  An experimental minimizer which takes a stochastic function (one implementing AbstractStochasticCachingDiffFunction)
 * and executes SGD for the first couple passes,  During the final iterations a series of approximate hessian vector
 * products are built up...  These are then passed to the QNMinimizer so that it can start right up without the typical
 * delay.
 *
 * @author <a href="mailto:akleeman@stanford.edu">Alex Kleeman</a>
 * @version 1.0
 * @since 1.0
 */
public class ScaledSGDMinimizer<Q extends AbstractStochasticCachingDiffFunction> extends StochasticMinimizer<Q>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ScaledSGDMinimizer.class);


  private static int method = 1;  // 0=MinErr  1=Bradley
  public List<double[]> yList = null;
  public List<double[]> sList = null;
  public double[] diag;

  private double fixedGain = 0.99;
  private static int pairMem = 20;
  private double aMax = 1e6;


  public double tuneFixedGain(edu.stanford.nlp.optimization.Function function, double[] initial, long msPerTest,double fixedStart){

    double[] xtest = new double[initial.length];
    double fOpt = 0.0;
    double factor = 1.2;
    double min = Double.POSITIVE_INFINITY;
    this.maxTime = msPerTest;
    double prev = Double.POSITIVE_INFINITY;
    // check for stochastic derivatives
    if (!(function instanceof AbstractStochasticCachingDiffFunction)) {
      throw new UnsupportedOperationException();
    }
    AbstractStochasticCachingDiffFunction dfunction = (AbstractStochasticCachingDiffFunction) function;

    int it = 1;
    boolean  toContinue = true;
    double f = fixedStart;

    do{
      System.arraycopy(initial, 0, xtest, 0, initial.length);
      log.info("");
      this.fixedGain = f;
      log.info("Testing with batchsize: " + bSize + "    gain:  " + gain + "  fixedGain:  " + nf.format(fixedGain) );
      this.numPasses = 10000;
      this.minimize(function, 1e-100, xtest);
      double result = dfunction.valueAt(xtest);

      if(it == 1){
        f = f/factor;
      }

      if( result < min ){
        min = result;
        fOpt = this.fixedGain;
        f = f/factor;
        prev = result;
      }else if(result < prev){
        f =f/factor;
        prev = result;
      }else if(result > prev){
        toContinue = false;
      }

      it += 1;
      log.info("");
      log.info("Final value is: " + nf.format(result));
      log.info("Optimal so far is:  fixedgain: " + fOpt);
    } while(toContinue);


    return fOpt;

  }


  private class setFixedGain implements PropertySetter<Double>{
    ScaledSGDMinimizer parent = null;

    public setFixedGain(ScaledSGDMinimizer min){parent = min;}

    public void set(Double in){
      parent.fixedGain = in ;
    }
  }


  @Override
  public Pair<Integer,Double> tune( edu.stanford.nlp.optimization.Function function,double[] initial, long msPerTest){

    this.quiet = true;

    for(int i =0;i<2; i++){
      this.fixedGain = tuneDouble(function,initial,msPerTest,new setFixedGain(this),0.1,1.0);
      gain = tuneGain(function,initial,msPerTest,1e-7,1.0);
      bSize = tuneBatch(function,initial,msPerTest,1);
      log.info("Results:  fixedGain: " + nf.format(this.fixedGain) + "  gain: " + nf.format(gain) + "  batch " + bSize );
    }

    return new Pair<>(bSize, gain);
  }

  @Override
  public void shutUp() {
    this.quiet = true;
  }

  public void setBatchSize(int batchSize) {
    bSize = batchSize;
  }


  public ScaledSGDMinimizer(double SGDGain, int batchSize, int sgdPasses){
    this(SGDGain,batchSize,sgdPasses, 1,false);
  }

  public ScaledSGDMinimizer(double SGDGain, int batchSize, int sgdPasses, int method){
    this(SGDGain,batchSize,sgdPasses, method,false);
  }

  public ScaledSGDMinimizer(double SGDGain, int batchSize, int sgdPasses, int method, boolean outputToFile) {
    bSize = batchSize;
    gain = SGDGain;
    this.numPasses = sgdPasses;
    ScaledSGDMinimizer.method = method;
    this.outputIterationsToFile = outputToFile;
  }

  public ScaledSGDMinimizer(double SGDGain, int batchSize){
    this(SGDGain,batchSize,50);
  }

  public void setMaxTime(Long max){
    maxTime = max;
  }



  @Override
  public String getName(){
    int g = (int) (gain*1000.0);
    int f =  (int) (fixedGain *1000.0);
    return "ScaledSGD" + bSize + "_g" + g + "_f" + f ;
  }

  @Override
  protected void takeStep(AbstractStochasticCachingDiffFunction dfunction){
    for(int i = 0; i < x.length; i++){
      double thisGain = fixedGain*gainSchedule(k,5*numBatches)/(diag[i]);
      newX[i] = x[i] - thisGain*grad[i];
    }

    //Get a new pair...
    say(" A ");
    double[] s;
    double[] y;
    if (pairMem > 0 && sList.size() == pairMem || sList.size() == pairMem) {
      s = sList.remove(0);
      y = yList.remove(0);
    } else {
      s = new double[x.length];
      y = new double[x.length];
    }

    s = ArrayMath.pairwiseSubtract(newX, x);
    dfunction.recalculatePrevBatch = true;
    System.arraycopy(dfunction.derivativeAt(newX,bSize),0, y,0,grad.length);

    ArrayMath.pairwiseSubtractInPlace(y,newGrad);  // newY = newY-newGrad
    double[] comp = new double[x.length];

    sList.add(s);
    yList.add(y);
    updateDiag(diag, s, y);
  }



  @Override
  protected void init(AbstractStochasticCachingDiffFunction func){
    diag = new double[x.length];
    memory = 1;
    for(int i=0;i<x.length;i++){diag[i]=fixedGain/gain;}
    sList = new ArrayList<>();
    yList = new ArrayList<>();
  }

  private void updateDiag(double[] diag,double[] s,double[] y){

    if(method == 0){
      updateDiagMinErr(diag,s,y);
    }else if(method == 1){
      updateDiagBFGS(diag,s,y);
    }

  }

  public void updateDiagBFGS(double[] diag,double[] s,double[] y){
    double sDs = 0.0;
    double sy = 0.0;
    for(int i=0;i<s.length;i++){
      sDs += s[i]*diag[i]*s[i];
      sy += s[i]*y[i];
    }
    say("B");
    double[] newDiag = new double[s.length];
    boolean updateDiag = true;
    for(int i=0;i<s.length;i++){
      newDiag[i] = (1-diag[i]*s[i]*s[i]/sDs)*diag[i] + y[i]*y[i]/sy;
      if(newDiag[i] < 0){updateDiag = false;break;}
    }

    if(updateDiag){
      System.arraycopy(newDiag, 0, diag, 0, s.length);
    }else{
      say("!");
    }
  }

  private void updateDiagMinErr(double[] diag,double[] s,double[] y){

    double low = 0.0;
    double high = 0.0;

    for(int i=0;i<s.length;i++){
      double tmp = s[i] * (y[i] - diag[i]);
      high += tmp*tmp;
    }
    say("M");
    double alpha = Math.sqrt((ArrayMath.norm(y)/ArrayMath.norm(s))) *Math.sqrt(( 50.0/ (50.0 + k) ));
    alpha = alpha*Math.sqrt(ArrayMath.average(diag));
    say(" alpha " + nf.format(alpha));
    high = Math.sqrt(high)/(2*alpha);

    DoubleUnaryOperator func = new lagrange(s,y,diag,alpha);

    double lamStar;
    if( func.applyAsDouble(low) > 0 ){
      lamStar = getRoot(func,low,high);
    } else{
      lamStar = 0.0;
      say(" * ");
    }

    for(int i=0;i<s.length;i++){
      diag[i] = ( Math.abs(y[i]*s[i]) + 2*lamStar*diag[i])/(s[i]*s[i] + 1e-8 + 2*lamStar);
      //diag[i] = (y[i]*s[i] + 2*lamStar*diag[i])/(s[i]*s[i] + 2*lamStar);
      if (diag[i] <= 1.0/aMax) {
        diag[i] = 1.0/gain;
      }
    }

  }




  private double getRoot(DoubleUnaryOperator func, double lower, double upper){
    double mid = 0.5*(lower + upper);
    double TOL = 1e-8;
    double skew = 0.4;
    int count = 0;

    if(func.applyAsDouble(upper) > 0 || func.applyAsDouble(lower) < 0){
      say("LOWER AND UPPER SUPPLIED TO GET ROOT DO NOT BOUND THE ROOT.");
    }

    double fval = func.applyAsDouble(mid);
    while( Math.abs(fval) > TOL ){
      count += 1;
      if( fval > 0 ){
        lower = mid;
      } else if( fval < 0){
        upper = mid;
      }

      mid = skew*lower + (1-skew)*upper;
      fval = func.applyAsDouble(mid);
      if (count > 100){
        break;
      }
    }
    say( "   " +  nf.format(mid) + "  f" + nf.format(fval) );
    return mid;
  }


  static class lagrange implements DoubleUnaryOperator  {

    private final double[] s;
    private final double[] y;
    private final double[] d;
    private final double a;

    public lagrange(double[] s, double[] y, double[] d, double a){
      this.s = s;
      this.y = y;
      this.d = d;
      this.a = a;
    }

    @Override
    public double applyAsDouble(double lam) {
      double val = 0.0;
      for(int i=0;i<s.length;i++){
        double tmp = (y[i]*s[i] + 2*lam*d[i])/(s[i]*s[i] + 2*lam) - d[i];
        val += tmp*tmp;
      }

      val -= a*a;
      return val;
    }

  } // end static class lagrange

  public static class Weights implements Serializable {
    public double [] w;
    public double [] d;
    private static final long serialVersionUID = 814182172645533781L;

    public Weights(double[] wt){
      w = wt;
    }

    public Weights(double[] wt,double[] di){
      w = wt;
      d = di;
    }

  }


  public static void serializeWeights(String serializePath,double[] weights) {
    serializeWeights(serializePath,weights,null);
  }

  public static void serializeWeights(String serializePath,double[] weights,double[] diag) {

    log.info("Serializing weights to " + serializePath + "...");

    try {
      Weights out = new Weights(weights,diag);
      IOUtils.writeObjectToFile(out, serializePath);
    } catch (Exception e) {
      log.info("Error serializing to " + serializePath);
      e.printStackTrace();
    }

  }


  public static double[] getWeights(String loadPath) throws IOException, ClassCastException, ClassNotFoundException {

    log.info("Loading weights from " + loadPath + "...");
    double[] wt;
    Weights w;

    w = IOUtils.readObjectFromFile(loadPath);
    wt = w.w;

    return wt;

  }

  public static double[] getDiag(String loadPath) throws IOException, ClassCastException, ClassNotFoundException {

    log.info("Loading weights from " + loadPath + "...");
    double[] diag;
    Weights w;

    w = IOUtils.readObjectFromFile(loadPath);
    diag = w.d;

    return diag;

  }





}

