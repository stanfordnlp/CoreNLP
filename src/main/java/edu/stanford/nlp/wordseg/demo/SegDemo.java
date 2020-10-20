package edu.stanford.nlp.wordseg.demo;

import java.io.*;
import java.util.List;
import java.util.Properties;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;


/** This is a very simple demo of calling the Chinese Word Segmenter
 *  programmatically.  It assumes an input file in UTF8.
 *  <br>
 *  <code>
 *  Usage: java -mx1g -cp seg.jar SegDemo fileName
 *  </code>
 *  <br>
 *  To run with the segmenter models jar file in the classpath:
 *  <br>
 *  <code>
 *  java -Dbasedir=edu/stanford/nlp/models/segmenter/chinese edu.stanford.nlp.wordseg.demo.SegDemo
 *  </code>
 *  This will run correctly in the distribution home directory.  To
 *  run in general, the properties for where to find dictionaries or
 *  normalizations have to be set.
 *
 *  @author Christopher Manning
 */

public class SegDemo {

  private static final String basedir = System.getProperty("basedir", "data");
  private static final String MODEL = System.getProperty("model", basedir + "/ctb.gz");
  private static final String DICT = System.getProperty("dict", basedir + "/dict-chris6.ser.gz");

  public static void main(String[] args) throws Exception {
    System.setOut(new PrintStream(System.out, true, "utf-8"));

    Properties props = new Properties();
    props.setProperty("sighanCorporaDict", basedir);
    // props.setProperty("NormalizationTable", "data/norm.simp.utf8");
    // props.setProperty("normTableEncoding", "UTF-8");
    // below is needed because CTBSegDocumentIteratorFactory accesses it
    props.setProperty("serDictionary", DICT);
    if (args.length > 0) {
      props.setProperty("testFile", args[0]);
    }
    props.setProperty("inputEncoding", "UTF-8");
    props.setProperty("sighanPostProcessing", "true");

    CRFClassifier<CoreLabel> segmenter = new CRFClassifier<>(props);
    segmenter.loadClassifierNoExceptions(MODEL, props);
    for (String filename : args) {
      segmenter.classifyAndWriteAnswers(filename);
    }

    if (args.length == 0) {
      String sample = "2008年我住在美国。";
      List<String> segmented = segmenter.segmentString(sample);
      System.out.println(segmented);
    }
  }

}
