package edu.stanford.nlp.ie.crf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;


import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.sequences.ExactBestSequenceFinder;
import edu.stanford.nlp.sequences.KBestSequenceFinder;
import edu.stanford.nlp.sequences.ObjectBankWrapper;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.BenchmarkingHelper;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.TestPaths;
import edu.stanford.nlp.util.Triple;


/** Test some of the methods of CRFClassifier.
 *
 *  @author Christopher Manning
 */
public class CRFClassifierITest {

  private static final String nerPath = "edu/stanford/nlp/models/ner/english.all.3class.distsim.crf.ser.gz";

  private static final String caselessPath = "edu/stanford/nlp/models/ner/english.all.3class.caseless.distsim.crf.ser.gz";
  // private static final String caselessPath = String.format("%s/ner/goodClassifiers/english.all.3class.caseless.distsim.crf.ser.gz", TestPaths.testHome()); // now works!
  // private static final String caselessPath = String.format("%s/ner/classifiers-2014-08-31/english.all.3class.caseless.distsim.crf.ser.gz", TestPaths.testHome());

  /* The extra spaces and tab (after fate) are there to test space preservation.
   * Each item of the top level array is an array of 7 Strings:
   * 0) Input text
   * 1) slash tags output (with spaces preserved)
   * 2) inline XML output (with spaces preserved)
   * 3) xml output (without spaces preserved)
   * 4) xml output (with spaces preserved)
   * 5) slash tags output (without spaces preserved)
   * 6) inline XML output (without spaces preserved)
   */
  private static final String[][] testTexts =
          { { "  The  fate\tof  Lehman Brothers, the beleaguered investment bank, hung in the balance on Sunday as Federal Reserve officials and the leaders of major financial institutions continued to gather in emergency meetings trying to complete a plan to rescue the stricken bank.  Several possible plans emerged from the talks, held at the Federal Reserve Bank of New York and led by Timothy R. Geithner, the president of the New York Fed, and Treasury Secretary Henry M. Paulson Jr.  ",
                  "  The/O  fate/O\tof/O  Lehman/ORGANIZATION Brothers/ORGANIZATION,/O the/O beleaguered/O investment/O bank/O,/O hung/O in/O the/O balance/O on/O Sunday/O as/O Federal/ORGANIZATION Reserve/ORGANIZATION officials/O and/O the/O leaders/O of/O major/O financial/O institutions/O continued/O to/O gather/O in/O emergency/O meetings/O trying/O to/O complete/O a/O plan/O to/O rescue/O the/O stricken/O bank/O./O  Several/O possible/O plans/O emerged/O from/O the/O talks/O,/O held/O at/O the/O Federal/ORGANIZATION Reserve/ORGANIZATION Bank/ORGANIZATION of/ORGANIZATION New/ORGANIZATION York/ORGANIZATION and/O led/O by/O Timothy/PERSON R./PERSON Geithner/PERSON,/O the/O president/O of/O the/O New/ORGANIZATION York/ORGANIZATION Fed/ORGANIZATION,/O and/O Treasury/ORGANIZATION Secretary/O Henry/PERSON M./PERSON Paulson/PERSON Jr./PERSON./O  ",
                  "  The  fate\tof  <ORGANIZATION>Lehman Brothers</ORGANIZATION>, the beleaguered investment bank, hung in the balance on Sunday as <ORGANIZATION>Federal Reserve</ORGANIZATION> officials and the leaders of major financial institutions continued to gather in emergency meetings trying to complete a plan to rescue the stricken bank.  Several possible plans emerged from the talks, held at the <ORGANIZATION>Federal Reserve Bank of New York</ORGANIZATION> and led by <PERSON>Timothy R. Geithner</PERSON>, the president of the <ORGANIZATION>New York Fed</ORGANIZATION>, and <ORGANIZATION>Treasury</ORGANIZATION> Secretary <PERSON>Henry M. Paulson Jr</PERSON>.  ",
                  "<wi num=\"0\" entity=\"O\">The</wi>\n" +
                          "<wi num=\"1\" entity=\"O\">fate</wi>\n" +
                          "<wi num=\"2\" entity=\"O\">of</wi>\n" +
                          "<wi num=\"3\" entity=\"ORGANIZATION\">Lehman</wi>\n" +
                          "<wi num=\"4\" entity=\"ORGANIZATION\">Brothers</wi>\n" +
                          "<wi num=\"5\" entity=\"O\">,</wi>\n" +
                          "<wi num=\"6\" entity=\"O\">the</wi>\n" +
                          "<wi num=\"7\" entity=\"O\">beleaguered</wi>\n" +
                          "<wi num=\"8\" entity=\"O\">investment</wi>\n" +
                          "<wi num=\"9\" entity=\"O\">bank</wi>\n" +
                          "<wi num=\"10\" entity=\"O\">,</wi>\n" +
                          "<wi num=\"11\" entity=\"O\">hung</wi>\n" +
                          "<wi num=\"12\" entity=\"O\">in</wi>\n" +
                          "<wi num=\"13\" entity=\"O\">the</wi>\n" +
                          "<wi num=\"14\" entity=\"O\">balance</wi>\n" +
                          "<wi num=\"15\" entity=\"O\">on</wi>\n" +
                          "<wi num=\"16\" entity=\"O\">Sunday</wi>\n" +
                          "<wi num=\"17\" entity=\"O\">as</wi>\n" +
                          "<wi num=\"18\" entity=\"ORGANIZATION\">Federal</wi>\n" +
                          "<wi num=\"19\" entity=\"ORGANIZATION\">Reserve</wi>\n" +
                          "<wi num=\"20\" entity=\"O\">officials</wi>\n" +
                          "<wi num=\"21\" entity=\"O\">and</wi>\n" +
                          "<wi num=\"22\" entity=\"O\">the</wi>\n" +
                          "<wi num=\"23\" entity=\"O\">leaders</wi>\n" +
                          "<wi num=\"24\" entity=\"O\">of</wi>\n" +
                          "<wi num=\"25\" entity=\"O\">major</wi>\n" +
                          "<wi num=\"26\" entity=\"O\">financial</wi>\n" +
                          "<wi num=\"27\" entity=\"O\">institutions</wi>\n" +
                          "<wi num=\"28\" entity=\"O\">continued</wi>\n" +
                          "<wi num=\"29\" entity=\"O\">to</wi>\n" +
                          "<wi num=\"30\" entity=\"O\">gather</wi>\n" +
                          "<wi num=\"31\" entity=\"O\">in</wi>\n" +
                          "<wi num=\"32\" entity=\"O\">emergency</wi>\n" +
                          "<wi num=\"33\" entity=\"O\">meetings</wi>\n" +
                          "<wi num=\"34\" entity=\"O\">trying</wi>\n" +
                          "<wi num=\"35\" entity=\"O\">to</wi>\n" +
                          "<wi num=\"36\" entity=\"O\">complete</wi>\n" +
                          "<wi num=\"37\" entity=\"O\">a</wi>\n" +
                          "<wi num=\"38\" entity=\"O\">plan</wi>\n" +
                          "<wi num=\"39\" entity=\"O\">to</wi>\n" +
                          "<wi num=\"40\" entity=\"O\">rescue</wi>\n" +
                          "<wi num=\"41\" entity=\"O\">the</wi>\n" +
                          "<wi num=\"42\" entity=\"O\">stricken</wi>\n" +
                          "<wi num=\"43\" entity=\"O\">bank</wi>\n" +
                          "<wi num=\"44\" entity=\"O\">.</wi>\n" +
                          "<wi num=\"0\" entity=\"O\">Several</wi>\n" +
                          "<wi num=\"1\" entity=\"O\">possible</wi>\n" +
                          "<wi num=\"2\" entity=\"O\">plans</wi>\n" +
                          "<wi num=\"3\" entity=\"O\">emerged</wi>\n" +
                          "<wi num=\"4\" entity=\"O\">from</wi>\n" +
                          "<wi num=\"5\" entity=\"O\">the</wi>\n" +
                          "<wi num=\"6\" entity=\"O\">talks</wi>\n" +
                          "<wi num=\"7\" entity=\"O\">,</wi>\n" +
                          "<wi num=\"8\" entity=\"O\">held</wi>\n" +
                          "<wi num=\"9\" entity=\"O\">at</wi>\n" +
                          "<wi num=\"10\" entity=\"O\">the</wi>\n" +
                          "<wi num=\"11\" entity=\"ORGANIZATION\">Federal</wi>\n" +
                          "<wi num=\"12\" entity=\"ORGANIZATION\">Reserve</wi>\n" +
                          "<wi num=\"13\" entity=\"ORGANIZATION\">Bank</wi>\n" +
                          "<wi num=\"14\" entity=\"ORGANIZATION\">of</wi>\n" +
                          "<wi num=\"15\" entity=\"ORGANIZATION\">New</wi>\n" +
                          "<wi num=\"16\" entity=\"ORGANIZATION\">York</wi>\n" +
                          "<wi num=\"17\" entity=\"O\">and</wi>\n" +
                          "<wi num=\"18\" entity=\"O\">led</wi>\n" +
                          "<wi num=\"19\" entity=\"O\">by</wi>\n" +
                          "<wi num=\"20\" entity=\"PERSON\">Timothy</wi>\n" +
                          "<wi num=\"21\" entity=\"PERSON\">R.</wi>\n" +
                          "<wi num=\"22\" entity=\"PERSON\">Geithner</wi>\n" +
                          "<wi num=\"23\" entity=\"O\">,</wi>\n" +
                          "<wi num=\"24\" entity=\"O\">the</wi>\n" +
                          "<wi num=\"25\" entity=\"O\">president</wi>\n" +
                          "<wi num=\"26\" entity=\"O\">of</wi>\n" +
                          "<wi num=\"27\" entity=\"O\">the</wi>\n" +
                          "<wi num=\"28\" entity=\"ORGANIZATION\">New</wi>\n" +
                          "<wi num=\"29\" entity=\"ORGANIZATION\">York</wi>\n" +
                          "<wi num=\"30\" entity=\"ORGANIZATION\">Fed</wi>\n" +
                          "<wi num=\"31\" entity=\"O\">,</wi>\n" +
                          "<wi num=\"32\" entity=\"O\">and</wi>\n" +
                          "<wi num=\"33\" entity=\"ORGANIZATION\">Treasury</wi>\n" +
                          "<wi num=\"34\" entity=\"O\">Secretary</wi>\n" +
                          "<wi num=\"35\" entity=\"PERSON\">Henry</wi>\n" +
                          "<wi num=\"36\" entity=\"PERSON\">M.</wi>\n" +
                          "<wi num=\"37\" entity=\"PERSON\">Paulson</wi>\n" +
                          "<wi num=\"38\" entity=\"PERSON\">Jr.</wi>\n" +
                          "<wi num=\"39\" entity=\"O\">.</wi>\n",
                  "  <wi num=\"0\" entity=\"O\">The</wi>  " +
                          "<wi num=\"1\" entity=\"O\">fate</wi>\t" +
                          "<wi num=\"2\" entity=\"O\">of</wi>  " +
                          "<wi num=\"3\" entity=\"ORGANIZATION\">Lehman</wi> " +
                          "<wi num=\"4\" entity=\"ORGANIZATION\">Brothers</wi>" +
                          "<wi num=\"5\" entity=\"O\">,</wi> " +
                          "<wi num=\"6\" entity=\"O\">the</wi> " +
                          "<wi num=\"7\" entity=\"O\">beleaguered</wi> " +
                          "<wi num=\"8\" entity=\"O\">investment</wi> " +
                          "<wi num=\"9\" entity=\"O\">bank</wi>" +
                          "<wi num=\"10\" entity=\"O\">,</wi> " +
                          "<wi num=\"11\" entity=\"O\">hung</wi> " +
                          "<wi num=\"12\" entity=\"O\">in</wi> " +
                          "<wi num=\"13\" entity=\"O\">the</wi> " +
                          "<wi num=\"14\" entity=\"O\">balance</wi> " +
                          "<wi num=\"15\" entity=\"O\">on</wi> " +
                          "<wi num=\"16\" entity=\"O\">Sunday</wi> " +
                          "<wi num=\"17\" entity=\"O\">as</wi> " +
                          "<wi num=\"18\" entity=\"ORGANIZATION\">Federal</wi> " +
                          "<wi num=\"19\" entity=\"ORGANIZATION\">Reserve</wi> " +
                          "<wi num=\"20\" entity=\"O\">officials</wi> " +
                          "<wi num=\"21\" entity=\"O\">and</wi> " +
                          "<wi num=\"22\" entity=\"O\">the</wi> " +
                          "<wi num=\"23\" entity=\"O\">leaders</wi> " +
                          "<wi num=\"24\" entity=\"O\">of</wi> " +
                          "<wi num=\"25\" entity=\"O\">major</wi> " +
                          "<wi num=\"26\" entity=\"O\">financial</wi> " +
                          "<wi num=\"27\" entity=\"O\">institutions</wi> " +
                          "<wi num=\"28\" entity=\"O\">continued</wi> " +
                          "<wi num=\"29\" entity=\"O\">to</wi> " +
                          "<wi num=\"30\" entity=\"O\">gather</wi> " +
                          "<wi num=\"31\" entity=\"O\">in</wi> " +
                          "<wi num=\"32\" entity=\"O\">emergency</wi> " +
                          "<wi num=\"33\" entity=\"O\">meetings</wi> " +
                          "<wi num=\"34\" entity=\"O\">trying</wi> " +
                          "<wi num=\"35\" entity=\"O\">to</wi> " +
                          "<wi num=\"36\" entity=\"O\">complete</wi> " +
                          "<wi num=\"37\" entity=\"O\">a</wi> " +
                          "<wi num=\"38\" entity=\"O\">plan</wi> " +
                          "<wi num=\"39\" entity=\"O\">to</wi> " +
                          "<wi num=\"40\" entity=\"O\">rescue</wi> " +
                          "<wi num=\"41\" entity=\"O\">the</wi> " +
                          "<wi num=\"42\" entity=\"O\">stricken</wi> " +
                          "<wi num=\"43\" entity=\"O\">bank</wi>" +
                          "<wi num=\"44\" entity=\"O\">.</wi>  " +
                          "<wi num=\"0\" entity=\"O\">Several</wi> " +
                          "<wi num=\"1\" entity=\"O\">possible</wi> " +
                          "<wi num=\"2\" entity=\"O\">plans</wi> " +
                          "<wi num=\"3\" entity=\"O\">emerged</wi> " +
                          "<wi num=\"4\" entity=\"O\">from</wi> " +
                          "<wi num=\"5\" entity=\"O\">the</wi> " +
                          "<wi num=\"6\" entity=\"O\">talks</wi>" +
                          "<wi num=\"7\" entity=\"O\">,</wi> " +
                          "<wi num=\"8\" entity=\"O\">held</wi> " +
                          "<wi num=\"9\" entity=\"O\">at</wi> " +
                          "<wi num=\"10\" entity=\"O\">the</wi> " +
                          "<wi num=\"11\" entity=\"ORGANIZATION\">Federal</wi> " +
                          "<wi num=\"12\" entity=\"ORGANIZATION\">Reserve</wi> " +
                          "<wi num=\"13\" entity=\"ORGANIZATION\">Bank</wi> " +
                          "<wi num=\"14\" entity=\"ORGANIZATION\">of</wi> " +
                          "<wi num=\"15\" entity=\"ORGANIZATION\">New</wi> " +
                          "<wi num=\"16\" entity=\"ORGANIZATION\">York</wi> " +
                          "<wi num=\"17\" entity=\"O\">and</wi> " +
                          "<wi num=\"18\" entity=\"O\">led</wi> " +
                          "<wi num=\"19\" entity=\"O\">by</wi> " +
                          "<wi num=\"20\" entity=\"PERSON\">Timothy</wi> " +
                          "<wi num=\"21\" entity=\"PERSON\">R.</wi> " +
                          "<wi num=\"22\" entity=\"PERSON\">Geithner</wi>" +
                          "<wi num=\"23\" entity=\"O\">,</wi> " +
                          "<wi num=\"24\" entity=\"O\">the</wi> " +
                          "<wi num=\"25\" entity=\"O\">president</wi> " +
                          "<wi num=\"26\" entity=\"O\">of</wi> " +
                          "<wi num=\"27\" entity=\"O\">the</wi> " +
                          "<wi num=\"28\" entity=\"ORGANIZATION\">New</wi> " +
                          "<wi num=\"29\" entity=\"ORGANIZATION\">York</wi> " +
                          "<wi num=\"30\" entity=\"ORGANIZATION\">Fed</wi>" +
                          "<wi num=\"31\" entity=\"O\">,</wi> " +
                          "<wi num=\"32\" entity=\"O\">and</wi> " +
                          "<wi num=\"33\" entity=\"ORGANIZATION\">Treasury</wi> " +
                          "<wi num=\"34\" entity=\"O\">Secretary</wi> " +
                          "<wi num=\"35\" entity=\"PERSON\">Henry</wi> " +
                          "<wi num=\"36\" entity=\"PERSON\">M.</wi> " +
                          "<wi num=\"37\" entity=\"PERSON\">Paulson</wi> " +
                          "<wi num=\"38\" entity=\"PERSON\">Jr.</wi>" +
                          "<wi num=\"39\" entity=\"O\">.</wi>  ",
                  "The/O fate/O of/O Lehman/ORGANIZATION Brothers/ORGANIZATION ,/O the/O beleaguered/O investment/O bank/O ,/O hung/O in/O the/O balance/O on/O Sunday/O as/O Federal/ORGANIZATION Reserve/ORGANIZATION officials/O and/O the/O leaders/O of/O major/O financial/O institutions/O continued/O to/O gather/O in/O emergency/O meetings/O trying/O to/O complete/O a/O plan/O to/O rescue/O the/O stricken/O bank/O ./O \nSeveral/O possible/O plans/O emerged/O from/O the/O talks/O ,/O held/O at/O the/O Federal/ORGANIZATION Reserve/ORGANIZATION Bank/ORGANIZATION of/ORGANIZATION New/ORGANIZATION York/ORGANIZATION and/O led/O by/O Timothy/PERSON R./PERSON Geithner/PERSON ,/O the/O president/O of/O the/O New/ORGANIZATION York/ORGANIZATION Fed/ORGANIZATION ,/O and/O Treasury/ORGANIZATION Secretary/O Henry/PERSON M./PERSON Paulson/PERSON Jr./PERSON ./O \n",
                  "The fate of <ORGANIZATION>Lehman Brothers</ORGANIZATION> , the beleaguered investment bank , hung in the balance on Sunday as <ORGANIZATION>Federal Reserve</ORGANIZATION> officials and the leaders of major financial institutions continued to gather in emergency meetings trying to complete a plan to rescue the stricken bank . \nSeveral possible plans emerged from the talks , held at the <ORGANIZATION>Federal Reserve Bank of New York</ORGANIZATION> and led by <PERSON>Timothy R. Geithner</PERSON> , the president of the <ORGANIZATION>New York Fed</ORGANIZATION> , and <ORGANIZATION>Treasury</ORGANIZATION> Secretary <PERSON>Henry M. Paulson Jr</PERSON> . \n",
          },
                  { "London",
                          "London/LOCATION",
                          "<LOCATION>London</LOCATION>",
                          "<wi num=\"0\" entity=\"LOCATION\">London</wi>\n",
                          "<wi num=\"0\" entity=\"LOCATION\">London</wi>",
                          "London/LOCATION \n",
                          "<LOCATION>London</LOCATION> \n",
                  },
                  { "It's",
                          "It/O's/O",
                          "It's",
                          "<wi num=\"0\" entity=\"O\">It</wi>\n" +
                                  "<wi num=\"1\" entity=\"O\">&apos;s</wi>\n",
                          "<wi num=\"0\" entity=\"O\">It</wi>" +
                                  "<wi num=\"1\" entity=\"O\">&apos;s</wi>",
                          "It/O 's/O \n",
                          "It 's \n",
                  },
                  { "  \"anaesthetic  Smith is\"  ",
                          "  \"/Oanaesthetic/O  Smith/PERSON is/O\"/O  ",
                          "  \"anaesthetic  <PERSON>Smith</PERSON> is\"  ",
                          "<wi num=\"0\" entity=\"O\">&quot;</wi>\n" +
                                  "<wi num=\"1\" entity=\"O\">anaesthetic</wi>\n" +
                                  "<wi num=\"2\" entity=\"PERSON\">Smith</wi>\n" +
                                  "<wi num=\"3\" entity=\"O\">is</wi>\n" +
                                  "<wi num=\"4\" entity=\"O\">&quot;</wi>\n",
                          "  <wi num=\"0\" entity=\"O\">&quot;</wi>" +
                                  "<wi num=\"1\" entity=\"O\">anaesthetic</wi>  " +
                                  "<wi num=\"2\" entity=\"PERSON\">Smith</wi> " +
                                  "<wi num=\"3\" entity=\"O\">is</wi>" +
                                  "<wi num=\"4\" entity=\"O\">&quot;</wi>  ",
                          "\"/O anaesthetic/O Smith/PERSON is/O \"/O \n",
                          "\" anaesthetic <PERSON>Smith</PERSON> is \" \n",

                  },
          };

  /* --- old test, doesn't work any more
  private static final String[][] caselessTests = {
          { "AISLINN JEWEL Y. CAPAROSO AND REV. CARMELO B. CAPAROSS ARE UPPERCASE NAMES.",
            "AISLINN/PERSON JEWEL/PERSON Y./PERSON CAPAROSO/PERSON AND/O REV./O CARMELO/PERSON B./PERSON CAPAROSS/PERSON ARE/O UPPERCASE/O NAMES/O ./O \n" },
          { "Aislinn Jewel Y. Caparoso and Rev. Carmelo B. Capaross are names.",
            "Aislinn/PERSON Jewel/PERSON Y./PERSON Caparoso/PERSON and/O Rev./O Carmelo/PERSON B./PERSON Capaross/PERSON are/O names/O ./O \n" },
          { "aislinn jewel y. caparoso and rev. carmelo b. capaross are lowercase names.",
            "aislinn/PERSON jewel/PERSON y./PERSON caparoso/PERSON and/O rev./O carmelo/PERSON b./PERSON capaross/PERSON are/O lowercase/O names/O ./O \n" },
  };
  */

  private static final String[][] caselessTests = {
    { "ABC'S GILLIAN FINDLAY REPORTS TONIGHT FROM PALESTINIAN GAZA.",
      "ABC/ORGANIZATION 'S/O GILLIAN/PERSON FINDLAY/PERSON REPORTS/O TONIGHT/O FROM/O PALESTINIAN/O GAZA/LOCATION ./O \n" },
    { "ABC's Gillian Findlay Reports Tonight from Palestinian Gaza.",
      "ABC/ORGANIZATION 's/O Gillian/PERSON Findlay/PERSON Reports/O Tonight/O from/O Palestinian/O Gaza/LOCATION ./O \n" },
    { "abc's gillian findlay reports tonight from palestinian gaza.",
      "abc/ORGANIZATION 's/O gillian/PERSON findlay/PERSON reports/O tonight/O from/O palestinian/O gaza/LOCATION ./O \n" },
  };

  /** Each of these array entries corresponds to one of the inputs in testTexts,
   *  and gives the entity output as entity type and character offset triples.
   */
  @SuppressWarnings({"unchecked"})
  private static final Triple[][] testTrip = {
          { new Triple("ORGANIZATION",16,31),
                  new Triple("ORGANIZATION",99,114),
                  new Triple("ORGANIZATION",330,362),
                  new Triple("PERSON",374,393),
                  new Triple("ORGANIZATION",416,428),
                  new Triple("ORGANIZATION",434,442),
                  new Triple("PERSON",453,472),
          },
          { new Triple("LOCATION", 0, 6)
          },
          {
          },
          { new Triple("PERSON", 16, 21)
          },
  };

  private static final int[][] offsets = { { 2, 3}, { 3, 14 } , { 16, 21}, {22, 24}, {24, 25} };

  /** I made this all one test or else you get problems in memory use if the
   *  JUnit stuff tries to run tests in parallel....
   */
  @Test
  public void testCRF() {
    CRFClassifier<CoreLabel> crf = CRFClassifier.getClassifierNoExceptions(
        System.getProperty("ner.model", nerPath));
    runCRFTest(crf);

    crf = CRFClassifier.getDefaultClassifier();
    runCRFTest(crf);

    final boolean isStoredAnswer = Boolean.parseBoolean(System.getProperty("ner.useStoredAnswer", "false"));
    String txt1 = "Jenny Finkel works for Mixpanel in San Francisco .";
    if (isStoredAnswer) {
      crf = CRFClassifier.getClassifierNoExceptions(nerPath2);
    }
    runKBestTest(crf, txt1, isStoredAnswer);
    runZeroOrder(crf, txt1);

    /* --- Test caseless NER models --- */

    CRFClassifier<CoreLabel> crfCaseless = CRFClassifier.getClassifierNoExceptions(
        System.getProperty("ner.caseless.model", caselessPath));

    try {
      Triple<Double, Double, Double> prf = crfCaseless.classifyAndWriteAnswers(String.format("%s/ner/column_data/ritter.3class.test", TestPaths.testHome()), true);
      Counter<String> results = new ClassicCounter<>();
      results.setCount("NER F1", prf.third());
      Counter<String> lowResults = new ClassicCounter<>();
      lowResults.setCount("NER F1", 53.0);
      Counter<String> highResults = new ClassicCounter<>();
      highResults.setCount("NER F1", 54.36);
      BenchmarkingHelper.benchmarkResults(results, lowResults, highResults, null);
    } catch (IOException ioe) {
      Assert.fail("IOError on CRF test file");
    }

    runSimpleCRFTest(crfCaseless, caselessTests);
  }


  private static void runSimpleCRFTest(CRFClassifier<CoreLabel> crf, String[][] testTexts) {
    for (String[] testText : testTexts) {
      Assert.assertEquals("Wrong array size in test", 2, testText.length);

      String out = crf.classifyToString(testText[0], "slashTags", false).replaceAll("\r", "");
      // System.out.println("Gold:  |" + testText[5] + "|");
      // System.out.println("Guess: |" + out + "|");
      Assert.assertEquals("CRF buggy on classifyToString(slashTags, false)", testText[1], out);

    }
  }


  private static void runCRFTest(CRFClassifier<CoreLabel> crf) {
    for (int i = 0; i < testTexts.length; i++) {
      String[] testText = testTexts[i];

      Assert.assertEquals(i + ": Wrong array size in test", 7, testText.length);
      // System.err.println("length of string is " + testText[0].length());
      String out;

      out = crf.classifyToString(testText[0]);
      Assert.assertEquals(i + ": CRF buggy on classifyToString", testText[1], out);

      out = crf.classifyWithInlineXML(testText[0]);
      Assert.assertEquals(i + ": CRF buggy on classifyWithInlineXML", testText[2], out);

      out = crf.classifyToString(testText[0], "xml", false).replaceAll("\r", "");
      Assert.assertEquals(i + ": CRF buggy on classifyToString(xml, false)", testText[3], out);

      out = crf.classifyToString(testText[0], "xml", true);
      Assert.assertEquals(i + ": CRF buggy on classifyToString(xml, true)", testText[4], out);

      out = crf.classifyToString(testText[0], "slashTags", false).replaceAll("\r", "");
      // System.out.println("Gold:  |" + testText[5] + "|");
      // System.out.println("Guess: |" + out + "|");
      Assert.assertEquals(i + ": CRF buggy on classifyToString(slashTags, false)", testText[5], out);

      out = crf.classifyToString(testText[0], "inlineXML", false).replaceAll("\r", "");
      Assert.assertEquals(i + ": CRF buggy on classifyToString(inlineXML, false)", testText[6], out);

      List<Triple<String,Integer,Integer>> trip = crf.classifyToCharacterOffsets(testText[0]);
      // I couldn't work out how to avoid a type warning in the next line, sigh [cdm 2009]
      Assert.assertEquals(i + ": CRF buggy on classifyToCharacterOffsets", Arrays.asList(testTrip[i]), trip);

      if (i == 0) {
        // cdm 2013: I forget exactly what this was but something about the reduplicated period at the end of Jr.?
        Triple<String,Integer,Integer> x = trip.get(trip.size() - 1);
        Assert.assertEquals("CRF buggy on classifyToCharacterOffsets abbreviation period",
                'r', testText[0].charAt(x.third() - 1));
      }

      if (i == 3) {
        // check that tokens have okay offsets
        List<List<CoreLabel>> doc = crf.classify(testText[0]);
        Assert.assertEquals("Wrong number of sentences", 1, doc.size());
        List<CoreLabel> tokens = doc.get(0);
        Assert.assertEquals("Wrong number of tokens", offsets.length, tokens.size());

        for (int j = 0, sz = tokens.size(); j < sz; j++) {
          CoreLabel token = tokens.get(j);
          Assert.assertEquals("Wrong begin offset", offsets[j][0], (int) token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
          Assert.assertEquals("Wrong end offset", offsets[j][1], (int) token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
        }
      }
    }
  }

  /** adapt changes from {@code Counter<int[]>} to an ordered {@code List<Pair<CRFLabel, Double>>} to make comparisons
   *  easier for the asserts.
   */
  private static List<Pair<CRFLabel, Double>> adapt(Counter<int[]> in) {
    List<Pair<int[], Double>> mid = Counters.toSortedListWithCounts(in);
    List<Pair<CRFLabel, Double>> ret = new ArrayList<>();
    for (Pair<int[], Double> pair : mid) {
      ret.add(new Pair<>(new CRFLabel(pair.first()), pair.second()));
    }
    return ret;
  }

  /** adapt2 changes from {@code Pair<List<CoreLabel>, Double>} to {@code Pair<List<String>, Double>} to make printout
   *  better.
   */
  private static List<Pair<List<String>, Double>> adapt2(List<Pair<List<CoreLabel>, Double>> in) {
    List<Pair<List<String>, Double>> ret = new ArrayList<>();
    for (Pair<List<CoreLabel>, Double> pair : in) {
      List<String> strs = new ArrayList<>();
      for (CoreLabel c : pair.first()) {
        String label = c.getString(CoreAnnotations.AnswerAnnotation.class);
        int max = Math.min(3, label.length());
        strs.add(label.substring(0, max));
      }
      ret.add(new Pair<>(strs, pair.second()));
    }
    return ret;
  }

  private static final String nerPath2 = "eng-conll-es=iobes-2015-01-02.ser.gz"; // gave the answers below

  private static final String[] iobesAnswers = {
          "[B-P, E-P, O, O, S-L, O, B-L, E-L, O]",
          "[B-P, E-P, O, O, S-P, O, B-L, E-L, O]",
          "[B-P, E-P, O, O, S-O, O, B-L, E-L, O]",
          "[B-P, E-P, O, O, O, O, B-L, E-L, O]",
          "[B-P, E-P, O, O, S-M, O, B-L, E-L, O]",
          "[O, O, O, O, S-L, O, B-L, E-L, O]",
          "[B-P, E-P, O, O, B-M, E-M, B-L, E-L, O]",
          "[B-P, E-P, O, O, B-L, I-L, I-L, E-L, O]",
          "[B-M, E-M, O, O, S-L, O, B-L, E-L, O]",
          "[B-O, E-O, O, O, S-L, O, B-L, E-L, O]",
          "[O, S-L, O, O, S-L, O, B-L, E-L, O]",
          "[O, S-P, O, O, S-L, O, B-L, E-L, O]",
  };

  private static final double[] scores = {
          -0.08155105576368271, -3.3992014063749423, -3.463640530637022, -4.466513037276215, -6.956538893107236,
          -8.397637157287733, -8.486597062990043, -8.590586463469464, -8.646039689125871, -9.435909605524955,
          -9.490079642343062, -9.585996407133365,
  };


  private static void runKBestTest(CRFClassifier<CoreLabel> crf, String str, boolean isStoredAnswer) {
    final int K_BEST = 12;
    String[] txt = str.split(" ");
    List<CoreLabel> input = SentenceUtils.toCoreLabelList(txt);

    // do the ugliness that the CRFClassifier routines do to augment the input
    ObjectBankWrapper<CoreLabel> obw = new ObjectBankWrapper<>(crf.flags, null, crf.getKnownLCWords());
    List<CoreLabel> input2 = obw.processDocument(input);

    SequenceModel sequenceModel = crf.getSequenceModel(input2);

    List<Pair<CRFLabel, Double>> kBestSequencesLast = null;
    for (int k = 1; k <= K_BEST; k++) {
      Counter<int[]> kBest = new KBestSequenceFinder().kBestSequences(sequenceModel, k);
      List<Pair<CRFLabel, Double>> kBestSequences = adapt(kBest);
      Assert.assertEquals(k, kBestSequences.size());
      // System.out.printf("k=%2d %s%n", k, kBestSequences);
      if (kBestSequencesLast != null) {
        Assert.assertEquals("k=" + k, kBestSequencesLast, kBestSequences.subList(0, k - 1)); // The rest of the list is the same
        Assert.assertTrue(kBestSequences.get(k - 1).second() <= kBestSequences.get(k - 2).second()); // New item is lower score
        for (int m = 0; m < (k - 1); m++) {
          Assert.assertNotEquals(kBestSequences.get(k - 1).first(), kBestSequences.get(m).first()); // New item is different
        }
      } else {
        int[] bestSequence = new ExactBestSequenceFinder().bestSequence(sequenceModel);
        int[] best1 = new ArrayList<>(kBest.keySet()).get(0);
        Assert.assertArrayEquals(bestSequence, best1);
      }
      kBestSequencesLast = kBestSequences;
    }

    List<Pair<List<String>, Double>> lastAnswer = null;
    for (int k = 1; k <= K_BEST; k++) {
      Counter<List<CoreLabel>> out = crf.classifyKBest(input, CoreAnnotations.AnswerAnnotation.class, k);
      Assert.assertEquals(k, out.size());
      List<Pair<List<CoreLabel>, Double>> beam = Counters.toSortedListWithCounts(out);
      List<Pair<List<String>, Double>> beam2 = adapt2(beam);
      // System.out.printf("k=%2d %s%n", k, beam2);
      if (isStoredAnswer) { // done for a particular sequence model at one point
        Assert.assertEquals(beam2.get(k - 1).first().toString(), iobesAnswers[k - 1]);
        Assert.assertEquals(beam2.get(k - 1).second(), scores[k - 1], 1e-8);
      }
      if (lastAnswer != null) {
        Assert.assertEquals("k=" + k, lastAnswer, beam2.subList(0, k - 1)); // The rest of the list is the same
        Assert.assertTrue(beam2.get(k - 1).second() <= beam2.get(k - 2).second()); // New item is lower score
        for (int m = 0; m < (k - 1); m++) {
          Assert.assertNotEquals(beam2.get(k - 1).first(), beam2.get(m).first()); // New item is different
        }
      } else {
        List<CoreLabel> best = crf.classify(input);
        for (CoreLabel bestToken : best) {
          bestToken.remove(CoreAnnotations.AnswerProbAnnotation.class);
        }
        Assert.assertEquals(best, beam.get(0).first());
      }
      lastAnswer = beam2;
    }
  }

  private static void runZeroOrder(CRFClassifier<CoreLabel> crf, String str) {
    String[] txt = str.split(" ");
    List<CoreLabel> input = SentenceUtils.toCoreLabelList(txt);

    // do the ugliness that the CRFClassifier routines do to augment the input
    ObjectBankWrapper<CoreLabel> obw = new ObjectBankWrapper<>(crf.flags, null, crf.getKnownLCWords());
    List<CoreLabel> input2 = obw.processDocument(input);

    List<Counter<String>> probs = crf.zeroOrderProbabilities(input2);
    Iterator<Counter<String>> iter = probs.iterator();
    for (CoreLabel cl : input2) {
      System.err.println(cl.word() + ": " + iter.next());
    }
  }

}
