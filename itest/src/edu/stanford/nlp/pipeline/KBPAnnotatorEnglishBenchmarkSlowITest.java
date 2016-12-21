package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;

import java.io.File;
import java.util.*;

public class KBPAnnotatorEnglishBenchmarkSlowITest extends KBPAnnotatorBenchmark {

  @Override
  public void setUp() {
    // set the English specific settings
    KBP_DOCS_DIR = "/scr/nlp/data/kbp-benchmark/kbp-docs";
    GOLD_RELATIONS_PATH = "/scr/nlp/data/kbp-benchmark/kbp-gold-relations.txt";
    KBP_MINIMUM_SCORE = .450;
    docIDToText = new HashMap<String,String>();
    docIDToRelations = new HashMap<String,Set<String>>();
    // load the gold relations from gold relations file
    loadGoldData();
    // set up the pipeline
    Properties props = new Properties();
    props.put("annotators",
            "tokenize,ssplit,pos,lemma,ner,regexner,parse,mention,entitymentions,coref,kbp");
    props.put("coref.md.type", "RULE");
    pipeline = new StanfordCoreNLP(props);
  }

}
