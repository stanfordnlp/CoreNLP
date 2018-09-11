package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.PropertiesUtils;
import junit.framework.TestCase;

import java.io.IOException;

/**
 * A very basic test for {@link edu.stanford.nlp.pipeline.CoNLLOutputter}.
 *
 * @author Gabor Angeli
 */
public class CoNLLOutputterTest extends TestCase {

  public void testSimpleSentence() throws IOException {
    Annotation ann = new Annotation("CoNLL is neat. Better than XML.");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize, ssplit"));
    pipeline.annotate(ann);
    String actual = new CoNLLOutputter().print(ann);
    String expected = "1\tCoNLL\t_\t_\t_\t_\t_\n" +
        "2\tis\t_\t_\t_\t_\t_\n" +
        "3\tneat\t_\t_\t_\t_\t_\n" +
        "4\t.\t_\t_\t_\t_\t_\n" +
        '\n' +
        "1\tBetter\t_\t_\t_\t_\t_\n" +
        "2\tthan\t_\t_\t_\t_\t_\n" +
        "3\tXML\t_\t_\t_\t_\t_\n" +
        "4\t.\t_\t_\t_\t_\t_\n" +
        '\n';
    assertEquals(expected, actual);
  }

  public void testCustomSimpleSentence() throws IOException {
    Annotation ann = new Annotation("CoNLL is neat. Better than XML.");
    String outputKeys = "word,pos";
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize, ssplit",
            "output.columns", outputKeys));
    pipeline.annotate(ann);
    String actual = new CoNLLOutputter().print(ann, pipeline);
    String expected = "CoNLL\t_\n" +
        "is\t_\n" +
        "neat\t_\n" +
        ".\t_\n" +
        '\n' +
        "Better\t_\n" +
        "than\t_\n" +
        "XML\t_\n" +
        ".\t_\n" +
        '\n';
    assertEquals(expected, actual);
  }

}
