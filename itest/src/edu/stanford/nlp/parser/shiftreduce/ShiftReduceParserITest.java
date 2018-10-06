package edu.stanford.nlp.parser.shiftreduce;

import edu.stanford.nlp.ling.SentenceUtils;
import junit.framework.TestCase;

import java.util.Collections;
import java.util.List;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.Tree;

public class ShiftReduceParserITest extends TestCase {
  private static ShiftReduceParser englishParser = null;
  private static MaxentTagger englishTagger = null;

  @Override
  public void setUp() {
    synchronized(ShiftReduceParserITest.class) {
      if (englishParser == null) {
        englishParser = ShiftReduceParser.loadModel("edu/stanford/nlp/models/srparser/englishSR.ser.gz");
        englishTagger = new MaxentTagger("edu/stanford/nlp/models/pos-tagger/english-left3words/english-left3words-distsim.tagger");
      }
    }
  }

  public void testSimpleParse() {
    List<CoreLabel> sentence = SentenceUtils.toCoreLabelList("This", "is", "a", "simple", "test", ".");
    englishTagger.tagCoreLabels(sentence);
    Tree result = englishParser.apply(sentence);
    // just care that it didn't crash
  }

  public void testBasicConstraint() {
    List<CoreLabel> sentence = SentenceUtils.toCoreLabelList("It", "was", "Carolina", "Reapers", ".");
    englishTagger.tagCoreLabels(sentence);
    Tree result = englishParser.apply(sentence);
    // pretty much need to make the test rely on the parser being consistent
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (NP (NNP Carolina) (NNP Reapers))) (. .)))", result.toString());

    ParserConstraint constraint = new ParserConstraint(2, 4, ".*");
    List<ParserConstraint> constraints = Collections.singletonList(constraint);
    ParserQuery pq = englishParser.parserQuery();
    pq.setConstraints(constraints);
    assertTrue(pq.parse(sentence));
    result = pq.getBestParse();
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (NP (NNP Carolina) (NNP Reapers))) (. .)))", result.toString());

    constraint = new ParserConstraint(2, 4, "NP");
    constraints = Collections.singletonList(constraint);
    pq = englishParser.parserQuery();
    pq.setConstraints(constraints);
    assertTrue(pq.parse(sentence));
    result = pq.getBestParse();
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (NP (NNP Carolina) (NNP Reapers))) (. .)))", result.toString());

    constraint = new ParserConstraint(2, 4, "ADJP");
    constraints = Collections.singletonList(constraint);
    pq = englishParser.parserQuery();
    pq.setConstraints(constraints);
    assertTrue(pq.parse(sentence));
    result = pq.getBestParse();
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (ADJP (NP (NNP Carolina) (NNP Reapers)))) (. .)))", result.toString());

    constraint = new ParserConstraint(1, 3, "VP");
    constraints = Collections.singletonList(constraint);
    pq = englishParser.parserQuery();
    pq.setConstraints(constraints);
    assertTrue(pq.parse(sentence));
    result = pq.getBestParse();
    System.out.println(result.toString());
    assertEquals("(ROOT (S (NP (PRP It)) (VP (VBD was) (NP (NNP Carolina))) (NP (NNP Reapers)) (. .)))", result.toString());
  }
}
