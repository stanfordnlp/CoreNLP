package edu.stanford.nlp.ling.tokensregex.types;

/**
* This interface represents an expression that can be assigned to.
*
* @author Angel Chang
*/
public interface AssignableExpression extends Expression {
  public Expression assign(Expression expr);
}
