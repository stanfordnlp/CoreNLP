package edu.stanford.nlp.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test some acronyms. Taken mostly from the 2013 KBP results.
 *
 * @author Gabor Angeli
 */
public class AcronymMatcherTest {

  @Test
  public void testBasic() {
    assertTrue(AcronymMatcher.isAcronym("B", "B".split("\\s+")));
    assertTrue(AcronymMatcher.isAcronym("IBM", "International Business Machines".split("\\s+")));
    assertTrue(AcronymMatcher.isAcronym("SIWI", "Stockholm International Water Institute".split("\\s+")));
    assertTrue(AcronymMatcher.isAcronym("CBRC", "China Banking Regulatory Commission".split("\\s+")));
    assertTrue(AcronymMatcher.isAcronym("ECC", "Election Complaints Commission".split("\\s+")));
  }

  @Test
  public void testFilterStopWords() {
    assertTrue(AcronymMatcher.isAcronym("CML", "Council of Mortgage Lenders".split("\\s+")));
    assertTrue(AcronymMatcher.isAcronym("AAAS", "American Association for the Advancement of Science".split("\\s+")));
  }

  @Test
  public void testStripCorp() {
    assertTrue(AcronymMatcher.isAcronym("FCI", "Fake Company International Corp.".split("\\s+")));
  }
}
