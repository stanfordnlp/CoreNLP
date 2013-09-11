package edu.stanford.nlp.wsd;

import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A handler which changes Senseval-2 format files to CS 224N format files
 * for word sense disambiguation
 *
 * @author Galen Andrew (pupochik@cs.stanford.edu)
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 */

public class SensevalHandler extends DefaultHandler {

  private String lexElt;
  private String nakedLexElt; // w/o pos
  private String instId;
  private String docSource;
  private ArrayList<String> senseIDs;
  private String currentString;
  private ArrayList<String> context;
  private int targetWordPos;
  private ArrayList<SensevalInstance> instances;
  private HashMap<String,Index<String>> numMap;
  private Index<String> senseNum;
  private boolean justSawSpecialChar;
  private boolean noNonIntSenses;

  public SensevalHandler(ArrayList<SensevalInstance> instances, HashMap<String,Index<String>> numMap, boolean noNonIntSenses) {
    this.instances = instances;
    this.numMap = numMap;
    this.noNonIntSenses = noNonIntSenses;
    justSawSpecialChar = false;
  }


  //
  // DocumentHandler methods
  //

  @Override
  public void startElement(String uri, String local, String name, Attributes attrs) {
    if (name.equals("lexelt")) {
      lexElt = new String(attrs.getValue("item"));
      nakedLexElt = new String(lexElt.substring(0, lexElt.indexOf('.')));
      if (numMap.containsKey(nakedLexElt)) {
        senseNum = numMap.get(nakedLexElt);
      } else {
        numMap.put(nakedLexElt, senseNum = new HashIndex<String>());
      }

    } else if (name.equals("instance")) {
      instId = new String(attrs.getValue("id"));
      if (attrs.getValue("docsrc") != null) {
        docSource = new String(attrs.getValue("docsrc"));
      } else {
        docSource = new String();
      }
      senseIDs = new ArrayList<String>();

    } else if (name.equals("answer")) {
      String senseID = new String(attrs.getValue("senseid"));
      if (!noNonIntSenses || senseID.length() > 1) {
        senseIDs.add(senseID);
      }
      if (senseID.length() > 1) {
        senseNum.indexOf(senseID, true);
      }

    } else if (name.equals("context")) {
      context = new ArrayList<String>();
      currentString = new String("");
    }

  }

  private void commitCurrent() {
    if (!currentString.equals("")) {
      context.add(new String(currentString));
      currentString = "";
    }
  }

  @Override
  public void characters(char ch[], int start, int length) {
    boolean printing = true;
    int i;
    for (i = start; i < start + length; i++) {

      switch (ch[i]) {

        case '[':
          printing = false;
          break;

        case ']':
          printing = true;
          break;

        case '\\':
          break;

        default:
          if (!printing) {
            continue;
          }
          if (Character.isWhitespace(ch[i])) {
            commitCurrent();
            break;
          }
          if (!printing) {
            break;
          }
          // - and ' are word characters
          if (Character.isLetterOrDigit(ch[i]) || ch[i] == '-' || ch[i] == '\'') {
            currentString += ch[i];
          }

          // underscore is not allowed in our format
          else if (ch[i] == '_') {
            currentString += '-';

          } else {
            commitCurrent();
            if (!justSawSpecialChar) {
              context.add(Character.toString(ch[i])); // punct chars are words
            }
          }
          justSawSpecialChar = false;

          break;
      }
    }

    // I can't find any other way to get these special characters
    // other than reading outside of the bounds of the array...
    if (i < ch.length && ch[i] == '&') {
      String specString = new String("");
      while (i < ch.length && ch[i] != ';') {
        specString += ch[i++];
      }
      specString += ';';
      if (specString.matches(".*grave;") || specString.matches(".*acute;") || specString.matches(".*circ;") || specString.matches(".*tilde;") || specString.matches(".*uml;") || specString.matches(".*ring;") || specString.matches(".*slash;") || specString.matches(".*strok;") || specString.matches(".*thorn;") || specString.matches(".*THORN;") || specString.matches(".*dot;") || specString.matches(".*breve;") || specString.matches(".*macr;") || specString.matches(".*ogon;") || specString.matches(".*caron;") || specString.matches(".*dblac;") || specString.matches(".*green;") || specString.matches(".*eng;") || specString.matches(".*lig;") || specString.matches(".*cedil;")) {
        currentString += specString;
      } else {
        commitCurrent();
        currentString = specString;
        commitCurrent();
      }
      justSawSpecialChar = true;

    } else {
      if (!justSawSpecialChar) {
        commitCurrent();
      }
    }
  }

  @Override
  public void endElement(String uri, String local, String name) {
    if (name.equals("head")) {
      targetWordPos = context.size() - 1;
      commitCurrent();

    } else if (name.equals("instance")) {
      instances.add(new SensevalInstance(lexElt, instId, docSource, senseIDs, context, targetWordPos));

    } else if (name.equals("context")) {
      currentString = "";
    }
  }

  /*---------------------------------------------------*/
  /*               ErrorHandler methods                */
  /*---------------------------------------------------*/


  /**
   * Warning.
   */
  @Override
  public void warning(SAXParseException ex) {
    System.err.println("[Warning] " + getLocationString(ex) + ": " + ex.getMessage());
  }

  /**
   * Error.
   */
  @Override
  public void error(SAXParseException ex) {
    System.err.println("[Error] " + getLocationString(ex) + ": " + ex.getMessage());
  }

  /**
   * Fatal error.
   */
  @Override
  public void fatalError(SAXParseException ex) throws SAXException {
    System.err.println("[Fatal Error] " + getLocationString(ex) + ": " + ex.getMessage());
    throw ex;
  }

  /**
   * Returns a string of the location.
   */
  private String getLocationString(SAXParseException ex) {
    StringBuffer str = new StringBuffer();

    String systemId = ex.getSystemId();
    if (systemId != null) {
      int index = systemId.lastIndexOf('/');
      if (index != -1) {
        systemId = systemId.substring(index + 1);
      }
      str.append(systemId);
    }
    str.append(':');
    str.append(ex.getLineNumber());
    str.append(':');
    str.append(ex.getColumnNumber());

    return str.toString();

  } // getLocationString(SAXParseException):String

}


