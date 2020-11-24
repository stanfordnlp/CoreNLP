package edu.stanford.nlp.pipeline;

import java.util.*;
import java.util.stream.*;

import org.junit.Assert;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.ClassicCounter;

/**
 * Utilities for benchmarking tokenizers.
 */
public class TokenizerBenchmarkTestCase {

  public static class MWTTokenCharacterOffsetBeginAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  public static class MWTTokenCharacterOffsetEndAnnotation implements CoreAnnotation<Integer> {
    @Override
    public Class<Integer> getType() {
      return Integer.class;
    }
  }

  // path to eval CoNLL-U
  public String goldFilePath;
  // list of examples
  public List<TestExample> testExamples;
  // pipeline to use for test
  public StanfordCoreNLP pipeline;

  /** Nested class for holding test example info such as text and gold tokens. **/
  class TestExample {

    private final String sentenceID;
    private final String sentenceText;
    private final List<CoreLabel> goldTokensList;
    private List<CoreLabel> systemTokensList;

    int CONLL_U_TOKEN_START = 2;

    public TestExample(List<String> conllLines) {
      int LENGTH_OF_SENTENCE_ID_PREFIX = "# sent_id = ".length();
      sentenceID = conllLines.get(0).substring(LENGTH_OF_SENTENCE_ID_PREFIX);
      int LENGTH_OF_TEXT_PREFIX = "# text = ".length();
      sentenceText = conllLines.get(1).substring(LENGTH_OF_TEXT_PREFIX);
      goldTokensList = new ArrayList<>();
      int charBegin = 0;
      int charEnd = 0;
      // if a mwt line is encountered, the next currMWT tokens need to be special cased
      // give words in a mwt the character offsets of the original token
      int currMWT = 0;
      for (String conllLine : conllLines.subList(CONLL_U_TOKEN_START, conllLines.size())) {
        // ignore commented out lines
        if (conllLine.startsWith("#")) {
          continue;
        }
        if (conllLine.split("\t")[0].contains("-")) {
          String[] mwtRange = conllLine.split("\t")[0].split("-");
          currMWT = 1 + Integer.parseInt(mwtRange[1]) - Integer.parseInt(mwtRange[0]);
          charEnd = charBegin + conllLine.split("\t")[1].length();
        } else {
          String tokenText = conllLine.split("\t")[1];
          if (currMWT == 0) {
            charEnd = charBegin + tokenText.length();
          }
          goldTokensList.add(buildCoreLabel(tokenText, charBegin, charEnd));
          if (currMWT > 0)
            currMWT--;
          if (currMWT == 0)
            charBegin = charEnd;
        }
      }
      tokenizeSentenceText();
    }

    /** helper method to build a CoreLabel from String and offsets **/
    public CoreLabel buildCoreLabel(String word, int begin, int end) {
      CoreLabel token = new CoreLabel();
      token.setWord(word);
      token.setBeginPosition(begin);
      token.setEndPosition(end);
      return token;
    }

    /** getter for the sentence id **/
    public String sentenceID() {
      return sentenceID;
    }

    /** getter for the sentence text **/
    public String sentenceText() {
      return sentenceText;
    }

    /** getter for the list of gold tokens **/
    public List<CoreLabel> goldTokensList() {
      return goldTokensList;
    }

    /** return the merged string of all the gold tokens **/
    public String goldTokensString() {
      return String.join("",
              goldTokensList.stream().map(CoreLabel::word).collect(Collectors.joining()));
    }

    /** return the merged string of all the system token **/
    public String systemTokensString() {
      return String.join("",
              systemTokensList.stream().map(CoreLabel::word).collect(Collectors.joining()));
    }

    /** tokenize text with pipeline, populate systemTokensList **/
    public void tokenizeSentenceText() {
      systemTokensList = new ArrayList<>();
      CoreLabel currMWTToken = null;
      CoreDocument exampleTokensDoc = new CoreDocument(pipeline.process(sentenceText));
      for (CoreLabel tok : exampleTokensDoc.tokens()) {
        if (containedByMultiWordToken(tok)) {
          if (currMWTToken == null || !isMultiWordTokenOf(tok, currMWTToken)) {
            int charBegin =
                    systemTokensList.size() == 0 ?
                            0 : systemTokensList.get(systemTokensList.size()-1).endPosition();
            currMWTToken = placeholderMWTToken(tok, charBegin);
          }
          systemTokensList.add(buildCoreLabel(tok.word(), currMWTToken.beginPosition(), currMWTToken.endPosition()));
        } else {
          currMWTToken = null;
          int charBegin =
                  systemTokensList.size() == 0 ?
                          0 : systemTokensList.get(systemTokensList.size()-1).endPosition();
          systemTokensList.add(buildCoreLabel(tok.word(), charBegin, charBegin + tok.word().length()));
        }
      }
    }

    /** create a placeholder CoreLabel with the info of the original mwt token **/
    public CoreLabel placeholderMWTToken(CoreLabel containedToken, int beginPosition) {
      CoreLabel placeholderToken = new CoreLabel();
      placeholderToken.setWord(containedToken.get(CoreAnnotations.MWTTokenTextAnnotation.class));
      placeholderToken.setBeginPosition(beginPosition);
      placeholderToken.setEndPosition(beginPosition + placeholderToken.word().length());
      placeholderToken.set(TokenizerBenchmarkTestCase.MWTTokenCharacterOffsetBeginAnnotation.class,
              containedToken.beginPosition());
      placeholderToken.set(TokenizerBenchmarkTestCase.MWTTokenCharacterOffsetEndAnnotation.class,
              containedToken.endPosition());
      placeholderToken.setIsMWT(true);
      return placeholderToken;
    }

    /** check if a token is split off from a multi word token **/
    public boolean containedByMultiWordToken(CoreLabel tok) {
      return tok.get(CoreAnnotations.MWTTokenTextAnnotation.class) != null;
    }

    /** check if a token is a split off token of another **/
    public boolean isMultiWordTokenOf(CoreLabel splitToken, CoreLabel multiWordPlaceholderToken) {
      int mwtPlaceholderBegin = multiWordPlaceholderToken.get(
              TokenizerBenchmarkTestCase.MWTTokenCharacterOffsetBeginAnnotation.class
      );
      int mwtPlaceholderEnd = multiWordPlaceholderToken.get(
              TokenizerBenchmarkTestCase.MWTTokenCharacterOffsetEndAnnotation.class
      );
      return splitToken.get(CoreAnnotations.MWTTokenTextAnnotation.class).equals(multiWordPlaceholderToken.word())
              && mwtPlaceholderBegin <= splitToken.beginPosition()
              && mwtPlaceholderBegin <= splitToken.endPosition()
              && mwtPlaceholderEnd >= splitToken.beginPosition()
              && mwtPlaceholderEnd >= splitToken.endPosition();
    }

    /** return TP, FP, FN stats for this example **/
    public ClassicCounter<String> f1Stats() {
      ClassicCounter<String> f1Stats = new ClassicCounter<>();
      // match system tokens to gold tokens
      for (CoreLabel cl : systemTokensList) {
        boolean foundMatch = false;
        for (CoreLabel gl : goldTokensList) {
          if (cl.word().equals(gl.word())
                  && cl.beginPosition() == gl.beginPosition() && cl.endPosition() == gl.endPosition()) {
            foundMatch = true;
            break;
          }
        }
        if (foundMatch) {
          f1Stats.incrementCount("TP");
        } else {
          f1Stats.incrementCount("FP");
        }
      }
      f1Stats.setCount("FN", goldTokensList.size() - f1Stats.getCount("TP"));
      return f1Stats;
    }
  }

  /** load all tokenizer test examples **/
  public void loadTokenizerTestExamples() {
    List<String> allLines = IOUtils.linesFromFile(goldFilePath);
    if (allLines == null) {
      throw new RuntimeException("Could not read file " + goldFilePath);
    }
    testExamples = new ArrayList<>();
    List<String> currSentence = new ArrayList<>();
    for (String conllLine : allLines) {
      if (conllLine.trim().equals("")) {
        testExamples.add(new TokenizerBenchmarkTestCase.TestExample(currSentence));
        currSentence.clear();
      } else {
        currSentence.add(conllLine);
      }
    }
  }

  /** calculate F1 scores from stats **/
  public static ClassicCounter<String> f1Scores(ClassicCounter<String> f1Stats) {
    ClassicCounter<String> f1Scores = new ClassicCounter<>();
    f1Scores.setCount("precision",
            f1Stats.getCount("TP")/(f1Stats.getCount("TP") + f1Stats.getCount("FP")));
    f1Scores.setCount("recall",
            f1Stats.getCount("TP")/(f1Stats.getCount("TP") + f1Stats.getCount("FN")));
    f1Scores.setCount("f1",
            (2 * f1Scores.getCount("precision") * f1Scores.getCount("recall"))/
                    (f1Scores.getCount("precision") + f1Scores.getCount("recall")));
    return f1Scores;
  }

  /** run the test and display report **/
  public void runTest(String evalSet, String lang, double expectedF1) {
    loadTokenizerTestExamples();
    ClassicCounter<String> allF1Stats = new ClassicCounter<>();
    for (TokenizerBenchmarkTestCase.TestExample testExample : testExamples) {
      System.err.println("---");
      System.err.println("sentence id: "+testExample.sentenceID);
      System.err.println("sentence text: "+testExample.sentenceText);
      System.err.println("gold tokens: "+testExample.goldTokensList.stream()
              .map(CoreLabel::word).collect(Collectors.toList()));
      System.err.println("system tokens: "+testExample.systemTokensList.stream()
              .map(CoreLabel::word).collect(Collectors.toList()));
      System.err.println(testExample.f1Stats());
      allF1Stats.addAll(testExample.f1Stats());
    }
    ClassicCounter<String> f1Scores = f1Scores(allF1Stats);
    System.err.println("---");
    System.err.println("Tokenizer Benchmark");
    System.err.println("language: "+lang);
    System.err.println("eval set: "+evalSet);
    System.err.println("Precision: "+f1Scores.getCount("precision"));
    System.err.println("Recall: "+f1Scores.getCount("recall"));
    System.err.println("F1: "+f1Scores.getCount("f1"));
    Assert.assertTrue("Test failure: System F1 of " + f1Scores.getCount("f1") + " below expected value of " +
            expectedF1, f1Scores.getCount("f1") >= expectedF1);
  }

}
