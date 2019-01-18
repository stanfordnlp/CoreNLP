package edu.stanford.nlp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Utilities for helping out with Iterables as Collections is to Collection.
 *
 * NB: Some Iterables returned by methods in this class return Iterators that
 * assume a call to hasNext will precede each call to next.  While this usage
 * is not up to the Java Iterator spec, it should work fine with
 * e.g. the Java enhanced for-loop.
 *
 * Methods in Iterators are merged.
 *
 * @author dramage
 * @author dlwh {@link #flatMap(Iterable, Function)}
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 *
 */
public class Iterables {

  private Iterables() { } // static methods

  /**
   * Transformed view of the given iterable.  Returns the output
   * of the given function when applied to each element of the
   * iterable.
   */
  public static <K,V> Iterable<V> transform(
      final Iterable<K> iterable, final Function<? super K,? extends V> function) {

    return ()-> { return new Iterator<V>() {
          Iterator<K> inner = iterable.iterator();

          public boolean hasNext() {
            return inner.hasNext();
          }

          public V next() {
            return function.apply(inner.next());
          }

          public void remove() {
            inner.remove();
          }
        };};
  }

  /**
   * Filtered view of the given iterable.  Returns only those elements
   * from the iterable for which the given Function returns true.
   */
  public static <T> Iterable<T> filter(
      final Iterable<T> iterable, final Predicate<T> accept) {

    return new Iterable<T>() {
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          Iterator<T> inner = iterable.iterator();

          boolean queued = false;
          T next = null;

          public boolean hasNext() {
            prepare();
            return queued;
          }

          public T next() {
            prepare();
            if (!queued) {
              throw new RuntimeException("Filter .next() called with no next");
            }
            T rv = next;
            next = null;
            queued = false;
            return rv;
          }

          public void prepare() {
            if (queued) {
              return;
            }

            while (inner.hasNext()) {
              T next = inner.next();
              if (accept.test(next)) {
                this.next = next;
                this.queued = true;
                return;
              }
            }
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  /**
   * Casts all values in the given Iterable to the given type.
   */
  public static <T> Iterable<T> cast(
      final Iterable<?> iterable, final Class<? extends T> type) {

    return ()-> { return new Iterator<T>() {
          Iterator<?> inner = iterable.iterator();

          public boolean hasNext() {
            return inner.hasNext();
          }

          public T next() {
            return type.cast(inner.next());
          }

          public void remove() {
            inner.remove();
          }
        };};
  }

  /**
   * Returns a shortened view of an iterator.  Returns at most <code>max</code> elements.
   */
  public static <T> Iterable<T> take(T[] array, int max) {
    return take(Arrays.asList(array),max);
  }

  /**
   * Returns a shortened view of an iterator.  Returns at most <code>max</code> elements.
   */
  public static <T> Iterable<T> take(
      final Iterable<T> iterable, final int max) {

    return new Iterable<T>() {
      final Iterator<T> iterator = iterable.iterator();

      // @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          int i = 0;

          // @Override
          public boolean hasNext() {
            return i < max && iterator.hasNext();
          }

          // @Override
          public T next() {
            i++;
            return iterator.next();
          }

          // @Override
          public void remove() {
            iterator.remove();
          }
        };
      }
    };
  }

  /**
   * Returns a view of the given data, ignoring the first toDrop elements.
   */
  public static <T> Iterable<T> drop(T[] array, int toDrop) {
    return drop(Arrays.asList(array),toDrop);
  }

  /**
   * Returns a view of the given data, ignoring the first toDrop elements.
   */
  public static <T> Iterable<T> drop(
      final Iterable<T> iterable, final int toDrop) {

    return new Iterable<T>() {
      final Iterator<T> iterator = iterable.iterator();

      // @Override
      public Iterator<T> iterator() {
        return new Iterator<T>() {
          int skipped = 0;

          // @Override
          public boolean hasNext() {
            while (skipped < toDrop && iterator.hasNext()) {
              iterator.next();
              skipped += 1;
            }
            return iterator.hasNext();
          }

          // @Override
          public T next() {
            while (skipped < toDrop && iterator.hasNext()) {
              iterator.next();
              skipped += 1;
            }
            return iterator.next();
          }

          // @Override
          public void remove() {
            iterator.remove();
          }
        };
      }
    };
  }

  /**
   * Chains together an Iterable of Iterables after transforming each one.
   * Equivalent to Iterables.transform(Iterables.chain(iterables),trans);
   */
  public static <T,U> Iterable<U> flatMap(final Iterable<? extends Iterable<T>> iterables, Function<? super T,U> trans) {
    return transform(chain(iterables),trans);
  }

  /**
   * Chains together a set of Iterables of compatible types.  Returns all
   * elements of the first iterable, then all of the second, then the third,
   * etc.
   */
  public static <T> Iterable<T> chain(final Iterable<? extends Iterable<T>> iterables) {
    return new Iterable<T>() {
      public Iterator<T> iterator() {
        final Iterator<? extends Iterable<T>> iterators = iterables.iterator();

        return new Iterator<T>() {
          private Iterator<T> current = null;

          public boolean hasNext() {
            // advance current iterator if necessary, return false at end
            while (current == null || !current.hasNext()) {
              if (iterators.hasNext()) {
                current = iterators.next().iterator();
              } else {
                return false;
              }
            }
            return true;
          }

          public T next() {
            return current.next();
          }

          public void remove() {
            current.remove();
          }
        };
      }
    };
  }

  /**
   * Chains together all Iterables of type T as given in an array or
   * varargs parameter.
   */
  public static <T> Iterable<T> chain(final Iterable<T> ... iterables) {
    return chain(Arrays.asList(iterables));
  }

  /**
   * Chains together all arrays of type T[] as given in an array or
   * varargs parameter.
   */
  public static <T> Iterable<T> chain(final T[] ... arrays) {
    LinkedList<Iterable<T>> iterables = new LinkedList<>();
    for (T[] array : arrays) {
      iterables.add(Arrays.asList(array));
    }
    return chain(iterables);
  }

  /**
   * Zips two iterables into one iterable over Pairs of corresponding
   * elements in the two underlying iterables.  Ends when the shorter
   * iterable ends.
   */
  public static <T1, T2> Iterable<Pair<T1,T2>> zip(
      final Iterable<T1> iter1, final Iterable<T2> iter2) {

    return ()-> { return zip(iter1.iterator(), iter2.iterator());};
  }

  /**
   * Zips two iterables into one iterable over Pairs of corresponding
   * elements in the two underlying iterables.  Ends when the shorter
   * iterable ends.
   */
  public static <T1,T2> Iterable<Pair<T1,T2>> zip(
      Iterable<T1> iter, T2 array[]) {

    return zip(iter, Arrays.asList(array));
  }

  /**
   * Zips two iterables into one iterable over Pairs of corresponding
   * elements in the two underlying iterables.  Ends when the shorter
   * iterable ends.
   */
  public static <T1, T2> Iterable<Pair<T1,T2>> zip(
      T1 array[], Iterable<T2> iter) {

    return zip(Arrays.asList(array), iter);
  }

  /**
   * Zips two iterables into one iterable over Pairs of corresponding
   * elements in the two underlying iterables.  Ends when the shorter
   * iterable ends.
   */
  public static <T1, T2> Iterable<Pair<T1,T2>> zip(
      T1 array1[], T2 array2[]) {

    return zip(Arrays.asList(array1), Arrays.asList(array2));
  }

  /**
   * Zips up two iterators into one iterator over Pairs of corresponding
   * elements.  Ends when the shorter iterator ends.
   */
  public static <T1,T2> Iterator<Pair<T1,T2>> zip(
      final Iterator<T1> iter1, final Iterator<T2> iter2) {

    return new Iterator<Pair<T1,T2>>() {
      public boolean hasNext() {
        return iter1.hasNext() && iter2.hasNext();
      }

      public Pair<T1, T2> next() {
        return new Pair<>(iter1.next(), iter2.next());
      }

      public void remove() {
        iter1.remove();
        iter2.remove();
      }
    };
  }

  /**
   * A comparator used by the merge functions to determine which of two
   * iterators to increment by one of the merge functions.
   *
   * @param <V1> Type of first iterator
   * @param <V2> Type of second iterator
   */
  public interface IncrementComparator<V1,V2> {
    /**
     * Returns -1 if the value of a should come before the value of b,
     * +1 if the value of b should come before the value of a, or 0 if
     * the two should be merged together.
     */
    public int compare(V1 a, V2 b);
  }

  /**
   * Iterates over pairs of objects from two (sorted) iterators such that
   * each pair a \in iter1, b \in iter2 returned has comparator.compare(a,b)==0.
   * If the comparator says that a and b are not equal, we increment the
   * iterator of the smaller value.  If the comparator says that a and b are
   * equal, we return that pair and increment both iterators.
   *
   * This is used, e.g. to return lines from two input files that have
   * the same "key" as determined by the given comparator.
   *
   * The comparator will always be passed elements from the first iter as
   * the first argument.
   */
  public static <V1,V2> Iterable<Pair<V1,V2>> merge(
      final Iterable<V1> iter1, final Iterable<V2> iter2,
      final IncrementComparator<V1,V2> comparator) {

    return new Iterable<Pair<V1,V2>>() {
      Iterator<V1> iterA = iter1.iterator();
      Iterator<V2> iterB = iter2.iterator();

      public Iterator<Pair<V1, V2>> iterator() {
        return new Iterator<Pair<V1,V2>>() {
          boolean ready = false;
          Pair<V1,V2> pending = null;

          public boolean hasNext() {
            if (!ready) {
              pending = nextPair();
              ready = true;
            }
            return pending != null;
          }

          public Pair<V1, V2> next() {
            if (!ready && !hasNext()) {
              throw new IllegalAccessError("Called next without hasNext");
            }
            ready = false;
            return pending;
          }

          public void remove() {
            throw new UnsupportedOperationException("Cannot remove pairs " +
            "from a merged iterator");
          }

          private Pair<V1,V2> nextPair() {
            V1 nextA = null;
            V2 nextB = null;

            while (iterA.hasNext() && iterB.hasNext()) {
              // increment iterators are null
              if (nextA == null) { nextA = iterA.next(); }
              if (nextB == null) { nextB = iterB.next(); }

              int cmp = comparator.compare(nextA, nextB);
              if (cmp < 0) {
                // iterA too small, increment it next time around
                nextA = null;
              } else if (cmp > 0) {
                // iterB too small, increment it next time around
                nextB = null;
              } else {
                // just right - return this pair
                return new Pair<>(nextA, nextB);
              }
            }

            return null;
          }
        };
      }

    };
  }

  /**
   * Same as {@link #merge(Iterable, Iterable, IncrementComparator)} but using
   * the given (symmetric) comparator.
   */
  public static <V> Iterable<Pair<V,V>> merge(
      final Iterable<V> iter1, final Iterable<V> iter2,
      final Comparator<V> comparator) {

    final IncrementComparator<V,V> inc = (a, b) -> comparator.compare(a,b);

    return merge(iter1, iter2, inc);
  }

  /**
   * Iterates over triples of objects from three (sorted) iterators such that
   * for every returned triple a (from iter1), b (from iter2), c (from iter3)
   * satisfies the constraint that <code>comparator.compare(a,b) ==
   * comparator.compare(a,c) == 0</code>.  Internally, this function first
   * calls merge(iter1,iter2,comparatorA), and then merges that iterator
   * with the iter3 by comparing based on the value returned by iter1.
   *
   * This is used, e.g. to return lines from three input files that have
   * the same "key" as determined by the given comparator.
   */
  public static <V1,V2,V3> Iterable<Triple<V1,V2,V3>> merge(
      final Iterable<V1> iter1, final Iterable<V2> iter2, final Iterable<V3> iter3,
      final IncrementComparator<V1,V2> comparatorA,
      final IncrementComparator<V1,V3> comparatorB) {

    // partial merge on first two iterables
    Iterable<Pair<V1,V2>> partial = merge(iter1, iter2, comparatorA);

    IncrementComparator<Pair<V1,V2>,V3> inc =
      new IncrementComparator<Pair<V1,V2>,V3>() {
      public int compare(Pair<V1, V2> a, V3 b) {
        return comparatorB.compare(a.first, b);
      }
    };

    // flattens the pairs into triple
    Function<Pair<Pair<V1,V2>, V3>, Triple<V1,V2,V3>> flatten =
        in -> new Triple<>(in.first.first, in.first.second, in.second);

    return transform(merge(partial, iter3, inc), flatten);
  }

  /**
   * Same as {@link #merge(Iterable, Iterable, Iterable, IncrementComparator, IncrementComparator)}
   * but using the given (symmetric) comparator.
   */
  public static <V> Iterable<Triple<V,V,V>> merge(
      final Iterable<V> iter1, final Iterable<V> iter2, Iterable<V> iter3,
      final Comparator<V> comparator) {

    final IncrementComparator<V,V> inc = (a, b) -> comparator.compare(a,b);

    return merge(iter1, iter2, iter3, inc, inc);
  }

  /**
   * Groups consecutive elements from the given iterable based on the value
   * in the given comparator.  Each inner iterable will iterate over consecutive
   * items from the input until the comparator says that the next item is not
   * equal to the previous.
   */
  public static <V> Iterable<Iterable<V>> group(final Iterable<V> iterable,
      final Comparator<V> comparator) {

    return new Iterable<Iterable<V>>() {
      public Iterator<Iterable<V>> iterator() {
        return new Iterator<Iterable<V>>() {
          /** Actual iterator */
          Iterator<V> it = iterable.iterator();

          /** Next element to return */
          V next;

          public boolean hasNext() {
            return next != null || it.hasNext();
          }

          public Iterable<V> next() {
            return () -> new Iterator<V>() {
              V last = null;

              public boolean hasNext() {
                // get next if we need to and one is available
                if (next == null && it.hasNext()) {
                  next = it.next();
                }

                // if next and last both have values, compare them
                if (last != null && next != null) {
                  return comparator.compare(last, next) == 0;
                }

                // one of them was not null - have more if it was next
                return next != null;
              }

              public V next() {
                if (!hasNext()) {
                  throw new IllegalStateException("Didn't have next");
                }
                V rv = next;
                last = next;
                next = null;
                return rv;
              }

              public void remove() {
                throw new UnsupportedOperationException();
              }
            };
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  /**
   * Returns a string representation of the contents of calling toString
   * on each element of the given iterable, joining the elements together
   * with the given glue.
   */
  public static <E> String toString(Iterable<E> iter, String glue) {
    StringBuilder builder = new StringBuilder();
    for (Iterator<E> it = iter.iterator(); it.hasNext(); ) {
      builder.append(it.next());
      if (it.hasNext()) {
        builder.append(glue);
      }
    }
    return builder.toString();
  }

  /**
   * Sample k items uniformly from an Iterable of size n (without replacement).
   *
   * @param items  The items from which to sample.
   * @param n      The total number of items in the Iterable.
   * @param k      The number of items to sample.
   * @param random The random number generator.
   * @return       An Iterable of k items, chosen randomly from the original n items.
   */
  public static <T> Iterable<T> sample(Iterable<T> items, int n, int k, Random random) {

    // assemble a list of all indexes
    List<Integer> indexes = new ArrayList<>();
    for (int i = 0; i < n; ++i) {
      indexes.add(i);
    }

    // shuffle the indexes and select the first k
    Collections.shuffle(indexes, random);
    final Set<Integer> indexSet = Generics.newHashSet(indexes.subList(0, k));

    // filter down to only the items at the selected indexes
    return Iterables.filter(items, new Predicate<T>() {
      private int index = -1;
      public boolean test(T item) {
        ++this.index;
        return indexSet.contains(this.index);
      }
    });
  }

  //  /**
  //   * Returns a dummy collection wrapper for the Iterable that iterates
  //   * it once to get the size if requested.  If the underlying iterable
  //   * cannot be iterated more than once, you're out of luck.
  //   */
  //  public static <E> Collection<E> toCollection(final Iterable<E> iter) {
  //    return new AbstractCollection<E>() {
  //      int size = -1;
  //
  //      @Override
  //      public Iterator<E> iterator() {
  //        return iter.iterator();
  //      }
  //
  //      @Override
  //      public int size() {
  //        if (size < 0) {
  //          size = 0;
  //          for (E elem : iter) { size++; }
  //        }
  //        return size;
  //      }
  //    };
  //  }

  //
  //  public static <E,L extends List<E>> L toList(Iterable<E> iter, Class<L> type) {
  //    try {
  //      type.newInstance();
  //    } catch (InstantiationException e) {
  //      e.printStackTrace();
  //    } catch (IllegalAccessException e) {
  //      e.printStackTrace();
  //    }
  //  }

  /**
   * Creates an ArrayList containing all of the Objects returned by the given Iterator.
   */
  public static <T> ArrayList<T> asArrayList(Iterator<? extends T> iter) {
    ArrayList<T> al = new ArrayList<>();
    return (ArrayList<T>) addAll(iter, al);
  }

  /**
   * Creates a HashSet containing all of the Objects returned by the given Iterator.
   */
  public static <T> HashSet<T> asHashSet(Iterator<? extends T> iter) {
    HashSet<T> hs = new HashSet<>();
    return (HashSet<T>) addAll(iter, hs);
  }

  /**
   * Creates a new Collection from the given CollectionFactory, and adds all of the Objects
   * returned by the given Iterator.
   */
  public static <E> Collection<E> asCollection(Iterator<? extends E> iter, CollectionFactory<E> cf) {
    Collection<E> c = cf.newCollection();
    return addAll(iter, c);
  }

  /**
   * Adds all of the Objects returned by the given Iterator into the given Collection.
   *
   * @return the given Collection
   */
  public static <T> Collection<T> addAll(Iterator<? extends T> iter, Collection<T> c) {
    while (iter.hasNext()) {
      c.add(iter.next());
    }
    return c;
  }

  /**
   * For internal debugging purposes only.
   */
  public static void main(String[] args) {
    String[] test = {"a", "b", "c"};

    List<String> l = Arrays.asList(test);

    System.out.println(asArrayList(l.iterator()));

    System.out.println(asHashSet(l.iterator()));

    System.out.println(asCollection(l.iterator(), CollectionFactory.<String>hashSetFactory()));

    ArrayList<String> al = new ArrayList<>();
    al.add("d");
    System.out.println(addAll(l.iterator(), al));
  }

}
