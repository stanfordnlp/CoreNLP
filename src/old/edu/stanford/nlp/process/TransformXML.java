package old.edu.stanford.nlp.process;


import java.io.*;
import java.util.*;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import old.edu.stanford.nlp.util.Function;
import old.edu.stanford.nlp.io.IOUtils;
import old.edu.stanford.nlp.util.XMLUtils;


/**
 * Reads XML from an input file or stream and writes XML to an output
 * file or stream, while transforming text appearing inside specified
 * XML tags by applying a specified {@link Function
 * <code>Function</code>}.  See TransformXMLApplications for examples.
 * <i>Implementation note:</i> This is done using SAX2.
 *
 * @param <T> The type of the output of the Function (from String to T)
 * @author Bill MacCartney
 * @author Anna Rafferty (refactoring, making SAXInterface easy to extend elsewhere)
 */
public class TransformXML<T> {

  private final SAXParser saxParser;

  private InputStream inStream;

  private SAXInterface<T> saxInterface = new SAXInterface<T>();


  public static class SAXInterface<T> extends DefaultHandler {

    protected List<String> elementsToBeTransformed;
    protected boolean inElementToBeTransformed;
    protected StringBuffer textToBeTransformed;
    protected PrintWriter outWriter = new PrintWriter(System.out, true);
    protected Function<String,T> function;

    // Called at the beginning of each element.  If the tag is on the
    // designated list, set flag to remember that we're in an element
    // to be transformed.  In either case, echo tag.
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if (elementsToBeTransformed.contains(qName)) {
        inElementToBeTransformed = true;
      }
      // echo tag to outStream
      outWriter.print('<');
      outWriter.print(qName);
      for (int i = 0; i < attributes.getLength(); i++) {
        outWriter.print(' ');
        outWriter.print(attributes.getQName(i));
        outWriter.print("=\"");
        outWriter.print(XMLUtils.escapeXML(attributes.getValue(i)));
        outWriter.print('"');
      }
      outWriter.println('>');
    }

    // Called at the end of each element.  If the tag is on the
    // designated list, apply the designated {@link Function <code>Function</code>} to the
    // accumulated text and echo the the result.  In either case, echo
    // the closing tag.
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      // System.err.println("end element " + qName + "; function is " + function.getClass());
      // System.err.println("elementsToBeTransformed is " + elementsToBeTransformed);
      // System.err.println("textToBeTransformed is " + textToBeTransformed);
      String text = textToBeTransformed.toString().trim();
      if (text.length() > 0) {
        if (elementsToBeTransformed.contains(qName)) {
          text = function.apply(text).toString();
        }
        outWriter.println(XMLUtils.escapeXML(text));
      }
      outWriter.print("</");
      outWriter.print(qName);
      outWriter.println('>');
      clear();
    }

    // Accumulate characters in buffer of text to be transformed
    // (SAX may call this after each line break)
    @Override
    public void characters(char[] buf, int offset, int len) throws SAXException {
      // System.err.println("characters |" + new String(buf, offset, len) + "|");
      textToBeTransformed.append(buf, offset, len);
    }

    // Forget where we are in the document
    protected void clear() {
      inElementToBeTransformed = false;
      textToBeTransformed = new StringBuffer();
    }

  } // end static class SAXInterface


  public TransformXML() {
    try {
      saxParser = SAXParserFactory.newInstance().newSAXParser();
      saxInterface.elementsToBeTransformed = new ArrayList<String>();
      saxInterface.clear();
    } catch (Exception e) {
      System.err.println("Error configuring XML parser: " + e);
      throw new RuntimeException(e);
    }
  }


  // Read XML from input stream and invoke SAX methods on it
  private void parse() throws SAXException, IOException {
    saxInterface.clear();
    saxParser.parse(inStream, saxInterface);
  }

  /**
   * Read XML from the specified file and write XML to stdout,
   * while transforming text appearing inside the specified XML
   * tags by applying the specified {@link Function
   * <code>Function</code>}.  Note that the <code>Function</code>
   * you supply must be prepared to accept <code>String</code>s as
   * input; if your <code>Function</code> doesn't handle
   * <code>String</code>s, you need to write a wrapper for it that
   * does.
   *
   * @param tags an array of <code>String</code>s, each an XML tag
   *             within which the transformation should be applied
   * @param fn   the {@link Function <code>Function</code>} to apply
   * @param in   the <code>File</code> to read from
   */
  public void transformXML(String[] tags, Function<String,T> fn, File in) {
    InputStream ins = null;
    try {
      ins = new BufferedInputStream(new FileInputStream(in));
      transformXML(tags, fn, ins, System.out);
    } catch (Exception e) {
      System.err.println("Error reading file " + in + ": " + e);
      e.printStackTrace();
    } finally {
      IOUtils.closeIgnoringExceptions(ins);
    }
  }

  /**
   * Read XML from the specified file and write XML to specified file,
   * while transforming text appearing inside the specified XML tags
   * by applying the specified {@link Function <code>Function</code>}.
   * Note that the <code>Function</code> you supply must be
   * prepared to accept <code>String</code>s as input; if your
   * <code>Function</code> doesn't handle <code>String</code>s, you
   * need to write a wrapper for it that does.
   *
   * @param tags an array of <code>String</code>s, each an XML tag
   *             within which the transformation should be applied
   * @param fn   the {@link Function <code>Function</code>} to apply
   * @param in   the <code>File</code> to read from
   * @param out  the <code>File</code> to write to
   */
  public void transformXML(String[] tags, Function<String,T> fn, File in, File out) {
    InputStream ins = null;
    OutputStream outs = null;
    try {
      ins = new BufferedInputStream(new FileInputStream(in));
      outs = new BufferedOutputStream(new FileOutputStream(out));
      transformXML(tags, fn, ins, outs);
    } catch (Exception e) {
      System.err.println("Error reading file " + in + " or writing file " + out + ": " + e);
      e.printStackTrace();
    } finally {
      IOUtils.closeIgnoringExceptions(ins);
      IOUtils.closeIgnoringExceptions(outs);
    }
  }

  /**
   * Read XML from input stream and write XML to stdout, while
   * transforming text appearing inside the specified XML tags by
   * applying the specified {@link Function <code>Function</code>}.
   * Note that the <code>Function</code> you supply must be
   * prepared to accept <code>String</code>s as input; if your
   * <code>Function</code> doesn't handle <code>String</code>s, you
   * need to write a wrapper for it that does.
   *
   * @param tags an array of <code>String</code>s, each an XML tag
   *             within which the transformation should be applied
   * @param fn   the {@link Function <code>Function</code>} to apply
   * @param in   the <code>InputStream</code> to read from
   */
  public void transformXML(String[] tags, Function<String,T> fn, InputStream in) {
    transformXML(tags, fn, in, System.out);
  }

  /**
   * Read XML from input stream and write XML to output stream,
   * while transforming text appearing inside the specified XML tags
   * by applying the specified {@link Function <code>Function</code>}.
   * Note that the <code>Function</code> you supply must be
   * prepared to accept <code>String</code>s as input; if your
   * <code>Function</code> doesn't handle <code>String</code>s, you
   * need to write a wrapper for it that does.
   *
   * @param tags an array of <code>String</code>s, each an XML tag
   *             within which the transformation should be applied
   * @param fn   the {@link Function <code>Function</code>} to apply
   * @param in   the <code>InputStream</code> to read from
   * @param out  the <code>OutputStream</code> to write to
   */
  public void transformXML(String[] tags, Function<String,T> fn, InputStream in, OutputStream out) {
    transformXML(tags, fn, in, new OutputStreamWriter(out), saxInterface);
  }

  /**
   * Read XML from input stream and write XML to output stream,
   * while transforming text appearing inside the specified XML tags
   * by applying the specified {@link Function <code>Function</code>}.
   * Note that the <code>Function</code> you supply must be
   * prepared to accept <code>String</code>s as input; if your
   * <code>Function</code> doesn't handle <code>String</code>s, you
   * need to write a wrapper for it that does.
   * <p><i>Implementation notes:</i> The InputStream is assumed to already
   * be buffered if useful, and we need a stream, so that the XML decoder
   * can determine the correct character encoding of the XML file. The output
   * is to a Writer, and the provided Writer should again be buffered if
   * desirable.  Internally, this Writer is wrapped as a PrintWriter.
   *
   * @param tags an array of <code>String</code>s, each an XML entity
   *             within which the transformation should be applied
   * @param fn   the {@link Function <code>Function</code>} to apply
   * @param in   the <code>InputStream</code> to read from
   * @param w    the <code>Writer</code> to write to
   */
  public void transformXML(String[] tags, Function<String,T> fn, InputStream in, Writer w) {
    transformXML(tags, fn, in, w, saxInterface);
  }

  /**
   * Read XML from input stream and write XML to output stream,
   * while transforming text appearing inside the specified XML tags
   * by applying the specified {@link Function <code>Function</code>}.
   * Note that the <code>Function</code> you supply must be
   * prepared to accept <code>String</code>s as input; if your
   * <code>Function</code> doesn't handle <code>String</code>s, you
   * need to write a wrapper for it that does.
   * <p><i>Implementation notes:</i> The InputStream is assumed to already
   * be buffered if useful, and we need a stream, so that the XML decoder
   * can determine the correct character encoding of the XML file. The output
   * is to a Writer, and the provided Writer should again be buffered if
   * desirable.  Internally, this Writer is wrapped as a PrintWriter.
   *
   * @param tags an array of <code>String</code>s, each an XML entity
   *             within which the transformation should be applied
   * @param fn   the {@link Function <code>Function</code>} to apply
   * @param in   the <code>InputStream</code> to read from
   * @param w    the <code>Writer</code> to write to
   * @param handler the sax handler you would like to use (default is SaxInterface, defined in this class, but you may define your own handler)
   */
  public void transformXML(String[] tags, Function<String,T> fn, InputStream in, Writer w, SAXInterface<T> handler) {
    saxInterface = handler;
    inStream = in;
    saxInterface.outWriter = new PrintWriter(w, true);
    saxInterface.function = fn;
    saxInterface.elementsToBeTransformed = new ArrayList<String>();
    saxInterface.elementsToBeTransformed.addAll(Arrays.asList(tags));
    try {
      parse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

} // end class TransformXML
