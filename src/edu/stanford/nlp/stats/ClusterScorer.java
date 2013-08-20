package edu.stanford.nlp.stats;

import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.util.Index;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class ClusterScorer
 *
 * @author Teg Grenager
 */
public class ClusterScorer {

  private ClusterScorer() {
  }


  /**
   * @param confusionMatrix a 2 dimensional array such that counts[i][j] contains the number of datums
   *                        assigned to cluster i by clustering1 and cluster j by clustering2.
   */
  public static double computeAdjustedRandIndex(int[][] confusionMatrix) {
    // compute totals
    int[] rowCounts = new int[confusionMatrix.length];
    int[] colCounts = new int[confusionMatrix[0].length];
    int totalCount = 0;
    for (int i = 0; i < confusionMatrix.length; i++) {
      for (int j = 0; j < confusionMatrix[i].length; j++) {
        int c = confusionMatrix[i][j];
        rowCounts[i] += c;
        colCounts[j] += c;
        totalCount += c;
      }
    }
    // compute the eachTerm
    double eachTerm = 0.0;
    for (int i = 0; i < confusionMatrix.length; i++) {
      for (int j = 0; j < confusionMatrix[i].length; j++) {
        eachTerm += SloppyMath.nChooseK(confusionMatrix[i][j], 2);
      }
    }
    // compute the rowTerm
    double rowTerm = 0.0;
    for (int i = 0; i < rowCounts.length; i++) {
      rowTerm += SloppyMath.nChooseK(rowCounts[i], 2);
    }
    // compute the colTerm
    double colTerm = 0.0;
    for (int j = 0; j < colCounts.length; j++) {
      colTerm += SloppyMath.nChooseK(colCounts[j], 2);
    }
    double allTerm = SloppyMath.nChooseK(totalCount, 2);

    double result = (eachTerm - ((rowTerm * colTerm) / allTerm)) / ((0.5 * (rowTerm + colTerm)) - ((rowTerm * colTerm) / allTerm));
    return result;
  }

  public static double computeMutualInformation(int[][] confusionMatrix) {
    // compute totals
    double[] rowCounts = new double[confusionMatrix.length];
    double[] colCounts = new double[confusionMatrix[0].length];
    double totalCount = 0;
    for (int i = 0; i < confusionMatrix.length; i++) {
      for (int j = 0; j < confusionMatrix[i].length; j++) {
        int c = confusionMatrix[i][j];
        rowCounts[i] += c;
        colCounts[j] += c;
        totalCount += c;
      }
    }
    double result = 0.0;
    for (int i = 0; i < confusionMatrix.length; i++) {
      for (int j = 0; j < confusionMatrix[i].length; j++) {
        int c = confusionMatrix[i][j];
        if (c>0) {
          double prob = c / totalCount;
          double prodMarginals = (rowCounts[i]/totalCount)*(colCounts[j]/totalCount);
          result += (prob * Math.log(prob/prodMarginals));
        }
      }
    }
    return result;
  }

  /**
   * Assumes gold clusters are the row labels, and guessed clusters are the col labels
   * Greedily assigns columns to the row that it has the highest overlap with.
   *
   * @return an assignment from col numbers to row numbers
   */
  public static int[] computeGreedyAssignment(int[][] confusionMatrix) {
    int[] assignment = new int[confusionMatrix[0].length];
    // compute the greedy assignment
    for (int j = 0; j < confusionMatrix[0].length; j++) { // go through columns
      int maxRow = -1;
      int maxNum = 0;
      for (int i = 0; i < confusionMatrix.length; i++) { // go through rows for this column
        if (confusionMatrix[i][j] >= maxNum) {
          maxNum = confusionMatrix[i][j];
          maxRow = i;
        }
      }
      assignment[j] = maxRow;
    }
    return assignment;
  }

  /**
   *
   * @param confusionMatrix a confusion matrix
   * @param assignment an assignment from col numbers to row numbers
   */
  public static double computeAccuracyOfAssignment(int[][] confusionMatrix, int[] assignment) {
    int numTotal = 0;
    int numCorrect = 0;
    for (int j = 0; j < confusionMatrix[0].length; j++) { // go through columns
      for (int i = 0; i < confusionMatrix.length; i++) { // go through rows for this column
        numTotal += confusionMatrix[i][j];
      }
      numCorrect += confusionMatrix[assignment[j]][j]; // num correct in this column according to the assignment
    }
    return (double) numCorrect / (double) numTotal;
  }

  /**
   * Assumes that rows and columns are indexed in the same order.
   * @param confusionMatrix a confusion matrix
   */
  public static double computeAccuracy(int[][] confusionMatrix) {
    int numTotal = 0;
    int numCorrect = 0;
    for (int j = 0; j < confusionMatrix[0].length; j++) { // go through columns
      for (int i = 0; i < confusionMatrix.length; i++) { // go through rows for this column
        numTotal += confusionMatrix[i][j];
      }
      numCorrect += confusionMatrix[j][j]; // num correct in this column
    }
    return (double) numCorrect / (double) numTotal;
  }


  public static <K,V> int[][] buildConfusionMatrix(Map<K,V> clusterMap1, Map<K,V> clusterMap2, Index<V> rowIndex, Index<V> colIndex) {
    if (!clusterMap1.keySet().equals(clusterMap2.keySet())) {
      throw new RuntimeException("keySets of clusterings must be equal.");
    }
    // first, put all object in canonical order
    List<K> keys = new ArrayList<K>(clusterMap1.keySet());
    // first thing, assign indices to the cluster objects themselves
    int[] clustering1 = new int[keys.size()];
    int[] clustering2 = new int[keys.size()];
    for (int i = 0; i < keys.size(); i++) {
      K key = keys.get(i);
      V cluster1 = clusterMap1.get(key);
      V cluster2 = clusterMap2.get(key);
      rowIndex.add(cluster1);
      colIndex.add(cluster2);
      clustering1[i] = rowIndex.indexOf(cluster1);
      clustering2[i] = colIndex.indexOf(cluster2);
    }
    return buildConfusionMatrix(clustering1, clustering2, rowIndex.size(), colIndex.size());
  }

  public static int[][] buildConfusionMatrix(int[] clustering1, int[] clustering2, int numRows, int numCols) {
    if (clustering1.length != clustering2.length) throw new RuntimeException("Clusterings must have same length.");
    // create a big 2 dimensional array of counts
    int[][] confusionMatrix = new int[numRows][numCols];
    // populate it
    for (int i=0; i<clustering1.length; i++) {
      confusionMatrix[clustering1[i]][clustering2[i]]++;
    }
    return confusionMatrix;
  }

}

