package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.util.Index;

import java.util.*;

/**
 * @author Mengqiu Wang
 * TODO(mengqiu) currently only works with disjoint feature sets
 * for non-disjoint feature sets, need to recompute EHat each iteration, and multiply in the scale
 * in EHat and E calculations for each lopExpert
 */

public class CRFLogConditionalObjectiveFunctionForLOP extends AbstractCachingDiffFunction implements HasCliquePotentialFunction {

  /** label indices - for all possible label sequences - for each feature */
  List<Index<CRFLabel>> labelIndices;
  Index<String> classIndex;  // didn't have <String> before. Added since that's what is assumed everywhere.
  double[][][] Ehat; // empirical counts of all the features [lopIter][feature][class]
  double[] sumOfObservedLogPotential; // empirical sum of all log potentials [lopIter]
  double[][][][][] sumOfExpectedLogPotential; // sumOfExpectedLogPotential[m][i][j][lopIter][k] m-docNo;i-position;j-cliqueNo;k-label 
  List<Set<Integer>> featureIndicesSetArray;
  List<List<Integer>> featureIndicesListArray;
  int window;
  int numClasses;
  int[] map;
  int[][][][] data;  // data[docIndex][tokenIndex][][]
  double[][] lopExpertWeights; // lopExpertWeights[expertIter][weightIndex]
  double[][][] lopExpertWeights2D;
  int[][] labels;    // labels[docIndex][tokenIndex]
  int[][] learnedParamsMapping;
  int numLopExpert;
  boolean backpropTraining;
  int domainDimension = -1;

  String crfType = "maxent";
  String backgroundSymbol;

  public static boolean VERBOSE = false;

  CRFLogConditionalObjectiveFunctionForLOP(int[][][][] data, int[][] labels, double[][] lopExpertWeights, int window,
      Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String backgroundSymbol, int numLopExpert,
      List<Set<Integer>> featureIndicesSetArray, List<List<Integer>> featureIndicesListArray, boolean backpropTraining) {
    this.window = window;
    this.classIndex = classIndex;
    this.numClasses = classIndex.size();
    this.labelIndices = labelIndices;
    this.map = map;
    this.data = data;
    this.lopExpertWeights = lopExpertWeights;
    this.labels = labels;
    this.backgroundSymbol = backgroundSymbol;
    this.numLopExpert = numLopExpert;
    this.featureIndicesSetArray = featureIndicesSetArray;
    this.featureIndicesListArray = featureIndicesListArray;
    this.backpropTraining = backpropTraining;
    initialize2DWeights();
    if (backpropTraining) {
      computeEHat();
    } else {
      logPotential(lopExpertWeights2D);
    }
  }

  @Override
  public int domainDimension() {
    if (domainDimension < 0) {
      domainDimension = numLopExpert;
      if (backpropTraining) {
        // for (int i = 0; i < map.length; i++) {
        //   domainDimension += labelIndices[map[i]].size();
        // }
        for (int i = 0; i < numLopExpert; i++) {
          List<Integer> featureIndicesList = featureIndicesListArray.get(i);
          double[][] expertWeights2D = lopExpertWeights2D[i];
          for (int fIndex: featureIndicesList) {
            int len = expertWeights2D[fIndex].length;
            domainDimension += len;
          }
        }
      }
    }
    return domainDimension;
  }

  @Override 
  public double[] initial() {
    double[] initial = new double[domainDimension()];
    if (backpropTraining) {
      learnedParamsMapping = new int[domainDimension()][3];
      int index = 0;
      for (; index < numLopExpert; index++) {
        initial[index] = 1.0;
      }
      for (int i = 0; i < numLopExpert; i++) {
        List<Integer> featureIndicesList = featureIndicesListArray.get(i);
        double[][] expertWeights2D = lopExpertWeights2D[i];
        for (int fIndex: featureIndicesList) {
          for (int j = 0; j < expertWeights2D[fIndex].length; j++) {
            initial[index] = expertWeights2D[fIndex][j];
            learnedParamsMapping[index] = new int[]{i, fIndex, j};
            index++;
          }
        }
      }
    } else {
      Arrays.fill(initial, 1.0);
    }
    return initial;
  }

  public double[][][] empty2D() {
    double[][][] d2 = new double[numLopExpert][][];
    for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
      double[][] d = new double[map.length][];
      // int index = 0;
      for (int i = 0; i < map.length; i++) {
        d[i] = new double[labelIndices.get(map[i]).size()];
        // cdm july 2005: below array initialization isn't necessary: JLS (3rd ed.) 4.12.5
        // Arrays.fill(d[i], 0.0);
        // index += labelIndices[map[i]].size();
      }
      d2[lopIter] = d;
    }
    return d2;
  }

  private void initialize2DWeights() {
    lopExpertWeights2D = new double[numLopExpert][][];
    for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
      lopExpertWeights2D[lopIter] = to2D(lopExpertWeights[lopIter], labelIndices, map); 
    }
  }

  public double[][] to2D(double[] weights, List<Index<CRFLabel>> labelIndices, int[] map) {
    double[][] newWeights = new double[map.length][];
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      newWeights[i] = new double[labelIndices.get(map[i]).size()];
      System.arraycopy(weights, index, newWeights[i], 0, labelIndices.get(map[i]).size());
      index += labelIndices.get(map[i]).size();
    }
    return newWeights;
  }
  

  private void computeEHat() {
    Ehat = empty2D();

    for (int m = 0; m < data.length; m++) {
      int[][][] docData = data[m];
      int[] docLabels = labels[m];
      int[] windowLabels = new int[window];
      Arrays.fill(windowLabels, classIndex.indexOf(backgroundSymbol));

      if (docLabels.length>docData.length) { // only true for self-training
        // fill the windowLabel array with the extra docLabels
        System.arraycopy(docLabels, 0, windowLabels, 0, windowLabels.length);
        // shift the docLabels array left
        int[] newDocLabels = new int[docData.length];
        System.arraycopy(docLabels, docLabels.length-newDocLabels.length, newDocLabels, 0, newDocLabels.length);
        docLabels = newDocLabels;
      }
      for (int i = 0; i < docData.length; i++) {
        System.arraycopy(windowLabels, 1, windowLabels, 0, window - 1);
        windowLabels[window - 1] = docLabels[i];
        int[][] docDataI = docData[i];

        for (int j = 0; j < docDataI.length; j++) { // j iterates over cliques
          int[] docDataIJ = docDataI[j];
          int[] cliqueLabel = new int[j + 1];
          System.arraycopy(windowLabels, window - 1 - j, cliqueLabel, 0, j + 1);
          CRFLabel crfLabel = new CRFLabel(cliqueLabel);
          Index<CRFLabel> labelIndex = labelIndices.get(j);

          int observedLabelIndex = labelIndex.indexOf(crfLabel);
          //System.err.println(crfLabel + " " + observedLabelIndex);
          for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
            double[][] ehatOfIter = Ehat[lopIter];
            Set<Integer> indicesSet = featureIndicesSetArray.get(lopIter);
            for (int k = 0; k < docDataIJ.length; k++) { // k iterates over features
              int featureIdx = docDataIJ[k];
              if (indicesSet.contains(featureIdx)) {
                ehatOfIter[featureIdx][observedLabelIndex]++;
              }
            }
          }
        }
      }
    }
  }

  private void logPotential(double[][][] learnedLopExpertWeights2D) {
    sumOfExpectedLogPotential = new double[data.length][][][][];
    sumOfObservedLogPotential = new double[numLopExpert];

    for (int m = 0; m < data.length; m++) {
      int[][][] docData = data[m];
      int[] docLabels = labels[m];
      int[] windowLabels = new int[window];
      Arrays.fill(windowLabels, classIndex.indexOf(backgroundSymbol));

      double[][][][] sumOfELPm = new double[docData.length][][][];
      
      if (docLabels.length>docData.length) { // only true for self-training
        // fill the windowLabel array with the extra docLabels
        System.arraycopy(docLabels, 0, windowLabels, 0, windowLabels.length);
        // shift the docLabels array left
        int[] newDocLabels = new int[docData.length];
        System.arraycopy(docLabels, docLabels.length-newDocLabels.length, newDocLabels, 0, newDocLabels.length);
        docLabels = newDocLabels;
      }
      for (int i = 0; i < docData.length; i++) {
        System.arraycopy(windowLabels, 1, windowLabels, 0, window - 1);
        windowLabels[window - 1] = docLabels[i];

        double[][][] sumOfELPmi = new double[docData[i].length][][]; 
        int[][] docDataI = docData[i];

        for (int j = 0; j < docDataI.length; j++) { // j iterates over cliques
          int[] docDataIJ = docDataI[j];
          int[] cliqueLabel = new int[j + 1];
          System.arraycopy(windowLabels, window - 1 - j, cliqueLabel, 0, j + 1);
          CRFLabel crfLabel = new CRFLabel(cliqueLabel);
          Index<CRFLabel> labelIndex = labelIndices.get(j);

          double[][] sumOfELPmij = new double[numLopExpert][];  

          int observedLabelIndex = labelIndex.indexOf(crfLabel);
          //System.err.println(crfLabel + " " + observedLabelIndex);
          for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
            double[] sumOfELPmijIter = new double[labelIndex.size()]; 
            Set<Integer> indicesSet = featureIndicesSetArray.get(lopIter);
            for (int k = 0; k < docDataIJ.length; k++) { // k iterates over features
              int featureIdx = docDataIJ[k];
              if (indicesSet.contains(featureIdx)) {
                sumOfObservedLogPotential[lopIter] += learnedLopExpertWeights2D[lopIter][featureIdx][observedLabelIndex];
                // sum over potential of this clique over all possible labels, used later in calculating expected counts
                for (int l = 0; l < labelIndex.size(); l++) {
                  sumOfELPmijIter[l] += learnedLopExpertWeights2D[lopIter][featureIdx][l];
                }
              }
            }
            sumOfELPmij[lopIter] = sumOfELPmijIter;
          }
          sumOfELPmi[j] = sumOfELPmij;
        }
        sumOfELPm[i] = sumOfELPmi;
      }
      sumOfExpectedLogPotential[m] = sumOfELPm;
    }
  }

  public static double[] combineAndScaleLopWeights(int numLopExpert, double[][] lopExpertWeights, double[] lopScales) {
    double[] newWeights = new double[lopExpertWeights[0].length];
    for (int i = 0; i < newWeights.length; i++) {
      double tempWeight = 0;
      for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
        tempWeight += lopExpertWeights[lopIter][i] * lopScales[lopIter];
      }
      newWeights[i] = tempWeight;
    }
    return newWeights;
  }

  public static double[][] combineAndScaleLopWeights2D(int numLopExpert, double[][][] lopExpertWeights2D, double[] lopScales) {
    double[][] newWeights = new double[lopExpertWeights2D[0].length][];
    for (int i = 0; i < newWeights.length; i++) {
      int innerDim = lopExpertWeights2D[0][i].length;
      double[] innerWeights = new double[innerDim];
      for (int j = 0; j < innerDim; j++) {
        double tempWeight = 0;
        for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
          tempWeight += lopExpertWeights2D[lopIter][i][j] * lopScales[lopIter];
        }
        innerWeights[j] = tempWeight;
      }
      newWeights[i] = innerWeights;
    }
    return newWeights;
  }

  public double[][][] separateLopExpertWeights2D(double[] learnedParams) {
    double[][][] learnedWeights2D = empty2D();
    for (int paramIndex = numLopExpert; paramIndex < learnedParams.length; paramIndex++) {
      int[] mapping = learnedParamsMapping[paramIndex];
      learnedWeights2D[mapping[0]][mapping[1]][mapping[2]] = learnedParams[paramIndex];
    }
    return learnedWeights2D;
  }

  public double[][] separateLopExpertWeights(double[] learnedParams) {
    double[][] learnedWeights = new double[numLopExpert][];
    double[][][] learnedWeights2D = separateLopExpertWeights2D(learnedParams);
    for (int i = 0; i < numLopExpert; i++) {
      learnedWeights[i] = CRFLogConditionalObjectiveFunction.to1D(learnedWeights2D[i], lopExpertWeights[i].length);
    }
    return learnedWeights;
  }

  public double[] separateLopScales(double[] learnedParams) {
    double[] rawScales = new double[numLopExpert];
    System.arraycopy(learnedParams, 0, rawScales, 0, numLopExpert);
    return rawScales;
  }

  public CliquePotentialFunction getCliquePotentialFunction(double[] x) {
    double[] rawScales = separateLopScales(x);
    double[] scales = ArrayMath.softmax(rawScales);
    double[][][] learnedLopExpertWeights2D = lopExpertWeights2D;
    if (backpropTraining) {
      learnedLopExpertWeights2D = separateLopExpertWeights2D(x);
    }

    double[][] combinedWeights2D = combineAndScaleLopWeights2D(numLopExpert, learnedLopExpertWeights2D, scales);
    return new LinearCliquePotentialFunction(combinedWeights2D);
  }

  // todo [cdm]: Below data[m] --> docData
  /**
   * Calculates both value and partial derivatives at the point x, and save them internally.
   */
  @Override
  public void calculate(double[] x) {

    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    double[][][] E = empty2D();
    double[] eScales = new double[numLopExpert];

    double[] rawScales = separateLopScales(x);
    double[] scales = ArrayMath.softmax(rawScales);
    double[][][] learnedLopExpertWeights2D = lopExpertWeights2D;
    if (backpropTraining) {
      learnedLopExpertWeights2D = separateLopExpertWeights2D(x);
      logPotential(learnedLopExpertWeights2D);
    }

    double[][] combinedWeights2D = combineAndScaleLopWeights2D(numLopExpert, learnedLopExpertWeights2D, scales);
    // iterate over all the documents
    for (int m = 0; m < data.length; m++) {
      int[][][] docData = data[m];
      int[] docLabels = labels[m];
      double[][][][] sumOfELPm = sumOfExpectedLogPotential[m]; // sumOfExpectedLogPotential[m][i][j][lopIter][k] m-docNo;i-position;j-cliqueNo;k-label 

      // make a clique tree for this document
      CliquePotentialFunction cliquePotentialFunc = new LinearCliquePotentialFunction(combinedWeights2D);
      CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, cliquePotentialFunc, null);

      // compute the log probability of the document given the model with the parameters x
      int[] given = new int[window - 1];
      Arrays.fill(given, classIndex.indexOf(backgroundSymbol));
      if (docLabels.length > docData.length) { // only true for self-training
        // fill the given array with the extra docLabels
        System.arraycopy(docLabels, 0, given, 0, given.length);
        // shift the docLabels array left
        int[] newDocLabels = new int[docData.length];
        System.arraycopy(docLabels, docLabels.length-newDocLabels.length, newDocLabels, 0, newDocLabels.length);
        docLabels = newDocLabels;
      }
      // iterate over the positions in this document
      for (int i = 0; i < docData.length; i++) {
        int label = docLabels[i];
        double p = cliqueTree.condLogProbGivenPrevious(i, label, given);
        if (VERBOSE) {
          System.err.println("P(" + label + "|" + ArrayMath.toString(given) + ")=" + p);
        }
        prob += p;
        System.arraycopy(given, 1, given, 0, given.length - 1);
        given[given.length - 1] = label;
      }

      // compute the expected counts for this document, which we will need to compute the derivative
      // iterate over the positions in this document
      for (int i = 0; i < docData.length; i++) {
        // for each possible clique at this position
        double[][][] sumOfELPmi = sumOfELPm[i];
        for (int j = 0; j < docData[i].length; j++) {
          double[][] sumOfELPmij = sumOfELPmi[j];
          Index<CRFLabel> labelIndex = labelIndices.get(j);
          // for each possible labeling for that clique
          for (int l = 0; l < labelIndex.size(); l++) {
            int[] label = labelIndex.get(l).getLabel();
            double p = cliqueTree.prob(i, label); // probability of these labels occurring in this clique with these features
            for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
              Set<Integer> indicesSet = featureIndicesSetArray.get(lopIter);
              double scale = scales[lopIter];
              double expected = sumOfELPmij[lopIter][l];
              for (int innerLopIter = 0; innerLopIter < numLopExpert; innerLopIter++) {
                expected -= scales[innerLopIter] * sumOfELPmij[innerLopIter][l];
              }
              expected *= scale;
              eScales[lopIter] += (p * expected);

              double[][] eOfIter = E[lopIter];
              if (backpropTraining) {
                for (int k = 0; k < docData[i][j].length; k++) { // k iterates over features
                  int featureIdx = docData[i][j][k];
                  if (indicesSet.contains(featureIdx)) {
                    eOfIter[featureIdx][l] += p;
                  }
                }
              }
            }
          }
        }
      }
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFLogConditionalObjectiveFunctionForLOP.calculate()");
    }

    value = -prob;
    if(VERBOSE){
      System.err.println("value is " + value);
    }
    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
      double scale = scales[lopIter];
      double observed = sumOfObservedLogPotential[lopIter];
      for (int j = 0; j < numLopExpert; j++) {
        observed -= scales[j] * sumOfObservedLogPotential[j];
      }
      observed *= scale;
      double expected = eScales[lopIter];

      derivative[lopIter] = (expected - observed);
      if (VERBOSE) {
        System.err.println("deriv(" + lopIter + ") = " + expected + " - " + observed + " = " + derivative[lopIter]);
      }
    }
    if (backpropTraining) {
      int dIndex = numLopExpert;
      for (int lopIter = 0; lopIter < numLopExpert; lopIter++) {
        double scale = scales[lopIter];
        double[][] eOfExpert = E[lopIter];
        double[][] ehatOfExpert = Ehat[lopIter];
        List<Integer> featureIndicesList = featureIndicesListArray.get(lopIter);
        for (int fIndex: featureIndicesList) {
          for (int j = 0; j < eOfExpert[fIndex].length; j++) {
            derivative[dIndex++] = scale * (eOfExpert[fIndex][j] - ehatOfExpert[fIndex][j]);
            if (VERBOSE) {
              System.err.println("deriv[" + lopIter+ "](" + fIndex + "," + j + ") = " + scale + " * (" + eOfExpert[fIndex][j] + " - " + ehatOfExpert[fIndex][j] + ") = " + derivative[dIndex - 1]);
            }
          }
        }
      }
      assert(dIndex == domainDimension());
    }
  }
}
