package edu.stanford.nlp.ie.hmm;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;

/**
 * Utility for viewing the contents of serialized HMM files.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see #main
 */
public class HMMViewer {
  // private constructor to prevent direct instantiation
  private HMMViewer() {
  }

  /**
   * Loads a serialized HMM and prints out info about it.
   * <p><tt>Usage: java HMMViewer hmmfile</tt>
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Usage: java HMMViewer hmmfile");
      System.exit(1);
    }

    // ensures the serialized file is readable
    File hmmFile = new File(args[0]);
    if (!hmmFile.canRead()) {
      System.err.println("ERROR: Cannot read " + hmmFile);
      System.exit(1);
    }

    // loads the HMM
    try {
      HMM hmm = (HMM) new ObjectInputStream(new FileInputStream(hmmFile)).readObject();
      hmm.printProbs();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
