/*
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.maxent;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

/**
 * An ArrayList of Feature.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class Features {

  // todo [cdm 2018]: Probably this class can just be removed! Use ArrayList

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Features.class);

  private ArrayList<Feature> f = new ArrayList<>();
  private static final int maxValue = 11000000;

  public Features() {
  }

  public void add(Feature m) {
    f.add(m);
  }

  public void removeLast() {
    f.remove(f.size() - 1);

  }

  public Feature get(int index) {
    return f.get(index);
  }

  public int size() {
    return f.size();
  }

  public Experiments domain() {
    get(0);
    return Feature.domain;
  }

  public void clean() {

  }

  public void print() {
    for (int i = 0; i < size(); i++) {
      get(i).print();
    }
  }


  /**
   * reads in the features from a file, having already read the
   * experiments
   */
  public Features(String filename, Experiments domain) {

    Exception e1 = new Exception("Incorrect data file format!");
    Index<IntPair> instanceIndex = domain.createIndex();
    try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
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
      int[] arrIndexes = new int[maxValue];
      double[] arrValues = new double[maxValue];

      for (int f = 0; f < number; f++) {
        String line = in.readLine();

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
          line = line.substring(indSp + 1);
          indSp = line.indexOf(" ");
          if (indSp == -1) {
            indSp = line.length();
          }
          double val = Double.parseDouble(line.substring(0, indSp));

          if (indSp < line.length()) {
            line = line.substring(indSp + 1);
          }
          arrIndexes[current] = instanceIndex.indexOf(new IntPair(x, y));
          arrValues[current] = val;
          current++;
        }
        int[] indValues = new int[current];
        double[] values = new double[current];
        for (int j = 0; j < current; j++) {
          indValues[j] = arrIndexes[j];
          values[j] = arrValues[j];
        }
        Feature bf = new Feature(domain, indValues, values, instanceIndex);
        this.add(bf);

      }// for f

    } catch (Exception e) {
      log.warn(e);
    }
  }

}
