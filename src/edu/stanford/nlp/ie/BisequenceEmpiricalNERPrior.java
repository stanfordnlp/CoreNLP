package edu.stanford.nlp.ie;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.sequences.SequenceListener;
import edu.stanford.nlp.sequences.SeqClassifierFlags;

import java.io.*;
import java.util.*;


/**
 * @author Mengqiu Wang
 */

public class BisequenceEmpiricalNERPrior<IN extends CoreMap> {

  private Index<String> tagIndex;
  private int backgroundSymbolIndex;
  private int numClasses;
  private int numTags;
  private int[] possibleValues;
  private int[] currSequence;
  private Index<String> classIndex;
  private List<String> wordDoc;
  private double[][] entityMatrix, subEntityMatrix;
  private List<Entity> entityList;
  private SeqClassifierFlags flags;

  // protected double p1 = -Math.log(0.01);

  private boolean VERBOSE = false;

  public static List debugIndices = Arrays.asList(80, 265, 53+598, 162+598, 163+598);
  public static boolean DEBUG = false;

  public BisequenceEmpiricalNERPrior(String backgroundSymbol, Index<String> classIndex, Index<String> tagIndex, List<IN> doc, Pair<double[][], double[][]> matrices, SeqClassifierFlags flags) {
    this.flags = flags;
    this.classIndex = classIndex;
    this.tagIndex = tagIndex;
    this.backgroundSymbolIndex = classIndex.indexOf(backgroundSymbol);
    this.numClasses = classIndex.size();
    this.numTags = tagIndex.size();
    this.possibleValues = new int[numClasses];
    for (int i=0; i<numClasses; i++) {
      possibleValues[i] = i;
    }
    this.wordDoc = new ArrayList<String>(doc.size());
    for (IN w: doc) {
      wordDoc.add(w.get(CoreAnnotations.TextAnnotation.class));
    }
    entityMatrix = matrices.first();
    subEntityMatrix = matrices.second();
  }

  public static Pair<double[][], double[][]> readEntityMatrices(String fileName, Index<String> tagIndex) {
    int numTags = tagIndex.size();
    double[][] matrix = new double[numTags-1][numTags-1];
    for (int i = 0; i < numTags-1; i++)
      matrix[i] = new double[numTags-1];
    double[][] subMatrix = new double[numTags-1][numTags-1];
    for (int i = 0; i < numTags-1; i++)
      subMatrix[i] = new double[numTags-1];

    try {
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(fileName))));
      String line = null;
      int lineCount = 0;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        String[] parts = line.split("\t");
        for (String part: parts) {
          String[] subparts = part.split(" ");
          String[] subsubparts = subparts[0].split(":");
          double counts = Double.parseDouble(subparts[1]);
          if (counts == 0.0) // smoothing
            counts = 1.0;
          int tagIndex1 = tagIndex.indexOf(subsubparts[0]);
          int tagIndex2 = tagIndex.indexOf(subsubparts[1]);
          if (lineCount < numTags-1)
            matrix[tagIndex1][tagIndex2] = counts;
          else
            subMatrix[tagIndex1][tagIndex2] = counts;
        }
        lineCount++;
      }
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(-1);
    }
    for (int i = 0; i < matrix.length; i++) {
      double sum = ArrayMath.sum(matrix[i]);
      for (int j = 0; j < matrix[i].length; j++)
        matrix[i][j] = Math.log(matrix[i][j] / sum) / 2;
    }
    for (int i = 0; i < subMatrix.length; i++) {
      double sum = ArrayMath.sum(subMatrix[i]);
      for (int j = 0; j < subMatrix[i].length; j++)
        subMatrix[i][j] = Math.log(subMatrix[i][j] / sum);
    }

    System.err.println("Matrix: ");
    System.err.println(ArrayUtils.toString(matrix));
    System.err.println("SubMatrix: ");
    System.err.println(ArrayUtils.toString(subMatrix));

    return new Pair<double[][], double[][]>(matrix, subMatrix);
  }

  static class Entity {
    public int startPosition;
    public int  wordsSize;
    public String surface;
    public int type;
    public List<Entity> subMatch;
    public List<Entity> exactMatch;
  
    public Entity(int startP, List<String> words, int type) {
      this.type = type;
      this.startPosition = startP;
      this.wordsSize = words.size();
      this.surface = StringUtils.join(words, " ");
    }
  
    /**
     * the begining index of other locations where this sequence of
     * words appears.
     */
    public int[] otherOccurrences;
  
    public String toString(Index<String> tagIndex) {
      StringBuffer sb = new StringBuffer();
      sb.append("\"");
      sb.append(surface);
      sb.append("\" start: ");
      sb.append(startPosition);
      sb.append(" type: ");
      sb.append(tagIndex.get(type));
      sb.append(" exact matches: [");
      for (Entity exact: exactMatch) {
        sb.append(exact.startPosition);
        sb.append(":");
        sb.append(exact.surface);
        sb.append(" ");
      }
      sb.append("],");
      sb.append(" sub matches: [");
      for (Entity sub: subMatch) {
        sb.append(sub.startPosition);
        sb.append(":");
        sb.append(sub.surface);
        sb.append(" ");
      }
      sb.append("]");
      return sb.toString();
    }
  }

  public static List<Entity> extractEntities(int[] sequence, List<String> wordDoc, Index<String> tagIndex, Index<String> classIndex, int backgroundSymbolIndex) {
    String rawTag = null;
    String[] parts = null;
    String currTag = "";
    List<String> currWords = new ArrayList<String>();
    List<Entity> entityList = new ArrayList<Entity>();

    for (int i = 0; i < sequence.length; i++) {
      if (sequence[i] != backgroundSymbolIndex) {
        rawTag = classIndex.get(sequence[i]);
        parts = rawTag.split("-");
        if (parts[0].equals("B")) { // B-
          if (currWords.size() > 0) {
            entityList.add(new Entity(i-currWords.size(), currWords, tagIndex.indexOf(currTag)));
            currWords.clear();
          }
          currWords.add(wordDoc.get(i));
          currTag = parts[1];
        } else { // I-
          if (currWords.size() > 0 && parts[1].equals(currTag)) { // matches proceeding tag
            currWords.add(wordDoc.get(i));
          } else { // orphan I- without proceeding B- or mismatch previous tag
            if (currWords.size() > 0) {
              entityList.add(new Entity(i-currWords.size(), currWords, tagIndex.indexOf(currTag)));
              currWords.clear();
            }
            currWords.add(wordDoc.get(i));
            currTag = parts[1];
          }
        }
      } else {
        if (currWords.size() > 0) {
          entityList.add(new Entity(i-currWords.size(), currWords, tagIndex.indexOf(currTag)));
          currWords.clear();
          currTag = "";
        }
      }
    }
    if (currWords.size() > 0) {
      entityList.add(new Entity(sequence.length-currWords.size(), currWords, tagIndex.indexOf(currTag)));
    }
    // build entity matching and sub-entity matching map
    for (int i = 0; i < entityList.size(); i++) {
      Entity curr = entityList.get(i);
      List<Entity> exact = new ArrayList<Entity>();
      List<Entity> subMatch = new ArrayList<Entity>();
      String currStr = curr.surface;

      for (int j = 0; j < entityList.size(); j++) {
        if (i == j)
          continue;
        Entity other = entityList.get(j);
        if (other.surface.indexOf(currStr) != -1) {
          if (other.surface.length() == currStr.length()) {
            if (i < j) // avoid double-counting
              exact.add(other);
          } else { // sub-match has no double-counting problem, cause it's one-directional
            subMatch.add(other);
          }
        }
      }

      curr.exactMatch = exact;
      curr.subMatch = subMatch;
    }

    return entityList;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    for (Entity entity: entityList) {
      sb.append(entity.startPosition);
      sb.append("\t");
      sb.append(entity.surface);
      sb.append("\t");
      sb.append(tagIndex.get(entity.type));
      sb.append("\n");
    }
    return sb.toString();
  }
}
