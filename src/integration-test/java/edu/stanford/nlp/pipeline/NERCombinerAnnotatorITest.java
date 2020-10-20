package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.NERClassifierCombiner;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.ColumnTabDocumentReaderWriter;
import edu.stanford.nlp.util.CoreMap;
import junit.framework.TestCase;

import java.io.StringReader;
import java.util.*;


/**
 * @author Angel Chang
 * @author John Bauer
 */
public class NERCombinerAnnotatorITest extends TestCase {

  public static final String NER_3CLASS = DefaultPaths.DEFAULT_NER_THREECLASS_MODEL;
  public static final String NER_7CLASS = DefaultPaths.DEFAULT_NER_MUC_MODEL;
  public static final String NER_MISCCLASS = DefaultPaths.DEFAULT_NER_CONLL_MODEL;

  private static NERCombinerAnnotator nerAnnotator = null;
  private static AnnotationPipeline unthreadedPipeline = null;
  private static AnnotationPipeline threaded4Pipeline = null;

  /**
   * Creates the tagger annotator if it isn't already created
   */
  @Override
  public void setUp()
    throws Exception
  {
    synchronized(NERCombinerAnnotatorITest.class) {
      if (nerAnnotator == null) {
        nerAnnotator = new NERCombinerAnnotator(false, NER_3CLASS, NER_7CLASS, NER_MISCCLASS);

        Properties props = new Properties();
        props.setProperty("ner.applyNumericClassifiers", "false");
        props.setProperty("ner.useSUTime", "false");
        props.setProperty("ner.model", NER_3CLASS);
        NERClassifierCombiner ner = NERClassifierCombiner.createNERClassifierCombiner("ner", props);
        NERCombinerAnnotator threaded4Annotator = new NERCombinerAnnotator(ner, false, 4, -1);

        threaded4Pipeline = new AnnotationPipeline();
        threaded4Pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
        threaded4Pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        threaded4Pipeline.addAnnotator(threaded4Annotator);

        NERCombinerAnnotator unthreadedAnnotator = new NERCombinerAnnotator(ner, false, 1, -1);
        unthreadedPipeline = new AnnotationPipeline();
        unthreadedPipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
        unthreadedPipeline.addAnnotator(new WordsToSentencesAnnotator(false));
        unthreadedPipeline.addAnnotator(unthreadedAnnotator);
      }
    }
  }

  public void testPipelineAnnotator() {
    Annotation document = new Annotation(TEXT);
    unthreadedPipeline.annotate(document);
    verifyAnswers(ANSWERS, document);
  }

  public void testThreadedAnnotator() {
    Annotation document = new Annotation(TEXT);
    threaded4Pipeline.annotate(document);
    verifyAnswers(ANSWERS, document);

    document = new Annotation(TEXT + TEXT + TEXT);
    threaded4Pipeline.annotate(document);
    verifyAnswers(ANSWERS, document);
  }

  public static void verifyAnswers(String[][] expected, Annotation document) {
    int sentenceIndex = 0;
    for (CoreMap sentence : document.get(CoreAnnotations.SentencesAnnotation.class)) {
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      assertEquals(expected[sentenceIndex % expected.length].length, tokens.size());

      int token = 0;
      for (CoreLabel word : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        assertEquals(expected[sentenceIndex % expected.length][token], word.ner());
        ++token;
      }
      ++sentenceIndex;
    }
  }

  public static final String TEXT = "John Bauer used to work at Stanford.  He worked there for 4 years.  John left in August 2014.  ";

  public static final String[][] ANSWERS = {
    { "PERSON", "PERSON", "O", "O", "O", "O", "ORGANIZATION", "O" },
    { "O", "O", "O", "O", "O", "O", "O" },
    { "PERSON", "O", "O", "O", "O", "O" }
  };

  private static Iterator<Annotation> getTestData(String inputString, boolean includeAnswer)
  {
    ColumnTabDocumentReaderWriter<CoreMap> colReader = new ColumnTabDocumentReaderWriter<>();
    if (includeAnswer) {
      colReader.init("word=0,tag=1,answer=2");
    } else {
      colReader.init("word=0,tag=1");
    }
    StringReader strReader = new StringReader(inputString);
    return colReader.getDocIterator(strReader);
  }

  private static void checkAnnotation(String goldInputString) throws Exception
  {
    // Use separate sets for gold and test since the NER annotator may write stuff to the AnswerAnnotation
    Iterator<Annotation> goldDocs = getTestData(goldInputString, true);
    Iterator<Annotation> testDocs = getTestData(goldInputString, false);
    int k = 0;
    while (testDocs.hasNext()) {
      Annotation goldDoc = goldDocs.next();
      Annotation testDoc = testDocs.next();
      nerAnnotator.annotate(testDoc);
      List<CoreLabel> goldTokens = goldDoc.get(CoreAnnotations.TokensAnnotation.class);
      List<CoreLabel> testTokens = testDoc.get(CoreAnnotations.TokensAnnotation.class);
      assertEquals("token number", goldTokens.size(), testTokens.size());
      for (int i = 0; i < goldTokens.size(); i++) {
        CoreLabel goldToken = goldTokens.get(i);
        CoreLabel testToken = testTokens.get(i);
        //System.err.println("POS: " + testToken.get(CoreAnnotations.PartOfSpeechAnnotation.class));
        String goldNer = goldToken.get(CoreAnnotations.AnswerAnnotation.class);
        String testNer = testToken.get(CoreAnnotations.NamedEntityTagAnnotation.class);
        //System.err.println("Ner tag for token " + i + " doc " + k +", GOLD: " + goldNer + ", TEST:" + testNer);
        assertEquals("Ner tag for token " + i + " (\"" + testToken.word() + "\") doc " + k, goldNer, testNer);
      }
      k++;
    }

  }

  public void testCombinedAnnotation() throws Exception
  {
    StringBuilder sb = new StringBuilder();
    sb.append("EU\tNNP\tORGANIZATION\n");
    sb.append("rejects\tVBZ\tO\n");
    sb.append("German\tNNP\tNATIONALITY\n");
    sb.append("call\tNN\tO\n");
    sb.append("to\tTO\tO\n");
    sb.append("boycott\tVB\tO\n");
    sb.append("British\tNNP\tNATIONALITY\n");
    sb.append("lamb\tNN\tO\n");
    sb.append(".\t.\tO\n");
    sb.append("Peter\tNNP\tPERSON\n");
    sb.append("Blackburn\tNNP\tPERSON\n");
    sb.append("BRUSSELS\tNNP\tCITY\n");
    sb.append("1996-08-22\tCD\tDATE\n");
    sb.append("It\tPRP\tO\n");
    sb.append("is\tVBZ\tO\n");
    sb.append("bright\tJJ\tO\n");
    sb.append("during\tIN\tO\n");
    sb.append("the\tDT\tDATE\n");
    sb.append("day\tNN\tDATE\n");
    sb.append(".\t.\tO\n");
    sb.append("It\tPRP\tO\n");
    sb.append("was\tVBZ\tO\n");
    sb.append("2\tJJ\tDURATION\n");
    sb.append("days\tIN\tDURATION\n");
    sb.append("before\tDT\tO\n");
    sb.append("the\tNN\tO\n");
    sb.append("meeting\tNN\tO\n");
    sb.append(".\t.\tO\n");

    checkAnnotation(sb.toString());
  }

  /** Basic test to check if statisticalOnly option is working properly **/
  public void testStatisticalOnlyOption() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.put("ssplit.isOneSentence", "true");
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz");
    props.setProperty("ner.statisticalOnly", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    List<String> examples = Arrays.asList("Joe Smith lives in California.", "He has 3 dogs.",
        "The party is on July 4th, 2019.");
    List<List<String>> goldNERTags = Arrays.asList(Arrays.asList("PERSON", "PERSON", "O", "O", "LOCATION", "O"),
        Arrays.asList("O", "O", "O", "O", "O"),
        Arrays.asList("O", "O", "O", "O", "O", "O", "O", "O", "O"));
    for (int i = 0 ; i < examples.size() ; i++) {
      CoreDocument doc = new CoreDocument(pipeline.process(examples.get(i)));
      assertEquals(goldNERTags.get(i), doc.sentences().get(0).nerTags());
    }
  }

  /** Basic test to check if statisticalOnly option is working properly **/
  public void testRulesOnlyOption() {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner");
    props.put("ssplit.isOneSentence", "true");
    props.setProperty("ner.model", "edu/stanford/nlp/models/ner/english.conll.4class.distsim.crf.ser.gz");
    props.setProperty("ner.rulesOnly", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    List<String> examples = Arrays.asList("Joe Smith lives in California.", "He has 3 dogs.",
        "The party is on July 4th, 2019.");
    List<List<String>> goldNERTags = Arrays.asList(Arrays.asList("O", "O", "O", "O", "STATE_OR_PROVINCE", "O"),
        Arrays.asList("O", "O", "NUMBER", "O", "O"),
        Arrays.asList("O", "O", "O", "O", "DATE", "DATE", "DATE", "DATE", "O"));
    for (int i = 0 ; i < examples.size() ; i++) {
      CoreDocument doc = new CoreDocument(pipeline.process(examples.get(i)));
      assertEquals(goldNERTags.get(i), doc.sentences().get(0).nerTags());
    }
  }

}
