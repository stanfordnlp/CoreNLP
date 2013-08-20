package edu.stanford.nlp.util;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Class representing a partition of objects into equivalence classes, such that every object
 * has exactly one class. Supports getClass(Object o) and getMembers(Object class).
 * @author grenager
 *
 */
public class Partition<O,C> {
  
  // the core data structure
  Map<O,C> objectsToClasses = new HashMap<O,C>();
  
  // an ancillary data structure for fast lookup
  CollectionValuedMap<C,O> classesToObjects = new CollectionValuedMap<C,O>();
  
  public C setClass(O o, C c) {
    C old = objectsToClasses.put(o,c);
    if (old!=null) {
      classesToObjects.removeMapping(old, o);      
    }
    classesToObjects.add(c, o);
    return old;
  }
  
  public C remove(O o) {
    C old = objectsToClasses.remove(o);
    if (old!=null) {
      classesToObjects.removeMapping(old, o);      
    }
    return old;
  }
  
  public C getClass(O o) {
    return objectsToClasses.get(o);
  }
  
  public Collection<O> getMembers(C c) {
    return classesToObjects.get(c);
  }

  public int size() {
    return objectsToClasses.size();
  }
  
  public Collection<O> getOtherMembers(O o) {
    C c = objectsToClasses.get(o); // could be null!
    return classesToObjects.get(c);
  }
  
  public Partition() {
    
  }

}
