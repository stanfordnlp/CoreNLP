package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.ling.SentenceUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.Tree;

public class ShiftReduceParserITest {
  private static ShiftReduceParser englishParser = null;
  private static MaxentTagger englishTagger = null;

  @Before
  public void setUp() {
    synchronized(ShiftReduceParserITest.class) {
      if (englishParser == null) {
        englishParser = ShiftReduceParser.loadModel("edu/stanford/nlp/models/srparser/englishSR.ser.gz");
        englishTagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words-distsim.tagger");
      }
    }
  }

  @Test
  public void testSimpleParse() {
    List<CoreLabel> sentence = SentenceUtils.toCoreLabelList("This", "is", "a", "simple", "test", ".");
    englishTagger.tagCoreLabels(sentence);
    Tree result = englishParser.apply(sentence);
    // just care that it didn't crash
  }

  @Test
  public void testBasicConstraint() {
    List<CoreLabel> sentence = SentenceUtils.toCoreLabelList("It", "was", "Carolina", "Reapers", ".");
    englishTagger.tagCoreLabels(sentence);
    Tree result = englishParser.apply(sentence);
    // pretty much need to make the test rely on the parser being consistent
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (NP (NNP Carolina) (NNPS Reapers))) (. .)))", result.toString());

    ParserConstraint constraint = new ParserConstraint(2, 4, ".*");
    List<ParserConstraint> constraints = Collections.singletonList(constraint);
    ParserQuery pq = englishParser.parserQuery();
    pq.setConstraints(constraints);
    assertTrue(pq.parse(sentence));
    result = pq.getBestParse();
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (NP (NNP Carolina) (NNPS Reapers))) (. .)))", result.toString());

    constraint = new ParserConstraint(2, 4, "NP");
    constraints = Collections.singletonList(constraint);
    pq = englishParser.parserQuery();
    pq.setConstraints(constraints);
    assertTrue(pq.parse(sentence));
    result = pq.getBestParse();
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (NP (NNP Carolina) (NNPS Reapers))) (. .)))", result.toString());

    // Note that since the constraints are introducing brackets which
    // don't exist, we may get some weird parse results as models
    // change in the future.  The important thing is that the ADJP
    // bracket appears for this test and the VP bracket appears for
    // the next test
    constraint = new ParserConstraint(2, 4, "ADJP");
    constraints = Collections.singletonList(constraint);
    pq = englishParser.parserQuery();
    pq.setConstraints(constraints);
    assertTrue(pq.parse(sentence));
    result = pq.getBestParse();
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (ADJP (NP (NNP Carolina) (NNPS Reapers)))) (. .)))", result.toString());

    constraint = new ParserConstraint(1, 3, "VP");
    constraints = Collections.singletonList(constraint);
    pq = englishParser.parserQuery();
    pq.setConstraints(constraints);
    assertTrue(pq.parse(sentence));
    result = pq.getBestParse();
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (ADJP (NNP Carolina))) (NP (NNPS Reapers)) (. .)))", result.toString());
  }
}
