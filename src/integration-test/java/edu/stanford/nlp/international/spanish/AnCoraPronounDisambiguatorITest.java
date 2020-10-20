package edu.stanford.nlp.international.spanish;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import edu.stanford.nlp.international.spanish.process.AnCoraPronounDisambiguator;
import junit.framework.TestCase;

public class AnCoraPronounDisambiguatorITest {

  private final SpanishVerbStripper verbStripper = SpanishVerbStripper.getInstance();

  private void runTest(AnCoraPronounDisambiguator.PersonalPronounType expected, String verb,
                       int i) {
    assertEquals(expected,
                 AnCoraPronounDisambiguator.disambiguatePersonalPronoun(verbStripper.separatePronouns(verb), i, ""));
  }

  @Test
  public void testDisambiguation() {
    runTest(AnCoraPronounDisambiguator.PersonalPronounType.REFLEXIVE, "enterarme", 0);
  }

}
