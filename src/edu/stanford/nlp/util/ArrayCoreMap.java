package edu.stanford.nlp.util;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.*;

import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;


/**
 * <p>
 * Base implementation of {@link CoreMap} backed by two Java arrays.
 * </p>
 *
 * <p>
 * Reasonable care has been put into ensuring that this class is both fast and
 * has a light memory footprint.
 * </p>
 *
 * <p>
 * Note that like the base classes in the Collections API, this implementation
 * is <em>not thread-safe</em>. For speed reasons, these methods are not
 * synchronized. A synchronized wrapper could be developed by anyone so
 * inclined.
 * </p>
 *
 * <p>
 * Equality is defined over the complete set of keys and values currently
 * stored in the map.  Because this class is mutable, it should not be used
 * as a key in a Map.
 * </p>
 *
 * @author dramage
 * @author rafferty
 */
public class ArrayCoreMap implements CoreMap /*, Serializable */ {

  /** Initial capacity of the array */
  private static final int INITIAL_CAPACITY = 4;

  /** Array of keys */
  private Class<? extends Key<?>>[] keys;

  /** Array of values */
  private Object[] values;

  /** Total number of elements actually in keys,values */
  private int size; // = 0;

  /**
   * Default constructor - initializes with default initial annotation
   * capacity of 4.
   */
  public ArrayCoreMap() {
    this(INITIAL_CAPACITY);
  }

  /**
   * Initializes this ArrayCoreMap, pre-allocating arrays to hold
   * up to capacity key,value pairs.  This array will grow if necessary.
   *
   * @param capacity Initial capacity of object in key,value pairs
   */
  public ArrayCoreMap(int capacity) {
    keys = ErasureUtils.uncheckedCast(new Class[capacity]);
    values = new Object[capacity];
    // size starts at 0
  }

  /**
   * Copy constructor.
   * @param other The ArrayCoreMap to copy. It may not be null.
   */
  public ArrayCoreMap(ArrayCoreMap other) {
    size = other.size;
    keys = Arrays.copyOf(other.keys, size);
    values = Arrays.copyOf(other.values, size);
  }

  /**
   * Copy constructor.
   * @param other The ArrayCoreMap to copy. It may not be null.
   */
  @SuppressWarnings("unchecked")
  public ArrayCoreMap(CoreMap other) {
    Set<Class<?>> otherKeys = other.keySet();

    size = otherKeys.size();
    keys = new Class[size];
    values = new Object[size];

    int i = 0;
    for (Class key : otherKeys) {
      this.keys[i] = key;
      this.values[i] = other.get(key);
      i++;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public <VALUE> VALUE get(Class<? extends Key<VALUE>> key) {
    for (int i = 0; i < size; i++) {
      if (key == keys[i]) {
        return (VALUE)values[i];
      }
    }
    return null;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <VALUE> boolean has(Class<? extends Key<VALUE>> key) {
    for (int i = 0; i < size; i++) {
      if (keys[i] == key) {
        return true;
      }
    }

    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public <VALUE> VALUE set(Class<? extends Key<VALUE>> key, VALUE value) {

    // search array for existing value to replace
    for (int i = 0; i < size; i++) {
      if (keys[i] == key) {
        VALUE rv = (VALUE)values[i];
        values[i] = value;
        return rv;
      }
    }
    // not found in arrays, add to end ...

    // increment capacity of arrays if necessary
    if (size >= keys.length) {
      int capacity = keys.length + (keys.length < 16 ? 4: 8);
      Class[] newKeys = new Class[capacity];
      Object[] newValues = new Object[capacity];
      System.arraycopy(keys, 0, newKeys, 0, size);
      System.arraycopy(values, 0, newValues, 0, size);
      keys = newKeys;
      values = newValues;
    }

    // store value
    keys[size] = key;
    values[size] = value;
    size++;

    return null;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Set<Class<?>> keySet() {

    return new AbstractSet<Class<?>>() {
      @Override
      public Iterator<Class<?>> iterator() {
        return new Iterator<Class<?>>() {
          private int i; // = 0;

          @Override
          public boolean hasNext() {
            return i < size;
          }

          @Override
          public Class<?> next() {
            try {
              return keys[i++];
            } catch (ArrayIndexOutOfBoundsException aioobe) {
              throw new NoSuchElementException("ArrayCoreMap keySet iterator exhausted");
            }
          }

          @Override
          @SuppressWarnings("unchecked")
          public void remove() {
            ArrayCoreMap.this.remove((Class)keys[i]);
          }
        };
      }

      @Override
      public int size() {
        return size;
      }
    };
  }

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public <VALUE> VALUE remove(Class<? extends Key<VALUE>> key) {

    Object rv = null;
    for (int i = 0; i < size; i++) {
      if (keys[i] == key) {
        rv = values[i];
        if (i < size - 1) {
          System.arraycopy(keys,   i+1, keys,   i, size-(i+1));
          System.arraycopy(values, i+1, values, i, size-(i+1));
        }
        size--;
        break;
      }
    }
    return (VALUE)rv;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public <VALUE> boolean containsKey(Class<? extends Key<VALUE>> key) {
    for (int i = 0; i < size; i++) {
      if (keys[i] == key) {
        return true;
      }
    }
    return false;
  }


  /**
   * Reduces memory consumption to the minimum for representing the values
   * currently stored stored in this object.
   */
  public void compact() {
    if (keys.length > size) {
      Class[] newKeys = new Class[size];
      Object[] newValues = new Object[size];
      System.arraycopy(keys, 0, newKeys, 0, size);
      System.arraycopy(values, 0, newValues, 0, size);
      keys = ErasureUtils.uncheckedCast(newKeys);
      values = newValues;
    }
  }

  public void setCapacity(int newSize) {
    if (size > newSize) { throw new RuntimeException("You cannot set capacity to smaller than the current size."); }
    Class[] newKeys = new Class[newSize];
    Object[] newValues = new Object[newSize];
    System.arraycopy(keys, 0, newKeys, 0, size);
    System.arraycopy(values, 0, newValues, 0, size);
    keys = ErasureUtils.uncheckedCast(newKeys);
    values = newValues;
  }

  /**
   * Returns the number of elements in this map.
   * @return The number of elements in this map.
   */
  @Override
  public int size() {
    return size;
  }

  /**
   * Keeps track of which ArrayCoreMaps have had toString called on
   * them.  We do not want to loop forever when there are cycles in
   * the annotation graph.  This is kept on a per-thread basis so that
   * each thread where toString gets called can keep track of its own
   * state.  When a call to toString is about to return, this is reset
   * to null for that particular thread.
   */
  private static final ThreadLocal<IdentityHashSet<CoreMap>> toStringCalled =
          new ThreadLocal<IdentityHashSet<CoreMap>>() {
            @Override protected IdentityHashSet<CoreMap> initialValue() {
              return new IdentityHashSet<CoreMap>();
            }
          };

  /** Prints a full dump of a CoreMap. This method is robust to
   *  circularity in the CoreMap.
   *
   *  @return A String representation of the CoreMap
   */
  @Override
  public String toString() {
    IdentityHashSet<CoreMap> calledSet = toStringCalled.get();
    boolean createdCalledSet = calledSet.isEmpty();

    if (calledSet.contains(this)) {
      return "[...]";
    }

    calledSet.add(this);

    StringBuilder s = new StringBuilder("[");
    for (int i = 0; i < size; i++) {
      s.append(keys[i].getSimpleName());
      s.append('=');
      s.append(values[i]);
      if (i < size-1) {
        s.append(' ');
      }
    }
    s.append(']');

    if (createdCalledSet) {
      toStringCalled.remove();
    } else {
      // Remove the object from the already called set so that
      // potential later calls in this object graph have something
      // more description than [...]
      calledSet.remove(this);
    }
    return s.toString();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toShorterString(String... what) {
    StringBuilder s = new StringBuilder("[");
    for (int i = 0; i < size; i++) {
      String name = keys[i].getSimpleName();
      int annoIdx = name.lastIndexOf("Annotation");
      if (annoIdx >= 0) {
        name = name.substring(0, annoIdx);
      }
      boolean include;
      if (what.length > 0) {
        include = false;
        for (String item : what) {
          if (item.equals(name)) {
            include = true;
            break;
          }
        }
      } else {
        include = true;
      }
      if (include) {
        if (s.length() > 1) {
          s.append(' ');
        }
        s.append(name);
        s.append('=');
        s.append(values[i]);
      }
    }
    s.append(']');
    return s.toString();
  }

  /** This gives a very short String representation of a CoreMap
   *  by leaving it to the content to reveal what field is being printed.
   *
   *  @param what An array (varargs) of Strings that say what annotation keys
   *     to print.  These need to be provided in a shortened form where you
   *     are just giving the part of the class name without package and up to
   *     "Annotation". That is,
   *     edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation
   *     -&gt; PartOfSpeech . As a special case, an empty array means
   *     to print everything, not nothing.
   *  @return Brief string where the field values are just separated by a
   *     character. If the string contains spaces, it is wrapped in "{...}".
   */
  public String toShortString(String... what) {
    return toShortString('/', what);
  }

  /** This gives a very short String representation of a CoreMap
   *  by leaving it to the content to reveal what field is being printed.
   *
   *  @param separator Character placed between fields in output
   *  @param what An array (varargs) of Strings that say what annotation keys
   *     to print.  These need to be provided in a shortened form where you
   *     are just giving the part of the class name without package and up to
   *     "Annotation". That is,
   *     edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation
   *     -&gt; PartOfSpeech . As a special case, an empty array means
   *     to print everything, not nothing.
   *  @return Brief string where the field values are just separated by a
   *     character. If the string contains spaces, it is wrapped in "{...}".
   */
  public String toShortString(char separator, String... what) {
    StringBuilder s = new StringBuilder();
    for (int i = 0; i < size; i++) {
      boolean include;
      if (what.length > 0) {
        String name = keys[i].getSimpleName();
        int annoIdx = name.lastIndexOf("Annotation");
        if (annoIdx >= 0) {
          name = name.substring(0, annoIdx);
        }
        include = false;
        for (String item : what) {
          if (item.equals(name)) {
            include = true;
            break;
          }
        }
      } else {
        include = true;
      }
      if (include) {
        if (s.length() > 0) {
          s.append(separator);
        }
        s.append(values[i]);
      }
    }
    String answer = s.toString();
    if (answer.indexOf(' ') < 0) {
      return answer;
    } else {
      return '{' + answer + '}';
    }
  }

  /**
   * Keeps track of which pairs of ArrayCoreMaps have had equals
   * called on them.  We do not want to loop forever when there are
   * cycles in the annotation graph.  This is kept on a per-thread
   * basis so that each thread where equals gets called can keep
   * track of its own state.  When a call to toString is about to
   * return, this is reset to null for that particular thread.
   */
  private static final ThreadLocal<TwoDimensionalMap<CoreMap, CoreMap, Boolean>> equalsCalled =
          new ThreadLocal<TwoDimensionalMap<CoreMap, CoreMap, Boolean>>();


  /**
   * Two CoreMaps are equal iff all keys and values are .equal.
   */
  @SuppressWarnings("unchecked")
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof CoreMap)) {
      return false;
    }

    if (obj instanceof HashableCoreMap) {
      // overridden behavior for HashableCoreMap
      return obj.equals(this);
    }

    if (obj instanceof ArrayCoreMap) {
      // specialized equals for ArrayCoreMap
      return equals((ArrayCoreMap)obj);
    }

    // TODO: make the general equality work in the situation of loops
    // in the object graph

    // general equality
    CoreMap other = (CoreMap)obj;
    if ( ! this.keySet().equals(other.keySet())) {
      return false;
    }
    for (Class key : this.keySet()) {
      if (!other.has(key)) {
        return false;
      }
      Object thisV = this.get(key), otherV = other.get(key);

      if (thisV == otherV) {
        continue;
      }
      // the two values must be unequal, so if either is null, the other isn't
      if (thisV == null || otherV == null) {
        return false;
      }

      if ( ! thisV.equals(otherV)) {
        return false;
      }
    }

    return true;
  }


  private boolean equals(ArrayCoreMap other) {
    TwoDimensionalMap<CoreMap, CoreMap, Boolean> calledMap = equalsCalled.get();
    boolean createdCalledMap = (calledMap == null);
    if (createdCalledMap) {
      calledMap = TwoDimensionalMap.identityHashMap();
      equalsCalled.set(calledMap);
    }

    // Note that for the purposes of recursion, we assume the two maps
    // are equals.  The two maps will therefore be equal if they
    // encounter each other again during the recursion unless there is
    // some other key that causes the equality to fail.
    // We do not need to later put false, as the entire call to equals
    // will unwind with false if any one equality check returns false.
    // TODO: since we only ever keep "true", we would rather use a
    // TwoDimensionalSet, but no such thing exists
    if (calledMap.contains(this, other)) {
      return true;
    }
    boolean result = true;
    calledMap.put(this, other, true);
    calledMap.put(other, this, true);

    if (this.size != other.size) {
      result = false;
    } else {
    for (int i = 0; i < this.size; i++) {
      // test if other contains this key,value pair
      boolean matched = false;
      for (int j = 0; j < other.size; j++) {
        if (this.keys[i] == other.keys[j]) {
          if ((this.values[i] == null && other.values[j] != null) ||
              (this.values[i] != null && other.values[j] == null)) {
            matched = false;
            break;
          }

          if ((this.values[i] == null && other.values[j] == null) ||
              (this.values[i].equals(other.values[j]))) {
            matched = true;
            break;
          }
        }
      }

      if (!matched) {
        result = false;
        break;
      }
    }
    }

    if (createdCalledMap) {
      equalsCalled.set(null);
    }
    return result;
  }

  /**
   * Keeps track of which ArrayCoreMaps have had hashCode called on
   * them.  We do not want to loop forever when there are cycles in
   * the annotation graph.  This is kept on a per-thread basis so that
   * each thread where hashCode gets called can keep track of its own
   * state.  When a call to toString is about to return, this is reset
   * to null for that particular thread.
   */
  private static final ThreadLocal<IdentityHashSet<CoreMap>> hashCodeCalled =
          new ThreadLocal<IdentityHashSet<CoreMap>>();


  /**
   * Returns a composite hashCode over all the keys and values currently
   * stored in the map.  Because they may change over time, this class
   * is not appropriate for use as map keys.
   */
  @Override
  public int hashCode() {
    IdentityHashSet<CoreMap> calledSet = hashCodeCalled.get();
    boolean createdCalledSet = (calledSet == null);
    if (createdCalledSet) {
      calledSet = new IdentityHashSet<CoreMap>();
      hashCodeCalled.set(calledSet);
    }

    if (calledSet.contains(this)) {
      return 0;
    }

    calledSet.add(this);

    int keysCode = 0;
    int valuesCode = 0;
    for (int i = 0; i < size; i++) {
      keysCode += (i < keys.length ? keys[i].hashCode() : 0);
      valuesCode += (i < values.length && values[i] != null ? values[i].hashCode() : 0);
    }

    if (createdCalledSet) {
      hashCodeCalled.set(null);
    } else {
      // Remove the object after processing is complete so that if
      // there are multiple instances of this CoreMap in the overall
      // object graph, they each have their hash code calculated.
      // TODO: can we cache this for later?
      calledSet.remove(this);
    }
    return keysCode * 37 + valuesCode;
  }

  //
  // serialization magic
  //

  /** Serialization version id */
  private static final long serialVersionUID = 1L;

  /**
   * Overridden serialization method: compacts our map before writing.
   *
   * @param out Stream to write to
   * @throws IOException If IO error
   */
  private void writeObject(ObjectOutputStream out) throws IOException {
    compact();
    out.defaultWriteObject();
  }

  // TODO: make prettyLog work in the situation of loops
  // in the object graph

  /**
   * {@inheritDoc}
   */
  @Override
  @SuppressWarnings("unchecked")
  public void prettyLog(RedwoodChannels channels, String description) {
    Redwood.startTrack(description);

    // sort keys by class name
    List<Class> sortedKeys = new ArrayList<Class>(this.keySet());
    Collections.sort(sortedKeys,
        new Comparator<Class>(){
      @Override
      public int compare(Class a, Class b) {
        return a.getCanonicalName().compareTo(b.getCanonicalName());
      }
    });

    // log key/value pairs
    for (Class key : sortedKeys) {
      String keyName = key.getCanonicalName().replace("class ", "");
      Object value = this.get(key);
      if (PrettyLogger.dispatchable(value)) {
        PrettyLogger.log(channels, keyName, value);
      } else {
        channels.logf("%s = %s", keyName, value);
      }
    }
    Redwood.endTrack(description);
  }

}
