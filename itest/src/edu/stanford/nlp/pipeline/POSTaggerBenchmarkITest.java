package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.tagger.maxent.TestClassifier;
import edu.stanford.nlp.util.TestPaths;


import junit.framework.TestCase;

import java.io.*;

public class POSTaggerBenchmarkITest extends TestCase {

  public void testEnglishWSJDevPOS() throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger",
            String.format("tagSeparator=_,%s/pos-tagger/models-4.0.0/data/wsj/dev/wsj-dev.txt", TestPaths.testHome()),
            96.76);
  }

  public void testEnglishWSJTestPOS() throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger",
            String.format("tagSeparator=_,%s/pos-tagger/models-4.0.0/data/wsj/test/wsj-test.txt", TestPaths.testHome()),
            97.21);
  }

  public void testEnglishBiDirectionalWSJDevPOS() throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/english-bidirectional-distsim.tagger",
            String.format("tagSeparator=_,%s/pos-tagger/models-4.0.0/data/wsj/dev/wsj-dev.txt", TestPaths.testHome()),
            97.21);
  }

  public void testEnglishBiDirectionalWSJTestPOS() throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/english-bidirectional-distsim.tagger",
            String.format("tagSeparator=_,%s/pos-tagger/models-4.0.0/data/wsj/test/wsj-test.txt", TestPaths.testHome()),
            97.55);
  }

  public void testEnglishCaselessWSJDevPOS() throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger",
            String.format("tagSeparator=_,%s/pos-tagger/models-4.0.0/data/wsj-caseless/dev/wsj-caseless-dev.txt", TestPaths.testHome()),
            95.81);
  }

  public void testEnglishCaselessWSJTestPOS() throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger",
            String.format("tagSeparator=_,%s/pos-tagger/models-4.0.0/data/wsj-caseless/test/wsj-caseless-test.txt", TestPaths.testHome()),
            96.29);
  }

  public void testChineseTestPOS() throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/chinese-distsim.tagger",
            String.format("format=TSV,wordColumn=0,tagColumn=1,%s/pos-tagger/chinese/ctb7.test.tsv", TestPaths.testHome()),
            97.46);
  }

  public void testFrenchDevPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/french-ud.tagger",
            String.format("format=TSV,wordColumn=1,tagColumn=3,%s/depparser/nn/models-4.0.0/data/clean/fr_gsd-ud-dev.conllu.clean", TestPaths.testHome()),
            96.96);
  }

  public void testFrenchTestPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/french-ud.tagger",
            String.format("format=TSV,wordColumn=1,tagColumn=3,%s/depparser/nn/models-4.0.0/data/clean/fr_gsd-ud-test.conllu.clean", TestPaths.testHome()),
            96.44);
  }

  public void testGermanDevPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/german-ud.tagger",
            String.format("format=TSV,wordColumn=1,tagColumn=3,%s/depparser/nn/models-4.0.0/data/clean/de_gsd-ud-dev.conllu.clean", TestPaths.testHome()),
            93.07);
  }

  public void testGermanTestPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/german-ud.tagger",
            String.format("format=TSV,wordColumn=1,tagColumn=3,%s/depparser/nn/models-4.0.0/data/clean/de_gsd-ud-test.conllu.clean", TestPaths.testHome()),
            94.5);
  }

  public void testSpanishDevPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger",
            String.format("format=TSV,wordColumn=1,tagColumn=3,%s/depparser/nn/models-4.0.0/data/clean/es_ancora-ud-dev.conllu.clean", TestPaths.testHome()),
            97.77);
  }

  public void testSpanishTestPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/spanish-ud.tagger",
            String.format("format=TSV,wordColumn=1,tagColumn=3,%s/depparser/nn/models-4.0.0/data/clean/es_ancora-ud-test.conllu.clean", TestPaths.testHome()),
            97.76);
  }

  public void runPOSTest(String modelPath, String dataPath, double expectedTokenAccuracy) throws IOException {
    String argsString = String.format("-model %s -testFile %s -verboseResults false", modelPath, dataPath);
    TaggerConfig config = new TaggerConfig(argsString.split(" "));
    MaxentTagger tagger = new MaxentTagger(config.getModel(), config);
    TestClassifier testClassifier = new TestClassifier(tagger);
    System.err.println("---");
    System.err.println(modelPath);
    System.err.println(dataPath);
    System.err.println(testClassifier.tagAccuracy());
    System.err.println(expectedTokenAccuracy);
    assertTrue("Expected accuracy " + expectedTokenAccuracy + " but got " + testClassifier.tagAccuracy(),
               testClassifier.tagAccuracy() >= expectedTokenAccuracy);
  }

}
