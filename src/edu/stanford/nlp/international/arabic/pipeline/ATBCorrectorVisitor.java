package edu.stanford.nlp.international.arabic.pipeline;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.TreeVisitor;

/**
 * Wrapper class for using the ATBCorrector class with TreebankPipeline's
 * TVISITOR parameter.
 * 
 * @author Spence Green
 *
 */
public class ATBCorrectorVisitor implements TreeVisitor {

  private final TreeTransformer atbCorrector = new ATBCorrector();
  
  public void visitTree(Tree t) {
    atbCorrector.transformTree(t);
  }

}
