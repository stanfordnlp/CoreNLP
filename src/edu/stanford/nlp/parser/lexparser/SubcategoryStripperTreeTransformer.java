package edu.stanford.nlp.parser.lexparser;

import java.io.Serializable;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreeTransformer;

/**
 * Goes through a tree, turns all the nodes with subcategories on the
 * labels into nodes with no subcategories on the labels.
 */
public class SubcategoryStripperTreeTransformer implements TreeTransformer, Serializable {
  TreebankLanguagePack langpack;

  public SubcategoryStripperTreeTransformer(Options options) { 
    langpack = options.langpack();
  }

  public Tree transformTree(Tree tree) {
    tree.label().setValue(langpack.basicCategory(tree.label().value()));
    for (Tree child : tree.children()) {
      transformTree(child);
    }
    return tree;
  }


  private static final long serialVersionUID = 1L;

  
}