package edu.stanford.nlp.process; 

import java.io.*;
import java.util.*;
import java.util.function.Function;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.XMLUtils;
import edu.stanford.nlp.util.logging.Redwood;


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
public class TransformXML<T>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(TransformXML.class);

  private final SAXParser saxParser;

  public SAXInterface<T> buildSaxInterface() { return new SAXInterface<>(); }

  public static class SAXInterface<T> extends DefaultHandler {

    protected List<String> elementsToBeTransformed;
    protected StringBuilder textToBeTransformed;
    protected PrintWriter outWriter = new PrintWriter(System.out, true);
    protected Function<String,T> function;

    /**
     * How far down we are in the nested tags.  For example, if we've
     * seen &lt;foo&gt; &lt;bar&gt; and "foo" and "bar" are both tags
     * we care about, then depth = 2.
     */
    protected int depth = 0;

    public SAXInterface() {
      elementsToBeTransformed = new ArrayList<>();
      depth = 0;
      openingTag = null;
      textToBeTransformed = new StringBuilder();
    }

    /**
     * The first tag from {@link <code>elementsToBeTransformed</code>}
     * that we saw the last time {@link <code>depth</code>} was
     * <code>0</code>.
     * <br>
     * You would expect incoming XML to be well-formatted, but just in
     * case it isn't, we keep track of this so we can output the
     * correct closing tag.
     */
    String openingTag;

    private void outputTextAndTag(String qName, Attributes attributes, boolean close) {
      // If we're not already in an element to be transformed, first
      // echo the previous text...
      outWriter.print(XMLUtils.escapeXML(textToBeTransformed.toString()));
      textToBeTransformed = new StringBuilder();
      
      // ... then echo the new tag to outStream 
      outWriter.print('<');
      if (close) {
        outWriter.print('/');
      }
      outWriter.print(qName);
      if (attributes != null) {
        for (int i = 0; i < attributes.getLength(); i++) {
          outWriter.print(' ');
          outWriter.print(attributes.getQName(i));
          outWriter.print("=\"");
          outWriter.print(XMLUtils.escapeXML(attributes.getValue(i)));
          outWriter.print('"');
        }
      }
      outWriter.print(">\n");
    }

    @Override
    public void endDocument() {
      // Theoretically, there shouldn't be anything in the buffer after
      // the last closing tag, but if there is, it's probably better to
      // echo it than ignore it
      outWriter.print(XMLUtils.escapeXML(textToBeTransformed.toString()));
      // we need to flush because there are no other ways we
      // explicitely flush
      outWriter.flush();
    }
    
    // Called at the beginning of each element.  If the tag is on the
    // designated list, set flag to remember that we're in an element
    // to be transformed.  In either case, echo tag.
    @Override
    public void startElement(String uri, String localName, String qName, 
                             Attributes attributes) throws SAXException {
      //log.info("start element " + qName);
      
      if (depth == 0) {
        outputTextAndTag(qName, attributes, false);
      }
      
      if (elementsToBeTransformed.contains(qName)) {
        if (depth == 0) {
          openingTag = qName;
        }
        ++depth;
      }
    }

    // Called at the end of each element.  If the tag is on the
    // designated list, apply the designated {@link Function
    // <code>Function</code>} to the accumulated text and echo the the
    // result.  In either case, echo the closing tag.
    @Override
    public void endElement(String uri, String localName, String qName) 
      throws SAXException 
    {
      //log.info("end element " + qName + "; function is " + function.getClass());
      //log.info("elementsToBeTransformed is " + elementsToBeTransformed);
      //log.info("textToBeTransformed is " + textToBeTransformed);
      
      if (depth == 0) {
        outputTextAndTag(qName, null, true);
      } else {
        if (elementsToBeTransformed.contains(qName)) {
          --depth;
          if (depth == 0) {
            String text = textToBeTransformed.toString().trim();
            // factored out so subclasses can handle the text differently
            processText(text);
            textToBeTransformed = new StringBuilder();
            outWriter.print("</" + openingTag + ">\n");
          }
        }
        // when we're inside a block to be transformed, we ignore
        // elements that don't end the block.
      }
    }

    public void processText(String text) {
      if (text.length() > 0) {
        text = function.apply(text).toString();
        outWriter.print(XMLUtils.escapeXML(text));
        outWriter.print('\n');
      }
    }
    

    // Accumulate characters in buffer of text to be transformed
    // (SAX may call this after each line break)
    @Override
    public void characters(char[] buf, int offset, int len) throws SAXException {
      // log.info("characters |" + new String(buf, offset, len) + "|");
      textToBeTransformed.append(buf, offset, len);
    }
  } // end static class SAXInterface


  /**
   * This version of the SAXInterface doesn't escape the text produced
   * by the function.  This is useful in the case where the function
   * already produces well-formed XML.  One example of this is the
   * Tagger, which already escapes the inner text and produces xml
   * tags around the words.
   */
  public static class NoEscapingSAXInterface<T> extends SAXInterface<T> {
    @Override
    public void processText(String text) {
      if (text.length() > 0) {
        text = function.apply(text).toString();
        outWriter.print(text);
        outWriter.print('\n');
      }
    }    
  }


  public TransformXML() {
    try {
      SAXParserFactory spf = SAXParserFactory.newInstance();
      spf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      saxParser = spf.newSAXParser();
    } catch (Exception e) {
      log.info("Error configuring XML parser: " + e);
      throw new RuntimeException(e);
    }
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
      log.info("Error reading file " + in + ": " + e);
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
   * @param in   the {@code File} to read from
   * @param out  the {@code File} to write to
   */
  public void transformXML(String[] tags, Function<String,T> fn, File in, File out) {
    InputStream ins = null;
    OutputStream outs = null;
    try {
      ins = new BufferedInputStream(new FileInputStream(in));
      outs = new BufferedOutputStream(new FileOutputStream(out));
      transformXML(tags, fn, ins, outs);
    } catch (Exception e) {
      log.info("Error reading file " + in + " or writing file " + out + ": " + e);
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
    transformXML(tags, fn, in, new OutputStreamWriter(out), 
                 buildSaxInterface());
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
    transformXML(tags, fn, in, w, buildSaxInterface());
  }

  /**
   * Calls the fully specified transformXML with an InputSource
   * constructed from <code>in</code>.
   */
  public void transformXML(String[] tags, Function<String,T> fn, InputStream in, Writer w, SAXInterface<T> handler) {
    transformXML(tags, fn, new InputSource(in), w, handler);
  }  

  /**
   * Calls the fully specified transformXML with an InputSource
   * constructed from <code>in</code>.
   */
  public void transformXML(String[] tags, Function<String,T> fn, Reader in, Writer w, SAXInterface<T> handler) {
    transformXML(tags, fn, new InputSource(in), w, handler);
  }

  /**
   * Read XML from input source and write XML to output writer,
   * while transforming text appearing inside the specified XML tags
   * by applying the specified {@link Function <code>Function</code>}.
   * Note that the <code>Function</code> you supply must be
   * prepared to accept <code>String</code>s as input; if your
   * <code>Function</code> doesn't handle <code>String</code>s, you
   * need to write a wrapper for it that does.
   * <br>
   * <p><i>Implementation notes:</i> The InputSource is assumed to already
   * be buffered if useful, and we need a stream, so that the XML decoder
   * can determine the correct character encoding of the XML file. 
   * TODO: does that mean there's a bug if you send it a Reader
   * instead of an InputStream?  It seems to work with a Reader...
   * <br>
   * The output is to a Writer, and the provided Writer should again
   * be buffered if desirable.  Internally, this Writer is wrapped as
   * a PrintWriter.
   *
   * @param tags an array of <code>String</code>s, each an XML entity
   *             within which the transformation should be applied
   * @param fn   the {@link Function <code>Function</code>} to apply
   * @param in   the <code>InputStream</code> to read from
   * @param w    the <code>Writer</code> to write to
   * @param saxInterface the sax handler you would like to use (default is SaxInterface, defined in this class, but you may define your own handler)
   */
  public void transformXML(String[] tags, Function<String,T> fn, InputSource in, Writer w, SAXInterface<T> saxInterface) {
    saxInterface.outWriter = new PrintWriter(w, true);
    saxInterface.function = fn;
    saxInterface.elementsToBeTransformed = new ArrayList<>();
    saxInterface.elementsToBeTransformed.addAll(Arrays.asList(tags));
    try {
      saxParser.parse(in, saxInterface);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

} // end class TransformXML
