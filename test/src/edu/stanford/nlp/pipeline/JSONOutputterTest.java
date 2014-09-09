package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.Arrays;
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
    assertEquals(expected, JSONOutputter.JSONWriter.cleanJSON(input));
  }

  public void testSanitizeJSONString() {
    testEscape("\b", "\\b");
    testEscape("\f", "\\f");
    testEscape("\n", "\\n");
    testEscape("\r", "\\r");
    testEscape("\t", "\\t");
    testEscape("'", "\\'");
    testEscape("\"", "\\\"");
    testEscape("\\", "\\\\");
    assertEquals("\\\\b", JSONOutputter.JSONWriter.cleanJSON("\\b"));
  }

  public void testSimpleJSON() {
    assertEquals(indent("{\n\t\"foo\": \"bar\"\n}"),
        JSONOutputter.JSONWriter.objectToJSON( (JSONOutputter.Writer writer) -> {
          writer.set("foo", "bar");
        }));
    assertEquals(indent("{\n\t\"foo\": \"bar\",\n\t\"baz\": \"hazzah\"\n}"),
        JSONOutputter.JSONWriter.objectToJSON( (JSONOutputter.Writer writer) -> {
          writer.set("foo", "bar");
          writer.set("baz", "hazzah");
        }));
  }

  public void testCollectionJSON() {
    assertEquals(indent("{\n\t\"foo\": [\n\t\t\"bar\",\n\t\t\"baz\"\n\t]\n}"),
        JSONOutputter.JSONWriter.objectToJSON( (JSONOutputter.Writer writer) -> {
          writer.set("foo", Arrays.asList("bar", "baz"));
        }));

  }

  public void testNestedJSON() {
    assertEquals(indent("{\n\t\"foo\": {\n\t\t\"bar\": \"baz\"\n\t}\n}"),
        JSONOutputter.JSONWriter.objectToJSON((JSONOutputter.Writer writer) -> {
          writer.set("foo", (Consumer<JSONOutputter.Writer>) writer1 -> writer1.set("bar", "baz"));
        }));
  }

  public void testComplexJSON() {
    assertEquals(indent("{\n\t\"1.1\": {\n\t\t\"2.1\": [\n\t\t\t\"a\",\n\t\t\t\"b\",\n\t\t\t{\n\t\t\t\t\"3.1\": \"v3.1\"\n\t\t\t}\n\t\t],\n\t\t\"2.2\": \"v2.2\"\n\t}\n}"),
        JSONOutputter.JSONWriter.objectToJSON((JSONOutputter.Writer l1) -> {
          l1.set("1.1", (Consumer<JSONOutputter.Writer>) l2 -> {
            l2.set("2.1", Arrays.asList(
                    "a",
                    "b",
                    (Consumer<JSONOutputter.Writer>) l3 -> {
                      l3.set("3.1", "v3.1");
                    }
            ));
            l2.set("2.2", "v2.2");
          });
        }));
  }

  // -----
  // BEGIN TESTS FOR ANNOTATION WRITING
  // -----

}
