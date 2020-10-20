package edu.stanford.nlp.pipeline;

import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

import org.junit.Before;

public class KBPAnnotatorEnglishBenchmarkSlowITest extends KBPAnnotatorBenchmark {

  @Before
  public void setUp() {
    // set the English specific settings
    KBP_DOCS_DIR = "/u/nlp/data/kbp-resources/benchmark/kbp-docs";
    GOLD_RELATIONS_PATH = "/u/nlp/data/kbp-resources/benchmark/kbp-gold-relations.txt";
    KBP_MINIMUM_SCORE = .455;
    docIDToText = new HashMap<String,String>();
    docIDToRelations = new HashMap<String,Set<String>>();
    // load the gold relations from gold relations file
    loadGoldData();
    // set up the pipeline
    Properties props = new Properties();
    props.put("annotators",
            "tokenize,ssplit,pos,lemma,ner,parse,coref,kbp");
    props.put("coref.md.type", "RULE");
    pipeline = new StanfordCoreNLP(props);
  }

}
