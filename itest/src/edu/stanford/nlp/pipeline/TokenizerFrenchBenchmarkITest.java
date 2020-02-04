package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.StringUtils;

import java.util.Properties;


public class TokenizerFrenchBenchmarkITest extends TokenizerBenchmarkTestCase {

  @Override
  public void setUp() {
    // set up the pipeline
    Properties props = StringUtils.argsToProperties("-props", "french");
    props.put("annotators", "tokenize,ssplit,mwt");
    props.put("ssplit.isOneSentence", "true");
    pipeline = new StanfordCoreNLP(props);
  }

  public void testOnDev() {
    goldFilePath = "/u/nlp/data/stanford-corenlp-testing/data/tokenize/fr_gsd-ud-dev.conllu";
    runTest("dev", "fr", 0.991);
  }

  public void testOnTest() {
    goldFilePath = "/u/nlp/data/stanford-corenlp-testing/data/tokenize/fr_gsd-ud-test.conllu";
    runTest("test", "fr", 0.985);
  }

}
