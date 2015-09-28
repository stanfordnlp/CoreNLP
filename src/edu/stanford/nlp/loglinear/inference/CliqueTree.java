package edu.stanford.nlp.loglinear.inference;

import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.GraphicalModel;

import java.util.*;

/**
 * Created by keenon on 8/11/15.
 *
 * This is instantiated once per model, so that it can keep caches of important stuff like messages and
 * local factors during many game playing sample steps. It assumes that the model that is passed in is by-reference,
 * and that it can change between inference calls in small ways, so that cacheing of some results is worthwhile.
 */
public class CliqueTree {
    GraphicalModel model;
    ConcatVector weights;

    // This is the metadata key for the model to store an observed value for a variable, as an int
    public static final String VARIABLE_OBSERVED_VALUE = "inference.CliqueTree.VARIABLE_OBSERVED_VALUE";

    /**
     * Create an Inference object for a given set of weights, and a model.
     * <p>
     * The object is around to facilitate cacheing as an eventual optimization, when models are changing in minor ways
     * and inference is required several times. Work is done lazily, so is left until actual inference is requested.
     *
     * @param model   the model to be computed over, subject to change in the future
     * @param weights the weights to dot product with model features to get log-linear factors
     */
    public CliqueTree(GraphicalModel model, ConcatVector weights) {
        this.model = model;
        this.weights = weights;
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
     * @return an array, indexed first by variable, then by variable assignment, of global probability
     */
    public MarginalResult calculateMarginals() {
        return messagePassing(MarginalizationMethod.SUM);
    }

    /**
     * This assumes that factors represent joint probabilities.
     *
     * @return an array, indexed by variable, of maximum likelihood assignments
     */
    public int[] calculateMAP() {
        double[][] mapMarginals = messagePassing(MarginalizationMethod.MAX).marginals;
        int[] result = new int[mapMarginals.length];
        for (int i = 0; i < result.length; i++) {
            if (mapMarginals[i] != null) {
                for (int j = 0; j < mapMarginals[i].length; j++) {
                    if (mapMarginals[i][j] > mapMarginals[i][result[i]]) {
                        result[i] = j;
                    }
                }
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

    /**
     * Does tree shaped message passing. The algorithm calls for first passing down to the leaves, then passing back up
     * to the root.
     *
     * @param marginalize the method for marginalization, controls MAP or marginals
     * @return the marginal messages
     */
    private MarginalResult messagePassing(MarginalizationMethod marginalize) {

        // Using the behavior of brute force factor multiplication as ground truth, the desired
        // outcome of marginal calculation with an impossible factor is a uniform probability dist.,
        // since we have a resulting factor of all 0s. That is of course assuming that normalizing
        // all 0s gives you uniform, but that's a useful tolerance to include, so we do.

        boolean impossibleObservationMade = false;

        // Message passing will look at fully observed cliques as non-entities, but their
        // log-likelihood (the log-likelihood of the single observed value) is still relevant for the
        // partition function.

        double partitionFunction = 1.0;

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

        // Create the cliques by multiplying out table factors
        // TODO:OPT This could be made more efficient by observing first, then dot product

        List<TableFactor> cliquesList = new ArrayList<>();
        Map<Integer, GraphicalModel.Factor> cliqueToFactor = new HashMap<>();

        for (GraphicalModel.Factor f : model.factors) {
            boolean allObserved = true;
            for (int n : f.neigborIndices) {
                if (!model.getVariableMetaDataByReference(n).containsKey(VARIABLE_OBSERVED_VALUE)) allObserved = false;
            }
            if (allObserved) continue;

            TableFactor clique = new TableFactor(weights, f);
            cliqueToFactor.put(cliquesList.size(), f);
            cliquesList.add(clique);
        }

        TableFactor[] cliques = cliquesList.toArray(new TableFactor[cliquesList.size()]);

        // Observe all variables that need to be observed out

        for (int i = 0; i < cliques.length; i++) {
            assert (cliqueToFactor.containsKey(i));
            TableFactor result = cliques[i];
            for (int n : cliques[i].neighborIndices) {
                Map<String, String> metadata = model.getVariableMetaDataByReference(n);
                if (metadata.containsKey(VARIABLE_OBSERVED_VALUE)) {
                    int value = Integer.parseInt(metadata.get(VARIABLE_OBSERVED_VALUE));
                    result = result.observe(n, value);
                }
            }

            boolean nonZeroValue = false;
            for (int[] assignment : result) {
                if (result.getAssignmentValue(assignment) > 0) {
                    nonZeroValue = true;
                    break;
                }
            }
            if (!nonZeroValue) impossibleObservationMade = true;

            cliques[i] = result;
        }

        // If we made any impossible observations, we can just return a uniform distribution for all the variables that
        // weren't observed, since that's the semantically correct thing to do (probability is broken at this point).

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
            for (GraphicalModel.Factor f : model.factors) {
                TableFactor uniformZero = new TableFactor(f.neigborIndices, f.featuresTable.getDimensions());

                for (int[] assignment : uniformZero) {
                    uniformZero.setAssignmentValue(assignment, 0.0);
                }

                jointMarginals.put(f, uniformZero);
            }

            return new MarginalResult(result, 1.0, jointMarginals);
        }

        // Indexed by (start-clique, end-clique), this array will remain mostly null in most graphs

        TableFactor[][] messages = new TableFactor[cliques.length][cliques.length];

        // Create the data structures to hold the tree pattern

        List<Integer> visited = new ArrayList<>();
        int[] parent = new int[cliques.length];
        for (int i = 0; i < parent.length; i++) parent[i] = -1;
        // Figure out which cliques are connected to which trees. This is important for calculating the partition
        // function later, since each tree will converge to its own partition function by multiplication, and we will
        // need to multiply the partition function of each of the trees to get the global one.
        int[] trees = new int[cliques.length];

        // Forward pass, record a BFS forest pattern that we can use for message passing

        int treeIndex = -1;
        while (visited.size() < cliques.length) {
            treeIndex++;

            // Pick the largest connected graph remaining as the root for message passing

            int root = -1;
            for (int i = 0; i < cliques.length; i++) {
                if (!visited.contains(i) &&
                        (root == -1 || cliques[i].neighborIndices.length > cliques[root].neighborIndices.length)) {
                    root = i;
                }
            }
            assert (root != -1);

            Queue<Integer> toVisit = new ArrayDeque<>();
            toVisit.add(root);

            while (toVisit.size() > 0) {
                int cursor = toVisit.poll();
                trees[cursor] = treeIndex;
                if (visited.contains(cursor)) {
                    System.err.println("Visited contains: " + cursor);
                    System.err.println("Visited: " + visited);
                    System.err.println("To visit: " + toVisit);
                }
                assert (!visited.contains(cursor));
                visited.add(cursor);

                for (int i = 0; i < cliques.length; i++) {
                    if (i == cursor) continue;
                    if (i == parent[cursor]) continue;
                    if (domainsOverlap(cliques[cursor], cliques[i])) {
                        if (parent[i] == -1) {
                            if (!toVisit.contains(i)) {
                                toVisit.add(i);
                            }
                            parent[i] = cursor;
                        }
                    }
                }
            }
        }

        assert (visited.size() == cliques.length);

        // Backward pass, run the visited list in reverse

        for (int i = visited.size() - 1; i >= 0; i--) {
            int cursor = visited.get(i);
            if (parent[cursor] == -1) continue;

            // Calculate the message to the clique's parent, given all incoming messages so far

            TableFactor message = cliques[cursor];
            for (int k = 0; k < cliques.length; k++) {
                if (k == parent[cursor]) continue;
                if (messages[k][cursor] != null) {
                    message = message.multiply(messages[k][cursor]);
                }
            }

            messages[cursor][parent[cursor]] = marginalizeMessage(message, cliques[parent[cursor]].neighborIndices, marginalize);
        }

        // Forward pass, run the visited list forward

        for (int cursor : visited) {
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

        // Calculate final marginals for each variable

        int maxVar = 0;
        for (GraphicalModel.Factor fac : model.factors) {
            for (int i : fac.neigborIndices) if (i > maxVar) maxVar = i;
        }

        double[][] marginals = new double[maxVar + 1][];

        // Include observed variables as deterministic

        for (GraphicalModel.Factor fac : model.factors) {
            for (int i = 0; i < fac.neigborIndices.length; i++) {
                int n = fac.neigborIndices[i];
                if (model.getVariableMetaDataByReference(n).containsKey(VARIABLE_OBSERVED_VALUE)) {
                    double[] deterministic = new double[fac.featuresTable.getDimensions()[i]];
                    deterministic[Integer.parseInt(model.getVariableMetaDataByReference(n).get(VARIABLE_OBSERVED_VALUE))] = 1.0;
                    marginals[n] = deterministic;
                }
            }
        }

        boolean[] partitionIncludesTrees = new boolean[treeIndex + 1];
        double[] treePartitionFunctions = new double[treeIndex + 1];

        Map<GraphicalModel.Factor, TableFactor> jointMarginals = new IdentityHashMap<>();

        for (int i = 0; i < cliques.length; i++) {
            TableFactor convergedClique = cliques[i];

            for (int j = 0; j < cliques.length; j++) {
                if (i == j) continue;
                if (messages[j][i] == null) continue;
                convergedClique = convergedClique.multiply(messages[j][i]);
            }

            if (marginalize == MarginalizationMethod.SUM) {

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

                    if (assertsEnabled()) {
                        double valueSum = convergedClique.valueSum();
                        if (Double.isFinite(valueSum) && Double.isFinite(treePartitionFunctions[trees[i]])) {
                            if (Math.abs(treePartitionFunctions[trees[i]] - valueSum) >= 1.0e-3 * treePartitionFunctions[trees[i]]) {
                                System.err.println("Different partition functions for tree " + i + ": ");
                                System.err.println("Pre-existing for tree: " + treePartitionFunctions[trees[i]]);
                                System.err.println("This clique for tree: " + valueSum);
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
            }

            for (int j : convergedClique.neighborIndices) {
                if (marginals[j] == null) {
                    switch (marginalize) {
                        case SUM:
                            marginals[j] = convergedClique.getSummedMarginal(j);
                            break;
                        case MAX:
                            marginals[j] = convergedClique.getMaxedMarginal(j);
                            break;
                    }
                }
            }
        }

        // Add any factors to the joint marginal map that were fully observed and so didn't get cliques
        if (marginalize == MarginalizationMethod.SUM) {
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
    private TableFactor marginalizeMessage(TableFactor message, int[] relevant, MarginalizationMethod marginalize) {
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
    private boolean domainsOverlap(TableFactor f1, TableFactor f2) {
        for (int n1 : f1.neighborIndices) {
            for (int n2 : f2.neighborIndices) {
                if (n1 == n2) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("*")
    private boolean assertsEnabled() {
        boolean assertsEnabled = false;
        assert(assertsEnabled = true); // intentional side effect
        return assertsEnabled;
    }
}
