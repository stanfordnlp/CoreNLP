package edu.stanford.nlp.util;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * Provides some utilities for dealing with XML files, both by properly
 * parsing them and by using the methods of a desperate Perl hacker.
 *
 * @author Teg Grenager
 * @author Grace Muzny
 */
public class XMLUtils  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(XMLUtils.class);

  private XMLUtils() {} // only static methods

  public static DocumentBuilderFactory safeDocumentBuilderFactory() {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    try {
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      dbf.setFeature("http://apache.org/xml/features/dom/create-entity-ref-nodes", false);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    } catch (ParserConfigurationException e) {
      log.warn(e);
    }
    return dbf;
  }
    

  /**
   * Returns the text content of all nodes in the given file with the given tag.
   *
   * @return List of String text contents of tags.
   */
  public static List<String> getTextContentFromTagsFromFile(File f, String tag) {
    List<String> sents = Generics.newArrayList();
    try {
      sents = getTextContentFromTagsFromFileSAXException(f, tag);
    } catch (SAXException e) {
      log.warn(e);
    }
    return sents;
  }

  /**
   * Returns the text content of all nodes in the given file with the given tag.
   * If the text contents contains embedded tags, strips the embedded tags out
   * of the returned text. E.g., {@code <s>This is a <s>sentence</s> with embedded tags
   * </s>} would return the list containing ["This is a sentence with embedded
   * tags", "sentence"].
   *
   * @throws SAXException if tag doesn't exist in the file.
   * @return List of String text contents of tags.
   */
  private static List<String> getTextContentFromTagsFromFileSAXException(
          File f, String tag) throws SAXException {
    List<String> sents = Generics.newArrayList();
    try {
      DocumentBuilderFactory dbf = safeDocumentBuilderFactory();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(f);
      doc.getDocumentElement().normalize();

      NodeList nodeList=doc.getElementsByTagName(tag);
      for (int i = 0; i < nodeList.getLength(); i++) {
        // Get element
        Element element = (Element)nodeList.item(i);
        String raw = element.getTextContent();
        StringBuilder builtUp = new StringBuilder();
        boolean inTag = false;
        for (int j = 0; j < raw.length(); j++) {
          if (raw.charAt(j) == '<') {
            inTag = true;
          }
          if (!inTag) {
            builtUp.append(raw.charAt(j));
          }
          if (raw.charAt(j) == '>') {
            inTag = false;
          }
        }
        sents.add(builtUp.toString());
      }
    } catch (IOException | ParserConfigurationException e) {
      log.warn(e);
    }
    return sents;
  }


  /**
   * Returns the text content of all nodes in the given file with the given tag.
   *
   * @return List of String text contents of tags.
   */
  public static List<Element> getTagElementsFromFile(File f, String tag) {
    List<Element> sents = Generics.newArrayList();
    try {
      sents = getTagElementsFromFileSAXException(f, tag);
    } catch (SAXException e) {
      log.warn(e);
    }
    return sents;
  }

  /**
   * Returns the text content of all nodes in the given file with the given tag.
   * If the text contents contains embedded tags, strips the embedded tags out
   * of the returned text. E.g., {@code <s>This is a <s>sentence</s> with embedded tags
   * </s>} would return the list containing ["This is a sentence with embedded
   * tags", "sentence"].
   *
   * @throws SAXException if tag doesn't exist in the file.
   * @return List of String text contents of tags.
   */
  private static List<Element> getTagElementsFromFileSAXException(
          File f, String tag) throws SAXException {
    List<Element> sents = Generics.newArrayList();
    try {
      DocumentBuilderFactory dbf = safeDocumentBuilderFactory();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(f);
      doc.getDocumentElement().normalize();

      NodeList nodeList=doc.getElementsByTagName(tag);
      for (int i = 0; i < nodeList.getLength(); i++) {
        // Get element
        Element element = (Element)nodeList.item(i);
        sents.add(element);
      }
    } catch (IOException | ParserConfigurationException e) {
      log.warn(e);
    }
    return sents;
  }

  /**
   * Returns the elements in the given file with the given tag associated with
   * the text content of the two previous siblings and two next siblings.
   *
   * @return List of {@code Triple<String, Element, String>} Targeted elements surrounded
   * by the text content of the two previous siblings and two next siblings.
   */
  public static List<Triple<String, Element, String>> getTagElementTriplesFromFile(File f, String tag) {
    List<Triple<String, Element, String>> sents = Generics.newArrayList();
    try {
      sents = getTagElementTriplesFromFileSAXException(f, tag);
    } catch (SAXException e) {
      log.warn(e);
    }
    return sents;
  }

  /**
   * Returns the elements in the given file with the given tag associated with
   * the text content of the previous and next siblings up to max numIncludedSiblings.
   *
   * @return List of {@code Triple<String, Element, String>} Targeted elements surrounded
   * by the text content of the two previous siblings and two next siblings.
   */
  public static List<Triple<String, Element, String>> getTagElementTriplesFromFileNumBounded(File f,
                                                                                             String tag,
                                                                                             int num) {
    List<Triple<String, Element, String>> sents = Generics.newArrayList();
    try {
      sents = getTagElementTriplesFromFileNumBoundedSAXException(f, tag, num);
    } catch (SAXException e) {
      log.warn(e);
    }
    return sents;
  }

  /**
   * Returns the elements in the given file with the given tag associated with
   * the text content of the two previous siblings and two next siblings.
   *
   * @throws SAXException if tag doesn't exist in the file.
   * @return List of {@code Triple<String, Element, String>} Targeted elements surrounded
   * by the text content of the two previous siblings and two next siblings.
   */
  public static List<Triple<String, Element, String>> getTagElementTriplesFromFileSAXException(
      File f, String tag) throws SAXException {
    return  getTagElementTriplesFromFileNumBoundedSAXException(f, tag, 2);
  }

  /**
   * Returns the elements in the given file with the given tag associated with
   * the text content of the previous and next siblings up to max numIncludedSiblings.
   *
   * @throws SAXException if tag doesn't exist in the file.
   * @return List of {@code Triple<String, Element, String>} Targeted elements surrounded
   * by the text content of the two previous siblings and two next siblings.
   */
  public static List<Triple<String, Element, String>> getTagElementTriplesFromFileNumBoundedSAXException(
      File f, String tag, int numIncludedSiblings) throws SAXException {
    List<Triple<String, Element, String>> sents = Generics.newArrayList();
    try {
      DocumentBuilderFactory dbf = safeDocumentBuilderFactory();
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(f);
      doc.getDocumentElement().normalize();

      NodeList nodeList=doc.getElementsByTagName(tag);
      for (int i = 0; i < nodeList.getLength(); i++) {
        // Get element
        Node prevNode = nodeList.item(i).getPreviousSibling();
        String prev = "";
        int count = 0;
        while (prevNode != null && count <= numIncludedSiblings) {
          prev = prevNode.getTextContent() + prev;
          prevNode = prevNode.getPreviousSibling();
          count++;
        }

        Node nextNode = nodeList.item(i).getNextSibling();
        String next = "";
        count = 0;
        while (nextNode != null && count <= numIncludedSiblings) {
          next = next + nextNode.getTextContent();
          nextNode = nextNode.getNextSibling();
          count++;
        }
        Element element = (Element)nodeList.item(i);
        Triple<String, Element, String> t = new Triple<>(prev, element, next);
        sents.add(t);
      }
    } catch (IOException | ParserConfigurationException e) {
      log.warn(e);
    }
    return sents;
  }


  /**
   * Returns a non-validating XML parser. The parser ignores both DTDs and XSDs.
   *
   * @return An XML parser in the form of a DocumentBuilder
   */
  public static DocumentBuilder getXmlParser() {
    DocumentBuilder db = null;
    try {
      DocumentBuilderFactory dbf = safeDocumentBuilderFactory();
      dbf.setValidating(false);

      //Disable DTD loading and validation
      //See http://stackoverflow.com/questions/155101/make-documentbuilder-parse-ignore-dtd-references
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

      db = dbf.newDocumentBuilder();
      db.setErrorHandler(new SAXErrorHandler());

    } catch (ParserConfigurationException e) {
      log.warnf("%s: Unable to create XML parser\n", XMLUtils.class.getName());
      log.warn(e);

    } catch(UnsupportedOperationException e) {
      log.warnf("%s: API error while setting up XML parser. Check your JAXP version\n", XMLUtils.class.getName());
      log.warn(e);
    }

    return db;
  }

  /**
   * Returns a validating XML parser given an XSD (not DTD!).
   *
   * @param schemaFile File wit hXML schema
   * @return An XML parser in the form of a DocumentBuilder
   */
  public static DocumentBuilder getValidatingXmlParser(File schemaFile) {
    DocumentBuilder db = null;
    try {
      DocumentBuilderFactory dbf = safeDocumentBuilderFactory();

      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Schema schema = factory.newSchema(schemaFile);
      dbf.setSchema(schema);

      db = dbf.newDocumentBuilder();
      db.setErrorHandler(new SAXErrorHandler());

    } catch (ParserConfigurationException e) {
      log.warnf("%s: Unable to create XML parser\n", XMLUtils.class.getName());
      log.warn(e);

    } catch (SAXException e) {
      log.warnf("%s: XML parsing exception while loading schema %s\n", XMLUtils.class.getName(),schemaFile.getPath());
      log.warn(e);

    } catch(UnsupportedOperationException e) {
      log.warnf("%s: API error while setting up XML parser. Check your JAXP version\n", XMLUtils.class.getName());
      log.warn(e);
    }

    return db;
  }

  /**
   * Block-level HTML tags that are rendered with surrounding line breaks.
   */
  private static final Set<String> breakingTags = Generics.newHashSet(Arrays.asList(new String[] {"blockquote", "br", "div", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "li", "ol", "p", "pre", "ul", "tr", "td"}));

  /**
   * @param r       the reader to read the XML/HTML from
   * @param mapBack a List of Integers mapping the positions in the result buffer
   *                to positions in the original Reader, will be cleared on receipt
   * @return the String containing the resulting text
   */
  public static String stripTags(Reader r, List<Integer> mapBack, boolean markLineBreaks) {
    if (mapBack != null) {
      mapBack.clear(); // just in case it has something in it!
    }
    StringBuilder result = new StringBuilder();
    try {
      int position = 0;
      do {
        String text = XMLUtils.readUntilTag(r);
        if (text.length() > 0) {
          // add offsets to the map back
          for (int i = 0; i < text.length(); i++) {
            result.append(text.charAt(i));
            if (mapBack != null) {
              mapBack.add(Integer.valueOf(position + i));
            }
          }
          position += text.length();
        }
        //        System.err.println(position + " got text: " + text);
        String tag = XMLUtils.readTag(r);
        if (tag == null) {
          break;
        }
        if (markLineBreaks && XMLUtils.isBreaking(parseTag(tag))) {
          result.append("\n");
          if (mapBack != null) {
            mapBack.add(Integer.valueOf(-position));
          }
        }
        position += tag.length();
        //        System.err.println(position + " got tag: " + tag);
      } while (true);
    } catch (IOException e) {
      log.warn("Error reading string");
      log.warn(e);
    }
    return result.toString();
  }

  public static boolean isBreaking(String tag) {
    return breakingTags.contains(tag);
  }

  public static boolean isBreaking(XMLTag tag) {
    return breakingTags.contains(tag.name);
  }

  /**
   * Reads all text up to next XML tag and returns it as a String.
   *
   * @return the String of the text read, which may be empty.
   */
  public static String readUntilTag(Reader r) throws IOException {
    if (!r.ready()) {
      return "";
    }
    StringBuilder b = new StringBuilder();
    int c = r.read();
    while (c >= 0 && c != '<') {
      b.append((char) c);
      c = r.read();
    }
    return b.toString();
  }

  /**
   * @return the new XMLTag object, or null if couldn't be created
   */
  public static XMLTag readAndParseTag(Reader r) throws IOException {
    String s = readTag(r);
    if (s == null) {
      return null;
    }
    XMLTag ret = null;
    try {
      ret = new XMLTag(s);
    } catch (Exception e) {
      log.warn("Failed to handle |" + s + "|");
    }
    return ret;
  }

  // Pattern is reentrant, going by the statement "many matchers can share the same pattern"
  // on the Pattern javadoc.  Therefore, this should be safe as a static final variable.
  private static final Pattern xmlEscapingPattern = Pattern.compile("&.+?;");

  public static String unescapeStringForXML(String s) {
    StringBuilder result = new StringBuilder();
    Matcher m = xmlEscapingPattern.matcher(s);
    int end = 0;
    while (m.find()) {
      int start = m.start();
      result.append(s, end, start);
      end = m.end();
      result.append(translate(s.substring(start, end)));
    }
    result.append(s, end, s.length());
    return result.toString();
  }

  private static char translate(String s) {
    switch (s) {
      case "&amp;":
        return '&';
      case "&lt;":
      case "&Lt;":
        return '<';
      case "&gt;":
      case "&Gt;":
        return '>';
      case "&quot;":
        return '\"';
      case "&apos;":
        return '\'';
      case "&ast;":
        return '*';
      case "&sharp;":
        return '\u266F';
      case "&equals;":
        return '=';
      case "&nbsp;":
        return (char) 0xA0;
      case "&iexcl;":
        return (char) 0xA1;
      case "&cent;":
      case "&shilling;":
        return (char) 0xA2;
      case "&pound;":
        return (char) 0xA3;
      case "&curren;":
        return (char) 0xA4;
      case "&yen;":
        return (char) 0xA5;
      case "&brvbar;":
        return (char) 0xA6;
      case "&sect;":
        return (char) 0xA7;
      case "&uml;":
        return (char) 0xA8;
      case "&copy;":
        return (char) 0xA9;
      case "&ordf;":
        return (char) 0xAA;
      case "&laquo; ":
        return (char) 0xAB;
      case "&not;":
        return (char) 0xAC;
      case "&shy; ":
        return (char) 0xAD;
      case "&reg;":
        return (char) 0xAE;
      case "&macr;":
        return (char) 0xAF;
      case "&deg;":
        return (char) 0xB0;
      case "&plusmn;":
        return (char) 0xB1;
      case "&sup2;":
        return (char) 0xB2;
      case "&sup3;":
        return (char) 0xB3;
      case "&acute;":
        return (char) 0xB4;
      case "&micro;":
        return (char) 0xB5;
      case "&middot;":
        return (char) 0xB7;
      case "&cedil;":
        return (char) 0xB8;
      case "&sup1;":
        return (char) 0xB9;
      case "&ordm;":
        return (char) 0xBA;
      case "&raquo;":
        return (char) 0xBB;
      case "&frac14; ":
        return (char) 0xBC;
      case "&frac12;":
        return (char) 0xBD;
      case "&frac34; ":
        return (char) 0xBE;
      case "&iquest;":
        return (char) 0xBF;
      case "&Agrave;":
        return (char) 0xC0;
      case "&Aacute;":
        return (char) 0xC1;
      case "&Acirc;":
        return (char) 0xC2;
      case "&Atilde;":
        return (char) 0xC3;
      case "&Auml;":
        return (char) 0xC4;
      case "&Aring;":
        return (char) 0xC5;
      case "&AElig;":
        return (char) 0xC6;
      case "&Ccedil;":
        return (char) 0xC7;
      case "&Egrave;":
        return (char) 0xC8;
      case "&Eacute;":
        return (char) 0xC9;
      case "&Ecirc;":
        return (char) 0xCA;
      case "&Euml;":
        return (char) 0xCB;
      case "&Igrave;":
        return (char) 0xCC;
      case "&Iacute;":
        return (char) 0xCD;
      case "&Icirc;":
        return (char) 0xCE;
      case "&Iuml;":
        return (char) 0xCF;
      case "&ETH;":
        return (char) 0xD0;
      case "&Ntilde;":
        return (char) 0xD1;
      case "&Ograve;":
        return (char) 0xD2;
      case "&Oacute;":
        return (char) 0xD3;
      case "&Ocirc;":
        return (char) 0xD4;
      case "&Otilde;":
        return (char) 0xD5;
      case "&Ouml;":
        return (char) 0xD6;
      case "&times;":
        return (char) 0xD7;
      case "&Oslash;":
        return (char) 0xD8;
      case "&Ugrave;":
        return (char) 0xD9;
      case "&Uacute;":
        return (char) 0xDA;
      case "&Ucirc;":
        return (char) 0xDB;
      case "&Uuml;":
        return (char) 0xDC;
      case "&Yacute;":
        return (char) 0xDD;
      case "&THORN;":
        return (char) 0xDE;
      case "&szlig;":
        return (char) 0xDF;
      case "&agrave;":
        return (char) 0xE0;
      case "&aacute;":
        return (char) 0xE1;
      case "&acirc;":
        return (char) 0xE2;
      case "&atilde;":
        return (char) 0xE3;
      case "&auml;":
        return (char) 0xE4;
      case "&aring;":
        return (char) 0xE5;
      case "&aelig;":
        return (char) 0xE6;
      case "&ccedil;":
        return (char) 0xE7;
      case "&egrave;":
        return (char) 0xE8;
      case "&eacute;":
        return (char) 0xE9;
      case "&ecirc;":
        return (char) 0xEA;
      case "&euml; ":
        return (char) 0xEB;
      case "&igrave;":
        return (char) 0xEC;
      case "&iacute;":
        return (char) 0xED;
      case "&icirc;":
        return (char) 0xEE;
      case "&iuml;":
        return 0xEF;
      case "&eth;":
        return (char) 0xF0;
      case "&ntilde;":
        return (char) 0xF1;
      case "&ograve;":
        return (char) 0xF2;
      case "&oacute;":
        return (char) 0xF3;
      case "&ocirc;":
        return (char) 0xF4;
      case "&otilde;":
        return (char) 0xF5;
      case "&ouml;":
        return (char) 0xF6;
      case "&divide;":
        return (char) 0xF7;
      case "&oslash;":
        return (char) 0xF8;
      case "&ugrave;":
        return (char) 0xF9;
      case "&uacute;":
        return (char) 0xFA;
      case "&ucirc;":
        return (char) 0xFB;
      case "&uuml;":
        return (char) 0xFC;
      case "&yacute;":
        return (char) 0xFD;
      case "&thorn;":
        return (char) 0xFE;
      case "&yuml;":
        return (char) 0xFF;
      case "&OElig;":
        return (char) 0x152;
      case "&oelig;":
        return (char) 0x153;
      case "&Scaron;":
        return (char) 0x160;
      case "&scaron;":
        return (char) 0x161;
      case "&Yuml;":
        return (char) 0x178;
      case "&circ;":
        return (char) 0x2C6;
      case "&tilde;":
        return (char) 0x2DC;
      case "&lrm;":
        return (char) 0x200E;
      case "&rlm;":
        return (char) 0x200F;
      case "&ndash;":
        return (char) 0x2013;
      case "&mdash;":
        return (char) 0x2014;
      case "&lsquo;":
        return (char) 0x2018;
      case "&rsquo;":
        return (char) 0x2019;
      case "&sbquo;":
        return (char) 0x201A;
      case "&ldquo;":
      case "&bquo;":
      case "&bq;":
        return (char) 0x201C;
      case "&rdquo;":
      case "&equo;":
        return (char) 0X201D;
      case "&bdquo;":
        return (char) 0x201E;
      case "&sim;":
        return (char) 0x223C;
      case "&radic;":
        return (char) 0x221A;
      case "&le;":
        return (char) 0x2264;
      case "&ge;":
        return (char) 0x2265;
      case "&larr;":
        return (char) 0x2190;
      case "&darr;":
        return (char) 0x2193;
      case "&rarr;":
        return (char) 0x2192;
      case "&hellip;":
        return (char) 0x2026;
      case "&prime;":
        return (char) 0x2032;
      case "&Prime;":
      case "&ins;":
        return (char) 0x2033;
      case "&trade;":
        return (char) 0x2122;
      case "&Alpha;":
      case "&Agr;":
        return (char) 0x391;
      case "&Beta;":
      case "&Bgr;":
        return (char) 0x392;
      case "&Gamma;":
      case "&Ggr;":
        return (char) 0x393;
      case "&Delta;":
      case "&Dgr;":
        return (char) 0x394;
      case "&Epsilon;":
      case "&Egr;":
        return (char) 0x395;
      case "&Zeta;":
      case "&Zgr;":
        return (char) 0x396;
      case "&Eta;":
        return (char) 0x397;
      case "&Theta;":
      case "&THgr;":
        return (char) 0x398;
      case "&Iota;":
      case "&Igr;":
        return (char) 0x399;
      case "&Kappa;":
      case "&Kgr;":
        return (char) 0x39A;
      case "&Lambda;":
      case "&Lgr;":
        return (char) 0x39B;
      case "&Mu;":
      case "&Mgr;":
        return (char) 0x39C;
      case "&Nu;":
      case "&Ngr;":
        return (char) 0x39D;
      case "&Xi;":
      case "&Xgr;":
        return (char) 0x39E;
      case "&Omicron;":
      case "&Ogr;":
        return (char) 0x39F;
      case "&Pi;":
      case "&Pgr;":
        return (char) 0x3A0;
      case "&Rho;":
      case "&Rgr;":
        return (char) 0x3A1;
      case "&Sigma;":
      case "&Sgr;":
        return (char) 0x3A3;
      case "&Tau;":
      case "&Tgr;":
        return (char) 0x3A4;
      case "&Upsilon;":
      case "&Ugr;":
        return (char) 0x3A5;
      case "&Phi;":
      case "&PHgr;":
        return (char) 0x3A6;
      case "&Chi;":
      case "&KHgr;":
        return (char) 0x3A7;
      case "&Psi;":
      case "&PSgr;":
        return (char) 0x3A8;
      case "&Omega;":
      case "&OHgr;":
        return (char) 0x3A9;
      case "&alpha;":
      case "&agr;":
        return (char) 0x3B1;
      case "&beta;":
      case "&bgr;":
        return (char) 0x3B2;
      case "&gamma;":
      case "&ggr;":
        return (char) 0x3B3;
      case "&delta;":
      case "&dgr;":
        return (char) 0x3B4;
      case "&epsilon;":
      case "&egr;":
        return (char) 0x3B5;
      case "&zeta;":
      case "&zgr;":
        return (char) 0x3B6;
      case "&eta;":
      case "&eegr;":
        return (char) 0x3B7;
      case "&theta;":
      case "&thgr;":
        return (char) 0x3B8;
      case "&iota;":
      case "&igr;":
        return (char) 0x3B9;
      case "&kappa;":
      case "&kgr;":
        return (char) 0x3BA;
      case "&lambda;":
      case "&lgr;":
        return (char) 0x3BB;
      case "&mu;":
      case "&mgr;":
        return (char) 0x3BC;
      case "&nu;":
      case "&ngr;":
        return (char) 0x3BD;
      case "&xi;":
      case "&xgr;":
        return (char) 0x3BE;
      case "&omicron;":
      case "&ogr;":
        return (char) 0x3BF;
      case "&pi;":
      case "&pgr;":
        return (char) 0x3C0;
      case "&rho;":
      case "&rgr;":
        return (char) 0x3C1;
      case "&sigma;":
      case "&sgr;":
        return (char) 0x3C3;
      case "&tau;":
      case "&tgr;":
        return (char) 0x3C4;
      case "&upsilon;":
      case "&ugr;":
        return (char) 0x3C5;
      case "&phi;":
      case "&phgr;":
        return (char) 0x3C6;
      case "&chi;":
      case "&khgr;":
        return (char) 0x3C7;
      case "&psi;":
      case "&psgr;":
        return (char) 0x3C8;
      case "&omega;":
      case "&ohgr;":
        return (char) 0x3C9;
      case "&bull;":
        return (char) 0x2022;
      case "&percnt;":
        return '%';
      case "&plus;":
        return '+';
      case "&dash;":
        return '-';
      case "&abreve;":
      case "&amacr;":
      case "&ape;":
      case "&aogon;":
        return 'a';
      case "&Amacr;":
        return 'A';
      case "&cacute;":
      case "&ccaron;":
      case "&ccirc;":
        return 'c';
      case "&Ccaron;":
        return 'C';
      case "&dcaron;":
        return 'd';
      case "&ecaron;":
      case "&emacr;":
      case "&eogon;":
        return 'e';
      case "&Emacr;":
      case "&Ecaron;":
        return 'E';
      case "&lacute;":
        return 'l';
      case "&Lacute;":
        return 'L';
      case "&nacute;":
      case "&ncaron;":
      case "&ncedil;":
        return 'n';
      case "&rcaron;":
      case "&racute;":
        return 'r';
      case "&Rcaron;":
        return 'R';
      case "&omacr;":
        return 'o';
      case "&imacr;":
        return 'i';
      case "&sacute;":
      case "&scedil;":
      case "&scirc;":
        return 's';
      case "&Sacute":
      case "&Scedil;":
        return 'S';
      case "&tcaron;":
      case "&tcedil;":
        return 't';
      case "&umacr;":
      case "&uring;":
        return 'u';
      case "&wcirc;":
        return 'w';
      case "&Ycirc;":
        return 'Y';
      case "&ycirc;":
        return 'y';
      case "&zcaron;":
      case "&zacute;":
        return 'z';
      case "&Zcaron;":
        return 'Z';
      case "&hearts;":
        return (char) 0x2665;
      case "&infin;":
        return (char) 0x221E;
      case "&dollar;":
        return '$';
      case "&sub;":
      case "&lcub;":
        return (char) 0x2282;
      case "&sup;":
      case "&rcub;":
        return (char) 0x2283;
      case "&lsqb;":
        return '[';
      case "&rsqb;":
        return ']';
      default:
        return ' ';
    }
  }


  /** Returns a String in which all the XML special characters have been
   *  escaped. The resulting String is valid to print in an XML file as an
   *  attribute or element value in all circumstances.  (Note that it may
   *  escape characters that didn't need to be escaped.)
   *
   *  @param in The String to escape
   *  @return The escaped String
   */
  public static String escapeXML(String in) {
    int leng = in.length();
    StringBuilder sb = new StringBuilder(leng);
    for (int i = 0; i < leng; i++) {
      char c = in.charAt(i);
      if (c == '&') {
        sb.append("&amp;");
      } else if (c == '<') {
        sb.append("&lt;");
      } else if (c == '>') {
        sb.append("&gt;");
      } else if (c == '"') {
        sb.append("&quot;");
      } else if (c == '\'') {
        sb.append("&apos;");
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }


  /** Returns a String in which some the XML special characters have been
   *  escaped: just the ones that need escaping in an element content.
   *
   *  @param in The String to escape
   *  @return The escaped String
   */
  public static String escapeElementXML(String in) {
    int leng = in.length();
    StringBuilder sb = new StringBuilder(leng);
    for (int i = 0; i < leng; i++) {
      char c = in.charAt(i);
      if (c == '&') {
        sb.append("&amp;");
      } else if (c == '<') {
        sb.append("&lt;");
      } else if (c == '>') {
        sb.append("&gt;");
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }


  /** Returns a String in which some XML special characters have been
   *  escaped. This just escapes attribute value ones, assuming that
   *  you're going to quote with double quotes.
   *  That is, only " and & are escaped.
   *
   *  @param in The String to escape
   *  @return The escaped String
   */
  public static String escapeAttributeXML(String in) {
    int leng = in.length();
    StringBuilder sb = new StringBuilder(leng);
    for (int i = 0; i < leng; i++) {
      char c = in.charAt(i);
      if (c == '&') {
        sb.append("&amp;");
      } else if (c == '"') {
        sb.append("&quot;");
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }


  public static String escapeTextAroundXMLTags(String s) {
    StringBuilder result = new StringBuilder();
    Reader r = new StringReader(s);
    try {
      do {
        String text = readUntilTag(r);
        //      System.err.println("got text: " + text);
        result.append(escapeXML(text));
        XMLTag tag = readAndParseTag(r);
        //      System.err.println("got tag: " + tag);
        if (tag == null) {
          break;
        }
        result.append(tag);
      } while (true);
    } catch (IOException e) {
      log.warn("Error reading string");
      log.warn(e);
    }
    return result.toString();
  }

  /**
   * return either the first space or the first nbsp
   */
  public static int findSpace(String haystack, int begin) {
    int space = haystack.indexOf(' ', begin);
    int nbsp = haystack.indexOf('\u00A0', begin);
    if (space == -1 && nbsp == -1) {
      return -1;
    } else if (space >= 0 && nbsp >= 0) {
      return Math.min(space, nbsp);
    } else {
      // eg one is -1, and the other is >= 0
      return Math.max(space, nbsp);
    }
  }

  public static class XMLTag {

    /** Stores the complete string passed in as the tag on construction. */
    public String text;

    /** Stores the element name, such as "doc". */
    public String name;

    /** Stores attributes as a Map from keys to values. */
    public Map<String,String> attributes;

    /** Whether this is an ending tag or not. */
    public boolean isEndTag;

    /** Whether this is an empty element expressed as a single empty element tag like {@code <p/>}. */
    public boolean isSingleTag;

    /**
     * Assumes that String contains an XML tag.
     *
     * @param tag String to turn into an XMLTag object
     */
    public XMLTag(String tag) {
      if (tag == null || tag.isEmpty()) {
        throw new NullPointerException("Attempted to parse empty/null tag");
      }
      if (tag.charAt(0) != '<') {
        throw new IllegalArgumentException("Tag did not start with <");
      }
      if (tag.charAt(tag.length() - 1) != '>') {
        throw new IllegalArgumentException("Tag did not end with >");
      }
      text = tag;
      int begin = 1;
      if (tag.charAt(1) == '/') {
        begin = 2;
        isEndTag = true;
      } else {
        isEndTag = false;
      }
      int end = tag.length() - 1;
      if (tag.charAt(tag.length() - 2) == '/') {
        end = tag.length() - 2;
        isSingleTag = true;
      } else {
        isSingleTag = false;
      }
      tag = tag.substring(begin, end);
      attributes = Generics.newHashMap();
      begin = 0;
      end = findSpace(tag, 0);

      if (end < 0) {
        name = tag;
      } else {
        name = tag.substring(begin, end);
        do {
          begin = end + 1;
          while (begin < tag.length() && tag.charAt(begin) < 0x21) {
            begin++; // get rid of leading whitespace
          }
          if (begin == tag.length()) {
            break;
          }
          end = tag.indexOf('=', begin);
          if (end < 0) {
            String att = tag.substring(begin);
            attributes.put(att, "");
            break;
          }
          String att = tag.substring(begin, end).trim();
          begin = end + 1;
          String value = null;
          if (tag.length() > begin) {
            while (begin < tag.length() && tag.charAt(begin) < 0x21) {
              begin++;
            }
            if (begin < tag.length() && tag.charAt(begin) == '\"') {
              // get quoted expression
              begin++;
              end = tag.indexOf('\"', begin);
              if (end < 0) {
                break; // this is a problem
              }
              value = tag.substring(begin, end);
              end++;
            } else {
              // get unquoted expression
              end = findSpace(tag, begin);
              if (end < 0) {
                end = tag.length();
              }
//              System.err.println(begin + " " + end);
              value = tag.substring(begin, end);
            }
          }
          attributes.put(att, value);
        } while (end < tag.length() - 3);
      }
    }

    public String toString() {
      return text;
    }

    /**
     * Given a list of attributes, return the first one that is non-null
     */
    public String getFirstNonNullAttributeFromList(List<String> attributesList) {
      for (String attribute : attributesList) {
        if (attributes.get(attribute) != null) {
          return attributes.get(attribute);
        }
      }
      return null;
    }
  } // end static class XMLTag


  /**
   * Reads all text of the XML tag and returns it as a String.
   * Assumes that a '<' character has already been read.
   *
   * @param r The reader to read from
   * @return The String representing the tag, or null if one couldn't be read
   *         (i.e., EOF).  The returned item is a complete tag including angle
   *         brackets, such as {@code <TXT>}
   */
  public static String readTag(Reader r) throws IOException {
    if ( ! r.ready()) {
      return null;
    }
    StringBuilder b = new StringBuilder("<");
    int c = r.read();
    while (c >= 0) {
      b.append((char) c);
      if (c == '>') {
        break;
      }
      c = r.read();
    }
    if (b.length() == 1) {
      return null;
    }
    return b.toString();
  }

  public static XMLTag parseTag(String tagString) {
    if (tagString == null || tagString.isEmpty()) {
      return null;
    }
    if (tagString.charAt(0) != '<' ||
        tagString.charAt(tagString.length() - 1) != '>') {
      return null;
    }
    return new XMLTag(tagString);
  }

  public static Document readDocumentFromFile(String filename) throws ParserConfigurationException, SAXException {
    try {
      InputSource in = new InputSource(new FileReader(filename));
      DocumentBuilderFactory factory = safeDocumentBuilderFactory();

      factory.setNamespaceAware(false);
      DocumentBuilder db = factory.newDocumentBuilder();
      db.setErrorHandler(new SAXErrorHandler());

      return db.parse(in);
    } catch(IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  private static class SAXErrorHandler implements ErrorHandler {

    public static String makeBetterErrorString(String msg,
                                               SAXParseException ex) {
      StringBuilder sb = new StringBuilder(msg);
      sb.append(": ");
      String str = ex.getMessage();
      if (str.lastIndexOf('.') == str.length() - 1) {
        str = str.substring(0, str.length() - 1);
      }
      sb.append(str);
      sb.append(" at document line ").append(ex.getLineNumber());
      sb.append(", column ").append(ex.getColumnNumber());
      if (ex.getSystemId() != null) {
        sb.append(" in entity from systemID ").append(ex.getSystemId());
      } else if (ex.getPublicId() != null) {
        sb.append(" in entity from publicID ").append(ex.getPublicId());
      }
      sb.append('.');
      return sb.toString();
    }

    @Override
    public void warning(SAXParseException exception) {
      log.warn(makeBetterErrorString("Warning", exception));
    }

    @Override
    public void error(SAXParseException exception) {
      log.error(makeBetterErrorString("Error", exception));
    }

    @Override
    public void fatalError(SAXParseException ex) throws SAXParseException {
      throw new SAXParseException(makeBetterErrorString("Fatal Error", ex),
              ex.getPublicId(), ex.getSystemId(), ex.getLineNumber(), ex.getColumnNumber());
      // throw new RuntimeException(makeBetterErrorString("Fatal Error", ex));
    }

  } // end class SAXErrorHandler

  public static Document readDocumentFromString(String s) throws ParserConfigurationException, SAXException {
    InputSource in = new InputSource(new StringReader(s));
    DocumentBuilderFactory factory = safeDocumentBuilderFactory();
    factory.setNamespaceAware(false);
    try {
      return factory.newDocumentBuilder().parse(in);
    } catch(IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  /** Tests a few methods.
   *  If the first arg is -readDoc then this method tests
   *  readDocumentFromFile.
   *  Otherwise, it tests readTag/readUntilTag and slurpFile.
   */
  public static void main(String[] args) throws Exception {
    if (args[0].equals("-readDoc")) {
      Document doc = readDocumentFromFile(args[1]);
      System.out.println(doc);
    } else {
      String s = IOUtils.slurpFile(args[0]);
      Reader r = new StringReader(s);
      String tag = readTag(r);
      while (tag != null && ! tag.isEmpty()) {
        readUntilTag(r);
        tag = readTag(r);
        if (tag == null || tag.isEmpty()) {
          break;
        }
        System.out.println("got tag=" + new XMLTag(tag));
      }
    }
  }

}
