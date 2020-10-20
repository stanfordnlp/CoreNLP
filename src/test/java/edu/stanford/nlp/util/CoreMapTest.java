package edu.stanford.nlp.util;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import edu.stanford.nlp.ling.CoreAnnotations;
import junit.framework.TestCase;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.math.ArrayMath;


/**
 * Tests that the CoreMap TypesafeMap works as expected.
 *
 * @author dramage
 */
public class CoreMapTest extends TestCase {

  private static class StringA implements CoreAnnotation<String> {
    public Class<String> getType() { return String.class; } }

  private static class StringB implements CoreAnnotation<String> {
    public Class<String> getType() { return String.class; } }

  /** This class is used in CoreMapsTest, so it can't be private. */
  static class IntegerA implements CoreAnnotation<Integer> {
    public Class<Integer> getType() { return Integer.class; } }


  @SuppressWarnings("unchecked")
  public void testCoreMap() {
    CoreMap object = new ArrayCoreMap(0);

    assertFalse(object.containsKey(StringA.class));
    object.set(StringA.class, "stem");

    assertTrue(object.containsKey(StringA.class));
    assertEquals("stem", object.get(StringA.class));

    object.set(StringA.class, "hi");
    assertEquals("hi", object.get(StringA.class));

    assertEquals(null, object.get(IntegerA.class));
    object.set(IntegerA.class, 4);
    assertEquals(Integer.valueOf(4), object.get(IntegerA.class));

    object.set(StringB.class, "Yes");

    assertEquals("Wrong # objects", 3, object.keySet().size());
    assertEquals("Wrong keyset",
        new HashSet<Class<?>>(
            Arrays.asList(StringA.class, IntegerA.class, StringB.class)),
        object.keySet());

    assertEquals("Wrong remove value", Integer.valueOf(4),
        object.remove(IntegerA.class));

    assertEquals("Wrong # objects", 2, object.keySet().size());
    assertEquals("Wrong keyset",
        new HashSet<Class<?>>(Arrays.asList(StringA.class, StringB.class)),
        object.keySet());

    assertEquals("Wrong value", "hi", object.get(StringA.class));
    assertEquals("Wrong value", "Yes", object.get(StringB.class));

    assertEquals(null, object.set(IntegerA.class, 7));
    assertEquals(Integer.valueOf(7), object.get(IntegerA.class));
    assertEquals(Integer.valueOf(7), object.set(IntegerA.class, 3));
    assertEquals(Integer.valueOf(3), object.get(IntegerA.class));
  }


  public void testToShorterString() {
    ArrayCoreMap a = new ArrayCoreMap();
    a.set(CoreAnnotations.TextAnnotation.class, "Australia");
    a.set(CoreAnnotations.NamedEntityTagAnnotation.class, "LOCATION");
    a.set(CoreAnnotations.BeforeAnnotation.class, "  ");
    a.set(CoreAnnotations.PartOfSpeechAnnotation.class, "NNP");
    a.set(CoreAnnotations.ShapeAnnotation.class, "Xx");
    assertEquals("Incorrect toShorterString()", "[Text=Australia NamedEntityTag=LOCATION]",
            a.toShorterString("Text", "NamedEntityTag"));
    assertEquals("Incorrect toShorterString()", "[Text=Australia]",
            a.toShorterString("Text"));
    assertEquals("Incorrect toShorterString()",
            "[Text=Australia NamedEntityTag=LOCATION Before=   PartOfSpeech=NNP Shape=Xx]",
            a.toShorterString());
  }

  public void testEquality() {
    CoreMap a = new ArrayCoreMap();
    CoreMap b = new ArrayCoreMap();

    assertTrue(a.equals(b));
    assertTrue(a.hashCode() == b.hashCode());

    a.set(StringA.class, "hi");

    assertFalse(a.equals(b));
    assertFalse(a.hashCode() == b.hashCode());

    b.set(StringA.class, "hi");

    assertTrue(a.equals(b));
    assertTrue(a.hashCode() == b.hashCode());

    a.remove(StringA.class);

    assertFalse(a.equals(b));
    assertFalse(a.hashCode() == b.hashCode());
  }


  /**
   * This method is for comparing the speed of the ArrayCoreMap family and
   * HashMap. It tests random access speed for a fixed number of accesses, i,
   * for both a CoreLabel (can be swapped out for an ArrayCoreMap) and a
   * HashMap. Switching the order of testing (CoreLabel first or second) shows
   * that there's a slight advantage to the second loop, especially noticeable
   * for small i - this is due to some background java funky-ness, so we now
   * run 50% each way.
   */
  @SuppressWarnings({"StringEquality"})
  public static void main(String[] args) {
    @SuppressWarnings("unchecked")
    Class<CoreAnnotation<String>>[] allKeys = new Class[]{CoreAnnotations.TextAnnotation.class, CoreAnnotations.LemmaAnnotation.class,
        CoreAnnotations.PartOfSpeechAnnotation.class, CoreAnnotations.ShapeAnnotation.class, CoreAnnotations.NamedEntityTagAnnotation.class,
        CoreAnnotations.DocIDAnnotation.class, CoreAnnotations.ValueAnnotation.class, CoreAnnotations.CategoryAnnotation.class,
        CoreAnnotations.BeforeAnnotation.class, CoreAnnotations.AfterAnnotation.class, CoreAnnotations.OriginalTextAnnotation.class,
        CoreAnnotations.ArgumentAnnotation.class, CoreAnnotations.MarkingAnnotation.class
    };

    // how many iterations
    final int numBurnRounds = 10;
    final int numGoodRounds = 60;
    final int numIterations = 2000000;
    final int maxNumKeys = 12;
    double gains = 0.0;

    for (int numKeys = 1; numKeys <= maxNumKeys; numKeys++) {
      // the HashMap instance
      HashMap<String,String> hashmap = new HashMap<>(numKeys);

      // the CoreMap instance
      CoreMap coremap = new ArrayCoreMap(numKeys);

      // the set of keys to use
      String[] hashKeys = new String[numKeys];
      @SuppressWarnings("unchecked")
      Class<CoreAnnotation<String>>[] coreKeys = new Class[numKeys];
      for (int key = 0; key < numKeys; key++) {
        hashKeys[key] = allKeys[key].getSimpleName();
        coreKeys[key] = allKeys[key];
      }

      // initialize with default values
      for (int i = 0; i < numKeys; i++) {
        coremap.set(coreKeys[i], String.valueOf(i));
        hashmap.put(hashKeys[i], String.valueOf(i));
      }

      assert coremap.size() == numKeys;
      assert hashmap.size() == numKeys;

      // for storing results
      double[] hashTimings = new double[numGoodRounds];
      double[] coreTimings = new double[numGoodRounds];

      final Random rand = new Random(0);
      boolean foundEqual = false;
      for (int round = 0; round < numBurnRounds + numGoodRounds; round++) {
        System.err.print(".");
        if (round % 2 == 0) {
          // test timings on hashmap first
          final long hashStart = System.nanoTime();
          final int length = hashKeys.length;
          String last = null;
          for (int i = 0; i < numIterations; i++) {
            int key = rand.nextInt(length);
            String val = hashmap.get(hashKeys[key]);
            if (val == last) {
              foundEqual = true;
            }
            last = val;
          }
          if (round >= numBurnRounds) {
            hashTimings[round-numBurnRounds] = (System.nanoTime() - hashStart) / 1000000000.0;
          }
        }
        { // test timings on coremap
          final long coreStart = System.nanoTime();
          final int length = coreKeys.length;
          String last = null;
           for (int i = 0; i < numIterations; i++) {
            int key = rand.nextInt(length);
            String val = coremap.get(coreKeys[key]);
             if (val == last) {
               foundEqual = true;
             }
             last = val;
          }
          if (round >= numBurnRounds) {
            coreTimings[round-numBurnRounds] = (System.nanoTime() - coreStart) / 1000000000.0;
          }
        }
        if (round % 2 == 1) {
          // test timings on hashmap second
          final long hashStart = System.nanoTime();
          final int length = hashKeys.length;
          String last = null;
          for (int i = 0; i < numIterations; i++) {
            int key = rand.nextInt(length);
            String val = hashmap.get(hashKeys[key]);
            if (val == last) {
              foundEqual = true;
            }
            last = val;
          }
          if (round >= numBurnRounds) {
            hashTimings[round-numBurnRounds] = (System.nanoTime() - hashStart) / 1000000000.0;
          }
        }
}
      if (foundEqual) { System.err.print(" [found equal]"); }
      System.err.println();

      double hashMean = ArrayMath.mean(hashTimings);
      double coreMean = ArrayMath.mean(coreTimings);
      double percentDiff = (hashMean - coreMean) / hashMean * 100.0;
      NumberFormat nf = new DecimalFormat("0.00");
      System.out.println("HashMap @ " + numKeys + " keys: "+ hashMean +
              " secs/2million gets");
      System.out.println("CoreMap @ " + numKeys + " keys: "+ coreMean +
              " secs/2million gets (" + nf.format(Math.abs(percentDiff)) + "% " +
              (percentDiff >= 0.0 ? "faster" : "slower") + ")");
      gains += percentDiff;
    }
    System.out.println();
    gains = gains / maxNumKeys;
    System.out.println("Average: " + Math.abs(gains) + "% " +
            (gains >= 0.0 ? "faster" : "slower") + ".");
  }

}
