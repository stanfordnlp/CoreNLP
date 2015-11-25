package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * A test for {@link edu.stanford.nlp.pipeline.JSONOutputter}.
 *
 * @author Gabor Angeli
 */
public class JSONOutputterTest extends TestCase {


  // -----
  // BEGIN TESTS FOR JSON WRITING
  // -----

  private static String indent(String in) { return in.replace("\t", JSONOutputter.INDENT_CHAR); }

  private static void testEscape(String input, String expected) {
    assertEquals(1, input.length());  // make sure I'm escaping right
    assertEquals(2, expected.length());  // make sure I'm escaping right
    assertEquals(expected, JSONOutputter.cleanJSON(input));
  }

  private static void testNoEscape(String input, String expected) {
    assertEquals(1, input.length());  // make sure I'm escaping right
    assertEquals(1, expected.length());  // make sure I'm escaping right
    assertEquals(expected, JSONOutputter.cleanJSON(input));
  }

  public void testSanitizeJSONString() {
    testEscape("\b", "\\b");
    testEscape("\f", "\\f");
    testEscape("\n", "\\n");
    testEscape("\r", "\\r");
    testEscape("\t", "\\t");
    testNoEscape("'", "'");
    testEscape("\"", "\\\"");
    testEscape("\\", "\\\\");
    assertEquals("\\\\b", JSONOutputter.cleanJSON("\\b"));
  }

  public void testSimpleJSON() {
    assertEquals(indent("{\n\t\"foo\": \"bar\"\n}"),
        JSONOutputter.JSONWriter.objectToJSON( (JSONOutputter.Writer writer) -> writer.set("foo", "bar")));
    assertEquals(indent("{\n\t\"foo\": \"bar\",\n\t\"baz\": \"hazzah\"\n}"),
        JSONOutputter.JSONWriter.objectToJSON( (JSONOutputter.Writer writer) -> {
          writer.set("foo", "bar");
          writer.set("baz", "hazzah");
        }));
  }

  public void testCollectionJSON() {
    assertEquals(indent("{\n\t\"foo\": [\n\t\t\"bar\",\n\t\t\"baz\"\n\t]\n}"),
        JSONOutputter.JSONWriter.objectToJSON( (JSONOutputter.Writer writer) -> writer.set("foo", Arrays.asList("bar", "baz"))));

  }

  public void testNestedJSON() {
    assertEquals(indent("{\n\t\"foo\": {\n\t\t\"bar\": \"baz\"\n\t}\n}"),
        JSONOutputter.JSONWriter.objectToJSON((JSONOutputter.Writer writer) -> writer.set("foo", (Consumer<JSONOutputter.Writer>) writer1 -> writer1.set("bar", "baz"))));
  }

  public void testComplexJSON() {
    assertEquals(indent("{\n\t\"1.1\": {\n\t\t\"2.1\": [\n\t\t\t\"a\",\n\t\t\t\"b\",\n\t\t\t{\n\t\t\t\t\"3.1\": \"v3.1\"\n\t\t\t}\n\t\t],\n\t\t\"2.2\": \"v2.2\"\n\t}\n}"),
        JSONOutputter.JSONWriter.objectToJSON((JSONOutputter.Writer l1) -> l1.set("1.1", (Consumer<JSONOutputter.Writer>) l2 -> {
          l2.set("2.1", Arrays.asList(
                  "a",
                  "b",
                  (Consumer<JSONOutputter.Writer>) l3 -> l3.set("3.1", "v3.1")
          ));
          l2.set("2.2", "v2.2");
        })));
  }

  // -----
  // BEGIN TESTS FOR ANNOTATION WRITING
  // -----

  public void testSimpleDocument() throws IOException {
    Annotation ann = new Annotation("JSON is neat. Better than XML.");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(new Properties() {{ setProperty("annotators", "tokenize, ssplit"); }});
    pipeline.annotate(ann);
    String actual = new JSONOutputter().print(ann);
    String expected = indent(
        "{\n" +
        "\t\"sentences\": [\n" +
        "\t\t{\n" +
        "\t\t\t\"index\": \"0\",\n" +
        "\t\t\t\"parse\": \"SENTENCE_SKIPPED_OR_UNPARSABLE\",\n" +
        "\t\t\t\"tokens\": [\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": \"1\",\n" +
        "\t\t\t\t\t\"word\": \"JSON\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": \"0\",\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": \"4\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": \"2\",\n" +
        "\t\t\t\t\t\"word\": \"is\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": \"5\",\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": \"7\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": \"3\",\n" +
        "\t\t\t\t\t\"word\": \"neat\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": \"8\",\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": \"12\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": \"4\",\n" +
        "\t\t\t\t\t\"word\": \".\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": \"12\",\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": \"13\"\n" +
        "\t\t\t\t}\n" +
        "\t\t\t]\n" +
        "\t\t},\n" +
        "\t\t{\n" +
        "\t\t\t\"index\": \"1\",\n" +
        "\t\t\t\"parse\": \"SENTENCE_SKIPPED_OR_UNPARSABLE\",\n" +
        "\t\t\t\"tokens\": [\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": \"1\",\n" +
        "\t\t\t\t\t\"word\": \"Better\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": \"14\",\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": \"20\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": \"2\",\n" +
        "\t\t\t\t\t\"word\": \"than\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": \"21\",\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": \"25\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": \"3\",\n" +
        "\t\t\t\t\t\"word\": \"XML\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": \"26\",\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": \"29\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": \"4\",\n" +
        "\t\t\t\t\t\"word\": \".\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": \"29\",\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": \"30\"\n" +
        "\t\t\t\t}\n" +
        "\t\t\t]\n" +
        "\t\t}\n" +
        "\t]\n" +
        "}");

    assertEquals(expected, actual);
  }

}
