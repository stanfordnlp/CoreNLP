package edu.stanford.nlp.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.coref.data.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.Constants;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;

public class DeterministicCorefAnnotatorITest {

  private static AnnotationPipeline pipeline;

  @Before
  public void setUp() throws Exception {
    synchronized(DeterministicCorefAnnotatorITest.class) {
      pipeline = new AnnotationPipeline();
      pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
      pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
      pipeline.addAnnotator(new POSTaggerAnnotator(false));
      pipeline.addAnnotator(new MorphaAnnotator(false));
      pipeline.addAnnotator(new NERCombinerAnnotator(false));
      pipeline.addAnnotator(new ParserAnnotator(false, -1));

      Properties corefProps = new Properties();
      corefProps.setProperty(Constants.DEMONYM_PROP, DefaultPaths.DEFAULT_DCOREF_DEMONYM);
      corefProps.setProperty(Constants.ANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_ANIMATE);
      corefProps.setProperty(Constants.INANIMATE_PROP, DefaultPaths.DEFAULT_DCOREF_INANIMATE);
      pipeline.addAnnotator(new DeterministicCorefAnnotator(corefProps));
    }
  }

  @Test
  public void testDeterministicCorefAnnotator() throws Exception {
    // create annotation with text
    String text = "Dan Ramage is working for\nMicrosoft. He's in Seattle!\nAt least, he used to be.  Ed is not in Seattle.";
    Annotation document = new Annotation(text);

    // annotate text with pipeline
    pipeline.annotate(document);

    // test CorefGraphAnnotation
    Map<Integer, CorefChain> corefChains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    Assert.assertNotNull(corefChains);

    // test chainID = m.corefClusterID
    for (int chainID : corefChains.keySet()) {
      CorefChain c = corefChains.get(chainID);
      for (CorefMention m : c.getMentionsInTextualOrder()) {
        Assert.assertEquals(m.corefClusterID, chainID);
      }
    }

    // test CorefClusterIdAnnotation
    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
    CoreLabel ramageToken = sentences.get(0).get(CoreAnnotations.TokensAnnotation.class).get(1);
    CoreLabel heToken = sentences.get(1).get(CoreAnnotations.TokensAnnotation.class).get(0);
    Integer ramageClusterId = ramageToken.get(CorefCoreAnnotations.CorefClusterIdAnnotation.class);
    Assert.assertNotNull(ramageClusterId);
    Assert.assertSame(ramageClusterId, heToken.get(CorefCoreAnnotations.CorefClusterIdAnnotation.class));
  }

  /**
   * Tests named entities with exact string matches (also tests some more pronouns).
   * @throws Exception
   */
  @Test
  public void testSameString() throws Exception {
    // create annotation with text
    String text = "Your mom thinks she lives in Denver, but it's a big city.  She actually lives outside of Denver.";
    Annotation document = new Annotation(text);

    // annotate text with pipeline
    pipeline.annotate(document);

    // test CorefChainAnnotation
    Map<Integer, CorefChain> chains = document.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    Assert.assertNotNull(chains);

    // test CorefGraphAnnotation
    //    List<Pair<IntTuple, IntTuple>> graph = document.get(CorefCoreAnnotations.CorefGraphAnnotation.class);
    //    Assert.assertNotNull(graph);

    //    for( Pair<IntTuple, IntTuple> pair : graph ) {
    //      System.out.println("pair " + pair);
    //    }

    // test chainID = m.corefClusterID
    for (int chainID : chains.keySet()) {
      CorefChain c = chains.get(chainID);
      for (CorefMention m : c.getMentionsInTextualOrder()) {
        Assert.assertEquals(m.corefClusterID, chainID);
      }
    }

    // test CorefClusterIdAnnotation
    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
    CoreLabel yourMomsToken = sentences.get(0).get(CoreAnnotations.TokensAnnotation.class).get(1);
    CoreLabel sheToken1 = sentences.get(0).get(CoreAnnotations.TokensAnnotation.class).get(3);
    CoreLabel sheToken2 = sentences.get(1).get(CoreAnnotations.TokensAnnotation.class).get(0);
    CoreLabel denverToken1 = sentences.get(0).get(CoreAnnotations.TokensAnnotation.class).get(6);
    CoreLabel denverToken2 = sentences.get(1).get(CoreAnnotations.TokensAnnotation.class).get(5);

    Integer yourMomsClusterId = yourMomsToken.get(CorefCoreAnnotations.CorefClusterIdAnnotation.class);
    Integer she1ClusterId = sheToken1.get(CorefCoreAnnotations.CorefClusterIdAnnotation.class);
    Integer she2ClusterId = sheToken2.get(CorefCoreAnnotations.CorefClusterIdAnnotation.class);
    Integer denver1ClusterId = denverToken1.get(CorefCoreAnnotations.CorefClusterIdAnnotation.class);
    Integer denver2ClusterId = denverToken2.get(CorefCoreAnnotations.CorefClusterIdAnnotation.class);
    Assert.assertNotNull(yourMomsClusterId);
    Assert.assertNotNull(she1ClusterId);
    Assert.assertNotNull(she2ClusterId);
    Assert.assertNotNull(denver1ClusterId);
    Assert.assertNotNull(denver2ClusterId);
    Assert.assertSame(yourMomsClusterId, she1ClusterId);
    Assert.assertSame(yourMomsClusterId, she2ClusterId);
    Assert.assertSame(denver1ClusterId, denver2ClusterId);
    Assert.assertNotSame(yourMomsClusterId, denver1ClusterId);

    // test CorefClusterAnnotation
    //    Assert.assertEquals(yourMomsToken.get(CorefCoreAnnotations.CorefClusterAnnotation.class), sheToken1.get(CorefCoreAnnotations.CorefClusterAnnotation.class));
    //    Assert.assertEquals(yourMomsToken.get(CorefCoreAnnotations.CorefClusterAnnotation.class), sheToken2.get(CorefCoreAnnotations.CorefClusterAnnotation.class));
    //    Assert.assertEquals(denverToken1.get(CorefCoreAnnotations.CorefClusterAnnotation.class), denverToken2.get(CorefCoreAnnotations.CorefClusterAnnotation.class));
  }

  public static void main(String[] args) throws Exception {
    DeterministicCorefAnnotatorITest itest = new DeterministicCorefAnnotatorITest();
    itest.testDeterministicCorefAnnotator();
  }

}
