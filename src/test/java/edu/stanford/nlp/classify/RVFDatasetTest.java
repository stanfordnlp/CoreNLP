package edu.stanford.nlp.classify;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import org.junit.Test;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CollectionUtils;


/** @author Steven Bethard */
public class RVFDatasetTest {

  @Test
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

    Iterator<RVFDatum<String, String>> iterator = data.iterator();
    assertEquals(datum1, iterator.next());
    assertEquals(datum2, iterator.next());
    assertFalse(iterator.hasNext());
  }

  @Test
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
      fail("expected failure with empty indexes");
    } catch (RuntimeException ignored) {}

    newDataset = new RVFDataset<>(
            dataset.size(), dataset.featureIndex(), dataset.labelIndex());
    newDataset.readSVMLightFormat(tempFile);
    assertEquals(CollectionUtils.toList(dataset), CollectionUtils.toList(newDataset));
  }

  @SafeVarargs
  private static <L, F> RVFDatum<L, F> newRVFDatum(L label, F ... items) {
    return new RVFDatum<>(Counters.asCounter(Arrays.asList(items)), label);
  }

}
