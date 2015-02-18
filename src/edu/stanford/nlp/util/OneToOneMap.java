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

  public static class OneToOneMapException extends Exception{
    public OneToOneMapException(String iDesc)
    {
      super(iDesc);
    }

    private static final long serialVersionUID = 7743164489912070054L;

  }

  //------------------------------------------------------------

  private Map<L,R> m_leftAsKey;
  private Map<R,L> m_rightAsKey;

  public OneToOneMap()
  {
    m_leftAsKey = Generics.newHashMap();
    m_rightAsKey = Generics.newHashMap();
  }



  public boolean isEmpty()
  {
    return m_leftAsKey.isEmpty();
  }



  public int size()
  {
    return m_leftAsKey.size();
  }

  public void put(L l,R r) throws OneToOneMapException
  {
    boolean hasLeft = m_leftAsKey.containsKey(l);
    boolean hasRight = m_rightAsKey.containsKey(r);


    if(hasLeft != hasRight)
      throw new OneToOneMapException("Error: cannot insert multiple keys with the same value");

    m_leftAsKey.put(l,r);
    m_rightAsKey.put(r, l);
  }



  public R getLeftAsKey(L l)
  {
    return m_leftAsKey.get(l);
  }



  public L getRightAsKey(R r)
  {
    return m_rightAsKey.get(r);
  }



  public R removeLeftAsKey(L l)
  {
    R r = m_leftAsKey.remove(l);

    if(r != null)
      m_rightAsKey.remove(r);

    return r;
  }



  public L removeRightAsKey(R r)
  {
    L l = m_rightAsKey.remove(r);

    if(l != null)
      m_leftAsKey.remove(l);

    return l;
  }


  public Collection<R> valuesLeftAsKey()
  {
    return m_leftAsKey.values();
  }


  public Collection<L> valuesRightAsKey()
  {
    return m_rightAsKey.values();
  }


  public Set<Map.Entry<L,R>> entrySetLeftAsKey()
  {
    return m_leftAsKey.entrySet();
  }

  public Set<Map.Entry<R,L>> entrySetRightAsKey()
  {
    return m_rightAsKey.entrySet();
  }


  public boolean containsLeftAsKey(L l) {
    return m_leftAsKey.containsKey(l);
  }

  public boolean containsRightAsKey(R r) {
    return m_rightAsKey.containsKey(r);
  }

  public void clear() {
    m_leftAsKey.clear();
    m_rightAsKey.clear();
  }

  private static final long serialVersionUID = 1L;
}
