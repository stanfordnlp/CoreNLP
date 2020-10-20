package edu.stanford.nlp.patterns.surface;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

public class AnnotatedTextReaderTest extends TestCase {

  public void testParse() {
    try {
      String text = "I am going to be in <LOC> Italy </LOC> sometime, soon. Specifically in <LOC> Tuscany </LOC> .";
      Set<String> labels = new HashSet<>();
      labels.add("LOC");
      System.out.println(AnnotatedTextReader.parseFile(new BufferedReader(new StringReader(text)),
          labels, null, true, ""));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
