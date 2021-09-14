package edu.stanford.nlp.trees.treebank;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeVisitor;

/**
 * Counts punctuation statistics of a treebank.
 * <br>
 * Outputs how many times each punct word occurs anywhere and how
 * often it occurs at the end of a sentence.
 *
 * @author John Bauer
 */
public class PunctCountingTreeVisitor implements TreeVisitor {
  final Pattern pattern;

  IntCounter<String> totalAnywhere = new IntCounter<>();
  IntCounter<String> totalFinal = new IntCounter<>();

  /**
   * Default to only looking for PUNC nodes.
   */
  public PunctCountingTreeVisitor() {
    this("PUNC");
  }

  /**
   * Specify what regex to match for punc nodes.
   */
  public PunctCountingTreeVisitor(String regex) {
    this.pattern = Pattern.compile(regex);
  }

  public void visitTree(Tree t) {
    countAnywhere(t);
    countFinal(t);
  }

  public void countAnywhere(Tree t) {
    if (t.isPreTerminal()) {
      countNode(t, totalAnywhere);
      return;
    }

    if (t.isLeaf()) {
      throw new AssertionError("Should not get here with a leaf!");
    }

    for (Tree child : t.children()) {
      countAnywhere(child);
    }
  }

  public void countFinal(Tree t) {
    if (t.isPreTerminal()) {
      countNode(t, totalFinal);
      return;
    }

    if (t.isLeaf()) {
      throw new AssertionError("Should not get here with a leaf!");
    }

    countFinal(t.children()[t.numChildren()-1]);
  }

  public void countNode(Tree t, IntCounter<String> counter) {
    String label = t.value();
    Matcher matcher = pattern.matcher(label);
    if (matcher.matches()) {
      Tree leaf = t.children()[0];
      counter.incrementCount(leaf.value());
    }
  }

  public static void main(String[] args) {
    DiskTreebank treebank = new DiskTreebank();
    for (String filename : args) {
      treebank.loadPath(filename, null);
    }

    // TODO: add a flag for using different tags
    PunctCountingTreeVisitor visitor = new PunctCountingTreeVisitor();
    treebank.apply(visitor);
    System.out.println("Punct anywhere in a tree:");
    System.out.println(visitor.totalAnywhere);
    System.out.println("Punct at the end of a tree:");
    System.out.println(visitor.totalFinal);
  }
}
