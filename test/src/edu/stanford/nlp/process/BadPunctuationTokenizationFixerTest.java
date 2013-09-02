package edu.stanford.nlp.process;

import junit.framework.TestCase;

/**
 * Tests various badly punctuated strings
 * 
 * @author dramage
 */
public class BadPunctuationTokenizationFixerTest extends TestCase {

  /** Things that need fixin */
  private final String[] bad = new String[] {
      " Mr . ", " Co . ",
      " U . S . A . ", " n . a . A . c . p . ",
      " 1 , 200 , 500 ", " 134 , 300 ",
      " 0 . 124 ", " 13435 . 2900 "
  };

  /** Fixed versions of bad */
  private final String[] fixed = new String[] {
      " Mr. ", " Co. ",
      " U.S.A. ", " n.a.A.c.p. ",
      " 1,200,500 ", " 134,300 ",
      " 0.124 ", " 13435.2900 "
  };

  /** Things that shouldn't be altered */
  private final String[] fine = new String[] {
      " boy . ", " hand your paper in . ",
      " the count was 343434 , 234 of which ",
      " 0 . "
  };
  
  private BadPunctuationTokenizationFixer fixer = new BadPunctuationTokenizationFixer();
  
  /** Catch needed fixes ? */
  public void testFixEnough() {
    assert bad.length == fixed.length;
    
    for (int i = 0; i < bad.length; i++) {
      assertEquals(fixer.apply(bad[i]), fixed[i]);
    }
  }

  /** Don't fix things that ain't broke. */
  public void testDontFixTooMuch() {
    for (String alreadyGood : fine) {
      assertEquals(fixer.apply(alreadyGood), alreadyGood);
    }
  }
}
