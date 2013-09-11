package edu.stanford.nlp.ie.ner;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.objectbank.ObjectBank;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.util.List;
import java.util.ListIterator;
import java.util.Iterator;
import java.util.Properties;

/**
 *
 * Ensures that serializing and deserializing the CMMClassifier doesn't degrade performance.
 *
 * @author Chris Cox
 */

public class TestCMMSerialization {

  /**
   * The main method relies on hardcoded paths and must be run on an nlp machine.
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {

    System.out.println("TESTING CMM CLASSIFIER....");
    String propsPathname = "/u/nlp/data/iedata/fruit.props";
    String serPathname = "/u/nlp/data/iedata/fruitClassifier.ser.gz";
    String testPathname = "/u/nlp/data/iedata/fruit.test";
    Properties p = new Properties();
    p.load(new BufferedInputStream(new FileInputStream(propsPathname)));
    CMMClassifier classifier = new CMMClassifier(p);
    classifier.train();

    classifier.serializeClassifier(serPathname);
    CMMClassifier deserClassifier = CMMClassifier.getClassifier(serPathname);
    ObjectBank<List<CoreLabel>> ob1 = 
      classifier.makeObjectBankFromFile(testPathname, 
                                        classifier.makeReaderAndWriter());
    ObjectBank<List<CoreLabel>> ob2 = 
      deserClassifier.makeObjectBankFromString(testPathname,
                                    deserClassifier.makeReaderAndWriter());
    boolean ok = true;
    Iterator<List<CoreLabel>> iter2 = ob2.iterator();
    for (List<CoreLabel> doc1 : ob1) {
      List<CoreLabel> doc2 = iter2.next();
      if (!compareLists(doc1, doc2)) {
        ok = false;
        break;
      }
    }
    if (ok) {
      System.out.println("Serialization test OK");
    }
  }

  static private boolean compareLists(List<CoreLabel> list1, List<CoreLabel> list2) {

    ListIterator<CoreLabel> iter1 = list1.listIterator();
    ListIterator<CoreLabel> iter2 = list1.listIterator();
    System.out.println("CLASSIFIER\tDESER_CLASSIFIER");
    while (iter1.hasNext()) {
      CoreLabel w1 = iter1.next();
      CoreLabel w2 = iter2.next();
      System.out.println(w1.get(AnswerAnnotation.class) + "\t\t" + w2.get(AnswerAnnotation.class) + "\t");
      if (!w1.get(AnswerAnnotation.class).equals(w2.get(AnswerAnnotation.class))) {
        System.err.println("Failed on word: " + w1);
        return false;
      }
    }
    return true;
  }
}
