package edu.stanford.nlp.parser.lexparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.MapFactory;
import edu.stanford.nlp.util.MutableDouble;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ThreeDimensionalMap;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.TwoDimensionalMap;


import java.io.*;

/**
 * This class is a reimplementation of Berkeley's state splitting
 * grammar.  This work is experimental and still in progress.  There
 * are several extremely important pieces to implement:
 * <ol>
 * <li> this code should use log probabilities throughout instead of
 *      multiplying tiny numbers
 * <li> time efficiency of the training code is fawful
 * <li> there are better ways to extract parses using this grammar than
 *      the method in ExhaustivePCFGParser
 * <li> we should also implement cascading parsers that let us
 *      shortcircuit low quality parses earlier (which could possibly
 *      benefit non-split parsers as well)
 * <li> when looping, we should short circuit if we go too many loops
 * <li> ought to smooth as per page 436
 * </ol>
 *
 * @author John Bauer
 */
public class SplittingGrammarExtractor  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(SplittingGrammarExtractor.class);
  static final int MIN_DEBUG_ITERATION=0;
  static final int MAX_DEBUG_ITERATION=0;
  static final int MAX_ITERATIONS = Integer.MAX_VALUE;

  int iteration = 0;

  boolean DEBUG() {
    return (iteration >= MIN_DEBUG_ITERATION && iteration < MAX_DEBUG_ITERATION);
  }

  Options op;
  /**
   * These objects are created and filled in here.  The caller can get
   * the data from the extractor once it is finished.
   */
  Index<String> stateIndex;
  Index<String> wordIndex;
  Index<String> tagIndex;
  /**
   * This is a list gotten from the list of startSymbols in op.langpack()
   */
  List<String> startSymbols;

  /**
   * A combined list of all the trees in the training set.
   */
  List<Tree> trees = new ArrayList<>();

  /**
   * All of the weights associated with the trees in the training set.
   * In general, this is just the weight of the original treebank.
   * Note that this uses an identity hash map to map from tree pointer
   * to weight.
   */
  Counter<Tree> treeWeights = new ClassicCounter<>(MapFactory.<Tree, MutableDouble>identityHashMapFactory());

  /**
   * How many total weighted trees we have
   */
  double trainSize;

  /**
   * The original states in the trees
   */
  Set<String> originalStates = Generics.newHashSet();

  /**
   * The current number of times a particular state has been split
   */
  IntCounter<String> stateSplitCounts = new IntCounter<>();

  /**
   * The binary betas are weights to go from Ax to By, Cz.  This maps
   * from (A, B, C) to (x, y, z) to beta(Ax, By, Cz).
   */
  ThreeDimensionalMap<String, String, String, double[][][]> binaryBetas = new ThreeDimensionalMap<>();
  /**
   * The unary betas are weights to go from Ax to By.  This maps
   * from (A, B) to (x, y) to beta(Ax, By).
   */
  TwoDimensionalMap<String, String, double[][]> unaryBetas = new TwoDimensionalMap<>();

  /**
   * The latest lexicon we trained.  At the end of the process, this
   * is the lexicon for the parser.
   */
  Lexicon lex;

  transient Index<String> tempWordIndex;
  transient Index<String> tempTagIndex;

  /**
   * The lexicon we are in the process of building in each iteration.
   */
  transient Lexicon tempLex;

  /**
   * The latest pair of unary and binary grammars we trained.
   */
  Pair<UnaryGrammar, BinaryGrammar> bgug;

  Random random = new Random(87543875943265L);

  static final double LEX_SMOOTH = 0.0001;
  static final double STATE_SMOOTH = 0.0;

  public SplittingGrammarExtractor(Options op) {
    this.op = op;
    startSymbols = Arrays.asList(op.langpack().startSymbols());
  }

  double[] neginfDoubles(int size) {
    double[] result = new double[size];
    for (int i = 0; i < size; ++i) {
      result[i] = Double.NEGATIVE_INFINITY;
    }
    return result;
  }

  public void outputTransitions(Tree tree,
                                IdentityHashMap<Tree, double[][]> unaryTransitions,
                                IdentityHashMap<Tree, double[][][]> binaryTransitions) {
    outputTransitions(tree, 0, unaryTransitions, binaryTransitions);
  }

  public void outputTransitions(Tree tree, int depth,
                                IdentityHashMap<Tree, double[][]> unaryTransitions,
                                IdentityHashMap<Tree, double[][][]> binaryTransitions) {
    for (int i = 0; i < depth; ++i) {
      System.out.print(" ");
    }
    if (tree.isLeaf()) {
      System.out.println(tree.label().value());
      return;
    }
    if (tree.children().length == 1) {
      System.out.println(tree.label().value() + " -> " + tree.children()[0].label().value());
      if (!tree.isPreTerminal()) {
        double[][] transitions = unaryTransitions.get(tree);
        for (int i = 0; i < transitions.length; ++i) {
          for (int j = 0; j < transitions[0].length; ++j) {
            for (int z = 0; z < depth; ++z) {
              System.out.print(" ");
            }
            System.out.println("  " + i + "," + j + ": " + transitions[i][j] + " | " + Math.exp(transitions[i][j]));
          }
        }
      }
    } else {
      System.out.println(tree.label().value() + " -> " + tree.children()[0].label().value() + " " + tree.children()[1].label().value());
      double[][][] transitions = binaryTransitions.get(tree);
      for (int i = 0; i < transitions.length; ++i) {
        for (int j = 0; j < transitions[0].length; ++j) {
          for (int k = 0; k < transitions[0][0].length; ++k) {
            for (int z = 0; z < depth; ++z) {
              System.out.print(" ");
            }
            System.out.println("  " + i + "," + j + "," + k + ": " + transitions[i][j][k] + " | " + Math.exp(transitions[i][j][k]));
          }
        }
      }
    }
    if (tree.isPreTerminal()) {
      return;
    }
    for (Tree child : tree.children()) {
      outputTransitions(child, depth + 1, unaryTransitions, binaryTransitions);
    }
  }

  public void outputBetas() {
    System.out.println("UNARY:");
    for (String parent : unaryBetas.firstKeySet()) {
      for (String child : unaryBetas.get(parent).keySet()) {
        System.out.println("  " + parent + "->" + child);
        double[][] betas = unaryBetas.get(parent).get(child);
        int parentStates = betas.length;
        int childStates = betas[0].length;
        for (int i = 0; i < parentStates; ++i) {
          for (int j = 0; j < childStates; ++j) {
            System.out.println("    " + i + "->" + j + " " + betas[i][j] + " | " + Math.exp(betas[i][j]));
          }
        }
      }
    }
    System.out.println("BINARY:");
    for (String parent : binaryBetas.firstKeySet()) {
      for (String left : binaryBetas.get(parent).firstKeySet()) {
        for (String right : binaryBetas.get(parent).get(left).keySet()) {
          System.out.println("  " + parent + "->" + left + "," + right);
          double[][][] betas = binaryBetas.get(parent).get(left).get(right);
          int parentStates = betas.length;
          int leftStates = betas[0].length;
          int rightStates = betas[0][0].length;
          for (int i = 0; i < parentStates; ++i) {
            for (int j = 0; j < leftStates; ++j) {
              for (int k = 0; k < rightStates; ++k) {
                System.out.println("    " + i + "->" + j + "," + k + " " + betas[i][j][k] + " | " + Math.exp(betas[i][j][k]));
              }
            }
          }
        }
      }
    }
  }

  public String state(String tag, int i) {
    if (startSymbols.contains(tag) || tag.equals(Lexicon.BOUNDARY_TAG)) {
      return tag;
    }
    return tag + "^" + i;
  }

  public int getStateSplitCount(Tree tree) {
    return stateSplitCounts.getIntCount(tree.label().value());
  }

  public int getStateSplitCount(String label) {
    return stateSplitCounts.getIntCount(label);
  }


  /**
   * Count all the internal labels in all the trees, and set their
   * initial state counts to 1.
   */
  public void countOriginalStates() {
    originalStates.clear();
    for (Tree tree : trees) {
      countOriginalStates(tree);
    }

    for (String state : originalStates) {
      stateSplitCounts.incrementCount(state, 1);
    }
  }

  /**
   * Counts the labels in the tree, but not the words themselves.
   */
  private void countOriginalStates(Tree tree) {
    if (tree.isLeaf()) {
      return;
    }

    originalStates.add(tree.label().value());
    for (Tree child : tree.children()) {
      if (child.isLeaf())
        continue;
      countOriginalStates(child);
    }
  }

  private void initialBetasAndLexicon() {
    wordIndex = new HashIndex<>();
    tagIndex = new HashIndex<>();
    lex = op.tlpParams.lex(op, wordIndex, tagIndex);
    lex.initializeTraining(trainSize);

    for (Tree tree : trees) {
      double weight = treeWeights.getCount(tree);
      lex.incrementTreesRead(weight);
      initialBetasAndLexicon(tree, 0, weight);
    }

    lex.finishTraining();
  }

  private int initialBetasAndLexicon(Tree tree, int position, double weight) {
    if (tree.isLeaf()) {
      // should never get here, unless a training tree is just one leaf
      return position;
    }

    if (tree.isPreTerminal()) {
      // fill in initial lexicon here
      String tag = tree.label().value();
      String word = tree.children()[0].label().value();
      TaggedWord tw = new TaggedWord(word, state(tag, 0));
      lex.train(tw, position, weight);
      return (position + 1);
    }

    if (tree.children().length == 2) {
      String label = tree.label().value();
      String leftLabel = tree.getChild(0).label().value();
      String rightLabel = tree.getChild(1).label().value();
      if (!binaryBetas.contains(label, leftLabel, rightLabel)) {
        double[][][] map = new double[1][1][1];
        map[0][0][0] = 0.0;
        binaryBetas.put(label, leftLabel, rightLabel, map);
      }
    } else if (tree.children().length == 1) {
      String label = tree.label().value();
      String childLabel = tree.getChild(0).label().value();
      if (!unaryBetas.contains(label, childLabel)) {
        double[][] map = new double[1][1];
        map[0][0] = 0.0;
        unaryBetas.put(label, childLabel, map);
      }
    } else {
      // should have been binarized
      throw new RuntimeException("Trees should have been binarized, expected 1 or 2 children");
    }

    for (Tree child : tree.children()) {
      position = initialBetasAndLexicon(child, position, weight);
    }
    return position;
  }


  /**
   * Splits the state counts.  Root states and the boundary tag do not
   * get their counts increased, and all others are doubled.  Betas
   * and transition weights are handled later.
   */
  private void splitStateCounts() {
    // double the count of states...
    IntCounter<String> newStateSplitCounts = new IntCounter<>();
    newStateSplitCounts.addAll(stateSplitCounts);
    newStateSplitCounts.addAll(stateSplitCounts);

    // root states should only have 1
    for (String root : startSymbols) {
      if (newStateSplitCounts.getCount(root) > 1) {
        newStateSplitCounts.setCount(root, 1);
      }
    }

    if (newStateSplitCounts.getCount(Lexicon.BOUNDARY_TAG) > 1) {
      newStateSplitCounts.setCount(Lexicon.BOUNDARY_TAG, 1);
    }

    stateSplitCounts = newStateSplitCounts;
  }


  static final double EPSILON = 0.0001;

  /**
   * Before each iteration of splitting states, we have tables of
   * betas which correspond to the transitions between different
   * substates.  When we resplit the states, we duplicate parent
   * states and then split their transitions 50/50 with some random
   * variation between child states.
   */
  public void splitBetas() {
    TwoDimensionalMap<String, String, double[][]> tempUnaryBetas = new TwoDimensionalMap<>();
    ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas = new ThreeDimensionalMap<>();

    for (String parent : unaryBetas.firstKeySet()) {
      for (String child : unaryBetas.get(parent).keySet()) {
        double[][] betas = unaryBetas.get(parent, child);
        int parentStates = betas.length;
        int childStates = betas[0].length;

        double[][] newBetas;
        if (!startSymbols.contains(parent)) {
          newBetas = new double[parentStates * 2][childStates];
          for (int i = 0; i < parentStates; ++i) {
            for (int j = 0; j < childStates; ++j) {
              newBetas[i * 2][j] = betas[i][j];
              newBetas[i * 2 + 1][j] = betas[i][j];
            }
          }
          parentStates *= 2;
          betas = newBetas;
        }
        if (!child.equals(Lexicon.BOUNDARY_TAG)) {
          newBetas = new double[parentStates][childStates * 2];
          for (int i = 0; i < parentStates; ++i) {
            for (int j = 0; j < childStates; ++j) {
              double childWeight = 0.45 + random.nextDouble() * 0.1;
              newBetas[i][j * 2] = betas[i][j] + Math.log(childWeight);
              newBetas[i][j * 2 + 1] = betas[i][j] + Math.log(1.0 - childWeight);
            }
          }
          betas = newBetas;
        }
        tempUnaryBetas.put(parent, child, betas);
      }
    }

    for (String parent : binaryBetas.firstKeySet()) {
      for (String left : binaryBetas.get(parent).firstKeySet()) {
        for (String right : binaryBetas.get(parent).get(left).keySet()) {
          double[][][] betas = binaryBetas.get(parent, left, right);
          int parentStates = betas.length;
          int leftStates = betas[0].length;
          int rightStates = betas[0][0].length;

          double[][][] newBetas;
          if (!startSymbols.contains(parent)) {
            newBetas = new double[parentStates * 2][leftStates][rightStates];
            for (int i = 0; i < parentStates; ++i) {
              for (int j = 0; j < leftStates; ++j) {
                for (int k = 0; k < rightStates; ++k) {
                  newBetas[i * 2][j][k] = betas[i][j][k];
                  newBetas[i * 2 + 1][j][k] = betas[i][j][k];
                }
              }
            }
            parentStates *= 2;
            betas = newBetas;
          }

          newBetas = new double[parentStates][leftStates * 2][rightStates];
          for (int i = 0; i < parentStates; ++i) {
            for (int j = 0; j < leftStates; ++j) {
              for (int k = 0; k < rightStates; ++k) {
                double leftWeight = 0.45 + random.nextDouble() * 0.1;
                newBetas[i][j * 2][k] = betas[i][j][k] + Math.log(leftWeight);
                newBetas[i][j * 2 + 1][k] = betas[i][j][k] + Math.log(1 - leftWeight);
              }
            }
          }
          leftStates *= 2;
          betas = newBetas;

          if (!right.equals(Lexicon.BOUNDARY_TAG)) {
            newBetas = new double[parentStates][leftStates][rightStates * 2];
            for (int i = 0; i < parentStates; ++i) {
              for (int j = 0; j < leftStates; ++j) {
                for (int k = 0; k < rightStates; ++k) {
                  double rightWeight = 0.45 + random.nextDouble() * 0.1;
                  newBetas[i][j][k * 2] = betas[i][j][k] + Math.log(rightWeight);
                  newBetas[i][j][k * 2 + 1] = betas[i][j][k] + Math.log(1 - rightWeight);
                }
              }
            }
          }
          tempBinaryBetas.put(parent, left, right, newBetas);
        }
      }
    }
    unaryBetas = tempUnaryBetas;
    binaryBetas = tempBinaryBetas;
  }


  /**
   * Recalculates the betas for all known transitions.  The current
   * betas are used to produce probabilities, which then are used to
   * compute new betas.  If splitStates is true, then the
   * probabilities produced are as if the states were split again from
   * the last time betas were calculated.
   * <br>
   * The return value is whether or not the betas have mostly
   * converged from the last time this method was called.  Obviously
   * if splitStates was true, the betas will be entirely different, so
   * this is false.  Otherwise, the new betas are compared against the
   * old values, and convergence means they differ by less than
   * EPSILON.
   */
  public boolean recalculateBetas(boolean splitStates) {
    if (splitStates) {
      if (DEBUG()) {
        System.out.println("Pre-split betas");
        outputBetas();
      }
      splitBetas();
      if (DEBUG()) {
        System.out.println("Post-split betas");
        outputBetas();
      }
    }

    TwoDimensionalMap<String, String, double[][]> tempUnaryBetas = new TwoDimensionalMap<>();
    ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas = new ThreeDimensionalMap<>();

    recalculateTemporaryBetas(splitStates, null, tempUnaryBetas, tempBinaryBetas);
    boolean converged = useNewBetas(!splitStates, tempUnaryBetas, tempBinaryBetas);

    if (DEBUG()) {
      outputBetas();
    }

    return converged;
  }

  public boolean useNewBetas(boolean testConverged,
                             TwoDimensionalMap<String, String, double[][]> tempUnaryBetas,
                             ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas) {
    rescaleTemporaryBetas(tempUnaryBetas, tempBinaryBetas);

    // if we just split states, we have obviously not converged
    boolean converged = testConverged && testConvergence(tempUnaryBetas, tempBinaryBetas);

    unaryBetas = tempUnaryBetas;
    binaryBetas = tempBinaryBetas;

    wordIndex = tempWordIndex;
    tagIndex = tempTagIndex;
    lex = tempLex;
    if (DEBUG()) {
      System.out.println("LEXICON");
      try {
        OutputStreamWriter osw = new OutputStreamWriter(System.out, "utf-8");
        lex.writeData(osw);
        osw.flush();
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }
    tempWordIndex = null;
    tempTagIndex = null;
    tempLex = null;

    return converged;
  }

  /**
   * Creates temporary beta data structures and fills them in by
   * iterating over the trees.
   */
  public void recalculateTemporaryBetas(boolean splitStates, Map<String, double[]> totalStateMass,
                                        TwoDimensionalMap<String, String, double[][]> tempUnaryBetas,
                                        ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas) {
    tempWordIndex = new HashIndex<>();
    tempTagIndex = new HashIndex<>();
    tempLex = op.tlpParams.lex(op, tempWordIndex, tempTagIndex);
    tempLex.initializeTraining(trainSize);

    for (Tree tree : trees) {
      double weight = treeWeights.getCount(tree);
      if (DEBUG()) {
        System.out.println("Incrementing trees read: " + weight);
      }
      tempLex.incrementTreesRead(weight);
      recalculateTemporaryBetas(tree, splitStates, totalStateMass, tempUnaryBetas, tempBinaryBetas);
    }

    tempLex.finishTraining();
  }

  public boolean testConvergence(TwoDimensionalMap<String, String, double[][]> tempUnaryBetas,
                                 ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas) {

    // now, we check each of the new betas to see if it's close to the
    // old value for the same transition.  if not, we have not yet
    // converged.  if all of them are, we have converged.
    for (String parentLabel : unaryBetas.firstKeySet()) {
      for (String childLabel : unaryBetas.get(parentLabel).keySet()) {
        double[][] betas = unaryBetas.get(parentLabel, childLabel);
        double[][] newBetas = tempUnaryBetas.get(parentLabel, childLabel);
        int parentStates = betas.length;
        int childStates = betas[0].length;
        for (int i = 0; i < parentStates; ++i) {
          for (int j = 0; j < childStates; ++j) {
            double oldValue = betas[i][j];
            double newValue = newBetas[i][j];
            if (Math.abs(newValue - oldValue) > EPSILON) {
              return false;
            }
          }
        }
      }
    }
    for (String parentLabel : binaryBetas.firstKeySet()) {
      for (String leftLabel : binaryBetas.get(parentLabel).firstKeySet()) {
        for (String rightLabel : binaryBetas.get(parentLabel).get(leftLabel).keySet()) {
          double[][][] betas = binaryBetas.get(parentLabel, leftLabel, rightLabel);
          double[][][] newBetas = tempBinaryBetas.get(parentLabel, leftLabel, rightLabel);
          int parentStates = betas.length;
          int leftStates = betas[0].length;
          int rightStates = betas[0][0].length;
          for (int i = 0; i < parentStates; ++i) {
            for (int j = 0; j < leftStates; ++j) {
              for (int k = 0; k < rightStates; ++k) {
                double oldValue = betas[i][j][k];
                double newValue = newBetas[i][j][k];
                if (Math.abs(newValue - oldValue) > EPSILON) {
                  return false;
                }
              }
            }
          }
        }
      }
    }

    return true;
  }

  public void recalculateTemporaryBetas(Tree tree, boolean splitStates,
                                        Map<String, double[]> totalStateMass,
                                        TwoDimensionalMap<String, String, double[][]> tempUnaryBetas,
                                        ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas) {
    if (DEBUG()) {
      System.out.println("Recalculating temporary betas for tree " + tree);
    }
    double[] stateWeights = { Math.log(treeWeights.getCount(tree)) };

    IdentityHashMap<Tree, double[][]> unaryTransitions = new IdentityHashMap<>();
    IdentityHashMap<Tree, double[][][]> binaryTransitions = new IdentityHashMap<>();
    recountTree(tree, splitStates, unaryTransitions, binaryTransitions);

    if (DEBUG()) {
      System.out.println("  Transitions:");
      outputTransitions(tree, unaryTransitions, binaryTransitions);
    }

    recalculateTemporaryBetas(tree, stateWeights, 0, unaryTransitions, binaryTransitions,
                              totalStateMass, tempUnaryBetas, tempBinaryBetas);
  }

  public int recalculateTemporaryBetas(Tree tree, double[] stateWeights, int position,
                                       IdentityHashMap<Tree, double[][]> unaryTransitions,
                                       IdentityHashMap<Tree, double[][][]> binaryTransitions,
                                       Map<String, double[]> totalStateMass,
                                       TwoDimensionalMap<String, String, double[][]> tempUnaryBetas,
                                       ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas) {
    if (tree.isLeaf()) {
      // possible to get here if we have a tree with no structure
      return position;
    }

    if (totalStateMass != null) {
      double[] stateTotal = totalStateMass.get(tree.label().value());
      if (stateTotal == null) {
        stateTotal = new double[stateWeights.length];
        totalStateMass.put(tree.label().value(), stateTotal);
      }
      for (int i = 0; i < stateWeights.length; ++i) {
        stateTotal[i] += Math.exp(stateWeights[i]);
      }
    }

    if (tree.isPreTerminal()) {
      // fill in our new lexicon here.
      String tag = tree.label().value();
      String word = tree.children()[0].label().value();
      // We smooth by LEX_SMOOTH, if relevant.  We rescale so that sum
      // of the weights being added to the lexicon stays the same.
      double total = 0.0;
      for (double stateWeight : stateWeights) {
        total += Math.exp(stateWeight);
      }
      if (total <= 0.0) {
        return position + 1;
      }
      double scale = 1.0 / (1.0 + LEX_SMOOTH);
      double smoothing = total * LEX_SMOOTH / stateWeights.length;
      for (int state = 0; state < stateWeights.length; ++state) {
        // TODO: maybe optimize all this TaggedWord creation
        TaggedWord tw = new TaggedWord(word, state(tag, state));
        tempLex.train(tw, position, (Math.exp(stateWeights[state]) + smoothing) * scale);
      }
      return position + 1;
    }

    if (tree.children().length == 1) {
      String parentLabel = tree.label().value();
      String childLabel = tree.children()[0].label().value();
      double[][] transitions = unaryTransitions.get(tree);
      int parentStates = transitions.length;
      int childStates = transitions[0].length;
      double[][] betas = tempUnaryBetas.get(parentLabel, childLabel);
      if (betas == null) {
        betas = new double[parentStates][childStates];
        for (int i = 0; i < parentStates; ++i) {
          for (int j = 0; j < childStates; ++j) {
            betas[i][j] = Double.NEGATIVE_INFINITY;
          }
        }
        tempUnaryBetas.put(parentLabel, childLabel, betas);
      }
      double[] childWeights = neginfDoubles(childStates);
      for (int i = 0; i < parentStates; ++i) {
        for (int j = 0; j < childStates; ++j) {
          double weight = transitions[i][j];
          betas[i][j] = SloppyMath.logAdd(betas[i][j], weight + stateWeights[i]);
          childWeights[j] = SloppyMath.logAdd(childWeights[j], weight + stateWeights[i]);
        }
      }
      position = recalculateTemporaryBetas(tree.children()[0], childWeights, position, unaryTransitions, binaryTransitions, totalStateMass, tempUnaryBetas, tempBinaryBetas);
    } else { // length == 2
      String parentLabel = tree.label().value();
      String leftLabel = tree.children()[0].label().value();
      String rightLabel = tree.children()[1].label().value();
      double[][][] transitions = binaryTransitions.get(tree);
      int parentStates = transitions.length;
      int leftStates = transitions[0].length;
      int rightStates = transitions[0][0].length;

      double[][][] betas = tempBinaryBetas.get(parentLabel, leftLabel, rightLabel);
      if (betas == null) {
        betas = new double[parentStates][leftStates][rightStates];
        for (int i = 0; i < parentStates; ++i) {
          for (int j = 0; j < leftStates; ++j) {
            for (int k = 0; k < rightStates; ++k) {
              betas[i][j][k] = Double.NEGATIVE_INFINITY;
            }
          }
        }
        tempBinaryBetas.put(parentLabel, leftLabel, rightLabel, betas);
      }
      double[] leftWeights = neginfDoubles(leftStates);
      double[] rightWeights = neginfDoubles(rightStates);
      for (int i = 0; i < parentStates; ++i) {
        for (int j = 0; j < leftStates; ++j) {
          for (int k = 0; k < rightStates; ++k) {
            double weight = transitions[i][j][k];
            betas[i][j][k] = SloppyMath.logAdd(betas[i][j][k], weight + stateWeights[i]);
            leftWeights[j] = SloppyMath.logAdd(leftWeights[j], weight + stateWeights[i]);
            rightWeights[k] = SloppyMath.logAdd(rightWeights[k], weight + stateWeights[i]);
          }
        }
      }
      position = recalculateTemporaryBetas(tree.children()[0], leftWeights, position, unaryTransitions, binaryTransitions, totalStateMass, tempUnaryBetas, tempBinaryBetas);
      position = recalculateTemporaryBetas(tree.children()[1], rightWeights, position, unaryTransitions, binaryTransitions, totalStateMass, tempUnaryBetas, tempBinaryBetas);
    }
    return position;
  }

  public void rescaleTemporaryBetas(TwoDimensionalMap<String, String, double[][]> tempUnaryBetas,
                                    ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas) {
    for (String parent : tempUnaryBetas.firstKeySet()) {
      for (String child : tempUnaryBetas.get(parent).keySet()) {
        double[][] betas = tempUnaryBetas.get(parent).get(child);
        int parentStates = betas.length;
        int childStates = betas[0].length;
        for (int i = 0; i < parentStates; ++i) {
          double sum = Double.NEGATIVE_INFINITY;
          for (int j = 0; j < childStates; ++j) {
            sum = SloppyMath.logAdd(sum, betas[i][j]);
          }
          if (Double.isInfinite(sum)) {
            for (int j = 0; j < childStates; ++j) {
              betas[i][j] = -Math.log(childStates);
            }
          } else {
            for (int j = 0; j < childStates; ++j) {
              betas[i][j] -= sum;
            }
          }
        }
      }
    }

    for (String parent : tempBinaryBetas.firstKeySet()) {
      for (String left : tempBinaryBetas.get(parent).firstKeySet()) {
        for (String right : tempBinaryBetas.get(parent).get(left).keySet()) {
          double[][][] betas = tempBinaryBetas.get(parent).get(left).get(right);
          int parentStates = betas.length;
          int leftStates = betas[0].length;
          int rightStates = betas[0][0].length;
          for (int i = 0; i < parentStates; ++i) {
            double sum = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < leftStates; ++j) {
              for (int k = 0; k < rightStates; ++k) {
                sum = SloppyMath.logAdd(sum, betas[i][j][k]);
              }
            }
            if (Double.isInfinite(sum)) {
              for (int j = 0; j < leftStates; ++j) {
                for (int k = 0; k < rightStates; ++k) {
                  betas[i][j][k] = -Math.log(leftStates * rightStates);
                }
              }
            } else {
              for (int j = 0; j < leftStates; ++j) {
                for (int k = 0; k < rightStates; ++k) {
                  betas[i][j][k] -= sum;
                }
              }
            }
          }
        }
      }
    }
  }

  public void recountTree(Tree tree, boolean splitStates,
                          IdentityHashMap<Tree, double[][]> unaryTransitions,
                          IdentityHashMap<Tree, double[][][]> binaryTransitions) {
    IdentityHashMap<Tree, double[]> probIn = new IdentityHashMap<>();
    IdentityHashMap<Tree, double[]> probOut = new IdentityHashMap<>();
    recountTree(tree, splitStates, probIn, probOut, unaryTransitions, binaryTransitions);
  }

  public void recountTree(Tree tree, boolean splitStates,
                          IdentityHashMap<Tree, double[]> probIn,
                          IdentityHashMap<Tree, double[]> probOut,
                          IdentityHashMap<Tree, double[][]> unaryTransitions,
                          IdentityHashMap<Tree, double[][][]> binaryTransitions) {
    recountInside(tree, splitStates, 0, probIn);
    if (DEBUG()) {
      System.out.println("ROOT PROBABILITY: " + probIn.get(tree)[0]);
    }
    recountOutside(tree, probIn, probOut);
    recountWeights(tree, probIn, probOut, unaryTransitions, binaryTransitions);
  }

  public void recountWeights(Tree tree,
                             IdentityHashMap<Tree, double[]> probIn,
                             IdentityHashMap<Tree, double[]> probOut,
                             IdentityHashMap<Tree, double[][]> unaryTransitions,
                             IdentityHashMap<Tree, double[][][]> binaryTransitions) {
    if (tree.isLeaf() || tree.isPreTerminal()) {
      return;
    }
    if (tree.children().length == 1) {
      Tree child = tree.children()[0];
      String parentLabel = tree.label().value();
      String childLabel = child.label().value();
      double[][] betas = unaryBetas.get(parentLabel, childLabel);
      double[] childInside = probIn.get(child);
      double[] parentOutside = probOut.get(tree);
      int parentStates = betas.length;
      int childStates = betas[0].length;
      double[][] transitions = new double[parentStates][childStates];
      unaryTransitions.put(tree, transitions);
      for (int i = 0; i < parentStates; ++i) {
        for (int j = 0; j < childStates; ++j) {
          transitions[i][j] = parentOutside[i] + childInside[j] + betas[i][j];
        }
      }
      // Renormalize.  Note that we renormalize to 1, regardless of
      // the original total.
      // TODO: smoothing?
      for (int i = 0; i < parentStates; ++i) {
        double total = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < childStates; ++j) {
          total = SloppyMath.logAdd(total, transitions[i][j]);
        }
        // By subtracting off the log total, we make it so the log sum
        // of the transitions is 0, meaning the sum of the actual
        // transitions is 1.  It works if you do the math...
        if (Double.isInfinite(total)) {
          double transition = -Math.log(childStates);
          for (int j = 0; j < childStates; ++j) {
            transitions[i][j] = transition;
          }
        } else {
          for (int j = 0; j < childStates; ++j) {
            transitions[i][j] = transitions[i][j] - total;
          }
        }
      }
      recountWeights(child, probIn, probOut, unaryTransitions, binaryTransitions);
    } else { // length == 2
      Tree left = tree.children()[0];
      Tree right = tree.children()[1];
      String parentLabel = tree.label().value();
      String leftLabel = left.label().value();
      String rightLabel = right.label().value();
      double[][][] betas = binaryBetas.get(parentLabel, leftLabel, rightLabel);
      double[] leftInside = probIn.get(left);
      double[] rightInside = probIn.get(right);
      double[] parentOutside = probOut.get(tree);
      int parentStates = betas.length;
      int leftStates = betas[0].length;
      int rightStates = betas[0][0].length;
      double[][][] transitions = new double[parentStates][leftStates][rightStates];
      binaryTransitions.put(tree, transitions);
      for (int i = 0; i < parentStates; ++i) {
        for (int j = 0; j < leftStates; ++j) {
          for (int k = 0; k < rightStates; ++k) {
            transitions[i][j][k] = parentOutside[i] + leftInside[j] + rightInside[k] + betas[i][j][k];
          }
        }
      }
      // Renormalize.  Note that we renormalize to 1, regardless of
      // the original total.
      // TODO: smoothing?
      for (int i = 0; i < parentStates; ++i) {
        double total = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < leftStates; ++j) {
          for (int k = 0; k < rightStates; ++k) {
            total = SloppyMath.logAdd(total, transitions[i][j][k]);
          }
        }
        // By subtracting off the log total, we make it so the log sum
        // of the transitions is 0, meaning the sum of the actual
        // transitions is 1.  It works if you do the math...
        if (Double.isInfinite(total)) {
          double transition = -Math.log(leftStates * rightStates);
          for (int j = 0; j < leftStates; ++j) {
            for (int k = 0; k < rightStates; ++k) {
              transitions[i][j][k] = transition;
            }
          }
        } else {
          for (int j = 0; j < leftStates; ++j) {
            for (int k = 0; k < rightStates; ++k) {
              transitions[i][j][k] = transitions[i][j][k] - total;
            }
          }
        }
      }
      recountWeights(left, probIn, probOut, unaryTransitions, binaryTransitions);
      recountWeights(right, probIn, probOut, unaryTransitions, binaryTransitions);
    }
  }

  public void recountOutside(Tree tree,
                             IdentityHashMap<Tree, double[]> probIn,
                             IdentityHashMap<Tree, double[]> probOut) {
    double[] rootScores = { 0.0 };
    probOut.put(tree, rootScores);
    recurseOutside(tree, probIn, probOut);
  }

  public void recurseOutside(Tree tree,
                             IdentityHashMap<Tree, double[]> probIn,
                             IdentityHashMap<Tree, double[]> probOut) {
    if (tree.isLeaf() || tree.isPreTerminal()) {
      return;
    }
    if (tree.children().length == 1) {
      recountOutside(tree.children()[0], tree, probIn, probOut);
    } else { // length == 2
      recountOutside(tree.children()[0], tree.children()[1], tree,
                     probIn, probOut);
    }
  }

  public void recountOutside(Tree child, Tree parent,
                             IdentityHashMap<Tree, double[]> probIn,
                             IdentityHashMap<Tree, double[]> probOut) {
    String parentLabel = parent.label().value();
    String childLabel = child.label().value();
    double[] parentScores = probOut.get(parent);
    double[][] betas = unaryBetas.get(parentLabel, childLabel);
    int parentStates = betas.length;
    int childStates = betas[0].length;

    double[] scores = neginfDoubles(childStates);
    probOut.put(child, scores);

    for (int i = 0; i < parentStates; ++i) {
      for (int j = 0; j < childStates; ++j) {
        // TODO: no inside scores here, right?
        scores[j] = SloppyMath.logAdd(scores[j], betas[i][j] + parentScores[i]);
      }
    }

    recurseOutside(child, probIn, probOut);
  }

  public void recountOutside(Tree left, Tree right, Tree parent,
                             IdentityHashMap<Tree, double[]> probIn,
                             IdentityHashMap<Tree, double[]> probOut) {
    String parentLabel = parent.label().value();
    String leftLabel = left.label().value();
    String rightLabel = right.label().value();
    double[] leftInsideScores = probIn.get(left);
    double[] rightInsideScores = probIn.get(right);
    double[] parentScores = probOut.get(parent);
    double[][][] betas = binaryBetas.get(parentLabel, leftLabel, rightLabel);
    int parentStates = betas.length;
    int leftStates = betas[0].length;
    int rightStates = betas[0][0].length;

    double[] leftScores = neginfDoubles(leftStates);
    probOut.put(left, leftScores);
    double[] rightScores = neginfDoubles(rightStates);
    probOut.put(right, rightScores);

    for (int i = 0; i < parentStates; ++i) {
      for (int j = 0; j < leftStates; ++j) {
        for (int k = 0; k < rightStates; ++k) {
          leftScores[j] = SloppyMath.logAdd(leftScores[j], betas[i][j][k] + parentScores[i] + rightInsideScores[k]);
          rightScores[k] = SloppyMath.logAdd(rightScores[k], betas[i][j][k] + parentScores[i] + leftInsideScores[j]);
        }
      }
    }

    recurseOutside(left, probIn, probOut);
    recurseOutside(right, probIn, probOut);
  }

  public int recountInside(Tree tree, boolean splitStates, int loc,
                           IdentityHashMap<Tree, double[]> probIn) {
    if (tree.isLeaf()) {
      throw new RuntimeException();
    } else if (tree.isPreTerminal()) {
      int stateCount = getStateSplitCount(tree);
      String word = tree.children()[0].label().value();
      String tag = tree.label().value();

      double[] scores = new double[stateCount];
      probIn.put(tree, scores);

      if (splitStates && !tag.equals(Lexicon.BOUNDARY_TAG)) {
        for (int i = 0; i < stateCount / 2; ++i) {
          IntTaggedWord tw = new IntTaggedWord(word, state(tag, i), wordIndex, tagIndex);
          double logProb = lex.score(tw, loc, word, null);
          double wordWeight = 0.45 + random.nextDouble() * 0.1;
          scores[i * 2] = logProb + Math.log(wordWeight);
          scores[i * 2 + 1] = logProb + Math.log(1.0 - wordWeight);
          if (DEBUG()) {
            System.out.println("Lexicon log prob " + state(tag, i) + "-" + word + ": " + logProb);
            System.out.println("  Log Split -> " + scores[i * 2] + "," + scores[i * 2 + 1]);
          }
        }
      } else {
        for (int i = 0; i < stateCount; ++i) {
          IntTaggedWord tw = new IntTaggedWord(word, state(tag, i), wordIndex, tagIndex);
          double prob = lex.score(tw, loc, word, null);
          if (DEBUG()) {
            System.out.println("Lexicon log prob " + state(tag, i) + "-" + word + ": " + prob);
          }
          scores[i] = prob;
        }
      }
      loc = loc + 1;
    } else if (tree.children().length == 1) {
      loc = recountInside(tree.children()[0], splitStates, loc, probIn);
      double[] childScores = probIn.get(tree.children()[0]);
      String parentLabel = tree.label().value();
      String childLabel = tree.children()[0].label().value();
      double[][] betas = unaryBetas.get(parentLabel, childLabel);
      int parentStates = betas.length; // size of the first key
      int childStates = betas[0].length;

      double[] scores = neginfDoubles(parentStates);
      probIn.put(tree, scores);

      for (int i = 0; i < parentStates; ++i) {
        for (int j = 0; j < childStates; ++j) {
          scores[i] = SloppyMath.logAdd(scores[i], childScores[j] + betas[i][j]);
        }
      }
      if (DEBUG()) {
        System.out.println(parentLabel + " -> " + childLabel);
        for (int i = 0; i < parentStates; ++i) {
          System.out.println("  " + i + ":" + scores[i]);
          for (int j = 0; j < childStates; ++j) {
            System.out.println("    " + i + "," + j + ": " + betas[i][j] + " | " + Math.exp(betas[i][j]));
          }
        }
      }
    } else { // length == 2
      loc = recountInside(tree.children()[0], splitStates, loc, probIn);
      loc = recountInside(tree.children()[1], splitStates, loc, probIn);
      double[] leftScores = probIn.get(tree.children()[0]);
      double[] rightScores = probIn.get(tree.children()[1]);
      String parentLabel = tree.label().value();
      String leftLabel = tree.children()[0].label().value();
      String rightLabel = tree.children()[1].label().value();
      double[][][] betas = binaryBetas.get(parentLabel, leftLabel, rightLabel);
      int parentStates = betas.length;
      int leftStates = betas[0].length;
      int rightStates = betas[0][0].length;

      double[] scores = neginfDoubles(parentStates);
      probIn.put(tree, scores);

      for (int i = 0; i < parentStates; ++i) {
        for (int j = 0; j < leftStates; ++j) {
          for (int k = 0; k < rightStates; ++k) {
            scores[i] = SloppyMath.logAdd(scores[i], leftScores[j] + rightScores[k] + betas[i][j][k]);
          }
        }
      }
      if (DEBUG()) {
        System.out.println(parentLabel + " -> " + leftLabel + "," + rightLabel);
        for (int i = 0; i < parentStates; ++i) {
          System.out.println("  " + i + ":" + scores[i]);
          for (int j = 0; j < leftStates; ++j) {
            for (int k = 0; k < rightStates; ++k) {
              System.out.println("    " + i + "," + j + "," + k + ": " + betas[i][j][k] + " | " + Math.exp(betas[i][j][k]));
            }
          }
        }
      }
    }
    return loc;
  }

  public void mergeStates() {
    if (op.trainOptions.splitRecombineRate <= 0.0) {
      return;
    }

    // we go through the machinery to sum up the temporary betas,
    // counting the total mass
    TwoDimensionalMap<String, String, double[][]> tempUnaryBetas = new TwoDimensionalMap<>();
    ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas = new ThreeDimensionalMap<>();
    Map<String, double[]> totalStateMass = Generics.newHashMap();
    recalculateTemporaryBetas(false, totalStateMass, tempUnaryBetas, tempBinaryBetas);

    // Next, for each tree we count the effect of merging its
    // annotations.  We only consider the most recently split
    // annotations as candidates for merging.
    Map<String, double[]> deltaAnnotations = Generics.newHashMap();
    for (Tree tree : trees) {
      countMergeEffects(tree, totalStateMass, deltaAnnotations);
    }

    // Now we have a map of the (approximate) likelihood loss from
    // merging each state.  We merge the ones that provide the least
    // benefit, up to the splitRecombineRate
    List<Triple<String, Integer, Double>> sortedDeltas =
            new ArrayList<>();
    for (String state : deltaAnnotations.keySet()) {
      double[] scores = deltaAnnotations.get(state);
      for (int i = 0; i < scores.length; ++i) {
        sortedDeltas.add(new Triple<>(state, i * 2, scores[i]));
      }
    }
    Collections.sort(sortedDeltas, new Comparator<Triple<String, Integer, Double>>() {
        public int compare(Triple<String, Integer, Double> first,
                           Triple<String, Integer, Double> second) {
          // The most useful splits will have a large loss in
          // likelihood if they are merged.  Thus, we want those at
          // the end of the list.  This means we make the comparison
          // "backwards", sorting from high to low.
          return Double.compare(second.third(), first.third());
        }
        public boolean equals(Object o) { return o == this; }
      });

    // for (Triple<String, Integer, Double> delta : sortedDeltas) {
    //   System.out.println(delta.first() + "-" + delta.second() + ": " + delta.third());
    // }
    // System.out.println("-------------");

    // Only merge a fraction of the splits based on what the user
    // originally asked for
    int splitsToMerge = (int) (sortedDeltas.size() * op.trainOptions.splitRecombineRate);
    splitsToMerge = Math.max(0, splitsToMerge);
    splitsToMerge = Math.min(sortedDeltas.size() - 1, splitsToMerge);
    sortedDeltas = sortedDeltas.subList(0, splitsToMerge);

    System.out.println();
    System.out.println(sortedDeltas);

    Map<String, int[]> mergeCorrespondence = buildMergeCorrespondence(sortedDeltas);

    recalculateMergedBetas(mergeCorrespondence);

    for (Triple<String, Integer, Double> delta : sortedDeltas) {
      stateSplitCounts.decrementCount(delta.first(), 1);
    }
  }

  public void recalculateMergedBetas(Map<String, int[]> mergeCorrespondence) {
    TwoDimensionalMap<String, String, double[][]> tempUnaryBetas = new TwoDimensionalMap<>();
    ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas = new ThreeDimensionalMap<>();

    tempWordIndex = new HashIndex<>();
    tempTagIndex = new HashIndex<>();
    tempLex = op.tlpParams.lex(op, tempWordIndex, tempTagIndex);
    tempLex.initializeTraining(trainSize);

    for (Tree tree : trees) {
      double treeWeight = treeWeights.getCount(tree);
      double[] stateWeights = { Math.log(treeWeight) };
      tempLex.incrementTreesRead(treeWeight);

      IdentityHashMap<Tree, double[][]> oldUnaryTransitions = new IdentityHashMap<>();
      IdentityHashMap<Tree, double[][][]> oldBinaryTransitions = new IdentityHashMap<>();
      recountTree(tree, false, oldUnaryTransitions, oldBinaryTransitions);

      IdentityHashMap<Tree, double[][]> unaryTransitions = new IdentityHashMap<>();
      IdentityHashMap<Tree, double[][][]> binaryTransitions = new IdentityHashMap<>();
      mergeTransitions(tree, oldUnaryTransitions, oldBinaryTransitions, unaryTransitions, binaryTransitions, stateWeights, mergeCorrespondence);

      recalculateTemporaryBetas(tree, stateWeights, 0, unaryTransitions, binaryTransitions,
                                null, tempUnaryBetas, tempBinaryBetas);
    }

    tempLex.finishTraining();
    useNewBetas(false, tempUnaryBetas, tempBinaryBetas);
  }

  /**
   * Given a tree and the original set of transition probabilities
   * from one state to the next in the tree, along with a list of the
   * weights in the tree and a count of the mass in each substate at
   * the current node, this method merges the probabilities as
   * necessary.  The results go into newUnaryTransitions and
   * newBinaryTransitions.
   */
  public void mergeTransitions(Tree parent,
                               IdentityHashMap<Tree, double[][]> oldUnaryTransitions,
                               IdentityHashMap<Tree, double[][][]> oldBinaryTransitions,
                               IdentityHashMap<Tree, double[][]> newUnaryTransitions,
                               IdentityHashMap<Tree, double[][][]> newBinaryTransitions,
                               double[] stateWeights,
                               Map<String, int[]> mergeCorrespondence) {
    if (parent.isPreTerminal() || parent.isLeaf()) {
      return;
    }
    if (parent.children().length == 1) {
      double[][] oldTransitions = oldUnaryTransitions.get(parent);

      String parentLabel = parent.label().value();
      int[] parentCorrespondence = mergeCorrespondence.get(parentLabel);
      int parentStates = parentCorrespondence[parentCorrespondence.length - 1] + 1;

      String childLabel = parent.children()[0].label().value();
      int[] childCorrespondence = mergeCorrespondence.get(childLabel);
      int childStates = childCorrespondence[childCorrespondence.length - 1] + 1;

      // System.out.println("P: " + parentLabel + " " + parentStates +
      //                    " C: " + childLabel + " " + childStates);


      // Add up the probabilities of transitioning to each state,
      // scaled by the probability of being in a given state to begin
      // with.  This accounts for when two states in the parent are
      // collapsed into one state.
      double[][] newTransitions = new double[parentStates][childStates];
      for (int i = 0; i < parentStates; ++i) {
        for (int j = 0; j < childStates; ++j) {
          newTransitions[i][j] = Double.NEGATIVE_INFINITY;
        }
      }
      newUnaryTransitions.put(parent, newTransitions);
      for (int i = 0; i < oldTransitions.length; ++i) {
        int ti = parentCorrespondence[i];
        for (int j = 0; j < oldTransitions[0].length; ++j) {
          int tj = childCorrespondence[j];
          // System.out.println(i + " " + ti + " " + j + " " + tj);
          newTransitions[ti][tj] = SloppyMath.logAdd(newTransitions[ti][tj], oldTransitions[i][j] + stateWeights[i]);
        }
      }

      // renormalize
      for (int i = 0; i < parentStates; ++i) {
        double total = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < childStates; ++j) {
          total = SloppyMath.logAdd(total, newTransitions[i][j]);
        }
        if (Double.isInfinite(total)) {
          for (int j = 0; j < childStates; ++j) {
            newTransitions[i][j] = -Math.log(childStates);
          }
        } else {
          for (int j = 0; j < childStates; ++j) {
            newTransitions[i][j] -= total;
          }
        }
      }

      double[] childWeights = neginfDoubles(oldTransitions[0].length);
      for (int i = 0; i < oldTransitions.length; ++i) {
        for (int j = 0; j < oldTransitions[0].length; ++j) {
          double weight = oldTransitions[i][j];
          childWeights[j] = SloppyMath.logAdd(childWeights[j], weight + stateWeights[i]);
        }
      }

      mergeTransitions(parent.children()[0], oldUnaryTransitions, oldBinaryTransitions, newUnaryTransitions, newBinaryTransitions, childWeights, mergeCorrespondence);
    } else {
      double[][][] oldTransitions = oldBinaryTransitions.get(parent);

      String parentLabel = parent.label().value();
      int[] parentCorrespondence = mergeCorrespondence.get(parentLabel);
      int parentStates = parentCorrespondence[parentCorrespondence.length - 1] + 1;

      String leftLabel = parent.children()[0].label().value();
      int[] leftCorrespondence = mergeCorrespondence.get(leftLabel);
      int leftStates = leftCorrespondence[leftCorrespondence.length - 1] + 1;

      String rightLabel = parent.children()[1].label().value();
      int[] rightCorrespondence = mergeCorrespondence.get(rightLabel);
      int rightStates = rightCorrespondence[rightCorrespondence.length - 1] + 1;

      // System.out.println("P: " + parentLabel + " " + parentStates +
      //                    " L: " + leftLabel + " " + leftStates +
      //                    " R: " + rightLabel + " " + rightStates);

      double[][][] newTransitions = new double[parentStates][leftStates][rightStates];
      for (int i = 0; i < parentStates; ++i) {
        for (int j = 0; j < leftStates; ++j) {
          for (int k = 0; k < rightStates; ++k) {
            newTransitions[i][j][k] = Double.NEGATIVE_INFINITY;
          }
        }
      }
      newBinaryTransitions.put(parent, newTransitions);
      for (int i = 0; i < oldTransitions.length; ++i) {
        int ti = parentCorrespondence[i];
        for (int j = 0; j < oldTransitions[0].length; ++j) {
          int tj = leftCorrespondence[j];
          for (int k = 0; k < oldTransitions[0][0].length; ++k) {
            int tk = rightCorrespondence[k];
            // System.out.println(i + " " + ti + " " + j + " " + tj + " " + k + " " + tk);
            newTransitions[ti][tj][tk] = SloppyMath.logAdd(newTransitions[ti][tj][tk], oldTransitions[i][j][k] + stateWeights[i]);
          }
        }
      }

      // renormalize
      for (int i = 0; i < parentStates; ++i) {
        double total = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < leftStates; ++j) {
          for (int k = 0; k < rightStates; ++k) {
            total = SloppyMath.logAdd(total, newTransitions[i][j][k]);
          }
        }
        if (Double.isInfinite(total)) {
          for (int j = 0; j < leftStates; ++j) {
            for (int k = 0; k < rightStates; ++k) {
              newTransitions[i][j][k] = -Math.log(leftStates * rightStates);
            }
          }
        } else {
          for (int j = 0; j < leftStates; ++j) {
            for (int k = 0; k < rightStates; ++k) {
              newTransitions[i][j][k] -= total;
            }
          }
        }
      }

      double[] leftWeights = neginfDoubles(oldTransitions[0].length);
      double[] rightWeights = neginfDoubles(oldTransitions[0][0].length);
      for (int i = 0; i < oldTransitions.length; ++i) {
        for (int j = 0; j < oldTransitions[0].length; ++j) {
          for (int k = 0; k < oldTransitions[0][0].length; ++k) {
            double weight = oldTransitions[i][j][k];
            leftWeights[j] = SloppyMath.logAdd(leftWeights[j], weight + stateWeights[i]);
            rightWeights[k] = SloppyMath.logAdd(rightWeights[k], weight + stateWeights[i]);
          }
        }
      }

      mergeTransitions(parent.children()[0], oldUnaryTransitions, oldBinaryTransitions, newUnaryTransitions, newBinaryTransitions, leftWeights, mergeCorrespondence);
      mergeTransitions(parent.children()[1], oldUnaryTransitions, oldBinaryTransitions, newUnaryTransitions, newBinaryTransitions, rightWeights, mergeCorrespondence);
    }
  }

  Map<String, int[]> buildMergeCorrespondence(List<Triple<String, Integer, Double>> deltas) {
    Map<String, int[]> mergeCorrespondence = Generics.newHashMap();
    for (String state : originalStates) {
      int states = getStateSplitCount(state);
      int[] correspondence = new int[states];
      for (int i = 0; i < states; ++i) {
        correspondence[i] = i;
      }
      mergeCorrespondence.put(state, correspondence);
    }
    for (Triple<String, Integer, Double> merge : deltas) {
      int states = getStateSplitCount(merge.first());
      int split = merge.second();
      int[] correspondence = mergeCorrespondence.get(merge.first());
      for (int i = split + 1; i < states; ++i) {
        correspondence[i] = correspondence[i] - 1;
      }
    }
    return mergeCorrespondence;
  }

  public void countMergeEffects(Tree tree, Map<String, double[]> totalStateMass,
                                Map<String, double[]> deltaAnnotations) {
    IdentityHashMap<Tree, double[]> probIn = new IdentityHashMap<>();
    IdentityHashMap<Tree, double[]> probOut = new IdentityHashMap<>();
    IdentityHashMap<Tree, double[][]> unaryTransitions = new IdentityHashMap<>();
    IdentityHashMap<Tree, double[][][]> binaryTransitions = new IdentityHashMap<>();
    recountTree(tree, false, probIn, probOut, unaryTransitions, binaryTransitions);

    // no need to count the root
    for (Tree child : tree.children()) {
      countMergeEffects(child, totalStateMass, deltaAnnotations, probIn, probOut);
    }
  }

  public void countMergeEffects(Tree tree, Map<String, double[]> totalStateMass,
                                Map<String, double[]> deltaAnnotations,
                                IdentityHashMap<Tree, double[]> probIn,
                                IdentityHashMap<Tree, double[]> probOut) {
    if (tree.isLeaf()) {
      return;
    }
    if (tree.label().value().equals(Lexicon.BOUNDARY_TAG)) {
      return;
    }

    String label = tree.label().value();
    double totalMass = 0.0;
    double[] stateMass = totalStateMass.get(label);
    for (double mass : stateMass) {
      totalMass += mass;
    }

    double[] nodeProbIn = probIn.get(tree);
    double[] nodeProbOut = probOut.get(tree);

    double[] nodeDelta = deltaAnnotations.get(label);
    if (nodeDelta == null) {
      nodeDelta = new double[nodeProbIn.length / 2];
      deltaAnnotations.put(label, nodeDelta);
    }

    for (int i = 0; i < nodeProbIn.length / 2; ++i) {
      double probInMerged = SloppyMath.logAdd(Math.log(stateMass[i * 2] / totalMass) + nodeProbIn[i * 2],
                                              Math.log(stateMass[i * 2 + 1] / totalMass) + nodeProbIn[i * 2 + 1]);
      double probOutMerged = SloppyMath.logAdd(nodeProbOut[i * 2], nodeProbOut[i * 2 + 1]);
      double probMerged = probInMerged + probOutMerged;
      double probUnmerged = SloppyMath.logAdd(nodeProbIn[i * 2] + nodeProbOut[i * 2],
                                              nodeProbIn[i * 2 + 1] + nodeProbOut[i * 2 + 1]);
      nodeDelta[i] = nodeDelta[i] + probMerged - probUnmerged;
    }

    if (tree.isPreTerminal()) {
      return;
    }
    for (Tree child : tree.children()) {
      countMergeEffects(child, totalStateMass, deltaAnnotations, probIn, probOut);
    }
  }

  public void buildStateIndex() {
    stateIndex = new HashIndex<>();
    for (String key : stateSplitCounts.keySet()) {
      for (int i = 0; i < stateSplitCounts.getIntCount(key); ++i) {
        stateIndex.addToIndex(state(key, i));
      }
    }
  }

  public void buildGrammars() {
    // In order to build the grammars, we first need to fill in the
    // temp betas with the sums of the transitions from Ax to By or Ax
    // to By,Cz.  We also need the sum total of the mass in each state
    // Ax over all the trees.

    // we go through the machinery to sum up the temporary betas,
    // counting the total mass...
    TwoDimensionalMap<String, String, double[][]> tempUnaryBetas = new TwoDimensionalMap<>();
    ThreeDimensionalMap<String, String, String, double[][][]> tempBinaryBetas = new ThreeDimensionalMap<>();
    Map<String, double[]> totalStateMass = Generics.newHashMap();
    recalculateTemporaryBetas(false, totalStateMass, tempUnaryBetas, tempBinaryBetas);

    // ... but note we don't actually rescale the betas.
    // instead we use the temporary betas and the total mass in each
    // state to calculate the grammars

    // First build up a BinaryGrammar.
    // The score for each rule will be the Beta scores found earlier,
    // scaled by the total weight of a transition between unsplit states
    BinaryGrammar bg = new BinaryGrammar(stateIndex);
    for (String parent : tempBinaryBetas.firstKeySet()) {
      int parentStates = getStateSplitCount(parent);
      double[] stateTotal = totalStateMass.get(parent);
      for (String left : tempBinaryBetas.get(parent).firstKeySet()) {
        int leftStates = getStateSplitCount(left);
        for (String right : tempBinaryBetas.get(parent).get(left).keySet()) {
          int rightStates = getStateSplitCount(right);
          double[][][] betas = tempBinaryBetas.get(parent, left, right);
          for (int i = 0; i < parentStates; ++i) {
            if (stateTotal[i] < EPSILON) {
              continue;
            }
            for (int j = 0; j < leftStates; ++j) {
              for (int k = 0; k < rightStates; ++k) {
                int parentIndex = stateIndex.indexOf(state(parent, i));
                int leftIndex = stateIndex.indexOf(state(left, j));
                int rightIndex = stateIndex.indexOf(state(right, k));
                double score = betas[i][j][k] - Math.log(stateTotal[i]);
                BinaryRule br = new BinaryRule(parentIndex, leftIndex, rightIndex, score);
                bg.addRule(br);
              }
            }
          }
        }
      }
    }

    // Now build up a UnaryGrammar
    UnaryGrammar ug = new UnaryGrammar(stateIndex);
    for (String parent : tempUnaryBetas.firstKeySet()) {
      int parentStates = getStateSplitCount(parent);
      double[] stateTotal = totalStateMass.get(parent);
      for (String child : tempUnaryBetas.get(parent).keySet()) {
        int childStates = getStateSplitCount(child);
        double[][] betas = tempUnaryBetas.get(parent, child);
        for (int i = 0; i < parentStates; ++i) {
          if (stateTotal[i] < EPSILON) {
            continue;
          }
          for (int j = 0; j < childStates; ++j) {
            int parentIndex = stateIndex.indexOf(state(parent, i));
            int childIndex = stateIndex.indexOf(state(child, j));
            double score = betas[i][j] - Math.log(stateTotal[i]);
            UnaryRule ur = new UnaryRule(parentIndex, childIndex, score);
            ug.addRule(ur);
          }
        }
      }
    }


    bgug = new Pair<>(ug, bg);
  }

  public void saveTrees(Collection<Tree> trees1, double weight1,
                        Collection<Tree> trees2, double weight2) {
    trainSize = 0.0;
    int treeCount = 0;
    trees.clear();
    treeWeights.clear();
    for (Tree tree : trees1) {
      trees.add(tree);
      treeWeights.incrementCount(tree, weight1);
      trainSize += weight1;
    }
    treeCount += trees1.size();
    if (trees2 != null && weight2 >= 0.0) {
      for (Tree tree : trees2) {
        trees.add(tree);
        treeWeights.incrementCount(tree, weight2);
        trainSize += weight2;
      }
      treeCount += trees2.size();
    }
    log.info("Found " + treeCount +
                       " trees with total weight " + trainSize);
  }

  public void extract(Collection<Tree> treeList) {
    extract(treeList, 1.0, null, 0.0);
  }

  /**
   * First, we do a few setup steps.  We read in all the trees, which
   * is necessary because we continually reprocess them and use the
   * object pointers as hash keys rather than hashing the trees
   * themselves.  We then count the initial states in the treebank.
   * <br>
   * Having done that, we then assign initial probabilities to the
   * trees.  At first, each state has 1.0 of the probability mass for
   * each Ax-ByCz and Ax-By transition.  We then split the number of
   * states and the probabilities on each tree.
   * <br>
   * We then repeatedly recalculate the betas and reannotate the
   * weights, going until we converge, which is defined as no betas
   * move more then epsilon.
   * <br>
   * java -mx4g edu.stanford.nlp.parser.lexparser.LexicalizedParser  -PCFG -saveToSerializedFile englishSplit.ser.gz -saveToTextFile englishSplit.txt -maxLength 40 -train ../data/wsj/wsjtwentytrees.mrg    -testTreebank ../data/wsj/wsjtwentytrees.mrg   -evals "factDA,tsv" -uwm 0  -hMarkov 0 -vMarkov 0 -simpleBinarizedLabels -noRebinarization -predictSplits -splitTrainingThreads 1 -splitCount 1 -splitRecombineRate 0.5
   * <br>
   * may also need
   * <br>
   *  -smoothTagsThresh 0
   * <br>
   * java -mx8g edu.stanford.nlp.parser.lexparser.LexicalizedParser -evals "factDA,tsv" -PCFG -vMarkov 0 -hMarkov 0 -uwm 0 -saveToSerializedFile wsjS1.ser.gz -maxLength 40 -train /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 200-2199 -testTreebank /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj 2200-2219 -compactGrammar 0 -simpleBinarizedLabels -predictSplits -smoothTagsThresh 0 -splitCount 1 -noRebinarization
   */
  public void extract(Collection<Tree> trees1, double weight1,
                      Collection<Tree> trees2, double weight2) {
    saveTrees(trees1, weight1, trees2, weight2);

    countOriginalStates();

    // Initial betas will be 1 for all possible unary and binary
    // transitions in our treebank
    initialBetasAndLexicon();

    for (int cycle = 0; cycle < op.trainOptions.splitCount; ++cycle) {
      // All states except the root state get split into 2
      splitStateCounts();

      // first, recalculate the betas and the lexicon for having split
      // the transitions
      recalculateBetas(true);

      // now, loop until we converge while recalculating betas
      // TODO: add a loop counter, stop after X iterations
      iteration = 0;
      boolean converged = false;
      while (!converged && iteration < MAX_ITERATIONS) {
        if (DEBUG()) {
          System.out.println();
          System.out.println();
          System.out.println("-------------------");
          System.out.println("Iteration " + iteration);
        }

        converged = recalculateBetas(false);
        ++iteration;
      }

      log.info("Converged for cycle " + cycle +
                         " in " + iteration + " iterations");

      mergeStates();
    }

    // Build up the state index.  The BG & UG both expect a set count
    // of states.
    buildStateIndex();

    buildGrammars();
  }
}
