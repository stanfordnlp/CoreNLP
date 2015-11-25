package edu.stanford.nlp.ie.pascal;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Jamie Nicolson
 */
public class Prior {
  // Map<String, int> maps field names to indexes in the matrix
  private Map fieldIndices;
  private String[] indexFields;

  // n-dimensional boolean matrix. There will be 2^n entries in the matrix.
  private double[] matrix;

  public Prior(BufferedReader reader) throws IOException {
    String line;
    line = reader.readLine();
    if (line == null) {
      throw new IOException();
    }
    indexFields = line.split("\\s+");
    fieldIndices = new HashMap();
    for (int i = 0; i < indexFields.length; ++i) {
      fieldIndices.put(indexFields[i], Integer.valueOf(i));
    }
    if (indexFields.length < 1 || indexFields.length > 31) {
      throw new IOException("Invalid number of fields, should be >=1 and <= 31");
    }
    int matrixSize = 1 << indexFields.length;
    matrix = new double[matrixSize];
    int matrixIdx = 0;
    while (matrixIdx < matrix.length && (line = reader.readLine()) != null) {
      String[] tokens = line.split("\\s+");
      for (int t = 0; matrixIdx < matrix.length && t < tokens.length; ++t) {
        matrix[matrixIdx++] = Double.parseDouble(tokens[t]);
      }
    }
  }

  /**
   * Map<String, boolean>
   */
  public double get(Set presentFields) {
    int index = 0;
    for (int f = 0; f < indexFields.length; ++f) {
      String field = indexFields[f];
      index *= 2;
      if (presentFields.contains(field)) {
        ++index;
      }
    }
    return matrix[index];
  }

  public static void main(String args[]) throws Exception {

    BufferedReader br = new BufferedReader(new FileReader("/tmp/acstats"));

    Prior p = new Prior(br);

    HashSet hs = new HashSet();
    hs.add("workshopname");
    //hs.add("workshopacronym");

    double d = p.get(hs);
    System.out.println("d is " + d);

  }

}
