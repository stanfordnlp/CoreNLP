package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SpanishUnknownWordModelTest {

  private SpanishUnknownWordModel uwm;

  @Before
  public void setUp() {
    // Build dummy UWM
    Options op = new Options();
    op.lexOptions.useUnknownWordSignatures = 1;

    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();

    uwm = new SpanishUnknownWordModel(op, new BaseLexicon(op, wordIndex, tagIndex),
                                      wordIndex, tagIndex, new ClassicCounter<IntTaggedWord>());
  }

  @Test
  public void testGetSignature() throws Exception {
    Assert.assertEquals("UNK-cond-c", uwm.getSignature("marcaría", 0));

    Assert.assertEquals("UNK-imp-c", uwm.getSignature("marcaba", 0));
    Assert.assertEquals("UNK-imp-c", uwm.getSignature("marcábamos", 0));
    Assert.assertEquals("UNK-imp-c", uwm.getSignature("vivías", 0));
    Assert.assertEquals("UNK-imp-c", uwm.getSignature("vivíamos", 0));

    Assert.assertEquals("UNK-inf-c", uwm.getSignature("brindar", 0));

    Assert.assertEquals("UNK-adv-c", uwm.getSignature("rápidamente", 0));

    // Broad-coverage patterns
    Assert.assertEquals("UNK-vb1p-c", uwm.getSignature("mandamos", 0));
    Assert.assertEquals("UNK-s-c", uwm.getSignature("últimos", 0));
    Assert.assertEquals("UNK-ger-c", uwm.getSignature("marcando", 0));
    Assert.assertEquals("UNK-s-c", uwm.getSignature("marcados", 0));
  }
}
