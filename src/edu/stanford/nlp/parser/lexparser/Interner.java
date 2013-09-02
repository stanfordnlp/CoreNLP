package edu.stanford.nlp.parser.lexparser;

import java.util.Map;
import edu.stanford.nlp.util.Generics;

/** (Someday this should be removed, but at present lexparser needs it)
 *  @author Dan Klein
 */
class Interner<E> {
  private Map<E, E> oToO = Generics.newHashMap();

  public E intern(E o) {
    E i = oToO.get(o);
    if (i == null) {
      i = o;
      oToO.put(o, o);
    }
    return i;
  }
}


