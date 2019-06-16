package edu.stanford.nlp.pipeline;

import java.util.Properties;


public class TokenizerFrenchBenchmarkITest  extends TokenizerBenchmarkTestCase {

    @Override
    public void setUp() {
        // set up the pipeline
        Properties props = new Properties();
        props.put("annotators", "tokenize,ssplit,mwt");
        props.put("tokenize.language", "fr");
        props.put("tokenize.options", "splitAll=false");
        props.put("mwt.mappingFile",
                "/u/nlp/data/stanford-corenlp/test/data/mwt/fr-mwt.tsv");
        props.put("mwt.pos.model", "/u/nlp/data/stanford-corenlp/test/models/fr-mwt.tagger");
        props.put("tokenize.mwt.statisticalMappingFile",
                "/u/nlp/data/stanford-corenlp/test/data/fr-mwt-statistical.tsv");
        props.put("ssplit.isOneSentence", "true");
        pipeline = new StanfordCoreNLP(props);
    }

    public void testOnDev() {
        goldFilePath = "/u/nlp/data/stanford-corenlp/test/data/tokenize/fr_gsd-ud-dev.conllu";
        runTest("dev", "fr", 0.90);
    }

}
