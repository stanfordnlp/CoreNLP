package edu.stanford.nlp.pipeline;

import java.util.*;

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

  public static Set<String> tagsToIgnore = new HashSet<String>(Arrays.asList("X", "_"));

  private static void testTagSet4(String[] lexParsers,
                                  String[] maxentTaggers,
                                  String[] srParsers,
                                  String[] nnDepParsers) {
    // Choose a reference point to work from. We choose the first maxent tagger, since there must be one of those.
    String refTaggerName = maxentTaggers[0];
    MaxentTagger refTagger = new MaxentTagger(refTaggerName);
    Set<String> tagSet = refTagger.tagSet();
    tagSet.removeAll(tagsToIgnore);

    for (String name : maxentTaggers) {
      MaxentTagger tagger = new MaxentTagger(name);
      Set<String> maxentTagSet = new HashSet<>(tagger.tagSet());
      maxentTagSet.removeAll(tagsToIgnore);
      assertEquals(refTaggerName + " vs. " + name + " tag set mismatch:\n" +
                   "left - right: " + Sets.diff(tagSet, maxentTagSet) +
                   "; right - left: " + Sets.diff(maxentTagSet, tagSet) + "\n",
                   tagSet, maxentTagSet);
    }
    for (String name : lexParsers) {
      LexicalizedParser lp = LexicalizedParser.loadModel(name);
      Set<String> lexParserTagSet =
          new HashSet<>(lp.getLexicon().tagSet(lp.treebankLanguagePack().getBasicCategoryFunction()));
      lexParserTagSet.removeAll(tagsToIgnore);
      assertEquals(refTaggerName + " vs. " + name + " tag set mismatch:\n" +
                   "left - right: " + Sets.diff(tagSet, lexParserTagSet) +
                   "; right - left: " + Sets.diff(lexParserTagSet, tagSet) + "\n",
                   tagSet, lexParserTagSet);
    }

    for (String name : srParsers) {
      ShiftReduceParser srp = ShiftReduceParser.loadModel(name);
      Set<String> srParserTagSet = new HashSet<>(srp.tagSet());
      srParserTagSet.removeAll(tagsToIgnore);
      assertEquals(refTaggerName + " vs. " + name + " tag set mismatch:\n" +
                   "left - right: " + Sets.diff(tagSet, srParserTagSet) +
                   "; right - left: " + Sets.diff(srParserTagSet, tagSet) + "\n",
                   tagSet, srParserTagSet);
    }

    for (String name : nnDepParsers) {
      DependencyParser dp = DependencyParser.loadFromModelFile(name);
      Set<String> nnDepParserTagSet = new HashSet<String>(dp.getPosSet());
      nnDepParserTagSet.removeAll(tagsToIgnore);
      assertEquals(refTaggerName + " vs. " + name + " tag set mismatch:\n" +
                   "left - right: " + Sets.diff(tagSet, nnDepParserTagSet) +
                   "; right - left: " + Sets.diff(nnDepParserTagSet, tagSet) + "\n",
                   tagSet, nnDepParserTagSet);
    }

  }


  private static final String[] englishTaggers = {
    "edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger",
    "edu/stanford/nlp/models/pos-tagger/english-bidirectional-distsim.tagger",
    "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger",
  };

  private static final String[] englishParsers = {
    "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz",
    "edu/stanford/nlp/models/lexparser/englishPCFG.caseless.ser.gz",
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
      "edu/stanford/nlp/models/pos-tagger/german-ud.tagger"
  };

  private static final String[] germanParsers = {
    "edu/stanford/nlp/models/lexparser/germanPCFG.ser.gz",
  };

  private static final String[] germanSrParsers = {
    "edu/stanford/nlp/models/srparser/germanSR.ser.gz",
  };


  private static final String[] germanNnParsers = {
    // This one now uses fine-grained STTS tag set not UD tags, it appears!
    // But it doesn't quite match because UD lacks POS tags [PPOSS, VMPP] that tagger produces. Just lacking in training data?!?
    "edu/stanford/nlp/models/parser/nndep/UD_German.gz"
  };

  public void testGermanTagSet() {
    testTagSet4(germanParsers, germanTaggers, germanSrParsers, germanNnParsers);
  }

  private static final String[] germanUDTaggers = {
    "edu/stanford/nlp/models/pos-tagger/german-ud.tagger",
  };

  private static final String[] germanUDParsers = {
  };

  private static final String[] germanUDSrParsers = {
  };

  private static final String[] germanUDNnParsers = {
  };

  public void testGermanUDTagSet() {
    testTagSet4(germanUDParsers, germanUDTaggers, germanUDSrParsers, germanUDNnParsers);
  }


  private static final String[] chineseTaggers = {
    "edu/stanford/nlp/models/pos-tagger/chinese-distsim.tagger",
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
      "edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger"
  };

  private static final String[] spanishParsers = {
    "edu/stanford/nlp/models/lexparser/spanishPCFG.ser.gz"
  };

  private static final String[] spanishSrParsers = {
      "edu/stanford/nlp/models/srparser/spanishSR.ser.gz",
      "edu/stanford/nlp/models/srparser/spanishSR.beam.ser.gz"
  };

  private static final String[] spanishNnParsers = {
      "edu/stanford/nlp/models/parser/nndep/UD_Spanish.gz"
  };

  public void testSpanishTagSet() {
    testTagSet4(spanishParsers, spanishTaggers, spanishSrParsers, spanishNnParsers);
  }


  private static final String[] frenchTaggers = {
      "edu/stanford/nlp/models/pos-tagger/french-ud.tagger"
  };

  private static final String[] frenchParsers = {
  };

  private static final String[] frenchSrParsers = {
      "edu/stanford/nlp/models/srparser/frenchSR.ser.gz",
      "edu/stanford/nlp/models/srparser/frenchSR.beam.ser.gz"
  };

  private static final String[] frenchNnParsers = {
  };

  public void testFrenchTagSet() {
    testTagSet4(frenchParsers, frenchTaggers, frenchSrParsers, frenchNnParsers);
  }


  private static final String[] arabicTaggers = {
    "edu/stanford/nlp/models/pos-tagger/arabic-train.tagger",
    "edu/stanford/nlp/models/pos-tagger/arabic.tagger",
  };

  private static final String[] arabicParsers = {
    "edu/stanford/nlp/models/lexparser/arabicFactored.ser.gz",
  };

  private static final String[] arabicSrParsers = {
    "edu/stanford/nlp/models/srparser/arabicSR.ser.gz",
  };

  private static final String[] arabicNnParsers = {
  };

  public void testArabicTagSet() {
    testTagSet4(arabicParsers, arabicTaggers, arabicSrParsers, arabicNnParsers);
  }


  // todo: Add other languages nndep parsers sometime

}
