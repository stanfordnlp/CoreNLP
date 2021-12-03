package edu.stanford.nlp.coref;

import edu.stanford.nlp.util.logging.Redwood;

import java.io.IOException;

public class NeuralEnglishCorefCoNLLBenchmarkSlowITest extends CorefBenchmark {

  @Override
  public void setUp() throws Exception, IOException {
    logger = Redwood.channels(NeuralEnglishCorefCoNLLBenchmarkSlowITest.class);
    EXPECTED_F1_SCORE = 65.51;
    PROPERTIES_PATH =  "edu/stanford/nlp/coref/properties/neural-english-conll.properties";
    WORK_DIR_NAME = "NeuralEnglishCorefCoNLLBenchmarkTest";
    testName = "Neural English Coref (CoNLL)";
    super.setUp();
  }

}
