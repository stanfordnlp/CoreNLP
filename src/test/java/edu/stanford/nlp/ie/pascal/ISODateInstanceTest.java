package edu.stanford.nlp.ie.pascal;

import junit.framework.TestCase;

public class ISODateInstanceTest extends TestCase {
  private String[] dateStrings = { "February 5, 1923",
      "Mar 3",
      "18 July 2005",
      "18 Sep 05",
      "Jan. 13 , '90",
      "Jan. 13",
      "01/03/07",
      "03-27-85",
      "1900-1946",
      "1900--1946",
      "June 8-10",
      "today, Saturday",
      "Saturday, June 10",
      "Dec. 27",
      "1438143814381434",
  };

  private String[] dateAnswers = { "19230205",
      "****0303",
      "20050718",
      "20050918",
      "19900113",
      "****0113",
      "20070103",
      "19850327",
      "1900/1946",
      "1900/1946",
      "****0608/****0610",
      "saturday",
      "****0610",
      "****1227",
      "1438",
  };

  private String[] staticCompatibleStrings1 = {
      "20071203",
      "****1203",
      "200712",
      "****1112"

  };

  private String[] staticCompatibleStrings2 = {
      "20071203",
      "20071203",
      "200412",
      "******12"
  };

  private boolean[] staticCompatibleAnswers= {
      true,
      true,
      false,
      true
  };

  private String[] staticAfterStrings2 = {
      "20071203",
      "20071203",
      "200712",
      "200712",
      "200701",
      "****05",
      "200703",
      "200703",
      "****11",
      "******03"
  };

  private String[] staticAfterStrings1 = {
      "20071207",
      "2008",
      "2008",
      "2007",
      "200703",
      "****06",
      "2006",
      "200701",
      "******03",
      "****11"

  };

  private boolean[] staticAfterAnswers= {
      true,
      true,
      true,
      false,
      true,
      true,
      false,
      false,
      true,
      true
  };



  public void testDateNormalization() {
    assertEquals(dateStrings.length, dateAnswers.length);
    for (int i = 0; i < dateStrings.length; i++) {
      ISODateInstance d = new ISODateInstance(dateStrings[i]);
      assertEquals("Testing " + dateStrings[i], dateAnswers[i], d.toString());
    }
  }

  public void testIsAfter() {
    for (int i = 0; i < staticAfterStrings1.length; i++) {
      assertEquals("Testing " + staticAfterStrings1[i] + " and " + staticAfterStrings2[i],
          staticAfterAnswers[i], ISODateInstance.isAfter(staticAfterStrings1[i], staticAfterStrings2[i]));
    }
  }

  public void testIsCompatible() {
    for (int i = 0; i < staticCompatibleStrings1.length; i++) {
      assertEquals("Testing " + staticCompatibleStrings1[i] + " and " + staticCompatibleStrings2[i],
          staticCompatibleAnswers[i], ISODateInstance.isCompatible(staticCompatibleStrings1[i], staticCompatibleStrings2[i]));
    }
  }

  private String[] originalDates = {
      "18 July 2005",
      "18 July 2005",
      "18 July 2005",
      "1 February 2008",
      "1 February 2008",
      "1 February",
      "1 February",
      "1 January 2008",
      "31 December 2007",
      "1 January",
      "31 December"
  };

  private String[] relativeArguments = {
      "today",
      "tomorrow",
      "yesterday",
      "tomorrow",
      "yesterday",
      "tomorrow",
      "yesterday",
      "yesterday",
      "tomorrow",
      "yesterday",
      "tomorrow"
  };

  private String[] relativeDateAnswers = {
    "20050718",
    "20050719",
    "20050717",
    "20080202",
    "20080131",
    "****0202",
    "****0131",
    "20071231",
    "20080101",
    "****1231",
    "****0101"
  };
  public void testRelativeDateCreation() {
    for (int i = 0; i < originalDates.length; i++) {
      assertEquals("Testing " + relativeArguments[i] + " with respect to " + originalDates[i],
          relativeDateAnswers[i], (new ISODateInstance(new ISODateInstance(originalDates[i]), relativeArguments[i])).getDateString());
    }
  }

  public void testContains() {
    //TODO: implement!
  }
}
