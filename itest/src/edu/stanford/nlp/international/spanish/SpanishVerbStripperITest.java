package edu.stanford.nlp.international.spanish;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import edu.stanford.nlp.util.Pair;

/**
 * @author Jon Gauthier
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

  @SuppressWarnings("unchecked")
  public void testSeparatePronouns() {
    List<String> pronouns = new ArrayList<String>();
    pronouns.add("me");
    assertEquals(new Pair("decir", pronouns),
                 verbStripper.separatePronouns("decirme"));

    // Should match capitalized verbs as well
    assertEquals(new Pair("Decir", pronouns),
      verbStripper.separatePronouns("Decirme"));

    pronouns.clear();
    pronouns.add("se");
    pronouns.add("lo");
    assertEquals(new Pair("contando", pronouns),
                 verbStripper.separatePronouns("contándoselo"));

    pronouns.clear();
    pronouns.add("se");
    pronouns.add("les");
    assertEquals(new Pair("aplicar", pronouns),
      verbStripper.separatePronouns("aplicárseles"));

    // Don't treat plural past participles as 2nd-person commands!
    Pair<String, List<String>> l = verbStripper.separatePronouns("sentados");
		assertNull(l);

    pronouns.clear();
    pronouns.add("os");
    assertEquals(new Pair("sentad", pronouns),
      verbStripper.separatePronouns("sentaos"));

    pronouns.clear();
    pronouns.add("se");
    assertEquals(new Pair("Imaginen", pronouns),
      verbStripper.separatePronouns("Imagínense"));

    // Match elided 1P verb forms
    pronouns.clear();
    pronouns.add("nos");
    assertEquals(new Pair("vamos", pronouns),
                 verbStripper.separatePronouns("vámonos"));

    // Let's write it to her
    pronouns.clear();
    pronouns.add("se");
    pronouns.add("la");
    assertEquals(new Pair("escribamos", pronouns),
                 verbStripper.separatePronouns("escribámosela"));

    // Looks like a verb with a clitic pronoun.. but it's not! There are
    // a *lot* of these in Spanish.
    assertNull(verbStripper.separatePronouns("címbalo"));

    assertNull(verbStripper.separatePronouns("contando"));
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
