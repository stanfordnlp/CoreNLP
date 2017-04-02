package edu.stanford.nlp.coref.statistical;

import java.util.Properties;

import edu.stanford.nlp.coref.CorefDocMaker;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
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

  public default void runFromScratch(Properties props, Dictionaries dictionaries)
      throws Exception {
    StanfordCoreNLP.clearAnnotatorPool();
    run(new CorefDocMaker(props, dictionaries));
  }

  public default void run(CorefDocMaker docMaker) throws Exception {
    Redwood.hideChannelsEverywhere("debug-mention", "debug-preprocessor", "debug-docreader",
        "debug-md");
    int docId = 0;
    Document document = docMaker.nextDoc();
    long time = System.currentTimeMillis();
    while (document != null) {
      document.extractGoldCorefClusters();
      process(docId, document);
      Redwood.log("scoref", "Processed document " + docId + " in "
          + (System.currentTimeMillis() - time) / 1000.0 + "s with " + getName());
      time = System.currentTimeMillis();
      docId++;
      document = docMaker.nextDoc();
    }
    finish();
  }
}
