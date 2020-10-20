package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.Generics;

import java.io.*;
import java.util.*;

/**
 * This class is meant to represent a clique in a (directed
 * or undirected) linear-chain graphical model.  It encodes
 * the relative indices that are included in a clique with
 * respect to the current index (0).  For instance if you have a clique
 * that is current label and two-ago label, then the relative
 * indices clique would look like [-2, 0].  The relativeIndices[]
 * array should be sorted.  Cliques are immutable.  Also, for two
 * cliques, c1 and c2, (c1 == c2) iff c1.equals(c2).
 *
 * @author Jenny Finkel
 */

public class Clique implements Serializable {

  private static final long serialVersionUID = -8109637472035159453L;

  private final int[] relativeIndices;
  protected static final Map<CliqueEqualityWrapper, Clique> interner = Generics.newHashMap();

  private static class CliqueEqualityWrapper {
    private final Clique c;

    public CliqueEqualityWrapper(Clique c) {
      this.c = c;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof CliqueEqualityWrapper)) { return false; }
      CliqueEqualityWrapper otherC = (CliqueEqualityWrapper)o;
      if (otherC.c.relativeIndices.length != c.relativeIndices.length) { return false; }
      for (int i = 0; i < c.relativeIndices.length; i++) {
        if (c.relativeIndices[i] != otherC.c.relativeIndices[i]) { return false; }
      }
      return true;
    }

    @Override
    public int hashCode() {
      int h = 1;
      for (int i : c.relativeIndices) {
        h *= 17;
        h += i;
      }
      return h;
    }
  } // end static class CliqueEqualityWrapper


  private static Clique intern(Clique c) {
    CliqueEqualityWrapper wrapper = new CliqueEqualityWrapper(c);
    Clique newC = interner.get(wrapper);
    if (newC == null) {
      interner.put(wrapper, c);
      newC = c;
    }
    return newC;
  }


  private Clique(int[] relativeIndices) {
    this.relativeIndices = relativeIndices;
  }

  public static Clique valueOf(int maxLeft, int maxRight) {
    int[] ri = new int[-maxLeft+maxRight+1];
    int j = maxLeft;
    for (int i = 0; i < ri.length; i++) {
      ri[i] = j++;
    }
    return valueOfHelper(ri);
  }

  /** Make a clique over the provided relativeIndices.
   *  relativeIndices should be sorted. */
  public static Clique valueOf(int[] relativeIndices) {
    checkSorted(relativeIndices);
    // copy the array so as to be safe
    return valueOfHelper(ArrayUtils.copy(relativeIndices));
  }

  public static Clique valueOf(Clique c, int offset) {
    int[] ri = new int[c.relativeIndices.length];
    for (int i = 0; i < ri.length; i++) {
      ri[i] = c.relativeIndices[i]+offset;
    }
    return valueOfHelper(ri);
  }

  /** This version assumes relativeIndices array no longer needs to
   *  be copied. Further it is assumed that it has already been
   *  checked or assured by construction that relativeIndices
   *  is sorted.
   */
  private static Clique valueOfHelper(int[] relativeIndices) {
    // if clique already exists, return that one
    Clique c = new Clique(relativeIndices);
    return intern(c);
  }

  /** Parameter validity check. */
  private static void checkSorted(int[] sorted) {
    for (int i = 0; i < sorted.length-1; i++) {
      if (sorted[i] > sorted[i+1]) {
        throw new RuntimeException("input must be sorted!");
      }
    }
  }

  /**
   * Convenience method for finding the most far left
   * relative index.
   */
  public int maxLeft() { return relativeIndices[0]; }

  /**
   * Convenience method for finding the most far right
   * relative index.
   */
  public int maxRight() { return relativeIndices[relativeIndices.length-1]; }

  /** The number of nodes in the clique. */
  public int size() { return relativeIndices.length; }

  /** @return the ith relativeIndex */
  public int relativeIndex(int i) { return relativeIndices[i]; }

  /**
   * For a particular relative index, returns which element in
   * the Clique it is.  For instance, if you created a Clique
   * c with relativeIndices [-2, -1, 0], then c.indexOfRelativeIndex(-1)
   * will return 1.  If the relative index is not present, it
   * will return -1.
   */
  public int indexOfRelativeIndex(int relativeIndex) {
    for (int i = 0; i < relativeIndices.length; i++) {
      if (relativeIndices[i] == relativeIndex) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < relativeIndices.length; i++) {
      sb.append(relativeIndices[i]);
      if (i != relativeIndices.length-1) {
        sb.append(", ");
      }
    }
    sb.append(']');
    return sb.toString();
  }

  public Clique leftMessage() {
    int[] ri = new int[relativeIndices.length-1];
    System.arraycopy(relativeIndices, 0, ri, 0, ri.length);
    return valueOfHelper(ri);
  }

  public Clique rightMessage() {
    int[] ri = new int[relativeIndices.length-1];
    System.arraycopy(relativeIndices, 1, ri, 0, ri.length);
    return valueOfHelper(ri);
  }

  public Clique shift(int shiftAmount) {
    if (shiftAmount == 0) { return this; }
    int[] ri = new int[relativeIndices.length];
    for (int i = 0; i < ri.length; i++) {
      ri[i] = relativeIndices[i]+shiftAmount;
    }
    return valueOfHelper(ri);
  }


  private int hashCode = -1;

  @Override
  public int hashCode() {
    if (hashCode == -1) {
      hashCode = toString().hashCode();
    }
    return hashCode;
  }

  protected Object readResolve() {
    return intern(this);
  }

}
