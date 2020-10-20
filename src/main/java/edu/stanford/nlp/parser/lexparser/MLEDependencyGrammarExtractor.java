package edu.stanford.nlp.parser.lexparser;

import java.util.*;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.trees.Tree;


/** Gathers statistics on tree dependencies and then passes them to an
 *  MLEDependencyGrammar for dependency grammar construction.
 *
 *  @author Dan Klein
 */
public class MLEDependencyGrammarExtractor extends AbstractTreeExtractor<DependencyGrammar> {

  protected final Index<String> wordIndex;
  protected final Index<String> tagIndex;

  /** This is where all dependencies are stored (using full tag space). */
  protected ClassicCounter<IntDependency> dependencyCounter = new ClassicCounter<>();
  //private Set dependencies = new HashSet();

  protected TreebankLangParserParams tlpParams;

  /** Whether left and right is distinguished. */
  protected boolean directional;

  /** Whether dependent distance from head is distinguished. */
  protected boolean useDistance;

  /** Whether dependent distance is distinguished more coarsely. */
  protected boolean useCoarseDistance;

  /** Whether basic category tags are in the dependency grammar. */
  protected final boolean basicCategoryTagsInDependencyGrammar;

  public MLEDependencyGrammarExtractor(Options op, Index<String> wordIndex, Index<String> tagIndex) {
    super(op);
    this.wordIndex = wordIndex;
    this.tagIndex = tagIndex;
    tlpParams = op.tlpParams;
    directional = op.directional;
    useDistance = op.distance;
    useCoarseDistance = op.coarseDistance;
    basicCategoryTagsInDependencyGrammar = op.trainOptions.basicCategoryTagsInDependencyGrammar;
  }

  @Override
  protected void tallyRoot(Tree lt, double weight) {
    // this list is in full (not reduced) tag space
    List<IntDependency> deps = MLEDependencyGrammar.treeToDependencyList(lt, wordIndex, tagIndex);
    for (IntDependency dependency : deps) {
      dependencyCounter.incrementCount(dependency, weight);
    }
  }

  @Override
  public DependencyGrammar formResult() {
    wordIndex.addToIndex(Lexicon.UNKNOWN_WORD);
    MLEDependencyGrammar dg = new MLEDependencyGrammar(tlpParams, directional, useDistance, useCoarseDistance, basicCategoryTagsInDependencyGrammar, op, wordIndex, tagIndex);
    for (IntDependency dependency : dependencyCounter.keySet()) {
      dg.addRule(dependency, dependencyCounter.getCount(dependency));
    }
    return dg;
  }

} // end class MLEDependencyGrammarExtractor
