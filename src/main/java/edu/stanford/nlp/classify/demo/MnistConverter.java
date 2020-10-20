package edu.stanford.nlp.classify.demo;

import java.io.*;

import edu.stanford.nlp.io.IOUtils;


import edu.stanford.nlp.util.logging.Redwood;


/** This class converts the MNIST data set from Yann LeCun's distributed binary
 *  form to the tab-separated column format of ColumnDataClassifier.
 *  The converted files are huge (100MB of train data) compared to the compact original format.
 *  Site for data: http://yann.lecun.com/exdb/mnist/ .
 *  Commands:
 *  java edu.stanford.nlp.classify.demo.MnistConverter train-images-idx3-ubyte.gz train-labels-idx1-ubyte.gz MNIST-train.tsv MNIST.prop
 *  java edu.stanford.nlp.classify.demo.MnistConverter t10k-images-idx3-ubyte.gz  t10k-labels-idx1-ubyte.gz MNIST-test.tsv /dev/null
 *  java -Xrunhprof:cpu=samples,depth=12,interval=2,file=hprof.txt edu.stanford.nlp.classify.ColumnDataClassifier -prop MNIST.prop -trainFile MNIST-train.tsv -testFile MNIST-test.tsv
 *  ...
 *  Accuracy/micro-averaged F1: 0.92140
 *  Macro-averaged F1: 0.92025
 *
 *  @author Christopher Manning
 */
public class MnistConverter {

  final static Redwood.RedwoodChannels logger = Redwood.channels(MnistConverter.class);

  private MnistConverter() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 4) {
      logger.info("Usage: MnistConverter dataFile labelFile outFile propsFile");
      return;
    }

    DataInputStream xStream = IOUtils.getDataInputStream(args[0]);
    DataInputStream yStream = IOUtils.getDataInputStream(args[1]);
    PrintWriter oStream = new PrintWriter(new FileWriter(args[2]));
    PrintWriter pStream = new PrintWriter(new FileWriter(args[3]));

    int xMagic = xStream.readInt();
    if (xMagic != 2051) throw new RuntimeException("Bad format of xStream");
    int yMagic = yStream.readInt();
    if (yMagic != 2049) throw new RuntimeException("Bad format of yStream");
    int xNumImages = xStream.readInt();
    int yNumLabels = yStream.readInt();
    if (xNumImages != yNumLabels) throw new RuntimeException("x and y sizes don't match");
    logger.info("Images and label file both contain " + xNumImages + " entries.");
    int xRows = xStream.readInt();
    int xColumns = xStream.readInt();
    for (int i = 0; i < xNumImages; i++) {
      int label = yStream.readUnsignedByte();
      int[] matrix = new int[xRows*xColumns];
      for (int j = 0; j < xRows*xColumns; j++) {
        matrix[j] = xStream.readUnsignedByte();
      }
      oStream.print(label);
      for (int k : matrix) {
        oStream.print('\t');
        oStream.print(k);
      }
      oStream.println();
    }
    logger.info("Converted.");
    xStream.close();
    yStream.close();
    oStream.close();
    // number from 1; column 0 is the class
    pStream.println("goldAnswerColumn = 0");
    pStream.println("useClassFeature = true");
    pStream.println("sigma = 10"); // not optimized, but weak regularization seems appropriate when much data, few features
    for (int j = 0; j < xRows*xColumns; j++) {
      pStream.println((j+1) + ".realValued = true");
    }
    pStream.close();
  }

}
