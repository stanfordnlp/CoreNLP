package edu.stanford.nlp.coref.docreader;

import edu.stanford.nlp.coref.data.InputDoc;

public interface DocReader {

  /** Read raw, CoNLL, ACE, or MUC document and return InputDoc */
  public InputDoc nextDoc();

  public void reset();
}
