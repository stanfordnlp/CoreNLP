package edu.stanford.nlp.util;


/**
 * Holds an unordered pair of objects.
 *
 * @author Dan Klein
 * @version 2/7/01
 */
public class UnorderedPair<T1,T2> extends Pair<T1,T2> {

  private static final long serialVersionUID = 1L;

  @Override
  public String toString() {
    return "{" + first + "," + second + "}";
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof UnorderedPair) {
      UnorderedPair p = (UnorderedPair) o;
      return (((first == null ? p.first == null : first.equals(p.first)) && (second == null ? p.second == null : second.equals(p.second))) || ((first == null ? p.second == null : first.equals(p.second)) && (second == null ? p.first == null : second.equals(p.first))));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int firstHashCode = (first == null ? 0 : first.hashCode());
    int secondHashCode = (second == null ? 0 : second.hashCode());
    if (firstHashCode != secondHashCode) {
      return (((firstHashCode & secondHashCode) << 16) ^ ((firstHashCode | secondHashCode)));
    } else {
      return firstHashCode;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public int compareTo(Pair<T1,T2> o) {
    UnorderedPair other = (UnorderedPair) o;
    // get canonical order of this and other
    Object this1 = first;
    Object this2 = second;
    int thisC = ((Comparable) first).compareTo(second);
    if (thisC < 0) {
      // switch em
      this1 = second;
      this2 = first;
    }
    Object other1 = first;
    Object other2 = second;
    int otherC = ((Comparable) other.first).compareTo(other.second);
    if (otherC < 0) {
      // switch em
      other1 = second;
      other2 = first;
    }
    int c1 = ((Comparable) this1).compareTo(other1);
    if (c1 != 0) {
      return c1; // base it on the first
    }
    int c2 = ((Comparable) this2).compareTo(other2);
    if (c2 != 0) {
      return c1; // base it on the second
    }
    return 0; // must be equal
  }

  public UnorderedPair() {
    first = null;
    second = null;
  }

  public UnorderedPair(T1 first, T2 second) {
    this.first = first;
    this.second = second;
  }

}
