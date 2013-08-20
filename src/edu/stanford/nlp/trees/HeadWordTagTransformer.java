package edu.stanford.nlp.trees;

import edu.stanford.nlp.ling.CyclicCoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.SemanticHeadWordAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SemanticTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SemanticWordAnnotation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

/**
 * Assumes all labels in tree are CyclicCoreLabel (use CyclicCoreLabelTransformer), and puts
 * in HeadWord , SemanticHead, and HeadCategory in the CyclicCoreLabel under
 * CyclicCoreLabel.HEAD_WORD_KEY  CyclicCoreLabel.SEMANTIC_HEAD_WORD_KEY and CyclicCoreLabel.HEAD_TAG_KEY respectively.
 * The SemanticHead is added. It uses
 * the default Collins head finder and the SemanticHeadFinder for finding semantic head.
 *
 * @author Aria Haghighi (aria42@stanford.edu)
 */
public class HeadWordTagTransformer implements TreeTransformer {
  public HeadWordTagTransformer() {
  }

  private static CollinsHeadFinder headFinder = new CollinsHeadFinder();
  private static SemanticHeadFinder semanticHeadFinder = new SemanticHeadFinder();

  public Tree transformTree(Tree tree) {
    tree = tree.treeSkeletonCopy();
    for (Iterator<Tree> it = tree.iterator(); it.hasNext();) {
      Tree subtree = it.next();
      CyclicCoreLabel label = (CyclicCoreLabel) subtree.label();

      if (subtree.isLeaf()) {
        label.setWord(subtree.toString());
        Tree p = subtree.parent(tree);
        String pos = p.label().value();
        label.setTag(pos);
        label.set(SemanticWordAnnotation.class, subtree.toString());
        label.set(SemanticTagAnnotation.class, pos);
        continue;
      }
      try {
        Tree headPreTerminal = subtree.headPreTerminal(headFinder);
        Tree headTerminal = headPreTerminal.getChild(0);
        String headWord = headTerminal.label().value();
        label.setWord(headWord);
        String headTag = headPreTerminal.label().value();
        label.setTag(headTag);
        Tree semanticNode = subtree.headPreTerminal(semanticHeadFinder);
        //if(semanticNode==null){semanticNode=headPreTerminal;}else{
        //System.err.println("non-null sem head");
        //}
        String semanticHeadWord = semanticNode.getChild(0).label().value();
        label.set(SemanticHeadWordAnnotation.class, semanticHeadWord);
        String semanticHeadTag = semanticNode.label().value();
        label.set(SemanticTagAnnotation.class, semanticHeadTag);
      } catch (Exception e) {
        subtree.pennPrint(System.err);
        e.printStackTrace();
        continue;
      }
    }
    return tree;
  }

  public static void main(String[] args) throws IOException {
    BufferedReader in = new BufferedReader(new FileReader(args[0]));
    PennTreeReader ptr = new PennTreeReader(in, new LabeledScoredTreeFactory());
    while (true) {
      Tree t = ptr.readTree();
      if (t == null) break;
      t = (new CyclicCoreLabelTransformer()).transformTree(t);
      (new HeadWordTagTransformer()).transformTree(t);
    }
  }
}
