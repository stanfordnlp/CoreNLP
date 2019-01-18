package edu.stanford.nlp.ie.crf;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFloatFunction;
import edu.stanford.nlp.util.Index;

import java.util.Arrays;
import java.util.List;


/**
 * @author Jenny Finkel
 */

public class CRFLogConditionalObjectiveFloatFunction extends AbstractCachingDiffFloatFunction implements HasCliquePotentialFunction  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CRFLogConditionalObjectiveFloatFunction.class);

  public static final int NO_PRIOR = 0;
  public static final int QUADRATIC_PRIOR = 1;
  /* Use a Huber robust regression penalty (L1 except very near 0) not L2 */
  public static final int HUBER_PRIOR = 2;
  public static final int QUARTIC_PRIOR = 3;

  protected int prior;
  protected float sigma;
  protected float epsilon;

  List<Index<CRFLabel>> labelIndices;
  Index<String> classIndex;
  float[][] Ehat; // empirical counts of all the features [feature][class]
  int window;
  int numClasses;
  int[] map;
  int[][][][] data;
  int[][] labels;
  int domainDimension = -1;

  String backgroundSymbol;

  public static boolean VERBOSE = false;

  CRFLogConditionalObjectiveFloatFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String backgroundSymbol) {
    this(data, labels, window, classIndex, labelIndices, map, QUADRATIC_PRIOR, backgroundSymbol);
  }

  CRFLogConditionalObjectiveFloatFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String backgroundSymbol, double sigma) {
    this(data, labels, window, classIndex, labelIndices, map, QUADRATIC_PRIOR, backgroundSymbol, sigma);
  }

  CRFLogConditionalObjectiveFloatFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, int prior, String backgroundSymbol) {
    this(data, labels, window, classIndex, labelIndices, map, prior, backgroundSymbol, 1.0f);
  }

  CRFLogConditionalObjectiveFloatFunction(int[][][][] data, int[][] labels, int window, Index<String> classIndex, List<Index<CRFLabel>> labelIndices, int[] map, int prior, String backgroundSymbol, double sigma) {
    this.window = window;
    this.classIndex = classIndex;
    this.numClasses = classIndex.size();
    this.labelIndices = labelIndices;
    this.map = map;
    this.data = data;
    this.labels = labels;
    this.prior = prior;
    this.backgroundSymbol = backgroundSymbol;
    this.sigma = (float) sigma;
    empiricalCounts(data, labels);
  }

  @Override
  public int domainDimension() {
    if (domainDimension < 0) {
      domainDimension = 0;
      for (int aMap : map) {
        domainDimension += labelIndices.get(aMap).size();
      }
    }
    return domainDimension;
  }

  @Override
  public CliquePotentialFunction getCliquePotentialFunction(double[] x) {
    throw new UnsupportedOperationException("CRFLogConditionalObjectiveFloatFunction is not clique potential compatible yet");
  }

  public float[][] to2D(float[] weights) {
    float[][] newWeights = new float[map.length][];
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      newWeights[i] = new float[labelIndices.get(map[i]).size()];
      System.arraycopy(weights, index, newWeights[i], 0, labelIndices.get(map[i]).size());
      index += labelIndices.get(map[i]).size();
    }
    return newWeights;
  }

  public float[] to1D(float[][] weights) {
    float[] newWeights = new float[domainDimension()];
    int index = 0;
    for (float[] weight : weights) {
      System.arraycopy(weight, 0, newWeights, index, weight.length);
      index += weight.length;
    }
    return newWeights;
  }

  public float[][] empty2D() {
    float[][] d = new float[map.length][];
    // int index = 0;
    for (int i = 0; i < map.length; i++) {
      d[i] = new float[labelIndices.get(map[i]).size()];
      // Arrays.fill(d[i], 0);  // not needed; Java arrays zero initialized
      // index += labelIndices.get(map[i]).size();
    }
    return d;
  }

  private void empiricalCounts(int[][][][] data, int[][] labels) {
    Ehat = empty2D();

    for (int m = 0; m < data.length; m++) {
      int[][][] dataDoc = data[m];
      int[] labelsDoc = labels[m];
      int[] label = new int[window];
      //Arrays.fill(label, classIndex.indexOf("O"));
      Arrays.fill(label, classIndex.indexOf(backgroundSymbol));
      for (int i = 0; i < dataDoc.length; i++) {
        System.arraycopy(label, 1, label, 0, window - 1);
        label[window - 1] = labelsDoc[i];
        for (int j = 0; j < dataDoc[i].length; j++) {
          int[] cliqueLabel = new int[j + 1];
          System.arraycopy(label, window - 1 - j, cliqueLabel, 0, j + 1);
          CRFLabel crfLabel = new CRFLabel(cliqueLabel);
          int labelIndex = labelIndices.get(j).indexOf(crfLabel);
          //log.info(crfLabel + " " + labelIndex);
          for (int k = 0; k < dataDoc[i][j].length; k++) {
            Ehat[dataDoc[i][j][k]][labelIndex]++;
          }
        }
      }
    }
  }

  public static FloatFactorTable getFloatFactorTable(float[][] weights, int[][] data, List<Index<CRFLabel>> labelIndices, int numClasses) {

    FloatFactorTable factorTable = null;

    for (int j = 0; j < labelIndices.size(); j++) {
      Index<CRFLabel> labelIndex = labelIndices.get(j);
      FloatFactorTable ft = new FloatFactorTable(numClasses, j + 1);

      // ...and each possible labeling for that clique
      for (int k = 0; k < labelIndex.size(); k++) {
        int[] label = labelIndex.get(k).getLabel();
        float weight = 0.0f;
        for (int m = 0; m < data[j].length; m++) {
          //log.info("**"+weights[data[j][m]][k]);
          weight += weights[data[j][m]][k];
        }
        ft.setValue(label, weight);
        //log.info(">>"+ft);
      }
      //log.info("::"+ft);
      if (j > 0) {
        ft.multiplyInEnd(factorTable);
      }
      //log.info("::"+ft);
      factorTable = ft;

    }

    return factorTable;
  }



  public static FloatFactorTable[] getCalibratedCliqueTree(float[][] weights, int[][][] data, List<Index<CRFLabel>> labelIndices, int numClasses) {

    //       for (int i = 0; i < weights.length; i++) {
    //         for (int j = 0; j < weights[i].length; j++) {
    //           log.info(i+" "+j+": "+weights[i][j]);
    //         }
    //       }

    //log.info("calibrating clique tree");

    FloatFactorTable[] factorTables = new FloatFactorTable[data.length];
    FloatFactorTable[] messages = new FloatFactorTable[data.length - 1];

    for (int i = 0; i < data.length; i++) {

      factorTables[i] = getFloatFactorTable(weights, data[i], labelIndices, numClasses);
      if (VERBOSE) {
        log.info(i + ": " + factorTables[i]);
      }

      if (i > 0) {
        messages[i - 1] = factorTables[i - 1].sumOutFront();
        if (VERBOSE) {
          log.info(messages[i - 1]);
        }
        factorTables[i].multiplyInFront(messages[i - 1]);
        if (VERBOSE) {
          log.info(factorTables[i]);
          if (i == data.length - 1) {
            log.info(i + ": " + factorTables[i].toProbString());
          }
        }
      }
    }

    for (int i = factorTables.length - 2; i >= 0; i--) {

      FloatFactorTable summedOut = factorTables[i + 1].sumOutEnd();
      if (VERBOSE) {
        log.info((i + 1) + "-->" + i + ": " + summedOut);
      }
      summedOut.divideBy(messages[i]);
      if (VERBOSE) {
        log.info((i + 1) + "-->" + i + ": " + summedOut);
      }
      factorTables[i].multiplyInEnd(summedOut);
      if (VERBOSE) {
        log.info(i + ": " + factorTables[i]);
        log.info(i + ": " + factorTables[i].toProbString());
      }

    }

    return factorTables;
  }

  @Override
  public void calculate(float[] x) {

    // if (crfType.equalsIgnoreCase("weird")) {
    //   calculateWeird(x);
    //   return;
    // }

    float[][] weights = to2D(x);
    float prob = 0;

    float[][] E = empty2D();

    for (int m = 0; m < data.length; m++) {

      FloatFactorTable[] factorTables = getCalibratedCliqueTree(weights, data[m], labelIndices, numClasses);
      //             log.info("calibrated:");
      //             for (int i = 0; i < factorTables.length; i++) {
      //               System.out.println(factorTables[i]);
      //               System.out.println("+++++++++++++++++++++++++++++");

      //             }
      //             System.exit(0);
      float z = factorTables[0].totalMass();

      int[] given = new int[window - 1];
      Arrays.fill(given, classIndex.indexOf(backgroundSymbol));
      for (int i = 0; i < data[m].length; i++) {
        float p = factorTables[i].conditionalLogProb(given, labels[m][i]);
        if (VERBOSE) {
          log.info("P(" + labels[m][i] + "|" + Arrays.toString(given) + ")=" + p);
        }
        prob += p;
        System.arraycopy(given, 1, given, 0, given.length - 1);
        given[given.length - 1] = labels[m][i];
      }

      // get predicted count
      for (int i = 0; i < data[m].length; i++) {
        // go through each clique...
        for (int j = 0; j < data[m][i].length; j++) {
          Index<CRFLabel> labelIndex = labelIndices.get(j);
          // ...and each possible labeling for that clique
          for (int k = 0; k < labelIndex.size(); k++) {
            int[] label = labelIndex.get(k).getLabel();

            // float p = Math.pow(Math.E, factorTables[i].logProbEnd(label));
            float p = (float) Math.exp(factorTables[i].unnormalizedLogProbEnd(label) - z);
            for (int n = 0; n < data[m][i][j].length; n++) {
              E[data[m][i][j][n]][k] += p;
            }
          }
        }
      }
    }

    if (Float.isNaN(prob)) {
      System.exit(0);
    }
    value = -prob;

    // compute the partial derivative for each feature
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        derivative[index++] = (E[i][j] - Ehat[i][j]);
        if (VERBOSE) {
          log.info("deriv(" + i + "," + j + ") = " + E[i][j] + " - " + Ehat[i][j] + " = " + derivative[index - 1]);
        }
      }
    }


    // priors
    if (prior == QUADRATIC_PRIOR) {
      float sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float k = 1.0f;
        float w = x[i];
        value += k * w * w / 2.0 / sigmaSq;
        derivative[i] += k * w / sigmaSq;
      }
    } else if (prior == HUBER_PRIOR) {
      float sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float w = x[i];
        float wabs = Math.abs(w);
        if (wabs < epsilon) {
          value += w * w / 2.0 / epsilon / sigmaSq;
          derivative[i] += w / epsilon / sigmaSq;
        } else {
          value += (wabs - epsilon / 2) / sigmaSq;
          derivative[i] += ((w < 0.0) ? -1.0 : 1.0) / sigmaSq;
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      float sigmaQu = sigma * sigma * sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float k = 1.0f;
        float w = x[i];
        value += k * w * w * w * w / 2.0 / sigmaQu;
        derivative[i] += k * w / sigmaQu;
      }
    }

  }

  public void calculateWeird1(float[] x) {

    float[][] weights = to2D(x);
    float[][] E = empty2D();

    value = 0.0f;
    Arrays.fill(derivative, 0.0f);
    float[][] sums = new float[labelIndices.size()][];
    float[][] probs = new float[labelIndices.size()][];
    float[][] counts = new float[labelIndices.size()][];

    for (int i = 0; i < sums.length; i++) {
      int size = labelIndices.get(i).size();
      sums[i] = new float[size];
      probs[i] = new float[size];
      counts[i] = new float[size];
      // Arrays.fill(counts[i], 0.0f); // not needed; Java arrays zero initialized
    }

    for (int d = 0; d < data.length; d++) {
      int[] llabels = labels[d];
      for (int e = 0; e < data[d].length; e++) {
        int[][] ddata = this.data[d][e];

        for (int cl = 0; cl < ddata.length; cl++) {
          int[] features = ddata[cl];
          // activation
          Arrays.fill(sums[cl], 0.0f);
          int numClasses = labelIndices.get(cl).size();
          for (int c = 0; c < numClasses; c++) {
            for (int feature : features) {
              sums[cl][c] += weights[feature][c];
            }
          }
        }


        for (int cl = 0; cl < ddata.length; cl++) {

          int[] label = new int[cl + 1];
          //Arrays.fill(label, classIndex.indexOf("O"));
          Arrays.fill(label, classIndex.indexOf(backgroundSymbol));
          int index1 = label.length - 1;
          for (int pos = e; pos >= 0 && index1 >= 0; pos--) {
            //log.info(index1+" "+pos);
            label[index1--] = llabels[pos];
          }
          CRFLabel crfLabel = new CRFLabel(label);
          int labelIndex = labelIndices.get(cl).indexOf(crfLabel);

          float total = ArrayMath.logSum(sums[cl]);
          // 		    int[] features = ddata[cl];
          int numClasses = labelIndices.get(cl).size();
          for (int c = 0; c < numClasses; c++) {
            probs[cl][c] = (float) Math.exp(sums[cl][c] - total);
          }
          // 		    for (int f=0; f<features.length; f++) {
          // 			for (int c=0; c<numClasses; c++) {
          // 			    //probs[cl][c] = Math.exp(sums[cl][c]-total);
          // 			    derivative[index] += probs[cl][c];
          // 			    if (c == labelIndex) {
          // 				derivative[index]--;
          // 			    }
          // 			    index++;
          // 			}
          // 		    }


          value -= sums[cl][labelIndex] - total;

          // 		    // observed
          // 		    for (int f=0; f<features.length; f++) {
          // 		        //int i = indexOf(features[f], labels[d]);
          // 		        derivative[index+labelIndex] -= 1.0;
          // 		    }

        }

        // go through each clique...
        for (int j = 0; j < data[d][e].length; j++) {
          Index<CRFLabel> labelIndex = labelIndices.get(j);

          // ...and each possible labeling for that clique
          for (int k = 0; k < labelIndex.size(); k++) {
            //int[] label = ((CRFLabel) labelIndex.get(k)).getLabel();

            // float p = Math.pow(Math.E, factorTables[i].logProbEnd(label));
            float p = probs[j][k];
            for (int n = 0; n < data[d][e][j].length; n++) {
              E[data[d][e][j][n]][k] += p;
            }
          }
        }
      }

    }


    // compute the partial derivative for each feature
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        derivative[index++] = (E[i][j] - Ehat[i][j]);
      }
    }

    // observed
    // 	int index = 0;
    // 	for (int i = 0; i < Ehat.length; i++) {
    // 	    for (int j = 0; j < Ehat[i].length; j++) {
    // 		derivative[index++] -= Ehat[i][j];
    // 	    }
    // 	}

    // priors
    if (prior == QUADRATIC_PRIOR) {
      float sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float k = 1.0f;
        float w = x[i];
        value += k * w * w / 2.0 / sigmaSq;
        derivative[i] += k * w / sigmaSq;
      }
    } else if (prior == HUBER_PRIOR) {
      float sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float w = x[i];
        float wabs = Math.abs(w);
        if (wabs < epsilon) {
          value += w * w / 2.0 / epsilon / sigmaSq;
          derivative[i] += w / epsilon / sigmaSq;
        } else {
          value += (wabs - epsilon / 2) / sigmaSq;
          derivative[i] += ((w < 0.0) ? -1.0 : 1.0) / sigmaSq;
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      float sigmaQu = sigma * sigma * sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float k = 1.0f;
        float w = x[i];
        value += k * w * w * w * w / 2.0 / sigmaQu;
        derivative[i] += k * w / sigmaQu;
      }
    }
  }

  /*
  // TODO(mengqiu) verify this is useless and remove
  public void calculateWeird(float[] x) {

    float[][] weights = to2D(x);
    float[][] E = empty2D();

    value = 0.0f;
    Arrays.fill(derivative, 0.0f);

    int size = labelIndices.get(labelIndices.size() - 1).size();

    float[] sums = new float[size];
    float[] probs = new float[size];

    Index labelIndex = labelIndices.get(labelIndices.size() - 1);

    for (int d = 0; d < data.length; d++) {
      int[] llabels = labels[d];

      int[] label = new int[window];
      //Arrays.fill(label, classIndex.indexOf("O"));
      Arrays.fill(label, classIndex.indexOf(backgroundSymbol));

      for (int e = 0; e < data[d].length; e++) {

        Arrays.fill(sums, 0.0f);

        System.arraycopy(label, 1, label, 0, window - 1);
        label[window - 1] = llabels[e];
        CRFLabel crfLabel = new CRFLabel(label);
        int maxCliqueLabelIndex = labelIndex.indexOf(crfLabel);

        int[][] ddata = this.data[d][e];

        //Iterator labelIter = labelIndices.get(labelIndices.size()-1).iterator();
        //while (labelIter.hasNext()) {

        for (int i = 0; i < labelIndex.size(); i++) {
          CRFLabel c = (CRFLabel) labelIndex.get(i);

          for (int cl = 0; cl < ddata.length; cl++) {

            CRFLabel cliqueLabel = c.getSmallerLabel(cl + 1);
            int clIndex = labelIndices.get(cl).indexOf(cliqueLabel);

            int[] features = ddata[cl];
            for (int f = 0; f < features.length; f++) {
              sums[i] += weights[features[f]][clIndex];
            }
          }
        }

        float total = ArrayMath.logSum(sums);
        for (int i = 0; i < probs.length; i++) {
          probs[i] = (float) Math.exp(sums[i] - total);
        }
        value -= sums[maxCliqueLabelIndex] - total;

        for (int i = 0; i < labelIndex.size(); i++) {
          CRFLabel c = (CRFLabel) labelIndex.get(i);

          for (int cl = 0; cl < ddata.length; cl++) {

            CRFLabel cliqueLabel = c.getSmallerLabel(cl + 1);
            int clIndex = labelIndices.get(cl).indexOf(cliqueLabel);
            int[] features = ddata[cl];

            for (int f = 0; f < features.length; f++) {
              E[features[f]][clIndex] += probs[i];
              if (i == maxCliqueLabelIndex) {
                E[features[f]][clIndex] -= 1.0f;
              }
              //sums[i] += weights[features[f]][cl];
            }
          }
        }
      }
    }


    // compute the partial derivative for each feature
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        //derivative[index++] = (E[i][j] - Ehat[i][j]);
        derivative[index++] = E[i][j];
      }
    }

    // priors
    if (prior == QUADRATIC_PRIOR) {
      float sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float k = 1.0f;
        float w = x[i];
        value += k * w * w / 2.0 / sigmaSq;
        derivative[i] += k * w / sigmaSq;
      }
    } else if (prior == HUBER_PRIOR) {
      float sigmaSq = sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float w = x[i];
        float wabs = Math.abs(w);
        if (wabs < epsilon) {
          value += w * w / 2.0 / epsilon / sigmaSq;
          derivative[i] += w / epsilon / sigmaSq;
        } else {
          value += (wabs - epsilon / 2) / sigmaSq;
          derivative[i] += ((w < 0.0f ? -1.0f : 1.0f) / sigmaSq);
        }
      }
    } else if (prior == QUARTIC_PRIOR) {
      float sigmaQu = sigma * sigma * sigma * sigma;
      for (int i = 0; i < x.length; i++) {
        float k = 1.0f;
        float w = x[i];
        value += k * w * w * w * w / 2.0 / sigmaQu;
        derivative[i] += k * w / sigmaQu;
      }
    }
  }
  */
}
