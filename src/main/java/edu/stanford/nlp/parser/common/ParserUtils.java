package edu.stanford.nlp.parser.common;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.trees.TreeFactory;


/**
 * Factor out some useful methods more than lexparser module may want.
 */
public class ParserUtils {

  private ParserUtils() {} // static methods

  /**
   * Construct a fall through tree in case we can't parse this sentence.
   *
   * @param words Words of the sentence that didn't parse
   * @return A tree with X for all the internal nodes.
   *     Preterminals have the right tag if the words are tagged.
   */
  public static Tree xTree(List<? extends HasWord> words) {
    TreeFactory treeFactory = new LabeledScoredTreeFactory();
    List<Tree> lst2 = new ArrayList<>();
    for (HasWord obj : words) {
      String s = obj.word();
      Tree t = treeFactory.newLeaf(s);
      String tag = "XX";
      if (obj instanceof HasTag) {
        if (((HasTag) obj).tag() != null) {
          tag = ((HasTag) obj).tag();
        }
      }
      Tree t2 = treeFactory.newTreeNode(tag, Collections.singletonList(t));
      lst2.add(t2);
    }
    return treeFactory.newTreeNode("X", lst2);
  }

  /**
   * Turn any trees which are taller than maxHeight into x trees
   */
  public static List<Tree> flattenTallTrees(int maxHeight, List<Tree> trees) {
    if (maxHeight <= 0) {
      return trees;
    }
    return trees.stream().map(tree -> (Trees.height(tree) > maxHeight) ? xTree(tree.taggedYield()) : tree).collect(Collectors.toList());
  }


  public static void printOutOfMemory(PrintWriter pw) {
    pw.println();
    pw.println("*******************************************************");
    pw.println("***  WARNING!! OUT OF MEMORY! THERE WAS NOT ENOUGH  ***");
    pw.println("***  MEMORY TO RUN ALL PARSERS.  EITHER GIVE THE    ***");
    pw.println("***  JVM MORE MEMORY, SET THE MAXIMUM SENTENCE      ***");
    pw.println("***  LENGTH WITH -maxLength, OR PERHAPS YOU ARE     ***");
    pw.println("***  HAPPY TO HAVE THE PARSER FALL BACK TO USING    ***");
    pw.println("***  A SIMPLER PARSER FOR VERY LONG SENTENCES.      ***");
    pw.println("*******************************************************");
    pw.println();
  }

}
