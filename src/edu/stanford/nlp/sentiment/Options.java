package edu.stanford.nlp.sentiment;

import java.io.Serializable;

import edu.stanford.nlp.trees.TreebankLanguagePack;

public class Options implements Serializable {
  public int randomSeed;

  /**
   * Filename for the word vectors
   */
  public String wordVectors;

  /**
   * In the wordVectors file, what word represents unknown?
   */
  public String unkWord;

  /**
   * Size of vectors to use.  Must be at most the size of the vectors
   * in the word vector file.  If a smaller size is specified, vectors
   * will be truncated.
   */
  public int numHid = 25;

  /**
   * Number of classes to build the RNN for
   */
  public int numClasses = 5;

  public boolean lowercaseWordVectors;

  public TreebankLanguagePack langpack;

  /**
   * No symantic untying - use the same category for all categories.
   * This results in all nodes getting the same matrix.
   */
  public boolean simplifiedModel;

  public TrainOptions trainOptions;
}
