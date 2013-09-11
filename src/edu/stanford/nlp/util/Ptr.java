package edu.stanford.nlp.util;

/**
 * This class facilitates methods with pass by Ptreference semantics 
 * 
 * e.g. 
 * 
 * public swap(Ptr<E> x, Ptr<Y> y) {
 *    E temp = x.dePtr();
 *    x.set(y.deref());
 *    y.set(temp);
 * } 
 * 
 * 
 * @author danielcer
 *
 * @param <E>
 */
public class Ptr<E> {
  private E e;
  
  
  public Ptr() { }
  
  public Ptr(E e) {
    this.e = e;
  }
  
  public E deref() {
    return e;
  }
  
  public Ptr<E> set(E e) {
    this.e = e;
    return this;
  }
}
