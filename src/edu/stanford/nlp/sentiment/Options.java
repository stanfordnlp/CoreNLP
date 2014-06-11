package edu.stanford.nlp.sentiment;

import java.io.Serializable;

import edu.stanford.nlp.trees.TreebankLanguagePack;

public class Options implements Serializable {
  public double scalingForInit;

  public int randomSeed;

  public String wordVectors;

  public int numHid;

  public boolean lowercaseWordVectors;

  public TreebankLanguagePack langpack;

  /**
   * No symantic untying - use the same category for all categories.
   * This results in all nodes getting the same matrix.
   */
  public boolean simplifiedModel;
}
