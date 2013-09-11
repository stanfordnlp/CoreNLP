package edu.stanford.nlp.ie.machinereading;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import junit.framework.TestCase;
import edu.stanford.nlp.ie.machinereading.MachineReading;

/**
 * Test NFL entity and relation extractors.
 * 
 * @author Andrey Gusev
 * @author Mihai
 */
public class NFLEntityExtractionSlowITest extends TestCase {
  
  static final String ORIGINAL_PROPS_FILE = "projects/core/itest/src/edu/stanford/nlp/ie/machinereading/nfl.properties";

  static void makePropsFile(String path, String workDir) 
    throws IOException
  {
    FileWriter fout = new FileWriter(path);
    BufferedWriter bout = new BufferedWriter(fout);
    FileReader fin = new FileReader(ORIGINAL_PROPS_FILE);
    BufferedReader bin = new BufferedReader(fin);

    String line;
    while ((line = bin.readLine()) != null) {
      bout.write(line);
      bout.newLine();
    }
    bout.write("serializedTrainingSentencesPath = " + 
               workDir + File.separator + "nfl_training_sentences.ser");
    bout.newLine();
    bout.write("serializedEntityExtractorPath = " +
               workDir + File.separator + "nfl_entity_model.ser");
    bout.newLine();
    bout.write("serializedRelationExtractorPath = " + 
               workDir + File.separator + "nfl_relation_model.ser");
    bout.newLine();
    bout.flush();
    fout.close();
    fin.close();
  }

  // This test takes a very long time, and the output isn't what the
  // expected output wants, so for now it's turned off...
  public void testEntityExtraction() throws Exception {    
    // TODO: get the "arguments" parameter from somewhere better, such
    // as the classpath, rather than hardcoding the path like this
    
    final File WORK_DIR_FILE = File.createTempFile("NFLitest", "");
    final String WORK_DIR = WORK_DIR_FILE.getPath();
    final String PROPS_PATH = WORK_DIR + File.separator + "nfl.properties";

    System.out.println("Working in directory " + WORK_DIR);

    WORK_DIR_FILE.delete();
    WORK_DIR_FILE.mkdir();
    WORK_DIR_FILE.deleteOnExit();
    
    makePropsFile(PROPS_PATH, WORK_DIR);
    System.out.println("Made props file " + PROPS_PATH);
    
    MachineReading mr = MachineReading
      .makeMachineReading(new String[] {
          "--arguments",
          PROPS_PATH
        });

    List<String> returnMsg = mr.run();

    String entityResults = returnMsg.get(1);
    System.out.println("Got the following results:");
    System.out.println(entityResults);
    String[] resultsLines = entityResults.trim().split("\\n");
    String totalResultsLine = resultsLines[resultsLines.length - 1];
    System.out.println("Final result line:");
    System.out.println(totalResultsLine);
    String[] scores = totalResultsLine.trim().split("\\s+");
    String totalF1 = scores[scores.length - 1];
    System.out.println("Total results F1 score: " + totalF1);
    double finalScore = Double.valueOf(totalF1);
    assertEquals(59.5, finalScore, 1.0);
  }

  // An example of results we would be happy with:
  /*
Label	Correct	Predict	Actual	Precn	Recall	F
_NR                            	6530.0	7287.0	6607.0	89.6	98.8	94.0
awayTeamInGame                 	0.0	0.0	11.0	0.0	0.0	0.0
fieldGoalPartialCount          	53.0	66.0	101.0	80.3	52.5	63.5
gameDate                       	33.0	36.0	115.0	91.7	28.7	43.7
gameLoser                      	35.0	74.0	124.0	47.3	28.2	35.4
gameWinner                     	33.0	69.0	123.0	47.8	26.8	34.4
homeTeamInGame                 	0.0	1.0	13.0	0.0	0.0	0.0
onePointConversionPartialCount 	0.0	0.0	2.0	0.0	0.0	0.0
safetyPartialCount             	0.0	0.0	3.0	0.0	0.0	0.0
teamFinalScore                 	108.0	112.0	232.0	96.4	46.6	62.8
teamInGame                     	117.0	242.0	257.0	48.3	45.5	46.9
teamScoringAll                 	230.0	267.0	321.0	86.1	71.7	78.2
touchDownPartialCount          	196.0	221.0	322.0	88.7	60.9	72.2
twoPointConversionPartialCount 	0.0	0.0	5.0	0.0	0.0	0.0
Total	805.0	1088.0	1629.0	74.0	49.4	59.3
  */
  
  private static void compare(String actualResults, String goldFile) throws IOException {
    File expectedResults = new File(goldFile);
    BufferedReader in = new BufferedReader(new FileReader(expectedResults));
    for (String line; (line = in.readLine()) != null; ) {
      assertTrue("Expected to find:" + line + "\n in \n" + actualResults,
                 actualResults.contains(line));
    }
    in.close();
  }
}
