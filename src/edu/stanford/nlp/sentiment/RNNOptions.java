package edu.stanford.nlp.sentiment;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Random;

import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.StringUtils;

public class RNNOptions implements Serializable {

  // TODO [2014]: This should really be a long
  /**
   * The random seed the random number generator is initialized with.
   */
  public int randomSeed = new Random().nextInt();

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
   * No syntactic untying - use the same matrix/tensor for all categories.
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

  public static final String[] DEFAULT_CLASS_NAMES = { "Very negative", "Negative", "Neutral", "Positive", "Very positive" };
  public static final String[] BINARY_DEFAULT_CLASS_NAMES = { "Negative", "Positive" };
  public String[] classNames = DEFAULT_CLASS_NAMES;

  public static final int[][] APPROXIMATE_EQUIVALENCE_CLASSES = { {0, 1}, {3, 4} };
  public static final int[][] BINARY_APPROXIMATE_EQUIVALENCE_CLASSES = { {0}, {1} }; // almost an owl
  /**
   * The following option represents classes which can be treated as
   * equivalent when scoring.  There will be two separate scorings,
   * one with equivalence used and one without.  Default is set for
   * the sentiment project.
   */
  public int[][] equivalenceClasses = APPROXIMATE_EQUIVALENCE_CLASSES;

  public static final String[] DEFAULT_EQUIVALENCE_CLASS_NAMES = { "Negative", "Positive" };
  public String[] equivalenceClassNames = DEFAULT_EQUIVALENCE_CLASS_NAMES;

  public RNNTestOptions testOptions = new RNNTestOptions();

  // TODO: we can remove this if we reserialize all the models
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();

    if (testOptions == null) {
      testOptions = new RNNTestOptions();
    }
  }

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
    result.append("classNames=" + StringUtils.join(classNames, ",") + "\n");
    result.append("equivalenceClasses=");
    if (equivalenceClasses != null) {
      for (int i = 0; i < equivalenceClasses.length; ++i) {
        if (i > 0) result.append(";");
        for (int j = 0; j < equivalenceClasses[i].length; ++j) {
          if (j > 0) result.append(",");
          result.append(equivalenceClasses[i][j]);
        }
      }
    }
    result.append("\n");
    result.append("equivalenceClassNames=");
    if (equivalenceClassNames != null) {
      result.append(StringUtils.join(equivalenceClassNames, ","));
    }
    result.append("\n");
    result.append(trainOptions.toString());
    result.append(testOptions.toString());
    return result.toString();
  }

  public int setOption(String[] args, int argIndex) {
    if (args[argIndex].equalsIgnoreCase("-randomSeed")) {
      randomSeed = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-wordVectors")) {
      wordVectors = args[argIndex + 1];
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-unkWord")) {
      unkWord = args[argIndex + 1];
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-numHid")) {
      numHid = Integer.parseInt(args[argIndex + 1]);
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-numClasses")) {
      numClasses = Integer.parseInt(args[argIndex + 1]);
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
    } else if (args[argIndex].equalsIgnoreCase("-classNames")) {
      classNames = args[argIndex + 1].split(",");
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-equivalenceClasses")) {
      if (args[argIndex + 1].trim().length() == 0) {
        equivalenceClasses = null;
        return argIndex + 2;
      }

      String[] pieces = args[argIndex + 1].split(";");
      equivalenceClasses = new int[pieces.length][];
      for (int i = 0; i < pieces.length; ++i) {
        String[] values = pieces[i].split(",");
        equivalenceClasses[i] = new int[values.length];
        for (int j = 0; j < values.length; ++j) {
          equivalenceClasses[i][j] = Integer.valueOf(values[j]);
        }
      }

      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-equivalenceClassNames")) {
      if (args[argIndex + 1].trim().length() > 0) {
        equivalenceClassNames = args[argIndex + 1].split(",");
      } else {
        equivalenceClassNames = null;
      }
      return argIndex + 2;
    } else if (args[argIndex].equalsIgnoreCase("-binaryModel")) { // macro option
      numClasses = 2;
      classNames = BINARY_DEFAULT_CLASS_NAMES;
      // TODO: should we just make this null?
      equivalenceClasses = BINARY_APPROXIMATE_EQUIVALENCE_CLASSES;
      trainOptions.setOption(args, argIndex); // in case the trainOptions use binaryModel as well
      return argIndex + 1;
    } else {
      int newIndex = trainOptions.setOption(args, argIndex);
      if (newIndex == argIndex) {
        newIndex = testOptions.setOption(args, argIndex);
      }
      return newIndex;
    }
  }

  private static final long serialVersionUID = 1;

}
