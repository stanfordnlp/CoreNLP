package edu.stanford.nlp.parser.lexparser;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.parser.common.NoSuchParseException;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.ScoredComparator;
import edu.stanford.nlp.util.ScoredObject;

/**
 * Rerank trees from the ParserQuery based on scores from a Reranker.
 * <br>
 * TODO: should handle Factored parsers as well
 *
 * @author John Bauer
 */
public class RerankingParserQuery implements ParserQuery {
  private final Options op;
  private final ParserQuery parserQuery;
  private final Reranker reranker;
  private final int rerankerKBest;

  private List<ScoredObject<Tree>> scoredTrees;

  /**
   * Data for this particular query stored by the Reranker will be
   * stored in this object
   */
  private RerankerQuery rerankerQuery;

  public RerankingParserQuery(Options op, ParserQuery parserQuery, Reranker reranker) {
    this.op = op;
    this.parserQuery = parserQuery;
    this.reranker = reranker;
    this.rerankerKBest = op.rerankerKBest;
  }

  @Override
  public boolean saidMemMessage() {
    return parserQuery.saidMemMessage();
  }

  @Override
  public void setConstraints(List<ParserConstraint> constraints) {
    parserQuery.setConstraints(constraints);
  }

  @Override
  public boolean parse(List<? extends HasWord> sentence) {
    boolean success = parserQuery.parse(sentence);
    if (!success) {
      return false;
    }

    List<ScoredObject<Tree>> bestKParses = parserQuery.getKBestPCFGParses(rerankerKBest);
    if (bestKParses.isEmpty()) {
      return false;
    }
    scoredTrees = rerank(sentence, bestKParses);
    return true;
  }

  @Override
  public boolean parseAndReport(List<? extends HasWord> sentence, PrintWriter pwErr) {
    boolean success = parserQuery.parseAndReport(sentence, pwErr);
    if (!success) {
      return false;
    }

    List<ScoredObject<Tree>> bestKParses = parserQuery.getKBestPCFGParses(rerankerKBest);
    if (bestKParses.isEmpty()) {
      return false;
    }
    scoredTrees = rerank(sentence, bestKParses);
    return true;
  }

  List<ScoredObject<Tree>> rerank(List<? extends HasWord> sentence, List<ScoredObject<Tree>> bestKParses) {
    this.rerankerQuery = reranker.process(sentence);

    List<ScoredObject<Tree>> reranked = new ArrayList<>();
    for (ScoredObject<Tree> scoredTree : bestKParses) {
      double score = scoredTree.score();
      try {
        score = op.baseParserWeight * score + rerankerQuery.score(scoredTree.object());
      } catch (NoSuchParseException e) {
        score = Double.NEGATIVE_INFINITY;
      }
      reranked.add(new ScoredObject<>(scoredTree.object(), score));
    }
    Collections.sort(reranked, ScoredComparator.DESCENDING_COMPARATOR);
    return reranked;
  }

  @Override
  public Tree getBestParse() {
    if (scoredTrees == null || scoredTrees.isEmpty()) {
      return null;
    }
    return scoredTrees.get(0).object();
  }

  @Override
  public List<ScoredObject<Tree>> getKBestParses(int k) {
    return this.getKBestPCFGParses(k);
  }

  @Override
  public double getBestScore() {
    return this.getPCFGScore();
  }

  @Override
  public Tree getBestPCFGParse() {
    return getBestParse();
  }

  @Override
  public double getPCFGScore() {
    if (scoredTrees == null || scoredTrees.isEmpty()) {
      throw new AssertionError();
    }
    return scoredTrees.get(0).score();
  }

  @Override
  public Tree getBestDependencyParse(boolean debinarize) {
    // TODO: barf?
    return null;
  }

  @Override
  public Tree getBestFactoredParse() {
    // TODO: barf?
    return null;
  }

  @Override
  public List<ScoredObject<Tree>> getBestPCFGParses() {
    if (scoredTrees == null || scoredTrees.isEmpty()) {
      throw new AssertionError();
    }
    List<ScoredObject<Tree>> equalTrees = Generics.newArrayList();
    double score = scoredTrees.get(0).score();
    int treePos = 0;
    while (treePos < scoredTrees.size() && scoredTrees.get(treePos).score() == score) {
      equalTrees.add(scoredTrees.get(treePos));
    }
    return equalTrees;
  }

  @Override
  public void restoreOriginalWords(Tree tree) {
    parserQuery.restoreOriginalWords(tree);
  }

  @Override
  public boolean hasFactoredParse() {
    return false;
  }

  @Override
  public List<ScoredObject<Tree>> getKBestPCFGParses(int kbestPCFG) {
    List<ScoredObject<Tree>> trees = Generics.newArrayList();
    for (int treePos = 0; treePos < scoredTrees.size() && treePos < kbestPCFG; ++treePos) {
      trees.add(scoredTrees.get(treePos));
    }
    return trees;
  }

  @Override
  public List<ScoredObject<Tree>> getKGoodFactoredParses(int kbest) {
    // TODO: barf?
    return null;
  }

  @Override
  public KBestViterbiParser getPCFGParser() {
    return null;
  }

  @Override
  public KBestViterbiParser getFactoredParser() {
    return null;
  }

  @Override
  public KBestViterbiParser getDependencyParser() {
    return null;
  }


  /**
   * Parsing succeeded without any horrible errors or fallback
   */
  @Override
  public boolean parseSucceeded() {
    return parserQuery.parseSucceeded();
  }

  /**
   * The sentence was skipped, probably because it was too long or of length 0
   */
  @Override
  public boolean parseSkipped() {
    return parserQuery.parseSkipped();
  }

  /**
   * The model had to fall back to a simpler model on the previous parse
   */
  @Override
  public boolean parseFallback() {
    return parserQuery.parseFallback();
  }

  /**
   * The model ran out of memory on the most recent parse
   */
  @Override
  public boolean parseNoMemory() {
    return parserQuery.parseNoMemory();
  }

  /**
   * The model could not parse the most recent sentence for some reason
   */
  @Override
  public boolean parseUnparsable() {
    return parserQuery.parseUnparsable();
  }

  @Override
  public List<? extends HasWord> originalSentence() {
    return parserQuery.originalSentence();
  }

  public RerankerQuery rerankerQuery() {
    return rerankerQuery;
  }
}
