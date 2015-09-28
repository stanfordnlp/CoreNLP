package edu.stanford.nlp.loglinear.inference;

import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.GraphicalModel;
import edu.stanford.nlp.loglinear.model.NDArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiFunction;

/**
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
 *
 * @author keenon
 */
public class TableFactor extends NDArray<Double> {
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
   * Convenience function to sum out all but one variable, and return the marginal array.
   *
   * @param variable the variable whose marginal we're interested in
   * @return an array of doubles one-to-one with variable states
   */
  public double[] getSummedMarginal(int variable) {
    // Safety check
    boolean containsVariable = false;
    for (int i : neighborIndices) {
      if (i == variable) {
        if (containsVariable) {
          System.out.println("Duplicate!" + Arrays.toString(neighborIndices));
        }
        assert (!containsVariable);
        containsVariable = true;
      }
    }
    assert (containsVariable);

    TableFactor result = this;
    for (int i : neighborIndices) {
      if (i != variable) result = result.sumOut(i);
    }
    assert (result.neighborIndices.length == 1);
    assert (result.neighborIndices[0] == variable);
    double[] marginal = new double[getVariableSize(variable)];

    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we trivially iterate ourselves:

    int[] assn = new int[]{0};
    for (; assn[0] < marginal.length; assn[0]++) {
      marginal[assn[0]] = result.getAssignmentLogValue(assn);
    }

    normalizeLogArr(marginal);

    return marginal;
  }

  /**
   * Convenience function to max out all but one variable, and return the marginal array.
   *
   * @param variable the variable whose marginal we're interested in
   * @return an array of doubles one-to-one with variable states
   */
  public double[] getMaxedMarginal(int variable) {
    TableFactor result = this;
    for (int i : neighborIndices) {
      if (i != variable) result = result.maxOut(i);
    }
    double[] marginal = new double[getVariableSize(variable)];

    // OPTIMIZATION:
    // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
    // we trivially iterate ourselves:

    int[] assn = new int[]{0};
    for (; assn[0] < marginal.length; assn[0]++) {
      marginal[assn[0]] = result.getAssignmentLogValue(assn);
    }

    normalizeLogArr(marginal);

    return marginal;
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
    // This is a little tricky because we need to use the stable log-sum-exp trick on top of our marginalize
    // dataflow operation.

    // First we calculate all the max values to use as pivots to prevent overflow
    TableFactor maxValues = maxOut(variable);

    // Then we do the sum against an offset from the pivots
    TableFactor marginalized = marginalize(variable, 0, (marginalizedVariableValue, assignment) -> (a, b) -> a + Math.exp(b - maxValues.getAssignmentLogValue(assignment)));

    // Then we factor the max values back in, and
    for (int[] assignment : marginalized) {
      marginalized.setAssignmentLogValue(assignment, maxValues.getAssignmentLogValue(assignment) + Math.log(marginalized.getAssignmentLogValue(assignment)));
    }

    return marginalized;
  }

  /**
   * Product two factors, taking the multiplication at the intersections.
   *
   * @param other the other factor to be multiplied
   * @return a factor containing the union of both variable sets
   */
  public TableFactor multiply(TableFactor other) {
    return product(other, (a, b) -> a + b);
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
  public Double getAssignmentValue(int[] assignment) {
    Double d = super.getAssignmentValue(assignment);
    if (d == null) d = Double.NEGATIVE_INFINITY;
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
  public void setAssignmentValue(int[] assignment, Double value) {
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
   * Performs a factor-product using a configurable join operation. This way we can use the same operation for sum
   * and product factor-products, since we're really just debugging a data-flow system.
   *
   * @param other the TableFactor to take the product with. The elements of 'other' are always the second argument
   *              to the join function.
   * @param join  the operation to use when combining elements in an outer product.
   * @return the TableFactor that is the outer product of this and 'other'
   */
  private TableFactor product(TableFactor other, BiFunction<Double, Double, Double> join) {

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
      result.setAssignmentLogValue(resultAssignment, join.apply(getAssignmentLogValue(assignment), other.getAssignmentLogValue(otherAssignment)));
      // This mutates the resultAssignment[] array, rather than creating a new one
      if (fastPassByReferenceIterator.hasNext()) fastPassByReferenceIterator.next();
      else break;
    }

    return result;
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
  private void normalizeLogArr(double[] arr) {
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
  }
}
