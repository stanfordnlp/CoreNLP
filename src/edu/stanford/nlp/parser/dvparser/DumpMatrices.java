package edu.stanford.nlp.parser.dvparser; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.FileSystem;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.util.TwoDimensionalMap;

import org.ejml.simple.SimpleMatrix;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

/**
 * Dump out the matrices in a DVModel to a given directory in text format.
 * <br>
 * Sample command line:
 * <br>
 * <code>
 * java -model &lt;modelname&gt; -output &lt;directory&gt;
 * </code>
 *
 * @author John Bauer
 */
public class DumpMatrices  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DumpMatrices.class);
  /**
   * Output some help and exit
   */
  public static void help() {
    log.info("-model : DVModel to load");
    log.info("-output : where to dump the matrices");
    System.exit(2);
  }

  public static void dumpMatrix(String filename, SimpleMatrix matrix) throws IOException {
    String matrixString = matrix.toString();
    int newLine = matrixString.indexOf("\n");
    if (newLine >= 0) {
      matrixString = matrixString.substring(newLine + 1);
    }
    FileWriter fout = new FileWriter(filename);
    BufferedWriter bout = new BufferedWriter(fout);
    bout.write(matrixString);
    bout.close();
    fout.close();
  }

  public static void main(String[] args) throws IOException {
    String modelPath = null;
    String outputDir = null;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-model")) {
        modelPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-output")) {
        outputDir = args[argIndex + 1];
        argIndex += 2;
      } else {
        log.info("Unknown argument " + args[argIndex]);
        help();
      }
    }

    if (outputDir == null || modelPath == null) {
      help();
    }

    File outputFile = new File(outputDir);
    FileSystem.checkNotExistsOrFail(outputFile);

    FileSystem.mkdirOrFail(outputFile);

    LexicalizedParser parser = LexicalizedParser.loadModel(modelPath);
    DVModel model = DVParser.getModelFromLexicalizedParser(parser);

    String binaryWDir = outputDir + File.separator + "binaryW";
    FileSystem.mkdirOrFail(binaryWDir);
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : model.binaryTransform) {
      String filename = binaryWDir + File.separator + entry.getFirstKey() + "_" + entry.getSecondKey() + ".txt";
      dumpMatrix(filename, entry.getValue());
    }

    String binaryScoreDir = outputDir + File.separator + "binaryScore";
    FileSystem.mkdirOrFail(binaryScoreDir);
    for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : model.binaryScore) {
      String filename = binaryScoreDir + File.separator + entry.getFirstKey() + "_" + entry.getSecondKey() + ".txt";
      dumpMatrix(filename, entry.getValue());
    }

    String unaryWDir = outputDir + File.separator + "unaryW";
    FileSystem.mkdirOrFail(unaryWDir);
    for (Map.Entry<String, SimpleMatrix> entry : model.unaryTransform.entrySet()) {
      String filename = unaryWDir + File.separator + entry.getKey() + ".txt";
      dumpMatrix(filename, entry.getValue());
    }

    String unaryScoreDir = outputDir + File.separator + "unaryScore";
    FileSystem.mkdirOrFail(unaryScoreDir);
    for (Map.Entry<String, SimpleMatrix> entry : model.unaryScore.entrySet()) {
      String filename = unaryScoreDir + File.separator + entry.getKey() + ".txt";
      dumpMatrix(filename, entry.getValue());
    }

    String embeddingFile = outputDir + File.separator + "embeddings.txt";
    FileWriter fout = new FileWriter(embeddingFile);
    BufferedWriter bout = new BufferedWriter(fout);
    for (Map.Entry<String, SimpleMatrix> entry : model.wordVectors.entrySet()) {
      bout.write(entry.getKey());
      SimpleMatrix vector = entry.getValue();
      for (int i = 0; i < vector.numRows(); ++i) {
        bout.write("  " + vector.get(i, 0));
      }
      bout.write("\n");
    }
    bout.close();
    fout.close();
  }
}
