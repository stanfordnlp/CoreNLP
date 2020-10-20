package edu.stanford.nlp.tagger.maxent;

import org.junit.Assert;
import org.junit.Test;

public class ExtractorFramesRareTest {

  @Test
  public void testAllNumeric() {
    String[] exx = { "-4.73", "foo", "foo18", "--", "34-46", "3,456.78" };
    boolean[] ans = { true, false, false, false, true, true };
    Assert.assertEquals(exx.length, ans.length);
    for (int i = 0; i < exx.length; i++) {
      Assert.assertEquals("Checking " + exx[i], ans[i],
              RareExtractor.containsNumber(exx[i]) && RareExtractor.allNumeric(exx[i]));
    }
  }

}
