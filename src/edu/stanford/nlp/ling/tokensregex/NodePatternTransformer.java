package edu.stanford.nlp.ling.tokensregex;

/**
 * Interface to transform a node pattern from a {@code NodePattern<T1>} into a
 * {@code NodePattern <T2>}.
 *
 * @author Angel Chang
 */
public interface NodePatternTransformer<T1,T2> {

  NodePattern<T2> transform(NodePattern<T1> n1);

  MultiNodePattern<T2> transform(MultiNodePattern<T1> n1);

}
