package edu.stanford.nlp.util;

import java.util.Iterator;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class ArrayIterable<E> implements Iterable<E> {
  public final E[] data;
  
  public ArrayIterable(E[] data){
    this.data = data;
  }

  public Iterator<E> iterator() {
    return new Iterator<E>(){
      int i=0;
      public boolean hasNext() {
        return i < data.length;
      }
      public E next() {
        i += 1;
        return data[i-1];
      }
      public void remove() {
        throw new RuntimeException("Cannot remove from this Iterator");
      }
    };
  }
}
