package edu.stanford.nlp.pipeline;

import java.util.Properties;

public class TokenizerGermanBenchmarkITest extends TokenizerBenchmarkTestCase {

    @Override
    public void setUp() {
        // set up the pipeline
        Properties props = new Properties();
        props.put("annotators", "tokenize,ssplit,mwt");
        props.put("tokenize.language", "de");
        props.put("mwt.mappingFile",
                "edu/stanford/nlp/models/mwt/german/german-mwt.tsv");
        pipeline = new StanfordCoreNLP(props);
    }

    public void testOnDev() {
        goldFilePath = "/u/nlp/data/stanford-corenlp/test/data/tokenize/de_gsd-ud-dev.conllu";
        runTest("dev", "de", 0.992);
    }

    public void testOnTest() {
        goldFilePath = "/u/nlp/data/stanford-corenlp/test/data/tokenize/de_gsd-ud-test.conllu";
        runTest("test", "de", 0.992);
    }

}
