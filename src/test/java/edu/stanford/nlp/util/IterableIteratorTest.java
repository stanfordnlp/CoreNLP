package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A test for the {@link edu.stanford.nlp.util.IterableIterator}.
 * Notably, I don't entirely trust myself to implement the {@link Iterable#spliterator()}} function
 * properly.
 *
 * @author Gabor Angeli
 */
public class IterableIteratorTest extends TestCase {

  public void testBasic() {
    String[] strings = new String[] {
        "do", "re", "mi", "fa", "so", "la", "ti", "do",
    };
    Iterator<String> it = Arrays.asList(strings).iterator();
    IterableIterator<String> iterit = new IterableIterator<>(it);
    assertEquals("do", iterit.next());
    assertEquals("re", iterit.next());
    assertEquals("mi", iterit.next());
    assertEquals("fa", iterit.next());
    assertEquals("so", iterit.next());
    assertEquals("la", iterit.next());
    assertEquals("ti", iterit.next());
    assertEquals("do", iterit.next());
    assertFalse(iterit.hasNext());

  }

  public void testSpliteratorInSequence() {
    ArrayList<Integer> x = new ArrayList<>();
    for (int i = 0; i < 1000; ++i) {
      x.add(i);
    }
    IterableIterator<Integer> iter = new IterableIterator<>(x.iterator());
    Spliterator<Integer> spliterator = iter.spliterator();
    Stream<Integer> stream = StreamSupport.stream(spliterator, false);
    final Integer[] next = new Integer[]{0};
    stream.forEach(elem -> {
      assertEquals(next[0], elem);
      next[0] += 1;
    });
  }

  public void testSpliteratorInParallel() {
    ArrayList<Integer> x = new ArrayList<>();
    for (int i = 0; i < 1000; ++i) {
      x.add(i);
    }
    IterableIterator<Integer> iter = new IterableIterator<>(x.iterator());
    Spliterator<Integer> spliterator = iter.spliterator();
    Stream<Integer> stream = StreamSupport.stream(spliterator, true);
    final boolean[] seen = new boolean[1000];
    stream.forEach(elem -> {
      assertFalse(seen[elem]);
      seen[elem] = true;
    });
    for (int i = 0; i < 1000; ++i) {
      assertTrue(seen[i]);
    }
  }
}
