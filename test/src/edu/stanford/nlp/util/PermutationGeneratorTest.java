package edu.stanford.nlp.util;

import java.math.BigInteger;
import java.util.*;

import junit.framework.TestCase;
import static org.junit.Assert.assertArrayEquals;


public class PermutationGeneratorTest extends TestCase {
  private static final int[][] permutations = {
    {0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
  };
  private final PermutationGenerator generator = new PermutationGenerator(3);

  public void testAll() {
    assertEquals(generator.getTotal(), BigInteger.valueOf(permutations.length));

    assertTrue(generator.hasNext());
    int[] sharedPermutation = generator.getNext();

    generator.reset();
    for (int i = 0; i < permutations.length; i++) {
      assertTrue(generator.hasNext());
      assertEquals(generator.getNumLeft(), BigInteger.valueOf(permutations.length - i));

      int[] permutation = generator.getNext();
      assertArrayEquals(permutations[i], permutation);
      assertSame(sharedPermutation, permutation);
    }
    assertFalse(generator.hasNext());
    assertEquals(generator.getNumLeft(), BigInteger.ZERO);

    assertEquals(generator.getTotal(), BigInteger.valueOf(permutations.length));

    int[][] generated = new int[permutations.length][];
    generator.reset();
    for (int i = 0; i < permutations.length; i++) {
      assertTrue(generator.hasNext());
      assertEquals(generator.getNumLeft(), BigInteger.valueOf(permutations.length - i));

      generated[i] = generator.next();
    }
    assertFalse(generator.hasNext());
    try {
      generator.next();
      fail("Failed to throw exception");
    } catch (NoSuchElementException nse) {
      // this is correct
    } catch (Exception e) {
      fail("Threw wrong exception");
    }
    assertEquals(generator.getNumLeft(), BigInteger.ZERO);
    for (int i = 0; i < permutations.length; i++) {
      assertArrayEquals(generated[i], permutations[i]);
    }
  }

  public void testPermutation() {
    int i = 0;
    for (int[] perm : new Permutation(3)) {
      assertArrayEquals(permutations[i], perm);
      i++;
    }
    assertEquals(i, 6);
  }
}
