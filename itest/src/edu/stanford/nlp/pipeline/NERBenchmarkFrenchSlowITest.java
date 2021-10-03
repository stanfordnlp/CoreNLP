package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.TestPaths;

public class NERBenchmarkFrenchSlowITest extends NERBenchmarkTestCase {

  @Override
  public void languageSpecificSetUp() {
    language = "french";
    workingDir = String.format("%s/ner/french", TestPaths.testHome());
    devGoldFile = "french-ner.wikiner.4class.dev";
    testGoldFile = "french-ner.wikiner.4class.test";
    expectedDevScore = 89.68;
    expectedTestScore = 89.42;
  }

  @Override
  public void addLanguageSpecificProperties() {

  }

}
