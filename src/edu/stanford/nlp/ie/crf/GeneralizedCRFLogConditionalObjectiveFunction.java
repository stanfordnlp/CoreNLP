
package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.AbstractCachingDiffFunction;
import edu.stanford.nlp.util.Index;

import java.util.Arrays;
import java.util.List;


public class GeneralizedCRFLogConditionalObjectiveFunction 
  extends AbstractCachingDiffFunction 
{

  public static final int NO_PRIOR = 0;
  public static final int QUADRATIC_PRIOR = 1;
  
  /* Use a Huber robust regression penalty (L1 except very near 0) not L2 */
  public static final int HUBER_PRIOR = 2;
  public static final int QUARTIC_PRIOR = 3;

  protected int prior;
  protected double[] mean;
  protected double[] sigma;
  protected double[] sigmaPower;
  protected double epsilon;

  List<Index<CRFLabel>> labelIndices;
  Index classIndex;
  Index featureIndex;
  double[][] Ehat; // empirical counts of all the features [feature][class]
  int window;
  int numClasses;
  int[] map;
  int[][][][] data;
  int[][] labels;
  int domainDimension = -1;

  String crfType = "maxent";
  String backgroundSymbol;

  public static boolean VERBOSE = false;

//   GeneralizedCRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, Index featureIndex, int window, Index classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String backgroundSymbol) {
//     this(data, labels, featureIndex, window, classIndex, labelIndices, map, QUADRATIC_PRIOR, backgroundSymbol);
//   }

  GeneralizedCRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, Index featureIndex, int window, Index classIndex, List<Index<CRFLabel>> labelIndices, int[] map, String backgroundSymbol, double[] mean,  double[] sigma) {
    this(data, labels, featureIndex, window, classIndex, labelIndices, map, QUADRATIC_PRIOR, backgroundSymbol, mean, sigma);
   }

//   GeneralizedCRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, Index featureIndex, int window, Index classIndex, List<Index<CRFLabel>> labelIndices, int[] map, int prior, String backgroundSymbol) {
    
//     this(data, labels, featureIndex, window, classIndex, labelIndices, map, prior, backgroundSymbol, 1.0);
//   }

  GeneralizedCRFLogConditionalObjectiveFunction(int[][][][] data, int[][] labels, Index featureIndex, int window, Index classIndex, List<Index<CRFLabel>> labelIndices, int[] map, int prior, String backgroundSymbol, double[] mean, double[] sigma) {
    this.featureIndex = featureIndex;
    this.window = window;
    this.classIndex = classIndex;
    this.numClasses = classIndex.size();
    //DEBUG only::Remove
    //System.out.println("GeneralizedCRFLogConditionalObjectiveFunction constructed: numClasses: " + numClasses);

    this.labelIndices = labelIndices;
    this.map = map;
    this.data = data;
    this.labels = labels;
    this.prior = prior;
    this.backgroundSymbol = backgroundSymbol;
    this.mean = mean;
    this.sigma = sigma;
    
    int domaindim = domainDimension();
    System.err.println("DOMAIN DIM:      " + domaindim);
    System.err.println("FEATUREIND SIZE: " + featureIndex.size());    
    System.err.println("MAP SIZE:        " + map.length);
    System.err.println("SIGMA SIZE:      " + sigma.length);
    
    //set the sigmaPower to sigma^power 
    // sigma^2 for huber and quadratic
    // sigma^4 for quartic
    this.sigmaPower = new double[this.sigma.length];
    for (int i= 0; i<this.sigma.length; i++) {
      if (prior == QUADRATIC_PRIOR || prior == HUBER_PRIOR) {      
        this.sigmaPower[i] = this.sigma[i] * this.sigma[i];      
      }
      else if (prior == QUARTIC_PRIOR) {
        this.sigmaPower[i] = this.sigma[i] * this.sigma[i] * this.sigma[i] * this.sigma[i];      
      }
    }

    empiricalCounts(data, labels);
  }

  @Override
  public int domainDimension() {
    if (domainDimension < 0) {
      domainDimension = 0;
      for (int i = 0; i < map.length; i++) {
        //System.out.println("      " + labelIndices[map[i]]);
        domainDimension += labelIndices.get(map[i]).size();
      }
    }
    return domainDimension;
  }

  /**
   * Takes a double array of weights which and creates a 2D array where:
   * 
   * the first element is the mapped index of featuresIndex
   * the second element is the index of the of the element
   *
   * @return a 2D weight array
   */
  public double[][] to2D(double[] weights) {
    double[][] newWeights = new double[map.length][];
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      newWeights[i] = new double[labelIndices.get(map[i]).size()];
      System.arraycopy(weights, index, newWeights[i], 0, labelIndices.get(map[i]).size());
      index += labelIndices.get(map[i]).size();
    }
    return newWeights;
  }

  public double[] to1D(double[][] weights) {
    double[] newWeights = new double[domainDimension()];
    int index = 0;
    for (int i = 0; i < weights.length; i++) {
      System.arraycopy(weights[i], 0, newWeights, index, weights[i].length);
      index += weights[i].length;
    }
    return newWeights;
  }

  public double[][] empty2D() {
    double[][] d = new double[map.length][];
    int index = 0;
    for (int i = 0; i < map.length; i++) {
      d[i] = new double[labelIndices.get(map[i]).size()];
      Arrays.fill(d[i], 0);
      index += labelIndices.get(map[i]).size();
    }
    return d;
  }

  private void empiricalCounts(int[][][][] data, int[][] labels) {
    Ehat = empty2D();


    for (int m = 0; m < data.length; m++) {
      int[][][] docData = data[m];
      int[] docLabels = labels[m];
      int[] windowLabels = new int[window];
      Arrays.fill(windowLabels, classIndex.indexOf(backgroundSymbol));
      if (docLabels.length>docData.length) { // only true for self-training
          
          //DEBUG: Remove
          //System.out.println("##EmpiricalCount: NumLabels: " + docLabels.length + " NumPositions in doc: " + docData.length);

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


        for (int j = 0; j < docData[i].length; j++) {
          int[] cliqueLabel = new int[j + 1];
          System.arraycopy(windowLabels, window - 1 - j, cliqueLabel, 0, j + 1);
          CRFLabel crfLabel = new CRFLabel(cliqueLabel);
          int labelIndex = labelIndices.get(j).indexOf(crfLabel);
          //System.err.println(crfLabel + " " + labelIndex);

          for (int k = 0; k < docData[i][j].length; k++) {
              //try{

            Ehat[docData[i][j][k]][labelIndex]++;

             //  } catch (Exception e) {
//                e.printStackTrace();
//                System.out.println("docData[i: " + i + " j: " + j + " k: " + k+ " ]");
//                //DEBUG: Remove
//                System.out.println("##EmpiricalCount: NumLabels: " + docLabels.length + " NumPositions in doc: " + docData.length);

//                //DEBUG: Remove
//                System.out.println("##EmpiricalCount: NumDocs:    " + data.length);

//                //DEBUG: Remove
//                System.out.println("##EmpiricalCount: NumPositions in doc:" + docData.length);
//                //DEBUG: Remove
//                System.out.println("##EmpiricalCount: NumCliqueSizes: " + docData[i].length);

//                //DEBUG: Remove
//                System.out.println("##EmpiricalCount: NumFeatures: " + docData[i][j].length);

//                System.out.println("##EmpiricalCount: Index of feature: " + docData[i][j][k]);

//                System.out.println("##EmpiricalCount: LabelIndex: " + labelIndex);
//                System.out.println("##EmpiricalCount: Ehat dimensions: " + Ehat.length + " NumLabels for current feature in Ehat: " + Ehat[docData[i][j][k]].length);
//                System.exit(1);
//            }

      
          }
        }
      }
    }
  }

  /**
   * Calculates both value and partial derivatives at the point x, and save them internally.
   */
  @Override
  public void calculate(double[] x) {

    double prob = 0; // the log prob of the sequence given the model, which is the negation of value at this point
    double[][] weights = to2D(x);

    // the expectations over counts
    // first index is feature index, second index is of possible labeling
    double[][] E = empty2D();

    // iterate over all the documents
    for (int m = 0; m < data.length; m++) {
      int[][][] docData = data[m];
      int[] docLabels = labels[m];

      CliquePotentialFunction cliquePotentialFunc = new LinearCliquePotentialFunction(weights);
      // make a clique tree for this document
      CRFCliqueTree cliqueTree = CRFCliqueTree.getCalibratedCliqueTree(docData, labelIndices, numClasses, classIndex, backgroundSymbol, cliquePotentialFunc, null);

      // compute the log probability of the document given the model with the parameters x
      int[] given = new int[window - 1];
      Arrays.fill(given, classIndex.indexOf(backgroundSymbol));
      if (docLabels.length>docData.length) { // only true for self-training
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
      for (int i = 0; i < data[m].length; i++) {
        // for each possible clique at this position
        for (int j = 0; j < data[m][i].length; j++) {
          Index labelIndex = labelIndices.get(j);
          // for each possible labeling for that clique
          for (int k = 0; k < labelIndex.size(); k++) {
            int[] label = ((CRFLabel) labelIndex.get(k)).getLabel();
            double p = cliqueTree.prob(i, label); // probability of these labels occurring in this clique with these features
            for (int n = 0; n < data[m][i][j].length; n++) {
              E[data[m][i][j][n]][k] += p;
            }
          }
        }
      }
    }

    if (Double.isNaN(prob)) { // shouldn't be the case
      throw new RuntimeException("Got NaN for prob in GeneralizedCRFLogConditionalObjectiveFunction.calculate()");
    }

    value = -prob;

    // compute the partial derivative for each feature by comparing expected counts to empirical counts
    int index = 0;
    for (int i = 0; i < E.length; i++) {
      for (int j = 0; j < E[i].length; j++) {
        derivative[index++] = (E[i][j] - Ehat[i][j]);
        if (VERBOSE) {
          System.err.println("deriv(" + i + "," + j + ") = " + E[i][j] + " - " + Ehat[i][j] + " = " + derivative[index - 1]);
        }
      }
    }

    // incorporate priors
    if (prior == QUADRATIC_PRIOR) {
      for (int i = 0; i < x.length; i++) {
        double k = 1.0;
        double w = x[i] - mean[i];
        value += k * w * w / 2.0 / sigmaPower[i];
        derivative[i] += k * w / sigmaPower[i];        
      }
      
    } else if (prior == HUBER_PRIOR) {
      double sigmaSq = sigma[0] * sigma[0];
      for (int i = 0; i < x.length; i++) {
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
      double sigmaQu = sigma[0] * sigma[0] * sigma[0] * sigma[0];
      for (int i = 0; i < x.length; i++) {
        double k = 1.0;
        double w = x[i];
        value += k * w * w * w * w / 2.0 / sigmaQu;
        derivative[i] += k * w / sigmaQu;
      }
    }
  }
}
