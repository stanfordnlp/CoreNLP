package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.util.Index;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


/**
 * @author Jenny Finkel
 */

public class CRFLabel implements Serializable {

  private static final long serialVersionUID = 7403010868396790276L;

  private final int[] label;
  int hashCode = -1;

  // todo: When rebuilding, change this to a better hash function like 31
  private static final int maxNumClasses = 10;

  public CRFLabel(int[] label) {
    this.label = label;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof CRFLabel)) {
      return false;
    }
    CRFLabel other = (CRFLabel) o;

    if (other.label.length != label.length) {
      return false;
    }
    for (int i = 0; i < label.length; i++) {
      if (label[i] != other.label[i]) {
        return false;
      }
    }

    return true;
  }

  public CRFLabel getSmallerLabel(int size) {
    int[] newLabel = new int[size];
    System.arraycopy(label, label.length - size, newLabel, 0, size);
    return new CRFLabel(newLabel);
  }

  public CRFLabel getOneSmallerLabel() {
    return getSmallerLabel(label.length - 1);
  }

  public int[] getLabel() {
    return label;
  }

  public <E> String toString(Index<E> classIndex) {
    List<E> l = new ArrayList<E>();
    for (int i = 0; i < label.length; i++) {
      l.add(classIndex.get(label[i]));
    }
    return l.toString();
  }

  @Override
  public String toString() {
    List<Integer> l = new ArrayList<Integer>();
    for (int i = 0; i < label.length; i++) {
      l.add(Integer.valueOf(label[i]));
    }
    return l.toString();
  }

  @Override
  public int hashCode() {
    if (hashCode < 0) {
      hashCode = 0;
      for (int i = 0; i < label.length; i++) {
        hashCode *= maxNumClasses;
        hashCode += label[i];
      }
    }
    return hashCode;
  }

}
