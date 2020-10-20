package edu.stanford.nlp.naturalli;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Test some simple properties of operators
 *
 * @author Gabor Angeli
 */
public class OperatorTest {

  @Test
  public void testValuesOrderedDesc() {
    int currLength = Integer.MAX_VALUE;
    for (Operator op : Operator.valuesByLengthDesc) {
      assertTrue(op.surfaceForm.split(" ").length <= currLength);
      currLength = op.surfaceForm.split(" ").length;
    }
  }
}
