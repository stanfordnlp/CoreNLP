package edu.stanford.nlp.international.french;

import junit.framework.TestCase;

/**
 * @author Christopher Manning
 */
public class FrenchUnknownWordSignaturesTest extends TestCase {

  public void testHasPunc() {
    assertEquals("-hpunc", FrenchUnknownWordSignatures.hasPunc("Yes!"));
    assertEquals("-hpunc", FrenchUnknownWordSignatures.hasPunc("["));
    assertEquals("-hpunc", FrenchUnknownWordSignatures.hasPunc("40%"));
    assertEquals("", FrenchUnknownWordSignatures.hasPunc("B"));
    assertEquals("-hpunc", FrenchUnknownWordSignatures.hasPunc("BQ_BD"));
    assertEquals("", FrenchUnknownWordSignatures.hasPunc("BQBD"));
    assertEquals("", FrenchUnknownWordSignatures.hasPunc("0"));
    assertEquals("-hpunc", FrenchUnknownWordSignatures.hasPunc("\\"));
    assertEquals("-hpunc", FrenchUnknownWordSignatures.hasPunc("]aeiou"));
    assertEquals("-hpunc", FrenchUnknownWordSignatures.hasPunc("]"));
    assertEquals("-hpunc", FrenchUnknownWordSignatures.hasPunc("÷"));
    assertEquals("", FrenchUnknownWordSignatures.hasPunc("ø"));

  }

  public void testIsPunc() {
    assertEquals("", FrenchUnknownWordSignatures.isPunc("Yes!"));
    assertEquals("-ipunc", FrenchUnknownWordSignatures.isPunc("["));
    assertEquals("", FrenchUnknownWordSignatures.isPunc("40%"));
    assertEquals("", FrenchUnknownWordSignatures.isPunc("B"));
    assertEquals("", FrenchUnknownWordSignatures.isPunc("BQ_BD"));
    assertEquals("", FrenchUnknownWordSignatures.isPunc("BQBD"));
    assertEquals("", FrenchUnknownWordSignatures.isPunc("0"));
    assertEquals("-ipunc", FrenchUnknownWordSignatures.isPunc("\\"));
    assertEquals("", FrenchUnknownWordSignatures.isPunc("]aeiou"));
    assertEquals("-ipunc", FrenchUnknownWordSignatures.isPunc("]"));
    assertEquals("-ipunc", FrenchUnknownWordSignatures.isPunc("÷"));
    assertEquals("", FrenchUnknownWordSignatures.isPunc("ø"));
  }

  public void testIsAllCaps() {
    assertEquals("-allcap", FrenchUnknownWordSignatures.isAllCaps("YO"));
    assertEquals("", FrenchUnknownWordSignatures.isAllCaps("\\\\"));
    assertEquals("", FrenchUnknownWordSignatures.isAllCaps("0D"));
    assertEquals("", FrenchUnknownWordSignatures.isAllCaps("×"));
    assertEquals("-allcap", FrenchUnknownWordSignatures.isAllCaps("ÀÅÆÏÜÝÞ"));
  }

}