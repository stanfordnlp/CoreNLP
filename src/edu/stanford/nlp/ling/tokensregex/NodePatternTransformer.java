package edu.stanford.nlp.ling.tokensregex;

/**
 * Interface to transform a node pattern from a NodePattern<T1> into a NodePattern <T2>
 *
 * @author Angel Chang
 */
public interface NodePatternTransformer<T1,T2> {
  public NodePattern<T2> transform(NodePattern<T1> n1);
  public MultiNodePattern<T2> transform(MultiNodePattern<T1> n1);
}
