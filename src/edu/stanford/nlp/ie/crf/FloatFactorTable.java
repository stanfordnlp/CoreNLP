package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.util.Index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/** Stores a factor table as a one dimensional array of floats.
 *
 *  @author Jenny Finkel
 */

public class FloatFactorTable {

  private final int numClasses;
  private final int windowSize;

  private final float[] table;

  public FloatFactorTable(int numClasses, int windowSize) {
    this.numClasses = numClasses;
    this.windowSize = windowSize;

    table = new float[SloppyMath.intPow(numClasses, windowSize)];
    Arrays.fill(table, Float.NEGATIVE_INFINITY);
  }

  public boolean hasNaN() {
    return ArrayMath.hasNaN(table);
  }

  public String toProbString() {
    StringBuilder sb = new StringBuilder("{\n");
    for (int i = 0; i < table.length; i++) {
      sb.append(Arrays.toString(toArray(i)));
      sb.append(": ");
      sb.append(prob(toArray(i)));
      sb.append("\n");
    }
    sb.append("}");
    return sb.toString();
  }

  public String toString(Index classIndex) {
    StringBuilder sb = new StringBuilder("{\n");
    for (int i = 0; i < table.length; i++) {
      sb.append(toString(toArray(i), classIndex));
      sb.append(": ");
      sb.append(getValue(i));
      sb.append("\n");
    }
    sb.append("}");
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("{\n");
    for (int i = 0; i < table.length; i++) {
      sb.append(Arrays.toString(toArray(i)));
      sb.append(": ");
      sb.append(getValue(i));
      sb.append("\n");
    }
    sb.append("}");
    return sb.toString();
  }

  private String toString(int[] array, Index classIndex) {
    List l = new ArrayList();
    for (int i = 0; i < array.length; i++) {
      l.add(classIndex.get(array[i]));
    }
    return l.toString();
  }

  private int[] toArray(int index) {
    int[] indices = new int[windowSize];
    for (int i = indices.length - 1; i >= 0; i--) {
      indices[i] = index % numClasses;
      index /= numClasses;
    }
    return indices;
  }

  private int indexOf(int[] entry) {
    int index = 0;
    for (int i = 0; i < entry.length; i++) {
      index *= numClasses;
      index += entry[i];
    }
    return index;
  }

  private int indexOf(int[] front, int end) {
    int index = 0;
    for (int i = 0; i < front.length; i++) {
      index *= numClasses;
      index += front[i];
    }
    index *= numClasses;
    index += end;
    return index;
  }

  private int[] indicesEnd(int[] entries) {
    int[] indices = new int[SloppyMath.intPow(numClasses, windowSize - entries.length)];
    int offset = SloppyMath.intPow(numClasses, entries.length);
    int index = 0;
    for (int i = 0; i < entries.length; i++) {
      index *= numClasses;
      index += entries[i];
    }
    for (int i = 0; i < indices.length; i++) {
      indices[i] = index;
      index += offset;
    }
    return indices;
  }

  private int[] indicesFront(int[] entries) {
    int[] indices = new int[SloppyMath.intPow(numClasses, windowSize - entries.length)];
    int offset = SloppyMath.intPow(numClasses, windowSize - entries.length);
    int start = 0;
    for (int i = 0; i < entries.length; i++) {
      start *= numClasses;
      start += entries[i];
    }
    start *= offset;
    int end = 0;
    for (int i = 0; i < entries.length; i++) {
      end *= numClasses;
      end += entries[i];
      if (i == entries.length - 1) {
        end += 1;
      }
    }
    end *= offset;
    for (int i = start; i < end; i++) {
      indices[i - start] = i;
    }
    return indices;
  }

  public int windowSize() {
    return windowSize;
  }

  public int numClasses() {
    return numClasses;
  }

  private int size() {
    return table.length;
  }

  public float totalMass() {
    return ArrayMath.logSum(table);
  }

  public float unnormalizedLogProb(int[] label) {
    return getValue(label);
  }

  public float logProb(int[] label) {
    return unnormalizedLogProb(label) - totalMass();
  }


  public float prob(int[] label) {
    return (float) Math.exp(unnormalizedLogProb(label) - totalMass());
  }

  // given is at the begining, of is at the end
  public float conditionalLogProb(int[] given, int of) {
    if (given.length != windowSize - 1) {
      System.err.println("error computing conditional log prob");
      System.exit(0);
    }
    int[] label = indicesFront(given);
    float[] masses = new float[label.length];
    for (int i = 0; i < masses.length; i++) {
      masses[i] = table[label[i]];
    }
    float z = ArrayMath.logSum(masses);

    return table[indexOf(given, of)] - z;
  }

  public float unnormalizedLogProbFront(int[] label) {
    label = indicesFront(label);
    float[] masses = new float[label.length];
    for (int i = 0; i < masses.length; i++) {
      masses[i] = table[label[i]];
    }
    return ArrayMath.logSum(masses);
  }

  public float logProbFront(int[] label) {
    return unnormalizedLogProbFront(label) - totalMass();
  }

  public float unnormalizedLogProbEnd(int[] label) {
    label = indicesEnd(label);
    float[] masses = new float[label.length];
    for (int i = 0; i < masses.length; i++) {
      masses[i] = table[label[i]];
    }
    return ArrayMath.logSum(masses);
  }

  public float logProbEnd(int[] label) {
    return unnormalizedLogProbEnd(label) - totalMass();
  }

  public float unnormalizedLogProbEnd(int label) {
    int[] l = {label};
    l = indicesEnd(l);
    float[] masses = new float[l.length];
    for (int i = 0; i < masses.length; i++) {
      masses[i] = table[l[i]];
    }
    return ArrayMath.logSum(masses);
  }

  public float logProbEnd(int label) {
    return unnormalizedLogProbEnd(label) - totalMass();
  }

  private float getValue(int index) {
    return table[index];
  }

  public float getValue(int[] label) {
    return table[indexOf(label)];
  }

  private void setValue(int index, float value) {
    table[index] = value;
  }

  public void setValue(int[] label, float value) {
    table[indexOf(label)] = value;
  }

  public void incrementValue(int[] label, float value) {
    table[indexOf(label)] += value;
  }

  private void logIncrementValue(int index, float value) {
    table[index] = SloppyMath.logAdd(table[index], value);
  }

  public void logIncrementValue(int[] label, float value) {
    int index = indexOf(label);
    table[index] = SloppyMath.logAdd(table[index], value);
  }

  public void multiplyInFront(FloatFactorTable other) {
    int divisor = SloppyMath.intPow(numClasses, windowSize - other.windowSize());
    for (int i = 0; i < table.length; i++) {
      table[i] += other.getValue(i / divisor);
    }
  }

  public void multiplyInEnd(FloatFactorTable other) {
    int divisor = SloppyMath.intPow(numClasses, other.windowSize());
    for (int i = 0; i < table.length; i++) {
      table[i] += other.getValue(i % divisor);
    }
  }

  public FloatFactorTable sumOutEnd() {
    FloatFactorTable ft = new FloatFactorTable(numClasses, windowSize - 1);
    for (int i = 0; i < table.length; i++) {
      ft.logIncrementValue(i / numClasses, table[i]);
    }
    return ft;
  }

  public FloatFactorTable sumOutFront() {
    FloatFactorTable ft = new FloatFactorTable(numClasses, windowSize - 1);
    int mod = SloppyMath.intPow(numClasses, windowSize - 1);
    for (int i = 0; i < table.length; i++) {
      ft.logIncrementValue(i % mod, table[i]);
    }
    return ft;
  }

  public void divideBy(FloatFactorTable other) {
    for (int i = 0; i < table.length; i++) {
      if (table[i] != Float.NEGATIVE_INFINITY || other.table[i] != Float.NEGATIVE_INFINITY) {
        table[i] -= other.table[i];
      }
    }
  }

  public static void main(String[] args) {
    FloatFactorTable ft = new FloatFactorTable(6, 3);

    /**
     for (int i = 0; i < 2; i++) {
     for (int j = 0; j < 2; j++) {
     for (int k = 0; k < 2; k++) {
     int[] a = new int[]{i, j, k};
     System.out.print(ft.toString(a)+": "+ft.indexOf(a));
     }
     }
     }
     for (int i = 0; i < 2; i++) {
     int[] b = new int[]{i};
     System.out.print(ft.toString(b)+": "+ft.toString(ft.indicesFront(b)));
     }
     for (int i = 0; i < 2; i++) {
     for (int j = 0; j < 2; j++) {
     int[] b = new int[]{i, j};
     System.out.print(ft.toString(b)+": "+ft.toString(ft.indicesFront(b)));
     }
     }
     for (int i = 0; i < 2; i++) {
     int[] b = new int[]{i};
     System.out.print(ft.toString(b)+": "+ft.toString(ft.indicesBack(b)));
     }	for (int i = 0; i < 2; i++) {
     for (int j = 0; j < 2; j++) {
     int[] b = new int[]{i, j};
     ft2.setValue(b, (i*2)+j);
     }
     }
     for (int i = 0; i < 2; i++) {
     for (int j = 0; j < 2; j++) {
     int[] b = new int[]{i, j};
     System.out.print(ft.toString(b)+": "+ft.toString(ft.indicesBack(b)));
     }
     }

     System.out.println("##########################################");

     **/

    for (int i = 0; i < 6; i++) {
      for (int j = 0; j < 6; j++) {
        for (int k = 0; k < 6; k++) {
          int[] b = new int[]{i, j, k};
          ft.setValue(b, (i * 4) + (j * 2) + k);
        }
      }
    }

    //System.out.println(ft);
    //System.out.println(ft.sumOutFront());

    FloatFactorTable ft2 = new FloatFactorTable(6, 2);
    for (int i = 0; i < 6; i++) {
      for (int j = 0; j < 6; j++) {
        int[] b = new int[]{i, j};
        ft2.setValue(b, i * 6 + j);
      }
    }

    System.out.println(ft);
    //FloatFactorTable ft3 = ft2.sumOutFront();
    //System.out.println(ft3);

    for (int i = 0; i < 6; i++) {
      for (int j = 0; j < 6; j++) {
        int[] b = new int[]{i, j};
        float t = 0;
        for (int k = 0; k < 6; k++) {
          t += Math.exp(ft.conditionalLogProb(b, k));
          System.err.println(k + "|" + i + "," + j + " : " + Math.exp(ft.conditionalLogProb(b, k)));
        }
        System.out.println(t);
      }
    }
  }
}
