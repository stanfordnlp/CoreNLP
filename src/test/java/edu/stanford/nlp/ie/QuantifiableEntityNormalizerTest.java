package edu.stanford.nlp.ie;

import junit.framework.TestCase;

/**
 * Notes: This tests the old code that is independent of SUTime!
 * The test that checks the integration of SUTime with QEN is NumberSequenceClassifierITest.
 *
 * @author Christopher Manning
 */
public class QuantifiableEntityNormalizerTest extends TestCase {

  private String[] dateStrings = { "February 5, 1923",
                                   "Mar 3",
                                   "18 July 2005",
                                   "18 Sep 05",
                                   "Jan. 13 , '90",
                                   "Jan. 13",
                                   "2009-07-19",
                                   "2007-06-16"
  };

  private String[] dateAnswers = { "19230205",
                                   "****0303",
                                   "20050718",
                                   "20050918",
                                   "19900113",
                                   "****0113",
                                   "20090719",
                                   "20070616"
  };

  private String[] percentStrings = { "one percent",
                                      "% 8",
                                      "8 %",
                                      "8.25 %",
                                      "48 percent",
                                      "%4.9" };

  private String[] percentAnswers = { "%1.0",
                                      "%8.0",
                                      "%8.0",
                                      "%8.25",
                                      "%48.0",
                                      "%4.9" };

  private String[] moneyStrings = { "24 cents",
                                    "18\u00A2",
                                    "250 won",
                                    "\u00A35.40",
                                    "10 thousand million pounds",
                                    "10 thousand million dollars",
                                    "million dollars",
                                    "four million dollars",
                                    "$1m",
                                    "50 million yuan",
                                    "50 cents",
                                    "# 1500",
                                    "\u00A3 1500",
                                    "\u00A3 .50",
                                    "# .50",
                                    "$ 1500",
                                    "$1500",
                                    "$ 1,500",
                                    "$1,500",
                                    "$48.75",
                                    "$ 57 . 60",
                                    "2.30",
                                    "8 million",
                                    "$8 thousand",
                                    "$42,33"};

  private String[] moneyAnswers = { "$0.24",
                                    "$0.18",
                                    "\u20A9250.0",
                                    "\u00A35.4",
                                    "\u00A31.0E10",
                                    "$1.0E10",
                                    "$1000000.0",
                                    "$4000000.0",
                                    "$1000000.0",
                                    "\u51435.0E7",
                                    "$0.5",
                                    "\u00A31500.0",
                                    "\u00A31500.0",
                                    "\u00A30.5",
                                    "\u00A30.5",
                                    "$1500.0",
                                    "$1500.0",
                                    "$1500.0",
                                    "$1500.0",
                                    "$48.75",
                                    "$57.6",
                                    "$2.3",
                                    "$8000000.0",
                                    "$8000.0",
                                    "$42.33"};

  private String[] numberStrings = { "twenty-five",
          "1.3 million",
          "10 thousand million",
          "3.625",
          "-15",
          "117-111",
          "",
          " ",
          "   ",
  };

  private String[] numberAnswers = { "25.0",
          "1300000.0",
          "1.0E10",
          "3.625",
          "-15.0",
          "117.0 - 111.0",
          "",
          " ",
          "   ",
  };

  private String[] ordinalStrings = { "twelfth",
                                      "twenty-second",
                                      "0th",
                                      "1,000th"
  };

  private String[] ordinalAnswers = { "12.0",
                                      "22.0",
                                      "0.0",
                                      "1000.0"
  };

  private String[] timeStrings = { "4:30",
          "11:00 pm",
          "2 am",
          "12:29 p.m.",
          "midnight",
          "22:26:48" };

  private String[] timeAnswers = { "4:30",
          "11:00pm",
          "2:00am",
          "12:29pm",
          "00:00am",
          "22:26:48" };



  public void testDateNormalization() {
    assertEquals(dateStrings.length, dateAnswers.length);
    for (int i = 0; i < dateStrings.length; i++) {
	assertEquals("Testing " + dateStrings[i], dateAnswers[i], QuantifiableEntityNormalizer.normalizedDateString(dateStrings[i], null));
    }
  }

  public void testPercentNormalization() {
    assertEquals(percentStrings.length, percentAnswers.length);
    for (int i = 0; i < percentStrings.length; i++) {
      assertEquals(percentAnswers[i], QuantifiableEntityNormalizer.normalizedPercentString(percentStrings[i], null));
    }
  }

  public void testMoneyNormalization() {
    assertEquals(moneyStrings.length, moneyAnswers.length);
    for (int i = 0; i < moneyStrings.length; i++) {
      assertEquals(moneyAnswers[i], QuantifiableEntityNormalizer.normalizedMoneyString(moneyStrings[i], null));
    }
  }

  public void testNumberNormalization() {
    assertEquals(numberStrings.length, numberAnswers.length);
    for (int i = 0; i < numberStrings.length; i++) {
      assertEquals(numberAnswers[i], QuantifiableEntityNormalizer.normalizedNumberString(numberStrings[i], "", null));
    }
  }

  public void testOrdinalNormalization() {
    assertEquals(ordinalStrings.length, ordinalAnswers.length);
    for (int i = 0; i < ordinalStrings.length; i++) {
      assertEquals(ordinalAnswers[i], QuantifiableEntityNormalizer.normalizedOrdinalString(ordinalStrings[i], null)) ;
    }
  }

  public void testTimeNormalization() {
    assertEquals(timeStrings.length, timeAnswers.length);
    for (int i = 0; i < timeStrings.length; i++) {
      assertEquals(timeAnswers[i], QuantifiableEntityNormalizer.normalizedTimeString(timeStrings[i], null));
    }
  }

}
