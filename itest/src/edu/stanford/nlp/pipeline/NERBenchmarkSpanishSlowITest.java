package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.TestPaths;

public class NERBenchmarkSpanishSlowITest  extends NERBenchmarkTestCase {

  @Override
  public void languageSpecificSetUp() {
    language = "spanish";
    workingDir = String.format("%s/ner/spanish/test", TestPaths.testHome());
    devGoldFile = "ancora-dev-4class.tsv";
    testGoldFile = "ancora-test-4class.tsv";
    expectedDevScore = 84.94;
    expectedTestScore = 84.59;
  }

  @Override
  public void addLanguageSpecificProperties() {
    pipelineProperties.setProperty("ner.applyFineGrained", "false");
    pipelineProperties.setProperty("ner.applyNumericClassifiers", "false");
    pipelineProperties.setProperty("ner.useSUTime", "false");
  }

}
