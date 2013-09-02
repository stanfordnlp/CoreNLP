package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Randomized Iterable
 * 
 * @author daniel cer (http://dmcer.net)
 *
 */
public class RandomizedIterable<T> implements Iterable<T> {
  private final Iterable<T> wrappedIterable;
  private final Random r;
  private final boolean caching;
  private List<T> items;
  
  public RandomizedIterable(Iterable<T> t) {
    wrappedIterable = t;
    r = new Random();
    caching = false;
  }
  
  public RandomizedIterable(Iterable<T> t, Random r) {
    wrappedIterable = t;
    this.r = r;
    caching = false;
  }
  
  public RandomizedIterable(Iterable<T> t, Random r, boolean caching) {
    wrappedIterable = t;
    this.r = r;
    this.caching = caching;
  }
  
  public Iterator<T> iterator() {
    if (!caching || items == null) {
      items = new ArrayList<T>();
      for (T item : wrappedIterable) {
        items.add(item);
      }
    }
    
    Collections.shuffle(items, r);  
    return items.iterator();
  }

}
