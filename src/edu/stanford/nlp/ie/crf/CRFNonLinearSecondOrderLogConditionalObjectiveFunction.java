package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.Quadruple;

import java.util.*;

/**
 * @author Mengqiu Wang
 */

public class CRFNonLinearSecondOrderLogConditionalObjectiveFunction extends AbstractCachingDiffFunction implements HasCliquePotentialFunction {

  public static final int NO_PRIOR = 0;
  public static final int QUADRATIC_PRIOR = 1;
  /* Use a Huber robust regression penalty (L1 except very near 0) not L2 */
  public static final int HUBER_PRIOR = 2;
  public static final int QUARTIC_PRIOR = 3;

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
  int[][] docWindowLabels;

  int[][] labels;    // labels[docIndex][tokenIndex]
  int domainDimension = -1;
  int inputLayerSize = -1;
  int outputLayerSize = -1;
  int inputLayerSize4Edge= -1;
  int outputLayerSize4Edge = -1;

  int edgeParamCount = -1;
  int numNodeFeatures = -1;
  int numEdgeFeatures = -1;
  int beforeOutputWeights = -1;

  // for debugging
  int originalFeatureCount = -1;

  int[][] weightIndices;

  String crfType = "maxent";
  String backgroundSymbol;

  public static boolean VERBOSE = false;

  public static int getPriorType(String priorTypeStr)
  {
    if (priorTypeStr == null) return QUADRATIC_PRIOR;  // default
    if ("QUADRATIC".equalsIgnoreCase(priorTypeStr)) {
      return QUADRATIC_PRIOR;
    } else if ("HUBER".equalsIgnoreCase(priorTypeStr)) {
      return HUBER_PRIOR;
    } else if ("QUARTIC".equalsIgnoreCase(priorTypeStr)) {
      return QUARTIC_PRIOR;
    } else if (priorTypeStr.equalsIgnoreCase("NONE")) {
      return NO_PRIOR;
    } else {
      throw new IllegalArgumentException("Unknown prior type: " + priorTypeStr);
    }
  }

  CRFNonLinearSecondOrderLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index classIndex, List<Index<CRFLabel>> labelIndices, int[] map, SeqClassifierFlags flags, int numNodeFeatures, int numEdgeFeatures) {
    this(data, labels, window, classIndex, labelIndices, map, QUADRATIC_PRIOR, flags, numNodeFeatures, numEdgeFeatures);
  }

  CRFNonLinearSecondOrderLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, int prior, SeqClassifierFlags flags, int numNodeFeatures, int numEdgeFeatures) {
    this.window = window;
    this.classIndex = classIndex;
    this.numClasses = classIndex.size();
    this.labelIndices = labelIndices;
    this.data = data;
    this.flags = flags;
    this.map = map;
    this.labels = labels;
    this.prior = prior;
    this.backgroundSymbol = flags.backgroundSymbol;
    this.sigma = flags.sigma;
    this.outputLayerSize = numClasses;
    this.outputLayerSize4Edge = numClasses * numClasses;
    this.numHiddenUnits = flags.numHiddenUnits;
    this.inputLayerSize = numHiddenUnits * numClasses;
    this.inputLayerSize4Edge = numHiddenUnits * numClasses * numClasses;
    this.numNodeFeatures = numNodeFeatures;
    this.numEdgeFeatures = numEdgeFeatures;
    this.useOutputLayer = flags.useOutputLayer;
    this.useHiddenLayer = flags.useHiddenLayer;
    this.useSigmoid = flags.useSigmoid;
    this.docWindowLabels = new int[data.length][];
    if (!useOutputLayer) {
      System.err.println("Output layer not activated, inputLayerSize must be equal to numClasses, setting it to " + numClasses);
      this.inputLayerSize = numClasses;
      this.inputLayerSize4Edge = numClasses * numClasses;
    } else if (flags.softmaxOutputLayer && !(flags.sparseOutputLayer || flags.tieOutputLayer)) {
      throw new RuntimeException("flags.softmaxOutputLayer == true, but neither flags.sparseOutputLayer or flags.tieOutputLayer is true");
    }
    // empiricalCounts();
  }

  @Override
  public int domainDimension() {
    if (domainDimension < 0) {
      originalFeatureCount = 0;
      for (int i = 0; i < map.length; i++) {
        int s = labelIndices.get(map[i]).size();
        originalFeatureCount += s;
      }
      domainDimension = 0;
      domainDimension += inputLayerSize4Edge * numEdgeFeatures;
      domainDimension += inputLayerSize * numNodeFeatures;
      beforeOutputWeights = domainDimension;
      if (useOutputLayer) {
        if (flags.sparseOutputLayer) {
          domainDimension += outputLayerSize4Edge * numHiddenUnits;
          domainDimension += outputLayerSize * numHiddenUnits;
        } else if (flags.tieOutputLayer) {
          domainDimension += 1 * numHiddenUnits;
          domainDimension += 1 * numHiddenUnits;
        } else {
          domainDimension += outputLayerSize4Edge * inputLayerSize4Edge;
          domainDimension += outputLayerSize * inputLayerSize;
        }
      }
      System.err.println("originalFeatureCount: "+originalFeatureCount);
      System.err.println("beforeOutputWeights: "+beforeOutputWeights);
      System.err.println("domainDimension: "+domainDimension);
    }
    return domainDimension;
  }

  @Override 
  public double[] initial() {
    double[] initial = new double[domainDimension()];
    // randomly initialize weights
    if (useHiddenLayer || useOutputLayer) {
      double epsilon = 0.1;
      double twoEpsilon = epsilon * 2;
      int count = 0;
      double val = 0;

      if (flags.blockInitialize) {
        int interval4Edge = numEdgeFeatures / numHiddenUnits;
        for (int i = 0; i < numHiddenUnits; i++) {
          int lower = i * interval4Edge;
          int upper = (i + 1) * interval4Edge;
          if (i == numHiddenUnits - 1)
            upper = numEdgeFeatures;
          for (int j = 0; j < outputLayerSize4Edge; j++) {
            for (int k = 0; k < numEdgeFeatures; k++) {
              val = 0;
              if (k >= lower && k < upper) {
                val = random.nextDouble() * twoEpsilon - epsilon;
              }
              initial[count++] = val;
            }
          }
        }

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
                val = random.nextDouble() * twoEpsilon - epsilon;
              }
              initial[count++] = val;
            }
          }
        }
        if (count != beforeOutputWeights) {
          throw new RuntimeException("after blockInitialize, param Index (" + count + ") not equal to beforeOutputWeights (" + beforeOutputWeights + ")");
        }
      } else {
        for (int i = 0; i < beforeOutputWeights; i++) {
          val = random.nextDouble() * twoEpsilon - epsilon;
          initial[count++] = val;
        }
      }

      if (flags.sparseOutputLayer) {
        for (int i = 0; i < outputLayerSize4Edge; i++) {
          double total = 1;
          for (int j = 0; j < numHiddenUnits-1; j++) {
            val = random.nextDouble() * total;
            initial[count++] = val;
            total -= val;
          }
          initial[count++] = total;
        }
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
          val = random.nextDouble() * total;
          initial[count++] = val;
          total -= val;
        }
        initial[count++] = total;
        total = 1;
        sum = 0;
        for (int j = 0; j < numHiddenUnits-1; j++) {
          val = random.nextDouble() * total;
          initial[count++] = val;
          total -= val;
        }
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

  private double[][] emptyU4Edge() {
    int innerSize = inputLayerSize4Edge;
    if (flags.sparseOutputLayer || flags.tieOutputLayer) {
      innerSize = numHiddenUnits;
    }
    int outerSize = outputLayerSize4Edge;
    if (flags.tieOutputLayer) {
      outerSize = 1;
    }

    double[][] temp = new double[outerSize][innerSize];
    for (int i = 0; i < outerSize; i++) {
      temp[i] = new double[innerSize];
    }
    return temp;
  }

  private double[][] emptyW4Edge() {
    double[][] temp = new double[inputLayerSize4Edge][numEdgeFeatures];
    for (int i = 0; i < inputLayerSize; i++) {
      temp[i] = new double[numEdgeFeatures];
    }
    return temp;
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
    double[][] temp = new double[inputLayerSize][numNodeFeatures];
    for (int i = 0; i < inputLayerSize; i++) {
      temp[i] = new double[numNodeFeatures];
    }
    return temp;
  }

  public Quadruple<double[][], double[][], double[][], double[][]> separateWeights(double[] x) {
    int index = 0;
    double[][] inputLayerWeights4Edge = emptyW4Edge();
    for (int i = 0; i < inputLayerWeights4Edge.length; i++) {
      for (int j = 0; j < inputLayerWeights4Edge[i].length; j++) {
        inputLayerWeights4Edge[i][j] = x[index++];
      }
    }

    double[][] inputLayerWeights = emptyW();
    for (int i = 0; i < inputLayerWeights.length; i++) {
      for (int j = 0; j < inputLayerWeights[i].length; j++) {
        inputLayerWeights[i][j] = x[index++];
      }
    }

    double[][] outputLayerWeights4Edge = emptyU4Edge();
    for (int i = 0; i < outputLayerWeights4Edge.length; i++) {
      for (int j = 0; j < outputLayerWeights4Edge[i].length; j++) {
        if (useOutputLayer)
          outputLayerWeights4Edge[i][j] = x[index++];
        else
          outputLayerWeights4Edge[i][j] = 1;
      }
    }

    double[][] outputLayerWeights = emptyU();
    for (int i = 0; i < outputLayerWeights.length; i++) {
      for (int j = 0; j < outputLayerWeights[i].length; j++) {
        if (useOutputLayer)
          outputLayerWeights[i][j] = x[index++];
        else
          outputLayerWeights[i][j] = 1;
      }
    }
    assert(index == x.length);
    return new Quadruple<double[][], double[][], double[][], double[][]>(inputLayerWeights4Edge, outputLayerWeights4Edge, inputLayerWeights, outputLayerWeights);
  }

  public CliquePotentialFunction getCliquePotentialFunction(double[] x) {
    Quadruple<double[][], double[][], double[][], double[][]> allParams = separateWeights(x);
    double[][] W4Edge = allParams.first(); // inputLayerWeights4Edge
    double[][] U4Edge = allParams.second(); // outputLayerWeights4Edge
    double[][] W = allParams.third(); // inputLayerWeights 
    double[][] U = allParams.fourth(); // outputLayerWeights 
    return new NonLinearSecondOrderCliquePotentialFunction(W4Edge, U4Edge, W, U, flags);
  }


  // todo [cdm]: Below data[m] --> docData
  /**
   * Calculates both value and partial derivatives at the point x, and save them internally.
   */
  @Override
  public void calculate(double[] x) {

    double prob = 0.0; // the log prob of the sequence given the model, which is the negation of value at this point
    Quadruple<double[][], double[][], double[][], double[][]> allParams = separateWeights(x);
    double[][] W4Edge = allParams.first(); // inputLayerWeights4Edge
    double[][] U4Edge = allParams.second(); // outputLayerWeights4Edge
    double[][] W = allParams.third(); // inputLayerWeights 
    double[][] U = allParams.fourth(); // outputLayerWeights 

    double[][] Y4Edge = null;
    double[][] Y = null;
    if (flags.softmaxOutputLayer) {
      Y4Edge = new double[U4Edge.length][];
      for (int i = 0; i < U4Edge.length; i++) {
        Y4Edge[i] = ArrayMath.softmax(U4Edge[i]);
      }
      Y = new double[U.length][];
      for (int i = 0; i < U.length; i++) {
        Y[i] = ArrayMath.softmax(U[i]);
      }
    }

    double[][] What4Edge = emptyW4Edge();
    double[][] Uhat4Edge = emptyU4Edge();
    double[][] What = emptyW();
    double[][] Uhat = emptyU();

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    double[][] eW4Edge = emptyW4Edge();
    double[][] eU4Edge = emptyU4Edge();
    double[][] eW = emptyW();
    double[][] eU = emptyU();

    // iterate over all the documents
    for (int m = 0; m < data.length; m++) {
      int[][][] docData = data[m];
      int[] docLabels = labels[m];

      NonLinearSecondOrderCliquePotentialFunction cliquePotentialFunction = new NonLinearSecondOrderCliquePotentialFunction(W4Edge, U4Edge, W, U, flags);

      // make a clique tree for this document
      CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex,
        backgroundSymbol, cliquePotentialFunction, null);

      // compute the log probability of the document given the model with the parameters x
      int[] given = new int[window - 1];
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

          int inputSize, outputSize = -1;
          if (j == 0) {
            inputSize = inputLayerSize;
            outputSize = outputLayerSize;
            As = cliquePotentialFunction.hiddenLayerOutput(W, cliqueFeatures, flags, null, j+1);
          } else {
            inputSize = inputLayerSize4Edge;
            outputSize = outputLayerSize4Edge;
            As = cliquePotentialFunction.hiddenLayerOutput(W4Edge, cliqueFeatures, flags, null, j+1);
          }

          fDeriv = new double[inputSize];
          double fD = 0;
          for (int q = 0; q < inputSize; q++) {
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

            yTimesA = new double[outputSize][numHiddenUnits];
            for (int ii = 0; ii < outputSize; ii++) {
              yTimesA[ii] = new double[numHiddenUnits];
            }
            sumOfYTimesA = new double[outputSize];

            for (int k = 0; k < outputSize; k++) {
              double[] Yk = null;
              if (flags.tieOutputLayer) {
                if (j == 0) {
                  Yk = Y[0];
                } else {
                  Yk = Y4Edge[0];
                }
              } else {
                if (j == 0) {
                  Yk = Y[k];
                } else {
                  Yk = Y4Edge[k];
                }
              }
              double sum = 0;
              for (int q = 0; q < inputSize; q++) {
                if (q % outputSize == k) {
                  int hiddenUnitNo = q / outputSize;
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
            if (j == 0) {
              Uk = U[0];
              UhatK = Uhat[0];
            } else {
              Uk = U4Edge[0];
              UhatK = Uhat4Edge[0];
            }
            if (flags.softmaxOutputLayer) {
              if (j == 0) {
                Yk = Y[0];
              } else {
                Yk = Y4Edge[0];
              }
            }
          } else {
            if (j == 0) {
              Uk = U[givenLabelIndex];
              UhatK = Uhat[givenLabelIndex];
            } else {
              Uk = U4Edge[givenLabelIndex];
              UhatK = Uhat4Edge[givenLabelIndex];
            }
            if (flags.softmaxOutputLayer) {
              if (j == 0) {
                Yk = Y[givenLabelIndex];
              } else {
                Yk = Y4Edge[givenLabelIndex];
              }
            }
          }

          if (flags.softmaxOutputLayer) {
            yTimesAK = yTimesA[givenLabelIndex];
            sumOfYTimesAK = sumOfYTimesA[givenLabelIndex];
          }

          for (int k = 0; k < inputSize; k++) {
            double deltaK = 1;
            if (flags.sparseOutputLayer || flags.tieOutputLayer) {
              if (k % outputSize == givenLabelIndex) {
                int hiddenUnitNo = k / outputSize;
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
                if (k % outputSize == givenLabelIndex) {
                  double[] WhatK = null;
                  if (j == 0) {
                    WhatK = What[k];
                  } else {
                    WhatK = What4Edge[k];
                  }
                  for (int n = 0; n < cliqueFeatures.length; n++) {
                    WhatK[cliqueFeatures[n]] += deltaK;
                  }
                }
              } else {
                double[] WhatK = null;
                if (j == 0) {
                  WhatK = What[k];
                } else {
                  WhatK = What4Edge[k];
                }
                for (int n = 0; n < cliqueFeatures.length; n++) {
                  WhatK[cliqueFeatures[n]] += deltaK;
                }
              }
            } else {
              if (k == givenLabelIndex) {
                double[] WhatK = null;
                if (j == 0) {
                  WhatK = What[k];
                } else {
                  WhatK = What4Edge[k];
                }
                for (int n = 0; n < cliqueFeatures.length; n++) {
                  WhatK[cliqueFeatures[n]] += deltaK;
                }
              }
            }
          }
          
          for (int k = 0; k < labelIndex.size(); k++) { // labelIndex.size() == numClasses
            int[] label = labelIndex.get(k).getLabel();
            double p = cliqueTree.prob(i, label); // probability of these labels occurring in this clique with these features
            double[] Uk2 = null;
            double[] eUK = null;
            double[] Yk2 = null;

            if (flags.tieOutputLayer) {
              if (j == 0) { // for node features
                Uk2 = U[0];
                eUK = eU[0];
              } else {
                Uk2 = U4Edge[0];
                eUK = eU4Edge[0];
              }
              if (flags.softmaxOutputLayer) {
                if (j == 0) {
                  Yk2 = Y[0];
                } else {
                  Yk2 = Y4Edge[0];
                }
              }
            } else {
              if (j == 0) {
                Uk2 = U[k];
                eUK = eU[k];
              } else {
                Uk2 = U4Edge[k];
                eUK = eU4Edge[k];
              }
              if (flags.softmaxOutputLayer) {
                if (j == 0) {
                  Yk2 = Y[k];
                } else {
                  Yk2 = Y4Edge[k];
                }
              }
            }
            if (useOutputLayer) {
              for (int q = 0; q < inputSize; q++) {
                double deltaQ = 1;
                if (flags.sparseOutputLayer || flags.tieOutputLayer) {
                  if (q % outputSize == k) {
                    int hiddenUnitNo = q / outputSize;
                    if (flags.softmaxOutputLayer) {
                      eUK[hiddenUnitNo] += (yTimesA[k][hiddenUnitNo] - Yk2[hiddenUnitNo] * sumOfYTimesA[k]) * p;
                      deltaQ = Yk2[hiddenUnitNo];
                    } else {
                      eUK[hiddenUnitNo] += As[q] * p;
                      deltaQ = Uk2[hiddenUnitNo];
                    }
                  }
                } else {
                  eUK[q] += As[q] * p;
                  deltaQ = Uk2[q];
                }
                if (useHiddenLayer)
                  deltaQ *= fDeriv[q];
                if (flags.sparseOutputLayer || flags.tieOutputLayer) {
                  if (q % outputSize == k) {
                    double[] eWq = null;
                    if (j == 0) {
                      eWq = eW[q];
                    } else {
                      eWq = eW4Edge[q];
                    }
                    for (int n = 0; n < cliqueFeatures.length; n++) {
                      eWq[cliqueFeatures[n]] += deltaQ * p;
                    }
                  }
                } else {
                  double[] eWq = null;
                  if (j == 0) {
                    eWq = eW[q];
                  } else {
                    eWq = eW4Edge[q];
                  }
                  for (int n = 0; n < cliqueFeatures.length; n++) {
                    eWq[cliqueFeatures[n]] += deltaQ * p;
                  }
                }
              }
            } else {
              double deltaK = 1;
              if (useHiddenLayer)
                deltaK *= fDeriv[k];
              double[] eWK = null;
              if (j == 0) {
                eWK = eW[k];
              } else {
                eWK = eW4Edge[k];
              }
              for (int n = 0; n < cliqueFeatures.length; n++) {
                eWK[cliqueFeatures[n]] += deltaK * p;
              }
            }
          }
        }
      }
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in CRFNonLinearSecondOrderLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;
    if(VERBOSE){
      System.err.println("value is " + value);
    }

    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < eW4Edge.length; i++) {
      for (int j = 0; j < eW4Edge[i].length; j++) {
        derivative[index++] = (eW4Edge[i][j] - What4Edge[i][j]);
        if (VERBOSE) {
          System.err.println("inputLayerWeights4Edge deriv(" + i + "," + j + ") = " + eW4Edge[i][j] + " - " + What4Edge[i][j] + " = " + derivative[index - 1]);
        }
      }
    }

    for (int i = 0; i < eW.length; i++) {
      for (int j = 0; j < eW[i].length; j++) {
        derivative[index++] = (eW[i][j] - What[i][j]);
        if (VERBOSE) {
          System.err.println("inputLayerWeights deriv(" + i + "," + j + ") = " + eW[i][j] + " - " + What[i][j] + " = " + derivative[index - 1]);
        }
      }
    }

    if (index != beforeOutputWeights)
      throw new RuntimeException("after W derivative, index("+index+") != beforeOutputWeights("+beforeOutputWeights+")");

    if (useOutputLayer) {
      for (int i = 0; i < eU4Edge.length; i++) {
        for (int j = 0; j < eU4Edge[i].length; j++) {
          derivative[index++] = (eU4Edge[i][j] - Uhat4Edge[i][j]);
          if (VERBOSE) {
            System.err.println("outputLayerWeights4Edge deriv(" + i + "," + j + ") = " + eU4Edge[i][j] + " - " + Uhat4Edge[i][j] + " = " + derivative[index - 1]);
          }
        }
      }
      for (int i = 0; i < eU.length; i++) {
        for (int j = 0; j < eU[i].length; j++) {
          derivative[index++] = (eU[i][j] - Uhat[i][j]);
          if (VERBOSE) {
            System.err.println("outputLayerWeights deriv(" + i + "," + j + ") = " + eU[i][j] + " - " + Uhat[i][j] + " = " + derivative[index - 1]);
          }
        }
      }
    }

    if (index != x.length)
      throw new RuntimeException("after W derivative, index("+index+") != x.length("+x.length+")");

    int regSize = x.length;
    if (flags.skipOutputRegularization || flags.softmaxOutputLayer) {
      regSize = beforeOutputWeights;
    }

    // incorporate priors
    if (prior == QUADRATIC_PRIOR) {
      double sigmaSq = sigma * sigma;
      for (int i = 0; i < regSize; i++) {
        double k = 1.0;
        double w = x[i];
        value += k * w * w / 2.0 / sigmaSq;
        derivative[i] += k * w / sigmaSq;
      }
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

}
