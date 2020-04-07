package edu.stanford.nlp.coref;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.StringUtils;

import edu.stanford.nlp.util.logging.Redwood;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;

public class CorefBenchmark extends TestCase {

  // coref system
  public static CorefSystem corefSystem;
  // properties for test
  public static Properties props;
  // temp working directory path
  public static File WORK_DIR_FILE;

  // test specific values
  protected static Redwood.RedwoodChannels logger;
  public double EXPECTED_F1_SCORE;
  public String PROPERTIES_PATH;
  public String WORK_DIR_NAME;
  public String testName;

  public void setUp() throws Exception, IOException {

    // set up working dir
    WORK_DIR_FILE = File.createTempFile(WORK_DIR_NAME, "");
    if ( ! (WORK_DIR_FILE.delete() && WORK_DIR_FILE.mkdir())) {
      throw new RuntimeIOException("Couldn't create temp directory " + WORK_DIR_FILE);
    }

    WORK_DIR_FILE.delete();
    WORK_DIR_FILE.mkdir();

    WORK_DIR_FILE.deleteOnExit();

    // settings for coref
    String[] corefArgs = { "-props", PROPERTIES_PATH};
    props = StringUtils.argsToProperties(corefArgs);
    props.setProperty("coref.conllOutputPath", WORK_DIR_FILE.getAbsolutePath()+"/");

    // build CorefSystem
    corefSystem = new CorefSystem(props);
  }

  public void testCoref() throws Exception {
    // run CorefSystem
    corefSystem.runOnConll(props);
    // get final score
    double finalConllScore =
            CorefScorer.getFinalConllScoreFromOutputDir(
                    WORK_DIR_FILE.getAbsolutePath()+"/",CorefProperties.getScorerPath(props));
    logger.log("---");
    logger.log(testName);
    logger.log(
            "Final conll score ((muc+bcub+ceafe)/3) = " + (
                    new DecimalFormat("#.##")).format(finalConllScore));
    // test score is sufficient
    assertTrue("CoNLL score below threshold: "+finalConllScore+" < " + EXPECTED_F1_SCORE,
            finalConllScore >= EXPECTED_F1_SCORE);

  }
}
