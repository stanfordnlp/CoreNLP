package edu.stanford.nlp.classify;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.Counter;


/** @author Christopher Manning */
public class DatasetTest {

  @Test
  public void testDataset() {
    Dataset<String, String> data = new Dataset<>();
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"fever", "cough", "congestion"}), "cold"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"fever", "cough", "nausea"}), "flu"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"cough", "congestion"}), "cold"));

    // data.summaryStatistics();
    Assert.assertEquals(4, data.numFeatures());
    Assert.assertEquals(4, data.numFeatureTypes());
    Assert.assertEquals(2, data.numClasses());
    Assert.assertEquals(8, data.numFeatureTokens());
    Assert.assertEquals(3, data.size());

    data.applyFeatureCountThreshold(2);

    Assert.assertEquals(3, data.numFeatures());
    Assert.assertEquals(3, data.numFeatureTypes());
    Assert.assertEquals(2, data.numClasses());
    Assert.assertEquals(7, data.numFeatureTokens());
    Assert.assertEquals(3, data.size());

    //Dataset data = Dataset.readSVMLightFormat(args[0]);
    //double[] scores = data.getInformationGains();
    //System.out.println(ArrayMath.mean(scores));
    //System.out.println(ArrayMath.variance(scores));
    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();
    LinearClassifier<String, String> classifier = factory.trainClassifier(data);

    Datum<String, String> d = new BasicDatum<>(Arrays.asList(new String[]{"cough", "fever"}));
    Assert.assertEquals("Classification incorrect", "flu", classifier.classOf(d));
    Counter<String> probs = classifier.probabilityOf(d);
    Assert.assertEquals("Returned probability incorrect", 0.4553, probs.getCount("cold"), 0.0001);
    Assert.assertEquals("Returned probability incorrect", 0.5447, probs.getCount("flu"), 0.0001);
    System.out.println();
  }

}
