package edu.stanford.nlp.trees;

import junit.framework.TestCase;

/**
 * Tests some of the various operations performed by the QPTreeTransformer.
 *
 * @author John Bauer
 */
public class QPTreeTransformerTest extends TestCase {

  public void testMoney() {
    String input = "(ROOT (S (NP (DT This)) (VP (VBZ costs) (NP (QP ($ $) (CD 1) (CD million)))) (. .)))";
    String output = "(ROOT (S (NP (DT This)) (VP (VBZ costs) (NP (QP ($ $) (QP (CD 1) (CD million))))) (. .)))";
    runTest(input, output);
  }

  public void testMoneyOrMore() {
    String input = "(ROOT (S (NP (DT This)) (VP (VBZ costs) (NP (QP ($ $) (CD 1) (CD million) (CC or) (JJR more)))) (. .)))";
    // TODO: NP for the right?
    String output = "(ROOT (S (NP (DT This)) (VP (VBZ costs) (NP (QP (QP ($ $) (QP (CD 1) (CD million))) (CC or) (NP (JJR more))))) (. .)))";
    runTest(input, output);

    // First it gets flattened, then the CC gets broken up, but the overall result should be the same
    input = "(ROOT (S (NP (DT This)) (VP (VBZ costs) (NP (QP ($ $) (CD 1) (CD million)) (QP (CC or) (JJR more)))) (. .)))";
    runTest(input, output);
  }

  public void testCompoundModifiers() {
    String input = "(ROOT (S (NP (NP (DT a) (NN stake)) (PP (IN of) (NP (QP (RB just) (IN under) (CD 30)) (NN %))))))";
    String output = "(ROOT (S (NP (NP (DT a) (NN stake)) (PP (IN of) (NP (QP (XS (RB just) (IN under)) (CD 30)) (NN %))))))";
    runTest(input, output);
    // "up" should be RB but make sure it also files for RP
    String input2 = "(ROOT (S (NP (NP (DT a) (NN stake)) (PP (IN of) (NP (QP (RP up) (IN to) (CD 30)) (NN million))))))";
    String output2 = "(ROOT (S (NP (NP (DT a) (NN stake)) (PP (IN of) (NP (QP (XSL (RP up) (IN to)) (CD 30)) (NN million))))))";
    runTest(input2, output2);
  }

  @SuppressWarnings("unused")
  private static void outputResults(String input, String output) {
    Tree inputTree = Tree.valueOf(input);
    System.err.println(inputTree);
    QPTreeTransformer qp = new QPTreeTransformer();
    Tree outputTree = qp.QPtransform(inputTree);
    System.err.println(outputTree);
    System.err.println(output);
  }

  private static void runTest(String input, String output) {
    Tree inputTree = Tree.valueOf(input);
    QPTreeTransformer qp = new QPTreeTransformer();
    Tree outputTree = qp.QPtransform(inputTree);
    assertEquals(output, outputTree.toString());
  }

}
