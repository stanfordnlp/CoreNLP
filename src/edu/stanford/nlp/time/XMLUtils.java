package edu.stanford.nlp.time;

import edu.stanford.nlp.io.StringOutputStream;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;

/**
 * XML Utility functions for use with dealing with Timex expressions
 *
 * @author Angel Chang
 */
public class XMLUtils {
  private static Document document = createDocument();

  public static String documentToString(Document document) {
    StringOutputStream s = new StringOutputStream();
    printNode(s, document, true, true);
    return s.toString();
  }

  public static String nodeToString(Node node, boolean prettyPrint) {
    StringOutputStream s = new StringOutputStream();
    printNode(s, node, prettyPrint, false);
    return s.toString();
  }

  public static void printNode(OutputStream out, Node node, boolean prettyPrint, boolean includeXmlDeclaration)
  {
    TransformerFactory tfactory = TransformerFactory.newInstance();
    Transformer serializer;
    try {
      serializer = tfactory.newTransformer();
      if (prettyPrint) {
        //Setup indenting to "pretty print"
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      }
      if (!includeXmlDeclaration) {
        serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      }

      DOMSource xmlSource = new DOMSource(node);
      StreamResult outputTarget = new StreamResult(out);
      serializer.transform(xmlSource, outputTarget);
    } catch (TransformerException e) {
      throw new RuntimeException(e);
    }
  }

  public static Document createDocument() {
    try {
      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
      Document doc = docBuilder.newDocument();
      return doc;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Text createTextNode(String text) {
    return document.createTextNode(text);
  }

  public static Element createElement(String tag) {
    return document.createElement(tag);
  }

  public static Element parseElement(String xml) {
    try {
      DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
      Document doc = docBuilder.parse(new ByteArrayInputStream(xml.getBytes()));
      return doc.getDocumentElement();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // Like element.getAttribute except returns null if attribute not present
  public static String getAttribute(Element element, String name) {
    Attr attr = element.getAttributeNode(name);
    return (attr != null)? attr.getValue(): null;
  }

  public static void removeChildren(Node e) {
    NodeList list = e.getChildNodes();
    for (int i = 0; i < list.getLength(); i++) {
      Node n = list.item(i);
      e.removeChild(n);
    }
  }
}
