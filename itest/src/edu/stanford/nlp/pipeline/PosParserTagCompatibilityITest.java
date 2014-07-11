package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * @author Christopher Manning
 */
public class PosParserTagCompatibilityITest extends TestCase {

  public void testEnglishTagSet() {
    LexicalizedParser lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
    MaxentTagger tagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger");
    assertEquals("English (PCFG/left3words) tagger/parser tag set mismatch",
            lp.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()), tagger.getTags().tagSet());
  }

}