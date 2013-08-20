package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * Trains HMM and saves it as a serialized object.
 * Gives the file the name targetField.hmm or the supplied name.
 *
 * @author Jim McFadden
 * @author Christopher Manning
 */
public class Trainer {

  /**
   * This isn't a class that can be instantiated.
   */
  private Trainer() {
  }


  /**
   * This is a command line utility that builds a serialized extractor
   * for a field.
   *
   * @param args The usage is <code>java edu.stanford.nlp.ie.hmm.Trainer
   *             trainingFile targetField [hmmFile]</code><br>
   *             It saves the built hmm in a file <code>targetField.hmm</code> unless
   *             the third parameter is supplied.
   */
  public static void main(String[] args) {
    boolean s1 = false;
    boolean s2 = false;
    boolean sd = false;
    int j;
    for (j = 0; j < args.length && args[j].startsWith("-"); j++) {
      if (args[j].equals("-s1")) {
        s1 = true;
      } else if (args[j].equals("-s2")) {
        s2 = true;
      } else if (args[j].equals("-sd")) {
        sd = true;
      } else {
        System.err.println("Unknown option: " + args[j]);
      }
    }
    if (j + 2 > args.length) {
      System.err.println("Usage: java edu.stanford.nlp.ie.hmm.Trainer " + "[-s1|-s2|-sd] dataFile targetField [hmmFile]");
      return;
    }

    String trainFile = args[j++];
    String targetField = args[j++];
    Corpus train = new Corpus(trainFile, targetField);

    Structure struc = null;
    if (s1) {
      struc = new Structure();
      struc.giveEmpty();
      struc.addPrefix(3);
      struc.addTarget(2);
      struc.addTarget(2);
      struc.addSuffix(3);
    } else if (s2) {
      Structure struct = new Structure();
      struct.giveDefault();
      struct.lengthenTarget(0);
      struct.lengthenTarget(0);
    } else if (sd) {
      struc = new Structure();
      struc.giveDefault();
    }

    HMM hmm = new HMM(struc, HMM.REGULAR_HMM);
    hmm.train(train);

    String hmmFilename;
    if (j < args.length) {
      hmmFilename = args[j++];
    } else {
      hmmFilename = targetField + ".hmm";
    }
    try {
      FileOutputStream fos = new FileOutputStream(hmmFilename);
      ObjectOutputStream out = new ObjectOutputStream(fos);
      out.writeObject(hmm);
      out.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    }
  }

}
