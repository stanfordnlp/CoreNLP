package edu.stanford.nlp.ie.crf; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.util.Index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stores a factor table as a one dimensional array of doubles.
 * This class supports a restricted form of factor table where each
 * variable has the same set of values, but supports cliques of
 * arbitrary size.
 *
 * @author Jenny Finkel
 */
@SuppressWarnings("UnusedDeclaration")
public class FactorTable  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FactorTable.class);

  private final int numClasses;
  private final int windowSize;

  private final double[] table;


  public FactorTable(int numClasses, int windowSize) {
    this.numClasses = numClasses;
    this.windowSize = windowSize;

    table = new double[SloppyMath.intPow(numClasses, windowSize)];
    Arrays.fill(table, Double.NEGATIVE_INFINITY);
  }

  public FactorTable(FactorTable t) {
    numClasses = t.numClasses();
    windowSize = t.windowSize();
    table = new double[t.size()];
    System.arraycopy(t.table, 0, table, 0, t.size());
  }

  public boolean hasNaN() {
    return ArrayMath.hasNaN(table);
  }

  public String toProbString() {
    StringBuilder sb = new StringBuilder(1000).append("{\n");
    for (int i = 0; i < table.length; i++) {
      sb.append(Arrays.toString(toArray(i)))
        .append(": ")
        .append(prob(toArray(i)))
        .append('\n');
    }
    return sb.append('}').toString();
  }

  public String toNonLogString() {
    StringBuilder sb = new StringBuilder(1000).append("{\n");
    for (int i = 0; i < table.length; i++) {
      sb.append(Arrays.toString(toArray(i)))
        .append(": ")
        .append(Math.exp(getValue(i)))
        .append('\n');
    }
    return sb.append('}').toString();
  }

  public <L> String toString(Index<L> classIndex) {
    StringBuilder sb = new StringBuilder(1000).append("{\n");
    for (int i = 0; i < table.length; i++) {
      sb.append(toString(toArray(i), classIndex))
        .append(": ")
        .append(getValue(i))
        .append('\n');
    }
    return sb.append('}').toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(1000).append("{\n");
    for (int i = 0; i < table.length; i++) {
      sb.append(Arrays.toString(toArray(i)))
        .append(": ")
        .append(getValue(i))
        .append('\n');
    }
    return sb.append('}').toString();
  }

  private static <L> String toString(int[] array, Index<L> classIndex) {
    List<L> l = new ArrayList<>(array.length);
    for (int item : array) {
      l.add(classIndex.get(item));
    }
    return l.toString();
  }

  public int[] toArray(int index) {
    int[] indices = new int[windowSize];
    for (int i = indices.length - 1; i >= 0; i--) {
      indices[i] = index % numClasses;
      index /= numClasses;
    }
    return indices;
  }

  /* e.g., numClasses = 4
    [2,3] -> 11
     0 1 2 3
     4 5 6 7
     8 9 10 11
    [0,2] -> 2

    summary:
    index % numClasses -> curr timestamp index
    index / numClasses -> prev timestamp index
  */
  private int indexOf(int[] entry) {
    int index = 0;
    for (int item : entry) {
      index = index * numClasses + item;
    }
    // if (index < 0) throw new RuntimeException("index=" + index + " entry=" + Arrays.toString(entry)); // only if overflow
    return index;
  }

  private int indexOf(int[] front, int end) {
    int index = 0;
    for (int item : front) {
      index = index * numClasses + item;
    }
    return index * numClasses + end;
  }

  private int indexOf(int front, int[] end) {
    int index = front;
    for (int item : end) {
      index = index * numClasses + item;
    }
    return index;
  }

  private int indexOf(int front, int[] end, int cutoff) {
    int index = front;
    for (int i = 0; i < cutoff; i++) {
      index = index * numClasses + end[i];
    }
    return index;
  }

  private int[] indicesEnd(int[] entries) {
    int index = 0;
    for (int entry : entries) {
      index = index * numClasses + entry;
    }
    int[] indices = new int[SloppyMath.intPow(numClasses, windowSize - entries.length)];
    final int offset = SloppyMath.intPow(numClasses, entries.length);
    for (int i = 0; i < indices.length; i++) {
      indices[i] = index;
      index += offset;
    }
    // log.info("indicesEnd returning: " + Arrays.toString(indices));
    return indices;
  }

  private int[] indicesEnd(int entry) {
    int index = entry;
    int[] indices = new int[SloppyMath.intPow(numClasses, windowSize - 1)];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = index;
      index += numClasses;
    }
    // log.info("indicesEnd returning: " + Arrays.toString(indices));
    return indices;
  }


  /** This now returns the first index of the requested entries.
   *  The run of numClasses ^ (windowSize - entries.length)
   *  successive entries will give all of them.
   *
   *  @param entries The class indices of size windowsSize
   *  @return First index of requested entries
   */
  private int indicesFront(int[] entries) {
    int start = 0;
    for (int entry : entries) {
      start = start * numClasses + entry;
    }
    return start * SloppyMath.intPow(numClasses, windowSize - entries.length);
  }

  public int windowSize() {
    return windowSize;
  }

  public int numClasses() {
    return numClasses;
  }

  public int size() {
    return table.length;
  }

  public double totalMass() {
    return ArrayMath.logSum(table);
  }

  /** Returns a single clique potential. */
  public double unnormalizedLogProb(int[] label) {
    return getValue(label);
  }

  /** Returns a single clique potential. */
  public double unnormalizedLogProb(int front, int[] end, int cutoff) {
    return table[indexOf(front, end, cutoff)];
  }

  public double logProb(int[] label) {
    return unnormalizedLogProb(label) - totalMass();
  }

  public double prob(int[] label) {
    return Math.exp(unnormalizedLogProb(label) - totalMass());
  }

  /**
   * Computes the probability of the tag OF being at the end of the table given
   * that the previous tag sequence in table is GIVEN. given is at the beginning,
   * of is at the end.
   *
   * @return the probability of the tag OF being at the end of the table
   */
  public double conditionalLogProbGivenPrevious(int[] given, int of) {
    if (given.length != windowSize - 1) {
      throw new IllegalArgumentException("conditionalLogProbGivenPrevious requires given one less than clique size (" +
              windowSize + ") but was " + Arrays.toString(given));
    }
    // Note: other similar methods could be optimized like this one, but this is the one the CRF uses....
    /*
    int startIndex = indicesFront(given);
    int numCellsToSum = SloppyMath.intPow(numClasses, windowSize - given.length);
    double z = ArrayMath.logSum(table, startIndex, startIndex + numCellsToSum);
    int i = indexOf(given, of);
    System.err.printf("startIndex is %d, numCellsToSum is %d, i is %d (of is %d)%n", startIndex, numCellsToSum, i, of);
    */
    int startIndex = indicesFront(given);
    double z = ArrayMath.logSum(table, startIndex, startIndex + numClasses);
    int i = startIndex + of;
    // System.err.printf("startIndex is %d, numCellsToSum is %d, i is %d (of is %d)%n", startIndex, numClasses, i, of);

    return table[i] - z;
  }

//  public double conditionalLogProbGivenPreviousForPartial(int[] given, int of) {
//    if (given.length != windowSize - 1) {
//      log.info("error computing conditional log prob");
//      System.exit(0);
//    }
//    // int[] label = indicesFront(given);
//    // double[] masses = new double[label.length];
//    // for (int i = 0; i < masses.length; i++) {
//    // masses[i] = table[label[i]];
//    // }
//    // double z = ArrayMath.logSum(masses);
//
//    int i = indexOf(given, of);
//    // if (SloppyMath.isDangerous(z) || SloppyMath.isDangerous(table[i])) {
//    // log.info("z="+z);
//    // log.info("t="+table[i]);
//    // }
//
//    return table[i];
//  }

  /**
   * Computes the probabilities of the tag at the end of the table given that
   * the previous tag sequence in table is GIVEN. given is at the beginning,
   * position in question is at the end
   *
   * @return the probabilities of the tag at the end of the table
   */
  public double[] conditionalLogProbsGivenPrevious(int[] given) {
    if (given.length != windowSize - 1) {
      throw new IllegalArgumentException("conditionalLogProbsGivenPrevious requires given one less than clique size (" +
          windowSize + ") but was " + Arrays.toString(given));
    }
    double[] result = new double[numClasses];
    for (int i = 0; i < numClasses; i++) {
      result[i] = table[indexOf(given, i)];
    }
    ArrayMath.logNormalize(result);
    return result;
  }

  /**
   * Computes the probability of the sequence OF being at the end of the table
   * given that the first tag in table is GIVEN. given is at the beginning, of is
   * at the end
   *
   * @return the probability of the sequence of being at the end of the table
   */
  public double conditionalLogProbGivenFirst(int given, int[] of) {
    if (of.length != windowSize - 1) {
      throw new IllegalArgumentException("conditionalLogProbGivenFirst requires of one less than clique size (" +
          windowSize + ") but was " + Arrays.toString(of));
    }
    // compute P(given, of)
    // double probAll = logProb(labels);
    double probAll = unnormalizedLogProb(given, of, windowSize - 1);

    // compute P(given)
    // double probGiven = logProbFront(given);
    double probGiven = unnormalizedLogProbFront(given);

    // compute P(given, of) / P(given)
    return probAll - probGiven;
  }

  /**
   * Computes the probability of the sequence OF being at the end of the table
   * given that the first tag in table is GIVEN. given is at the beginning, of is
   * at the end.
   *
   * @return the probability of the sequence of being at the end of the table
   */
  public double unnormalizedConditionalLogProbGivenFirst(int given, int[] of) {
    if (of.length != windowSize - 1) {
      throw new IllegalArgumentException("unnormalizedConditionalLogProbGivenFirst requires of one less than clique size (" +
              windowSize + ") but was " + Arrays.toString(of));
    }
    // compute P(given, of)
    // double probAll = logProb(labels);
    double probAll = unnormalizedLogProb(given, of, windowSize - 1);

    // compute P(given)
    // double probGiven = logProbFront(given);
    // double probGiven = unnormalizedLogProbFront(given);

    // compute P(given, of) / P(given)
    // return probAll - probGiven;
    return probAll;
  }

  /**
   * Computes the probability of the tag OF being at the beginning of the table
   * given that the tag sequence GIVEN is at the end of the table. given is at
   * the end, of is at the beginning
   *
   * @return the probability of the tag of being at the beginning of the table
   */
  public double conditionalLogProbGivenNext(int[] given, int of) {
    if (given.length != windowSize - 1) {
      throw new IllegalArgumentException("conditionalLogProbGivenNext requires given one less than clique size (" +
          windowSize + ") but was " + Arrays.toString(given));
    }
    int[] label = indicesEnd(given);
    double[] masses = new double[label.length];
    for (int i = 0; i < masses.length; i++) {
      masses[i] = table[label[i]];
    }
    return table[indexOf(of, given)] - ArrayMath.logSum(masses);
  }

  public double unnormalizedLogProbFront(int[] labels) {
    int startIndex = indicesFront(labels);
    int numCellsToSum = SloppyMath.intPow(numClasses, windowSize - labels.length);
    // double[] masses = new double[labels.length];
    // for (int i = 0; i < masses.length; i++) {
    //   masses[i] = table[labels[i]];
    // }
    return ArrayMath.logSum(table, startIndex, startIndex + numCellsToSum);
  }

  public double logProbFront(int[] label) {
    return unnormalizedLogProbFront(label) - totalMass();
  }

  public double unnormalizedLogProbFront(int label) {
    int numCellsToSum = SloppyMath.intPow(numClasses, windowSize - 1);
    int startIndex = label * numCellsToSum;
    return ArrayMath.logSum(table, startIndex, startIndex + numCellsToSum);
  }

  public double logProbFront(int label) {
    return unnormalizedLogProbFront(label) - totalMass();
  }

  public double unnormalizedLogProbEnd(int[] labels) {
    labels = indicesEnd(labels);
    double[] masses = new double[labels.length];
    for (int i = 0; i < masses.length; i++) {
      masses[i] = table[labels[i]];
    }
    return ArrayMath.logSum(masses);
  }

  public double logProbEnd(int[] labels) {
    return unnormalizedLogProbEnd(labels) - totalMass();
  }

  public double unnormalizedLogProbEnd(int label) {
    int[] labels = indicesEnd(label);
    double[] masses = new double[labels.length];
    for (int i = 0; i < masses.length; i++) {
      masses[i] = table[labels[i]];
    }
    return ArrayMath.logSum(masses);
  }

  public double logProbEnd(int label) {
    return unnormalizedLogProbEnd(label) - totalMass();
  }

  public double getValue(int index) {
    return table[index];
  }

  public double getValue(int[] label) {
    return table[indexOf(label)];
  }

  public void setValue(int index, double value) {
    table[index] = value;
  }

  public void setValue(int[] label, double value) {
    // try{
    table[indexOf(label)] = value;
    // } catch (Exception e) {
    // e.printStackTrace();
    // log.info("Table length: " + table.length + " indexOf(label): "
    // + indexOf(label));
    // throw new ArrayIndexOutOfBoundsException(e.toString());
    // // System.exit(1);
    // }
  }

  public void incrementValue(int[] label, double value) {
    incrementValue(indexOf(label), value);
  }

  public void incrementValue(int index, double value) {
    table[index] += value;
  }

  void logIncrementValue(int index, double value) {
    table[index] = SloppyMath.logAdd(table[index], value);
  }

  public void logIncrementValue(int[] label, double value) {
    logIncrementValue(indexOf(label), value);
  }

  public void multiplyInFront(FactorTable other) {
    int divisor = SloppyMath.intPow(numClasses, windowSize - other.windowSize());
    for (int i = 0; i < table.length; i++) {
      table[i] += other.getValue(i / divisor);
    }
  }

  public void multiplyInEnd(FactorTable other) {
    int divisor = SloppyMath.intPow(numClasses, other.windowSize());
    for (int i = 0; i < table.length; i++) {
      table[i] += other.getValue(i % divisor);
    }
  }

  public FactorTable sumOutEnd() {
    FactorTable ft = new FactorTable(numClasses, windowSize - 1);
    for (int i = 0, sz = ft.size(); i < sz; i++) {
      ft.table[i] = ArrayMath.logSum(table, i * numClasses, (i+1) * numClasses);
    }
    /*
    for (int i = 0; i < table.length; i++) {
      ft.logIncrementValue(i / numClasses, table[i]);
    }
    */
    return ft;
  }

  public FactorTable sumOutFront() {
    FactorTable ft = new FactorTable(numClasses, windowSize - 1);
    int stride = ft.size();
    for (int i = 0; i < stride; i++) {
      ft.setValue(i, ArrayMath.logSum(table, i, table.length, stride));
    }
    return ft;
  }

  public void divideBy(FactorTable other) {
    for (int i = 0; i < table.length; i++) {
      if (table[i] != Double.NEGATIVE_INFINITY || other.table[i] != Double.NEGATIVE_INFINITY) {
        table[i] -= other.table[i];
      }
    }
  }


  public static void main(String[] args) {
    int numClasses = 6;
    final int cliqueSize = 3;
    System.err.printf("Creating factor table with %d classes and window (clique) size %d%n", numClasses, cliqueSize);
    FactorTable ft = new FactorTable(numClasses, cliqueSize);

    /**
     * for (int i = 0; i < 2; i++) { for (int j = 0; j < 2; j++) { for (int k =
     * 0; k < 2; k++) { int[] a = new int[]{i, j, k};
     * System.out.print(ft.toString(a)+": "+ft.indexOf(a)); } } } for (int i =
     * 0; i < 2; i++) { int[] b = new int[]{i};
     * System.out.print(ft.toString(b)+": "+ft.toString(ft.indicesFront(b))); }
     * for (int i = 0; i < 2; i++) { for (int j = 0; j < 2; j++) { int[] b = new
     * int[]{i, j};
     * System.out.print(ft.toString(b)+": "+ft.toString(ft.indicesFront(b))); }
     * } for (int i = 0; i < 2; i++) { int[] b = new int[]{i};
     * System.out.print(ft.toString(b)+": "+ft.toString(ft.indicesBack(b))); }
     * for (int i = 0; i < 2; i++) { for (int j = 0; j < 2; j++) { int[] b = new
     * int[]{i, j}; ft2.setValue(b, (i*2)+j); } } for (int i = 0; i < 2; i++) {
     * for (int j = 0; j < 2; j++) { int[] b = new int[]{i, j};
     * System.out.print(ft.toString(b)+": "+ft.toString(ft.indicesBack(b))); } }
     *
     * System.out.println("##########################################");
     **/

    for (int i = 0; i < numClasses; i++) {
      for (int j = 0; j < numClasses; j++) {
        for (int k = 0; k < numClasses; k++) {
          ft.setValue(new int[] { i, j, k }, (i * 4) + (j * 2) + k);
        }
      }
    }

    log.info(ft);
    double normalization = 0.0;
    for (int i = 0; i < numClasses; i++) {
      for (int j = 0; j < numClasses; j++) {
        for (int k = 0; k < numClasses; k++) {
          normalization += ft.unnormalizedLogProb(new int[] {i, j, k});
        }
      }
    }
    log.info("Normalization Z = " + normalization);

    log.info(ft.sumOutFront());

    FactorTable ft2 = new FactorTable(numClasses, 2);
    for (int i = 0; i < numClasses; i++) {
      for (int j = 0; j < numClasses; j++) {
        ft2.setValue(new int[] { i, j }, i * numClasses + j);
      }
    }

    log.info(ft2);
    // FactorTable ft3 = ft2.sumOutFront();
    // log.info(ft3);

    for (int i = 0; i < numClasses; i++) {
      for (int j = 0; j < numClasses; j++) {
        int[] b = { i, j };
        double t = 0;
        for (int k = 0; k < numClasses; k++) {
          t += Math.exp(ft.conditionalLogProbGivenPrevious(b, k));
          System.err
              .println(k + "|" + i + ',' + j + " : " + Math.exp(ft.conditionalLogProbGivenPrevious(b, k)));
        }
        log.info(t);
      }
    }

    log.info("conditionalLogProbGivenFirst");
    for (int j = 0; j < numClasses; j++) {
      for (int k = 0; k < numClasses; k++) {
        int[] b = { j, k };
        double t = 0.0;
        for (int i = 0; i < numClasses; i++) {
          t += ft.unnormalizedConditionalLogProbGivenFirst(i, b);
          System.err
              .println(i + "|" + j + ',' + k + " : " + ft.unnormalizedConditionalLogProbGivenFirst(i, b));
        }
        log.info(t);
      }
    }

    log.info("conditionalLogProbGivenFirst");
    for (int i = 0; i < numClasses; i++) {
      for (int j = 0; j < numClasses; j++) {
        int[] b = { i, j };
        double t = 0.0;
        for (int k = 0; k < numClasses; k++) {
          t += ft.conditionalLogProbGivenNext(b, k);
          System.err
              .println(i + "," + j + '|' + k + " : " + ft.conditionalLogProbGivenNext(b, k));
        }
        log.info(t);
      }
    }

    numClasses = 2;
    FactorTable ft3 = new FactorTable(numClasses, cliqueSize);
    ft3.setValue(new int[] {0, 0, 0}, Math.log(0.25));
    ft3.setValue(new int[] {0, 0, 1}, Math.log(0.35));
    ft3.setValue(new int[] {0, 1, 0}, Math.log(0.05));
    ft3.setValue(new int[] {0, 1, 1}, Math.log(0.07));
    ft3.setValue(new int[] {1, 0, 0}, Math.log(0.08));
    ft3.setValue(new int[] {1, 0, 1}, Math.log(0.16));
    ft3.setValue(new int[] {1, 1, 0}, Math.log(1e-50));
    ft3.setValue(new int[] {1, 1, 1}, Math.log(1e-50));

    FactorTable ft4 = ft3.sumOutFront();
    log.info(ft4.toNonLogString());
    FactorTable ft5 = ft3.sumOutEnd();
    log.info(ft5.toNonLogString());
  } // end main
}
