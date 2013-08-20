package edu.stanford.nlp.ie;

import edu.stanford.nlp.util.StringUtils;
import org.xml.sax.*;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.util.Iterator;

/**
 * Helper class to read and write XML for ExtractorMediator.
 * Current implementation is based on SAX for both reading and writing XML.
 * This class is not public because its only use is package-internal.
 * <p>Reading code based on http://java.sun.com/webservices/docs/1.0/tutorial/doc/JAXPSAX4.html
 * <p>Writing code based on http://java.sun.com/webservices/docs/1.0/tutorial/doc/JAXPXSLT6.html
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
class EMXMLHandler extends DefaultHandler implements XMLReader {
  private ExtractorMediator mediator; // current mediator being used
  private ContentHandler handler; // used to generate SAX events while storing

  // We're not doing namespaces, and we have no attributes on our elements.
  private static final String nsu = "";  // NamespaceURI
  private static final Attributes noAtts = new AttributesImpl();

  // indentations to make XML readable
  private static final String nl = "\n";
  private static final String tab = " "; // change tab-width here
  private static final String indent0 = nl;
  private static final String indent1 = nl + tab;
  private static final String indent2 = nl + tab + tab;
  private static final String indent3 = nl + tab + tab + tab;
  private static final String indent4 = nl + tab + tab + tab + tab;
  private static final String[] indents = new String[]{indent0, indent1, indent2, indent3, indent4};

  // names of XML elements

  private static final String extractorMediatorName = "extractor-mediator";

  private static final String extractorsName = "extractors";
  private static final String extractorName = "extractor";
  private static final String nameName = "name";
  private static final String classNameName = "class-name";
  private static final String descriptionName = "description";
  private static final String extractableFieldsName = "extractable-fields";

  private static final String associationsName = "associations";
  private static final String associationName = "association";
  private static final String className = "class";
  private static final String slotName = "slot";
  // "extractor" is already defined (bad to have same elem name serving two purposes??
  private static final String fieldName = "field";

  // no guarantee text elements will come all at once, so have to accumulate it
  private StringBuffer textBuffer = null;

  // name of extractor currently being read from XML
  private String currentExtractorName = null;

  // fields in current association
  private String caClass = null, caSlot = null, caExtractor = null, caField = null;

  /**
   * Constructs a new xml handler for the given extractor mediator.
   * This xml handler is responsible for loading and storing XML representing
   * the mediator.
   */
  public EMXMLHandler(ExtractorMediator mediator) {
    this.mediator = mediator;
  }

  /**
   * Loads the mediator from its mediator XML file.
   * Returns whether loading was successful.
   */
  boolean loadMediator() {
    try {
      // Use the default (non-validating) parser to parse the input
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(mediator.getMediatorFile(), this);
      return (true);
    } catch (Exception e) {
      e.printStackTrace();
      return (false);
    }
  }

  /**
   * Doesn't do anything now, but left in for future needs.
   */
  @Override
  public void startElement(String namespaceURI, String sName, // simple name
                           String qName, // qualified name
                           Attributes attrs) throws SAXException {
    String eName = sName; // element name
    if ("".equals(eName)) {
      eName = qName; // not namespaceAware
    }

    // nothing to do here really, it's all done by the end tag
  }

  /**
   * Accumulates text elements (they may come in multiple pieces).
   */
  @Override
  public void characters(char buf[], int offset, int len) throws SAXException {
    String s = new String(buf, offset, len);
    if (s.trim().length() > 0) // ignores intermediate whitespace
    {
      if (textBuffer == null) {
        textBuffer = new StringBuffer(s);
      } else {
        textBuffer.append(s);
      }
    }
  }

  /**
   * The meat of the loading from XML. Figures out which tag just ended and
   * sticks the previous text contents in the appropriate place.
   */
  @Override
  public void endElement(String namespaceURI, String sName, // simple name
                         String qName  // qualified name
                         ) throws SAXException {

    String eName = sName; // element name
    if ("".equals(eName)) {
      eName = qName; // not namespaceAware
    }

    if (nameName.equals(eName)) // extractor's unique name
    {
      currentExtractorName = textBuffer.toString();
      mediator.addExtractorName(currentExtractorName);
    } else if (classNameName.equals(eName)) // extractor class name
    {
      mediator.setClassName(currentExtractorName, textBuffer.toString());
    } else if (descriptionName.equals(eName)) // extractor description
    {
      mediator.setDescription(currentExtractorName, textBuffer.toString());
    } else if (extractableFieldsName.equals(eName)) {
      mediator.setExtractableFields(currentExtractorName, textBuffer.toString().split(" "));
    } else if (extractorName.equals(eName)) {
      // "extractor" is ambiguous -> check if it's part of an association
      if (caClass == null) // in an association if class name is not null
      {
        currentExtractorName = null; // done with this extractor
      } else {
        caExtractor = textBuffer.toString(); // extractor for association
      }
    } else if (className.equals(eName)) // class of association
    {
      caClass = textBuffer.toString();
    } else if (slotName.equals(eName)) // slot of association
    {
      caSlot = textBuffer.toString();
    } else if (fieldName.equals(eName)) // field of association
    {
      caField = textBuffer.toString();
    } else if (associationName.equals(eName)) // done with current association
    {
      mediator.assignExtractorField(caClass, caSlot, caExtractor, caField);
      caClass = caSlot = caExtractor = caField = null; // reset association values
    }

    textBuffer = null; // clears previous run of chars
  }

  /**
   * Writes the mediator out to the its mediator file as XML.
   * Returns whether storage was successful.
   */
  boolean storeMediator() {
    try {
      // Use a Transformer for output
      TransformerFactory tFactory = TransformerFactory.newInstance();
      Transformer transformer = tFactory.newTransformer();

      // use this class as a "reader" that generates SAX events
      SAXSource source = new SAXSource(this, new InputSource());
      StreamResult result = new StreamResult(mediator.getMediatorFile());
      transformer.transform(source, result);

      return (true);
    } catch (Exception e) {
      e.printStackTrace();
      return (false);
    }
  }

  /**
   * Generates SAX events as if the mediator were an XML document being read.
   * input is ignored entirely (required for the SAXSource interface).
   */
  public void parse(InputSource input) throws IOException, SAXException {
    if (handler == null) {
      throw new SAXException("No content handler");
    }
    handler.startDocument();
    handler.startElement(nsu, extractorMediatorName, extractorMediatorName, noAtts);

    // outputs the extractors managed by the mediator
    // - extractors
    //   - extractor
    //     - name
    //     - class-name
    //     - description
    //     - extractable-fields [space-separated]
    outputStart(extractorsName, 1);
    Iterator<String> extractorNamesIter = mediator.getExtractorNames().iterator();
    while (extractorNamesIter.hasNext()) {
      String name = extractorNamesIter.next();
      outputStart(extractorName, 2);
      outputElement(nameName, name, 3);
      outputElement(classNameName, mediator.getClassName(name), 3);
      outputElement(descriptionName, mediator.getDescription(name), 3);
      outputElement(extractableFieldsName, StringUtils.join(mediator.getExtractableFields(name)), 3);
      outputEnd(extractorName, 2);
    }
    outputEnd(extractorsName, 1);

    // outputs associations between class-slot and extractor-field
    // - associations
    //   - association
    //     - class
    //     - slot
    //     - extractor
    //     - field
    // NOTE: current implementation assumes at most one extractorfield per
    // slot, but this could easily be changed
    outputStart(associationsName, 1);
    String[] classNames = mediator.getExtractableClassNames();
    for (int i = 0; i < classNames.length; i++) {
      String[] slotNames = mediator.getExtractableSlots(classNames[i]);
      for (int j = 0; j < slotNames.length; j++) {
        ExtractorMediator.ExtractorField ef = mediator.getAssignedExtractorField(classNames[i], slotNames[j]);
        outputStart(associationName, 2);
        outputElement(className, classNames[i], 3);
        outputElement(slotName, slotNames[j], 3);
        outputElement(extractorName, ef.getExtractorName(), 3);
        outputElement(fieldName, ef.getFieldName(), 3);
        outputEnd(associationName, 2);
      }
    }
    outputEnd(associationsName, 1);

    handler.ignorableWhitespace(indent0.toCharArray(), 0, indent0.length());
    handler.endElement(nsu, extractorMediatorName, extractorMediatorName);
    handler.endDocument();
  }

  /**
   * Outputs the given text surrounded by the given tag indented at the given level.
   */
  private void outputElement(String elementName, String text, int level) throws SAXException {
    outputStart(elementName, level);
    handler.characters(text.toCharArray(), 0, text.length());
    outputEnd(elementName, -1);
  }

  /**
   * Outputs the given start tag indented at the given level.
   */
  private void outputStart(String elementName, int level) throws SAXException {
    indent(level);
    handler.startElement(nsu, elementName, elementName, noAtts);
  }

  /**
   * Outputs the given end tag indented at the given level.
   */
  private void outputEnd(String elementName, int level) throws SAXException {
    indent(level);
    handler.endElement(nsu, elementName, elementName);
  }

  /**
   * Writes out a newline and the given number of tabs.
   * Does nothing if level is negative.
   */
  private void indent(int level) throws SAXException {
    if (level >= 0) {
      handler.ignorableWhitespace(indents[level].toCharArray(), 0, indents[level].length());
    }
  }


  /*  ----------------------------------------------------------
      METHODS BELOW ARE REQUIRED FOR STORING XML VIA SAX
      TAKEN FROM: http://java.sun.com/xml/jaxp/dist/1.1/docs
                    /tutorial/xslt/work/AddressBookReader02.java
      ---------------------------------------------------------- */

  /**
   * Allow an application to register a content event handler.
   */
  public void setContentHandler(ContentHandler handler) {
    this.handler = handler;
  }

  /**
   * Return the current content handler.
   */
  public ContentHandler getContentHandler() {
    return this.handler;
  }

  //=============================================
  // IMPLEMENT THESE FOR A ROBUST APP
  //=============================================
  /**
   * Allow an application to register an error event handler.
   */
  public void setErrorHandler(ErrorHandler handler) {
  }

  /**
   * Return the current error handler.
   */
  public ErrorHandler getErrorHandler() {
    return null;
  }

  //=============================================
  // IGNORE THESE
  //=============================================
  /**
   * Parse an XML document from a system identifier (URI).
   */
  public void parse(String systemId) throws IOException, SAXException {
  }

  /**
   * Return the current DTD handler.
   */
  public DTDHandler getDTDHandler() {
    return null;
  }

  /**
   * Return the current entity resolver.
   */
  public EntityResolver getEntityResolver() {
    return null;
  }

  /**
   * Allow an application to register an entity resolver.
   */
  public void setEntityResolver(EntityResolver resolver) {
  }

  /**
   * Allow an application to register a DTD event handler.
   */
  public void setDTDHandler(DTDHandler handler) {
  }

  /**
   * Look up the value of a property.
   */
  public Object getProperty(java.lang.String name) {
    return null;
  }

  /**
   * Set the value of a property.
   */
  public void setProperty(java.lang.String name, java.lang.Object value) {
  }

  /**
   * Set the state of a feature.
   */
  public void setFeature(java.lang.String name, boolean value) {
  }

  /**
   * Look up the value of a feature.
   */
  public boolean getFeature(java.lang.String name) {
    return false;
  }

}
