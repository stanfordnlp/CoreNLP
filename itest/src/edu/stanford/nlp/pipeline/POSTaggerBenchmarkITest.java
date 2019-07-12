package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.tagger.maxent.TaggerConfig;
import edu.stanford.nlp.tagger.maxent.TestClassifier;
import junit.framework.TestCase;

import java.io.*;

public class POSTaggerBenchmarkITest extends TestCase {

  public void testFrenchDevPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/french/french-ud.tagger",
            "format=TSV,wordColumn=1,tagColumn=3,/u/nlp/data/depparser/nn/models-4.0.0/data/clean/fr_gsd-ud-dev.conllu.clean",
            96.96);
  }

  public void testFrenchTestPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/french/french-ud.tagger",
            "format=TSV,wordColumn=1,tagColumn=3,/u/nlp/data/depparser/nn/models-4.0.0/data/clean/fr_gsd-ud-test.conllu.clean",
            96.44);
  }

  public void testGermanDevPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/french/french-ud.tagger",
            "format=TSV,wordColumn=1,tagColumn=3,/u/nlp/data/depparser/nn/models-4.0.0/data/clean/de_gsd-ud-dev.conllu.clean",
            93.07);
  }

  public void testGermanTestPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/french/french-ud.tagger",
            "format=TSV,wordColumn=1,tagColumn=3,/u/nlp/data/depparser/nn/models-4.0.0/data/clean/de_gsd-ud-test.conllu.clean",
            92.84);
  }

  public void testSpanishDevPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/french/french-ud.tagger",
            "format=TSV,wordColumn=1,tagColumn=3,/u/nlp/data/depparser/nn/models-4.0.0/data/clean/es_ancora-ud-dev.conllu.clean",
            97.77);
  }

  public void testSpanishTestPOS()  throws IOException {
    runPOSTest("edu/stanford/nlp/models/pos-tagger/french/french-ud.tagger",
            "format=TSV,wordColumn=1,tagColumn=3,/u/nlp/data/depparser/nn/models-4.0.0/data/clean/es_ancora-ud-test.conllu.clean",
            97.76);
  }

  public void runPOSTest(String modelPath, String dataPath, double expectedTokenAccuracy) throws IOException {
    String argsString = String.format("-model %s -testFile %s", modelPath, dataPath);
    TaggerConfig config = new TaggerConfig(argsString.split(" "));
    MaxentTagger tagger = new MaxentTagger(config.getModel(), config);
    TestClassifier testClassifier = new TestClassifier(tagger);
    System.err.println(testClassifier.tagAccuracy());
    assertTrue(testClassifier.tagAccuracy() >= expectedTokenAccuracy);
  }

}
