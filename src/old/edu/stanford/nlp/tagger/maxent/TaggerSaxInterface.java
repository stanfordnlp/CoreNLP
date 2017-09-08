package old.edu.stanford.nlp.tagger.maxent;

import old.edu.stanford.nlp.process.TransformXML;
import old.edu.stanford.nlp.util.XMLUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 * Based on SAXInterface in TransformXML, but discards internal tags in fields
 * to be tagged, tagging all content in the fields.
 *
 * @author Anna Rafferty
 */
public class TaggerSaxInterface<T> extends TransformXML.SAXInterface<T> {


  // Called at the beginning of each element.  If the tag is on the
  // designated list, set flag to remember that we're in an element
  // to be transformed.  In either case, echo tag.
  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (elementsToBeTransformed.contains(qName)) {
      inElementToBeTransformed = true;
    }
    // echo tag to outStream only if not inside element being transformed
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
    } else {
      if (!inElementToBeTransformed || elementsToBeTransformed.contains(qName))
        outWriter.println(XMLUtils.escapeXML(text));
    }
    if (!inElementToBeTransformed || elementsToBeTransformed.contains(qName)) {
      outWriter.print("</");
      outWriter.print(qName);
      outWriter.println('>');
    }
  }

}
