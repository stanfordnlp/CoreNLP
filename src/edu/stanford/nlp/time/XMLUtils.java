package edu.stanford.nlp.time;

import edu.stanford.nlp.io.StringOutputStream;
import static edu.stanford.nlp.util.XMLUtils.safeDocumentBuilderFactory;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * XML Utility functions for use with dealing with Timex expressions.
 *
 * @author Angel Chang
 */
public class XMLUtils {

  private static final Document document = createDocument();

  // Modern implementations of TransformerFactory (from JDK 7) are threadsafe, while a Transformer should be per thread.
  private static final TransformerFactory tFactory = TransformerFactory.newInstance();

  private XMLUtils() {} // static class

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

  public static void printNode(OutputStream out, Node node, boolean prettyPrint, boolean includeXmlDeclaration) {
    try {
      Transformer serializer = tFactory.newTransformer();
      if (prettyPrint) {
        //Set up indenting to "pretty print"
        serializer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      }
      if ( ! includeXmlDeclaration) {
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
      DocumentBuilderFactory dbFactory = safeDocumentBuilderFactory();
      DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
      return docBuilder.newDocument();
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
      DocumentBuilderFactory dbFactory = safeDocumentBuilderFactory();
      DocumentBuilder docBuilder = dbFactory.newDocumentBuilder();
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

  private static void getMatchingNodes(Node node, Pattern[] nodePath, int cur, List<Node> res) {
    if (cur < 0 || cur >= nodePath.length) return;
    boolean last = (cur == nodePath.length-1);
    Pattern pattern = nodePath[cur];
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node c = children.item(i);
      if (pattern.matcher(c.getNodeName()).matches()) {
        if (last) {
          res.add(c);
        } else {
          getMatchingNodes(c, nodePath, cur+1, res);
        }
      }
    }
  }

  /** @return A maybe empty but never null Node List for nodes matching the pattern. */
  public static List<Node> getNodes(Node node, Pattern... nodePath) {
    List<Node> res = new ArrayList<>();
    getMatchingNodes(node, nodePath, 0, res);
    return res;
  }

  public static String getNodeText(Node node, Pattern... nodePath) {
    List<Node> nodes = getNodes(node, nodePath);
    if (nodes.size() > 0) {
      return nodes.get(0).getTextContent();
    } else {
      return null;
    }
  }

  public static Node getNode(Node node, Pattern... nodePath) {
    List<Node> nodes = getNodes(node, nodePath);
    if (nodes.size() > 0) {
      return nodes.get(0);
    } else {
      return null;
    }
  }

  private static void getMatchingNodes(Node node, String[] nodePath, int cur, List<Node> res) {
    if (cur < 0 || cur >= nodePath.length) return;
    boolean last = (cur == nodePath.length-1);
    String name = nodePath[cur];
    if (node.hasChildNodes()) {
      NodeList children = node.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        Node c = children.item(i);
        if (name.equals(c.getNodeName())) {
          if (last) {
            res.add(c);
          } else {
            getMatchingNodes(c, nodePath, cur+1, res);
          }
        }
      }
    }
  }

  public static List<Node> getNodes(Node node, String... nodePath) {
    List<Node> res = new ArrayList<>();
    getMatchingNodes(node, nodePath, 0, res);
    return res;
  }

  public static List<String> getNodeTexts(Node node, String... nodePath) {
    List<Node> nodes = getNodes(node, nodePath);
    List<String> strings = new ArrayList<>(nodes.size());
    for (Node n : nodes) {
      strings.add(n.getTextContent());
    }
    return strings;
  }

  public static String getNodeText(Node node, String... nodePath) {
    List<Node> nodes = getNodes(node, nodePath);
    if (nodes.size() > 0) {
      return nodes.get(0).getTextContent();
    } else {
      return null;
    }
  }

  public static String getAttributeValue(Node node, String name) {
    Node attr = getAttribute(node, name);
    return (attr != null) ? attr.getNodeValue(): null;
  }

  public static Node getAttribute(Node node, String name) {
    return node.getAttributes().getNamedItem(name);
  }

  public static Node getNode(Node node, String... nodePath) {
    List<Node> nodes = getNodes(node, nodePath);
    if (nodes.size() > 0) {
      return nodes.get(0);
    } else {
      return null;
    }
  }

}
