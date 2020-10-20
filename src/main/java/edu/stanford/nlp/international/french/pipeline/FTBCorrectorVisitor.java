package edu.stanford.nlp.international.french.pipeline;

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
public class FTBCorrectorVisitor implements TreeVisitor {

  private final TreeTransformer ftbCorrector = new FTBCorrector();

  public void visitTree(Tree t) {
    ftbCorrector.transformTree(t);
  }

}
