package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.ClassicCounter;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class LinearClassifierITest {

  private static <L, F> RVFDatum<L, F> newDatum(L label,
                                                F[] features,
                                                Double[] counts) {
    ClassicCounter<F> counter = new ClassicCounter<>();
    for (int i = 0; i < features.length; i++) {
      counter.setCount(features[i], counts[i]);
    }
    return new RVFDatum<>(counter, label);
  }

  /**
   * Tests string based features
   *
   * @throws Exception
   */
  private static void testStrBinaryDatums(double d1f1, double d1f2, double d2f1, double d2f2) throws Exception {
    RVFDataset<String, String> trainData = new RVFDataset<>();
    RVFDatum<String, String> d1 = newDatum("alpha",
      new String[]{"f1", "f2"},
      new Double[]{d1f1, d1f2});
    RVFDatum<String, String> d2 = newDatum("beta",
      new String[]{"f1", "f2"},
      new Double[]{d2f1, d2f2});
    trainData.add(d1);
    trainData.add(d2);
    LinearClassifierFactory<String, String> lfc = new LinearClassifierFactory<>();
    LinearClassifier<String, String> lc = lfc.trainClassifier(trainData);
    // Try the obvious (should get train data with 100% acc)
    assertEquals(d1.label(), lc.classOf(d1));
    assertEquals(d2.label(), lc.classOf(d2));
  }

  @Test
  public void testStrBinaryDatums() throws Exception {
    testStrBinaryDatums(-1.0, 0.0, 1.0, 0.0);
    testStrBinaryDatums(1.0, 0.0, -1.0, 0.0);
    testStrBinaryDatums(0.0, 1.0, 0.0, -1.0);
    testStrBinaryDatums(0.0, -1.0, 0.0, 1.0);
    testStrBinaryDatums(1.0, 1.0, -1.0, -1.0);
    testStrBinaryDatums(0.0, 1.0, 1.0, 0.0);
    testStrBinaryDatums(1.0, 0.0, 0.0, 1.0);
  }

  @Test
  public void testStrMultiClassDatums() throws Exception {
    RVFDataset<String, String> trainData = new RVFDataset<>();
    List<RVFDatum<String, String>> datums = new ArrayList<>();
    datums.add(newDatum("alpha",
      new String[]{"f1", "f2"},
      new Double[]{1.0, 0.0}));

    datums.add(newDatum("beta",
      new String[]{"f1", "f2"},
      new Double[]{0.0, 1.0}));
    datums.add(newDatum("charlie",
      new String[]{"f1", "f2"},
      new Double[]{5.0, 5.0}));
    for (RVFDatum<String, String> datum : datums)
      trainData.add(datum);
    LinearClassifierFactory<String, String> lfc = new LinearClassifierFactory<>();
    LinearClassifier<String, String> lc = lfc.trainClassifier(trainData);

    RVFDatum<String,String> td1 = newDatum("alpha",
      new String[]{"f1", "f2","f3"},
      new Double[]{2.0, 0.0, 5.5});

    // Try the obvious (should get train data with 100% acc)
    for (RVFDatum<String, String> datum : datums) {
      assertEquals(datum.label(), lc.classOf(datum));
    }

    // Test data
    assertEquals(td1.label(), lc.classOf(td1));
  }

}
