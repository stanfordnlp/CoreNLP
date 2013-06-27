package edu.stanford.nlp.parser.lexparser;

import java.util.Collection;
import java.util.Iterator;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.trees.Tree;

/**
 * An abstract superclass for parser classes that extract counts from Trees.
 * @author grenager
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) - cleanup and filling in types
 */

public abstract class AbstractTreeExtractor<T> implements Extractor<T> {

  protected final Options op;
  
  protected AbstractTreeExtractor(Options op) {
    this.op = op;
  }


  @SuppressWarnings({"UnusedDeclaration"})
  protected void tallyLeaf(Tree lt, double weight) {
  }

  protected void tallyPreTerminal(Tree lt, double weight) {
  }

  protected void tallyInternalNode(Tree lt, double weight) {
  }

  protected void tallyRoot(Tree lt, double weight) {
  }

  public T formResult() {
    return null;
  }

  protected void tallyLocalTree(Tree lt, double weight) {
    // printTrainTree(null, "Tallying local tree:", lt);

    if (lt.isLeaf()) {
      //      System.out.println("it's a leaf");
      tallyLeaf(lt, weight);
    } else if (lt.isPreTerminal()) {
      //      System.out.println("it's a preterminal");
      tallyPreTerminal(lt, weight);
    } else {
      //      System.out.println("it's a internal node");
      tallyInternalNode(lt, weight);
    }
  }

  public void tallyTree(Tree t, double weight) {
    tallyRoot(t, weight);
    for (Tree localTree : t.subTreeList()) {
      tallyLocalTree(localTree, weight);
    }
  }

  protected void tallyTrees(Collection<Tree> trees, double weight) {
    for (Tree tree : trees) {
      tallyTree(tree, weight);
    }
  }

  protected void tallyTreeIterator(Iterator<Tree> treeIterator, 
                                   Function<Tree, Tree> f, double weight) {
    while (treeIterator.hasNext()) {
      Tree tree = treeIterator.next();
      try {
        tree = f.apply(tree);
      } catch (Exception e) {
        if (op.testOptions.verbose) {
          e.printStackTrace();
        }
      }
      tallyTree(tree, weight);
    }
  }

  public T extract() {
    return formResult();
  }

  public T extract(Collection<Tree> treeList) {
    tallyTrees(treeList, 1.0);
    return formResult();
  }

  public T extract(Collection<Tree> trees1, double weight1, 
                   Collection<Tree> trees2, double weight2) {
    tallyTrees(trees1, weight1);
    tallyTrees(trees2, weight2);
    return formResult();
  }

  public T extract(Iterator<Tree> treeIterator, Function<Tree, Tree> f, double weight) {
    tallyTreeIterator(treeIterator, f, weight);
    return formResult();
  }

  public T extract(Iterator<Tree> iterator, Function<Tree, Tree> f) {
    return extract(iterator, f, 1.0);
  }

}
