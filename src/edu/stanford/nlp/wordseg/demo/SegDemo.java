package edu.stanford.nlp.wordseg.demo;

import java.io.*;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;


/** This is a very simple demo of calling the Chinese Word Segmenter
 *  programmatically.  It assumes an input file in UTF8.
 *  <p/>
 *  <code>
 *  Usage: java -mx1g -cp seg.jar SegDemo fileName
 *  </code>
 *  This will run correctly in the distribution home directory.  To
 *  run in general, the properties for where to find dictionaries or
 *  normalizations have to be set.
 *
 *  @author Christopher Manning
 */

public class SegDemo {

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("usage: java -mx1g SegDemo filename");
      return;
    }

    Properties props = new Properties();
    props.setProperty("sighanCorporaDict", "data");
    // props.setProperty("NormalizationTable", "data/norm.simp.utf8");
    // props.setProperty("normTableEncoding", "UTF-8");
    // below is needed because CTBSegDocumentIteratorFactory accesses it
    props.setProperty("serDictionary","data/dict-chris6.ser.gz");
    props.setProperty("testFile", args[0]);
    props.setProperty("inputEncoding", "UTF-8");
    props.setProperty("sighanPostProcessing", "true");

    CRFClassifier<CoreLabel> segmenter = new CRFClassifier<CoreLabel>(props);
    segmenter.loadClassifierNoExceptions("data/ctb.gz", props);
    segmenter.classifyAndWriteAnswers(args[0]);
  }

}
