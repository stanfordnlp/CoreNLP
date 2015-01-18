package edu.stanford.nlp.ie.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;


/**
 * A rather specialized xml processor that turns files in OntoNotes
 * xml format into a column data structure with word in the first
 * column and tag in the second column.  It parses lines one line at a
 * time and treats each line as a separate document.
 *
 * It is specialized for OntoNotes in that it parses only lines that
 * occur between &lt;DOC&gt; and &lt;/DOC&gt; tags.  It also looks for
 * a very specific TYPE tag to denote the ner type.  It could
 * theoretically be adapted or generalized for other projects if
 * needed, though.
 *
 * @author John Bauer
 */
public class OntonotesXMLtoColumn {
  final SAXParser parser;

  int filesProcessed; // = 0;

  public OntonotesXMLtoColumn() {
    try {
      parser = SAXParserFactory.newInstance().newSAXParser();
    } catch (ParserConfigurationException | SAXException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Reads the given filename (assumed to be a file at this point)
   * by creating a reader and then processing that reader with
   * processXML(Reader)
   */
  public void processXML(String filename) {
    try {
      processXML(IOUtils.readerFromString(filename, "UTF-8"), filename);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (SAXException e) {
      throw new RuntimeException("Error while parsing " + filename +
                                 ":\n" + e.toString(), e);
    }
  }


  /**
   * Feeds the given reader to the sax parser using the handler
   * created in the constructor.
   * <br>
   * TODO: throw a different kind of exception?
   */
  public void processXML(BufferedReader input, String filename)
    throws SAXException
  {
    try {
      ++filesProcessed;
      String line;
      boolean active = false;
      while ((line = input.readLine()) != null) {
        if ( ! active) {
          if (line.startsWith("<DOC")) {
            active = true;
          }
          continue;
        } else if (line.startsWith("</DOC")) {
          break;
        }

        line = line.trim();
        // System.err.println("Line is |" + line + "|");

        if ( ! (line.equals("（ 完 ）") || line.equals("完") || line.equals("<TURN>"))) {
        InputSource source = new InputSource(new StringReader("<xml>" + line + "</xml>"));
        // System.err.println("Parsing |" + "<xml>" + line + "</xml>" + "|");
        source.setEncoding("UTF-8");

        ColumnHandler handler = getHandler();
        parser.parse(source, handler);
        finishXML(handler);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  private static void finishXML(ColumnHandler handler) {
    for (int i = 0; i < handler.words.size(); ++i) {
      System.out.printf("%s\t%s%n", handler.words.get(i), handler.tags.get(i));
    }
    System.out.println();
  }


  private static ColumnHandler getHandler() {
    return new ColumnHandler();
  }


  public static class ColumnHandler extends DefaultHandler {
    StringBuilder currentText = new StringBuilder();
    String inside = null;

    List<String> words = new ArrayList<String>();
    List<String> tags = new ArrayList<String>();

    public void saveWords() {
      String text = currentText.toString().trim();
      if (text.length() >= 0) {
        String[] pieces = text.split(" +");
        for (String word : pieces) {
          word = word.trim();
          if (word.equals("")) {
            continue;
          }
          words.add(word);
          tags.add(inside == null ? "O" : inside);
        }
      }
      currentText = new StringBuilder();
    }

    @Override
    public void startElement(String uri, String localName,
                             String qName, Attributes attributes)
      throws SAXException {
      saveWords();
      //String name = ((!localName.equals("")) ? localName : qName);
      inside = attributes.getValue("TYPE");
    }

    @Override
    public void endElement(String uri, String localName,
                           String qName)
      throws SAXException {
      saveWords();
      inside = null;
    }

    @Override
    public void characters(char[] buf, int offset, int len) {
      String newText = new String(buf, offset, len);
      currentText.append(newText);
    }
  }

  public static void main(String[] args) {
    OntonotesXMLtoColumn processor = new OntonotesXMLtoColumn();
    if (args.length == 0) {
      try {
        BufferedReader br = IOUtils.readerFromStdin();
        for (String line; (line = br.readLine()) != null; ) {
          processor.processXML(line);
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    } else {
      for (String filename : args) {
        processor.processXML(filename);
      }
    }
  }

}
