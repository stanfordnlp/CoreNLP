package edu.stanford.nlp.ling;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.util.CoreMap;

/**
 * Tests for CoreLabel copy constructors and correctness.
 */
public class CoreLabelTest {

  private CoreLabel createSampleLabel() {
    CoreLabel label = new CoreLabel();
    label.setWord("Stanford");
    label.setValue("Stanford");
    label.setTag("NNP");
    label.setNER("ORGANIZATION");
    label.setLemma("Stanford");
    label.setBeginPosition(0);
    label.setEndPosition(8);
    label.setIndex(1);
    label.setSentIndex(0);
    label.setDocID("doc-1");
    label.setBefore(" ");
    label.setAfter(" ");
    return label;
  }

  @Test
  public void testCoreLabelCopyConstructor() {
    CoreLabel orig = createSampleLabel();
    CoreLabel copy = new CoreLabel(orig);

    Assert.assertEquals(orig.size(), copy.size());
    Assert.assertEquals(orig, copy);
    Assert.assertEquals("Stanford", copy.word());
    Assert.assertEquals("NNP", copy.tag());
    Assert.assertEquals("ORGANIZATION", copy.ner());
    Assert.assertEquals("Stanford", copy.lemma());
    Assert.assertEquals(0, copy.beginPosition());
    Assert.assertEquals(8, copy.endPosition());
    Assert.assertEquals(1, copy.index());
    Assert.assertEquals(0, copy.sentIndex());
    Assert.assertEquals("doc-1", copy.docID());

    // Verify independence
    copy.setWord("Harvard");
    copy.setTag("NN");
    Assert.assertEquals("Harvard", copy.word());
    Assert.assertEquals("NN", copy.tag());
    Assert.assertEquals("Stanford", orig.word());
    Assert.assertEquals("NNP", orig.tag());
    Assert.assertFalse(orig.equals(copy));
  }

  @Test
  public void testCoreLabelFromCoreMap() {
    CoreLabel orig = createSampleLabel();
    CoreMap asCoreMap = orig;
    CoreLabel copy = new CoreLabel(asCoreMap);

    Assert.assertEquals(orig.size(), copy.size());
    Assert.assertEquals(orig, copy);
    Assert.assertEquals("ORGANIZATION", copy.ner());

    // Verify independence
    copy.setNER("LOCATION");
    Assert.assertEquals("LOCATION", copy.ner());
    Assert.assertEquals("ORGANIZATION", orig.ner());
  }

  @Test
  public void testCoreLabelFromLabel() {
    CoreLabel orig = createSampleLabel();

    // From CoreLabel as Label
    CoreLabel fromCoreLabel = new CoreLabel((Label) orig);
    Assert.assertEquals(orig.size(), fromCoreLabel.size());
    Assert.assertEquals(orig, fromCoreLabel);

    // From IndexedWord as Label
    IndexedWord iw = new IndexedWord(orig);
    CoreLabel fromIndexedWord = new CoreLabel(iw);
    Assert.assertEquals(orig.size(), fromIndexedWord.size());
    Assert.assertEquals("Stanford", fromIndexedWord.word());
    Assert.assertEquals("ORGANIZATION", fromIndexedWord.ner());

    // From plain Word as Label
    Word word = new Word("testWord");
    CoreLabel fromWord = new CoreLabel(word);
    Assert.assertEquals("testWord", fromWord.word());
    Assert.assertEquals("testWord", fromWord.value());
    Assert.assertNull(fromWord.ner());
  }

  @Test
  public void testCopyDoesNotShareStorage() {
    CoreLabel orig = createSampleLabel();
    int origSize = orig.size();
    CoreLabel copy = new CoreLabel(orig);
    copy.set(CoreAnnotations.ShapeAnnotation.class, "Xxxxx");
    Assert.assertEquals(origSize, orig.size());
    Assert.assertNull(orig.get(CoreAnnotations.ShapeAnnotation.class));
    orig.remove(CoreAnnotations.NamedEntityTagAnnotation.class);
    Assert.assertEquals("ORGANIZATION", copy.ner());
    Assert.assertEquals(copy, new CoreLabel(copy));
  }
}
