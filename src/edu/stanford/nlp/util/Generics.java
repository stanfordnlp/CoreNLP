package edu.stanford.nlp.util; 
import edu.stanford.nlp.util.logging.Redwood;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.util.concurrent.SynchronizedInterner;

/**
 * A collection of utilities to make dealing with Java generics less
 * painful and verbose.  For example, rather than declaring
 *
 * <pre>
 * {@code Map<String,ClassicCounter<List<String>>> = new HashMap<String,ClassicCounter<List<String>>>()}
 * </pre>
 *
 * you just call {@code Generics.newHashMap()}:
 *
 * <pre>
 * {@code Map<String,ClassicCounter<List<String>>> = Generics.newHashMap()}
 * </pre>
 *
 * Java type-inference will almost always just <em>do the right thing</em>
 * (every once in a while, the compiler will get confused before you do,
 * so you might still occasionally have to specify the appropriate types).
 *
 * This class is based on the examples in Brian Goetz's article
 * <a href="http://www.ibm.com/developerworks/library/j-jtp02216.html">Java
 * theory and practice: The pseudo-typedef antipattern</a>.
 *
 * @author Ilya Sherman
 */
public class Generics  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(Generics.class);

  private Generics() {} // static class

  /* Collections */
  /**
   * Returns a new empty ArrayList.
   *
   * @param <E> the element type
   * @return a new ArrayList
   */
  public static <E> ArrayList<E> newArrayList() {
    return new ArrayList<>();
  }

  /**
   * Returns a new empty ArrayList with the given initial capacity.
   *
   * @param <E> the element type
   * @param size the initial capacity
   * @return a new ArrayList
   */
  public static <E> ArrayList<E> newArrayList(int size) {
    return new ArrayList<>(size);
  }

  /**
   * Returns a new ArrayList containing the elements of the given collection.
   *
   * @param <E> the element type
   * @param c the collection whose elements are copied
   * @return a new ArrayList
   */
  public static <E> ArrayList<E> newArrayList(Collection<? extends E> c) {
    return new ArrayList<>(c);
  }

  /**
   * Returns a new empty LinkedList.
   *
   * @param <E> the element type
   * @return a new LinkedList
   */
  public static <E> LinkedList<E> newLinkedList() {
    return new LinkedList<>();
  }

  /**
   * Returns a new LinkedList containing the elements of the given collection.
   *
   * @param <E> the element type
   * @param c the collection whose elements are copied
   * @return a new LinkedList
   */
  public static <E> LinkedList<E> newLinkedList(Collection<? extends E> c) {
    return new LinkedList<>(c);
  }

  /**
   * Returns a new empty Stack.
   *
   * @param <E> the element type
   * @return a new Stack
   */
  public static <E> Stack<E> newStack() {
    return new Stack<>();
  }

  /**
   * Returns a new empty BinaryHeapPriorityQueue.
   *
   * @param <E> the element type
   * @return a new BinaryHeapPriorityQueue
   */
  public static <E> BinaryHeapPriorityQueue<E> newBinaryHeapPriorityQueue() {
    return new BinaryHeapPriorityQueue<>();
  }

  /**
   * Returns a new empty TreeSet using the elements' natural ordering.
   *
   * @param <E> the element type
   * @return a new TreeSet
   */
  public static <E> TreeSet<E> newTreeSet() {
    return new TreeSet<>();
  }

  /**
   * Returns a new empty TreeSet ordered by the given comparator.
   *
   * @param <E> the element type
   * @param comparator the comparator used to order the set
   * @return a new TreeSet
   */
  public static <E> TreeSet<E> newTreeSet(Comparator<? super E> comparator) {
    return new TreeSet<>(comparator);
  }

  /**
   * Returns a new TreeSet containing the elements of the given set, with the same ordering.
   *
   * @param <E> the element type
   * @param s the sorted set whose elements and ordering are copied
   * @return a new TreeSet
   */
  public static <E> TreeSet<E> newTreeSet(SortedSet<E> s) {
    return new TreeSet<>(s);
  }

  /** Name of the system property which selects the class returned by the {@code newHashSet} methods. */
  public static final String HASH_SET_PROPERTY = "edu.stanford.nlp.hashset.impl";
  /** Value of the {@value #HASH_SET_PROPERTY} system property, or null if it is not set. */
  public static final String HASH_SET_CLASSNAME = System.getProperty(HASH_SET_PROPERTY);
  private static final Class<?> HASH_SET_CLASS = getHashSetClass();
  private static final Constructor HASH_SET_SIZE_CONSTRUCTOR = getHashSetSizeConstructor();
  private static final Constructor HASH_SET_COLLECTION_CONSTRUCTOR = getHashSetCollectionConstructor();

  private static Class getHashSetClass() {
    try {
      if (HASH_SET_CLASSNAME == null) {
        return HashSet.class;
      } else {
        return Class.forName(HASH_SET_CLASSNAME);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // must be called after HASH_SET_CLASS is defined
  private static Constructor getHashSetSizeConstructor() {
    try {
      return HASH_SET_CLASS.getConstructor(Integer.TYPE);
    } catch (Exception e) {
      log.info("Warning: could not find a constructor for objects of " + HASH_SET_CLASS + " which takes an integer argument.  Will use the no argument constructor instead.");
    }
    return null;
  }

  // must be called after HASH_SET_CLASS is defined
  private static Constructor getHashSetCollectionConstructor() {
    try {
      return HASH_SET_CLASS.getConstructor(Collection.class);
    } catch (Exception e) {
      throw new RuntimeException("Error: could not find a constructor for objects of " + HASH_SET_CLASS + " which takes an existing collection argument.", e);
    }
  }

  /**
   * Returns a new empty hash set.
   * The class used is {@code java.util.HashSet} unless the system property
   * {@value #HASH_SET_PROPERTY} names another class.
   *
   * @param <E> the element type
   * @return a new hash set
   */
  public static <E> Set<E> newHashSet() {
    try {
      return ErasureUtils.uncheckedCast(HASH_SET_CLASS.getDeclaredConstructor().newInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a new empty hash set with the given initial capacity.
   * The class used is {@code java.util.HashSet} unless the system property
   * {@value #HASH_SET_PROPERTY} names another class.
   * If that class has no int constructor, the capacity is ignored.
   *
   * @param <E> the element type
   * @param initialCapacity the initial capacity
   * @return a new hash set
   */
  public static <E> Set<E> newHashSet(int initialCapacity) {
    if (HASH_SET_SIZE_CONSTRUCTOR == null) {
      return newHashSet();
    }
    try {
      return ErasureUtils.uncheckedCast(HASH_SET_SIZE_CONSTRUCTOR.newInstance(initialCapacity));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a new hash set containing the elements of the given collection.
   * The class used is {@code java.util.HashSet} unless the system property
   * {@value #HASH_SET_PROPERTY} names another class.
   *
   * @param <E> the element type
   * @param c the collection whose elements are copied
   * @return a new hash set
   */
  public static <E> Set<E> newHashSet(Collection<? extends E> c) {
    try {
      return ErasureUtils.uncheckedCast(HASH_SET_COLLECTION_CONSTRUCTOR.newInstance(c));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Name of the system property which selects the class returned by the {@code newHashMap} methods. */
  public static final String HASH_MAP_PROPERTY = "edu.stanford.nlp.hashmap.impl";
  /** Value of the {@value #HASH_MAP_PROPERTY} system property, or null if it is not set. */
  public static final String HASH_MAP_CLASSNAME = System.getProperty(HASH_MAP_PROPERTY);
  private static final Class<?> HASH_MAP_CLASS = getHashMapClass();
  private static final Constructor HASH_MAP_SIZE_CONSTRUCTOR = getHashMapSizeConstructor();
  private static final Constructor HASH_MAP_FROM_MAP_CONSTRUCTOR = getHashMapFromMapConstructor();

  private static Class getHashMapClass() {
    try {
      if (HASH_MAP_CLASSNAME == null) {
        return HashMap.class;
      } else {
        return Class.forName(HASH_MAP_CLASSNAME);
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // must be called after HASH_MAP_CLASS is defined
  private static Constructor getHashMapSizeConstructor() {
    try {
      return HASH_MAP_CLASS.getConstructor(Integer.TYPE);
    } catch (Exception e) {
      log.info("Warning: could not find a constructor for objects of " + HASH_MAP_CLASS + " which takes an integer argument.  Will use the no argument constructor instead.");
    }
    return null;
  }

  // must be called after HASH_MAP_CLASS is defined
  private static Constructor getHashMapFromMapConstructor() {
    try {
      return HASH_MAP_CLASS.getConstructor(Map.class);
    } catch (Exception e) {
      throw new RuntimeException("Error: could not find a constructor for objects of " + HASH_MAP_CLASS + " which takes an existing Map argument.", e);
    }
  }

  /* Maps */
  /**
   * Returns a new empty hash map.
   * The class used is {@code java.util.HashMap} unless the system property
   * {@value #HASH_MAP_PROPERTY} names another class.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a new hash map
   */
  public static <K,V> Map<K,V> newHashMap() {
    try {
      return ErasureUtils.uncheckedCast(HASH_MAP_CLASS.getDeclaredConstructor().newInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a new empty hash map with the given initial capacity.
   * The class used is {@code java.util.HashMap} unless the system property
   * {@value #HASH_MAP_PROPERTY} names another class.
   * If that class has no int constructor, the capacity is ignored.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @param initialCapacity the initial capacity
   * @return a new hash map
   */
  public static <K,V> Map<K,V> newHashMap(int initialCapacity) {
    if (HASH_MAP_SIZE_CONSTRUCTOR == null) {
      return newHashMap();
    }
    try {
      return ErasureUtils.uncheckedCast(HASH_MAP_SIZE_CONSTRUCTOR.newInstance(initialCapacity));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a new hash map containing the mappings of the given map.
   * The class used is {@code java.util.HashMap} unless the system property
   * {@value #HASH_MAP_PROPERTY} names another class.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @param m the map whose mappings are copied
   * @return a new hash map
   */
  public static <K,V> Map<K,V> newHashMap(Map<? extends K,? extends V> m) {
    try {
      return ErasureUtils.uncheckedCast(HASH_MAP_FROM_MAP_CONSTRUCTOR.newInstance(m));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a new empty IdentityHashMap.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a new IdentityHashMap
   */
  public static <K,V> IdentityHashMap<K,V> newIdentityHashMap() {
    return new IdentityHashMap<>();
  }

  /**
   * Returns a new empty Set which compares elements by identity, backed by an IdentityHashMap.
   *
   * @param <K> the element type
   * @return a new identity-based Set
   */
  public static <K> Set<K> newIdentityHashSet() {
    return Collections.newSetFromMap(Generics.<K, Boolean>newIdentityHashMap());
  }

  /**
   * Returns a new empty WeakHashMap.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a new WeakHashMap
   */
  public static <K,V> WeakHashMap<K,V> newWeakHashMap() {
    return new WeakHashMap<>();
  }

  /**
   * Returns a new empty WeakHashMap with the given initial capacity.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @param initialCapacity the initial capacity
   * @return a new WeakHashMap
   */
  public static <K,V> WeakHashMap<K,V> newWeakHashMap(int initialCapacity) {
    return new WeakHashMap<>(initialCapacity);
  }

  /**
   * Returns a new empty ConcurrentHashMap.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a new ConcurrentHashMap
   */
  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap() {
    return new ConcurrentHashMap<>();
  }

  /**
   * Returns a new empty ConcurrentHashMap with the given initial capacity.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @param initialCapacity the initial capacity
   * @return a new ConcurrentHashMap
   */
  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap(int initialCapacity) {
    return new ConcurrentHashMap<>(initialCapacity);
  }

  /**
   * Returns a new empty ConcurrentHashMap with the given sizing parameters.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @param initialCapacity the initial capacity
   * @param loadFactor the load factor
   * @param concurrencyLevel the estimated number of concurrently updating threads
   * @return a new ConcurrentHashMap
   */
  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap(int initialCapacity,
      float loadFactor, int concurrencyLevel) {
    return new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
  }

  /**
   * Returns a new empty TreeMap using the keys' natural ordering.
   *
   * @param <K> the key type
   * @param <V> the value type
   * @return a new TreeMap
   */
  public static <K,V> TreeMap<K,V> newTreeMap() {
    return new TreeMap<>();
  }

  /**
   * Returns a new empty Index, implemented as a HashIndex.
   *
   * @param <E> the element type
   * @return a new HashIndex
   */
  public static <E> Index<E> newIndex() {
    return new HashIndex<>();
  }

  /**
   * Returns a new empty thread-safe Set backed by a ConcurrentHashMap.
   *
   * @param <E> the element type
   * @return a new concurrent Set
   */
  public static <E> Set<E> newConcurrentHashSet() {
    return Collections.newSetFromMap(new ConcurrentHashMap<>());
  }

  /**
   * Returns a new thread-safe Set backed by a ConcurrentHashMap, containing the elements of the given set.
   *
   * @param <E> the element type
   * @param set the set whose elements are copied
   * @return a new concurrent Set
   */
  public static <E> Set<E> newConcurrentHashSet(Set<E> set) {
    Set<E> ret = Collections.newSetFromMap(new ConcurrentHashMap<>());
    ret.addAll(set);
    return ret;
  }


  /* Other */
  /**
   * Returns a new Pair of the given objects.
   *
   * @param <T1> the type of the first element
   * @param <T2> the type of the second element
   * @param first the first element
   * @param second the second element
   * @return a new Pair
   */
  public static <T1,T2> Pair<T1,T2> newPair(T1 first, T2 second) {
    return new Pair<>(first, second);
  }

  /**
   * Returns a new Triple of the given objects.
   *
   * @param <T1> the type of the first element
   * @param <T2> the type of the second element
   * @param <T3> the type of the third element
   * @param first the first element
   * @param second the second element
   * @param third the third element
   * @return a new Triple
   */
  public static <T1,T2, T3> Triple<T1,T2, T3> newTriple(T1 first, T2 second, T3 third) {
    return new Triple<>(first, second, third);
  }

  /**
   * Returns a new empty Interner.
   *
   * @param <T> the type of the interned objects
   * @return a new Interner
   */
  public static <T> Interner<T> newInterner() {
    return new Interner<>();
  }

  /**
   * Returns a SynchronizedInterner wrapping the given Interner, which synchronizes on itself.
   *
   * @param <T> the type of the interned objects
   * @param interner the Interner to wrap
   * @return a new SynchronizedInterner
   */
  public static <T> SynchronizedInterner<T> newSynchronizedInterner(Interner<T> interner) {
    return new SynchronizedInterner<>(interner);
  }

  /**
   * Returns a SynchronizedInterner wrapping the given Interner, which synchronizes on the given mutex.
   *
   * @param <T> the type of the interned objects
   * @param interner the Interner to wrap
   * @param mutex the object to synchronize on
   * @return a new SynchronizedInterner
   */
  public static <T> SynchronizedInterner<T> newSynchronizedInterner(Interner<T> interner,
                                                                    Object mutex) {
    return new SynchronizedInterner<>(interner, mutex);
  }

  /**
   * Returns a new WeakReference to the given object.
   *
   * @param <T> the type of the referent
   * @param referent the object to refer to
   * @return a new WeakReference
   */
  public static <T> WeakReference<T> newWeakReference(T referent) {
    return new WeakReference<>(referent);
  }
}
