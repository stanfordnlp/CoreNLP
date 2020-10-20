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

  private static final boolean VERBOSE = false;

  private static void runSentence(StanfordCoreNLP pipeline, String sentence, String[] ans) {
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
        if (VERBOSE) {
          if (!w.equals(tcw)) {
            System.err.print("\"" + w + "\" true cased to \"" + tcw + "\" in context:");
            for (int j = Math.max(0, i - 2); j < Math.min(words.size(), i + 2); j++) {
              System.err.print(" " + words.get(j).word());
            }
            System.err.println();
          }
        }
        assertEquals("Error in truecasing", ans[i], tcw);
      }
    }
  }


  public void testTrueCaseAnnotator() {
    // run an annotation through the pipeline
    String text1 = "HEATHER BROWN WAS LEAD WOMAN AT DUKE UNIVERSITY.";
    String text2 = "heather brown was lead woman at duke university.";
    String text3 = "Heather Brown was lead woman at Duke University.";
    String[] ans1 = { "Heather", "Brown", "was", "lead", "woman", "at", "Duke", "University", "." };

    String text4 = "\"GOOD MORNING AMERICA FROM MCVEY!\"";
    String text5 = "\"good morning america from mcvey!\"";
    String text6 = "\"Good Morning America From McVey!\"";
    String[] ans4 = { "\"", "Good", "Morning", "America", "from", "McVey", "!", "\"" };

    Properties props = PropertiesUtils.asProperties("annotators", "tokenize, ssplit, truecase");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    runSentence(pipeline, text1, ans1);
    runSentence(pipeline, text2, ans1);
    runSentence(pipeline, text3, ans1);

    runSentence(pipeline, text4, ans4);
    runSentence(pipeline, text5, ans4);
    runSentence(pipeline, text6, ans4);
  }

}
