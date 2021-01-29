package edu.stanford.nlp.parser.shiftreduce;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.ling.TaggedWord;

public class RemoveUnaryTransitionTest extends TestCase {
  public void testIsLegal() {
    String[] words = { "This", "is", "a", "short", "test", "." };
    String[] tags = { "DT", "VBZ", "DT", "JJ", "NN", "." };
    assertEquals(words.length, tags.length);
    List<TaggedWord> sentence = SentenceUtils.toTaggedList(Arrays.asList(words), Arrays.asList(tags));
    State state = ShiftReduceParser.initialStateFromTaggedSentence(sentence);

    ShiftTransition shift = new ShiftTransition();
    state = shift.apply(state);
    RemoveUnaryTransition removeNP = new RemoveUnaryTransition("NP");
    RemoveUnaryTransition removeVP = new RemoveUnaryTransition("VP");
    assertFalse(removeNP.isLegal(state, null));
    assertFalse(removeVP.isLegal(state, null));

    UnaryTransition unary = new UnaryTransition("NP", false);
    state = unary.apply(state);
    assertFalse(removeNP.isLegal(state, null));
    assertFalse(removeVP.isLegal(state, null));

    state = shift.apply(state);
    assertTrue(removeNP.isLegal(state, null));
    assertFalse(removeVP.isLegal(state, null));
  }
}
