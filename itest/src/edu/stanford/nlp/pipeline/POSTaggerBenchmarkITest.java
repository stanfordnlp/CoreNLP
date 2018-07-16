package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.*;
import java.util.stream.*;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.*;

public class POSTaggerBenchmarkITest extends TestCase {

  String TAG_DELIMITER = "_";

  public List<String> readInPOSData(String testDataFilePath) {
    List<String> sentences = IOUtils.linesFromFile(testDataFilePath);
    return sentences;
  }

  public List<CoreLabel> entryToTokensList(String entryLine) {
    String[] tokensAndTags = entryLine.split(" ");
    List<CoreLabel> tokensFromGold = new ArrayList<CoreLabel>();
    for (String tokenAndTag : tokensAndTags) {
      String[] tokenAndTagSplit = tokenAndTag.split(TAG_DELIMITER);
      CoreLabel cl = new CoreLabel();
      cl.setWord(tokenAndTagSplit[0]);
      cl.setTag(tokenAndTagSplit[1]);
      tokensFromGold.add(cl);
    }
    return tokensFromGold;
  }

  /** number of correct tokens and sentence length **/
  public HashMap<String, Integer> sentenceResult(StanfordCoreNLP pipeline,
                                              List<CoreLabel> goldSentenceTokens) throws RuntimeException {
    int numTokens = goldSentenceTokens.size();
    String inputSentenceText =
        goldSentenceTokens.stream().map(cl -> cl.word()).collect(Collectors.joining(" "));
    Annotation sentenceAnnotation = new Annotation(inputSentenceText);
    pipeline.annotate(sentenceAnnotation);
    if (sentenceAnnotation.get(CoreAnnotations.TokensAnnotation.class).size() != numTokens)
      throw new RuntimeException("Error!!  Mismatch between annotated sentence tokens size and gold tokens size!");
    int numCorrectlyTaggedTokens = 0;
    for (int i = 0 ; i < goldSentenceTokens.size() ; i++) {
      if (
          sentenceAnnotation.get(
              CoreAnnotations.TokensAnnotation.class).get(i).tag().equals(
                  goldSentenceTokens.get(i).tag())) {
        numCorrectlyTaggedTokens++;
      }
    }
    HashMap result = new HashMap<String,Integer>();
    result.put("correctTokens", numCorrectlyTaggedTokens);
    result.put("numSentenceTokens", numTokens);
    return result;
  }

  public void testEnglishPOSModelAccuracy() {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos");
    props.setProperty("tokenize.whitespace", "true");
    StanfordCoreNLP englishPipeline = new StanfordCoreNLP(props);
    String englishPOSTestPath = "/u/nlp/data/pos-tagger/english/test-wsj-22-24";
    List<String> sentences = readInPOSData(englishPOSTestPath);
    double ENGLISH_TOKEN_ACCURACY = .968;
    double ENGLISH_SENTENCE_ACCURACY = .516;
    runPOSTest(sentences, englishPipeline, ENGLISH_TOKEN_ACCURACY, ENGLISH_SENTENCE_ACCURACY, "English");
  }

  public void testGermanPOSModelAccuracy() {
    // set up pipeline
    Properties props = StringUtils.argsToProperties("-args", "StanfordCoreNLP-german.properties");
    props.setProperty("annotators", "tokenize,ssplit,pos");
    props.setProperty("tokenize.whitespace", "true");
    StanfordCoreNLP germanPipeline = new StanfordCoreNLP(props);
    String germanPOSTestPath = "/u/nlp/data/GermanACL08/negra/negra-corpus.test.utf8";
    List<String> sentences = readInPOSData(germanPOSTestPath);
    double GERMAN_TOKEN_ACCURACY = .934;
    double GERMAN_SENTENCE_ACCURACY = .511;
    runPOSTest(sentences, germanPipeline, GERMAN_TOKEN_ACCURACY, GERMAN_SENTENCE_ACCURACY, "German");
  }

  public void runPOSTest(List<String> sentences, StanfordCoreNLP pipeline,
                         double tokenAccuracyThreshold, double avgSentenceAccuracyThreshold, String language) {
    int totalTokens = 0;
    int totalCorrectTokens = 0;
    int numSentences = 0;
    int correctSentences = 0;
    for (String sentence : sentences) {
      numSentences += 1;
      List<CoreLabel> inputSentenceTokens = entryToTokensList(sentence);
      HashMap<String,Integer> result = sentenceResult(pipeline, inputSentenceTokens);
      totalTokens += result.get("numSentenceTokens");
      totalCorrectTokens += result.get("correctTokens");
      double currSentenceAccuracy = result.get("correctTokens")/((double) result.get("numSentenceTokens"));
      if (currSentenceAccuracy == 1.0)
        correctSentences += 1;
    }
    double tokenAccuracy = ((double) totalCorrectTokens)/((double) totalTokens);
    double sentenceAccuracy = ((double) correctSentences / ((double) numSentences));
    System.err.println("---");
    System.err.println(language);
    System.err.println("token accuracy: "+tokenAccuracy);
    assertTrue(tokenAccuracy >= tokenAccuracyThreshold);
    System.err.println("sentence accuracy: "+sentenceAccuracy);
    assertTrue(sentenceAccuracy >= avgSentenceAccuracyThreshold);
  }

}
