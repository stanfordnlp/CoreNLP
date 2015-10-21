package edu.stanford.nlp.scoref;

import edu.stanford.nlp.hcoref.data.Document;

public interface DocumentProcessor {
  public void process(int id, Document document);
  public void finish() throws Exception;
}
