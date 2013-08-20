package edu.stanford.nlp.parser.eval;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ArraySet;

import java.io.PrintWriter;
import java.util.Set;

/** A representation for a single gold-standard correct tree and a set of guess trees.
 * @author Roger Levy
 */
public class CandidateParses {
  private Set<Tree> candidates;
  private Tree trueTree;

  /**
   * The candidate set.
   */
  public Set<Tree> candidates() {
    return candidates;
  }

  /**
   * The true tree.
   */
  public Tree trueTree() {
    return trueTree;
  }

  /**
   * Constructs a new object with the specified candidate set and true tree.
   */
  public CandidateParses(Set<Tree> candidates, Tree trueTree) {
    this.candidates = candidates;
    this.trueTree = trueTree;
  }

  /**
   * Constructs a new object with a singleton candidate set and true tree.
   */
  public CandidateParses(Tree candidate, Tree trueTree) {
    this.candidates = new ArraySet<Tree>(candidate);
    this.trueTree = trueTree;
  }

  /**
   * A string representation.
   */
  @Override
  public String toString() {
    return "true: " + trueTree + "\ncandidates: " + candidates;
  }

  /**
   * Penn-prints the true tree and each guess.
   */
  public void display(PrintWriter pw) {
    pw.println("true:");
    trueTree.pennPrint(pw);
    for (Tree t : candidates) {
      pw.println("candidate:");
      t.pennPrint(pw);
    }
  }

  /**
   * Displays the objectification of the gold and the guesses. 
   */
  public void displayObjectification(PrintWriter pw, TreeObjectifier tobj) {
   pw.println("true:");
    pw.println(tobj.objectify(trueTree));
    for (Tree t : candidates) {
      pw.println("candidate:");
      pw.println(tobj.objectify(t));
    }
  }
}
