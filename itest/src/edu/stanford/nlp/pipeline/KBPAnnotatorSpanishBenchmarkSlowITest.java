package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.StringUtils;
import java.util.Properties;

public class KBPAnnotatorSpanishBenchmarkSlowITest extends KBPAnnotatorBenchmark {

  @Override
  public void setUp() {
    // set the English specific settings
    KBP_DOCS_DIR = "/scr/nlp/data/kbp-benchmark/spanish/kbp-docs-spanish";
    GOLD_RELATIONS_PATH = "/scr/nlp/data/kbp-benchmark/spanish/kbp-gold-relations-spanish.txt";
    KBP_MINIMUM_SCORE = .1;
    // load the gold relations from gold relations file
    loadGoldData();
    // set up the pipeline
    Properties props = StringUtils.argsToProperties("-props", "StanfordCoreNLP-spanish.properties");
    props.put("annotators",
        "tokenize,ssplit,pos,lemma,ner,regexner,entitymentions,depparse,kbp");
    pipeline = new StanfordCoreNLP(props);
  }

}
