package edu.stanford.nlp.stats;

import edu.stanford.nlp.util.DeltaMap;
import edu.stanford.nlp.util.MutableDouble;

/**
 * A Counter that is maintained as a delta off another Counter.
 * This will be efficient if you are maintaining minor modifications to
 * Counters.
 *
 * @author Teg Grenager
 * @version Jan 26, 2004 8:39:49 PM
 */
public class DeltaCounter<E> extends ClassicCounter<E> {

  private static final long serialVersionUID = 2417145091311373088L;

  /**
   * puts new MutableDoubles.
   */
  @Override
  public void setCount(E key, double count) {
    MutableDouble newMD = new MutableDouble(count);
    map.put(key, newMD);
  }

  /**
   * puts new MutableDoubles.
   */
  @Override
  public double incrementCount(E key, double count) {
    MutableDouble oldMD = map.get(key);
    if (oldMD != null) {
      count += oldMD.doubleValue();
    }
    MutableDouble newMD = new MutableDouble(count);
    map.put(key, newMD);
    return count;
  }


  public DeltaCounter(ClassicCounter<E> c) {
    super(c.getMapFactory()); // Sets this.mapFactory
    map = new DeltaMap<E,MutableDouble>(c.map, getMapFactory());
  }

}
