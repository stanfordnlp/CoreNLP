package edu.stanford.nlp.pipeline;

import java.util.Set;

import junit.framework.TestCase;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * @author Christopher Manning
 */
public class PosParserTagCompatibilityITest extends TestCase {

  // todo: rename to TaggerParserPosCompatibility.  Add other models.

  private static final String[] englishTaggers = {
    "edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger",
    "edu/stanford/nlp/models/pos-tagger/english-bidirectional/english-bidirectional-distsim.tagger",
    "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger",
  };

  private static final String[] englishParsers = {
    "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz",
    "edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz",
    "edu/stanford/nlp/models/lexparser/englishRNN.ser.gz",
    "edu/stanford/nlp/models/lexparser/englishFactored.ser.gz",
  };


  public void testEnglishTagSet() {
    LexicalizedParser lp = LexicalizedParser.loadModel(englishParsers[0]);
    Set<String> tagSet = lp.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction());
    for (String name : englishTaggers) {
      MaxentTagger tagger = new MaxentTagger(name);
      assertEquals("English PCFG parser/" + name + " tag set mismatch", tagSet, tagger.tagSet());
    }
    for (String name : englishParsers) {
      LexicalizedParser lp2 = LexicalizedParser.loadModel(name);
      assertEquals("English PCFG parser/" + name + " tag set mismatch",
                   tagSet, lp2.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()));
    }
  }

  private static final String[] germanTaggers = {
    "/u/nlp/data/pos-tagger/distrib-2014-07-03/german-fast.tagger",
    "/u/nlp/data/pos-tagger/distrib-2014-07-03/german-fast-caseless.tagger",
    "/u/nlp/data/pos-tagger/distrib-2014-07-03/german-fast.tagger",
    "/u/nlp/data/pos-tagger/distrib-2014-07-03/german-hgc.tagger"
  };

  private static final String[] germanParsers = {
    "edu/stanford/nlp/models/lexparser/germanPCFG.ser.gz",
    "edu/stanford/nlp/models/lexparser/germanFactored.ser.gz",
  };


  /*
  // todo: fix German so the tests pass
  // todo: Change to use classpath models once new taggers are in classpath models
  public void testGermanTagSet() {
    LexicalizedParser lp = LexicalizedParser.loadModel(germanParsers[0]);
    Set<String> tagSet = lp.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction());
    for (String name : germanTaggers) {
      MaxentTagger tagger = new MaxentTagger(name);
      assertEquals("German PCFG parser/tagger tag set mismatch", tagSet, tagger.tagSet());
    }
    LexicalizedParser lp2 = LexicalizedParser.loadModel(germanParsers[1]);
    assertEquals("German (PCFG/factored) parsers tag set mismatch",
                 lp.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()),
                 lp2.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()));
  }
  */

  // todo: rewrite to test all Chinese models, as for English
  public void testChineseTagSet() {
    LexicalizedParser lp = LexicalizedParser.loadModel("edu/stanford/nlp/models/lexparser/chineseFactored.ser.gz");
    MaxentTagger tagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/chinese-distsim/chinese-distsim.tagger");
    assertEquals("Chinese (Fact/distsim) parser/tagger tag set mismatch",
            lp.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()), tagger.tagSet());
  }

}
