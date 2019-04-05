package edu.stanford.nlp.pipeline;

import java.util.Properties;

public class TokenizerSpanishBenchmarkITest extends TokenizerBenchmarkTestCase {

    @Override
    public void setUp() {
        // set up the pipeline
        Properties props = new Properties();
        props.put("annotators", "tokenize");
        props.put("tokenize.language", "es");
        pipeline = new StanfordCoreNLP(props);
    }

    public void testOnDev() {
        goldFilePath = "/u/nlp/data/stanford-corenlp-testing/data/tokenize/es_ancora-ud-dev.conllu";
        runTest("dev", "es", 0.5);
    }


}
