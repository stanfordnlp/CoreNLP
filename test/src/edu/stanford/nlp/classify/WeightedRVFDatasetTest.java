package edu.stanford.nlp.classify;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counters;


/**
 * TODO(gabor) JavaDoc
 *
 * @author Gabor Angeli
 */
public class WeightedRVFDatasetTest {

  @Test
  public void testWeightingWorks() {
    WeightedRVFDataset<String, String> dataset = new WeightedRVFDataset<>();
    RVFDatum<String, String> datum1 = newRVFDatum(null, "a", "b", "a");
    dataset.add(datum1, 42.0f);
    RVFDatum<String, String> datum2 = newRVFDatum(null, "a", "b", "a");
    dataset.add(datum2, 7.3f);
    Assert.assertEquals(42.0f, dataset.getWeights()[0], 1e-10);
    Assert.assertEquals(7.3f, dataset.getWeights()[1], 1e-10);
  }

  @Test
  public void testBackwardsCompatibility() {
    WeightedRVFDataset<String, String> dataset = new WeightedRVFDataset<>();
    RVFDatum<String, String> datum1 = newRVFDatum(null, "a", "b", "a");
    dataset.add(datum1);
    RVFDatum<String, String> datum2 = newRVFDatum(null, "a", "b", "a");
    dataset.add(datum2);
    Assert.assertEquals(1.0f, dataset.getWeights()[0], 1e-10);
    Assert.assertEquals(1.0f, dataset.getWeights()[1], 1e-10);
  }


  @Test
  public void testMixedCompatibility() {
    WeightedRVFDataset<String, String> dataset = new WeightedRVFDataset<>();
    RVFDatum<String, String> datum1 = newRVFDatum(null, "a", "b", "a");
    dataset.add(datum1, 42.0f);
    RVFDatum<String, String> datum2 = newRVFDatum(null, "a", "b", "a");
    dataset.add(datum2);
    RVFDatum<String, String> datum3 = newRVFDatum(null, "a", "b", "a");
    dataset.add(datum3, 7.3f);
    Assert.assertEquals(42.0f, dataset.getWeights()[0], 1e-10);
    Assert.assertEquals(1.0f, dataset.getWeights()[1], 1e-10);
    Assert.assertEquals(7.3f, dataset.getWeights()[2], 1e-10);
  }


  @SafeVarargs
  private static <L, F> RVFDatum<L, F> newRVFDatum(L label, F ... items) {
    return new RVFDatum<>(Counters.asCounter(Arrays.asList(items)), label);
  }

}
