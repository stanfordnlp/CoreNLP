package edu.stanford.nlp.util;

import java.util.AbstractList;
import java.util.List;
import java.util.ArrayList;


/**
 * A CircleList wraps another list, presenting an apparently infinite
 * list by wrapping around all requests for an item, as if the list were
 * circular.  Note that <code>size()</code> returns the true size, but
 * <code>get()</code> works for any number.
 * The CircleList is an immutable list. Setting and adding methods are not
 * implemented.  But the constructor does not copy the underlying list it
 * is constructed from.
 *
 * @author Dan Klein
 * @author Christopher Manning
 */
public class CircleList<E> extends AbstractList<E> {

  private final List<E> list;


  /**
   * This gives a modulo operation that always returns a number
   * between 0 and (modulo - 1) whereas the standard % will modulo
   * negative numbers to non-positive numbers (1-modulo) to (modulo-1).
   *
   * @param x The number
   * @param modulo The modulus
   * @return The number between 0 and (modulo-1) inclusive
   */
  private static int positiveModulo(int x, int modulo) {
    int out = x % modulo;
    if (out < 0) {
      out += modulo;
    }
    return out;
  }

  @Override
  public int size() {
    return list.size();
  }

  @Override
  public E get(int i) {
    // int ind = (i%size()+size())%size();
    int sz = size();
    int ind = positiveModulo(i, sz);
    return list.get(ind);
  }


  /**
   * subList for a CircleList is defined so it can wrap around but not so
   * there is replication. So, for a 3 element list <code>foo</code>,
   * <code>foo.subList(1,3).equals(foo.subList(1,6)</code>,
   * <code>foo.subList(2,4)</code> returns a two element list with the last
   * and then first elements of <code>foo</code>, and you get the same
   * answer for <code>foo.subList(2,1)</code>. Hence, this implementation
   * never throws an IllegalArgumentException.
   *
   * @param start First index of subList (inclusive)
   * @param end Index after last item of subList (exclusive)
   * @return The subList
   */
  @Override
  public List<E> subList(int start, int end) {
    int size = list.size();
    start = positiveModulo(start, size);
    end = positiveModulo(end, size);
//  System.out.println("Start: " + start+", end: "+end+", size: "+size);
    if (start > end) {
      List<E> newList = new ArrayList<E>();
      newList.addAll(list.subList(start, size));
//    System.err.println(newList.size());
      newList.addAll(list.subList(0, end));
//    System.err.println(newList.size());
      return newList;
    } else {
      return list.subList(start, end);
    }
  }

  public CircleList(List<E> l) {
    this.list = l;
  }

}
