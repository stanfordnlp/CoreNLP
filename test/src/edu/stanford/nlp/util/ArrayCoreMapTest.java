package edu.stanford.nlp.util;

import junit.framework.TestCase;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreAnnotations.ParagraphsAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;

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

    foo.set(TextAnnotation.class, "foo");
    assertEquals("foo", foo.get(TextAnnotation.class));
    assertEquals(null, foo.get(PartOfSpeechAnnotation.class));
    assertEquals(null, foo.get(ParagraphsAnnotation.class));
    assertEquals(1, foo.size());

    foo.set(PartOfSpeechAnnotation.class, "F");
    assertEquals("foo", foo.get(TextAnnotation.class));
    assertEquals("F", foo.get(PartOfSpeechAnnotation.class));
    assertEquals(null, foo.get(ParagraphsAnnotation.class));
    assertEquals(2, foo.size());

    List<CoreMap> paragraphs = new ArrayList<CoreMap>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(ParagraphsAnnotation.class, paragraphs);
    assertEquals("foo", foo.get(TextAnnotation.class));
    assertEquals("F", foo.get(PartOfSpeechAnnotation.class));
    // will test equality of the coremaps in another test
    assertEquals(3, foo.size());    
  }

  public void testSimpleEquals() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<CoreMap>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(ParagraphsAnnotation.class, paragraphs);

    ArrayCoreMap bar = new ArrayCoreMap();
    bar.set(ParagraphsAnnotation.class, paragraphs);
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
  public void testNoHanging() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<CoreMap>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(ParagraphsAnnotation.class, paragraphs);

    foo.toString();
    foo.hashCode();
  }

  public void testRemove() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(TextAnnotation.class, "foo");
    foo.set(PartOfSpeechAnnotation.class, "F");
    assertEquals("foo", foo.get(TextAnnotation.class));
    assertEquals("F", foo.get(PartOfSpeechAnnotation.class));
    assertEquals(2, foo.size());

    foo.remove(TextAnnotation.class);
    assertEquals(1, foo.size());
    assertEquals(null, foo.get(TextAnnotation.class));
    assertEquals("F", foo.get(PartOfSpeechAnnotation.class));

    foo.set(TextAnnotation.class, "bar");
    assertEquals("bar", foo.get(TextAnnotation.class));
    assertEquals("F", foo.get(PartOfSpeechAnnotation.class));
    assertEquals(2, foo.size());

    foo.remove(TextAnnotation.class);
    assertEquals(1, foo.size());
    assertEquals(null, foo.get(TextAnnotation.class));
    assertEquals("F", foo.get(PartOfSpeechAnnotation.class));

    foo.remove(PartOfSpeechAnnotation.class);
    assertEquals(0, foo.size());
    assertEquals(null, foo.get(TextAnnotation.class));
    assertEquals(null, foo.get(PartOfSpeechAnnotation.class));

    // Removing an element that doesn't exist 
    // shouldn't blow up on us in any way
    foo.remove(PartOfSpeechAnnotation.class);
    assertEquals(0, foo.size());
    assertEquals(null, foo.get(TextAnnotation.class));
    assertEquals(null, foo.get(PartOfSpeechAnnotation.class));

    // after removing all sorts of stuff, the original ArrayCoreMap
    // should now be equal to a new empty one
    ArrayCoreMap bar = new ArrayCoreMap();
    assertEquals(foo, bar);

    foo.set(TextAnnotation.class, "foo");
    foo.set(PartOfSpeechAnnotation.class, "F");
    bar.set(TextAnnotation.class, "foo");
    assertFalse(foo.equals(bar));
    foo.remove(PartOfSpeechAnnotation.class);
    assertEquals(foo, bar);

    assertEquals(1, foo.size());
    foo.remove(PartOfSpeechAnnotation.class);
    assertEquals(1, foo.size());
    assertEquals("foo", foo.get(TextAnnotation.class));
    assertEquals(null, foo.get(PartOfSpeechAnnotation.class));
  }

  /**
   * Tests equals in the case of different annotations added in
   * different orders
   */
  public void testEqualsReversedInsertOrder() {
    ArrayCoreMap foo = new ArrayCoreMap();
    List<CoreMap> paragraphs = new ArrayList<CoreMap>();
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(TextAnnotation.class, "f");
    paragraphs.add(f1);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(TextAnnotation.class, "o");
    paragraphs.add(f2);
    foo.set(ParagraphsAnnotation.class, paragraphs);
    foo.set(TextAnnotation.class, "A");
    foo.set(PartOfSpeechAnnotation.class, "B");

    ArrayCoreMap bar = new ArrayCoreMap();
    List<CoreMap> paragraphs2 = new ArrayList<CoreMap>(paragraphs);
    bar.set(TextAnnotation.class, "A");
    bar.set(PartOfSpeechAnnotation.class, "B");
    bar.set(ParagraphsAnnotation.class, paragraphs2);
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
    foo.set(TextAnnotation.class, "foo");
    foo.set(PartOfSpeechAnnotation.class, "B");
    List<CoreMap> fooParagraph = new ArrayList<CoreMap>();
    fooParagraph.add(foo);
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(ParagraphsAnnotation.class, fooParagraph);
    List<CoreMap> p1 = new ArrayList<CoreMap>();
    p1.add(f1);
    foo.set(ParagraphsAnnotation.class, p1);

    foo.toString();
    foo.hashCode();
  }

  public void testObjectLoopEquals() {
    ArrayCoreMap foo = new ArrayCoreMap();
    foo.set(TextAnnotation.class, "foo");
    foo.set(PartOfSpeechAnnotation.class, "B");
    List<CoreMap> fooParagraph = new ArrayList<CoreMap>();
    fooParagraph.add(foo);
    ArrayCoreMap f1 = new ArrayCoreMap();
    f1.set(ParagraphsAnnotation.class, fooParagraph);
    List<CoreMap> p1 = new ArrayList<CoreMap>();
    p1.add(f1);
    foo.set(ParagraphsAnnotation.class, p1);

    foo.toString();
    foo.hashCode();

    ArrayCoreMap bar = new ArrayCoreMap();
    bar.set(TextAnnotation.class, "foo");
    bar.set(PartOfSpeechAnnotation.class, "B");
    List<CoreMap> barParagraph = new ArrayList<CoreMap>();
    barParagraph.add(bar);
    ArrayCoreMap f2 = new ArrayCoreMap();
    f2.set(ParagraphsAnnotation.class, barParagraph);
    List<CoreMap> p2 = new ArrayList<CoreMap>();
    p2.add(f2);
    bar.set(ParagraphsAnnotation.class, p2);

    bar.toString();
    bar.hashCode();

    assertEquals(foo, bar);
    assertEquals(bar, foo);

    ArrayCoreMap baz = new ArrayCoreMap();
    baz.set(TextAnnotation.class, "foo");
    baz.set(PartOfSpeechAnnotation.class, "B");
    List<CoreMap> foobarParagraph = new ArrayList<CoreMap>();
    foobarParagraph.add(foo);
    foobarParagraph.add(bar);
    ArrayCoreMap f3 = new ArrayCoreMap();
    f3.set(ParagraphsAnnotation.class, foobarParagraph);
    List<CoreMap> p3 = new ArrayList<CoreMap>();
    p3.add(f3);
    baz.set(ParagraphsAnnotation.class, p3);

    assertFalse(foo.equals(baz));
    assertFalse(baz.equals(foo));

    ArrayCoreMap biff = new ArrayCoreMap();
    biff.set(TextAnnotation.class, "foo");
    biff.set(PartOfSpeechAnnotation.class, "B");
    List<CoreMap> barfooParagraph = new ArrayList<CoreMap>();
    barfooParagraph.add(foo);
    barfooParagraph.add(bar);
    ArrayCoreMap f4 = new ArrayCoreMap();
    f4.set(ParagraphsAnnotation.class, barfooParagraph);
    List<CoreMap> p4 = new ArrayList<CoreMap>();
    p4.add(f4);
    biff.set(ParagraphsAnnotation.class, p4);

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
}
