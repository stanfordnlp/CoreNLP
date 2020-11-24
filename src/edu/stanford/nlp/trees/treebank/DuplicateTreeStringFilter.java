package edu.stanford.nlp.trees.treebank;

import edu.stanford.nlp.trees.Tree;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Filters trees based on duplicate toString()
 * <br>
 * for example, 
 * <code>java edu.stanford.nlp.trees.Treebanks -filter edu.stanford.nlp.trees.treebank.DuplicateTreeStringFilter -pennPrint /u/nlp/data/constituency-parser/models-4.0.0/data/ewt/ptb/train/ewt-train.mrg</code>
 *
 * @author John Bauer
 */

public class DuplicateTreeStringFilter implements Predicate<Tree> {
  Set<String> alreadySeen = new HashSet<>();
  public DuplicateTreeStringFilter() { }

  public synchronized boolean test(Tree tree) {
    String treeString = tree.toString();
    if (alreadySeen.contains(treeString)) {
      return false;
    } else {
      alreadySeen.add(treeString);
      return true;
    }
  }
}
