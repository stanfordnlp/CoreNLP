/**
 * Title:        StanfordMaxEnt<p>
 * Description:  A Maximum Entropy Toolkit<p>
 * Copyright:    Copyright (c) Kristina Toutanova<p>
 * Company:      Stanford University<p>
 */


package edu.stanford.nlp.maxent;

import edu.stanford.nlp.maxent.iis.LambdaSolve;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.HashIndex;

import java.io.*;
import java.util.HashMap;

/**
 * A class to solve a maxent problem given an input in weka format.
 * This class was created to make it easy to train MaxEnt models
 * from files in the same format that Weka uses.
 * The class can be used to classify instances specified in the same format also.
 * TODO: Check the workings of the Index for the features
 *
 * @author Kristina Toutanova
 * @version 1.0
 */
public class WekaProblemSolver {

  ReadDataWeka train;
  Experiments data;
  Features functions;
  Problem p;
  LambdaSolve model;
  MaxEntModel mE;
  static int cutoff = 0, iters = 300, ftNum = 30, top = 7, selected = top;
  static boolean binary = false;
  static boolean clean = false;
  static boolean select = false;
  static boolean crossvalidation = false;
  HashMap<Object, Integer> fAssociations = new HashMap<Object, Integer>();
  HashMap<Integer, String> reverseMap = new HashMap<Integer, String>(); // the reverse map will contain the keys for ids;
  static int[] inds; // the indexes of the top accuracy classifiers
  static int[] indsselected;
  Index<IntPair> instanceIndex;


  public WekaProblemSolver() {
  }

  public WekaProblemSolver(String wekaProbFile) {
    this.read(wekaProbFile);
  }


  public void readTrainingInstances(String wekaDataFile) throws Exception {
    train = new ReadDataWeka(wekaDataFile);
  }

  Index<IntPair> createIndex(int numX, int numY) {
    Index<IntPair> index = new HashIndex<IntPair>();
    for (int x = 0; x < numX; x++) {
      for (int y = 0; y < numY; y++) {
        index.add(new IntPair(x, y));
      }
    }
    return index;
  }

  public void makeFeatures(String kind) {
    data = new Experiments(train.vArray, train.numClasses);
    instanceIndex = createIndex(train.vArray.length, train.numClasses);
    train.makeStringsClasses();
//    int max = train.getNumAttributes();
    // now make the features
    // for each nominal attribute add features for each of its values + each of the classes
    // for numeric attributes, make features that take on double values

    functions = new Features();
//    int start = 0;
//    if (binary) {
//      start = 1;
//    }

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

    //first hash all possible features and count their support

    HashMap<String, Integer> possibleKeys = new HashMap<String, Integer>();
    for (int i = 0; i < data.size(); i++) {
      for (int indA = 0; indA < maxA; indA++) {
        int a = indA;
        int value = (int) train.getFValue(a, i);
        String key = a + "|" + value + '|' + train.getClass(i);
        if (possibleKeys.containsKey(key)) {

          Integer prev = possibleKeys.get(key);
          prev = Integer.valueOf(prev.intValue() + 1);
          possibleKeys.put(key, prev);
        } else {
          possibleKeys.put(key, Integer.valueOf(1));

        }
      }


    }



    //add functions for the attributes
    for (int indA = 0; indA < maxA; indA++) {
      int a = indA;
      if (train.nominal(a)) {
        int[][] indsall = new int[train.getNumValues(a)][];
        int[] currentinds = new int[train.getNumValues(a)];
        for (int val = 0; val < train.getNumValues(a); val++) {//collect the data with these values
          int totalval = 0;
          for (int y = 0; y < data.ySize; y++) {
            String key = a + "|" + val + '|' + y;
            if (possibleKeys.containsKey(key)) {
              int thisy = possibleKeys.get(key).intValue();
              totalval += thisy;
            }
          }

          indsall[val] = new int[totalval];

        }//for val
        for (int i = 0; i < data.size(); i++) {
          int val = (int) train.getFValue(a, i);
          indsall[val][currentinds[val]++] = i * data.ySize;

        }// for i


        for (int val = 0; val < train.getNumValues(a); val++) {
          for (int y = 0; y < data.ySize; y++) {
            String key = a + "|" + val + '|' + y;
            int numTrue = 0;
            if (possibleKeys.containsKey(key)) {
              numTrue = possibleKeys.get(key).intValue();
            }
            if ((numTrue > cutoff)) {
              int numHere = indsall[val].length;
              int[] indS = new int[numHere];
              for (int j = 0; j < numHere; j++) {
                indS[j] = indsall[val][j] + y;
              }
              functions.add(new BinaryFeature(data, indS, instanceIndex));
              fAssociations.put(a + "|" + val + '|' + y, Integer.valueOf(functions.size() - 1));
              reverseMap.put(Integer.valueOf(functions.size() - 1), a + "|" + val + '|' + y);
              System.out.println("Added " + a + '|' + val + '|' + y);
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
          if (numTrue > cutoff) {
            functions.add(new Feature(data, v1, instanceIndex));
            fAssociations.put(a + "|" + "num" + '|' + y, Integer.valueOf(functions.size() - 1));
            reverseMap.put(Integer.valueOf(functions.size() - 1), a + "|" + "num" + '|' + y);
          }//if
        }//for y

      }// if numeric

    }// for a


    System.out.println("Number of features " + functions.size());

    p = new Problem(data, functions);
  }


  /*
  * Adding only some kinds of features
  * Where the classifier predicts the same label as is true
  * Or one of the participating classifiers
  */

  public boolean isOk(String key) {

    int indFirstBar = key.indexOf('|');
    int aNo = Integer.parseInt(key.substring(0, indFirstBar));

    String keyRest = key.substring(key.indexOf('|') + 1);
    //System.out.println(keyRest);
    int indBar = keyRest.indexOf('|');
    int val = Integer.parseInt(keyRest.substring(0, indBar));
    int y = Integer.parseInt(keyRest.substring(indBar + 1));

    String stVal = train.getAttrName(aNo, val);
    String stCl = train.getYName(y);

    return ((stVal.indexOf(stCl)) > -1);


  }


  public void makeFeaturesAssociations() {
    data = new Experiments(train.vArray);
    int max = train.getNumAttributes();

    HashMap<Object,Integer> newAssociations = new HashMap<Object,Integer>();
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
            if (fAssociations.containsKey(a + "|" + val + '|' + y)) {
              functions.add(new BinaryFeature(data, v1, instanceIndex));
              newAssociations.put(a + "|" + val + '|' + y, Integer.valueOf(functions.size() - 1));
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
          if (fAssociations.containsKey(a + "|" + "num" + '|' + y)) {
            functions.add(new Feature(data, v1, instanceIndex));
            newAssociations.put(a + "|" + "num" + '|' + y, Integer.valueOf(functions.size() - 1));
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
        System.out.println(keys[num] + " lambda " + f + ' ' + model.lambda[f]);
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
    HashMap<Object, Integer> survived = new HashMap<Object, Integer>();
    Object[] keys = fAssociations.keySet().toArray();
    for (int num = 0; num < keys.length; num++) {
      int numF = fAssociations.get(keys[num]).intValue();
      if (mE.correspondences[numF] > -1) {
        survived.put(keys[num], Integer.valueOf(mE.correspondences[numF]));
        int f = mE.correspondences[numF];
        System.out.println(keys[num] + " lambda " + f + ' ' + model.lambda[f]);
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

  static void eraseTmps() {
    File f1 = new File("train.tmp");
    f1.delete();
    f1 = new File("validation.tmp");
    f1.delete();
  }


  static void split(String trainFileName, String fileOne, String fileTwo) throws Exception {

    BufferedReader in = new BufferedReader(new FileReader(trainFileName));
    BufferedWriter out1 = new BufferedWriter(new FileWriter(fileOne));
    BufferedWriter out2 = new BufferedWriter(new FileWriter(fileTwo));

    String line;


    while (true) {
      line = (in.readLine() + '\n');
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
        out1.write(line + '\n');
      } else {
        out2.write(line + '\n');
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

      int res = 0;
      //top=mE.prob.lambda.length+1;
      for (int d = 0; d < test.v.size(); d++) {
        DataDouble dD = test.getData(d);
        int y = getClassification(dD);
        if (clean) {
          System.out.println(train.getYName(y));

        }

        //if(!clean) System.out.println("Answer is "+train.getYName(dD.getYNo()));


        if (y == dD.getYNo()) {
          res++;
        }


      }//d
      if (!clean) {

        System.out.println(mE.prob.lambda.length + " " + top);
        System.out.println(train.numSamples() / (double) train.numClasses);
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
          String key = a + "|" + val + '|' + y;
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
          String key = a + "|" + "num" + '|' + y;
          if (mE.fAssociations.containsKey(key)) {
            numF = mE.fAssociations.get(key).intValue();
            if (numF > -1) {
              posters[y] += (val * model.lambda[numF]);
            }
          }
        }//for y
      }//numeric

    }// for a



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
      WekaProblemSolver wPS = new WekaProblemSolver();
      int crnt = 0;

      while (crnt < args.length) {
        String token = args[crnt];
        if (token.equals("-train")) {
          trainFile = args[++crnt];
          train = true;
          crnt++;
        } else if (token.equals("-test")) {
          trainFile = args[++crnt];
          testFile = args[++crnt];
          test = true;
          crnt++;
        } else if (token.equals("-gain")) {
          gain = Double.parseDouble(args[++crnt]);
          crnt++;
        } else if (token.equals("-support")) {
          cutoff = Integer.parseInt(args[++crnt]);
          System.out.println(" cutoff is " + cutoff);
          crnt++;
        } else if (token.equals("-iters")) {
          iters = Integer.parseInt(args[++crnt]);
          crnt++;
        } else if (token.equals("-ftNum")) {
          ftNum = Integer.parseInt(args[++crnt]);
          crnt++;
        } else if (token.equals("-binary")) {
          binary = true;
          crnt++;
        } else if (token.equals("-crossval")) {
          crossvalidation = true;
          crnt++;
        } else if (token.equals("-validation")) {
          validation = true;
          crnt++;
        } else if (token.equals("-clean")) {
          clean = true;
          crnt++;
        } else if (token.equals("-no_sel")) {
          select = false;
          crnt++;
        }

      } //while

      if (train) {

        if (!validation) {
          wPS.buildClassifier(trainFile, iters, gain);
        } else {
          wPS.buildClassifierValidation(trainFile, iters, gain);
        }
        wPS.save(trainFile);
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

    mE.save(filename + ".me");
    train.save(filename + ".dat");
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


  public void printFeatures() {
    Object[] keys = mE.fAssociations.keySet().toArray();
    for (int num = 0; num < keys.length; num++) {
      int numF = mE.fAssociations.get(keys[num]).intValue();
      if (numF > -1) {
        System.out.println(keys[num] + " lambda " + numF + ' ' + model.lambda[numF]);
      }
    }// for numF
  }


}
