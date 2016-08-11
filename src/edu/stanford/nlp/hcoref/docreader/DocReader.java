package edu.stanford.nlp.hcoref.docreader;

import edu.stanford.nlp.hcoref.data.InputDoc;

public interface DocReader {
  
  /** Read raw, CoNLL, ACE, or MUC document and return InputDoc */
  public InputDoc nextDoc();

  public void reset();
}
