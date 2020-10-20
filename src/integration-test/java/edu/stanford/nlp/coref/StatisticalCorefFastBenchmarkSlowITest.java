package edu.stanford.nlp.coref;


import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;

public class StatisticalCorefFastBenchmarkSlowITest extends CorefBenchmark {

  @Override
  public void setUp() throws Exception, IOException {
    logger = Redwood.channels(StatisticalCorefFastBenchmarkSlowITest.class);
    EXPECTED_F1_SCORE = 56.10;
    PROPERTIES_PATH =  "edu/stanford/nlp/coref/properties/statistical-english.properties";
    WORK_DIR_NAME = "StatisticalCorefBenchmarkTest";
    testName = "Statistical English Coref (CoNLL)";
    super.setUp();
  }

}
