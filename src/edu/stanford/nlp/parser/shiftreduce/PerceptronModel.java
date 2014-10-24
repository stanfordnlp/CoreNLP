package edu.stanford.nlp.parser.shiftreduce;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import edu.stanford.nlp.parser.common.ParserConstraint;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.ScoredComparator;
import edu.stanford.nlp.util.ScoredObject;

public class PerceptronModel implements Serializable {
  final Map<String, Weight> featureWeights;
  final FeatureFactory featureFactory;

  final ShiftReduceOptions op;

  // This is shared with the owning ShiftReduceParser (for now, at least)
  final Index<Transition> transitionIndex;
  final Set<String> knownStates;
  final Set<String> rootStates;
  final Set<String> rootOnlyStates;

  public PerceptronModel(ShiftReduceOptions op, Index<Transition> transitionIndex,
                         Set<String> knownStates, Set<String> rootStates, Set<String> rootOnlyStates) {
    this.transitionIndex = transitionIndex;
    this.op = op;

    this.knownStates = knownStates;
    this.rootStates = rootStates;
    this.rootOnlyStates = rootOnlyStates;

    this.featureWeights = Generics.newHashMap();

    String[] classes = op.featureFactoryClass.split(";");
    if (classes.length == 1) {
      this.featureFactory = ReflectionLoading.loadByReflection(classes[0]);
    } else {
      FeatureFactory[] factories = new FeatureFactory[classes.length];
      for (int i = 0; i < classes.length; ++i) {
        int paren = classes[i].indexOf("(");
        if (paren >= 0) {
          String arg = classes[i].substring(paren + 1, classes[i].length() - 1);
          factories[i] = ReflectionLoading.loadByReflection(classes[i].substring(0, paren), arg);
        } else {
          factories[i] = ReflectionLoading.loadByReflection(classes[i]);
        }
      }
      this.featureFactory = new CombinationFeatureFactory(factories);
    }
  }

  public PerceptronModel(PerceptronModel other) {
    this.featureFactory = other.featureFactory;
    this.op = other.op;

    this.transitionIndex = other.transitionIndex;
    this.knownStates = other.knownStates;
    this.rootStates = other.rootStates;
    this.rootOnlyStates = other.rootOnlyStates;

    this.featureWeights = Generics.newHashMap();
    for (String feature : other.featureWeights.keySet()) {
      featureWeights.put(feature, new Weight(other.featureWeights.get(feature)));
    }
  }

  public PerceptronModel(Map<String, Weight> featureWeights, 
                         ShiftReduceOptions op, FeatureFactory featureFactory, Index<Transition> transitionIndex,
                         Set<String> knownStates, Set<String> rootStates, Set<String> rootOnlyStates) {
    this.featureWeights = featureWeights;
    this.op = op;
    this.featureFactory = featureFactory;
    this.transitionIndex = transitionIndex;
    this.knownStates = knownStates;
    this.rootStates = rootStates;
    this.rootOnlyStates = rootOnlyStates;
  }

  private static final NumberFormat NF = new DecimalFormat("0.00");

  public static PerceptronModel averageScoredModels(Collection<ScoredObject<PerceptronModel>> scoredModels) {
    if (scoredModels.size() == 0) {
      throw new IllegalArgumentException("Cannot average empty models");
    }

    System.err.print("Averaging " + scoredModels.size() + " models with scores");
    for (ScoredObject<PerceptronModel> model : scoredModels) {
      System.err.print(" " + NF.format(model.score()));
    }
    System.err.println();

    List<PerceptronModel> models = CollectionUtils.transformAsList(scoredModels, object -> object.object());
    return averageModels(models);
  }

  public static PerceptronModel averageModels(Collection<PerceptronModel> models) {
    if (models.size() == 0) {
      throw new IllegalArgumentException("Cannot average empty models");
    }

    Set<String> features = Generics.newHashSet();
    for (PerceptronModel model : models) {
      for (String feature : model.featureWeights.keySet()) {
        features.add(feature);
      }
    }

    Map<String, Weight> featureWeights = Generics.newHashMap();
    for (String feature : features) {
      featureWeights.put(feature, new Weight());
    }

    int numModels = models.size();
    for (String feature : features) {
      for (PerceptronModel model : models) {
        if (!model.featureWeights.containsKey(feature)) {
          continue;
        }
        featureWeights.get(feature).addScaled(model.featureWeights.get(feature), 1.0f / numModels);
      }
    }

    PerceptronModel first = models.iterator().next();
    return new PerceptronModel(featureWeights, first.op, first.featureFactory, first.transitionIndex, 
                               first.knownStates, first.rootStates, first.rootOnlyStates);
  }

  /**
   * Iterate over the feature weight map.
   * For each feature, remove all transitions with score of 0.
   * Any feature with no transitions left is then removed
   */
  void condenseFeatures() {
    Iterator<String> featureIt = featureWeights.keySet().iterator();
    while (featureIt.hasNext()) {
      String feature = featureIt.next();
      Weight weights = featureWeights.get(feature);
      weights.condense();
      if (weights.size() == 0) {
        featureIt.remove();
      }
    }
  }

  void filterFeatures(Set<String> keep) {
    Iterator<String> featureIt = featureWeights.keySet().iterator();
    while (featureIt.hasNext()) {
      if (!keep.contains(featureIt.next())) {
        featureIt.remove();
      }
    }
  }


  /**
   * Output some random facts about the model
   */
  public void outputStats() {
    System.err.println("Number of known features: " + featureWeights.size());

    int numWeights = 0;
    for (String feature : featureWeights.keySet()) {
      numWeights += featureWeights.get(feature).size();
    }
    System.err.println("Number of non-zero weights: " + numWeights);

    int wordLength = 0;
    for (String feature : featureWeights.keySet()) {
      wordLength += feature.length();
    }
    System.err.println("Total word length: " + wordLength);

    System.err.println("Number of transitions: " + transitionIndex.size());
  }



  /**
   * Returns a transition which might not even be part of the model,
   * but will hopefully allow progress in an otherwise stuck parse
   *
   * TODO: perhaps we want to create an EmergencyTransition class
   * which indicates that something has gone wrong
   */
  public Transition findEmergencyTransition(State state, List<ParserConstraint> constraints) {
    if (state.stack.size() == 0) {
      return null;
    }

    // See if there is a constraint whose boundaries match the end
    // points of the top node on the stack.  If so, we can apply a
    // UnaryTransition / CompoundUnaryTransition if that would solve
    // the constraint
    if (constraints != null) {
      final Tree top = state.stack.peek();
      for (ParserConstraint constraint : constraints) {
        if (ShiftReduceUtils.leftIndex(top) != constraint.start || ShiftReduceUtils.rightIndex(top) != constraint.end - 1) {
          continue;
        }
        if (ShiftReduceUtils.constraintMatchesTreeTop(top, constraint)) {
          continue;
        }
        // found an unmatched constraint that can be fixed with a unary transition
        // now we need to find a matching state for the transition
        for (String label : knownStates) {
          if (constraint.state.matcher(label).matches()) {
            return ((op.compoundUnaries) ?
                    new CompoundUnaryTransition(Collections.singletonList(label), false) :
                    new UnaryTransition(label, false));
          }
        }
      }
    }

    if (ShiftReduceUtils.isTemporary(state.stack.peek()) &&
        (state.stack.size() == 1 || ShiftReduceUtils.isTemporary(state.stack.pop().peek()))) {
      return ((op.compoundUnaries) ?
              new CompoundUnaryTransition(Collections.singletonList(state.stack.peek().value().substring(1)), false) :
              new UnaryTransition(state.stack.peek().value().substring(1), false));
    }

    if (state.stack.size() == 1 && state.tokenPosition >= state.sentence.size()) {
      // either need to finalize or transition to a root state
      if (!rootStates.contains(state.stack.peek().value())) {
        String root = rootStates.iterator().next();
        return ((op.compoundUnaries) ?
                new CompoundUnaryTransition(Collections.singletonList(root), false) :
                new UnaryTransition(root, false));
      }
    }

    if (state.stack.size() == 1) {
      return null;
    }

    if (ShiftReduceUtils.isTemporary(state.stack.peek())) {
      return new BinaryTransition(state.stack.peek().value().substring(1), BinaryTransition.Side.RIGHT);
    }

    if (ShiftReduceUtils.isTemporary(state.stack.pop().peek())) {
      return new BinaryTransition(state.stack.pop().peek().value().substring(1), BinaryTransition.Side.LEFT);
    }

    return null;
  }


  /** Convenience method: returns one highest scoring transition, without any ParserConstraints */
  public ScoredObject<Integer> findHighestScoringTransition(State state, List<String> features, boolean requireLegal) {
    Collection<ScoredObject<Integer>> transitions = findHighestScoringTransitions(state, features, requireLegal, 1, null);
    if (transitions.size() == 0) {
      return null;
    }
    return transitions.iterator().next();
  }

  public Collection<ScoredObject<Integer>> findHighestScoringTransitions(State state, boolean requireLegal, int numTransitions, List<ParserConstraint> constraints) {
    List<String> features = featureFactory.featurize(state);
    return findHighestScoringTransitions(state, features, requireLegal, numTransitions, constraints);
  }

  public Collection<ScoredObject<Integer>> findHighestScoringTransitions(State state, List<String> features, boolean requireLegal, int numTransitions, List<ParserConstraint> constraints) {
    float[] scores = new float[transitionIndex.size()];
    for (String feature : features) {
      Weight weight = featureWeights.get(feature);
      if (weight == null) {
        // Features not in our index are ignored
        continue;
      }
      weight.score(scores);
    }

    PriorityQueue<ScoredObject<Integer>> queue = new PriorityQueue<ScoredObject<Integer>>(numTransitions + 1, ScoredComparator.ASCENDING_COMPARATOR);
    for (int i = 0; i < scores.length; ++i) {
      if (!requireLegal || transitionIndex.get(i).isLegal(state, constraints)) {
        queue.add(new ScoredObject<Integer>(i, scores[i]));
        if (queue.size() > numTransitions) {
          queue.poll();
        }
      }
    }

    return queue;
  }

  private static final long serialVersionUID = 1;
}
