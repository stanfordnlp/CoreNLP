package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Timing;

import java.util.*;

/**
 * Checks the coverage of rules in a grammar on a test treebank.
 *
 * @author Teg Grenager
 */
public class GrammarCoverageChecker {
  private Options op;

  private void testOnTreebank(LexicalizedParser pd, TreebankLangParserParams tlpParams, Treebank testTreebank, String treebankRoot, Index<String> stateIndex) {
    Timing.startTime();
    TreeTransformer annotator = new TreeAnnotator(tlpParams.headFinder(), tlpParams, op);
    // CDM: Aug 2004: With new implementation of treebank split categories,
    // I've hardwired this to load English ones.  Otherwise need training data.
    // op.trainOptions.splitters = new HashSet(Arrays.asList(op.tlpParams.splitters()));
    op.trainOptions.splitters = ParentAnnotationStats.getEnglishSplitCategories(treebankRoot);
    op.trainOptions.sisterSplitters = Generics.newHashSet(Arrays.asList(op.tlpParams.sisterSplitters()));
    for (Tree goldTree : testTreebank) {
      goldTree = annotator.transformTree(goldTree);
      //      System.out.println();
      //      System.out.println("Checking tree: " + goldTree);
      for (Tree localTree : goldTree) {
        // now try to use the grammar to score this local tree
        if (localTree.isLeaf() || localTree.isPreTerminal() || localTree.children().length < 2) {
          continue;
        }
        System.out.println(localTreeToRule(localTree));
        double score = computeLocalTreeScore(localTree, stateIndex, pd);
        if (score == Double.NEGATIVE_INFINITY) {
          //          System.out.println(localTreeToRule(localTree));
        }
        System.out.println("score: " + score);
      }
    }
  }

  private static String localTreeToRule(Tree localTree) {
    StringBuilder sb = new StringBuilder();
    sb.append(localTree.value()).append(" -> ");
    for (int i = 0; i < localTree.children().length - 1; i++) {
      sb.append(localTree.children()[i].value()).append(" ");
    }
    sb.append(localTree.children()[localTree.children().length - 1].value());
    return sb.toString();
  }

  private static double computeLocalTreeScore(Tree localTree, Index<String> stateIndex, LexicalizedParser pd) {
    try {
      String parent = localTree.value();
      int parentState = stateIndex.indexOf(parent);
      //      System.out.println("parentState: " + parentState);
      Tree[] children = localTree.children();
      // let's find the unary to kick things off with the left child (since we assume a left to right grammar
      // first we create the synthetic parent of the leftmost child
      String nextChild = children[0].value();
      // childState = stateIndex.indexOf(nextChild);
      String current = "@" + parent + "| [ [" + nextChild + "] ";
      int currentState = stateIndex.indexOf(current);
      List<UnaryRule> rules = pd.ug.rulesByParent(currentState);
      UnaryRule ur = rules.get(0);
      //      System.out.println("rule: " + ur);
      double localTreeScore = ur.score();
      // go through rest of rules
      for (int i = 1; i < children.length; i++) {
        // find rules in BinaryGrammar that can extend this state
        //        System.out.println("currentState: " + currentState);
        nextChild = children[i].value();
        int childState = stateIndex.indexOf(nextChild);
        //        System.out.println("childState: " + childState);
        List<BinaryRule> l = pd.bg.ruleListByLeftChild(currentState);
        BinaryRule foundBR = null;
        if (i < children.length - 1) {
          // need to the rewrite that doesn't rewrite to the parent
          for (BinaryRule br : l) {
            //            System.out.println("\t\trule: " + br + " parent: " + br.parent + " right: " + br.rightChild);
            if (br.rightChild == childState && br.parent != parentState) {
              foundBR = br;
              break;
            }
          }
        } else {
          // this is the last rule, need to find the rewrite to the parent of the whole local tree
          for (BinaryRule br : l) {
            //            System.out.println("\t\trule: " + br + " parent: " + br.parent + " right: " + br.rightChild);
            if (br.rightChild == childState && br.parent == parentState) {
              foundBR = br;
              break;
            }
          }
        }
        if (foundBR == null) {
          // we never found a matching rule!
          //          System.out.println("broke on " + nextChild);
          return Double.NEGATIVE_INFINITY;
        }
        //        System.out.println("rule: " + foundBR);
        currentState = foundBR.parent;
        localTreeScore += foundBR.score;
      } // end loop through children
      return localTreeScore;
    } catch (NoSuchElementException e) {
      // we couldn't find a state for one of the needed categories
      //      System.out.println("no state found: " + e.toString());
      //      List tempRules = pd.ug.rulesByChild(childState);
      //      for (Iterator iter = tempRules.iterator(); iter.hasNext();) {
      //        UnaryRule ur = (UnaryRule) iter.next();
      //        System.out.println("\t\t\trule with child: " + ur);
      //      }
      return Double.NEGATIVE_INFINITY;
    }
  }


  /**
   * Usage: java edu.stanford.nlp.parser.lexparser.GrammarCoverageChecker parserFile treebankPath low high [optionFlags*]
   */
  public static void main(String[] args) {
    new GrammarCoverageChecker().runTest(args);
  }

  public void runTest(String[] args) {
    // get a parser from file
    LexicalizedParser pd = LexicalizedParser.loadModel(args[0]);
    op = pd.getOp(); // in case a serialized options was read in
    Treebank testTreebank = op.tlpParams.memoryTreebank();
    int testlow = Integer.parseInt(args[2]);
    int testhigh = Integer.parseInt(args[3]);
    testTreebank.loadPath(args[1], new NumberRangeFileFilter(testlow, testhigh, true));
    op.setOptionsOrWarn(args, 4, args.length);
    testOnTreebank(pd, new EnglishTreebankParserParams(), testTreebank, args[1], pd.stateIndex);
  }
}
