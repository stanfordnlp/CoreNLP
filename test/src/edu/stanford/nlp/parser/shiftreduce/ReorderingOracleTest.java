package edu.stanford.nlp.parser.shiftreduce;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.Debinarizer;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.util.Generics;

/**
 * Test the results that come back when you run the ReorderingOracle
 * on various inputs
 *
 * @author John Bauer
 */
public class ReorderingOracleTest extends TestCase {
  FinalizeTransition finalize = new FinalizeTransition(Collections.singleton("ROOT"));
  ShiftTransition shift = new ShiftTransition();

  BinaryTransition rightNP = new BinaryTransition("NP", BinaryTransition.Side.RIGHT, false);
  BinaryTransition tempRightNP = new BinaryTransition("@NP", BinaryTransition.Side.RIGHT, false);
  BinaryTransition leftNP = new BinaryTransition("NP", BinaryTransition.Side.LEFT, false);
  BinaryTransition tempLeftNP = new BinaryTransition("@NP", BinaryTransition.Side.LEFT, false);

  BinaryTransition rightVP = new BinaryTransition("VP", BinaryTransition.Side.RIGHT, false);
  BinaryTransition tempRightVP = new BinaryTransition("@VP", BinaryTransition.Side.RIGHT, false);
  BinaryTransition leftVP = new BinaryTransition("VP", BinaryTransition.Side.LEFT, false);
  BinaryTransition tempLeftVP = new BinaryTransition("@VP", BinaryTransition.Side.LEFT, false);

  BinaryTransition rightS = new BinaryTransition("S", BinaryTransition.Side.RIGHT, false);
  BinaryTransition tempRightS = new BinaryTransition("@S", BinaryTransition.Side.RIGHT, false);
  BinaryTransition leftS = new BinaryTransition("S", BinaryTransition.Side.LEFT, false);
  BinaryTransition tempLeftS = new BinaryTransition("@S", BinaryTransition.Side.LEFT, false);

  UnaryTransition unaryADVP = new UnaryTransition("ADVP", false);

  String[] WORDS = { "My", "dog", "also", "likes", "eating", "sausage" };
  String[] TAGS = { "PRP$", "NN", "RB", "VBZ", "VBZ", "NN" };
  List<TaggedWord> sentence = SentenceUtils.toTaggedList(Arrays.asList(WORDS), Arrays.asList(TAGS));

  Tree[] correctTrees = { 
    Tree.valueOf("(ROOT (S (NP (PRP$ My) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))"),
    Tree.valueOf("(NP (NP (NN A) (NN B)) (NN C))") , // doesn't have to make sense
    Tree.valueOf("(ROOT (S (NP (PRP$ My) (JJ small) (NN dog)) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))"),
  };
  List<Tree> binarizedTrees; // initialized in setUp

  ReorderingOracle oracle = new ReorderingOracle(new ShiftReduceOptions(), Collections.singleton("ROOT"));
  
  Tree[] incorrectShiftTrees = { 
    Tree.valueOf("(ROOT (S (PRP$ My) (NN dog) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))"),
    Tree.valueOf("(NP (NN A) (NN B) (NN C))") , // doesn't have to make sense
    Tree.valueOf("(ROOT (S (PRP$ My) (JJ small) (NN dog) (ADVP (RB also)) (VP (VBZ likes) (S (VP (VBG eating) (NP (NN sausage))))) (. .)))"),
  };

  Debinarizer debinarizer = new Debinarizer(false);

  public void setUp() {
    Options op = new Options();
    Treebank treebank = op.tlpParams.memoryTreebank();
    
    treebank.addAll(Arrays.asList(correctTrees));
    binarizedTrees = ShiftReduceParser.binarizeTreebank(treebank, op);
  }
  
  public List<Transition> buildTransitionList(Transition ... transitions) {
    return Generics.newLinkedList(Arrays.asList(transitions));
  }

  public void testReorderIncorrectBinaryTransition() {
    List<Transition> transitions = buildTransitionList(shift, rightNP, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectBinaryTransition(transitions));
    assertEquals(buildTransitionList(shift, rightVP, finalize), transitions);

    transitions = buildTransitionList(shift, unaryADVP, rightNP, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectBinaryTransition(transitions));
    assertEquals(buildTransitionList(shift, unaryADVP, rightVP, finalize), transitions);    

    transitions = buildTransitionList(shift, rightNP, unaryADVP, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectBinaryTransition(transitions));
    assertEquals(buildTransitionList(shift, rightVP, finalize), transitions);    
  }

  public void testReorderIncorrectShiftResultingTree() {
    for (int testcase = 0; testcase < correctTrees.length; ++testcase) {
      State state = ShiftReduceParser.initialStateFromGoldTagTree(correctTrees[testcase]);
      List<Transition> gold = CreateTransitionSequence.createTransitionSequence(binarizedTrees.get(testcase));
      // System.err.println(correctTrees[testcase]);
      // System.err.println(gold);

      int tnum = 0;
      for (; tnum < gold.size(); ++tnum) {
        if (gold.get(tnum) instanceof BinaryTransition) {
          break;
        }
        state = gold.get(tnum).apply(state);
      }
      state = shift.apply(state);
      List<Transition> reordered = Generics.newLinkedList(gold.subList(tnum, gold.size()));
      assertTrue(oracle.reorderIncorrectShiftTransition(reordered));
      // System.err.println(reordered);
      for (Transition transition : reordered) {
        state = transition.apply(state);
      }
      Tree debinarized = debinarizer.transformTree(state.stack.peek());
      // System.err.println(debinarized);
      assertEquals(incorrectShiftTrees[testcase].toString(), debinarized.toString());
    }
  }

  public void testReorderIncorrectShift() {
    List<Transition> transitions = buildTransitionList(rightNP, shift, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(tempRightVP, rightVP, finalize), transitions);

    transitions = buildTransitionList(rightNP, shift, shift, leftNP, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(shift, leftNP, tempRightVP, rightVP, finalize), transitions);

    transitions = buildTransitionList(rightNP, shift, unaryADVP, shift, leftNP, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(unaryADVP, shift, leftNP, tempRightVP, rightVP, finalize), transitions);

    transitions = buildTransitionList(rightNP, shift, shift, unaryADVP, leftNP, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(shift, unaryADVP, leftNP, tempRightVP, rightVP, finalize), transitions);

    transitions = buildTransitionList(leftNP, shift, shift, unaryADVP, leftNP, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(shift, unaryADVP, leftNP, tempRightVP, rightVP, finalize), transitions);

    transitions = buildTransitionList(leftNP, shift, shift, unaryADVP, leftNP, leftVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(shift, unaryADVP, leftNP, tempLeftVP, leftVP, finalize), transitions);

    transitions = buildTransitionList(rightNP, shift, shift, unaryADVP, leftNP, leftVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(shift, unaryADVP, leftNP, tempLeftVP, rightVP, finalize), transitions);

    transitions = buildTransitionList(leftNP, leftNP, shift, shift, unaryADVP, leftNP, rightVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(shift, unaryADVP, leftNP, tempRightVP, tempRightVP, rightVP, finalize), transitions);

    transitions = buildTransitionList(leftNP, rightNP, shift, shift, unaryADVP, leftNP, leftVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(shift, unaryADVP, leftNP, tempLeftVP, tempLeftVP, rightVP, finalize), transitions);

    transitions = buildTransitionList(leftNP, leftNP, shift, shift, unaryADVP, leftNP, leftVP, finalize);
    assertTrue(oracle.reorderIncorrectShiftTransition(transitions));
    assertEquals(buildTransitionList(shift, unaryADVP, leftNP, tempLeftVP, tempLeftVP, leftVP, finalize), transitions);
  }
}
