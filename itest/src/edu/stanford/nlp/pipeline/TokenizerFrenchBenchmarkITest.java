package edu.stanford.nlp.pipeline;

import java.util.Properties;


public class TokenizerFrenchBenchmarkITest extends TokenizerBenchmarkTestCase {

  @Override
  public void setUp() {
    // set up the pipeline
    Properties props = new Properties();
    props.put("annotators", "tokenize,ssplit,mwt");
    props.put("tokenize.language", "fr");
    props.put("mwt.mappingFile",
        "edu/stanford/nlp/models/mwt/french/french-mwt.tsv");
    props.put("mwt.pos.model", "edu/stanford/nlp/models/mwt/french/french-mwt.tagger");
    props.put("mwt.statisticalMappingFile",
        "edu/stanford/nlp/models/mwt/french/french-mwt-statistical.tsv");
    props.put("ssplit.isOneSentence", "true");
    pipeline = new StanfordCoreNLP(props);
  }

  public void testOnDev() {
    goldFilePath = "/u/nlp/data/stanford-corenlp/test/data/tokenize/fr_gsd-ud-dev.conllu";
    runTest("dev", "fr", 0.991);
  }

  public void testOnTest() {
    goldFilePath = "/u/nlp/data/stanford-corenlp/test/data/tokenize/fr_gsd-ud-test.conllu";
    runTest("test", "fr", 0.985);
  }

}
