package edu.stanford.nlp.process;

import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

public class LexerUtilsTest {

  private static final Pattern p = Pattern.compile("foo[dl]");
  private static final Pattern HYPHENS_DASHES = Pattern.compile("[-\u2010-\u2015]");
  private static final Pattern HYPHENS = Pattern.compile("[-\u2010-\u2011]");


  @Test
  public void testIndexOfRegex() {
    Assert.assertEquals(10, LexerUtils.indexOfRegex(p, "Fred is a fool for food"));
    Assert.assertEquals(2, LexerUtils.indexOfRegex(HYPHENS_DASHES, "18-11"));
    Assert.assertEquals(5, LexerUtils.indexOfRegex(HYPHENS_DASHES, "Asian-American"));
  }

}
