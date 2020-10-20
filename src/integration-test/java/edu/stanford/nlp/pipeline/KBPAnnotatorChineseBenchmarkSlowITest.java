package edu.stanford.nlp.pipeline;

import java.util.Properties;

import org.junit.Before;

import edu.stanford.nlp.util.StringUtils;

public class KBPAnnotatorChineseBenchmarkSlowITest  extends KBPAnnotatorBenchmark {

  @Before
  public void setUp() {
    // set the English specific settings
    KBP_DOCS_DIR = "/u/nlp/data/kbp-resources/benchmark/chinese/kbp-docs-chinese";
    GOLD_RELATIONS_PATH = "/u/nlp/data/kbp-resources/benchmark/chinese/kbp-gold-relations-chinese.txt";
    KBP_MINIMUM_SCORE = .31;
    // load the gold relations from gold relations file
    loadGoldData();
    // set up the pipeline
    Properties props = StringUtils.argsToProperties("-props", "StanfordCoreNLP-chinese.properties");
    props.put("annotators",
            "tokenize,ssplit,pos,lemma,ner,parse,coref,kbp");
    pipeline = new StanfordCoreNLP(props);
  }

}
