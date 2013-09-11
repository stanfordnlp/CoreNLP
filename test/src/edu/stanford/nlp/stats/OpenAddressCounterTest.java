package edu.stanford.nlp.stats;
import java.io.*;
import edu.stanford.nlp.io.IOUtils;

/**
 * Tests for the OpenAddressCounter.
 * 
 * @author dramage
 */
public class OpenAddressCounterTest extends CounterTestBase {
  public OpenAddressCounterTest() {
    super(new OpenAddressCounter<String>());
  }

  
  public void testSerialization() {
    try {
    File temp = File.createTempFile("testCounter", ".sser");
    temp.deleteOnExit();
    OpenAddressCounter<Integer> c = new OpenAddressCounter<Integer>();
    c.incrementCount(3);
    c.incrementCount(42);
    IOUtils.writeObjectToFile(c,temp.getPath());
    @SuppressWarnings("unchecked")
    Counter<String> c2 = (Counter<String>)IOUtils.readObjectFromFile(temp.getPath());
    assertEquals(c,c2);
    } catch(Exception ie) {
      assertTrue(false);
    }
  }
}
