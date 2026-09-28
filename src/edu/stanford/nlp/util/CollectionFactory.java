package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.*;

/**
 * Factory for vending Collections.  It's a class instead of an interface because I guessed that it'd primarily be used for its inner classes.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 * @param <T> The type of the elements of the collections made
 */
public abstract class CollectionFactory<T> implements Serializable {

  private static final long serialVersionUID = 3711321773145894069L;
  /** A factory for ArrayList collections. */
  @SuppressWarnings("unchecked")
  public static final CollectionFactory ARRAY_LIST_FACTORY = new ArrayListFactory();
  /** A factory for LinkedList collections. */
  @SuppressWarnings("unchecked")
  public static final CollectionFactory LINKED_LIST_FACTORY = new LinkedListFactory();
  /** A factory for HashSet collections. */
  @SuppressWarnings("unchecked")
  public static final CollectionFactory HASH_SET_FACTORY = new HashSetFactory();
  /** A factory for TreeSet collections. */
  @SuppressWarnings("unchecked")
  public static final CollectionFactory TREE_SET_FACTORY = new TreeSetFactory();

  /** Constructor for use by subclasses. */
  public CollectionFactory() { }

  /**
   * Make a new, empty, modifiable collection.
   *
   * @return A new collection
   */
  public abstract Collection<T> newCollection();

  /**
   * Return an empty collection. The implementations here return an immutable empty collection.
   *
   * @return An empty collection
   */
  public abstract Collection<T> newEmptyCollection();

  /**
   * Return a collection containing only the given element. The implementations here
   * return an immutable singleton collection.
   *
   * @param t The element
   * @return A collection containing just {@code t}
   */
  public abstract Collection<T> newSingletonCollection(T t);


  /** Return a factory for making ArrayList Collections.
   *  This method allows type safety in calling code.
   *
   *  @param <E> The type of the elements of the collections made
   *  @return A factory for ArrayList collections.
   */
  public static <E> CollectionFactory<E> arrayListFactory() {
    return ErasureUtils.uncheckedCast(ARRAY_LIST_FACTORY);
  }

  /** Return a factory for making ArrayList Collections with the given initial capacity.
   *  Each call makes a new factory.
   *
   *  @param size The initial capacity of each ArrayList made
   *  @param <E> The type of the elements of the collections made
   *  @return A factory for ArrayList collections.
   */
  public static <E> CollectionFactory<E> arrayListFactory(int size) {
    return ErasureUtils.uncheckedCast(new SizedArrayListFactory(size));
  }

  /** Return a factory for making LinkedList Collections.
   *  This method allows type safety in calling code.
   *
   *  @param <E> The type of the elements of the collections made
   *  @return A factory for LinkedList collections.
   */
  public static <E> CollectionFactory<E> linkedListFactory() {
    return ErasureUtils.uncheckedCast(LINKED_LIST_FACTORY);
  }

  /** Return a factory for making HashSet Collections.
   *  This method allows type safety in calling code.
   *
   *  @param <E> The type of the elements of the collections made
   *  @return A factory for HashSet collections.
   */
  public static <E> CollectionFactory<E> hashSetFactory() {
    return ErasureUtils.uncheckedCast(HASH_SET_FACTORY);
  }

  /** Return a factory for making TreeSet Collections.
   *  This method allows type safety in calling code.
   *
   *  @param <E> The type of the elements of the collections made
   *  @return A factory for TreeSet collections.
   */
  public static <E> CollectionFactory<E> treeSetFactory() {
    return ErasureUtils.uncheckedCast(TREE_SET_FACTORY);
  }

  /**
   * A factory for ArrayList collections.
   *
   * @param <T> The type of the elements of the collections made
   */
  public static class ArrayListFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = 1L;

    /** Create a factory for ArrayList collections. */
    public ArrayListFactory() { }

    @Override
    public Collection<T> newCollection() {
      return new ArrayList<>();
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptyList();
    }

    @Override
    public Collection<T> newSingletonCollection(T t) {
      return Collections.singletonList(t);
    }
  }

  /**
   * A factory for ArrayList collections with a given initial capacity.
   *
   * @param <T> The type of the elements of the collections made
   */
  public static class SizedArrayListFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = 1L;
    /** The initial capacity of each ArrayList made. */
    private int defaultSize = 1;

    /**
     * Create a factory for ArrayList collections with the given initial capacity.
     *
     * @param size The initial capacity of each ArrayList made
     */
    public SizedArrayListFactory(int size)
    {
      this.defaultSize = size;
    }

    @Override
    public Collection<T> newCollection() {
      return new ArrayList<>(defaultSize);
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptyList();
    }

    @Override
    public Collection<T> newSingletonCollection(T t) {
      return Collections.singletonList(t);
    }
  }

  /**
   * A factory for LinkedList collections.
   *
   * @param <T> The type of the elements of the collections made
   */
  public static class LinkedListFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = -4236184979948498000L;

    /** Create a factory for LinkedList collections. */
    public LinkedListFactory() { }

    @Override
    public Collection<T> newCollection() {
      return new LinkedList<>();
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptyList();
    }

    @Override
    public Collection<T> newSingletonCollection(T t) {
      return Collections.singletonList(t);
    }
  }


  /**
   * A factory for HashSet collections.
   *
   * @param <T> The type of the elements of the collections made
   */
  public static class HashSetFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = -6268401669449458602L;

    /** Create a factory for HashSet collections. */
    public HashSetFactory() { }

    @Override
    public Collection<T> newCollection() {
      return Generics.newHashSet();
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptySet();
    }

    @Override
    public Collection<T> newSingletonCollection(T t) {
      return Collections.singleton(t);
    }
  }

  /**
   * A factory for TreeSet collections.
   *
   * @param <T> The type of the elements of the collections made
   */
  public static class TreeSetFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = -3451920268219478134L;

    /** Create a factory for TreeSet collections. */
    public TreeSetFactory() { }

    @Override
    public Collection<T> newCollection() {
      return new TreeSet<>();
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptySet();
    }

    @Override
    public Collection<T> newSingletonCollection(T t) {
      return Collections.singleton(t);
    }
  }

}
