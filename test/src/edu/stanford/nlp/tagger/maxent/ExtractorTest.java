package edu.stanford.nlp.tagger.maxent;

import org.junit.Assert;
import org.junit.Test;

/** @author Christopher Manning */
public class ExtractorTest {

  @Test
  public void testGetParenthesizedArg() {
    Assert.assertEquals("yellow", Extractor.getParenthesizedArg("foo(bar,gak,yellow)", 3));
    Assert.assertEquals("ye(,)ow", Extractor.getParenthesizedArg("foo(bar,gak,\"ye(,)ow\")", 3));
    Assert.assertEquals("ye()ow", Extractor.getParenthesizedArg("foo(bar,gak,ye()ow)", 3));
    Assert.assertEquals("'\"", Extractor.getParenthesizedArg("foo(\"'\"\"\")", 1));
    Assert.assertEquals("'\"", Extractor.getParenthesizedArg("foo(\"bar\",\"'\"\"\",gak)", 2));
    Assert.assertNull(Extractor.getParenthesizedArg("foo(bar,gak,yellow)", 0));
    Assert.assertNull(Extractor.getParenthesizedArg("foo(bar,gak,yellow)", 4));
    Assert.assertEquals(-15, Extractor.getParenthesizedNum("foo(bar,-15,yellow)", 2));
    Assert.assertEquals(0, Extractor.getParenthesizedNum("foo(bar,gak,yellow)", 0));
    Assert.assertEquals(0, Extractor.getParenthesizedNum("foo(bar,gak,yellow)", 3));
    Assert.assertEquals(0, Extractor.getParenthesizedNum("foo(bar,gak,yellow)", 4));
  }

}
