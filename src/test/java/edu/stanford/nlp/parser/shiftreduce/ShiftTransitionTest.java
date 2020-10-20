package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.ling.SentenceUtils;
import junit.framework.TestCase;

import edu.stanford.nlp.ling.TaggedWord;

import java.util.Arrays;
import java.util.List;

/**
 * Test a couple transition operations and their effects
 *
 * @author John Bauer
 */
public class ShiftTransitionTest extends TestCase {
  // TODO: add test for isLegal

  public void testTransition() {
    String[] words = { "This", "is", "a", "short", "test", "." };
    String[] tags = { "DT", "VBZ", "DT", "JJ", "NN", "." };
    assertEquals(words.length, tags.length);
    List<TaggedWord> sentence = SentenceUtils.toTaggedList(Arrays.asList(words), Arrays.asList(tags));
    State state = ShiftReduceParser.initialStateFromTaggedSentence(sentence);

    ShiftTransition shift = new ShiftTransition();
    for (int i = 0; i < 3; ++i) {
      state = shift.apply(state);
    }
    assertEquals(3, state.tokenPosition);
  }
}
