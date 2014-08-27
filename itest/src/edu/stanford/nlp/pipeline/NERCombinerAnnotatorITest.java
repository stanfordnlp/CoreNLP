package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.ColumnTabDocumentReaderWriter;
import junit.framework.TestCase;

import java.io.StringReader;
import java.util.Iterator;
import java.util.List;


/** @author Angel Chang */
public class NERCombinerAnnotatorITest extends TestCase {

  static NERCombinerAnnotator nerAnnotator = null;
  public static final String NER_3CLASS = DefaultPaths.DEFAULT_NER_THREECLASS_MODEL;
  public static final String NER_7CLASS = DefaultPaths.DEFAULT_NER_MUC_MODEL;
  public static final String NER_MISCCLASS = DefaultPaths.DEFAULT_NER_CONLL_MODEL;

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
      }
    }
  }

  private static Iterator<Annotation> getTestData(String inputString, boolean includeAnswer)
  {
    ColumnTabDocumentReaderWriter colReader = new ColumnTabDocumentReaderWriter();
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
        System.err.println("POS: " + testToken.get(CoreAnnotations.PartOfSpeechAnnotation.class));
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
    sb.append("German\tNNP\tMISC\n");
    sb.append("call\tNN\tO\n");
    sb.append("to\tTO\tO\n");
    sb.append("boycott\tVB\tO\n");
    sb.append("British\tNNP\tMISC\n");
    sb.append("lamb\tNN\tO\n");
    sb.append(".\t.\tO\n");
    sb.append("Peter\tNNP\tPERSON\n");
    sb.append("Blackburn\tNNP\tPERSON\n");
    sb.append("BRUSSELS\tNNP\tLOCATION\n");
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

}