package edu.stanford.nlp.objectbank;

import java.io.StringReader;
import java.util.Iterator;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class LineIteratorTest extends TestCase {

  public void testLineIterator() {
    String s = "\n\n@@123\nthis\nis\na\nsentence\n\n@@124\nThis\nis another\n.\n\n@125\nThis is the\tlast\n";
    String[] output = { "", "", "@@123", "this", "is", "a", "sentence", "", "@@124", "This", "is another",
            ".", "", "@125", "This is the\tlast"};
    Iterator<String> di = new LineIterator<>(new StringReader(s), new IdentityFunction<>());

    try {
      for (String out : output) {
        String ans = di.next();
        // System.out.println(ans);
        assertEquals("Wrong line", out, ans);
      }
      if (di.hasNext()) {
        fail("Too many things in iterator: " + di.next());
      }
    } catch (Exception e) {
      fail("Probably too few things in iterator: " + e);
    }
  }

}