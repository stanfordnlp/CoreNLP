//MaxentTaggerITest -- StanfordMaxEnt, A Maximum Entropy Toolkit
//Copyright (c) 2002-2010 Leland Stanford Junior University


//This program is free software; you can redistribute it and/or
//modify it under the terms of the GNU General Public License
//as published by the Free Software Foundation; either version 2
//of the License, or (at your option) any later version.

//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.

//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

//For more information, bug reports, fixes, contact:
//Christopher Manning
//Dept of Computer Science, Gates 1A
//Stanford CA 94305-9010
//USA
//Support/Questions: java-nlp-user@lists.stanford.edu
//Licensing: java-nlp-support@lists.stanford.edu
//http://www-nlp.stanford.edu/software/tagger.shtml



// Author: John Bauer
// The purpose of this itest is to make sure that the standard tagger
// tags things in the expected manner.
// TODO: add more test cases

package edu.stanford.nlp.tagger.maxent;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Word;

import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.WhitespaceTokenizer;
import edu.stanford.nlp.sequences.PlainTextDocumentReaderAndWriter.OutputStyle;

public class MaxentTaggerITest extends TestCase {

  private static MaxentTagger tagger = null;

  @Override
  public void setUp() throws Exception {
    synchronized(MaxentTaggerITest.class) {
      if (tagger == null) {
        tagger = new MaxentTagger(MaxentTagger.DEFAULT_JAR_PATH);
      }
    }
  }

  public void testChooseTokenizer()
    throws Exception {
    TokenizerFactory<? extends HasWord> tokenizer;
    tokenizer = MaxentTagger.chooseTokenizerFactory(false, "", "", false);
    assertTrue(tokenizer instanceof WhitespaceTokenizer.WhitespaceTokenizerFactory);

    tokenizer = MaxentTagger.chooseTokenizerFactory(true, "", "", false);
    assertTrue(tokenizer instanceof PTBTokenizer.PTBTokenizerFactory);
    //System.out.println(tokenizer.getClass());

    tokenizer = MaxentTagger.chooseTokenizerFactory(true, "edu.stanford.nlp.process.PTBTokenizer$PTBTokenizerFactory", "", false);
    assertTrue(tokenizer instanceof PTBTokenizer.PTBTokenizerFactory);
    //System.out.println(tokenizer.getClass());
  }

  public void testTokenizeTest() {
    String text = "I think I'll go to Boston.  I think I'm starting over.  I think I'll start a new life where no one knows my name.";
    String []expectedResults = {"[I, think, I, 'll, go, to, Boston, .]",
                                "[I, think, I, 'm, starting, over, .]",
                                "[I, think, I, 'll, start, a, new, life, where, no, one, knows, my, name, .]"};
    List<List<HasWord>> results = MaxentTagger.tokenizeText(new BufferedReader(new StringReader(text)));
    for (int i = 0; i < results.size(); ++i) {
      StringWriter result = new StringWriter();
      result.write(results.get(i).toString());
      assertEquals( expectedResults[i], result.toString());
    }
  }

  private static void compareResults(String []expectedOutput, ArrayList<String> outputStrings) {
    assertEquals(expectedOutput.length, outputStrings.size());
    for (int i = 0; i < outputStrings.size(); ++i) {
      assertEquals(expectedOutput[i].trim(), outputStrings.get(i).trim());
    }
  }

  private static void runRunTaggerTest(boolean emulateStdin, String xmlTag,
                               String input,
                               String ... expectedOutput) {
    StringWriter output = new StringWriter();
    try {
      if (emulateStdin) {
        tagger.runTaggerStdin(new BufferedReader(new StringReader(input)),
                              new BufferedWriter(output),
                              OutputStyle.SLASH_TAGS);
      } else {
        tagger.runTagger(new BufferedReader(new StringReader(input)),
                         new BufferedWriter(output), xmlTag,
                         OutputStyle.SLASH_TAGS);
      }
    } catch(Exception e) {
      throw new RuntimeException(e);
    }

    //System.out.println(input);
    //System.out.println(output.toString());

    BufferedReader reader = new BufferedReader(new StringReader(output.toString()));
    ArrayList<String> outputStrings = new ArrayList<>();
    try {
      for (String outputLine; (outputLine = reader.readLine()) != null; ) {
        outputStrings.add(outputLine);
      }
    } catch(IOException e) {
      throw new RuntimeException(e);
    }

    compareResults(expectedOutput, outputStrings);
  }

  /**
   * Test the stdin handling of runTagger
   */
  public void testRunTaggerStdin() {
    runRunTaggerTest(true, "",
                     "This is a test.\nThe cat fought the dog.  The dog won because it was much bigger.",
                     "This_DT is_VBZ a_DT test_NN ._.",
                     "The_DT cat_NN fought_VBD the_DT dog_NN ._.",
                     "The_DT dog_NN won_VBD because_IN it_PRP was_VBD much_RB bigger_JJR ._.");
  }

  /**
   * Test the non-console (eg file input) text handling of runTagger
   */
  public void testRunTaggerNotStdin() {
    runRunTaggerTest(false, "",
                     "This is another test.  This time, the input is not from the console.",
                     "This_DT is_VBZ another_DT test_NN ._.",
                     "This_DT time_NN ,_, the_DT input_NN is_VBZ not_RB from_IN the_DT console_NN ._.");
  }

  /**
   * Test the non-console xml handling the runTagger
   */
  public void testRunTaggerXML() {
    runRunTaggerTest(false, "text",
                     "<tagger>\n  <text>\n    This tests the xml input.\n  </text>  \n  This should not be tagged.  \n  <text>\n    This should be tagged.\n  </text>\n  <text>\n    The dog's barking kept the\n neighbors up all night.\n  </text>\n</tagging>",
                     "This_DT tests_VBZ the_DT xml_NN input_NN ._.",
                     "This_DT should_MD be_VB tagged_VBN ._.",
                     "The_DT dog_NN 's_POS barking_VBG kept_VBD the_DT neighbors_NNS up_RB all_DT night_NN ._.");
  }

  public void testRunTaggerXML2Tags() {
    runRunTaggerTest(false, "foo|bar",
                     "<tagger>\n  <foo>\n    This tests the xml input.\n  </foo>  \n  This should not be tagged.  \n  <bar>\n    This should be tagged.\n  </bar>\n  <foo>\n    The dog's barking kept the\n neighbors up all night.\n  </foo>\n</tagging>",
                     "This_DT tests_VBZ the_DT xml_NN input_NN ._.",
                     "This_DT should_MD be_VB tagged_VBN ._.",
                     "The_DT dog_NN 's_POS barking_VBG kept_VBD the_DT neighbors_NNS up_RB all_DT night_NN ._.");
  }

  public void testRunTaggerManyTags() {
    runRunTaggerTest(false, "text.*",
                     "<tagger>\n  <text1>\n    This tests the xml input.\n  </text1>  \n  This should not be tagged.  \n  <text2>\n    This should be tagged.\n  </text2>\n  <text3>\n    The dog's barking kept the\n neighbors up all night.\n  </text3>\n</tagging>",
                     "This_DT tests_VBZ the_DT xml_NN input_NN ._.",
                     "This_DT should_MD be_VB tagged_VBN ._.",
                     "The_DT dog_NN 's_POS barking_VBG kept_VBD the_DT neighbors_NNS up_RB all_DT night_NN ._.");
  }

  private static void runTagFromXMLTest(String input,
                                String expectedOutput, String ... tags) {
    StringWriter outputWriter = new StringWriter();
    tagger.tagFromXML(new BufferedReader(new StringReader(input)),
                      new BufferedWriter(outputWriter), tags);
    String actualOutput = outputWriter.toString().replaceAll("\\s+", " ");
    expectedOutput = expectedOutput.replaceAll("\\s+", " ");
    //System.out.println("'" + actualOutput + "'");
    //System.out.println("'" + expectedOutput + "'");
    assertEquals(expectedOutput.trim(), actualOutput.trim());
  }

  public void testTagFromXMLSimple() {
    String input = "<tagger><foo>This should be tagged</foo></tagger>";
    String output = "<tagger> <foo> This_DT should_MD be_VB tagged_VBN </foo> </tagger>";
    runTagFromXMLTest(input, output, "foo");
  }

  public void testTagFromXMLTwoTags() {
    String input = "<tagger><foo>This should be tagged</foo>This should not<bar>This should also be tagged</bar></tagger>";
    String output = "<tagger> <foo> This_DT should_MD be_VB tagged_VBN </foo> This should not<bar> This_DT should_MD also_RB be_VB tagged_VBN </bar> </tagger>";
    runTagFromXMLTest(input, output, "foo", "bar");
  }

  public void testTagFromXMLNested() {
    String input = "<tagger><foo><bar>This should be tagged</bar></foo></tagger>";
    String output = "<tagger> <foo> This_DT should_MD be_VB tagged_VBN </foo> </tagger>";
    runTagFromXMLTest(input, output, "foo", "bar");
  }

  public void testTagFromXMLSingleTag() {
    String input = "<tagger><foo>I have no idea what this will output</foo><bar/>but this should not be tagged<bar>this should be tagged</bar></tagger>";
    String output = "<tagger> <foo> I_PRP have_VBP no_DT idea_NN what_WP this_DT will_MD output_NN </foo> <bar> </bar> but this should not be tagged<bar> this_DT should_MD be_VB tagged_VBN </bar> </tagger> ";
    runTagFromXMLTest(input, output, "foo", "bar");
  }

  public void testTagFromXMLEscaping() {
    String input = "<tagger><foo>A simple math formula is 5 &lt; 6</foo> which is the same as 6 &gt; 5</tagger>";
    // the JJR tag here is wrong, but that's a tagger training data issue.
    String output = "<tagger> <foo> A_DT simple_JJ math_NN formula_NN is_VBZ 5_CD &lt;_SYM 6_CD </foo> which is the same as 6 &gt; 5</tagger>";
    runTagFromXMLTest(input, output, "foo", "bar");
  }

  public void testTagString() {
    String input = "My dog is fluffy and white and has a fluffy tail.";
    String expectedOutput = "My_PRP$ dog_NN is_VBZ fluffy_JJ and_CC white_JJ and_CC has_VBZ a_DT fluffy_JJ tail_NN ._.";
    String output = tagger.tagString(input).trim();
    assertEquals(expectedOutput, output);
  }

  public void testTagCoreLabels() {
    List<CoreLabel> words = new ArrayList<>();
    String[] testWords = {"I", "think", "I", "'ll",
                          "go", "to", "Boston", "."};

    for (String word : testWords) {
      CoreLabel label = new CoreLabel(new Word(word));
      label.setWord(label.value());
      words.add(label);
    }

    tagger.tagCoreLabels(words);

    String[] expectedTags = {"PRP", "VBP", "PRP", "MD",
                             "VB", "IN", "NNP", "."};

    assertEquals(expectedTags.length, words.size());
    for (int i = 0; i < expectedTags.length; ++i) {
      assertEquals(expectedTags[i], words.get(i).tag());
    }
  }

  public void testTaggerWrapper() {
    TaggerConfig config = new TaggerConfig(tagger.config);
    config.setProperty("tokenize", "false");

    MaxentTagger.TaggerWrapper wrapper =
      new MaxentTagger.TaggerWrapper(tagger);
    String query = "This is a test . What is the result of two sentences ?";
    String expectedResult = "This_DT is_VBZ a_DT test_NN ._. " +
      "What_WP is_VBZ the_DT result_NN of_IN two_CD sentences_NNS ?_.";
    String result = wrapper.apply(query).trim();
    assertEquals(expectedResult, result);
  }

}
