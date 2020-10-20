package edu.stanford.nlp.ling.tokensregex.types;

import edu.stanford.nlp.ling.tokensregex.Env;

import java.util.List;

/**
 * A function that takes as input an environment (Env) and a list of values
 * ({@code List<Value>}) and returns a Value.
 *
 * @author Angel Chang
 */
public interface ValueFunction {

  /**
   * Checks if the arguments are valid.
   *
   * @param in The input arguments
   * @return true if the arguments are valid (false otherwise)
   */
  boolean checkArgs(List<Value> in);

  /**
   * Applies the function to the list values using the environment as context
   * and returns the evaluated value.
   *
   * @param env The environment to use
   * @param in The input arguments
   * @return Value indicating the value of the function
   */
  Value apply(Env env, List<Value> in);

  /**
   * Returns a string describing what this function does.
   *
   * @return String describing the function
   */
  String getDescription();

}
