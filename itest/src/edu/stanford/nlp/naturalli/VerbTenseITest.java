package edu.stanford.nlp.naturalli;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Some tests for {@link VerbTense}
 *
 * @author <a href="mailto:gabor@eloquent.ai">Gabor Angeli</a>
 */
public class VerbTenseITest {

  @Test
  public void testPlay() {
    assertEquals("play", VerbTense.INFINITIVE.conjugateEnglish("play"));
    assertEquals("play", VerbTense.SINGULAR_PRESENT_FIRST_PERSON.conjugateEnglish("play"));
    assertEquals("play", VerbTense.SINGULAR_PRESENT_SECOND_PERSON.conjugateEnglish("play"));
    assertEquals("plays", VerbTense.SINGULAR_PRESENT_THIRD_PERSON.conjugateEnglish("play"));
    assertEquals("playing", VerbTense.PRESENT_PARTICIPLE.conjugateEnglish("play"));
    assertEquals("played", VerbTense.SINGULAR_PAST_FIRST_PERSON.conjugateEnglish("play"));
    assertEquals("played", VerbTense.SINGULAR_PAST_SECOND_PERSON.conjugateEnglish("play"));
    assertEquals("played", VerbTense.SINGULAR_PAST_THIRD_PERSON.conjugateEnglish("play"));
    assertEquals("played", VerbTense.PAST.conjugateEnglish("play"));
    assertEquals("played", VerbTense.PAST_PLURAL.conjugateEnglish("play"));
    assertEquals("played", VerbTense.PAST_PARTICIPLE.conjugateEnglish("play"));
  }


  @Test
  public void testChoose() {
    assertEquals("choose", VerbTense.INFINITIVE.conjugateEnglish("choose"));
    assertEquals("choose", VerbTense.SINGULAR_PRESENT_FIRST_PERSON.conjugateEnglish("choose"));
    assertEquals("choose", VerbTense.SINGULAR_PRESENT_SECOND_PERSON.conjugateEnglish("choose"));
    assertEquals("chooses", VerbTense.SINGULAR_PRESENT_THIRD_PERSON.conjugateEnglish("choose"));
    assertEquals("choosing", VerbTense.PRESENT_PARTICIPLE.conjugateEnglish("choose"));
    assertEquals("chose", VerbTense.SINGULAR_PAST_FIRST_PERSON.conjugateEnglish("choose"));
    assertEquals("chose", VerbTense.SINGULAR_PAST_SECOND_PERSON.conjugateEnglish("choose"));
    assertEquals("chose", VerbTense.SINGULAR_PAST_THIRD_PERSON.conjugateEnglish("choose"));
    assertEquals("chose", VerbTense.PAST.conjugateEnglish("choose"));
    assertEquals("chose", VerbTense.PAST_PLURAL.conjugateEnglish("choose"));
    assertEquals("chosen", VerbTense.PAST_PARTICIPLE.conjugateEnglish("choose"));
  }


  @Test
  public void testBe() {
    assertEquals("be", VerbTense.INFINITIVE.conjugateEnglish("be"));
    assertEquals("am", VerbTense.SINGULAR_PRESENT_FIRST_PERSON.conjugateEnglish("be"));
    assertEquals("are", VerbTense.SINGULAR_PRESENT_SECOND_PERSON.conjugateEnglish("be"));
    assertEquals("is", VerbTense.SINGULAR_PRESENT_THIRD_PERSON.conjugateEnglish("be"));
    assertEquals("being", VerbTense.PRESENT_PARTICIPLE.conjugateEnglish("be"));
    assertEquals("was", VerbTense.SINGULAR_PAST_FIRST_PERSON.conjugateEnglish("be"));
    assertEquals("were", VerbTense.SINGULAR_PAST_SECOND_PERSON.conjugateEnglish("be"));
    assertEquals("was", VerbTense.SINGULAR_PAST_THIRD_PERSON.conjugateEnglish("be"));
    assertEquals("were", VerbTense.PAST.conjugateEnglish("be"));
    assertEquals("were", VerbTense.PAST_PLURAL.conjugateEnglish("be"));
    assertEquals("been", VerbTense.PAST_PARTICIPLE.conjugateEnglish("be"));
  }


  @Test
  public void testUse() {
    assertEquals("use", VerbTense.INFINITIVE.conjugateEnglish("use"));
    assertEquals("use", VerbTense.SINGULAR_PRESENT_FIRST_PERSON.conjugateEnglish("use"));
    assertEquals("use", VerbTense.SINGULAR_PRESENT_SECOND_PERSON.conjugateEnglish("use"));
    assertEquals("uses", VerbTense.SINGULAR_PRESENT_THIRD_PERSON.conjugateEnglish("use"));
    assertEquals("using", VerbTense.PRESENT_PARTICIPLE.conjugateEnglish("use"));
    assertEquals("used", VerbTense.SINGULAR_PAST_FIRST_PERSON.conjugateEnglish("use"));
    assertEquals("used", VerbTense.SINGULAR_PAST_SECOND_PERSON.conjugateEnglish("use"));
    assertEquals("used", VerbTense.SINGULAR_PAST_THIRD_PERSON.conjugateEnglish("use"));
    assertEquals("used", VerbTense.PAST.conjugateEnglish("use"));
    assertEquals("used", VerbTense.PAST_PLURAL.conjugateEnglish("use"));
    assertEquals("used", VerbTense.PAST_PARTICIPLE.conjugateEnglish("use"));
  }


  @Test
  public void testLicense() {
    assertEquals("license", VerbTense.INFINITIVE.conjugateEnglish("license"));
    assertEquals("license", VerbTense.SINGULAR_PRESENT_FIRST_PERSON.conjugateEnglish("license"));
    assertEquals("license", VerbTense.SINGULAR_PRESENT_SECOND_PERSON.conjugateEnglish("license"));
    assertEquals("licenses", VerbTense.SINGULAR_PRESENT_THIRD_PERSON.conjugateEnglish("license"));
    assertEquals("licensing", VerbTense.PRESENT_PARTICIPLE.conjugateEnglish("license"));
    assertEquals("licensed", VerbTense.SINGULAR_PAST_FIRST_PERSON.conjugateEnglish("license"));
    assertEquals("licensed", VerbTense.SINGULAR_PAST_SECOND_PERSON.conjugateEnglish("license"));
    assertEquals("licensed", VerbTense.SINGULAR_PAST_THIRD_PERSON.conjugateEnglish("license"));
    assertEquals("licensed", VerbTense.PAST.conjugateEnglish("license"));
    assertEquals("licensed", VerbTense.PAST_PLURAL.conjugateEnglish("license"));
    assertEquals("licensed", VerbTense.PAST_PARTICIPLE.conjugateEnglish("license"));
  }

}