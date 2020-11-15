package edu.stanford.nlp.patterns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

import edu.stanford.nlp.io.FileSystem;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.patterns.DataInstance;
import edu.stanford.nlp.patterns.GetPatternsFromDataMultiClass;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.patterns.surface.SurfacePattern;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.pipeline.MorphaAnnotator;
import edu.stanford.nlp.pipeline.NERCombinerAnnotator;
import edu.stanford.nlp.pipeline.POSTaggerAnnotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator;
import edu.stanford.nlp.pipeline.WordsToSentencesAnnotator;
import edu.stanford.nlp.pipeline.TokenizerAnnotator.TokenizerType;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.CoreMap;

/**
 * Tests the occurence of CoreNLP issue #1024.
 * <br>
 * The problem discovered was that some of the possible subdivisions
 * of threads and test items result in nothing being labeled.
 *
 * @author aggarcia3
 * @author John Bauer
 */
public class PatternsSimpleThreadedITest {
  private static Properties nerAnnotatorProperties = null;
  private static AnnotationPipeline nlpPipeline = null;

  @BeforeClass
  public static void setUp() {
    nlpPipeline = new AnnotationPipeline();
    // We assume the input is already tokenized, so we use a cheap whitespace tokenizer.
    // The original code uses this property for the tokenizer:
    // props.setProperty("tokenize.options", "ptb3Escaping=false,normalizeParentheses=false,escapeForwardSlashAsterisk=false");
    nlpPipeline.addAnnotator(new TokenizerAnnotator(false, TokenizerType.Whitespace));
    nlpPipeline.addAnnotator(new WordsToSentencesAnnotator(false));
    nlpPipeline.addAnnotator(new POSTaggerAnnotator());
    nlpPipeline.addAnnotator(new MorphaAnnotator(false));
    Properties nerAnnotatorProperties = new Properties();
    nerAnnotatorProperties.setProperty("ner.useSUTime", Boolean.toString(false));
    nerAnnotatorProperties.setProperty("ner.applyFineGrained", Boolean.toString(false));
    //nerAnnotatorProperties.setProperty("ner.fine.regexner.mapping", spiedProperties.getProperty("fineGrainedRegexnerMapping"));
    try {
      nlpPipeline.addAnnotator(new NERCombinerAnnotator(nerAnnotatorProperties));
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  @Test
  public void testSingleThread() {
    runTest("1");
  }

  @Test
  public void testTwoThreads() {
    runTest("2");
  }

  @Test
  public void testThreeThreads() {
    runTest("3");
  }

  @Test
  public void testFourThreads() {
    runTest("4");
  }

  void runTest(String numThreads) {
    Properties spiedProperties = new Properties();
    final Path tempPath;
    try {
      tempPath = Files.createTempDirectory(null);
      spiedProperties.load(new InputStreamReader(new FileInputStream(new File("data/edu/stanford/nlp/patterns/patterns_itest.properties")),
                                                 StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }

    Path outputPath = Paths.get(tempPath.toString(), "output");
    Path modelPath = Paths.get(tempPath.toString(), "model");
    Path docsentsPath = Paths.get(tempPath.toString(), "docsents.ser");
    System.out.println("Test " + numThreads + " writing to " + tempPath);

    spiedProperties.setProperty("seedWordsFiles", "VACCINE_PREVENTABLE_DISEASE,data/edu/stanford/nlp/patterns/VACCINE_PREVENTABLE_DISEASE.txt");
    spiedProperties.setProperty("file", docsentsPath.toString()); // We generate this file below
    spiedProperties.setProperty("fileFormat", "ser");
    spiedProperties.setProperty("outDir", outputPath.toString());
    spiedProperties.setProperty("patternsWordsDir", modelPath.toString());
    spiedProperties.setProperty("loadSavedPatternsWordsDir", Boolean.toString(false));

    spiedProperties.setProperty("numThreads", numThreads);
    // Run the pipeline on an input document
    // Algorithm based on
    // https://github.com/stanfordnlp/CoreNLP/blob/a9a4c2d75b177790a24c0f46188810668d044cd8/src/edu/stanford/nlp/patterns/GetPatternsFromDataMultiClass.java#L702
    // useTargetParserParentRestriction is false
    final Annotation document = new Annotation("** If you survive measles without complications ** I love these . " +
                                               "Why would n't you survive without complications , Immunologist ?");
    nlpPipeline.annotate(document);

    // Convert annotation to map to serialize, similarly to the original code algorithm
    int i = 0;
    final Map<String, DataInstance> sentenceMap = new HashMap<>();
    for (final CoreMap sentence : document.get(SentencesAnnotation.class)) {
      sentenceMap.put(Integer.toString(i++),
                      DataInstance.getNewInstance(PatternFactory.PatternType.SURFACE, sentence));
    }

    try (final ObjectOutputStream sentenceMapStream = new ObjectOutputStream(new FileOutputStream(docsentsPath.toString()))) {
      sentenceMapStream.writeObject(sentenceMap);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
    
    try {
      GetPatternsFromDataMultiClass.<SurfacePattern>run(spiedProperties);
    } catch (Exception e) {
      System.out.println("Test " + numThreads + " FAILED");
      System.out.println("  Intermediate files in " + tempPath);
      throw new RuntimeException(e);
    }

    System.out.println("Cleaning up temp files from " + tempPath);
    FileSystem.deleteDir(tempPath.toFile());
  };
}
