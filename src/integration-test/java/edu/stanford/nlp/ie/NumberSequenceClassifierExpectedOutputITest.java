package edu.stanford.nlp.ie;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ie.regexp.NumberSequenceClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreUtilities;


/** These tests focus on whether the new framework delivers results that
 *  the old framework did.
 *
 *  @author Christopher Manning
 */
public class NumberSequenceClassifierExpectedOutputITest {

  private NumberSequenceClassifier nscOld = new NumberSequenceClassifier(false);
  private NumberSequenceClassifier nscNew = new NumberSequenceClassifier(true);

  private String[][] w1 = {
          { "\u20AC", "30" },
          { "500", "US$" },
          { "forty", "three", "cents" },
          { "weighs", "almost", "192", "pounds" },
  };
  private String[][] t1 = {
          { "SYM", "CD" },
          { "CD", "$" },
          { "CD", "CD", "NNS" },
          { "VBZ", "RB", "CD", "NNS" },
  };
  private int[] i1 = {
          0,
          1,
          0,
          2,
  };
  private String[] a1 = {
          "MONEY",
          "MONEY",
          "MONEY",
          "NUMBER",
  };

  @Test
  public void testCurrencyOld() {
    assert w1.length == t1.length;
    assert w1.length == i1.length;
    assert w1.length == a1.length;
    for (int i = 0; i < w1.length; i++) {
      List<CoreLabel> cl = CoreUtilities.toCoreLabelList(w1[i], t1[i]);
      cl = nscOld.classify(cl);
      Assert.assertEquals("Failed on " + w1[i][i1[i]], a1[i], cl.get(i1[i]).get(CoreAnnotations.AnswerAnnotation.class));
    }
  }

  @Test
  public void testCurrencyNew() {
    assert w1.length == t1.length;
    assert w1.length == i1.length;
    assert w1.length == a1.length;
    for (int i = 0; i < w1.length; i++) {
      List<CoreLabel> cl = CoreUtilities.toCoreLabelList(w1[i], t1[i]);
      cl = nscNew.classify(cl);
      Assert.assertEquals("Failed on " + w1[i][i1[i]], a1[i], cl.get(i1[i]).get(CoreAnnotations.AnswerAnnotation.class));
    }
  }

  // notes:
  // SUTime is regarding 1929 or even 1132 by itself as a DATE.  Too broad?
  // SUTime shouldn't exception on "11/31/1986" even though it isn't a valid date

  private String[][] w2 = {
          { "1:43" },
          { "42:76" },
          { "22:14:12" },
          { "02:96:15" },
          { "12/31/1986" },
          { "5/18/1986" },
          { "11\\/3\\/1986" },
          { "2011-08-18"},
          { "13", "Oct"},
          { "December", "7"},
          { "18", "September", "2001" },
          { "3rd", "October", "1952" },
          { "5th", "of", "January", ",", "2011" },
          { "5:45", "a.m." },
          { "7:50", "PM"},
          { "March", "2001"},
          { "2011", "November", "18"},
          { "four", "hundred", "and", "two"},
          { "11th", "of", "February"},
          { "31st", "of", "December"},
          { "11th", "time"},
          { "First", "of", "April"},
  };
  private String[][] t2 = {
          { "CD" },
          { "CD" },
          { "CD" },
          { "CD" },
          { "CD" },
          { "CD" },
          { "CD" },
          { "CD" },
          { "CD", "NNP" },
          { "NNP", "CD" },
          { "CD", "NNP", "CD" },
          { "JJ", "NNP", "CD" },
          { "JJ", "IN", "NNP", ",", "CD" },
          { "CD", "NN"},
          { "CD", "NN"},
          { "NNP", "CD"},
          { "CD", "NNP", "CD"},
          { "CD", "CD", "CC", "CD"},
          { "JJ", "IN", "NNP"},
          { "JJ", "IN", "NNP"},
          { "JJ", "NN"},
          { "JJ", "IN", "NNP"},
  };
  private int[] i2 = {
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          1,
          2,
          0,
          1,
          1,
          1,
          1,
          1,
          2,
          0,
          1,
          0,
          0,
  };
  private String[] a2 = {
          "TIME",
          "NUMBER",
          "TIME",
          "NUMBER",
          "DATE",
          "DATE",
          "DATE",
          "DATE",
          "DATE",
          "DATE",
          "DATE",
          "DATE",
          "DATE",
          "TIME",
          "TIME",
          "DATE",
          "DATE",
          "NUMBER",
          "DATE",
          "DATE",
          "ORDINAL",
          "DATE",
  };

  @Test
  public void testCdOld() {
    assert w2.length == t2.length;
    assert w2.length == i2.length;
    assert w2.length == a2.length;
    for (int i = 0; i < w2.length; i++) {
      List<CoreLabel> cl = CoreUtilities.toCoreLabelList(w2[i], t2[i]);
      cl = nscOld.classify(cl);
      Assert.assertEquals("Failed on " + w2[i][i2[i]], a2[i], cl.get(i2[i]).get(CoreAnnotations.AnswerAnnotation.class));
    }
  }


  @Test
  public void testCdNew() {
    assert w2.length == t2.length;
    assert w2.length == i2.length;
    assert w2.length == a2.length;
    for (int i = 0; i < w2.length; i++) {
      List<CoreLabel> cl = CoreUtilities.toCoreLabelList(w2[i], t2[i]);
      //System.err.println("CHECKING: " + cl);
      cl = nscNew.classify(cl);
      Assert.assertEquals("Failed on " + w2[i][i2[i]], a2[i], cl.get(i2[i]).get(CoreAnnotations.AnswerAnnotation.class));
    }
  }

}
