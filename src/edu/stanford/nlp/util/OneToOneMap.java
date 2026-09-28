package edu.stanford.nlp.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * One to one map that allows to get a value for a key and a key for a value in O(1).
 *
 * @author jonathanberant
 *
 * @param <L> keys on the left
 * @param <R> keys on the right
 */
public class OneToOneMap<L,R> implements Serializable{

  /** Thrown when an insertion would break the one-to-one mapping. */
  public static class OneToOneMapException extends Exception{
    /**
     * Creates an exception with the given message.
     *
     * @param iDesc The detail message
     */
    public OneToOneMapException(String iDesc)
    {
      super(iDesc);
    }

    private static final long serialVersionUID = 7743164489912070054L;

  }

  //------------------------------------------------------------

  /** Map from left keys to right keys. */
  private Map<L,R> m_leftAsKey;
  /** Map from right keys to left keys. */
  private Map<R,L> m_rightAsKey;

  /** Creates an empty map. */
  public OneToOneMap()
  {
    m_leftAsKey = Generics.newHashMap();
    m_rightAsKey = Generics.newHashMap();
  }



  /**
   * Returns whether the map is empty.
   *
   * @return Whether there are no pairs in the map
   */
  public boolean isEmpty()
  {
    return m_leftAsKey.isEmpty();
  }



  /**
   * Returns the number of pairs.
   *
   * @return The number of left keys in the map
   */
  public int size()
  {
    return m_leftAsKey.size();
  }

  /**
   * Adds the pair {@code (l, r)}.  If both {@code l} and {@code r} are already
   * present, the pair is added without removing their existing partners.
   *
   * @param l The left key
   * @param r The right key
   * @throws OneToOneMapException if exactly one of {@code l} and {@code r} is already in the map
   */
  public void put(L l,R r) throws OneToOneMapException
  {
    boolean hasLeft = m_leftAsKey.containsKey(l);
    boolean hasRight = m_rightAsKey.containsKey(r);


    if(hasLeft != hasRight)
      throw new OneToOneMapException("Error: cannot insert multiple keys with the same value");

    m_leftAsKey.put(l,r);
    m_rightAsKey.put(r, l);
  }



  /**
   * Returns the right key paired with a left key.
   *
   * @param l The left key
   * @return The right key, or null if {@code l} is not in the map
   */
  public R getLeftAsKey(L l)
  {
    return m_leftAsKey.get(l);
  }



  /**
   * Returns the left key paired with a right key.
   *
   * @param r The right key
   * @return The left key, or null if {@code r} is not in the map
   */
  public L getRightAsKey(R r)
  {
    return m_rightAsKey.get(r);
  }



  /**
   * Removes the pair with the given left key.
   *
   * @param l The left key
   * @return The right key it was paired with, or null if {@code l} was not in the map
   */
  public R removeLeftAsKey(L l)
  {
    R r = m_leftAsKey.remove(l);

    if(r != null)
      m_rightAsKey.remove(r);

    return r;
  }



  /**
   * Removes the pair with the given right key.
   *
   * @param r The right key
   * @return The left key it was paired with, or null if {@code r} was not in the map
   */
  public L removeRightAsKey(R r)
  {
    L l = m_rightAsKey.remove(r);

    if(l != null)
      m_leftAsKey.remove(l);

    return l;
  }


  /**
   * Returns the right keys.
   *
   * @return The values view of the left-to-right map (not a copy)
   */
  public Collection<R> valuesLeftAsKey()
  {
    return m_leftAsKey.values();
  }


  /**
   * Returns the left keys.
   *
   * @return The values view of the right-to-left map (not a copy)
   */
  public Collection<L> valuesRightAsKey()
  {
    return m_rightAsKey.values();
  }


  /**
   * Returns the pairs as left-to-right entries.
   *
   * @return The entry set of the left-to-right map (not a copy)
   */
  public Set<Map.Entry<L,R>> entrySetLeftAsKey()
  {
    return m_leftAsKey.entrySet();
  }

  /**
   * Returns the pairs as right-to-left entries.
   *
   * @return The entry set of the right-to-left map (not a copy)
   */
  public Set<Map.Entry<R,L>> entrySetRightAsKey()
  {
    return m_rightAsKey.entrySet();
  }


  /**
   * Returns whether a left key is in the map.
   *
   * @param l The left key
   * @return Whether {@code l} is present
   */
  public boolean containsLeftAsKey(L l) {
    return m_leftAsKey.containsKey(l);
  }

  /**
   * Returns whether a right key is in the map.
   *
   * @param r The right key
   * @return Whether {@code r} is present
   */
  public boolean containsRightAsKey(R r) {
    return m_rightAsKey.containsKey(r);
  }

  /** Removes all pairs. */
  public void clear() {
    m_leftAsKey.clear();
    m_rightAsKey.clear();
  }

  private static final long serialVersionUID = 1L;
}
