/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.maxent;

import edu.stanford.nlp.io.InDataStreamFile;
import edu.stanford.nlp.io.OutDataStreamFile;
import edu.stanford.nlp.maxent.iis.LambdaSolve;

import java.util.HashMap;


/** This is for feature selection. Not fully integrated yet.
 *  @author Kristina Toutanova
 *  @version 1.0
 */
public final class MaxEntModel {

  public Features feats;
  public Experiments data;
  Problem p;
  Features activeFeats;
  public LambdaSolve prob;
  boolean[] active;
  double gainerror;
  double gainlimit;
  public int[] correspondences; // correspondences[i] is the new feature number of the old feature i
  public HashMap<Object, Integer> fAssociations = new HashMap<Object, Integer>(); // ugh!: it's Integer,Integer in this class but String,Integer in WekaProblemSolverCombination!
  public static int numnonexistent = 0;
  public static int numtotal = 0;


  public MaxEntModel(Experiments d, Features fs, double gainerr, double gainlmt) {
    gainerror = gainerr;
    gainlimit = gainlmt;
    activeFeats = new Features();
    feats = fs;
    data = d;
    p = new Problem(data, activeFeats);
    prob = new LambdaSolve(p, 0.0001, 0.00001);
    prob.improvedIterative();
    active = new boolean[feats.size()];
    correspondences = new int[feats.size()];
    for (int j = 0; j < correspondences.length; j++) {
      correspondences[j] = -1;
    }
  }


  public MaxEntModel() {
  }


  public double getLambda(int fNo) {
    numtotal++;
    Integer val = fAssociations.get(Integer.valueOf(fNo));
    if (val == null) {
      numnonexistent++;
      return 0;
    }
    return prob.lambda[val.intValue()];
  }


  public void FindModel() {
    correspondences = new int[feats.size()];
    for (int j = 0; j < correspondences.length; j++) {
      correspondences[j] = -1;
    }
    while (true) {
      int nextFeat = -1;
      double maxGain = -1.0;
      double temp = 0.0;

      for (int i = 0; i < feats.size(); i++) {
        if (!active[i]) {
          temp = prob.GainCompute(feats.get(i), gainerror);
          //System.out.println(" gain form feature "+ i+" : "+temp);
          if (temp > maxGain) {
            maxGain = temp;
            nextFeat = i;
          }
        }
      }
      if ((nextFeat > -1) && (maxGain > gainlimit)) {
        //activeFeats.add(feats.get(nextFeat));
        active[nextFeat] = true;
        p.add(feats.get(nextFeat));
        correspondences[nextFeat] = p.fSize - 1;
        prob = new LambdaSolve(p, 0.0001, 0.00001);
        prob.improvedIterative(50);
        //prob.print();
      } else {
        return;
      }
    }
  }


  public int addFeature(double gainlimit) {

    int nextFeat = -1;
    double maxGain = -1.0;
    double temp = 0.0;

    for (int i = 0; i < feats.size(); i++) {
      if (!active[i]) {
        temp = prob.GainCompute(feats.get(i), gainerror);
        //System.out.println(" gain form feature "+ i+" : "+temp);
        if (temp > maxGain) {
          maxGain = temp;
          nextFeat = i;
        }
      }
    }
    if ((nextFeat > -1) && (maxGain > gainlimit)) {
      //activeFeats.add(feats.get(nextFeat));
      active[nextFeat] = true;
      p.add(feats.get(nextFeat));
      correspondences[nextFeat] = p.fSize - 1;
      prob = new LambdaSolve(p, 0.0001, 0.00001);
      prob.improvedIterative();
      return nextFeat;
      //prob.print();
    } else {
      return -1;
    }

  }


  public void FindModel(double gainlimit) {
    correspondences = new int[feats.size()];
    for (int j = 0; j < correspondences.length; j++) {
      correspondences[j] = -1;
    }
    int iters = 0;

    if (gainlimit == 0) {

      for (int i = 0; i < feats.size(); i++) {

        int nextFeat = i;
        active[nextFeat] = true;
        p.add(feats.get(nextFeat));
        correspondences[nextFeat] = p.fSize - 1;
      }

      prob = new LambdaSolve(p, 0.0001, 0.00001);
      return;
    }

    while (iters < 1) {
      iters++;
      int nextFeat = -1;
      double temp = 0.0;

      for (int i = 0; i < feats.size(); i++) {
        if (!active[i]) {
          temp = prob.GainCompute(feats.get(i), gainerror);
          //System.out.println(" gain form feature "+ i+" : "+temp);
          if (temp > gainlimit) {
            nextFeat = i;
            System.out.println(" Adding " + nextFeat + " " + temp);
            //activeFeats.add(feats.get(nextFeat));
            active[nextFeat] = true;
            p.add(feats.get(nextFeat));
            correspondences[nextFeat] = p.fSize - 1;
            prob = new LambdaSolve(p, 0.0001, 0.00001);
            prob.improvedIterative(50);
            //prob.print();
          }// gain enough

        }// not active
      }// for
    }//while
    prob = new LambdaSolve(p, 0.0001, 0.00001);
    prob.improvedIterative(300);

  }


  public void print() {
    for (int i = 0; i < data.xSize; i++) {
      for (int j = 0; j < data.ySize; j++) {
        System.out.println("p(" + j + ", " + i + ") " + prob.pcond(j, i));
      }
    }
  }


  public void save(String filename) {
    try {
      OutDataStreamFile rf = new OutDataStreamFile(filename);
      Object[] keys = fAssociations.keySet().toArray();
      rf.writeInt(keys.length);
      for (int i = 0; i < keys.length; i++) {
        byte[] bytes = keys[i].toString().getBytes();
        rf.writeInt(bytes.length);
        rf.write(bytes);
        int No = fAssociations.get(keys[i]).intValue();
        rf.writeInt(No);
      }// for

      rf.writeInt(prob.lambda.length);
      byte[] lArr = Convert.doubleArrToByteArr(prob.lambda);
      rf.write(lArr);
      rf.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public void read(String filename) {
    try {
      InDataStreamFile rf = new InDataStreamFile(filename);
      fAssociations = new HashMap<Object,Integer>();
      int lngth = rf.readInt();
      for (int i = 0; i < lngth; i++) {
        int lb = rf.readInt();
        byte[] arr = new byte[lb];
        rf.read(arr);
        int No = rf.readInt();
        fAssociations.put(Integer.valueOf(new String(arr)), Integer.valueOf(No));
      }// for

      prob = new LambdaSolve();
      int funsize = rf.readInt();
      prob.lambda = new double[funsize];
      byte[] b = new byte[8 * funsize];
      rf.read(b);
      prob.lambda = Convert.byteArrToDoubleArr(b);
      rf.close();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
