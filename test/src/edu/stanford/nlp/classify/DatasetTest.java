package edu.stanford.nlp.classify;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.stats.Counter;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;


/** @author Christopher Manning */
public class DatasetTest {

  private Dataset<String, String> data = new Dataset<>();

  @Before
  public void DSsetUp() {
    data.add(new BasicDatum<String, String>(Arrays.asList("fever", "cough", "congestion"), "cold"));
    data.add(new BasicDatum<String, String>(Arrays.asList("fever", "cough", "nausea"), "flu"));
    data.add(new BasicDatum<String, String>(Arrays.asList("cough", "congestion"), "cold"));
  }

  @Test
  public void testDSnumFeatures(){
    Assert.assertEquals(4, data.numFeatures());

    data.applyFeatureCountThreshold(2);

    Assert.assertEquals("test numFeatures after calling FeatureThreshhold",3, data.numFeatures());
  }

  @Test
  public void testDSnumFeatureTypes() {
    Assert.assertEquals(4, data.numFeatureTypes());

    data.applyFeatureCountThreshold(2);

    Assert.assertEquals("test numFeatureTypes after calling FeatureThreshhold",3, data.numFeatureTypes());
  }

  @Test
  public void testDSsize() {
    Assert.assertEquals(3, data.size());

    data.applyFeatureCountThreshold(2);

    Assert.assertEquals("test size after calling FeatureThreshhold",3,data.size());
  }

  @Test
  public void testDSnumFeatureTokens() {
    Assert.assertEquals(8, data.numFeatureTokens());

    data.applyFeatureCountThreshold(2);

    Assert.assertEquals("test numFeatureTokens after calling FeatureThreshhold",7, data.numFeatureTokens());
  }

  @Test
  public void testDSnumClasses() {
    Assert.assertEquals(2, data.numClasses());

    data.applyFeatureCountThreshold(2);

    Assert.assertEquals("test numClasses after calling FeatureThreshhold",2, data.numClasses());
  }

  @Test
  public void testLinearClassifierClassOfUsingDS() {
    data.applyFeatureCountThreshold(2);
    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();
    LinearClassifier<String, String> classifier = factory.trainClassifier(data);
    Datum<String, String> d = new BasicDatum<>(Arrays.asList("cough", "fever"));

    Assert.assertEquals("Classification incorrect", "flu", classifier.classOf(d));
  }

  @Test
  public void testLinearClassifierProbabilityUsingDS(){
    data.applyFeatureCountThreshold(2);
    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();
    LinearClassifier<String, String> classifier = factory.trainClassifier(data);
    Datum<String, String> d = new BasicDatum<>(Arrays.asList("cough", "fever"));

    Counter<String> probs = classifier.probabilityOf(d);
    Assert.assertEquals("Returned probability incorrect", 0.4553, probs.getCount("cold"), 0.0001);
    Assert.assertEquals("Returned probability incorrect", 0.5447, probs.getCount("flu"), 0.0001);
    System.out.println();
  }
}
