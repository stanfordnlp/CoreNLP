package edu.stanford.nlp.pipeline;

import java.util.Properties;

import org.junit.Before;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;

public class KBPAnnotatorChineseBenchmarkSlowITest  extends KBPAnnotatorBenchmark {

  @Before
  public void setUp() {
    // set the English specific settings
    KBP_DOCS_DIR = String.format("%s/kbp-resources/benchmark/chinese/kbp-docs-chinese", TestPaths.testHome());
    GOLD_RELATIONS_PATH =
        String.format("%s/kbp-resources/benchmark/chinese/kbp-gold-relations-chinese.txt", TestPaths.testHome());
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
