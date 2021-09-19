package edu.stanford.nlp.classify;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.util.Pair;

/**
 * @author Christopher Manning
 */
public class GeneralDatasetTest {

  @Test
  public void testCreateFolds() {
    GeneralDataset<String, String> data = new Dataset<>();
    data.add(new BasicDatum<>(Arrays.asList(new String[]{"fever", "cough", "congestion"}), "cold"));
    data.add(new BasicDatum<>(Arrays.asList(new String[]{"fever", "cough", "nausea"}), "flu"));
    data.add(new BasicDatum<>(Arrays.asList(new String[]{"cough", "congestion"}), "cold"));
    data.add(new BasicDatum<>(Arrays.asList(new String[]{"cough", "congestion"}), "cold"));
    data.add(new BasicDatum<>(Arrays.asList(new String[]{"fever", "nausea"}), "flu"));
    data.add(new BasicDatum<>(Arrays.asList(new String[]{"cough", "sore throat"}), "cold"));

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest =
            data.split(3, 5);
    Assert.assertEquals(4, devTrainTest.first().size());
    Assert.assertEquals(2, devTrainTest.second().size());
    Assert.assertEquals("cold", devTrainTest.first().getDatum(devTrainTest.first().size() - 1).label());
    Assert.assertEquals("flu", devTrainTest.second().getDatum(devTrainTest.second().size() - 1).label());

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest2 =
            data.split(0,2);
    Assert.assertEquals(4, devTrainTest2.first().size());
    Assert.assertEquals(2, devTrainTest2.second().size());

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest3 =
            data.split(1.0/3.0);
    Assert.assertEquals(devTrainTest2.first().size(), devTrainTest3.first().size());
    Assert.assertEquals(devTrainTest2.first().labelIndex(), devTrainTest3.first().labelIndex());
    Assert.assertEquals(devTrainTest2.second().size(), devTrainTest3.second().size());
    Assert.assertArrayEquals(devTrainTest2.first().labels, devTrainTest2.first().labels);
    Assert.assertArrayEquals(devTrainTest2.second().labels, devTrainTest2.second().labels);

    data.add(new BasicDatum<>(Arrays.asList(new String[]{"fever", "nausea"}), "flu"));

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest4 =
            data.split(1.0/3.0);
    Assert.assertEquals(5, devTrainTest4.first().size());
    Assert.assertEquals(2, devTrainTest4.second().size());

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest5 =
            data.split(1.0/8.0);
    Assert.assertEquals(7, devTrainTest5.first().size());
    Assert.assertEquals(0, devTrainTest5.second().size());

    // Sonal did this, but I think she got it wrong and either should have passed in test ratio or have taken p.second()
    // double trainRatio = 0.9;
    // Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> p = data.split(0, (int) Math.floor(data.size() * trainRatio));
    // assertEquals(6, p.first().size());
  }

}
