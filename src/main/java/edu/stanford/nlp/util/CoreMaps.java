package edu.stanford.nlp.util;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;

import java.util.*;

/**
 * Utility functions for working with {@link CoreMap}'s.
 *
 * @author dramage
 * @author Gabor Angeli (merge() method)
 */
public class CoreMaps {

  private CoreMaps() {} // static stuff


  /**
   * Merge one CoreMap into another -- that is, overwrite and add any keys in
   * the base CoreMap with those in the one to be merged.
   * This method is functional -- neither of the argument CoreMaps are changed.
   * @param base The CoreMap to serve as the base (keys in this are lower priority)
   * @param toBeMerged The CoreMap to merge in (keys in this are higher priority)
   * @return A new CoreMap representing the merge of the two inputs
   */
  public static CoreMap merge(CoreMap base, CoreMap toBeMerged){
    //(variables)
    CoreMap rtn = new ArrayCoreMap(base.size());
    //(copy base)
    for(Class key : base.keySet()){
      rtn.set(key, base.get(key));
    }
    //(merge)
    for(Class key : toBeMerged.keySet()){
      rtn.set(key,toBeMerged.get(key));
    }
    //(return)
    return rtn;
  }

  /**
   * see merge(CoreMap base, CoreMap toBeMerged)
   */
  public static CoreLabel merge(CoreLabel base, CoreLabel toBeMerged){
    //(variables)
    CoreLabel rtn = new CoreLabel(base.size());
    //(copy base)
    for(Class key : base.keySet()){
      rtn.set(key,base.get(key));
    }
    //(merge)
    for(Class key : toBeMerged.keySet()){
      rtn.set(key,toBeMerged.get(key));
    }
    //(return)
    return rtn;
  }

  /**
   * Returns a view of a collection of CoreMaps as a Map from each CoreMap to
   * the value it stores under valueKey. Changes to the map are propagated
   * directly to the coremaps in the collection and to the collection itself in
   * the case of removal operations.  Keys added or removed from the given
   * collection by anything other than the returned map will leave the map
   * in an undefined state.
   */
  public static <V, CM extends CoreMap, COLL extends Collection<CM>> Map<CM,V>
    asMap(final COLL coremaps, final Class<? extends TypesafeMap.Key<V>> valueKey) {

    final IdentityHashMap<CM,Boolean> references = new IdentityHashMap<>();
    for(CM map : coremaps){
      references.put(map, true);
    }

    // an EntrySet view of the elements of coremaps
    final Set<Map.Entry<CM, V>> entrySet = new AbstractSet<Map.Entry<CM,V>>() {
      @Override
      public Iterator<Map.Entry<CM, V>> iterator() {
        return new Iterator<Map.Entry<CM,V>>() {
          Iterator<CM> it = coremaps.iterator();
          CM last = null;

          public boolean hasNext() {
            return it.hasNext();
          }

          public Map.Entry<CM, V> next() {
            final CM next = it.next();
            last = next;
            return new Map.Entry<CM,V>() {
              public CM getKey() {
                return next;
              }

              public V getValue() {
                return next.get(valueKey);
              }

              public V setValue(V value) {
                return next.set(valueKey, value);
              }
            };
          }

          public void remove() {
            references.remove(last);
            it.remove();
          }
        };
      }

      @Override
      public int size() {
        return coremaps.size();
      }
    };

    return new AbstractMap<CM,V>() {
      @Override
      public int size() {
        return coremaps.size();
      }

      @Override
      public boolean containsKey(Object key) {
        return coremaps.contains(key);
      }

      @Override
      public V get(Object key) {
        if (!references.containsKey(key)) {
          return null;
        }
        return ((CoreMap)key).get(valueKey);
      }

      @Override
      public V put(CM key, V value) {
        if (!references.containsKey(key)) {
          coremaps.add(key);
          references.put(key,true);
        }
        return key.set(valueKey, value);
      }

      @Override
      public V remove(Object key) {
        if (!references.containsKey(key)) {
          return null;
        }
        return coremaps.remove(key) ? ((CoreMap)key).get(valueKey) : null;
      }

      @Override
      public Set<Map.Entry<CM, V>> entrySet() {
        return entrySet;
      }
    };
  }

  /**
   * Utility function for dumping all the keys and values of a CoreMap to a String.
   */
  public static String dumpCoreMap(CoreMap cm) {
    StringBuilder sb = new StringBuilder();
    dumpCoreMapToStringBuilder(cm, sb);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  public static void dumpCoreMapToStringBuilder(CoreMap cm, StringBuilder sb) {
    for (Class<?> rawKey : cm.keySet()) {
      Class<CoreAnnotation<Object>> key = (Class<CoreAnnotation<Object>>) rawKey;
      String className = key.getSimpleName();
      Object value = cm.get(key);
      sb.append(className).append(": ").append(value).append("\n");
    }
  }

}
