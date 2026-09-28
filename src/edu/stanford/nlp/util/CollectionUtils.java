package edu.stanford.nlp.util; 
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

/**
 * Collection of useful static methods for working with Collections. Includes
 * methods to increment counts in maps and cast list/map elements to common
 * types.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class CollectionUtils  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CollectionUtils.class);

  /**
   * Private constructor to prevent direct instantiation.
   */
  private CollectionUtils() {
  }

  // Utils for making collections out of arrays of primitive types.

  /**
   * Returns a new list containing the elements of an int array, boxed.
   *
   * @param a The array
   * @return A new modifiable list of the values
   */
  public static List<Integer> asList(int[] a) {
    List<Integer> result = new ArrayList<>(a.length);
    for (int j : a) {
      result.add(Integer.valueOf(j));
    }
    return result;
  }

  /**
   * Returns a new list containing the elements of a double array, boxed.
   *
   * @param a The array
   * @return A new modifiable list of the values
   */
  public static List<Double> asList(double[] a) {
    List<Double> result = new ArrayList<>(a.length);
    for (double v : a) {
      result.add(Double.valueOf(v));
    }
    return result;
  }

  // Inverses of the above

  /**
   * Returns the elements of a collection as an int array, in iteration order.
   *
   * @param coll The collection; it must not contain null
   * @return A new array of the values
   */
  public static int[] asIntArray(Collection<Integer> coll) {
    int[] result = new int[coll.size()];
    int index = 0;
    for (Integer element : coll) {
      result[index] = element;
      index++;
    }

    return result;
  }

  /**
   * Returns the elements of a collection as a double array, in iteration order.
   *
   * @param coll The collection; it must not contain null
   * @return A new array of the values
   */
  public static double[] asDoubleArray(Collection<Double> coll) {
    double[] result = new double[coll.size()];
    int index = 0;
    for (Double element : coll) {
      result[index] = element;
      index++;
    }

    return result;
  }

  /**
   * Returns a new List containing the given objects.
   *
   * @param <T> The type of the items
   * @param items The items
   * @return A new modifiable ArrayList of the items
   */
  @SafeVarargs
  public static <T> List<T> makeList(T... items) {
    return new ArrayList<>(Arrays.asList(items));
  }

  /**
   * Returns a new Set containing all the objects in the specified array.
   *
   * @param <T> The type of the items
   * @param o The items
   * @return A new HashSet of the items
   */
  @SafeVarargs
  public static <T> Set<T> asSet(T... o) {
    return Generics.newHashSet(Arrays.asList(o));
  }

  /**
   * Returns the intersection of two sets.
   *
   * @param <T> The type of the items
   * @param set1 The first set
   * @param set2 The second set
   * @return A new set of the items of {@code set1} that are also in {@code set2}
   */
  public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
    Set<T> intersect = Generics.newHashSet();
    for (T t : set1) {
      if (set2.contains(t)) {
        intersect.add(t);
      }
    }
    return intersect;
  }

  /**
   * Returns the concatenation of two collections.  Duplicates are kept.
   *
   * @param <T> The type of the items
   * @param set1 The first collection
   * @param set2 The second collection
   * @return A new list of the items of {@code set1} followed by those of {@code set2}
   */
  public static <T> Collection<T> union(Collection<T> set1, Collection<T> set2) {
    Collection<T> union = new ArrayList<>();
    for (T t : set1) {
      union.add(t);
    }
    for (T t : set2) {
      union.add(t);
    }
    return union;
  }

  /**
   * Returns the union of two collections as a set.
   *
   * @param <T> The type of the items
   * @param set1 The first collection
   * @param set2 The second collection
   * @return A new set of the items in either collection
   */
  public static <T> Set<T> unionAsSet(Collection<T> set1, Collection<T> set2) {
    Set<T> union = Generics.newHashSet();
    for (T t : set1) {
      union.add(t);
    }
    for (T t : set2) {
      union.add(t);
    }
    return union;
  }

  /**
   * Returns the union of any number of collections as a set.
   *
   * @param <T> The type of the items
   * @param sets The collections
   * @return A new set of the items in any of the collections
   */
  @SafeVarargs
  public static <T> Set<T> unionAsSet(Collection<T>... sets) {
    Set<T> union = Generics.newHashSet();
    for(Collection<T> set: sets){
      for (T t : set) {
        union.add(t);
      }
    }
    return union;
  }

  /**
   * Returns all objects in list1 that are not in list2.
   *
   * @param <T> Type of items in the collection
   * @param list1 First collection
   * @param list2 Second collection
   * @return The collection difference list1 - list2
   */
  public static <T> Collection<T> diff(Collection<T> list1, Collection<T> list2) {
    Collection<T> diff = new ArrayList<>();
    for (T t : list1) {
      if (!list2.contains(t)) {
        diff.add(t);
      }
    }
    return diff;
  }

  /**
   * Returns all objects in list1 that are not in list2.
   *
   * @param <T> Type of items in the collection
   * @param list1 First collection
   * @param list2 Second collection
   * @return The collection difference list1 - list2
   */
  public static <T> Set<T> diffAsSet(Collection<T> list1, Collection<T> list2) {
    Set<T> diff = new HashSet<>();
    for (T t : list1) {
      if (!list2.contains(t)) {
        diff.add(t);
      }
    }
    return diff;
  }

  // Utils for loading and saving Collections to/from text files

  /**
   * Loads a collection from a file with one item per line, constructing each
   * item by passing the line to the String constructor of {@code c}.
   * Reading stops at the first empty line.  Lines that fail to construct are
   * logged and skipped.
   *
   * @param <T> The type of the items
   * @param filename The path to the file to load the List from
   * @param c The Class to instantiate each member of the List. Must have a
   *          String constructor.
   * @param cf The factory for the collection to return
   * @return The new collection
   * @throws Exception if {@code c} has no public String constructor or the file cannot be read
   */
  public static <T> Collection<T> loadCollection(String filename, Class<T> c, CollectionFactory<T> cf) throws Exception {
    return loadCollection(new File(filename), c, cf);
  }

  /**
   * Loads a collection from a file with one item per line, constructing each
   * item by passing the line to the String constructor of {@code c}.
   * Reading stops at the first empty line.  Lines that fail to construct are
   * logged and skipped.
   *
   * @param <T> The type of the items
   * @param file The file to load the List from
   * @param c The Class to instantiate each member of the List. Must have a
   *          String constructor.
   * @param cf The factory for the collection to return
   * @return The new collection
   * @throws Exception if {@code c} has no public String constructor or the file cannot be read
   */
  public static <T> Collection<T> loadCollection(File file, Class<T> c, CollectionFactory<T> cf) throws Exception {
    Constructor<T> m = c.getConstructor(new Class[] { String.class });
    Collection<T> result = cf.newCollection();
    BufferedReader in = new BufferedReader(new FileReader(file));
    String line = in.readLine();
    while (line != null && line.length() > 0) {
      try {
        T o = m.newInstance(line);
        result.add(o);
      } catch (Exception e) {
        log.info("Couldn't build object from line: " + line);
        e.printStackTrace();
      }
      line = in.readLine();
    }
    in.close();
    return result;
  }

  /**
   * Adds the items from the file to the collection.
   * Reading stops at the first empty line.
   *
   * @param <T> The type of the items.
   * @param fileName The name of the file from which items should be loaded.
   * @param itemClass The class of the items (must have a constructor that accepts a String).
   * @param collection The collection to which items should be added.
   * @throws NoSuchMethodException if {@code itemClass} has no public String constructor
   * @throws InstantiationException if {@code itemClass} is abstract
   * @throws IllegalAccessException if the constructor is inaccessible
   * @throws InvocationTargetException if the constructor throws an exception
   * @throws IOException if the file cannot be read
   */
  public static <T> void loadCollection(String fileName, Class<T> itemClass, Collection<T> collection) throws NoSuchMethodException, InstantiationException,
      IllegalAccessException, InvocationTargetException, IOException {
    loadCollection(new File(fileName), itemClass, collection);
  }

  /**
   * Adds the items from the file to the collection.
   * Reading stops at the first empty line.
   *
   * @param <T> The type of the items.
   * @param file The file from which items should be loaded.
   * @param itemClass The class of the items (must have a constructor that accepts a String).
   * @param collection The collection to which items should be added.
   * @throws NoSuchMethodException if {@code itemClass} has no public String constructor
   * @throws InstantiationException if {@code itemClass} is abstract
   * @throws IllegalAccessException if the constructor is inaccessible
   * @throws InvocationTargetException if the constructor throws an exception
   * @throws IOException if the file cannot be read
   */
  public static <T> void loadCollection(File file, Class<T> itemClass, Collection<T> collection) throws NoSuchMethodException, InstantiationException, IllegalAccessException,
      InvocationTargetException, IOException {
    Constructor<T> itemConstructor = itemClass.getConstructor(String.class);
    BufferedReader in = new BufferedReader(new FileReader(file));
    String line = in.readLine();
    while (line != null && line.length() > 0) {
      T t = itemConstructor.newInstance(line);
      collection.add(t);
      line = in.readLine();
    }
    in.close();
  }

  /**
   * Parses a map from a string in the format of {@link AbstractMap#toString()},
   * such as {@code {a=1, b=2}}.  The string is split on whitespace and the last
   * character of each field (the comma or closing brace) is dropped, so keys
   * and values must not contain whitespace or '='.  A field with no '=' gets
   * a value constructed from the empty string.
   *
   * @param <K> The type of the keys
   * @param <V> The type of the values
   * @param s The string to parse
   * @param keyClass The key class, which must have a public String constructor
   * @param valueClass The value class, which must have a public String constructor
   * @param mapFactory The factory for the map to return
   * @return The new map
   * @throws RuntimeException if {@code s} does not start with '{'
   * @throws ClassNotFoundException never; it is declared but not thrown
   * @throws NoSuchMethodException if either class lacks a public String constructor
   * @throws IllegalAccessException if a constructor is inaccessible
   * @throws InvocationTargetException if a constructor throws an exception
   * @throws InstantiationException if either class is abstract
   */
  public static <K, V> Map<K, V> getMapFromString(String s, Class<K> keyClass, Class<V> valueClass, MapFactory<K, V> mapFactory) throws ClassNotFoundException,
      NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Constructor<K> keyC = keyClass.getConstructor(new Class[] { String.class });
    Constructor<V> valueC = valueClass.getConstructor(new Class[] { String.class });
    if (s.charAt(0) != '{')
      throw new RuntimeException("");
    s = s.substring(1); // get rid of first brace
    String[] fields = s.split("\\s+");
    Map<K, V> m = mapFactory.newMap();
    // populate m
    for (int i = 0; i < fields.length; i++) {
      // log.info("Parsing " + fields[i]);
      fields[i] = fields[i].substring(0, fields[i].length() - 1); // get rid of
      // following
      // comma or
      // brace
      String[] a = fields[i].split("=");
      K key = keyC.newInstance(a[0]);
      V value;
      if (a.length > 1) {
        value = valueC.newInstance(a[1]);
      } else {
        value = valueC.newInstance("");
      }
      m.put(key, value);
    }
    return m;
  }

  /**
   * Checks whether a Collection contains a specified Object. Object equality
   * (==), rather than .equals(), is used.
   *
   * @param <T> The type of the items
   * @param c The collection to search
   * @param o The object to look for
   * @return Whether {@code c} contains {@code o} itself
   */
  public static <T> boolean containsObject(Collection<T> c, T o) {
    for (Object o1 : c) {
      if (o == o1) {
        return true;
      }
    }
    return false;
  }

  /**
   * Removes the first occurrence in the list of the specified object, using
   * object identity (==) not equality as the criterion for object presence. If
   * this list does not contain the element, it is unchanged.
   *
   * @param <T> The type of the items
   * @param l The {@link List} from which to remove the object
   * @param o The object to be removed.
   * @return Whether or not the List was changed.
   */
  public static <T> boolean removeObject(List<T> l, T o) {
    int i = 0;
    for (Object o1 : l) {
      if (o == o1) {
        l.remove(i);
        return true;
      } else
        i++;
    }
    return false;
  }

  /**
   * Returns the index of the first occurrence in the list of the specified
   * object, using object identity (==) not equality as the criterion for object
   * presence. If this list does not contain the element, return -1.
   *
   * @param <T> The type of the items
   * @param l The {@link List} to find the object in.
   * @param o The sought-after object.
   * @return The index of {@code o}, or -1 if it is not in the list
   */
  public static <T> int getIndex(List<T> l, T o) {
    int i = 0;
    for (Object o1 : l) {
      if (o == o1)
        return i;
      else
        i++;
    }
    return -1;
  }

  /**
   * Returns the index of the first occurrence at or after fromIndex (inclusive)
   * in the list of the specified object, using object equals function. If this
   * list does not contain the element, return -1.
   *
   * @param <T> The type of the items
   * @param l The {@link List} to find the object in.
   * @param o The sought-after object; must not be null
   * @param fromIndex The start index
   * @return The index of the first match at or after {@code fromIndex}, or -1
   */
  public static <T> int getIndex(List<T> l, T o, int fromIndex) {
    int i = -1;
    for (T o1 : l) {
      i++;
      if (i < fromIndex) {
        continue;
      }
      if (o.equals(o1)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Samples without replacement from a collection.
   *
   * @param <E> The type of the items
   * @param c The collection to be sampled from
   * @param n The number of samples to take
   * @return a new collection with the sample
   */
  public static <E> Collection<E> sampleWithoutReplacement(Collection<E> c, int n) {
    return sampleWithoutReplacement(c, n, new Random());
  }

  /**
   * Samples without replacement from a collection, using your own
   * {@link Random} number generator.
   *
   * @param <E> The type of the items
   * @param c The collection to be sampled from
   * @param n The number of samples to take
   * @param r The random number generator
   * @return a new collection with the sample
   * @throws IllegalArgumentException if {@code n} is negative or larger than the collection
   */
  public static <E> Collection<E> sampleWithoutReplacement(Collection<E> c, int n, Random r) {
    if (n < 0)
      throw new IllegalArgumentException("n < 0: " + n);
    if (n > c.size())
      throw new IllegalArgumentException("n > size of collection: " + n + ", " + c.size());
    List<E> copy = new ArrayList<>(c.size());
    copy.addAll(c);
    Collection<E> result = new ArrayList<>(n);
    for (int k = 0; k < n; k++) {
      double d = r.nextDouble();
      int x = (int) (d * copy.size());
      result.add(copy.remove(x));
    }
    return result;
  }

  /**
   * Returns a uniformly random element of a list.
   *
   * @param <E> The type of the items
   * @param l The list; must not be empty
   * @param r The random number generator
   * @return A random element of {@code l}
   */
  public static <E> E sample(List<E> l, Random r) {
    int i = r.nextInt(l.size());
    return l.get(i);
  }

  /**
   * Samples with replacement from a collection.
   *
   * @param <E> The type of the items
   * @param c The collection to be sampled from
   * @param n The number of samples to take
   * @return a new collection with the sample
   */
  public static <E> Collection<E> sampleWithReplacement(Collection<E> c, int n) {
    return sampleWithReplacement(c, n, new Random());
  }

  /**
   * Samples with replacement from a collection, using your own {@link Random}
   * number generator.
   *
   * @param <E> The type of the items
   * @param c The collection to be sampled from
   * @param n The number of samples to take
   * @param r The random number generator
   * @return a new collection with the sample
   */
  public static <E> Collection<E> sampleWithReplacement(Collection<E> c, int n, Random r) {
    if (n < 0)
      throw new IllegalArgumentException("n < 0: " + n);
    List<E> copy = new ArrayList<>(c.size());
    copy.addAll(c);
    Collection<E> result = new ArrayList<>(n);
    for (int k = 0; k < n; k++) {
      double d = r.nextDouble();
      int x = (int) (d * copy.size());
      result.add(copy.get(x));
    }
    return result;
  }

  /**
   * Returns true iff l1 is a sublist of l (i.e., every member of l1 is in l,
   * and for every e1 &lt; e2 in l1, there is an e1 &lt; e2 occurrence in l).
   *
   * @param <T> The type of the items
   * @param l1 The candidate subsequence
   * @param l The list to search in
   * @return Whether the items of {@code l1} occur in {@code l} in the same order
   */
  public static <T> boolean isSubList(List<T> l1, List<? super T> l) {
    Iterator<? super T> it = l.iterator();
    for (T o1 : l1) {
      if (!it.hasNext()) {
        return false;
      }
      Object o = it.next();
      while ((o == null && !(o1 == null)) || (o != null && !o.equals(o1))) {
        if (!it.hasNext()) {
          return false;
        }
        o = it.next();
      }
    }
    return true;
  }

  /**
   * Returns the entries of a map as {@code key=value} lines, each ending in a newline.
   *
   * @param <K> The type of the keys
   * @param <V> The type of the values
   * @param m The map
   * @return The string of entries, in the map's iteration order
   */
  public static <K, V> String toVerticalString(Map<K, V> m) {
    StringBuilder b = new StringBuilder();
    Set<Map.Entry<K, V>> entries = m.entrySet();
    for (Map.Entry<K, V> e : entries) {
      b.append(e.getKey()).append('=').append(e.getValue()).append('\n');
    }
    return b.toString();
  }

  /**
   * Provides a consistent ordering over lists. First compares by the first
   * element. If that element is equal, the next element is considered, and so
   * on.  A list that is a prefix of the other comes first.
   *
   * @param <T> The type of the items
   * @param list1 The first list
   * @param list2 The second list
   * @return A negative number, zero, or a positive number as {@code list1} is
   *     less than, equal to, or greater than {@code list2}
   * @throws IllegalArgumentException if exactly one of the lists is null
   */
  public static <T extends Comparable<T>> int compareLists(List<T> list1, List<T> list2) {
    if (list1 == null && list2 == null)
      return 0;
    if (list1 == null || list2 == null) {
      throw new IllegalArgumentException();
    }
    int size1 = list1.size();
    int size2 = list2.size();
    int size = Math.min(size1, size2);
    for (int i = 0; i < size; i++) {
      int c = list1.get(i).compareTo(list2.get(i));
      if (c != 0)
        return c;
    }
    if (size1 < size2)
      return -1;
    if (size1 > size2)
      return 1;
    return 0;
  }

  /**
   * Returns a comparator that orders lists by {@link #compareLists}.
   *
   * @param <C> The type of the list items
   * @return A lexicographic list comparator
   */
  public static <C extends Comparable<C>> Comparator<List<C>> getListComparator() {
    return CollectionUtils::compareLists;
  }

  /**
   * Return the items of an Iterable as a sorted list.
   *
   * @param <T> The type of items in the Iterable.
   * @param items The collection to be sorted.
   * @return A list containing the same items as the Iterable, but sorted.
   */
  public static <T extends Comparable<T>> List<T> sorted(Iterable<T> items) {
    List<T> result = toList(items);
    Collections.sort(result);
    return result;
  }

  /**
   * Return the items of an Iterable as a sorted list.
   *
   * @param <T> The type of items in the Iterable.
   * @param items The collection to be sorted.
   * @param comparator The comparator that determines the order
   * @return A list containing the same items as the Iterable, but sorted.
   */
  public static <T> List<T> sorted(Iterable<T> items, Comparator<T> comparator) {
    List<T> result = toList(items);
    Collections.sort(result, comparator);
    return result;
  }

  /**
   * Create a list out of the items in the Iterable.
   *
   * @param <T> The type of items in the Iterable.
   * @param items The items to be made into a list.
   * @return A list consisting of the items of the Iterable, in the same order.
   */
  public static <T> List<T> toList(Iterable<T> items) {
    List<T> list = new ArrayList<>();
    addAll(list, items);
    return list;
  }

  /**
   * Create a set out of the items in the Iterable.
   *
   * @param <T> The type of items in the Iterable.
   * @param items The items to be made into a set.
   * @return A set consisting of the items from the Iterable.
   */
  public static <T> Set<T> toSet(Iterable<T> items) {
    Set<T> set = Generics.newHashSet();
    addAll(set, items);
    return set;
  }

  /**
   * Add all the items from an iterable to a collection.
   *
   * @param <T> The type of items in the iterable and the collection
   * @param collection The collection to which the items should be added.
   * @param items The items to add to the collection.
   */
  public static <T> void addAll(Collection<T> collection, Iterable<? extends T> items) {
    for (T item : items) {
      collection.add(item);
    }
  }

  /**
   * Get all sub-lists of the given list of the given sizes.
   *
   * For example:
   *
   * <pre>
   * List&lt;String&gt; items = Arrays.asList(&quot;a&quot;, &quot;b&quot;, &quot;c&quot;, &quot;d&quot;);
   * System.out.println(CollectionUtils.getNGrams(items, 1, 2));
   * </pre>
   *
   * would print out:
   *
   * <pre>
   * [[a], [a, b], [b], [b, c], [c], [c, d], [d]]
   * </pre>
   *
   * @param <T> The type of items contained in the list.
   * @param items The list of items.
   * @param minSize The minimum size of an ngram.
   * @param maxSize The maximum size of an ngram.
   * @return All sub-lists of the given sizes.
   */
  public static <T> List<List<T>> getNGrams(List<T> items, int minSize, int maxSize) {
    List<List<T>> ngrams = new ArrayList<>();
    int listSize = items.size();
    for (int i = 0; i < listSize; ++i) {
      for (int ngramSize = minSize; ngramSize <= maxSize; ++ngramSize) {
        if (i + ngramSize <= listSize) {
          List<T> ngram = new ArrayList<>();
          for (int j = i; j < i + ngramSize; ++j) {
            ngram.add(items.get(j));
          }
          ngrams.add(ngram);
        }
      }
    }
    return ngrams;
  }

  /**
   * Get all prefix/suffix combinations from a list. It can extract just
   * prefixes, just suffixes, or prefixes and suffixes of the same length.
   *
   * For example:
   *
   * <pre>
   * List&lt;String&gt; items = Arrays.asList(&quot;a&quot;, &quot;b&quot;, &quot;c&quot;, &quot;d&quot;);
   * System.out.println(CollectionUtils.getPrefixesAndSuffixes(items, 1, 2, null, true, true));
   * </pre>
   *
   * would print out:
   *
   * <pre>
   * [[d], [a], [a, d], [d, c], [a, b], [a, b, c, d]]
   * </pre>
   *
   * and
   *
   * <pre>
   * List&lt;String&gt; items2 = Arrays.asList(&quot;a&quot;);
   * System.out.println(CollectionUtils.getPrefixesAndSuffixes(items2, 1, 2, null, true, true));
   * </pre>
   *
   * would print:
   *
   * <pre>
   * [[a], [a], [a, a], [a, null], [a, null], [a, null, a, null]]
   * </pre>
   *
   * @param <T> The type of items contained in the list.
   * @param items The list of items.
   * @param minSize The minimum length of a prefix/suffix span (should be at least 1)
   * @param maxSize The maximum length of a prefix/suffix span
   * @param paddingSymbol Symbol to be included if we run out of bounds (e.g. if items has
   *          size 3 and we try to extract a span of length 4).
   * @param includePrefixes Whether to extract prefixes
   * @param includeSuffixes Whether to extract suffixes
   * @return All prefix/suffix combinations of the given sizes.
   */
  public static <T> List<List<T>> getPrefixesAndSuffixes(List<T> items, int minSize, int maxSize, T paddingSymbol, boolean includePrefixes, boolean includeSuffixes) {
    assert minSize > 0;
    assert maxSize >= minSize;
    assert includePrefixes || includeSuffixes;

    List<List<T>> prefixesAndSuffixes = new ArrayList<>();
    for (int span = minSize - 1; span < maxSize; span++) {
      List<Integer> indices = new ArrayList<>();
      List<T> seq = new ArrayList<>();
      if (includePrefixes) {
        for (int i = 0; i <= span; i++) {
          indices.add(i);
        }
      }
      if (includeSuffixes) {
        int maxIndex = items.size() - 1;
        for (int i = span; i >= 0; i--) {
          indices.add(maxIndex - i);
        }
      }

      for (int i : indices) {
        try {
          seq.add(items.get(i));
        } catch (IndexOutOfBoundsException ioobe) {
          seq.add(paddingSymbol);
        }
      }

      prefixesAndSuffixes.add(seq);
    }

    return prefixesAndSuffixes;
  }

  /**
   * Replaces spans of a list with aggregated items.  Each matched object is
   * converted to a token interval by {@code toIntervalFunc} and then the list
   * is merged as by {@link #mergeList(List, List, Function)}.
   *
   * @param <T> The type of the list items
   * @param <M> The type of the matched objects
   * @param list The items to merge
   * @param matched The matched objects giving the spans to merge
   * @param toIntervalFunc Converts a matched object to a span {@code [begin, end)} of {@code list}
   * @param aggregator Combines the items in a span into one item
   * @return A new list with each span replaced by its aggregate
   */
  public static <T, M> List<T> mergeList(List<? extends T> list, Collection<M> matched, Function<M, Interval<Integer>> toIntervalFunc, Function<List<? extends T>, T> aggregator) {
    List<Interval<Integer>> matchedIntervals = new ArrayList<>(matched.size());
    for (M m : matched) {
      matchedIntervals.add(toIntervalFunc.apply(m));
    }
    return mergeList(list, matchedIntervals, aggregator);
  }

  /**
   * Replaces spans of a list with aggregated items.  Sorts {@code matched} in
   * place by {@link HasInterval#ENDPOINTS_COMPARATOR} and then merges as by
   * {@link #mergeListWithSortedMatched}.
   *
   * @param <T> The type of the list items
   * @param list The items to merge
   * @param matched The spans {@code [begin, end)} of {@code list} to merge; this list is sorted
   * @param aggregator Combines the items in a span into one item
   * @return A new list with each span replaced by its aggregate
   */
  public static <T> List<T> mergeList(List<? extends T> list, List<? extends HasInterval<Integer>> matched, Function<List<? extends T>, T> aggregator) {
    Collections.sort(matched, HasInterval.ENDPOINTS_COMPARATOR);
    return mergeListWithSortedMatched(list, matched, aggregator);
  }

  /**
   * Replaces spans of a list with aggregated items.  Items outside the spans
   * are copied unchanged.  A span that starts before the end of the previous
   * merged span is skipped.
   *
   * @param <T> The type of the list items
   * @param list The items to merge
   * @param matched The spans {@code [begin, end)} of {@code list} to merge, sorted by start
   * @param aggregator Combines the items in a span into one item
   * @return A new list with each span replaced by its aggregate
   */
  public static <T> List<T> mergeListWithSortedMatched(List<? extends T> list, List<? extends HasInterval<Integer>> matched, Function<List<? extends T>, T> aggregator) {
    List<T> merged = new ArrayList<>(list.size()); // Approximate size
    int last = 0;
    for (HasInterval<Integer> m : matched) {
      Interval<Integer> interval = m.getInterval();
      int start = interval.getBegin();
      int end = interval.getEnd();
      if (start >= last) {
        merged.addAll(list.subList(last, start));
        T t = aggregator.apply(list.subList(start, end));
        merged.add(t);
        last = end;
      }
    }
    // Add rest of elements
    if (last < list.size()) {
      merged.addAll(list.subList(last, list.size()));
    }
    return merged;
  }

  /**
   * Replaces spans of a list with items that have already been aggregated.
   * Items outside the spans are copied unchanged.  A matched item whose span
   * starts before the end of the previous merged span is skipped.
   *
   * @param <T> The type of the list items
   * @param list The items to merge
   * @param matched The aggregated items, sorted by the start of their spans
   * @param toIntervalFunc Gives the span {@code [begin, end)} of {@code list} that a matched item replaces
   * @return A new list with each span replaced by its matched item
   */
  public static <T> List<T> mergeListWithSortedMatchedPreAggregated(List<? extends T> list, List<? extends T> matched, Function<T, Interval<Integer>> toIntervalFunc) {
    List<T> merged = new ArrayList<>(list.size()); // Approximate size
    int last = 0;
    for (T m : matched) {
      Interval<Integer> interval = toIntervalFunc.apply(m);
      int start = interval.getBegin();
      int end = interval.getEnd();
      if (start >= last) {
        merged.addAll(list.subList(last, start));
        merged.add(m);
        last = end;
      }
    }
    // Add rest of elements
    if (last < list.size()) {
      merged.addAll(list.subList(last, list.size()));
    }
    return merged;
  }

  /**
   * Combines all the lists in a collection to a single list.
   *
   * @param <T> The type of the items
   * @param nestedList The lists to combine
   * @return A new list of all the items, in order
   */
  public static <T> List<T> flatten(Collection<List<T>> nestedList) {
    List<T> result = new ArrayList<>();
    for (List<T> list : nestedList) {
      result.addAll(list);
    }
    return result;
  }

  /**
   * Makes it possible to uniquify a collection of objects which are normally
   * non-hashable. Alternatively, it lets you define an alternate hash function
   * for them for limited-use hashing.
   * When several objects have the same hash, the last one is kept.
   *
   * @param <ObjType> The type of the objects
   * @param <Hashable> The type of the keys computed by {@code customHasher}
   * @param objects The objects to uniquify
   * @param customHasher Computes the key that determines whether two objects are the same
   * @return The values view of an internal map, with one object per distinct key
   */
  public static <ObjType, Hashable> Collection<ObjType> uniqueNonhashableObjects(Collection<ObjType> objects, Function<ObjType, Hashable> customHasher) {
    Map<Hashable, ObjType> hashesToObjects = Generics.newHashMap();
    for (ObjType object : objects) {
      hashesToObjects.put(customHasher.apply(object), object);
    }
    return hashesToObjects.values();
  }

  /**
   * if any item in toCheck is present in collection
   *
   * @param <T> The type of the items
   * @param collection The collection to search
   * @param toCheck The items to look for
   * @return true if any item of {@code toCheck} is in {@code collection}
   */
  public static <T> boolean containsAny(Collection<T> collection, Collection<T> toCheck){
    for(T c: toCheck){
      if(collection.contains(c))
        return true;
    }
    return false;

  }

  /**
   * Split a list into numFolds (roughly) equally sized folds. The earlier folds
   * may have one more item in them than later folds.
   * <br>
   * The lists returned are subList()s of the original list.
   * Therefore, don't try to modify the sublists, and don't modify the
   * original list while the sublists are in use.
   *
   * @param <T> The type of the items
   * @param values The list to split
   * @param numFolds The number of folds
   * @return A list of numFolds sublists covering {@code values} in order
   */
  public static <T> List<List<T>> partitionIntoFolds(List<T> values, int numFolds) {
    List<List<T>> folds = Generics.newArrayList();
    int numValues = values.size();
    int foldSize = numValues / numFolds;
    int remainder = numValues % numFolds;

    int start = 0;
    int end = foldSize;
    for (int foldNum = 0; foldNum < numFolds; foldNum++) {
      // if we're in the first 'remainder' folds, we get an extra item
      if (foldNum < remainder) {
        end++;
      }
      folds.add(values.subList(start, end));

      start = end;
      end += foldSize;
    }

    return folds;
  }

  /**
   * Split a list into train, test pairs for use in k-fold crossvalidation. This
   * returns a list of numFold (train, test) pairs where each train list will
   * contain (numFolds-1)/numFolds of the original values and the test list will
   * contain the remaining 1/numFolds of the original values.
   * The folds are made by {@link #partitionIntoFolds}.
   *
   * @param <T> The type of the items
   * @param values The list to split
   * @param numFolds The number of folds
   * @return The (train, test) pairs; each test collection is a sublist view of {@code values}
   */
  public static <T> Collection<Pair<Collection<T>, Collection<T>>> trainTestFoldsForCV(List<T> values, int numFolds) {
    Collection<Pair<Collection<T>, Collection<T>>> trainTestPairs = new ArrayList<>();
    List<List<T>> folds = partitionIntoFolds(values, numFolds);
    for (int splitNum = 0; splitNum < numFolds; splitNum++) {
      Collection<T> test = folds.get(splitNum);
      Collection<T> train = new ArrayList<>();
      for (int foldNum = 0; foldNum < numFolds; foldNum++) {
        if (foldNum != splitNum) {
          train.addAll(folds.get(foldNum));
        }
      }

      trainTestPairs.add(new Pair<>(train, test));
    }

    return trainTestPairs;
  }

  /**
   * Returns a list of all modes in the Collection.  (If the Collection has multiple items with the
   * highest frequency, all of them will be returned.)
   *
   * @param <T> The type of the items
   * @param values The collection; must not be empty
   * @return The set of the most frequent items
   */
  public static <T> Set<T> modes(Collection<T> values) {
    Counter<T> counter = new ClassicCounter<>(values);
    List<Double> sortedCounts = CollectionUtils.sorted(counter.values());
    Double highestCount = sortedCounts.get(sortedCounts.size() - 1);
    Counters.retainAbove(counter, highestCount);
    return counter.keySet();
  }

  /**
   * Returns the mode in the Collection.  If the Collection has multiple modes, this method picks one
   * arbitrarily.
   *
   * @param <T> The type of the items
   * @param values The collection; must not be empty
   * @return A most frequent item
   */
  public static <T> T mode(Collection<T> values) {
    Set<T> modes = modes(values);
    return modes.iterator().next();
  }


  /**
   * Transforms the keyset of collection according to the given Function and returns a set of the keys.
   *
   * @param <T1> The type of the original items
   * @param <T2> The type of the transformed items
   * @param original The items to transform
   * @param f The function to apply to each item
   * @return A new set of the transformed items
   */
  public static<T1, T2> Set<T2> transformAsSet(Collection<? extends T1> original, Function<T1, ? extends T2> f){
    Set<T2> transformed = Generics.newHashSet();
    for(T1 t: original){
      transformed.add(f.apply(t));
    }
    return transformed;
  }


  /**
   * Transforms the keyset of collection according to the given Function and returns a list.
   *
   * @param <T1> The type of the original items
   * @param <T2> The type of the transformed items
   * @param original The items to transform
   * @param f The function to apply to each item
   * @return A new list of the transformed items, in iteration order
   */
  public static<T1, T2> List<T2> transformAsList(Collection<? extends T1> original, Function<T1, ? extends T2> f){
    List<T2> transformed = new ArrayList<>();
    for(T1 t: original){
      transformed.add(f.apply(t));
    }
    return transformed;
  }

  /**
   * Filters the objects in the collection according to the given Filter and returns a list.
   *
   * @param <T> The type of the items
   * @param original The items to filter
   * @param f The predicate that items must satisfy to be kept
   * @return A new list of the items that satisfy {@code f}, in iteration order
   */
  public static<T> List<T> filterAsList(Collection<? extends T> original, Predicate<? super T> f){
    List<T> transformed = new ArrayList<>();
    for (T t: original) {
      if (f.test(t)) {
        transformed.add(t);
      }
    }
    return transformed;
  }

  /**
   * Get all values corresponding to the indices (if they exist in the map).
   *
   * @param <T> The type of the keys
   * @param <V> The type of the values
   * @param map Any map from T to V
   * @param indices A collection of indices of type T
   * @return The corresponding list of values of type V
   */
  public static<T,V> List<V> getAll(Map<T, V> map, Collection<T> indices) {
    List<V> result = new ArrayList<>();
    for(T i: indices)
      if(map.containsKey(i)){
        result.add(map.get(i));
      }
    return result;
  }

  /**
   * Returns the index of the largest item in a list.  Ties go to the earliest.
   *
   * @param <T> The type of the items
   * @param list The list
   * @return The index of the maximum, or -1 if the list is empty
   */
  public static<T extends Comparable<? super T>> int maxIndex(List<T> list){
   T max = null;
   int i = 0;
   int maxIndex = -1;
   for(T t: list)
   {
     if(max == null || t.compareTo(max) > 0)
     {
       max = t;
       maxIndex = i;
     }
     i++;
   }
   return maxIndex;
  }


  /**
   * Concatenate a number of iterators together, to form one big iterator.
   * This should respect the remove() functionality of the constituent iterators.
   *
   * @param iterators The iterators to concatenate.
   * @param <E> The type of the iterators.
   * @return An iterator consisting of all the component iterators concatenated together in order.
   */
  @SafeVarargs
  public static <E> Iterator<E> concatIterators(final Iterator<E>... iterators) {
    return new Iterator<E>() {
      Iterator<E> lastIter = null;
      List<Iterator<E>> iters = new LinkedList<>(Arrays.asList(iterators));
      @Override
      public boolean hasNext() {
        return !iters.isEmpty() && iters.get(0).hasNext();
      }
      @Override
      public E next() {
        if (!hasNext()) {
          throw new IllegalArgumentException("Iterator is empty!");
        }
        E next = iters.get(0).next();
        lastIter = iters.get(0);
        while (!iters.isEmpty() && !iters.get(0).hasNext()) {
          iters.remove(0);
        }
        return next;
      }
      @Override
      public void remove() {
        if (lastIter == null) {
          throw new IllegalStateException("Call next() before calling remove()!");
        }
        lastIter.remove();
      }
    };
  }

  /**
   * Wraps an Enumeration as an Iterator.  The iterator does not support remove.
   *
   * @param <E> The type of the elements
   * @param lst_ The enumeration
   * @return An iterator over the enumeration's elements
   */
  public static <E> Iterator<E> iteratorFromEnumerator(final Enumeration<E> lst_) {
    return new Iterator<E>() {
      private final Enumeration<E> lst = lst_;
      @Override
      public boolean hasNext() {
        return lst.hasMoreElements();
      }

      @Override
      public E next() {
        return lst.nextElement();
      }
    };
  }

  /**
   * Wraps an Enumeration as an Iterable.  The Iterable reads from the same
   * enumeration each time, so it can only be iterated once.
   *
   * @param <E> The type of the elements
   * @param lst The enumeration
   * @return An iterable over the enumeration's elements
   */
  public static <E> Iterable<E> iterableFromEnumerator(final Enumeration<E> lst) {
    return new IterableIterator<>(iteratorFromEnumerator(lst));
  }


}
