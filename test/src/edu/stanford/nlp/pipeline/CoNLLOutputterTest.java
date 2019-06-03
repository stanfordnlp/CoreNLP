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
    String expected = "1\tCoNLL\t_\t_\t_\t_\t_" + System.lineSeparator() +
        "2\tis\t_\t_\t_\t_\t_" + System.lineSeparator() +
        "3\tneat\t_\t_\t_\t_\t_" + System.lineSeparator() +
        "4\t.\t_\t_\t_\t_\t_" + System.lineSeparator() +
        System.lineSeparator() +
        "1\tBetter\t_\t_\t_\t_\t_" + System.lineSeparator() +
        "2\tthan\t_\t_\t_\t_\t_" + System.lineSeparator() +
        "3\tXML\t_\t_\t_\t_\t_" + System.lineSeparator() +
        "4\t.\t_\t_\t_\t_\t_" + System.lineSeparator() +
        System.lineSeparator();
    assertEquals(expected, actual);
  }

  public void testCustomSimpleSentence() throws IOException {
    Annotation ann = new Annotation("CoNLL is neat. Better than XML.");
    String outputKeys = "word,pos";
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize, ssplit",
            "output.columns", outputKeys));
    pipeline.annotate(ann);
    String actual = new CoNLLOutputter().print(ann, pipeline);
    String expected = "CoNLL\t_" + System.lineSeparator() +
        "is\t_" + System.lineSeparator() +
        "neat\t_" + System.lineSeparator() +
        ".\t_" + System.lineSeparator() +
        System.lineSeparator() +
        "Better\t_" + System.lineSeparator() +
        "than\t_" + System.lineSeparator() +
        "XML\t_" + System.lineSeparator() +
        ".\t_" + System.lineSeparator() +
        System.lineSeparator();
    assertEquals(expected, actual);
  }

}
