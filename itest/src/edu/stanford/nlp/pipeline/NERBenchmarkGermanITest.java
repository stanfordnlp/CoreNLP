package edu.stanford.nlp.pipeline;


public class NERBenchmarkGermanITest  extends NERBenchmarkTestCase {

  @Override
  public void languageSpecificSetUp() {
    language = "german";
    workingDir = "/u/nlp/data/ner/german";
    devGoldFile = "german-ner-w-hyphens.dev.conll";
    testGoldFile = "german-ner-w-hyphens.test.conll";
    expectedDevScore = 82.27;
    expectedTestScore = 84.59;
  }

  @Override
  public void addLanguageSpecificProperties() {

  }

}
