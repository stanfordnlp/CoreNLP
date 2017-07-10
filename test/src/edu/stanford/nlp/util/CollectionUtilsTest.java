package edu.stanford.nlp.util;

import java.io.File;
import java.util.*;

import org.junit.Assert;
import junit.framework.TestCase;

public class CollectionUtilsTest extends TestCase {

  File outputDir;

  protected void setUp() throws Exception {
    super.setUp();
    outputDir = File.createTempFile("IOUtilsTest", ".dir");
    assertTrue(outputDir.delete());
    assertTrue(outputDir.mkdir());
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    this.remove(this.outputDir);
  }

  protected void remove(File file) {
    if (file.isDirectory()) {
      for (File child: file.listFiles()) {
        this.remove(child);
      }
    }
    file.delete();
  }

  public void testLoadCollection() throws Exception {
    File collectionFile = new File(this.outputDir, "string.collection");
    StringUtils.printToFile(collectionFile, "-1\n42\n122\n-3.14");

    Set<String> actualSet = new HashSet<>();
    CollectionUtils.loadCollection(collectionFile, String.class, actualSet);
    Set<String> expectedSet = new HashSet<>(Arrays.asList("-1 42 122 -3.14".split(" ")));
    Assert.assertEquals(expectedSet, actualSet);

    List<TestDouble> actualList = new ArrayList<>();
    actualList.add(new TestDouble("95.2"));
    CollectionUtils.loadCollection(collectionFile.getPath(), TestDouble.class, actualList);
    List<TestDouble> expectedList = new ArrayList<>();
    expectedList.add(new TestDouble("95.2"));
    expectedList.add(new TestDouble("-1"));
    expectedList.add(new TestDouble("42"));
    expectedList.add(new TestDouble("122"));
    expectedList.add(new TestDouble("-3.14"));
    Assert.assertEquals(expectedList, actualList);
  }

  public static class TestDouble {
    public double d;
    public TestDouble(String string) {
      this.d = Double.parseDouble(string);
    }
    public boolean equals(Object other) {
      return this.d == ((TestDouble)other).d;
    }
    public String toString() {
      return String.format("%f", this.d);
    }
  }

  public void testSorted() throws Exception {
    List<Integer> inputInts = Arrays.asList(5, 4, 3, 2, 1);
    List<Integer> expectedInts = Arrays.asList(1, 2, 3, 4, 5);
    Assert.assertEquals(expectedInts, CollectionUtils.sorted(inputInts));

    Set<String> inputStrings = new HashSet<>(Arrays.asList("d a c b".split(" ")));
    List<String> expectedStrings = Arrays.asList("a b c d".split(" "));
    Assert.assertEquals(expectedStrings, CollectionUtils.sorted(inputStrings));
  }

  public void testToList() {
    Iterable<String> iter = Iterables.take(Arrays.asList("a", "b", "c"), 2);
    Assert.assertEquals(Arrays.asList("a", "b"), CollectionUtils.toList(iter));
  }

  public void testToSet() {
    Iterable<String> iter = Iterables.drop(Arrays.asList("c", "a", "b", "a"), 1);
    Assert.assertEquals(new HashSet<String>(Arrays.asList("a", "b")), CollectionUtils.toSet(iter));
  }

  public void testGetNGrams() {
    List<String> items;
    List<List<String>> expected, actual;

    items = splitOne("a#b#c#d#e");
    expected = split("a b c d e");
    actual = CollectionUtils.getNGrams(items, 1, 1);
    Assert.assertEquals(expected, actual);

    items = splitOne("a#b#c#d#e");
    expected = split("a#b b#c c#d d#e");
    actual = CollectionUtils.getNGrams(items, 2, 2);
    Assert.assertEquals(expected, actual);

    items = splitOne("a#b#c#d#e");
    expected = split("a a#b b b#c c c#d d d#e e");
    actual = CollectionUtils.getNGrams(items, 1, 2);
    Assert.assertEquals(expected, actual);

    items = splitOne("a#b#c#d#e");
    expected = split("a#b#c#d a#b#c#d#e b#c#d#e");
    actual = CollectionUtils.getNGrams(items, 4, 6);
    Assert.assertEquals(expected, actual);

    items = splitOne("a#b#c#d#e");
    expected = new ArrayList<List<String>>();
    actual = CollectionUtils.getNGrams(items, 6, 6);
    Assert.assertEquals(expected, actual);
  }

  private static List<String> splitOne(String wordString) {
    return Arrays.asList(wordString.split("#"));
  }

  private static List<List<String>> split(String wordListsString) {
    List<List<String>> result = new ArrayList<>();
    for (String wordString: wordListsString.split(" ")) {
      result.add(splitOne(wordString));
    }
    return result;
  }

  public void testGetIndex(){
    int startIndex = 4;
    List<String> list = Arrays.asList("this","is","a","test","which","test","is","it");
    int index = CollectionUtils.getIndex(list, "test", startIndex);
    Assert.assertEquals(5,index);

    startIndex = 0;
    list = Arrays.asList("Biology","is","a","test","which","test","is","it");
    index = CollectionUtils.getIndex(list, "Biology", startIndex);
    Assert.assertEquals(0,index);
  }

  public void testContainsAny(){
    List<String> list = Arrays.asList("this","is","a","test","which","test","is","it");
    List<String> toCheck = Arrays.asList("a","which");
    Assert.assertTrue(CollectionUtils.containsAny(list, toCheck));

    toCheck = Arrays.asList("not","a");
    Assert.assertTrue(CollectionUtils.containsAny(list, toCheck));

    toCheck = Arrays.asList("not","here");
    Assert.assertFalse(CollectionUtils.containsAny(list, toCheck));
  }

  public void testIsSubList(){

    List<String> t1 = Arrays.asList("this","is","test");
    List<String> t2 = Arrays.asList("well","this","this","again","is","test");
    Assert.assertTrue(CollectionUtils.isSubList(t1, t2));

    t1 = Arrays.asList("test","this","is");
    Assert.assertFalse(CollectionUtils.isSubList(t1, t2));

  }

  public void testMaxIndex(){
    List<Integer> t1 = Arrays.asList(2,-1,4);
    Assert.assertEquals(2, CollectionUtils.maxIndex(t1));
  }

  public void testIteratorConcatEmpty(){
    Iterator<String> iter = CollectionUtils.concatIterators();
    assertFalse(iter.hasNext());
  }

  public void testIteratorConcatSingleIter(){
    Iterator<String> iter = CollectionUtils.concatIterators(new ArrayList<String>(){{ add("foo"); }}.iterator());
    assertTrue(iter.hasNext());
    assertEquals("foo", iter.next());
    assertFalse(iter.hasNext());
  }

  public void testIteratorConcatMultiIter(){
    Iterator<String> iter = CollectionUtils.concatIterators(
        new ArrayList<String>(){{ add("foo"); }}.iterator(),
        new ArrayList<String>(){{ add("bar"); add("baz"); }}.iterator(),
        new ArrayList<String>(){{ add("boo"); }}.iterator()
        );
    assertTrue(iter.hasNext()); assertEquals("foo", iter.next());
    assertTrue(iter.hasNext()); assertEquals("bar", iter.next());
    assertTrue(iter.hasNext()); assertEquals("baz", iter.next());
    assertTrue(iter.hasNext()); assertEquals("boo", iter.next());
    assertFalse(iter.hasNext());
  }

  public void testIteratorConcatEmptyIter(){
    Iterator<String> iter = CollectionUtils.concatIterators(
        new ArrayList<String>(){{ add("foo"); }}.iterator(),
        new ArrayList<String>(){{ }}.iterator(),
        new ArrayList<String>(){{ add("boo"); }}.iterator()
    );
    assertTrue(iter.hasNext()); assertEquals("foo", iter.next());
    assertTrue(iter.hasNext()); assertEquals("boo", iter.next());
    assertFalse(iter.hasNext());
  }

  public void testIteratorConcaatRemove(){
    ArrayList<String> a = new ArrayList<String>(){{ add("foo"); }};
    ArrayList<String> b = new ArrayList<String>(){{ }};
    ArrayList<String> c = new ArrayList<String>(){{ add("bar"); add("baz"); }};
    Iterator<String> iter = CollectionUtils.concatIterators( a.iterator(), b.iterator(), c.iterator() );
    assertTrue(iter.hasNext()); assertEquals("foo", iter.next());
    assertTrue(iter.hasNext()); assertEquals("bar", iter.next());
    iter.remove();
    assertTrue(iter.hasNext()); assertEquals("baz", iter.next());
    assertEquals(new ArrayList<String>(){{ add("foo"); }}, a);
    assertEquals(new ArrayList<String>(){{ }}, b);
    assertEquals(new ArrayList<String>(){{ add("baz"); }}, c);
  }

}
