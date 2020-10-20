package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;

import edu.stanford.nlp.ling.CategoryWordTagFactory;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.util.*;


/** Provides a much faster way to realize the factored
 *  parsing idea, including easily returning "k good" results
 *  at the expense of optimality.  Exploiting the k best functionality
 *  of the ExhaustivePCFGParser, this model simply gets more than
 *  k best PCFG parsers, scores them according to the dependency
 *  grammar, and returns them in terms of their product score.
 *  No actual parsing is done.
 *
 *  @author Christopher Manning
 */
public class FastFactoredParser implements KBestViterbiParser  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(FastFactoredParser.class);

  // TODO Regression tests
  // TODO Set dependency tuning and test whether useful
  // TODO Validate and up the Arabic numbers
  // TODO Make the printing options for k good/best sane
  // TODO Check parsing of a List<String>.  Change defaultSentence() to be List<HasWord>

  protected static final boolean VERBOSE = false;

  protected ExhaustivePCFGParser pparser;
  protected GrammarProjection projection;

  protected MLEDependencyGrammar dg;
  protected Options op;

  private int numToFind;

  private final Index<String> wordIndex;
  private final Index<String> tagIndex;

  protected int project(int state) {
    return projection.project(state);
  }

  /**
   * Return the best parse of the sentence most recently parsed.
   *
   * @return The best (highest score) tree
   */
  public Tree getBestParse() {
    return nGoodTrees.get(0).object();
  }

  public double getBestScore() {
    return nGoodTrees.get(0).score();
  }


  public boolean hasParse() {
    return ! nGoodTrees.isEmpty();
  }


  private List<ScoredObject<Tree>> nGoodTrees = new ArrayList<>();



  /**
   * Return the list of N "good" parses of the sentence most recently parsed.
   * (The first is guaranteed to be the best, but later ones are only
   * guaranteed the best subject to the possibilities that disappear because
   * the PCFG/Dep charts only store the best over each span.)
   *
   * @return The list of N best trees
   */
  public List<ScoredObject<Tree>> getKGoodParses(int k) {
    if (k <= nGoodTrees.size()) {
      return nGoodTrees.subList(0, k);
    } else {
      throw new UnsupportedOperationException("FastFactoredParser: cannot provide " + k + " good parses.");
    }
  }


  /** Use the DependencyGrammar to score the tree.
   *
   * @param tr A binarized tree (as returned by the PCFG parser
   * @return The score for the tree according to the grammar
   */
  private double depScoreTree(Tree tr) {
    // log.info("Here's our tree:");
    // tr.pennPrint();
    // log.info(Trees.toDebugStructureString(tr));
    Tree cwtTree = tr.deepCopy(new LabeledScoredTreeFactory(), new CategoryWordTagFactory());
    cwtTree.percolateHeads(binHeadFinder);
    // log.info("Here's what it went to:");
    // cwtTree.pennPrint();
    List<IntDependency> deps = MLEDependencyGrammar.treeToDependencyList(cwtTree, wordIndex, tagIndex);
    // log.info("Here's the deps:\n" + deps);
    return dg.scoreAll(deps);
  }

  private final HeadFinder binHeadFinder = new BinaryHeadFinder();

   /**
   * Parse a Sentence.  It is assumed that when this is called, the pparser
   * has already been called to parse the sentence.
   *
   * @param words The list of words to parse.
   * @return true iff it could be parsed
   */
  public boolean parse(List<? extends HasWord> words) {
    nGoodTrees.clear();

    int numParsesToConsider = numToFind * op.testOptions.fastFactoredCandidateMultiplier + op.testOptions.fastFactoredCandidateAddend;
    if (pparser.hasParse()) {
      List<ScoredObject<Tree>> pcfgBest = pparser.getKBestParses(numParsesToConsider);
      Beam<ScoredObject<Tree>> goodParses = new Beam<>(numToFind);

      for (ScoredObject<Tree> candidate : pcfgBest) {
        if (Thread.interrupted()) {
          throw new RuntimeInterruptedException();
        }
        double depScore = depScoreTree(candidate.object());
        ScoredObject<Tree> x = new ScoredObject<>(candidate.object(), candidate.score() + depScore);
        goodParses.add(x);
      }
      nGoodTrees = goodParses.asSortedList();
    }
    return ! nGoodTrees.isEmpty();
  }

  /** Get the exact k best parses for the sentence.
   *
   *  @param k The number of best parses to return
   *  @return The exact k best parses for the sentence, with
   *         each accompanied by its score (typically a
   *         negative log probability).
   */
  public List<ScoredObject<Tree>> getKBestParses(int k) {
    throw new UnsupportedOperationException();
  }


  /** Get a complete set of the maximally scoring parses for a sentence,
   *  rather than one chosen at random.  This set may be of size 1 or larger.
   *
   *  @return All the equal best parses for a sentence, with each
   *         accompanied by its score
   */
  public List<ScoredObject<Tree>> getBestParses() {
    throw new UnsupportedOperationException();
  }

  /** Get k parse samples for the sentence.  It is expected that the
   *  parses are sampled based on their relative probability.
   *
   *  @param k The number of sampled parses to return
   *  @return A list of k parse samples for the sentence, with
   *         each accompanied by its score
   */
  public List<ScoredObject<Tree>> getKSampledParses(int k) {
    throw new UnsupportedOperationException();
  }


  FastFactoredParser(ExhaustivePCFGParser pparser, MLEDependencyGrammar dg, Options op, int numToFind, Index<String> wordIndex, Index<String> tagIndex) {
    this(pparser, dg, op, numToFind, new NullGrammarProjection(null, null), wordIndex, tagIndex);
  }

  FastFactoredParser(ExhaustivePCFGParser pparser, MLEDependencyGrammar dg, Options op, int numToFind, GrammarProjection projection, Index<String> wordIndex, Index<String> tagIndex) {
    this.pparser = pparser;
    this.projection = projection;
    this.dg = dg;
    this.op = op;
    this.numToFind = numToFind;
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
  }

} // end class FastFactoredParser
