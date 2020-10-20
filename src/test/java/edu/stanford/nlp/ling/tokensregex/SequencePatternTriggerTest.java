package edu.stanford.nlp.ling.tokensregex;

import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.util.CoreMap;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tests triggering of sequence patterns
 *
 * @author Angel Chang
 */
public class SequencePatternTriggerTest extends TestCase {

  public void testSimpleTrigger() throws Exception {
    List<TokenSequencePattern> patterns = new ArrayList<TokenSequencePattern>();
    patterns.add(TokenSequencePattern.compile("which word should be matched"));

    MultiPatternMatcher.SequencePatternTrigger<CoreMap> trigger =
        new MultiPatternMatcher.BasicSequencePatternTrigger<CoreMap>(
          new CoreMapNodePatternTrigger(patterns));

    Collection<SequencePattern<CoreMap>> triggered = trigger.apply(SentenceUtils.toCoreLabelList("one", "two", "three"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "should", "be", "matched"));
    assertEquals(1, triggered.size());
  }

  public void testOptionalTrigger() throws Exception {
    List<TokenSequencePattern> patterns = new ArrayList<TokenSequencePattern>();
    patterns.add(TokenSequencePattern.compile("which word should? be matched"));

    MultiPatternMatcher.SequencePatternTrigger<CoreMap> trigger =
        new MultiPatternMatcher.BasicSequencePatternTrigger<CoreMap>(
            new CoreMapNodePatternTrigger(patterns));

    Collection<SequencePattern<CoreMap>> triggered = trigger.apply(SentenceUtils.toCoreLabelList("one", "two", "three"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("matched"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("should"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "be"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "be", "matched"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "should", "be", "matched"));
    assertEquals(1, triggered.size());
  }

  public void testOptionalTrigger2() throws Exception {
    List<TokenSequencePattern> patterns = new ArrayList<TokenSequencePattern>();
    patterns.add(TokenSequencePattern.compile("which word should? be matched?"));

    MultiPatternMatcher.SequencePatternTrigger<CoreMap> trigger =
      new MultiPatternMatcher.BasicSequencePatternTrigger<CoreMap>(
        new CoreMapNodePatternTrigger(patterns));

    Collection<SequencePattern<CoreMap>> triggered = trigger.apply(SentenceUtils.toCoreLabelList("one", "two", "three"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("matched"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("should"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "be"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "should", "be", "matched"));
    assertEquals(1, triggered.size());
  }

  public void testOptionalTrigger3() throws Exception {
    List<TokenSequencePattern> patterns = new ArrayList<TokenSequencePattern>();
    patterns.add(TokenSequencePattern.compile("which word ( should | would ) be matched?"));

    MultiPatternMatcher.SequencePatternTrigger<CoreMap> trigger =
      new MultiPatternMatcher.BasicSequencePatternTrigger<CoreMap>(
        new CoreMapNodePatternTrigger(patterns));

    Collection<SequencePattern<CoreMap>> triggered = trigger.apply(SentenceUtils.toCoreLabelList("one", "two", "three"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("matched"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("should"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "be"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "should", "be", "matched"));
    assertEquals(1, triggered.size());
  }

  public void testOptionalTrigger4() throws Exception {
    List<TokenSequencePattern> patterns = new ArrayList<TokenSequencePattern>();
    patterns.add(TokenSequencePattern.compile("which word should? be matched{1,2}"));

    MultiPatternMatcher.SequencePatternTrigger<CoreMap> trigger =
      new MultiPatternMatcher.BasicSequencePatternTrigger<CoreMap>(
        new CoreMapNodePatternTrigger(patterns));

    Collection<SequencePattern<CoreMap>> triggered = trigger.apply(SentenceUtils.toCoreLabelList("one", "two", "three"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("matched"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("should"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "be"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "be", "matched"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "should", "be", "matched"));
    assertEquals(1, triggered.size());
  }

  public void testOptionalTrigger5() throws Exception {
    List<TokenSequencePattern> patterns = new ArrayList<TokenSequencePattern>();
    patterns.add(TokenSequencePattern.compile("which word should? be matched{1,8}"));

    MultiPatternMatcher.SequencePatternTrigger<CoreMap> trigger =
      new MultiPatternMatcher.BasicSequencePatternTrigger<CoreMap>(
        new CoreMapNodePatternTrigger(patterns));

    Collection<SequencePattern<CoreMap>> triggered = trigger.apply(SentenceUtils.toCoreLabelList("one", "two", "three"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("matched"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("should"));
    assertEquals(0, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "be"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "be", "matched"));
    assertEquals(1, triggered.size());

    triggered = trigger.apply(SentenceUtils.toCoreLabelList("which", "word", "should", "be", "matched"));
    assertEquals(1, triggered.size());
  }

}
