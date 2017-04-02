package edu.stanford.nlp.pipeline;

import java.util.Set;

import junit.framework.TestCase;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.nndep.DependencyParser;
import edu.stanford.nlp.parser.shiftreduce.ShiftReduceParser;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.Sets;

/** This test checks whether our trained POS tagger and parser models are using the identical POS tag set
 *  for the various languages that we support. It's a good idea if they are.
 *
 *  @author Christopher Manning
 */
public class TaggerParserPosTagCompatibilityITest extends TestCase {

  private static void testTagSet4(String[] lexParsers,
                                  String[] maxentTaggers,
                                  String[] srParsers,
                                  String[] nnDepParsers) {
    LexicalizedParser lp = LexicalizedParser.loadModel(lexParsers[0]);
    Set<String> tagSet = lp.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction());
    for (String name : maxentTaggers) {
      MaxentTagger tagger = new MaxentTagger(name);
      assertEquals(lexParsers[0] + " vs. " + name + " tag set mismatch:\n" +
                   "left - right: " + Sets.diff(tagSet, tagger.tagSet()) +
                   "; right - left: " + Sets.diff(tagger.tagSet(), tagSet) + "\n",
                   tagSet, tagger.tagSet());
    }
    for (String name : lexParsers) {
      LexicalizedParser lp2 = LexicalizedParser.loadModel(name);
      assertEquals(lexParsers[0] + " vs. " + name + " tag set mismatch:\n" +
                   "left - right: " + Sets.diff(tagSet, lp2.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction())) + 
                   "; right - left: " + Sets.diff(lp2.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()), tagSet) + "\n",
                   tagSet, lp2.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()));
    }

    for (String name : srParsers) {
      ShiftReduceParser srp = ShiftReduceParser.loadModel(name);

      assertEquals(lexParsers[0] + " vs. " + name + " tag set mismatch:\n" +
                   "left - right: " + Sets.diff(tagSet, srp.tagSet()) +
                   "; right - left: " + Sets.diff(srp.tagSet(), tagSet) + "\n",
                   tagSet, srp.tagSet());
    }

    for (String name : nnDepParsers) {
      DependencyParser dp = DependencyParser.loadFromModelFile(name);

      assertEquals(lexParsers[0] + " vs. " + name + " tag set mismatch:\n" +
                   "left - right: " + Sets.diff(tagSet, dp.getPosSet()) +
                   "; right - left: " + Sets.diff(dp.getPosSet(), tagSet) + "\n",
                   tagSet, dp.getPosSet());
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

  private static final String[] englishNnParsers = {
    "edu/stanford/nlp/models/parser/nndep/english_SD.gz",
    "edu/stanford/nlp/models/parser/nndep/english_UD.gz"
  };

  public void testEnglishTagSet() {
    testTagSet4(englishParsers, englishTaggers, englishSrParsers, englishNnParsers);
  }


  private static final String[] germanTaggers = {
    "edu/stanford/nlp/models/pos-tagger/german/german-fast.tagger",
    "edu/stanford/nlp/models/pos-tagger/german/german-fast-caseless.tagger",
    // "edu/stanford/nlp/models/pos-tagger/german/german-dewac.tagger", // No longer supported; always worse than hgc
    "edu/stanford/nlp/models/pos-tagger/german/german-hgc.tagger"
  };

  private static final String[] germanParsers = {
    "edu/stanford/nlp/models/lexparser/germanPCFG.ser.gz",
    "edu/stanford/nlp/models/lexparser/germanFactored.ser.gz",
  };

  private static final String[] germanSrParsers = {
    "edu/stanford/nlp/models/srparser/germanSR.ser.gz",
  };

  private static final String[] germanNnParsers = {
    // This one uses UD tag set not fine-grained tags!
    // "edu/stanford/nlp/models/parser/nndep/UD_German.gz",
  };

  public void testGermanTagSet() {
    testTagSet4(germanParsers, germanTaggers, germanSrParsers, germanNnParsers);
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

  private static final String[] chineseNnParsers = {
    // this one doesn't quite work because Factored has URL tag but UD_Chinese doesn't (not quite sure why...).
    //    "edu/stanford/nlp/models/parser/nndep/UD_Chinese.gz"
  };

  public void testChineseTagSet() {
    testTagSet4(chineseParsers, chineseTaggers, chineseSrParsers, chineseNnParsers);
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

  private static final String[] spanishNnParsers = {
  };

  public void testSpanishTagSet() {
    testTagSet4(spanishParsers, spanishTaggers, spanishSrParsers, spanishNnParsers);
  }


  private static final String[] frenchTaggers = {
    "edu/stanford/nlp/models/pos-tagger/french/french.tagger",
  };

  private static final String[] frenchParsers = {
    "edu/stanford/nlp/models/lexparser/frenchFactored.ser.gz",
  };

  private static final String[] frenchSrParsers = {
    // todo [cdm 2016]: For some reason the SR parsers don't have the same tag set. Investigate.
    // "edu/stanford/nlp/models/srparser/frenchSR.beam.ser.gz",
    // "edu/stanford/nlp/models/srparser/frenchSR.ser.gz",
  };

  private static final String[] frenchNnParsers = {
  };

  public void testFrenchTagSet() {
    testTagSet4(frenchParsers, frenchTaggers, frenchSrParsers, frenchNnParsers);
  }


  // todo: Add Arabic sometime
  // todo: Add nndep parsers sometime

}
