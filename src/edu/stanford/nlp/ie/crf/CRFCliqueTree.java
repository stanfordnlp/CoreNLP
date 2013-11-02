package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.sequences.SequenceListener;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.GeneralizedCounter;
import edu.stanford.nlp.util.Index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds a CliqueTree (an array of FactorTable) and does message passing
 * inference along it.
 *
 * @param <E> The type of the label (usually String in our uses)
 * @author Jenny Finkel
 */
public class CRFCliqueTree<E> implements SequenceModel, SequenceListener {

  protected final FactorTable[] factorTables;
  protected final double z; // norm constant
  protected final Index<E> classIndex;
  private final E backgroundSymbol;
  private final int backgroundIndex;
  // the window size, which is also the clique size
  protected final int windowSize;
  // the number of possible classes for each label
  private final int numClasses;
  private final int[] possibleValues;

  /** Initialize a clique tree */
  public CRFCliqueTree(FactorTable[] factorTables, Index<E> classIndex, E backgroundSymbol) {
    this(factorTables, classIndex, backgroundSymbol, factorTables[0].totalMass());
  }

  /** This extra constructor was added to support the CRFCliqueTreeForPartialLabels */
  CRFCliqueTree(FactorTable[] factorTables, Index<E> classIndex, E backgroundSymbol, double z) {
    this.factorTables = factorTables;
    this.z = z;
    this.classIndex = classIndex;
    this.backgroundSymbol = backgroundSymbol;
    backgroundIndex = classIndex.indexOf(backgroundSymbol);
    windowSize = factorTables[0].windowSize();
    numClasses = classIndex.size();
    possibleValues = new int[numClasses];
    for (int i = 0; i < numClasses; i++) {
      possibleValues[i] = i;
    }

    // Debug only
    // System.out.println("CRFCliqueTree constructed::numClasses: " +
    // numClasses);
  }

  public FactorTable[] getFactorTables() {
    return this.factorTables;
  }

  public Index<E> classIndex() {
    return classIndex;
  }

  // SEQUENCE MODEL METHODS

  @Override
  public int length() {
    return factorTables.length;
  }

  @Override
  public int leftWindow() {
    return windowSize;
  }

  @Override
  public int rightWindow() {
    return 0;
  }

  @Override
  public int[] getPossibleValues(int position) {
    return possibleValues;
  }

  @Override
  public double scoreOf(int[] sequence, int pos) {
    return scoresOf(sequence, pos)[sequence[pos]];
  }

  /**
   * Computes the unnormalized log conditional distribution over values of the
   * element at position pos in the sequence, conditioned on the values of the
   * elements in all other positions of the provided sequence.
   *
   * @param sequence
   *          the sequence containing the rest of the values to condition on
   * @param position
   *          the position of the element to give a distribution for
   * @return an array of type double, representing a probability distribution;
   *         sums to 1.0
   */
  @Override
  public double[] scoresOf(int[] sequence, int position) {
    if (position >= factorTables.length) throw new RuntimeException("Index out of bounds: " + position);
    // DecimalFormat nf = new DecimalFormat("#0.000");
    // if (position>0 && position<sequence.length-1) System.out.println(position
    // + ": asking about " +sequence[position-1] + "(" + sequence[position] +
    // ")" + sequence[position+1]);
    double[] probThisGivenPrev = new double[numClasses];
    double[] probNextGivenThis = new double[numClasses];
    // double[] marginal = new double[numClasses]; // for debugging only

    // compute prob of this tag given the window-1 previous tags, normalized
    // extract the window-1 previous tags, pad left with background if necessary
    int prevLength = windowSize - 1;
    int[] prev = new int[prevLength + 1]; // leave an extra element for the
    // label at this position
    int i = 0;
    for (; i < prevLength - position; i++) { // will only happen if
      // position-prevLength < 0
      prev[i] = classIndex.indexOf(backgroundSymbol);
    }
    for (; i < prevLength; i++) {
      prev[i] = sequence[position - prevLength + i];
    }
    for (int label = 0; label < numClasses; label++) {
      prev[prev.length - 1] = label;
      probThisGivenPrev[label] = factorTables[position].unnormalizedLogProb(prev);
      // marginal[label] = factorTables[position].logProbEnd(label); // remove:
      // for debugging only
    }

    // ArrayMath.logNormalize(probThisGivenPrev);

    // compute the prob of the window-1 next tags given this tag
    // extract the window-1 next tags
    int nextLength = windowSize - 1;
    if (position + nextLength >= length()) {
      nextLength = length() - position - 1;
    }
    FactorTable nextFactorTable = factorTables[position + nextLength];
    if (nextLength != windowSize - 1) {
      for (int j = 0; j < windowSize - 1 - nextLength; j++) {
        nextFactorTable = nextFactorTable.sumOutFront();
      }
    }
    if (nextLength == 0) { // we are asking about the prob of no sequence
      Arrays.fill(probNextGivenThis, 1.0);
    } else {
      int[] next = new int[nextLength];
      System.arraycopy(sequence, position + 1, next, 0, nextLength);
      for (int label = 0; label < numClasses; label++) {
        // ask the factor table such that pos is the first position in the
        // window
        // probNextGivenThis[label] =
        // factorTables[position+nextLength].conditionalLogProbGivenFirst(label,
        // next);
        // probNextGivenThis[label] =
        // nextFactorTable.conditionalLogProbGivenFirst(label, next);
        probNextGivenThis[label] = nextFactorTable.unnormalizedConditionalLogProbGivenFirst(label, next);
      }
    }

    // pointwise multiply
    return ArrayMath.pairwiseAdd(probThisGivenPrev, probNextGivenThis);
  }

  /**
   * Returns the log probability of this sequence given the CRF. Does so by
   * computing the marginal of the first windowSize tags, and then computing the
   * conditional probability for the rest of them, conditioned on the previous
   * tags.
   *
   * @param sequence The sequence to compute a score for
   * @return the score for the sequence
   */
  @Override
  public double scoreOf(int[] sequence) {

    int[] given = new int[window() - 1];
    Arrays.fill(given, classIndex.indexOf(backgroundSymbol));
    double logProb = 0.0;
    for (int i = 0, length = length(); i < length; i++) {
      int label = sequence[i];
      logProb += condLogProbGivenPrevious(i, label, given);
      System.arraycopy(given, 1, given, 0, given.length - 1);
      given[given.length - 1] = label;
    }
    return logProb;
  }

  // OTHER

  public int window() {
    return windowSize;
  }

  public int getNumClasses() {
    return numClasses;
  }

  public double totalMass() {
    return z;
  }

  public int backgroundIndex() {
    return backgroundIndex;
  }

  public E backgroundSymbol() {
    return backgroundSymbol;
  }

  //
  // MARGINAL PROB OF TAG AT SINGLE POSITION
  //

  public double[][] logProbTable() {
    double[][] result = new double[length()][classIndex.size()];
    for (int i = 0; i < length(); i++) {
      result[i] = new double[classIndex.size()];
      for (int j = 0; j < classIndex.size(); j++) {
        result[i][j] = logProb(i, j);
      }
    }

    return result;
  }

  /*
  * TODO(mengqiu) this function is buggy, should make sure label converts properly into int[] in cases where it's not 0-order label
  */
  public double logProbStartPos() {
    double u = factorTables[0].unnormalizedLogProbFront(backgroundIndex);
    return u - z;
  }

  public double logProb(int position, int label) {
    double u = factorTables[position].unnormalizedLogProbEnd(label);
    return u - z;
  }

  public double prob(int position, int label) {
    return Math.exp(logProb(position, label));
  }

  public double logProb(int position, E label) {
    return logProb(position, classIndex.indexOf(label));
  }

  public double prob(int position, E label) {
    return Math.exp(logProb(position, label));
  }

  public double[] probsToDoubleArr(int position) {
    double[] probs = new double[classIndex.size()];
    for (int i = 0, sz = classIndex.size(); i < sz; i++) {
      probs[i] = prob(position, i);
    }
    return probs;
  }

  public double[] logProbsToDoubleArr(int position) {
    double[] probs = new double[classIndex.size()];
    for (int i = 0, sz = classIndex.size(); i < sz; i++) {
      probs[i] = logProb(position, i);
    }
    return probs;
  }

  public Counter<E> probs(int position) {
    Counter<E> c = new ClassicCounter<E>();
    for (int i = 0, sz = classIndex.size(); i < sz; i++) {
      E label = classIndex.get(i);
      c.incrementCount(label, prob(position, i));
    }
    return c;
  }

  public Counter<E> logProbs(int position) {
    Counter<E> c = new ClassicCounter<E>();
    for (int i = 0, sz = classIndex.size(); i < sz; i++) {
      E label = classIndex.get(i);
      c.incrementCount(label, logProb(position, i));
    }
    return c;
  }

  //
  // MARGINAL PROBS OF TAGS AT MULTIPLE POSITIONS
  //

  /**
   * returns the log probability for the given labels (indexed using
   * classIndex), where the last label corresponds to the label at the specified
   * position. For instance if you called logProb(5, {1,2,3}) it will return the
   * marginal log prob that the label at position 3 is 1, the label at position
   * 4 is 2 and the label at position 5 is 3.
   */
  public double logProb(int position, int[] labels) {
    if (labels.length < windowSize) {
      return factorTables[position].unnormalizedLogProbEnd(labels) - z;
    } else if (labels.length == windowSize) {
      return factorTables[position].unnormalizedLogProb(labels) - z;
    } else {
      int[] l = new int[windowSize];
      System.arraycopy(labels, 0, l, 0, l.length);
      int position1 = position - labels.length + windowSize;
      double p = factorTables[position1].unnormalizedLogProb(l) - z;
      l = new int[windowSize - 1];
      System.arraycopy(labels, 1, l, 0, l.length);
      position1++;
      for (int i = windowSize; i < labels.length; i++) {
        p += condLogProbGivenPrevious(position1++, labels[i], l);
        System.arraycopy(l, 1, l, 0, l.length - 1);
        l[windowSize - 2] = labels[i];
      }
      return p;
    }
  }

  /**
   * Returns the probability for the given labels (indexed using classIndex),
   * where the last label corresponds to the label at the specified position.
   * For instance if you called prob(5, {1,2,3}) it will return the marginal
   * prob that the label at position 3 is 1, the label at position 4 is 2 and
   * the label at position 5 is 3.
   */
  public double prob(int position, int[] labels) {
    return Math.exp(logProb(position, labels));
  }

  /**
   * returns the log probability for the given labels, where the last label
   * corresponds to the label at the specified position. For instance if you
   * called logProb(5, {"O", "PER", "ORG"}) it will return the marginal log prob
   * that the label at position 3 is "O", the label at position 4 is "PER" and
   * the label at position 5 is "ORG".
   */
  public double logProb(int position, E[] labels) {
    return logProb(position, objectArrayToIntArray(labels));
  }

  /**
   * returns the probability for the given labels, where the last label
   * corresponds to the label at the specified position. For instance if you
   * called logProb(5, {"O", "PER", "ORG"}) it will return the marginal prob
   * that the label at position 3 is "O", the label at position 4 is "PER" and
   * the label at position 5 is "ORG".
   */
  public double prob(int position, E[] labels) {
    return Math.exp(logProb(position, labels));
  }

  public GeneralizedCounter logProbs(int position, int window) {
    GeneralizedCounter<E> gc = new GeneralizedCounter<E>(window);
    int[] labels = new int[window];
    // cdm july 2005: below array initialization isn't necessary: JLS (3rd ed.)
    // 4.12.5
    // Arrays.fill(labels, 0);

    OUTER: while (true) {
      List<E> labelsList = intArrayToListE(labels);
      gc.incrementCount(labelsList, logProb(position, labels));
      for (int i = 0; i < labels.length; i++) {
        labels[i]++;
        if (labels[i] < numClasses) {
          break;
        }
        if (i == labels.length - 1) {
          break OUTER;
        }
        labels[i] = 0;
      }
    }
    return gc;
  }

  public GeneralizedCounter probs(int position, int window) {
    GeneralizedCounter<E> gc = new GeneralizedCounter<E>(window);
    int[] labels = new int[window];
    // cdm july 2005: below array initialization isn't necessary: JLS (3rd ed.)
    // 4.12.5
    // Arrays.fill(labels, 0);

    OUTER: while (true) {
      List<E> labelsList = intArrayToListE(labels);
      gc.incrementCount(labelsList, prob(position, labels));
      for (int i = 0; i < labels.length; i++) {
        labels[i]++;
        if (labels[i] < numClasses) {
          break;
        }
        if (i == labels.length - 1) {
          break OUTER;
        }
        labels[i] = 0;
      }
    }
    return gc;
  }

  //
  // HELPER METHODS
  //

  private int[] objectArrayToIntArray(E[] os) {
    int[] is = new int[os.length];
    for (int i = 0; i < os.length; i++) {
      is[i] = classIndex.indexOf(os[i]);
    }
    return is;
  }

  private List<E> intArrayToListE(int[] is) {
    List<E> os = new ArrayList<E>(is.length);
    for (int i : is) {
      os.add(classIndex.get(i));
    }
    return os;
  }

  /**
   * Gives the probability of a tag at a single position conditioned on a
   * sequence of previous labels.
   *
   * @param position Index in sequence
   * @param label Label of item at index
   * @param prevLabels Indices of labels in previous positions
   * @return conditional log probability
   */
  public double condLogProbGivenPrevious(int position, int label, int[] prevLabels) {
    if (prevLabels.length + 1 == windowSize) {
      return factorTables[position].conditionalLogProbGivenPrevious(prevLabels, label);
    } else if (prevLabels.length + 1 < windowSize) {
      FactorTable ft = factorTables[position].sumOutFront();
      while (ft.windowSize() > prevLabels.length + 1) {
        ft = ft.sumOutFront();
      }
      return ft.conditionalLogProbGivenPrevious(prevLabels, label);
    } else {
      int[] p = new int[windowSize - 1];
      System.arraycopy(prevLabels, prevLabels.length - p.length, p, 0, p.length);
      return factorTables[position].conditionalLogProbGivenPrevious(p, label);
    }
  }

  public double condLogProbGivenPrevious(int position, E label, E[] prevLabels) {
    return condLogProbGivenPrevious(position, classIndex.indexOf(label), objectArrayToIntArray(prevLabels));
  }

  public double condProbGivenPrevious(int position, int label, int[] prevLabels) {
    return Math.exp(condLogProbGivenPrevious(position, label, prevLabels));
  }

  public double condProbGivenPrevious(int position, E label, E[] prevLabels) {
    return Math.exp(condLogProbGivenPrevious(position, label, prevLabels));
  }

  public Counter<E> condLogProbsGivenPrevious(int position, int[] prevlabels) {
    Counter<E> c = new ClassicCounter<E>();
    for (int i = 0, sz = classIndex.size(); i < sz; i++) {
      E label = classIndex.get(i);
      c.incrementCount(label, condLogProbGivenPrevious(position, i, prevlabels));
    }
    return c;
  }

  public Counter<E> condLogProbsGivenPrevious(int position, E[] prevlabels) {
    Counter<E> c = new ClassicCounter<E>();
    for (int i = 0, sz = classIndex.size(); i < sz; i++) {
      E label = classIndex.get(i);
      c.incrementCount(label, condLogProbGivenPrevious(position, label, prevlabels));
    }
    return c;
  }

  //
  // PROB OF TAG AT SINGLE POSITION CONDITIONED ON FOLLOWING SEQUENCE OF LABELS
  //

  public double condLogProbGivenNext(int position, int label, int[] nextLabels) {
    position = position + nextLabels.length;
    if (nextLabels.length + 1 == windowSize) {
      return factorTables[position].conditionalLogProbGivenNext(nextLabels, label);
    } else if (nextLabels.length + 1 < windowSize) {
      FactorTable ft = factorTables[position].sumOutFront();
      while (ft.windowSize() > nextLabels.length + 1) {
        ft = ft.sumOutFront();
      }
      return ft.conditionalLogProbGivenPrevious(nextLabels, label);
    } else {
      int[] p = new int[windowSize - 1];
      System.arraycopy(nextLabels, 0, p, 0, p.length);
      return factorTables[position].conditionalLogProbGivenPrevious(p, label);
    }
  }

  public double condLogProbGivenNext(int position, E label, E[] nextLabels) {
    return condLogProbGivenNext(position, classIndex.indexOf(label), objectArrayToIntArray(nextLabels));
  }

  public double condProbGivenNext(int position, int label, int[] nextLabels) {
    return Math.exp(condLogProbGivenNext(position, label, nextLabels));
  }

  public double condProbGivenNext(int position, E label, E[] nextLabels) {
    return Math.exp(condLogProbGivenNext(position, label, nextLabels));
  }

  public Counter<E> condLogProbsGivenNext(int position, int[] nextlabels) {
    Counter<E> c = new ClassicCounter<E>();
    for (int i = 0, sz = classIndex.size(); i < sz; i++) {
      E label = classIndex.get(i);
      c.incrementCount(label, condLogProbGivenNext(position, i, nextlabels));
    }
    return c;
  }

  public Counter<E> condLogProbsGivenNext(int position, E[] nextlabels) {
    Counter<E> c = new ClassicCounter<E>();
    for (int i = 0, sz = classIndex.size(); i < sz; i++) {
      E label = classIndex.get(i);
      c.incrementCount(label, condLogProbGivenNext(position, label, nextlabels));
    }
    return c;
  }

  //
  // PROB OF TAG AT SINGLE POSITION CONDITIONED ON PREVIOUS AND FOLLOWING
  // SEQUENCE OF LABELS
  //

  // public double condProbGivenPreviousAndNext(int position, int label, int[]
  // prevLabels, int[] nextLabels) {

  // }



  //
  // JOINT CONDITIONAL PROBS
  //
  /**
   * @return a new CRFCliqueTree for the weights on the data
   */
  public static <E> CRFCliqueTree<E> getCalibratedCliqueTree(int[][][] data, List<Index<CRFLabel>> labelIndices,
      int numClasses, Index<E> classIndex, E backgroundSymbol, CliquePotentialFunction cliquePotentialFunc, double[][][] featureVals) {

    FactorTable[] factorTables = new FactorTable[data.length];
    FactorTable[] messages = new FactorTable[data.length - 1];

    for (int i = 0; i < data.length; i++) {
      double[][] featureValByCliqueSize = null;
      if (featureVals != null)
        featureValByCliqueSize = featureVals[i];
      factorTables[i] = getFactorTable(data[i], labelIndices, numClasses, cliquePotentialFunc, featureValByCliqueSize, i);

      // System.err.println("before calibration,FT["+i+"] = " + factorTables[i].toProbString());

      if (i > 0) {
        messages[i - 1] = factorTables[i - 1].sumOutFront();
        // System.err.println("forward message, message["+(i-1)+"] = " + messages[i-1].toProbString());
        factorTables[i].multiplyInFront(messages[i - 1]);
        // System.err.println("after forward calibration, FT["+i+"] = " + factorTables[i].toProbString());
      }
    }

    for (int i = factorTables.length - 2; i >= 0; i--) {
      FactorTable summedOut = factorTables[i + 1].sumOutEnd();
      summedOut.divideBy(messages[i]);
      // System.err.println("backward summedOut, summedOut= " + summedOut.toProbString());
      factorTables[i].multiplyInEnd(summedOut);
      // System.err.println("after backward calibration, FT["+i+"] = " + factorTables[i].toProbString());
    }

    return new CRFCliqueTree<E>(factorTables, classIndex, backgroundSymbol);
  }

  /**
   * This function assumes a LinearCliquePotentialFunction is used for wrapping the weights
   * @return a new CRFCliqueTree for the weights on the data
   */
  public static <E> CRFCliqueTree<E> getCalibratedCliqueTree(double[] weights, double wscale, int[][] weightIndices,
      int[][][] data, List<Index<CRFLabel>> labelIndices, int numClasses, Index<E> classIndex, E backgroundSymbol) {

    FactorTable[] factorTables = new FactorTable[data.length];
    FactorTable[] messages = new FactorTable[data.length - 1];

    for (int i = 0; i < data.length; i++) {

      factorTables[i] = getFactorTable(weights, wscale, weightIndices, data[i], labelIndices, numClasses);

      if (i > 0) {
        messages[i - 1] = factorTables[i - 1].sumOutFront();
        factorTables[i].multiplyInFront(messages[i - 1]);
      }
    }

    for (int i = factorTables.length - 2; i >= 0; i--) {

      FactorTable summedOut = factorTables[i + 1].sumOutEnd();
      summedOut.divideBy(messages[i]);
      factorTables[i].multiplyInEnd(summedOut);
    }

    return new CRFCliqueTree<E>(factorTables, classIndex, backgroundSymbol);
  }

  private static FactorTable getFactorTable(double[] weights, double wscale, int[][] weightIndices, int[][] data,
      List<Index<CRFLabel>> labelIndices, int numClasses) {

    FactorTable factorTable = null;

    for (int j = 0, sz = labelIndices.size(); j < sz; j++) {
      Index labelIndex = labelIndices.get(j);
      FactorTable ft = new FactorTable(numClasses, j + 1);

      // ... and each possible labeling for that clique
      for (int k = 0, liSize = labelIndex.size(); k < liSize; k++) {
        int[] label = ((CRFLabel) labelIndex.get(k)).getLabel();
        double weight = 0.0;
        for (int m = 0; m < data[j].length; m++) {
          int wi = weightIndices[data[j][m]][k];
          weight += wscale * weights[wi];
        }
        // try{
        ft.setValue(label, weight);
        // } catch (Exception e) {
        // System.out.println("CRFCliqueTree::getFactorTable");
        // System.out.println("NumClasses: " + numClasses + " j+1: " + (j+1));
        // System.out.println("k: " + k+" label: " +label+" labelIndexSize: " +
        // labelIndex.size());
        // throw new RunTimeException(e.toString());
        // }

      }
      if (j > 0) {
        ft.multiplyInEnd(factorTable);
      }
      factorTable = ft;

    }

    return factorTable;
  }

  // static FactorTable getFactorTable(double[][] weights, int[][] data, List<Index<CRFLabel>> labelIndices, int numClasses, int posInSent) {
  //   CliquePotentialFunction cliquePotentialFunc = new LinearCliquePotentialFunction(weights);
  //   return getFactorTable(data, labelIndices, numClasses, cliquePotentialFunc, null, posInSent);
  // }

  static FactorTable getFactorTable(int[][] data, List<Index<CRFLabel>> labelIndices, int numClasses,
      CliquePotentialFunction cliquePotentialFunc, double[][] featureValByCliqueSize, int posInSent) {
    FactorTable factorTable = null;

    for (int j = 0, sz = labelIndices.size(); j < sz; j++) {
      Index labelIndex = labelIndices.get(j);
      FactorTable ft = new FactorTable(numClasses, j + 1);
      double[] featureVal = null;
      if (featureValByCliqueSize != null)
        featureVal = featureValByCliqueSize[j];

      // ... and each possible labeling for that clique
      for (int k = 0, liSize = labelIndex.size(); k < liSize; k++) {
        int[] label = ((CRFLabel) labelIndex.get(k)).getLabel();
        double cliquePotential = cliquePotentialFunc.computeCliquePotential(j+1, k, data[j], featureVal, posInSent);
        // for (int m = 0; m < data[j].length; m++) {
        //   weight += weights[data[j][m]][k];
        // }
        // try{
        ft.setValue(label, cliquePotential);
        // } catch (Exception e) {
        // System.out.println("CRFCliqueTree::getFactorTable");
        // System.out.println("NumClasses: " + numClasses + " j+1: " + (j+1));
        // System.out.println("k: " + k+" label: " +label+" labelIndexSize: " +
        // labelIndex.size());
        // throw new RunTimeException(e.toString());
        // }

      }
      if (j > 0) {
        ft.multiplyInEnd(factorTable);
      }
      factorTable = ft;

    }

    return factorTable;
  }


  // SEQUENCE MODEL METHODS

  /**
   * Computes the distribution over values of the element at position pos in the
   * sequence, conditioned on the values of the elements in all other positions
   * of the provided sequence.
   *
   * @param sequence
   *          the sequence containing the rest of the values to condition on
   * @param position
   *          the position of the element to give a distribution for
   * @return an array of type double, representing a probability distribution;
   *         sums to 1.0
   */
  public double[] getConditionalDistribution(int[] sequence, int position) {
    double[] result = scoresOf(sequence, position);
    ArrayMath.logNormalize(result);
    // System.out.println("marginal:          " + ArrayMath.toString(marginal,
    // nf));
    // System.out.println("conditional:       " + ArrayMath.toString(result,
    // nf));
    result = ArrayMath.exp(result);
    // System.out.println("conditional:       " + ArrayMath.toString(result,
    // nf));
    return result;
  }

  /**
   * Informs this sequence model that the value of the element at position pos
   * has changed. This allows this sequence model to update its internal model
   * if desired.
   */
  @Override
  public void updateSequenceElement(int[] sequence, int pos, int oldVal) {
    // do nothing; we don't change this model
  }

  /**
   * Informs this sequence model that the value of the whole sequence is
   * initialized to sequence
   */
  @Override
  public void setInitialSequence(int[] sequence) {
    // do nothing
  }

  /**
   * @return the number of possible values for each element; it is assumed to be
   *         the same for the element at each position
   */
  public int getNumValues() {
    return numClasses;
  }

}
