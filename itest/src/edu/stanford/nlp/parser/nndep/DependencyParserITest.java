package edu.stanford.nlp.parser.nndep;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import junit.framework.TestCase;

import edu.stanford.nlp.util.StringUtils;
import org.hamcrest.CoreMatchers;
import org.junit.matchers.JUnitMatchers;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertThat;
import static org.junit.matchers.JUnitMatchers.hasItem;


/**
 *  @author Christopher Manning
 *  @author Jon Gauthier
 */
public class DependencyParserITest extends TestCase {

  private static final double EnglishSdLas = 89.58544553340093;

  /**
   * Test that the NN dependency parser performance doesn't change.
   */
  public void testDependencyParserEnglishSD() {
    DependencyParser parser = new DependencyParser();
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2014-10-26/PTB_Stanford_params.txt.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/PTB_Stanford/3.3.0/dev.conll", null);
    assertEquals(String.format("English SD LAS should be %.2f but was %.2f",
            EnglishSdLas, las), EnglishSdLas, las, 1e-4);
  }

  private static final double EnglishConll2008Las = 90.97206578058122;

  /**
   * Test that the NN dependency parser performance doesn't change.
   */
  public void testDependencyParserEnglishCoNLL2008() {
    DependencyParser parser = new DependencyParser();
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2014-10-26/PTB_CoNLL_params.txt.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/PTB_CoNLL/dev.conll", null);
    assertEquals(String.format("English CoNLL2008 LAS should be %.2f but was %.2f",
            EnglishConll2008Las, las), EnglishConll2008Las, las, 1e-4);
  }

  private static final double ChineseConllxGoldTagsLas = 82.42855503270974;

  /**
   * Test that the NN dependency parser performance doesn't change.
   */
  public void testDependencyParserChineseCoNLLX() {
    Properties props = StringUtils.stringToProperties("language=Chinese");
    DependencyParser parser = new DependencyParser(props);
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2014-10-26/CTB_CoNLL_params.txt.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/CTB/dev.gold.conll", null);
    assertEquals(String.format("Chinese CoNLLX gold tags LAS should be %.2f but was %.2f",
            ChineseConllxGoldTagsLas, las), ChineseConllxGoldTagsLas, las, 1e-4);
  }

  /**
   * Test that postprocessing like CC-processing can handle the parser
   * output properly
   */
  public void testCCProcess() {
    Properties props = PropertiesUtils.fromString("annotators=tokenize,ssplit,pos,depparse");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    String text = "Chris and John went to the store.";
    Annotation document = new Annotation(text);
    pipeline.annotate(document);

    SemanticGraph ccProcessed = document.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                                        .get(
                                            SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
    Collection<TypedDependency> dependencies = ccProcessed.typedDependencies();

    GrammaticalRelation expected = EnglishGrammaticalRelations.getConj("and");
    assertThat(dependencies.stream().map(d -> d.reln()).collect(toList()),
        hasItem(expected));
  }

  /**
   * Test that Java serialization works properly.
   */
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
