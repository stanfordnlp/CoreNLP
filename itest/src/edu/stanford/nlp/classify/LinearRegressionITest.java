package edu.stanford.nlp.classify;


import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;

import junit.framework.Assert;
import junit.framework.TestCase;

public class LinearRegressionITest extends TestCase {

  public void testLinear() throws Exception {
    RVFDataset<Double, String> trainData = new RVFDataset<Double, String>();
    
    trainData.add(newRVFDatum(new double[]{1.0,1.0}, -1.0)); //using y = 2*x_1 - 3*x_2
    trainData.add(newRVFDatum(new double[]{1.0,-1.0}, 5.0));
    //trainData.add(newRVFDatum(new double[]{1.0,0.0}, 2.0));
    
    LinearRegressionFactory<String> factory = new LinearRegressionFactory<String>();
    factory.setRegularizationCoeff(0.0);
    Regressor<String> lr = factory.train(trainData);
    
    RVFDatum<Double,String> testDatum = newRVFDatum(new double[]{4.0,5.0}, -7.0);
    double predictedValue = lr.valueOf(testDatum);
    //System.out.println("predictedValue = "+predictedValue);
    Assert.assertEquals(new Double(Math.round(predictedValue)).doubleValue(), testDatum.label());
  }
  
  private static RVFDatum<Double, String> newRVFDatum(
      double[] values, double label) {
    Counter<String> features = new ClassicCounter<String>();
    for(int i = 0; i < values.length; i++)features.incrementCount(new Integer(i).toString(), values[i]);
    return new RVFDatum<Double, String>(features, label);
  }
}
