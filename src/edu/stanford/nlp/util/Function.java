package edu.stanford.nlp.util;


/**
 * An interface for classes that act as a function transforming one object
 * to another.
 * <p>
 * <i>Implementation note:</i> A function by itself is not serializable.
 * We do however also provide an interface
 * {@link edu.stanford.nlp.process.SerializableFunction} for
 * the common case of a function that should be Serializable.
 *
 * @author Dan Klein
 * @param <T1> The domain of the function
 * @param <T2> The range of the function
 */
public interface Function <T1,T2> {

  /**
   * Converts a T1 to a different T2.  For example, a Parser
   * will convert a Sentence to a Tree.  A Tagger will convert a Sentence
   * to a TaggedSentence.
   *
   * @param in The function's argument
   * @return The function's evaluated value
   */
  public T2 apply(T1 in);

}
