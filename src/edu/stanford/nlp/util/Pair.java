package edu.stanford.nlp.util;

import java.io.DataOutputStream;
import java.io.Serializable;
import java.util.Comparator;
import java.util.List;

import edu.stanford.nlp.util.logging.PrettyLoggable;
import edu.stanford.nlp.util.logging.PrettyLogger;
import edu.stanford.nlp.util.logging.Redwood.RedwoodChannels;

/**
 * Pair is a Class for holding mutable pairs of objects.
 * <p>
 * <i>Implementation note:</i>
 * On a 32-bit JVM uses ~ 8 (this) + 4 (first) + 4 (second) = 16 bytes.
 * On a 64-bit JVM uses ~ 16 (this) + 8 (first) + 8 (second) = 32 bytes.
 * <p>
 * Many applications use a lot of Pairs so it's good to keep this
 * number small.
 *
 * @author Dan Klein
 * @author Christopher Manning (added stuff from Kristina's, rounded out)
 * @version 2002/08/25
 */

public class Pair <T1,T2> implements Comparable<Pair<T1,T2>>, Serializable, PrettyLoggable {

  /**
   * Direct access is deprecated.  Use first().
   *
   * @serial
   */
  public T1 first;

  /**
   * Direct access is deprecated.  Use second().
   *
   * @serial
   */
  public T2 second;

  public Pair() {
    // first = null; second = null; -- default initialization
  }

  public Pair(T1 first, T2 second) {
    this.first = first;
    this.second = second;
  }

  public T1 first() {
    return first;
  }

  public T2 second() {
    return second;
  }

  public void setFirst(T1 o) {
    first = o;
  }

  public void setSecond(T2 o) {
    second = o;
  }

  @Override
  public String toString() {
    return "(" + first + "," + second + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof Pair) {
      @SuppressWarnings("rawtypes")
      Pair p = (Pair) o;
      return (first == null ? p.first() == null : first.equals(p.first())) && (second == null ? p.second() == null : second.equals(p.second()));
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    int firstHash  = (first == null ? 0 : first.hashCode());
    int secondHash = (second == null ? 0 : second.hashCode());

    return firstHash*31 + secondHash;
  }

  public List<Object> asList() {
    return CollectionUtils.makeList(first, second);
  }

  /**
   * Returns a Pair constructed from X and Y.  Convenience method; the
   * compiler will disambiguate the classes used for you so that you
   * don't have to write out potentially long class names.
   */
  public static <X, Y> Pair<X, Y> makePair(X x, Y y) {
    return new Pair<>(x, y);
  }

  /**
   * Write a string representation of a Pair to a DataStream.
   * The {@code toString()} method is called on each of the pair
   * of objects and a {@code String} representation is written.
   * This might not allow one to recover the pair of objects unless they
   * are of type {@code String}.
   */
  public void save(DataOutputStream out) {
    try {
      out.writeUTF(first.toString());
      out.writeUTF(second.toString());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Compares this {@code Pair} to another object.
   * If the object is a {@code Pair}, this function will work providing
   * the elements of the {@code Pair} are themselves comparable.
   * It will then return a value based on the pair of objects, where
   * <code>p &gt; q iff p.first() &gt; q.first() ||
   * (p.first().equals(q.first()) && p.second() &gt; q.second())</code>.
   * If the other object is not a {@code Pair}, it throws a
   * {@code ClassCastException}.
   *
   * @param another the {@code Object} to be compared.
   * @return the value {@code 0} if the argument is a
   *         {@code Pair} equal to this {@code Pair}; a value less than
   *         {@code 0} if the argument is a {@code Pair}
   *         greater than this {@code Pair}; and a value
   *         greater than {@code 0} if the argument is a
   *         {@code Pair} less than this {@code Pair}.
   * @throws ClassCastException if the argument is not a
   *                            {@code Pair}.
   * @see java.lang.Comparable
   */
  @SuppressWarnings("unchecked")
  public int compareTo(Pair<T1,T2> another) {
    if (first() instanceof Comparable) {
      int comp = ((Comparable<T1>) first()).compareTo(another.first());
      if (comp != 0) {
        return comp;
      }
    }

    if (second() instanceof Comparable) {
      return ((Comparable<T2>) second()).compareTo(another.second());
    }

    if ((!(first() instanceof Comparable)) && (!(second() instanceof Comparable))) {
      throw new AssertionError("Neither element of pair comparable");
    }

    return 0;
  }

  /**
   * If first and second are Strings, then this returns an MutableInternedPair
   * where the Strings have been interned, and if this Pair is serialized
   * and then deserialized, first and second are interned upon
   * deserialization.
   *
   * @param p A pair of Strings
   * @return MutableInternedPair, with same first and second as this.
   */
  public static Pair<String, String> stringIntern(Pair<String, String> p) {
    return new MutableInternedPair(p);
  }

  /**
   * Returns an MutableInternedPair where the Strings have been interned.
   * This is a factory method for creating an
   * MutableInternedPair.  It requires the arguments to be Strings.
   * If this Pair is serialized
   * and then deserialized, first and second are interned upon
   * deserialization.
   * <p><i>Note:</i> I put this in thinking that its use might be
   * faster than calling <code>x = new Pair(a, b).stringIntern()</code>
   * but it's not really clear whether this is true.
   *
   * @param first  The first object
   * @param second The second object
   * @return An MutableInternedPair, with given first and second
   */
  public static Pair<String, String> internedStringPair(String first, String second) {
    return new MutableInternedPair(first, second);
  }


  /**
   * use serialVersionUID for cross version serialization compatibility
   */
  private static final long serialVersionUID = 1360822168806852921L;


  static class MutableInternedPair extends Pair<String, String> {

    private MutableInternedPair(Pair<String, String> p) {
      super(p.first, p.second);
      internStrings();
    }

    private MutableInternedPair(String first, String second) {
      super(first, second);
      internStrings();
    }

    protected Object readResolve() {
      internStrings();
      return this;
    }

    private void internStrings() {
      if (first != null) {
        first = first.intern();
      }
      if (second != null) {
        second = second.intern();
      }
    }

    // use serialVersionUID for cross version serialization compatibility
    private static final long serialVersionUID = 1360822168806852922L;

  }

  /**
   * {@inheritDoc}
   */
  public void prettyLog(RedwoodChannels channels, String description) {
    PrettyLogger.log(channels, description, this.asList());
  }
  
  /**
   * Compares a {@code Pair} to another {@code Pair} according to the first object of the pair only
   * This function will work providing the first element of the {@code Pair} is comparable,
   * otherwise will throw a {@code ClassCastException}.
   *
   * @author jonathanberant
   */
  public static class ByFirstPairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

    @SuppressWarnings("unchecked")
    @Override
    public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
      return ((Comparable<T1>) pair1.first()).compareTo(pair2.first());
    }
  }
  
  /**
   * Compares a {@code Pair} to another {@code Pair} according to the first object of the pair only in decreasing order
   * This function will work providing
   * the first element of the {@code Pair} is comparable, otherwise will throw a {@code ClassCastException}
   * @author jonathanberant
   */
  public static class ByFirstReversePairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

    @SuppressWarnings("unchecked")
    @Override
    public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
      return -((Comparable<T1>) pair1.first()).compareTo(pair2.first());
    }
  }
  
  /**
   * Compares a {@code Pair} to another {@code Pair} according to the second object of the pair only
   * This function will work providing
   * the first element of the {@code Pair} is comparable, otherwise will throw a {@code ClassCastException}
   * @author jonathanberant
   */
  public static class BySecondPairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

    @SuppressWarnings("unchecked")
    @Override
    public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
      return ((Comparable<T2>) pair1.second()).compareTo(pair2.second());
    }
  }
  
  /**
   * Compares a {@code Pair} to another {@code Pair} according to the second object of the pair only in decreasing order
   * This function will work providing
   * the first element of the {@code Pair} is comparable, otherwise will throw a {@code ClassCastException}
   *
   * @author jonathanberant
   */
  public static class BySecondReversePairComparator<T1,T2> implements Comparator<Pair<T1,T2>> {

    @SuppressWarnings("unchecked")
    @Override
    public int compare(Pair<T1, T2> pair1, Pair<T1, T2> pair2) {
      return -((Comparable<T2>) pair1.second()).compareTo(pair2.second());
    }
  }
  
}
