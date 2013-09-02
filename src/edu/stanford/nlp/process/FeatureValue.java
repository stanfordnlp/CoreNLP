package edu.stanford.nlp.process;


import java.io.Serializable;

/**
 * This defines an interface for the set of possible values that a
 * Feature can assume. Each FeatureValue instance is a value of an
 * equivalence class over possible unseen words. When implementing
 * this interface you must also implement the Feature interface. The
 * Feature class is responsible for mapping unseen words to
 * FeatureValue instances.  This is at present just a marker class,
 * as a FeatureValue needs only standard object methods (including in
 * particular a "value" hashCode() and equals() implementation).
 *
 * @author Teg Grenager grenager@cs.stanford.edu
 */
public interface FeatureValue extends Serializable {

}
