package edu.stanford.nlp.ling;

import org.junit.Assert;
import org.junit.Test;

/** Tests the behavior of IndexedWord a little.
 *
 *  @author Christopher Manning
 */
public class IndexedWordTest {

  @SuppressWarnings("UnnecessaryLocalVariable")
  @Test
  public void testIndexedWordComparisons() {
    CoreLabel cl = CoreLabel.wordFromString("fiddle");
    Label l = cl;
    IndexedWord iw1 = new IndexedWord(cl);
    IndexedWord iw2 = new IndexedWord(l);
    IndexedWord iw3 = new IndexedWord("test", 1, 3);
    iw3.setWord("fiddle");
    IndexedWord iw4 = IndexedWord.NO_WORD;
    IndexedWord iw5 = new IndexedWord("test", 1, 1);
    iw5.setWord("earlier");
    IndexedWord iw6 = new IndexedWord("test", 1, 3);
    iw6.setWord("different");

    Assert.assertEquals(l, cl);
    Assert.assertEquals(cl, iw1.backingLabel());
    Assert.assertEquals(cl, iw2.backingLabel());
    Assert.assertEquals(iw1, iw2);
    Assert.assertEquals(iw1.word(), iw3.word());
    Assert.assertEquals(0, iw1.compareTo(iw2));
    Assert.assertEquals(iw3, iw6);
    Assert.assertTrue(iw4.compareTo(iw3) < 0);
    Assert.assertTrue(iw3.compareTo(iw4) > 0);
    Assert.assertEquals(iw6, iw3);
    Assert.assertTrue(iw3.compareTo(iw5) > 0);
  }

}
