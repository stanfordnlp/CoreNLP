package edu.stanford.nlp.international.spanish;

import junit.framework.TestCase;

/**
 * @author Jon Gauthier
 */
public class SpanishUnknownWordSignaturesTest extends TestCase {

  public void testHasConditionalSuffix() {
    assertTrue("debería has conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("debería"));
    assertTrue("deberías has conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("deberías"));
    assertTrue("deberíamos has conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("deberíamos"));
    assertTrue("deberíais has conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("deberíais"));
    assertTrue("deberían has conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("deberían"));

    assertFalse("debía no conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("debía"));
    assertFalse("debías no conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("debías"));
    assertFalse("debíamos no conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("debíamos"));
    assertFalse("debíais no conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("debíais"));
    assertFalse("debían no conditional suffix", SpanishUnknownWordSignatures.hasConditionalSuffix("debían"));
  }

  public void testHasImperfectErIrSuffix() {
    assertTrue("vivía has imperfect Er Ir Suffix", SpanishUnknownWordSignatures.hasImperfectErIrSuffix("vivía"));
    assertFalse("viviría does not have imperfect Er Ir Suffix", SpanishUnknownWordSignatures.hasImperfectErIrSuffix("viviría"));
  }

}
