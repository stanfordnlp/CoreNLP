package edu.stanford.nlp.coref;


import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;

public class DeterministicChineseCorefBenchmarkSlowITest extends CorefBenchmark {

  @Override
  public void setUp() throws Exception, IOException {
    logger = Redwood.channels(NeuralEnglishCorefCoNLLBenchmarkSlowITest.class);
    EXPECTED_F1_SCORE = 47.40;
    PROPERTIES_PATH =  "edu/stanford/nlp/coref/properties/deterministic-chinese.properties";
    WORK_DIR_NAME = "DeterministicChineseCorefBenchmarkTest";
    testName = "Deterministic Chinese Coref";
    super.setUp();
  }

}