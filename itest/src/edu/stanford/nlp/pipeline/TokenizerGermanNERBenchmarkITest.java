package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.StringUtils;

import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

public class TokenizerGermanNERBenchmarkITest extends TokenizerBenchmarkTestCase {

  @Before
  public void setUp() {
    // set up the pipeline
    Properties props = StringUtils.argsToProperties("-props", "german");
    props.put("customAnnotatorClass.tokenize.german.ner",
        "edu.stanford.nlp.pipeline.GermanNERTokenizerAnnotator");
    props.put("annotators", "tokenize,ssplit,mwt,tokenize.german.ner");
    props.put("ssplit.isOneSentence", "true");
    pipeline = new StanfordCoreNLP(props);
  }

  @Test
  public void testOnSample() {
    goldFilePath = "/u/nlp/data/ner/german/tokenization/german-ner-gold-tokenization.conllu";
    runTest("test", "de", .992);
  }

}