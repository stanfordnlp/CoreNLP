package edu.stanford.nlp.pipeline;

import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import junit.framework.TestCase;
import org.junit.Assert;

/**
 * @author Christopher Manning
 */
public class TrueCaseAnnotatorITest extends TestCase {

  private void runSentence(StanfordCoreNLP pipeline, String sentence, String[] ans) {
    Annotation document = new Annotation(sentence);
    pipeline.annotate(document);

    // check that tokens are present
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(tokens);
    Assert.assertEquals("Wrong number of tokens: " + tokens + " vs. " + ans.length, ans.length, tokens.size());

    // check that sentences are present
    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
    Assert.assertNotNull(sentences);
    Assert.assertEquals("Wrong number of sentences", 1, sentences.size());

    for (CoreMap sent: document.get(CoreAnnotations.SentencesAnnotation.class)){
      List<? extends CoreLabel> words = sent.get(CoreAnnotations.TokensAnnotation.class);
      for(int i = 0; i < words.size(); i ++) {
        String w = words.get(i).word();
        String tcw = words.get(i).get(CoreAnnotations.TrueCaseTextAnnotation.class);
        assertEquals("Error in truecasing", ans[i], tcw);
        if (! w.equals(tcw)){
          System.err.print("\"" + w + "\" true cased to \"" + tcw + "\" in context:");
          for(int j = Math.max(0, i - 2); j < Math.min(words.size(), i + 2); j ++){
            System.err.print(" " + words.get(j).word());
          }
          System.err.println();
        }
      }
    }
  }


  public void testTrueCaseAnnotator() {
    // run an annotation through the pipeline
    String text1 = "HEATHER BROWN WAS LEAD WOMAN AT DUKE UNIVERSITY.";
    String[] ans1 = { "Heather", "Brown", "was", "lead", "woman", "at", "Duke", "University", "." };

    String text2 = "heather brown was lead woman at the duke university.";
    String[] ans2 = { "Heather", "Brown", "was", "lead", "woman", "at", "the", "Duke", "University", "." };
    Annotation document2 = new Annotation(text2);

    Properties props = PropertiesUtils.asProperties("annotators", "tokenize, ssplit, pos, lemma, ner, truecase");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    runSentence(pipeline, text1, ans1);
    runSentence(pipeline, text2, ans2);
  }

}
