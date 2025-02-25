package edu.stanford.nlp.trees.ud;

import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;

/**
 * Load a CoNLL-U file, retag it using the specified tagger, and output back to stdout
 *
 * @author Jason Bolton
 */
public class CoNLLUTagUpdater {

  public static MaxentTagger maxentTagger;

  public static void main(String[] args) throws ClassNotFoundException, IOException {
    Properties props = StringUtils.argsToProperties(args);
    String filePath = props.getProperty("file");
    CoNLLUReader reader = new CoNLLUReader();
    CoNLLUOutputter writer = new CoNLLUOutputter();
    System.err.println("Reading in docs...");
    List<Annotation> docs = reader.readCoNLLUFile(filePath);
    System.err.println("Done.");

    System.err.println("Tagging docs...");
    String taggerPath = props.getProperty("tagger");
    maxentTagger = new MaxentTagger(taggerPath);

    // output each doc to stdout
    // multiple documents could occur if the reader splits the conll-u file
    // at a #newdoc comment
    for (Annotation doc : docs) {
      CoreDocument coreDoc = new CoreDocument(doc);
      for (CoreSentence sentence : coreDoc.sentences()) {
        maxentTagger.tagCoreLabels(sentence.tokens());
      }
      String updatedCoNLLU = writer.print(doc);
      System.out.println(updatedCoNLLU.trim()+"\n");
    }
    System.err.println("Done.");
  }
}
