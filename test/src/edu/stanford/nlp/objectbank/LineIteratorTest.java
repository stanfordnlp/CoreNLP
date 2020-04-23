package edu.stanford.nlp.objectbank;

import java.io.StringReader;
import java.util.Iterator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Christopher Manning
 */
public class LineIteratorTest {
  private String s;
  private String[] output;
  Iterator<String> di;

  @Before
  public void setUp() {
    this.s = "\n\n@@123\nthis\nis\na\nsentence\n\n@@124\nThis\nis another\n.\n\n@125\nThis is the\tlast\n";
    this.output = new String[]{"", "", "@@123", "this", "is", "a", "sentence", "", "@@124", "This", "is another",
            ".", "", "@125", "This is the\tlast"};
    this.di = new LineIterator<>(new StringReader(s), new IdentityFunction<>());
  }

  @Test
  public void testWrongLine() {
    try {
      for (String out : output) {
        String ans = di.next();

        Assert.assertEquals("Wrong line", out, ans);
      }
    } catch (Exception e) {
      Assert.fail("Probably too few things in iterator: " + e);
    }
  }

  @Test
  public void testEnoughElements() {
    try {
      Assert.assertTrue(di.hasNext());
    } catch (Exception e) {
      Assert.fail("Probably too few things in iterator: " + e);
    }
  }
}