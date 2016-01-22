package edu.stanford.nlp.ling.tokensregex.types;

/**
* A expression that has been evaluated to a Java object of type T
*
* @author Angel Chang
*/
public interface Value<T> extends Expression {
  /**
   * The Java object representing the value of the expressions
   * @return a Java object
   */
  public T get();
}
