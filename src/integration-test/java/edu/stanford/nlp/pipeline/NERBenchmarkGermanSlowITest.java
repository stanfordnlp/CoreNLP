package edu.stanford.nlp.pipeline;


public class NERBenchmarkGermanSlowITest  extends NERBenchmarkTestCase {

  @Override
  public void languageSpecificSetUp() {
    language = "german";
    workingDir = "/u/nlp/data/ner/german";
    devGoldFile = "german-ner-w-hyphens.dev.conll";
    testGoldFile = "german-ner-w-hyphens.test.conll";
    expectedDevScore = 82.42;
    expectedTestScore = 79.44;
  }

  @Override
  public void addLanguageSpecificProperties() {

  }

}
