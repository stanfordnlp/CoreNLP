package edu.stanford.nlp.trees;

import java.util.Comparator;
import java.util.IdentityHashMap;

import org.ejml.simple.SimpleMatrix;

/**
 * A tree combined with a map from subtree to SimpleMatrix vectors.
 *
 * @author Richard Socher
 */
public class DeepTree {
  Tree tree;
  IdentityHashMap<Tree, SimpleMatrix> vectors; 
  public double score;
  
  public Tree getTree() {
    return tree;
  }
  
  public void setTree(Tree tree) {
    this.tree = tree;
  }
  
  public IdentityHashMap<Tree, SimpleMatrix> getVectors() {
    return vectors;
  }
  
  public void setVectors(IdentityHashMap<Tree, SimpleMatrix> vectors) {
    this.vectors = vectors;
  }
  
  public double getScore() {
    return score;
  }
  
  public void setScore(double score) {
    this.score = score;
  }

  public DeepTree(Tree tree, IdentityHashMap<Tree, SimpleMatrix> vectors, double score) {
    this.tree = tree;
    this.vectors = vectors;
    this.score = score;
  }

  /**
   * A comparator that can be used with Collections.sort() that puts
   * the highest scoring tree first
   */
  public static final Comparator<DeepTree> DESCENDING_COMPARATOR = new Comparator<DeepTree>() {
    /** Reverses the score comparison so that we can sort highest score first */
    public int compare(DeepTree o1, DeepTree o2) {
      return -Double.compare(o1.score, o2.score);
    }

    public boolean equals(Object obj) {
      return obj == this;
    }
  };
}
