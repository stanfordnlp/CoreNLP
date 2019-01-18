package edu.stanford.nlp.pipeline;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Iterables;
import edu.stanford.nlp.util.StringUtils;

public class PipelineITest {

  @Test
  public void testPipeline() throws Exception {
    // create pipeline
    AnnotationPipeline pipeline = new AnnotationPipeline();
    pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
    pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
    pipeline.addAnnotator(new POSTaggerAnnotator(false));
    pipeline.addAnnotator(new MorphaAnnotator(false));
    pipeline.addAnnotator(new NERCombinerAnnotator(false));
    pipeline.addAnnotator(new ParserAnnotator(false, -1));
    //pipeline.addAnnotator(new CorefAnnotator(null, null, null, false));
    //pipeline.addAnnotator(new SRLAnnotator(false));

    // create annotation with text
    String text = "Dan Ramage is working for\nMicrosoft. He's in Seattle! \n";
    Annotation document = new Annotation(text);
    Assert.assertEquals(text, document.toString());
    Assert.assertEquals(text, document.get(CoreAnnotations.TextAnnotation.class));

    // annotate text with pipeline
    pipeline.annotate(document);

    // demonstrate typical usage
    for (CoreMap sentence: document.get(CoreAnnotations.SentencesAnnotation.class)) {

      // get the tree for the sentence
      Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);

      // get the tokens for the sentence and iterate over them
      for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {

        // get token attributes
        String tokenText = token.get(CoreAnnotations.TextAnnotation.class);
        String tokenPOS = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
        String tokenLemma = token.get(CoreAnnotations.LemmaAnnotation.class);
        String tokenNE = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

        // text, pos, lemma and name entity tag should be defined
        Assert.assertNotNull(tokenText);
        Assert.assertNotNull(tokenPOS);
        Assert.assertNotNull(tokenLemma);
        Assert.assertNotNull(tokenNE);
      }
      // tree should be defined
      Assert.assertNotNull(tree);
    }

    // get tokens
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    String tokensText = "Dan Ramage is working for Microsoft . He 's in Seattle !";
    Assert.assertNotNull(tokens);
    Assert.assertEquals(12, tokens.size());
    Assert.assertEquals(tokensText, join(tokens));
    Assert.assertEquals(0, (int)tokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    Assert.assertEquals(3, (int)tokens.get(0).get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    Assert.assertEquals("NNP", tokens.get(0).get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertEquals("VBZ", tokens.get(2).get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertEquals(".", tokens.get(11).get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertEquals("Ramage", tokens.get(1).get(CoreAnnotations.LemmaAnnotation.class));
    Assert.assertEquals("be", tokens.get(2).get(CoreAnnotations.LemmaAnnotation.class));
    Assert.assertEquals("PERSON", tokens.get(0).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    Assert.assertEquals("PERSON", tokens.get(1).get(CoreAnnotations.NamedEntityTagAnnotation.class));
    Assert.assertEquals("CITY", tokens.get(10).get(CoreAnnotations.NamedEntityTagAnnotation.class));

    // get sentences
    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
    Assert.assertNotNull(sentences);
    Assert.assertEquals(2, sentences.size());

    // sentence 1
    String text1 = "Dan Ramage is working for\nMicrosoft.";
    CoreMap sentence1 = sentences.get(0);
    Assert.assertEquals(text1, sentence1.toString());
    Assert.assertEquals(text1, sentence1.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertEquals(0, (int)sentence1.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    Assert.assertEquals(36, (int)sentence1.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    Assert.assertEquals(0, (int)sentence1.get(CoreAnnotations.TokenBeginAnnotation.class));
    Assert.assertEquals(7, (int)sentence1.get(CoreAnnotations.TokenEndAnnotation.class));

    // sentence 1 tree
    Tree tree1 = Tree.valueOf("(ROOT (S (NP (NNP Dan) (NNP Ramage)) (VP (VBZ is) " +
        "(VP (VBG working) (PP (IN for) (NP (NNP Microsoft))))) (. .)))");
    Assert.assertEquals(tree1, sentence1.get(TreeCoreAnnotations.TreeAnnotation.class));

    // sentence 1 tokens
    String tokenText1 = "Dan Ramage is working for Microsoft .";
    List<CoreLabel> tokens1 = sentence1.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(tokens1);
    Assert.assertEquals(7, tokens1.size());
    Assert.assertEquals(tokenText1, join(tokens1));
    Assert.assertEquals(4, (int)tokens1.get(1).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    Assert.assertEquals(10, (int)tokens1.get(1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    Assert.assertEquals("IN", tokens1.get(4).get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertEquals("NNP", tokens1.get(5).get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertEquals("work", tokens1.get(3).get(CoreAnnotations.LemmaAnnotation.class));
    Assert.assertEquals(".", tokens1.get(6).get(CoreAnnotations.LemmaAnnotation.class));
    Assert.assertEquals("ORGANIZATION", tokens1.get(5).get(CoreAnnotations.NamedEntityTagAnnotation.class));

    // sentence 2
    String text2 = "He's in Seattle!";
    CoreMap sentence2 = sentences.get(1);
    Assert.assertEquals(text2, sentence2.toString());
    Assert.assertEquals(text2, sentence2.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertEquals(37, (int)sentence2.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    Assert.assertEquals(53, (int)sentence2.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    Assert.assertEquals(7, (int)sentence2.get(CoreAnnotations.TokenBeginAnnotation.class));
    Assert.assertEquals(12, (int)sentence2.get(CoreAnnotations.TokenEndAnnotation.class));

    // sentence 2 tree (note error on Seattle, caused by part of speech tagger)
    Tree tree2 = Tree.valueOf("(ROOT (S (NP (PRP He)) (VP (VBZ 's) (PP (IN in) " +
        "(NP (NNP Seattle)))) (. !)))");
    Assert.assertEquals(tree2, sentence2.get(TreeCoreAnnotations.TreeAnnotation.class));

    // sentence 2 tokens
    String tokenText2 = "He 's in Seattle !";
    List<CoreLabel> tokens2 = sentence2.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(tokens2);
    Assert.assertEquals(5, tokens2.size());
    Assert.assertEquals(tokenText2, join(tokens2));
    Assert.assertEquals(39, (int)tokens2.get(1).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    Assert.assertEquals(41, (int)tokens2.get(1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    Assert.assertEquals("VBZ", tokens2.get(1).get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertEquals("be", tokens2.get(1).get(CoreAnnotations.LemmaAnnotation.class));
    Assert.assertEquals("CITY", tokens2.get(3).get(CoreAnnotations.NamedEntityTagAnnotation.class));
  }

  private static String join(List<CoreLabel> tokens) {
    return StringUtils.join(Iterables.transform(tokens, (CoreLabel token) -> token.get(CoreAnnotations.TextAnnotation.class)));
  }

}
