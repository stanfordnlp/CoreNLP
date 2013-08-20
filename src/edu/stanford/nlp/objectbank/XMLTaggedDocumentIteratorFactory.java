package edu.stanford.nlp.objectbank;

import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.io.*;

import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;

/**
 * A class capable of parsing documents from an XML file.
 * Returns an iterator over documents, each of which is a List of MapLabel tokens.
 * XML file must be in the following format:
 *
 * @author grenager
 */
public class XMLTaggedDocumentIteratorFactory implements IteratorFromReaderFactory<List<CoreLabel>> {

  private static boolean verbose = false;

  String docTag;
  boolean useComplexTags;
  String otherTag;
  TokenizerFactory<CoreLabel> tokenizerFactory;

  /**
   * Reads them all into a list before iterating. Not memory-efficient.
   *
   */
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    Handler handler = new Handler();
    SAXParserFactory factory = SAXParserFactory.newInstance();
    try {
      // Parse the input
      SAXParser saxParser = factory.newSAXParser();
      saxParser.parse(new InputSource(r), handler);
    } catch (Throwable t) {
      System.err.println(t);
      t.printStackTrace();
    }
    List<List<CoreLabel>> documents = handler.getDocuments();
    System.err.println("Done reading docs. Read in " + documents.size() + " docs.");
    return documents.iterator();
  }

  class Handler extends DefaultHandler {
    List<List<CoreLabel>> documents;
    List<CoreLabel> thisDoc;
    StringBuffer fieldBuffer;

    public List<List<CoreLabel>> getDocuments() {
      return documents;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs) {
      if (qName.equals(docTag)) {
        thisDoc = new ArrayList<CoreLabel>();
        fieldBuffer = new StringBuffer();
      } else if (useComplexTags) {
        // using complex tags
        handleComplexTag(qName, attrs);
      } else {
        if (thisDoc != null) { // we are in a document
          // use simple tags
          // add all the tokens since the last annotation
          addPreviousToDoc(otherTag);
        }
      }
    }

    private void handleComplexTag(String qName, Attributes attrs) {
      if (qName.equals("tag")) {
        // get attributes
        String tag = attrs.getValue("name");
        String val = attrs.getValue("value");
        if (val.equals("start")) {
          // add all the tokens since the last annotation
          addPreviousToDoc(otherTag);
        } else if (val.equals("end")) {
          // add all the tokens in this annotation
          addPreviousToDoc(tag);
        } else {
          throw new RuntimeException("unexpected value=" + val);
        }
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
      if (qName.equals(docTag)) {
        // add all the tokens since the last annotation
        String s = fieldBuffer.toString();
        Tokenizer<CoreLabel> tok = tokenizerFactory.getTokenizer(new StringReader(s));
        List<CoreLabel> tokens = tok.tokenize();
        for (int i = 0; i < tokens.size(); i++) {
          CoreLabel mapLabel = tokens.get(i);
          mapLabel.setTag(otherTag);
          thisDoc.add(mapLabel); // these are outside of any field
        }
        // close the document
        documents.add(thisDoc);
//        System.err.println("adding doc=" + thisDoc.subList(0, Math.min(10, thisDoc.size())));
        if (documents.size()%100==0) {
          System.err.println("Read in " + documents.size() + " docs.");
        }
        thisDoc = null;
        fieldBuffer = null;
      } else if (useComplexTags) {
        // do nothing
      } else {
        if (thisDoc != null) {
          // use simple tags
          // add all the tokens in this annotation
          addPreviousToDoc(qName);
        }
      }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
      if (fieldBuffer != null) {
        fieldBuffer.append(ch, start, length);
      }
    }


    private void addPreviousToDoc(String tag) {
      String s = fieldBuffer.toString();
      if (verbose) System.out.println("Adding as "+tag+": \"" + s + "\"");
      fieldBuffer = new StringBuffer();
      Tokenizer<CoreLabel> tok = tokenizerFactory.getTokenizer(new StringReader(s));
      List<CoreLabel> tokens = tok.tokenize();
      for (int i = 0; i < tokens.size(); i++) {
        CoreLabel mapLabel = tokens.get(i);
        mapLabel.setTag(tag);
        thisDoc.add(mapLabel);
      }
    }

    public Handler() {
      this.documents = new ArrayList<List<CoreLabel>>();
    }
  }


  public XMLTaggedDocumentIteratorFactory(String docTag, boolean useComplexTags, TokenizerFactory<CoreLabel> tokenizerFactory, String otherTag) {
    this.docTag = docTag;
    this.useComplexTags = useComplexTags;
    this.tokenizerFactory = tokenizerFactory;
    this.otherTag = otherTag;
  }

  public static void main(String[] args) throws FileNotFoundException {
    BufferedReader in = new BufferedReader(new FileReader(args[0]));
    ObjectBank<List<CoreLabel>> ob = new ObjectBank<List<CoreLabel>>(new ReaderIteratorFactory(in),
                                   new XMLTaggedDocumentIteratorFactory("document", args[1].equals("true"), PTBTokenizer.PTBTokenizerFactory.newPTBTokenizerFactory(true, true), "other"));
    for (Iterator<List<CoreLabel>> docIter = ob.iterator(); docIter.hasNext();) {
      List<CoreLabel> doc = docIter.next();
      System.err.println(doc);
    }
  }
}
