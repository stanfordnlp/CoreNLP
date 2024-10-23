package edu.stanford.nlp.time;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.util.CoreMap;

import org.junit.Assert;
import junit.framework.TestCase;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SUTimeITest extends TestCase {

  private static AnnotationPipeline pipeline = null;
  private static final String TIME_ANNOTATOR_NAME = "sutime";

  @Override
  public void setUp() throws Exception {
    synchronized(SUTimeITest.class) {
      if (pipeline == null) {
        pipeline = new AnnotationPipeline();
        pipeline.addAnnotator(new TokenizerAnnotator(false, "en", "splitHyphenated=false"));
        // WTS not needed - is now built in to the tokenizer
        //pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        pipeline.addAnnotator(new POSTaggerAnnotator(DefaultPaths.DEFAULT_POS_MODEL, false));
        //pipeline.addAnnotator(new NumberAnnotator(false));
        //pipeline.addAnnotator(new QuantifiableEntityNormalizingAnnotator(false, false));
      }
    }
  }

  protected static Properties getDefaultProperties() {
    Properties props = new Properties();
//    props.setProperty(TIME_ANNOTATOR_NAME + ".verbose", "true");
    return props;
  }

  private static TimeAnnotator getTimeAnnotator() {
    return new TimeAnnotator(TIME_ANNOTATOR_NAME, getDefaultProperties());
  }

  private static TimeAnnotator getTimeAnnotator(Properties props) {
    return new TimeAnnotator(TIME_ANNOTATOR_NAME, props);
  }

  // TODO: better to have file of test text, and expected results?
  public void testSUTimeDurations() throws IOException {
    // Set up test text
    String testText = "It was a 3-year long drought.\n" +
            "The four-month old baby slept peacefully.\n" +
            "Over the past twenty four years, the number of crashes has decreased.\n" +
            // following the crash not included (TODO in perl)
            "In the 2 months following the crash, the investigators checked all the records.\n" +
            // before leaving not included (TODO in perl)
            "He was preoccupied for ten days before leaving.\n" +
            "Sales rose for the fifth straight year.\n" +
            "Business was slow for the third straight month in a row.\n" +
            "There are no more than 60 days.\n" +
            "In no more than 20 years, the city completely changed.\n" +
            "It has been more than 60 days.\n" +
            "Has it been more than 20 years?\n" +
            "There was at least sixty days.\n" +
            "The book was completed in four years.\n" +
            "That took a decade.\n" +
            "After a few decades, old memories faded.\n" +
            "After a few hundred decades, everything changed.\n" +
            "It has been warm in recent weeks.\n" +
            "Did it rain on the ninth day consecutively?\n" +
            "The meeting was two days ago.\n";

    // set up expected results
    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"P3Y\" type=\"DURATION\">3-year</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"P4M\" type=\"DURATION\">four-month old</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"P24Y\" type=\"DURATION\" beginPoint=\"t3\" endPoint=\"t0\">the past twenty four years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"P2M\" type=\"DURATION\">the 2 months</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"P10D\" type=\"DURATION\">ten days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"P5Y\" type=\"DURATION\" beginPoint=\"t7\" endPoint=\"t0\">the fifth straight year</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"P3M\" type=\"DURATION\" beginPoint=\"t9\" endPoint=\"t0\">the third straight month in a row</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"P60D\" type=\"DURATION\" mod=\"EQUAL_OR_LESS\">no more than 60 days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"P20Y\" type=\"DURATION\" mod=\"EQUAL_OR_LESS\">no more than 20 years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"P60D\" type=\"DURATION\" mod=\"MORE_THAN\">more than 60 days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"P20Y\" type=\"DURATION\" mod=\"MORE_THAN\">more than 20 years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" value=\"P60D\" type=\"DURATION\" mod=\"EQUAL_OR_MORE\">at least sixty days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" value=\"P4Y\" type=\"DURATION\">four years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t17\" value=\"P10Y\" type=\"DURATION\">a decade</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t18\" value=\"PXY\" type=\"DURATION\">a few decades</TIMEX3>"), // TODO: Expect PX0Y?
                    Timex.fromXml("<TIMEX3 tid=\"t19\" value=\"P1000Y\" type=\"DURATION\">hundred decades</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t20\" value=\"PXW\" type=\"DURATION\">recent weeks</TIMEX3>"),   // TODO: Expect PXD?
                    Timex.fromXml("<TIMEX3 tid=\"t22\" value=\"P9D\" type=\"DURATION\" beginPoint=\"t21\" endPoint=\"t0\">the ninth day consecutively</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t23\" alt_value=\"OFFSET P-2D\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t0\">two days ago</TIMEX3>")  ).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"P3Y\" type=\"DURATION\">3-year</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"P4M\" type=\"DURATION\">four-month old</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"P24Y\" type=\"DURATION\" beginPoint=\"t3\" endPoint=\"t0\">the past twenty four years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"P2M\" type=\"DURATION\">the 2 months</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"P10D\" type=\"DURATION\">ten days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"P5Y\" type=\"DURATION\" beginPoint=\"t7\" endPoint=\"t0\">the fifth straight year</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"P3M\" type=\"DURATION\" beginPoint=\"t9\" endPoint=\"t0\">the third straight month in a row</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"P60D\" type=\"DURATION\" mod=\"EQUAL_OR_LESS\">no more than 60 days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"P20Y\" type=\"DURATION\" mod=\"EQUAL_OR_LESS\">no more than 20 years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"P60D\" type=\"DURATION\" mod=\"MORE_THAN\">more than 60 days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"P20Y\" type=\"DURATION\" mod=\"MORE_THAN\">more than 20 years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" value=\"P60D\" type=\"DURATION\" mod=\"EQUAL_OR_MORE\">at least sixty days</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"P60D\" type=\"DURATION\" mod=\"EQUAL_OR_LESS\" beginPoint=\"t0\" endPoint=\"t11\">no more than 60 days</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"P20Y\" type=\"DURATION\" mod=\"EQUAL_OR_LESS\" beginPoint=\"t0\" endPoint=\"t13\">no more than 20 years</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t16\" value=\"P60D\" type=\"DURATION\" mod=\"MORE_THAN\" beginPoint=\"t0\" endPoint=\"t15\">more than 60 days</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t18\" value=\"P20Y\" type=\"DURATION\" mod=\"MORE_THAN\" beginPoint=\"t0\" endPoint=\"t17\">more than 20 years</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t20\" value=\"P60D\" type=\"DURATION\" mod=\"EQUAL_OR_MORE\" beginPoint=\"t0\" endPoint=\"t19\">at least sixty days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" value=\"P4Y\" type=\"DURATION\">four years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t17\" value=\"P10Y\" type=\"DURATION\">a decade</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t18\" value=\"PXY\" type=\"DURATION\">a few decades</TIMEX3>"), // TODO: Expect PX0Y?
                    Timex.fromXml("<TIMEX3 tid=\"t19\" value=\"P1000Y\" type=\"DURATION\">hundred decades</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t20\" value=\"PXW\" type=\"DURATION\">recent weeks</TIMEX3>"),   // TODO: Expect PXD?
                    Timex.fromXml("<TIMEX3 tid=\"t22\" value=\"P9D\" type=\"DURATION\" beginPoint=\"t21\" endPoint=\"t0\">the ninth day consecutively</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t23\" value=\"2010-02-15\" type=\"DATE\">two days ago</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
  }

  public void testSUTimeDurations2() throws IOException {
    // Following should be improved...
    String testText =
            // of 1997 not included as part of duration...  (TODO in perl)
            "He was away for the first 9 months of 1997.\n" +
            // Should be one duration, currently three durations
            "It has been three hours, twenty minutes and 5 seconds.\n" +
            // Should be one duration, currently two durations
            "He waited for two years and three months.\n" +
            "He waited for 6 and a half months.\n" + // TODO: What about this?
            "It was two to three months.\n" +
            "It was five hundred and twelve days.\n" +  // TODO: five hundred and 12 is not recognized
            "It was six and three months.\n" +
            "Several days has already passed.\n" +
            "For five hours on Friday, the shop was closed.\n" +
            "For more than five hours on Friday, the shop was closed.\n";
           // "Last two days was hot.";  // TODO: Add this test case

    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" alt_value=\"1997 INTERSECT P9M\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t2\">the first 9 months of 1997</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"PT3H\" type=\"DURATION\">three hours</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"PT20M\" type=\"DURATION\">twenty minutes</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"PT5S\" type=\"DURATION\">5 seconds</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"P2Y\" type=\"DURATION\">two years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"P3M\" type=\"DURATION\">three months</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"PXM\" type=\"DURATION\">months</TIMEX3>"),    // TODO: Should be 6 and a half months
                    Timex.fromXml("<TIMEX3 tid=\"t9\" alt_value=\"P2M/P3M\" type=\"DURATION\">two to three months</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"P512D\" type=\"DURATION\">five hundred and twelve days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"P3M\" type=\"DURATION\">three months</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"PXD\" type=\"DURATION\">Several days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" alt_value=\"XXXX-WXX-5 INTERSECT PT5H\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf1\" anchorTimeID=\"t14\">five hours on Friday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" alt_value=\"XXXX-WXX-5 INTERSECT &gt;PT5H\" type=\"DATE\" mod=\"MORE_THAN\" temporalFunction=\"true\" valueFromFunction=\"tf2\" anchorTimeID=\"t14\">more than five hours on Friday</TIMEX3>")
                    //Timex.fromXml("<TIMEX3 alt_value=\"THIS P2D OFFSET P-2D\" anchorTimeID=\"t17\" temporalFunction=\"true\" tid=\"t16\" type=\"DATE\" valueFromFunction=\"tf3\">Last two days</TIMEX3>")
      ).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" alt_value=\"1997 INTERSECT P9M\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t2\">the first 9 months of 1997</TIMEX3>"),   // TODO: of 1997
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"PT3H\" type=\"DURATION\">three hours</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"PT20M\" type=\"DURATION\">twenty minutes</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"PT5S\" type=\"DURATION\">5 seconds</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"P2Y\" type=\"DURATION\">two years</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"P3M\" type=\"DURATION\">three months</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"PXM\" type=\"DURATION\">months</TIMEX3>"),    // TODO: Should be 6 and a half months
                    Timex.fromXml("<TIMEX3 tid=\"t9\" alt_value=\"P2M/P3M\" type=\"DURATION\">two to three months</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"P512D\" type=\"DURATION\">five hundred and twelve days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"P3M\" type=\"DURATION\">three months</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"PXD\" type=\"DURATION\">Several days</TIMEX3>"),
                    // Friday past tense
                    Timex.fromXml("<TIMEX3 tid=\"t13\" alt_value=\"2010-02-19-WXX-5 INTERSECT PT5H\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf1\" anchorTimeID=\"t14\">five hours on Friday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" alt_value=\"2010-02-19-WXX-5 INTERSECT &gt;PT5H\" type=\"DATE\" mod=\"MORE_THAN\" temporalFunction=\"true\" valueFromFunction=\"tf2\" anchorTimeID=\"t16\">more than five hours on Friday</TIMEX3>")
                    //Timex.fromXml("<TIMEX3 alt_value=\"THIS P2D OFFSET P-2D\" anchorTimeID=\"t17\" temporalFunction=\"true\" tid=\"t16\" type=\"DATE\" valueFromFunction=\"tf3\">Last two days</TIMEX3>")
      ).iterator();

    Annotation document = createDocument(testText);

    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeDurations3() throws IOException {
    String testText =
            "Claudia had ruled the duchy well in her regency from 1632 to 1646, and was successful in keeping Tyrol out of the Thirty Years War.\n" +
            "The focus of today's Daily Eye Candy is the 22-year old fashion supermodel Chad White who is a native of Portland , Oregon.\n";

    Iterator<Timex> expectedTimexes =
      // TODO: Better range duration
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"PT122736H\" type=\"DURATION\" beginPoint=\"t1\" endPoint=\"t2\">from 1632 to 1646</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"P30Y\" type=\"DURATION\">the Thirty Years</TIMEX3>"),     // TODO: Should just be "Thirty Years"
                    Timex.fromXml("<TIMEX3 tid=\"t5\" alt_value=\"THIS P1D INTERSECT P1D\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t6\">today's Daily</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"P22Y\" type=\"DURATION\">22-year old</TIMEX3>")
      ).iterator();

    Iterator<Timex> expectedTimexesResolved =
      // TODO: Better range duration
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"PT122736H\" type=\"DURATION\" beginPoint=\"t1\" endPoint=\"t2\">from 1632 to 1646</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"P30Y\" type=\"DURATION\">the Thirty Years</TIMEX3>"),   // TODO: Should just be "Thirty Years"
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"2010-02-17\" type=\"SET\" quant=\"EVERY\" freq=\"P1X\" periodicity=\"P1D\">today's Daily</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"P22Y\" type=\"DURATION\">22-year old</TIMEX3>")
      ).iterator();

    Annotation document = createDocument(testText);

    Properties props = getDefaultProperties();
    props.setProperty(TIME_ANNOTATOR_NAME + ".markTimeRanges", "true");
    TimeAnnotator sutime = getTimeAnnotator(props);
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeIso() throws IOException {
    // Set up test text
    String testText =
            "ISO datetime is 2004-03-04T18:32:56+1600.\n" +
            "ISO datetime is 2004-03-04T18:32:56.\n" +
            "ISO date is 19880217.\n" +        // not recognized ?
            "ISO date is 1988-02-17.\n" +
            "Euro date is 19.02.10.\n" +
            "Euro date is 19.02.2010.\n" +
            "ISO time is T13:23:42.\n" +
            "It is 8:43 on 6/12/2008.\n" +
            "It is 6:53:32 on 7-16-2010.\n" +
            "The date is 12-03-2007.\n" +
            "ISO date without day is 2008-04.\n" +
            "ISO partial datetime 2008-05-16T09.\n" +
            "It is 2:14:12\n";

    // set up expected results
    Iterator<Timex> expectedTimexes =
      Arrays.asList(
              Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2004-03-04T18:32:56+1600\" type=\"TIME\">2004-03-04T18:32:56+1600</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2004-03-04T18:32:56\" type=\"TIME\">2004-03-04T18:32:56</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"1988-02-17\" type=\"DATE\">1988-02-17</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"XX10-02-19\" type=\"DATE\">19.02.10</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"2010-02-19\" type=\"DATE\">19.02.2010</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"T13:23:42\" type=\"TIME\">T13:23:42</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2008-06-12T08:43\" type=\"TIME\">8:43 on 6/12/2008</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"2010-07-16T06:53:32\" type=\"TIME\">6:53:32 on 7-16-2010</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"2007-12-03\" type=\"DATE\">12-03-2007</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"P1D\" type=\"DURATION\">day</TIMEX3>"),               // TODO: Fix to PXD, remove
              Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"2008-04\" type=\"DATE\">2008-04</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"2008-05-16T09\" type=\"TIME\">2008-05-16T09</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"T02:14:12\" type=\"TIME\">2:14:12</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2004-03-04T18:32:56+1600\" type=\"TIME\">2004-03-04T18:32:56+1600</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2004-03-04T18:32:56\" type=\"TIME\">2004-03-04T18:32:56</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"1988-02-17\" type=\"DATE\">1988-02-17</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"2010-02-19\" type=\"DATE\">19.02.10</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"2010-02-19\" type=\"DATE\">19.02.2010</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"2010-02-17T13:23:42\" type=\"TIME\">T13:23:42</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2008-06-12T08:43\" type=\"TIME\">8:43 on 6/12/2008</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"2010-07-16T06:53:32\" type=\"TIME\">6:53:32 on 7-16-2010</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"2007-12-03\" type=\"DATE\">12-03-2007</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"P1D\" type=\"DURATION\">day</TIMEX3>"),               // TODO: Fix to PXD, remove
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"2008-04\" type=\"DATE\">2008-04</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"2008-05-16T09\" type=\"TIME\">2008-05-16T09</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"2010-02-17T02:14:12\" type=\"TIME\">2:14:12</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  // Re-enable me
  public void testSUIsoWithTimezone() throws IOException {
    String testText =
      "ISO datetime is 2004-03-04T18:32:56 Pacific Standard Time.\n" +
        "ISO datetime is 2004-03-04T18:32:56 Eastern Standard Time.\n" +
        "Time is 4:30pm Mountain Time.\n" +        // not recognized ?
        "Time is 9:30 am Pacific Time.\n" +
        "ISO time is T13:23:42 America/Denver.\n" +
        "It is 8:43 PST on 6/12/2008.\n" +
        "It is 6:53:32 PDT on 7-16-2010.\n" +
        "ISO partial datetime 2008-05-16T09 Beijing Time.\n" +
        "It is 2:14:12 MSK\n";
//        "ISO datetime is 11 Feb 2005 17:13:41-0800 (PST).\n" +
//        "ISO datetime is Fri, 11 Feb 2005 17:13:41 -0800 (PST).\n" +
//        "ISO datetime is 2004-03-04T18:32:56-0800 (PST).\n" +
//        "ISO datetime is 2004-03-05T18:32:56 (PST).\n" +
//        "ISO datetime is 2004-03-06T18:32:56+0800.\n" +
//        "ISO datetime is 2004-03-06T18:32:56-0800.\n";
//        "The time is 1400D\n";

    // set up expected results
    Iterator<Timex> expectedTimexes =
      Arrays.asList(
        Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2004-03-04T18:32:56-0800\" type=\"TIME\">2004-03-04T18:32:56 Pacific Standard Time</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2004-03-04T18:32:56-0500\" type=\"TIME\">2004-03-04T18:32:56 Eastern Standard Time</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"T16:30\" type=\"TIME\">4:30pm</TIMEX3>"), // TODO: Mountain Time
        Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"T09:30\" type=\"TIME\">9:30 am</TIMEX3>"),// TODO:  Pacific Time
        Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"T13:23:42-0700\" type=\"TIME\">T13:23:42 America/Denver</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"2008-06-12T08:43-0800\" type=\"TIME\">8:43 PST on 6/12/2008</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2010-07-16T06:53:32-0800\" type=\"TIME\">6:53:32 PDT on 7-16-2010</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"2008-05-16T09\" type=\"TIME\">2008-05-16T09</TIMEX3>"),  // TODO: Beijing Time
        Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"T02:14:12+0300\" type=\"TIME\">2:14:12 MSK</TIMEX3>")
//        Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"2005-02-11T17:13:41-0800\" type=\"TIME\">11 Feb 2005 17:13:41-0800 (PST)</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"2005-02-11T17:13:41-0800\" type=\"TIME\">Fri, 11 Feb 2005 17:13:41-0800 (PST)</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"2004-03-04T18:32:56-0800\" type=\"TIME\">2004-03-04T18:32:56-0800 (PST)</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"2004-03-05T18:32:56-0800\" type=\"TIME\">2004-03-05T18:32:56 (PST)</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"2004-03-06T18:32:56+0800\" type=\"TIME\">2004-03-06T18:32:56+0800</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"2004-03-06T18:32:56-0800\" type=\"TIME\">2004-03-06T18:32:56-0800</TIMEX3>")
//        Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"T14:00+0400\" type=\"TIME\">1400D</TIMEX3>")
      ).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(
        Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2004-03-04T18:32:56-0800\" type=\"TIME\">2004-03-04T18:32:56 Pacific Standard Time</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2004-03-04T18:32:56-0500\" type=\"TIME\">2004-03-04T18:32:56 Eastern Standard Time</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2010-02-17T16:30\" type=\"TIME\">4:30pm</TIMEX3>"), // TODO: Mountain Time
        Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"2010-02-17T09:30\" type=\"TIME\">9:30 am</TIMEX3>"),// TODO:  Pacific Time
        Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"2010-02-17T13:23:42-0700\" type=\"TIME\">T13:23:42 America/Denver</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"2008-06-12T08:43-0800\" type=\"TIME\">8:43 PST on 6/12/2008</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2010-07-16T06:53:32-0800\" type=\"TIME\">6:53:32 PDT on 7-16-2010</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"2008-05-16T09\" type=\"TIME\">2008-05-16T09</TIMEX3>"),  // TODO: Beijing Time
        Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"2010-02-17T02:14:12+0300\" type=\"TIME\">2:14:12 MSK</TIMEX3>")
//        Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"2005-02-11T17:13:41-0800\" type=\"TIME\">11 Feb 2005 17:13:41-0800 (PST)</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"2005-02-11T17:13:41-0800\" type=\"TIME\">Fri, 11 Feb 2005 17:13:41-0800 (PST)</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"2004-03-04T18:32:56-0800\" type=\"TIME\">2004-03-04T18:32:56-0800 (PST)</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"2004-03-05T18:32:56-0800\" type=\"TIME\">2004-03-05T18:32:56 (PST)</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"2004-03-06T18:32:56+0800\" type=\"TIME\">2004-03-06T18:32:56+0800</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"2004-03-06T18:32:56-0800\" type=\"TIME\">2004-03-06T18:32:56-0800</TIMEX3>")
//        Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"2010-02-17T14:00+0400\" type=\"TIME\">1400D</TIMEX3>")
      ).iterator();


    Annotation document = createDocument(testText);

    Properties props = getDefaultProperties();
    TimeAnnotator sutime = getTimeAnnotator(props);
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTime2() throws IOException {
    String testText = "I'm leaving on vacation two weeks from next Tuesday.\n" +
            "John left 2 days before yesterday.\n" +
            "It's almost Thursday.\n" +
            "I tutored an English student some Thursdays in 1994.\n" +
            "The meeting is scheduled for every Monday from 3 to 4 p.m.\n" +
            "The concert is at 8:00 pm on Friday.\n" +
            "The concert is Friday at 8:00 pm.\n" +
            "Did you get up before 7:00 am today?\n" +
            "The company had its third best second quarter ever.\n" +
            "Mr. Smith left Friday, October 1, 1999.\n" +
            "Mr. Smith arrived on the second of December.\n" +
            "Mr. Smith arrived on the second of June.\n" +
            "Mr. Smith arrived on the second of July.\n" +
            "Mr. Smith arrived on the second of August.\n" +
            "In October of 1963, there was a big snow storm.\n" +
            "Yesterday, it was raining.\n" +
            "She arrived Saturday night.\n" +
            "She spent one dollar ($1) yesterday afternoon.\n" +
            "The book was published in nineteen ninety-one.\n";

    // set up expected results
    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" alt_value=\"XXXX-WXX-2 OFFSET P3W\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t2\">two weeks from next Tuesday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" alt_value=\"OFFSET P-3D\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf1\" anchorTimeID=\"t0\">2 days before yesterday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"XXXX-WXX-4\" type=\"DATE\">Thursday</TIMEX3>"),     // TODO: Should almost be included?
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1994-WXX-4\" type=\"SET\" periodicity=\"P1W\">Thursdays in 1994</TIMEX3>"),    // TODO: include phrase some Thursdays in 1994
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"XXXX-WXX-1\" type=\"SET\" quant=\"every\" periodicity=\"P1W\">every Monday</TIMEX3>"),  // TODO: phrase from 3 to 4 p.m.
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"T15:57\" type=\"TIME\">3 to 4 p.m</TIMEX3>"),  // TODO: Set , phrase from 3 to 4 p.m.  (range) // Was "p.m." but now just "p.m" because although TextAnnotation is "p.m.", the OriginalTextAnnotation, which is used here, is just "p.m", because the other period is treated as a sentence final period.
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"XXXX-WXX-5T20:00\" type=\"TIME\">8:00 pm on Friday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"XXXX-WXX-5T20:00\" type=\"TIME\">Friday at 8:00 pm</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" alt_value=\"THIS P1D INTERSECT T07:00\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf2\" anchorTimeID=\"t11\">7:00 am today</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"XXXX-Q2\" type=\"DATE\">second quarter</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"1999-10-01\" type=\"DATE\">Friday, October 1, 1999</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"XXXX-12-02\" type=\"DATE\">the second of December</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" value=\"XXXX-06-02\" type=\"DATE\">the second of June</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" value=\"XXXX-07-02\" type=\"DATE\">the second of July</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t17\" value=\"XXXX-08-02\" type=\"DATE\">the second of August</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t18\" value=\"1963-10\" type=\"DATE\">October of 1963</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t19\" alt_value=\"OFFSET P-1D\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf3\" anchorTimeID=\"t0\">Yesterday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t20\" value=\"XXXX-WXX-6TNI\" type=\"TIME\">Saturday night</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t21\" alt_value=\"OFFSET P-1D INTERSECT AF\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf4\" anchorTimeID=\"t19\">yesterday afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t22\" value=\"1991\" type=\"DATE\">nineteen ninety-one</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1995-08-08\" type=\"DATE\">two weeks from next Tuesday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1995-07-17\" type=\"DATE\">2 days before yesterday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"1995-07-20\" type=\"DATE\">Thursday</TIMEX3>"),     // TODO: Should almost be included?
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"1994-WXX-4\" type=\"SET\" periodicity=\"P1W\">Thursdays in 1994</TIMEX3>"),    // TODO:  include phrase some Thursdays in 1998
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"XXXX-WXX-1\" type=\"SET\" quant=\"every\" periodicity=\"P1W\">every Monday</TIMEX3>"),  // TODO: phrase from 3 to 4 p.m.
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"1995-07-20T15:57\" type=\"TIME\">3 to 4 p.m</TIMEX3>"),  // TODO: Set , phrase from 3 to 4 p.m.  (range) // Was "p.m." but now just "p.m" because although TextAnnotation is "p.m.", the OriginalTextAnnotation, which is used here, is just "p.m", because the other period is treated as a sentence final period.
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"1995-07-21T20:00\" type=\"TIME\">8:00 pm on Friday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"1995-07-21T20:00\" type=\"TIME\">Friday at 8:00 pm</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"1995-07-20T07:00\" type=\"TIME\">7:00 am today</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"1995-Q2\" type=\"DATE\">second quarter</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"1999-10-01\" type=\"DATE\">Friday, October 1, 1999</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"1994-12-02\" type=\"DATE\">the second of December</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"1995-06-02\" type=\"DATE\">the second of June</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"1995-07-02\" type=\"DATE\">the second of July</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" value=\"1994-08-02\" type=\"DATE\">the second of August</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" value=\"1963-10\" type=\"DATE\">October of 1963</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t17\" value=\"1995-07-19\" type=\"DATE\">Yesterday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t18\" value=\"1995-07-22TNI\" type=\"TIME\">Saturday night</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t19\" value=\"1995-07-19TAF\" type=\"TIME\">yesterday afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t20\" value=\"1991\" type=\"DATE\">nineteen ninety-one</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "19950720");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeDate() throws IOException {
    String testText = "Mr Smith left in the summer of 1964.\n" +
            "He started working on Tuesday the 14th.\n" +
            "This year's summer was very hot.\n" +
            "In November 1943, it was very cold.\n" +
            "Are we meeting two weeks from next Tuesday?\n" +
            "The accident happened last week.\n" +
            "The year two thousand.\n" +
            "The 1997 second quarter and 1998 first quarter were very good.\n" +
            "Mr. Smith moved in nineteen ninety six.\n" +
            "The accident was Saturday last week.\n" +
            "He went to Japan August last year.\n" +
            "He died in 567 bc.\n" +
            "The temple was built in the late 5th century B.C. and collapsed in the 3rd century A.D.\n" +
            "I think 1000 BC was a long time ago\n";

    Iterator<Timex> expectedTimexes =
      Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1964-SU\" type=\"DATE\">the summer of 1964</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"XXXX-WXX-2\" type=\"DATE\">Tuesday the 14th</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" alt_value=\"THIS P1Y INTERSECT SU\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t4\">This year's summer</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1943-11\" type=\"DATE\">November 1943</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" alt_value=\"XXXX-WXX-2 OFFSET P3W\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf1\" anchorTimeID=\"t7\">two weeks from next Tuesday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" alt_value=\"THIS P1W OFFSET P-1W\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf2\" anchorTimeID=\"t9\">last week</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"2000\" type=\"DATE\">The year two thousand</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"1997-Q2\" type=\"DATE\">The 1997 second quarter</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"1998-Q1\" type=\"DATE\">1998 first quarter</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"1996\" type=\"DATE\">nineteen ninety six</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" alt_value=\"THIS P1W OFFSET P-1W INTERSECT XXXX-WXX-6\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf3\" anchorTimeID=\"t15\">Saturday last week</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" alt_value=\"THIS P1Y OFFSET P-1Y INTERSECT XXXX-08\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf4\" anchorTimeID=\"t17\">August last year</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t18\" value=\"-0566\" type=\"DATE\">567 bc</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t19\" value=\"-04XX\" type=\"DATE\" mod=\"LATE\">the late 5th century B.C.</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t20\" value=\"02XX\" type=\"DATE\">the 3rd century A.D.</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t21\" value=\"-0999\" type=\"DATE\">1000 BC</TIMEX3>")
              ).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1964-SU\" type=\"DATE\">the summer of 1964</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1999-12-14\" type=\"DATE\">Tuesday the 14th</TIMEX3>"),   // TODO: Fix resolving
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"1999-SU\" type=\"DATE\">This year's summer</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"1943-11\" type=\"DATE\">November 1943</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"2000-01-18\" type=\"DATE\">two weeks from next Tuesday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"1999-W51\" type=\"DATE\">last week</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2000\" type=\"DATE\">The year two thousand</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"1997-Q2\" type=\"DATE\">The 1997 second quarter</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"1998-Q1\" type=\"DATE\">1998 first quarter</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"1996\" type=\"DATE\">nineteen ninety six</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"1999-12-25\" type=\"DATE\">Saturday last week</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"1999-W51-6\" type=\"DATE\">Saturday last week</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"1998-08\" type=\"DATE\">August last year</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"-0566\" type=\"DATE\">567 bc</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"-04XX\" type=\"DATE\" mod=\"LATE\">the late 5th century B.C.</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" value=\"02XX\" type=\"DATE\">the 3rd century A.D.</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" value=\"-0999\" type=\"DATE\">1000 BC</TIMEX3>")
      ).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "1999-12-30");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeTime() throws IOException {
    String testText = "Mr Smith left ten minutes to three.\n" +
    //        "He started working at five to eight.\n" +
            "He went for lunch at twenty after twelve.\n" +
            "He arrived at half past noon.\n" +
            "It happened at eleven in the morning.\n" +
            "The meeting is scheduled for 9 a.m. Friday, October 1, 1999.\n" +
            "He arrived at a quarter past 6.\n";

    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"T02:50\" type=\"TIME\">ten minutes to three</TIMEX3>"),
  //                  Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"T07:55\" type=\"TIME\">five to eight</TIMEX3>"),     // TODO: Fix. For now, expression too vague, err on side of caution
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"T12:20\" type=\"TIME\">twenty after twelve</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"T12:30\" type=\"TIME\">half past noon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"T11:00\" type=\"TIME\">eleven in the morning</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1999-10-01T09:00\" type=\"TIME\">9 a.m. Friday, October 1, 1999</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"T06:15\" type=\"TIME\">a quarter past 6</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1998-04-17T02:50\" type=\"TIME\">ten minutes to three</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1998-04-17T07:55\" type=\"TIME\">five to eight</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1998-04-17T12:20\" type=\"TIME\">twenty after twelve</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"1998-04-17T12:30\" type=\"TIME\">half past noon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"1998-04-17T11:00\" type=\"TIME\">eleven in the morning</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1999-10-01T09:00\" type=\"TIME\">9 a.m. Friday, October 1, 1999</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"1998-04-17T06:15\" type=\"TIME\">a quarter past 6</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "19980417");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeResolveTime() throws IOException {
    String testText = "Mr Smith left ten minutes to three.\n" +
      //        "He started working at five to eight.\n" +
      "He went for lunch at twenty after twelve today.\n" +
      "He arrived at half past noon Saturday.\n" +
      "He is arriving at half past noon Saturday.\n" +
      "It happened at eleven in the morning on Tuesday.\n" +
      "The meeting is scheduled for 9 a.m. tomorrow.\n" +
      "He arrived at a quarter past 6 yesterday.\n";

    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"T02:50\" type=\"TIME\">ten minutes to three</TIMEX3>"),
        //                  Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"T07:55\" type=\"TIME\">five to eight</TIMEX3>"),     // TODO: Fix. For now, expression too vague, err on side of caution
        Timex.fromXml("<TIMEX3 alt_value=\"THIS P1D INTERSECT T12:20\" anchorTimeID=\"t3\" temporalFunction=\"true\" tid=\"t2\" type=\"DATE\" valueFromFunction=\"tf0\">twenty after twelve today</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t4\" type=\"TIME\" value=\"XXXX-WXX-6T12:30\">half past noon Saturday</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t5\" type=\"TIME\" value=\"XXXX-WXX-6T12:30\">half past noon Saturday</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t6\" type=\"TIME\" value=\"XXXX-WXX-2T11:00\">eleven in the morning on Tuesday</TIMEX3>"),
        Timex.fromXml("<TIMEX3 alt_value=\"T09:00 OFFSET P1D\" anchorTimeID=\"t8\" temporalFunction=\"true\" tid=\"t7\" type=\"DATE\" valueFromFunction=\"tf1\">9 a.m. tomorrow</TIMEX3>"),
        Timex.fromXml("<TIMEX3 alt_value=\"T06:15 OFFSET P-1D\" anchorTimeID=\"t10\" temporalFunction=\"true\" tid=\"t9\" type=\"DATE\" valueFromFunction=\"tf2\">a quarter past 6 yesterday</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved1 =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1998-04-17T02:50\" type=\"TIME\">ten minutes to three</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1998-04-17T07:55\" type=\"TIME\">five to eight</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1998-04-17T12:20\" type=\"TIME\">twenty after twelve today</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"1998-04-18T12:30\" type=\"TIME\">half past noon Saturday</TIMEX3>"),  // TODO: Should favor 04-11
        Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"1998-04-18T12:30\" type=\"TIME\">half past noon Saturday</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1998-04-14T11:00\" type=\"TIME\">eleven in the morning on Tuesday</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"1998-04-18T09:00\" type=\"TIME\">9 a.m. tomorrow</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"1998-04-16T06:15\" type=\"TIME\">a quarter past 6 yesterday</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved2 =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1998-04-17T02:50\" type=\"TIME\">ten minutes to three</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1998-04-17T07:55\" type=\"TIME\">five to eight</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1998-04-17T12:20\" type=\"TIME\">twenty after twelve today</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"1998-04-11T12:30\" type=\"TIME\">half past noon Saturday</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"1998-04-18T12:30\" type=\"TIME\">half past noon Saturday</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1998-04-14T11:00\" type=\"TIME\">eleven in the morning on Tuesday</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"1998-04-18T09:00\" type=\"TIME\">9 a.m. tomorrow</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"1998-04-16T06:15\" type=\"TIME\">a quarter past 6 yesterday</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved3 =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1998-04T02:50\" type=\"TIME\">ten minutes to three</TIMEX3>"),
//                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1998-04T07:55\" type=\"TIME\">five to eight</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1998-04T12:20\" type=\"TIME\">twenty after twelve today</TIMEX3>"),
        Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"1998-WXX-6T12:30\" type=\"TIME\">half past noon Saturday</TIMEX3>"),   // TODO: The month is lost
        Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"1998-WXX-6T12:30\" type=\"TIME\">half past noon Saturday</TIMEX3>"),   // TODO: The month is lost
        Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1998-WXX-2T11:00\" type=\"TIME\">eleven in the morning on Tuesday</TIMEX3>"),  // TODO: The month is lost
        Timex.fromXml("<TIMEX3 alt_value=\"1998-04T09:00 OFFSET P1D\" anchorTimeID=\"t7\" temporalFunction=\"true\" tid=\"t6\" type=\"DATE\" valueFromFunction=\"tf0\">9 a.m. tomorrow</TIMEX3>"),
        Timex.fromXml("<TIMEX3 alt_value=\"1998-04T06:15 OFFSET P-1D\" anchorTimeID=\"t9\" temporalFunction=\"true\" tid=\"t8\" type=\"DATE\" valueFromFunction=\"tf1\">a quarter past 6 yesterday</TIMEX3>")).iterator();
//        Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"1998-04T09:00\" type=\"TIME\">9 a.m. tomorrow</TIMEX3>"),
//        Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"1998-04T06:15\" type=\"TIME\">a quarter past 6 yesterday</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "19980417");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved1.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    documentWithRefTime = createDocument(testText, "19980417T11:00");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved2.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    documentWithRefTime = createDocument(testText, "199804XX");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved3.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeRangeWithoutRange() throws IOException {
    String testText =
            "Archduchess Maria Magdalena ( 17 August 1656 - 21 January 1669 )\n";

    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1656-08-17\" type=\"DATE\">17 August 1656</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1669-01-21\" type=\"DATE\">21 January 1669</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
            Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1656-08-17\" type=\"DATE\">17 August 1656</TIMEX3>"),
                          Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1669-01-21\" type=\"DATE\">21 January 1669</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20030414");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeRangeWithRange() throws IOException {
    String testText =
            "Archduchess Maria Magdalena ( 17 August 1656 - 21 January 1669 )\n";

    Iterator<Timex> expectedTimexes =
      Arrays.asList(
              Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1656-08-17\" type=\"DATE\">17 August 1656</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1669-01-21\" type=\"DATE\">21 January 1669</TIMEX3>"),
              // TODO: better range duration
              Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"PT108960H\" type=\"DURATION\" beginPoint=\"t1\" endPoint=\"t2\">17 August 1656 - 21 January 1669</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(
              Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"1656-08-17\" type=\"DATE\">17 August 1656</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1669-01-21\" type=\"DATE\">21 January 1669</TIMEX3>"),
              // TODO: better range duration
              Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"PT108960H\" type=\"DURATION\" beginPoint=\"t1\" endPoint=\"t2\">17 August 1656 - 21 January 1669</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    Properties props = getDefaultProperties();
    props.setProperty(TIME_ANNOTATOR_NAME + ".markTimeRanges", "true");
    props.setProperty(TIME_ANNOTATOR_NAME + ".includeNested", "true");
    TimeAnnotator sutime = getTimeAnnotator(props);
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20030414");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeRangeConversion() throws IOException {
    String testText =
            "The morning of January 31 was very cold.\n" +
            "December of 2002.\n" +
            "This winter was not as cold.\n" +
            "The 1960s.\n" +
            "He returned in 1971.\n" +
            "Meeting in madrid this week.\n" +
            "They watched a movie over the weekend.\n" +
            // TODO: semi-annual
            "But publicity-shy Gabriel Garcia Marquez , who turned 80 this month, did finally appear Monday at the semi-annual meeting of the Inter American Press Association as it ended with a luncheon\n" +
            "The event happens tomorrow night, not Wednesday afternoon.\n" +
            "What is last week, or last month, or even the last 3 months, or just last 3 months?";


    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"XXXX-01-31TMO\" type=\"TIME\" range=\"(XXXX-01-31T06:00:00.000,XXXX-01-31T12:00,PT6H)\">The morning of January 31</TIMEX3>"), // TODO: noon....
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2002-12\" type=\"DATE\" range=\"(2002-12-01,2002-12-31,P1M)\">December of 2002</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" alt_value=\"THIS WI\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t0\" range=\"(THIS WI,THIS WI,)\">This winter</TIMEX3>"), // TODO: range
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"196X\" type=\"DATE\" range=\"(1960-01-01,1969-12-31,P10Y)\">The 1960s</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1971\" type=\"DATE\" range=\"(1971-01-01,1971-12-31,P1Y)\">1971</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" alt_value=\"THIS P1W\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf1\" anchorTimeID=\"t0\" range=\"(THIS P1W,THIS P1W,)\">this week</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"XXXX-WE\" type=\"DATE\" range=\"(XXXX-WXX-6,XXXX-WXX-7,P2D)\">the weekend</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" alt_value=\"THIS P1M\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf2\" anchorTimeID=\"t0\" range=\"(THIS P1M,THIS P1M,)\">this month</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"XXXX-WXX-1\" type=\"DATE\" range=\"(XXXX-WXX-1,XXXX-WXX-1,P1D)\">Monday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"P6M\" type=\"SET\" quant=\"EVERY\" freq=\"P1X\" periodicity=\"P6M\">semi-annual</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 alt_value=\"OFFSET P1D INTERSECT NI\" anchorTimeID=\"t12\" range=\"(OFFSET P1D INTERSECT NI,OFFSET P1D INTERSECT NI,)\" temporalFunction=\"true\" tid=\"t11\" type=\"DATE\" valueFromFunction=\"tf3\">tomorrow night</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 range=\"(XXXX-WXX-3T12:00:00.000,XXXX-WXX-3T18,PT6H)\" tid=\"t13\" type=\"TIME\" value=\"XXXX-WXX-3TAF\">Wednesday afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" alt_value=\"THIS P1W OFFSET P-1W\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf4\" anchorTimeID=\"t15\" range=\"(THIS P1W OFFSET P-1W,THIS P1W OFFSET P-1W,)\">last week</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" alt_value=\"THIS P1M OFFSET P-1M\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf5\" anchorTimeID=\"t17\" range=\"(THIS P1M OFFSET P-1M,THIS P1M OFFSET P-1M,)\">last month</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 beginPoint=\"t18\" endPoint=\"t0\" range=\"(UNKNOWN,REF,P3M)\" tid=\"t19\" type=\"DURATION\" value=\"P3M\">the last 3 months</TIMEX3>"), // TODO: Fix this treatment of "the xxx" as duration (but that's what tempeval wants)
                    Timex.fromXml("<TIMEX3 alt_value=\"THIS P3M OFFSET P-3M\" anchorTimeID=\"t21\" range=\"(THIS P3M OFFSET P-3M,THIS P3M OFFSET P-3M,)\" temporalFunction=\"true\" tid=\"t20\" type=\"DATE\" valueFromFunction=\"tf6\">last 3 months</TIMEX3>")
      ).iterator();
    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2003-01-31TMO\" type=\"TIME\" range=\"(2003-01-31T06:00:00.000,2003-01-31T12:00,PT6H)\">The morning of January 31</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2002-12\" type=\"DATE\" range=\"(2002-12-01,2002-12-31,P1M)\">December of 2002</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2003-WI\" type=\"DATE\" range=\"(2003-12-01,2003-03,P3M)\">This winter</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"196X\" type=\"DATE\" range=\"(1960-01-01,1969-12-31,P10Y)\">The 1960s</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1971\" type=\"DATE\" range=\"(1971-01-01,1971-12-31,P1Y)\">1971</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"2003-W16\" type=\"DATE\" range=\"(2003-04-14,2003-04-20,P1W)\">this week</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2003-W16-WE\" type=\"DATE\" range=\"(2003-04-19,2003-04-20,P2D)\">the weekend</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"2003-04\" type=\"DATE\" range=\"(2003-04-01,2003-04-30,P1M)\">this month</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"2003-04-14\" type=\"DATE\" range=\"(2003-04-14-WXX-1,2003-04-14-WXX-1,P1D)\">Monday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"P6M\" type=\"SET\" quant=\"EVERY\" freq=\"P1X\" periodicity=\"P6M\">semi-annual</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 range=\"(2003-04-15T19:00:00.000,2003-04-16T05:00:00.000,PT10H)\" tid=\"t11\" type=\"TIME\" value=\"2003-04-15TNI\">tomorrow night</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 range=\"(2003-04-16-WXX-3T12:00:00.000,2003-04-16-WXX-3T18,PT6H)\" tid=\"t12\" type=\"TIME\" value=\"2003-04-16TAF\">Wednesday afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 range=\"(2003-04-07,2003-04-13,P1W)\" tid=\"t13\" type=\"DATE\" value=\"2003-W15\">last week</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 range=\"(2003-03-01,2003-03-31,P1M)\" tid=\"t14\" type=\"DATE\" value=\"2003-03\">last month</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 beginPoint=\"t15\" endPoint=\"t0\" range=\"(UNKNOWN,REF,P3M)\" tid=\"t16\" type=\"DURATION\" value=\"P3M\">the last 3 months</TIMEX3>"), // TODO: Fix this
                    Timex.fromXml("<TIMEX3 range=\"(2003-01-14,2003-04-14,P3M)\" tid=\"t17\" type=\"DATE\" value=\"2003-01-14/2003-04-14\">last 3 months</TIMEX3>")
      ).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    Properties props = getDefaultProperties();
    props.setProperty("sutime.includeRange", "true");
    TimeAnnotator sutime = new TimeAnnotator("sutime", props);
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20030414");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeEmptySentence() throws IOException {
    String testText = ".";

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    Properties props = getDefaultProperties();
    props.setProperty(TIME_ANNOTATOR_NAME + ".includeRange", "true");
    TimeAnnotator sutime = getTimeAnnotator(props);
    sutime.annotate(document);

    // Check answers
    List<CoreMap> timexes = document.get(TimeAnnotations.TimexAnnotations.class);
    assertEquals(0, timexes.size());

    Annotation documentWithRefTime = createDocument(testText, "2007-05-14");
    sutime.annotate(documentWithRefTime);

    timexes = document.get(TimeAnnotations.TimexAnnotations.class);
    assertEquals(0, timexes.size());
  }


  public void testSUTimeInexactTime() throws IOException {
    String testText =
            "The morning of January 31 was very cold.\n" +
            "He arrived late last night.\n" +
            "He arrived last night.\n";

    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"XXXX-01-31TMO\" type=\"TIME\">The morning of January 31</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" alt_value=\"TNI OFFSET P-1D\" type=\"DATE\" mod=\"LATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t3\">late last night</TIMEX3>"),   // TODO: time
                    Timex.fromXml("<TIMEX3 tid=\"t4\" alt_value=\"TNI OFFSET P-1D\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf1\" anchorTimeID=\"t5\">last night</TIMEX3>")).iterator();  // TODO: time

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2003-01-31TMO\" type=\"TIME\">The morning of January 31</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2003-04-13TNI\" type=\"TIME\" mod=\"LATE\">late last night</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2003-04-13TNI\" type=\"TIME\">last night</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20030414");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeSet() throws IOException {
    String testText = "John swims twice a week.\n" +
            "Every 2 days, he goes jogging.\n" +
            "Every third week of October, he goes to the lake.\n" +
            "On alternate Fridays, he drives to the park.\n";

    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"P1W\" type=\"DURATION\">a week</TIMEX3>"),    //TODO: twice a week
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"P2D\" type=\"SET\" quant=\"every\" periodicity=\"P2D\">Every 2 days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" alt_value=\"XXXX-10 INTERSECT P1W-#3\" type=\"SET\" quant=\"every\">Every third week of October</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"XXXX-WXX-5\" type=\"SET\" quant=\"alternate\" periodicity=\"P2W\">alternate Fridays</TIMEX3>")).iterator();
    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"P1W\" type=\"DURATION\">a week</TIMEX3>"),    //TODO: twice a week
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"P2D\" type=\"SET\" quant=\"every\" periodicity=\"P2D\">Every 2 days</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" alt_value=\"XXXX-10 INTERSECT P1W-#3\" type=\"SET\" quant=\"every\">Every third week of October</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"XXXX-WXX-5\" type=\"SET\" quant=\"alternate\" periodicity=\"P2W\">alternate Fridays</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20030414");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeDateTime() throws IOException {
    // Set up test text
    String testText = "The vase of 14 fell early Friday evening.\n" +
            "The bus is not coming until 8:00 pm.\n" +
            "They were to have lunch at 12:15 on Thursday.\n" +
            "Or was it quarter to twelve on Wed?\n" +
            "He got home at twelve o'clock midnight.\n" +
            "Next Tuesday is Tuesday the 18th.\n" +
            "It happened early yesterday morning.\n" +
            "It happened late afternoon.\n" +
            "It happened late this afternoon.\n" +
            "It happened at 1800 hours.\n" +
            "The early nineteen fifties.\n" +
            "The story broke in the last week of October.\n" +
            "It was 7pm and then 7:20pm.";

    // set up expected results
    Iterator<Timex> expectedTimexes =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"XXXX-WXX-5TEV\" type=\"TIME\" mod=\"EARLY\">early Friday evening</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"T20:00\" type=\"TIME\">8:00 pm</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"XXXX-WXX-4T12:15\" type=\"TIME\">12:15 on Thursday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"XXXX-WXX-3T11:45\" type=\"TIME\">quarter to twelve on Wed</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"T00:00\" type=\"TIME\">twelve o'clock midnight</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" alt_value=\"XXXX-WXX-2 OFFSET P1W\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t7\">Next Tuesday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"XXXX-WXX-2\" type=\"DATE\">Tuesday the 18th</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" alt_value=\"OFFSET P-1D INTERSECT MO\" type=\"DATE\" mod=\"EARLY\" temporalFunction=\"true\" valueFromFunction=\"tf1\" anchorTimeID=\"t10\">early yesterday morning</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"TAF\" type=\"TIME\" mod=\"LATE\">late afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" alt_value=\"THIS AF\" type=\"DATE\" mod=\"LATE\" temporalFunction=\"true\" valueFromFunction=\"tf2\" anchorTimeID=\"t0\">late this afternoon</TIMEX3>"),    // TODO: time
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"T18:00\" type=\"TIME\">1800 hours</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"195X\" type=\"DATE\" mod=\"EARLY\">The early nineteen fifties</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" alt_value=\"PREV_IMMEDIATE P1W INTERSECT XXXX-10\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf3\" anchorTimeID=\"t16\">the last week of October</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t17\" value=\"T19:00\" type=\"TIME\">7pm</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t18\" value=\"T19:20\" type=\"TIME\">7:20pm.</TIMEX3>") // TODO: the period should be dropped
      ).iterator();

    Iterator<Timex> expectedTimexesResolved =
      Arrays.asList(Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2005-08-12TEV\" type=\"TIME\" mod=\"EARLY\">early Friday evening</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2005-08-12T20:00\" type=\"TIME\">8:00 pm</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2005-08-11T12:15\" type=\"TIME\">12:15 on Thursday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"2005-08-10T11:45\" type=\"TIME\">quarter to twelve on Wed</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"2005-08-12T00:00\" type=\"TIME\">twelve o'clock midnight</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"2005-08-16\" type=\"DATE\">Next Tuesday</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2005-08-18\" type=\"DATE\">Tuesday the 18th</TIMEX3>"),    // TODO: Tuesday, the 18th  flag inconsistency (18th is thursday)
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"2005-08-11TMO\" type=\"TIME\" mod=\"EARLY\">early yesterday morning</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"2005-08-12TAF\" type=\"TIME\" mod=\"LATE\">late afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"2005-08-12TAF\" type=\"TIME\" mod=\"LATE\">late this afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"2005-08-12T18:00\" type=\"TIME\">1800 hours</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"195X\" type=\"DATE\" mod=\"EARLY\">The early nineteen fifties</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" alt_value=\"PREV_IMMEDIATE P1W INTERSECT XXXX-10\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t14\">the last week of October</TIMEX3>"),    // TODO: Resolve
                    Timex.fromXml("<TIMEX3 tid=\"t15\" value=\"2005-08-12T19:00\" type=\"TIME\">7pm</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" value=\"2005-08-12T19:20\" type=\"TIME\">7:20pm.</TIMEX3>") // TODO: the period should be dropped
      ).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20050812");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  // TODO: Re-enable me
  public void testSUTimeDateTime2() throws IOException {
    // Set up test text
    String testText = "The meeting is scheduled for 09/18/05 or 18 Sep '05.\n" +
                      "1 year ago tomorrow.\n" +
                      "3 months ago saturday at 5:00 pm.\n" +
                      "7 hours before tomorrow at noon.\n" +
                      "3rd wednesday in november.\n" +
                      "3rd month next year.\n" +
                      "3rd thursday this september.\n" +
                      "4th day last week.\n" +
                      "fourteenth of june 2010 at eleven o'clock in the evening.\n" +
                      "may seventh '97 at three in the morning.\n" +
                      "The third week of april was very hot.\n";

    // set up expected results
    Iterator<Timex> expectedTimexes =
            Arrays.asList(
              Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"XX05-09-18\" type=\"DATE\">09/18/05</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"XX05-09-18\" type=\"DATE\">18 Sep '05</TIMEX3>"),
              Timex.fromXml("<TIMEX3 alt_value=\"OFFSET P-1Y INTERSECT OFFSET P1D\" anchorTimeID=\"t4\" temporalFunction=\"true\" tid=\"t3\" type=\"DATE\" valueFromFunction=\"tf0\">1 year ago tomorrow</TIMEX3>"),
              Timex.fromXml("<TIMEX3 alt_value=\"OFFSET P-3M INTERSECT XXXX-WXX-6T17:00\" anchorTimeID=\"t6\" temporalFunction=\"true\" tid=\"t5\" type=\"DATE\" valueFromFunction=\"tf1\">3 months ago saturday at 5:00 pm</TIMEX3>"),
              Timex.fromXml("<TIMEX3 alt_value=\"OFFSET P1DT-7H INTERSECT T12:00\" anchorTimeID=\"t8\" temporalFunction=\"true\" tid=\"t7\" type=\"DATE\" valueFromFunction=\"tf2\">7 hours before tomorrow at noon</TIMEX3>"),
              Timex.fromXml("<TIMEX3 alt_value=\"XXXX-11-WXX-3-#3\" tid=\"t9\" type=\"DATE\">3rd wednesday in november</TIMEX3>"),
              Timex.fromXml("<TIMEX3 alt_value=\"THIS P1Y OFFSET P1Y INTERSECT P1M-#3\" anchorTimeID=\"t11\" temporalFunction=\"true\" tid=\"t10\" type=\"DATE\" valueFromFunction=\"tf3\">3rd month next year</TIMEX3>"),
              Timex.fromXml("<TIMEX3 alt_value=\"THIS XXXX-09 INTERSECT XXXX-WXX-4-#3\" anchorTimeID=\"t13\" temporalFunction=\"true\" tid=\"t12\" type=\"DATE\" valueFromFunction=\"tf4\">3rd thursday this september</TIMEX3>"),
              Timex.fromXml("<TIMEX3 alt_value=\"THIS P1W OFFSET P-1W INTERSECT P1D-#4\" anchorTimeID=\"t15\" temporalFunction=\"true\" tid=\"t14\" type=\"DATE\" valueFromFunction=\"tf5\">4th day last week</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t16\" type=\"TIME\" value=\"2010-06-14T23:00\">fourteenth of june 2010 at eleven o'clock in the evening</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t17\" type=\"TIME\" value=\"XX97-05-07T03:00\">may seventh '97 at three in the morning</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t18\" alt_value=\"XXXX-04 INTERSECT P1W-#3\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf6\" anchorTimeID=\"t19\">The third week of april</TIMEX3>")
            ).iterator();

    Iterator<Timex> expectedTimexesResolved =
            Arrays.asList(
              Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2005-09-18\" type=\"DATE\">09/18/05</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2005-09-18\" type=\"DATE\">18 Sep '05</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2008-08-13\" type=\"DATE\">1 year ago tomorrow</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"2009-05-12T17:00\" type=\"TIME\">3 months ago saturday at 5:00 pm</TIMEX3>"),  // TODO: Should be 05-10
              Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"2009-08-13T12:00\" type=\"TIME\">7 hours before tomorrow at noon</TIMEX3>"),  // TODO: Should be T05:00
              Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"2009-11-18\" type=\"DATE\">3rd wednesday in november</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2010-03\" type=\"DATE\">3rd month next year</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"2009-09-17\" type=\"DATE\">3rd thursday this september</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"2009-08-06\" type=\"DATE\">4th day last week</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"2010-06-14T23:00\" type=\"TIME\">fourteenth of june 2010 at eleven o'clock in the evening</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"1997-05-07T03:00\" type=\"TIME\">may seventh '97 at three in the morning</TIMEX3>"),
              Timex.fromXml("<TIMEX3 tid=\"t12\" type=\"DATE\" value=\"2009-W16\">The third week of april</TIMEX3>")  // TODO: check week number
            ).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20090812");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeIso2() throws IOException {
    // Set up test text
    String testText =
            "ISO date is 6/12/2008, 1988-02-17.\n";

    // set up expected results
    Iterator<Timex> expectedTimexes =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2008-06-12\" type=\"DATE\">6/12/2008</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1988-02-17\" type=\"DATE\">1988-02-17</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2008-06-12\" type=\"DATE\">6/12/2008</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"1988-02-17\" type=\"DATE\">1988-02-17</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeDate2() throws IOException {
    // Set up test text
    String testText =
            "The date is October 15, 2003.\n" +
                    "The fires started on the first of August 2010.\n" +
                    "It snowed in November 2004.\n" +
                    "The year twenty ten is here.\n" +
                    "I was born in 1879\n"/* +
             "They met every Tuesday afternoon this March." */;

    // set up expected results
    Iterator<Timex> expectedTimexes =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2003-10-15\" type=\"DATE\">October 15, 2003</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2010-08-01\" type=\"DATE\">the first of August 2010</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2004-11\" type=\"DATE\">November 2004</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"2010\" type=\"DATE\">The year twenty ten</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1879\" type=\"DATE\">1879</TIMEX3>")
            ).iterator();

    Iterator<Timex> expectedTimexesResolved =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2003-10-15\" type=\"DATE\">October 15, 2003</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2010-08-01\" type=\"DATE\">the first of August 2010</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2004-11\" type=\"DATE\">November 2004</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"2010\" type=\"DATE\">The year twenty ten</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"1879\" type=\"DATE\">1879</TIMEX3>")
            ).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTimeHolidays() throws IOException {
    // Set up test text
    String testText =
            "When is mother's day 2012?\n" +
                    "When is Christmas 2010?\n" +
                    "When is Easter 2011?\n";

    // set up expected results
    Iterator<Timex> expectedTimexes =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2012-05-13\" type=\"DATE\">mother's day 2012</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2010-12-25\" type=\"DATE\">Christmas 2010</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2011-04-24\" type=\"DATE\">Easter 2011</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2012-05-13\" type=\"DATE\">mother's day 2012</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2010-12-25\" type=\"DATE\">Christmas 2010</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2011-04-24\" type=\"DATE\">Easter 2011</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testSUTime12AmPm() throws IOException {
    // Set up test text
    String testText =
            "The hour of 12 in the morning.\n" +
                    "It is 12 in the morning.  It is 12:00 or is it 12 am.\n" +
                    "It is 12 in the afternoon.  It is 12 in the evening.\n" +
                    "It is 12 at night.  It is 12 pm, in 12 hours it will be 24 o'clock (24:00).\n" +
                    "It is 12:34 p.m.";

    // set up expected results
    Iterator<Timex> expectedTimexes =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"T00:00\" type=\"TIME\">The hour of 12 in the morning</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"T00:00\" type=\"TIME\">12 in the morning</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"T12:00\" type=\"TIME\">12:00</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"T00:00\" type=\"TIME\">12 am</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"T12:00\" type=\"TIME\">12 in the afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" alt_value=\"T00:00 OFFSET P1D\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf0\" anchorTimeID=\"t7\">12 in the evening</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" alt_value=\"T00:00 OFFSET P1D\" type=\"DATE\" temporalFunction=\"true\" valueFromFunction=\"tf1\" anchorTimeID=\"t9\">12 at night</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"T12:00\" type=\"TIME\">12 pm</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"PT12H\" type=\"DURATION\">12 hours</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"T24:00\" type=\"TIME\">24 o'clock</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" value=\"T24:00\" type=\"TIME\">24:00</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" value=\"T12:34\" type=\"TIME\">12:34 p.m.</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" value=\"2010-02-17T00:00\" type=\"TIME\">The hour of 12 in the morning</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" value=\"2010-02-17T00:00\" type=\"TIME\">12 in the morning</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" value=\"2010-02-17T12:00\" type=\"TIME\">12:00</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" value=\"2010-02-17T00:00\" type=\"TIME\">12 am</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" value=\"2010-02-17T12:00\" type=\"TIME\">12 in the afternoon</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" value=\"2010-02-18T00:00\" type=\"TIME\">12 in the evening</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" value=\"2010-02-18T00:00\" type=\"TIME\">12 at night</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" value=\"2010-02-17T12:00\" type=\"TIME\">12 pm</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" value=\"PT12H\" type=\"DURATION\">12 hours</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" value=\"2010-02-17T24:00\" type=\"TIME\">24 o'clock</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" value=\"2010-02-17T24:00\" type=\"TIME\">24:00</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" value=\"2010-02-17T12:34\" type=\"TIME\">12:34 p.m.</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testInnerCaptureGroupAccess() throws IOException {
    // Set up test text
    String testText = "The sword is believed to be from the 10th-century a.d. according to experts.";
    // set up expected results
    Iterator<Timex> expectedTimexes =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"09XX\">the 10th-century a.d.</TIMEX3>")
            ).iterator();
    // run test
    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }

  public void testOverlaps() throws IOException {
    String testText = "Sun Apr 21\n" +
            "Wed Apr 24\n" +
            "Fri Apr 26\n" +
            "Wed May 1\n" +
            "Fri May 3\n" +
            "Sun May 5\n" +
            "Fri May 10\n" +
            "Sat May 11\n" +
            "Wed May 15\n" +
            "Sat May 18\n" +
            "Wed May 22\n" +
            "Mon May 27\n" +
            "Fri May 31\n" +
            "Mon June 3\n" +
            "    June 8\n" +
            "Tue Jun 18\n" +
            "Wed Jun 19\n";

    // set up expected results
    Iterator<Timex> expectedTimexes =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"XXXX-04-21\">Sun Apr 21</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" type=\"DATE\" value=\"XXXX-04-24\">Wed Apr 24</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" type=\"DATE\" value=\"XXXX-04-26\">Fri Apr 26</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" type=\"DATE\" value=\"XXXX-05-01\">Wed May 1</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" type=\"DATE\" value=\"XXXX-05-03\">Fri May 3</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" type=\"DATE\" value=\"XXXX-05-05\">Sun May 5</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" type=\"DATE\" value=\"XXXX-05-10\">Fri May 10</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" type=\"DATE\" value=\"XXXX-05-11\">Sat May 11</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" type=\"DATE\" value=\"XXXX-05-15\">Wed May 15</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" type=\"DATE\" value=\"XXXX-05-18\">Sat May 18</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" type=\"DATE\" value=\"XXXX-05-22\">Wed May 22</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" type=\"DATE\" value=\"XXXX-05-27\">Mon May 27</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" type=\"DATE\" value=\"XXXX-05-31\">Fri May 31</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" type=\"DATE\" value=\"XXXX-06-03\">Mon June 3</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" type=\"DATE\" value=\"XXXX-06-08\">June 8\nTue</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" type=\"DATE\" value=\"XXXX-06-18\">Jun 18\nWed</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t17\" type=\"DATE\" value=\"XXXX-06-19\">Jun 19</TIMEX3>")).iterator();

    Iterator<Timex> expectedTimexesResolved =
            Arrays.asList(
                    Timex.fromXml("<TIMEX3 tid=\"t1\" type=\"DATE\" value=\"2010-04-21\">Sun Apr 21</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t2\" type=\"DATE\" value=\"2010-04-24\">Wed Apr 24</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t3\" type=\"DATE\" value=\"2010-04-26\">Fri Apr 26</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t4\" type=\"DATE\" value=\"2010-05-01\">Wed May 1</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t5\" type=\"DATE\" value=\"2010-05-03\">Fri May 3</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t6\" type=\"DATE\" value=\"2010-05-05\">Sun May 5</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t7\" type=\"DATE\" value=\"2010-05-10\">Fri May 10</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t8\" type=\"DATE\" value=\"2010-05-11\">Sat May 11</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t9\" type=\"DATE\" value=\"2010-05-15\">Wed May 15</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t10\" type=\"DATE\" value=\"2010-05-18\">Sat May 18</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t11\" type=\"DATE\" value=\"2010-05-22\">Wed May 22</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t12\" type=\"DATE\" value=\"2010-05-27\">Mon May 27</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t13\" type=\"DATE\" value=\"2010-05-31\">Fri May 31</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t14\" type=\"DATE\" value=\"2010-06-03\">Mon June 3</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t15\" type=\"DATE\" value=\"2010-06-08\">June 8\nTue</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t16\" type=\"DATE\" value=\"2010-06-18\">Jun 18\nWed</TIMEX3>"),
                    Timex.fromXml("<TIMEX3 tid=\"t17\" type=\"DATE\" value=\"2010-06-19\">Jun 19</TIMEX3>")).iterator();

    // create document
    Annotation document = createDocument(testText);

    // Time annotate
    TimeAnnotator sutime = getTimeAnnotator();
    sutime.annotate(document);

    // Check answers
    for (CoreMap timexAnn: document.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexes.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());

    Annotation documentWithRefTime = createDocument(testText, "20100217");
    sutime.annotate(documentWithRefTime);

    for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
      Timex expectedTimex = expectedTimexesResolved.next();
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
    }
    assertFalse(expectedTimexes.hasNext());
  }


  // Early summer morning
  // A morning in early summer

  public void testResolve1()
  {
    String[] refTimes = {
            "2011-06-03",
            "2010-06-03",
            "2010-05-03",
            "2011-05-03",
            "1988-01-03"
    };

    String[] expectedTimexStrsResolved =
            { "<TIMEX3 tid=\"t1\" value=\"2011-05-23\" type=\"DATE\">last Monday</TIMEX3>",
              "<TIMEX3 tid=\"t1\" value=\"2010-05-24\" type=\"DATE\">last Monday</TIMEX3>",
              "<TIMEX3 tid=\"t1\" value=\"2010-04-26\" type=\"DATE\">last Monday</TIMEX3>",
              "<TIMEX3 tid=\"t1\" value=\"2011-04-25\" type=\"DATE\">last Monday</TIMEX3>",
              "<TIMEX3 tid=\"t1\" value=\"1987-12-21\" type=\"DATE\">last Monday</TIMEX3>"
            };

    String testText = "last Monday";
    for (int i = 0; i < refTimes.length; i++) {
      String refTime = refTimes[i];
      String expectedTimexStr = expectedTimexStrsResolved[i];
      Timex expectedTimex = Timex.fromXml(expectedTimexStr);

      // create document
      Annotation documentWithRefTime = createDocument(testText, refTime);

      // Time annotate
      TimeAnnotator sutime = getTimeAnnotator();
      sutime.annotate(documentWithRefTime);

      List<CoreMap> timexes = documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class);
      assertEquals(1, timexes.size());
      checkTimex(testText, expectedTimex.text(), expectedTimex, timexes.get(0));
    }
  }


  public void testResolve2()
  {
    String[] refTimes = {
            "2011-06-03",
            "2010-11-23",
            "1988-01-16"
    };

    String[] refModifiers = { "last", "this", "next", "this coming"/*, "this past"*/};

    String[] refExprs = {
            "week",
            "weekend",
            "fortnight",
            "month",
            "quarter",
         //   "season",
            "year",
            "decade",
            "century",
            "spring",
            "summer",
            "autumn",
            "fall",
            "winter",

            "monday",
            "tuesday",
            "wednesday",
            "thursday",
            "friday",
            "saturday",
            "sunday",

            "january",
            "february",
            "march",
            "april",
            "May",    // hmmm
            "june",
            "july",
            "august",
            "september",
            "october",
            "november",
            "december",

            "easter",
            "mother's day"
    };

    String[][] expectedValuesPrev =
      { /* 2011-06-03 */
        { "2011-W21", "2011-W21-WE", "2011-05-20/2011-06-03",
          "2011-05", "2011-Q1", "2010", "200X", "19XX",
          "2010-SP", "2010-SU", "2010-FA", "2010-FA", "2010-WI",
          "2011-05-23", "2011-05-24", "2011-05-25", "2011-05-26", "2011-05-27", "2011-05-28", "2011-05-29", //"2011-05-WE?",
          "2010-01", "2010-02", "2010-03", "2010-04", "2010-05", "2010-06",
          "2010-07", "2010-08", "2010-09", "2010-10", "2010-11", "2010-12",
          "2010-04-04", "2010-05-09"
        },
        /* 2010-11-23 */
        { "2010-W46", "2010-W46-WE", "2010-11-09/2010-11-23",
          "2010-10", "2010-Q3", "2009", "200X", "19XX",
          "2009-SP", "2009-SU", "2009-FA", "2009-FA", "2009-WI",
          "2010-11-15", "2010-11-16", "2010-11-17", "2010-11-18", "2010-11-19", "2010-11-20", "2010-11-21",// "2010-11-23",
          "2009-01", "2009-02", "2009-03", "2009-04", "2009-05", "2009-06",
          "2009-07", "2009-08", "2009-09", "2009-10", "2009-11", "2009-12",
          "2009-04-12", "2009-05-10"
        },
        /* 1988-01-16 */
        { "1988-W01", "1988-W01-WE", "1988-01-02/1988-01-16",
          "1987-12", "1987-Q4", "1987", "197X", "18XX",
          "1987-SP", "1987-SU", "1987-FA", "1987-FA", "1987-WI",
          "1988-01-04", "1988-01-05", "1988-01-06", "1988-01-07", "1988-01-08", "1988-01-09", "1988-01-10",// "2010-11-23",
          "1987-01", "1987-02", "1987-03", "1987-04", "1987-05", "1987-06",
          "1987-07", "1987-08", "1987-09", "1987-10", "1987-11", "1987-12",
          "1987-04-19", "1987-05-10"
        },
      };

    String[][] expectedValuesThis =
      { /* 2011-06-03 */
        { "2011-W22", "2011-W22-WE", "2011-05-27/2011-06-10",
          "2011-06", "2011-Q2", "2011", "201X", "20XX",
          "2011-SP", "2011-SU", "2011-FA", "2011-FA", "2011-WI",
          "2011-05-30", "2011-05-31", "2011-06-01", "2011-06-02", "2011-06-03", "2011-06-04", "2011-06-05",// "2011-06-WE?",
          "2011-01", "2011-02", "2011-03", "2011-04", "2011-05", "2011-06",
          "2011-07", "2011-08", "2011-09", "2011-10", "2011-11", "2011-12",
          "2011-04-24", "2011-05-08"
        },
        /* 2010-11-23 */
        { "2010-W47", "2010-W47-WE", "2010-11-16/2010-11-30",
          "2010-11", "2010-Q4", "2010", "201X", "20XX",
          "2010-SP", "2010-SU", "2010-FA", "2010-FA", "2010-WI",
          "2010-11-22", "2010-11-23", "2010-11-24", "2010-11-25", "2010-11-26", "2010-11-27", "2010-11-28",// "2010-11-23",
          "2010-01", "2010-02", "2010-03", "2010-04", "2010-05", "2010-06",
          "2010-07", "2010-08", "2010-09", "2010-10", "2010-11", "2010-12",
          "2010-04-04", "2010-05-09"
        },
        /* 1988-01-16 */
        { "1988-W02", "1988-W02-WE", "1988-01-09/1988-01-23",
          "1988-01", "1988-Q1", "1988", "198X", "19XX",
          "1988-SP", "1988-SU", "1988-FA", "1988-FA", "1988-WI",
          "1988-01-11", "1988-01-12", "1988-01-13", "1988-01-14", "1988-01-15", "1988-01-16", "1988-01-17",// "2010-11-23",
          "1988-01", "1988-02", "1988-03", "1988-04", "1988-05", "1988-06",
          "1988-07", "1988-08", "1988-09", "1988-10", "1988-11", "1988-12",
          "1988-04-03", "1988-05-08"
        },
      };

    String[][] expectedValuesNext =
      { /* 2011-06-03 */
        { "2011-W23", "2011-W23-WE", "2011-06-03/2011-06-17",
          "2011-07", "2011-Q3", "2012", "202X", "21XX",
          "2012-SP", "2012-SU", "2012-FA", "2012-FA", "2012-WI",
          "2011-06-06", "2011-06-07", "2011-06-08", "2011-06-09", "2011-06-10", "2011-06-11", "2011-06-12",// "2011-06-WE?",
          "2012-01", "2012-02", "2012-03", "2012-04", "2012-05", "2012-06",
          "2012-07", "2012-08", "2012-09", "2012-10", "2012-11", "2012-12",
          "2012-04-08", "2012-05-13"
        },
        /* 2010-11-23 */
        { "2010-W48", "2010-W48-WE", "2010-11-23/2010-12-07",
          "2010-12", "2011-Q1", "2011", "202X", "21XX",
          "2011-SP", "2011-SU", "2011-FA", "2011-FA", "2011-WI",
          "2010-11-29", "2010-11-30", "2010-12-01", "2010-12-02", "2010-12-03", "2010-12-04", "2010-12-05",// "2010-11-23",
          "2011-01", "2011-02", "2011-03", "2011-04", "2011-05", "2011-06",
          "2011-07", "2011-08", "2011-09", "2011-10", "2011-11", "2011-12",
          "2011-04-24", "2011-05-08"
        },
        /* 1988-01-16 */
        { "1988-W03", "1988-W03-WE", "1988-01-16/1988-01-30",
          "1988-02", "1988-Q2", "1989", "199X", "20XX",
          "1989-SP", "1989-SU", "1989-FA", "1989-FA", "1989-WI",
          "1988-01-18", "1988-01-19", "1988-01-20", "1988-01-21", "1988-01-22", "1988-01-23", "1988-01-24",// "2010-11-23",
          "1989-01", "1989-02", "1989-03", "1989-04", "1989-05", "1989-06",
          "1989-07", "1989-08", "1989-09", "1989-10", "1989-11", "1989-12",
          "1989-03-26", "1989-05-14"
        },
      };

    String[][] expectedValuesNextImme =
      { /* 2011-06-03 */
        { "2011-W23", "2011-W23-WE", "2011-06-03/2011-06-17",
          "2011-07", "2011-Q3", "2012", "202X", "21XX",
          "2012-SP", "2011-SU", "2011-FA", "2011-FA", "2011-WI",
          "2011-06-06", "2011-06-07", "2011-06-08", "2011-06-09", "2011-06-10", "2011-06-04", "2011-06-05",// "2011-06-WE?",
          "2012-01", "2012-02", "2012-03", "2012-04", "2012-05", "2012-06",
          "2011-07", "2011-08", "2011-09", "2011-10", "2011-11", "2011-12",
          "2012-04-08", "2012-05-13"
        },
        /* 2010-11-23 */
        { "2010-W48", "2010-W48-WE", "2010-11-23/2010-12-07",
          "2010-12", "2011-Q1", "2011", "202X", "21XX",
          "2011-SP", "2011-SU", "2011-FA", "2011-FA", "2010-WI",
          "2010-11-29", "2010-11-30", "2010-11-24", "2010-11-25", "2010-11-26", "2010-11-27", "2010-11-28",// "2010-11-23",
          "2011-01", "2011-02", "2011-03", "2011-04", "2011-05", "2011-06",
          "2011-07", "2011-08", "2011-09", "2011-10", "2011-11", "2010-12",
          "2011-04-24", "2011-05-08"
        },
        /* 1988-01-16 */
        { "1988-W03", "1988-W03-WE", "1988-01-16/1988-01-30",
          "1988-02", "1988-Q2", "1989", "199X", "20XX",
          "1988-SP", "1988-SU", "1988-FA", "1988-FA", "1988-WI",               // TODO: Should be 1988-WI or 1989-WI?
          "1988-01-18", "1988-01-19", "1988-01-20", "1988-01-21", "1988-01-22", "1988-01-23", "1988-01-17",// "2010-11-23",
          "1989-01", "1988-02", "1988-03", "1988-04", "1988-05", "1988-06",
          "1988-07", "1988-08", "1988-09", "1988-10", "1988-11", "1988-12",
          "1988-04-03", "1988-05-08"
        },
      };

    // TODO: Add to test
    String[][] expectedValuesPrevImme =
      { /* 2011-06-03 */
        { "2011-W21", "2011-W21-WE", "2011-05-20/2011-06-03",  // TODO: should be W22 or W21?
          "2011-05", "2011-Q1", "2010", "200X", "19XX",        // TODO: should be 2011-Q1 or 2011-Q2
          "2010-SP", "2010-SU", "2010-FA", "2010-FA", "2010-WI",
          "2011-05-30", "2011-05-31", "2011-06-01", "2011-06-02", "2011-05-27", "2011-05-28", "2011-05-29", //"2011-05-WE?",
          "2011-01", "2011-02", "2011-03", "2011-04", "2011-05", "2010-06",
          "2010-07", "2010-08", "2010-09", "2010-10", "2010-11", "2010-12",
          "", ""
        },
        /* 2010-11-23 */
        { "2010-W46", "2010-W46-WE", "2010-11-09/2010-11-23",
          "2010-10", "2010-Q3", "2009", "200X", "19XX",
          "2010-SP", "2010-SU", "2010-FA", "2010-FA", "2009-WI",
          "2010-11-22", "2010-11-16", "2010-11-17", "2010-11-18", "2010-11-19", "2010-11-20", "2010-11-21",// "2010-11-23",
          "2010-01", "2010-02", "2010-03", "2010-04", "2010-05", "2010-06",
          "2010-07", "2010-08", "2010-09", "2010-10", "2009-11", "2009-12",
          "", ""
        },
        /* 1988-01-16 */
        { "1988-W01", "1988-W01-WE", "1988-01-02/1988-01-16,P2W",
          "1987-12", "1987-Q4", "1987", "197X", "18XX",
          "1987-SP", "1987-SU", "1987-FA", "1987-FA", "1987-WI",
          "1988-01-11", "1988-01-12", "1988-01-13", "1988-01-14", "1988-01-15", "1988-01-09", "1988-01-10",// "2010-11-23",
          "1987-01", "1987-02", "1987-03", "1987-04", "1987-05", "1987-06",
          "1987-07", "1987-08", "1987-09", "1987-10", "1987-11", "1987-12",
          "", ""
        },
      };


    String[][][] expectedValuesAll = { expectedValuesPrev, expectedValuesThis, expectedValuesNext, expectedValuesNextImme/*, expectedValuesPrevImme */ };

    for (int i = 0; i < refTimes.length; i++) {
      for (int j = 0; j < refModifiers.length; j++) {
        String refTime = refTimes[i];
        String refMod = refModifiers[j];

        StringBuilder sb = new StringBuilder();
        for (String s:refExprs) {
          sb.append("It happened " + refMod + " " + s + ". ");
        }
        String testText = sb.toString();

        String[] expectedValues = expectedValuesAll[j][i];
        Timex[] expectedTimexes = new Timex[refExprs.length];
        for (int k = 0; k < refExprs.length; k++) {
          String v = expectedValues[k];
          String expr = refMod + " " + refExprs[k];
          if (v.contains("=")) {
            expectedTimexes[k] = Timex.fromXml(
                  "<TIMEX3 tid=\"t" + (k+1) + "\" " + v + " type=\"DATE\">" + expr + "</TIMEX3>");
          } else {
            expectedTimexes[k] = Timex.fromXml(
                  "<TIMEX3 tid=\"t" + (k+1) + "\" value=\"" + v + "\" type=\"DATE\">" + expr + "</TIMEX3>");
          }
        }
        // create document
        Annotation documentWithRefTime = createDocument(testText, refTime);

        // Time annotate
        TimeAnnotator sutime = getTimeAnnotator();
        sutime.annotate(documentWithRefTime);

        int k = 0;
        for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
          Timex expectedTimex = expectedTimexes[k];
          checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
          k++;
        }
        assertEquals(expectedTimexes.length, k);
      }
    }
  }

  public void testResolve3()
  {
    String[] refTimes = {
            "2011-06-03",
            "2010-11-23",
            "1988-01-16"
    };

    String[] refModifiers = { "", "last", "this", "next", "this coming"};
    String[] todExprs = { "at 8 p.m.", "at 8 in the morning", "at 8 in the evening",
                     "1:00 pm", "morning", "noon", "afternoon", "evening", "night", "midnight" };
    String[] expectedTodValues = { "T20:00", "T08:00", "T20:00", "T13:00", "TMO", "T12:00", "TAF", "TEV", "TNI", "T00:00" };

    String[] refExprs = {
            "monday",
            "tuesday",
            "wednesday",
            "thursday",
            "friday",
            "saturday",
            "sunday",
    };

    String[][] expectedValuesPrev =
      { /* 2011-06-03 */
        { "2011-05-23", "2011-05-24", "2011-05-25", "2011-05-26", "2011-05-27", "2011-05-28", "2011-05-29"
        },
        /* 2010-11-23 */
        { "2010-11-15", "2010-11-16", "2010-11-17", "2010-11-18", "2010-11-19", "2010-11-20", "2010-11-21"
        },
        /* 1988-01-16 */
        { "1988-01-04", "1988-01-05", "1988-01-06", "1988-01-07", "1988-01-08", "1988-01-09", "1988-01-10"
        },
      };

    String[][] expectedValuesThis =
      { /* 2011-06-03 */
        { "2011-05-30", "2011-05-31", "2011-06-01", "2011-06-02", "2011-06-03", "2011-06-04", "2011-06-05"
        },
        /* 2010-11-23 */
        { "2010-11-22", "2010-11-23", "2010-11-24", "2010-11-25", "2010-11-26", "2010-11-27", "2010-11-28"
        },
        /* 1988-01-16 */
        { "1988-01-11", "1988-01-12", "1988-01-13", "1988-01-14", "1988-01-15", "1988-01-16", "1988-01-17"
        },
      };

    String[][] expectedValuesNext =
      { /* 2011-06-03 */
        { "2011-06-06", "2011-06-07", "2011-06-08", "2011-06-09", "2011-06-10", "2011-06-11", "2011-06-12"
        },
        /* 2010-11-23 */
        { "2010-11-29", "2010-11-30", "2010-12-01", "2010-12-02", "2010-12-03", "2010-12-04", "2010-12-05"
        },
        /* 1988-01-16 */
        { "1988-01-18", "1988-01-19", "1988-01-20", "1988-01-21", "1988-01-22", "1988-01-23", "1988-01-24"
        },
      };

    String[][] expectedValuesNextImme =
      { /* 2011-06-03 */
        { "2011-06-06", "2011-06-07", "2011-06-08", "2011-06-09", "2011-06-10", "2011-06-04", "2011-06-05"
        },
        /* 2010-11-23 */
        { "2010-11-29", "2010-11-30", "2010-11-24", "2010-11-25", "2010-11-26", "2010-11-27", "2010-11-28"
        },
        /* 1988-01-16 */
        { "1988-01-18", "1988-01-19", "1988-01-20", "1988-01-21", "1988-01-22", "1988-01-23", "1988-01-17"
        },
      };

    String[][][] expectedValuesAll = { expectedValuesThis, expectedValuesPrev, expectedValuesThis, expectedValuesNext, expectedValuesNextImme  };

    for (int i = 0; i < refTimes.length; i++) {
      for (int j = 0; j < refModifiers.length; j++) {
        String refTime = refTimes[i];
        String refMod = refModifiers[j];

        StringBuilder sb = new StringBuilder();
        for (String s:refExprs) {
          for (String tod:todExprs) {
            sb.append("It happened " + refMod + " " + s + " " + tod + ". ");
          }
        }
        String testText = sb.toString();

        String[] expectedValues = expectedValuesAll[j][i];
        Timex[] expectedTimexes = new Timex[refExprs.length * todExprs.length];
        int k = 0;
        for (int k1 = 0; k1 < refExprs.length; k1++) {
          for (int k2 = 0; k2 < todExprs.length; k2++) {
            String v = expectedValues[k1] + expectedTodValues[k2];
            String expr = (refMod.length() > 0)? (refMod + " "):"";
            expr = expr + refExprs[k1] + " " + todExprs[k2];
            if (v.contains("=")) {
              expectedTimexes[k] = Timex.fromXml(
                    "<TIMEX3 tid=\"t" + (k+1) + "\" " + v + " type=\"TIME\">" + expr + "</TIMEX3>");
            } else {
              expectedTimexes[k] = Timex.fromXml(
                    "<TIMEX3 tid=\"t" + (k+1) + "\" value=\"" + v + "\" type=\"TIME\">" + expr + "</TIMEX3>");
            }
            k++;
          }
        }
        // create document
        Annotation documentWithRefTime = createDocument(testText, refTime);

        // Time annotate
        TimeAnnotator sutime = getTimeAnnotator();
        sutime.annotate(documentWithRefTime);

        k = 0;
        for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
          Timex expectedTimex = expectedTimexes[k];
          checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
          k++;
        }
        assertEquals(expectedTimexes.length, k);
      }
    }
  }

  public void testSet() {

    String[] refModifiers = { "every", "every other", "each", "alternate", "every 3rd", "every fourth"};

    String[] refExprs = {
            "week",
            "weekend",
            "fortnight",
            "month",
         //  "quarter",
         //   "season",
            "year",
            "decade",
            "century",
            "millenium",
            "spring",
            "summer",
            "autumn",
            "fall",
            "winter",

            "monday",
            "tuesday",
            "wednesday",
            "thursday",
            "friday",
            "saturday",
            "sunday",

            "january",
            "february",
            "march",
            "april",
            "May",    // hmmm
            "june",
            "july",
            "august",
            "september",
            "october",
            "november",
            "december"
    };

    String[][] expectedValuesEvery =
      {
        { "P1W", "P1W" }, { /*"XXXX-WXX-WE" */ "XXXX-WE" /*"WE"*/, "P1W" },
        { "P2W", "P2W" }, { "P1M", "P1M" },  /*{ "P3M", "P3M" } ,*/
        { "P1Y", "P1Y" }, { "P10Y", "P10Y" },  { "P100Y", "P100Y" }, { "P1000Y", "P1000Y" },
        { "XXXX-SP", "P1Y" }, { "XXXX-SU", "P1Y" }, { "XXXX-FA", "P1Y" },
        { "XXXX-FA", "P1Y" }, { "XXXX-WI", "P1Y" },
        { "XXXX-WXX-1", "P1W" }, { "XXXX-WXX-2", "P1W" }, { "XXXX-WXX-3", "P1W" }, { "XXXX-WXX-4", "P1W" },
        { "XXXX-WXX-5", "P1W" }, { "XXXX-WXX-6", "P1W" }, { "XXXX-WXX-7", "P1W" },
        { "XXXX-01", "P1Y" }, { "XXXX-02", "P1Y" }, { "XXXX-03", "P1Y" }, { "XXXX-04", "P1Y" },
        { "XXXX-05", "P1Y" }, { "XXXX-06", "P1Y" }, { "XXXX-07", "P1Y" }, { "XXXX-08", "P1Y" },
        { "XXXX-09", "P1Y" }, { "XXXX-10", "P1Y" }, { "XXXX-11", "P1Y" }, { "XXXX-12", "P1Y" },
      };

    int[] expectedMultiples = { 1, 2, 1, 2, 3, 4 };

    final Pattern p = Pattern.compile("(\\D+)(\\d+)(\\D+)");
    for (int j = 0; j < refModifiers.length; j++) {
      String refTime = null;
      String refMod = refModifiers[j];

      StringBuilder sb = new StringBuilder();
      for (String s:refExprs) {
        sb.append("It happens " + refMod + " " + s + ". ");
      }
      String testText = sb.toString();

      Timex[] expectedTimexes = new Timex[refExprs.length];
      int m = expectedMultiples[j];
      for (int k = 0; k < refExprs.length; k++) {
        String v = expectedValuesEvery[k][0];
        String g = expectedValuesEvery[k][1];
        Matcher matcher = p.matcher(g);
        if (matcher.matches()) {
          int mg = Integer.parseInt(matcher.group(2));
          mg = mg*m;
          g = matcher.group(1) + mg + matcher.group(3);
        }
        String expr = refMod + ' ' + refExprs[k];
        expectedTimexes[k] = Timex.fromXml(
                  "<TIMEX3 tid=\"t" + (k+1) + "\" value=\"" + v + "\" type=\"SET\" quant=\"" + refMod + "\" periodicity=\"" + g + "\">" + expr + "</TIMEX3>");
      }
      // create document
      Annotation documentWithRefTime = createDocument(testText, refTime);

      // Time annotate
      TimeAnnotator sutime = getTimeAnnotator();
      sutime.annotate(documentWithRefTime);

      int k = 0;
      for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
        Timex expectedTimex = expectedTimexes[k];
        checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
        k++;
      }
      assertEquals(expectedTimexes.length, k);
    }
  }

  public void testSet2() {
    String[] refModifiers = { "", "bi", "bi-", "bi - "/*, "semi", "semi-" */};

    String[] refExprs = {
            "annually",
            "yearly",
            "annual",
            "quarterly",
            "monthly",
            "weekly",
            "daily",
            "nightly",
            "hourly"
    };

    String[][] expectedValuesEvery = {
                    { "P1Y", "P1Y" }, { "P1Y", "P1Y" }, { "P1Y", "P1Y" },
                    { "P3M", "P3M" }, { "P1M", "P1M" }, { "P1W", "P1W" }, { "P1D", "P1D" },
                    { "TNI", "P1D" }, { "PT1H", "PT1H" },
            };

    int[] expectedMultiples = { 1, 2, 2, 2 /*, 1, 1 */ };

    final Pattern p = Pattern.compile("(\\D+)(\\d+)(\\D+)");
    for (int j = 0; j < refModifiers.length; j++) {
      String refTime = null;
      String refMod = refModifiers[j];

      StringBuilder sb = new StringBuilder();
      for (String s:refExprs) {
        sb.append("It happens " + refMod + s + ". ");
      }
      String testText = sb.toString();

      Timex[] expectedTimexes = new Timex[refExprs.length];
      int m = expectedMultiples[j];
      for (int k = 0; k < refExprs.length; k++) {
        String v = expectedValuesEvery[k][0];
        String g = expectedValuesEvery[k][1];
        boolean updateV = v.equals(g);
        Matcher matcher = p.matcher(g);
        if (matcher.matches()) {
          int mg = Integer.parseInt(matcher.group(2));
          mg = mg*m;
          g = matcher.group(1) + mg + matcher.group(3);
        }
        if (updateV) {
          v = g;
        }
        String expr = refMod + refExprs[k];
        expectedTimexes[k] = Timex.fromXml(
                "<TIMEX3 tid=\"t" + (k+1) + "\" value=\"" + v + "\" type=\"SET\" quant=\"" + "EVERY" + "\" freq=\"P1X\" periodicity=\"" + g + "\">" + expr + "</TIMEX3>");
      }
      // create document
      Annotation documentWithRefTime = createDocument(testText, refTime);

      // Time annotate
      TimeAnnotator sutime = getTimeAnnotator();
      sutime.annotate(documentWithRefTime);

      int k = 0;
      for (CoreMap timexAnn: documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
        Timex expectedTimex = expectedTimexes[k];
        checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
        k++;
      }
      assertEquals(expectedTimexes.length, k);
    }
  }

  public void testSeasons() {
    String[] refModifiers = { "", "the "};

    String[] refExprs1 = {
            "summer",
            "winter",
            "autumn",
            "fall",
            "spring"
    };

    String[] refExprs2 = {
            "nineteen ninety five",
            "2005"
    };

    String[] expected1 = { "SU", "WI", "FA", "FA", "SP"};
    String[] expected2 = { "1995", "2005" };

    for (String refModifier : refModifiers) {
      String refTime = null;

      StringBuilder sb = new StringBuilder();
      for (String s : refExprs1) {
        for (String y : refExprs2) {
          sb.append("It happens " + refModifier + s + " " + y + ". ");
          sb.append("It happens " + refModifier + s + " of " + y + ". ");
          sb.append("It happens " + refModifier + y + " " + s + ". ");
        }
      }
      String testText = sb.toString();

      Timex[] expectedTimexes = new Timex[refExprs1.length * refExprs2.length * 3];
      int i = 0;
      for (int k = 0; k < refExprs1.length; k++) {
        for (int m = 0; m < refExprs2.length; m++) {
          String s = expected1[k];
          String y = expected2[m];
          String v = y + "-" + s;
          String expr = refModifier + refExprs1[k] + " " + refExprs2[m];
          expectedTimexes[i++] = Timex.fromXml("<TIMEX3 tid=\"t" + (i) + "\" value=\"" + v + "\" type=\"DATE\">" + expr + "</TIMEX3>");
          expr = refModifier + refExprs1[k] + " of " + refExprs2[m];
          expectedTimexes[i++] = Timex.fromXml("<TIMEX3 tid=\"t" + (i) + "\" value=\"" + v + "\" type=\"DATE\">" + expr + "</TIMEX3>");
          expr = refModifier + refExprs2[m] + " " + refExprs1[k];
          expectedTimexes[i++] = Timex.fromXml("<TIMEX3 tid=\"t" + (i) + "\" value=\"" + v + "\" type=\"DATE\">" + expr + "</TIMEX3>");
        }
      }
      // create document
      Annotation documentWithRefTime = createDocument(testText, refTime);

      // Time annotate
      TimeAnnotator sutime = getTimeAnnotator();
      sutime.annotate(documentWithRefTime);

      i = 0;
      for (CoreMap timexAnn : documentWithRefTime.get(TimeAnnotations.TimexAnnotations.class)) {
        Timex expectedTimex = expectedTimexes[i];
        checkTimex(testText, expectedTimex.text(), expectedTimex, timexAnn);
        i++;
      }
      assertEquals(expectedTimexes.length, i);
    }
  }

  private static void checkTimex(String documentText, String expectedText,
                          Timex expectedTimex, CoreMap timexAnn) {
    String actualText = timexAnn.get(CoreAnnotations.TextAnnotation.class);
    Assert.assertEquals(expectedText, actualText);

    int begin =
      timexAnn.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    int end = timexAnn.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
    Assert.assertEquals(expectedText, documentText.substring(begin, end));

    Timex actualTimex = timexAnn.get(TimeAnnotations.TimexAnnotation.class);
    Assert.assertEquals("Expected \"" + expectedTimex + "\" but got \"" + actualTimex + '"',
                        expectedTimex.toString(), actualTimex.toString());
  }


  protected static Annotation createDocument(String text) {
    Annotation annotation = new Annotation(text);
    pipeline.annotate(annotation);
    return annotation;
  }

  protected static Annotation createDocument(String text, String date) {
    Annotation annotation = new Annotation(text);
    annotation.set(CoreAnnotations.DocDateAnnotation.class, date);
    pipeline.annotate(annotation);
    return annotation;
  }
}
