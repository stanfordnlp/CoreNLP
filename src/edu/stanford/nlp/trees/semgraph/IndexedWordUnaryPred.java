package edu.stanford.nlp.trees.semgraph;

import edu.stanford.nlp.ling.IndexedWord;

/**
 * Represents an extensible class that can be extended and passed around
 * to perform 
 * @author Eric Yeh
 *
 */
public class IndexedWordUnaryPred {
  public boolean test(IndexedWord node) { return true; }    
  
  public boolean test(IndexedWord node, SemanticGraph sg) { return test(node); }
}
