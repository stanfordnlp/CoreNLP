package edu.stanford.nlp.pipeline;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.util.PropertiesUtils;

public class XMLOutputterTest {

  // For some reason the XML library used by XMLOutputter uses \r\n
  // even on Linux
  public static final String expectedSimple =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
    "<?xml-stylesheet href=\"CoreNLP-to-HTML.xsl\" type=\"text/xsl\"?>\r\n" +
    "<root>\r\n" +
    "  <document>\r\n" +
    "    <sentences>\r\n" +
    "      <sentence id=\"1\">\r\n" +
    "        <tokens>\r\n" +
    "          <token id=\"1\">\r\n" +
    "            <word>Unban</word>\r\n" +
    "            <CharacterOffsetBegin>0</CharacterOffsetBegin>\r\n" +
    "            <CharacterOffsetEnd>5</CharacterOffsetEnd>\r\n" +
    "          </token>\r\n" +
    "          <token id=\"2\">\r\n" +
    "            <word>mox</word>\r\n" +
    "            <CharacterOffsetBegin>6</CharacterOffsetBegin>\r\n" +
    "            <CharacterOffsetEnd>9</CharacterOffsetEnd>\r\n" +
    "          </token>\r\n" +
    "          <token id=\"3\">\r\n" +
    "            <word>opal</word>\r\n" +
    "            <CharacterOffsetBegin>10</CharacterOffsetBegin>\r\n" +
    "            <CharacterOffsetEnd>14</CharacterOffsetEnd>\r\n" +
    "          </token>\r\n" +
    "          <token id=\"4\">\r\n" +
    "            <word>!</word>\r\n" +
    "            <CharacterOffsetBegin>14</CharacterOffsetBegin>\r\n" +
    "            <CharacterOffsetEnd>15</CharacterOffsetEnd>\r\n" +
    "          </token>\r\n" +
    "        </tokens>\r\n" +
    "      </sentence>\r\n" +
    "    </sentences>\r\n" +
    "  </document>\r\n" +
    "</root>\r\n";

  public static final String expectedIncludeText =
    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
    "<?xml-stylesheet href=\"CoreNLP-to-HTML.xsl\" type=\"text/xsl\"?>\r\n" +
    "<root>\r\n" +
    "  <document>\r\n" +
    "    <text>Unban mox opal!</text>\r\n" +
    "    <sentences>\r\n" +
    "      <sentence id=\"1\">\r\n" +
    "        <tokens>\r\n" +
    "          <token id=\"1\">\r\n" +
    "            <word>Unban</word>\r\n" +
    "            <before/>\r\n" +
    "            <after> </after>\r\n" +
    "            <originalText>Unban</originalText>\r\n" +
    "            <CharacterOffsetBegin>0</CharacterOffsetBegin>\r\n" +
    "            <CharacterOffsetEnd>5</CharacterOffsetEnd>\r\n" +
    "          </token>\r\n" +
    "          <token id=\"2\">\r\n" +
    "            <word>mox</word>\r\n" +
    "            <before> </before>\r\n" +
    "            <after> </after>\r\n" +
    "            <originalText>mox</originalText>\r\n" +
    "            <CharacterOffsetBegin>6</CharacterOffsetBegin>\r\n" +
    "            <CharacterOffsetEnd>9</CharacterOffsetEnd>\r\n" +
    "          </token>\r\n" +
    "          <token id=\"3\">\r\n" +
    "            <word>opal</word>\r\n" +
    "            <before> </before>\r\n" +
    "            <after/>\r\n" +
    "            <originalText>opal</originalText>\r\n" +
    "            <CharacterOffsetBegin>10</CharacterOffsetBegin>\r\n" +
    "            <CharacterOffsetEnd>14</CharacterOffsetEnd>\r\n" +
    "          </token>\r\n" +
    "          <token id=\"4\">\r\n" +
    "            <word>!</word>\r\n" +
    "            <before/>\r\n" +
    "            <after/>\r\n" +
    "            <originalText>!</originalText>\r\n" +
    "            <CharacterOffsetBegin>14</CharacterOffsetBegin>\r\n" +
    "            <CharacterOffsetEnd>15</CharacterOffsetEnd>\r\n" +
    "          </token>\r\n" +
    "        </tokens>\r\n" +
    "      </sentence>\r\n" +
    "    </sentences>\r\n" +
    "  </document>\r\n" +
    "</root>\r\n";

  
  @Test
  public void testSimpleDocument() throws IOException {
    Annotation ann = new Annotation("Unban mox opal!");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize, ssplit"));
    pipeline.annotate(ann);
    String actual = new XMLOutputter().print(ann);
    Assert.assertEquals(expectedSimple, actual);
  }

  @Test
  public void testIncludeText() throws IOException {
    Annotation ann = new Annotation("Unban mox opal!");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(PropertiesUtils.asProperties("annotators", "tokenize, ssplit"));
    pipeline.annotate(ann);
    AnnotationOutputter.Options options = new AnnotationOutputter.Options(PropertiesUtils.asProperties("output.includeText", "true"));
    String actual = new XMLOutputter().print(ann, options);
    Assert.assertEquals(expectedIncludeText, actual);
  }
}
