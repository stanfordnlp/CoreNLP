package edu.stanford.nlp.international.spanish;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import edu.stanford.nlp.util.Pair;

/**
 * @author Jon Gauthier
 */
public class SpanishVerbStripperTest extends TestCase {

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
  }

  @SuppressWarnings("unchecked")
  public static void testSeparatePronouns() {
    List<String> pronouns = new ArrayList<String>();
    pronouns.add("me");
    assertEquals(new Pair("decir", pronouns),
                 SpanishVerbStripper.separatePronouns("decirme"));

    pronouns.clear();
    pronouns.add("se");
    pronouns.add("lo");
    assertEquals(new Pair("contando", pronouns),
                 SpanishVerbStripper.separatePronouns("contándoselo"));

    // Looks like a verb with a clitic pronoun.. but it's not! There are
    // a *lot* of these in Spanish.
    assertNull(SpanishVerbStripper.separatePronouns("címbalo"));

    assertNull(SpanishVerbStripper.separatePronouns("contando"));
  }

  public static void testStripVerb() {
    assertEquals("decir", SpanishVerbStripper.stripVerb("decirme"));
    assertEquals("decir", SpanishVerbStripper.stripVerb("decirnos"));
    assertEquals("jugar", SpanishVerbStripper.stripVerb("jugarles"));
    assertEquals("mandar", SpanishVerbStripper.stripVerb("mandarlos"));
    assertEquals("leer", SpanishVerbStripper.stripVerb("leerlo"));
    assertEquals("jugar", SpanishVerbStripper.stripVerb("jugarla"));
    assertEquals("jugar", SpanishVerbStripper.stripVerb("jugárselos"));
    assertEquals("decir", SpanishVerbStripper.stripVerb("decírmelo"));
    assertEquals("contando", SpanishVerbStripper.stripVerb("contándolo"));
    assertEquals("yendo", SpanishVerbStripper.stripVerb("yéndole"));
    assertEquals("viviendo", SpanishVerbStripper.stripVerb("viviéndolo"));
    assertEquals("leyendo", SpanishVerbStripper.stripVerb("leyéndolo"));
    assertEquals("buscando", SpanishVerbStripper.stripVerb("buscándome"));
    assertEquals("senta", SpanishVerbStripper.stripVerb("sentaos"));
    assertEquals("vesti", SpanishVerbStripper.stripVerb("vestíos"));
    assertEquals("compre", SpanishVerbStripper.stripVerb("cómprelos"));
    assertEquals("haz", SpanishVerbStripper.stripVerb("házmelo"));
  }

  public static void main(String[] args) {
    testStrippable();
    testStripVerb();
  }

}
