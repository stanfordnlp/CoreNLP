package edu.stanford.nlp.classify;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CollectionUtils;
import junit.framework.Assert;
import junit.framework.TestCase;

public class RVFDatasetTest extends TestCase {

  public void testCombiningDatasets() {
    RVFDatum<String, String> datum1 = newRVFDatum(null, "a", "b", "a");
    RVFDatum<String, String> datum2 = newRVFDatum(null, "c", "c", "b");

    RVFDataset<String, String> data1 = new RVFDataset<>();
    data1.add(datum1);

    RVFDataset<String, String> data2 = new RVFDataset<>();
    data1.add(datum2);

    RVFDataset<String, String> data = new RVFDataset<>();
    data.addAll(data1);
    data.addAll(data2);

    Iterator<RVFDatum<String, String>> iter = data.iterator();
    Assert.assertEquals(datum1, iter.next());
    Assert.assertEquals(datum2, iter.next());
    Assert.assertFalse(iter.hasNext());
  }

  public void testSVMLightIntegerFormat() throws IOException {
    RVFDataset<Boolean, Integer> dataset = new RVFDataset<>();
    dataset.add(newRVFDatum(true, 1, 2, 1, 0));
    dataset.add(newRVFDatum(false, 2, 2, 0, 0));
    dataset.add(newRVFDatum(true, 0, 1, 2, 2));

    File tempFile = File.createTempFile("testSVMLightIntegerFormat", ".svm");
    dataset.writeSVMLightFormat(tempFile);

    RVFDataset<Boolean, Integer> newDataset = new RVFDataset<>();
    try {
      newDataset.readSVMLightFormat(tempFile);
      Assert.fail("expected failure with empty indexes");
    } catch (RuntimeException e) {}

    newDataset = new RVFDataset<Boolean, Integer>(
        dataset.size(), dataset.featureIndex(), dataset.labelIndex());
    newDataset.readSVMLightFormat(tempFile);
    Assert.assertEquals(CollectionUtils.toList(dataset), CollectionUtils.toList(newDataset));
  }

  private static <L, F> RVFDatum<L, F> newRVFDatum(L label, F ... items) {
    return new RVFDatum<L, F>(Counters.asCounter(Arrays.asList(items)), label);
  }

}
