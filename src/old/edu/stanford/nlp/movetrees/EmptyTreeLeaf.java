package old.edu.stanford.nlp.movetrees;

import old.edu.stanford.nlp.ling.Label;
import old.edu.stanford.nlp.ling.LabeledWord;
import old.edu.stanford.nlp.ling.StringLabel;
import old.edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import old.edu.stanford.nlp.trees.LabeledScoredTreeLeaf;
import old.edu.stanford.nlp.trees.Tree;
import old.edu.stanford.nlp.trees.TreeFactory;

import java.util.List;


/**
 * @author Roger Levy
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <EmptyType> 
 */

public class EmptyTreeLeaf<EmptyType> extends LabeledScoredTreeLeaf implements HasTrace {

  /**
   * 
   */
  private static final long serialVersionUID = 77798427030716852L;

  static final String STANDIN = "standin";

  private Tree movedTree;
  private EmptyType emptyType;


  public EmptyType emptyType() {
    return emptyType;
  }

  public void setEmptyType(EmptyType o) {
    emptyType = o;
  }

  public Tree traceTo() {
    return movedTree;
  }

  public void setTraceTo(Tree t) {
    movedTree = t;
  }


  /**
   * A <code>EmptyTreeLeaf</code> is invisible in the yield
   */
  @Override
  public <T> List<T> yield(List<T> y) {
    return y;
  }

  /**
   * A <code>EmptyTreeLeaf</code> is invisible in the tagged yield
   */
  @Override
  public List<LabeledWord> labeledYield(List<LabeledWord> y) {
    return y;
  }

  /**
   * prints the trace, plus the label and the yield of the pointed-to
   * constituent
   */
  @Override
  public String toString() {
    String str = label().toString() + "_" + emptyType().toString();
    if (traceTo() != null) {
      if (traceTo().dominates(this)) // error-checking
      {
        System.err.println("Warning -- trace points to self-dominating node");
      }
      str = str + "[" + traceTo().label() + " " + traceTo().yield() + "]";
    }
    return str;
  }

  /**
   * major yuk here. Reproduces the emptyType and traceTo from the
   * leaf out of which the treeFactory is vended.
   */
  @Override
  public TreeFactory treeFactory() {
    return new TreeFactory() {
      TreeFactory lstf = new LabeledScoredTreeFactory();

      public Tree newLeaf(Label l) {
        return new EmptyTreeLeaf<EmptyType>(l, emptyType(), traceTo());
      }

      public Tree newLeaf(String str) {
        return new EmptyTreeLeaf<EmptyType>(new StringLabel(str), emptyType(), traceTo());
      }

      public Tree newTreeNode(Label l, List<Tree> kids) {
        return lstf.newTreeNode(l, kids);
      }

      public Tree newTreeNode(String str, List<Tree> kids) {
        return lstf.newTreeNode(str, kids);
      }


    };
  }

  /**
   * Create a leaf parse tree with given word.
   *
   * @param label the <code>Label</code> representing the <i>word</i> for
   *              this new tree leaf.
   * @param t     the antecedent returned by {@link #traceTo()}
   */
  public EmptyTreeLeaf(Label label, EmptyType type, Tree t) {
    this(label, type);
    movedTree = t;
  }

  /**
   * Create a leaf parse tree with given word.
   *
   * @param label the <code>Label</code> representing the <i>word</i> for
   *              this new tree leaf.
   */
  public EmptyTreeLeaf(Label label, EmptyType type) {
    super(label);
    emptyType = type;
    movedTree = null;
  }

}
