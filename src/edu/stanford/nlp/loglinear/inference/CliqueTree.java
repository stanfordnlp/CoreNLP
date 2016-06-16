package edu.stanford.nlp.loglinear.inference;

import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.GraphicalModel;

import java.util.*;

/**
 * Created on 8/11/15.
 * @author keenon
 * <p>
 * This is instantiated once per model, so that it can keep caches of important stuff like messages and
 * local factors during many game playing sample steps. It assumes that the model that is passed in is by-reference,
 * and that it can change between inference calls in small ways, so that cacheing of some results is worthwhile.
 */
public class CliqueTree  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CliqueTree.class);
  private GraphicalModel model;
  private ConcatVector weights;

  // This is the metadata key for the model to store an observed value for a variable, as an int
  public static final String VARIABLE_OBSERVED_VALUE = "inference.CliqueTree.VARIABLE_OBSERVED_VALUE";

  private static final boolean CACHE_MESSAGES = true;

  /**
   * Create an Inference object for a given set of weights, and a model.
   * <p>
   * The object is around to facilitate cacheing as an eventual optimization, when models are changing in minor ways
   * and inference is required several times. Work is done lazily, so is left until actual inference is requested.
   *
   * @param model   the model to be computed over, subject to change in the future
   * @param weights the weights to dot product with model features to get log-linear factors, is cloned internally so
   *                that no changes to the weights vector will be reflected by the CliqueTree. If you want to change
   *                the weights, you must create a new CliqueTree.
   */
  public CliqueTree(GraphicalModel model, ConcatVector weights) {
    this.model = model;
    this.weights = weights.deepClone();
  }

  /**
   * Little data structure for passing around the results of marginal computations.
   */
  public static class MarginalResult {
    public double[][] marginals;
    public double partitionFunction;
    public Map<GraphicalModel.Factor, TableFactor> jointMarginals;

    public MarginalResult(double[][] marginals, double partitionFunction, Map<GraphicalModel.Factor, TableFactor> jointMarginals) {
      this.marginals = marginals;
      this.partitionFunction = partitionFunction;
      this.jointMarginals = jointMarginals;
    }
  }

  /**
   * This assumes that factors represent joint probabilities.
   *
   * @return global marginals
   */
  public MarginalResult calculateMarginals() {
    return messagePassing(MarginalizationMethod.SUM, true);
  }

  /**
   * This will calculate marginals, but skip the stuff that is created for gradient descent: joint marginals and
   * partition functions. This makes it much faster. It is thus appropriate for gameplayer style work, where many
   * samples need to be drawn with the same marginals.
   *
   * @return an array, indexed first by variable, then by variable assignment, of global probability
   */
  public double[][] calculateMarginalsJustSingletons() {
    MarginalResult result = messagePassing(MarginalizationMethod.SUM, false);
    return result.marginals;
  }

  /**
   * This assumes that factors represent joint probabilities.
   *
   * @return an array, indexed by variable, of maximum likelihood assignments
   */
  public int[] calculateMAP() {
    double[][] mapMarginals = messagePassing(MarginalizationMethod.MAX, false).marginals;
    int[] result = new int[mapMarginals.length];
    for (int i = 0; i < result.length; i++) {
      if (mapMarginals[i] != null) {
        for (int j = 0; j < mapMarginals[i].length; j++) {
          if (mapMarginals[i][j] > mapMarginals[i][result[i]]) {
            result[i] = j;
          }
        }
      }
      // If there is no factor touching an observed variable, the resulting MAP won't reference the variable
      // observation since message passing won't touch the variable index
      if (model.getVariableMetaDataByReference(i).containsKey(VARIABLE_OBSERVED_VALUE)) {
        result[i] = Integer.parseInt(model.getVariableMetaDataByReference(i).get(VARIABLE_OBSERVED_VALUE));
      }
    }
    return result;
  }

  ////////////////////////////////////////////////////////////////////////////
  // PRIVATE IMPLEMENTATION
  ////////////////////////////////////////////////////////////////////////////

  private enum MarginalizationMethod {
    SUM,
    MAX
  }

  // OPTIMIZATION:
  // cache the creation of TableFactors, to avoid redundant dot products

  private IdentityHashMap<GraphicalModel.Factor, CachedFactorWithObservations> cachedFactors = new IdentityHashMap<>();

  private static class CachedFactorWithObservations {
    TableFactor cachedFactor;
    int[] observations;
    boolean impossibleObservation;
  }

  // OPTIMIZATION:
  // cache the last list of factors, and the last set of messages passed, in case we can recycle some

  private TableFactor[] cachedCliqueList;
  private TableFactor[][] cachedMessages;
  private boolean[][] cachedBackwardPassedMessages;

  /**
   * Does tree shaped message passing. The algorithm calls for first passing down to the leaves, then passing back up
   * to the root.
   *
   * @param marginalize the method for marginalization, controls MAP or marginals
   * @return the marginal messages
   */
  private MarginalResult messagePassing(MarginalizationMethod marginalize, boolean includeJointMarginalsAndPartition) {

    // Using the behavior of brute force factor multiplication as ground truth, the desired
    // outcome of marginal calculation with an impossible factor is a uniform probability dist.,
    // since we have a resulting factor of all 0s. That is of course assuming that normalizing
    // all 0s gives you uniform, which is not real math, but that's a useful tolerance to include, so we do.

    boolean impossibleObservationMade = false;

    // Message passing will look at fully observed cliques as non-entities, but their
    // log-likelihood (the log-likelihood of the single observed value) is still relevant for the
    // partition function.

    double partitionFunction = 1.0;

    if (includeJointMarginalsAndPartition) {
      outer:
      for (GraphicalModel.Factor f : model.factors) {
        for (int n : f.neigborIndices) {
          if (!model.getVariableMetaDataByReference(n).containsKey(VARIABLE_OBSERVED_VALUE)) continue outer;
        }

        int[] assignment = new int[f.neigborIndices.length];
        for (int i = 0; i < f.neigborIndices.length; i++) {
          assignment[i] = Integer.parseInt(model.getVariableMetaDataByReference(f.neigborIndices[i]).get(VARIABLE_OBSERVED_VALUE));
        }

        double assignmentValue = f.featuresTable.getAssignmentValue(assignment).get().dotProduct(weights);
        if (Double.isInfinite(assignmentValue)) {
          impossibleObservationMade = true;
        } else {
          partitionFunction *= Math.exp(assignmentValue);
        }
      }
    }

    // Create the cliques by multiplying out table factors
    // TODO:OPT This could be made more efficient by observing first, then dot product

    List<TableFactor> cliquesList = new ArrayList<>();
    Map<Integer, GraphicalModel.Factor> cliqueToFactor = new HashMap<>();

    int numFactorsCached = 0;

    for (GraphicalModel.Factor f : model.factors) {
      boolean allObserved = true;
      int maxVar = 0;
      for (int n : f.neigborIndices) {
        if (!model.getVariableMetaDataByReference(n).containsKey(VARIABLE_OBSERVED_VALUE)) allObserved = false;
        if (n > maxVar) maxVar = n;
      }
      if (allObserved) continue;

      TableFactor clique = null;

      // Retrieve cache if exists and none of the observations have changed

      if (cachedFactors.containsKey(f)) {
        CachedFactorWithObservations obs = cachedFactors.get(f);
        boolean allConsistent = true;
        for (int i = 0; i < f.neigborIndices.length; i++) {
          int n = f.neigborIndices[i];
          if (model.getVariableMetaDataByReference(n).containsKey(VARIABLE_OBSERVED_VALUE) &&
              (obs.observations[i] == -1 ||
                  Integer.parseInt(model.getVariableMetaDataByReference(n).get(VARIABLE_OBSERVED_VALUE)) != obs.observations[i])) {
            allConsistent = false;
            break;
          }
          // NOTE: This disqualifies lots of stuff for some reason...
          if (!model.getVariableMetaDataByReference(n).containsKey(VARIABLE_OBSERVED_VALUE) && (obs.observations[i] != -1)) {
            allConsistent = false;
            break;
          }
        }
        if (allConsistent) {
          clique = obs.cachedFactor;
          numFactorsCached++;
          if (obs.impossibleObservation) {
            impossibleObservationMade = true;
          }
        }
      }

      // Otherwise make a new cache

      if (clique == null) {
        int[] observations = new int[f.neigborIndices.length];
        for (int i = 0; i < observations.length; i++) {
          Map<String, String> metadata = model.getVariableMetaDataByReference(f.neigborIndices[i]);
          if (metadata.containsKey(VARIABLE_OBSERVED_VALUE)) {
            int value = Integer.parseInt(metadata.get(VARIABLE_OBSERVED_VALUE));
            observations[i] = value;
          } else {
            observations[i] = -1;
          }
        }

        clique = new TableFactor(weights, f, observations);

        CachedFactorWithObservations cache = new CachedFactorWithObservations();
        cache.cachedFactor = clique;
        cache.observations = observations;

        // Check for an impossible observation
        boolean nonZeroValue = false;
        for (int[] assignment : clique) {
          if (clique.getAssignmentValue(assignment) > 0) {
            nonZeroValue = true;
            break;
          }
        }
        if (!nonZeroValue) {
          impossibleObservationMade = true;
          cache.impossibleObservation = true;
        }

        cachedFactors.put(f, cache);
      }

      cliqueToFactor.put(cliquesList.size(), f);
      cliquesList.add(clique);
    }

    TableFactor[] cliques = cliquesList.toArray(new TableFactor[cliquesList.size()]);

    // If we made any impossible observations, we can just return a uniform distribution for all the variables that
    // weren't observed, since that's the semantically correct thing to do (our 'probability' is broken at this
    // point).

    if (impossibleObservationMade) {
      int maxVar = 0;
      for (TableFactor c : cliques) {
        for (int i : c.neighborIndices) if (i > maxVar) maxVar = i;
      }

      double[][] result = new double[maxVar + 1][];

      for (TableFactor c : cliques) {
        for (int i = 0; i < c.neighborIndices.length; i++) {
          result[c.neighborIndices[i]] = new double[c.getDimensions()[i]];
          for (int j = 0; j < result[c.neighborIndices[i]].length; j++) {
            result[c.neighborIndices[i]][j] = 1.0 / result[c.neighborIndices[i]].length;
          }
        }
      }

      // Create a bunch of uniform joint marginals, constrained by observations, and fill up the joint marginals
      // with them

      Map<GraphicalModel.Factor, TableFactor> jointMarginals = new IdentityHashMap<>();
      if (includeJointMarginalsAndPartition) {
        for (GraphicalModel.Factor f : model.factors) {
          TableFactor uniformZero = new TableFactor(f.neigborIndices, f.featuresTable.getDimensions());

          for (int[] assignment : uniformZero) {
            uniformZero.setAssignmentValue(assignment, 0.0);
          }

          jointMarginals.put(f, uniformZero);
        }
      }

      return new MarginalResult(result, 1.0, jointMarginals);
    }

    // Find the largest contained variable, so that we can size arrays appropriately

    int maxVar = 0;
    for (GraphicalModel.Factor fac : model.factors) {
      for (int i : fac.neigborIndices) if (i > maxVar) maxVar = i;
    }


    // Indexed by (start-clique, end-clique), this array will remain mostly null in most graphs

    TableFactor[][] messages = new TableFactor[cliques.length][cliques.length];

    // OPTIMIZATION:
    // check if we've only added one factor since the last time we ran marginal inference. If that's the case, we
    // can use the new factor as the root, all the messages passed in from the leaves will not have changed. That
    // means we can cut message passing computation in half.

    boolean[][] backwardPassedMessages = new boolean[cliques.length][cliques.length];

    int forceRootForCachedMessagePassing = -1;
    int[] cachedCliquesBackPointers = null;

    if (CACHE_MESSAGES && (numFactorsCached == cliques.length - 1) && (numFactorsCached > 0)) {
      cachedCliquesBackPointers = new int[cliques.length];

      // Sometimes we'll have cached versions of the factors, but they're from inference steps a long time ago, so we
      // don't get consistent backpointers to our cache of factors. This is a flag to indicate if this happens.
      boolean backPointersConsistent = true;

      // Calculate the correspondence between the old cliques list and the new cliques list

      for (int i = 0; i < cliques.length; i++) {
        cachedCliquesBackPointers[i] = -1;
        for (int j = 0; j < cachedCliqueList.length; j++) {
          if (cliques[i] == cachedCliqueList[j]) {
            cachedCliquesBackPointers[i] = j;
            break;
          }
        }
        if (cachedCliquesBackPointers[i] == -1) {
          if (forceRootForCachedMessagePassing != -1) {
            backPointersConsistent = false;
            break;
          }
          forceRootForCachedMessagePassing = i;
        }
      }

      if (!backPointersConsistent) forceRootForCachedMessagePassing = -1;
    }

    // Create the data structures to hold the tree pattern

    boolean[] visited = new boolean[cliques.length];
    int numVisited = 0;
    int[] visitedOrder = new int[cliques.length];

    int[] parent = new int[cliques.length];
    for (int i = 0; i < parent.length; i++) parent[i] = -1;
    // Figure out which cliques are connected to which trees. This is important for calculating the partition
    // function later, since each tree will converge to its own partition function by multiplication, and we will
    // need to multiply the partition function of each of the trees to get the global one.
    int[] trees = new int[cliques.length];

    // Forward pass, record a BFS forest pattern that we can use for message passing

    int treeIndex = -1;
    boolean[] seenVariable = new boolean[maxVar + 1];
    while (numVisited < cliques.length) {
      treeIndex++;

      // Pick the largest connected graph remaining as the root for message passing

      int root = -1;

      // OPTIMIZATION: if there's a forced root for message passing (a node that we just added) then make it the
      // root

      if (CACHE_MESSAGES && forceRootForCachedMessagePassing != -1 && !visited[forceRootForCachedMessagePassing]) {
        root = forceRootForCachedMessagePassing;
      } else {
        for (int i = 0; i < cliques.length; i++) {
          if (!visited[i] &&
              (root == -1 || cliques[i].neighborIndices.length > cliques[root].neighborIndices.length)) {
            root = i;
          }
        }
      }
      assert (root != -1);

      Queue<Integer> toVisit = new ArrayDeque<>();
      toVisit.add(root);
      boolean[] toVisitArray = new boolean[cliques.length];
      toVisitArray[root] = true;

      while (toVisit.size() > 0) {
        int cursor = toVisit.poll();
        // toVisitArray[cursor] = false;
        trees[cursor] = treeIndex;
        if (visited[cursor]) {
          log.info("Visited contains: " + cursor);
          log.info("Visited: " + Arrays.toString(visited));
          log.info("To visit: " + toVisit);
        }
        assert (!visited[cursor]);
        visited[cursor] = true;
        visitedOrder[numVisited] = cursor;
        for (int i : cliques[cursor].neighborIndices) seenVariable[i] = true;
        numVisited++;

        childLoop:
        for (int i = 0; i < cliques.length; i++) {
          if (i == cursor) continue;
          if (i == parent[cursor]) continue;
          if (domainsOverlap(cliques[cursor], cliques[i])) {

            // Make sure that for every variable that we've already seen somewhere in the graph, if it's
            // in the child, it's in the parent. Otherwise we'll break the property of continuous
            // transmission of information about variables through messages.

            childNeighborLoop:
            for (int child : cliques[i].neighborIndices) {
              if (seenVariable[child]) {
                for (int j : cliques[cursor].neighborIndices) {
                  if (j == child) {
                    continue childNeighborLoop;
                  }
                }
                // If we get here it means that this clique is not good as a child, since we can't pass
                // it all the information it needs from other elements of the tree
                continue childLoop;
              }
            }

            if (parent[i] == -1 && !visited[i]) {
              if (!toVisitArray[i]) {
                toVisit.add(i);
                toVisitArray[i] = true;
                for (int j : cliques[i].neighborIndices) seenVariable[j] = true;
              }
              parent[i] = cursor;
            }
          }
        }
      }
      // No cycles in the tree
      assert (parent[root] == -1);
    }

    assert (numVisited == cliques.length);

    // Backward pass, run the visited list in reverse

    for (int i = numVisited - 1; i >= 0; i--) {
      int cursor = visitedOrder[i];
      if (parent[cursor] == -1) continue;

      backwardPassedMessages[cursor][parent[cursor]] = true;

      // OPTIMIZATION:
      // if these conditions are met we can avoid calculating the message, and instead retrieve from the cache,
      // since they should be the same

      if (CACHE_MESSAGES
          && forceRootForCachedMessagePassing != -1
          && cachedCliquesBackPointers[cursor] != -1
          && cachedCliquesBackPointers[parent[cursor]] != -1
          && cachedMessages[cachedCliquesBackPointers[cursor]][cachedCliquesBackPointers[parent[cursor]]] != null
          && cachedBackwardPassedMessages[cachedCliquesBackPointers[cursor]][cachedCliquesBackPointers[parent[cursor]]]) {
        messages[cursor][parent[cursor]] =
            cachedMessages[cachedCliquesBackPointers[cursor]][cachedCliquesBackPointers[parent[cursor]]];
      } else {

        // Calculate the message to the clique's parent, given all incoming messages so far

        TableFactor message = cliques[cursor];
        for (int k = 0; k < cliques.length; k++) {
          if (k == parent[cursor]) continue;
          if (messages[k][cursor] != null) {
            message = message.multiply(messages[k][cursor]);
          }
        }

        messages[cursor][parent[cursor]] = marginalizeMessage(message, cliques[parent[cursor]].neighborIndices, marginalize);

        // Invalidate any cached outgoing messages
        if (CACHE_MESSAGES
            && forceRootForCachedMessagePassing != -1
            && cachedCliquesBackPointers[parent[cursor]] != -1) {
          for (int k = 0; k < cachedCliqueList.length; k++) {
            cachedMessages[cachedCliquesBackPointers[parent[cursor]]][k] = null;
          }
        }
      }
    }

    // Forward pass, run the visited list forward

    for (int i = 0; i < numVisited; i++) {
      int cursor = visitedOrder[i];
      for (int j = 0; j < cliques.length; j++) {
        if (parent[j] != cursor) continue;

        TableFactor message = cliques[cursor];
        for (int k = 0; k < cliques.length; k++) {
          if (k == j) continue;
          if (messages[k][cursor] != null) {
            message = message.multiply(messages[k][cursor]);
          }
        }

        messages[cursor][j] = marginalizeMessage(message, cliques[j].neighborIndices, marginalize);
      }
    }

    // OPTIMIZATION:
    // cache the messages, and the current list of cliques

    if (CACHE_MESSAGES) {
      cachedCliqueList = cliques;
      cachedMessages = messages;
      cachedBackwardPassedMessages = backwardPassedMessages;
    }

    // Calculate final marginals for each variable

    double[][] marginals = new double[maxVar + 1][];

    // Include observed variables as deterministic

    for (GraphicalModel.Factor fac : model.factors) {
      for (int i = 0; i < fac.neigborIndices.length; i++) {
        int n = fac.neigborIndices[i];
        if (model.getVariableMetaDataByReference(n).containsKey(VARIABLE_OBSERVED_VALUE)) {
          double[] deterministic = new double[fac.featuresTable.getDimensions()[i]];
          int assignment = Integer.parseInt(model.getVariableMetaDataByReference(n).get(VARIABLE_OBSERVED_VALUE));
          if (assignment > deterministic.length) {
            throw new IllegalStateException("Variable " + n + ": Can't have as assignment (" + assignment + ") that is out of bounds for dimension size (" + deterministic.length + ")");
          }
          deterministic[assignment] = 1.0;
          marginals[n] = deterministic;
        }
      }
    }

    Map<GraphicalModel.Factor, TableFactor> jointMarginals = new IdentityHashMap<>();

    if (marginalize == MarginalizationMethod.SUM && includeJointMarginalsAndPartition) {
      boolean[] partitionIncludesTrees = new boolean[treeIndex + 1];
      double[] treePartitionFunctions = new double[treeIndex + 1];

      for (int i = 0; i < cliques.length; i++) {
        TableFactor convergedClique = cliques[i];

        for (int j = 0; j < cliques.length; j++) {
          if (i == j) continue;
          if (messages[j][i] == null) continue;
          convergedClique = convergedClique.multiply(messages[j][i]);
        }

        // Calculate the partition function when we're calculating marginals
        // We need one contribution per tree in our forest graph

        if (!partitionIncludesTrees[trees[i]]) {
          partitionIncludesTrees[trees[i]] = true;
          treePartitionFunctions[trees[i]] = convergedClique.valueSum();
          partitionFunction *= treePartitionFunctions[trees[i]];
        } else {

          // This is all just an elaborate assert
          // Check that our partition function is the same as the trees we're attached to, or with %.1, for numerical reasons.
          // Sometimes the partition function will explode in value, which can make a non-%-based assert worthless here

          if (assertsEnabled() && !TableFactor.USE_EXP_APPROX) {
            double valueSum = convergedClique.valueSum();
            if (Double.isFinite(valueSum) && Double.isFinite(treePartitionFunctions[trees[i]])) {
              if (Math.abs(treePartitionFunctions[trees[i]] - valueSum) >= 1.0e-3 * treePartitionFunctions[trees[i]]) {
                log.info("Different partition functions for tree " + trees[i] + ": ");
                log.info("Pre-existing for tree: " + treePartitionFunctions[trees[i]]);
                log.info("This clique for tree: " + valueSum);
              }
              assert (Math.abs(treePartitionFunctions[trees[i]] - valueSum) < 1.0e-3 * treePartitionFunctions[trees[i]]);
            }
          }
        }

        // Calculate the factor this clique corresponds to, and put in an entry for joint marginals

        GraphicalModel.Factor f = cliqueToFactor.get(i);
        assert (f != null);
        if (!jointMarginals.containsKey(f)) {
          int[] observedAssignments = getObservedAssignments(f);

          // Collect back pointers and check if this factor matches the clique we're using

          int[] backPointers = new int[observedAssignments.length];
          int cursor = 0;
          for (int j = 0; j < observedAssignments.length; j++) {
            if (observedAssignments[j] == -1) {
              backPointers[j] = cursor;
              cursor++;
            }
            // This is not strictly necessary but will trigger array OOB exception if things go wrong, so is nice
            else backPointers[j] = -1;
          }

          double sum = convergedClique.valueSum();

          TableFactor jointMarginal = new TableFactor(f.neigborIndices, f.featuresTable.getDimensions());

          // OPTIMIZATION:
          // Rather than use the standard iterator, which creates lots of int[] arrays on the heap, which need to be GC'd,
          // we use the fast version that just mutates one array. Since this is read once for us here, this is ideal.
          Iterator<int[]> fastPassByReferenceIterator = convergedClique.fastPassByReferenceIterator();
          int[] assignment = fastPassByReferenceIterator.next();
          while (true) {
            if (backPointers.length == assignment.length) {
              jointMarginal.setAssignmentValue(assignment, convergedClique.getAssignmentValue(assignment) / sum);
            } else {
              int[] jointAssignment = new int[backPointers.length];
              for (int j = 0; j < jointAssignment.length; j++) {
                if (observedAssignments[j] != -1) jointAssignment[j] = observedAssignments[j];
                else jointAssignment[j] = assignment[backPointers[j]];
              }
              jointMarginal.setAssignmentValue(jointAssignment, convergedClique.getAssignmentValue(assignment) / sum);
            }

            // Set the assignment arrays correctly
            if (fastPassByReferenceIterator.hasNext()) fastPassByReferenceIterator.next();
            else break;
          }

          jointMarginals.put(f, jointMarginal);
        }

        boolean anyNull = false;
        for (int j = 0; j < convergedClique.neighborIndices.length; j++) {
          int k = convergedClique.neighborIndices[j];
          if (marginals[k] == null) {
            anyNull = true;
          }
        }

        if (anyNull) {
          double[][] cliqueMarginals = null;
          switch (marginalize) {
            case SUM:
              cliqueMarginals = convergedClique.getSummedMarginals();
              break;
            case MAX:
              cliqueMarginals = convergedClique.getMaxedMarginals();
              break;
          }
          for (int j = 0; j < convergedClique.neighborIndices.length; j++) {
            int k = convergedClique.neighborIndices[j];
            if (marginals[k] == null) {
              marginals[k] = cliqueMarginals[j];
            }
          }
        }
      }
    }
    // If we don't care about joint marginals, we can be careful about not calculating more cliques than we need to,
    // by explicitly sorting by which cliques are most profitable to calculate over. In this way we can avoid, in
    // the case of a chain CRF, calculating almost half the joint factors.
    else {
      // First do a pass where we only calculate all-null neighbors
      for (int i = 0; i < cliques.length; i++) {
        boolean allNull = true;
        for (int k : cliques[i].neighborIndices) {
          if (marginals[k] != null) allNull = false;
        }
        if (allNull) {
          TableFactor convergedClique = cliques[i];

          for (int j = 0; j < cliques.length; j++) {
            if (i == j) continue;
            if (messages[j][i] == null) continue;
            convergedClique = convergedClique.multiply(messages[j][i]);
          }

          double[][] cliqueMarginals = null;
          switch (marginalize) {
            case SUM:
              cliqueMarginals = convergedClique.getSummedMarginals();
              break;
            case MAX:
              cliqueMarginals = convergedClique.getMaxedMarginals();
              break;
          }
          for (int j = 0; j < convergedClique.neighborIndices.length; j++) {
            int k = convergedClique.neighborIndices[j];
            if (marginals[k] == null) {
              marginals[k] = cliqueMarginals[j];
            }
          }
        }
      }
      // Now we calculate any remaining cliques with any non-null variables
      for (int i = 0; i < cliques.length; i++) {
        boolean anyNull = false;
        for (int j = 0; j < cliques[i].neighborIndices.length; j++) {
          int k = cliques[i].neighborIndices[j];
          if (marginals[k] == null) {
            anyNull = true;
          }
        }

        if (anyNull) {
          TableFactor convergedClique = cliques[i];

          for (int j = 0; j < cliques.length; j++) {
            if (i == j) continue;
            if (messages[j][i] == null) continue;
            convergedClique = convergedClique.multiply(messages[j][i]);
          }

          double[][] cliqueMarginals = null;
          switch (marginalize) {
            case SUM:
              cliqueMarginals = convergedClique.getSummedMarginals();
              break;
            case MAX:
              cliqueMarginals = convergedClique.getMaxedMarginals();
              break;
          }
          for (int j = 0; j < convergedClique.neighborIndices.length; j++) {
            int k = convergedClique.neighborIndices[j];
            if (marginals[k] == null) {
              marginals[k] = cliqueMarginals[j];
            }
          }
        }
      }
    }

    // Add any factors to the joint marginal map that were fully observed and so didn't get cliques
    if (marginalize == MarginalizationMethod.SUM && includeJointMarginalsAndPartition) {
      for (GraphicalModel.Factor f : model.factors) {
        if (!jointMarginals.containsKey(f)) {
          // This implies that every variable in the factor is observed. If that's the case, we need to construct
          // a one hot TableFactor representing the deterministic distribution.
          TableFactor deterministicJointMarginal = new TableFactor(f.neigborIndices, f.featuresTable.getDimensions());
          int[] observedAssignment = getObservedAssignments(f);
          for (int i : observedAssignment) assert (i != -1);
          deterministicJointMarginal.setAssignmentValue(observedAssignment, 1.0);

          jointMarginals.put(f, deterministicJointMarginal);
        }
      }
    }

    return new MarginalResult(marginals, partitionFunction, jointMarginals);
  }

  private int[] getObservedAssignments(GraphicalModel.Factor f) {
    int[] observedAssignments = new int[f.neigborIndices.length];
    for (int i = 0; i < observedAssignments.length; i++) {
      if (model.getVariableMetaDataByReference(f.neigborIndices[i]).containsKey(VARIABLE_OBSERVED_VALUE)) {
        observedAssignments[i] = Integer.parseInt(model.getVariableMetaDataByReference(f.neigborIndices[i]).get(VARIABLE_OBSERVED_VALUE));
      } else observedAssignments[i] = -1;
    }
    return observedAssignments;
  }

  /**
   * This is a key step in message passing. When we are calculating a message, we want to marginalize out all variables
   * not relevant to the recipient of the message. This function does that.
   *
   * @param message     the message to marginalize
   * @param relevant    the variables that are relevant
   * @param marginalize whether to use sum of max marginalization, for marginal or MAP inference
   * @return the marginalized message
   */
  private static TableFactor marginalizeMessage(TableFactor message, int[] relevant, MarginalizationMethod marginalize) {
    TableFactor result = message;

    for (int i : message.neighborIndices) {
      boolean contains = false;
      for (int j : relevant) {
        if (i == j) {
          contains = true;
          break;
        }
      }
      if (!contains) {
        switch (marginalize) {
          case SUM:
            result = result.sumOut(i);
            break;
          case MAX:
            result = result.maxOut(i);
            break;
        }
      }
    }

    return result;
  }

  /**
   * Just a quick inline to check if two factors have overlapping domains. Since factor neighbor sets are super small,
   * this n^2 algorithm is fine.
   *
   * @param f1 first factor to compare
   * @param f2 second factor to compare
   * @return whether their domains overlap
   */
  private static boolean domainsOverlap(TableFactor f1, TableFactor f2) {
    for (int n1 : f1.neighborIndices) {
      for (int n2 : f2.neighborIndices) {
        if (n1 == n2) return true;
      }
    }
    return false;
  }

  @SuppressWarnings({"*", "AssertWithSideEffects", "ConstantConditions", "UnusedAssignment"})
  private static boolean assertsEnabled() {
    boolean assertsEnabled = false;
    assert (assertsEnabled = true); // intentional side effect
    return assertsEnabled;
  }
}
