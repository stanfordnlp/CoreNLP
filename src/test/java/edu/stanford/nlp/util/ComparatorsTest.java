package edu.stanford.nlp.util;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author Christopher Manning
 */
public class ComparatorsTest extends TestCase {

  public void testNullSafeComparator() {
    Comparator<Integer> comp = Comparators.nullSafeNaturalComparator();
    assertEquals(0, comp.compare(null, null));
    assertEquals(-1, comp.compare(null, Integer.valueOf(42)));
    assertEquals(1, comp.compare(Integer.valueOf(42), null));
    assertEquals(-1, comp.compare(11, 18));
    assertEquals(0, comp.compare(11, 11));
  }

  public void testListComparator() {
    Comparator<List<String>> lc = Comparators.getListComparator();
    String[] one = {"hello", "foo"};
    String[] two = {"hi", "foo"};
    String[] three = {"hi", "foo", "bar" };
    assertTrue(lc.compare(Arrays.asList(one), Arrays.asList(one)) == 0);
    assertTrue(lc.compare(Arrays.asList(one), Arrays.asList(two)) < 0);
    assertTrue(lc.compare(Arrays.asList(one), Arrays.asList(three)) < 0);
    assertTrue(lc.compare(Arrays.asList(three), Arrays.asList(two)) > 0);
  }

  private static <C extends Comparable> void compare(C[] a1, C[] a2) {
    System.out.printf("compare(%s, %s) = %d%n",
                      Arrays.toString(a1),
                      Arrays.toString(a2),
                      ArrayUtils.compareArrays(a1, a2));
  }

  public void testArrayComparator() {
    Comparator<Boolean[]> ac = Comparators.getArrayComparator();
    assertTrue(ac.compare(new Boolean[]{true, false, true,},
            new Boolean[]{true, false, true,}) == 0);
    assertTrue(ac.compare(new Boolean[]{true, false, true,},
            new Boolean[]{true, false,}) > 0);
    assertTrue(ac.compare(new Boolean[]{true, false, true,},
            new Boolean[]{true, false, true, false}) < 0);
    assertTrue(ac.compare(new Boolean[]{false, false, true,},
            new Boolean[]{true, false, true,}) < 0);
  }

}