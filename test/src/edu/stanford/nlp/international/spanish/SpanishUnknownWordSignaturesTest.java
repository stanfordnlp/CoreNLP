package edu.stanford.nlp.international.spanish;

import junit.framework.TestCase;

/**
 * @author Jon Gauthier
 */
public class SpanishUnknownWordSignaturesTest extends TestCase {

  public void testHasConditionalSuffix() {
    assertTrue(SpanishUnknownWordSignatures.hasConditionalSuffix("debería"));
    assertTrue(SpanishUnknownWordSignatures.hasConditionalSuffix("deberías"));
    assertTrue(SpanishUnknownWordSignatures.hasConditionalSuffix("deberíamos"));
    assertTrue(SpanishUnknownWordSignatures.hasConditionalSuffix("deberíais"));
    assertTrue(SpanishUnknownWordSignatures.hasConditionalSuffix("deberían"));

    assertFalse(SpanishUnknownWordSignatures.hasConditionalSuffix("debía"));
    assertFalse(SpanishUnknownWordSignatures.hasConditionalSuffix("debías"));
    assertFalse(SpanishUnknownWordSignatures.hasConditionalSuffix("debíamos"));
    assertFalse(SpanishUnknownWordSignatures.hasConditionalSuffix("debíais"));
    assertFalse(SpanishUnknownWordSignatures.hasConditionalSuffix("debían"));
  }

  public void testHasImperfectErIrSuffix() {
    assertTrue(SpanishUnknownWordSignatures.hasImperfectErIrSuffix("vivía"));
    assertFalse(SpanishUnknownWordSignatures.hasImperfectErIrSuffix("viviría"));
  }

}
