package edu.stanford.nlp.coref;


import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;

public class DeterministicChineseCorefCoNLLBenchmarkSlowITest extends CorefBenchmark {

  @Override
  public void setUp() throws Exception, IOException {
    logger = Redwood.channels(NeuralEnglishCorefCoNLLBenchmarkSlowITest.class);
    EXPECTED_F1_SCORE = 54.90;
    PROPERTIES_PATH =  "edu/stanford/nlp/coref/properties/deterministic-chinese-conll.properties";
    WORK_DIR_NAME = "DeterministicChineseCorefCoNLLBenchmarkTest";
    testName = "Deterministic Chinese Coref (CoNLL)";
    super.setUp();
  }

}