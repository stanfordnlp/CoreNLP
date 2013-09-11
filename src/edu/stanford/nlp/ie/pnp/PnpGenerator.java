package edu.stanford.nlp.ie.pnp;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Properties;

/**
 * Generates novel PNPs using a trained PnpClassifier.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see #main
 */
public class PnpGenerator {
  private PnpClassifier pnpc; // used to generate
  private static final int numLinesPerCategory = 100; // number of PNPs to generate per category

  /**
   * Constructs a new PnpClassifier using the given PnpClassifier to generate
   * novel PNPs.
   */
  public PnpGenerator(PnpClassifier pnpc) {
    this.pnpc = pnpc;

    for (int c = 1; c <= pnpc.getNumCategories(); c++) {
      System.out.println("Generating for category " + c);
      for (int i = 0; i < numLinesPerCategory; i++) {
        String line = pnpc.generateLine(c);
        double score = pnpc.getScore(line, c);
        ScoredLine sl = new ScoredLine(line, score);
        System.out.println(sl);
      }
    }
  }

  /**
   * Generates random novel PNPs and prints them to stdout.
   * <p><tt>Usage: java PnpGenerator serialized_pnpc [trainingFile] [propertiesFile]</tt></p>
   * If a training file is provided, a new PnpClassifier is trained and serialized
   * as <tt>serialized_pnpc</tt> before generating the examples (properties file
   * is used if provided). If only  the first
   * argument is provided, the given serialized PnpClassifier is loaded and used.
   * This way you can just train once and generate many times quickly.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("Usage: java PnpGenerator serialized_pnpc [trainingFile] [propertiesFile]");
      System.exit(-1);
    }

    try {
      PnpClassifier pnpc;

      if (args.length > 1) {
        System.err.println("Creating new PnpClassifier...");
        Properties props = (args.length == 3 ? PnpClassifier.loadProperties(args[2]) : null);
        pnpc = new PnpClassifier(args[1], props);
        ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(args[0]));
        oos.writeObject(pnpc);
        oos.flush();
        oos.close();
      } else {
        System.err.println("Loading serialized PnpClassifier...");
        ObjectInputStream ois = new ObjectInputStream(new FileInputStream(args[0]));
        pnpc = (PnpClassifier) ois.readObject();
      }

      new PnpGenerator(pnpc);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Generated PNP and its associated score.
   */
  private class ScoredLine implements Comparable {
    String line;
    double score;

    public ScoredLine(String line, double score) {
      this.line = line;
      this.score = score;
    }

    public int compareTo(Object o) {
      ScoredLine s = (ScoredLine) o;
      double d = s.getScore();
      if (score > d) {
        return (1);
      }
      if (d > score) {
        return (-1);
      }
      return (0);
    }

    public String getLine() {
      return (line);
    }

    public double getScore() {
      return (score);
    }

    @Override
    public String toString() {
      return (score + "\t" + line);
    }
  }
}
