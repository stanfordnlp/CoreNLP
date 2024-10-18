package edu.stanford.nlp.trees;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A TreeTransformer that applies component TreeTransformers in order.
 * The order in which they are applied is the order in which they are added or
 * the order in which they appear in the List passed to the constructor.
 *
 * @author Galen Andrew
 */
public class CompositeTreeTransformer implements TreeTransformer {

  private final List<TreeTransformer> transformers = new ArrayList<>();

  public CompositeTreeTransformer() {
  }

  public CompositeTreeTransformer(List<TreeTransformer> tt) {
    transformers.addAll(tt);
  }

  public CompositeTreeTransformer(TreeTransformer ... tt) {
    transformers.addAll(Arrays.asList(tt));
  }

  public void addTransformer(TreeTransformer tt) {
    transformers.add(tt);
  }

  public Tree transformTree(Tree t) {
    for (TreeTransformer tt : transformers) {
      t = tt.transformTree(t);
    }
    return t;
  }


  @Override
  public String toString() {
    return "CompositeTreeTransformer: " + transformers;
  }
}
