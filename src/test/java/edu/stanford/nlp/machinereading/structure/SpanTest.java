package edu.stanford.nlp.machinereading.structure;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Apparently nothing works unless I test it.
 *
 * @author Gabor Angeli
 */
public class SpanTest {

  @Test
  public void testUnion() {
    assertEquals(Span.fromValues(1, 5), Span.union(Span.fromValues(1, 2), Span.fromValues(3, 5)));
    assertEquals(Span.fromValues(1, 5), Span.union(Span.fromValues(1, 2), Span.fromValues(1, 5)));
    assertEquals(Span.fromValues(1, 5), Span.union(Span.fromValues(1, 5), Span.fromValues(2, 3)));
    assertEquals(Span.fromValues(1, 5), Span.union(Span.fromValues(3, 5), Span.fromValues(1, 2)));
    assertEquals(Span.fromValues(1, 5), Span.union(Span.fromValues(1, 1), Span.fromValues(5, 5)));
    assertEquals(Span.fromValues(1, 5), Span.union(Span.fromValues(5, 5), Span.fromValues(1, 1)));
  }
}
