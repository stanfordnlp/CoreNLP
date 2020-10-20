package edu.stanford.nlp.util;

import edu.stanford.nlp.ling.CoreLabel;
import junit.framework.TestCase;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations;

/**
 * Test various operations of the ArrayCoreMap: equals, toString, etc.
 */
public class ArrayCoreMapTest extends TestCase {

  public void testCreate() {
    ArrayCoreMap foo = new ArrayCoreMap();
    assertEquals(0, foo.size());
  }

  public void testGetAndSet() {
    ArrayCoreMap foo = new ArrayCoreMap();
    assertEquals(0, foo.size());

    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals(null, foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    assertEquals(null, foo.get(CoreAnnotations.ParagraphsAnnotation.class));
    assertEquals(1, foo.size());

    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "F");
    assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    assertEquals(null, foo.get(CoreAnnotations.ParagraphsAnnotation.class));
    assertEquals(2, foo.size());

    List<CoreMap> paragraphs = new ArrayList<CoreMap>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(CoreAnnotations.TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs);
    assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    // will test equality of the coremaps in another test
    assertEquals(3, foo.size());
  }

  public void testSimpleEquals() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<CoreMap>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(CoreAnnotations.TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs);

    ArrayCoreMap bar = new ArrayCoreMap();
    bar.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs);
    assertEquals(foo, bar);
    assertEquals(bar, foo);
    assertFalse(foo.equals(f1));
    assertFalse(foo.equals(f2));
    assertEquals(f1, f1);
    assertFalse(f1.equals(f2));
  }

  /**
   * Test that neither hashCode() nor toString() hang
   */
  public void testKeySet() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "NN");
    foo.set(CoreAnnotations.DocIDAnnotation.class, null);
    assertTrue(foo.keySet().contains(CoreAnnotations.TextAnnotation.class));
    assertTrue(foo.keySet().contains(CoreAnnotations.PartOfSpeechAnnotation.class));
    assertTrue(foo.keySet().contains(CoreAnnotations.DocIDAnnotation.class));
    assertFalse(foo.keySet().contains(CoreAnnotations.TokensAnnotation.class));
  }

  /**
   * Test that neither hashCode() nor toString() hang
   */
  public void testNoHanging() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<CoreMap>();
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

  public void testRemove() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "F");
    assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    assertEquals(2, foo.size());

    foo.remove(CoreAnnotations.TextAnnotation.class);
    assertEquals(1, foo.size());
    assertEquals(null, foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));

    foo.set(CoreAnnotations.TextAnnotation.class, "bar");
    assertEquals("bar", foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
    assertEquals(2, foo.size());

    foo.remove(CoreAnnotations.TextAnnotation.class);
    assertEquals(1, foo.size());
    assertEquals(null, foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals("F", foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));

    foo.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
    assertEquals(0, foo.size());
    assertEquals(null, foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals(null, foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));

    // Removing an element that doesn't exist
    // shouldn't blow up on us in any way
    foo.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
    assertEquals(0, foo.size());
    assertEquals(null, foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals(null, foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));

    // after removing all sorts of stuff, the original ArrayCoreMap
    // should now be equal to a new empty one
    ArrayCoreMap bar = new ArrayCoreMap();
    assertEquals(foo, bar);

    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "F");
    bar.set(CoreAnnotations.TextAnnotation.class, "foo");
    assertFalse(foo.equals(bar));
    foo.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
    assertEquals(foo, bar);

    assertEquals(1, foo.size());
    foo.remove(CoreAnnotations.PartOfSpeechAnnotation.class);
    assertEquals(1, foo.size());
    assertEquals("foo", foo.get(CoreAnnotations.TextAnnotation.class));
    assertEquals(null, foo.get(CoreAnnotations.PartOfSpeechAnnotation.class));
  }

  public void testToShortString() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "word");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "NN");
    assertEquals("word/NN", foo.toShortString("Text", "PartOfSpeech"));
    assertEquals("NN", foo.toShortString("PartOfSpeech"));
    assertEquals("", foo.toShortString("Lemma"));
    assertEquals("word|NN", foo.toShortString('|', "Text", "PartOfSpeech", "Lemma"));
    foo.set(CoreAnnotations.AntecedentAnnotation.class, "the price of tea");
    assertEquals("{word/NN/the price of tea}", foo.toShortString("Text", "PartOfSpeech", "Antecedent"));
  }

  /**
   * Tests equals in the case of different annotations added in
   * different orders
   */
  public void testEqualsReversedInsertOrder() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<CoreMap>();
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
    List<CoreMap> paragraphs2 = new ArrayList<CoreMap>(paragraphs);
    bar.set(CoreAnnotations.TextAnnotation.class, "A");
    bar.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    bar.set(CoreAnnotations.ParagraphsAnnotation.class, paragraphs2);
    assertEquals(foo, bar);
    assertEquals(bar, foo);
    assertFalse(foo.equals(f1));
    assertFalse(foo.equals(f2));

    assertEquals(3, foo.size());
  }

  /**
   * ArrayCoreMap should be able to handle loops in its annotations
   * without blowing up
   */
  public void testObjectLoops() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> fooParagraph = new ArrayList<CoreMap>();
    fooParagraph.add(foo);
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.ParagraphsAnnotation.class, fooParagraph);
    List<CoreMap> p1 = new ArrayList<CoreMap>();
    p1.add(f1);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, p1);

    foo.toString();
    foo.hashCode();
  }

  public void testObjectLoopEquals() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> fooParagraph = new ArrayList<CoreMap>();
    fooParagraph.add(foo);
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(CoreAnnotations.ParagraphsAnnotation.class, fooParagraph);
    List<CoreMap> p1 = new ArrayList<CoreMap>();
    p1.add(f1);
    foo.set(CoreAnnotations.ParagraphsAnnotation.class, p1);

    foo.toString();
    int fh = foo.hashCode();

    ArrayCoreMap bar = new ArrayCoreMap();
    bar.set(CoreAnnotations.TextAnnotation.class, "foo");
    bar.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> barParagraph = new ArrayList<CoreMap>();
    barParagraph.add(bar);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(CoreAnnotations.ParagraphsAnnotation.class, barParagraph);
    List<CoreMap> p2 = new ArrayList<CoreMap>();
    p2.add(f2);
    bar.set(CoreAnnotations.ParagraphsAnnotation.class, p2);

    bar.toString();
    int bh = bar.hashCode();

    assertEquals(foo, bar);
    assertEquals(bar, foo);
    assertEquals(fh, bh);

    ArrayCoreMap baz = new ArrayCoreMap();
    baz.set(CoreAnnotations.TextAnnotation.class, "foo");
    baz.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> foobarParagraph = new ArrayList<CoreMap>();
    foobarParagraph.add(foo);
    foobarParagraph.add(bar);
    ArrayCoreMap f3 = new ArrayCoreMap();
    f3.set(CoreAnnotations.ParagraphsAnnotation.class, foobarParagraph);
    List<CoreMap> p3 = new ArrayList<CoreMap>();
    p3.add(f3);
    baz.set(CoreAnnotations.ParagraphsAnnotation.class, p3);

    assertFalse(foo.equals(baz));
    assertFalse(baz.equals(foo));

    ArrayCoreMap biff = new ArrayCoreMap();
    biff.set(CoreAnnotations.TextAnnotation.class, "foo");
    biff.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    List<CoreMap> barfooParagraph = new ArrayList<CoreMap>();
    barfooParagraph.add(foo);
    barfooParagraph.add(bar);
    ArrayCoreMap f4 = new ArrayCoreMap();
    f4.set(CoreAnnotations.ParagraphsAnnotation.class, barfooParagraph);
    List<CoreMap> p4 = new ArrayList<CoreMap>();
    p4.add(f4);
    biff.set(CoreAnnotations.ParagraphsAnnotation.class, p4);

    assertEquals(baz, biff);

    barfooParagraph.clear();
    assertFalse(baz.equals(biff));

    barfooParagraph.add(foo);
    assertFalse(baz.equals(biff));

    barfooParagraph.add(baz);
    assertFalse(baz.equals(biff));

    barfooParagraph.clear();
    assertFalse(baz.equals(biff));

    barfooParagraph.add(foo);
    barfooParagraph.add(bar);
    assertEquals(baz, biff);
  }

  public void testCoreLabelSetWordBehavior() {
    CoreLabel foo = new CoreLabel();
    foo.set(CoreAnnotations.TextAnnotation.class, "foo");
    foo.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    foo.set(CoreAnnotations.LemmaAnnotation.class, "fool");

    // Lemma gets removed with word
    ArrayCoreMap copy = new ArrayCoreMap(foo);
    assertEquals(copy, foo);
    foo.setWord("foo");
    assertEquals(copy, foo);  // same word set
    foo.setWord("bar");
    assertFalse(copy.equals(foo));  // lemma removed
    foo.setWord("foo");
    assertFalse(copy.equals(foo));  // still removed
    foo.set(CoreAnnotations.LemmaAnnotation.class, "fool");
    assertEquals(copy, foo);  // back to normal

    // Hash code is consistent
    int hashCode = foo.hashCode();
    assertEquals(copy.hashCode(), hashCode);
    foo.setWord("bar");
    assertFalse(hashCode == foo.hashCode());
    foo.setWord("foo");
    assertFalse(hashCode == foo.hashCode());

    // Hash code doesn't care between a value of null and the key not existing
    assertTrue(foo.lemma() == null);
    int lemmalessHashCode = foo.hashCode();
    foo.remove(CoreAnnotations.LemmaAnnotation.class);
    assertEquals(lemmalessHashCode, foo.hashCode());
    foo.setLemma(null);
    assertEquals(lemmalessHashCode, foo.hashCode());
    foo.setLemma("fool");
    assertEquals(hashCode, foo.hashCode());

    // Check equals
    foo.setWord("bar");
    foo.setWord("foo");
    ArrayCoreMap nulledCopy = new ArrayCoreMap(foo);
    assertEquals(nulledCopy, foo);
    foo.remove(CoreAnnotations.LemmaAnnotation.class);
    assertEquals(nulledCopy, foo);
  }

  public void testCopyConstructor() {
    ArrayCoreMap biff = new ArrayCoreMap();
    biff.set(CoreAnnotations.TextAnnotation.class, "foo");
    biff.set(CoreAnnotations.PartOfSpeechAnnotation.class, "B");
    biff.set(CoreAnnotations.LemmaAnnotation.class, "fozzle");
    ArrayCoreMap boff = new ArrayCoreMap(biff);
    assertEquals(3, boff.size());
    assertEquals(biff, boff);
    assertEquals("fozzle", boff.get(CoreAnnotations.LemmaAnnotation.class));
  }

}
