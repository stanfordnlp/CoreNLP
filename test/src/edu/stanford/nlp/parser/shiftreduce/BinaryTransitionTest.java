package edu.stanford.nlp.parser.shiftreduce;

import junit.framework.TestCase;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;

import java.util.Arrays;
import java.util.List;

/**
 * Test a couple transition operations and their effects
 *
 * @author John Bauer
 */
public class BinaryTransitionTest extends TestCase {
  // TODO: add more tests for isLegal

  public static State buildState(int shifts) {
    String[] words = { "This", "is", "a", "short", "test", "." };
    String[] tags = { "DT", "VBZ", "DT", "JJ", "NN", "." };
    assertEquals(words.length, tags.length);
    List<TaggedWord> sentence = SentenceUtils.toTaggedList(Arrays.asList(words), Arrays.asList(tags));
    State state = ShiftReduceParser.initialStateFromTaggedSentence(sentence);

    ShiftTransition shift = new ShiftTransition();
    for (int i = 0; i < shifts; ++i) {
      state = shift.apply(state);
    }
    assertEquals(shifts, state.tokenPosition);
    return state;
  }

  public void testLeftTransition() {
    State state = buildState(2);
    BinaryTransition transition = new BinaryTransition("NP", BinaryTransition.Side.LEFT, false);
    assertTrue(transition.isLegal(state, null));
    state = transition.apply(state);
    // should be illegal now that the stack has 1 thing on it
    assertFalse(transition.isLegal(state, null));

    assertEquals(2, state.tokenPosition);
    assertEquals(1, state.stack.size());
    assertEquals(2, state.stack.peek().children().length);
    assertEquals("NP", state.stack.peek().value());
    checkHeads(state.stack.peek(), state.stack.peek().children()[0]);
  }

  public void testRightTransition() {
    State state = buildState(2);
    BinaryTransition transition = new BinaryTransition("NP", BinaryTransition.Side.RIGHT, false);
    assertTrue(transition.isLegal(state, null));
    state = transition.apply(state);
    // should be illegal now that the stack has 1 thing on it
    assertFalse(transition.isLegal(state, null));

    assertEquals(2, state.tokenPosition);
    assertEquals(1, state.stack.size());
    assertEquals(2, state.stack.peek().children().length);
    assertEquals("NP", state.stack.peek().value());
    checkHeads(state.stack.peek(), state.stack.peek().children()[1]);
  }

  public static void checkHeads(Tree t1, Tree t2) {
    assertTrue(t1.label() instanceof CoreLabel);
    assertTrue(t2.label() instanceof CoreLabel);

    CoreLabel l1 = (CoreLabel) t1.label();
    CoreLabel l2 = (CoreLabel) t2.label();

    assertEquals(l1.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class), l2.get(TreeCoreAnnotations.HeadWordLabelAnnotation.class));
    assertEquals(l1.get(TreeCoreAnnotations.HeadTagLabelAnnotation.class), l2.get(TreeCoreAnnotations.HeadTagLabelAnnotation.class));
  }
}
