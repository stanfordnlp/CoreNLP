package edu.stanford.nlp.optimization;

import java.util.Arrays;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class MinimizerTest extends TestCase {

  /** H.H. Rosenbrock. 1960. An Automatic Method for Finding the Greatest or
   *  Least Value of a Function. Computer Journal 3, 175-184.
   */
  private static class RosenbrockFunction implements DiffFunction {

    @Override
    public double[] derivativeAt(double[] x) {
      double[] derivatives = new double[2];
      // df/dx = -400x(y-x^2) - 2(1-x)
      derivatives[0] = -400.0 * x[0] * (x[1] - x[0] * x[0]) - 2 * (1.0 - x[0]);
      // df/dy = 200(y-x^2)
      derivatives[1] = 200.0 * (x[1] - x[0] * x[0]);
      return derivatives;
    }

    /** f(x,y) = (1-x)^2 + 100(y-x^2)^2 */
    @Override
    public double valueAt(double[] x) {
      double t1 = (1.0 - x[0]);
      double t2 = x[1] - x[0] * x[0];
      return t1 * t1 + 100.0 * t2 * t2;
    }

    @Override
    public int domainDimension() {
      return 2;
    }
  }

  public void testRosenbrock() {
    DiffFunction rf = new RosenbrockFunction();
    DiffFunctionTest.gradientCheck(rf);
  }

  public void testQNMinimizerRosenbrock() {
    double[] initial = { 0.0, 0.0 };
    DiffFunction rf = new RosenbrockFunction();
    QNMinimizer qn = new QNMinimizer();
    double[] answer = qn.minimize(rf, 1e-10, initial);
    System.err.println("Answer is: " + Arrays.toString(answer));
    assertEquals(1.0, answer[0], 1e-8);
    assertEquals(1.0, answer[1], 1e-8);
  }

}