package old.edu.stanford.nlp.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Collection of useful static methods for working with Collections. Includes
 * methods to increment counts in maps and cast list/map elements to common
 * types.
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 */
public class CollectionUtils {
  /**
   * Private constructor to prevent direct instantiation.
   */
  private CollectionUtils() {
  }

  // Utils for making collections out of arrays of primitive types.

  public static List<Integer> asList(int[] a) {
    List<Integer> result = new ArrayList<Integer>();
    for (int i = 0; i < a.length; i++) {
      result.add(Integer.valueOf(a[i]));
    }
    return result;
  }

  public static List<Double> asList(double[] a) {
    List<Double> result = new ArrayList<Double>();
    for (int i = 0; i < a.length; i++) {
      result.add(new Double(a[i]));
    }
    return result;
  }

  /** Returns a new List containing the specified objects. */
  public static List<Object> asList(Object ... args) {
    List<Object> result = new ArrayList<Object>();
    for (int i = 0; i < args.length; i++) {
      result.add(args[i]);
    }
    return result;
  }

  /** Returns a new List containing the given object. */
  public static <T> List<T> makeList(T e) {
    List<T> s = new ArrayList<T>();
    s.add(e);
    return s;
  }

  /** Returns a new List containing the given objects. */
  public static <T> List<T> makeList(T e1, T e2) {
    List<T> s = new ArrayList<T>();
    s.add(e1);
    s.add(e2);
    return s;
  }

  /** Returns a new List containing the given objects. */
  public static <T> List<T> makeList(T e1, T e2, T e3) {
    List<T> s = new ArrayList<T>();
    s.add(e1);
    s.add(e2);
    s.add(e3);
    return s;
  }

  /** Returns a new Set containing all the objects in the specified array. */
  public static <T> Set<T> asSet(T[] o) {
    return new HashSet<T>(Arrays.asList(o));
  }

  public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
    Set<T> intersect = new HashSet<T>();
    for (T t : set1) {
      if (set2.contains(t)) {
        intersect.add(t);
      }
    }
    return intersect;
  }
  
  // Utils for loading and saving Collections to/from text files

  /**
   * @param filename the path to the file to load the List from
   * @param c        the Class to instantiate each member of the List. Must have a String constructor.
   */
  public static <T> Collection<T> loadCollection(String filename, Class<T> c, CollectionFactory<T> cf) throws Exception {
    return loadCollection(new File(filename), c, cf);
  }
  /**
   * @param file     the file to load the List from
   * @param c        the Class to instantiate each member of the List. Must have a String constructor.
   */
  public static <T> Collection<T> loadCollection(File file, Class<T> c, CollectionFactory<T> cf) throws Exception {
    Constructor<T> m = c.getConstructor(new Class[]{Class.forName("java.lang.String")});
    Collection<T> result = cf.newCollection();
    BufferedReader in = new BufferedReader(new FileReader(file));
    String line = in.readLine();
    while (line != null && line.length()>0) {
      try {
        T o = m.newInstance(line);
        result.add(o);
      } catch (Exception e) {
        System.err.println("Couldn't build object from line: " + line);
        e.printStackTrace();
      }
      line = in.readLine();
    }
    in.close();
    return result;
  }
  
  /**
   * Adds the items from the file to the collection.
   * 
   * @param <T>        The type of the items.
   * @param fileName   The name of the file from which items should be loaded.
   * @param itemClass  The class of the items (must have a constructor that accepts a String).
   * @param collection The collection to which items should be added.
   */
  public static <T> void loadCollection(String fileName, Class<T> itemClass, Collection<T> collection)
  throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
    loadCollection(new File(fileName), itemClass, collection);
  }

  /**
   * Adds the items from the file to the collection.
   * 
   * @param <T>        The type of the items.
   * @param file       The file from which items should be loaded.
   * @param itemClass  The class of the items (must have a constructor that accepts a String).
   * @param collection The collection to which items should be added.
   */
  public static <T> void loadCollection(File file, Class<T> itemClass, Collection<T> collection)
  throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, IOException {
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

  public static <K,V> Map<K, V> getMapFromString(String s, Class<K> keyClass, Class<V> valueClass, MapFactory<K, V> mapFactory) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
    Constructor<K> keyC = keyClass.getConstructor(new Class[]{Class.forName("java.lang.String")});
    Constructor<V> valueC = valueClass.getConstructor(new Class[]{Class.forName("java.lang.String")});
    if (s.charAt(0)!='{')
      throw new RuntimeException("");
    s = s.substring(1); // get rid of first brace
    String[] fields = s.split("\\s+");
    Map<K, V> m = mapFactory.newMap();
    // populate m
    for (int i=0; i<fields.length; i++) {
//      System.err.println("Parsing " + fields[i]);
      fields[i] = fields[i].substring(0, fields[i].length()-1); // get rid of following comma or brace
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
   * Checks whether a Collection contains a specified Object.  Object equality (==), rather than .equals(), is used.
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
   * Removes the first occurrence in the list of the specified object, using object identity (==) not equality as the criterion
   * for object presence. If this list does not contain the element, it is unchanged.
   *
   * @param l The {@link List} from which to remove the object
   * @param o The object to be removed.
   * @return Whether or not the List was changed.
   */
  public static <T> boolean removeObject(List<T> l, T o) {
    int i = 0;
    for(Object o1 : l) {
      if(o == o1) {
        l.remove(i);
        return true;
      }
      else
        i++;
    }
    return false;
  }


  /**
   * Returns the index of the first occurrence in the list of the specified object, using object identity (==) not equality as the criterion
   * for object presence. If this list does not contain the element, return -1.
   *
   * @param l The {@link List} to find the object in.
   * @param o The sought-after object.
   * @return Whether or not the List was changed.
   */
  public static <T> int getIndex(List<T> l, T o) {
    int i = 0;
    for(Object o1 : l) {
      if(o == o1)
        return i;
      else
        i++;
    }
    return -1;
  }

  /**
   * Samples without replacement from a collection.
   *
   * @param c The collection to be sampled from
   * @param n The number of samples to take
   * @return a new collection with the sample
   */
  public static <E> Collection<E> sampleWithoutReplacement(Collection<E> c, int n) {
    return sampleWithoutReplacement(c, n, new Random());
  }

  /**
   * Samples without replacement from a collection, using your own {@link
   * Random} number generator.
   *
   * @param c The collection to be sampled from
   * @param n The number of samples to take
   * @param r the random number generator
   * @return a new collection with the sample
   */
  public static <E> Collection<E> sampleWithoutReplacement(Collection<E> c, int n, Random r) {
    if (n < 0)
      throw new IllegalArgumentException("n < 0: " + n);
    if (n > c.size())
      throw new IllegalArgumentException("n > size of collection: " + n + ", " + c.size());
    List<E> copy = new ArrayList<E>(c.size());
    copy.addAll(c);
    Collection<E> result = new ArrayList<E>(n);
    for(int k = 0; k < n; k++) {
      double d = r.nextDouble();
      int x = (int) (d * copy.size());
      result.add(copy.remove(x));
    }
    return result;
  }

  public static <E> E sample (List<E> l, Random r) {
    int i = r.nextInt(l.size());
    return l.get(i);
  }

  /**
   * Samples with replacement from a collection
   * @param c The collection to be sampled from
   * @param n The number of samples to take
   * @return a new collection with the sample
   */
  public static <E> Collection<E> sampleWithReplacement(Collection<E> c, int n) {
    return sampleWithReplacement(c, n, new Random());
  }

  /**
   * Samples with replacement from a collection, using your own {@link Random} number generator
   * @param c The collection to be sampled from
   * @param n The number of samples to take
   * @param r the random number generator
   * @return a new collection with the sample
   */
  public static <E> Collection<E> sampleWithReplacement(Collection<E> c, int n, Random r) {
    if (n < 0)
      throw new IllegalArgumentException("n < 0: " + n);
    List<E> copy = new ArrayList<E>(c.size());
    copy.addAll(c);
    Collection<E> result = new ArrayList<E>(n);
    for(int k = 0; k < n; k++) {
      double d = r.nextDouble();
      int x = (int) (d*copy.size());
      result.add(copy.get(x));
    }
    return result;
  }

  /**
   * Returns true iff l1 is a sublist of l (i.e., every member of l1 is in l, and for every e1 < e2 in l1, there is
   * an e1 < e2 occurrence in l).
   */
  public static <T> boolean isSubList(List<T> l1, List<? super T> l) {
    Iterator<? super T> it = l.iterator();
    Iterator<T> it1 = l1.iterator();
    while(it1.hasNext()) {
      Object o1 = it1.next();
      if(! it.hasNext())
        return false;
      Object o = it.next();
      while((o == null && ! (o1 == null)) || (o != null && !o.equals(o1))) {
        if(! it.hasNext())
          return false;
        o = it.next();
      }
    }
    return true;
  }

  public static <K,V> String toVerticalString(Map<K,V> m) {
    StringBuilder b = new StringBuilder();
    Set<Map.Entry<K,V>> entries = m.entrySet();
    for (Map.Entry<K,V> e : entries) {
      b.append(e.getKey() + "=" + e.getValue() + "\n");
    }
    return b.toString();
  }

  public static <T extends Comparable<T>> int compareLists(List<T> list1, List<T> list2) {
    if (list1 == null && list2 == null) return 0;
    if (list1 == null || list2 == null) {
      throw new IllegalArgumentException();
    }
    int size1 = list1.size();
    int size2 = list2.size();
    int size = Math.min(size1, size2);
    for (int i = 0; i < size; i++) {
      int c = list1.get(i).compareTo(list2.get(i));
      if (c != 0) return c;
    }
    if (size1 < size2) return -1;
    if (size1 > size2) return 1;
    return 0;
  }

  public static <C extends Comparable<C>> Comparator<List<C>> getListComparator() {
    return new Comparator<List<C>>() {
      public int compare(List<C> list1, List<C> list2) {
        return compareLists(list1, list2);
      }
    };
  }
  
  /**
   * Return the items of an Iterable as a sorted list.
   * 
   * @param <T>   The type of items in the Iterable.
   * @param items The collection to be sorted.
   * @return      A list containing the same items as the Iterable, but sorted.
   */
  public static <T extends Comparable<T>> List<T> sorted(Iterable<T> items) {
    List<T> result = toList(items);
    Collections.sort(result);
    return result;
  }
  
  /**
   * Create a list out of the items in the Iterable.
   * 
   * @param <T>   The type of items in the Iterable.
   * @param items The items to be made into a list.
   * @return      A list consisting of the items of the Iterable, in the same order.
   */
  public static <T> List<T> toList(Iterable<T> items) {
    List<T> list = new ArrayList<T>();
    addAll(list, items);
    return list;
  }
  
  /**
   * Create a set out of the items in the Iterable.
   * 
   * @param <T>   The type of items in the Iterable.
   * @param items The items to be made into a set.
   * @return      A set consisting of the items from the Iterable.
   */
  public static <T> Set<T> toSet(Iterable<T> items) {
    Set<T> set = new HashSet<T>();
    addAll(set, items);
    return set;
  }
  
  /**
   * Add all the items from an iterable to a collection.
   * 
   * @param <T>        The type of items in the iterable and the collection
   * @param collection The collection to which the items should be added.
   * @param items      The items to add to the collection.
   */
  public static <T> void addAll(Collection<T> collection, Iterable<? extends T> items) {
    for (T item: items) {
      collection.add(item);
    }
  }
}
