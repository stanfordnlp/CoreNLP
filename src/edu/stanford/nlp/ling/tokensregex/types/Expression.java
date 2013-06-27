package edu.stanford.nlp.ling.tokensregex.types;

import edu.stanford.nlp.ling.tokensregex.Env;

/**
* This interfaces represents an expression that can be evaluated to obtain a value
*
* @author Angel Chang
*/
public interface Expression {
  /**
   * Returns tags associated with this expression
   * @return Tags associated with this expression
   */
  public Tags getTags();

  /**
   * Set the tags associated with this expression
   * @param tags Tags to associate with this expression
   */
  public void setTags(Tags tags);

  /**
   * Returns a string indicating the type of this expression
   * @return type of this expressions
   */
  public String getType();

  /**
   * Simplifies the expression using the specified environment
   * @param env Environment to simply with respect to
   * @return Simplified expressions
   */
  public Expression simplify(Env env);

  /**
   * Evaluates the expression using the specified environment and
   *   arguments.  Arguments are additional context not provided
   *   by the environment.
   * @param env
   * @param args
   * @return Evaluated value
   */
  public Value evaluate(Env env, Object... args);

  /**
   * Returns whether the expression has already been evaluated to
   *   a Value
   * @return True if the expression is already evaluated
   */
  public boolean hasValue();
}
