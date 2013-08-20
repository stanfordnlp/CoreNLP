/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */

package edu.stanford.nlp.maxent;


import java.io.*;
import java.util.*;

import edu.stanford.nlp.maxent.iis.LambdaSolve;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;


/**
 * This class was created to make it easy to train MaxEnt models from files in the same format that
 * Weka uses.
 * The class can be used to classify instances specified in the same format also.
 * @author Kristina Toutanova
 * @version 1.0
 */
public class WekaProblemSolverCombinations {
  ReadDataWeka train;
  Experiments data;
  Features functions;
  Problem p;
  LambdaSolve model;
  MaxEntModel mE;
  static int cutoff = 0, iters = 300, ftNum = 30, top = 7, selected = top;
  static boolean binary = false;
  static boolean clean = false, usetop = true;
  static boolean select = true;
  static boolean fitNumClassifiers = false;
  static boolean selectOverlap = false;
  static boolean selectAboveBaseline = true;
  static boolean crossvalidation = false;
  static String kind = "mx"; // could also be maj or w
  HashMap<Object,Integer> fAssociations = new HashMap<Object,Integer>();
  HashMap<Integer,String> reverseMap = new HashMap<Integer,String>(); // the reverse map will contain the keys for ids;
  static int[] inds; // the indexes of the top accuracy classifiers
  static int[] indsselected;
  static byte[][] diff;
  static byte[] marked;
  static double[] accuracies;
  static double[] chrisweights;
  Index<IntPair> instanceIndex;

  public WekaProblemSolverCombinations() {
  }

  public WekaProblemSolverCombinations(String wekaProbFile) {
    this.read(wekaProbFile);
  }


  public void readTrainingInstances(String wekaDataFile) throws Exception {
    train = new ReadDataWeka(wekaDataFile);
  }

  public void makeFeatures(String kind) {
    data = new Experiments(train.vArray, train.numClasses);
    train.makeStringsClasses();
    inds = getSortedAccuracy();
    marked = new byte[train.numAttributes];
    if (fitNumClassifiers && selectOverlap) {
      selectClassifiers();
    }
    // int max = train.getNumAttributes();
    // now make the features
    // for each nominal attribute add features for each of its values + each of the classes
    // for numeric attributes, make features that take on double values

    functions = new Features();
    // int start = 0;
    // if (binary) {
    //   start = 1;
    // }

    //add functions for the classes
    /*
    for(int y=0;y<data.ySize;y++){
     int numTrue=0;
     double[][] v1=new double[data.size()][data.ySize];

     for(int i=0;i<data.size();i++){
        v1[i][y]=1;
        if(train.getClass(i)==y)
        numTrue++;
       }
     if(numTrue>cutoff){
     functions.add(new BinaryFeature(data,v1));
     fAssociations.put(max+"|"+y,new Integer(functions.size()-1));
     reverseMap.put(new Integer(functions.size()-1),max+"|"+y);
         //System.out.println(" Precision "+getPrecision(functions.size()-1));
     }//if
    }//for val, y

    */

    int maxA = train.numAttributes;
    if (usetop) {
      maxA = top;
    }
    //add functions for the attributes
    for (int indA = 0; indA < maxA; indA++) {
      int a = inds[indA];
      if (train.nominal(a)) {
        /*
        for(int val=start;val<train.getNumValues(a);val++)
        for(int y=0;y<data.ySize;y++){
         int numTrue=0;
         double[][] v1=new double[data.size()][data.ySize];

         for(int i=0;i<data.size();i++){
           if(train.getFValue(a,i)==val){
            v1[i][y]=1;
            if(train.getClass(i)==y)
            numTrue++;
           }
          }

         if((numTrue>cutoff)&&isOk(a+"|"+val+"|"+y)){
         functions.add(new BinaryFeature(data,v1));
         fAssociations.put(a+"|"+val+"|"+y,new Integer(functions.size()-1));
         reverseMap.put(new Integer(functions.size()-1),a+"|"+val+"|"+y);
         double prec=getPrecision(functions.size()-1);
         if(prec<.3){
           //System.out.println(" Precision "+prec);
           functions.removeLast();
           fAssociations.remove(a+"|"+val+"|"+y);
           reverseMap.remove(new Integer(functions.size()-1));
         }
         }//if
        }//for val, y
        */
        // Now add just accuracy features

        int numTrue = 0;
        double[][] v1 = new double[data.size()][data.ySize];

        for (int i = 0; i < data.size(); i++) {
          int val = (int) train.getFValue(a, i);
          String cls = train.getAttrName(a, val);
          int clsNo = train.getYIndex(cls);
          if (clsNo > -1) {
            v1[i][clsNo] = 1;
          }
          if (train.getClass(i) == clsNo) {
            numTrue++;
          }
        }

        if ((numTrue > cutoff) && (marked[a] < 2)) {
          functions.add(new BinaryFeature(data, v1, instanceIndex));
          fAssociations.put(a + "", Integer.valueOf(functions.size() - 1));
          reverseMap.put(Integer.valueOf(functions.size() - 1), String.valueOf(a));


          for (int k = 0; k < train.numAttributes; k++) {
            if ((diff[a][k] == 0) && (diff[k][a] == 0)) {
              marked[k]++;
            }
          }


        }//if

      }// if nominal
      if (train.numeric(a)) {
        for (int y = 0; y < data.ySize; y++) {
          int numTrue = 0;
          double[][] v1 = new double[data.size()][data.ySize];

          for (int i = 0; i < data.size(); i++) {
            if (train.getFValue(a, i) > 0) {
              v1[i][y] = train.getFValue(a, i);
              numTrue++;
            }
          }
          if (numTrue > cutoff) {
            functions.add(new Feature(data, v1, instanceIndex));
            fAssociations.put(a + "|" + "num" + "|" + y, Integer.valueOf(functions.size() - 1));
            reverseMap.put(Integer.valueOf(functions.size() - 1), a + "|" + "num" + "|" + y);
          }//if
        }//for y

      }// if numeric

    }// for a


    // now add a majority accuracy feature

    /*
     int numTrue=0;
      double[][] v1=new double[data.size()][data.ySize];

      for(int i=0;i<data.size();i++){
       int val=getClassificationVoting(train.getData(i),top,true);
       int clsNo=val;
       if(clsNo>-1)
         v1[i][clsNo]=1;
         if(train.getClass(i)==clsNo)
         numTrue++;
        }
      if((numTrue>cutoff)){
      functions.add(new BinaryFeature(data,v1));
      fAssociations.put("m",new Integer(functions.size()-1));
      reverseMap.put(new Integer(functions.size()-1),"m");
      }//if
    */
    p = new Problem(data, functions);
  }


  /*
  * Adding only some kinds of features
  * Where the classifier predicts the same label as is true
  * Or one of the participating classifiers
  */

  public boolean isOk(String key) {

    int indFirstBar = key.indexOf("|");
    int aNo = Integer.parseInt(key.substring(0, indFirstBar));

    String keyRest = key.substring(key.indexOf("|") + 1);
    //System.out.println(keyRest);
    int indBar = keyRest.indexOf("|");
    int val = Integer.parseInt(keyRest.substring(0, indBar));
    int y = Integer.parseInt(keyRest.substring(indBar + 1));

    String stVal = train.getAttrName(aNo, val);
    String stCl = train.getYName(y);

    return ((stVal.indexOf(stCl)) > -1);


  }


  public void analyseFeatures(String kind) {

//    int[] indexesSupport = new int[p.fSize];
//    int[] indexesPrecision = new int[p.fSize];
//    int[] indexesGain = new int[p.fSize];
    double[] gains = new double[p.fSize];

    LambdaSolve prob = new LambdaSolve(new Problem(data, new Features()), .0001, .0001);
    double gain;
    for (int i = 0; i < p.fSize; i++) {
//      String key = reverseMap.get(Integer.valueOf(i));
      gain = prob.GainCompute(p.functions.get(i), .0001);
      gains[i] = gain;

      //insert the gain in the sorted list


    }
  }


  public double getPrecision(int fNo) {
    int tp = 0, fp = 0;
//    String key = reverseMap.get(Integer.valueOf(fNo));
    //System.out.println(" Feature "+key);
    Feature f = functions.get(fNo);
    int yNo = f.getY(0);
    for (int l = 0; l < f.len(); l++) {
      int x = f.getX(l);
      int cls = train.getClass(x);
      if (cls == yNo) {
        tp++;
      } else {
        fp++;
      }

    }

    return tp / (double) (tp + fp);

  }


  public void makeFeaturesAssociations() {
    data = new Experiments(train.vArray);
    int max = train.getNumAttributes();

    HashMap<Object, Integer> newAssociations = new HashMap<Object, Integer>();
    // now make the features
    // for each nominal attribute add features for each of its values + each of the classes
    // for numeric attributes, make features that take on double values

    functions = new Features();
    int start = 0;
    if (binary) {
      start = 1;
    }
    //add functions for the classes

    for (int y = 0; y < data.ySize; y++) {
      int numTrue = 0;
      double[][] v1 = new double[data.size()][data.ySize];

      for (int i = 0; i < data.size(); i++) {
        v1[i][y] = 1;
        if (train.getClass(i) == y) {
          numTrue++;
        }
      }
      if (fAssociations.containsKey(max + "|" + y)) {
        functions.add(new BinaryFeature(data, v1, instanceIndex));
        newAssociations.put(max + "|" + y, Integer.valueOf(functions.size() - 1));

      }//if
    }//for  y


    //add functions for the attributes
    for (int a = 0; a < train.getNumAttributes(); a++) {
      if (train.nominal(a)) {
        for (int val = start; val < train.getNumValues(a); val++) {
          for (int y = 0; y < data.ySize; y++) {
            int numTrue = 0;
            double[][] v1 = new double[data.size()][data.ySize];

            for (int i = 0; i < data.size(); i++) {
              if (train.getFValue(a, i) == val) {
                v1[i][y] = 1;
                if (train.getClass(i) == y) {
                  numTrue++;
                }
              }
            }
            if (fAssociations.containsKey(a + "|" + val + "|" + y)) {
              functions.add(new BinaryFeature(data, v1, instanceIndex));
              newAssociations.put(a + "|" + val + "|" + y, Integer.valueOf(functions.size() - 1));
            }//if
          }//for val, y
        }
      }// if nominal
      if (train.numeric(a)) {
        for (int y = 0; y < data.ySize; y++) {
          int numTrue = 0;
          double[][] v1 = new double[data.size()][data.ySize];

          for (int i = 0; i < data.size(); i++) {
            if (train.getFValue(a, i) > 0) {
              v1[i][y] = train.getFValue(a, i);
              numTrue++;
            }
          }
          if (fAssociations.containsKey(a + "|" + "num" + "|" + y)) {
            functions.add(new Feature(data, v1, instanceIndex));
            newAssociations.put(a + "|" + "num" + "|" + y, Integer.valueOf(functions.size() - 1));
          }//if
        }//for y

      }// if numeric


    }// for a
    p = new Problem(data, functions);
    fAssociations.clear();
    fAssociations = newAssociations;


  }


  public void buildClassifier(String trainFileName, int iters, double gaincutoff) throws Exception {
    readTrainingInstances(trainFileName);
    makeFeatures("n");
    mE = new MaxEntModel(p.data, p.functions, 0.001, gaincutoff);
    if (select) {
      mE.FindModel(gaincutoff);
    } else {
      mE.FindModel(0);
    }
    //System.out.println("Number features are "+mE.activeFeats.size());
    model = mE.prob;
    model.improvedIterative(iters);
    model.checkCorrectness();
    HashMap<Object, Integer> survived = new HashMap<Object, Integer>();
    Object[] keys = fAssociations.keySet().toArray();
    for (int num = 0; num < keys.length; num++) {
      int numF = fAssociations.get(keys[num]).intValue();
      if (mE.correspondences[numF] > -1) {
        survived.put(keys[num], Integer.valueOf(mE.correspondences[numF]));
        int f = mE.correspondences[numF];
        System.out.println(keys[num] + " lambda " + f + " " + model.lambda[f]);
      }
    }// for numF
    fAssociations.clear();
    fAssociations = survived;
    mE.fAssociations = fAssociations;
  }


  /*
  * Uses ten-fold cross-validation to select number of classifiers
  * to include in the voting scheme
  */

  public void buildClassifierCrossValidation(String trainFileName, int iters, double gaincutoff) throws Exception {
    String trainFile = "train.tmp", validationFile = "validation.tmp";
    double[] accs = new double[5]; //trying for 5,7,9,11,13,15 classifiers
    // make sure all the booleans are properly set
    fitNumClassifiers = false;
    usetop = true;
    top = 5;
    int current = 0;
    while (current < 5) {
      for (int round = 0; round < 5; round++) {
        fAssociations.clear();
        split(trainFileName, trainFile, validationFile);
        readTrainingInstances(trainFile);
        ReadDataWeka test = new ReadDataWeka(validationFile, train);
        makeFeatures("n");
        mE = new MaxEntModel(p.data, p.functions, 0.001, gaincutoff);
        if (select) {
          mE.FindModel(gaincutoff);
        } else {
          mE.FindModel(0);
        }
        System.out.println("Number features are " + mE.activeFeats.size());
        model = mE.prob;
        model.improvedIterative(iters);
        model.checkCorrectness();
        mE.fAssociations = fAssociations;
        model = mE.prob;
        int res = 0;
        for (int d = 0; d < test.v.size(); d++) {
          DataDouble dD = test.getData(d);
          int y = getClassification(dD);
          if (y == dD.getYNo()) {
            res++;
          }
        }//d
        double newAcc = res / (double) test.numSamples();
        System.out.println("Accuracy " + newAcc);
        accs[current] = ((accs[current] * round) + newAcc) / (double) (round + 1);

      }//round
      current++;
      top += 2;
    }

    int max = 2;
    for (int j = 0; j < accs.length; j++) {
      System.out.println(" accuracies " + j + " " + accs[j]);
      if (accs[j] > accs[max]) {
        max = j;
      }
    }
    top = 5 + max * 2;

    fAssociations.clear();
    mE.fAssociations.clear();
    reverseMap.clear();
    buildClassifier(trainFileName, iters, gaincutoff);

  }


  public void buildClassifierValidation(String trainFileName, int iters, double gaincutoff) throws Exception {
    String trainFile = "train.tmp", validationFile = "validation.tmp";
    int time = 0;
    int maxTime = ftNum;
    double rate = .8;
    // if increase, accept
    // else accept with probability
    split(trainFileName, trainFile, validationFile);
    readTrainingInstances(trainFile);
    ReadDataWeka test = new ReadDataWeka(validationFile, train);
    makeFeatures("n");
    mE = new MaxEntModel(p.data, p.functions, 0.001, gaincutoff);
    boolean increases = true;
    double oldAcc = 0, newAcc = 0;
    double probAccept;
    int countDown = 2 * train.numClasses * train.numClasses;

    while ((time < maxTime) && (countDown > 0)) {
      int nextFt = mE.addFeature(gaincutoff);
      gaincutoff *= rate;
      if (nextFt == -1) {
        System.out.println(" Can't add more features \n");
        break;
      }
      String key = reverseMap.get(Integer.valueOf(nextFt));

      mE.fAssociations.put(key, Integer.valueOf(mE.activeFeats.size() - 1));
      int res = 0;
      model = mE.prob;
      for (int d = 0; d < test.v.size(); d++) {
        DataDouble dD = test.getData(d);
        int y = getClassification(dD);
        if (y == dD.getYNo()) {
          res++;
        }
      }//d
      newAcc = res / (double) test.numSamples();
      System.out.println("Accuracy " + newAcc);
      increases = (newAcc > oldAcc);
      if (increases) {
        probAccept = .95;
      } else if (newAcc == oldAcc) {
        probAccept = .8;
      } else {
        probAccept = Math.exp((newAcc - oldAcc) * time);
      }
      System.out.println(" Probability of accepting is " + probAccept + " fts " + mE.p.fSize);
      double ran = Math.random();
      if (ran > probAccept) { //Don't add the feature
        mE.p.removeLast();
        mE.fAssociations.remove(key);
        mE.correspondences[nextFt] = -1;

      } else {
        System.out.println(" Added " + key);
        oldAcc = newAcc;
        if (!increases) {
          countDown--;
        } else {
          countDown = train.numClasses * train.numClasses;
        }
        System.out.println(" Count down is " + countDown);
      }
      time++;
    }

    System.out.println("Number features are " + mE.activeFeats.size());
    HashMap<Object,Integer> survived = new HashMap<Object,Integer>();
    Set<Object> keys = fAssociations.keySet();
    for (Object key : keys) {
      int numF = fAssociations.get(key).intValue();
      if (mE.correspondences[numF] > -1) {
        survived.put(key, Integer.valueOf(mE.correspondences[numF]));
        int f = mE.correspondences[numF];
        System.out.println(key + " lambda " + f + " " + model.lambda[f]);
      }
    }// for numF
    save(trainFile);
    fAssociations.clear();
    fAssociations = survived;
    readTrainingInstances(trainFileName);
    makeFeaturesAssociations();
    mE.fAssociations = fAssociations;
    mE.prob = new LambdaSolve(p, .0001, .0001);
    mE.prob.improvedIterative(iters);
    eraseTmps();
  }

  /**
   * This is just to delete the temporary files. Can deal with the problem in a better
   * way probably
   */

  void eraseTmps() {
    File f1 = new File("train.tmp");
    f1.delete();
    f1 = new File("validation.tmp");
    f1.delete();
  }


  private static void split(String trainFileName, String fileOne, String fileTwo) throws Exception {

    BufferedReader in = new BufferedReader(new FileReader(trainFileName));
    BufferedWriter out1 = new BufferedWriter(new FileWriter(fileOne));
    BufferedWriter out2 = new BufferedWriter(new FileWriter(fileTwo));

    String line;

    while (true) {
      line = (in.readLine() + "\n");
      out1.write(line);
      out2.write(line);
      if (line.startsWith("@data")) {
        break;
      }
    }

    //separate 9 to 1
    while ((line = in.readLine()) != null) {
      double ran = Math.random();
      if (ran < .9) {
        out1.write(line + "\n");
      } else {
        out2.write(line + "\n");
      }
    }//while
    in.close();
    out1.close();
    out2.close();

  };


  /**
   * This file is supposed to be in Weka format
   * The class attribute might be missing
   */
  public void test(String fileName) {
    try {
      ReadDataWeka test = new ReadDataWeka(fileName, train);
      data = new Experiments(train.vArray);
      train.makeStringsClasses();
      inds = getSortedAccuracy();
      if (selectOverlap) {
        selectClassifiers();
      }
      int res = 0, resVoting = 0, resBestHeldOut = 0, resWeightedVoting = 0;
      //top=mE.prob.lambda.length+1;
      for (int d = 0; d < test.v.size(); d++) {
        DataDouble dD = test.getData(d);
        int y = getClassification(dD);
        if (clean && kind.equals("mx")) {
          System.out.println(train.getYName(y));

        }

        //if(!clean) System.out.println("Answer is "+train.getYName(dD.getYNo()));


        if (y == dD.getYNo()) {
          res++;
        }

        int yH = train.getYIndex(train.getAttrName(inds[0], (int) dD.x[inds[0]]));
        if (dD.getYNo() == yH) {
          resBestHeldOut++;
        }


        int yV = getClassificationVoting(dD, top, false);

        if (dD.getYNo() == yV) {
          resVoting++;
        }
        if (clean && kind.equals("maj")) {
          System.out.println(train.getYName(yV));
        }

        int yWV = getClassificationWeightedVoting(dD, top, false);
        if (dD.getYNo() == yWV) {
          resWeightedVoting++;
        }

        if (clean && kind.equals("w")) {
          System.out.println(train.getYName(yWV));
        }


      }//d
      if (!clean) {

        System.out.println(mE.prob.lambda.length + " " + top);
        System.out.println(train.numSamples() / (double) train.numClasses);
        System.out.println("Accuracy weighted " + resWeightedVoting / (double) test.numSamples());
        System.out.println("Accuracy best " + resBestHeldOut / (double) test.numSamples());
        System.out.println("Accuracy voting " + resVoting / (double) test.numSamples());
        System.out.println("Accuracy " + res / (double) test.numSamples());

      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public int getClassification(DataDouble d) {
    double[] posters = getPosteriorsNom(d);
    //System.out.println(posters[0]+" "+posters[1]);
    double max = Double.NEGATIVE_INFINITY;
    int maxI = -1;
    for (int z = 0; z < posters.length; z++) {
      if (posters[z] > max) {
        max = posters[z];
        maxI = z;
      }
    }

    return maxI;

  }


  public int getClassificationVoting(DataDouble d, int topNo, boolean inTrain) {
    double[] posters = new double[train.numClasses];
    double[] scores = new double[train.numClasses]; // this is for tie breaking
    double[] chrisscores = new double[train.numClasses];

    //System.out.println(posters[0]+" "+posters[1]);
    int start = 0;
    if (inTrain) {
      start = 2;
      topNo += 2;
    }

    double[] x = d.x;
    for (int j = start; j < topNo; j++) {
      int yVc = (int) x[inds[j]];
      String nameVal = train.getAttrName(inds[j], yVc);
      int yV = train.getYIndex(nameVal);
      if (yV > -1) {
        posters[yV]++;
        chrisscores[yV] += chrisweights[inds[j]];
        scores[yV] += accuracies[inds[j]];
      }

    }

    double max = 0;
    int maxI = 0;
    for (int z = 0; z < posters.length; z++) {
      if (posters[z] > max) {
        max = posters[z];
        maxI = z;
      }
      if (posters[z] == max) {
        if (scores[z] > scores[maxI]) {
          max = posters[z];
          maxI = z;
        }
      }

    }
    // make sure the cases of ties are treated properly


    return maxI;

  }


  public int getClassificationWeightedVoting(DataDouble d, int topNo, boolean inTrain) {

    double[] chrisscores = new double[train.numClasses];

    //System.out.println(posters[0]+" "+posters[1]);
    int start = 0;
    if (inTrain) {
      start = 2;
      topNo += 2;
    }

    double[] x = d.x;
    for (int j = start; j < topNo; j++) {
      int yVc = (int) x[inds[j]];
      String nameVal = train.getAttrName(inds[j], yVc);
      int yV = train.getYIndex(nameVal);
      if (yV > -1) {
        chrisscores[yV] += chrisweights[inds[j]];
      }

    }

    double max = 0;
    int maxI = 0;
    for (int z = 0; z < chrisscores.length; z++) {
      if (chrisscores[z] > max) {
        max = chrisscores[z];
        maxI = z;
      }

    }


    return maxI;

  }


  public double[] getPosteriorsFN(DataDouble d) {
    double[] posters = new double[train.numClasses];

    for (int j = 0; j < posters.length; j++) {
      posters[j] = 1;
    }
    int numA = train.getNumAttributes();


    double sum = 0;
    for (int y = 0; y < train.numClasses; y++) {
      if (y > 0) {
        for (int f = 0; f < numA; f++) {
          int fNo = 2 * f + 1;
          posters[y] *= Math.exp(model.lambda[fNo] * d.getX(f));
          fNo = 2 * f;
          posters[y] *= Math.exp(model.lambda[fNo] * (1 - d.getX(f)));
        }//f
      }

      if (y == 0) {
        for (int f = 0; f < numA; f++) {
          int fNo = 2 * f;
          posters[y] *= Math.exp(model.lambda[fNo]);
        }//f
      }

      sum += posters[y];

    }//for y

    for (int y = 0; y < train.numClasses; y++) {
      posters[y] /= sum;
    }


    return posters;


  }


  public double[] getPosteriors(DataDouble d) {
    double[] posters = new double[train.numClasses];

    for (int j = 0; j < posters.length; j++) {
      posters[j] = 1;
    }
    int numA = train.getNumAttributes();
    int cnt = 0;

    for (int j = 0; j < numA; j++) {
      cnt += d.getX(j);
    }

    double sum = 0;
    for (int y = 0; y < train.numClasses; y++) {
      if (y > 0) {
        for (int f = 0; f < numA; f++) {
          int fNo = f;
          if (fNo > -1) {
            posters[y] *= Math.exp(model.lambda[fNo] * d.getX(f));
          }
        }//f
      }

      /*
      if(y==0) posters[y]*=Math.exp(model.lambda[numA]*max);
       else
           posters[y]*=Math.exp(model.lambda[numA]*(max-cnt));
       */
      sum += posters[y];

    }//for y

    for (int y = 0; y < train.numClasses; y++) {
      posters[y] /= sum;
    }


    return posters;


  }


  public double[] getPosteriorsNom(DataDouble d) {
    double[] posters = new double[train.numClasses];
    int numF;

    int numA = train.getNumAttributes();

    // the class priors first

    for (int y = 0; y < train.numClasses; y++) {
      String key = numA + "|" + y;
      if (mE.fAssociations.containsKey(key)) {
        numF = mE.fAssociations.get(key).intValue();
        if (numF > -1) {
          posters[y] += model.lambda[numF];
        }
      }
    }

    // the attributes

    for (int a = 0; a < numA; a++) {
      if (train.nominal(a)) {
        int val = (int) d.getX(a);
        for (int y = 0; y < train.numClasses; y++) {
          String key = a + "|" + val + "|" + y;
          if (mE.fAssociations.containsKey(key)) {
            numF = mE.fAssociations.get(key).intValue();
            if (numF > -1) {
              posters[y] += (model.lambda[numF]);
            }
          }
          if (val == y) {
            key = a + "";
            if (mE.fAssociations.containsKey(key)) {
              numF = mE.fAssociations.get(key).intValue();
              if (!clean) {
                System.out.println(" Number function " + numF);
              }
              if (numF > -1) {
                posters[y] += (model.lambda[numF]);
              }
            }
          }

        }//for y

      }//nominal

      if (train.numeric(a)) {
        double val = d.getX(a);
        for (int y = 0; y < train.numClasses; y++) {
          String key = a + "|" + "num" + "|" + y;
          if (mE.fAssociations.containsKey(key)) {
            numF = mE.fAssociations.get(key).intValue();
            if (numF > -1) {
              posters[y] += (val * model.lambda[numF]);
            }
          }
        }//for y
      }//numeric

    }// for a


    // the majority feature
    int val = getClassificationVoting(d, top, false);
    if (val > -1) {
      String key = "m";
      if (mE.fAssociations.containsKey(key)) {
        numF = mE.fAssociations.get(key).intValue();
        if (numF > -1) {
          posters[val] += (model.lambda[numF]);
        }
      }
    }


    /*
      for(int y=0;y<train.numClasses;y++)
         sum+=posters[y];

      for(int y=0;y<train.numClasses;y++)
        posters[y]/=sum;

    */

    return posters;

  }


  /**
   * Parameters :
   * -train trainFileArff ( training will be done now )
   * -gain double         ( the gain cutoff )
   * -support int         ( the minimum number of times a feature must appear to be included )
   * -test trainFile testFile
   * -iters numIterations ( iterative scaling iterations )
   * -binary              ( indicates that for attributes that are binary we are adding features only for the value 1 of them )
   * -validation          ( use cross-validation to select features )
   * -clean               ( in testing, print only one classification per line )
   * -ftNum [numFeatures] ( the maximum number of features )
   * -no_sel              ( do not do feature selection )
   * -usetop [numTop]     ( use only top numTop classifiers )
   * -fixedtop            ( do not select number of classifiers to include, use specified )
   * -crossval            ( use cross validation to choose optinmal number of clasisifers to combine )
   */

  public static void main(String[] args) {
    double gain = .002;

    boolean train = false, test = false, validation = false;
    String trainFile = "NO_TRAIN_FILE", testFile = "NO_TEST_FILE";

    try {
      WekaProblemSolverCombinations wPS = new WekaProblemSolverCombinations();
      int crnt = 0;

      while (crnt < args.length) {
        String token = args[crnt];
        if (token.equals("-train")) {
          trainFile = args[++crnt];
          train = true;
          crnt++;
        }
        if (token.equals("-test")) {
          trainFile = args[++crnt];
          testFile = args[++crnt];
          test = true;
          crnt++;
        }
        if (token.equals("-gain")) {
          gain = Double.parseDouble(args[++crnt]);
          crnt++;
        }
        if (token.equals("-support")) {
          cutoff = Integer.parseInt(args[++crnt]);
          System.out.println(" cutoff is " + cutoff);
          crnt++;
        }

        if (token.equals("-usetop")) {
          usetop = true;
          top = Integer.parseInt(args[++crnt]);
          crnt++;
        }

        if (token.equals("-kind")) {
          kind = args[++crnt];
          crnt++;
        }

        if (token.equals("-iters")) {
          iters = Integer.parseInt(args[++crnt]);
          crnt++;
        }

        if (token.equals("-ftNum")) {
          ftNum = Integer.parseInt(args[++crnt]);
          crnt++;
        }


        if (token.equals("-binary")) {
          binary = true;
          crnt++;
        }

        if (token.equals("-fixedtop")) {
          fitNumClassifiers = false;
          crnt++;
        }

        if (token.equals("-crossval")) {
          crossvalidation = true;
          fitNumClassifiers = false;
          crnt++;
        }


        if (token.equals("-validation")) {
          validation = true;
          crnt++;
        }

        if (token.equals("-clean")) {
          clean = true;
          crnt++;
        }

        if (token.equals("-no_sel")) {
          select = false;
          crnt++;
        }


      } //while

      if (train) {

        if (crossvalidation) {
          wPS.buildClassifierCrossValidation(trainFile, iters, gain);
          wPS.save(trainFile);
        } else {
          if (!validation) {
            wPS.buildClassifier(trainFile, iters, gain);
          } else {
            wPS.buildClassifierValidation(trainFile, iters, gain);
          }
          wPS.save(trainFile);
        }

      }
      if (test) {
        wPS.read(trainFile);
        wPS.test(testFile);
      }

    } catch (Exception e) {
      e.printStackTrace();
      System.out.println(" Format :\n [-train fileName] [-test trainFile testFile] [-gain doubleVal] [-iters numIters] [-support minsupport ] \n");
    }

  }


  public void save(String filename) {

    String fName = filename;
    mE.save(fName + ".me");
    train.save(fName + ".dat");
  }

  public void read(String filename) {
    try {
      train = new ReadDataWeka(filename);
      //train.read(filename+".dat");
      mE = new MaxEntModel();
      mE.read(filename + ".me");
      model = mE.prob;
      if (!clean) {
        printFeatures();
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
  }


  public int[] getSortedAccuracy() {
    // returns the indexes of the classifiers sorted by accurcaies;
    accuracies = new double[train.numAttributes];
    chrisweights = new double[train.numAttributes];
    int indbaseline = 0;

    diff = new byte[train.numAttributes][train.numAttributes];

    for (int i = 0; i < train.numSamples(); i++) {
      double[] a = train.getData(i).x;
      int y = train.getClass(i);
      String cls = train.getYName(y);
      int right = 0;
      for (int j = 0; j < a.length; j++) {
        if (train.getAttrName(j, (int) a[j]).equals(cls)) {
          accuracies[j]++;
          right++;
        }
      }

      for (int j = 0; j < a.length; j++) {
        if (train.getAttrName(j, (int) a[j]).equals(cls)) {
          chrisweights[j] += 1 / (double) right;
        }
      }


    }// for


    for (int i = 0; i < train.numSamples(); i++) {
      double[] a = train.getData(i).x;
      for (int j = 0; j < train.numAttributes; j++) {
        for (int g = j + 1; g < train.numAttributes; g++) {
          if (!(train.getAttrName(j, (int) a[j]).equals(train.getAttrName(g, (int) a[g])))) {
            diff[j][g] = 1;
          }
        }
      }//j
    }//i


    int[] indexes = new int[train.numAttributes];

    for (int ind = 1; ind < train.numAttributes; ind++) {

      double val = accuracies[ind];
      int current = ind;

      while ((current > 0) && (val > accuracies[indexes[current - 1]])) {
        indexes[current] = indexes[current - 1];
        current--;
      }

      if (current > 0) {
        if (val == accuracies[indexes[current - 1]]) {
          if ((diff[ind][indexes[current - 1]] == 0) && (diff[indexes[current - 1]][ind] == 0)) {
            if (!clean) {
              System.out.println(" Not different " + ind + " " + indexes[current - 1]);
            }
          }

        }//if


      }

      indexes[current] = ind;


    } // for ind

    for (int j = 0; j < accuracies.length; j++) {
      accuracies[indexes[j]] = accuracies[indexes[j]] / (double) train.numSamples();
      if (!clean) {
        System.out.println(indexes[j] + " " + accuracies[indexes[j]]);
      }
    }


    for (int s = 0; s < accuracies.length; s++) {
      if (indexes[s] == 0) {
        indbaseline = s;
        break;
      }
    }


    // estimating standard deviation of the accuracies
    // first the mean
    double mean = 0;

    for (int i = 0; i < train.numAttributes; i++) {
      mean += accuracies[i];
    }

    mean = mean / train.numAttributes;

    double stdev = 0;
    for (int i = 0; i < train.numAttributes; i++) {
      stdev += (accuracies[i] - mean) * (accuracies[i] - mean);
    }

    stdev = stdev / train.numAttributes;

    stdev = Math.sqrt(stdev);


    if (!clean) {
      System.out.println("Mean is " + mean + " stdev " + stdev);
    }

    //System.exit(0);

    double gap = 0;
    int maxGapIndex = top;

    if (WekaProblemSolverCombinations.fitNumClassifiers) {

      int middle = 3;
      if (top > middle + 1) {

        gap = stdev;
        if (!clean) {
          System.out.println(" Standard deviation " + gap);
        }
        for (int k = middle + 1; k < train.numAttributes; k++) {
          double currGap = -accuracies[indexes[k]] + accuracies[indexes[middle]];
          if (currGap > gap) {
            maxGapIndex = k;
            gap = currGap;
            break;
          }//if
        }// for k

      }//top

    }// if fitnumclassifiers
    if (!clean) {
      System.out.println(" The stopping gap is " + gap + " index " + maxGapIndex);
    }
    top = (maxGapIndex < 20 ? maxGapIndex : top);

    if (!clean) {
      System.out.println("Using top " + top);
    }

    for (int i = 0; i < train.numAttributes; i++) {
      for (int j = i + 1; j < train.numAttributes; j++) {
        if ((diff[i][j] == 0) && (diff[j][i] == 0)) {
          if (!clean) {
            System.out.println(" Not different " + i + " " + j);
          }
        }
      }
    }


    if (WekaProblemSolverCombinations.selectAboveBaseline && (indbaseline > 5) && (fitNumClassifiers)) {
      top = indbaseline + 1;
    }

    return indexes;


  }


  public void selectClassifiers() {

    // starting with the most accurate classifier
    // add classiifiers with highest accuracy until they have guessed something correct
    // that the selected so far haven't
    int[] indsselected = new int[train.numAttributes];
    byte guessedCorrect[] = new byte[train.numSamples()];
    boolean improves = true;
    indsselected[0] = inds[0];
    int current = 0;
    int currentAcc = 0;
    int max = top;

    while (improves && (currentAcc < max)) {

      improves = false;
      // mark all the correct ones
      for (int i = 0; i < train.numSamples(); i++) {
        if (guessedCorrect[i] < 2) {
          double[] a = train.getData(i).x;
          int y = train.getClass(i);
          String cls = train.getYName(y);
          int j = inds[currentAcc];
          if (train.getAttrName(j, (int) a[j]).equals(cls)) {
            guessedCorrect[i]++;
          }
        }
      }// for

      while (currentAcc < max) {
        if (improves) {
          break;
        }
        currentAcc++;
        for (int i = 0; i < train.numSamples(); i++) {
          if (guessedCorrect[i] < 2) {
            double[] a = train.getData(i).x;
            int y = train.getClass(i);
            String cls = train.getYName(y);
            int j = inds[currentAcc];
            if (train.getAttrName(j, (int) a[j]).equals(cls)) {
              improves = true;
              current++;
              indsselected[current] = inds[currentAcc];
              System.out.println(" Adding " + currentAcc);
              break;
            }//if
          }// if
        }

      }//while

    }//while

    System.out.println("Selected " + (current + 1) + " classifiers ");
    selected = current + 1;

    if (fitNumClassifiers && selectOverlap) {
      top = selected;
      for (int i = 0; i < top; i++) {
        inds[i] = indsselected[i];
      }

    }


  }


  public void printFeatures() {

    Object[] keys = mE.fAssociations.keySet().toArray();
    for (int num = 0; num < keys.length; num++) {
      int numF = mE.fAssociations.get(keys[num]).intValue();
      if (numF > -1) {
        System.out.println(keys[num] + " lambda " + numF + " " + model.lambda[numF]);
      }
    }// for numF


  }


}
