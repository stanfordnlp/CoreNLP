package edu.stanford.nlp.loglinear.inference;

import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import edu.stanford.nlp.loglinear.model.NDArrayDoubles;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Created on 8/11/15.
 * @author keenon
 * <p>
 * Holds a factor populated by doubles that knows how to do all the important operations for PGM inference. Internally,
 * these are just different flavors of two basic data-flow operations:
 * <p>
 * - Factor product
 * - Factor marginalization
 * <p>
 * The output here is different ways to grow and shrink factors that turn out to be useful for downstream uses in PGMs.
 * Basically, we care about message passing, as that will be the primary operation.
 * <p>
 * Everything is represented as log-linear, because the primary use for TableFactor is in CliqueTree, and that is
 * intended for use with log-linear models.
 */
public class TableFactor extends NDArrayDoubles {
  public int[] neighborIndices;

  /**
   * Construct a TableFactor for inference within a model. This just copies the important bits from the model factor,
   * and replaces the ConcatVectorTable with an internal datastructure that has done all the dotproducts with the
   * weights out, and so stores only doubles.
   * <p>
   * Each element of the table is given by: t_i = exp(f_i*w)
   *
   * @param weights the vector to dot product with every element of the factor table
   * @param factor  the feature factor to be multiplied in
   */
  public TableFactor(ConcatVector weights, GraphicalModel.Factor factor) {
    super(factor.featuresTable.getDimensions());
    this.neighborIndices = factor.neigborIndices;

    // Calculate the factor residents by dot product with the weights

    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.
    Iterator<int[]> fastPassByReferenceIterator = factor.featuresTable.fastPassByReferenceIterator();
    int[] assignment = fastPassByReferenceIterator.next();
    while (true) {
      setAssignmentLogValue(assignment, factor.featuresTable.getAssignmentValue(assignment).get().dotProduct(weights));
      // This mutates the assignment[] array, rather than creating a new one
      if (fastPassByReferenceIterator.hasNext()) fastPassByReferenceIterator.next();
      else break;
    }
  }

  /**
   * Fast approximation of the exp() function.
   * This approximation was suggested in the paper
   * A Fast, Compact Approximation of the Exponential Function
   * http://nic.schraudolph.org/pubs/Schraudolph99.pdf
   * by Nicol N. Schraudolph. However, it does not seem accurate
   * enough to be a good default for CRFs.
   *
   * @param val The value to be exponentiated
   * @return The exponentiated value
   */
  public static double exp(double val) {
    final long tmp = (long) (1512775 * val + 1072632447);
    return Double.longBitsToDouble(tmp << 32);
  }

  public static final boolean USE_EXP_APPROX = false;

  /**
   * Construct a TableFactor for inference within a model. This is the same as the other constructor, except that the
   * table is observed out before any unnecessary dot products are done out, so hopefully we dramatically reduce the
   * number of computations required to calculate the resulting table.
   * <p>
   * Each element of the table is given by: t_i = exp(f_i*w)
   *
   * @param weights the vector to dot product with every element of the factor table
   * @param factor  the feature factor to be multiplied in
   */
  public TableFactor(ConcatVector weights, GraphicalModel.Factor factor, int[] observations) {
    super();
    assert (observations.length == factor.neigborIndices.length);

    int size = 0;
    for (int observation : observations) if (observation == -1) size++;

    neighborIndices = new int[size];
    dimensions = new int[size];
    int[] forwardPointers = new int[size];
    int[] factorAssignment = new int[factor.neigborIndices.length];

    int cursor = 0;
    for (int i = 0; i < factor.neigborIndices.length; i++) {
      if (observations[i] == -1) {
        neighborIndices[cursor] = factor.neigborIndices[i];
        dimensions[cursor] = factor.featuresTable.getDimensions()[i];
        forwardPointers[cursor] = i;
        cursor++;
      } else factorAssignment[i] = observations[i];
    }
    assert (cursor == size);

    values = new double[combinatorialNeighborStatesCount()];

    for (int[] assn : this) {
      for (int i = 0; i < assn.length; i++) {
        factorAssignment[forwardPointers[i]] = assn[i];
      }
      setAssignmentLogValue(assn, factor.featuresTable.getAssignmentValue(factorAssignment).get().dotProduct(weights));
    }
  }

  /**
   * Remove a variable by observing it at a certain value, return a new factor without that variable.
   *
   * @param variable the variable to be observed
   * @param value    the value the variable takes when observed
   * @return a new factor with 'variable' in it
   */
  public TableFactor observe(int variable, final int value) {
    return marginalize(variable, 0, (marginalizedVariableValue, assignment) -> {
      if (marginalizedVariableValue == value) {
        return (old, n) -> {
          // This would mean that we're observing something with 0 probability, which will wonk up downstream
          // stuff
          // assert(n != 0);
          return n;
        };
      } else {
        return (old, n) -> old;
      }
    });
  }

  /**
   * Returns the summed marginals for each element in the factor. These are represented in log space, and are summed
   * using the numerically stable variant, even though it's slightly slower.
   *
   * @return an array of doubles one-to-one with variable states for each variable
   */
  public double[][] getSummedMarginals() {
    double[][] results = new double[neighborIndices.length][];
    for (int i = 0; i < neighborIndices.length; i++) {
      results[i] = new double[getDimensions()[i]];
    }

    double[][] maxValues = new double[neighborIndices.length][];
    for (int i = 0; i < neighborIndices.length; i++) {
      maxValues[i] = new double[getDimensions()[i]];
      for (int j = 0; j < maxValues[i].length; j++) maxValues[i][j] = Double.NEGATIVE_INFINITY;
    }

    // Get max values

    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.

    Iterator<int[]> fastPassByReferenceIterator = fastPassByReferenceIterator();
    int[] assignment = fastPassByReferenceIterator.next();
    while (true) {
      double v = getAssignmentLogValue(assignment);
      for (int i = 0; i < neighborIndices.length; i++) {
        if (maxValues[i][assignment[i]] < v) maxValues[i][assignment[i]] = v;
      }
      // This mutates the resultAssignment[] array, rather than creating a new one
      if (fastPassByReferenceIterator.hasNext()) {
        fastPassByReferenceIterator.next();
      } else break;
    }

    // Do the summation

    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.

    Iterator<int[]> secondFastPassByReferenceIterator = fastPassByReferenceIterator();
    assignment = secondFastPassByReferenceIterator.next();
    while (true) {
      double v = getAssignmentLogValue(assignment);
      for (int i = 0; i < neighborIndices.length; i++) {
        if (USE_EXP_APPROX) {
          results[i][assignment[i]] += exp(v - maxValues[i][assignment[i]]);
        } else {
          results[i][assignment[i]] += Math.exp(v - maxValues[i][assignment[i]]);
        }
      }
      // This mutates the resultAssignment[] array, rather than creating a new one
      if (secondFastPassByReferenceIterator.hasNext()) {
        secondFastPassByReferenceIterator.next();
      } else break;
    }

    // normalize results, and move to linear space

    for (int i = 0; i < neighborIndices.length; i++) {
      double sum = 0.0;
      for (int j = 0; j < results[i].length; j++) {
        if (USE_EXP_APPROX) {
          results[i][j] = exp(maxValues[i][j]) * results[i][j];
        } else {
          results[i][j] = Math.exp(maxValues[i][j]) * results[i][j];
        }
        sum += results[i][j];
      }
      if (Double.isInfinite(sum)) {
        for (int j = 0; j < results[i].length; j++) {
          results[i][j] = 1.0 / results[i].length;
        }
      } else {
        for (int j = 0; j < results[i].length; j++) {
          results[i][j] /= sum;
        }
      }
    }

    return results;
  }

  /**
   * Convenience function to max out all but one variable, and return the marginal array.
   *
   * @return an array of doubles one-to-one with variable states for each variable
   */
  public double[][] getMaxedMarginals() {
    double[][] maxValues = new double[neighborIndices.length][];
    for (int i = 0; i < neighborIndices.length; i++) {
      maxValues[i] = new double[getDimensions()[i]];
      for (int j = 0; j < maxValues[i].length; j++) maxValues[i][j] = Double.NEGATIVE_INFINITY;
    }

    // Get max values

    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.

    Iterator<int[]> fastPassByReferenceIterator = fastPassByReferenceIterator();
    int[] assignment = fastPassByReferenceIterator.next();
    while (true) {
      double v = getAssignmentLogValue(assignment);
      for (int i = 0; i < neighborIndices.length; i++) {
        if (maxValues[i][assignment[i]] < v) maxValues[i][assignment[i]] = v;
      }
      // This mutates the resultAssignment[] array, rather than creating a new one
      if (fastPassByReferenceIterator.hasNext()) {
        fastPassByReferenceIterator.next();
      } else break;
    }

    for (int i = 0; i < neighborIndices.length; i++) {
      normalizeLogArr(maxValues[i]);
    }

    return maxValues;
  }

  /**
   * Marginalize out a variable by taking the max value.
   *
   * @param variable the variable to be maxed out.
   * @return a table factor that will contain the largest value of the variable being marginalized out.
   */
  public TableFactor maxOut(int variable) {
    return marginalize(variable, Double.NEGATIVE_INFINITY, (marginalizedVariableValue, assignment) -> Math::max);
  }

  /**
   * Marginalize out a variable by taking a sum.
   *
   * @param variable the variable to be summed out
   * @return a factor with variable removed
   */
  public TableFactor sumOut(int variable) {

    // OPTIMIZATION: This is by far the most common case, for linear chain inference, and is worth making fast
    // We can use closed loops, and not bother with using the basic iterator to loop through indices.
    // If this special case doesn't trip, we fall back to the standard (but slower) algorithm for the general case

    if (getDimensions().length == 2) {
      if (neighborIndices[0] == variable) {
        TableFactor marginalized = new TableFactor(new int[]{neighborIndices[1]}, new int[]{getDimensions()[1]});

        for (int i = 0; i < marginalized.values.length; i++) marginalized.values[i] = 0;

        // We use the stable log-sum-exp trick here, so first we calculate the max

        double[] max = new double[getDimensions()[1]];
        for (int j = 0; j < getDimensions()[1]; j++) {
          max[j] = Double.NEGATIVE_INFINITY;
        }

        for (int i = 0; i < getDimensions()[0]; i++) {
          int k = i * getDimensions()[1];
          for (int j = 0; j < getDimensions()[1]; j++) {
            int index = k + j;
            if (values[index] > max[j]) {
              max[j] = values[index];
            }
          }
        }

        // Then we take the sum, minus the max

        for (int i = 0; i < getDimensions()[0]; i++) {
          int k = i * getDimensions()[1];
          for (int j = 0; j < getDimensions()[1]; j++) {
            int index = k + j;
            if (Double.isFinite(max[j])) {
              if (USE_EXP_APPROX) {
                marginalized.values[j] += exp(values[index] - max[j]);
              } else {
                marginalized.values[j] += Math.exp(values[index] - max[j]);
              }
            }
          }
        }

        // And now we exponentiate, and add back in the values

        for (int j = 0; j < getDimensions()[1]; j++) {
          if (Double.isFinite(max[j])) {
            marginalized.values[j] = max[j] + Math.log(marginalized.values[j]);
          } else {
            marginalized.values[j] = max[j];
          }
        }

        return marginalized;
      } else {
        assert (neighborIndices[1] == variable);
        TableFactor marginalized = new TableFactor(new int[]{neighborIndices[0]}, new int[]{getDimensions()[0]});

        for (int i = 0; i < marginalized.values.length; i++) marginalized.values[i] = 0;

        // We use the stable log-sum-exp trick here, so first we calculate the max

        double[] max = new double[getDimensions()[0]];
        for (int i = 0; i < getDimensions()[0]; i++) {
          max[i] = Double.NEGATIVE_INFINITY;
        }

        for (int i = 0; i < getDimensions()[0]; i++) {
          int k = i * getDimensions()[1];
          for (int j = 0; j < getDimensions()[1]; j++) {
            int index = k + j;
            if (values[index] > max[i]) {
              max[i] = values[index];
            }
          }
        }

        // Then we take the sum, minus the max

        for (int i = 0; i < getDimensions()[0]; i++) {
          int k = i * getDimensions()[1];
          for (int j = 0; j < getDimensions()[1]; j++) {
            int index = k + j;
            if (Double.isFinite(max[i])) {
              if (USE_EXP_APPROX) {
                marginalized.values[i] += exp(values[index] - max[i]);
              } else {
                marginalized.values[i] += Math.exp(values[index] - max[i]);
              }
            }
          }
        }

        // And now we exponentiate, and add back in the values

        for (int i = 0; i < getDimensions()[0]; i++) {
          if (Double.isFinite(max[i])) {
            marginalized.values[i] = max[i] + Math.log(marginalized.values[i]);
          } else {
            marginalized.values[i] = max[i];
          }
        }

        return marginalized;
      }
    } else {
      // This is a little tricky because we need to use the stable log-sum-exp trick on top of our marginalize
      // dataflow operation.

      // First we calculate all the max values to use as pivots to prevent overflow
      TableFactor maxValues = maxOut(variable);

      // Then we do the sum against an offset from the pivots
      TableFactor marginalized = marginalize(variable, 0, (marginalizedVariableValue, assignment) -> (a, b) -> a + (USE_EXP_APPROX ? exp(b - maxValues.getAssignmentLogValue(assignment)) : Math.exp(b - maxValues.getAssignmentLogValue(assignment))));

      // Then we factor the max values back in, and
      for (int[] assignment : marginalized) {
        marginalized.setAssignmentLogValue(assignment, maxValues.getAssignmentLogValue(assignment) + Math.log(marginalized.getAssignmentLogValue(assignment)));
      }

      return marginalized;
    }
  }

  /**
   * Product two factors, taking the multiplication at the intersections.
   *
   * @param other the other factor to be multiplied
   * @return a factor containing the union of both variable sets
   */
  public TableFactor multiply(TableFactor other) {

    // Calculate the result domain

    List<Integer> domain = new ArrayList<>();
    List<Integer> otherDomain = new ArrayList<>();
    List<Integer> resultDomain = new ArrayList<>();

    for (int n : neighborIndices) {
      domain.add(n);
      resultDomain.add(n);
    }
    for (int n : other.neighborIndices) {
      otherDomain.add(n);
      if (!resultDomain.contains(n)) resultDomain.add(n);
    }

    // Create result TableFactor

    int[] resultNeighborIndices = new int[resultDomain.size()];
    int[] resultDimensions = new int[resultNeighborIndices.length];
    for (int i = 0; i < resultDomain.size(); i++) {
      int var = resultDomain.get(i);
      resultNeighborIndices[i] = var;
      // assert consistency about variable size, we can't have the same variable with two different sizes
      assert ((getVariableSize(var) == 0 && other.getVariableSize(var) > 0) ||
          (getVariableSize(var) > 0 && other.getVariableSize(var) == 0) ||
          (getVariableSize(var) == other.getVariableSize(var)));
      resultDimensions[i] = Math.max(getVariableSize(resultDomain.get(i)), other.getVariableSize(resultDomain.get(i)));
    }
    TableFactor result = new TableFactor(resultNeighborIndices, resultDimensions);

    // OPTIMIZATION:
    // If we're a factor of size 2 receiving a message of size 1, then we can optimize that pretty heavily
    // We could just use the general algorithm at the end of this set of special cases, but this is the fastest way
    if (otherDomain.size() == 1 && (resultDomain.size() == domain.size()) && domain.size() == 2) {
      int msgVar = otherDomain.get(0);
      int msgIndex = resultDomain.indexOf(msgVar);

      if (msgIndex == 0) {
        for (int i = 0; i < resultDimensions[0]; i++) {
          double d = other.values[i];
          int k = i * resultDimensions[1];
          for (int j = 0; j < resultDimensions[1]; j++) {
            int index = k + j;
            result.values[index] = values[index] + d;
          }
        }
      } else if (msgIndex == 1) {
        for (int i = 0; i < resultDimensions[0]; i++) {
          int k = i * resultDimensions[1];
          for (int j = 0; j < resultDimensions[1]; j++) {
            int index = k + j;
            result.values[index] = values[index] + other.values[j];
          }
        }
      }
    }
    // OPTIMIZATION:
    // The special case where we're a message of size 1, and the other factor is receiving the message, and of size 2
    else if (domain.size() == 1 && (resultDomain.size() == otherDomain.size()) && resultDomain.size() == 2) {
      return other.multiply(this);
    }
    // Otherwise we follow the big comprehensive, slow general purpose algorithm
    else {

      // Calculate back-pointers from the result domain indices to original indices

      int[] mapping = new int[result.neighborIndices.length];
      int[] otherMapping = new int[result.neighborIndices.length];
      for (int i = 0; i < result.neighborIndices.length; i++) {
        mapping[i] = domain.indexOf(result.neighborIndices[i]);
        otherMapping[i] = otherDomain.indexOf(result.neighborIndices[i]);
      }

      // Do the actual joining operation between the two tables, applying 'join' for each result element.

      int[] assignment = new int[neighborIndices.length];
      int[] otherAssignment = new int[other.neighborIndices.length];

      // OPTIMIZATION:
      // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
      // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.
      Iterator<int[]> fastPassByReferenceIterator = result.fastPassByReferenceIterator();
      int[] resultAssignment = fastPassByReferenceIterator.next();
      while (true) {
        // Set the assignment arrays correctly
        for (int i = 0; i < resultAssignment.length; i++) {
          if (mapping[i] != -1) assignment[mapping[i]] = resultAssignment[i];
          if (otherMapping[i] != -1) otherAssignment[otherMapping[i]] = resultAssignment[i];
        }
        result.setAssignmentLogValue(resultAssignment, getAssignmentLogValue(assignment) + other.getAssignmentLogValue(otherAssignment));
        // This mutates the resultAssignment[] array, rather than creating a new one
        if (fastPassByReferenceIterator.hasNext()) fastPassByReferenceIterator.next();
        else break;
      }
    }

    return result;
  }

  /**
   * This is useful for calculating the partition function, and is exposed here because when implemented internally
   * we can do a much more numerically stable summation.
   *
   * @return the sum of all values for all assignments to the TableFactor
   */
  public double valueSum() {

    // We want the exp(log-sum-exp), for stability
    // This rearranges to exp(a)*(sum-exp)

    double max = 0.0;
    for (int[] assignment : this) {
      double v = getAssignmentLogValue(assignment);
      if (v > max) {
        max = v;
      }
    }

    double sumExp = 0.0;
    for (int[] assignment : this) {
      sumExp += Math.exp(getAssignmentLogValue(assignment) - max);
    }

    return sumExp * Math.exp(max);
  }

  /**
   * Just a pass through to the NDArray version, plus a Math.exp to ensure that to the outside world the TableFactor
   * doesn't look like it's in log-space
   *
   * @param assignment a list of variable settings, in the same order as the neighbors array of the factor
   * @return the value of the assignment
   */
  @Override
  public double getAssignmentValue(int[] assignment) {
    double d = super.getAssignmentValue(assignment);
    // if (d == null) d = Double.NEGATIVE_INFINITY;
    return Math.exp(d);
  }

  /**
   * Just a pass through to the NDArray version, plus a Math.log to ensure that to the outside world the TableFactor
   * doesn't look like it's in log-space
   *
   * @param assignment a list of variable settings, in the same order as the neighbors array of the factor
   * @param value      the value to put into the factor table
   */
  @Override
  public void setAssignmentValue(int[] assignment, double value) {
    super.setAssignmentValue(assignment, Math.log(value));
  }

  ////////////////////////////////////////////////////////////////////////////
  // PRIVATE IMPLEMENTATION
  ////////////////////////////////////////////////////////////////////////////

  private double getAssignmentLogValue(int[] assignment) {
    return super.getAssignmentValue(assignment);
  }

  private void setAssignmentLogValue(int[] assignment, double value) {
    super.setAssignmentValue(assignment, value);
  }

  /**
   * Marginalizes out a variable by applying an associative join operation for each possible assignment to the
   * marginalized variable.
   *
   * @param variable      the variable (by 'name', not offset into neighborIndices)
   * @param startingValue associativeJoin is basically a foldr over a table, and this is the initialization
   * @param curriedFoldr  the associative function to use when applying the join operation, taking first the
   *                      assignment to the value being marginalized, and then a foldr operation
   * @return a new TableFactor that doesn't contain 'variable', where values were gotten through associative
   * marginalization.
   */
  private TableFactor marginalize(int variable, double startingValue, BiFunction<Integer, int[], BiFunction<Double, Double, Double>> curriedFoldr) {
    // Can't marginalize the last variable
    assert (getDimensions().length > 1);

    // Calculate the result domain

    List<Integer> resultDomain = new ArrayList<>();
    for (int n : neighborIndices) {
      if (n != variable) {
        resultDomain.add(n);
      }
    }

    // Create result TableFactor

    int[] resultNeighborIndices = new int[resultDomain.size()];
    int[] resultDimensions = new int[resultNeighborIndices.length];
    for (int i = 0; i < resultDomain.size(); i++) {
      int var = resultDomain.get(i);
      resultNeighborIndices[i] = var;
      resultDimensions[i] = getVariableSize(var);
    }
    TableFactor result = new TableFactor(resultNeighborIndices, resultDimensions);

    // Calculate forward-pointers from the old domain to new domain

    int[] mapping = new int[neighborIndices.length];
    for (int i = 0; i < neighborIndices.length; i++) {
      mapping[i] = resultDomain.indexOf(neighborIndices[i]);
    }

    // Initialize

    for (int[] assignment : result) {
      result.setAssignmentLogValue(assignment, startingValue);
    }

    // Do the actual fold into the result

    int[] resultAssignment = new int[result.neighborIndices.length];
    int marginalizedVariableValue = 0;

    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.
    Iterator<int[]> fastPassByReferenceIterator = fastPassByReferenceIterator();
    int[] assignment = fastPassByReferenceIterator.next();
    while (true) {
      // Set the assignment arrays correctly
      for (int i = 0; i < assignment.length; i++) {
        if (mapping[i] != -1) resultAssignment[mapping[i]] = assignment[i];
        else marginalizedVariableValue = assignment[i];
      }
      result.setAssignmentLogValue(resultAssignment, curriedFoldr.apply(marginalizedVariableValue, resultAssignment)
          .apply(result.getAssignmentLogValue(resultAssignment), getAssignmentLogValue(assignment)));
      if (fastPassByReferenceIterator.hasNext()) fastPassByReferenceIterator.next();
      else break;
    }

    return result;
  }

  /**
   * Address a variable by index to get it's size. Basically just a convenience function.
   *
   * @param variable the name, not index into neighbors, of the variable in question
   * @return the size of the factor along this dimension
   */
  private int getVariableSize(int variable) {
    for (int i = 0; i < neighborIndices.length; i++) {
      if (neighborIndices[i] == variable) return getDimensions()[i];
    }
    return 0;
  }

  /**
   * Super basic in-place array normalization
   *
   * @param arr the array to normalize
   */
  private static void normalizeLogArr(double[] arr) {
    // Find the log-scale normalization value
    double max = Double.NEGATIVE_INFINITY;
    for (double d : arr) {
      if (d > max) max = d;
    }
    double expSum = 0.0;
    for (double d : arr) {
      expSum += Math.exp(d - max);
    }
    double logSumExp = max + Math.log(expSum);

    if (Double.isInfinite(logSumExp)) {
      // Just put in uniform probabilities if we are normalizing all 0s
      for (int i = 0; i < arr.length; i++) {
        arr[i] = 1.0 / arr.length;
      }
    } else {
      // Normalize in log-scale before exponentiation, to help with stability
      for (int i = 0; i < arr.length; i++) {
        arr[i] = Math.exp(arr[i] - logSumExp);
      }
    }
  }

  /**
   * FOR PRIVATE USE AND TESTING ONLY
   */
  TableFactor(int[] neighborIndices, int[] dimensions) {
    super(dimensions);
    this.neighborIndices = neighborIndices;
    for (int i = 0; i < values.length; i++) {
      values[i] = Double.NEGATIVE_INFINITY;
    }
  }

  @SuppressWarnings("*")
  private boolean assertsEnabled() {
    boolean assertsEnabled = false;
    assert (assertsEnabled = true); // intentional side effect
    return assertsEnabled;
  }
}
