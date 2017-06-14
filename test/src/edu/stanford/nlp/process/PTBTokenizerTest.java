package edu.stanford.nlp.process;

import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.international.negra.NegraPennLanguagePack;


/** @author Christopher Manning
 */
public class PTBTokenizerTest {

  private final String[] ptbInputs = {
      "This is a sentence.",
      "U.S. insurance: Conseco acquires Kemper Corp. \n</HEADLINE>\n<P>\nU.S insurance",
      "Based in Eugene,Ore., PakTech needs a new distributor after Sydney-based Creative Pack Pty. Ltd. went into voluntary administration.",
      "The Iron Age (ca. 1300 – ca. 300 BC).",
      "Indo\u00ADnesian ship\u00ADping \u00AD",
      "Gimme a phone, I'm gonna call.",
      "\"John & Mary's dog,\" Jane thought (to herself).\n\"What a #$%!\na- ``I like AT&T''.\"",
      "I said at 4:45pm.",
      "I can't believe they wanna keep 40% of that.\"\n``Whatcha think?''\n\"I don't --- think so...,\"",
      "You `paid' US$170,000?!\nYou should've paid only$16.75.",
      "1. Buy a new Chevrolet (37%-owned in the U.S..) . 15%",
      "I like you ;-) but do you care :(. I'm happy ^_^ but shy (x.x)!",
      "Diamond (``Not even the chair'') lives near Udaipur (84km). {1. A potential Palmer trade:}",
      "No. I like No. 24 and no.47.",
      "You can get a B.S. or a B. A. or a Ph.D (sometimes a Ph. D) from Stanford.",
      "@Harry_Styles didn`t like Mu`ammar al-Qaddafi",
      "Kenneth liked Windows 3.1, Windows 3.x, and Mesa A.B as I remember things.",
      "I like programming in F# more than C#.",
      "NBC Live will be available free through the Yahoo! Chat Web site. E! Entertainment said ``Jeopardy!'' is a game show.",
      "I lived in O\u2019Malley and read OK! Magazine.",
      "I lived in O\u0092Malley and read OK! Magazine.", /* invalid unicode codepoint, but inherit from cp1252 */
      "I like: \u2022wine, \u0095cheese, \u2023salami, & \u2043speck.",
      "I don't give a f**k about your sh*tty life.",
      "First sentence.... Second sentence.",
      "First sentence . . . . Second sentence.",
      "I wasn’t really ... well, what I mean...see . . . what I'm saying, the thing is . . . I didn’t mean it.",
      "This is a url test. Here is one: http://google.com.",
      "This is a url test. Here is one: htvp://google.com.",
      "Download from ftp://myname@host.dom/%2Fetc/motd",
      "Download from svn://user@location.edu/path/to/magic/unicorns",
      "Download from svn+ssh://user@location.edu/path/to/magic/unicorns",
      "We traveled from No. Korea to So. Calif. yesterday.",
      "I dunno.",
      "The o-kay was received by the anti-acquisition front on its foolishness-filled fish market.",
      "We ran the pre-tests through the post-scripted centrifuge.",
      "School-aged parents should be aware of the unique problems that they face.",
      "I dispute Art. 53 of the convention.",
      "I like Art. And I like History.",
      "Contact: sue@google.com, fred@stanford.edu; michael.inman@lab.rpi.cs.cmu.edu.",
      "Email: recruiters@marvelconsultants.com <mailto:recruiters@marvelconsultants.com>",
      " Jeremy Meier <jermeier@earthlink.net>",
      "Ram Tackett,  (mailto:rtackett@abacustech.net)",
      "[Jgerma5@aol.com]. Danny_Jones%ENRON@eott.com",
      "https://fancy.startup.ai",
      "mid-2015",
      "UK-based",
      "2010-2015",
      "20-30%",
      "80,000-man march",
      "39-yard",
      "60-90's",
      "Soft AC-styled",
      "3 p.m., eastern time",
  };

  private final String[][] ptbGold = {
      { "This", "is", "a", "sentence", "." },
      { "U.S.", "insurance", ":", "Conseco", "acquires", "Kemper", "Corp.", ".",
          "</HEADLINE>", "<P>", "U.S", "insurance" },
      { "Based", "in", "Eugene", ",", "Ore.", ",", "PakTech", "needs", "a", "new",
          "distributor", "after", "Sydney-based", "Creative", "Pack", "Pty.", "Ltd.",
          "went", "into", "voluntary", "administration", "." },
      { "The", "Iron", "Age", "-LRB-", "ca.", "1300", "--", "ca.", "300", "BC", "-RRB-", "." },
      { "Indonesian", "shipping", "-" },
      { "Gim", "me", "a", "phone", ",", "I", "'m", "gon", "na", "call", "."},
      { "``", "John", "&", "Mary", "'s", "dog", ",", "''", "Jane", "thought", "-LRB-", "to", "herself", "-RRB-",
          ".", "``", "What", "a", "#", "$", "%", "!", "a", "-", "``", "I", "like", "AT&T", "''", ".", "''" },
      { "I", "said", "at", "4:45", "pm", "."},
      { "I", "ca", "n't", "believe", "they", "wan", "na", "keep", "40", "%", "of", "that", ".", "''",
          "``", "Whatcha", "think", "?", "''", "``", "I", "do", "n't", "--", "think", "so", "...", ",", "''" },
      // We don't yet split "Whatcha" but probably should following model of "Whaddya" --> What d ya. Maybe What cha
      { "You", "`", "paid", "'", "US$", "170,000", "?!", "You", "should", "'ve", "paid", "only", "$", "16.75", "." },
      { "1", ".", "Buy", "a", "new", "Chevrolet",
          "-LRB-", "37", "%", "-", "owned", "in", "the", "U.S.", ".", "-RRB-", ".", "15", "%" },
      // Unclear if 37%-owned is right or wrong under old PTB....  Maybe should be 37 %-owned even though sort of crazy
      { "I", "like", "you", ";--RRB-", "but", "do", "you", "care",  ":-LRB-", ".",
          "I", "'m", "happy", "^_^", "but", "shy", "-LRB-x.x-RRB-", "!" },
      { "Diamond", "-LRB-", "``", "Not", "even",  "the", "chair", "''", "-RRB-", "lives", "near", "Udaipur", "-LRB-", "84km", "-RRB-", ".",
          "-LCB-", "1", ".", "A", "potential", "Palmer", "trade", ":", "-RCB-"},
      { "No", ".", "I", "like", "No.", "24", "and", "no.", "47", "." },
      { "You", "can", "get", "a", "B.S.", "or", "a", "B.", "A.", "or", "a", "Ph.D", "-LRB-", "sometimes", "a", "Ph.", "D", "-RRB-", "from", "Stanford", "." },
      { "@Harry_Styles", "did", "n`t", "like", "Mu`ammar", "al-Qaddafi" },
      { "Kenneth", "liked", "Windows", "3.1", ",", "Windows", "3.x", ",", "and", "Mesa", "A.B", "as", "I", "remember", "things", ".", },
      { "I", "like", "programming", "in", "F#", "more", "than", "C#", "." },
      { "NBC", "Live", "will", "be", "available", "free", "through", "the", "Yahoo!", "Chat", "Web", "site", ".",
          "E!", "Entertainment", "said", "``", "Jeopardy!", "''", "is", "a", "game", "show", "." },
      { "I", "lived", "in", "O'Malley", "and", "read", "OK!", "Magazine", "." },
      { "I", "lived", "in", "O'Malley", "and", "read", "OK!", "Magazine", "." },
      { "I", "like", ":", "\u2022", "wine", ",", "\u2022", "cheese", ",", "\u2023", "salami",
          ",", "&", "\u2043", "speck", "." },
      { "I", "do", "n't", "give", "a", "f**k", "about", "your", "sh*tty", "life", "." },
      { "First", "sentence", "...", ".", "Second", "sentence", "." },
      { "First", "sentence", "...", ".", "Second", "sentence", "." },
      { "I", "was", "n't", "really", "...", "well", ",", "what", "I", "mean", "...", "see", "...", "what", "I", "'m", "saying",
          ",", "the", "thing", "is", "...", "I", "did", "n't", "mean", "it", "." },
      {"This", "is", "a", "url", "test", ".", "Here", "is", "one", ":", "http://google.com", "."},
      {"This", "is", "a", "url", "test", ".", "Here", "is", "one", ":", "htvp", ":", "/", "/", "google.com", "."},
      {"Download", "from", "ftp://myname@host.dom/%2Fetc/motd"},
      {"Download", "from", "svn://user@location.edu/path/to/magic/unicorns"},
      {"Download", "from", "svn+ssh://user@location.edu/path/to/magic/unicorns"},
      { "We", "traveled", "from", "No.", "Korea", "to", "So.", "Calif.", "yesterday", "." },
      { "I", "du", "n", "no", "." },
      {"The", "o-kay", "was", "received", "by", "the", "anti-acquisition", "front", "on", "its", "foolishness-filled", "fish", "market", "."},
      {"We", "ran", "the", "pre-tests", "through", "the", "post-scripted", "centrifuge", "."},
      {"School-aged", "parents", "should", "be", "aware", "of", "the", "unique", "problems", "that", "they", "face","."},
      { "I", "dispute", "Art.", "53", "of", "the", "convention", "." },
      { "I", "like", "Art", ".", "And", "I", "like", "History", "." },
      { "Contact", ":", "sue@google.com", ",", "fred@stanford.edu", ";", "michael.inman@lab.rpi.cs.cmu.edu", "." },
      { "Email", ":", "recruiters@marvelconsultants.com", "<mailto:recruiters@marvelconsultants.com>" },
      { "Jeremy", "Meier", "<jermeier@earthlink.net>" },
      { "Ram", "Tackett", ",", "-LRB-", "mailto:rtackett@abacustech.net", "-RRB-" },
      { "-LSB-", "Jgerma5@aol.com", "-RSB-", ".", "Danny_Jones%ENRON@eott.com" },
      { "https://fancy.startup.ai" },
      { "mid-2015" },
      { "UK-based" },
      { "2010-2015" },
      { "20-30", "%" },
      { "80,000-man", "march" },
      { "39-yard" },
      { "60-90", "'s" },
      { "Soft", "AC-styled" },
      { "3", "p.m.", ",", "eastern", "time" },
  };

  private final String[][] ptbGoldSplitHyphenated = {
      { "This", "is", "a", "sentence", "." },
      { "U.S.", "insurance", ":", "Conseco", "acquires", "Kemper", "Corp.", ".",
          "</HEADLINE>", "<P>", "U.S", "insurance" },
      { "Based", "in", "Eugene", ",", "Ore.", ",", "PakTech", "needs", "a", "new",
          "distributor", "after", "Sydney", "-", "based", "Creative", "Pack", "Pty.", "Ltd.",
          "went", "into", "voluntary", "administration", "." },
      { "The", "Iron", "Age", "-LRB-", "ca.", "1300", "--", "ca.", "300", "BC", "-RRB-", "." },
      { "Indonesian", "shipping", "-" },
      { "Gim", "me", "a", "phone", ",", "I", "'m", "gon", "na", "call", "."},
      { "``", "John", "&", "Mary", "'s", "dog", ",", "''", "Jane", "thought", "-LRB-", "to", "herself", "-RRB-",
          ".", "``", "What", "a", "#", "$", "%", "!", "a", "-", "``", "I", "like", "AT&T", "''", ".", "''" },
      { "I", "said", "at", "4:45", "pm", "."},
      { "I", "ca", "n't", "believe", "they", "wan", "na", "keep", "40", "%", "of", "that", ".", "''",
          "``", "Whatcha", "think", "?", "''", "``", "I", "do", "n't", "--", "think", "so", "...", ",", "''" },
      // We don't yet split "Whatcha" but probably should following model of "Whaddya" --> What d ya. Maybe What cha
      { "You", "`", "paid", "'", "US$", "170,000", "?!", "You", "should", "'ve", "paid", "only", "$", "16.75", "." },
      { "1", ".", "Buy", "a", "new", "Chevrolet",
          "-LRB-", "37", "%", "-", "owned", "in", "the", "U.S.", ".", "-RRB-", ".", "15", "%" },
      // Unclear if 37%-owned is right or wrong under old PTB....  Maybe should be 37 %-owned even though sort of crazy
      { "I", "like", "you", ";--RRB-", "but", "do", "you", "care",  ":-LRB-", ".",
          "I", "'m", "happy", "^_^", "but", "shy", "-LRB-x.x-RRB-", "!" },
      { "Diamond", "-LRB-", "``", "Not", "even",  "the", "chair", "''", "-RRB-", "lives", "near", "Udaipur", "-LRB-", "84km", "-RRB-", ".",
          "-LCB-", "1", ".", "A", "potential", "Palmer", "trade", ":", "-RCB-"},
      { "No", ".", "I", "like", "No.", "24", "and", "no.", "47", "." },
      { "You", "can", "get", "a", "B.S.", "or", "a", "B.", "A.", "or", "a", "Ph.D", "-LRB-", "sometimes", "a", "Ph.", "D", "-RRB-", "from", "Stanford", "." },
      { "@Harry_Styles", "did", "n`t", "like", "Mu`ammar", "al", "-", "Qaddafi" },
      { "Kenneth", "liked", "Windows", "3.1", ",", "Windows", "3.x", ",", "and", "Mesa", "A.B", "as", "I", "remember", "things", ".", },
      { "I", "like", "programming", "in", "F#", "more", "than", "C#", "." },
      { "NBC", "Live", "will", "be", "available", "free", "through", "the", "Yahoo!", "Chat", "Web", "site", ".",
          "E!", "Entertainment", "said", "``", "Jeopardy!", "''", "is", "a", "game", "show", "." },
      { "I", "lived", "in", "O'Malley", "and", "read", "OK!", "Magazine", "." },
      { "I", "lived", "in", "O'Malley", "and", "read", "OK!", "Magazine", "." },
      { "I", "like", ":", "\u2022", "wine", ",", "\u2022", "cheese", ",", "\u2023", "salami",
          ",", "&", "\u2043", "speck", "." },
      { "I", "do", "n't", "give", "a", "f**k", "about", "your", "sh*tty", "life", "." },
      { "First", "sentence", "...", ".", "Second", "sentence", "." },
      { "First", "sentence", "...", ".", "Second", "sentence", "." },
      { "I", "was", "n't", "really", "...", "well", ",", "what", "I", "mean", "...", "see", "...", "what", "I", "'m", "saying",
          ",", "the", "thing", "is", "...", "I", "did", "n't", "mean", "it", "." },
      {"This", "is", "a", "url", "test", ".", "Here", "is", "one", ":", "http://google.com", "."},
      {"This", "is", "a", "url", "test", ".", "Here", "is", "one", ":", "htvp", ":", "/", "/", "google.com", "."},
      {"Download", "from", "ftp://myname@host.dom/%2Fetc/motd"},
      {"Download", "from", "svn://user@location.edu/path/to/magic/unicorns"},
      {"Download", "from", "svn+ssh://user@location.edu/path/to/magic/unicorns"},
      { "We", "traveled", "from", "No.", "Korea", "to", "So.", "Calif.", "yesterday", "." },
      { "I", "du", "n", "no", "." },
      {"The", "o-kay", "was", "received", "by", "the", "anti-acquisition", "front", "on", "its", "foolishness", "-", "filled", "fish", "market", "."},
      {"We", "ran", "the", "pre-tests", "through", "the", "post-scripted", "centrifuge", "."},
      {"School", "-", "aged", "parents", "should", "be", "aware", "of", "the", "unique", "problems", "that", "they", "face","."},
      { "I", "dispute", "Art.", "53", "of", "the", "convention", "." },
      { "I", "like", "Art", ".", "And", "I", "like", "History", "." },
      { "Contact", ":", "sue@google.com", ",", "fred@stanford.edu", ";", "michael.inman@lab.rpi.cs.cmu.edu", "." },
      { "Email", ":", "recruiters@marvelconsultants.com", "<mailto:recruiters@marvelconsultants.com>" },
      { "Jeremy", "Meier", "<jermeier@earthlink.net>" },
      { "Ram", "Tackett", ",", "-LRB-", "mailto:rtackett@abacustech.net", "-RRB-" },
      { "-LSB-", "Jgerma5@aol.com", "-RSB-", ".", "Danny_Jones%ENRON@eott.com" },
      { "https://fancy.startup.ai" },
      { "mid", "-", "2015" },
      { "UK", "-", "based" },
      { "2010", "-", "2015" },
      { "20", "-", "30", "%" },
      { "80,000", "-", "man", "march" },
      { "39", "-", "yard"},
      { "60", "-", "90", "'s" },
      { "Soft", "AC", "-", "styled" },
      { "3", "p.m.", ",", "eastern", "time" },
  };

  @Test
  public void testPTBTokenizerWord() {
    TokenizerFactory<Word> tokFactory = PTBTokenizer.factory();
    runOnTwoArrays(tokFactory, ptbInputs, ptbGold);
  }


  private final String[] corpInputs = {
    "So, too, many analysts predict, will Exxon Corp., Chevron Corp. and Amoco Corp.",
    "So, too, many analysts predict, will Exxon Corp., Chevron Corp. and Amoco Corp.   ",
  };

  private final String[][] corpGold = {
          { "So", ",", "too", ",", "many", "analysts", "predict", ",", "will", "Exxon",
            "Corp.", ",", "Chevron", "Corp.", "and", "Amoco", "Corp", "." }, // strictTreebank3
          { "So", ",", "too", ",", "many", "analysts", "predict", ",", "will", "Exxon",
                  "Corp.", ",", "Chevron", "Corp.", "and", "Amoco", "Corp.", "." }, // regular
  };

  @Test
  public void testCorp() {
    // We test a 2x2 design: {strict, regular} x {no following context, following context}
    for (int sent = 0; sent < 4; sent++) {
      PTBTokenizer<CoreLabel> ptbTokenizer = new PTBTokenizer<>(new StringReader(corpInputs[sent / 2]),
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
        System.out.print("Gold: ");
        System.out.println(Arrays.toString(corpGold[sent % 2]));
        List<CoreLabel> tokens = new PTBTokenizer<>(new StringReader(corpInputs[sent / 2]),
              new CoreLabelTokenFactory(),
              (sent % 2 == 0) ? "strictTreebank3": "").tokenize();
        System.out.print("Guess: ");
        System.out.println(SentenceUtils.listToString(tokens));
        System.out.flush();
      }
      assertEquals("PTBTokenizer num tokens problem", i, corpGold[sent % 2].length);
    }
  }


  @Test
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


  private static final String[] untokInputs = {
    "London - AFP reported junk .",
    "Paris - Reuters reported news .",
    "Sydney - News said - something .",
    "HEADLINE - New Android phone !",
    "I did it 'cause I wanted to , and you 'n' me know that .",
    "He said that `` Luxembourg needs surface - to - air missiles . ''",
  };

  private static final String[] untokOutputs = {
    "London - AFP reported junk.",
    "Paris - Reuters reported news.",
    "Sydney - News said - something.",
    "HEADLINE - New Android phone!",
    "I did it 'cause I wanted to, and you 'n' me know that.",
    "He said that \"Luxembourg needs surface-to-air missiles.\"",
  };

  @Test
  public void testUntok() {
    assert(untokInputs.length == untokOutputs.length);
    for (int i = 0; i < untokInputs.length; i++) {
      assertEquals("untok gave the wrong result", untokOutputs[i], PTBTokenizer.ptb2Text(untokInputs[i]));
    }
  }


  @Test
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
    // americanize is now off by default
    assertEquals("colourful", tokens.get(3).get(CoreAnnotations.TextAnnotation.class));
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


  private final String[] sgmlInputs = {
    "Significant improvements in peak FEV1 were demonstrated with tiotropium/olodaterol 5/2 μg (p = 0.008), 5/5 μg (p = 0.012), and 5/10 μg (p < 0.0001) versus tiotropium monotherapy [51].",
    "Panasonic brand products are produced by Samsung Electronics Co. Ltd. Sanyo products aren't.",
    "Oesophageal acid exposure (% time <pH 4) was similar in patients with or without complications (19.2% v 19.3% p>0.05).",
    "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Strict//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">",
    "Hi! <foo bar=\"baz xy = foo !$*) 422\" > <?PITarget PIContent?> <?PITarget PIContent> Hi!",
    "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n<?xml-stylesheet type=\"text/xsl\" href=\"style.xsl\"?>\n<book xml:id=\"simple_book\" xmlns=\"http://docbook.org/ns/docbook\" version=\"5.0\">\n",
    "<chapter xml:id=\"chapter_1\"><?php echo $a; ?>\n<!-- This is an SGML/XML comment \"Hi!\" -->\n<p> </p> <p-fix / >",
    "<a href=\"http:\\\\it's\\here\"> <quote orig_author='some \"dude'/> <not sgmltag",
    "<quote previouspost=\"\n" +
            "&gt; &gt; I really don't want to process this junk.\n" +
            "&gt; No one said you did, runny.  What's got you so scared, anyway?-\n" +
            "\">",
    "&lt;b...@canada.com&gt; funky@thedismalscience.net <myemail@where.com>",
    "<DOC> <DOCID> nyt960102.0516 </DOCID><STORYID cat=w pri=u> A0264 </STORYID> <SLUG fv=ttj-z> ", // this is a MUC7 document
    // In WMT 2015 from GigaWord (mis)processing. Do not always want to allow newline within SGML as messes too badly with CoNLL and one-sentence-per-line processing
    "<!-- copy from here --> <a href=\"http://strategis.gc.ca/epic/internet/inabc-eac.nsf/en/home\"><img src=\"id-images/ad-220x80_01e.jpg\" alt=\"Aboriginal Business Canada:\n" +
                  "Opening New Doors for Your Business\" width=\"220\" height=\"80\" border=\"0\"></a> <!-- copy to here --> Small ABC Graphic Instructions 1.",
    "We traveled from No.\nKorea to the U.S.A.\nWhy?"
  };

  private final String[][] sgmlGold = {
    { "Significant", "improvements", "in", "peak", "FEV1", "were", "demonstrated", "with", "tiotropium/olodaterol",
            "5/2", "μg", "-LRB-", "p", "=", "0.008", "-RRB-", ",", "5/5", "μg", "-LRB-", "p", "=", "0.012", "-RRB-",
            ",", "and", "5/10", "μg", "-LRB-", "p", "<", "0.0001", "-RRB-", "versus", "tiotropium", "monotherapy",
            "-LSB-", "51", "-RSB-", "." },
    { "Panasonic", "brand", "products", "are", "produced", "by", "Samsung", "Electronics", "Co.", "Ltd.", ".",
            "Sanyo", "products", "are", "n't", ".", },
    { "Oesophageal", "acid", "exposure", "-LRB-", "%", "time", "<", "pH", "4", "-RRB-", "was", "similar", "in",
            "patients", "with", "or", "without", "complications", "-LRB-", "19.2", "%", "v", "19.3", "%",
            "p", ">", "0.05", "-RRB-", ".", },
    { "<!DOCTYPE\u00A0html\u00A0PUBLIC\u00A0\"-//W3C//DTD\u00A0HTML\u00A04.01\u00A0Strict//EN\"\u00A0\"http://www.w3.org/TR/html4/strict.dtd\">" }, // spaces go to &nbsp; \u00A0
    { "Hi", "!", "<foo\u00A0bar=\"baz\u00A0xy\u00A0=\u00A0foo\u00A0!$*)\u00A0422\"\u00A0>", "<?PITarget\u00A0PIContent?>", "<?PITarget\u00A0PIContent>", "Hi", "!" },
    { "<?xml\u00A0version=\"1.0\"\u00A0encoding=\"UTF-8\"\u00A0?>", "<?xml-stylesheet\u00A0type=\"text/xsl\"\u00A0href=\"style.xsl\"?>",
            "<book\u00A0xml:id=\"simple_book\"\u00A0xmlns=\"http://docbook.org/ns/docbook\"\u00A0version=\"5.0\">", },
    { "<chapter\u00A0xml:id=\"chapter_1\">", "<?php\u00A0echo\u00A0$a;\u00A0?>", "<!--\u00A0This\u00A0is\u00A0an\u00A0SGML/XML\u00A0comment\u00A0\"Hi!\"\u00A0-->",
            "<p>", "</p>", "<p-fix\u00A0/\u00A0>"},
    { "<a href=\"http:\\\\it's\\here\">", "<quote orig_author='some \"dude'/>", "<", "not", "sgmltag" },
    { "<quote previouspost=\"\u00A0" +
            "&gt; &gt; I really don't want to process this junk.\u00A0" +
            "&gt; No one said you did, runny.  What's got you so scared, anyway?-\u00A0" +
            "\">" },
    { "&lt;b...@canada.com&gt;", "funky@thedismalscience.net", "<myemail@where.com>" },
    { "<DOC>", "<DOCID>", "nyt960102", ".0516", "</DOCID>", "<STORYID\u00A0cat=w\u00A0pri=u>", "A0264", "</STORYID>", "<SLUG\u00A0fv=ttj-z>" },
    { "<!--\u00A0copy\u00A0from\u00A0here\u00A0-->", "<a\u00A0href=\"http://strategis.gc.ca/epic/internet/inabc-eac.nsf/en/home\">",
            "<img\u00A0src=\"id-images/ad-220x80_01e.jpg\"\u00A0alt=\"Aboriginal\u00A0Business\u00A0Canada:\u00A0" +
            "Opening\u00A0New\u00A0Doors\u00A0for\u00A0Your\u00A0Business\"\u00A0width=\"220\"\u00A0height=\"80\"\u00A0border=\"0\">",
            "</a>",  "<!--\u00A0copy\u00A0to\u00A0here\u00A0-->", "Small", "ABC", "Graphic", "Instructions", "1", "." },
    { "We", "traveled", "from", "No.", "Korea", "to", "the", "U.S.A.", ".", "Why", "?" },
  };

  @Test
  public void testPTBTokenizerSGML() {
    // System.err.println("Starting SGML test");
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("invertible");
    runOnTwoArrays(tokFactory, sgmlInputs, sgmlGold);
    runAgainstOrig(tokFactory, sgmlInputs);
  }


  private final String[][] sgmlPerLineGold = {
    { "Significant", "improvements", "in", "peak", "FEV1", "were", "demonstrated", "with", "tiotropium/olodaterol",
            "5/2", "μg", "-LRB-", "p", "=", "0.008", "-RRB-", ",", "5/5", "μg", "-LRB-", "p", "=", "0.012", "-RRB-",
            ",", "and", "5/10", "μg", "-LRB-", "p", "<", "0.0001", "-RRB-", "versus", "tiotropium", "monotherapy",
            "-LSB-", "51", "-RSB-", "." },
    { "Panasonic", "brand", "products", "are", "produced", "by", "Samsung", "Electronics", "Co.", "Ltd.", ".",
            "Sanyo", "products", "are", "n't", ".", },
    { "Oesophageal", "acid", "exposure", "-LRB-", "%", "time", "<", "pH", "4", "-RRB-", "was", "similar", "in",
            "patients", "with", "or", "without", "complications", "-LRB-", "19.2", "%", "v", "19.3", "%",
            "p", ">", "0.05", "-RRB-", ".", },
    { "<!DOCTYPE\u00A0html\u00A0PUBLIC\u00A0\"-//W3C//DTD\u00A0HTML\u00A04.01\u00A0Strict//EN\"\u00A0\"http://www.w3.org/TR/html4/strict.dtd\">" }, // spaces go to &nbsp; \u00A0
    { "Hi", "!", "<foo\u00A0bar=\"baz\u00A0xy\u00A0=\u00A0foo\u00A0!$*)\u00A0422\"\u00A0>", "<?PITarget\u00A0PIContent?>", "<?PITarget\u00A0PIContent>", "Hi", "!" },
    { "<?xml\u00A0version=\"1.0\"\u00A0encoding=\"UTF-8\"\u00A0?>", "<?xml-stylesheet\u00A0type=\"text/xsl\"\u00A0href=\"style.xsl\"?>",
            "<book\u00A0xml:id=\"simple_book\"\u00A0xmlns=\"http://docbook.org/ns/docbook\"\u00A0version=\"5.0\">", },
    { "<chapter\u00A0xml:id=\"chapter_1\">", "<?php\u00A0echo\u00A0$a;\u00A0?>", "<!--\u00A0This\u00A0is\u00A0an\u00A0SGML/XML\u00A0comment\u00A0\"Hi!\"\u00A0-->",
            "<p>", "</p>", "<p-fix\u00A0/\u00A0>"},
    { "<a href=\"http:\\\\it's\\here\">", "<quote orig_author='some \"dude'/>", "<", "not", "sgmltag" },
    { "<", "quote", "previouspost", "=", "''",
            ">", ">", "I", "really", "do", "n't", "want", "to", "process", "this", "junk", ".",
            ">", "No", "one", "said", "you", "did", ",", "runny", ".", "What", "'s", "got", "you", "so", "scared", ",", "anyway", "?", "-",
            "''", ">" },
    { "&lt;b...@canada.com&gt;", "funky@thedismalscience.net", "<myemail@where.com>" },
    { "<DOC>", "<DOCID>", "nyt960102", ".0516", "</DOCID>", "<STORYID\u00A0cat=w\u00A0pri=u>", "A0264", "</STORYID>", "<SLUG\u00A0fv=ttj-z>" },
    { "<!--\u00A0copy\u00A0from\u00A0here\u00A0-->", "<a\u00A0href=\"http://strategis.gc.ca/epic/internet/inabc-eac.nsf/en/home\">",
            "<", "img", "src", "=", "``", "id-images/ad-220x80_01e.jpg", "''", "alt", "=", "``", "Aboriginal", "Business", "Canada", ":",
            "Opening", "New", "Doors", "for", "Your", "Business", "''",
            "width", "=", "``", "220", "''", "height", "=", "``", "80", "''", "border", "=", "``", "0", "''", ">",
            "</a>",  "<!--\u00A0copy\u00A0to\u00A0here\u00A0-->", "Small", "ABC", "Graphic", "Instructions", "1", "." },
    { "We", "traveled", "from", "No", ".", "Korea", "to", "the", "U.S.A.", "Why", "?" },
  };

  @Test
  public void testPTBTokenizerTokenizePerLineSGML() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("tokenizePerLine=true,invertible");
    runOnTwoArrays(tokFactory, sgmlInputs, sgmlPerLineGold);
    runAgainstOrig(tokFactory, sgmlInputs);
  }

  @Test
  public void testPTBTokenizerTokenizeSplitHyphens() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("splitHyphenated=true,invertible");
    runOnTwoArrays(tokFactory, ptbInputs, ptbGoldSplitHyphenated);
    runAgainstOrig(tokFactory, ptbInputs);
  }


  @Test
  public void testFractions() {
    String[] sample = { "5-1/4 plus 2 3/16 = 7\u00A07/16 in the U.S.S.R. Why not?" };
    String[][] tokenizedNormal = { { "5-1/4", "plus", "2\u00A03/16", "=", "7\u00A07/16", "in", "the", "U.S.S.R.", ".", "Why", "not", "?" } };
    String[][] tokenizedStrict = { { "5-1/4", "plus", "2", "3/16", "=", "7", "7/16", "in", "the", "U.S.S.R", ".", "Why", "not", "?" } };
    TokenizerFactory<CoreLabel> tokFactoryNormal = PTBTokenizer.coreLabelFactory("invertible=true");
    TokenizerFactory<CoreLabel> tokFactoryStrict = PTBTokenizer.coreLabelFactory("strictTreebank3=true,invertible=true");
    runOnTwoArrays(tokFactoryNormal, sample, tokenizedNormal);
    runOnTwoArrays(tokFactoryStrict, sample, tokenizedStrict);
    runAgainstOrig(tokFactoryNormal, sample);
    runAgainstOrig(tokFactoryStrict, sample);
  }


  private static <T extends Label> void runOnTwoArrays(TokenizerFactory<T> tokFactory, String[] inputs, String[][] desired) {
    assertEquals("Test data arrays don't match in length", inputs.length, desired.length);
    for (int sent = 0; sent < inputs.length; sent++) {
      Tokenizer<T> tok = tokFactory.getTokenizer(new StringReader(inputs[sent]));
      for (int i = 0; tok.hasNext() || i < desired[sent].length; i++) {
        if ( ! tok.hasNext()) {
          fail("PTBTokenizer generated too few tokens for sentence " + sent + "! Missing " + desired[sent][i]);
        }
        T w = tok.next();
        if (i >= desired[sent].length) {
          fail("PTBTokenizer generated too many tokens for sentence " + sent + "! Added " + w.value());
        } else {
          assertEquals("PTBTokenizer got wrong token", desired[sent][i], w.value());
        }
      }
    }
  }

  /** The appending has to run one behind so as to make sure that the after annotation has been filled in!
   *  Just placing the appendTextFrom() after reading tok.next() in the loop does not work.
   */
  private static <T extends CoreLabel> void runAgainstOrig(TokenizerFactory<T> tokFactory, String[] inputs) {
    for (String input : inputs) {
      // System.err.println("Running on line: |" + input + "|");
      StringBuilder origText = new StringBuilder();
      T last = null;
      for (Tokenizer<T> tok = tokFactory.getTokenizer(new StringReader(input)); tok.hasNext(); ) {
        appendTextFrom(origText, last);
        last = tok.next();
      }
      appendTextFrom(origText, last);
      assertEquals("PTBTokenizer has wrong originalText", input, origText.toString());
    }
  }

  private static <T extends CoreLabel> void appendTextFrom(StringBuilder origText, T token) {
    if (token != null) {
      // System.err.println("|Before|OrigText|After| = |" + token.get(CoreAnnotations.BeforeAnnotation.class) +
      //         "|" + token.get(CoreAnnotations.OriginalTextAnnotation.class) + "|" + token.get(CoreAnnotations.AfterAnnotation.class) + "|");
      if (origText.length() == 0) {
        origText.append(token.get(CoreAnnotations.BeforeAnnotation.class));
      }
      origText.append(token.get(CoreAnnotations.OriginalTextAnnotation.class));
      origText.append(token.get(CoreAnnotations.AfterAnnotation.class));
    }
  }


  @SuppressWarnings("unchecked")
  @Test
  public void testPTBTokenizerGerman() {
    String[] sample = { "Das TV-Duell von Kanzlerin Merkel und SPD-Herausforderer Steinbrück war eher lahm - können es die Spitzenleute der kleinen Parteien besser? ",
            "Die erquickende Sicherheit und Festigkeit in der Bewegung, den Vorrat von Kraft, kann ja die Versammlung nicht fühlen, hören will sie sie nicht, also muß sie sie sehen; und die sehe man einmal in einem Paar spitzen Schultern, zylindrischen Schenkeln, oder leeren Ärmeln, oder lattenförmigen Beinen." };
    String[][] tokenized = {
            { "Das", "TV-Duell", "von", "Kanzlerin", "Merkel", "und", "SPD-Herausforderer", "Steinbrück", "war", "eher",
              "lahm", "-", "können", "es", "die", "Spitzenleute", "der", "kleinen", "Parteien", "besser", "?", },
            {"Die", "erquickende", "Sicherheit", "und", "Festigkeit", "in", "der", "Bewegung", ",", "den", "Vorrat", "von",
                    "Kraft", ",", "kann", "ja", "die", "Versammlung", "nicht", "fühlen", ",", "hören", "will", "sie", "sie",
                    "nicht", ",", "also", "muß", "sie", "sie", "sehen", ";", "und", "die", "sehe", "man", "einmal", "in", "einem",
                    "Paar", "spitzen", "Schultern", ",", "zylindrischen", "Schenkeln", ",", "oder", "leeren", "Ärmeln", ",",
                    "oder", "lattenförmigen", "Beinen", "."
            }
    };
    TreebankLanguagePack tlp = new NegraPennLanguagePack();
    TokenizerFactory tokFactory = tlp.getTokenizerFactory();
    runOnTwoArrays(tokFactory, sample, tokenized);
  }

  private final String[] mtInputs = {
    "Enter an option [?/Current]:{1}",
    "for example, {1}http://www.autodesk.com{2}, or a path",
    "enter {3}@{4} at the Of prompt.",
    "{1}block name={2}",
    "1202-03-04 5:32:56 2004-03-04T18:32:56",
    "20°C is 68°F because 0℃ is 32℉",
    "a.jpg a-b.jpg a.b.jpg a-b.jpg a_b.jpg a-b-c.jpg 0-1-2.jpg a-b/c-d_e.jpg a-b/c-9a9_9a.jpg\n",
    "#hashtag #Azərbaycanca #mûǁae #Čeština #日本語ハッシュタグ #1 #23 #Trump2016 @3 @acl_2016",
          "Sect. 793 of the Penal Code",
  };

  private final String[][] mtGold = {
    { "Enter", "an", "option", "-LSB-", "?", "/", "Current", "-RSB-", ":", "-LCB-", "1", "-RCB-" },
    { "for", "example", ",", "-LCB-", "1", "-RCB-", "http://www.autodesk.com", "-LCB-", "2", "-RCB-", ",", "or", "a", "path" },
    { "enter", "-LCB-", "3", "-RCB-", "@", "-LCB-", "4", "-RCB-", "at", "the", "Of", "prompt", "." },
    { "-LCB-", "1", "-RCB-", "block", "name", "=", "-LCB-", "2", "-RCB-" },
    { "1202-03-04", "5:32:56", "2004-03-04T18:32:56" },
    { "20", "°C", "is", "68", "°F", "because", "0", "℃", "is", "32", "℉" },
    { "a.jpg", "a-b.jpg", "a.b.jpg", "a-b.jpg", "a_b.jpg", "a-b-c.jpg", "0-1-2.jpg", "a-b/c-d_e.jpg", "a-b/c-9a9_9a.jpg"},
    { "#hashtag", "#Azərbaycanca", "#mûǁae", "#Čeština", "#日本語ハッシュタグ", "#", "1", "#", "23", "#Trump2016", "@", "3", "@acl_2016" },
          { "Sect.", "793", "of", "the", "Penal", "Code" },
  };

  @Test
  public void testPTBTokenizerMT() {
    TokenizerFactory<Word> tokFactory = PTBTokenizer.factory();
    runOnTwoArrays(tokFactory, mtInputs, mtGold);
  }

  private final String[] emojiInputs = {
          // The non-BMP Emoji end up being surrogate pair encoded in Java! This list includes a flag.
          "\uD83D\uDE09\uD83D\uDE00\uD83D\uDE02\uD83D\uDE0D\uD83E\uDD21\uD83C\uDDE6\uD83C\uDDFA\uD83C\uDF7A",
          // People with skin tones
          "\uD83D\uDC66\uD83C\uDFFB\uD83D\uDC67\uD83C\uDFFF",
          // A family with cheese
          "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\uD83E\uDDC0",
          // Some BMP emoji
          "\u00AE\u203C\u2198\u231A\u2328\u23F0\u2620\u26BD\u2705\u2757",
          // Choosing emoji vs. text presentation.
          "⚠⚠️⚠︎❤️❤",
          "¯\\_(ツ)_/¯"
  };

  private final String[][] emojiGold = {
          { "\uD83D\uDE09", "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83E\uDD21", "\uD83C\uDDE6\uD83C\uDDFA", "\uD83C\uDF7A" },
          { "\uD83D\uDC66\uD83C\uDFFB", "\uD83D\uDC67\uD83C\uDFFF" },
          { "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", "\uD83E\uDDC0" },
          { "\u00AE", "\u203C", "\u2198", "\u231A", "\u2328", "\u23F0", "\u2620", "\u26BD", "\u2705", "\u2757" },
          { "⚠", "⚠️", "⚠︎", "❤️", "❤"},
          { "¯\\_-LRB-ツ-RRB-_/¯" },
  };

  @Test
  public void testEmoji() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("invertible");
    runOnTwoArrays(tokFactory, emojiInputs, emojiGold);
    runAgainstOrig(tokFactory, emojiInputs);
  }

  private final String[] hyphenInputs = {
          // Text starting with BOM (should be deleted), words with soft hyphens and non-breaking space.
          "\uFEFFThis is hy\u00ADphen\u00ADated and non-breaking spaces: 3\u202F456\u202F473.89",
          // Test that some cp1252 that shouldn't be in file is normalized okay
          "\u0093I need \u008080.\u0094 \u0082And \u0085 dollars.\u0092"
  };

  private final String[][] hyphenGold = {
          { "This", "is", "hyphenated", "and", "non-breaking", "spaces", ":", "3456473.89" },
          { "``", "I", "need", "€", "80", ".", "''", "`", "And", "...", "dollars", ".", "'" }
  };

  @Test
  public void testHyphensAndBOM() {
    TokenizerFactory<CoreLabel> tokFactory = PTBTokenizer.coreLabelFactory("normalizeCurrency=false,invertible");
    runOnTwoArrays(tokFactory, hyphenInputs, hyphenGold);
    runAgainstOrig(tokFactory, hyphenInputs);
  }

}
