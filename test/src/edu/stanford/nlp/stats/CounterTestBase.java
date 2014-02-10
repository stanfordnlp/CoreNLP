package edu.stanford.nlp.stats;

import java.util.*;
import java.io.*;

import junit.framework.TestCase;
import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.io.IOUtils;


/**
 * Base tests that should work on any type of Counter.  This class
 * is subclassed by e.g., {@link ClassicCounterTest} to provide the
 * particular Counter instance being tested.
 */
public abstract class CounterTestBase extends TestCase {

  private Counter<String> c;
  private final boolean integral;

  private static final double TOLERANCE = 0.001;


  public CounterTestBase(Counter<String> c) {
    this(c, false);
  }

  public CounterTestBase(Counter<String> c, boolean integral) {
    this.c = c;
    this.integral = integral;
  }

  @Override
  public void setUp() {
    c.clear();
  }

  public void testClassicCounterHistoricalMain() {
    c.setCount("p", 0);
    c.setCount("q", 2);
    ClassicCounter<String> small_c = new ClassicCounter<String>(c);

    Counter<String> c7 = c.getFactory().create();
    c7.addAll(c);
    assertEquals(c.totalCount(), 2.0);
    c.incrementCount("p");
    assertEquals(c.totalCount(), 3.0);
    c.incrementCount("p", 2.0);
    assertEquals(Counters.min(c), 2.0);
    assertEquals(Counters.argmin(c), "q");
    // Now p is p=3.0, q=2.0
    c.setCount("w", -5.0);
    c.setCount("x", -4.5);
    List<String> biggestKeys = new ArrayList<String>(c.keySet());
    assertEquals(biggestKeys.size(), 4);
    Collections.sort(biggestKeys, Counters.toComparator(c, false, true));
    assertEquals("w", biggestKeys.get(0));
    assertEquals("x", biggestKeys.get(1));
    assertEquals("p", biggestKeys.get(2));
    assertEquals("q", biggestKeys.get(3));
    assertEquals(Counters.min(c), -5.0, TOLERANCE);
    assertEquals(Counters.argmin(c), "w");
    assertEquals(Counters.max(c), 3.0, TOLERANCE);
    assertEquals(Counters.argmax(c), "p");
    if (integral) {
      assertEquals(Counters.mean(c), -1.0);
    } else {
      assertEquals(Counters.mean(c), -1.125, TOLERANCE);
    }

    if ( ! integral) {
      // only do this for floating point counters.  Too much bother to rewrite
      c.setCount("x", -2.5);
      ClassicCounter<String> c2 = new ClassicCounter<String>(c);
      assertEquals(3.0, c2.getCount("p"));
      assertEquals(2.0, c2.getCount("q"));
      assertEquals(-5.0, c2.getCount("w"));
      assertEquals(-2.5, c2.getCount("x"));

      Counter<String> c3 = c.getFactory().create();
      for (String str: c2.keySet()) {
        c3.incrementCount(str);
      }
      assertEquals(1.0, c3.getCount("p"));
      assertEquals(1.0, c3.getCount("q"));
      assertEquals(1.0, c3.getCount("w"));
      assertEquals(1.0, c3.getCount("x"));

      Counters.addInPlace(c2, c3, 10.0);
      assertEquals(13.0, c2.getCount("p"));
      assertEquals(12.0, c2.getCount("q"));
      assertEquals(5.0, c2.getCount("w"));
      assertEquals(7.5, c2.getCount("x"));

      c3.addAll(c);
      assertEquals(4.0, c3.getCount("p"));
      assertEquals(3.0, c3.getCount("q"));
      assertEquals(-4.0, c3.getCount("w"));
      assertEquals(-1.5, c3.getCount("x"));

      Counters.subtractInPlace(c3, c);
      assertEquals(1.0, c3.getCount("p"));
      assertEquals(1.0, c3.getCount("q"));
      assertEquals(1.0, c3.getCount("w"));
      assertEquals(1.0, c3.getCount("x"));

      for (String str : c.keySet()) {
        c3.incrementCount(str);
      }
      assertEquals(2.0, c3.getCount("p"));
      assertEquals(2.0, c3.getCount("q"));
      assertEquals(2.0, c3.getCount("w"));
      assertEquals(2.0, c3.getCount("x"));

      Counters.divideInPlace(c2, c3);
      assertEquals(6.5, c2.getCount("p"));
      assertEquals(6.0, c2.getCount("q"));
      assertEquals(2.5, c2.getCount("w"));
      assertEquals(3.75, c2.getCount("x"));

      Counters.divideInPlace(c2, 0.5);
      assertEquals(13.0, c2.getCount("p"));
      assertEquals(12.0, c2.getCount("q"));
      assertEquals(5.0, c2.getCount("w"));
      assertEquals(7.5, c2.getCount("x"));

      Counters.multiplyInPlace(c2, 2.0);
      assertEquals(26.0, c2.getCount("p"));
      assertEquals(24.0, c2.getCount("q"));
      assertEquals(10.0, c2.getCount("w"));
      assertEquals(15.0, c2.getCount("x"));

      Counters.divideInPlace(c2, 2.0);
      assertEquals(13.0, c2.getCount("p"));
      assertEquals(12.0, c2.getCount("q"));
      assertEquals(5.0, c2.getCount("w"));
      assertEquals(7.5, c2.getCount("x"));

      for (String str : c2.keySet()) {
        c2.incrementCount(str);
      }
      assertEquals(14.0, c2.getCount("p"));
      assertEquals(13.0, c2.getCount("q"));
      assertEquals(6.0, c2.getCount("w"));
      assertEquals(8.5, c2.getCount("x"));

      for (String str : c.keySet()) {
        c2.incrementCount(str);
      }
      assertEquals(15.0, c2.getCount("p"));
      assertEquals(14.0, c2.getCount("q"));
      assertEquals(7.0, c2.getCount("w"));
      assertEquals(9.5, c2.getCount("x"));

      c2.addAll(small_c);
      assertEquals(15.0, c2.getCount("p"));
      assertEquals(16.0, c2.getCount("q"));
      assertEquals(7.0, c2.getCount("w"));
      assertEquals(9.5, c2.getCount("x"));

      assertEquals(new HashSet<String>(Arrays.asList("p", "q")), Counters.keysAbove(c2, 14));
      assertEquals(new HashSet<String>(Arrays.asList("q")), Counters.keysAt(c2, 16));
      assertEquals(new HashSet<String>(Arrays.asList("x", "w")), Counters.keysBelow(c2, 9.5));

      Counters.addInPlace(c2,small_c, -6);
      assertEquals(15.0, c2.getCount("p"));
      assertEquals(4.0, c2.getCount("q"));
      assertEquals(7.0, c2.getCount("w"));
      assertEquals(9.5, c2.getCount("x"));

      Counters.subtractInPlace(c2, small_c);
      Counters.subtractInPlace(c2, small_c);
      Counters.retainNonZeros(c2);

      assertEquals(15.0, c2.getCount("p"));
      assertFalse(c2.containsKey("q"));
      assertEquals(7.0, c2.getCount("w"));
      assertEquals(9.5, c2.getCount("x"));
    }

    // serialize to Stream
    if (c instanceof Serializable) {
      try {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(baos));
        out.writeObject(c);
        out.close();

        // reconstitute
        byte[] bytes = baos.toByteArray();
        ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)));
        c = IOUtils.readObjectFromObjectStream(in);
        in.close();
        if (!this.integral) {
          assertEquals(-2.5, c.totalCount());
          assertEquals(-5.0, Counters.min(c));
          assertEquals("w", Counters.argmin(c));
        }
        c.clear();
        if (!this.integral) {
          assertEquals(0.0, c.totalCount());
        }
      } catch (IOException ioe) {
        fail("IOException: " + ioe);
      } catch (ClassNotFoundException cce) {
      fail("ClassNotFoundException: " + cce);
      }
    }
  }

  public void testFactory() {
    Factory<Counter<String>> fcs = c.getFactory();
    Counter<String> c2 = fcs.create();
    c2.incrementCount("fr");
    c2.incrementCount("de");
    c2.incrementCount("es", -3);
    Counter<String> c3 = fcs.create();
    c3.decrementCount("es");
    Counter<String> c4 = fcs.create();
    c4.incrementCount("fr");
    c4.setCount("es", -3);
    c4.setCount("de", 1.0);
    assertEquals("Testing factory and counter equality", c2, c4);
    assertEquals("Testing factory", c2.totalCount(), -1.0);
    c3.addAll(c2);
    assertEquals(c3.keySet().size(), 3);
    assertEquals(c3.size(), 3);
    assertEquals("Testing addAll", -2.0, c3.totalCount());
  }

  public void testReturnValue() {
    c.setDefaultReturnValue(-1);
    assertEquals(c.defaultReturnValue(), -1.0);
    assertEquals(c.getCount("-!-"), -1.0);
    c.setDefaultReturnValue(0.0);
    assertEquals(c.getCount("-!-"), 0.0);
  }

  public void testSetCount() {
    c.clear();
    c.setCount("p", 0);
    c.setCount("q", 2);
    assertEquals("Failed setCount", 2.0, c.totalCount());
    assertEquals("Failed setCount", 2.0, c.getCount("q"));
  }

  public void testIncrement() {
    c.clear();
    assertEquals(0., c.getCount("r"));
    assertEquals(1., c.incrementCount("r"));
    assertEquals(1., c.getCount("r"));
    c.setCount("p", 0);
    c.setCount("q", 2);
    assertEquals(true, c.containsKey("q"));
    assertEquals(false, c.containsKey("!!!"));
    assertEquals(0., c.getCount("p"));
    assertEquals(1., c.incrementCount("p"));
    assertEquals(1., c.getCount("p"));
    assertEquals(4., c.totalCount());
    c.decrementCount("s", 5.0);
    assertEquals(-5.0, c.getCount("s"));
    c.remove("s");
    assertEquals(4.0, c.totalCount());
  }

  public void testIncrement2() {
    c.clear();
    c.setCount("p", .5);
    c.setCount("q", 2);
    if (integral) {
      assertEquals(3., c.incrementCount("p", 3.5));
      assertEquals(3., c.getCount("p"));
      assertEquals(5., c.totalCount());
    } else {
      assertEquals(4., c.incrementCount("p", 3.5));
      assertEquals(4., c.getCount("p"));
      assertEquals(6., c.totalCount());
    }
  }

  public void testLogIncrement() {
    c.clear();
    c.setCount("p", Math.log(.5));
    // System.out.println(c.getCount("p"));
    c.setCount("q", Math.log(.2));
    // System.out.println(c.getCount("q"));
    if (integral) {
      // 0.5 gives 0 and 0.3 gives -1, so -1
      double ans = c.logIncrementCount("p", Math.log(.3));
      // System.out.println(ans);
      assertEquals(0., ans, .0001);
      assertEquals(-1., c.totalCount(), .0001);
    } else {
      assertEquals(Math.log(.5+.3), c.logIncrementCount("p", Math.log(.3)), .0001);
      assertEquals(Math.log(.5+.3)+Math.log(.2), c.totalCount(), .0001);
    }
  }

  public void testEntrySet() {
    c.clear();
    c.setCount("r", 3.0);
    c.setCount("p", 1.0);
    c.setCount("q", 2.0);
    c.setCount("s", 4.0);

    assertEquals(10.0, c.totalCount());
    assertEquals(1.0, c.getCount("p"));
    for (Map.Entry<String,Double> entry : c.entrySet()) {
      if (entry.getKey().equals("p")) {
        assertEquals(1.0, entry.setValue(3.0));
        assertEquals(3.0, entry.getValue());
      }
    }
    assertEquals(3.0, c.getCount("p"));
    assertEquals(12.0, c.totalCount());
    Collection<Double> vals = c.values();
    double tot = 0.0;
    for (double d : vals) {
      tot += d;
    }
    assertEquals("Testing values()", 12.0, tot);
  }

  public void testComparators() {
    c.clear();
    c.setCount("b", 3.0);
    c.setCount("p", -5.0);
    c.setCount("a", 2.0);
    c.setCount("s", 4.0);

    List<String> list = new ArrayList<String>(c.keySet());

    Comparator<String> cmp = Counters.toComparator(c);
    Collections.sort(list, cmp);
    assertEquals(4, list.size());
    assertEquals("p", list.get(0));
    assertEquals("a", list.get(1));
    assertEquals("b", list.get(2));
    assertEquals("s", list.get(3));

    Comparator<String> cmp2 = Counters.toComparatorDescending(c);
    Collections.sort(list, cmp2);
    assertEquals(4, list.size());
    assertEquals("p", list.get(3));
    assertEquals("a", list.get(2));
    assertEquals("b", list.get(1));
    assertEquals("s", list.get(0));

    Comparator<String> cmp3 = Counters.toComparator(c, true, true);
    Collections.sort(list, cmp3);
    assertEquals(4, list.size());
    assertEquals("p", list.get(3));
    assertEquals("a", list.get(0));
    assertEquals("b", list.get(1));
    assertEquals("s", list.get(2));

    Comparator<String> cmp4 = Counters.toComparator(c, false, true);
    Collections.sort(list, cmp4);
    assertEquals(4, list.size());
    assertEquals("p", list.get(0));
    assertEquals("a", list.get(3));
    assertEquals("b", list.get(2));
    assertEquals("s", list.get(1));

    Comparator<String> cmp5 = Counters.toComparator(c, false, false);
    Collections.sort(list, cmp5);
    assertEquals(4, list.size());
    assertEquals("p", list.get(3));
    assertEquals("a", list.get(2));
    assertEquals("b", list.get(1));
    assertEquals("s", list.get(0));
  }

  public void testClear() {
    c.incrementCount("xy", 30);
    c.clear();
    assertEquals(0.0, c.totalCount());
  }


}
