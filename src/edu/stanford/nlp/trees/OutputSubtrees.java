package edu.stanford.nlp.trees;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.StringUtils;

/**
 * Output a tree and all of its subtrees.  Each subtree is on a
 * separate line, with the start of the line being the top node's
 * label and the rest of the line being the text of the leaves.
 * <br>
 * One specific use of this is to output the sentiment treebank
 * as a bunch of lines with no tree structure.  This is useful
 * for then building a phrase/sentence classifier using that
 * dataset.
 *
 * @author John Bauer
 */
public class OutputSubtrees {
  @ArgumentParser.Option(name="input", gloss="The file to use as input.", required=true)
  private static String INPUT; // = null;

  @ArgumentParser.Option(name="root_only", gloss="Output only the roots", required=false)
  private static boolean ROOT_ONLY = false;

  public static void main(String[] args) {
    // Parse the arguments
    Properties props = StringUtils.argsToProperties(args);
    ArgumentParser.fillOptions(new Class[]{ArgumentParser.class, OutputSubtrees.class}, props);

    MemoryTreebank treebank = new MemoryTreebank("utf-8");
    treebank.loadPath(INPUT, null);
    for (Tree tree : treebank) {
      //System.out.println(tree);
      //System.out.println("--------------");
      Iterable<Tree> subtrees = (ROOT_ONLY) ? Collections.singletonList(tree) : tree;
      for (Tree subtree : subtrees) {
        if (subtree.isLeaf()) {
          continue;
        }
        String value = subtree.label().value();
        List<Tree> leaves = Trees.leaves(subtree);
        List<Label> labels = leaves.stream().map(x -> x.label()).collect(Collectors.toList());
        String text = SentenceUtils.listToString(labels);
          
        System.out.println(value + "   " + text);
      }
      System.out.println();
    }
  }
}

