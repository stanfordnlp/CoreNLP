package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * A test for {@link edu.stanford.nlp.pipeline.JSONOutputter}.
 *
 * @author Gabor Angeli
 */
public class JSONOutputterTest {


  // -----
  // BEGIN TESTS FOR JSON WRITING
  // -----

  private static String indent(String in) { return in.replace("\t", JSONOutputter.INDENT_CHAR); }

  private static void testEscape(String input, String expected) {
    Assert.assertEquals(1, input.length());  // make sure I'm escaping right
    Assert.assertEquals(2, expected.length());  // make sure I'm escaping right
    Assert.assertEquals(expected, StringUtils.escapeJsonString(input));
  }

  private static void testNoEscape(String input, String expected) {
    Assert.assertEquals(1, input.length());  // make sure I'm escaping right
    Assert.assertEquals(1, expected.length());  // make sure I'm escaping right
    Assert.assertEquals(expected, StringUtils.escapeJsonString(input));
  }

  @Test
  public void testSanitizeJSONString() {
    testEscape("\b", "\\b");
    testEscape("\f", "\\f");
    testEscape("\n", "\\n");
    testEscape("\r", "\\r");
    testEscape("\t", "\\t");
    testNoEscape("'", "'");
    testNoEscape("/", "/");
    testNoEscape("-", "-");
    testEscape("\"", "\\\"");
    testEscape("\\", "\\\\");
    Assert.assertEquals("\\\\b", StringUtils.escapeJsonString("\\b"));
  }

  @Test
  public void testSimpleJSON() {
    Assert.assertEquals(indent("{\n\t\"foo\": \"bar\"\n}"),
            JSONOutputter.JSONWriter.objectToJSON((JSONOutputter.Writer writer) -> writer.set("foo", "bar")));
    Assert.assertEquals(indent("{\n\t\"foo\": \"bar\",\n\t\"baz\": \"hazzah\"\n}"),
            JSONOutputter.JSONWriter.objectToJSON((JSONOutputter.Writer writer) -> {
              writer.set("foo", "bar");
              writer.set("baz", "hazzah");
            }));
  }

  @Test
  public void testCollectionJSON() {
    Assert.assertEquals(indent("{\n\t\"foo\": [\n\t\t\"bar\",\n\t\t\"baz\"\n\t]\n}"),
            JSONOutputter.JSONWriter.objectToJSON((JSONOutputter.Writer writer) -> writer.set("foo", Arrays.asList("bar", "baz"))));

  }

  @Test
  public void testNestedJSON() {
    Assert.assertEquals(indent("{\n\t\"foo\": {\n\t\t\"bar\": \"baz\"\n\t}\n}"),
            JSONOutputter.JSONWriter.objectToJSON((JSONOutputter.Writer writer) -> writer.set("foo", (Consumer<JSONOutputter.Writer>) writer1 -> writer1.set("bar", "baz"))));
  }

  @Test
  public void testComplexJSON() {
    Assert.assertEquals(indent("{\n\t\"1.1\": {\n\t\t\"2.1\": [\n\t\t\t\"a\",\n\t\t\t\"b\",\n\t\t\t{\n\t\t\t\t\"3.1\": \"v3.1\"\n\t\t\t}\n\t\t],\n\t\t\"2.2\": \"v2.2\"\n\t}\n}"),
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


  @Test
  public void testSimpleDocument() throws IOException {
    Annotation ann = new Annotation("JSON is neat. Better than XML.");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize, ssplit"));
    pipeline.annotate(ann);
    String actual = new JSONOutputter().print(ann);
    String expected = indent(
        "{\n" +
        "\t\"sentences\": [\n" +
        "\t\t{\n" +
        "\t\t\t\"index\": 0,\n" +
        "\t\t\t\"tokens\": [\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 1,\n" +
        "\t\t\t\t\t\"word\": \"JSON\",\n" +
        "\t\t\t\t\t\"originalText\": \"JSON\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 0,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 4,\n" +
        "\t\t\t\t\t\"before\": \"\",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 2,\n" +
        "\t\t\t\t\t\"word\": \"is\",\n" +
        "\t\t\t\t\t\"originalText\": \"is\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 5,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 7,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 3,\n" +
        "\t\t\t\t\t\"word\": \"neat\",\n" +
        "\t\t\t\t\t\"originalText\": \"neat\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 8,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 12,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \"\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 4,\n" +
        "\t\t\t\t\t\"word\": \".\",\n" +
        "\t\t\t\t\t\"originalText\": \".\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 12,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 13,\n" +
        "\t\t\t\t\t\"before\": \"\",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t}\n" +
        "\t\t\t]\n" +
        "\t\t},\n" +
        "\t\t{\n" +
        "\t\t\t\"index\": 1,\n" +
        "\t\t\t\"tokens\": [\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 1,\n" +
        "\t\t\t\t\t\"word\": \"Better\",\n" +
        "\t\t\t\t\t\"originalText\": \"Better\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 14,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 20,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 2,\n" +
        "\t\t\t\t\t\"word\": \"than\",\n" +
        "\t\t\t\t\t\"originalText\": \"than\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 21,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 25,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 3,\n" +
        "\t\t\t\t\t\"word\": \"XML\",\n" +
        "\t\t\t\t\t\"originalText\": \"XML\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 26,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 29,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \"\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 4,\n" +
        "\t\t\t\t\t\"word\": \".\",\n" +
        "\t\t\t\t\t\"originalText\": \".\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 29,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 30,\n" +
        "\t\t\t\t\t\"before\": \"\",\n" +
        "\t\t\t\t\t\"after\": \"\"\n" +
        "\t\t\t\t}\n" +
        "\t\t\t]\n" +
        "\t\t}\n" +
        "\t]\n" +
        "}\n");

    Assert.assertEquals(expected, actual);
  }

  /** Test with codepoints - could refactor, but meh */
  @Test
  public void testCodepointDocument() throws IOException {
    Annotation ann = new Annotation("JSON is neat. Better than 😺.");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize", "tokenize.codepoint", "true"));
    pipeline.annotate(ann);
    String actual = new JSONOutputter().print(ann);
    String expected = indent(
        "{\n" +
        "\t\"sentences\": [\n" +
        "\t\t{\n" +
        "\t\t\t\"index\": 0,\n" +
        "\t\t\t\"tokens\": [\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 1,\n" +
        "\t\t\t\t\t\"word\": \"JSON\",\n" +
        "\t\t\t\t\t\"originalText\": \"JSON\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 0,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 4,\n" +
        "\t\t\t\t\t\"codepointOffsetBegin\": 0,\n" +
        "\t\t\t\t\t\"codepointOffsetEnd\": 4,\n" +
        "\t\t\t\t\t\"before\": \"\",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 2,\n" +
        "\t\t\t\t\t\"word\": \"is\",\n" +
        "\t\t\t\t\t\"originalText\": \"is\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 5,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 7,\n" +
        "\t\t\t\t\t\"codepointOffsetBegin\": 5,\n" +
        "\t\t\t\t\t\"codepointOffsetEnd\": 7,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 3,\n" +
        "\t\t\t\t\t\"word\": \"neat\",\n" +
        "\t\t\t\t\t\"originalText\": \"neat\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 8,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 12,\n" +
        "\t\t\t\t\t\"codepointOffsetBegin\": 8,\n" +
        "\t\t\t\t\t\"codepointOffsetEnd\": 12,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \"\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 4,\n" +
        "\t\t\t\t\t\"word\": \".\",\n" +
        "\t\t\t\t\t\"originalText\": \".\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 12,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 13,\n" +
        "\t\t\t\t\t\"codepointOffsetBegin\": 12,\n" +
        "\t\t\t\t\t\"codepointOffsetEnd\": 13,\n" +
        "\t\t\t\t\t\"before\": \"\",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t}\n" +
        "\t\t\t]\n" +
        "\t\t},\n" +
        "\t\t{\n" +
        "\t\t\t\"index\": 1,\n" +
        "\t\t\t\"tokens\": [\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 1,\n" +
        "\t\t\t\t\t\"word\": \"Better\",\n" +
        "\t\t\t\t\t\"originalText\": \"Better\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 14,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 20,\n" +
        "\t\t\t\t\t\"codepointOffsetBegin\": 14,\n" +
        "\t\t\t\t\t\"codepointOffsetEnd\": 20,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 2,\n" +
        "\t\t\t\t\t\"word\": \"than\",\n" +
        "\t\t\t\t\t\"originalText\": \"than\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 21,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 25,\n" +
        "\t\t\t\t\t\"codepointOffsetBegin\": 21,\n" +
        "\t\t\t\t\t\"codepointOffsetEnd\": 25,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \" \"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 3,\n" +
        "\t\t\t\t\t\"word\": \"😺\",\n" +
        "\t\t\t\t\t\"originalText\": \"😺\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 26,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 28,\n" +
        "\t\t\t\t\t\"codepointOffsetBegin\": 26,\n" +
        "\t\t\t\t\t\"codepointOffsetEnd\": 27,\n" +
        "\t\t\t\t\t\"before\": \" \",\n" +
        "\t\t\t\t\t\"after\": \"\"\n" +
        "\t\t\t\t},\n" +
        "\t\t\t\t{\n" +
        "\t\t\t\t\t\"index\": 4,\n" +
        "\t\t\t\t\t\"word\": \".\",\n" +
        "\t\t\t\t\t\"originalText\": \".\",\n" +
        "\t\t\t\t\t\"characterOffsetBegin\": 28,\n" +
        "\t\t\t\t\t\"characterOffsetEnd\": 29,\n" +
        "\t\t\t\t\t\"codepointOffsetBegin\": 27,\n" +
        "\t\t\t\t\t\"codepointOffsetEnd\": 28,\n" +
        "\t\t\t\t\t\"before\": \"\",\n" +
        "\t\t\t\t\t\"after\": \"\"\n" +
        "\t\t\t\t}\n" +
        "\t\t\t]\n" +
        "\t\t}\n" +
        "\t]\n" +
        "}\n");

    Assert.assertEquals(expected, actual);
  }

}
