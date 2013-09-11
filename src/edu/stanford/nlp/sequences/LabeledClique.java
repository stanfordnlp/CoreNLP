package edu.stanford.nlp.sequences;

import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.Index;

import java.util.*;
import java.io.*;

/**
 * This class contains a {@link Clique} along with
 * the labels at each point.  LabeledCliques are
 * immutable and for two LabeledCliques l1 and l2,
 * (l1 == l2) iff (l1.equals(l2)).
 *
 * @author Jenny Finkel
 */
// cdm 2009: I'm not sure why this doesn't just use the default Object.hashCode() given how equality works....
@SuppressWarnings({"EqualsAndHashcode",
        "ComparableImplementedButEqualsNotOverridden"})
public class LabeledClique implements Serializable,Comparable {

  private static final long serialVersionUID = -311125697888954061L;

  public final Clique clique;
  private final int[] labels;

  protected static Map<LabeledCliqueEqualityWrapper, LabeledClique> interner = new HashMap<LabeledCliqueEqualityWrapper, LabeledClique>();

  private static class LabeledCliqueEqualityWrapper {
    private LabeledClique lc;

    public LabeledCliqueEqualityWrapper(LabeledClique lc) {
      this.lc = lc;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof LabeledCliqueEqualityWrapper)) { return false; }
      LabeledCliqueEqualityWrapper otherLC = (LabeledCliqueEqualityWrapper)o;
      if (otherLC.lc.clique != lc.clique) { return false; }
      if (otherLC.lc.labels.length != lc.labels.length) { return false; }
      for (int i = 0; i < lc.labels.length; i++) {
        if (lc.labels[i] != otherLC.lc.labels[i]) { return false; }
      }
//      System.err.println("true");
      return true;
    }

    @Override
    public int hashCode() {
      int h = lc.clique.hashCode();
      for (int i : lc.labels) {
        h *= 17;
        h += i;
      }
//      System.err.println(lc+" "+h);
      return h;
    }

    @Override
    public String toString() { return '{' +lc.toString()+ '}'; }
  }

  private LabeledClique(Clique c, int[] labels) {
    this.clique = c;
    this.labels = labels;
  }

  private static LabeledClique intern(LabeledClique lc) {
    LabeledCliqueEqualityWrapper wrapper = new LabeledCliqueEqualityWrapper(lc);
    LabeledClique newLC = interner.get(wrapper);
    if (newLC == null) {
      interner.put(wrapper, lc);
      newLC = lc;
    }
    return newLC;
  }

  public static LabeledClique valueOf(Clique c, int[] labels) {
    if (labels.length != c.size()) {
      throw new RuntimeException("Clique and labels must be the same size!");
    }
    return valueOfHelper(c, ArrayUtils.copy(labels));
  }

  /** This version assumes that labels is safe and does not need to be
   *  copied. For example, it is created within another method of this class.
   *  It also assumes labels.length is known to be c.size().
   *
   * @param c The Clique
   * @param labels The array of labels
   * @return A canonical LabeledClique for this Clique and labels
   */
  private static LabeledClique valueOfHelper(Clique c, int[] labels) {
    return intern(new LabeledClique(c, labels));
  }

  public static LabeledClique valueOf(int[] relativeIndices, int[] labels) {
    return valueOf(Clique.valueOf(relativeIndices), labels);
  }

  /**
   * Create a new LabeledClique based on the specified Clique c,
   * and take its labels from the sequence array, where the
   * clique is at the specified position.
   */
  public static LabeledClique valueOf(Clique c, int[] sequence, int position) {
    return valueOf(c, sequence, position, -1);
  }

  /**
   * Create a new LabeledClique based on the specified Clique c,
   * and take its labels from the sequence array, where the
   * clique is at the specified position.  In the event that the
   * clique extends past the sequence array, the background index
   * is used for the label instead.
   */
  public static LabeledClique valueOf(Clique c, int[] sequence, int position, int backgroundIndex) {
    int[] labels = new int[c.size()];
    for (int i = 0; i < labels.length; i++) {
      int ri = c.relativeIndex(i);
      if (position + ri >= 0 && position + ri < sequence.length) {
        labels[i] = sequence[position+ri];
      } else {
        labels[i] = backgroundIndex;
      }
    }
    return valueOfHelper(c, labels);
  }

  /**
   * Create a new LabeledClique, with relative indices taken from Clique c, and
   * the labels taken from the LabeledClique lc, offset by the specified amount.
   * To be more specific, for each index in c, you add the offset to it, and
   * find the label at this new position in lc, and use that label.
   * For example, if c's relative indices are just [0], and lc's are [-1, 0] with
   * corresponding labels [PER, O], and the offset is -1, then the new LabeledClique
   * will have relativeIndices [0], and labels [PER].
   */
  public static LabeledClique valueOf(Clique c, LabeledClique lc, int offset) {
    int[] labels = new int[c.size()];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = lc.labelAtRelIndex(c.relativeIndex(i)+offset);
    }
    return valueOfHelper(c, labels);
  }

  /**
   * @return the label of the ith relative index
   */
  public int label(int i) { return labels[i]; }

  /** The number of nodes in the labeled clique.
   *
   *  @return The number of nodes in the labeled clique.
   */
  public int size() { return clique.size(); }

  /**
   * Returns the label of the specified relative index.
   * If the relatativeIndex does not exist, it returns -1.
   */
  public int labelAtRelIndex(int relativeIndex) {
    int i = clique.indexOfRelativeIndex(relativeIndex);
    if (i < 0) { return -1; }
    return labels[i];
  }

  /**
   * This function takes another LabeledClique and determines
   * if, for the indices that they share, the labels are the
   * same.
   */
  public boolean compatible(LabeledClique otherClique) {
    return compatible(otherClique, 0);
  }

  /**
   * This function takes another LabeledClique and determines
   * if, for the indices that they share (with some offset
   * added to the indices of the otherClique),
   * the labels are the same.  For instance, if I am a clique
   * with relative indices [-1, 0] and labels [PER, OTHER] and
   * this function is passed another
   * labeledClique with relative indices [-1, 0] and labels
   * [OTHER, OTHER] and offset -1, then they are compatible.
   */
  public boolean compatible(LabeledClique otherClique, int offset) {
    int i = 0, j = 0;
    while (true) {
      int ri_i = clique.relativeIndex(i);
      int ri_j = otherClique.clique.relativeIndex(j);
      if (ri_i == ri_j+offset) {
        if (labels[i] != otherClique.labels[j]) {
          return false;
        }
        i++;
        j++;
      } else if (ri_i > ri_j+offset) {
        j++;
      } else {
        i++;
      }
      if (i == size() || j == otherClique.size()) {
        break;
      }
    }
    return true;
  }

  public boolean isSubClique(LabeledClique other, int offset) {
    for (int i = 0, sz = other.size(); i < sz; i++) {
      int ri = other.clique.relativeIndex(i)+offset;
      int label = other.label(i);
      if (label != labelAtRelIndex(ri)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < labels.length; i++) {
      sb.append(clique.relativeIndex(i)).append(" = ").append(labels[i]);
      if (i != labels.length-1) {
        sb.append(", ");
      }
    }
    sb.append(']');
    return sb.toString();
  }

  public String toString(Index labelIndex) {
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < labels.length; i++) {
      sb.append(clique.relativeIndex(i)).append(" = ").append(labelIndex.get(labels[i]));
      if (i != labels.length-1) {
        sb.append(", ");
      }
    }
    sb.append(']');
    return sb.toString();
  }

  private LabeledClique leftMessage; // = null;

  public LabeledClique leftMessage() {
    if (leftMessage == null) {
      Clique lm = clique.leftMessage();
      leftMessage = valueOf(lm, this, 0).shift(1);
    }
    return leftMessage;
  }

  private LabeledClique rightMessage; // = null;

  public LabeledClique rightMessage() {
    if (rightMessage == null) {
      Clique rm = clique.rightMessage();
      rightMessage = valueOf(rm, this, 0);
    }
    return rightMessage;
  }

  public LabeledClique shift(int amount) {
    Clique c = clique.shift(amount);
    return valueOfHelper(c, labels);
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

  public LabeledClique reversedLabels() {
    int[] newLabels = new int[clique.size()];
    for (int i = 0; i < labels.length; i++) {
      newLabels[labels.length-1-i] = labels[i];
    }
    return valueOfHelper(clique, newLabels);
  }

  public int compareTo(Object o) {
    return toString().compareTo(o.toString());
  }

}
