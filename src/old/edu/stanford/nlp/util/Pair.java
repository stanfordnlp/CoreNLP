package old.edu.stanford.nlp.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.Serializable;

/**
 * Pair is a Class for holding a pair of objects.
 * <i>Implementation note:</i>
 * uses ~ 8 (this) + 4 (first) + 4 (second) = 16 bytes.
 * Many applications use a lot of Pair's so it's good to keep this
 * number small.
 *
 * @author Dan Klein
 * @author Christopher Manning (added stuff from Kristina's, rounded out)
 * @version 2002/08/25
 */
public class Pair <T1,T2> implements Comparable<Pair<T1,T2>>, Serializable {

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
  @SuppressWarnings("unchecked")
  public boolean equals(Object o) {
    if (o instanceof Pair) {
      Pair p = (Pair) o;
      return (first == null ? p.first == null : first.equals(p.first)) && (second == null ? p.second == null : second.equals(p.second));
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return (((first == null) ? 0 : first.hashCode()) << 16) ^ ((second == null) ? 0 : second.hashCode());
  }

  /**
   * Read a string representation of a Pair from a DataStream.
   * This might not work correctly unless the pair of objects are of type
   * <code>String</code>.
   */
  public static Pair<String, String> readStringPair(DataInputStream in) {
    Pair<String, String> p = new Pair<String, String>();
    try {
      p.first = in.readUTF();
      p.second = in.readUTF();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return p;
  }

  /**
   * Write a string representation of a Pair from a DataStream.
   * The <code>toString()</code> method is called on each of the pair
   * of objects and a <code>String</code> representation is written.
   * This might not allow one to recover the pair of objects unless they
   * are of type <code>String</code>.
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
   * Compares this <code>Pair</code> to another object.
   * If the object is a <code>Pair</code>, this function will work providing
   * the elements of the <code>Pair</code> are themselves comparable.
   * It will then return a value based on the pair of objects, where
   * <code>p &gt; q iff p.first() &gt; q.first() ||
   * (p.first().equals(q.first()) && p.second() &gt; q.second())</code>.
   * If the other object is not a <code>Pair</code>, it throws a
   * <code>ClassCastException</code>.
   *
   * @param another the <code>Object</code> to be compared.
   * @return the value <code>0</code> if the argument is a
   *         <code>Pair</code> equal to this <code>Pair</code>; a value less than
   *         <code>0</code> if the argument is a <code>Pair</code>
   *         greater than this <code>Pair</code>; and a value
   *         greater than <code>0</code> if the argument is a
   *         <code>Pair</code> less than this <code>Pair</code>.
   * @throws ClassCastException if the argument is not a
   *                            <code>Pair</code>.
   * @see Comparable
   */
  @SuppressWarnings("unchecked")
  public int compareTo(Pair<T1,T2> another) {
    int comp = ((Comparable<T1>) first()).compareTo(another.first());
    if (comp != 0) {
      return comp;
    } else {
      return ((Comparable<T2>) second()).compareTo(another.second());
    }
  }

  /**
   * If first and second are Strings, then this returns an InternedPair
   * where the Strings have been interned, and if this Pair is serialized
   * and then deserialized, first and second are interned upon
   * deserialization.
   *
   * @param p A pair of Strings
   * @return InternedPair, with same first and second as this.
   */
  public static Pair<String, String> stringIntern(Pair<String, String> p) {
    return new InternedPair(p);
  }

  /**
   * Returns an InternedPair where the Strings have been interned.
   * This is a factory method for creating an
   * InternedPair.  It requires the arguments to be Strings.
   * If this Pair is serialized
   * and then deserialized, first and second are interned upon
   * deserialization.
   * <p><i>Note:</i> I put this in thinking that its use might be
   * faster than calling <code>x = new Pair(a, b).stringIntern()</code>
   * but it's not really clear whether this is true.
   *
   * @param first  The first object
   * @param second The second object
   * @return An InternedPair, with given first and second
   */
  public static Pair<String, String> internedStringPair(String first, String second) {
    return new InternedPair(first, second);
  }


  /**
   * use serialVersionUID for cross version serialization compatibility
   */
  private static final long serialVersionUID = 1360822168806852921L;


  static class InternedPair extends Pair<String, String> {

    private InternedPair(Pair<String, String> p) {
      super(p.first, p.second);
      internStrings();
    }

    private InternedPair(String first, String second) {
      super(first, second);
      internStrings();
    }

    private Object readResolve() {
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
}
