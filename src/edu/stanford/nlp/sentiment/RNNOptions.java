package edu.stanford.nlp.sentiment;

import java.io.Serializable;

import edu.stanford.nlp.trees.TreebankLanguagePack;

public class RNNOptions implements Serializable {
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

  public RNNTrainOptions trainOptions = new RNNTrainOptions();

  public int setOption(String[] args, int argIndex) {
    if (args[argIndex].equalsIgnoreCase("-seed")) {
      randomSeed = Integer.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-wordVectors")) {
      wordVectors = args[argIndex + 1];
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-unkWord")) {
      unkWord = args[argIndex] + 1;
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-numHid")) {
      numHid = Integer.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-numClasses")) {
      numClasses = Integer.valueOf(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-lowercaseWordVectors")) {
      lowercaseWordVectors = true;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-nolowercaseWordVectors")) {
      lowercaseWordVectors = false;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-simplifiedModel")) {
      simplifiedModel = true;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-nosimplifiedModel")) {
      simplifiedModel = false;
      return argIndex + 1;
    } else {
      return trainOptions.setOption(args, argIndex);
    }
  }
}
