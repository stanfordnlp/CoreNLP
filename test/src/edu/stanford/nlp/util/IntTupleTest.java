package edu.stanford.nlp.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class IntTupleTest {
  IntTuple sut6Tuple;
  IntTuple sut1Tuple;

  @Before
  public void setup() {
    int[] nrs = {1, 2, 3, 5, 7, 11};
    this.sut6Tuple = new IntTuple(nrs);
    this.sut1Tuple = new IntTuple(13);
  }

  @Test
  public void testCompareToSameLength() {
  }

  @Test
  public void testCompareTo() {
    int[] nrs = {1, 2, 3, 5, 7, 11, 12, 13};
    IntTuple sut6TupleExtended = new IntTuple(nrs);
    int[] nrs2 = {1, 8, 3, 5, 7, 11};
    IntTuple sut6TupleChanged = new IntTuple(nrs2);

    // Tuples are of equal length 
    assertEquals(0, sut6Tuple.compareTo(sut6Tuple));
    assertEquals(-1, sut6Tuple.compareTo(sut6TupleChanged));
    assertEquals(1, sut6TupleChanged.compareTo(sut6Tuple));

    // Not of equal length and common part is equal
    assertEquals(-1, sut6Tuple.compareTo(sut6TupleExtended));
    assertEquals(1, sut6TupleExtended.compareTo(sut6Tuple));
  }
  
  @Test
  public void testSet() {
    sut6Tuple.set(0, -1);
    assertEquals(-1, sut6Tuple.get(0));
  }
  
  @Test
  public void testShiftLeft() {
    sut6Tuple.shiftLeft();
    assertEquals(2, sut6Tuple.get(0));
    // When shifting left, the leftmost value is lost and the rightmost
    // index then gets filled with a 0
    assertEquals(0, sut6Tuple.get(5));
  }

  @Test
  public void testGetCopy() {
    assertEquals(sut6Tuple, sut6Tuple.getCopy());
  }

  @Test
  public void testElems() {
    int[] nrs = {1, 2, 3, 5, 7, 11};
    assertArrayEquals(nrs, sut6Tuple.elems());
  }
  
  @Test
  public void testEquals() {
    int[] nrs = {1, 2, 3, 4, 5, 6};
    IntTuple sut6TupleDifferentVals = new IntTuple(nrs);
    assertFalse("Can only be equals along type hierarchy", sut6Tuple.equals("not tuple"));
    assertFalse("Same n in n-tuple", sut6Tuple.equals(sut1Tuple));
    assertFalse("Values should be equal", sut6Tuple.equals(sut6TupleDifferentVals));
  }

  @Test
  public void testHashCode() {
    int hashCode = sut6Tuple.hashCode();
    assertTrue(hashCode > 0);
  }
  
  @Test
  public void testGetIntTuple() {
    List<Integer> integers = new ArrayList<Integer>();
    integers.add(0);
    integers.add(1);
    integers.add(3);
    IntTuple tuple = IntTuple.getIntTuple(integers);
    assertEquals(3, tuple.length());
    assertEquals(0, tuple.get(0));
    assertEquals(1, tuple.get(1));
    assertEquals(3, tuple.get(2));
  }
  
  @Test
  public void testToString() {
    String stringified = sut6Tuple.toString();
    assertEquals("1 2 3 5 7 11", stringified);
  }
  
  @Test
  public void testConcat() {
    IntTuple concat = sut6Tuple.concat(sut6Tuple, sut6Tuple);
    assertEquals(12, concat.length());
    assertEquals(1, concat.get(0));
    assertEquals(11, concat.get(11));
  }

}
