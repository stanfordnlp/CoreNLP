package edu.stanford.nlp.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
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
 * {@code Map<String, List<Pair<IndexedWord, GrammaticalRelation>>> = new HashMap<String, List<Pair<IndexedWord, GrammaticalRelation>>>()}
 * </pre>
 *
 * you just call <code>Generics.newHashMap()</code>:
 *
 * <pre>
 * {@code Map<String, List<Pair<IndexedWord, GrammaticalRelation>>> = Generics.newHashMap()}
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
public class Generics {

  private Generics() {} // static class

  /* Collections */
  public static <E> ArrayList<E> newArrayList() {
    return new ArrayList<E>();
  }

  public static <E> ArrayList<E> newArrayList(int size) {
    return new ArrayList<E>(size);
  }

  public static <E> ArrayList<E> newArrayList(Collection<? extends E> c) {
    return new ArrayList<E>(c);
  }

  public static <E> LinkedList<E> newLinkedList() {
    return new LinkedList<E>();
  }

  public static <E> LinkedList<E> newLinkedList(Collection<? extends E> c) {
    return new LinkedList<E>(c);
  }

  public static <E> HashSet<E> newHashSet() {
    return new HashSet<E>();
  }

  public static <E> HashSet<E> newHashSet(int initialCapacity) {
    return new HashSet<E>(initialCapacity);
  }

  public static <E> HashSet<E> newHashSet(Collection<? extends E> c) {
    return new HashSet<E>(c);
  }

  public static <E> TreeSet<E> newTreeSet() {
    return new TreeSet<E>();
  }

  public static <E> TreeSet<E> newTreeSet(Comparator<? super E> comparator) {
    return new TreeSet<E>(comparator);
  }

  public static <E> TreeSet<E> newTreeSet(SortedSet<E> s) {
    return new TreeSet<E>(s);
  }

  public static <E> Stack<E> newStack() {
    return new Stack<E>();
  }

  public static <E> BinaryHeapPriorityQueue<E> newBinaryHeapPriorityQueue() {
    return new BinaryHeapPriorityQueue<E>();
  }


  /* Maps */
  public static <K,V> HashMap<K,V> newHashMap() {
    return new HashMap<K,V>();
  }

  public static <K,V> HashMap<K,V> newHashMap(int initialCapacity) {
    return new HashMap<K,V>(initialCapacity);
  }

  public static <K,V> HashMap<K,V> newHashMap(Map<? extends K,? extends V> m) {
    return new HashMap<K,V>(m);
  }

  public static <K,V> WeakHashMap<K,V> newWeakHashMap() {
    return new WeakHashMap<K,V>();
  }

  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap() {
    return new ConcurrentHashMap<K,V>();
  }

  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap(int initialCapacity) {
    return new ConcurrentHashMap<K,V>(initialCapacity);
  }

  public static <K,V> ConcurrentHashMap<K,V> newConcurrentHashMap(int initialCapacity,
      float loadFactor, int concurrencyLevel) {
    return new ConcurrentHashMap<K,V>(initialCapacity, loadFactor, concurrencyLevel);
  }

  public static <K,V> TreeMap<K,V> newTreeMap() {
    return new TreeMap<K,V>();
  }

  public static <E> Index<E> newIndex() {
    return new HashIndex<E>();
  }


  /* Other */
  public static <T1,T2> Pair<T1,T2> newPair(T1 first, T2 second) {
    return new Pair<T1,T2>(first, second);
  }

  public static <T1,T2, T3> Triple<T1,T2, T3> newTriple(T1 first, T2 second, T3 third) {
    return new Triple<T1,T2, T3>(first, second, third);
  }

  public static <T> Interner<T> newInterner() {
    return new Interner<T>();
  }

  public static <T> SynchronizedInterner<T> newSynchronizedInterner(Interner<T> interner) {
    return new SynchronizedInterner<T>(interner);
  }

  public static <T> SynchronizedInterner<T> newSynchronizedInterner(Interner<T> interner,
                                                                    Object mutex) {
    return new SynchronizedInterner<T>(interner, mutex);
  }

  public static <T> WeakReference<T> newWeakReference(T referent) {
    return new WeakReference<T>(referent);
  }
}