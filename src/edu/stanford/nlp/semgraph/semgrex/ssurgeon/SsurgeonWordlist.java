package edu.stanford.nlp.semgraph.semgrex.ssurgeon;

import java.util.*;
import java.io.*;

import org.w3c.dom.*;

/**
 * This implements an unordered word-list resource for Ssurgeon
 * @author Eric Yeh
 *
 */
public class SsurgeonWordlist {
  private static final String WORD_ELT = "word";
  private String id;
  private HashSet<String> words = new java.util.HashSet<>();
  
  @Override
  public String toString() {
    StringWriter buf = new StringWriter();
    buf.write("Ssurgeon Wordlist Resource, id=");
    buf.write(id);
    buf.write(", elements=(");
    for (String word : words) {
      buf.write(" ");
      buf.write(word);
    }
    buf.write(")");
    return buf.toString();
  }
  public String getID() { return id ; }
  /**
   * Reconstructs the resource from the XML file
   */
  @SuppressWarnings("unchecked")
  public SsurgeonWordlist(Element rootElt) {
    id = rootElt.getAttribute("id");
    NodeList wordEltNL = rootElt.getElementsByTagName(WORD_ELT);
    for (int i=0; i<wordEltNL.getLength(); i++) {
    	Node node = wordEltNL.item(i);
    	if (node.getNodeType() == Node.ELEMENT_NODE) {
    		String word = Ssurgeon.getEltText((Element) node);
    		words.add(word);
    	}
    }    
  }
  
  public boolean contains(String testWord) {
    return words.contains(testWord);
  }
  
  /**
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub

  }

}
