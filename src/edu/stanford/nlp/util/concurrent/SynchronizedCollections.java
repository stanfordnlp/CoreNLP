package edu.stanford.nlp.util.concurrent;

import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Factory;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;

import java.util.Random;
import java.util.Collection;
import java.util.Set;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.io.Writer;
import java.io.IOException;

/**
 * @author Michel Galley
 */
public class SynchronizedCollections {

  private SynchronizedCollections() {}

  public static <T> Index<T> synchronizedIndex(Index<T> index) {
    return new SynchronizedIndex<T>(index);
  }

  static class SynchronizedIndex<E> implements Index<E> {

    private static final long serialVersionUID = -7754090372962971523L;

    final Index<E> index;
    final Object mutex;     // Object on which to synchronize

    SynchronizedIndex(Index<E> index) {
      if (index==null)
        throw new NullPointerException();
      mutex = new Object();
      this.index = index;
    }

    public int size() {
	    synchronized(mutex) {return index.size();}
    }

    public int indexOf(E o) {
	    synchronized(mutex) {return index.indexOf(o);}
    }

    public int indexOf(E o, boolean add) {
	    synchronized(mutex) {return index.indexOf(o, add);}
    }

    public List<E> objectsList() {
      synchronized(mutex) {return index.objectsList();}
    }

    public Collection<E> objects(int[] ints) {
      synchronized(mutex) {return index.objects(ints);}
    }

    public boolean isLocked() {
      synchronized(mutex) {return index.isLocked();}
    }

    public void lock() {
      synchronized(mutex) {index.lock();}
    }

    public void unlock() {
      synchronized(mutex) {index.unlock();}
    }

    public void saveToWriter(Writer out) throws IOException {
      synchronized(mutex) {index.saveToWriter(out);}
    }

    public void saveToFilename(String s) {
      synchronized(mutex) {index.saveToFilename(s);}
    }

    public boolean contains(Object o) {
      synchronized(mutex) {return index.contains(o);}
    }

    public Iterator<E> iterator() {
      synchronized(mutex) {return index.iterator();}
    }

    public <T> T[] toArray(T[] a) {
      synchronized(mutex) {return index.toArray(a);}
    }

    public boolean add(E e) {
      synchronized(mutex) {return index.add(e);}
    }


    public boolean addAll(Collection<? extends E> c) {
      synchronized(mutex) {return index.addAll(c);}
    }

    public void clear() {
      synchronized(mutex) {index.clear();}
    }

    public E get(int i) {
	    synchronized(mutex) {return index.get(i);}
    }
  }

  public static <T> Counter<T> synchronizedCounter(Counter<T> counter) {
    return new SynchronizedCounter<T>(counter);
  }

  static class SynchronizedCounter<E> implements Counter<E> {

    private static final long serialVersionUID = -7754090372962971522L;

    final Counter<E> counter;
    final Object mutex;     // Object on which to synchronize

    SynchronizedCounter(Counter<E> counter) {
      if (counter==null)
        throw new NullPointerException();
      mutex = new Object();
      this.counter = counter;
    }

    public void clear() {
	    synchronized(mutex) {counter.clear();}
    }

    public double incrementCount(E key) {
	    synchronized(mutex) {return counter.incrementCount(key);}
    }

    public double incrementCount(E key, double v) {
	    synchronized(mutex) {return counter.incrementCount(key, v);}
    }

    public double logIncrementCount(E key, double v) {
	    synchronized(mutex) {return counter.logIncrementCount(key, v);}
    }

    public double decrementCount(E key) {
	    synchronized(mutex) {return counter.decrementCount(key);}
    }

    public double decrementCount(E key, double v) {
	    synchronized(mutex) {return counter.decrementCount(key, v);}
    }

    public Collection<Double> values() {
	    synchronized(mutex) {return counter.values();}
    }

    public void setCount(E key, double v) {
	    synchronized(mutex) {counter.setCount(key, v);}
    }

    public double getCount(Object key) {
	    synchronized(mutex) {return counter.getCount(key);}
    }

    public double totalCount() {
	    synchronized(mutex) {return counter.totalCount();}
    }

    public int size() {
	    synchronized(mutex) {return counter.size();}
    }

    public Set<E> keySet() {
	    synchronized(mutex) {return counter.keySet();}
    }

    public double defaultReturnValue() {
	    synchronized(mutex) {return counter.defaultReturnValue();}
    }

    public void setDefaultReturnValue(double v) {
	    synchronized(mutex) {counter.setDefaultReturnValue(v);}
    }

    public boolean containsKey(E key) {
	    synchronized(mutex) {return counter.containsKey(key);}
    }

    public double remove(E key) {
      synchronized(mutex) {return counter.remove(key);}
    }

    public void addAll(Counter<E> c) {
      synchronized(mutex) {counter.addAll(c);}
    }

    public Factory<Counter<E>> getFactory() {
      throw new UnsupportedOperationException();
    }

    public Set<Map.Entry<E,Double>> entrySet() {
      // either throw exception or require user to synchronize externally:
      // former for now
      throw new UnsupportedOperationException();
    }
    
    /**
     * {@inheritDoc}
     */
    public void prettyLog(RedwoodChannels channels, String description) {
      synchronized (mutex) {
        PrettyLogger.log(channels, description, Counters.asMap(this));  
      }
    }
  } // end static class SynchronizedCounter


  public static void main(String[] args) {
    final Index<Integer> index = synchronizedIndex(new HashIndex<Integer>());
    //final Index<Integer> index = new HashIndex<Integer>();
    for(int j=0; j<4; ++j) {
      Thread r = new Thread() {
        public void run() {
          Random r = new Random();
          for(int i=0; i<100000; ++i) {
            int obj = r.nextInt(10000);
            int idx = index.indexOf(obj,true);
            System.err.printf("thread %s got index %d for object %d\n",this,idx,obj);
          }
          //System.err.printf("thread %s sees an index of size %d\n",this,index.size());
        }
      };
      r.start();
    }

    // With index = new Index<Integer>():
    // java [...] | cut -f 5-8 -d' ' | sort | uniq | wc -l
    // 16163

    // With index = synchronizedIndex(new Index<Integer>()):
    // java [...] | cut -f 5-8 -d' ' | sort | uniq | wc -l
    // 10000
  }

}
