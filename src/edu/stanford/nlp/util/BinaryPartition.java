package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.List;

/**
 * A partition of a set of objects into positive and negative classes.
 * 
 * @author David McClosky
 */
public class BinaryPartition<T> {
  private List<T> positiveElements;
  private List<T> negativeElements;

  public BinaryPartition() {
    positiveElements = new ArrayList<T>();
    negativeElements = new ArrayList<T>();
  }
  
  /**
   * Add a positive example.
   */
  public void addPositive(T object) {
    positiveElements.add(object);
  }

  /**
   * Add a negative example.
   */
  public void addNegative(T object) {
    negativeElements.add(object);
  }
  
  /**
   * Add a positive or negative example.
   */
  public void add(boolean positive, T object) {
    if (positive) {
      positiveElements.add(object);
    } else {
      negativeElements.add(object);
    }
  }

  public void setPositiveElements(List<T> positiveElements) {
    this.positiveElements = positiveElements;
  }

  public List<T> getPositiveElements() {
    return positiveElements;
  }

  public void setNegativeElements(List<T> negativeElements) {
    this.negativeElements = negativeElements;
  }

  public List<T> getNegativeElements() {
    return negativeElements;
  }
  
  public List<T> getAllElements() {
    List<T> allElements = new ArrayList<T>();
    allElements.addAll(positiveElements);
    allElements.addAll(negativeElements);
    return allElements;
  }
}
