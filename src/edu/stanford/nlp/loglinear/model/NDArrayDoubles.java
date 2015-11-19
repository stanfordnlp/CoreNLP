package edu.stanford.nlp.loglinear.model;

import java.util.Iterator;

/**
 * Created on 9/12/15.
 * @author keenon
 * <p>
 * Holds and provides access to an N-dimensional array.
 * <p>
 * Yes, generics will lead to unfortunate boxing and unboxing in the TableFactor case, we'll handle that if it becomes a
 * problem.
 */
public class NDArrayDoubles implements Iterable<int[]> {
  // public data
  protected int[] dimensions;

  // OPTIMIZATION:
  // in normal NDArray this is private, but to allow for optimizations we actually leave it as protected
  protected double[] values;

  /**
   * Constructor takes a list of neighbor variables to use for this factor. This must not change after construction,
   * and the number of states of those variables must also not change.
   *
   * @param dimensions list of neighbor variables assignment range sizes
   */
  public NDArrayDoubles(int[] dimensions) {
    for (int size : dimensions) {
      assert (size > 0);
    }
    this.dimensions = dimensions;
    values = new double[combinatorialNeighborStatesCount()];
  }

  /**
   * This is to enable the partially observed constructor for TableFactor. It's an ugly break of modularity, but seems
   * to be necessary if we want to keep the constructor for TableFactor with partial observations relatively simple.
   */
  protected NDArrayDoubles() {
  }

  /**
   * Set a single value in the factor table.
   *
   * @param assignment a list of variable settings, in the same order as the neighbors array of the factor
   * @param value      the value to put into the factor table
   */
  public void setAssignmentValue(int[] assignment, double value) {
    values[getTableAccessOffset(assignment)] = value;
  }

  /**
   * Retrieve a single value for an assignment.
   *
   * @param assignment a list of variable settings, in the same order as the neighbors array of the factor
   * @return the value for the given assignment. Can be null if not been set yet.
   */
  public double getAssignmentValue(int[] assignment) {
    return values[getTableAccessOffset(assignment)];
  }

  /**
   * @return the size array of the neighbors of the feature factor, passed by value to ensure immutability.
   */
  public int[] getDimensions() {
    return dimensions.clone();
  }

  /**
   * WARNING: This is pass by reference to avoid massive GC overload during heavy iterations, and because the standard
   * use case is to use the assignments array as an accessor. Please, clone if you save a copy, otherwise the array
   * will mutate underneath you.
   *
   * @return an iterator over all possible assignments to this factor
   */
  @Override
  public Iterator<int[]> iterator() {
    return new Iterator<int[]>() {
      Iterator<int[]> unsafe = fastPassByReferenceIterator();

      @Override
      public boolean hasNext() {
        return unsafe.hasNext();
      }

      @Override
      public int[] next() {
        return unsafe.next().clone();
      }
    };
  }

  /**
   * This is its own function because people will inevitably attempt this optimization of not cloning the array we
   * hand to the iterator, to save on GC, and it should not be default behavior. If you know what you're doing, then
   * this may be the iterator for you.
   *
   * @return an iterator that will mutate the value it returns to you, so you must clone if you want to keep a copy
   */
  public Iterator<int[]> fastPassByReferenceIterator() {
    final int[] assignments = new int[dimensions.length];
    if (dimensions.length > 0) assignments[0] = -1;

    return new Iterator<int[]>() {
      @Override
      public boolean hasNext() {
        for (int i = 0; i < assignments.length; i++) {
          if (assignments[i] < dimensions[i] - 1) return true;
        }
        return false;
      }

      @Override
      public int[] next() {
        // Add one to the first position
        assignments[0]++;
        // Carry any resulting overflow all the way to the end.
        for (int i = 0; i < assignments.length; i++) {
          if (assignments[i] >= dimensions[i]) {
            assignments[i] = 0;
            if (i < assignments.length - 1) {
              assignments[i + 1]++;
            }
          } else {
            break;
          }
        }
        return assignments;
      }
    };
  }

  /**
   * @return the total number of states this factor must represent to include all neighbors.
   */
  public int combinatorialNeighborStatesCount() {
    int c = 1;
    for (int n : dimensions) {
      c *= n;
    }
    return c;
  }

  ////////////////////////////////////////////////////////////////////////////
  // PRIVATE IMPLEMENTATION
  ////////////////////////////////////////////////////////////////////////////

  /**
   * Compute the distance into the one dimensional factorTable array that corresponds to a setting of all the
   * neighbors of the factor.
   *
   * @param assignment assignment indices, in same order as neighbors array
   * @return the offset index
   */
  private int getTableAccessOffset(int[] assignment) {
    assert (assignment.length == dimensions.length);
    int offset = 0;
    for (int i = 0; i < assignment.length; i++) {
      assert (assignment[i] < dimensions[i]);
      offset = (offset * dimensions[i]) + assignment[i];
    }
    return offset;
  }
}
