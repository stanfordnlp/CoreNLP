package edu.stanford.nlp.parser.dvparser;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.parser.lexparser.BinaryGrammar;
import edu.stanford.nlp.parser.lexparser.BinaryRule;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.UnaryGrammar;
import edu.stanford.nlp.parser.lexparser.UnaryRule;
import edu.stanford.nlp.trees.Tree;
import java.util.function.Predicate;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.TwoDimensionalSet;

/**
 * This filter rejects Trees which have unary or binary productions
 * which the given parser does not contain.  
 * <br>
 * One situation where this happens often is when grammar compaction
 * is turned on; this can often result in a Tree where there is no
 * BinaryRule which could explicitely create a particular node, but
 * the Tree is still valid.  However, for various applications of the
 * DVParser, this kind of Tree is useless.  A good way to eliminate
 * most of this kind of tree is to make sure the parser is trained
 * with <code>-compactGrammar 0</code>.
 */
public class FilterConfusingRules implements Predicate<Tree>, Serializable {
  final Set<String> unaryRules = new HashSet<String>();
  final TwoDimensionalSet<String, String> binaryRules = new TwoDimensionalSet<String, String>();
  
  static final boolean DEBUG = false;

  public FilterConfusingRules(LexicalizedParser parser) {
    BinaryGrammar binaryGrammar = parser.bg;
    UnaryGrammar unaryGrammar = parser.ug;
    Options op = parser.getOp();
    Index<String> stateIndex = parser.stateIndex;
    
    for (UnaryRule unaryRule : unaryGrammar) {
      // only make one matrix for each parent state, and only use the
      // basic category for that      
      String childState = stateIndex.get(unaryRule.child);
      String childBasic = op.langpack().basicCategory(childState);
      
      unaryRules.add(childBasic);
    }
    
    for (BinaryRule binaryRule : binaryGrammar) {
      // only make one matrix for each parent state, and only use the
      // basic category for that
      String leftState = stateIndex.get(binaryRule.leftChild);
      String leftBasic = op.langpack().basicCategory(leftState);
      String rightState = stateIndex.get(binaryRule.rightChild);
      String rightBasic = op.langpack().basicCategory(rightState);
      
      binaryRules.add(leftBasic, rightBasic);
    }

    if (DEBUG) {
      System.err.println("UNARY RULES");
      for (String rule : unaryRules) {
        System.err.println("  " + rule);
      }
      System.err.println();
      System.err.println("BINARY RULES");
      for (Pair<String, String> rule : binaryRules) {
        System.err.println("  " + rule);
      }
      System.err.println();
      System.err.println();
    }
  }

  public boolean test(Tree tree) {
    if (tree.isLeaf() || tree.isPreTerminal()) {
      return true;
    }
    if (tree.children().length == 0 || tree.children().length > 2) {
      throw new AssertionError("Tree not binarized");
    }
    if (tree.children().length == 1) {
      if (!unaryRules.contains(tree.children()[0].label().value())) {
        if (DEBUG) {
          System.err.println("Filtered tree because of unary rule: " + tree.children()[0].label().value());
        }
        return false;
      }
    } else {
      if (!binaryRules.contains(tree.children()[0].label().value(), tree.children()[1].label().value())) {
        if (DEBUG) {
          System.err.println("Filtered tree because of binary rule: " + tree.children()[0].label().value() + "," + tree.children()[1].label().value());
        }
        return false;
      }
    }
    for (Tree child : tree.children()) {
      if (!test(child)) {
        return false;
      }
    }
    return true;
  }

}
