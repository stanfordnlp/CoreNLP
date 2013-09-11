package edu.stanford.nlp.ie.pascal;

import junit.framework.TestCase;

/** @author Christopher Manning
 */
public class DateInstanceTest extends TestCase {

  public void testMonthNormalization() {
    assertEquals("February", DateInstance.normalizeMonth("Feb."));
    assertEquals("September", DateInstance.normalizeMonth("Sept"));
    assertEquals("May", DateInstance.normalizeMonth("May"));
    assertEquals("Fred", DateInstance.normalizeMonth("Fred"));
  }

}
