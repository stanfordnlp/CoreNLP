package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;

import java.util.Properties;

import org.junit.Before;

public class KBPAnnotatorSpanishBenchmarkSlowITest extends KBPAnnotatorBenchmark {

  @Before
  public void setUp() {
    // set the English specific settings
    KBP_DOCS_DIR = String.format("%s/kbp-resources/benchmark/spanish/kbp-docs-spanish", TestPaths.testHome());
    GOLD_RELATIONS_PATH = String.format("%s/kbp-resources/benchmark/spanish/kbp-gold-relations-spanish.txt", TestPaths.testHome());
    KBP_MINIMUM_SCORE = .3425;
    // load the gold relations from gold relations file
    loadGoldData();
    // set up the pipeline
    Properties props = StringUtils.argsToProperties("-props", "StanfordCoreNLP-spanish.properties");
    props.put("ner.model", "edu/stanford/nlp/models/ner/spanish.kbp.ancora.distsim.s512.crf.ser.gz");
    pipeline = new StanfordCoreNLP(props);
  }

}
