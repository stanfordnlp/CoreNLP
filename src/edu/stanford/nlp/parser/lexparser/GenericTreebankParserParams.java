package edu.stanford.nlp.parser.lexparser;

import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.trees.DiskTreebank;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.MemoryTreebank;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReaderFactory;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.TreebankLanguagePack;

public class GenericTreebankParserParams extends AbstractTreebankParserParams {

  /**
   * 
   */
  private static final long serialVersionUID = -617650500538652513L;

  protected GenericTreebankParserParams(TreebankLanguagePack tlp) {
    super(tlp);
    // TODO Auto-generated constructor stub
  }

  @Override
  public AbstractCollinizer collinizer() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public AbstractCollinizer collinizerEvalb() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void display() {
    // TODO Auto-generated method stub
    
  }

  @Override
  public HeadFinder headFinder() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public HeadFinder typedDependencyHeadFinder() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public MemoryTreebank memoryTreebank() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int setOptionFlag(String[] args, int i) {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public String[] sisterSplitters() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Tree transformTree(Tree t, Tree root) {
    // TODO Auto-generated method stub
    return null;
  }

  public List<? extends HasWord> defaultTestSentence() {
    // TODO Auto-generated method stub
    return null;
  }

  public DiskTreebank diskTreebank() {
    // TODO Auto-generated method stub
    return null;
  }

  public TreeReaderFactory treeReaderFactory() {
    // TODO Auto-generated method stub
    return null;
  }

}
