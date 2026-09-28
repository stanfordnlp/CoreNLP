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
  /**
   * Returns the id of this word list, which predicate tests use to refer to it.
   *
   * @return the id
   */
  public String getID() { return id ; }
  /**
   * Reconstructs the resource from the XML file
   *
   * @param rootElt the resource element; its {@code id} attribute gives the
   *   id, and the text of each {@code word} element below it is a word
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
  
  /**
   * Tests whether a word is in this list.  The comparison is case sensitive.
   *
   * @param testWord the word to look for
   * @return whether the word is in the list
   */
  public boolean contains(String testWord) {
    return words.contains(testWord);
  }
  
  /**
   * Does nothing.
   *
   * @param args ignored
   */
  public static void main(String[] args) {
    // TODO Auto-generated method stub

  }

}
