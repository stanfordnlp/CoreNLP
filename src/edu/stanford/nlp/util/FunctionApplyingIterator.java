package edu.stanford.nlp.util;

import java.util.Iterator;

import edu.stanford.nlp.util.AbstractIterator;

/**
 * This Iterator will take each element in the Iterator
 * it is given and apply the function to it, and return
 * the result.
 *
 * @author Jenny Finkel
 */
public class FunctionApplyingIterator<T1,T2> extends AbstractIterator<T2> {

  private Function<T1,T2> func;
  private Iterator<T1> iter;
  
  public FunctionApplyingIterator(Function<T1,T2> func, Iterator<T1> iter) {
    this.func = func;
    this.iter = iter;
  }
  
  @Override
  public boolean hasNext() {
    return iter.hasNext();
  }

  @Override
  public T2 next() {
    return func.apply(iter.next());
  }
  
}
