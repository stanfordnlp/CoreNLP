package edu.stanford.nlp.util; 
import edu.stanford.nlp.util.logging.Redwood;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;

/**
 * For interning (canonicalizing) things.
 * <br>
 * It maps any object to a unique interned version which .equals the
 * presented object.  If presented with a new object which has no
 * previous interned version, the presented object becomes the
 * interned version.  You can tell if your object has been chosen as
 * the new unique representative by checking whether o == intern(o).
 * The interners use WeakHashMap, meaning that if the only pointers
 * to an interned item are the interners' backing maps, that item can
 * still be garbage collected.  Since the gc thread can silently
 * remove things from the backing map, there's no public way to get
 * the backing map, but feel free to add one at your own risk.
 * <br>
 * Note that in general it is just as good or better to use the
 * static Interner.globalIntern() method rather than making an
 * instance of Interner and using the instance-level intern().
 * <br>
 * Author: Dan Klein
 * Date: 9/28/03
 *
 * @author Dan Klein
 * @param <T> The type of the objects interned
 */
public class Interner<T>  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(Interner.class);

  /** The interner used by the global methods. */
  protected static Interner<Object> interner = new Interner<>();

  /** Create an empty interner. */
  public Interner() {
    map = Generics.newWeakHashMap();
  }

  /**
   * Create an empty interner.
   *
   * @param initialCapacity The initial capacity of the backing map
   */
  public Interner(int initialCapacity) {
    map = Generics.newWeakHashMap(initialCapacity);
  }

  /**
   * For getting the instance that global methods use.
   *
   * @return The global interner
   */
  public static Interner<Object> getGlobal() {
    return interner;
  }

  /**
   * For supplying a new instance for the global methods to use.
   * 
   * @param interner The interner for the global methods to use
   * @return the previous global interner.
   */
  public static Interner<Object> setGlobal(Interner<Object> interner) {
    Interner<Object> oldInterner = Interner.interner;
    Interner.interner = interner;
    return oldInterner;
  }

  /**
   * Returns a unique object o' that .equals the argument o.  If o
   * itself is returned, this is the first request for an object
   * .equals to o.  This uses the global interner.
   *
   * @param <T> The type of the object
   * @param o The object to intern
   * @return The interned object which .equals o
   */
  @SuppressWarnings("unchecked")
  public static <T> T globalIntern(T o) {
    return (T) getGlobal().intern(o);
  }


  /** The backing map from each interned object to a weak reference to itself. */
  protected Map<T,WeakReference<T>> map;

  /** Remove all the interned objects, by replacing the backing map with a new empty one. */
  public void clear() { map = Generics.newWeakHashMap(); }
  /**
   * Remove all the interned objects, by replacing the backing map with a new empty one.
   *
   * @param initialCapacity The initial capacity of the new backing map
   */
  public void clear(int initialCapacity) { map = Generics.newWeakHashMap(initialCapacity); }
  
  /**
   * Returns a unique object o' that .equals the argument o.  If o
   * itself is returned, this is the first request for an object
   * .equals to o.
   *
   * @param o The object to intern
   * @return The interned object which .equals o
   */
  public synchronized T intern(T o) {
    WeakReference<T> ref = map.get(o);
    if (ref == null) {
      ref = Generics.newWeakReference(o);
      map.put(o, ref);
    }
//    else {
//      log.info("Found dup for " + o);
//    }
    return ref.get();
  }

  /**
   * Returns a <code>Set</code> such that each element in the returned set
   * is a unique object e' that .equals the corresponding element e in the
   * original set.
   *
   * @param s The set of objects to intern
   * @return A new set of the interned objects
   */
  public Set<T> internAll(Set<T> s) {
    Set<T> result = Generics.newHashSet();
    for (T o : s) {
      result.add(intern(o));
    }
    return result;
  }

  /**
   * Returns the number of interned objects which have not been garbage collected.
   *
   * @return The size of the backing map
   */
  public int size() {
    return map.size();
  }

  /**
   * Test method: interns its arguments and says whether they == themselves.
   *
   * @param args The Strings to intern
   */
  public static void main(String[] args) {
    for (String str : args) {
      System.out.println(Interner.globalIntern(str) == str);
    }
  }
}
