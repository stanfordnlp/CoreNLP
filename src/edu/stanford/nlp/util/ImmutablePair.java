package edu.stanford.nlp.util;

/**
 * Represents an immutable ordered pair of elements.  (The elements may be
 * mutable, but the pair is not).  Can also be used as a node in an
 * (unlabeled) binary tree.  Null elements are allowed.
 *
 * @author Bill MacCartney
 */
public class ImmutablePair<F, S> {

  // instance variables ----------------------------------------------------

  private final F first;
  private final S second;


  // factory methods -------------------------------------------------------

  // private to ensure immutability
  private ImmutablePair(F first, S second) {
    this.first = first;
    this.second = second;
  }

  public static <F, S> ImmutablePair<F, S> make(F first, S second) {
    return new ImmutablePair<F, S>(first, second);
  }

  // access methods --------------------------------------------------------

  public F first() { return first; }
  public S second() { return second; }
  

  // replace methods (make methods in disguise) --------------------------------

  /** 
   * Returns the new {@link ImmutablePair} that results from replacing this
   * pair's first element with the supplied <code>newElement</code>.
   */
  public ImmutablePair<F, S> replaceFirst(F newElement) {
    return make(newElement, second());
  }
  
  /** 
   * Returns the new {@link ImmutablePair} that results from replacing this
   * pair's second element with the supplied <code>newElement</code>.
   */
  public ImmutablePair<F, S> replaceSecond(S newElement) {
    return make(first(), newElement);
  }
  

  // utility methods -------------------------------------------------------

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ImmutablePair)) return false;
    ImmutablePair p = (ImmutablePair) o;
    return 
      (first()  == null ? p.first()  == null : first().equals(p.first())) && 
      (second() == null ? p.second() == null : second().equals(p.second()));
  }

  @Override
  public int hashCode() {
    int result = 17;
    result =               (first()  != null ? first().hashCode()  : 0);
    result = 29 * result + (second() != null ? second().hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "<" + first() + ", " + second() + ">";
  }

}
