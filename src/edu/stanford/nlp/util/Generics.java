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
  public static <E> ArrayList<E> newArrayList() {
    return new ArrayList<>();
  }

  public static <E> ArrayList<E> newArrayList(int size) {
    return new ArrayList<>(size);
  }

  public static <E> ArrayList<E> newArrayList(Collection<? extends E> c) {
    return new ArrayList<>(c);
  }

  public static <E> LinkedList<E> newLinkedList() {
    return new LinkedList<>();
  }

  public static <E> LinkedList<E> newLinkedList(Collection<? extends E> c) {
    return new LinkedList<>(c);
  }

  public static <E> Stack<E> newStack() {
    return new Stack<>();
  }

  public static <E> BinaryHeapPriorityQueue<E> newBinaryHeapPriorityQueue() {
    return new BinaryHeapPriorityQueue<>();
  }

  public static <E> TreeSet<E> newTreeSet() {
    return new TreeSet<>();
  }

  public static <E> TreeSet<E> newTreeSet(Comparator<? super E> comparator) {
    return new TreeSet<>(comparator);
  }

  public static <E> TreeSet<E> newTreeSet(SortedSet<E> s) {
    return new TreeSet<>(s);
  }

  public static final String HASH_SET_PROPERTY = "edu.stanford.nlp.hashset.impl";
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

  public static <E> Set<E> newHashSet() {
    try {
      return ErasureUtils.uncheckedCast(HASH_SET_CLASS.newInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

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

  public static <E> Set<E> newHashSet(Collection<? extends E> c) {
    try {
      return ErasureUtils.uncheckedCast(HASH_SET_COLLECTION_CONSTRUCTOR.newInstance(c));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static final String HASH_MAP_PROPERTY = "edu.stanford.nlp.hashmap.impl";
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
  public static <K,V> Map<K,V> newHashMap() {
    try {
      return ErasureUtils.uncheckedCast(HASH_MAP_CLASS.newInstance());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

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

  public static <K,V> Map<K,V> newHashMap(Map<? extends K,? extends V> m) {
    try {
      return ErasureUtils.uncheckedCast(HASH_MAP_FROM_MAP_CONSTRUCTOR.newInstance(m));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static <K,V> IdentityHashMap<K,V> newIdentityHashMap() {
    return new IdentityHashMap<>();
  }

  public static <K> Set<K> newIdentityHashSet() {
    return Collections.newSetFromMap(Generics.<K, Boolean>newIdentityHashMap());
  }

  public static <K,V> WeakHashMap<K,V> newWeakHashMap() {
    return new WeakHashMap<>();
  }

  public static <K,V> WeakHashMap<K,V> newWeakHashMap(int initialCapacity) {
    return new WeakHashMap<>(initialCapacity);
  }

  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap() {
    return new ConcurrentHashMap<>();
  }

  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap(int initialCapacity) {
    return new ConcurrentHashMap<>(initialCapacity);
  }

  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap(int initialCapacity,
      float loadFactor, int concurrencyLevel) {
    return new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel);
  }

  public static <K,V> TreeMap<K,V> newTreeMap() {
    return new TreeMap<>();
  }

  public static <E> Index<E> newIndex() {
    return new HashIndex<>();
  }

  public static <E> Set<E> newConcurrentHashSet() {
    return Collections.newSetFromMap(new ConcurrentHashMap<>());
  }

  public static <E> Set<E> newConcurrentHashSet(Set<E> set) {
    Set<E> ret = Collections.newSetFromMap(new ConcurrentHashMap<>());
    ret.addAll(set);
    return ret;
  }


  /* Other */
  public static <T1,T2> Pair<T1,T2> newPair(T1 first, T2 second) {
    return new Pair<>(first, second);
  }

  public static <T1,T2, T3> Triple<T1,T2, T3> newTriple(T1 first, T2 second, T3 third) {
    return new Triple<>(first, second, third);
  }

  public static <T> Interner<T> newInterner() {
    return new Interner<>();
  }

  public static <T> SynchronizedInterner<T> newSynchronizedInterner(Interner<T> interner) {
    return new SynchronizedInterner<>(interner);
  }

  public static <T> SynchronizedInterner<T> newSynchronizedInterner(Interner<T> interner,
                                                                    Object mutex) {
    return new SynchronizedInterner<>(interner, mutex);
  }

  public static <T> WeakReference<T> newWeakReference(T referent) {
    return new WeakReference<>(referent);
  }
}
