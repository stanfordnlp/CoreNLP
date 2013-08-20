package edu.stanford.nlp.util;

import java.util.*;

/**
 * For interning (canonicalizing) things.
 * <p/>
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
 * <p/>
 * Note that in general it is just as good or better to use the
 * static Interner.globalIntern() method rather than making an
 * instance of Interner and using the instance-level intern().
 * <p/>
 * Author: Dan Klein
 * Date: 9/28/03
 *
 * @author Teg Grenager
 */
public class WrappingInterner<T> {
  
  protected Map<T,IdentityWrapper<T>> map = new WeakHashMap<T,IdentityWrapper<T>>();

  public void clear() { map = new WeakHashMap<T,IdentityWrapper<T>>(); }
  
  /**
   * Returns a unique object o' that .equals the argument o.  If o
   * itself is returned, this is the first request for an object
   * .equals to o.
   */
  public IdentityWrapper<T> intern(T o) {
    IdentityWrapper<T> i = map.get(o);
    if (i == null) {
      i = new IdentityWrapper<T>(o);
      map.put(o, i);
    }
    return i;
  }

  public int size() {
    return map.size();
  }

}
