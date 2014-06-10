package edu.stanford.nlp.parser.lexparser;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
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

  public RerankingParserQuery(Options op, ParserQuery parserQuery, Reranker reranker) {
    this.op = op;
    this.parserQuery = parserQuery;
    this.reranker = reranker;
    this.rerankerKBest = op.rerankerKBest;
  }

  public boolean saidMemMessage() {
    return parserQuery.saidMemMessage();
  }

  public void setConstraints(List<ParserConstraint> constraints) {
    parserQuery.setConstraints(constraints);
  }

  public boolean parse(List<? extends HasWord> sentence) {
    // TODO: do we actually want to return the LPQ's result, or do we
    // only care if we get a result of some kind?
    boolean result = parserQuery.parse(sentence);
    List<ScoredObject<Tree>> bestKParses = parserQuery.getKBestPCFGParses(rerankerKBest);
    scoredTrees = rerank(sentence, bestKParses);
    return result;
  }

  public void parseWithFallback(List<? extends HasWord> sentence, PrintWriter pwErr) {
    parserQuery.parseWithFallback(sentence, pwErr);
    Tree result = parserQuery.getBestParse();
    if (result == null) {
      return;
    }

    List<ScoredObject<Tree>> bestKParses = parserQuery.getKBestPCFGParses(rerankerKBest);
    scoredTrees = rerank(sentence, bestKParses);
  }

  List<ScoredObject<Tree>> rerank(List<? extends HasWord> sentence, List<ScoredObject<Tree>> bestKParses) {
    RerankerQuery rq = reranker.process(sentence);

    List<ScoredObject<Tree>> reranked = new ArrayList<ScoredObject<Tree>>();
    for (ScoredObject<Tree> scoredTree : bestKParses) {
      double score = scoredTree.score();
      try {
        score = op.baseParserWeight * score + rq.score(scoredTree.object());
      } catch (NoSuchParseException e) {
        score = Double.NEGATIVE_INFINITY;
      }
      reranked.add(new ScoredObject<Tree>(scoredTree.object(), score));
    }
    Collections.sort(reranked, ScoredComparator.DESCENDING_COMPARATOR);
    return reranked;
  }

  public Tree getBestParse() {
    if (scoredTrees == null || scoredTrees.size() == 0) {
      return null;
    }
    return scoredTrees.get(0).object();
  }

  public Tree getBestPCFGParse() {
    return getBestParse();
  }

  public double getPCFGScore() {
    if (scoredTrees == null || scoredTrees.size() == 0) {
      throw new AssertionError();
    }
    return scoredTrees.get(0).score();
  }

  public Tree getBestDependencyParse(boolean debinarize) {
    // TODO: barf?
    return null;
  }

  public Tree getBestFactoredParse() {
    // TODO: barf?
    return null;
  }

  public List<ScoredObject<Tree>> getBestPCFGParses() {
    if (scoredTrees == null || scoredTrees.size() == 0) {
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

  public void restoreOriginalWords(Tree tree) {
    parserQuery.restoreOriginalWords(tree);
  }

  public boolean hasFactoredParse() {
    return false;
  }

  public List<ScoredObject<Tree>> getKBestPCFGParses(int kbestPCFG) {
    List<ScoredObject<Tree>> trees = Generics.newArrayList();
    for (int treePos = 0; treePos < scoredTrees.size() && treePos < kbestPCFG; ++treePos) {
      trees.add(scoredTrees.get(treePos));
    }
    return trees;
  }

  public List<ScoredObject<Tree>> getKGoodFactoredParses(int kbest) {
    // TODO: barf?
    return null;
  }

  public KBestViterbiParser getPCFGParser() {
    return null;
  }

  public KBestViterbiParser getFactoredParser() {
    return null;
  }

  public KBestViterbiParser getDependencyParser() {
    return null;
  }


  /**
   * Parsing succeeded without any horrible errors or fallback
   */
  public boolean parseSucceeded() {
    return parserQuery.parseSucceeded();
  }

  /**
   * The sentence was skipped, probably because it was too long or of length 0
   */
  public boolean parseSkipped() {
    return parserQuery.parseSkipped();
  }

  /**
   * The model had to fall back to a simpler model on the previous parse
   */
  public boolean parseFallback() {
    return parserQuery.parseFallback();
  }

  /**
   * The model ran out of memory on the most recent parse
   */
  public boolean parseNoMemory() {
    return parserQuery.parseNoMemory();
  }

  /**
   * The model could not parse the most recent sentence for some reason
   */
  public boolean parseUnparsable() {
    return parserQuery.parseUnparsable();
  }

  public List<? extends HasWord> originalSentence() { 
    return parserQuery.originalSentence();
  }
}
