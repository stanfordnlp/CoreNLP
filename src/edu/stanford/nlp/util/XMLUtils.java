package edu.stanford.nlp.util;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import edu.stanford.nlp.io.IOUtils;

/**
 * Provides some utilities for dealing with XML files, both by properly
 * parsing them and by using the methods of a desperate Perl hacker.
 *
 * @author Teg Grenager
 */
public class XMLUtils {

  private XMLUtils() {} // only static methods

  /**
   * Returns a non-validating XML parser. The parser ignores both DTDs and XSDs.
   *
   * @return An XML parser in the form of a DocumentBuilder
   */
  public static DocumentBuilder getXmlParser() {
    DocumentBuilder db = null;
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setValidating(false);

      //Disable DTD loading and validation
      //See http://stackoverflow.com/questions/155101/make-documentbuilder-parse-ignore-dtd-references
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
      dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

      db = dbf.newDocumentBuilder();
      db.setErrorHandler(new SAXErrorHandler());

    } catch (ParserConfigurationException e) {
      System.err.printf("%s: Unable to create XML parser\n", XMLUtils.class.getName());
      e.printStackTrace();

    } catch(UnsupportedOperationException e) {
      System.err.printf("%s: API error while setting up XML parser. Check your JAXP version\n", XMLUtils.class.getName());
      e.printStackTrace();
    }

    return db;
  }

  /**
   * Returns a validating XML parser given an XSD (not DTD!).
   *
   * @param schemaFile
   * @return An XML parser in the form of a DocumentBuilder
   */
  public static DocumentBuilder getValidatingXmlParser(File schemaFile) {
    DocumentBuilder db = null;
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      Schema schema = factory.newSchema(schemaFile);
      dbf.setSchema(schema);

      db = dbf.newDocumentBuilder();
      db.setErrorHandler(new SAXErrorHandler());

    } catch (ParserConfigurationException e) {
      System.err.printf("%s: Unable to create XML parser\n", XMLUtils.class.getName());
      e.printStackTrace();

    } catch (SAXException e) {
      System.err.printf("%s: XML parsing exception while loading schema %s\n", XMLUtils.class.getName(),schemaFile.getPath());
      e.printStackTrace();

    } catch(UnsupportedOperationException e) {
      System.err.printf("%s: API error while setting up XML parser. Check your JAXP version\n", XMLUtils.class.getName());
      e.printStackTrace();
    }

    return db;
  }

  /**
   * Block-level HTML tags that are rendered with surrounding line breaks.
   */
  public static final Set<String> breakingTags = Generics.newHashSet(Arrays.asList(new String[] {"blockquote", "br", "div", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "li", "ol", "p", "pre", "ul", "tr", "td"}));

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
    String text;
    String tag;
    int position = 0;
    try {
      do {
        text = XMLUtils.readUntilTag(r); // will do nothing if the next thing is a tag
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
        //        System.out.println(position + " got text: " + text);
        tag = XMLUtils.readTag(r);
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
        //        System.out.println(position + " got tag: " + tag);
      } while (true);
    } catch (IOException e) {
      System.err.println("Error reading string");
      e.printStackTrace();
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
      System.err.println("Failed to handle |" + s + "|");
    }
    return ret;
  }

  // Pattern is reentrant, going by the statement
  // "many matchers can share the same pattern"
  // on the Pattern javadoc.  Therefore, this should be
  // safe as a static final variable.
  static final Pattern xmlEscapingPattern = Pattern.compile("\\&.+?;");

  public static String unescapeStringForXML(String s) {
    StringBuilder result = new StringBuilder();
    Matcher m = xmlEscapingPattern.matcher(s);
    int end = 0;
    while (m.find()) {
      int start = m.start();
      result.append(s.substring(end, start));
      end = m.end();
      result.append(translate(s.substring(start, end)));
    }
    result.append(s.substring(end, s.length()));
    return result.toString();
  }

  private static char translate(String s) {
    if (s.equals("&amp;")) {
      return '&';
    } else if (s.equals("&lt;") || s.equals("&Lt;")) {
      return '<';
    } else if (s.equals("&gt;") || s.equals("&Gt;")) {
      return '>';
    } else if (s.equals("&quot;")) {
      return '\"';
    } else if (s.equals("&apos;")) {
      return '\'';
    } else if (s.equals("&ast;") || s.equals("&sharp;")) {
      return '-';
    } else if (s.equals("&equals;")) {
      return '=';
    } else if (s.equals("&nbsp;")) {
      return (char) 0xA0;
    } else if (s.equals("&iexcl;")) {
      return (char) 0xA1;
    } else if (s.equals("&cent;") || s.equals("&shilling;")) {
      return (char) 0xA2;
    } else if (s.equals("&pound;")) {
      return (char) 0xA3;
    } else if (s.equals("&curren;")) {
      return (char) 0xA4;
    } else if (s.equals("&yen;")) {
      return (char) 0xA5;
    } else if (s.equals("&brvbar;")) {
      return (char) 0xA6;
    } else if (s.equals("&sect;")) {
      return (char) 0xA7;
    } else if (s.equals("&uml;")) {
      return (char) 0xA8;
    } else if (s.equals("&copy;")) {
      return (char) 0xA9;
    } else if (s.equals("&ordf;")) {
      return (char) 0xAA;
    } else if (s.equals("&laquo; ")) {
      return (char) 0xAB;
    } else if (s.equals("&not;")) {
      return (char) 0xAC;
    } else if (s.equals("&shy; ")) {
      return (char) 0xAD;
    } else if (s.equals("&reg;")) {
      return (char) 0xAE;
    } else if (s.equals("&macr;")) {
      return (char) 0xAF;
    } else if (s.equals("&deg;")) {
      return (char) 0xB0;
    } else if (s.equals("&plusmn;")) {
      return (char) 0xB1;
    } else if (s.equals("&sup2;")) {
      return (char) 0xB2;
    } else if (s.equals("&sup3;")) {
      return (char) 0xB3;
    } else if (s.equals("&acute;")) {
      return (char) 0xB4;
    } else if (s.equals("&micro;")) {
      return (char) 0xB5;
    } else if (s.equals("&middot;")) {
      return (char) 0xB7;
    } else if (s.equals("&cedil;")) {
      return (char) 0xB8;
    } else if (s.equals("&sup1;")) {
      return (char) 0xB9;
    } else if (s.equals("&ordm;")) {
      return (char) 0xBA;
    } else if (s.equals("&raquo;")) {
      return (char) 0xBB;
    } else if (s.equals("&frac14; ")) {
      return (char) 0xBC;
    } else if (s.equals("&frac12;")) {
      return (char) 0xBD;
    } else if (s.equals("&frac34; ")) {
      return (char) 0xBE;
    } else if (s.equals("&iquest;")) {
      return (char) 0xBF;
    } else if (s.equals("&Agrave;")) {
      return (char) 0xC0;
    } else if (s.equals("&Aacute;")) {
      return (char) 0xC1;
    } else if (s.equals("&Acirc;")) {
      return (char) 0xC2;
    } else if (s.equals("&Atilde;")) {
      return (char) 0xC3;
    } else if (s.equals("&Auml;")) {
      return (char) 0xC4;
    } else if (s.equals("&Aring;")) {
      return (char) 0xC5;
    } else if (s.equals("&AElig;")) {
      return (char) 0xC6;
    } else if (s.equals("&Ccedil;")) {
      return (char) 0xC7;
    } else if (s.equals("&Egrave;")) {
      return (char) 0xC8;
    } else if (s.equals("&Eacute;")) {
      return (char) 0xC9;
    } else if (s.equals("&Ecirc;")) {
      return (char) 0xCA;
    } else if (s.equals("&Euml;")) {
      return (char) 0xCB;
    } else if (s.equals("&Igrave;")) {
      return (char) 0xCC;
    } else if (s.equals("&Iacute;")) {
      return (char) 0xCD;
    } else if (s.equals("&Icirc;")) {
      return (char) 0xCE;
    } else if (s.equals("&Iuml;")) {
      return (char) 0xCF;
    } else if (s.equals("&ETH;")) {
      return (char) 0xD0;
    } else if (s.equals("&Ntilde;")) {
      return (char) 0xD1;
    } else if (s.equals("&Ograve;")) {
      return (char) 0xD2;
    } else if (s.equals("&Oacute;")) {
      return (char) 0xD3;
    } else if (s.equals("&Ocirc;")) {
      return (char) 0xD4;
    } else if (s.equals("&Otilde;")) {
      return (char) 0xD5;
    } else if (s.equals("&Ouml;")) {
      return (char) 0xD6;
    } else if (s.equals("&times;")) {
      return (char) 0xD7;
    } else if (s.equals("&Oslash;")) {
      return (char) 0xD8;
    } else if (s.equals("&Ugrave;")) {
      return (char) 0xD9;
    } else if (s.equals("&Uacute;")) {
      return (char) 0xDA;
    } else if (s.equals("&Ucirc;")) {
      return (char) 0xDB;
    } else if (s.equals("&Uuml;")) {
      return (char) 0xDC;
    } else if (s.equals("&Yacute;")) {
      return (char) 0xDD;
    } else if (s.equals("&THORN;")) {
      return (char) 0xDE;
    } else if (s.equals("&szlig;")) {
      return (char) 0xDF;
    } else if (s.equals("&agrave;")) {
      return (char) 0xE0;
    } else if (s.equals("&aacute;")) {
      return (char) 0xE1;
    } else if (s.equals("&acirc;")) {
      return (char) 0xE2;
    } else if (s.equals("&atilde;")) {
      return (char) 0xE3;
    } else if (s.equals("&auml;")) {
      return (char) 0xE4;
    } else if (s.equals("&aring;")) {
      return (char) 0xE5;
    } else if (s.equals("&aelig;")) {
      return (char) 0xE6;
    } else if (s.equals("&ccedil;")) {
      return (char) 0xE7;
    } else if (s.equals("&egrave;")) {
      return (char) 0xE8;
    } else if (s.equals("&eacute;")) {
      return (char) 0xE9;
    } else if (s.equals("&ecirc;")) {
      return (char) 0xEA;
    } else if (s.equals("&euml; ")) {
      return (char) 0xEB;
    } else if (s.equals("&igrave;")) {
      return (char) 0xEC;
    } else if (s.equals("&iacute;")) {
      return (char) 0xED;
    } else if (s.equals("&icirc;")) {
      return (char) 0xEE;
    } else if (s.equals("&iuml;")) {
      return 0xEF;
    } else if (s.equals("&eth;")) {
      return (char) 0xF0;
    } else if (s.equals("&ntilde;")) {
      return (char) 0xF1;
    } else if (s.equals("&ograve;")) {
      return (char) 0xF2;
    } else if (s.equals("&oacute;")) {
      return (char) 0xF3;
    } else if (s.equals("&ocirc;")) {
      return (char) 0xF4;
    } else if (s.equals("&otilde;")) {
      return (char) 0xF5;
    } else if (s.equals("&ouml;")) {
      return (char) 0xF6;
    } else if (s.equals("&divide;")) {
      return (char) 0xF7;
    } else if (s.equals("&oslash;")) {
      return (char) 0xF8;
    } else if (s.equals("&ugrave;")) {
      return (char) 0xF9;
    } else if (s.equals("&uacute;")) {
      return (char) 0xFA;
    } else if (s.equals("&ucirc;")) {
      return (char) 0xFB;
    } else if (s.equals("&uuml;")) {
      return (char) 0xFC;
    } else if (s.equals("&yacute;")) {
      return (char) 0xFD;
    } else if (s.equals("&thorn;")) {
      return (char) 0xFE;
    } else if (s.equals("&yuml;")) {
      return (char) 0xFF;
    } else if (s.equals("&OElig;")) {
      return (char) 0x152;
    } else if (s.equals("&oelig;")) {
      return (char) 0x153;
    } else if (s.equals("&Scaron;")) {
      return (char) 0x160;
    } else if (s.equals("&scaron;")) {
      return (char) 0x161;
    } else if (s.equals("&Yuml;")) {
      return (char) 0x178;
    } else if (s.equals("&circ;")) {
      return (char) 0x2C6;
    } else if (s.equals("&tilde;")) {
      return (char) 0x2DC;
    } else if (s.equals("&lrm;")) {
      return (char) 0x200E;
    } else if (s.equals("&rlm;")) {
      return (char) 0x200F;
    } else if (s.equals("&ndash;")) {
      return (char) 0x2013;
    } else if (s.equals("&mdash;")) {
      return (char) 0x2014;
    } else if (s.equals("&lsquo;")) {
      return (char) 0x2018;
    } else if (s.equals("&rsquo;")) {
      return (char) 0x2019;
    } else if (s.equals("&sbquo;")) {
      return (char) 0x201A;
    } else if (s.equals("&ldquo;") || s.equals("&bquo;") || s.equals("&bq;")) {
      return (char) 0x201C;
    } else if (s.equals("&rdquo;") || s.equals("&equo;")) {
      return (char) 0X201D;
    } else if (s.equals("&bdquo;")) {
      return (char) 0x201E;
    } else if (s.equals("&sim;")) {
      return (char) 0x223C;
    } else if (s.equals("&radic;")) {
      return (char) 0x221A;
    } else if (s.equals("&le;")) {
      return (char) 0x2264;
    } else if (s.equals("&ge;")) {
      return (char) 0x2265;
    } else if (s.equals("&larr;")) {
      return (char) 0x2190;
    } else if (s.equals("&darr;")) {
      return (char) 0x2193;
    } else if (s.equals("&rarr;")) {
      return (char) 0x2192;
    } else if (s.equals("&hellip;")) {
      return (char) 0x2026;
    } else if (s.equals("&prime;")) {
      return (char) 0x2032;
    } else if (s.equals("&Prime;") || s.equals("&ins;")) {
      return (char) 0x2033;
    } else if (s.equals("&trade;")) {
      return (char) 0x2122;
    } else if (s.equals("&Alpha;") || s.equals("&Agr;")) {
      return (char) 0x391;
    } else if (s.equals("&Beta;") || s.equals("&Bgr;")) {
      return (char) 0x392;
    } else if (s.equals("&Gamma;") || s.equals("&Ggr;")) {
      return (char) 0x393;
    } else if (s.equals("&Delta;") || s.equals("&Dgr;")) {
      return (char) 0x394;
    } else if (s.equals("&Epsilon;") || s.equals("&Egr;")) {
      return (char) 0x395;
    } else if (s.equals("&Zeta;") || s.equals("&Zgr;")) {
      return (char) 0x396;
    } else if (s.equals("&Eta;")) {
      return (char) 0x397;
    } else if (s.equals("&Theta;") || s.equals("&THgr;")) {
      return (char) 0x398;
    } else if (s.equals("&Iota;") || s.equals("&Igr;")) {
      return (char) 0x399;
    } else if (s.equals("&Kappa;") || s.equals("&Kgr;")) {
      return (char) 0x39A;
    } else if (s.equals("&Lambda;") || s.equals("&Lgr;")) {
      return (char) 0x39B;
    } else if (s.equals("&Mu;") || s.equals("&Mgr;")) {
      return (char) 0x39C;
    } else if (s.equals("&Nu;") || s.equals("&Ngr;")) {
      return (char) 0x39D;
    } else if (s.equals("&Xi;") || s.equals("&Xgr;")) {
      return (char) 0x39E;
    } else if (s.equals("&Omicron;") || s.equals("&Ogr;")) {
      return (char) 0x39F;
    } else if (s.equals("&Pi;") || s.equals("&Pgr;")) {
      return (char) 0x3A0;
    } else if (s.equals("&Rho;") || s.equals("&Rgr;")) {
      return (char) 0x3A1;
    } else if (s.equals("&Sigma;") || s.equals("&Sgr;")) {
      return (char) 0x3A3;
    } else if (s.equals("&Tau;") || s.equals("&Tgr;")) {
      return (char) 0x3A4;
    } else if (s.equals("&Upsilon;") || s.equals("&Ugr;")) {
      return (char) 0x3A5;
    } else if (s.equals("&Phi;") || s.equals("&PHgr;")) {
      return (char) 0x3A6;
    } else if (s.equals("&Chi;") || s.equals("&KHgr;")) {
      return (char) 0x3A7;
    } else if (s.equals("&Psi;") || s.equals("&PSgr;")) {
      return (char) 0x3A8;
    } else if (s.equals("&Omega;") || s.equals("&OHgr;")) {
      return (char) 0x3A9;
    } else if (s.equals("&alpha;") || s.equals("&agr;")) {
      return (char) 0x3B1;
    } else if (s.equals("&beta;") || s.equals("&bgr;")) {
      return (char) 0x3B2;
    } else if (s.equals("&gamma;") || s.equals("&ggr;")) {
      return (char) 0x3B3;
    } else if (s.equals("&delta;") || s.equals("&dgr;")) {
      return (char) 0x3B4;
    } else if (s.equals("&epsilon;") || s.equals("&egr;")) {
      return (char) 0x3B5;
    } else if (s.equals("&zeta;") || s.equals("&zgr;")) {
      return (char) 0x3B6;
    } else if (s.equals("&eta;") || s.equals("&eegr;")) {
      return (char) 0x3B7;
    } else if (s.equals("&theta;") || s.equals("&thgr;")) {
      return (char) 0x3B8;
    } else if (s.equals("&iota;") || s.equals("&igr;")) {
      return (char) 0x3B9;
    } else if (s.equals("&kappa;") || s.equals("&kgr;")) {
      return (char) 0x3BA;
    } else if (s.equals("&lambda;") || s.equals("&lgr;")) {
      return (char) 0x3BB;
    } else if (s.equals("&mu;") || s.equals("&mgr;")) {
      return (char) 0x3BC;
    } else if (s.equals("&nu;") || s.equals("&ngr;")) {
      return (char) 0x3BD;
    } else if (s.equals("&xi;") || s.equals("&xgr;")) {
      return (char) 0x3BE;
    } else if (s.equals("&omicron;") || s.equals("&ogr;")) {
      return (char) 0x3BF;
    } else if (s.equals("&pi;") || s.equals("&pgr;")) {
      return (char) 0x3C0;
    } else if (s.equals("&rho;") || s.equals("&rgr;")) {
      return (char) 0x3C1;
    } else if (s.equals("&sigma;") || s.equals("&sgr;")) {
      return (char) 0x3C3;
    } else if (s.equals("&tau;") || s.equals("&tgr;")) {
      return (char) 0x3C4;
    } else if (s.equals("&upsilon;") || s.equals("&ugr;")) {
      return (char) 0x3C5;
    } else if (s.equals("&phi;") || s.equals("&phgr;")) {
      return (char) 0x3C6;
    } else if (s.equals("&chi;") || s.equals("&khgr;")) {
      return (char) 0x3C7;
    } else if (s.equals("&psi;") || s.equals("&psgr;")) {
      return (char) 0x3C8;
    } else if (s.equals("&omega;") || s.equals("&ohgr;")) {
      return (char) 0x3C9;
    } else if (s.equals("&bull;")) {
      return (char) 0x2022;
    } else if (s.equals("&percnt;")) {
      return '%';
    } else if (s.equals("&plus;")) {
      return '+';
    } else if (s.equals("&dash;")) {
      return '-';
    } else if (s.equals("&abreve;") || s.equals("&amacr;") || s.equals("&ape;") || s.equals("&aogon;") || s.equals("&aring;")) {
      return 'a';
    } else if (s.equals("&Amacr;")) {
      return 'A';
    } else if (s.equals("&cacute;") || s.equals("&ccaron;") || s.equals("&ccirc;")) {
      return 'c';
    } else if (s.equals("&Ccaron;")) {
      return 'C';
    } else if (s.equals("&dcaron;")) {
      return 'd';
    } else if (s.equals("&ecaron;") || s.equals("&emacr;") || s.equals("&eogon;")) {
      return 'e';
    } else if (s.equals("&Emacr;") || s.equals("&Ecaron;")) {
      return 'E';
    } else if (s.equals("&lacute;")) {
      return 'l';
    } else if (s.equals("&Lacute;")) {
      return 'L';
    } else if (s.equals("&nacute;") || s.equals("&ncaron;") || s.equals("&ncedil;")) {
      return 'n';
    } else if (s.equals("&rcaron;") || s.equals("&racute;")) {
      return 'r';
    } else if (s.equals("&Rcaron;")) {
      return 'R';
    } else if (s.equals("&omacr;")) {
      return 'o';
    } else if (s.equals("&imacr;")) {
      return 'i';
    } else if (s.equals("&sacute;") || s.equals("&scedil;") || s.equals("&scirc;")) {
      return 's';
    } else if (s.equals("&Sacute") || s.equals("&Scedil;")) {
      return 'S';
    } else if (s.equals("&tcaron;") || s.equals("&tcedil;")) {
      return 't';
    } else if (s.equals("&umacr;") || s.equals("&uring;")) {
      return 'u';
    } else if (s.equals("&wcirc;")) {
      return 'w';
    } else if (s.equals("&Ycirc;")) {
      return 'Y';
    } else if (s.equals("&ycirc;")) {
      return 'y';
    } else if (s.equals("&zcaron;") || s.equals("&zacute;")) {
      return 'z';
    } else if (s.equals("&Zcaron;")) {
      return 'Z';
    } else if (s.equals("&hearts;")) {
      return (char) 0x2665;
    } else if (s.equals("&infin;")) {
      return (char) 0x221E;
    } else if (s.equals("&dollar;")) {
      return '$';
    } else if (s.equals("&sub;") || s.equals("&lcub;")) {
      return (char) 0x2282;
    } else if (s.equals("&sup;") || s.equals("&rcub;")) {
      return (char) 0x2283;
    } else if (s.equals("&lsqb;")) {
      return '[';
    } else if (s.equals("&rsqb;")) {
      return ']';
    } else {
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
        //      System.out.println("got text: " + text);
        result.append(escapeXML(text));
        XMLTag tag = readAndParseTag(r);
        //      System.out.println("got tag: " + tag);
        if (tag == null) {
          break;
        }
        result.append(tag.toString());
      } while (true);
    } catch (IOException e) {
      System.err.println("Error reading string");
      e.printStackTrace();
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
    public String text;
    public String name;
    public Map<String,String> attributes;
    public boolean isEndTag;
    public boolean isSingleTag;

    /**
     * Assumes that String contains an XML tag.
     *
     * @param tag String to turn into an XMLTag object
     */
    public XMLTag(String tag) {
      if (tag == null || tag.length() == 0) {
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
              System.out.println(begin + " " + end);
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
  } // end static class XMLTag


  /**
   * Reads all text of the XML tag and returns it as a String.
   * Assumes that a '<' character has already been read.
   *
   * @param r The reader to read from
   * @return The String representing the tag, or null if one couldn't be read
   *         (i.e., EOF).  The returned item is a complete tag including angle
   *         brackets, such as <code>&lt;TXT&gt;</code>
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
    if (tagString == null || tagString.length() == 0) {
      return null;
    }
    if (tagString.charAt(0) != '<' ||
        tagString.charAt(tagString.length() - 1) != '>') {
      return null;
    }
    return new XMLTag(tagString);
  }

  public static Document readDocumentFromFile(String filename)
    throws Exception
  {
    InputSource in = new InputSource(new FileReader(filename));
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    DocumentBuilder db = factory.newDocumentBuilder();
    db.setErrorHandler(new SAXErrorHandler());
    return db.parse(in);
  }

  private static class SAXErrorHandler implements ErrorHandler {

    public static String makeBetterErrorString(String msg,
                                               SAXParseException ex) {
      StringBuilder sb = new StringBuilder(msg);
      sb.append(": ");
      String str = ex.getMessage();
      if (str.lastIndexOf(".") == str.length() - 1) {
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
      sb.append(".");
      return sb.toString();
    }

    public void warning(SAXParseException exception) {
      System.err.println(makeBetterErrorString("Warning", exception));
    }

    public void error(SAXParseException exception) {
      System.err.println(makeBetterErrorString("Error", exception));
    }

    public void fatalError(SAXParseException ex) throws SAXParseException {
      throw new SAXParseException(makeBetterErrorString("Fatal Error", ex), ex.getPublicId(), ex.getSystemId(), ex.getLineNumber(), ex.getColumnNumber());
      // throw new RuntimeException(makeBetterErrorString("Fatal Error", ex));
    }

  } // end class SAXErrorHandler

  public static Document readDocumentFromString(String s) throws Exception {
    InputSource in = new InputSource(new StringReader(s));
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    return factory.newDocumentBuilder().parse(in);
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
      while (tag.length() > 0) {
        readUntilTag(r);
        tag = readTag(r);
        if (tag.length() == 0) {
          break;
        }
        System.out.println("got tag=" + new XMLTag(tag));
      }
    }
  }

}
