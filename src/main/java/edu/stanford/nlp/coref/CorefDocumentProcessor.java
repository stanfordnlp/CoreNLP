package edu.stanford.nlp.coref;

import java.util.Properties;

import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.coref.data.DocumentMaker;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * An interface for classes that iterate through coreference documents and process them one by one.
 * @author Kevin Clark
 */
public interface CorefDocumentProcessor {
  public void process(int id, Document document);

  public void finish() throws Exception;

  public default String getName() {
    return this.getClass().getName();
  }

  public default void run(Properties props, Dictionaries dictionaries) throws Exception {
    run(new DocumentMaker(props, dictionaries));
  }

  public default void runFromScratch(Properties props, Dictionaries dictionaries)
      throws Exception {
    // Some annotators produce slightly different outputs when running over the same input data
    // twice. Here we first clear annotator pool to avoid this.
    StanfordCoreNLP.clearAnnotatorPool();
    run(new DocumentMaker(props, dictionaries));
  }

  public default void run(DocumentMaker docMaker) throws Exception {
    Redwood.hideChannelsEverywhere("debug-mention", "debug-preprocessor", "debug-docreader",
        "debug-md");
    int docId = 0;
    Document document = docMaker.nextDoc();
    long time = System.currentTimeMillis();
    while (document != null) {
      process(docId, document);
      Redwood.log(getName(), "Processed document " + docId + " in "
          + (System.currentTimeMillis() - time) / 1000.0 + "s");
      time = System.currentTimeMillis();
      docId++;
      document = docMaker.nextDoc();
    }
    finish();
  }
}
