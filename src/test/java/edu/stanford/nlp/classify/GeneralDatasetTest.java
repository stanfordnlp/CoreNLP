package edu.stanford.nlp.classify;

import java.util.Arrays;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.util.Pair;


/**
 * @author Christopher Manning
 */
public class GeneralDatasetTest extends TestCase {

  public static void testCreateFolds() {
    GeneralDataset<String, String> data = new Dataset<>();
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"fever", "cough", "congestion"}), "cold"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"fever", "cough", "nausea"}), "flu"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"cough", "congestion"}), "cold"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"cough", "congestion"}), "cold"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"fever", "nausea"}), "flu"));
    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"cough", "sore throat"}), "cold"));

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest =
            data.split(3, 5);
    assertEquals(4, devTrainTest.first().size());
    assertEquals(2, devTrainTest.second().size());
    assertEquals("cold", devTrainTest.first().getDatum(devTrainTest.first().size() - 1).label());
    assertEquals("flu", devTrainTest.second().getDatum(devTrainTest.second().size() - 1).label());

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest2 =
            data.split(0,2);
    assertEquals(4, devTrainTest2.first().size());
    assertEquals(2, devTrainTest2.second().size());

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest3 =
            data.split(1.0/3.0);
    assertEquals(devTrainTest2.first().size(), devTrainTest3.first().size());
    assertEquals(devTrainTest2.first().labelIndex(), devTrainTest3.first().labelIndex());
    assertEquals(devTrainTest2.second().size(), devTrainTest3.second().size());
    assertTrue(Arrays.equals(devTrainTest2.first().labels, devTrainTest2.first().labels));
    assertTrue(Arrays.equals(devTrainTest2.second().labels, devTrainTest2.second().labels));

    data.add(new BasicDatum<String, String>(Arrays.asList(new String[]{"fever", "nausea"}), "flu"));

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest4 =
            data.split(1.0/3.0);
    assertEquals(5, devTrainTest4.first().size());
    assertEquals(2, devTrainTest4.second().size());

    Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> devTrainTest5 =
            data.split(1.0/8.0);
    assertEquals(7, devTrainTest5.first().size());
    assertEquals(0, devTrainTest5.second().size());

    // Sonal did this, but I think she got it wrong and either should have past in test ratio or have taken p.second()
    // double trainRatio = 0.9;
    // Pair<GeneralDataset<String,String>,GeneralDataset<String,String>> p = data.split(0, (int) Math.floor(data.size() * trainRatio));
    // assertEquals(6, p.first().size());
  }

}
