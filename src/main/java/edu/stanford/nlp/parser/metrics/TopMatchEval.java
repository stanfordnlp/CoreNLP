package edu.stanford.nlp.parser.metrics;

import java.util.Collections;
import java.util.Set;

import edu.stanford.nlp.trees.Constituent;
import edu.stanford.nlp.trees.ConstituentFactory;
import edu.stanford.nlp.trees.LabeledScoredConstituentFactory;
import edu.stanford.nlp.trees.Tree;

/**
 * Measures accuracy by only considering the very top of the parse tree, eg where S, SINV, etc go
 *
 * @author John Bauer
 */
public class TopMatchEval extends AbstractEval {

  private final ConstituentFactory cf;

  public TopMatchEval(String name, boolean runningAverages) {
    super(name, runningAverages);
    cf = new LabeledScoredConstituentFactory();
  }

  @Override
  protected Set<Constituent> makeObjects(Tree tree) {
    if (tree == null) {
      return Collections.emptySet();
    }
    // The eval trees won't have a root level, instead starting with
    // the S/SINV/FRAG/whatever, so just eval at the top level
    Set<Constituent> result = tree.constituents(cf, 0);
    return result;
  }

}

