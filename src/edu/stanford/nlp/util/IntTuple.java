package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.List;


/**
 * A tuple of int. There are special classes for IntUni, IntPair, IntTriple
 * and IntQuadruple. The motivation for that was the different hashCode
 * implementations.
 * By using the static IntTuple.getIntTuple(numElements) one can obtain an
 * instance of the appropriate sub-class.
 *
 * @author Kristina Toutanova (kristina@cs.stanford.edu)
 */
public class IntTuple implements Serializable, Comparable<IntTuple> {

  final int[] elements;

  private static final long serialVersionUID = 7266305463893511982L;


  public IntTuple(int[] arr) {
    elements = arr;
  }

  public IntTuple(int num) {
    elements = new int[num];
  }

  @Override
  public int compareTo(IntTuple o) {
    int commonLen = Math.min(o.length(), length());
    for (int i = 0; i < commonLen; i++) {
      int a = get(i);
      int b = o.get(i);
      if (a < b) return -1;
      if (b < a) return 1;
    }
    if (o.length() == length()) {
      return 0;
    } else {
      return (length() < o.length())? -1:1;
    }
  }

  public int get(int num) {
    return elements[num];
  }


  public void set(int num, int val) {
    elements[num] = val;
  }

  public void shiftLeft() {
    System.arraycopy(elements, 1, elements, 0, elements.length - 1);  // the API does guarantee that this works when src and dest overlap, as here
    elements[elements.length - 1] = 0;
  }


  public IntTuple getCopy() {
    IntTuple copy = IntTuple.getIntTuple(elements.length); //new IntTuple(numElements);
    System.arraycopy(elements, 0, copy.elements, 0, elements.length);
    return copy;
  }


  public int[] elems() {
    return elements;
  }

  @Override
  public boolean equals(Object iO) {
    if (!(iO instanceof IntTuple)) {
      return false;
    }
    IntTuple i = (IntTuple) iO;
    if (i.elements.length != elements.length) {
      return false;
    }
    for (int j = 0; j < elements.length; j++) {
      if (elements[j] != i.get(j)) {
        return false;
      }
    }
    return true;
  }


  @Override
  public int hashCode() {
    int sum = 0;
    for (int element : elements) {
      sum = sum * 17 + element;
    }
    return sum;
  }


  public int length() {
    return elements.length;
  }


  public static IntTuple getIntTuple(int num) {
    if (num == 1) {
      return new IntUni();
    }
    if ((num == 2)) {
      return new IntPair();
    }
    if (num == 3) {
      return new IntTriple();
    }
    if (num == 4) {
      return new IntQuadruple();
    } else {
      return new IntTuple(num);
    }
  }


  public static IntTuple getIntTuple(List<Integer> integers) {
    IntTuple t = IntTuple.getIntTuple(integers.size());
    for (int i = 0; i < t.length(); i++) {
      t.set(i, integers.get(i).intValue());
    }
    return t;
  }

  @Override
  public String toString() {
    StringBuilder name = new StringBuilder();
    for (int i = 0; i < elements.length; i++) {
      name.append(get(i));
      if (i < elements.length - 1) {
        name.append(' ');
      }
    }
    return name.toString();
  }


  public static IntTuple concat(IntTuple t1, IntTuple t2) {
    int n1 = t1.length();
    int n2 = t2.length();
    IntTuple res = IntTuple.getIntTuple(n1 + n2);

    for (int j = 0; j < n1; j++) {
      res.set(j, t1.get(j));
    }
    for (int i = 0; i < n2; i++) {
      res.set(n1 + i, t2.get(i));
    }
    return res;
  }


  public void print() {
    String s = toString();
    System.out.print(s);
  }

}
