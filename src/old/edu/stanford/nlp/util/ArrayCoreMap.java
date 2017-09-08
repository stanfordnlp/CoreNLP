package old.edu.stanford.nlp.util;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;


/**
 * <p>
 * Base implementation of {@link CoreMap} backed by Java Arrays.
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
 * as a key in a HashMap.
 * </p>
 *
 * @author dramage
 * @author rafferty
 */
public class ArrayCoreMap implements CoreMap, Serializable {

  /** Initial capacity of the array */
  private static final int INITIAL_CAPACITY = 4;

  /** Array of keys */
  private Class<?>[] keys;

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
   * Initializes this MapNLPObject, pre-allocating arrays to hold
   * up to capacity key,value pairs.  This array will grow if necessary.
   *
   * @param capacity Initial capacity of object in key,value pairs
   */
  public ArrayCoreMap(int capacity) {
    keys = new Class<?>[capacity];
    values = new Object[capacity];
  }

  /**
   * Copy constructor.
   * @param other The ArrayCoreMap to copy
   */
  public ArrayCoreMap(ArrayCoreMap other) {
    size = other.size;
    keys = new Class<?>[size];
    values = new Object[size];

    for (int i = 0; i < size; i++) {
      this.keys[i] = other.keys[i];
      this.values[i] = other.values[i];
    }
  }

  /**
   * Copy constructor.
   * @param other The ArrayCoreMap to copy
   */
  @SuppressWarnings("unchecked")
  public ArrayCoreMap(CoreMap other) {
    Set<Class<?>> otherKeys = other.keySet();

    size = otherKeys.size();
    keys = new Class<?>[size];
    values = new Object[size];

    int i = 0;
    for (Class<?> key : otherKeys) {
      this.keys[i] = key;
      this.values[i] = other.get((Class)key);
      i++;
    }
  }

  /**
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  public <VALUE, KEY extends Key<CoreMap, VALUE>>
    VALUE get(Class<KEY> key) {
    for (int i = size; i > 0; ) {
      if (keys[--i] == key) {
        return (VALUE)values[i];
      }
    }
    return null;
  }



  /**
   * {@inheritDoc}
   */
  public <VALUE, KEY extends Key<CoreMap, VALUE>>
    boolean has(Class<KEY> key) {

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
  @SuppressWarnings("unchecked")
  public <VALUEBASE, VALUE extends VALUEBASE, KEY extends Key<CoreMap, VALUEBASE>>
    VALUE set(Class<KEY> key, VALUE value) {

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
      int capacity = keys.length + Math.min((keys.length >> 1) + 1, 8);
      Class<?>[] newKeys = new Class<?>[capacity];
      Object[] newVals = new Object[capacity];
      System.arraycopy(keys, 0, newKeys, 0, size);
      System.arraycopy(values, 0, newVals, 0, size);
      keys = newKeys;
      values = newVals;
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
  public Set<Class<?>> keySet() {

    return new AbstractSet<Class<?>>() {
      @Override
      public Iterator<Class<?>> iterator() {
        return new Iterator<Class<?>>() {
          private int i; // = 0;

          public boolean hasNext() {
            return i < size;
          }

          public Class<?> next() {
            try {
              return keys[i++];
            } catch (ArrayIndexOutOfBoundsException aioobe) {
              throw new NoSuchElementException("ArrayCoreMap keySet iterator exhausted");
            }
          }

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
  @SuppressWarnings("unchecked")
  public <VALUE, KEY extends Key<CoreMap, VALUE>>
    VALUE remove(Class<KEY> key) {

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
  public <VALUE, KEY extends Key<CoreMap, VALUE>>
  boolean containsKey(Class<KEY> key) {
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
      Class<?>[] newKeys = new Class<?>[size];
      Object[] newVals = new Object[size];
      System.arraycopy(keys, 0, newKeys, 0, size);
      System.arraycopy(values, 0, newVals, 0, size);
      keys = newKeys;
      values = newVals;
    }
  }

  /**
   * Returns the number of elements in this map.
   * @return The number of elements in this map.
   */
  public int size() {
    return size;
  }

  @Override
  public String toString() {
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
    return s.toString();
  }

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
    if (this.size != other.size) {
      return false;
    }

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
        return false;
      }
    }

    return true;
  }

  /**
   * Returns a composite hashcode over all the keys and values currently
   * stored in the map.  Because they may change over time, this class
   * is not appropriate for use as map keys.
   */
  @Override
  public int hashCode() {
    int keyscode = 0;
    int valuescode = 0;
    for (int i = 0; i < size; i++) {
      keyscode += keys[i].hashCode();
      valuescode += (values[i] != null ? values[i].hashCode() : 0);
    }
    return keyscode * 37 + valuescode;
  }

  //
  // serialization magic
  //

  /** Serialization version id */
  private static final long serialVersionUID = 1L;

  /**
   * Overriden serialization method: compacts our map before writing.
   *
   * @param out Stream to write to
   * @throws IOException If IO error
   */
  private void writeObject(ObjectOutputStream out) throws IOException {
    compact();
    out.defaultWriteObject();
  }

}
