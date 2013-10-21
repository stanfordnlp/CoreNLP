package edu.stanford.nlp.sentiment;

import java.io.Serializable;

public class Options implements Serializable {
  public double scalingForInit;

  public int randomSeed;

  public String wordVectors;

  public int numHid;

  public boolean lowercaseWordVectors;
}
