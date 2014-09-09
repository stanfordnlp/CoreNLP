package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Properties;

/**
 * Tests for the various annotation outputters which require the models to be loaded.
 *
 * @author Gabor Angeli
 */
public class AnnotationOutputterITest extends TestCase {

  static StanfordCoreNLP pipeline =
      new StanfordCoreNLP(new Properties() {{ setProperty("annotators", "tokenize, ssplit, pos, lemma, ner, parse"); }});

  public void testSimpleSentenceCoNLL() throws IOException {
    Annotation ann = new Annotation("The cat is fat. The dog is lazy.");
    pipeline.annotate(ann);
    String actual = new CoNLLOutputter().print(ann);
    String expected =
        "1\tThe\tthe\tDT\tO\t2\tdet\n" +
            "2\tcat\tcat\tNN\tO\t4\tnsubj\n" +
            "3\tis\tbe\tVBZ\tO\t4\tcop\n" +
            "4\tfat\tfat\tJJ\tO\t0\tROOT\n" +
            "5\t.\t.\t.\tO\t_\t_\n" +
            "\n" +
            "1\tThe\tthe\tDT\tO\t2\tdet\n" +
            "2\tdog\tdog\tNN\tO\t4\tnsubj\n" +
            "3\tis\tbe\tVBZ\tO\t4\tcop\n" +
            "4\tlazy\tlazy\tJJ\tO\t0\tROOT\n" +
            "5\t.\t.\t.\tO\t_\t_";
    assertEquals(expected, actual);
  }

  public void testSimpleSentenceJSON() throws IOException {
    Annotation ann = new Annotation("Bad wolf");
    pipeline.annotate(ann);
    String actual = new JSONOutputter().print(ann);
    String expected =
        "{\n" +
            "  \"sentences\": [\n" +
            "    {\n" +
            "      \"index\": \"0\",\n" +
            "      \"parse\": \"(ROOT\\n  (NP (JJ Bad) (NN wolf)))\\n\\n\",\n" +
            "      \"basic-dependencies\": [\n" +
            "        {\n" +
            "          \"dep\": \"ROOT\",\n" +
            "          \"governor\": \"0\",\n" +
            "          \"governorGloss\": \"ROOT\",\n" +
            "          \"dependent\": \"2\",\n" +
            "          \"dependentGloss\": \"wolf\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"dep\": \"amod\",\n" +
            "          \"governor\": \"2\",\n" +
            "          \"governorGloss\": \"wolf\",\n" +
            "          \"dependent\": \"1\",\n" +
            "          \"dependentGloss\": \"Bad\"\n" +
            "        }\n" +
            "      ],\n" +
            "      \"collapsed-dependencies\": [\n" +
            "        {\n" +
            "          \"dep\": \"ROOT\",\n" +
            "          \"governor\": \"0\",\n" +
            "          \"governorGloss\": \"ROOT\",\n" +
            "          \"dependent\": \"2\",\n" +
            "          \"dependentGloss\": \"wolf\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"dep\": \"amod\",\n" +
            "          \"governor\": \"2\",\n" +
            "          \"governorGloss\": \"wolf\",\n" +
            "          \"dependent\": \"1\",\n" +
            "          \"dependentGloss\": \"Bad\"\n" +
            "        }\n" +
            "      ],\n" +
            "      \"collapsed-ccprocessed-dependencies\": [\n" +
            "        {\n" +
            "          \"dep\": \"ROOT\",\n" +
            "          \"governor\": \"0\",\n" +
            "          \"governorGloss\": \"ROOT\",\n" +
            "          \"dependent\": \"2\",\n" +
            "          \"dependentGloss\": \"wolf\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"dep\": \"amod\",\n" +
            "          \"governor\": \"2\",\n" +
            "          \"governorGloss\": \"wolf\",\n" +
            "          \"dependent\": \"1\",\n" +
            "          \"dependentGloss\": \"Bad\"\n" +
            "        }\n" +
            "      ],\n" +
            "      \"tokens\": [\n" +
            "        {\n" +
            "          \"index\": \"1\",\n" +
            "          \"word\": \"Bad\",\n" +
            "          \"lemma\": \"bad\",\n" +
            "          \"characterOffsetBegin\": \"0\",\n" +
            "          \"characterOffsetEnd\": \"3\",\n" +
            "          \"pos\": \"JJ\",\n" +
            "          \"ner\": \"O\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"index\": \"2\",\n" +
            "          \"word\": \"wolf\",\n" +
            "          \"lemma\": \"wolf\",\n" +
            "          \"characterOffsetBegin\": \"4\",\n" +
            "          \"characterOffsetEnd\": \"8\",\n" +
            "          \"pos\": \"NN\",\n" +
            "          \"ner\": \"O\"\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  ]\n" +
            "}";
    assertEquals(expected, actual);
  }
}
