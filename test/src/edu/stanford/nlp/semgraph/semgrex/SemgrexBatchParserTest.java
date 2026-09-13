package edu.stanford.nlp.semgraph.semgrex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.PatternSyntaxException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Tests reading a file of semgrex patterns, with the macros that go with it.
 *
 * @author John Bauer
 */
public class SemgrexBatchParserTest {

  /**
   * Compiles a batch file given as one line per element
   */
  public static List<SemgrexPattern> compile(String ... lines) {
    String batch = String.join("\n", lines);
    try {
      return SemgrexBatchParser.compileStream(
          new ByteArrayInputStream(batch.getBytes(StandardCharsets.UTF_8)));
    } catch (IOException e) {
      throw new RuntimeException("Reading a byte array should not have failed", e);
    }
  }

  static void assertPatterns(List<SemgrexPattern> patterns, String ... expected) {
    assertEquals("Wrong number of patterns", expected.length, patterns.size());
    for (int i = 0; i < expected.length; ++i) {
      assertEquals(expected[i], patterns.get(i).toString().replaceAll(" +", " ").trim());
    }
  }

  /**
   * One pattern per line
   */
  @Test
  public void testSeveralPatterns() {
    assertPatterns(compile("{word:foo}", "{word:bar} > {word:baz}"),
                   "{word:foo}", "{word:bar} > {word:baz}");
  }

  /**
   * Blank lines and lines starting with # are skipped
   */
  @Test
  public void testCommentsAndBlankLines() {
    assertPatterns(compile("# a comment", "", "{word:foo}", "   ", "# another comment"),
                   "{word:foo}");
    assertPatterns(compile("# nothing but comments"));
  }

  /**
   * A macro is written once and referred to as ${name}
   *<br>
   * The substitution is textual and happens before the pattern is
   * compiled, so the macro holds the text of the regex rather than a regex
   * with the slashes around it.
   */
  @Test
  public void testMacro() {
    assertPatterns(compile("macro STEM = (fill|spill)",
                           "{word:/${STEM}ing/} > {word:/${STEM}ed/}"),
                   "{word:/(fill|spill)ing/} > {word:/(fill|spill)ed/}");

    // the same macro may be used more than once on a line, and a macro
    // which is never used is simply not substituted anywhere
    assertPatterns(compile("macro A = foo", "{word:/${A}/} > {word:/${A}/}"),
                   "{word:/foo/} > {word:/foo/}");
    assertPatterns(compile("macro A = foo", "{word:bar}"), "{word:bar}");

    // two macros may appear in one pattern, next to each other
    assertPatterns(compile("macro A = foo", "macro B = bar", "{word:/${A}${B}/}"),
                   "{word:/foobar/}");
  }

  /**
   * The macros are all read before any pattern is compiled
   *<br>
   * The file is read twice, once for the macros and once for the patterns,
   * so a macro may be defined below the pattern which uses it.
   */
  @Test
  public void testMacroDefinedAfterUse() {
    assertPatterns(compile("{word:/${STEM}ing/}", "macro STEM = (fill|spill)"),
                   "{word:/(fill|spill)ing/}");
  }

  /**
   * A macro is substituted once, so one macro cannot be written in terms of another
   *<br>
   * The line is scanned left to right and the scan continues after the text
   * which was substituted in, so a ${...} which arrives as part of a macro's
   * value is left alone.  Here that leaves ${A} in the regex, which the regex
   * compiler then rejects.
   */
  @Test
  public void testMacrosAreNotRecursive() {
    assertThrows(PatternSyntaxException.class,
                 () -> compile("macro A = foo", "macro B = ${A}|bar", "{word:/${B}/}"));
  }

  /**
   * A macro name is matched as letters and digits, and looked up exactly
   *<br>
   * A name with any other character in it cannot be referred to: the ${...}
   * is not recognised, so it stays in the pattern as literal text.  The
   * lookup is case sensitive even though the ${...} itself is not, so
   * ${foo} does not find a macro named Foo.
   */
  @Test
  public void testMacroNames() {
    assertPatterns(compile("macro W1 = foo", "{word:/${W1}/}"), "{word:/foo/}");

    // the ${W_1} is left in the regex, which the regex compiler rejects
    assertThrows(PatternSyntaxException.class,
                 () -> compile("macro W_1 = foo", "{word:/${W_1}/}"));

    RuntimeException e = assertThrows(RuntimeException.class,
                                      () -> compile("macro Foo = bar", "{word:/${foo}/}"));
    assertTrue(e.getMessage().contains("Unknown macro"));
  }

  /**
   * Referring to a macro which was never defined is an error
   */
  @Test
  public void testUnknownMacro() {
    RuntimeException e = assertThrows(RuntimeException.class,
                                      () -> compile("{word:/${NOPE}/}"));
    assertTrue(e.getMessage().contains("Unknown macro"));
  }

  /**
   * A macro line needs a name and a value
   */
  @Test
  public void testMalformedMacro() {
    for (String line : new String[] {"macro W (foo|bar)", "macro  = foo", "macro W ="}) {
      RuntimeException e = assertThrows("Expected \"" + line + "\" to be rejected",
                                        RuntimeException.class,
                                        () -> compile(line, "{word:foo}"));
      assertTrue(e.getMessage().contains("Invalid syntax in macro line"));
    }
  }

  /**
   * A pattern which does not compile is reported rather than skipped
   */
  @Test
  public void testBadPattern() {
    assertThrows(SemgrexParseException.class,
                 () -> compile("{word:foo}", "{word:bar"));
  }
}
