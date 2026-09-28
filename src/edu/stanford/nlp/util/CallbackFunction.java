package edu.stanford.nlp.util;

/** A callback function (along the lines of Berkeley optimization repo), which is currently used in the optimization package.
 * In the optimization package, it is used for passing values (newX, iteration,  newObjectiveValue, newGradient) at every iteration and
 * then you can do whatever you want with those values.
 *
 * One use case is to print the values in a file; another is do some sanity check etc.
 * *
 * Created by sonalg on 2/4/15.
 */
public abstract class CallbackFunction {
  /** Constructor for use by subclasses. */
  public CallbackFunction() { }

  /**
   * Called with whatever values the caller chooses to pass (in the optimization package,
   * the values of the current iteration).
   *
   * @param args The values passed by the caller
   */
  public abstract void callback(Object... args);
}
