package edu.stanford.nlp.ie.crf; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.optimization.HasRegularizerParamRange;
import edu.stanford.nlp.optimization.HasFeatureGrouping;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Triple;

import java.util.*;

/**
 * @author Mengqiu Wang
 */

public class CRFNonLinearLogConditionalObjectiveFunction extends AbstractCachingDiffFunction implements
    HasCliquePotentialFunction, HasFeatureGrouping, HasRegularizerParamRange {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CRFNonLinearLogConditionalObjectiveFunction.class);

  public static final int NO_PRIOR = 0;
  public static final int QUADRATIC_PRIOR = 1;
  /* Use a Huber robust regression penalty (L1 except very near 0) not L2 */
  public static final int HUBER_PRIOR = 2;
  public static final int QUARTIC_PRIOR = 3;
  public static final int L1_PRIOR = 4;
  boolean useOutputLayer;
  boolean useHiddenLayer;
  boolean useSigmoid;
  SeqClassifierFlags flags;

  int count = 0;
  protected int prior;
  protected double sigma;
  protected double epsilon;
  Random random = new Random(2147483647L);
  /** label indices - for all possible label sequences - for each feature */
  List<Index<CRFLabel>> labelIndices;
  Index<String> classIndex;  // didn't have <String> before. Added since that's what is assumed everywhere.
  double[][] Ehat; // empirical counts of all the linear features [feature][class]
  double[][] Uhat; // empirical counts of all the output layer features [num of class][input layer size]
  double[][] What; // empirical counts of all the input layer features [input layer size][featureIndex.size()]
  int window;
  int numClasses;
  // hidden layer number of neuron = numHiddenUnits * numClasses
  int numHiddenUnits;
  int[] map;
  int[][][][] data;  // data[docIndex][tokenIndex][][]
  double[][][][] featureVal;  // featureVal[docIndex][tokenIndex][][]
  int[][] docWindowLabels;

  int[][] labels;    // labels[docIndex][tokenIndex]
  int domainDimension = -1;
  int inputLayerSize = -1;
  int outputLayerSize = -1;
  int edgeParamCount = -1;
  int numNodeFeatures = -1;
  int numEdgeFeatures = -1;
  int beforeOutputWeights = -1;
  int originalFeatureCount = -1;

  int[][] weightIndices;

  String backgroundSymbol;

  private int[][] featureGrouping = null;
  public static boolean VERBOSE = false;
  public static boolean DEBUG = false;

  public boolean gradientsOnly = false;

  public static int getPriorType(String priorTypeStr)
  {
    if (priorTypeStr == null) return QUADRATIC_PRIOR;  // default
    if ("QUADRATIC".equalsIgnoreCase(priorTypeStr)) {
      return QUADRATIC_PRIOR;
    } else if ("L1".equalsIgnoreCase(priorTypeStr)) {
      return L1_PRIOR;
    } else if ("HUBER".equalsIgnoreCase(priorTypeStr)) {
      return HUBER_PRIOR;
    } else if ("QUARTIC".equalsIgnoreCase(priorTypeStr)) {
      return QUARTIC_PRIOR;
    } else if (priorTypeStr.equalsIgnoreCase("lasso") ||
               priorTypeStr.equalsIgnoreCase("ridge") ||
               priorTypeStr.equalsIgnoreCase("ae-lasso") ||
               priorTypeStr.equalsIgnoreCase("g-lasso") ||
               priorTypeStr.equalsIgnoreCase("sg-lasso") ||
               priorTypeStr.equalsIgnoreCase("NONE") ) {
      return NO_PRIOR;
    } else {
      throw new IllegalArgumentException("Unknown prior type: " + priorTypeStr);
    }
  }

  CRFNonLinearLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, SeqClassifierFlags flags, int numNodeFeatures, int numEdgeFeatures, double[][][][] featureVal) {
    this.window = window;
    this.classIndex = classIndex;
    this.numClasses = classIndex.size();
    this.labelIndices = labelIndices;
    this.data = data;
    this.featureVal = featureVal;
    this.flags = flags;
    this.map = map;
    this.labels = labels;
    this.prior = getPriorType(flags.priorType);
    this.backgroundSymbol = flags.backgroundSymbol;
    this.sigma = flags.sigma;
    this.outputLayerSize = numClasses;
    this.numHiddenUnits = flags.numHiddenUnits;
    if (flags.arbitraryInputLayerSize != -1)
      this.inputLayerSize = flags.arbitraryInputLayerSize;
    else
      this.inputLayerSize = numHiddenUnits * numClasses;
    this.numNodeFeatures = numNodeFeatures;
    this.numEdgeFeatures = numEdgeFeatures;
    log.info("numOfEdgeFeatures: " + numEdgeFeatures);
    this.useOutputLayer = flags.useOutputLayer;
    this.useHiddenLayer = flags.useHiddenLayer;
    this.useSigmoid = flags.useSigmoid;
    this.docWindowLabels = new int[data.length][];
    if (!useOutputLayer) {
      log.info("Output layer not activated, inputLayerSize must be equal to numClasses, setting it to " + numClasses);
      this.inputLayerSize = numClasses;
    } else if (flags.softmaxOutputLayer && !(flags.sparseOutputLayer || flags.tieOutputLayer)) {
      throw new RuntimeException("flags.softmaxOutputLayer == true, but neither flags.sparseOutputLayer or flags.tieOutputLayer is true");
    }
    empiricalCounts();
  }

  @Override
  public int domainDimension() {
    if (domainDimension < 0) {
      domainDimension = 0;
      edgeParamCount = numEdgeFeatures * labelIndices.get(1).size();

      originalFeatureCount = 0;
      for (int aMap : map) {
        int s = labelIndices.get(aMap).size();
        originalFeatureCount += s;
      }

      domainDimension += edgeParamCount;
      domainDimension += inputLayerSize * numNodeFeatures;
      beforeOutputWeights = domainDimension;
      // TODO(mengqiu) temporary fix for debugging
      if (useOutputLayer) {
        if (flags.sparseOutputLayer) {
          domainDimension += outputLayerSize * numHiddenUnits;
        } else if (flags.tieOutputLayer) {
          domainDimension += 1 * numHiddenUnits;
        } else {
          domainDimension += outputLayerSize * inputLayerSize;
        }
      }
      log.info("edgeParamCount: "+edgeParamCount);
      log.info("originalFeatureCount: "+originalFeatureCount);
      log.info("beforeOutputWeights: "+beforeOutputWeights);
      log.info("domainDimension: "+domainDimension);
    }
    return domainDimension;
  }

  @Override
  //TODO(mengqiu) initialize edge feature weights to be weights from CRF
  public double[] initial() {
    double[] initial = new double[domainDimension()];
    // randomly initialize weights
    if (useHiddenLayer || useOutputLayer) {
      double epsilon = 0.1;
      double twoEpsilon = epsilon * 2;
      int count = 0;
      double val = 0;

      // init edge param weights
      for (int i = 0; i < edgeParamCount; i++) {
        val = random.nextDouble() * twoEpsilon - epsilon;
        initial[count++] = val;
      }

      if (flags.blockInitialize) {
        double fanIn = 1/Math.sqrt(numNodeFeatures+0.0);
        double twoFanIn = 2.0 * fanIn;
        int interval = numNodeFeatures / numHiddenUnits;
        for (int i = 0; i < numHiddenUnits; i++) {
          int lower = i * interval;
          int upper = (i + 1) * interval;
          if (i == numHiddenUnits - 1)
            upper = numNodeFeatures;
          for (int j = 0; j < outputLayerSize; j++) {
            for (int k = 0; k < numNodeFeatures; k++) {
              val = 0;
              if (k >= lower && k < upper) {
                val = random.nextDouble() * twoFanIn - fanIn;
              }
              initial[count++] = val;
            }
          }
        }
        if (count != beforeOutputWeights) {
          throw new RuntimeException("after blockInitialize, param Index (" + count + ") not equal to beforeOutputWeights (" + beforeOutputWeights + ")");
        }
      } else {
        double fanIn = 1 / Math.sqrt(numNodeFeatures+0.0);
        double twoFanIn = 2.0 * fanIn;
        for (int i = edgeParamCount; i < beforeOutputWeights; i++) {
          val = random.nextDouble() * twoFanIn - fanIn;
          initial[count++] = val;
        }
      }

      // init output layer weights
      if (flags.sparseOutputLayer) {
        for (int i = 0; i < outputLayerSize; i++) {
          double total = 1;
          for (int j = 0; j < numHiddenUnits-1; j++) {
            val = random.nextDouble() * total;
            initial[count++] = val;
            total -= val;
          }
          initial[count++] = total;
        }
      } else if (flags.tieOutputLayer) {
        double total = 1;
        double sum = 0;
        for (int j = 0; j < numHiddenUnits-1; j++) {
          if (flags.hardcodeSoftmaxOutputWeights)
            val = 1.0 / numHiddenUnits;
          else {
            val = random.nextDouble() * total;
            total -= val;
          }
          initial[count++] = val;
        }
        if (flags.hardcodeSoftmaxOutputWeights)
          initial[count++] = 1.0 / numHiddenUnits;
        else
          initial[count++] = total;
      } else {
        for (int i = beforeOutputWeights; i < domainDimension(); i++) {
          val = random.nextDouble() * twoEpsilon - epsilon;
          initial[count++] = val;
        }
      }
      if (count != domainDimension()) {
        throw new RuntimeException("after param initialization, param Index (" + count + ") not equal to domainDimension (" + domainDimension() + ")");
      }
    }
    return initial;
  }

  private void empiricalCounts() {
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
        // for (int j = 1; j < docData[i].length; j++) { // j starting from 1, skip all node features
        //TODO(mengqiu) generalize this for bigger cliques
        int j = 1;
        int[] cliqueLabel = new int[j + 1];
        System.arraycopy(windowLabels, window - 1 - j, cliqueLabel, 0, j + 1);
        CRFLabel crfLabel = new CRFLabel(cliqueLabel);
        int labelIndex = labelIndices.get(j).indexOf(crfLabel);
        int[] cliqueFeatures = docData[i][j];
        //log.info(crfLabel + " " + labelIndex);
        for (int cliqueFeature : cliqueFeatures) {
          Ehat[cliqueFeature][labelIndex]++;
        }
      }
    }
  }

  private double[][] emptyU() {
    int innerSize = inputLayerSize;
    if (flags.sparseOutputLayer || flags.tieOutputLayer) {
      innerSize = numHiddenUnits;
    }
    int outerSize = outputLayerSize;
    if (flags.tieOutputLayer) {
      outerSize = 1;
    }

    double[][] temp = new double[outerSize][innerSize];
    for (int i = 0; i < outerSize; i++) {
      temp[i] = new double[innerSize];
    }
    return temp;
  }

  private double[][] emptyW() {
    // TODO(mengqiu) temporary fix for debugging
    double[][] temp = new double[inputLayerSize][numNodeFeatures];
    for (int i = 0; i < inputLayerSize; i++) {
      temp[i] = new double[numNodeFeatures];
    }
    return temp;
  }

  public Triple<double[][], double[][], double[][]> separateWeights(double[] x) {
    double[] linearWeights = new double[edgeParamCount];
    System.arraycopy(x, 0, linearWeights, 0, edgeParamCount);
    double[][] linearWeights2D = to2D(linearWeights);
    int index = edgeParamCount;

    double[][] inputLayerWeights = emptyW();
    for (int i = 0; i < inputLayerWeights.length; i++) {
      for (int j = 0; j < inputLayerWeights[i].length; j++) {
        inputLayerWeights[i][j] = x[index++];
      }
    }

    double[][] outputLayerWeights = emptyU();
    for (int i = 0; i < outputLayerWeights.length; i++) {
      for (int j = 0; j < outputLayerWeights[i].length; j++) {
        if (useOutputLayer) {
          if (flags.hardcodeSoftmaxOutputWeights)
            outputLayerWeights[i][j] = 1.0 / numHiddenUnits;
          else
            outputLayerWeights[i][j] = x[index++];
        } else
          outputLayerWeights[i][j] = 1;
      }
    }
    assert(index == x.length);
    return new Triple<>(linearWeights2D, inputLayerWeights, outputLayerWeights);
  }

  public CliquePotentialFunction getCliquePotentialFunction(double[] x) {
    Triple<double[][], double[][], double[][]> allParams = separateWeights(x);
    double[][] linearWeights = allParams.first();
    double[][] W = allParams.second(); // inputLayerWeights
    double[][] U = allParams.third(); // outputLayerWeights
    return new NonLinearCliquePotentialFunction(linearWeights, W, U, flags);
  }

  /**
   * Calculates both value and partial derivatives at the point x, and save them internally.
   */
  @Override
  public void calculate(double[] x) {

    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    Triple<double[][], double[][], double[][]> allParams = separateWeights(x);
    double[][] linearWeights = allParams.first();
    double[][] W = allParams.second(); // inputLayerWeights
    double[][] U = allParams.third(); // outputLayerWeights

    double[][] Y = null;
    if (flags.softmaxOutputLayer) {
      Y = new double[U.length][];
      for (int i = 0; i < U.length; i++) {
        Y[i] = ArrayMath.softmax(U[i]);
      }
    }

    double[][] What = emptyW();
    double[][] Uhat = emptyU();

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    double[][] E = empty2D();
    double[][] eW = emptyW();
    double[][] eU = emptyU();

    // iterate over all the documents
    for (int m = 0; m < data.length; m++) {
      int[][][] docData = data[m];
      int[] docLabels = labels[m];

      double[][][] featureVal3DArr = null;
      if (featureVal != null)
        featureVal3DArr = featureVal[m];

      if (DEBUG) log.info("processing doc " + m);

      NonLinearCliquePotentialFunction cliquePotentialFunction = new NonLinearCliquePotentialFunction(linearWeights, W, U, flags);

      // make a clique tree for this document
      CRFCliqueTree<String> cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex,
        backgroundSymbol, cliquePotentialFunction, featureVal3DArr);

      // compute the log probability of the document given the model with the parameters x
      int[] given = new int[window - 1];
      if (!gradientsOnly)
        Arrays.fill(given, classIndex.indexOf(backgroundSymbol));
      int[] windowLabels = new int[window];
      Arrays.fill(windowLabels, classIndex.indexOf(backgroundSymbol));

      if (docLabels.length>docData.length) { // only true for self-training
        // fill the given array with the extra docLabels
        System.arraycopy(docLabels, 0, given, 0, given.length);
        System.arraycopy(docLabels, 0, windowLabels, 0, windowLabels.length);
        // shift the docLabels array left
        int[] newDocLabels = new int[docData.length];
        System.arraycopy(docLabels, docLabels.length-newDocLabels.length, newDocLabels, 0, newDocLabels.length);
        docLabels = newDocLabels;
      }
      if (!gradientsOnly) {
        // iterate over the positions in this document
        for (int i = 0; i < docData.length; i++) {
          int label = docLabels[i];
          double p = cliqueTree.condLogProbGivenPrevious(i, label, given);
          if (VERBOSE) {
            log.info("P(" + label + "|" + ArrayMath.toString(given) + ")=" + p);
          }
          prob += p;
          System.arraycopy(given, 1, given, 0, given.length - 1);
          given[given.length - 1] = label;
        }
      }

      // compute the expected counts for this document, which we will need to compute the derivative
      // iterate over the positions in this document
      for (int i = 0; i < docData.length; i++) {
        // for each possible clique at this position
        System.arraycopy(windowLabels, 1, windowLabels, 0, window - 1);
        windowLabels[window - 1] = docLabels[i];
        for (int j = 0; j < docData[i].length; j++) {
          Index<CRFLabel> labelIndex = labelIndices.get(j);
          // for each possible labeling for that clique
          int[] cliqueFeatures = docData[i][j];
          double[] As = null;
          double[] fDeriv = null;
          double[][] yTimesA = null;
          double[] sumOfYTimesA = null;

          if (DEBUG) log.info("calculating Ehat[" + i + "]");
          // calculating empirical counts of node features
          if (j == 0) {
            double[] featureValArr = null;
            if (featureVal3DArr != null)
              featureValArr = featureVal3DArr[i][j];
            As = cliquePotentialFunction.hiddenLayerOutput(W, cliqueFeatures, flags, featureValArr);
            fDeriv = new double[inputLayerSize];
            double fD = 0;
            for (int q = 0; q < inputLayerSize; q++) {
              if (useSigmoid) {
                fD = As[q] * (1 - As[q]);
              } else {
                fD = 1 - As[q] * As[q];
              }
              fDeriv[q] = fD;
            }

            // calculating yTimesA for softmax
            if (flags.softmaxOutputLayer) {
              double val = 0;

              yTimesA = new double[outputLayerSize][numHiddenUnits];
              for (int ii = 0; ii < outputLayerSize; ii++) {
                yTimesA[ii] = new double[numHiddenUnits];
              }
              sumOfYTimesA = new double[outputLayerSize];

              for (int k = 0; k < outputLayerSize; k++) {
                double[] Yk = null;
                if (flags.tieOutputLayer) {
                  Yk = Y[0];
                } else {
                  Yk = Y[k];
                }
                double sum = 0;
                for (int q = 0; q < inputLayerSize; q++) {
                  if (q % outputLayerSize == k) {
                    int hiddenUnitNo = q / outputLayerSize;
                    val = As[q] * Yk[hiddenUnitNo];
                    yTimesA[k][hiddenUnitNo] = val;
                    sum += val;
                  }
                }
                sumOfYTimesA[k] = sum;
              }
            }

            // calculating Uhat What
            int[] cliqueLabel = new int[j + 1];
            System.arraycopy(windowLabels, window - 1 - j, cliqueLabel, 0, j + 1);

            CRFLabel crfLabel = new CRFLabel(cliqueLabel);
            int givenLabelIndex = labelIndex.indexOf(crfLabel);
            double[] Uk = null;
            double[] UhatK = null;
            double[] Yk = null;
            double[] yTimesAK = null;
            double sumOfYTimesAK = 0;
            if (flags.tieOutputLayer) {
              Uk = U[0];
              UhatK = Uhat[0];
              if (flags.softmaxOutputLayer) {
                Yk = Y[0];
              }
            } else {
              Uk = U[givenLabelIndex];
              UhatK = Uhat[givenLabelIndex];
              if (flags.softmaxOutputLayer) {
                Yk = Y[givenLabelIndex];
              }
            }

            if (flags.softmaxOutputLayer) {
              yTimesAK = yTimesA[givenLabelIndex];
              sumOfYTimesAK = sumOfYTimesA[givenLabelIndex];
            }

            for (int k = 0; k < inputLayerSize; k++) {
              double deltaK = 1;
              if (flags.sparseOutputLayer || flags.tieOutputLayer) {
                if (k % outputLayerSize == givenLabelIndex) {
                  int hiddenUnitNo = k / outputLayerSize;
                  if (flags.softmaxOutputLayer) {
                    UhatK[hiddenUnitNo] += (yTimesAK[hiddenUnitNo] - Yk[hiddenUnitNo] * sumOfYTimesAK);
                    deltaK *= Yk[hiddenUnitNo];
                  } else {
                    UhatK[hiddenUnitNo] += As[k];
                    deltaK *= Uk[hiddenUnitNo];
                  }
                }
              } else {
                UhatK[k] += As[k];
                if (useOutputLayer) {
                  deltaK *= Uk[k];
                }
              }
              if (useHiddenLayer)
                deltaK *= fDeriv[k];
              if (useOutputLayer) {
                if (flags.sparseOutputLayer || flags.tieOutputLayer) {
                  if (k % outputLayerSize == givenLabelIndex) {
                    double[] WhatK = What[k];
                    for (int n = 0; n < cliqueFeatures.length; n++) {
                      double fVal = 1.0;
                      if (featureVal3DArr != null)
                        fVal = featureVal3DArr[i][j][n];
                      WhatK[cliqueFeatures[n]] += deltaK * fVal;
                    }
                  }
                } else {
                  double[] WhatK = What[k];
                  double fVal = 1.0;
                  for (int n = 0; n < cliqueFeatures.length; n++) {
                    fVal = 1.0;
                    if (featureVal3DArr != null)
                      fVal = featureVal3DArr[i][j][n];
                    WhatK[cliqueFeatures[n]] += deltaK * fVal;
                  }
                }
              } else {
                if (k == givenLabelIndex) {
                  double[] WhatK = What[k];
                  double fVal = 1.0;
                  for (int n = 0; n < cliqueFeatures.length; n++) {
                    fVal = 1.0;
                    if (featureVal3DArr != null)
                      fVal = featureVal3DArr[i][j][n];
                    WhatK[cliqueFeatures[n]] += deltaK * fVal;
                  }
                }
              }
            }
          }
          if (DEBUG) log.info(" done!");

          if (DEBUG) log.info("calculating E[" + i + "]");
          // calculate expected count of features
          for (int k = 0; k < labelIndex.size(); k++) { // labelIndex.size() == numClasses
            int[] label = labelIndex.get(k).getLabel();
            double p = cliqueTree.prob(i, label); // probability of these labels occurring in this clique with these features
            if (j == 0) { // for node features
              double[] Uk = null;
              double[] eUK = null;
              double[] Yk = null;
              if (flags.tieOutputLayer) {
                Uk = U[0];
                eUK = eU[0];
                if (flags.softmaxOutputLayer) {
                  Yk = Y[0];
                }
              } else {
                Uk = U[k];
                eUK = eU[k];
                if (flags.softmaxOutputLayer) {
                  Yk = Y[k];
                }
              }
              if (useOutputLayer) {
                for (int q = 0; q < inputLayerSize; q++) {
                  double deltaQ = 1;
                  if (flags.sparseOutputLayer || flags.tieOutputLayer) {
                    if (q % outputLayerSize == k) {
                      int hiddenUnitNo = q / outputLayerSize;
                      if (flags.softmaxOutputLayer) {
                        eUK[hiddenUnitNo] += (yTimesA[k][hiddenUnitNo] - Yk[hiddenUnitNo] * sumOfYTimesA[k]) * p;
                        deltaQ = Yk[hiddenUnitNo];
                      } else {
                        eUK[hiddenUnitNo] += As[q] * p;
                        deltaQ = Uk[hiddenUnitNo];
                      }
                    }
                  } else {
                    eUK[q] += As[q] * p;
                    deltaQ = Uk[q];
                  }
                  if (useHiddenLayer)
                    deltaQ *= fDeriv[q];
                  if (flags.sparseOutputLayer || flags.tieOutputLayer) {
                    if (q % outputLayerSize == k) {
                      double[] eWq = eW[q];
                      double fVal = 1.0;
                      for (int n = 0; n < cliqueFeatures.length; n++) {
                        fVal = 1.0;
                        if (featureVal3DArr != null)
                          fVal = featureVal3DArr[i][j][n];
                        eWq[cliqueFeatures[n]] += deltaQ * p * fVal;
                      }
                    }
                  } else {
                    double[] eWq = eW[q];
                    double fVal = 1.0;
                    for (int n = 0; n < cliqueFeatures.length; n++) {
                      fVal = 1.0;
                      if (featureVal3DArr != null)
                        fVal = featureVal3DArr[i][j][n];
                      eWq[cliqueFeatures[n]] += deltaQ * p * fVal;
                    }
                  }
                }
              } else {
                double deltaK = 1;
                if (useHiddenLayer)
                  deltaK *= fDeriv[k];
                double[] eWK = eW[k];
                double fVal = 1.0;
                for (int n = 0; n < cliqueFeatures.length; n++) {
                  fVal = 1.0;
                  if (featureVal3DArr != null)
                    fVal = featureVal3DArr[i][j][n];
                  eWK[cliqueFeatures[n]] += deltaK * p * fVal;
                }
              }
            } else { // for edge features
              for (int cliqueFeature : cliqueFeatures) {
                E[cliqueFeature][k] += p;
              }
            }
          }
          if (DEBUG) log.info(" done!");
        }
      }
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFNonLinearLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;
    if(VERBOSE){
      log.info("value is " + value);
    }

    if (DEBUG) log.info("calculating derivative ");
    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        derivative[index++] = (E[i][j] - Ehat[i][j]);
        if (VERBOSE) {
          log.info("linearWeights deriv(" + i + "," + j + ") = " + E[i][j] + " - " + Ehat[i][j] + " = " + derivative[index - 1]);
        }
      }
    }
    if (index != edgeParamCount)
      throw new RuntimeException("after edge derivative, index("+index+") != edgeParamCount("+edgeParamCount+")");

    for (int i = 0; i < eW.length; i++) {
      for (int j = 0; j < eW[i].length; j++) {
        derivative[index++] = (eW[i][j] - What[i][j]);
        if (VERBOSE) {
          log.info("inputLayerWeights deriv(" + i + "," + j + ") = " + eW[i][j] + " - " + What[i][j] + " = " + derivative[index - 1]);
        }
      }
    }

    if (index != beforeOutputWeights)
      throw new RuntimeException("after W derivative, index("+index+") != beforeOutputWeights("+beforeOutputWeights+")");

    if (useOutputLayer) {
      for (int i = 0; i < eU.length; i++) {
        for (int j = 0; j < eU[i].length; j++) {
          if (flags.hardcodeSoftmaxOutputWeights)
            derivative[index++] = 0;
          else
            derivative[index++] = (eU[i][j] - Uhat[i][j]);
          if (VERBOSE) {
            log.info("outputLayerWeights deriv(" + i + "," + j + ") = " + eU[i][j] + " - " + Uhat[i][j] + " = " + derivative[index - 1]);
          }
        }
      }
    }

    if (index != x.length)
      throw new RuntimeException("after W derivative, index("+index+") != x.length("+x.length+")");

    int regSize = x.length;
    if (flags.skipOutputRegularization || flags.softmaxOutputLayer || flags.hardcodeSoftmaxOutputWeights) {
      regSize = beforeOutputWeights;
    }

    if (DEBUG) log.info("done!");

    if (DEBUG) log.info("incorporating priors ...");

    // incorporate priors
    if (prior == QUADRATIC_PRIOR) {
      double sigmaSq = sigma * sigma;
      double twoSigmaSq =  2.0 * sigmaSq;
      double w = 0;
      double valueSum = 0;
      for (int i = 0; i < regSize; i++) {
        w = x[i];
        valueSum += w * w;
        derivative[i] += w / sigmaSq;
      }
      value += valueSum / twoSigmaSq;
    } else if (prior == L1_PRIOR) { // Do nothing, as the prior will be applied in OWL-QN
    } else if (prior == HUBER_PRIOR) {
      double sigmaSq = sigma * sigma;
      for (int i = 0; i < regSize; i++) {
        double w = x[i];
        double wabs = Math.abs(w);
        if (wabs < epsilon) {
          value += w * w / 2.0 / epsilon / sigmaSq;
          derivative[i] += w / epsilon / sigmaSq;
        } else {
          value += (wabs - epsilon / 2) / sigmaSq;
          derivative[i] += ((w < 0.0) ? -1.0 : 1.0) / sigmaSq;
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      double sigmaQu = sigma * sigma * sigma * sigma;
      for (int i = 0; i < regSize; i++) {
        double k = 1.0;
        double w = x[i];
        value += k * w * w * w * w / 2.0 / sigmaQu;
        derivative[i] += k * w / sigmaQu;
      }
    }

    if (flags.regularizeSoftmaxTieParam &&
        flags.softmaxOutputLayer && !flags.hardcodeSoftmaxOutputWeights) {
      // lambda is 1/(2*sigma*sigma)
      double softmaxLambda = flags.softmaxTieLambda;
      double oneDividedByTwoSigmaSq = softmaxLambda * 2;
      double y = 0;
      double mean = 1.0 / numHiddenUnits;
      int count = 0;
      for (double[] aU : U) {
        for (int j = 0; j < aU.length; j++) {
          y = aU[j];
          value += (y - mean) * (y - mean) * softmaxLambda;
          double grad = (y - mean) * oneDividedByTwoSigmaSq;
          // log.info("U["+i+"]["+j+"]="+x[beforeOutputWeights+count]+", Y["+i+"]["+j+"]="+Y[i][j]+", grad="+grad);
          derivative[beforeOutputWeights + count] += grad;
          count++;
        }
      }
    }
    if (DEBUG) log.info("done!");
  }

  public Set<Integer> getRegularizerParamRange(double[] x) {
    Set<Integer> paramRange = Generics.newHashSet(x.length);
    for (int i = 0; i < beforeOutputWeights; i++)
      paramRange.add(i);
    return paramRange;
  }

  public double[][] to2D(double[] linearWeights) {
    double[][] newWeights = new double[numEdgeFeatures][];
    int index = 0;
    int labelIndicesSize = labelIndices.get(1).size();
    for (int i = 0; i < numEdgeFeatures; i++) {
      newWeights[i] = new double[labelIndicesSize];
      System.arraycopy(linearWeights, index, newWeights[i], 0, labelIndicesSize);
      index += labelIndicesSize;
    }
    return newWeights;
  }

  public double[][] empty2D() {
    double[][] d = new double[numEdgeFeatures][];
    // int index = 0;
    int labelIndicesSize = labelIndices.get(1).size();
    for (int i = 0; i < numEdgeFeatures; i++) {
      d[i] = new double[labelIndicesSize];
      // cdm july 2005: below array initialization isn't necessary: JLS (3rd ed.) 4.12.5
      // Arrays.fill(d[i], 0.0);
      // index += labelIndices.get(map[i]).size();
    }
    return d;
  }

  public double[][] emptyFull2D() {
    double[][] d = new double[map.length][];
    // int index = 0;
    for (int i = 0; i < map.length; i++) {
      d[i] = new double[labelIndices.get(map[i]).size()];
      // cdm july 2005: below array initialization isn't necessary: JLS (3rd ed.) 4.12.5
      // Arrays.fill(d[i], 0.0);
      // index += labelIndices.get(map[i]).size();
    }
    return d;
  }

  @Override
  public int[][] getFeatureGrouping() {
    if (featureGrouping != null)
      return featureGrouping;
    else {
      List<Set<Integer>> groups = new ArrayList<>();
      if (flags.groupByInput) {
        for (int nodeFeatureIndex = 0; nodeFeatureIndex < numNodeFeatures; nodeFeatureIndex++) { // for each node feature, we enforce the sparsity
          Set<Integer> newSet = new HashSet<>();
          for (int outputClassIndex = 0; outputClassIndex < numClasses; outputClassIndex++) {
            for (int hiddenUnitIndex = 0; hiddenUnitIndex < numHiddenUnits; hiddenUnitIndex++) {
              int firstLayerIndex = hiddenUnitIndex * numClasses + outputClassIndex;
              int oneDIndex = firstLayerIndex * numNodeFeatures + nodeFeatureIndex + edgeParamCount;
              newSet.add(oneDIndex);
            }
          }
          groups.add(newSet);
        }
      } else if (flags.groupByHiddenUnit) {
        for (int nodeFeatureIndex = 0; nodeFeatureIndex < numNodeFeatures; nodeFeatureIndex++) { // for each node feature, we enforce the sparsity
          for (int hiddenUnitIndex = 0; hiddenUnitIndex < numHiddenUnits; hiddenUnitIndex++) {
            Set<Integer> newSet = new HashSet<>();
            for (int outputClassIndex = 0; outputClassIndex < numClasses; outputClassIndex++) {
              int firstLayerIndex = hiddenUnitIndex * numClasses + outputClassIndex;
              int oneDIndex = firstLayerIndex * numNodeFeatures + nodeFeatureIndex + edgeParamCount;
              newSet.add(oneDIndex);
            }
            groups.add(newSet);
          }
        }
      } else {
        for (int nodeFeatureIndex = 0; nodeFeatureIndex < numNodeFeatures; nodeFeatureIndex++) { // for each node feature, we enforce the sparsity
          for (int outputClassIndex = 0; outputClassIndex < numClasses; outputClassIndex++) {
            Set<Integer> newSet = new HashSet<>();
            for (int hiddenUnitIndex = 0; hiddenUnitIndex < numHiddenUnits; hiddenUnitIndex++) {
              int firstLayerIndex = hiddenUnitIndex * numClasses + outputClassIndex;
              int oneDIndex = firstLayerIndex * numNodeFeatures + nodeFeatureIndex + edgeParamCount;
              newSet.add(oneDIndex);
            }
            groups.add(newSet);
          }
        }
      }

      int[][] fg = new int[groups.size()][];
      for (int i = 0; i < fg.length; i++) {
        Set<Integer> aSet = groups.get(i);
        fg[i] = new int[aSet.size()];
        int ind = 0;
        for (int j: aSet)
          fg[i][ind++] = j;
      }
      featureGrouping = fg;
      return fg;
    }
  }

}
