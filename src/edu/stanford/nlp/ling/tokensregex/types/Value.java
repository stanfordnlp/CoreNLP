package edu.stanford.nlp.ling.tokensregex.types;

/**
* An expression that has been evaluated to a Java object of type T.
*
* @author Angel Chang
*/
public interface Value<T> extends Expression {

  /**
   * The Java object representing the value of the expression.
   *
   * @return a Java object
   */
  public T get();

}
