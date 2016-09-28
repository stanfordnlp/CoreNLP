package edu.stanford.nlp.coref.statistical;

import java.util.Properties;

import edu.stanford.nlp.coref.CorefDocMaker;
import edu.stanford.nlp.coref.data.Dictionaries;
import edu.stanford.nlp.coref.data.Document;
import edu.stanford.nlp.util.logging.Redwood;

public class DocumentProcessorRunner {
  private final CorefDocMaker docMaker;
  private final DocumentProcessor processor;
  private final int maxDocs;

  public DocumentProcessorRunner(Properties props, Dictionaries dictionaries,
      DocumentProcessor processor) {
    this(props, dictionaries, processor, Integer.MAX_VALUE);
  }

  public DocumentProcessorRunner(Properties props, Dictionaries dictionaries,
      DocumentProcessor processor, int maxDocs) {
    Redwood.hideChannelsEverywhere("debug-mention", "debug-preprocessor", "debug-docreader",
          "debug-md");
    try {
      docMaker = new CorefDocMaker(props, dictionaries);
    } catch (Exception e) {
      throw new RuntimeException("Error initializing coref system", e);
    }
    this.processor = processor;
    this.maxDocs = maxDocs;
  }

  public void run() throws Exception {
    Document document = docMaker.nextDoc();
    int docId = 0;
    long time = System.currentTimeMillis();
    while (document != null) {
      if (docId >= maxDocs) {
        break;
      }
      document.extractGoldCorefClusters();
      processor.process(docId, document);
      Redwood.log("scoref", "Processed document " + docId + " in "
          + (System.currentTimeMillis() - time) / 1000.0 + "s with "
          + processor.getClass().getSimpleName());
      time = System.currentTimeMillis();
      document = docMaker.nextDoc();
      docId++;
    }
    processor.finish();
  }
}
