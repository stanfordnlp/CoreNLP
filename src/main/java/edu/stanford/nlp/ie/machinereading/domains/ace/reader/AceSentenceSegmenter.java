package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import edu.stanford.nlp.ie.machinereading.common.DomReader;
import edu.stanford.nlp.ie.machinereading.domains.ace.reader.RobustTokenizer.WordToken;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.util.Generics;

public class AceSentenceSegmenter extends DomReader {
  // list of tokens which mark sentence boundaries
  private final static String[] sentenceFinalPunc = new String[] { ".", "!",
      "?" };
  private static Set<String> sentenceFinalPuncSet = Generics.newHashSet();

  static {
    // set up sentenceFinalPuncSet
    for (String aSentenceFinalPunc : sentenceFinalPunc) sentenceFinalPuncSet.add(aSentenceFinalPunc);
  }

  /**
   * @param filenamePrefix
   *          path to an ACE .sgm file (but not including the .sgm extension)
   */
  public static List<List<AceToken>> tokenizeAndSegmentSentences(String filenamePrefix)
      throws IOException, SAXException, ParserConfigurationException {

    List<List<AceToken>> sentences = new ArrayList<>();
    File inputFile = new File(filenamePrefix + AceDocument.ORIG_EXT);
    String input  =IOUtils.slurpFile(inputFile);

    // now we can split the text into tokens
    RobustTokenizer<Word> tokenizer = new RobustTokenizer<>(input);
    List<WordToken> tokenList = tokenizer.tokenizeToWordTokens();

    // and group the tokens into sentences
    ArrayList<AceToken> currentSentence = new ArrayList<>();
    int quoteCount = 0;
    for (int i = 0; i < tokenList.size(); i ++){
      WordToken token = tokenList.get(i);
      String tokenText = token.getWord();
      AceToken convertedToken = wordTokenToAceToken(token, sentences.size());

      // start a new sentence if we skipped 2+ lines (after datelines, etc.)
      // or we hit some SGML
      // if (token.getNewLineCount() > 1 || AceToken.isSgml(tokenText)) {
      if(AceToken.isSgml(tokenText)) {
        if (currentSentence.size() > 0) sentences.add(currentSentence);
        currentSentence = new ArrayList<>();
        quoteCount = 0;
      }

      currentSentence.add(convertedToken);
      if(tokenText.equals("\"")) quoteCount ++;

      // start a new sentence whenever we hit sentence-final punctuation
      if (sentenceFinalPuncSet.contains(tokenText)){
        // include quotes after EOS
        if(i < tokenList.size() - 1 && quoteCount % 2 == 1 && tokenList.get(i + 1).getWord().equals("\"")){
          AceToken quoteToken = wordTokenToAceToken(tokenList.get(i + 1), sentences.size());
          currentSentence.add(quoteToken);
          quoteCount ++;
          i ++;
        }
        if (currentSentence.size() > 0) sentences.add(currentSentence);
        currentSentence = new ArrayList<>();
        quoteCount = 0;
      }
      
      // start a new sentence when we hit an SGML tag
      else if(AceToken.isSgml(tokenText)) {
        if (currentSentence.size() > 0) sentences.add(currentSentence);
        currentSentence = new ArrayList<>();
        quoteCount = 0;
      }
    }
    
    return sentences;
  }

  public static AceToken wordTokenToAceToken(WordToken wordToken, int sentence) {
    return new AceToken(wordToken.getWord(), "", "", "", "", Integer
        .toString(wordToken.getStart()), Integer.toString(wordToken.getEnd()),
        sentence);
  }
  
  // simple testing code
  public static void main(String[] args) throws IOException, SAXException,
      ParserConfigurationException {
    String testFilename = "/home/mcclosky/data/ACE2005/English/wl/timex2norm/AGGRESSIVEVOICEDAILY_20041101.1144";
    // testFilename =
    // "/home/mcclosky/data/ACE2005/English/bc/timex2norm/CNN_CF_20030303.1900.02";
    // testFilename =
    // "/home/mcclosky/data/ACE2005/English/un/timex2norm/alt.atheism_20041104.2428";
    testFilename = "/home/mcclosky/data/ACE2005/English/nw/timex2norm/AFP_ENG_20030502.0614";
    
    List<List<AceToken>> sentences = tokenizeAndSegmentSentences(testFilename);
    for (List<AceToken> sentence : sentences)
      System.out.println("s: [" + sentence + "]");
  }
}