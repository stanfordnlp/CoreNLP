package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import junit.framework.TestCase;

public class SpanishUnknownWordModelTest extends TestCase {

  private SpanishUnknownWordModel uwm;

  public void setUp() {
    // Build dummy UWM
    Options op = new Options();
    op.lexOptions.useUnknownWordSignatures = 1;

    Index<String> wordIndex = new HashIndex<String>();
    Index<String> tagIndex = new HashIndex<String>();

    uwm = new SpanishUnknownWordModel(op, new BaseLexicon(op, wordIndex, tagIndex),
                                      wordIndex, tagIndex, new ClassicCounter<IntTaggedWord>());
  }

  public void testGetSignature() throws Exception {
    assertEquals("UNK-cond-c", uwm.getSignature("marcaría", 0));

    assertEquals("UNK-imp-c", uwm.getSignature("marcaba", 0));
    assertEquals("UNK-imp-c", uwm.getSignature("marcábamos", 0));
    assertEquals("UNK-imp-c", uwm.getSignature("vivías", 0));
    assertEquals("UNK-imp-c", uwm.getSignature("vivíamos", 0));

    assertEquals("UNK-inf-c", uwm.getSignature("brindar", 0));

    assertEquals("UNK-adv-c", uwm.getSignature("rápidamente", 0));

    // Broad-coverage patterns
    assertEquals("UNK-vb1p-c", uwm.getSignature("mandamos", 0));
    assertEquals("UNK-s-c", uwm.getSignature("últimos", 0));
    assertEquals("UNK-ger-c", uwm.getSignature("marcando", 0));
    assertEquals("UNK-s-c", uwm.getSignature("marcados", 0));
  }
}