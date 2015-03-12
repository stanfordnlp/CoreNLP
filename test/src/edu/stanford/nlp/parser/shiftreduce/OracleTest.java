package edu.stanford.nlp.parser.shiftreduce;

import junit.framework.TestCase;

import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OracleTest extends TestCase {
  public void testBuildParentMap() {
    Tree tree = Tree.valueOf("(A (B foo) (C bar))");
    Map<Tree, Tree> parents = Oracle.buildParentMap(tree);
    int total = recursiveTestBuildParentMap(tree, parents);
    assertEquals(total, parents.size());
  }

  public static int recursiveTestBuildParentMap(Tree tree, Map<Tree, Tree> parents) {
    int children = tree.children().length;
    for (Tree child : tree.children()) {
      assertEquals(tree, parents.get(child));
      children += recursiveTestBuildParentMap(child, parents);
    }
    return children;
  }

  // A small variety of trees to test on, especially with different depths of unary transitions
  String[] TEST_TREES = { "(ROOT (S (S (NP (PRP I)) (VP (VBP like) (NP (JJ big) (NNS butts)))) (CC and) (S (NP (PRP I)) (VP (MD can) (RB not) (VP (VB lie)))) (. .)))",
                          "(ROOT (S (NP (NP (RB Not) (PDT all) (DT those)) (SBAR (WHNP (WP who)) (S (VP (VBD wrote))))) (VP (VBP oppose) (NP (DT the) (NNS changes))) (. .)))",
                          "(ROOT (S (NP (NP (DT The) (NNS anthers)) (PP (IN in) (NP (DT these) (NNS plants)))) (VP (VBP are) (ADJP (JJ difficult) (SBAR (S (VP (TO to) (VP (VB clip) (PRT (RP off)))))))) (. .)))" };

  public List<Tree> buildTestTreebank() {
    MemoryTreebank treebank = new MemoryTreebank();

    for (String text : TEST_TREES) {
      Tree tree = Tree.valueOf(text);
      treebank.add(tree);
    }

    List<Tree> binarizedTrees = ShiftReduceParser.binarizeTreebank(treebank, new Options());
    return binarizedTrees;
  }

  /**
   * Tests that if you give the Oracle a tree and ask it for a
   * sequence of transitions, applying the given transition each time,
   * it produces the original tree again.
   */
  public void testEndToEndCompoundUnaries() {
    List<Tree> binarizedTrees = buildTestTreebank();
    Oracle oracle = new Oracle(binarizedTrees, true, Collections.singleton("ROOT"));
    runEndToEndTest(binarizedTrees, oracle);
  }

  public void testEndToEndSingleUnaries() {
    List<Tree> binarizedTrees = buildTestTreebank();
    Oracle oracle = new Oracle(binarizedTrees, false, Collections.singleton("ROOT"));
    runEndToEndTest(binarizedTrees, oracle);
  }

  public static void runEndToEndTest(List<Tree> binarizedTrees, Oracle oracle) {
    for (int index = 0; index < binarizedTrees.size(); ++index) {
      State state = ShiftReduceParser.initialStateFromGoldTagTree(binarizedTrees.get(index));
      while (!state.isFinished()) {
        OracleTransition gold = oracle.goldTransition(index, state);
        assertTrue(gold.transition != null);
        state = gold.transition.apply(state);
      }
      assertEquals(binarizedTrees.get(index), state.stack.peek());
    }
  }
}
