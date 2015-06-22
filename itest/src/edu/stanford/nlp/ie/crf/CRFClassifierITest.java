package edu.stanford.nlp.ie.crf;

import java.util.*;

import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Triple;


/** Test some of the methods of CRFClassifier.
 *
 *  @author Christopher Manning
 */
public class CRFClassifierITest extends TestCase {

  private static final String nerPath = "/u/nlp/data/ner/goodClassifiers/english.all.3class.distsim.crf.ser.gz";

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
                          "  ``/Oanaesthetic/O  Smith/PERSON is/O''/O  ",
                          "  \"anaesthetic  <PERSON>Smith</PERSON> is\"  ",
                          "<wi num=\"0\" entity=\"O\">``</wi>\n" +
                                  "<wi num=\"1\" entity=\"O\">anaesthetic</wi>\n" +
                                  "<wi num=\"2\" entity=\"PERSON\">Smith</wi>\n" +
                                  "<wi num=\"3\" entity=\"O\">is</wi>\n" +
                                  "<wi num=\"4\" entity=\"O\">&apos;&apos;</wi>\n",
                          "  <wi num=\"0\" entity=\"O\">``</wi>" +
                                  "<wi num=\"1\" entity=\"O\">anaesthetic</wi>  " +
                                  "<wi num=\"2\" entity=\"PERSON\">Smith</wi> " +
                                  "<wi num=\"3\" entity=\"O\">is</wi>" +
                                  "<wi num=\"4\" entity=\"O\">&apos;&apos;</wi>  ",
                          "``/O anaesthetic/O Smith/PERSON is/O ''/O \n",
                          "\" anaesthetic <PERSON>Smith</PERSON> is \" \n",

                  },
          };

  /* Each of these array entries corresponds to one of the inputs in testTexts,
   * and gives the entity output as entity type and character offset triples.
   */
  @SuppressWarnings({"unchecked"})
  private static final Triple[][] testTrip =
          {
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
  public void testCRF() {
    CRFClassifier<CoreLabel> crf = CRFClassifier.getClassifierNoExceptions(
        System.getProperty("ner.model", nerPath));
    runCRFTest(crf);

    crf = CRFClassifier.getDefaultClassifier();
    runCRFTest(crf);
  }


  @SuppressWarnings({"AssertEqualsBetweenInconvertibleTypes"})
  public static void runCRFTest(CRFClassifier<CoreLabel> crf) {
    for (int i = 0; i < testTexts.length; i++) {
      String[] testText = testTexts[i];

      assertEquals("Wrong array size in test", 7, testText.length);
      // System.err.println("length of string is " + testText[0].length());
      String out;

      out = crf.classifyToString(testText[0]);
      assertEquals("CRF buggy on classifyToString", testText[1], out);

      out = crf.classifyWithInlineXML(testText[0]);
      assertEquals("CRF buggy on classifyWithInlineXML", testText[2], out);

      out = crf.classifyToString(testText[0], "xml", false).replaceAll("\r", "");
      assertEquals("CRF buggy on classifyToString(xml, false)", testText[3], out);

      out = crf.classifyToString(testText[0], "xml", true);
      assertEquals("CRF buggy on classifyToString(xml, true)", testText[4], out);

      out = crf.classifyToString(testText[0], "slashTags", false).replaceAll("\r", "");
      // System.out.println("Gold:  |" + testText[5] + "|");
      // System.out.println("Guess: |" + out + "|");
      assertEquals("CRF buggy on classifyToString(slashTags, false)", testText[5], out);

      out = crf.classifyToString(testText[0], "inlineXML", false).replaceAll("\r", "");
      assertEquals("CRF buggy on classifyToString(inlineXML, false)", testText[6], out);

      List<Triple<String,Integer,Integer>> trip = crf.classifyToCharacterOffsets(testText[0]);
      // I couldn't work out how to avoid a type warning in the next line, sigh [cdm 2009]
      assertEquals("CRF buggy on classifyToCharacterOffsets", Arrays.asList(testTrip[i]), trip);

      if (i == 0) {
        // cdm 2013: I forget exactly what this was but something about the reduplicated period at the end of Jr.?
        Triple<String,Integer,Integer> x = trip.get(trip.size() - 1);
        assertEquals("CRF buggy on classifyToCharacterOffsets abbreviation period",
                'r', testText[0].charAt(x.third() - 1));
      }

      if (i == 3) {
        // check that tokens have okay offsets
        List<List<CoreLabel>> doc = crf.classify(testText[0]);
        assertEquals("Wrong number of sentences", 1, doc.size());
        List<CoreLabel> tokens = doc.get(0);
        assertEquals("Wrong number of tokens", offsets.length, tokens.size());

        for (int j = 0, sz = tokens.size(); j < sz; j++) {
          CoreLabel token = tokens.get(j);
          assertEquals("Wrong begin offset", offsets[j][0], (int) token.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
          assertEquals("Wrong end offset", offsets[j][1], (int) token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
        }
      }
    }
  }

}
