package edu.stanford.nlp.optimization;

import edu.stanford.nlp.maxent.Convert;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * @author Galen Andrew
 */
public class ResultStoringFloatMonitor implements FloatFunction {
  int i = 0;
  final int outputFreq;
  final String filename;

  public ResultStoringFloatMonitor(int outputFreq, String filename) {
    if (filename.lastIndexOf('.') >= 0) {
      this.filename = filename.substring(0, filename.lastIndexOf('.')) + ".fdat";
    } else {
      this.filename = filename + ".fdat";
    }
    this.outputFreq = outputFreq;
  }

  public float valueAt(float[] x) {
    if (++i % outputFreq == 0) {
      System.err.print("Storing interim (float) weights to " + filename + " ... ");
      try {
        DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(filename))));
        Convert.saveFloatArr(dos, x);
        dos.close();
      } catch (IOException e) {
        System.err.println("ERROR!");
        return 1;
      }
      System.err.println("DONE.");
    }
    return 0;
  }

  public int domainDimension() {
    return 0;
  }
}
