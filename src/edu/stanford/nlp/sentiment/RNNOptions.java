package edu.stanford.nlp.sentiment;

import java.io.Serializable;
import java.util.Random;

import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TreebankLanguagePack;

public class RNNOptions implements Serializable {
  /**
   * The random seed the random number generator is initialized with.  
   */
  public int randomSeed = (new Random()).nextInt();

  /**
   * Filename for the word vectors
   */
  public String wordVectors;

  /**
   * In the wordVectors file, what word represents unknown?
   */
  public String unkWord = "UNK";

  /**
   * By default, initialize random word vectors instead of reading
   * from a file
   */
  public boolean randomWordVectors = true;

  /**
   * Size of vectors to use.  Must be at most the size of the vectors
   * in the word vector file.  If a smaller size is specified, vectors
   * will be truncated.
   */
  public int numHid = 30;

  /**
   * Number of classes to build the RNN for
   */
  public int numClasses = 5;

  public boolean lowercaseWordVectors = true;

  public boolean useTensors = true;

  // TODO: add an option to set this to some other language pack
  public TreebankLanguagePack langpack = new PennTreebankLanguagePack();

  /**
   * No symantic untying - use the same category for all categories.
   * This results in all nodes getting the same matrix.
   */
  public boolean simplifiedModel = true;

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
    } else if (args[argIndex].equalsIgnoreCase("-randomWordVectors")) {
      randomWordVectors = true;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-norandomWordVectors")) {
      randomWordVectors = false;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-simplifiedModel")) {
      simplifiedModel = true;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-nosimplifiedModel")) {
      simplifiedModel = false;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-useTensors")) {
      useTensors = true;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-nouseTensors")) {
      useTensors = false;
      return argIndex + 1;
    } else {
      return trainOptions.setOption(args, argIndex);
    }
  }
}
