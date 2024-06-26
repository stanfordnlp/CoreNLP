package edu.stanford.nlp.util;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import edu.stanford.nlp.stats.Counter;


/** This class can be used to help assess results of benchmark tests.
 *
 *  @author Christopher Manning
 */
public class BenchmarkingHelper {

  private BenchmarkingHelper() {} // static methods

  public static void setLowHighExpected(Counter<String> lowRes, Counter<String> highRes, Counter<String> expRes, String key,
                                         double lowVal, double highVal, double expVal) {
    lowRes.setCount(key, lowVal);
    highRes.setCount(key, highVal);
    expRes.setCount(key, expVal);
  }

  public static void benchmarkResults(Counter<String> results,
                                      Counter<String> lowResults,
                                      Counter<String> highResults,
                                      Counter<String> expectedResults) {
    if (results.keySet().isEmpty()) {
      fail("There are no results to benchmark!");
    }
    for (String key : results.keySet()) {
      if ( ! (highResults.containsKey(key) && lowResults.containsKey(key))) {
        fail("Missing performance bounds for " + key);
      }
      double val = results.getCount(key);
      double high = highResults.getCount(key);
      double low = lowResults.getCount(key);
      assertTrue("Value for " + key + " = " + val + " is lower than expected minimum " + low, val >= low);
      assertTrue("Value for " + key + " = " + val + " is higher than expected maximum " + high +
          " [not a bug, but a breakthrough!]", val <= high);
      if (expectedResults == null) {
        System.err.printf("Value for %s = %.4f is within the expected range [%.4f, %.4f]%n", key, val, low, high);
      } else {
        double expected = expectedResults.getCount(key);
        if (val < (expected - 1e-4)) {
          System.err.printf("Value for %s = %.4f is slightly lower than expected %.4f%n", key, val, expected);
        } else if (val > (expected + 1e-4)) {
          System.err.printf("Value for %s = %.4f is slightly higher than expected %.4f%n", key, val, expected);
        } else {
          System.err.printf("Value for %s  = %.4f is as expected%n", key, val);
        }
      }
    }
  }

}
