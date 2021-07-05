package edu.stanford.nlp.tagger.maxent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;

public class MaxentTaggerTest {

  @Test
  public void testGetXmlWords()   {
    CoreLabel[] words = new CoreLabel[8];
    words[0] = new CoreLabel(new String[] {"word", "pos", "lemma"}, new String[] { "This", "DT", "this"});
    words[1] = new CoreLabel(new String[] {"word", "pos", "lemma"}, new String[] { "is", "VBZ", "be"});
    words[2] = new CoreLabel(new String[] {"word", "pos", "lemma"}, new String[] { "an", "DT", "a"});
    words[3] = new CoreLabel(new String[] {"word", "pos", "lemma"}, new String[] { "\"", "``", "\""});
    words[4] = new CoreLabel(new String[] {"word", "pos", "lemma"}, new String[] { "awesome", "JJ", "awesome"});
    words[5] = new CoreLabel(new String[] {"word", "pos", "lemma"}, new String[] { "\"", "''", "\""});
    words[6] = new CoreLabel(new String[] {"word", "pos", "lemma"}, new String[] { "solution", "NN", "solution"});
    words[7] = new CoreLabel(new String[] {"word", "pos", "lemma"}, new String[] { ".", ".", "."});

    List<CoreLabel> sent = new ArrayList<>();
    Collections.addAll(sent, words);

    String xmlSent = MaxentTagger.getXMLWords(sent, 0, true);
    String answer = "<sentence id=\"0\">\n" +
            "  <word wid=\"0\" pos=\"DT\" lemma=\"this\">This</word>\n" +
            "  <word wid=\"1\" pos=\"VBZ\" lemma=\"be\">is</word>\n" +
            "  <word wid=\"2\" pos=\"DT\" lemma=\"a\">an</word>\n" +
            "  <word wid=\"3\" pos=\"``\" lemma=\"&quot;\">\"</word>\n" +
            "  <word wid=\"4\" pos=\"JJ\" lemma=\"awesome\">awesome</word>\n" +
            "  <word wid=\"5\" pos=\"''\" lemma=\"&quot;\">\"</word>\n" +
            "  <word wid=\"6\" pos=\"NN\" lemma=\"solution\">solution</word>\n" +
            "  <word wid=\"7\" pos=\".\" lemma=\".\">.</word>\n" +
            "</sentence>\n";
    Assert.assertEquals(answer, xmlSent);
  }
}
