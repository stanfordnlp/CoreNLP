package edu.stanford.nlp.parser.nndep;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

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
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;



/**
 *  @author Christopher Manning
 *  @author Jon Gauthier
 */
public class DependencyParserITest extends TestCase {

  private static final double EnglishSdLas = 89.55236534222574; // was until Sept 2016: 89.46997859637266;

  /**
   * Test that the NN dependency parser performance doesn't change.
   */
  public void testDependencyParserEnglishSD() {
    DependencyParser parser = new DependencyParser();
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2014-10-26/PTB_Stanford_params.txt.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/PTB/Stanford_3_3_0/dev.conll", null);
    assertEquals(String.format("English SD LAS should be %.2f but was %.2f",
            EnglishSdLas, las), EnglishSdLas, las, 1e-4);
  }

  // Lower because we're evaluating on PTB + extraDevTest, not just PTB
  private static final double EnglishUdLas = 88.78652574464478; // was until Sept 2016: 88.72648417258083;

  /**
   * Test that the NN dependency parser performance doesn't change.
   */
  public void testDependencyParserEnglishUD() {
    DependencyParser parser = new DependencyParser();
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2015-04-16/english_UD.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/UD-converted/dev.conll", null);
    assertEquals(String.format("English UD LAS should be %.2f but was %.2f",
        EnglishUdLas, las), EnglishUdLas, las, 1e-4);
  }

  private static final double EnglishConll2008Las = 90.97206578058122;

  /**
   * Test that the NN dependency parser performance doesn't change.
   */
  public void testDependencyParserEnglishCoNLL2008() {
    DependencyParser parser = new DependencyParser();
    parser.loadModelFile("/u/nlp/data/depparser/nn/distrib-2014-10-26/PTB_CoNLL_params.txt.gz");
    double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/PTB/CoNLL/dev.conll", null);
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
    // [was but now no such file:] double las = parser.testCoNLL("/u/nlp/data/depparser/nn/data/dependency_treebanks/CTB/ctb5.1/dev.gold.conll", null);
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

    SemanticGraph ccProcessed =
            document.get(CoreAnnotations.SentencesAnnotation.class).get(0)
                                .get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class);
    Collection<TypedDependency> dependencies = ccProcessed.typedDependencies();

    GrammaticalRelation expected = UniversalEnglishGrammaticalRelations.getConj("and");
    assertTrue(dependencies.stream().map(TypedDependency::reln).collect(Collectors.toList()).contains(expected));
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
