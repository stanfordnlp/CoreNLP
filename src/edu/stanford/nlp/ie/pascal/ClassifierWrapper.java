package edu.stanford.nlp.ie.pascal;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.GoldAnswerAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Distribution;
import edu.stanford.nlp.util.StringUtils;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * Generates classifier output with marginal probability distributions
 * at each token.  Written as a front end for the Edinburgh folks'
 * Pascal IE system.
 *
 * @author Jamie Nicolson
 * @author Chris Cox
 */

public class ClassifierWrapper {


  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    String trainFile = props.getProperty("trainFile");
    String testFileString = props.getProperty("testFile");
    String loadPath = props.getProperty("loadClassifier");
    String serializeTo = props.getProperty("serializeTo");
    String useCRFString = props.getProperty("useCRF");
    String outputPrefix = props.getProperty("outputPrefix");
    boolean useCRF = false;
    if (useCRFString != null && Boolean.parseBoolean(useCRFString)) {
      useCRF = true;
    }

    AbstractSequenceClassifier classifier;
    if (useCRF) {
      classifier = new CRFClassifier(props);
    } else {
      classifier = new CMMClassifier(props);
    }

    if (trainFile != null) {
      classifier.train(trainFile);
    }

    if (serializeTo != null) {
      classifier.serializeClassifier(serializeTo);
    }

    if (loadPath != null) {
      classifier.loadClassifier(loadPath);
      classifier.flags.setProperties(props);
    }

    if (testFileString != null) {
      String[] testFiles = testFileString.split(",");
      for (int f = 0; f < testFiles.length; ++f) {
        PrintStream output = System.out;
        if (outputPrefix != null) {
          output = new PrintStream(new FileOutputStream(testFiles[f] + '.' + outputPrefix));
        }
        if (useCRF) {
          testCRF((CRFClassifier) classifier, testFiles[f], output);
        } else {
          testCMM((CMMClassifier) classifier, testFiles[f], output);
        }
        if (outputPrefix != null) {
          output.close();
        }
      }
    }
  }

  private static void testCRF(CRFClassifier crf, String testFile, PrintStream out) {
    ObjectBank<List<CoreLabel>> docs = 
      crf.makeObjectBankFromFile(testFile, crf.makeReaderAndWriter());
    for (List<CoreLabel> doc : docs) {
      crf.classify(doc);
      crf.printProbsDocument(doc);
    }
  }


  private static void testCMM(CMMClassifier classifier, String testFile, PrintStream out) {
    ObjectBank<List<CoreLabel>> test = 
      classifier.makeObjectBankFromFile(testFile, 
                                        classifier.makeReaderAndWriter());
    for (List<CoreLabel> lines : test) {
      int numelems = lines.size();

      Iterator line = lines.iterator();
      for (int i = 0; i < numelems; ++i) {
        CoreLabel curLine = (CoreLabel) line.next();
        Counter c = classifier.scoresOf(lines, i);
        Distribution d = Distribution.getDistributionFromLogValues(c);
        CoreLabel token = lines.get(i);
        String bestlabel = (String) d.argmax();
        token.set(AnswerAnnotation.class, bestlabel);
        out.println(token.word() + ' ' + token.get(GoldAnswerAnnotation.class) + ' ' + bestlabel + ' ' + distributionString(d));
        //classifier.justificationOf(lines, i, outWriter);
      }
    }
  }

  public static String distributionString(Distribution d) {
    SortedSet sortedKeys = new TreeSet(d.keySet());
    Iterator iter = sortedKeys.iterator();
    StringBuilder rep = new StringBuilder();
    while (iter.hasNext()) {
      String key = (String) iter.next();
      double value = d.getCount(key);
      rep.append(key).append(':').append(value).append(' ');
    }
    return rep.toString();
  }

}
