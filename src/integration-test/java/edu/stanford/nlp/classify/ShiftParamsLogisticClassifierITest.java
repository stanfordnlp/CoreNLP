package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.ClassicCounter;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author sonalg
 * @version 11/24/14
 */
public class ShiftParamsLogisticClassifierITest {

  private static <L, F> BasicDatum<L, F> newDatum(L label,
                                                  F[] features,
                                                  Double[] counts) {
    ClassicCounter<F> counter = new ClassicCounter<>();
    for (int i = 0; i < features.length; i++) {
      counter.setCount(features[i], counts[i]);
    }
    return new BasicDatum<>(counter.keySet(), label);
  }

  private static void testStrBinaryDatums(double d1f1, double d1f2, double d2f1, double d2f2) throws Exception {
    Dataset<String, String> trainData = new Dataset<>();
    Datum<String, String> d1 = newDatum("alpha",
      new String[]{"f1", "f2"},
      new Double[]{d1f1, d1f2});
    Datum<String, String> d2 = newDatum("beta",
      new String[]{"f1", "f2"},
      new Double[]{d2f1, d2f2});
    trainData.add(d1);
    trainData.add(d2);
    LogPrior prior = new LogPrior(LogPrior.LogPriorType.QUADRATIC, 1.0, 0.1);

    ShiftParamsLogisticClassifierFactory<String, String> lfc = new ShiftParamsLogisticClassifierFactory<>(prior, 0.01);
    MultinomialLogisticClassifier<String, String> lc = lfc.trainClassifier(trainData);
    // Try the obvious (should get train data with 100% acc)
    Assert.assertEquals(d1.label(), lc.classOf(d1));
    Assert.assertEquals(d2.label(), lc.classOf(d2));
  }

  @Test
  public void testStrBinaryDatums() throws Exception {
//    testStrBinaryDatums(1.0, 0.0, 1.0, 0.0);
//    testStrBinaryDatums(1.0, 0.0, -1.0, 0.0);
//    testStrBinaryDatums(0.0, 1.0, 0.0, -1.0);
//    testStrBinaryDatums(0.0, -1.0, 0.0, 1.0);
//    testStrBinaryDatums(1.0, 1.0, -1.0, -1.0);
//    testStrBinaryDatums(0.0, 1.0, 1.0, 0.0);
//    testStrBinaryDatums(1.0, 0.0, 0.0, 1.0);
  }

}
