/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 * @author Kristina Toutanova
 * @version 1.0
 */

package edu.stanford.nlp.maxent;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;

import java.io.BufferedReader;
import java.io.FileReader;

/**
 * An ArrayList of BinaryFeature
 */
public class BinaryFeatures extends Features {
  private static int maxValue = 1000;

  public BinaryFeatures() {
  }


  /**
   * Reads the features from a file.
   */
  public BinaryFeatures(String filename, Experiments domain) {

    Exception e1 = new Exception("Incorrect data file format!");
    Index<IntPair> instanceIndex = domain.createIndex();
    try {
      BufferedReader in = new BufferedReader(new FileReader(filename));
      String s;
      while (true) {
        s = in.readLine();
        if (s.equals("<features>")) {
          break;
        }
      }
      if (s == null) {
        throw e1;
      }
      s = in.readLine();
      if (!s.startsWith("<fSize>")) {
        throw e1;
      }
      if (!s.endsWith("</fSize>")) {
        throw e1;
      }
      int index1 = s.indexOf(">");
      int index2 = s.lastIndexOf("<");
      String fSt = s.substring(index1 + 1, index2);
      System.out.println(fSt);
      int number = Integer.parseInt(fSt);
      System.out.println("fSize is " + number);
      String line;
      int[] arrIndexes = new int[maxValue];
      for (int f = 0; f < number; f++) {
        line = in.readLine();

        int indSp = -1;
        int current = 0;
        while ((indSp = line.indexOf(" ")) > -1) {
          int x = Integer.parseInt(line.substring(0, indSp));
          line = line.substring(indSp + 1);
          indSp = line.indexOf(" ");
          if (indSp == -1) {
            indSp = line.length();
          }
          int y = Integer.parseInt(line.substring(0, indSp));
          if (indSp < line.length()) {
            line = line.substring(indSp + 1);
          }
          arrIndexes[current] = instanceIndex.indexOf(new IntPair(x, y));
          current++;
        }
        int[] indValues = new int[current];
        for (int j = 0; j < current; j++) {
          indValues[j] = arrIndexes[j];
        }
        BinaryFeature bf = new BinaryFeature(domain, indValues, instanceIndex);
        this.add(bf);

      }// for f

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


}
