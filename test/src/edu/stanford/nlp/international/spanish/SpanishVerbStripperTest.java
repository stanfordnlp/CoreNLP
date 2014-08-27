package edu.stanford.nlp.international.spanish;

import junit.framework.TestCase;

/**
 * @author Jon Gauthier
 */
public class SpanishVerbStripperTest extends TestCase {

  public void testStrippable() {
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
  }

  public void testStripVerb() {
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
  }

}
