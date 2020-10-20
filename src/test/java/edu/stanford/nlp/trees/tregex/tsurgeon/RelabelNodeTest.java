package edu.stanford.nlp.trees.tregex.tsurgeon;

import junit.framework.TestCase;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Test the regex patterns in RelabelNode.  The operation itself will
 * be tested in TsurgeonTest.
 *
 * @author John Bauer
 */
public class RelabelNodeTest extends TestCase {
  public void testRegexPattern() {
    Pattern pattern = RelabelNode.regexPattern;

    String[] goodLabels = {"//", "/foo/", "/\\\\/", "/\\\\\\\\/",
                           "/foo\\\\/", "/f\\oo\\\\/", "/f\\oo/", "/f\\o/",
                           "/f\\/oo/"};
    String[] badLabels = {"foo", "/\\/", "/\\\\\\/", "/foo\\/", "asdf"};

    runPatternTest(pattern, goodLabels, badLabels, 1, -1);
  }

  public void testNodePattern() {
    Pattern pattern = Pattern.compile(RelabelNode.nodePatternString);
    
    String[] goodMatches = {"={foo}", "={blah}", "={z954240_fdsfgsf}"};
    String[] badMatches = {"%{foo}", "bar", "=%{blah}", "%={blah}", 
                           "=foo", "%foo"};

    runPatternTest(pattern, goodMatches, badMatches, 0, 0);
  }

  public void testVariablePattern() {
    Pattern pattern = Pattern.compile(RelabelNode.variablePatternString);
    
    String[] goodMatches = {"%{foo}", "%{blah}", "%{z954240_fdsfgsf}"};
    String[] badMatches = {"={foo}", "{bar}", "=%{blah}", "%={blah}",
                           "=foo", "%foo"};

    runPatternTest(pattern, goodMatches, badMatches, 0, 0);
  }

  public void runPatternTest(Pattern pattern, String[] good, String[] bad,
                             int startOffset, int endOffset) {
    for (String test : good) {
      Matcher m = pattern.matcher(test);
      assertTrue("Should have matched on " + test, m.matches());
      String matched = m.group(1);
      String expected = test.substring(startOffset, test.length() + endOffset);
      assertEquals("Matched group wasn't " + test, expected, matched);
    }

    for (String test : bad) {
      Matcher m = pattern.matcher(test);
      assertFalse("Shouldn't have matched on " + test, m.matches());
    }
  }
}
