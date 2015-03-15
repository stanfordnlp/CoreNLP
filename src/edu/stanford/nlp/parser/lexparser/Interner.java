package edu.stanford.nlp.parser.lexparser;

import java.util.Map;
import java.util.HashMap;

/** (Someday this should be removed, but at present lexparser needs it)
 *  @author Dan Klein
 */
class Interner<E> {
  private Map<E, E> oToO = new HashMap<E, E>();

  public E intern(E o) {
    E i = oToO.get(o);
    if (i == null) {
      i = o;
      oToO.put(o, o);
    }
    return i;
  }
}


