package edu.stanford.nlp.wsd.synsense;

import edu.stanford.nlp.parser.lexparser.Lexicon;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Class Fixer
 *
 * @author Teg Grenager
 */
public class Fixer {

  private Fixer() {}

  /**
   * Usage: java edu.stanford.nlp.wsd.synsense.Fixer
   * Takes all *.ser files in the current directory and fixes them and outputs new ones.
   *
   */
  public static void main(String[] args) {
    // get all ser files from the current directory, that's how we'll get the word list
    File currentDir = new File(".");
    String[] filenames = currentDir.list();
    SynSense.words = new ArrayList<String>();
    System.out.println("loading data for words: ");
    for (int i = 0; i < filenames.length; i++) {
      String filename = filenames[i];
      if (filename.endsWith(".ser")) {
        String word = filename.substring(0, filename.length() - 4);
        SynSense.words.add(word);
        System.out.print(word + " ");
      }
    }
    System.out.println();
    SynSense.senseTrainData = new ArrayList[SynSense.words.size()];
    SynSense.senseTestData = new ArrayList[SynSense.words.size()];
    SynSense.subcatTrainData = new ArrayList[SynSense.words.size()];
    SynSense.subcatTestData = new ArrayList[SynSense.words.size()];
    for (int i = 0; i < SynSense.words.size(); i++) {
      String word = SynSense.words.get(i);
      SynSense.readInstancesOfOneWordFromSerializedFile(word, i);
      fixInstances(SynSense.senseTrainData[i]);
      fixInstances(SynSense.senseTestData[i]);
      fixInstances(SynSense.subcatTrainData[i]);
      fixInstances(SynSense.subcatTestData[i]);
      writeInstancesOfOneWordToSerializedFile(word, i);
    }

  }

  private static void fixInstances(List list) {
    for (Iterator iter = list.iterator(); iter.hasNext();) {
      Instance ins = (Instance) iter.next();
      Object last = ins.sentence.get(ins.sentence.size() - 1);
      //      System.out.println(last);
      if (!last.equals(Lexicon.BOUNDARY)) {
        throw new RuntimeException();
      }
      ins.sentence.remove(ins.sentence.size() - 1);
    }
  }

  private static void writeInstancesOfOneWordToSerializedFile(String word, int i) {
    // get parsed instances from file
    System.out.println("Writing instances for word " + word);
    try {
      FileOutputStream fos = new FileOutputStream(word + ".ser");
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      System.out.println("Writing sense train instances");
      oos.writeObject(SynSense.senseTrainData[i]);
      System.out.println("Writing sense test instances");
      oos.writeObject(SynSense.senseTestData[i]);
      System.out.println("Writing subcat train instances");
      oos.writeObject(SynSense.subcatTrainData[i]);
      System.out.println("Writing subcat test instances");
      oos.writeObject(SynSense.subcatTestData[i]);
      oos.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
