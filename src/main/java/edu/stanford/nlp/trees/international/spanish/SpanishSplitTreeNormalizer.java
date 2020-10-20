package edu.stanford.nlp.trees.international.spanish;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;

/**
 * A tree normalizer made to be used immediately on trees which have
 * been split apart.
 *
 * This is used in AnCora processing in order to fix some common
 * problems with splitting multi-sentence trees.
 *
 * @author Jon Gauthier
 */
public class SpanishSplitTreeNormalizer extends SpanishTreeNormalizer {

  private static final TregexPattern nonsensicalClauseRewrite =
    TregexPattern.compile("sentence=sentence < (S=S !$ /^[^f]/)");
  private static final TsurgeonPattern eraseClause = Tsurgeon.parseOperation("excise S S");

  private static final long serialVersionUID = -3237606914912983720L;

  @Override
  public Tree normalizeWholeTree(Tree tree, TreeFactory tf, boolean expandElisions, boolean expandConmigo) {
    tree = super.normalizeWholeTree(tree, tf, expandElisions, expandConmigo);
    tree = Tsurgeon.processPattern(nonsensicalClauseRewrite, eraseClause, tree);
    return tree;
  }

}
