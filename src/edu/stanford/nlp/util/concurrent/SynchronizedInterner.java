package edu.stanford.nlp.util.concurrent;

import java.util.Set;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Interner;

/**
 * <p>
 * For interning (canonicalizing) things in a multi-threaded environment.
 * </p>
 *
 * <p>
 * Maps any object to a unique interned version which .equals the
 * presented object.  If presented with a new object which has no
 * previous interned version, the presented object becomes the
 * interned version.  You can tell if your object has been chosen as
 * the new unique representative by checking whether o == intern(o).
 * The interners use a concurrent map with weak references, meaning that
 * if the only pointers to an interned item are the interners' backing maps,
 * that item can still be garbage collected.  Since the gc thread can
 * silently remove things from the backing map, there's no public way to
 * get the backing map, but feel free to add one at your own risk.
 * </p>
 * Note that in general it is just as good or better to use the
 * static SynchronizedInterner.globalIntern() method rather than making an
 * instance of SynchronizedInterner and using the instance-level intern().
 * <br>
 *
 * @author Ilya Sherman
 * @see edu.stanford.nlp.util.Interner
 * @param <T> The type of the objects being interned
 */
// TODO would be nice to have this share an interface with Interner
public class SynchronizedInterner<T> {
  /** The lock used by the global interner and the static methods. */
  protected static final Object globalMutex = new Object();
  /** The interner used by the static methods. */
  protected static SynchronizedInterner<Object> interner =
     Generics.newSynchronizedInterner(Interner.getGlobal(), globalMutex);


  /**
   * For getting the instance that global methods use.
   *
   * @return The global interner
   */
  public static SynchronizedInterner<Object> getGlobal() {
    synchronized(globalMutex) {
      return interner;
    }
  }

  /**
   * For supplying a new instance for the global methods to use.
   * The new interner synchronizes on itself rather than on {@link #globalMutex}.
   *
   * @param delegate The interner to wrap and install as the global interner
   * @return the previous global interner.
   */
  public static SynchronizedInterner<Object> setGlobal(Interner<Object> delegate) {
    synchronized(globalMutex) {
      SynchronizedInterner<Object> oldInterner = SynchronizedInterner.interner;
      SynchronizedInterner.interner = Generics.newSynchronizedInterner(delegate);
      return oldInterner;
    }
  }

  /**
   * Returns a unique object o' that .equals the argument o.  If o
   * itself is returned, this is the first request for an object
   * .equals to o.  Uses the global interner.
   *
   * @param <T> The type of the object
   * @param o The object to intern
   * @return The canonical object equal to {@code o}
   */
  @SuppressWarnings("unchecked")
  public static <T> T globalIntern(T o) {
    synchronized(globalMutex) {
      return (T) getGlobal().intern(o);
    }
  }


  /** The interner that does the actual work. */
  protected final Interner<T> delegate;
  /** The object synchronized on around each call to {@link #delegate}. */
  protected final Object mutex;

  /**
   * Wraps {@code delegate}, synchronizing on this object.
   *
   * @param delegate The interner to wrap
   * @throws NullPointerException If {@code delegate} is null
   */
  public SynchronizedInterner(Interner<T> delegate) {
    if (delegate == null) throw new NullPointerException();
    this.delegate = delegate;
    this.mutex = this;
  }

  /**
   * Wraps {@code delegate}, synchronizing on {@code mutex}.
   *
   * @param delegate The interner to wrap
   * @param mutex The object to synchronize on
   * @throws NullPointerException If {@code delegate} is null
   */
  public SynchronizedInterner(Interner<T> delegate, Object mutex) {
    if (delegate == null) throw new NullPointerException();
    this.delegate = delegate;
    this.mutex = mutex;
  }

  /** Removes all interned objects from the delegate interner. */
  public void clear() {
    synchronized(mutex) {
      delegate.clear();
    }
  }

  /**
   * Returns a unique object o' that .equals the argument o.  If o
   * itself is returned, this is the first request for an object
   * .equals to o.
   *
   * @param o The object to intern
   * @return The canonical object equal to {@code o}
   */
  public T intern(T o) {
    synchronized(mutex) {
      return delegate.intern(o);
    }
  }

  /**
   * Returns a <code>Set</code> such that each element in the returned set
   * is a unique object e' that .equals the corresponding element e in the
   * original set.
   *
   * @param s The objects to intern
   * @return A set of the interned versions of the objects in {@code s}
   */
  public Set<T> internAll(Set<T> s) {
    synchronized(mutex) {
      return delegate.internAll(s);
    }
  }

  /**
   * Returns the number of objects in the delegate interner.
   *
   * @return The number of interned objects
   */
  public int size() {
    synchronized(mutex) {
      return delegate.size();
    }
  }

  /**
   * Test method: interns its arguments with the global interner in 100 threads.
   * A thread throws an {@code AssertionError} if an argument's interned version is not
   * the argument itself.
   *
   * @param args The strings to intern
   * @throws InterruptedException If interrupted while waiting for the threads to finish
   */
  public static void main(final String[] args) throws InterruptedException {
    final Thread[] threads = new Thread[100];
    for (int i = 0; i < threads.length; i++) {
      threads[i] = new Thread(() -> {
        for (String str : args) {
          String interned = SynchronizedInterner.globalIntern(str);
          Thread.yield();
          if (interned != str)
            throw new AssertionError("Interning failed for " + str);
        }
      });
    }
    for (Thread thread : threads) {
      thread.start();
    }
    for (Thread thread : threads) {
      thread.join();
    }
  }
}