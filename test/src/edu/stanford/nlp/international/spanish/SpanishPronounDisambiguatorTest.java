package edu.stanford.nlp.international.spanish;

import junit.framework.TestCase;

public class SpanishPronounDisambiguatorTest extends TestCase {

  private void runTest(SpanishPronounDisambiguator.PersonalPronounType expected, String verb, int i) {
    assertEquals(expected,
      SpanishPronounDisambiguator.disambiguatePersonalPronoun(SpanishVerbStripper.separatePronouns(verb), i));
  }

  public void testDisambiguation() {
    runTest(SpanishPronounDisambiguator.PersonalPronounType.REFLEXIVE, "enterarme", 0);
  }

}