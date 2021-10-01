package edu.stanford.nlp.parser.nndep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.Test;

import junit.framework.TestCase;


import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalRelations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TestPaths;


/**
 *  @author Christopher Manning
 *  @author Jon Gauthier
 */
public class DependencyParserITest {

  public void runDepparseTest(Properties props, double uasThreshold, double lasThreshold) {
    DependencyParser parser = new DependencyParser(props);
    parser.loadModelFile(props.getProperty("model"));
    Pair<Double, Double> scores =
        parser.testCoNLLReturnScores(props.getProperty("testFile"), props.getProperty("outFile"));
    double uas = scores.first;
    double las = scores.second;
    assertTrue(uas >= uasThreshold);
    assertTrue(las >= lasThreshold);
    // clean up after test
    try {
      Files.deleteIfExists(Paths.get("tmp.conll"));
    } catch (IOException e) {
      System.err.println("Error with removing tmp.conll");
    }
  }

  @Test
  public void testEnglishOnWSJDev() {
    Properties props = new Properties();
    props.put("testFile", String.format("%s/depparser/nn/benchmark/wsj-dev.conllu", TestPaths.testHome()));
    props.put("model", "edu/stanford/nlp/models/parser/nndep/english_UD.gz");
    props.put("outFile", "tmp.conll");
    runDepparseTest(props,93.4, 91.9);
  }

  @Test
  public void testEnglishOnWSJTest() {
    Properties props = new Properties();
    props.put("testFile", String.format("%s/depparser/nn/benchmark/wsj-test.conllu", TestPaths.testHome()));
    props.put("model", "edu/stanford/nlp/models/parser/nndep/english_UD.gz");
    props.put("outFile", "tmp.conll");
    runDepparseTest(props, 93.4, 92.09);
  }


  /**
   * Test that postprocessing like CC-processing can handle the parser
   * output properly
   */
  @Test
  public void testCCProcess() {
    Properties props = PropertiesUtils.fromString("annotators=tokenize,ssplit,pos,depparse");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    String text = "Chris and John went to the store.";
    Annotation document = new Annotation(text);
    pipeline.annotate(document);

    SemanticGraph enhancedPlusPlus =
            document.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                                .get(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class);
    Collection<TypedDependency> dependencies = enhancedPlusPlus.typedDependencies();
    
    GrammaticalRelation expected = UniversalEnglishGrammaticalRelations.getConj("and");
    assertTrue(dependencies.stream().map(TypedDependency::reln).collect(Collectors.toList()).contains(expected));
  }

  /**
   * Test that Java serialization works properly.
   */
  @Test
  public void testSerializationAnnotation() throws IOException, ClassNotFoundException {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,depparse");
    String text = "Barack Obama, a Yale professor, is president.";
    Annotation document = new Annotation(text);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);

    // Serialization should not bork.
    File tempfile = IOUtils.writeObjectToTempFile(document.get(CoreAnnotations.SentencesAnnotation.class), "temp");

    // Deserialization should not bork.
    List<CoreMap> readSentences = IOUtils.readObjectFromFile(tempfile);

    // Make sure we didn't lose any information
    assertEquals(document.get(CoreAnnotations.SentencesAnnotation.class), readSentences);
  }

}
