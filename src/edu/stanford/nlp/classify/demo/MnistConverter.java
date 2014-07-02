package edu.stanford.nlp.classify.demo;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;


/** This class converts the MNIST data set from Yann LeCun's distributed binary
 *  form to the tab-separated column format of ColumnDataClassifier.
 *  Site for data: http://yann.lecun.com/exdb/mnist/
 *
 *  @author Christopher Manning
 */
public class MnistConverter {

  private MnistConverter() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 4) {
      System.err.println("Usage: MnistConverter dataFile labelFile outFile propsFile");
      return;
    }

    DataInputStream xStream = new DataInputStream(new FileInputStream(args[0]));
    DataInputStream yStream = new DataInputStream(new FileInputStream(args[1]));
    PrintWriter oStream = new PrintWriter(new FileWriter(args[2]));
    PrintWriter pStream = new PrintWriter(new FileWriter(args[3]));

    int xMagic = xStream.readInt();
    if (xMagic != 2051) throw new RuntimeException("Bad format of xStream");
    int yMagic = yStream.readInt();
    if (yMagic != 2049) throw new RuntimeException("Bad format of yStream");
    int xNumImages = xStream.readInt();
    int yNumLabels = yStream.readInt();
    if (xNumImages != yNumLabels) throw new RuntimeException("x and y sizes don't match");
    System.err.println("Images and label file both contain " + xNumImages + " entries.");
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
    System.err.println("Converted.");
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
