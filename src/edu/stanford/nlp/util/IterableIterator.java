package edu.stanford.nlp.util;

import java.util.*;

/**
 * This cures a pet peeve of mine: that you can't use an Iterator directly in
 * Java 5's foreach construct.  Well, this one you can, dammit.
 *
 * @author Bill MacCartney
 */
public class IterableIterator<E> implements Iterator<E>, Iterable<E> {

  private Iterator<E> it;

  public IterableIterator(Iterator<E> it) {
    this.it = it;
  }

  public boolean hasNext() { return it.hasNext(); }
  public E next() { return it.next(); }
  public void remove() { it.remove(); }
  
  public Iterator<E> iterator() { return this; }

  public static void main(String[] args) {

    String[] strings = new String[] {
      "do", "re", "mi", "fa", "so", "la", "ti", "do", 
    };

    Iterator<String> it = Arrays.asList(strings).iterator();
    // for (String s : it) {               // UH-OH!!
    //   System.out.println(s);
    // }

    IterableIterator<String> iterit = new IterableIterator<String>(it);
    for (String s : iterit) {           // YAY!!
      System.out.println(s);
    }

  }
}
