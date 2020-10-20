package edu.stanford.nlp.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Christopher Manning
 * @author John Bauer
 */
public class ArrayUtilsTest {

  private int[] sampleGaps = {1, 5, 6, 10, 17, 22, 29, 33, 100, 1000, 10000, 9999999};
  private int[] sampleBadGaps = {1, 6, 5, 10, 17};

  @Test
  public void testEqualContentsInt() {
    org.junit.Assert.assertTrue(ArrayUtils.equalContents(sampleGaps, sampleGaps));
    org.junit.Assert.assertTrue(ArrayUtils.equalContents(sampleBadGaps, sampleBadGaps));
    org.junit.Assert.assertFalse(ArrayUtils.equalContents(sampleGaps, sampleBadGaps));
  }

  @Test
  public void testGaps() {
    byte[] encoded = ArrayUtils.gapEncode(sampleGaps);
    int[] decoded = ArrayUtils.gapDecode(encoded);
    org.junit.Assert.assertTrue(ArrayUtils.equalContents(decoded, sampleGaps));

    try {
      ArrayUtils.gapEncode(sampleBadGaps);
      throw new RuntimeException("Expected an IllegalArgumentException");
    } catch(IllegalArgumentException e) {
      // yay, we passed
    }
  }

  @Test
  public void testDelta() {
    byte[] encoded = ArrayUtils.deltaEncode(sampleGaps);
    int[] decoded = ArrayUtils.deltaDecode(encoded);
    org.junit.Assert.assertTrue(ArrayUtils.equalContents(decoded, sampleGaps));

    try {
      ArrayUtils.deltaEncode(sampleBadGaps);
      throw new RuntimeException("Expected an IllegalArgumentException");
    } catch(IllegalArgumentException e) {
      // yay, we passed
    }
  }

  @Test
  public void testRemoveAt() {
    String[] strings = new String[]{"a", "b", "c"};
    strings = (String[]) ArrayUtils.removeAt(strings, 2);
    int i = 0;
    for (String string : strings) {
      if (i == 0) {
        assertEquals("a", string);
      } else if (i == 1) {
        assertEquals("b", string);
      } else {
        org.junit.Assert.fail("Array is too big!");
      }
      i++;
    }
  }

  @Test
  public void testAsSet() {
    String[] items = {"larry", "moe", "curly"};
    Set<String> set = new HashSet<>(Arrays.asList(items));
    assertEquals(set, ArrayUtils.asSet(items));
  }


  @Test
  public void testgetSubListIndex() {
    String[] t1 = {"this", "is", "test"};
    String[] t2 = {"well","this","is","not","this","is","test","also"};
    assertEquals(4,(ArrayUtils.getSubListIndex(t1, t2).get(0).intValue()));
    String[] t3 = {"cough","increased"};
    String[] t4 = {"i","dont","really","cough"};
    assertEquals(0, ArrayUtils.getSubListIndex(t3, t4).size());
    String[] t5 = {"cough","increased"};
    String[] t6 = {"cough","aggravated"};
    assertEquals(0, ArrayUtils.getSubListIndex(t5, t6).size());
    String[] t7 = {"cough","increased"};
    String[] t8 = {"cough","aggravated","cough","increased","and","cough", "increased","and","cough","and","increased"};
    assertEquals(2, ArrayUtils.getSubListIndex(t7, t8).get(0).intValue());
    assertEquals(5, ArrayUtils.getSubListIndex(t7, t8).get(1).intValue());
  }

}
