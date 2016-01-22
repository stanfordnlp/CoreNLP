package edu.stanford.nlp.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.*;

/** This class provides a <code>IdentityHashMap</code>-backed
 *  implementation of the <code>Set</code> interface.  This means that
 *  whether an object is an element of the set depends on whether it is ==
 *  (rather than <code>equals()</code>) to an element of the set.  This is
 *  different from a normal <code>HashSet</code>, where set membership
 *  depends on <code>equals()</code>, rather than ==.
 *
 *  Each element in the set is a key in the backing IdentityHashMap; each key
 *  maps to a static token, denoting that the key does, in fact, exist.
 *
 *  Most operations are O(1), assuming no hash collisions.  In the worst
 *  case (where all hashes collide), operations are O(n).
 *
 *  @author Bill MacCartney
 */
public class IdentityHashSet<E> extends AbstractSet<E> implements Cloneable, Serializable {

  // todo: The Java bug database notes that "From 1.6, an identity hash set can be created by Collections.newSetFromMap(new IdentityHashMap())."

  // INSTANCE VARIABLES -------------------------------------------------

  // the IdentityHashMap which backs this set
  private transient IdentityHashMap<E,Boolean> map;
  private static final long serialVersionUID = -5024744406713321676L;


  // CONSTRUCTORS ---------------------------------------------------------

  /** Construct a new, empty IdentityHashSet whose backing IdentityHashMap
   *  has the default expected maximum size (21);
   */
  public IdentityHashSet() {
    map = new IdentityHashMap<>();
  }

  /** Construct a new, empty IdentityHashSet whose backing IdentityHashMap
   *  has the specified expected maximum size.  Putting more than the
   *  expected number of elements into the set may cause the internal data
   *  structure to grow, which may be somewhat time-consuming.
   *
   * @param expectedMaxSize the expected maximum size of the set.
   */
  public IdentityHashSet(int expectedMaxSize) {
    map = new IdentityHashMap<>(expectedMaxSize);
  }

  /** Construct a new IdentityHashSet with the same elements as the supplied
   *  Collection (eliminating any duplicates, of course); the backing
   *  IdentityHashMap will have the default expected maximum size (21).
   *
   * @param c a Collection containing the elements with which this set will
   *          be initialized.
   */
  public IdentityHashSet(Collection<? extends E> c) {
    map = new IdentityHashMap<>();
    addAll(c);
  }


  // PUBLIC METHODS ---------------------------------------------------------

  /** Adds the specified element to this set if it is not already present.
   *
   *  Remember that this set implementation uses == (not
   *  <code>equals()</code>) to test whether an element is present in the
   *  set.
   *
   * @param       o             element to add to this set
   * @return      true          if the element was added,
   *              false         otherwise
   */
  @Override
  public boolean add(E o) {
    if (map.containsKey(o)) {
      return false;
    } else {
      internalAdd(o);
      return true;
    }
  }

  /** Removes all of the elements from this set.
   */
  @Override
  public void clear() {
    map.clear();
  }

  /** Returns a shallow copy of this <code>IdentityHashSet</code> instance:
   *  the elements themselves are not cloned.
   *
   *  @return a shallow copy of this set.
   */
  @Override
  public Object clone() {
    Iterator<E> it = iterator();
    IdentityHashSet<E> clone = new IdentityHashSet<>(size() * 2);
    while (it.hasNext()) {
      clone.internalAdd(it.next());
    }
    return clone;
  }

  /** Returns true if this set contains the specified element.
   *
   *  Remember that this set implementation uses == (not
   *  <code>equals()</code>) to test whether an element is present in the
   *  set.
   *
   *  @param o Element whose presence in this set is to be
   *  tested.
   *
   *  @return <code>true</code> if this set contains the specified element.
   */
  @Override
  public boolean contains(Object o) {
    return map.containsKey(o);
  }

  /** Returns <code>true</code> if this set contains no elements.
   *
   *  @return <code>true</code> if this set contains no elements.
   */
  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  /** Returns an iterator over the elements in this set. The elements are
   *  returned in no particular order.
   *
   *  @return an <code>Iterator</code> over the elements in this set.
   */
  @Override
  public Iterator<E> iterator() {
    return map.keySet().iterator();
  }

  /** Removes the specified element from this set if it is present.
   *
   *  Remember that this set implementation uses == (not
   *  <code>equals()</code>) to test whether an element is present in the
   *  set.
   *
   *  @param o Object to be removed from this set, if present.
   *
   *  @return <code>true</code> if the set contained the specified element.
   */
  @Override
  public boolean remove(Object o) {
    return (map.remove(o) != null);
  }

  /** Returns the number of elements in this set (its cardinality).
   *
   *  @return the number of elements in this set (its cardinality).
   */
  @Override
  public int size() {
    return map.size();
  }

  /** Just for testing. */
  public static void main(String[] args) {
    Integer x = Integer.valueOf(3);
    Integer y = Integer.valueOf(4);
    Integer z = Integer.valueOf(5);
    List<Integer> a = Arrays.asList(new Integer[] {x, y, z});
    List<String> b = Arrays.asList(new String[] {"Larry", "Moe", "Curly"});
    List<Integer> c = Arrays.asList(new Integer[] {x, y, z});
    List<String> d = Arrays.asList(new String[] {"Larry", "Moe", "Curly"});
    Set<List<?>> hs = Generics.newHashSet();
    IdentityHashSet<List<?>> ihs = new IdentityHashSet<>();
    hs.add(a);
    hs.add(b);
    ihs.add(a);
    ihs.add(b);
    System.out.println("List a is " + a);
    System.out.println("List b is " + b);
    System.out.println("List c is " + c);
    System.out.println("List d is " + d);
    System.out.println("HashSet hs contains a and b: " + hs);
    System.out.println("IdentityHashSet ihs contains a and b: " + ihs);
    System.out.println("hs contains a? " + hs.contains(a));
    System.out.println("hs contains b? " + hs.contains(b));
    System.out.println("hs contains c? " + hs.contains(c));
    System.out.println("hs contains d? " + hs.contains(d));
    System.out.println("ihs contains a? " + ihs.contains(a));
    System.out.println("ihs contains b? " + ihs.contains(b));
    System.out.println("ihs contains c? " + ihs.contains(c));
    System.out.println("ihs contains d? " + ihs.contains(d));
  }

  // PRIVATE METHODS -----------------------------------------------------------

  /** Adds the supplied element to this set.  This private method is used
   *  internally [by clone()] instead of add(), because add() can be
   *  overridden to do unexpected things.
   *
   *  @param    o        the element to add to this set
   */
  private void internalAdd(E o) {
    map.put(o, Boolean.TRUE);
  }

  /** Serialize this Object in a manner which is binary-compatible with the
   *  JDK.
   */
  private void writeObject(ObjectOutputStream s) throws IOException {
    Iterator<E> it = iterator();
    s.writeInt(size() * 2);             // expectedMaxSize
    s.writeInt(size());
    while (it.hasNext())
      s.writeObject(it.next());
  }

  /** Deserialize this Object in a manner which is binary-compatible with
   *  the JDK.
   */
  private void readObject(ObjectInputStream s)
    throws IOException, ClassNotFoundException {
    int size, expectedMaxSize;
    Object o;

    expectedMaxSize = s.readInt();
    size = s.readInt();

    map = new IdentityHashMap<>(expectedMaxSize);
    for (int i = 0; i < size; i++) {
      o = s.readObject();
      internalAdd(ErasureUtils.<E>uncheckedCast(o));
    }
  }
}


