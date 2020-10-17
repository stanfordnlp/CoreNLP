package edu.stanford.nlp.parser.shiftreduce;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;



public class ShiftReduceUtilsTest extends TestCase {
  public void testBinarySide() {
    String[] words = { "This", "is", "a", "short", "test", "." };
    String[] tags = { "DT", "VBZ", "DT", "JJ", "NN", "." };
    assertEquals(words.length, tags.length);
    List<TaggedWord> sentence = SentenceUtils.toTaggedList(Arrays.asList(words), Arrays.asList(tags));
    State state = ShiftReduceParser.initialStateFromTaggedSentence(sentence);

    ShiftTransition shift = new ShiftTransition();
    state = shift.apply(shift.apply(state));

    BinaryTransition transition = new BinaryTransition("NP", BinaryTransition.Side.RIGHT, false);
    State next = transition.apply(state);
    assertEquals(BinaryTransition.Side.RIGHT, ShiftReduceUtils.getBinarySide(next.stack.peek()));

    transition = new BinaryTransition("NP", BinaryTransition.Side.LEFT, false);
    next = transition.apply(state);
    assertEquals(BinaryTransition.Side.LEFT, ShiftReduceUtils.getBinarySide(next.stack.peek()));
  }

}
