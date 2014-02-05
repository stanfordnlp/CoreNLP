package edu.stanford.nlp.process;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.ling.Sentence;
import junit.framework.TestCase;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Word;


/** @author Christopher Manning
 */
public class PTBTokenizerTest extends TestCase {

  private String[] ptbInputs = {
    "This is a sentence.",
    "U.S. insurance: Conseco acquires Kemper Corp. \n</HEADLINE>\n<P>\nU.S insurance",
    "Based in Eugene,Ore., PakTech needs a new distributor after Sydney-based Creative Pack Pty. Ltd. went into voluntary administration.",
    "The Iron Age (ca. 1300 – ca. 300 BC).",
    "Indo\u00ADnesian ship\u00ADing \u00AD",
    "Gimme a phone, I'm gonna call."
  };

  private String[][] ptbGold = {
    { "This", "is", "a", "sentence", "." },
    { "U.S.", "insurance", ":", "Conseco", "acquires", "Kemper", "Corp.", ".",
      "</HEADLINE>", "<P>", "U.S", "insurance" },
    { "Based", "in", "Eugene", ",", "Ore.", ",", "PakTech", "needs", "a", "new",
      "distributor", "after", "Sydney-based", "Creative", "Pack", "Pty.", "Ltd.",
      "went", "into", "voluntary", "administration", "." },
    { "The", "Iron", "Age", "-LRB-", "ca.", "1300", "--", "ca.", "300", "BC", "-RRB-", "." },
    { "Indonesian", "shiping", "-" },
    { "Gim", "me", "a", "phone", ",", "I", "'m", "gon", "na", "call", "." }
  };

  public void testPTBTokenizerWord() {
    assert(ptbInputs.length == ptbGold.length);
    for (int sent = 0; sent < ptbInputs.length; sent++) {
      PTBTokenizer<Word> ptbTokenizer = PTBTokenizer.newPTBTokenizer(new StringReader(ptbInputs[sent]));
      int i = 0;
      while (ptbTokenizer.hasNext()) {
        Word w = ptbTokenizer.next();
        try {
          assertEquals("PTBTokenizer problem", ptbGold[sent][i], w.value());
        } catch (ArrayIndexOutOfBoundsException aioobe) {
          // the assertion below outside the loop will fail
        }
        i++;
      }
      assertEquals("PTBTokenizer num tokens problem", i, ptbGold[sent].length);
    }
  }

  private String[] corpInputs = {
    "So, too, many analysts predict, will Exxon Corp., Chevron Corp. and Amoco Corp.",
    "So, too, many analysts predict, will Exxon Corp., Chevron Corp. and Amoco Corp.   ",
  };

  private String[][] corpGold = {
          { "So", ",", "too", ",", "many", "analysts", "predict", ",", "will", "Exxon",
            "Corp.", ",", "Chevron", "Corp.", "and", "Amoco", "Corp", "." }, // strictTreebank3
          { "So", ",", "too", ",", "many", "analysts", "predict", ",", "will", "Exxon",
                  "Corp.", ",", "Chevron", "Corp.", "and", "Amoco", "Corp.", "." }, // regular
  };

  public void testCorp() {
    // We test a 2x2 design: {strict, regular} x {no following context, following context}
    for (int sent = 0; sent < 4; sent++) {
      PTBTokenizer<CoreLabel> ptbTokenizer = new PTBTokenizer<CoreLabel>(new StringReader(corpInputs[sent / 2]),
              new CoreLabelTokenFactory(),
              (sent % 2 == 0) ? "strictTreebank3": "");
      int i = 0;
      while (ptbTokenizer.hasNext()) {
        CoreLabel w = ptbTokenizer.next();
        try {
          assertEquals("PTBTokenizer problem", corpGold[sent % 2][i], w.word());
        } catch (ArrayIndexOutOfBoundsException aioobe) {
          // the assertion below outside the loop will fail
        }
        i++;
      }
      if (i != corpGold[sent % 2].length) {
        System.out.println("Gold: " + Arrays.toString(corpGold[sent % 2]));
        List<CoreLabel> tokens = new PTBTokenizer<CoreLabel>(new StringReader(corpInputs[sent / 2]),
              new CoreLabelTokenFactory(),
              (sent % 2 == 0) ? "strictTreebank3": "").tokenize();
        System.out.println("Guess: " + Sentence.listToString(tokens));
        System.out.flush();
      }
      assertEquals("PTBTokenizer num tokens problem", i, corpGold[sent % 2].length);
    }
  }

  public void testJacobEisensteinApostropheCase() {
    StringReader reader = new StringReader("it's");
    PTBTokenizer<Word> tokenizer = PTBTokenizer.newPTBTokenizer(reader);
    List<Word> stemmedTokens = tokenizer.tokenize();
    // for (Word word : stemmedTokens) System.out.print (word+" ");
    reader = new StringReader(" it's ");
    tokenizer = PTBTokenizer.newPTBTokenizer(reader);
    List<Word> stemmedTokens2 = tokenizer.tokenize();
    // System.out.println ();
    // for (Word word : stemmedTokens2) System.out.print (word+" ");
    // System.out.println();
    assertEquals(stemmedTokens, stemmedTokens2);
  }

  private static String[] untokInputs = {
    "London - AFP reported junk .",
    "Paris - Reuters reported news .",
    "Sydney - News said - something .",
    "HEADLINE - New Android phone !",
    "I did it 'cause I wanted to , and you 'n' me know that .",
    "He said that `` Luxembourg needs surface - to - air missiles . ''",
  };

  private static String[] untokOutputs = {
    "London - AFP reported junk.",
    "Paris - Reuters reported news.",
    "Sydney - News said - something.",
    "HEADLINE - New Android phone!",
    "I did it 'cause I wanted to, and you 'n' me know that.",
    "He said that \"Luxembourg needs surface-to-air missiles.\"",
  };


  public void testUntok() {
    assert(untokInputs.length == untokOutputs.length);
    for (int i = 0; i < untokInputs.length; i++) {
      assertEquals("untok gave the wrong result", untokOutputs[i], PTBTokenizer.ptb2Text(untokInputs[i]));
    }
  }


  public void testInvertible() {
    String text = "  This     is     a      colourful sentence.    ";
    PTBTokenizer<CoreLabel> tokenizer =
      PTBTokenizer.newPTBTokenizer(new StringReader(text), false, true);
    List<CoreLabel> tokens = tokenizer.tokenize();
    assertEquals(6, tokens.size());
    assertEquals("  ", tokens.get(0).get(CoreAnnotations.BeforeAnnotation.class));
    assertEquals("     ", tokens.get(0).get(CoreAnnotations.AfterAnnotation.class));
    assertEquals("Wrong begin char offset", 2, (int) tokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
    assertEquals("Wrong end char offset", 6, (int) tokens.get(0).get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
    assertEquals("This", tokens.get(0).get(CoreAnnotations.OriginalTextAnnotation.class));
    // note: after(x) and before(x+1) are the same
    assertEquals("     ", tokens.get(0).get(CoreAnnotations.AfterAnnotation.class));
    assertEquals("     ", tokens.get(1).get(CoreAnnotations.BeforeAnnotation.class));
    assertEquals("colorful", tokens.get(3).get(CoreAnnotations.TextAnnotation.class));
    assertEquals("colourful", tokens.get(3).get(CoreAnnotations.OriginalTextAnnotation.class));
    assertEquals("", tokens.get(4).after());
    assertEquals("", tokens.get(5).before());
    assertEquals("    ", tokens.get(5).get(CoreAnnotations.AfterAnnotation.class));

    StringBuilder result = new StringBuilder();
    result.append(tokens.get(0).get(CoreAnnotations.BeforeAnnotation.class));
    for (CoreLabel token : tokens) {
      result.append(token.get(CoreAnnotations.OriginalTextAnnotation.class));
      String after = token.get(CoreAnnotations.AfterAnnotation.class);
      if (after != null)
        result.append(after);
    }
    assertEquals(text, result.toString());

    for (int i = 0; i < tokens.size() - 1; ++i) {
      assertEquals(tokens.get(i).get(CoreAnnotations.AfterAnnotation.class),
                   tokens.get(i + 1).get(CoreAnnotations.BeforeAnnotation.class));
    }
  }

}
