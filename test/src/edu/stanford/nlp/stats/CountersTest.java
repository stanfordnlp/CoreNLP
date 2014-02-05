package edu.stanford.nlp.stats;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.util.Pair;

import junit.framework.Assert;
import junit.framework.TestCase;

public class CountersTest extends TestCase {

  private ClassicCounter<String> c1;
  private ClassicCounter<String> c2;

  private static final double TOLERANCE = 0.001;

  @Override
  protected void setUp() {
    c1 = new ClassicCounter<String>();
    c1.setCount("p", 1.0);
    c1.setCount("q", 2.0);
    c1.setCount("r", 3.0);
    c1.setCount("s", 4.0);
    c2 = new ClassicCounter<String>();
    c2.setCount("p", 5.0);
    c2.setCount("q", 6.0);
    c2.setCount("r", 7.0);
    c2.setCount("t", 8.0);
  }

  public void testUnion() {
    ClassicCounter<String> c3 = Counters.union(c1, c2);
    assertEquals(c3.getCount("p"), 6.0);
    assertEquals(c3.getCount("s"), 4.0);
    assertEquals(c3.getCount("t"), 8.0);
    assertEquals(c3.totalCount(), 36.0);
  }

  public void testIntersection() {
    Counter<String> c3 = Counters.intersection(c1, c2);
    assertEquals(c3.getCount("p"), 1.0);
    assertEquals(c3.getCount("q"), 2.0);
    assertEquals(c3.getCount("s"), 0.0);
    assertEquals(c3.getCount("t"), 0.0);
    assertEquals(c3.totalCount(), 6.0);
  }

  public void testProduct() {
    Counter<String> c3 = Counters.product(c1, c2);
    assertEquals(c3.getCount("p"), 5.0);
    assertEquals(c3.getCount("q"), 12.0);
    assertEquals(c3.getCount("r"), 21.0);
    assertEquals(c3.getCount("s"), 0.0);
    assertEquals(c3.getCount("t"), 0.0);
  }

  public void testDotProduct() {
    double d1 = Counters.dotProduct(c1, c2);
    assertEquals(d1, 38.0);
    double d2 = Counters.dotProduct(c1, c1);
    assertEquals(d2, 30.0);
  }

  public void testAbsoluteDifference() {
    Counter<String> c3 = Counters.absoluteDifference(c1, c2);
    assertEquals(c3.getCount("p"), 4.0);
    assertEquals(c3.getCount("q"), 4.0);
    assertEquals(c3.getCount("r"), 4.0);
    assertEquals(c3.getCount("s"), 4.0);
    assertEquals(c3.getCount("t"), 8.0);
    Counter<String> c4 = Counters.absoluteDifference(c2, c1);
    assertEquals(c4.getCount("p"), 4.0);
    assertEquals(c4.getCount("q"), 4.0);
    assertEquals(c4.getCount("r"), 4.0);
    assertEquals(c4.getCount("s"), 4.0);
    assertEquals(c4.getCount("t"), 8.0);
  }

  @SuppressWarnings("unchecked")
  public void testSerialization() {
    try {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      ObjectOutputStream oout = new ObjectOutputStream(bout);
      oout.writeObject(c1);
      byte[] bleh = bout.toByteArray();
      ByteArrayInputStream bin = new ByteArrayInputStream(bleh);
      ObjectInputStream oin = new ObjectInputStream(bin);
      ClassicCounter<String> c3 = (ClassicCounter<String>) oin.readObject();
      assertEquals(c3, c1);
    } catch (Exception e) {
      Assert.fail(e.getMessage());
    }
  }

  public void testMin() {
    assertEquals(Counters.min(c1), 1.0);
    assertEquals(Counters.min(c2), 5.0);
  }

  public void testArgmin() {
    assertEquals(Counters.argmin(c1), "p");
    assertEquals(Counters.argmin(c2), "p");
  }

  public void testL2Norm() {
    ClassicCounter<String> c = new ClassicCounter<String>();
    c.incrementCount("a", 3);
    c.incrementCount("b", 4);
    assertEquals(5.0, Counters.L2Norm(c), TOLERANCE);
    c.incrementCount("c", 6);
    c.incrementCount("d", 4);
    c.incrementCount("e", 2);
    assertEquals(9.0, Counters.L2Norm(c), TOLERANCE);
  }

  @SuppressWarnings({ "ConstantMathCall" })
  public void testLogNormalize() {
    ClassicCounter<String> c = new ClassicCounter<String>();
    c.incrementCount("a", Math.log(4.0));
    c.incrementCount("b", Math.log(2.0));
    c.incrementCount("c", Math.log(1.0));
    c.incrementCount("d", Math.log(1.0));
    Counters.logNormalizeInPlace(c);
    assertEquals(c.getCount("a"), -0.693, TOLERANCE);
    assertEquals(c.getCount("b"), -1.386, TOLERANCE);
    assertEquals(c.getCount("c"), -2.079, TOLERANCE);
    assertEquals(c.getCount("d"), -2.079, TOLERANCE);
    assertEquals(Counters.logSum(c), 0.0, TOLERANCE);
  }

  public void testL2Normalize() {
    ClassicCounter<String> c = new ClassicCounter<String>();
    c.incrementCount("a", 4.0);
    c.incrementCount("b", 2.0);
    c.incrementCount("c", 1.0);
    c.incrementCount("d", 2.0);
    Counter<String> d = Counters.L2Normalize(c);
    assertEquals(d.getCount("a"), 0.8, TOLERANCE);
    assertEquals(d.getCount("b"), 0.4, TOLERANCE);
    assertEquals(d.getCount("c"), 0.2, TOLERANCE);
    assertEquals(d.getCount("d"), 0.4, TOLERANCE);
  }

  public void testRetainAbove() {
    c1 = new ClassicCounter<String>();
    c1.incrementCount("a", 1.1);
    c1.incrementCount("b", 1.0);
    c1.incrementCount("c", 0.9);
    c1.incrementCount("d", 0);
    Set<String> removed = Counters.retainAbove(c1, 1.0);
    Set<String> expected = new HashSet<String>();
    expected.add("c");
    expected.add("d");
    assertEquals(expected, removed);
    assertEquals(1.1, c1.getCount("a"));
    assertEquals(1.0, c1.getCount("b"));
    assertFalse(c1.containsKey("c"));
    assertFalse(c1.containsKey("d"));
  }

  private final String[] ascending = { "e", "d", "a", "b", "c" };

  public void testToSortedList() {
    c1 = new ClassicCounter<String>();
    c1.incrementCount("a", 0.9);
    c1.incrementCount("b", 1.0);
    c1.incrementCount("c", 1.5);
    c1.incrementCount("d", 0.0);
    c1.incrementCount("e", -2.0);
    List<String> ascendList = Counters.toSortedList(c1, true);
    List<String> descendList = Counters.toSortedList(c1);
    for (int i = 0; i < ascending.length; i++) {
      assertEquals(ascending[i], ascendList.get(i));
      assertEquals(ascending[i], descendList.get(ascending.length - i - 1));
    }
  }

  public void testRetainTop() {
    c1 = new ClassicCounter<String>();
    c1.incrementCount("a", 0.9);
    c1.incrementCount("b", 1.0);
    c1.incrementCount("c", 1.5);
    c1.incrementCount("d", 0.0);
    c1.incrementCount("e", -2.0);
    Counters.retainTop(c1, 3);
    assertEquals(3, c1.size());
    assertTrue(c1.containsKey("a"));
    assertFalse(c1.containsKey("d"));
    Counters.retainTop(c1, 1);
    assertEquals(1, c1.size());
    assertTrue(c1.containsKey("c"));
    assertEquals(1.5, c1.getCount("c"));
  }

  public void testPointwiseMutualInformation() {
    Counter<String> x = new ClassicCounter<String>();
    x.incrementCount("0", 0.8);
    x.incrementCount("1", 0.2);

    Counter<Integer> y = new ClassicCounter<Integer>();
    y.incrementCount(0, 0.25);
    y.incrementCount(1, 0.75);

    Counter<Pair<String, Integer>> joint;
    joint = new ClassicCounter<Pair<String, Integer>>();
    joint.incrementCount(new Pair<String, Integer>("0", 0), 0.1);
    joint.incrementCount(new Pair<String, Integer>("0", 1), 0.7);
    joint.incrementCount(new Pair<String, Integer>("1", 0), 0.15);
    joint.incrementCount(new Pair<String, Integer>("1", 1), 0.05);

    // Check that correct PMI values are calculated, using tables from
    // http://en.wikipedia.org/wiki/Pointwise_mutual_information
    double pmi;
    Pair<String, Integer> pair;

    pair = new Pair<String, Integer>("0", 0);
    pmi = Counters.pointwiseMutualInformation(x, y, joint, pair);
    assertEquals(-1, pmi, 10e-5);

    pair = new Pair<String, Integer>("0", 1);
    pmi = Counters.pointwiseMutualInformation(x, y, joint, pair);
    assertEquals(0.222392421, pmi, 10e-5);

    pair = new Pair<String, Integer>("1", 0);
    pmi = Counters.pointwiseMutualInformation(x, y, joint, pair);
    assertEquals(1.584962501, pmi, 10e-5);

    pair = new Pair<String, Integer>("1", 1);
    pmi = Counters.pointwiseMutualInformation(x, y, joint, pair);
    assertEquals(-1.584962501, pmi, 10e-5);
  }

  public void testToSortedString() {
    Counter<String> c = new ClassicCounter<String>();
    c.setCount("b", 0.25);
    c.setCount("a", 0.5);
    c.setCount("c", 1.0);

    // check full argument version
    String result = Counters.toSortedString(c, 5, "%s%.1f", ":", "{%s}");
    assertEquals("{c1.0:a0.5:b0.3}", result);

    // check version with no wrapper
    result = Counters.toSortedString(c, 2, "%2$f %1$s", "\n");
    assertEquals("1.000000 c\n0.500000 a", result);

    // check some equivalences to other Counters methods
    int k = 2;
    result = Counters.toSortedString(c, k, "%s=%s", ", ", "[%s]");
    assertEquals(Counters.toString(c, k), result);
    assertEquals(Counters.toBiggestValuesFirstString(c, k), result);
    result = Counters.toSortedString(c, k, "%2$g\t%1$s", "\n", "%s\n");
    assertEquals(Counters.toVerticalString(c, k), result);

    // test sorting by keys
    result = Counters.toSortedByKeysString(c, "%s=>%.2f", "; ", "<%s>");
    assertEquals("<a=>0.50; b=>0.25; c=>1.00>", result);
  }

  public void testHIndex() {
    // empty counter
    Counter<String> c = new ClassicCounter<String>();
    assertEquals(0, Counters.hIndex(c));

    // two items with 2 or more citations
    c.setCount("X", 3);
    c.setCount("Y", 2);
    c.setCount("Z", 1);
    assertEquals(2, Counters.hIndex(c));

    // 14 items with 14 or more citations
    for (int i = 0; i < 14; ++i) {
      c.setCount(String.valueOf(i), 15);
    }
    assertEquals(14, Counters.hIndex(c));

    // 15 items with 15 or more citations
    c.setCount("15", 15);
    assertEquals(15, Counters.hIndex(c));
  }

  public void testAddInPlaceCollection() {
    // initialize counter
    setUp();
    List<String> collection = new ArrayList<String>();
    collection.add("p");
    collection.add("p");
    collection.add("s");
    Counters.addInPlace(c1, collection);
    assertEquals(3.0, c1.getCount("p"));
    assertEquals(5.0, c1.getCount("s"));

  }

  public void testRemoveKeys() {
    setUp();
    Collection<String> c = new ArrayList<String>();
    c.add("p");
    c.add("r");
    c.add("s");
    Counters.removeKeys(c1, c);
    assertEquals(c1.keySet().size(), 1);
    Object[] keys = c1.keySet().toArray();
    assertEquals(keys[0], "q");
  }

  public void testRetainTopMass() {
    setUp();
    System.out.println(Counters.toString(c1, c1.size()));
    Counters.retainTopMass(c1, 3);
    assertEquals(c1.keySet().toArray()[0], "s");
    assertEquals(c1.size(), 1);

  }

  public void testDivideInPlace() {
    TwoDimensionalCounter<String, String> a = new TwoDimensionalCounter<String, String>();
    a.setCount("a", "b", 1);
    a.setCount("a", "c", 1);
    a.setCount("c", "a", 1);
    a.setCount("c", "b", 1);
    Counters.divideInPlace(a, a.totalCount());
    assertEquals(1.0, a.totalCount());
    assertEquals(0.25, a.getCount("a", "b"));
  }

  public void testPearsonsCorrelationCoefficient(){
    setUp();
    Counters.pearsonsCorrelationCoefficient(c1, c2);
  }
  
  public void testToTiedRankCounter(){
    setUp();
    c1.setCount("t",1.0);
    c1.setCount("u",1.0);
    c1.setCount("v",2.0);
    c1.setCount("z",4.0);
    Counter<String> rank = Counters.toTiedRankCounter(c1);
    assertEquals(1.5, rank.getCount("z"));
    assertEquals(7.0, rank.getCount("t"));
  }
}
