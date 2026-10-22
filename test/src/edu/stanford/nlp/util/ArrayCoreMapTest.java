package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;

/**
 * Test various operations of the ArrayCoreMap: equals, toString, etc.
 */
public class ArrayCoreMapTest {

  @Test
  public void testCreate() {
    ArrayCoreMap foo = new ArrayCoreMap();
    Assert.assertEquals(0, foo.size());
  }

  @Test
  public void testGetAndSet() {
    ArrayCoreMap foo = new ArrayCoreMap();
    Assert.assertEquals(0, foo.size());

    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    Assert.assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertNull(foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertNull(foo.get(CoreAnnotations.ParagraphsAnnotation.class));
    Assert.assertEquals(1, foo.size());

    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "F");
    Assert.assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertNull(foo.get(CoreAnnotations.ParagraphsAnnotation.class));
    Assert.assertEquals(2, foo.size());

    List<CoreMap> paragraphs = new ArrayList<>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(CoreAnnotations.TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs);
    Assert.assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    // will test equality of the CoreMaps in another test
    Assert.assertEquals(3, foo.size());
  }

  @SuppressWarnings("SimplifiableAssertion")
  @Test
  public void testSimpleEquals() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(CoreAnnotations.TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs);

    ArrayCoreMap bar = new ArrayCoreMap();
    bar.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs);
    Assert.assertEquals(foo, bar);
    Assert.assertEquals(bar, foo);
    Assert.assertFalse(foo.equals(f1));
    Assert.assertFalse(foo.equals(f2));
    Assert.assertEquals(f1, f1);
    Assert.assertFalse(f1.equals(f2));
  }

  /**
   * Test that neither hashCode() nor toString() hang
   */
  @Test
  public void testKeySet() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "NN");
    foo.set(CoreAnnotations.DocIDAnnotation.class, null);
    Assert.assertTrue(foo.keySet().contains(CoreAnnotations.TextAnnotation.class));
    Assert.assertTrue(foo.keySet().contains(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertTrue(foo.keySet().contains(CoreAnnotations.DocIDAnnotation.class));
    Assert.assertFalse(foo.keySet().contains(CoreAnnotations.TokensAnnotation.class));
  }

  /**
   * Test that neither hashCode() nor toString() hang
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Test
  public void testNoHanging() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(CoreAnnotations.TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs);

    foo.toString();
    foo.hashCode();
  }

  @SuppressWarnings("SimplifiableAssertion")
  @Test
  public void testRemove() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "F");
    Assert.assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertEquals(2, foo.size());

    foo.remove(CoreAnnotations.TextAnnotation.class);
    Assert.assertEquals(1, foo.size());
    Assert.assertNull(foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));

    foo.set(CoreAnnotations.TextAnnotation.class, "bar");
    Assert.assertEquals("bar", foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    Assert.assertEquals(2, foo.size());

    foo.remove(CoreAnnotations.TextAnnotation.class);
    Assert.assertEquals(1, foo.size());
    Assert.assertNull(foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));

    foo.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
    Assert.assertEquals(0, foo.size());
    Assert.assertNull(foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertNull(foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));

    // Removing an element that doesn't exist
    // shouldn't blow up on us in any way
    foo.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
    Assert.assertEquals(0, foo.size());
    Assert.assertNull(foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertNull(foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));

    // after removing all sorts of stuff, the original ArrayCoreMap
    // should now be equal to a new empty one
    ArrayCoreMap bar = new ArrayCoreMap();
    Assert.assertEquals(foo, bar);

    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "F");
    bar.set(CoreAnnotations.TextAnnotation.class, "foo");
    Assert.assertFalse(foo.equals(bar));
    foo.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
    Assert.assertEquals(foo, bar);

    Assert.assertEquals(1, foo.size());
    foo.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
    Assert.assertEquals(1, foo.size());
    Assert.assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    Assert.assertNull(foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
  }

  @Test
  public void testToShortString() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "word");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "NN");
    Assert.assertEquals("word/NN", foo.toShortString("Text", "PartOfSpeech"));
    Assert.assertEquals("NN", foo.toShortString("PartOfSpeech"));
    Assert.assertEquals("", foo.toShortString("Lemma"));
    Assert.assertEquals("word|NN", foo.toShortString('|', "Text", "PartOfSpeech", "Lemma"));
    foo.set(CoreAnnotations.AntecedentAnnotation.class, "the price of tea");
    Assert.assertEquals("{word/NN/the price of tea}", foo.toShortString("Text", "PartOfSpeech", "Antecedent"));
  }

  /**
   * Tests equals in the case of different annotations added in
   * different orders
   */
  @SuppressWarnings("SimplifiableAssertion")
  @Test
  public void testEqualsReversedInsertOrder() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(CoreAnnotations.TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs);
    foo.set(CoreAnnotations.TextAnnotation.class, "A");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");

    ArrayCoreMap bar = new ArrayCoreMap();
    List<CoreMap> paragraphs2 = new ArrayList<>(paragraphs);
    bar.set(CoreAnnotations.TextAnnotation.class, "A");
    bar.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    bar.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs2);
    Assert.assertEquals(foo, bar);
    Assert.assertEquals(bar, foo);
    Assert.assertFalse(foo.equals(f1));
    Assert.assertFalse(foo.equals(f2));

    Assert.assertEquals(3, foo.size());
  }

  /**
   * ArrayCoreMap should be able to handle loops in its annotations
   * without blowing up
   */
  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Test
  public void testObjectLoops() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> fooParagraph = new ArrayList<>();
    fooParagraph.add(foo);
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.ParagraphsAnnotation.class, fooParagraph);
    List<CoreMap> p1 = new ArrayList<>();
    p1.add(f1);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, p1);

    foo.toString();
    foo.hashCode();
  }

  @SuppressWarnings({"SimplifiableAssertion", "ResultOfMethodCallIgnored"})
  @Test
  public void testObjectLoopEquals() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> fooParagraph = new ArrayList<>();
    fooParagraph.add(foo);
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.ParagraphsAnnotation.class, fooParagraph);
    List<CoreMap> p1 = new ArrayList<>();
    p1.add(f1);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, p1);

    foo.toString();
    int fh = foo.hashCode();

    ArrayCoreMap bar = new ArrayCoreMap();
    bar.set(CoreAnnotations.TextAnnotation.class, "foo");
    bar.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> barParagraph = new ArrayList<>();
    barParagraph.add(bar);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(CoreAnnotations.ParagraphsAnnotation.class, barParagraph);
    List<CoreMap> p2 = new ArrayList<>();
    p2.add(f2);
    bar.set(CoreAnnotations.ParagraphsAnnotation.class, p2);

    bar.toString();
    int bh = bar.hashCode();

    Assert.assertEquals(foo, bar);
    Assert.assertEquals(bar, foo);
    Assert.assertEquals(fh, bh);

    ArrayCoreMap baz = new ArrayCoreMap();
    baz.set(CoreAnnotations.TextAnnotation.class, "foo");
    baz.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> foobarParagraph = new ArrayList<>();
    foobarParagraph.add(foo);
    foobarParagraph.add(bar);
    ArrayCoreMap f3 = new ArrayCoreMap();
    f3.set(CoreAnnotations.ParagraphsAnnotation.class, foobarParagraph);
    List<CoreMap> p3 = new ArrayList<>();
    p3.add(f3);
    baz.set(CoreAnnotations.ParagraphsAnnotation.class, p3);

    Assert.assertFalse(foo.equals(baz));
    Assert.assertFalse(baz.equals(foo));

    ArrayCoreMap biff = new ArrayCoreMap();
    biff.set(CoreAnnotations.TextAnnotation.class, "foo");
    biff.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> barfooParagraph = new ArrayList<>();
    barfooParagraph.add(foo);
    barfooParagraph.add(bar);
    ArrayCoreMap f4 = new ArrayCoreMap();
    f4.set(CoreAnnotations.ParagraphsAnnotation.class, barfooParagraph);
    List<CoreMap> p4 = new ArrayList<>();
    p4.add(f4);
    biff.set(CoreAnnotations.ParagraphsAnnotation.class, p4);

    Assert.assertEquals(baz, biff);

    barfooParagraph.clear();
    Assert.assertFalse(baz.equals(biff));

    barfooParagraph.add(foo);
    Assert.assertFalse(baz.equals(biff));

    barfooParagraph.add(baz);
    Assert.assertFalse(baz.equals(biff));

    barfooParagraph.clear();
    Assert.assertFalse(baz.equals(biff));

    barfooParagraph.add(foo);
    barfooParagraph.add(bar);
    Assert.assertEquals(baz, biff);
  }

  @Test
  public void testCopyConstructor() {
    ArrayCoreMap biff = new ArrayCoreMap();
    biff.set(CoreAnnotations.TextAnnotation.class, "foo");
    biff.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    biff.set(CoreAnnotations.LemmaAnnotation.class, "fozzle");
    ArrayCoreMap boff = new ArrayCoreMap(biff);
    Assert.assertEquals(3, boff.size());
    Assert.assertEquals(biff, boff);
    Assert.assertEquals("fozzle", boff.get(CoreAnnotations.LemmaAnnotation.class));
  }

}
