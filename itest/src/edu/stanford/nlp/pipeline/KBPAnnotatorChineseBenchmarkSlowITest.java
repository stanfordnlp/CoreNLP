package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.StringUtils;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;

public class KBPAnnotatorChineseBenchmarkSlowITest  extends KBPAnnotatorBenchmark {

  @Override
  public void setUp() {
    // set the English specific settings
    KBP_DOCS_DIR = "/scr/nlp/data/kbp-benchmark/chinese/kbp-docs-chinese";
    GOLD_RELATIONS_PATH = "/scr/nlp/data/kbp-benchmark/chinese/kbp-gold-relations-chinese.txt";
    KBP_MINIMUM_SCORE = .290;
    // load the gold relations from gold relations file
    loadGoldData();
    // set up the pipeline
    Properties props = StringUtils.argsToProperties("-props", "StanfordCoreNLP-chinese.properties");
    props.put("annotators",
            "tokenize,ssplit,pos,lemma,ner,regexner,parse,mention,entitymentions,coref,kbp");
    pipeline = new StanfordCoreNLP(props);
  }

}
