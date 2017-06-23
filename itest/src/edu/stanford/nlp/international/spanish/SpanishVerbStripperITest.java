package edu.stanford.nlp.international.spanish;

import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import edu.stanford.nlp.util.Pair;

/**
 * @author Jon Gauthier
 * @author Christopher Manning
 */
public class SpanishVerbStripperITest extends TestCase {

  private final SpanishVerbStripper verbStripper = SpanishVerbStripper.getInstance();

  public static void testStrippable() {
    assertTrue(SpanishVerbStripper.isStrippable("decirme"));
    assertTrue(SpanishVerbStripper.isStrippable("decirnos"));
    assertTrue(SpanishVerbStripper.isStrippable("jugarles"));
    assertTrue(SpanishVerbStripper.isStrippable("mandarlos"));
    assertTrue(SpanishVerbStripper.isStrippable("leerlo"));
    assertTrue(SpanishVerbStripper.isStrippable("jugarla"));
    assertTrue(SpanishVerbStripper.isStrippable("jugárselos"));
    assertTrue(SpanishVerbStripper.isStrippable("decírmelo"));
    assertTrue(SpanishVerbStripper.isStrippable("contándolo"));
    assertTrue(SpanishVerbStripper.isStrippable("yéndole"));
    assertTrue(SpanishVerbStripper.isStrippable("viviéndolo"));
    assertTrue(SpanishVerbStripper.isStrippable("leyéndolo"));
    assertTrue(SpanishVerbStripper.isStrippable("buscándome"));
    assertTrue(SpanishVerbStripper.isStrippable("sentaos"));
    assertTrue(SpanishVerbStripper.isStrippable("vestíos"));
    assertTrue(SpanishVerbStripper.isStrippable("cómprelos"));
    assertTrue(SpanishVerbStripper.isStrippable("házmelo"));
    assertTrue(SpanishVerbStripper.isStrippable("sentémonos"));
    assertTrue(SpanishVerbStripper.isStrippable("escribámosela"));
    assertTrue(SpanishVerbStripper.isStrippable("ponerlos"));
  }

  private void checkPronouns(String word, String verb, String... pronouns) {
    if (pronouns.length == 0) {
      // special case, the method returns null if no pronouns found to separate off
      assertNull(verbStripper.separatePronouns(word));
    } else {
      List<String> pronounList = Arrays.asList(pronouns);
      assertEquals(new Pair<>(verb, pronounList), verbStripper.separatePronouns(word));
    }
  }

  @SuppressWarnings("unchecked")
  public void testSeparatePronouns() {

    checkPronouns("decirme", "decir", "me");

    // Should match capitalized verbs as well
    checkPronouns("Decirme", "Decir", "me");

    checkPronouns("contándoselo", "contando", "se", "lo");

    checkPronouns("aplicárseles", "aplicar", "se", "les");

    // Don't treat plural past participles as 2nd-person commands!
    checkPronouns("sentados", "sentados");

    checkPronouns("sentaos", "sentad", "os");

    checkPronouns("damelo", "da", "me", "lo");

    checkPronouns("Imagínense", "Imaginen", "se");

    // Match elided 1P verb forms
    checkPronouns("vámonos", "vamos", "nos");

    // Let's write it to her
    checkPronouns("escribámosela", "escribamos", "se", "la");

    // Looks like a verb with a clitic pronoun.. but it's not! There are
    // a *lot* of these in Spanish.
    checkPronouns("címbalo", "címbalo");

    checkPronouns("contando", "contando");

    // [cdm, Jan 2016] I think this shouldn't be split, but it was being erroneously split as [sal, os, null]. Ouch! */
    checkPronouns("salos", "salos");

  }

  public void testStripVerb() {
    assertEquals("decir", verbStripper.stripVerb("decirme"));
    assertEquals("decir", verbStripper.stripVerb("decirnos"));
    assertEquals("jugar", verbStripper.stripVerb("jugarles"));
    assertEquals("mandar", verbStripper.stripVerb("mandarlos"));
    assertEquals("leer", verbStripper.stripVerb("leerlo"));
    assertEquals("jugar", verbStripper.stripVerb("jugarla"));
    assertEquals("jugar", verbStripper.stripVerb("jugárselos"));
    assertEquals("jugar", verbStripper.stripVerb("jugaros"));
    assertEquals("decir", verbStripper.stripVerb("decírmelo"));
    assertEquals("contando", verbStripper.stripVerb("contándolo"));
    assertEquals("yendo", verbStripper.stripVerb("yéndole"));
    assertEquals("viviendo", verbStripper.stripVerb("viviéndolo"));
    assertEquals("leyendo", verbStripper.stripVerb("leyéndolo"));
    assertEquals("buscando", verbStripper.stripVerb("buscándome"));
    assertEquals("sentad", verbStripper.stripVerb("sentaos"));
    assertEquals("vestid", verbStripper.stripVerb("vestíos"));
    assertEquals("compre", verbStripper.stripVerb("cómprelos"));
    assertEquals("haz", verbStripper.stripVerb("házmelo"));
    assertEquals("oír", verbStripper.stripVerb("oírse"));
    assertEquals("escribamos", verbStripper.stripVerb("escribámosela"));
    assertEquals("sentemos", verbStripper.stripVerb("sentémonos"));
    assertEquals("haber", verbStripper.stripVerb("haberlo"));
    assertEquals("poner", verbStripper.stripVerb("ponerlos"));
  }

}
