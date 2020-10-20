package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.ling.SentenceUtils;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.CollectionUtils;
import java.util.function.Function;

public class ShiftReduceParserTest extends TestCase {
  String commaTreeString = "(ROOT (FRAG (NP (DT A) (@NP (ADJP (JJ short) (@ADJP (, ,) (JJ simple))) (NN test)))))";

  String[] treeStrings = {
    "(ROOT (S (INTJ (RB No)) (@S (, ,) (@S (NP (PRP it)) (@S (VP (@VP (VBD was) (RB n't)) (NP (NNP Black) (NNP Monday))) (. .))))) (.$$. .$.))",
    "(ROOT (S (CC But) (@S (SBAR (IN while) (S (NP (DT the) (@NP (NNP New) (@NP (NNP York) (@NP (NNP Stock) (NNP Exchange))))) (VP (@VP (VBD did) (RB n't)) (VP (@VP (@VP (VB fall) (ADVP (RB apart))) (NP (NNP Friday))) (SBAR (IN as) (S (NP (DT the) (@NP (NNP Dow) (@NP (NNP Jones) (@NP (NNP Industrial) (NNP Average))))) (VP (VBD plunged) (NP (NP (CD 190.58) (NNS points)) (PRN (: --) (@PRN (NP (@NP (NP (JJS most)) (PP (IN of) (NP (PRP it)))) (PP (IN in) (NP (DT the) (@NP (JJ final) (NN hour))))) (: --))))))))))) (@S (NP (PRP it)) (@S (ADVP (RB barely)) (@S (VP (VBD managed) (S (VP (TO to) (VP (VB stay) (NP (NP (DT this) (NN side)) (PP (IN of) (NP (NN chaos)))))))) (. .)))))) (.$$. .$.))",
    "(ROOT (S (NP (NP (DT Some) (@NP (`` ``) (@NP (NN circuit) (@NP (NNS breakers) ('' ''))))) (VP (VBN installed) (PP (IN after) (NP (DT the) (@NP (NNP October) (@NP (CD 1987) (NN crash))))))) (@S (VP (@VP (@VP (VBD failed) (NP (PRP$ their) (@NP (JJ first) (NN test)))) (PRN (, ,) (@PRN (S (NP (NNS traders)) (VP (VBP say))) (, ,)))) (S (ADJP (JJ unable) (S (VP (TO to) (VP (VB cool) (NP (NP (DT the) (@NP (NN selling) (NN panic))) (PP (IN in) (NP (DT both) (@NP (@NP (NNS stocks) (CC and)) (NNS futures))))))))))) (. .))) (.$$. .$.))",
    "(ROOT (S (NP (SBAR foo))))",
    commaTreeString,
  };


  /**
   * Test that the entire transition process is working: get the
   * transitions from a few trees, start an empty state from those
   * trees, and verify that running the transitions on those states
   * gets back the correct tree.  Runs the test with unary transitions
   */
  public void testUnaryTransitions() {
    for (String treeText : treeStrings) {
      Tree tree = convertTree(treeText);
      List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree, false, Collections.singleton("ROOT"), Collections.singleton("ROOT"));
      State state = ShiftReduceParser.initialStateFromGoldTagTree(tree);
      for (Transition transition : transitions) {
        state = transition.apply(state);
      }
      assertEquals(tree, state.stack.peek());
    }
  }

  /**
   * Same thing, but with compound unary transitions
   */
  public void testCompoundUnaryTransitions() {
    for (String treeText : treeStrings) {
      Tree tree = convertTree(treeText);
      List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree, true, Collections.singleton("ROOT"), Collections.singleton("ROOT"));
      State state = ShiftReduceParser.initialStateFromGoldTagTree(tree);
      for (Transition transition : transitions) {
        state = transition.apply(state);
      }
      assertEquals(tree, state.stack.peek());
    }
  }

  Tree convertTree(String treeText) {
    Options op = new Options();
    HeadFinder binaryHeadFinder = new BinaryHeadFinder(op.tlpParams.headFinder());
    Tree tree = Tree.valueOf(treeText);
    Trees.convertToCoreLabels(tree);
    tree.percolateHeadAnnotations(binaryHeadFinder);
    return tree;
  }

  public void testSeparators() {
    Tree tree = convertTree(commaTreeString);
    List<Transition> transitions = CreateTransitionSequence.createTransitionSequence(tree, true, Collections.singleton("ROOT"), Collections.singleton("ROOT"));
    List<String> expectedTransitions = Arrays.asList(new String[] { "Shift", "Shift", "Shift", "Shift", "RightBinary(@ADJP)", "RightBinary(ADJP)", "Shift", "RightBinary(@NP)", "RightBinary(NP)", "CompoundUnary*([ROOT, FRAG])", "Finalize", "Idle" });
    assertEquals(expectedTransitions, CollectionUtils.transformAsList(transitions, (Transition t)->{ return t.toString();}));

    String expectedSeparators = "[{2=,}]";

    State state = ShiftReduceParser.initialStateFromGoldTagTree(tree);
    assertEquals(1, state.separators.size());
    assertEquals(2, state.separators.firstKey().intValue());
    assertEquals(",", state.separators.get(2));
  }

  public void testInitialStateFromTagged() {
    String[] words = { "This", "is", "a", "short", "test", "." };
    String[] tags = { "DT", "VBZ", "DT", "JJ", "NN", "." };
    assertEquals(words.length, tags.length);
    List<TaggedWord> sentence = SentenceUtils.toTaggedList(Arrays.asList(words), Arrays.asList(tags));
    State state = ShiftReduceParser.initialStateFromTaggedSentence(sentence);
    for (int i = 0; i < words.length; ++i) {
      assertEquals(tags[i], state.sentence.get(i).value());
      assertEquals(1, state.sentence.get(i).children().length);
      assertEquals(words[i], state.sentence.get(i).children()[0].value());
    }
  }

  public void binarize() {
    // TreeBinarizer binarizer = new TreeBinarizer(new PennTreebankLanguagePack().headFinder(), new PennTreebankLanguagePack(),
    //                                             false, false, 0, false, false, 0.0, false, true, true);
    // Tree tree = Tree.valueOf(commas);
    // Trees.convertToCoreLabels(tree);
    // Tree binarized = binarizer.transformTree(tree);
    // System.err.println(binarized);
  }
}
