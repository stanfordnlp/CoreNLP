package edu.stanford.nlp.sentiment;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.rnn.SimpleTensor;
import edu.stanford.nlp.util.Generics;

/**
 * This tool is of very limited scope: it converts a model built with
 * the Matlab version of the code to the Java version of the code.  It
 * is useful to save this tool in case the format of the Java model
 * changes, in which case this will let us easily recreate it.
 *
 * @author John Bauer
 */
public class ConvertMatlabModel {
  public static void main(String[] args) throws IOException {
    String basePath = "/user/socherr/scr/projects/semComp/RNTN/src/params/";
    SimpleMatrix[] slices = new SimpleMatrix[25];
    for (int i = 0; i < 25; ++i) {
      String filename = basePath + "bin/Wt_" + (i + 1) + ".bin";
      SimpleMatrix slice = SimpleMatrix.loadBinary(filename);
      slices[i] = slice;
    }
    SimpleTensor tensor = new SimpleTensor(slices);

    String Wfilename = basePath + "bin/W.bin";
    SimpleMatrix W = SimpleMatrix.loadBinary(Wfilename);
    
    String WcatFilename = basePath + "bin/Wcat.bin";
    SimpleMatrix Wcat = SimpleMatrix.loadBinary(WcatFilename);

    String WvFilename = basePath + "bin/Wv.bin";
    SimpleMatrix combinedWV = SimpleMatrix.loadBinary(WvFilename);

    String vocabFilename = basePath + "vocab_1.txt";
    List<String> lines = Generics.newArrayList();
    for (String line : IOUtils.readLines(vocabFilename)) {
      lines.add(line.trim());
    }

    Map<String, SimpleMatrix> wordVectors = Generics.newTreeMap();

    for (int i = 0; i < lines.size() - 1; ++i) { // leave out UNK
      String[] pieces = lines.get(i).split(" +");
      if (pieces.length > 1) {
        continue;
      }
      wordVectors.put(pieces[0], combinedWV.extractMatrix(0, 25, i, i+1));
    }

    wordVectors.put(",", new SimpleMatrix(wordVectors.get(".")));
    wordVectors.put(";", new SimpleMatrix(wordVectors.get(".")));
    wordVectors.put("``", new SimpleMatrix(wordVectors.get("''")));

    RNNOptions op = new RNNOptions();
    op.lowercaseWordVectors = false;

    wordVectors.put(SentimentModel.UNKNOWN_WORD, SentimentModel.randomWordVector(25, new Random()));

    SentimentModel model = SentimentModel.modelFromMatrices(W, Wcat, tensor, wordVectors, op);
    model.saveSerialized("matlab.ser.gz");
  }
}
