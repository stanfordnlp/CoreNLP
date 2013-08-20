package edu.stanford.nlp.util;

import java.util.*;

/**
 * An iterator over the cartesian product of a List of Collections.
 * Could be easily wrapped into a Collection.
 *
 * @author Kristina Toutanova
 *         Sep 27, 2004
 */
public class CartesianProductIterator<T> extends AbstractIterator<List<T>> {
  private Collection<?>[] collections;
  private Iterator<?>[] iterators;
  private ArrayList<T> nextObject = null;
  private boolean hasNull = false;
  private int length;


  public CartesianProductIterator(List<? extends Collection<T>> collections) {
    this.collections = new Collection[collections.size()];
    for (int i = 0; i < collections.size(); i++) {
      this.collections[i] = collections.get(i);
      if ((this.collections[i] == null) || (this.collections[i].size() == 0)) {
        hasNull = true;
      }
    }
    length = collections.size();
    if (hasNull) {
      return;
    }
    iterators = new Iterator[collections.size()];
    nextObject = new ArrayList<T>();
    for (int i = 0; i < iterators.length; i++) {
      iterators[i] = this.collections[i].iterator();
      nextObject.add(ErasureUtils.<T>uncheckedCast(iterators[i].next()));
    }
  }

  private void advance() {
    if (hasNull) {
      return;
    }
    if (nextObject == null) {
      return;
    }
    int cIndex = length - 1;
    boolean canAdvance = false;
    while (cIndex >= 0) {
      if (iterators[cIndex].hasNext()) {
        //nextObject.add(cIndex, iterators[cIndex].next());
        nextObject.set(cIndex, ErasureUtils.<T>uncheckedCast(iterators[cIndex].next())); // rajat

        canAdvance = true;
        break;
      }
      //else need to reset that iterator
      iterators[cIndex] = collections[cIndex].iterator();

      //nextObject.add(cIndex, iterators[cIndex].next());
      nextObject.set(cIndex, ErasureUtils.<T>uncheckedCast(iterators[cIndex].next())); // rajat
      cIndex--;
    }
    if (!canAdvance) {
      nextObject = null;
    }

  }

  @Override
  public boolean hasNext() {
    if (hasNull) {
      return false;
    }
    return nextObject != null;
  }

  @Override
  public List<T> next() {
    //ArrayList prev = nextObject;
    ArrayList<T> prev = ErasureUtils.uncheckedCast(nextObject.clone()); // rajat

    if (hasNext()) {
      advance();
    }
    //System.out.println(prev);
    return prev;
  }


    public static void main(String[] args) {
        HashSet<String> words = new HashSet<String>();
        words.add("Blah");
        words.add("Blub");
        LinkedList<HashSet<String>> sets = new LinkedList<HashSet<String>>();
        sets.add(words);
        sets.add(words);

        CartesianProductIterator<String> i = new CartesianProductIterator<String>(sets);
        while (i.hasNext()){
            System.out.println(i.next());
        }
    }
}
