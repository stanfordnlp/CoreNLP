package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ie.machinereading.structure.MachineReadingAnnotations;
import edu.stanford.nlp.ie.machinereading.structure.RelationMention;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import org.junit.Assert;
import junit.framework.TestCase;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class StanfordCoreNLPITest extends TestCase {

  public void testRequires() throws Exception {
    Properties props = new Properties();
    try {
      // Put the lemma before pos and you have a problem
      props.setProperty("annotators", "tokenize,ssplit,lemma,pos,ner,parse");
      new StanfordCoreNLP(props);
      throw new RuntimeException("Should have thrown an exception");
    } catch (IllegalArgumentException e) {
      // yay
    }

    // This should be okay: parse can take the place of pos
    props.setProperty("annotators", "tokenize,ssplit,parse,lemma,ner");
    new StanfordCoreNLP(props);
  }

  public void testRequiresForCoref() throws Exception {
    Properties props = new Properties();
    try {
      // Put the lemma before pos and you have a problem
      props.setProperty("annotators", "tokenize,ssplit,lemma,pos,ner,coref");
      new StanfordCoreNLP(props);
      throw new RuntimeException("Should have thrown an exception");
    } catch (IllegalArgumentException e) {
      // yay
    }

    // This should be okay: parse can take the place of pos
    props.setProperty("annotators", "tokenize,ssplit,parse,lemma,ner");
    new StanfordCoreNLP(props);
  }

  public void test() throws Exception {
    // create a properties that enables all the annotators
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse");

    // run an annotation through the pipeline
    String text = "Dan Ramage is working for\nMicrosoft. He's in Seattle! \n";
    Annotation document = new Annotation(text);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);

    // check that tokens are present
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(tokens);
    Assert.assertEquals(12, tokens.size());

    // check that sentences are present
    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
    Assert.assertNotNull(sentences);
    Assert.assertEquals(2, sentences.size());

    // check that pos, lemma and ner and parses are present
    for (CoreMap sentence : sentences) {
      List<CoreLabel> sentenceTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      Assert.assertNotNull(sentenceTokens);
      for (CoreLabel token : sentenceTokens) {
        Assert.assertNotNull(token.get(CoreAnnotations.PartOfSpeechAnnotation.class));
        Assert.assertNotNull(token.get(CoreAnnotations.LemmaAnnotation.class));
        Assert.assertNotNull(token.get(CoreAnnotations.NamedEntityTagAnnotation.class));
      }

      // check for parse tree
      Assert.assertNotNull(sentence.get(TreeCoreAnnotations.TreeAnnotation.class));

      // check that dependency graph Labels have word()
      SemanticGraph deps = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
      for (IndexedWord vertex : deps.vertexSet()) {
        Assert.assertNotNull(vertex.word());
        Assert.assertEquals(vertex.word(), vertex.value());
      }
    }


    // test pretty print
    StringWriter stringWriter = new StringWriter();
    pipeline.prettyPrint(document, new PrintWriter(stringWriter));
    String result = stringWriter.getBuffer().toString();
    Assert.assertTrue("Tokens are wrong in " + result,
      StringUtils.find(result, "\\[Text=Dan .*PartOfSpeech=NNP Lemma=Dan NamedEntityTag=PERSON\\]"));
    Assert.assertTrue("Parses are wrong in " + result,
      result.contains("(NP (PRP He))"));
    Assert.assertTrue("Parses are wrong in " + result,
      result.contains("(VP (VBZ 's)"));
    Assert.assertTrue("Sentence header is wrong in " + result,
      result.contains("Sentence #1 (7 tokens)"));
    Assert.assertTrue("Dependencies are wrong in " + result,
      result.contains("nsubj(working-4, Ramage-2)"));

    // test XML
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    pipeline.xmlPrint(document, os);
    result = new String(os.toByteArray(), "UTF-8");
    Assert.assertTrue("XML header is wrong in " + result,
      result.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
    Assert.assertTrue("XML root is wrong in " + result,
      result.contains("<?xml-stylesheet href=\"CoreNLP-to-HTML.xsl\" type=\"text/xsl\"?>"));
    Assert.assertTrue("XML word info is wrong in " + result,
      StringUtils.find(result, "<token id=\"2\">\\s*" +
        "<word>Ramage</word>\\s*" +
        "<lemma>Ramage</lemma>\\s*" +
        "<CharacterOffsetBegin>4</CharacterOffsetBegin>\\s*" +
        "<CharacterOffsetEnd>10</CharacterOffsetEnd>\\s*" +
        "<POS>NNP</POS>\\s*" +
        "<NER>PERSON</NER>"));
    Assert.assertTrue("XML dependencies are wrong in " + result,
      StringUtils.find(result, "<dep type=\"compound\">\\s*<governor idx=\"2\">" +
        "Ramage</governor>\\s*<dependent idx=\"1\">Dan</dependent>\\s*</dep>"));
  }


  private static void checkNer(String message, String[][][] expected, CoreMap coremap, String coremapOutput) {
    List<CoreMap> sentences = coremap.get(CoreAnnotations.SentencesAnnotation.class);
    assertEquals(message + ": number of sentences for\n" + coremapOutput, expected.length, sentences.size());
    for (int i = 0; i < expected.length; i++) {
      CoreMap sentence = sentences.get(i);
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      assertEquals(message + ": number of tokens for sentence " + (i + 1) + "\n" + coremapOutput, expected[i].length, tokens.size());
      for (int j = 0; j < expected[i].length; j++) {
        String text = expected[i][j][0];
        String ner = expected[i][j][1];
        String debug = "sentence " + (i + 1) + ", token " + (j + 1);
        assertEquals(message + ": text mismatch for " + debug + "\n" + coremapOutput, text, tokens.get(j).word());
        assertEquals(message + ": ner mismatch for " + debug + "(" + tokens.get(j).word() + ")\n" + coremapOutput, ner, tokens.get(j).ner());
      }
    }
  }

  public void testRegexNer() throws Exception {
    // Check the regexner is integrated with the StanfordCoreNLP
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,regexner");
    props.setProperty("regexner.ignorecase", "true");  // Maybe ignorecase should be on by default...

    String text = "Barack Obama is the 44th President of the United States.  He is the first African American president.";
    Annotation document = new Annotation(text);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);

    StringWriter stringWriter = new StringWriter();
    pipeline.prettyPrint(document, new PrintWriter(stringWriter));
    String result = stringWriter.getBuffer().toString();

    // Check the named entity types
    String[][][] expected = {
      {
        {"Barack", "PERSON"},
        {"Obama", "PERSON"},
        {"is", "O"},
        {"the", "O"},
        {"44th", "ORDINAL"},
        {"President", "TITLE"},
        {"of", "O"},
        {"the", "O"},
        {"United", "COUNTRY"},
        {"States", "COUNTRY"},
        {".", "O"},
      },
      {
        {"He", "O"},
        {"is", "O"},
        {"the", "O"},
        {"first", "ORDINAL"},
        {"African", "NATIONALITY"},
        {"American", "NATIONALITY"},
        {"president", "TITLE"},
        {".", "O"},
      },
    };

    checkNer("testRegexNer", expected, document, result);
  }

  public void testRelationExtractor() throws Exception {
    // Check the regexner is integrated with the StanfordCoreNLP
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,relation");
    //props.setProperty("sup.relation.model", "/home/sonalg/javanlp/tmp/roth_relation_model_pipeline.ser");
    String text = "Barack Obama, a Yale professor, is president.";
    Annotation document = new Annotation(text);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);
    CoreMap sentence = document.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    List<RelationMention> rel = sentence.get(MachineReadingAnnotations.RelationMentionsAnnotation.class);
    assertEquals(rel.get(0).getType(), "Work_For");
//    StringWriter stringWriter = new StringWriter();
//    pipeline.prettyPrint(document, new PrintWriter(stringWriter));
//    String result = stringWriter.getBuffer().toString();
//    System.out.println(result);
  }


  /* This test no longer supported. Do not mess with AnnotatorPool outside of StanfordCoreNLP */
  /*
  public void testAnnotatorPool() throws Exception {
    AnnotatorPool pool = new AnnotatorPool();
    pool.register("tokenize", new Factory<Annotator>() {
      private static final long serialVersionUID = 1L;
      public Annotator create() {
        return new Tokenize();
      }
    });
    // make sure only one Tokenize is created even with multiple pipelines
    Properties properties = this.newProperties("annotators=tokenize");
    new StanfordCoreNLP(pool, properties);
    new StanfordCoreNLP(pool, properties);
    Assert.assertEquals(1, Tokenize.N);
  }

  private static class Tokenize implements Annotator {
    public static int N = 0;
    public Tokenize() {
      ++N;
    }
    public void annotate(Annotation annotation) {
    }
  }

  private Properties newProperties(String desc) {
    Properties properties = new Properties();
    for (String nameValue: desc.split("\\s*,\\s*")) {
      String[] nameValueArray = nameValue.split("\\s*=\\s*");
      if (nameValueArray.length != 2) {
        throw new IllegalArgumentException("invalid name=value string: " + nameValue);
      }
      properties.setProperty(nameValueArray[0], nameValueArray[1]);
    }
    return properties;
  }
  */
  
  /* This test no longer suppported. */
  /*public void testSerialization()
    throws Exception {
    // Test that an annotation can be serialized and deserialized

    StanfordCoreNLP pipeline = new StanfordCoreNLP();
    Annotation document = new Annotation("Stanford University is located in California. It is a great university.");
    pipeline.annotate(document);

    CoreMap sentence = document.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    SemanticGraph g = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    processSerialization(g);

    processSerialization(sentence.get(TreeCoreAnnotations.TreeAnnotation.class));
    processSerialization(sentence.get(CoreAnnotations.TokensAnnotation.class));
    processSerialization(sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class));
    processSerialization(sentence);

    Object processed = processSerialization(document);
    assertTrue(processed instanceof Annotation);
    Annotation newDocument = (Annotation) processed;
    assertEquals(document.get(CoreAnnotations.SentencesAnnotation.class).size(),
      newDocument.get(CoreAnnotations.SentencesAnnotation.class).size());
    for (int i = 0; i < document.get(CoreAnnotations.SentencesAnnotation.class).size(); ++i) {
      CoreMap oldSentence = document.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      CoreMap newSentence = newDocument.get(CoreAnnotations.SentencesAnnotation.class).get(0);
      assertEquals(oldSentence.get(TreeCoreAnnotations.TreeAnnotation.class),
        newSentence.get(TreeCoreAnnotations.TreeAnnotation.class));
      assertEquals(oldSentence.get(CoreAnnotations.TokensAnnotation.class),
        newSentence.get(CoreAnnotations.TokensAnnotation.class));
    }
    assertTrue(document.equals(newDocument));
  }*/

  private static Object processSerialization(Object input)
    throws Exception {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    ObjectOutputStream oout = new ObjectOutputStream(bout);
    oout.writeObject(input);
    oout.flush();
    oout.close();

    ByteArrayInputStream bin = new ByteArrayInputStream(bout.toByteArray());
    ObjectInputStream oin = new ObjectInputStream(bin);
    return oin.readObject();
  }

  public void testSentenceNewlines() {
    // create a properties that enables all the annotators
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos");
    props.setProperty("ssplit.isOneSentence", "true");

    // run an annotation through the pipeline
    String text = "At least a few female committee members are from Scandinavia. \n";
    Annotation document = new Annotation(text);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);

    // check that tokens are present
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(tokens);
    Assert.assertEquals("Wrong number of tokens: " + tokens, 11, tokens.size());

    // check that sentences are present
    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
    Assert.assertNotNull(sentences);
    Assert.assertEquals("Wrong number of sentences", 1, sentences.size());
  }

  public void testSentenceNewlinesTwo() {
    // create a properties that enables all the annotators
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize");

    // run an annotation through the pipeline
    String text = "At least a few female committee members\nare from Scandinavia.\n";
    Annotation document = new Annotation(text);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);

    // check that tokens are present
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(tokens);
    Assert.assertEquals("Wrong number of tokens: " + tokens, 11, tokens.size());
  }

  public void testSentenceNewlinesThree() {
    // create a properties that enables all the annotators
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos");

    // run an annotation through the pipeline
    String text = "At least a few female committee members\nare from Scandinavia.\n";
    Annotation document = new Annotation(text);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    pipeline.annotate(document);

    // check that tokens are present
    List<CoreLabel> tokens = document.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(tokens);
    Assert.assertEquals("Wrong number of tokens: " + tokens, 11, tokens.size());

    // check that sentences are present
    List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);
    Assert.assertNotNull(sentences);
    Assert.assertEquals("Wrong number of sentences", 1, sentences.size());
    CoreMap firstSentence = sentences.get(0);
    List<CoreLabel> sentTokens = firstSentence.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(sentTokens);
    Assert.assertEquals("Wrong number of sentTokens: " + sentTokens, 11, sentTokens.size());
  }

  private static void checkSUTimeAnnotation(String message,
                                     StanfordCoreNLP pipeline, String text,
                                     int nExpectedSentences, int nExpectedTokens,
                                     Map<Integer,String> expectedNormalizedNER) {
    Annotation doc = new Annotation(text);
    pipeline.annotate(doc);

    // check that sentences and tokens are present
    List<CoreMap> sentences = doc.get(CoreAnnotations.SentencesAnnotation.class);
    Assert.assertNotNull(sentences);
    Assert.assertEquals(message + ": number of sentences", nExpectedSentences, sentences.size());

    List<CoreLabel> tokens = doc.get(CoreAnnotations.TokensAnnotation.class);
    Assert.assertNotNull(tokens);
    Assert.assertEquals(message + ": number of tokens", nExpectedTokens, tokens.size());

    for (Map.Entry<Integer,String> kv : expectedNormalizedNER.entrySet()) {
      Assert.assertEquals(message + ": token " + kv.getKey(), kv.getValue(),
        tokens.get(kv.getKey()).get(CoreAnnotations.NormalizedNamedEntityTagAnnotation.class));
    }
  }

  public void testSUTimeProperty() {
    // Test that SUTime properties are passed through
    String text = "The date is 2001-10-02.  There is a meeting tomorrow.";
    int nExpectedSentences = 2;
    int nExpectedTokens = 11;

    // CoreNLP without properties
    StanfordCoreNLP pipeline1 = new StanfordCoreNLP();
    Map<Integer,String> expectedValues1 = new HashMap<>();
    expectedValues1.put(3, "2001-10-02");
    expectedValues1.put(9, "OFFSET P1D");
    checkSUTimeAnnotation("Default properties", pipeline1, text, nExpectedSentences, nExpectedTokens, expectedValues1);

    // CoreNLP with properties
    Properties props = new Properties();
    props.setProperty("sutime.searchForDocDate", "true");

    StanfordCoreNLP pipeline2 = new StanfordCoreNLP(props);
    Map<Integer,String> expectedValues2 = new HashMap<>();
    expectedValues2.put(3, "2001-10-02");
    expectedValues2.put(9, "2001-10-03");
    checkSUTimeAnnotation("With searchForDocDate", pipeline2, text, nExpectedSentences, nExpectedTokens, expectedValues2);
  }

}
