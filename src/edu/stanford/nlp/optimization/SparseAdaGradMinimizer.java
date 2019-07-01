package edu.stanford.nlp.optimization; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.Timing;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Random;

/**
 * AdaGrad optimizer that works online, and use sparse gradients, need a
 * function that takes a Counter&lt;K&gt; as argument and returns a 
 * Counter&lt;K&gt; as gradient
 * 
 * @author Sida Wang
 */
public class SparseAdaGradMinimizer<K, F extends SparseOnlineFunction<K>> implements SparseMinimizer<K, F>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SparseAdaGradMinimizer.class);
  public boolean quiet = false;

  protected int numPasses;
  protected int batchSize;
  protected double eta;
  protected double lambdaL1;
  protected double lambdaL2;

  protected Counter<K> sumGradSquare;
  protected Counter<K> x;

  protected Random randGenerator = new Random(1);

  public final double EPS = 1e-15;
  public final double soften = 1e-4;

  public SparseAdaGradMinimizer(int numPasses) {
	this(numPasses, 0.1);
  }

  public SparseAdaGradMinimizer(int numPasses, double eta) {
	this(numPasses, eta, 1, 0, 0);
  }

  // use FOBOS to handle L1 or L2. The alternative is just setting these to 0,
  // and take any penalty into account through the derivative
  public SparseAdaGradMinimizer(int numPasses, double eta, int batchSize, double lambdaL1, double lambdaL2) {
	this.numPasses = numPasses;
	this.eta = eta;
	this.batchSize = batchSize;
	this.lambdaL1 = lambdaL1;
	this.lambdaL2 = lambdaL2;
	// can use another counter to make this thread-safe
	this.sumGradSquare = new ClassicCounter<>();
  }

  @Override
  public Counter<K> minimize(F function, Counter<K> initial) {
	return minimize(function, initial, -1);
  }

  // Does L1 or L2 using FOBOS and lazy update, so L1 should not be handled in the
  // objective
  // Alternatively, you can handle other regularization in the objective,
  // but then, if the derivative is not sparse, this routine would not be very
  // efficient. However, might still be okay for CRFs
  @Override
  public Counter<K> minimize(F function, Counter<K> x, int maxIterations) {

	sayln("       Batch size of: " + batchSize);
	sayln("       Data dimension of: " + function.dataSize());

	int numBatches = (function.dataSize() - 1) / this.batchSize + 1;
	sayln("       Batches per pass through data:  " + numBatches);
	sayln("       Number of passes is = " + numPasses);
	sayln("       Max iterations is = " + maxIterations);

	Counter<K> lastUpdated = new ClassicCounter<>();
	int timeStep = 0;

	Timing total = new Timing();
	total.start();

	for (int iter = 0; iter < numPasses; iter++) {
	  double totalObjValue = 0;

	  for (int j = 0; j < numBatches; j++) {
		int[] selectedData = getSample(function, this.batchSize);
		// the core adagrad
		Counter<K> gradient = function.derivativeAt(x, selectedData);
		totalObjValue = totalObjValue + function.valueAt(x, selectedData);

		for (K feature : gradient.keySet()) {
		  double gradf = gradient.getCount(feature);
		  double prevrate = eta / (Math.sqrt(sumGradSquare.getCount(feature)) + soften);

		  double sgsValue = sumGradSquare.incrementCount(feature, gradf * gradf);
		  double currentrate = eta / (Math.sqrt(sgsValue) + soften);
		  double testupdate = x.getCount(feature) - (currentrate * gradient.getCount(feature));
		  double lastUpdateTimeStep = lastUpdated.getCount(feature);
		  double idleinterval = timeStep - lastUpdateTimeStep - 1;
		  lastUpdated.setCount(feature, (double) timeStep);

		  // does lazy update using idleinterval
		  double trunc = Math
			  .max(0.0, (Math.abs(testupdate) - (currentrate + prevrate * idleinterval) * this.lambdaL1));
		  double trunc2 = trunc * Math.pow(1 - this.lambdaL2, currentrate + prevrate * idleinterval); 
		  double realupdate = Math.signum(testupdate) * trunc2;
		  if (realupdate < EPS) {
			x.remove(feature);
		  } else {
			x.setCount(feature, realupdate);
		  }

		  // reporting
		  timeStep++;
		  if (timeStep > maxIterations) {
			sayln("Stochastic Optimization complete.  Stopped after max iterations");
			break;
		  }
		  sayln(System.out.format("Iter %d \t batch: %d \t time=%.2f \t obj=%.4f", iter, timeStep,
			  total.report() / 1000.0, totalObjValue).toString());
		}
	  }
	}
	return x;
  }

  // you do not have to use this, and can handle the data pipeline yourself.
  // See AbstractStochasticCachingDiffFunction for more minibatching schemes,
  // but it really
  // should not matter very much
  private int[] getSample(F function, int sampleSize) {
	int[] sample = new int[sampleSize];
	for (int i = 0; i < sampleSize; i++) {
	  sample[i] = randGenerator.nextInt(function.dataSize());
	}
	return sample;
  }

  private static final NumberFormat nf = new DecimalFormat("0.000E0");

  protected String getName() {
	return "SparseAdaGrad_batchsize" + batchSize + "_eta" + nf.format(eta) + "_lambdaL1" + nf.format(lambdaL1)
		+ "_lambdaL2" + nf.format(lambdaL2);
  }

  protected void sayln(String s) {
	if (!quiet) {
	  log.info(s);
	}
  }
}
