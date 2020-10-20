package edu.stanford.nlp.ie;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.*;

/**
 * A test for the {@link KBPRelationExtractor} base class.
 * Also tests various nested classes.
 */
public class KBPRelationExtractorTest {

  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
  @Test
  public void testAccuracySimple() {
    KBPRelationExtractor.Accuracy accuracy = new KBPRelationExtractor.Accuracy();
    accuracy.predict(
        new HashSet<>(Arrays.asList("a")),
        new HashSet<>(Arrays.asList("a")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("a")),
        new HashSet<>(Arrays.asList()));
    accuracy.predict(
        new HashSet<>(Arrays.asList()),
        new HashSet<>(Arrays.asList("b")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList()));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList("b")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList("b")));

    assertEquals(0.5, accuracy.precision("a"), 1e-10);
    assertEquals(1.0, accuracy.recall("a"), 1e-10);
    assertEquals(2.0 * 1.0 * 0.5 / (1.0 + 0.5), accuracy.f1("a"), 1e-10);

    assertEquals(2.0 / 3.0, accuracy.precision("b"), 1e-10);
    assertEquals(2.0 / 3.0, accuracy.recall("b"), 1e-10);

    assertEquals(3.0 / 5.0, accuracy.precisionMicro(), 1e-10);
    assertEquals(7.0 / 12.0, accuracy.precisionMacro(), 1e-10);

    assertEquals(3.0 / 4.0, accuracy.recallMicro(), 1e-10);
    assertEquals(5.0 / 6.0, accuracy.recallMacro(), 1e-10);
  }

  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
  @Test
  public void testAccuracyNoRelation() {
    KBPRelationExtractor.Accuracy accuracy = new KBPRelationExtractor.Accuracy();
    accuracy.predict(
        new HashSet<>(Arrays.asList("a")),
        new HashSet<>(Arrays.asList("a")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("a")),
        new HashSet<>(Arrays.asList("no_relation")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("no_relation")),
        new HashSet<>(Arrays.asList("b")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList("no_relation")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList("b")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList("b")));

    assertEquals(0.5, accuracy.precision("a"), 1e-10);
    assertEquals(1.0, accuracy.recall("a"), 1e-10);
    assertEquals(2.0 * 1.0 * 0.5 / (1.0 + 0.5), accuracy.f1("a"), 1e-10);

    assertEquals(2.0 / 3.0, accuracy.precision("b"), 1e-10);
    assertEquals(2.0 / 3.0, accuracy.recall("b"), 1e-10);

    assertEquals(3.0 / 5.0, accuracy.precisionMicro(), 1e-10);
    assertEquals(7.0 / 12.0, accuracy.precisionMacro(), 1e-10);

    assertEquals(3.0 / 4.0, accuracy.recallMicro(), 1e-10);
    assertEquals(5.0 / 6.0, accuracy.recallMacro(), 1e-10);
  }

  @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
  @Test
  public void testAccuracyTrueNegatives() {
    KBPRelationExtractor.Accuracy accuracy = new KBPRelationExtractor.Accuracy();
    accuracy.predict(
        new HashSet<>(Arrays.asList("a")),
        new HashSet<>(Arrays.asList("a")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("a")),
        new HashSet<>(Arrays.asList("no_relation")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("no_relation")),
        new HashSet<>(Arrays.asList("b")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList("no_relation")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList("b")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("b")),
        new HashSet<>(Arrays.asList("b")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("no_relation")),
        new HashSet<>(Arrays.asList("no_relation")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("no_relation")),
        new HashSet<>(Arrays.asList("no_relation")));
    accuracy.predict(
        new HashSet<>(Arrays.asList("no_relation")),
        new HashSet<>(Arrays.asList("no_relation")));

    assertEquals(0.5, accuracy.precision("a"), 1e-10);
    assertEquals(1.0, accuracy.recall("a"), 1e-10);
    assertEquals(2.0 * 1.0 * 0.5 / (1.0 + 0.5), accuracy.f1("a"), 1e-10);

    assertEquals(2.0 / 3.0, accuracy.precision("b"), 1e-10);
    assertEquals(2.0 / 3.0, accuracy.recall("b"), 1e-10);

    assertEquals(3.0 / 5.0, accuracy.precisionMicro(), 1e-10);
    assertEquals(7.0 / 12.0, accuracy.precisionMacro(), 1e-10);

    assertEquals(3.0 / 4.0, accuracy.recallMicro(), 1e-10);
    assertEquals(5.0 / 6.0, accuracy.recallMacro(), 1e-10);
  }

}
