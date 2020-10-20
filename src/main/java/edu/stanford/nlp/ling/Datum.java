package edu.stanford.nlp.ling;

import java.io.Serializable;


/**
 * Interface for Objects which can be described by their features.
 * An Object is described by a Datum as a List of categorical features.
 * (For features which have numeric values, see {@link RVFDatum}.
 * These objects can also be Serialized (for insertion into a file database).
 *
 * @author Sepandar Kamvar (sdkamvar@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <L> The type of the labels in the Datum
 * @param <F> The type of the features in the Datum
 */
public interface Datum<L, F> extends Serializable, Featurizable<F>, Labeled<L> {
}




