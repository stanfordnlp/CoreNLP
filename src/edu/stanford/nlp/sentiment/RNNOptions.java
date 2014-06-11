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
  public int numHid = 25;

  /**
   * Number of classes to build the RNN for
   */
  public int numClasses = 5;

  public boolean lowercaseWordVectors = false;

  public boolean useTensors = true;

  // TODO: add an option to set this to some other language pack
  public TreebankLanguagePack langpack = new PennTreebankLanguagePack();

  /**
   * No symantic untying - use the same category for all categories.
   * This results in all nodes getting the same matrix (and tensor,
   * where applicable)
   */
  public boolean simplifiedModel = true;

  /**
   * If this option is true, then the binary and unary classification
   * matrices are combined.  Only makes sense if simplifiedModel is true.
   * If combineClassification is set to true, simplifiedModel will
   * also be set to true.  If simplifiedModel is set to false, this
   * will be set to false.
   */
  public boolean combineClassification = true;

  public RNNTrainOptions trainOptions = new RNNTrainOptions();

  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    result.append("GENERAL OPTIONS\n");
    result.append("randomSeed=" + randomSeed + "\n");
    result.append("wordVectors=" + wordVectors + "\n");
    result.append("unkWord=" + unkWord + "\n");
    result.append("randomWordVectors=" + randomWordVectors + "\n");
    result.append("numHid=" + numHid + "\n");
    result.append("numClasses=" + numClasses + "\n");
    result.append("lowercaseWordVectors=" + lowercaseWordVectors + "\n");
    result.append("useTensors=" + useTensors + "\n");
    result.append("simplifiedModel=" + simplifiedModel + "\n");
    result.append("combineClassification=" + combineClassification + "\n");
    result.append(trainOptions.toString());
    return result.toString();
  }

  public int setOption(String[] args, int argIndex) {
    if (args[argIndex].equalsIgnoreCase("-randomSeed")) {
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
      combineClassification = false;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-combineClassification")) {
      combineClassification = true;
      simplifiedModel = true;
      return argIndex + 1;
    } else if (args[argIndex].equalsIgnoreCase("-nocombineClassification")) {
      combineClassification = false;
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

  private static final long serialVersionUID = 1;
}
