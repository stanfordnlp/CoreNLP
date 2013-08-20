package edu.stanford.nlp.ling;

import java.util.List;

/**
 * Interface for data collections. A DataCollection is basically a List
 * of Datum objects with meta-data and convenience methods.
 *
 * @author Sepandar Kamvar
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 */
public interface DataCollection<L, F> extends List<Datum<L, F>> {
  /** Returns the feature matrix. */
  //public Matrix dataMatrix();

  /**
   * Returns a list of the primary labels for each Datum.
   */
  public List<L> labels();

  /**
   * Returns a Collection of all the features.
   */
  public List<F> features();

  /**
   * Returns a String that gives the name of the data collection stored
   * in this DataCollection.
   */
  public String name();

  /**
   * Returns a String representation of the DataCollection.
   */
  public String toString();

  /**
   * Inserts a Datum into the DataCollection.
   * This assigns Datum to lowest unassigned index in FileDataCollection
   * and returns this index.
   * Note: this allows for duplicate objects to be stored with different
   * indices.
   */
  public boolean add(Datum<L, F> d);

  /**
   * Returns the ith Datum in this DataCollection.
   */
  public Datum<L, F> getDatum(int i);

  /**
   * Returns a new empty DataCollection with the same meta-data (name, etc)
   * as this DataCollection. Subclasses that store extra state should provide custom
   * implementations of this method. This method is primarily used when splitting
   * a DataCollection into pieces so each piece will preserve the meta-data in
   * the original DataCollection.
   */
  public DataCollection<L, F> blankDataCollection();

}
