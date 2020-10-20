package edu.stanford.nlp.coref;


import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;

public class NeuralEnglishCorefBenchmarkSlowITest extends CorefBenchmark {

  @Override
  public void setUp() throws Exception, IOException {
    logger = Redwood.channels(NeuralEnglishCorefCoNLLBenchmarkSlowITest.class);
    EXPECTED_F1_SCORE = 59.90;
    PROPERTIES_PATH =  "edu/stanford/nlp/coref/properties/neural-english.properties";
    WORK_DIR_NAME = "NeuralEnglishCorefBenchmarkTest";
    testName = "Neural English Coref";
    super.setUp();
  }

}