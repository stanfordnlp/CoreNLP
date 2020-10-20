/*
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Trustees of Leland Stanford University
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.maxent;

import edu.stanford.nlp.io.PrintFile;


/**
 * This is a general class for a Problem to be solved by the MaxEnt toolkit.
 * There have to be experiments and features.
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class Problem {

  // todo [cdm 2018]: This class can probably just be written out of existence.

  public int exSize;
  public int fSize;

  /**
   * This is the training data.
   */
  public Experiments data;

  /**
   * These are the features.
   */
  public Features functions;

  public Problem(Experiments d, Features f) {
    data = d;
    functions = f;
    exSize = d.size();
    fSize = f.size();
  }

  public Problem() {
  }


  public void add(Feature f) {
    functions.add(f);
    fSize++;
  }


  public void removeLast() {
    functions.removeLast();
    fSize--;
  }

  public void print() {
    System.out.println(" Problem printing ");
    data.print();
    System.out.println(" Function printing ");
    for (int i = 0; i < fSize; i++) {
      functions.get(i).print();
    }
  }

  public void print(String filename) {
    try {
      PrintFile pf = new PrintFile(filename);
      pf.println(" Problem printing ");
      data.print(pf);
      pf.println(" Function printing ");
      for (int i = 0; i < fSize; i++) {
        functions.get(i).print(pf);
      }
    } catch (Exception e) {
      System.out.println("Exception in Problem.print()");
    }
  }

  /*
  // This is broken... it's not clear what it's supposed to do, but
  // class Experiments requires a "vArray" to function correctly.
  // Otherwise you just can't run ptilde on it.  If that makes sense
  // to you, please do everyone a favor and fix this test program or
  // at least document what those fields mean.  -horatio
  public static void main(String[] args) {
    double[] f1 = {0, 1, 1, 0, 1, 1};
    double[] f2 = {1, 0, 1, 1, 0, 1};
    Experiments gophers = new Experiments();
    for (int i = 0; i < 3; i++) {
      gophers.add(new Experiments());
    }
    for (int i = 0; i < 3; i++) {
      gophers.add(new Experiments());
    }
    gophers.ptilde();
    Index<IntPair> instanceIndex = gophers.createIndex();
    Features feats = new Features();
    feats.add(new Feature(gophers, f1, instanceIndex));
    feats.add(new Feature(gophers, f2, instanceIndex));
    Problem p = new Problem(gophers, feats);
    System.out.println(p.exSize);
    System.out.println(p.functions.get(1).ftilde());
  }
  */
}
