package edu.stanford.nlp.sentiment;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.neural.SimpleTensor;
import edu.stanford.nlp.util.Generics;

/**
 * This tool is of very limited scope: it converts a model built with
 * the Matlab version of the code to the Java version of the code.  It
 * is useful to save this tool in case the format of the Java model
 * changes, in which case this will let us easily recreate it.
 * <br>
 * Another set of matrices is in <br>
 * /u/nlp/data/sentiment/binary/model_binary_best_asTextFiles/
 *
 * @author John Bauer
 */
public class ConvertMatlabModel  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(ConvertMatlabModel.class);

  private ConvertMatlabModel() {} // static class

  /** Will not overwrite an existing word vector if it is already there */
  public static void copyWordVector(Map<String, SimpleMatrix> wordVectors, String source, String target) {
    if (wordVectors.containsKey(target) || !wordVectors.containsKey(source)) {
      return;
    }

    log.info("Using wordVector " + source + " for " + target);

    wordVectors.put(target, new SimpleMatrix(wordVectors.get(source)));
  }

  /** <b>Will</b> overwrite an existing word vector */
  public static void replaceWordVector(Map<String, SimpleMatrix> wordVectors, String source, String target) {
    if (!wordVectors.containsKey(source)) {
      return;
    }

    wordVectors.put(target, new SimpleMatrix(wordVectors.get(source)));
  }

  public static SimpleMatrix loadMatrix(String binaryName, String textName) throws IOException {
    File matrixFile = new File(binaryName);
    if (matrixFile.exists()) {
      return SimpleMatrix.loadBinary(matrixFile.getPath());
    }

    matrixFile = new File(textName);
    if (matrixFile.exists()) {
      return NeuralUtils.loadTextMatrix(matrixFile);
    }

    throw new RuntimeException("Could not find either " + binaryName + " or " + textName);
  }

  public static void main(String[] args) throws IOException {
    String basePath = "/user/socherr/scr/projects/semComp/RNTN/src/params/";
    int numSlices = 25;

    boolean useEscapedParens = false;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-slices")) {
        numSlices = Integer.parseInt(args[argIndex + 1]);
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-path")) {
        basePath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-useEscapedParens")) {
        useEscapedParens = true;
        argIndex += 1;
      } else {
        log.info("Unknown argument " + args[argIndex]);
        System.exit(2);
      }
    }

    SimpleMatrix[] slices = new SimpleMatrix[numSlices];
    for (int i = 0; i < numSlices; ++i) {
      slices[i] = loadMatrix(basePath + "bin/Wt_" + (i + 1) + ".bin", basePath + "Wt_" + (i + 1) + ".txt");
    }
    SimpleTensor tensor = new SimpleTensor(slices);
    log.info("W tensor size: " + tensor.numRows() + "x" + tensor.numCols() + "x" + tensor.numSlices());

    SimpleMatrix W = loadMatrix(basePath + "bin/W.bin", basePath + "W.txt");
    log.info("W matrix size: " + W.numRows() + "x" + W.numCols());

    SimpleMatrix Wcat = loadMatrix(basePath + "bin/Wcat.bin", basePath + "Wcat.txt");
    log.info("W cat size: " + Wcat.numRows() + "x" + Wcat.numCols());

    SimpleMatrix combinedWV = loadMatrix(basePath + "bin/Wv.bin", basePath + "Wv.txt");
    log.info("Word matrix size: " + combinedWV.numRows() + "x" + combinedWV.numCols());

    File vocabFile = new File(basePath + "vocab_1.txt");
    if (!vocabFile.exists()) {
      vocabFile = new File(basePath + "words.txt");
    }
    List<String> lines = Generics.newArrayList();
    for (String line : IOUtils.readLines(vocabFile)) {
      lines.add(line.trim());
    }

    log.info("Lines in vocab file: " + lines.size());

    Map<String, SimpleMatrix> wordVectors = Generics.newTreeMap();

    for (int i = 0; i < lines.size() && i < combinedWV.numCols(); ++i) {
      String[] pieces = lines.get(i).split(" +");
      if (pieces.length == 0 || pieces.length > 1) {
        continue;
      }
      wordVectors.put(pieces[0], combinedWV.extractMatrix(0, numSlices, i, i+1));
      if (pieces[0].equals("UNK")) {
        wordVectors.put(SentimentModel.UNKNOWN_WORD, wordVectors.get("UNK"));
      }
    }

    // If there is no ",", we first try to look for an HTML escaping,
    // then fall back to "." as better than just a random word vector.
    // Same for "``" and ";"
    copyWordVector(wordVectors, "&#44", ",");
    copyWordVector(wordVectors, ".", ",");
    copyWordVector(wordVectors, "&#59", ";");
    copyWordVector(wordVectors, ".", ";");
    copyWordVector(wordVectors, "&#96&#96", "``");
    copyWordVector(wordVectors, "''", "``");

    if (useEscapedParens) {
      replaceWordVector(wordVectors, "(", "-LRB-");
      replaceWordVector(wordVectors, ")", "-RRB-");
    }

    RNNOptions op = new RNNOptions();
    op.numHid = numSlices;
    op.lowercaseWordVectors = false;

    if (Wcat.numRows() == 2) {
      op.classNames = new String[] { "Negative", "Positive" };
      op.equivalenceClasses = new int[][] { { 0 }, { 1 } }; // TODO: set to null once old models are updated
      op.numClasses = 2;
    }

    if (!wordVectors.containsKey(SentimentModel.UNKNOWN_WORD)) {
      wordVectors.put(SentimentModel.UNKNOWN_WORD, SimpleMatrix.random_DDRM(numSlices, 1, -0.00001, 0.00001, new Random()));
    }

    SentimentModel model = SentimentModel.modelFromMatrices(W, Wcat, tensor, wordVectors, op);
    model.saveSerialized("matlab.ser.gz");
  }
}
