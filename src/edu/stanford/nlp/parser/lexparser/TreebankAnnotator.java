package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.io.NumberRangeFileFilter;
import edu.stanford.nlp.ling.CategoryWordTag;
import edu.stanford.nlp.ling.WordFactory;
import edu.stanford.nlp.trees.*;

import java.io.Reader;
import java.util.*;

/**
 * Class for getting an annotated treebank.
 *
 * @author Dan Klein
 */
public class TreebankAnnotator {

  final TreeTransformer treeTransformer;
  final TreeTransformer treeUnTransformer;
  final TreeTransformer collinizer;
  final Options op;

  public List<Tree> annotateTrees(List<Tree> trees) {
    List<Tree> annotatedTrees = new ArrayList<Tree>();
    for (Tree tree : trees) {
      annotatedTrees.add(treeTransformer.transformTree(tree));
    }
    return annotatedTrees;
  }

  public List<Tree> deannotateTrees(List<Tree> trees) {
    List<Tree> deannotatedTrees = new ArrayList<Tree>();
    for (Tree tree : trees) {
      deannotatedTrees.add(treeUnTransformer.transformTree(tree));
    }
    return deannotatedTrees;
  }


  public static List<Tree> getTrees(String path, int low, int high, int minLength, int maxLength) {
    Treebank treebank = new DiskTreebank(new TreeReaderFactory() {
      public TreeReader newTreeReader(Reader in) {
        return new PennTreeReader(in, new LabeledScoredTreeFactory(new WordFactory()), new BobChrisTreeNormalizer());
      }
    });
    treebank.loadPath(path, new NumberRangeFileFilter(low, high, true));
    List<Tree> trees = new ArrayList<Tree>();
    for (Tree tree : treebank) {
      if (tree.yield().size() <= maxLength && tree.yield().size() >= minLength) {
        trees.add(tree);
      }
    }
    return trees;
  }

  public static List<Tree> removeDependencyRoots(List<Tree> trees) {
    List<Tree> prunedTrees = new ArrayList<Tree>();
    for (Tree tree : trees) {
      prunedTrees.add(removeDependencyRoot(tree));
    }
    return prunedTrees;
  }

  static Tree removeDependencyRoot(Tree tree) {
    List<Tree> childList = tree.getChildrenAsList();
    Tree last = childList.get(childList.size() - 1);
    if (!last.label().value().equals(Lexicon.BOUNDARY_TAG)) {
      return tree;
    }
    List<Tree> lastGoneList = childList.subList(0, childList.size() - 1);
    tree.setChildren(lastGoneList);
    return tree;
  }

  public Tree collinize(Tree tree) {
    return collinizer.transformTree(tree);
  }

  public TreebankAnnotator(Options op, String treebankRoot) {
    //    op.tlpParams = new EnglishTreebankParserParams();
    // CDM: Aug 2004: With new implementation of treebank split categories,
    // I've hardwired this to load English ones.  Otherwise need training data.
    // op.trainOptions.splitters = new HashSet(Arrays.asList(op.tlpParams.splitters()));
    op.trainOptions.splitters = ParentAnnotationStats.getEnglishSplitCategories(treebankRoot);
    op.trainOptions.sisterSplitters = new HashSet<String>(Arrays.asList(op.tlpParams.sisterSplitters()));
    op.setOptions("-acl03pcfg", "-cnf");
    treeTransformer = new TreeAnnotatorAndBinarizer(op.tlpParams, op.forceCNF, !op.trainOptions.outsideFactor(), true, op);
    //    BinarizerFactory.TreeAnnotator.setTreebankLang(op.tlpParams);
    treeUnTransformer = new Debinarizer(op.forceCNF);
    collinizer = op.tlpParams.collinizer();
    this.op = op;
  }


  public static void main(String[] args) {
    CategoryWordTag.printWordTag = false;
    String path = args[0];
    List<Tree> trees = getTrees(path, 200, 219, 0, 10);
    trees.iterator().next().pennPrint();
    Options op = new Options();
    List<Tree> annotatedTrees = TreebankAnnotator.removeDependencyRoots(new TreebankAnnotator(op, path).annotateTrees(trees));
    annotatedTrees.iterator().next().pennPrint();
  }

}
