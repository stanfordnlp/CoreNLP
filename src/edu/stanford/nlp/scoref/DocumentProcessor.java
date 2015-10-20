package edu.stanford.nlp.scoref;

import java.util.Properties;

import edu.stanford.nlp.hcoref.CorefDocMaker;
import edu.stanford.nlp.hcoref.data.Dictionaries;
import edu.stanford.nlp.hcoref.data.Document;
import edu.stanford.nlp.util.logging.Redwood;

public interface DocumentProcessor {
  public void process(int id, Document document);

  public void finish() throws Exception;

  public default String getName() {
    return this.getClass().getSimpleName();
  }

  public default void run(Properties props, Dictionaries dictionaries) throws Exception {
    run(new CorefDocMaker(props, dictionaries));
  }

  public default void run(CorefDocMaker docMaker) throws Exception {
    Redwood.hideChannelsEverywhere("debug-mention", "debug-preprocessor", "debug-docreader",
        "debug-md");
    Document document = docMaker.nextDoc();
    int docId = 0;
    long time = System.currentTimeMillis();
    while (document != null) {
      document.extractGoldCorefClusters();
      process(docId, document);
      Redwood.log("scoref", "Processed document " + docId + " in "
          + (System.currentTimeMillis() - time) / 1000.0 + "s with " + getName());
      time = System.currentTimeMillis();
      document = docMaker.nextDoc();
      docId++;
    }
    finish();
  }
}
