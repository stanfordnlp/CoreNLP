package edu.stanford.nlp.pipeline;

import java.util.Set;

import edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser;
import junit.framework.TestCase;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * @author Christopher Manning
 */
public class TaggerParserPosTagCompatibilityITest extends TestCase {

  private static void testTagSet3(String[] lexParsers, String[] maxentTaggers, String[] srParsers) {
    LexicalizedParser lp = LexicalizedParser.loadModel(lexParsers[0]);
    Set<String> tagSet = lp.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction());
    for (String name : maxentTaggers) {
      MaxentTagger tagger = new MaxentTagger(name);
      assertEquals(lexParsers[0] + " vs. " + name + " tag set mismatch", tagSet, tagger.tagSet());
    }
    for (String name : lexParsers) {
      LexicalizedParser lp2 = LexicalizedParser.loadModel(name);
      assertEquals(lexParsers[0] + " vs. " + name + " tag set mismatch",
                   tagSet, lp2.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()));
    }

    for (String name : srParsers) {
      ShiftReduceParser srp = ShiftReduceParser.loadModel(name);

      assertEquals(lexParsers[0] + " vs. " + name + " tag set mismatch",
                   tagSet, srp.tagSet());
    }
  }


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

  private static final String[] englishSrParsers = {
    "edu/stanford/nlp/models/srparser/englishSR.beam.ser.gz",
    "edu/stanford/nlp/models/srparser/englishSR.ser.gz",
  };

  public void testEnglishTagSet() {
    testTagSet3(englishParsers, englishTaggers, englishSrParsers);
  }


  private static final String[] germanTaggers = {
    "edu/stanford/nlp/models/pos-tagger/german/german-fast.tagger",
    "edu/stanford/nlp/models/pos-tagger/german/german-fast-caseless.tagger",
    "edu/stanford/nlp/models/pos-tagger/german/german-fast.tagger",
    "edu/stanford/nlp/models/pos-tagger/german/german-hgc.tagger"
  };

  private static final String[] germanParsers = {
    "edu/stanford/nlp/models/lexparser/germanPCFG.ser.gz",
    "edu/stanford/nlp/models/lexparser/germanFactored.ser.gz",
  };
  private static final String[] germanSrParsers = {
    "edu/stanford/nlp/models/srparser/germanSR.ser.gz",
  };

  public void testGermanTagSet() {
    testTagSet3(germanParsers, germanTaggers, germanSrParsers);
  }


  private static final String[] chineseTaggers = {
    "edu/stanford/nlp/models/pos-tagger/chinese-distsim/chinese-distsim.tagger",
  };

  private static final String[] chineseParsers = {
    // Can't compare Xinhua ones because they have a smaller tag set than the full CTB v6+
//    "edu/stanford/nlp/models/lexparser/xinhuaPCFG.ser.gz",
    "edu/stanford/nlp/models/lexparser/chineseFactored.ser.gz",
    "edu/stanford/nlp/models/lexparser/chinesePCFG.ser.gz",
//    "edu/stanford/nlp/models/lexparser/xinhuaFactoredSegmenting.ser.gz",
//    "edu/stanford/nlp/models/lexparser/xinhuaFactored.ser.gz",

  };
  private static final String[] chineseSrParsers = {
    "edu/stanford/nlp/models/srparser/chineseSR.ser.gz",
  };

  public void testChineseTagSet() {
    testTagSet3(chineseParsers, chineseTaggers, chineseSrParsers);
  }


  private static final String[] spanishTaggers = {
    "edu/stanford/nlp/models/pos-tagger/spanish/spanish.tagger",
    "edu/stanford/nlp/models/pos-tagger/spanish/spanish-distsim.tagger",
  };

  private static final String[] spanishParsers = {
    "edu/stanford/nlp/models/lexparser/spanishPCFG.ser.gz",
  };

  private static final String[] spanishSrParsers = {
          // todo [cdm 2014]: For some reason the SR parsers don't have the same tag set, missing 6 tags....
//    "edu/stanford/nlp/models/srparser/spanishSR.ser.gz",
//          "edu/stanford/nlp/models/srparser/spanishSR.beam.ser.gz",
  };

  public void testSpanishTagSet() {
    testTagSet3(spanishParsers, spanishTaggers, spanishSrParsers);
  }

  // todo: Add French and Arabic sometime

}
