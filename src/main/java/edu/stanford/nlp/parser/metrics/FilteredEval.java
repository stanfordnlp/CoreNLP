package edu.stanford.nlp.parser.metrics;

import java.util.Set;

import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.ConstituentFactory;
import edu.stanford.nlp.trees.LabeledScoredConstituentFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreeFilters;
import java.util.function.Predicate;
import edu.stanford.nlp.util.Generics;

/**
 * An AbstractEval which doesn't just evaluate all constituents, but
 * lets you provide a filter to only pay attention to constituents
 * formed from certain subtrees.  For example, one provided filter
 * lets you limit the evaluation to subtrees which contain a
 * particular kind of node.
 *
 * @author John Bauer
 */
public class FilteredEval extends AbstractEval {
  Predicate<Tree> subtreeFilter;

  private final ConstituentFactory cf = new LabeledScoredConstituentFactory();

  public FilteredEval(String str, boolean runningAverages, Predicate<Tree> subtreeFilter) {
    super(str, runningAverages);
    this.subtreeFilter = subtreeFilter;
  }

  protected Set<?> makeObjects(Tree tree) {
    Set<Constituent> set = Generics.newHashSet();
    if (tree != null) {
      set.addAll(tree.constituents(cf, false, subtreeFilter));
    }
    return set;
    
  }

  /**
   * Returns an eval which is good for counting the attachment of
   * specific node types.  For example, suppose you want to count the
   * attachment of PP in an English parsing.  You could create one
   * with PP as the child pattern, and then it would give you p/r/f1
   * for just nodes which have a PP as a child.
   */
  public static FilteredEval childFilteredEval(String str, boolean runningAverages, TreebankLanguagePack tlp, String childPattern) {
    return new FilteredEval(str, runningAverages, new TreeFilters.HasMatchingChild(tlp, childPattern));
  }

}
