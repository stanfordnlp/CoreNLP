package edu.stanford.nlp.pipeline;

import junit.framework.TestCase;

import java.util.*;
import java.util.stream.*;

import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.*;

public class POSTaggerBenchmarkITest extends TestCase {

  public List<String> readInPOSData(String testDataFilePath) {
    List<String> sentences = IOUtils.linesFromFile(testDataFilePath);
    return sentences;
  }

  public List<CoreLabel> entryToTokensList(String entryLine, String tagDelimiter, boolean caseless) {
    String[] tokensAndTags = entryLine.split(" ");
    List<CoreLabel> tokensFromGold = new ArrayList<CoreLabel>();
    for (String tokenAndTag : tokensAndTags) {
      String[] tokenAndTagSplit = tokenAndTag.split(tagDelimiter);
      CoreLabel cl = new CoreLabel();
      String finalWord = tokenAndTagSplit[0];
      if (caseless)
        finalWord = finalWord.toLowerCase();
      cl.setWord(finalWord);
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
    runPOSTest(sentences, "_", englishPipeline, ENGLISH_TOKEN_ACCURACY, ENGLISH_SENTENCE_ACCURACY,
        "English", false);
  }

  public void testEnglishBiDirectionalPOSModelAccuracy() {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("pos.model",
        "edu/stanford/nlp/models/pos-tagger/english-bidirectional/english-bidirectional-distsim.tagger");
    StanfordCoreNLP englishBiDirectionalPipeline = new StanfordCoreNLP(props);
    String englishPOSTestPath = "/u/nlp/data/pos-tagger/english/test-wsj-22-24";
    List<String> sentences = readInPOSData(englishPOSTestPath);
    double ENGLISH_BIDIRECTIONAL_TOKEN_ACCURACY = .972;
    double ENGLISH_BIDIRECTIONAL_SENTENCE_ACCURACY = .564;
    runPOSTest(sentences, "_", englishBiDirectionalPipeline,
        ENGLISH_BIDIRECTIONAL_TOKEN_ACCURACY, ENGLISH_BIDIRECTIONAL_SENTENCE_ACCURACY,
        "English BiDirectional", false);
  }

  public void testEnglishCaselessPOSModelAccuracy() {
    // set up pipeline
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos");
    props.setProperty("tokenize.whitespace", "true");
    props.setProperty("pos.model",
        "edu/stanford/nlp/models/pos-tagger/english-caseless-left3words-distsim.tagger");
    StanfordCoreNLP englishBiDirectionalPipeline = new StanfordCoreNLP(props);
    String englishPOSTestPath = "/u/nlp/data/pos-tagger/english/test-wsj-22-24";
    List<String> sentences = readInPOSData(englishPOSTestPath);
    double ENGLISH_CASELESS_TOKEN_ACCURACY = .958;
    double ENGLISH_CASELESS_SENTENCE_ACCURACY = .462;
    runPOSTest(sentences, "_", englishBiDirectionalPipeline,
        ENGLISH_CASELESS_TOKEN_ACCURACY, ENGLISH_CASELESS_SENTENCE_ACCURACY,
        "English Caseless", true);
  }

  public void testChinesePOSModelAccuracy() {
    // set up pipeline
    Properties props = StringUtils.argsToProperties("-args", "StanfordCoreNLP-chinese.properties");
    props.setProperty("annotators", "tokenize,ssplit,pos");
    props.setProperty("tokenize.whitespace", "true");
    StanfordCoreNLP chinesePipeline = new StanfordCoreNLP(props);
    String chinesePOSTestPath = "/u/nlp/data/pos-tagger/chinese/ctb7.test";
    List<String> sentences = readInPOSData(chinesePOSTestPath);
    double CHINESE_TOKEN_ACCURACY = .974;
    double CHINESE_SENTENCE_ACCURACY = .577;
    runPOSTest(sentences, "#", chinesePipeline, CHINESE_TOKEN_ACCURACY, CHINESE_SENTENCE_ACCURACY,
        "Chinese", false);
  }

  public void testFrenchUDPOSModelAccuracy() {
    // set up pipeline
    Properties props = StringUtils.argsToProperties("-args", "StanfordCoreNLP-french.properties");
    props.setProperty("annotators", "tokenize,ssplit,pos");
    props.setProperty("tokenize.whitespace", "true");
    StanfordCoreNLP frenchPipeline = new StanfordCoreNLP(props);
    String frenchPOSTestPath = "/u/nlp/data/pos-tagger/french/fr-pos-ud-test.sentence_per_line";
    List<String> sentences = readInPOSData(frenchPOSTestPath);
    double FRENCH_UD_TOKEN_ACCURACY = .941;
    double FRENCH_UD_SENTENCE_ACCURACY = .375;
    runPOSTest(sentences, "_", frenchPipeline, FRENCH_UD_TOKEN_ACCURACY, FRENCH_UD_SENTENCE_ACCURACY,
        "FrenchUD", false);
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
    runPOSTest(sentences, "_", germanPipeline, GERMAN_TOKEN_ACCURACY, GERMAN_SENTENCE_ACCURACY,
        "German", false);
  }

  public void testSpanishUDPOSModelAccuracy() {
    // set up pipeline
    Properties props = StringUtils.argsToProperties("-args", "StanfordCoreNLP-spanish.properties");
    props.setProperty("annotators", "tokenize,ssplit,pos");
    props.setProperty("tokenize.whitespace", "true");
    StanfordCoreNLP spanishPipeline = new StanfordCoreNLP(props);
    String spanishPOSTestPath = "/u/nlp/data/pos-tagger/spanish/spanish-ud-ancora-test.sentence_per_line";
    List<String> sentences = readInPOSData(spanishPOSTestPath);
    double SPANISH_UD_TOKEN_ACCURACY = .5;
    double SPANISH_UD_SENTENCE_ACCURACY = .3;
    runPOSTest(sentences, "_", spanishPipeline, SPANISH_UD_TOKEN_ACCURACY, SPANISH_UD_SENTENCE_ACCURACY,
        "SpanishUD", false);
  }

  public void runPOSTest(List<String> sentences, String tagDelimiter, StanfordCoreNLP pipeline,
                         double tokenAccuracyThreshold, double avgSentenceAccuracyThreshold,
                         String language, boolean caseless) {
    int totalTokens = 0;
    int totalCorrectTokens = 0;
    int numSentences = 0;
    int correctSentences = 0;
    for (String sentence : sentences) {
      numSentences += 1;
      List<CoreLabel> inputSentenceTokens = entryToTokensList(sentence, tagDelimiter, caseless);
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
