package edu.stanford.nlp.pipeline;


public class NERBenchmarkSpanishSlowITest  extends NERBenchmarkTestCase {

  @Override
  public void languageSpecificSetUp() {
    language = "spanish";
    workingDir = "/u/nlp/data/ner/spanish/test";
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
