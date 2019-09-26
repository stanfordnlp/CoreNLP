package edu.stanford.nlp.pipeline;


public class NERBenchmarkSpanishSlowITest  extends NERBenchmarkTestCase {

  @Override
  public void languageSpecificSetUp() {
    language = "spanish";
    workingDir = "/u/nlp/data/ner/spanish/4class";
    devGoldFile = "ancora.dev.conll";
    testGoldFile = "ancora.test.conll";
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