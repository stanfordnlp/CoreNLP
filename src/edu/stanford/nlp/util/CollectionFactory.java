package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.*;

/**
 * Factory for vending Collections.  It's a class instead of an interface because I guessed that it'd primarily be used for its inner classes.
 *
 * @author Dan Klein (klein@cs.stanford.edu)
 */
public abstract class CollectionFactory<T> implements Serializable {

  private static final long serialVersionUID = 3711321773145894069L;
  @SuppressWarnings("unchecked")
  public static final CollectionFactory ARRAY_LIST_FACTORY = new ArrayListFactory();
  @SuppressWarnings("unchecked")
  public static final CollectionFactory LINKED_LIST_FACTORY = new LinkedListFactory();
  @SuppressWarnings("unchecked")
  public static final CollectionFactory HASH_SET_FACTORY = new HashSetFactory();
  @SuppressWarnings("unchecked")
  public static final CollectionFactory TREE_SET_FACTORY = new TreeSetFactory();


  public abstract Collection<T> newCollection();

  public abstract Collection<T> newEmptyCollection();


  /** Return a factory for making ArrayList Collections.
   *  This method allows type safety in calling code.
   *
   *  @return A factory for ArrayList collections.
   */
  public static <E> CollectionFactory<E> arrayListFactory() {
    return ErasureUtils.uncheckedCast(ARRAY_LIST_FACTORY);
  }

  public static <E> CollectionFactory<E> arrayListFactory(int size) {
    return ErasureUtils.uncheckedCast(new SizedArrayListFactory(size));
  }

  public static <E> CollectionFactory<E> linkedListFactory() {
    return ErasureUtils.uncheckedCast(LINKED_LIST_FACTORY);
  }

  public static <E> CollectionFactory<E> hashSetFactory() {
    return ErasureUtils.uncheckedCast(HASH_SET_FACTORY);
  }

  public static <E> CollectionFactory<E> treeSetFactory() {
    return ErasureUtils.uncheckedCast(TREE_SET_FACTORY);
  }

  public static class ArrayListFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = 1L;

    @Override
    public Collection<T> newCollection() {
      return new ArrayList<T>();
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptyList();
    }
  }

  public static class SizedArrayListFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = 1L;
    private int defaultSize = 1;

    public SizedArrayListFactory(int size)
    {
      this.defaultSize = size;
    }

    @Override
    public Collection<T> newCollection() {
      return new ArrayList<T>(defaultSize);
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptyList();
    }
  }

  public static class LinkedListFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = -4236184979948498000L;

    @Override
    public Collection<T> newCollection() {
      return new LinkedList<T>();
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptyList();
    }
  }


  public static class HashSetFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = -6268401669449458602L;

    @Override
    public Collection<T> newCollection() {
      return Generics.newHashSet();
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptySet();
    }
  }

  public static class TreeSetFactory<T> extends CollectionFactory<T> {
    private static final long serialVersionUID = -3451920268219478134L;

    @Override
    public Collection<T> newCollection() {
      return new TreeSet<T>();
    }

    @Override
    public Collection<T> newEmptyCollection() {
      return Collections.emptySet();
    }
  }

}
