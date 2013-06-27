package edu.stanford.nlp.stats;

import edu.stanford.nlp.math.SloppyMath;

/**
 * Default implementations of all the convenience methods provided by
 * {@link Counter}.
 *
 * @author dramage
 */
public abstract class AbstractCounter<E> implements Counter<E> {

  public double logIncrementCount(E key, double amount) {
    double count = SloppyMath.logAdd(getCount(key), amount);
    setCount(key, count);
    return getCount(key);
  }

  public double incrementCount(E key, double amount) {
    double count = getCount(key) + amount;
    setCount(key, count);
    // get the value just to make sure it agrees with what is in the counter
    // (in case it's a float or int)
    return getCount(key);
  }

  public double incrementCount(E key) {
    return incrementCount(key, 1.0);
  }

  public double decrementCount(E key, double amount) {
    return incrementCount(key, -amount);
  }

  public double decrementCount(E key) {
    return incrementCount(key, -1.0);
  }

  /** {@inheritDoc} */
  public void addAll(Counter<E> counter) {
    Counters.addInPlace(this, counter);
  }

}
