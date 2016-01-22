package edu.stanford.nlp.trees.tregex.tsurgeon;

import junit.framework.TestCase;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Test the regex patterns in AuxiliaryTree.  The tree itself will be
 * tested indirectly by seeing that Tsurgeon works, via TsurgeonTest.
 *
 * @author John Bauer
 */
public class AuxiliaryTreeTest extends TestCase {
  public void testNamePattern() {
    runNamePatternTrue("abcd=efgh", "abcd", "efgh");
    runNamePatternFalse("abcd\\=efgh");
    runNamePatternTrue("abcd\\\\=efgh", "abcd\\\\", "efgh");
    runNamePatternFalse("abcd\\\\\\=efgh");
    runNamePatternTrue("abcd\\\\\\\\=efgh", "abcd\\\\\\\\", "efgh");
  }

  public void runNamePatternFalse(String input) {
    Matcher m = AuxiliaryTree.namePattern.matcher(input);
    assertFalse(m.find());
  }

  public void runNamePatternTrue(String input, String leftover, String name) {
    Matcher m = AuxiliaryTree.namePattern.matcher(input);
    assertTrue(m.find());
    assertEquals(leftover, m.group(1));
    assertEquals(name, m.group(2));
  }

  public void testUnescape() {
    assertEquals("asdf", AuxiliaryTree.unescape("asdf"));
    assertEquals("asdf=", AuxiliaryTree.unescape("asdf\\="));
    assertEquals("asdf\\=", AuxiliaryTree.unescape("asdf\\\\="));
  }
}


