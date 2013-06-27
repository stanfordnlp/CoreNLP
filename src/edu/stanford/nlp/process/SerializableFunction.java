package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;

import java.io.Serializable;

/**
 * This interface is a conjunction of Function and Serializable, which is
 * a bad idea from the perspective of the type system, but one that seems
 * more palatable than other bad ideas until java's type system is flexible
 * enough to support type conjunctions.
 *
 * @author dramage
 */
public interface SerializableFunction<T1,T2> extends Function<T1,T2>, Serializable {

}
