package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.trees.LeftHeadFinder;
import edu.stanford.nlp.trees.Tree;

/** The purpose of this class is to do the necessary transformations to
 *  parse trees read off the treebank, so that they can be passed to a
 *  <code>MLEDependencyGrammarExtractor</code>.
 * 
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class TransformTreeDependency implements Function<Tree,Tree> {

  TreeAnnotatorAndBinarizer binarizer;
  CollinsPuncTransformer collinsPuncTransformer;
  TrainOptions trainOptions;

  public TransformTreeDependency(TreebankLangParserParams tlpParams, boolean forceCNF, Options op) {
    trainOptions = op.trainOptions;
    if (!trainOptions.leftToRight) {
      binarizer = new TreeAnnotatorAndBinarizer(tlpParams, forceCNF, !trainOptions.outsideFactor(), true, op);
    } else {
      binarizer = new TreeAnnotatorAndBinarizer(tlpParams.headFinder(), new LeftHeadFinder(), tlpParams, forceCNF, !trainOptions.outsideFactor(), true, op);
    }
    if (trainOptions.collinsPunc) {
      collinsPuncTransformer = new CollinsPuncTransformer(tlpParams.treebankLanguagePack());
    }
  }


  public Tree apply(Tree tree) {

    if (trainOptions.hSelSplit) {
      binarizer.setDoSelectiveSplit(false);
      if (trainOptions.collinsPunc) {
        tree = collinsPuncTransformer.transformTree(tree);
      }
      binarizer.transformTree(tree);
      binarizer.setDoSelectiveSplit(true);
    }

    if (trainOptions.collinsPunc) {
      tree = collinsPuncTransformer.transformTree(tree);
    }
    tree = binarizer.transformTree(tree);
    return tree;
  }

}
