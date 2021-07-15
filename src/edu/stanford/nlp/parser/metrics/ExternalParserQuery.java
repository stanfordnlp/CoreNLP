/**
 * Wraps a parse given to the scoring system from an external parser
 */

package edu.stanford.nlp.parser.metrics;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.parser.KBestViterbiParser;
import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.ScoredObject;

public class ExternalParserQuery implements ParserQuery  {
  final List<? extends HasWord> originalSentence;
  final List<ScoredObject<Tree>> results;
  final boolean success;

  public ExternalParserQuery(List<? extends HasWord> sentence, List<ScoredObject<Tree>> results) {
    this.originalSentence = sentence;
    this.results = results;
    this.success = (results != null);
  }

  @Override
  public boolean parse(List<? extends HasWord> sentence) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean parseAndReport(List<? extends HasWord> sentence, PrintWriter pwErr) {
    return parse(sentence);
  }

  @Override
  public double getPCFGScore() {
    if (results == null) {
      throw new IllegalStateException("getPCFGScore called before a sentence has been parsed");
    }
    return results.get(0).score();
  }

  @Override
  public Tree getBestParse() {
    if (results == null) {
      throw new IllegalStateException("getPCFGScore called before a sentence has been parsed");
    }
    return results.get(0).object();
  }

  @Override
  public List<ScoredObject<Tree>> getKBestParses(int k) {
    if (results == null) {
      throw new IllegalStateException("getPCFGScore called before a sentence has been parsed");
    }
    if (results.size() > k) {
      return results.subList(0, k);
    } else {
      return results;
    }
  }

  @Override
  public double getBestScore() {
    return getPCFGScore();
  }

  @Override
  public Tree getBestPCFGParse() {
    return getBestParse();
  }

  @Override
  public Tree getBestDependencyParse(boolean debinarize) {
    return null;
  }

  @Override
  public Tree getBestFactoredParse() {
    return null;
  }

  @Override
  public List<ScoredObject<Tree>> getBestPCFGParses() {
    return results;
  }

  @Override
  public void restoreOriginalWords(Tree tree) {
    if (originalSentence == null || tree == null) {
      return;
    }
    List<Tree> leaves = tree.getLeaves();
    if (leaves.size() != originalSentence.size()) {
      throw new IllegalStateException("originalWords and sentence of different sizes: " + originalSentence.size() + " vs. " + leaves.size() +
                                      "\n Orig: " + SentenceUtils.listToString(originalSentence) +
                                      "\n Pars: " + SentenceUtils.listToString(leaves));
    }
    Iterator<Tree> leafIterator = leaves.iterator();
    for (HasWord word : originalSentence) {
      Tree leaf = leafIterator.next();
      if (!(word instanceof Label)) {
        continue;
      }
      leaf.setLabel((Label) word);
    }
  }

  @Override
  public boolean hasFactoredParse() {
    return false;
  }

  @Override
  public List<ScoredObject<Tree>> getKBestPCFGParses(int kbestPCFG) {
    return getKBestParses(kbestPCFG);
  }

  @Override
  public List<ScoredObject<Tree>> getKGoodFactoredParses(int kbest) {
    throw new UnsupportedOperationException();
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

  @Override
  public void setConstraints(List<ParserConstraint> constraints) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean saidMemMessage() {
    return false;
  }

  /**
   * Parsing succeeded without any horrible errors or fallback
   */
  @Override
  public boolean parseSucceeded() {
    return success;
  }

  /**
   * The sentence was skipped, probably because it was too long or of length 0
   */
  @Override
  public boolean parseSkipped() {
    return false;
  }

  /**
   * The model had to fall back to a simpler model on the previous parse
   */
  @Override
  public boolean parseFallback() {
    return false;
  }

  /**
   * The model ran out of memory on the most recent parse
   */
  @Override
  public boolean parseNoMemory() {
    return false;
  }

  /**
   * The model could not parse the most recent sentence for some reason
   */
  @Override
  public boolean parseUnparsable() {
    return !success;
  }

  @Override
  public List<? extends HasWord> originalSentence() {
    return originalSentence;
  }
}
