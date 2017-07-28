package edu.stanford.nlp.classify;

import java.util.Arrays;

import edu.stanford.nlp.stats.Counter;
import junit.framework.TestCase;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;


/** @author Christopher Manning */
public class DatasetTest extends TestCase {

  public static void testDataset() {
    Dataset<String, String> data = new Dataset<>();
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"fever", "cough", "congestion"}), "cold"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"fever", "cough", "nausea"}), "flu"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"cough", "congestion"}), "cold"));

    // data.summaryStatistics();
    assertEquals(4, data.numFeatures());
    assertEquals(4, data.numFeatureTypes());
    assertEquals(2, data.numClasses());
    assertEquals(8, data.numFeatureTokens());
    assertEquals(3, data.size());

    data.applyFeatureCountThreshold(2);

    assertEquals(3, data.numFeatures());
    assertEquals(3, data.numFeatureTypes());
    assertEquals(2, data.numClasses());
    assertEquals(7, data.numFeatureTokens());
    assertEquals(3, data.size());

    //Dataset data = Dataset.readSVMLightFormat(args[0]);
    //double[] scores = data.getInformationGains();
    //System.out.println(ArrayMath.mean(scores));
    //System.out.println(ArrayMath.variance(scores));
    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();
    LinearClassifier<String, String> classifier = factory.trainClassifier(data);

    Datum<String, String> d = new BasicDatum<>(Arrays.asList(new String[]{"cough", "fever"}));
    assertEquals("Classification incorrect", "flu", classifier.classOf(d));
    Counter<String> probs = classifier.probabilityOf(d);
    assertEquals("Returned probability incorrect", 0.4553, probs.getCount("cold"), 0.0001);
    assertEquals("Returned probability incorrect", 0.5447, probs.getCount("flu"), 0.0001);
    System.out.println();
  }

}
