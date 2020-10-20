package edu.stanford.nlp.coref;


import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;

public class NeuralChineseCorefCoNLLBenchmarkSlowITest extends CorefBenchmark {

  @Override
  public void setUp() throws Exception, IOException {
    logger = Redwood.channels(NeuralEnglishCorefCoNLLBenchmarkSlowITest.class);
    EXPECTED_F1_SCORE = 63.10;
    PROPERTIES_PATH =  "edu/stanford/nlp/coref/properties/neural-chinese-conll.properties";
    WORK_DIR_NAME = "NeuralChineseCorefCoNLLBenchmarkTest";
    testName = "Neural Chinese Coref (CoNLL)";
    super.setUp();
  }

}