package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Unit tests for Iterables utility class.
 *
 * @author dramage
 */
public class IterablesTest {

  @Test
  public void testZip() {
    String[] s1 = new String[]{"a", "b", "c"};
    Integer[] s2 = new Integer[]{1, 2, 3, 4};

    int count = 0;
    for (Pair<String,Integer> pair : Iterables.zip(s1, s2)) {
      assertEquals(pair.first, s1[count]);
      assertEquals(pair.second, s2[count]);
      count++;
    }

    assertEquals(s1.length < s2.length ? s1.length : s2.length, count);
  }


  @Test
  @SuppressWarnings("unchecked")
  public void testChain() {
    List<String> s1 = Arrays.asList(new String[]{"hi", "there"});
    List<String> s2 = Arrays.asList(new String[]{});
    List<String> s3 = Arrays.asList(new String[]{"yoo"});
    List<String> s4 = Arrays.asList(new String[]{});

    List<String> answer = Arrays.asList(new String[]{"yoo","hi","there","yoo"});
    List<String> chained = new ArrayList<>();
    for (String s : Iterables.chain(s3, s1, s2, s3, s4)) {
      chained.add(s);
    }

    assertEquals(answer, chained);
  }

  @Test
  public void testFilter() {
    List<String> values = Arrays.asList("a","HI","tHere","YO");

    Iterator<String> iterator = Iterables.filter(values,
            (String in) -> in.equals(in.toUpperCase())).iterator();

    assertTrue(iterator.hasNext());
    assertEquals(iterator.next(), "HI");
    assertEquals(iterator.next(), "YO");
    org.junit.Assert.assertFalse(iterator.hasNext());
  }

  @Test
  public void testTransform() {
    List<Integer> values = Arrays.asList(1,2,3,4);
    List<Integer> squares = Arrays.asList(1,4,9,16);

    Function<Integer,Integer> squarer = (Integer in)-> in * in;

    for (Pair<Integer,Integer> pair : Iterables.zip(Iterables.transform(values, squarer), squares)) {
      assertEquals(pair.first, pair.second);
    }
  }

  @Test
  public void testMerge() {
    List<String> a = Arrays.asList("a","b","d","e");
    List<String> b = Arrays.asList("b","c","d","e");
    Comparator<String> comparator = Comparator.naturalOrder();

    Iterator<Pair<String,String>> iter = Iterables.merge(a, b, comparator).iterator();
    assertEquals(iter.next(), new Pair<>("b", "b"));
    assertEquals(iter.next(), new Pair<>("d", "d"));
    assertEquals(iter.next(), new Pair<>("e", "e"));
    assertTrue(!iter.hasNext());
  }


  @Test
  public void testMerge3() {
    List<String> a = Arrays.asList("a","b","d","e");
    List<String> b = Arrays.asList("b","c","d","e");
    List<String> c = Arrays.asList("a", "b", "c", "e", "f");

    Comparator<String> comparator = Comparator.naturalOrder();

    Iterator<Triple<String,String,String>> iter = Iterables.merge(a, b, c, comparator).iterator();
    assertEquals(iter.next(), new Triple<>("b", "b", "b"));
    assertEquals(iter.next(), new Triple<>("e", "e", "e"));
    assertTrue(!iter.hasNext());
  }


  @Test
  public void testGroup() {
    String[] input = new String[]{
        "0 ab",
        "0 bb",
        "0 cc",
        "1 dd",
        "2 dd",
        "2 kj",
        "3 kj",
        "3 kk"};
    int[] counts = new int[]{3,1,2,2};

    Comparator<String> fieldOne = Comparator.comparing(o -> o.split(" ")[0]);

    int index = 0;
    int group = 0;
    for (Iterable<String> set : Iterables.group(Arrays.asList(input), fieldOne)) {
      String sharedKey = null;

      int thisCount = 0;
      for (String line : set) {
        String thisKey = line.split(" ")[0];
        if (sharedKey == null) {
          sharedKey = thisKey;
        } else {
          assertEquals("Wrong key", sharedKey, thisKey);
        }
        assertEquals("Wrong input line", line, input[index++]);
        thisCount++;
      }
      assertEquals("Wrong number of items in this iterator", counts[group++], thisCount);
    }

    assertEquals("Didn't get all inputs", input.length, index);
    assertEquals("Wrong number of groups", counts.length, group);
  }

  @Test
  public void testSample() {
    // make sure correct number of items is sampled and items are in range
    Iterable<Integer> items = Arrays.asList(5, 4, 3, 2, 1);
    int count = 0;
    for (Integer item: Iterables.sample(items, 5, 2, new Random())) {
      ++count;
      assertTrue(item <= 5);
      assertTrue(item >= 1);
    }
    assertEquals(2, count);
  }

}
