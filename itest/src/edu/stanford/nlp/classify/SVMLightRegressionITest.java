package edu.stanford.nlp.classify;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import junit.framework.Assert;
import junit.framework.TestCase;

public class SVMLightRegressionITest extends TestCase {
  
  private File modelFile;

  @Override
  protected void setUp() throws Exception {
    String prefix = SVMLightRegressionITest.class.getName();
    this.modelFile = File.createTempFile(prefix, ".model");
  }

  @Override
  protected void tearDown() throws Exception {
    Assert.assertTrue(this.modelFile.delete());
  }

  public void testLinear() throws Exception {
    RVFDataset<Double, String> trainData = new RVFDataset<Double, String>();
    for (double i = 0; i < 100; i += 2) {
      trainData.add(newRVFDatum(i, "foo", i));
    }

    RVFDataset<Double, String> testData = new RVFDataset<Double, String>();
    testData.add(newRVFDatum(6.0, "bar", 3.0));
    testData.add(newRVFDatum((Double)null, "foo", 0.0));
    testData.add(newRVFDatum(1.0, "foo", 3.0));
    testData.add(newRVFDatum(1.0, "foo", 101.0));
    List<Long> expected = Arrays.asList(0L, 0L, 3L, 101L);

    SVMLightRegressionFactory<String> factory = new SVMLightRegressionFactory<String>();
    factory.setCost(1.0);
    Regressor<String> svm = factory.train(trainData, this.modelFile);
    Assert.assertEquals(expected, rounded(svm.valuesOf(testData)));
  }
    
  public void testPolynomial() throws Exception {
    RVFDataset<Double, String> trainData = new RVFDataset<Double, String>();
    for (double i = 0; i < 20; i += 2) {
      trainData.add(newRVFDatum(Math.pow(i, 2), "foo", i));
    }

    RVFDataset<Double, String> testData = new RVFDataset<Double, String>();
    testData.add(newRVFDatum(6.0, "bar", 3.0));
    testData.add(newRVFDatum((Double)null, "foo", 0.0));
    testData.add(newRVFDatum(1.0, "foo", 3.0));
    testData.add(newRVFDatum(1.0, "foo", 11.0));
    List<Long> expected = Arrays.asList(0L, 0L, 9L, 121L);

    SVMLightRegressionFactory<String> factory = new SVMLightRegressionFactory<String>();
    factory.setKernelType(SVMLightRegressionFactory.KernelType.Polynomial);
    factory.setDegree(2);
    factory.setCost(100.0);
    Regressor<String> svm = factory.train(trainData, this.modelFile);
    Assert.assertEquals(expected, rounded(svm.valuesOf(testData)));
  }
  
  private static <L, F> RVFDatum<L, F> newRVFDatum(
      L label, F featureName, double featureValue) {
    Counter<F> features = new ClassicCounter<F>();
    features.setCount(featureName, featureValue);
    return new RVFDatum<L, F>(features, label);
  }
  
  private static List<Long> rounded(List<Double> values) {
    List<Long> results = new ArrayList<Long>();
    for (double value: values) {
      results.add(Math.round(value));
    }
    return results;
  }
}
