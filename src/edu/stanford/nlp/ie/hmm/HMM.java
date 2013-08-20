package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.AnswerChecker;
import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.ie.TypedTaggedDocument;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.TypedTaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.math.SloppyMath;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.process.Feature;
import edu.stanford.nlp.process.FeatureValue;
import edu.stanford.nlp.process.NumAndCapFeature;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.StringUtils;
import java.io.PrintWriter;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

/**
 * Class for a Hidden Markov Model information extraction tool.
 * <p/>
 * Overview: This class is where the actual training and use of
 * the Hidden Markov Model extractor is implemented. The Constructors
 * initialize the structure (transitions, and maybe emissions of the HMM.
 * A default structure
 * can be used, or the various Structure classes can be used to pass
 * in an arbitrary structure.
 * <p/>
 * Three types of HMMs can be trained: regular, target, or context.
 * A regular HMM is a self-contained full extractor. A target HMM is
 * an HMM that emits only target sequences. A context HMM learns where
 * in documents targets appear. A context and set of targets can be combined
 * to form a regular HMM using the merge constructor.
 * <p/>
 * Training: the training process has up to 3 parts:
 * <ol>
 * <li>Regular Parameter Estimation using EM: Initial transitions are set
 * to either hard-coded defaults or pulled from the Structure object. Initial
 * emissions are set to use simple word counts with add-one smoothing from
 * the training corpus.  Regular parameter estimation is implemented as
 * described in Manning and Sch&uuml;tze (1999).</li>
 * <p/>
 * <li>Unseen Estimation: There are three options for estimating emissions
 * of unseen words: (A) One is for the HMM code to do nothing about unseens.
 * Barring a closed vocabulary, this only makes sense to use if the passed
 * in Corpus maps some words down to a featural representation expressed by
 * a disjoint token set.  The simplest implementation of this idea is to
 * map all singletons to an unknown ($UNK$) token.
 * (C) The third possibility is to use held out data to estimate how likely
 * each state is to emit an unseen word based on a featural decomposition.
 * </li>
 * <p/>
 * <li>Shrink Estimation: Uses held out data to estimate the shrink
 * parameters, which means the HMM is learning the optimum compromise
 * between the specific model that has more structure but suffers from
 * more sparsity with the general model that has
 * less structure more more training data.</li>
 * </ol>
 * For training an HMM, of the data set passed in, a portion (currently 3/4)
 * is used for training, and the remainder is used as a validation set for
 * setting parameters, such as in the Shrink Estimation.
 * <p/>
 * <i>The rest of these comments are notes on the implementation.</i>
 * <p/>
 * The HMM
 * implements a probabilistic regular grammar with a start and an end state
 * within the transition matrix (rather than having a start probability
 * matrix and no end), and so there are two additional states beyond the
 * surface visible ones.  The sequence model of the HMM has time 0 be when
 * it is in the start state and time (numTimes-1) is when it is in the finish
 * state. Emissions are state emissions at each time apart from the start
 * and end.  So we have:
 * <pre>
 * Times:          0   1    2      ...     (numTimes-2)    (numTimes-1)
 * Document.get(): -   0    1      ...     (doc.size()-1)  -
 * State:          S                                       F
 * </pre>
 * <p/>
 * <b>Emissions</b> are initialized as MLE unigram estimates over the whole
 * corpus for all states.
 * During the basic forward-backward reestimation, emissions are calculated
 * for states, and shrinked state-type states.  Only the actual states have
 * unseen word models.  These models are done by counting singleton tokens
 * in the training data as unseen (but this tends to work very badly for
 * this kind of data, because terms are so bursty -- if you see a company
 * name once, then you usually see it several times).
 * <p/>
 * There's lots of code in this class that initializes arrays to zero,
 * but arrays are always initialized to zero on creation (JLS 4.5.5), so
 * this code should disappear.
 *
 * @author Jim McFadden
 * @author Christopher Manning
 * @version 2002/06/01
 */
public class HMM implements Serializable {

  // remember to change this when internal data changes
  private static final long serialVersionUID = 7;

  /**
   * @serial The array of states.  There are states.length states.
   * This includes a special start state and an end state.
   */
  protected State[] states;

  /**
   * @serial names of fields being extracted
   */
  protected String[] targetFields;

  /**
   * Maps words to Double of how many times they appeared in training.
   * We keep this in HMM so that we can map test words to UNK if
   * appropriate during testing/use.
   */
  private ClassicCounter<String> vocab;

  /**
   * Optional Structure object to get transition and start probabilities
   * from. Useful for structure learning
   */
  protected transient GeneralStructure structure;

  protected int hmmType;  // takes following values:
  public static final int REGULAR_HMM = 1;  // for a regular HMM
  public static final int TARGET_HMM = 2;  // for a target-only HMM trained on target sequences only
  public static final int CONTEXT_HMM = 3;  // what is connected to a target-only to get regular
  // all background states besides targets

  // Constants for unseen word strategies

  /**
   * Unseen mode in which nothing special is done for unknown words.
   */
  public static final int UNSEENMODE_NONEXISTENT = 0;
  /**
   * Unseen mode in which words that occur a small number of times are collapsed into special UNK tokens.
   */
  public static final int UNSEENMODE_UNK_LOW_COUNTS = 1;
  /**
   * Unseen mode in which some emission mass is reserved for generating previously unseen words.
   */
  public static final int UNSEENMODE_HOLD_OUT_MASS = 2;
  /**
   * Strategy for unseen words used during training.
   */
  public static final int UNSEENMODE_USE_CHAR_NGRAMS = 3;
  private int unseenMode;

  // Constants for how to calculate statistics for unknown words

  /**
   * Unknown model in which all unseen words are lumped into a single UNK bin.
   */
  public static final int UNKMODEL_SINGLE_UNK = 0;
  /**
   * Unknown model in which unseen words are split into bins based on a featural decomposition (capitalization, numbers, etc).
   */
  public static final int UNKMODEL_FEATURAL_DECOMP = 1;
  /**
   * Unknown model used for training and testing.
   */
  private int unkModel;

  // Constants for data sources used in unknown word estimation

  /**
   * Use words that occurred once in training to estimate unseen probs and/or featural decomp.
   */
  public static final int SOURCE_SINGLETONS = 0;
  /**
   * Use held-out data to estimate unseen probs and/or featural decomp.
   */
  public static final int SOURCE_HELD_OUT = 1;

  /**
   * Whether to estimate the chance of seeing an unknown word in each state from singletons or held-out data.
   */
  private int unseenProbSource;
  /**
   * Whether to estimate the featural breakdown fo unknown words from singletons or held-out data.
   */
  private int featureSource;

  private Feature feat;

  private static final double DEFAULT_HELD_OUT_FRAC = 0.25;

  private static final boolean spillEmissions = false;
  private static final boolean sanityCheck = true;  // check assertions (NOTE: TURN OFF FOR CONDITIONAL TRAINING)

  private static final int windowSize = 4; // context window round HMM

  private static final boolean josephStuff = false; // keep all iters. and do joint-cond (temporary only)

  private transient HMMTrainer hmmt;


  /**
   * Construct a new HMM, with default states
   * <code>Structure.defaultStates()</code>, initializing just the
   * <code>hmmType</code>.
   *
   * @param hmmType One of REGULAR_HMM, TARGET_HMM, CONTEXT_HMM
   */
  public HMM(int hmmType) {
    this((GeneralStructure) null, hmmType);
  }

  /**
   * Build an HMM with the given structure and type.
   *
   * @param struc   The qualitative structure of the HMM
   * @param hmmType Whether this is a regular, target or context HMM
   */
  public HMM(GeneralStructure struc, int hmmType) {
    this(((struc == null) ? Structure.defaultStates() : struc.getStates()), hmmType);
    structure = struc;
  }

  /**
   * Build an HMM with the given states and type.
   *
   * @param states  The transitions and emissions as a state array
   * @param hmmType Whether this is a regular, target or context HMM
   */
  public HMM(State[] states, int hmmType) {
    this.states = states;
    this.hmmType = hmmType;
    structure = new Structure(states);

  }

  /**
   * Build an HMM, specifying everything.
   *
   * @param states       The transitions and emissions as a state array
   * @param hmmType      Whether this is a regular, target or context HMM
   * @param targetFields The names for the state types
   * @param vocab        All the words known by the HMM
   */
  public HMM(State[] states, int hmmType, String[] targetFields, ClassicCounter<String> vocab) {
    this.states = states;
    this.hmmType = hmmType;
    structure = new Structure(states);
    this.targetFields = targetFields;
    this.vocab = vocab;
  }

  /**
   * Constructor for merging a context HMM and its targetHMMs.
   * There must be a targetHMM for each target that contextHMM was
   * trained on.
   *
   * @param contextHMM This is assumed to have some number of context
   *                   (background) states,
   *                   and placeholders with a ConstantEmitMap where the target HMMs
   *                   will be inserted.
   * @param targetHMMs An HMM for each target
   * @param trained    Specifies whether the HMMs have been
   *                   trained or not.
   * @param verbose    whether to print debug info to stderr
   */
  public HMM(HMM contextHMM, HMM[] targetHMMs, boolean trained, boolean verbose) {
    hmmType = REGULAR_HMM;
    int numStates = 0;

    // copies and merges the vocabs from all the HMMs for this HMM's vocab
    if (trained) {
      vocab = new ClassicCounter<String>();
      Counters.addInPlace(vocab, contextHMM.getVocab());
      for (HMM targetHMM : targetHMMs) {
        Counters.addInPlace(vocab, targetHMM.getVocab());
      }
    }

    // uses the unseen mode and unkModel from the context HMM
    unseenMode = contextHMM.getUnseenMode();
    unkModel = contextHMM.getUnkModel();
    unseenProbSource = contextHMM.getUnseenProbSource();
    featureSource = contextHMM.getFeatureSource();
    feat = contextHMM.getFeature();

    // count the number of non-placeholder states in the contextHMM
    for (int i = 0; i < contextHMM.states.length; i++) {
      //if ( ! (contextHMM.states[i].emit instanceof ConstantEmitMap))
      if (contextHMM.states[i].type <= 0) {
        numStates++;
      }
    }

    HashMap<String,Integer> targetIndexMap = new HashMap<String,Integer>();
    for (int i = 0; i < targetHMMs.length; i++) {
      if (verbose) {
        System.err.println("Target HMM " + i + ": " + targetHMMs[i]);
      }
      // ensures that this is a valid target HMM (has only 1 target)
      // use 2 because (Background) is added to the targetFields list
      if (targetHMMs[i].targetFields.length != 2) {
        // HN TODO: I'm sure we can work around this
        if (verbose) {
          System.err.println("Invalid target: " + (targetHMMs[i].targetFields.length - 1) + " target fields.  Ignoring this target.");
        }
      } else {
        // record the index of the target in the targetHMMs array
        targetIndexMap.put(targetHMMs[i].targetFields[1], Integer.valueOf(i));
        // subtract 2 because we are going to remove the start and end
        // states from the targets
        numStates += (targetHMMs[i].states.length - 2);
      }
    }
    if (verbose) {
      System.err.println("New HMM will have " + numStates + " states.");
    }
    states = new State[numStates];
    targetFields = contextHMM.targetFields;

    // copies the contextHMM states over to the merged HMM, leaving
    // the placeholders for now
    for (int i = 0; i < contextHMM.states.length; i++) {
      states[i] = new State(contextHMM.states[i].type, contextHMM.states[i].emit, numStates);
      System.arraycopy(contextHMM.states[i].transition, 0, states[i].transition, 0, contextHMM.states[i].transition.length);
    }

    // the newTargetBaseNum will be used to adjust the local state numbers
    // of the target states to the appropriate global state numbers
    // in the merged HMM
    // initialize to the "end" of the contextHMM states.  The target
    // states added by the targetHMMs will be appended to the end of
    // contextHMM states in the merged HMM
    int newTargetBaseNum = contextHMM.states.length;

    // Now iterates over the target placeholders, wiring the appropriate
    // target HMM into the merged HMM
    for (int i = 0; i < contextHMM.states.length; i++) {
      // if the state is a placeholder for a target state...
      if (contextHMM.states[i].type > 0) {
        // save the placeHolder (for transitions) since we are
        // going to overwrite it
        State placeHolder = new State(states[i]);

        // the integer value for the target type is determined by
        // the contextHMM.  target type i corresponds to
        // target i in the targetFields array
        int targetType = contextHMM.states[i].type;

        // if we don't have an HMM for the target, continue
        if (targetIndexMap.get(targetFields[targetType]) == null) {
          if (verbose) {
            System.err.println("Could not find HMM for target " + targetFields[targetType]);
            System.err.println("targetIndexMap: " + targetIndexMap);
            for (String targetField : targetFields) {
              System.err.print(targetField + " ");
            }
            System.err.println();
            System.err.println("targetType: " + targetType);
          }
          continue;
        }
        int targetIndex = targetIndexMap.get(targetFields[targetType]).intValue();

        State[] targetStates = targetHMMs[targetIndex].states;

        // we need to "compile" out the start state by transitioning
        // from other states to the initial states of the target state with
        // probability equal to the product of
        // the probability of transitioning into the
        // placeholder from another state and the
        // probability of transitioning to the different states
        // in the targetHMM from the targetHMM's start state
        // IMPORTANT BUT YUCKY: WE NEED TO COUNT DOWN SO THAT WE UPDATE
        // OTHER TARGET STATES BEFORE OVERWITING transition[i] FOR STATES
        for (int k = targetStates[State.STARTIDX].transition.length - 1; k > State.STARTIDX; k--) {
          if (targetStates[State.STARTIDX].transition[k] != 0.0) {
            // copies the first non-start/end target
            // state to the location formerly occupied
            // by placeholder.  Copies the rest to the
            // "end" of the mergedHMM
            int newTargetStateNum = (k == 2) ? i : (newTargetBaseNum + k - 3);
            for (int l = 0; l < newTargetBaseNum; l++) {
              states[l].transition[newTargetStateNum] = // transition from state[l]
                      // into placeholder
                      states[l].transition[i] * // transition from the target
                      // HMM's start state to state[k]
                      targetStates[State.STARTIDX].transition[k];
            }
          }
        } // for k
        for (int j = State.STARTIDX + 1; j < targetStates.length; j++) {
          State curTargetState = targetStates[j];
          int newTargetStateNum = (j == State.STARTIDX + 1) ? i : (newTargetBaseNum + j - 3);
          states[newTargetStateNum] = new State(targetType, curTargetState.emit, numStates);

          if (verbose) {
            System.err.println("Copying target state " + j + " to state " + newTargetStateNum + " (type " + targetType + ": " + targetFields[targetType] + ") in the merged HMM");
          }

          // we need to "compile" out transitions to the end state
          // from target states with probability equal to the product of
          // the probability of transitioning into the
          // end state from a target state in the target HMM and the
          // probability of transitioning from the placeholder
          // to a context state
          for (int k = 0; k < states.length; k++) {
            states[newTargetStateNum].transition[k] = // probability of transitioning to end state
                    curTargetState.transition[State.FINISHIDX] * // transition from placeholder to state[k]
                    placeHolder.transition[k];
          }
          for (int k = State.STARTIDX + 1; k < curTargetState.transition.length; k++) {
            // the first non-start/end target
            // state takes the location formerly occupied
            // by placeholder.  the rest are appended
            // to the "end" of the mergedHMM
            int outgoingState = (k == 2) ? i : (newTargetBaseNum + k - 3);
            // for the rest of the states, just map the
            // probabilities to the correct corresponding
            // global indices in the merged HMM
            states[newTargetStateNum].transition[outgoingState] = curTargetState.transition[k];
          } // for k
        } // for j in targetStates
        // increment the base index
        newTargetBaseNum += targetStates.length - 3;
      } // if contextHMM state i type > 0
    } // for contextHMM states i
    checkNormalized();
    if (verbose) {
      printProbs();
    }
  }


  /**
   * Sets the vocab for this HMM (String (word) -> Double (count)).
   */
  public void setVocab(ClassicCounter<String> vocab) {
    this.vocab = vocab;
  }


  /**
   * @return An array containing the target fields extracted by this
   *         HMM.  The index of the field in the array is the integer type
   *         assigned to that field
   */
  public String[] getTargetFields() {
    return targetFields;
  }

  /**
   * Set the target fields.  This is normally done in training.  Use only
   * if not going to call train() on this HMM, i.e.,
   * when running conditional training on merged hmm.
   */
  public void setTargetFields(String[] targetFields) {
    this.targetFields = targetFields;
  }

  /**
   * Returns expected number of transitions from each state (gammas)
   * computed during EM training.
   *
   * @return Expected number of transitions from each state (gammas)
   */
  public double[] getGammas() {
    return hmmt.getGammas();
  }

  /**
   * Trains without verbose printing.
   */
  public HMMTrainer train(Corpus trainCorpus) {
    return (train(trainCorpus, false));
  }

  /**
   * Trains with default properties.
   */
  public HMMTrainer train(Corpus trainCorpus, boolean verbose) {
    return (train(trainCorpus, null, verbose));
  }

  /**
   * Trains without a specified held out corpus.
   */
  public HMMTrainer train(Corpus trainCorpus, Properties props, boolean verbose) {
    return (train(trainCorpus, null, props, verbose));
  }

  /**
   * Trains this HMM using the given training corpus and properties.
   * Properties specify whether to train joint or conditionally, how to
   * handle unknown words, whether to perform shrinkage, and so on. Uses
   * {@link Extractor#getDefaultProperties} if <tt>props</tt> is null. Splits
   * off a bit of the training corpus for held out estimation if heldOut is
   * null but the properties call for held-out estimation.
   *
   * @param train   main training corpus to use
   * @param props   training properties to use (if null, uses defaults from Extractor)
   * @param heldOut held out corpus to use for shrinkage and/or unseen
   *                estimation
   * @param verbose whether to print messages to stderr while running
   * @return the HMMTrainer used to do the actual training
   * @see Extractor#getDefaultProperties
   */
  protected HMMTrainer train(Corpus train, Corpus heldOut, Properties props, boolean verbose) {

    Corpus originalTrain;
    if (josephStuff) {
      originalTrain = train; // JS: TAKE ME OUT
    }

    // uses defaults if props aren't specified -- but ideally these would
    // be the defaults of what got passed in anyway
    if (props == null) {
      props = Extractor.getDefaultProperties();
    }

    // Get the Feature set to be used for unknown words
    if (feat == null) {
      try {
        String unkFeature = props.getProperty("unkFeature");
        if (verbose) {
          System.err.println("Instantiating Feature class: " + unkFeature);
        }
        feat = (Feature) Class.forName(unkFeature).newInstance();
      } catch (Exception e) {
        if (verbose) {
          System.err.println("Feature class specified in properties file not found: " + e);
          System.err.println("Using default edu.stanford.nlp.ie.hmm.NumAndCapFeature instead.");
        }
        feat = new NumAndCapFeature();
      }
    }

    boolean shrinkage = (props.getProperty("shrinkage") != null);
    unseenMode = parseUnseenMode(props.getProperty("unseenMode"));
    unkModel = parseUnkModel(props.getProperty("unkModel"));
    unseenProbSource = parseSource(props.getProperty("unseenProbSource"));
    featureSource = parseSource(props.getProperty("featureSource"));
    boolean initEmissions = props.getProperty("initEmissions").equalsIgnoreCase("true");
    // These can smooth the various estimates by dividing this number of
    // pseudoCounts across the multinomial (Mitchell's "m-estimate")
    double pseudoTransitionsCount = Double.parseDouble(props.getProperty("pseudoTransitionsCount"));
    double pseudoEmissionsCount = Double.parseDouble(props.getProperty("pseudoEmissionsCount"));
    double pseudoUnknownsCount = Double.parseDouble(props.getProperty("pseudoUnknownsCount"));


    // splits up the corpus if heldOut is null but the training params
    // require a held out corpus (shrinkage or estimation on held out)
    if (heldOut == null && (shrinkage || (unseenMode == UNSEENMODE_HOLD_OUT_MASS && (unseenProbSource == SOURCE_HELD_OUT || featureSource == SOURCE_HELD_OUT)))) {
      if (verbose) {
        System.err.println("Splitting the training corpus because heldOut was null");
      }
      double splitPoint = 1.0 - DEFAULT_HELD_OUT_FRAC;
      heldOut = (Corpus) train.splitRange(splitPoint, 1.0);
      train = (Corpus) train.splitRange(0.0, splitPoint);
    }

    // collapses rare words into UNK tokens if that's the chosen unseen method
    // remembers so test documents can be processed in the same way
    if (unseenMode == UNSEENMODE_UNK_LOW_COUNTS) {
      // whether to perform a featural decomp or use a single UNK
      boolean decomp = (unkModel == UNKMODEL_FEATURAL_DECOMP);

      ClassicCounter<String> fullVocab = new ClassicCounter<String>(); // train + heldOut vocab for UNKing
      if (train != null) {
        Counters.addInPlace(fullVocab, train.getVocab());
      }
      if (heldOut != null) {
        Counters.addInPlace(fullVocab, heldOut.getVocab());
      }
      Set frequentWords = Counters.keysAbove(fullVocab, (double) 2); // words seen at least 2x

      if (train != null) {
        train = new UnknownWordCollapser(frequentWords, decomp, feat).processCorpus(train);
      }
      if (heldOut != null) {
        heldOut = new UnknownWordCollapser(frequentWords, decomp, feat).processCorpus(heldOut);
      }
    }

    // prints out some initial info
    targetFields = train.getTargetFields();
    if (verbose) {
      System.err.println("Training HMM on " + train.size() + " documents with targets");
      System.err.println("----------------------------");
      for (int i = 0; i < targetFields.length; i++) {
        System.err.println(i + ": " + targetFields[i]);
      }
      System.err.println("----------------------------");
      printTransitions();
    }

    if (sanityCheck) {
      checkStochasticMatrix(states);
    }
    hmmt = new HMMTrainer(verbose, pseudoTransitionsCount, pseudoEmissionsCount, pseudoUnknownsCount);
    if (train != null) {
      hmmt.setTrainingCorpus(train);
    }
    hmmt.initEmissions(initEmissions);

    if (props.getProperty("trainType").equals("conditional")) {
      // train using gradient descent to optimize conditional likelihood
      Minimizer cgm;
      if (verbose) {
        cgm = new CGMinimizer(new HMM.HMMConditionalTrainingMonitorFunction(hmmt));
      } else {
        cgm = new CGMinimizer(); // monitor function's only purpose is printing stuff out
      }
      ConstrainedMinimizer<DiffFunction> ccgm = new PenaltyConstrainedMinimizer(cgm);
      double tol = 1e-4;  // convergence threshold
      // HMMTrainer implements diff function
      //double[] minimum = cgm.minimize(hmmt, tol, hmmt.initialParams());
      HMMConditionalTrainingMassPenaltyFunction penaltyFunction = new HMMConditionalTrainingMassPenaltyFunction(hmmt, states.length);
      hmmt.setSigmaSquared(Integer.parseInt(props.getProperty("sigmaSquared")));
      double penaltyTolerance = penaltyFunction.getIdealTotalMass() * 0.1; // 10% deviation is ok
      double[] minimum = ccgm.minimize(hmmt, tol, new DiffFunction[]{penaltyFunction}, penaltyTolerance, new DiffFunction[0], 0, hmmt.initialParams());
      if (verbose) {
        System.err.println("Final score for minimum conditional paramters: " + hmmt.valueAt(minimum));
      }
      hmmt.applyParams(minimum); // sticks these best params into the HMM
    } else if ("entropicPriorMAP".equals(props.getProperty("trainType"))) {
      hmmt.trainEntropicPriorMAP();
    } else {
      hmmt.forwardBackward(); // standard baum-welch estimation
      // for paper: test conditional starting at various points from EM
      if (josephStuff) {
        props.setProperty("trainType", "conditional");
        props.setProperty("initEmissions", "false");
        for (int i = 0; i < 4; i++) {
          switch (i) {
            case 0:
              System.err.println("$$$ Training conditionally before any EM iterations");
              break;
            case 1:
              System.err.println("$$$ Training conditionally after 1 round of EM");
              break;
            case 2:
              System.err.println("$$$ Training conditionally after best round of EM");
              break;
            case 3:
              System.err.println("$$$ Training conditionally after last round of EM");
              break;
          }
          setParams(hmmt.startCondParams[i]);
          train(originalTrain, null, props, verbose);
        }
        props.setProperty("trainType", "joint");
        props.setProperty("initEmissions", "true");
      }
    }

    if (heldOut != null && heldOut.size() > 0) {
      if (verbose) {
        printStates(states);
      }
      //hmmt.estimateShrinkUnseen_OLD(heldOut, shrinkage,
      boolean useSingletons = unseenProbSource == SOURCE_SINGLETONS;
      Corpus docs = useSingletons ? train : heldOut;
      hmmt.estimateShrinkUnseen(docs, useSingletons, shrinkage, unseenMode == UNSEENMODE_HOLD_OUT_MASS && unseenProbSource == SOURCE_HELD_OUT);

      // retrain on heldOut but keep unseen/shrinkage params fixed
      // (passing in empty held-out corpus is a bit of a hack but it works)
      props.setProperty("initEmissions", "false"); // start where training left off

      train.addAll(heldOut); // use train + heldOut
      if (verbose) {
        System.err.println("Retraining on held-out docs");
      }
      train(train, new Corpus(), props, verbose);
      props.setProperty("initEmissions", String.valueOf(initEmissions)); // revert to normal
    }
    /*
    // turn all emit maps into n-gram emit maps after EM
    if(unseenMode==UNSEENMODE_USE_CHAR_NGRAMS)
    {
        int maxNGramLength=Integer.parseInt(props.getProperty("maxNGramLength"));
        if(verbose) System.err.println("Converting emit maps to char n-grams (n="+maxNGramLength+") and tuning parameters...");
        for(int i=0;i<states.length;i++)
            if(states[i].emit instanceof PlainEmitMap)
                states[i].emit=new CharSequenceEmitMap(states[i].emit,maxNGramLength);
    }
    */
    if (verbose) {
      printStates(states);
    }

    return hmmt;
  }


  private void unitTestTraining1(Corpus trainCorpus) {
    Properties props = new Properties();
    props.setProperty("unseenSource", "singletons");
    HMMTrainer hmmt = train(trainCorpus, props, true);

    double internalLogLike = 0.0;
    double internalUnscaledLogLike = 0.0;
    for (int d = 0, size = hmmt.trainDocs.size(); d < size; d++) {
      System.err.println("Document " + d + " of " + size + ".");
      Document doc = (Document) hmmt.trainDocs.get(d);
      int numTimes = doc.size() + 2;
      System.err.println();
      System.err.println("Forward backward with scaling.");
      if (!hmmt.forwardAlgorithm(doc, true)) {
        System.err.println("Couldn't generate document " + d);
      } else {
        hmmt.backwardAlgorithm(doc, true);
        printTrellis("Forward", hmmt.alpha);
        printTrellis("Backward", hmmt.beta);
        printStateVector("Scaling", hmmt.scale);
        double C = 1.0;
        for (int t = 0; t < numTimes; t++) {
          C *= hmmt.scale[t];
        }
        C = 1 / C;
        System.err.println();
        System.err.println("Scaled P(O|mu) = " + C + "  (log(P) = " + Math.log(C) + ")");
        internalLogLike += Math.log(C);
      }

      System.err.println();
      System.err.println("Forward backward without scaling.");
      if (!hmmt.forwardAlgorithm(doc, false)) {
        System.err.println("Couldn't generate document " + d);
      } else {
        hmmt.backwardAlgorithm(doc, false);
        printTrellis("Forward", hmmt.alpha);
        printTrellis("Backward", hmmt.beta);
        double C = 0.0;
        for (int i = 0; i < states.length; i++) {
          C += hmmt.alpha[i][numTimes - 1];
        }
        System.err.println("Unscaled P(O|mu) = " + C);
        internalUnscaledLogLike += Math.log(C);
      }
      System.err.println();
    }
    System.err.println();
    System.err.println("internal Scaled logLikelihood = " + internalLogLike);
    System.err.println("internal Unscaled logLikelihood = " + internalUnscaledLogLike);
    System.err.println("Scaled logLikelihood = " + logLikelihood(trainCorpus));
    System.err.println("Unscaled logLikelihood = " + logLikelihood(trainCorpus, false));
    System.err.println();
  }

  private void unitTestTraining2() {
    System.err.println("Testing FSNLP 1ed HMM");
    // HACK -- shows that something is broken in the design!
    String[] targets = {"normal"};
    targetFields = targets;
    printProbs();
    String fsnlpFilename = "/u/nlp/data/iedata/fsnlpHmmTest";
    HMMTrainer fhmmt = new HMMTrainer(true);
    Corpus fcorpus = new Corpus(fsnlpFilename, "fred");
    fhmmt.setTrainingCorpus(fcorpus);
    fhmmt.forwardAlgorithm((Document) fcorpus.get(0), false);
    fhmmt.backwardAlgorithm((Document) fcorpus.get(0), false);
    printTrellis("Forward", fhmmt.alpha);
    printTrellis("Backward", fhmmt.beta);
  }


  /**
   * Calculate a Viterbi alignment through the document from start state
   * to end state, which precede and follow the state emission observations
   * respectively. <br>
   * <i>Implementation notes:</i> This works with real probabilities.
   * If they are
   * getting too small, we manually rescale them to keep them within a
   * reasonable range (since only the max matters).  This routine
   * deliberately uses <code>Word</code> not <code>TypedTaggedWord</code>
   * so that it will work for any <code>Document</code>.
   *
   * @param doc The document to find a Viterbi sequence over
   * @return The viterbi state sequence (including start and end states)
   */
  public int[] viterbiSequence(Document doc) {
    // mark rare words as UNK as per the training vocab
    if (unseenMode == UNSEENMODE_UNK_LOW_COUNTS) {
      doc = new UnknownWordCollapser(vocab.keySet(), unkModel == UNKMODEL_FEATURAL_DECOMP, feat).processDocument(doc);
    }
    int numTimes = doc.size() + 2;
    int numStates = states.length;

    // psi and delta are both state by time lattices of
    // viterbi max scores and best backpointers respectively
    double[][] delta = new double[numStates][numTimes];
    int[][] psi = new int[numStates][numTimes];

    // we always start in the start state, state 1; others init to 0.0
    delta[State.STARTIDX][0] = 1.0;

    int numScalings = 0;
    // fill in values for delta, psi going through tokens
    // we're storing in t+1 so only go through up to numTimes-2
    for (int t = 0; t < numTimes - 1; t++) {  // go through time
      Word ttw = null;
      if (t > 0 && t < (numTimes - 1)) {
        ttw = (Word) doc.get(t - 1);
      }
      double[] emitProbs = null; // cached emissions (from states) to prevent n^2 lookup
      if (ttw != null) {
        emitProbs = new double[numStates];
        for (int i = 0; i < numStates; i++) {
          if (states[i].emit != null) {
            emitProbs[i] = states[i].emit.get(ttw.word());
          }
        }
      }
      double maxmax = 0.0;
      for (int j = 0; j < numStates; j++) {  // go through to
        double max = 0.0;
        // int argmax = -1;
        // default to BKGRNDIDX
        int argmax = State.BKGRNDIDX;

        for (int i = 0; i < numStates; i++) { // go through from
          double p;
          if (ttw == null) {
            p = delta[i][t] * states[i].transition[j];
          } else if (states[i].emit == null) {
            p = 0.0; // not possible
          } else {
            p = delta[i][t] * states[i].transition[j] * emitProbs[i];
          }
          if (p > max) {
            max = p;
            argmax = i;
          }
          if (false && t < 200 && p != 0.0) {
            System.err.println("Time " + t + " i=" + i + " to j=" + j + "; States[i] is " + states[i] + "; word is " + ttw + "; emit is " + ((states[i].emit == null || ttw == null) ? "null" : Double.toString(emitProbs[i])) + " trans is " + states[i].transition[j] + " path prob is " + p);
          }
        } // for from state
        delta[j][t + 1] = max;
        psi[j][t + 1] = argmax;
        if (max > maxmax) {
          maxmax = max;
        }
      } // for to state
      if (maxmax == 0.0) {
        System.err.println("Viterbi warning: no path at time " + t + " [word: " + ttw + "]");
        // fake rest of the array to keep other machinery happy
        // the other machinery should be fixed.
        for (int a = t + 1; a < numTimes; a++) {
          psi[2][a] = State.BKGRNDIDX;
        }
        psi[State.FINISHIDX][numTimes - 1] = State.BKGRNDIDX;
        break;
      } else if (maxmax < 1e-200) {
        // System.err.println("XXXXX Warning: Viterbi algorithm about to underflow at time " + (t+1));
        // rescale
        numScalings++;
        for (int j = 0; j < numStates; j++) {
          delta[j][t + 1] *= 1e200;
        }
      }
    } // for time

    // find most likely sequence, right to left from finish state
    // we know we ended in state 0 and so don't have to search
    int[] sequence = new int[numTimes];
    sequence[numTimes - 1] = State.FINISHIDX;
    if (false) {
      System.err.print("Viterbi sequence prob = " + delta[0][numTimes - 1]);
      if (numScalings > 0) {
        System.err.print(" * 10^-" + (numScalings * 200));
      }
      System.err.println();
    }

    for (int k = numTimes - 2; k >= 0; k--) {
      sequence[k] = psi[sequence[k + 1]][k + 1];
    }
    if (false) {
      System.out.println("Viterbi Sequence is:");
      for (int k = 0; k < numTimes; k++) {
        if (k != 0) {
          System.out.print(" - ");
        }
        System.out.print(sequence[k]);
      }
      System.out.println();
    }
    return sequence;
  }


  /**
   * Returns the state type for each state in a (viterbi) state sequence.
   * This strips the start and end so that it's of length (numTimes-2)
   */
  public int[] getLabelsForSequence(int[] sequence) {
    int[] guesses = new int[sequence.length - 2];

    for (int t = 0; t < guesses.length; t++) {
      guesses[t] = states[sequence[t + 1]].type;
    }
    return guesses;
  }

  private static void printStateVector(String name, double[] states) {
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(3);

    System.err.print(name + " = {");
    for (int i = 0; i < states.length; i++) {
      System.err.print(i + "=" + nf.format(states[i]));
      if (i != states.length - 1) {
        System.err.print(",");
      }
    }
    System.err.println("}");
  }

  /**
   * Pulls out the best answers from the given text for each target type
   * defined for this HMM. Specifically, calls
   * {@link #bestAnswers(Document,int[])} by creating a
   * {@link edu.stanford.nlp.ie.TypedTaggedDocument} for
   * the given text and computing the viterbi state sequence of this HMM for
   * that document. Returns a map from state type (Integer) -> best answer
   * (String). Note that the list of words for each answer returned by
   * bestAnswers is put together and returned as a single string.
   *
   * @param text The text to do information extraction from, as one long
   *             string.
   * @return a <code>Map</code> from a state type (as an Integer) to an
   *         answer as a String
   */
  public Map bestAnswers(String text) {
    Document doc = new TypedTaggedDocument().init(text);
    Map bestAnswersByType = bestAnswers(doc, viterbiSequence(doc));
    return (collapseAnswerStrings(bestAnswersByType));
  }

  /**
   * Pulls out the first answers from the given text for each target type
   * defined for this HMM. Specifically, calls
   * {@link #firstAnswers(Document,int[])} by creating a
   * {@link edu.stanford.nlp.ie.TypedTaggedDocument} for
   * the given text and computing the viterbi state sequence of this HMM for
   * that document. Returns a map from state type (Integer) -> first answer
   * (String). Note that the list of words for each answer returned by
   * firstAnswers is put together and returned as a single string.
   *
   * @param text The text to do information extraction from, as one long
   *             string.
   * @return a <code>Map</code> from a state type (as an Integer) to an
   *         answer as a String
   */
  public Map firstAnswers(String text) {
    Document doc = new TypedTaggedDocument().init(text);
    Map firstAnswersByType = firstAnswers(doc, viterbiSequence(doc));
    return (collapseAnswerStrings(firstAnswersByType));
  }

  /**
   * Pulls out all of the answers from the given text for each target type
   * defined for this HMM. Specifically, calls
   * {@link #allAnswers(Document,int[])} by creating a
   * {@link edu.stanford.nlp.ie.TypedTaggedDocument} for
   * the given text and computing the viterbi state sequence of this HMM for
   * that document. Returns a map from state type (Integer) -> answers
   * (List of Strings). Note that the list of words for each answer returned by
   * firstAnswers is put together and returned as a single string.
   *
   * @param text The text to do information extraction from, as one long
   *             string.
   * @return a <code>Map</code> from a state type (as an Integer) to an
   *         answer as a String
   */
  public Map allAnswers(String text) {
    Document doc = new TypedTaggedDocument().init(text);
    return (allAnswers(doc, viterbiSequence(doc)));
  }

  /**
   * Takes a map whose values are Lists of Strings and returns a map whose
   * values are Strings made by joining ths lists with spaces.
   * So {key -> ["two", "words"]} becomes {key -> "two words"}.
   */
  private Map collapseAnswerStrings(Map answersByType) {
    Map answerStringsByType = new HashMap();
    for (Iterator iter = answersByType.keySet().iterator(); iter.hasNext();) {
      Object type = iter.next();
      List answerWords = (List) answersByType.get(type);
      answerStringsByType.put(type, StringUtils.join(answerWords, " "));
    }
    return (answerStringsByType);
  }

  /**
   * Returns a map from state type (Integer) -> List of Strings reprsenting
   * the first answer (word sequence) for that type. This is an alternative
   * to getting the {@link #bestAnswers(Document,int[])} that often works
   * better in practice, particularly for web pages.
   */
  public HashMap<Integer,List<String>> firstAnswers(Document doc, int[] stateSequence) {
    int[] typeSequence = getLabelsForSequence(stateSequence);
    HashMap<Integer,List<AnswerChecker.Range>> answerRangesByType = AnswerChecker.getAnswerRanges(typeSequence);
    HashMap<Integer,List<String>> firstAnswerByType = new HashMap<Integer,List<String>>();
    for (Integer type : answerRangesByType.keySet()) {
      List answerRanges = answerRangesByType.get(type);
      AnswerChecker.Range firstRange = (AnswerChecker.Range) answerRanges.get(0);
      firstAnswerByType.put(type, firstRange.extractRange(doc));
    }
    return firstAnswerByType;
  }

  /**
   * Returns a map from state type (Integer) -> List of Strings representing
   * the best answer (word sequence) for that type. The best answer is the
   * one with highest probability of being generated by this HMM.
   */
  public HashMap<Integer,List<String>> bestAnswersOLD(Document doc, int[] stateSequence) {
    // mark rare words as UNK as per the training vocab
    // keep original around for actually returning word sequences
    Document unkDoc = doc;
    if (unseenMode == UNSEENMODE_UNK_LOW_COUNTS) {
      unkDoc = new UnknownWordCollapser(vocab.keySet(), unkModel == UNKMODEL_FEATURAL_DECOMP, feat).processDocument(doc);
    }

    int[] typeSequence = getLabelsForSequence(stateSequence);
    HashMap<Integer,List<AnswerChecker.Range>> answerRangesByType = AnswerChecker.getAnswerRanges(typeSequence);
    HashMap<Integer,List<String>> bestAnswerByType = new HashMap<Integer,List<String>>();
    for (Integer type : answerRangesByType.keySet()) {
      List<AnswerChecker.Range> answerRanges = answerRangesByType.get(type);
      ClassicCounter<AnswerChecker.Range> rangeScores = new ClassicCounter<AnswerChecker.Range>();
      for (AnswerChecker.Range r : answerRanges) {
        double score = 1.0; // probability of generating this range
        int from = Math.max(1, r.getFrom() - windowSize); // first emission state for this answer with context
        int to = Math.min(stateSequence.length - 1, r.getTo() + windowSize); // last emission state (inclusive) for this answer with context
        for (int i = from; i <= to; i++) {
          score *= states[stateSequence[i - 1]].transition[stateSequence[i]]; // transition prob
          if (i < stateSequence.length - 1) // emission prob except at final state in HMM
          {
            score *= states[stateSequence[i]].emit.get(((HasWord) unkDoc.get(i - 1)).word());
          }
        }
        score = Math.pow(score, 1.0 / (to - from)); // takes length'th root to normalize for length
        rangeScores.setCount(r, score);
      }
      AnswerChecker.Range bestAnswerRange = Counters.argmax(rangeScores);
      if (bestAnswerRange != null) {
        // extracts the list of words (Strings) for the best range
        bestAnswerByType.put(type, bestAnswerRange.extractRange(doc));
      }
    }
    return bestAnswerByType;
  }

  /**
   * Calls the correct bestAnswers (old or noew). TEMPORARY!!
   */
  public HashMap<Integer,List<String>> bestAnswers(Document doc, int[] stateSequence) {
    return bestAnswersOLD(doc, stateSequence);
    //return bestAnswersNEW(doc,stateSequence);
  }

  /**
   * New version of bestAnswers that uses the new target scoring code.
   */
  public HashMap<Integer,List<String>> bestAnswersNEW(Document doc, int[] stateSequence) {
    // NOTE: we eventually want to learn/change this threshold
    double targetScoreThreshold = 1.0; // only return answers above this threshold

    // mark rare words as UNK as per the training vocab
    // keep original around for actually returning word sequences
    Document unkDoc = doc;
    if (unseenMode == UNSEENMODE_UNK_LOW_COUNTS) {
      unkDoc = new UnknownWordCollapser(vocab.keySet(), unkModel == UNKMODEL_FEATURAL_DECOMP, feat).processDocument(doc);
    }

    // precompute forward and backward vars (we assume it's generatable)
    HMMTrainer hmmt = new HMMTrainer(false); // for scoring ranges
    hmmt.forwardAlgorithm(unkDoc, true, false);
    hmmt.backwardAlgorithm(unkDoc, true, false);

    int[] typeSequence = getLabelsForSequence(stateSequence);
    HashMap answerRangesByType = AnswerChecker.getAnswerRanges(typeSequence);
    HashMap bestAnswerByType = new HashMap();

    for (Iterator iter = answerRangesByType.keySet().iterator(); iter.hasNext();) {
      Integer type = (Integer) iter.next();
      List answerRanges = (List) answerRangesByType.get(type);
      ClassicCounter rangeScores = new ClassicCounter();
      for (Iterator rangeIter = answerRanges.iterator(); rangeIter.hasNext();) {
        AnswerChecker.Range r = (AnswerChecker.Range) rangeIter.next();
        rangeScores.setCount(r, hmmt.computeTargetScore(unkDoc, type.intValue(), r));
      }
      // TAKE ME OUT - DBEUG PRINOUT
      for (Iterator iter2 = rangeScores.keySet().iterator(); iter2.hasNext();) {
        AnswerChecker.Range r = (AnswerChecker.Range) iter2.next();
        System.err.println(r.extractRange(doc) + ": " + rangeScores.getCount(r));
      }
      AnswerChecker.Range bestAnswerRange = (AnswerChecker.Range) Counters.argmax(rangeScores);
      if (bestAnswerRange != null && rangeScores.getCount(bestAnswerRange) > targetScoreThreshold) {
        bestAnswerByType.put(type, bestAnswerRange.extractRange(doc));
      }
    }

    return (bestAnswerByType);
  }

  /**
   * Returns a map from state type (Integer) -> List of Strings
   * reprsenting all of the answers (word sequence) for that type.  Useful
   * when trying to extract fields that may have multiple instances per
   * document.
   */
  public HashMap allAnswers(Document doc, int[] stateSequence) {
    int[] typeSequence = getLabelsForSequence(stateSequence);
    HashMap answerRangesByType = AnswerChecker.getAnswerRanges(typeSequence);
    HashMap answerByType = new HashMap();
    for (Iterator iter = answerRangesByType.keySet().iterator(); iter.hasNext();) {
      Integer type = (Integer) iter.next();
      ArrayList answers = new ArrayList();
      List answerRanges = (List) answerRangesByType.get(type);
      for (Iterator iter2 = answerRanges.iterator(); iter2.hasNext();) {
        AnswerChecker.Range range = (AnswerChecker.Range) iter.next();
        answers.add(StringUtils.join(range.extractRange(doc), " "));
      }
      answerByType.put(type, answers);
    }
    return (answerByType);
  }

  /**
   * p is the start position, and l is the length of the target.
   * I couldn't be bothered renaming all the variables....
   */
  void spillGutsPrintout(int p, int l, Document words, int[] sequence) {
    // print detailed transitioning
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(6);
    for (int z = p - windowSize + 1; z < p + l + windowSize + 1; z++) {
      if (z > 0 && z <= words.size() + 1) {
        System.err.print("  State " + sequence[z] + " [");
        if (states[sequence[z]].type >= targetFields.length) {
          System.err.print("(END)");
        } else if (states[sequence[z]].type < 0) {
          if (z == -1) {
            System.err.print("(START)");
          } else if (z == -2) {
            System.err.print("{END}");
          }
        } else {
          String str = targetFields[states[sequence[z]].type];
          System.err.print(str.substring(0, 5) + str.substring(str.length() - 1));
        }
        System.err.print("] ");
        System.err.print("transition in " + nf.format(states[sequence[z - 1]].transition[sequence[z]]));
        if (z <= words.size()) {
          Word ttw = (Word) words.get(z - 1);
          //boolean seen = ((ShrinkedEmitMap) (states[sequence[z]].emit)).isSeen(ttw.word());
          System.err.println(" emission " + ttw.word() + " [" + //(seen ? "seen" : "unseen") + "] " +
                  nf.format(states[sequence[z]].emit.get(ttw.word())));
        } else {
          System.err.println();
        }
      }
    }
  }


  /**
   * Prints transitions and states.
   */
  public void printProbs() {
    printTransitions();
    printStates();
  }

  public void printTransitions() {
    printTransitions(states);
  }

  public static void printTransitions(State[] states) {
    System.err.println();
    System.err.println("Transition Matrix");
    System.err.println();
    System.err.println("Finish state is 0, start state is 1, " + "(bkgrnd state is 2, * = target state).");
    System.err.println();
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(2);
    nf.setMinimumFractionDigits(2);

    System.err.print("    ");
    for (int j = 0; j < states.length; j++) {
      String sname;
      if (states[j] == null) {
        System.err.println(j);
      }
      //System.exit(0);
      if (states[j].type > 0) {
        sname = "*" + Integer.toString(j);
      } else {
        sname = Integer.toString(j);
      }
      System.err.print(StringUtils.padLeft(sname + " ", 5));
    }
    System.err.println();

    for (int i = 0; i < states.length; i++) {
      String sname;
      if (states[i].type > 0) {
        sname = "*" + Integer.toString(i);
      } else {
        sname = Integer.toString(i);
      }
      System.err.print(StringUtils.padLeft(sname, 3) + " ");
      for (int j = 0; j < states.length; j++) {
        if (states[i].transition[j] == 0.0) {
          System.err.print("   - "); // 5 blank spaces
        } else if (states[i].transition[j] < 0.005) {
          System.err.print("   = ");
        } else {
          System.err.print(nf.format(states[i].transition[j]) + " ");
        }
      }
      System.err.println();
    }
    System.err.println();
  }


  /**
   * Print out a complete trellis (a state x time double array).
   * This sends it in a pretty-printed format to System.err.
   */
  public void printTrellis(String name, double[][] trellis) {
    printTrellis(name, trellis, 0, trellis[0].length - 1);
  }


  /**
   * Print out a time slice of a trellis (a state x time double array.
   * This sends it in a pretty-printed format to System.err.
   * Print times from fromTime to toTime inclusively at both ends.
   * If your fromTime or toTime are too big/small, they will be adjusted to
   * what is in the trellis.
   */
  public void printTrellis(String name, double[][] trellis, int fromTime, int toTime) {
    printTrellis(name, trellis, fromTime, toTime, 2);
  }


  /**
   * Print out a time slice of a trellis (a state x time double array.
   * This sends it in a pretty-printed format to System.err.
   * Print times from fromTime to toTime inclusively at both ends.
   * If your fromTime or toTime are too big/small, they will be adjusted to
   * what is in the trellis.
   */
  public static void printTrellis(String name, double[][] trellis, int fromTime, int toTime, int decimalPlaces) {
    if (fromTime < 0) {
      fromTime = 0;
    }
    if (trellis.length > 0 && toTime >= trellis[0].length) {
      toTime = trellis[0].length - 1;
    }
    System.err.println();
    if (name != null && !name.equals("")) {
      System.err.print(name + " ");
    }
    System.err.println("Trellis");
    System.err.println();
    System.err.println("Finish state is 0, start state is 1, " + "(bkgrnd state is 2).");
    System.err.println();
    NumberFormat nf = NumberFormat.getNumberInstance();
    nf.setMaximumFractionDigits(decimalPlaces);
    nf.setMinimumFractionDigits(decimalPlaces);

    System.err.print("    ");
    for (int j = fromTime; j <= toTime; j++) {
      System.err.print(StringUtils.padLeft(Integer.toString(j), decimalPlaces + 2) + " ");
    }
    System.err.println();

    for (int i = 0; i < trellis.length; i++) {
      System.err.print(StringUtils.padLeft(Integer.toString(i), 3) + " ");
      for (int j = fromTime; j <= toTime; j++) {
        System.err.print(nf.format(trellis[i][j]) + " ");
      }
      System.err.println();
    }
    System.err.println();
  }

  public void printStates() {
    printStates(states);
  }

  public void printStates(State[] states) {
    System.err.println();
    for (int i = 0; i < states.length; i++) {
      String stateName = null;
      if (states[i].type >= 0) {
        stateName = targetFields[states[i].type];
      }
      System.err.println(states[i].toVerboseString(i, stateName));
    }
  }

  /**
   * @return the total number of parameters (emission and transition) in
   *         the HMM.  Useful for structure learning
   */
  protected int numParameters() {
    int numParams = 0;
    // don't include the emission parameters
    /*for(int i=0; i<states.length; i++) {
      EmitMap emissions = states[i].emit;
      if(emissions != null) {
      numParams += emissions.getMap().keySet().size();
      }
    }*/
    if (structure != null && structure instanceof Structure) {
      numParams += ((Structure) structure).numParameters();
    } else {
      System.err.println("No structure???");
    }
    return numParams;
  }


  /**
   * Computes the MDL score for this HMM structure on the training corpus.
   * The MDL score is
   * <tt>logLikelihood(docs) - log(docs.size())*numParameters()/2</tt>.
   *
   * @return The MDL score for this HMM structure on the training corpus
   */
  public double mdlScore(Corpus corpus) {
    return mdlScore(corpus, logLikelihood(corpus));
  }


  /**
   * Computes the MDL score for this HMM structure on the training corpus.
   * This assumes that you already know the loglikelihood.  This routine
   * mainly exists so that one doesn't have to recalculate the
   * loglikelihood, which is potentially expensive.  The MDL score is
   * <tt>logLikelihood(docs) - log(docs.size())*numParameters()/2</tt>.
   *
   * @return The MDL score for this HMM structure on the training corpus
   */
  public double mdlScore(Corpus corpus, double logLikelihood) {
    int numParams = numParameters();
    return logLikelihood - 0.5 * Math.log(corpus.size()) * numParams;
  }


  /**
   * Calculate the loglikelihood of the passed in corpus according to
   * to the model (stored in class variables).
   * This now returns Double.NEGATIVE_INFINITY if there is at least one
   * document in the Corpus that the model cannot generate.
   *
   * @param trainDocs The corpus to work out the log likelihood of
   * @return log P(trainDocs|model)
   */
  public double logLikelihood(Corpus trainDocs) {
    return logLikelihood(trainDocs, true);
  }


  /**
   * Calculate the loglikelihood of the passed in corpus according to
   * to the model (stored in class variables).
   *
   * @param observedDocs The corpus to work out the log likelihood of
   * @param useScaling   True means use scaling coefficients
   * @return log P(trainDocs|model)
   */
  public double logLikelihood(Corpus observedDocs, boolean useScaling) {
    return new HMMTrainer(false).logLikelihood(observedDocs, useScaling, true);
  }


  /**
   * Calculates and returns Log[P(c|w)] for the given Corpus.
   * This is the conditional probability of generating the correct sequence
   * of state types for each word given the corpus of words.
   */
  public double logConditionalLikelihood(Corpus observedDocs) {
    return (new HMMTrainer(false).logConditionalLikelihood(observedDocs));
  }


  /**
   * Print out a representation of the HMM.
   * This should be rewritten to utilize the better print methods.
   *
   * @return HMM representation
   */
  public String toString() {
    StringBuffer sb = new StringBuffer("HMM{" + states.length + " states: ");
    for (int i = 0; i < states.length; i++) {
      sb.append(states[i].toString());
      if (i != states.length - 1) {
        sb.append("; ");
      }
    }
    sb.append("}");
    return sb.toString();
  }


  private static boolean checkStochasticMatrix(State[] states) {
    boolean stochastic = true;
    for (int i = 0; i < states.length; i++) {
      double sum = 0.0;
      for (int j = 0; j < states[i].transition.length; j++) {
        sum += states[i].transition[j];
      }
      if (Math.abs(1.0 - sum) > 0.0001) {
        System.err.println("Bung matrix: state " + i + " not stochastic; sum = " + sum);
        System.err.print("[ ");
        for (int j = 0; j < states[i].transition.length; j++) {
          System.err.print(states[i].transition[j] + " ");
        }
        System.err.println("]");
        stochastic = false;
      }
    }
    return stochastic;
  }


  /**
   * Returns the current array of states for this HMM.
   */
  public State[] getStates() {
    return (states);
  }

  /**
   * Returns this HMM's strategy for handling unknown words.
   */
  public int getUnseenMode() {
    return (unseenMode);
  }

  /**
   * Returns this HMM's model for counting unknown words probabilities.
   */
  public int getUnkModel() {
    return (unkModel);
  }

  /**
   * Returns this HMM's source for estimating the probability of seeing an unknown word in each state.
   */
  public int getUnseenProbSource() {
    return (unseenProbSource);
  }

  /**
   * Returns this HMM's source for estimating the featural breakdown of unknown words.
   */
  public int getFeatureSource() {
    return featureSource;
  }

  /**
   * Returns the words (Strings) this HMM has seen mapped to their observed
   * counts (Double).
   */
  public ClassicCounter<String> getVocab() {
    return vocab;
  }

  /**
   * Returns the Feature used to train this HMM.
   */
  public Feature getFeature() {
    return feat;
  }

  /**
   * Returns a parameter vector of the current transitions and emissions for this
   * HMM. The vector is laid out as blocks of outgoing transitions and emissions
   * for each state (no emissions for start/end):
   * <tt>[t0][t1][t2][e2][t3][e3]...[tn][en]</tt>. Thus its length will be
   * <tt>numStates*numStates + (numStates-2)*vocab.size()</tt>.
   *
   * @return parameter vector of transitions and emissions for this HMM
   */
  public double[] getParams() {
    int numStates = states.length;
    int numParams = numStates * numStates + (numStates - 2) * vocab.size();
    double[] x = new double[numParams];
    int index = 0;

    for (int i = 0; i < numStates; i++) {
      State s = states[i];
      for (int j = 0; j < numStates; j++) {
        x[index++] = s.transition[j];
      }
      if (i != State.STARTIDX && i != State.FINISHIDX && !(s.emit instanceof ConstantEmitMap)) {
        for (Iterator iter = vocab.keySet().iterator(); iter.hasNext();) {
          x[index++] = s.emit.get((String) iter.next());
        }
      }
    }

    return (x);
  }

  /**
   * Sets the current transition and emission parameters of this HMM. The
   * parameter vector should be one you got from calling {@link #getParams}.
   *
   * @param x parameter vector from {@link #getParams}
   */
  public void setParams(double[] x) {
    int numStates = states.length;
    int index = 0; // index in x array

    for (int i = 0; i < numStates; i++) {
      State s = states[i]; // current state
      for (int j = 0; j < numStates; j++) {
        s.transition[j] = x[index++];
      }
      if (i != State.STARTIDX && i != State.FINISHIDX && !(s.emit instanceof ConstantEmitMap)) {
        for (Iterator iter = vocab.keySet().iterator(); iter.hasNext();) {
          s.emit.set((String) iter.next(), x[index++]);
        }
      }
    }
  }

  /**
   * Returns an unseen mode by name.
   * Legal values: nonexistent, unk_low_counts, hold_out_mass, use_char_ngrams.
   *
   * @throws IllegalArgumentException if the unseenMode is not a legal value.
   */
  public static int parseUnseenMode(String unseenMode) {
    if (unseenMode.equals("nonexistent")) {
      return (UNSEENMODE_NONEXISTENT);
    }
    if (unseenMode.equals("unk_low_counts")) {
      return (UNSEENMODE_UNK_LOW_COUNTS);
    }
    if (unseenMode.equals("hold_out_mass")) {
      return (UNSEENMODE_HOLD_OUT_MASS);
    }
    if (unseenMode.equals("use_char_ngrams")) {
      return (UNSEENMODE_USE_CHAR_NGRAMS);
    }
    throw(new IllegalArgumentException("Illegal unseenMode: " + unseenMode));
  }

  /**
   * Returns an unknown model by name.
   * Legal values: single_unk, featural_decomp
   *
   * @throws IllegalArgumentException if the unkModel is not a legal value.
   */
  public static int parseUnkModel(String unkModel) {
    if (unkModel.equals("single_unk")) {
      return (UNKMODEL_SINGLE_UNK);
    }
    if (unkModel.equals("featural_decomp")) {
      return (UNKMODEL_FEATURAL_DECOMP);
    }
    throw(new IllegalArgumentException("Illegal unkModel: " + unkModel));
  }

  /**
   * Returns a source by name (for unseenProbSource or featureSource).
   * Legal values: singletons, held_out
   *
   * @throws IllegalArgumentException if the unseenMode is not a legal value.
   */
  public static int parseSource(String source) {
    if (source.equals("singletons")) {
      return (SOURCE_SINGLETONS);
    }
    if (source.equals("held_out")) {
      return (SOURCE_HELD_OUT);
    }
    throw(new IllegalArgumentException("Illegal source: " + source));
  }

  public void checkNormalized() {
    for (int i = 0; i < states.length; i++) {
      double tot = states[i].transitionSum();
      if (Math.abs(tot - 1.0) > 1e-5) {
        System.err.println("State " + i + " not normalized: transitions sum to " + tot);
      }
    }
  }


  /**
   * Inner class to handle training, likelihood, etc.
   */
  protected class HMMTrainer implements DiffFunction {
    /**
     * Training corpus.
     */
    protected Corpus trainDocs;

    private static final int MAX_ITER = 50000; // 100;   // was: 25
    private static final int MIN_ITER = 10;  // was: 5

    /**
     * for shrinkage: uniform distribution over all words
     */
    private PlainEmitMap uniform;

    /**
     * Shrinked emissions for union of all states of a particular type.
     * The background parent is targetParents[0].
     */
    private PlainEmitMap[] targetParents;

    /**
     * for training: alpha[state][time]
     */
    protected double[][] alpha;
    protected double[][] beta;
    protected double[] scale;

    private int numStates = states.length;
    private Set badDocs; // docs that were zero likelihood in constrained expectations (Integer indices)

    // for doing state transition expectations over mutiple trainDocs
    // expectation of i->j transitions gamma_ij
    private double[][] totalP = new double[numStates][numStates];
    // total expected movements out of a state: gamma_i
    private double[] totalFrom = new double[numStates];

    // same thing for observation probs
    // really only part of the arrays marked // PARTIAL are filled,
    // but we allocate for all states so as to avoid reindexing

    //  summed mass for each word in each state sum_t P(s_jt, o_t=w|o)
    ClassicCounter[] totalOP = new ClassicCounter[numStates];
    // summed mass for each word in each state class
    ClassicCounter[] totalTargetOP = new ClassicCounter[targetFields.length];
    // summed mass for all outputs from this state class
    double[] totalTargetOD = new double[targetFields.length]; // inits to 0.0

    // mass of seen words for each state
    double[] totalSeenP = new double[numStates]; // inits to 0.0
    // mass of seen words in shrinked state-type classes
    double[] totalParentSeenP = new double[targetFields.length]; // inits to 0
    // mass of unseen word feature representation equivalence classes
    FeatureMap[] featureMaps = new FeatureMap[numStates];
    // mass of unseen word feature rep in shrinked state-type classes
    FeatureMap[] parentFeatureMaps = new FeatureMap[targetFields.length];

    static final double CONVERGE_SIZE = 0.005; // was 0.01; for reestimation
    static final double LL_CONVERGE_SIZE = 1e-8; // relative change in LL
    static final double TOLERANCE = 0.001; // was 0.01; for checks things add
    protected static final int MAX_SHR_ITER = 20;  // was: 10

    private static final boolean ignoreUngeneratableDocs = true; // for logLike
    private static final boolean printAllTrellises = false; // output intensive

    private double globalMaxChange = 0.0; // for debugging training
    private double pseudoTransitionsCount;
    private double pseudoEmissionsCount;
    private double pseudoUnknownsCount;
    private static final boolean convergenceOnLikelihood = true;

    private boolean verbose;


    public HMMTrainer(boolean verbose) {
      this(verbose, 0.0, 0.0, 0.0);
    }

    public HMMTrainer(boolean verbose, double pseudoTransitionsCount, double pseudoEmissionsCount, double pseudoUnknownsCount) {
      this.verbose = verbose;
      this.pseudoTransitionsCount = pseudoTransitionsCount;
      this.pseudoEmissionsCount = pseudoEmissionsCount;
      this.pseudoUnknownsCount = pseudoUnknownsCount;
      // alpha(i,t) = P(o_{0, ..., t-2}, X_t =i|mu)
      alpha = new double[numStates][];
      // beta(i,t) = P(o_{t-1, tsize|X_t=i,mu)
      beta = new double[numStates][];

    }

    /**
     * Returns expected number of transitions from each state (gammas)
     */
    public double[] getGammas() {
      return totalFrom;
    }

    /**
     * Sets the training corpus
     */
    public void setTrainingCorpus(Corpus train) {
      trainDocs = train;
    }


    /**
     * Sets up the initial emissions of a created HMM using the training
     * corpus trainDocs. If <tt>initStateEmissions</tt> is false, the vocab
     * and starter vocabs are computed as normal but the actual state emit maps
     * aren't touched. This is useful for training on top of an already-trained
     * HMM where you don't want to reset the parameters.
     */
    private void initEmissions(boolean initStateEmissions) {

      vocab = new ClassicCounter();
      Counters.addInPlace(vocab, trainDocs.getVocab());

      if (unseenMode == UNSEENMODE_UNK_LOW_COUNTS) {
        addUnknownCounts(vocab);
      }

      ClassicCounter<String> uniformTable = new ClassicCounter<String>();
      for (String key : vocab.keySet()) {
        uniformTable.incrementCount(key, 1.0 / vocab.size());
      }
      //double uniformInit=1.0/vocab.size();
      uniform = new PlainEmitMap(uniformTable);

      // map for starting probabilities for emissions for each state (unigram)
      ClassicCounter<String> starter = new ClassicCounter<String>();
      double trainWordCount = trainDocs.wordCount();
      for (String word : vocab.keySet()) {
        // word is current word in vocab
        //uniformTable.incrementCount(word,uniformInit);
        // unigram MLE
        starter.incrementCount(word, vocab.getCount(word) / trainWordCount);
      }

      if (targetParents == null || initStateEmissions) {
        // init per-target shrinkage emissions to MLE unigrams
        // skipped if re-training after doing shrinkage
        targetParents = new PlainEmitMap[targetFields.length];
        for (int i = 0; i < targetParents.length; i++) {
          targetParents[i] = new PlainEmitMap(starter);
        }
      }

      // leave start, end states with no emissions (null emit map)
      // otherwise non-background states have a ConstantEmitMap in context HMMs
      // otherwise unigram MLE map, perhaps with unseens
      if (initStateEmissions) {
        FeatureMap dummyFM = new FeatureMap(feat);
        for (int i = State.BKGRNDIDX; i < states.length; i++) {
          if (hmmType == CONTEXT_HMM && states[i].type > 0) {
            states[i].emit = new ConstantEmitMap(targetFields[states[i].type] + "STATE");
          } else if (unseenMode == UNSEENMODE_HOLD_OUT_MASS) {
            UnseenEmitMap uem = new UnseenEmitMap(starter);
            uem.setFeatureMap(dummyFM, trainDocs.wordCount());
            states[i].emit = uem;
          } else if (unseenMode == UNSEENMODE_USE_CHAR_NGRAMS) {
            CharSequenceEmitMap csem = new CharSequenceEmitMap(new PlainEmitMap(starter)); // JS TODO: add maxNGramLength
            states[i].emit = csem;
          } else {
            states[i].emit = new PlainEmitMap(starter);
          }
        }
      }
    }


    /**
     * Adds one count for every possible unknown word (either just UNK or all
     * the possible featural decomps). This way you'll never get 0 probability
     * while testing (well--almost never).
     */
    private void addUnknownCounts(ClassicCounter vocab) {
      if (unkModel == UNKMODEL_SINGLE_UNK) {
        // just add a count for UNK
        if (verbose) {
          System.err.println("Adding extra count for single unk to vocab to prevent 0-prob emissions");
        }
        vocab.incrementCount(UnknownWordCollapser.defaultUnknownWord);
      } else if (unkModel == UNKMODEL_FEATURAL_DECOMP) {
        // add a count for every possible unknown featural pattern
        if (verbose) {
          System.err.println("Adding extra count each decomp unk to vocab to prevent 0-prob emissions");
        }
        FeatureValue[] allValues = feat.allValues();
        for (int i = 0; i < allValues.length; i++) {
          vocab.incrementCount(UnknownWordCollapser.defaultUnknownWord + allValues[i]);
        }
      }
    }


    public double globalMaxChange() {
      return globalMaxChange;
    }

    /**
     * Computes a confidence score for labeling the given range in the given
     * doc as the given target type. This score is the ratio of two probabilities:
     * (1) the sum of all sequence probs that stay in target states throughtout
     * the target range; and (2) the sum of all sequence probs that stay out of
     * target states throughout that range. This is like the odds of the given
     * range being a target sequence, except we're ignoring all cases where
     * only some of the given range was in target states. In any case, higher
     * numbers mean greater confidence that this is a good target. Scores should
     * range from 0 to infinity.
     */
    public double computeTargetScore(Document doc, int targetType, AnswerChecker.Range targetRange) {
      double[][] alphaTarget = new double[numStates][2]; // prev and current probs for each state (target seqs)
      double[][] alphaNonTarget = new double[numStates][2]; // prev and current probs for each state (non-target seqs)
      int prev = 0, cur = 1; // these two flipflop so we can just use 2 time slices as we go along

      System.err.println("computeTargetScore: " + targetRange.extractRange(doc));
      java.text.DecimalFormat df = new java.text.DecimalFormat("0.00E000");

      for (int i = 0; i < numStates; i++) {
        // prob of generating through first target state (+1 is going from type to state)
        if (states[i].type == targetType) {
          alphaTarget[i][prev] = alpha[i][targetRange.getFrom() + 1];
        } else {
          alphaNonTarget[i][prev] = alpha[i][targetRange.getFrom() + 1];
        }
      }

      for (int i = 0; i < numStates; i++) {
        System.err.print(df.format(alphaTarget[i][prev]) + " ");
      }
      System.err.print("|| ");
      for (int i = 0; i < numStates; i++) {
        System.err.print(df.format(alphaNonTarget[i][prev]) + " ");
      }
      System.err.println();

      // mini-forward algorithm where you have to stay exclusively in or out of the target states
      for (int t = targetRange.getFrom() + 1; t < targetRange.getTo(); t++) {
        TypedTaggedWord ttw = (TypedTaggedWord) doc.get(t);
        for (int j = 0; j < states.length; j++) {
          double targetSum = 0.0;
          double nonTargetSum = 0.0;
          for (int i = 0; i < states.length; i++) {
            if (states[i].type == targetType) {
              targetSum += alphaTarget[i][prev] * states[i].transition[j];
            } else {
              nonTargetSum += alphaNonTarget[i][prev] * states[i].transition[j];
            }
          }
          if (states[j].emit != null) {
            if (states[j].type == targetType) {
              alphaTarget[j][cur] = targetSum * states[j].emit.get(ttw.word());
            } else {
              alphaNonTarget[j][cur] = nonTargetSum * states[j].emit.get(ttw.word());
            }
          }
        }
        for (int i = 0; i < numStates; i++) {
          System.err.print(df.format(alphaTarget[i][cur]) + " ");
        }
        System.err.print("|| ");
        for (int i = 0; i < numStates; i++) {
          System.err.print(df.format(alphaNonTarget[i][cur]) + " ");
        }
        System.err.println();

        cur ^= 1;
        prev ^= 1; // flip values
      }

      for (int i = 0; i < numStates; i++) {
        System.err.println("beta[" + i + "] = " + beta[i][targetRange.getTo() + 1]);
        // prob of generating rest of doc from here (+1 is going from type to state)
        if (states[i].type == targetType) {
          alphaTarget[i][cur] = alphaTarget[i][prev] * beta[i][targetRange.getTo() + 1];
        } else {
          alphaNonTarget[i][cur] = alphaNonTarget[i][prev] * beta[i][targetRange.getTo() + 1];
        }
      }
      for (int i = 0; i < numStates; i++) {
        System.err.print(df.format(alphaTarget[i][cur]) + " ");
      }
      System.err.print("|| ");
      for (int i = 0; i < numStates; i++) {
        System.err.print(df.format(alphaNonTarget[i][cur]) + " ");
      }
      System.err.println();


      double totalTargetProb = 0.0;
      double totalNonTargetProb = 0.0;
      for (int i = 0; i < numStates; i++) {
        // sum sequence probs for target and non-target sequences
        totalTargetProb += alphaTarget[i][cur];
        totalNonTargetProb += alphaNonTarget[i][cur];
      }
      System.err.println("totalTargetProb: " + totalTargetProb);
      System.err.println("totalNonTargetProb: " + totalNonTargetProb);
      System.err.println("--> score: " + (totalTargetProb / totalNonTargetProb));

      // return ratio of probs
      if (totalTargetProb == 0) {
        return (0); // prevents 0/0
      }
      return (totalTargetProb / totalNonTargetProb);
    }

    /**
     * Returns a Counter for each state mapping words to their expectation of
     * being emitted in that state according to the given corpus. If
     * <tt>useSingletons</tt> is true, only words in the given corpus that occurred
     * exactly once in the training corpus are counted, otherwise all words are
     * counted (this is what you want for a held-out corpus).
     */
    private ClassicCounter<String>[] computeExpectedEmissions(Corpus docs, boolean useSingletons) {
      ClassicCounter[] expectedEmissions = new ClassicCounter[states.length];
      for (int i = 0; i < expectedEmissions.length; i++) {
        expectedEmissions[i] = new ClassicCounter();
      }

      // pSGT[j] = P(Sj|T=t, O, mu) = gamma_j(t): probability of
      // State Given Time for model and observations (recomputed for each t).
      // We've now corrected to use alpha and beta and calculate gamma
      // variables as in M&S ch. 9.
      // gamma_i(t) = P(X_t=i|O,mu) = scaled alpha_i(t)beta_i(t)
      // gamma_i(t) = alpha_i(t)beta_i(t)/sum_j alpha_j(t)beta_j(t)
      double[] pSGT = new double[numStates];

      for (int d = 0, dsize = docs.size(); d < dsize; d++) {
        Document doc = (Document) docs.get(d);

        if (!forwardAlgorithm(doc, true)) {
          if (verbose) {
            System.err.println("HMM.computeExpectedEmissions: warning: " + "couldn't generate held out document " + d + ".");
          }
        } else {
          backwardAlgorithm(doc, true);
          int numTimes = doc.size() + 2;
          // start with time 1 and first observation; just do states with obs
          for (int t = 1; t < numTimes - 1; t++) {
            TypedTaggedWord ttw = (TypedTaggedWord) doc.get(t - 1);
            double total = 0.0;
            for (int k = 0; k < states.length; k++) {
              pSGT[k] = alpha[k][t] * beta[k][t];  // assuming scaling
              if (!useSingletons || vocab.getCount(ttw.word()) == 1) {
                expectedEmissions[k].incrementCount(ttw.word(), pSGT[k]);
              }
              total += pSGT[k];
            }
            if (sanityCheck && Math.abs(total - 1.0) > TOLERANCE) {
              System.err.println("Bung: total in shrinkUnseen is " + total);
              if (SloppyMath.isDangerous(total)) {
                System.err.println("Total is dangerous (" + total + ") for doc " + d + " time " + t + "! TypedTaggedWord is " + ((ttw == null) ? "null" : ttw.word()));
                printTrellis("Forward", alpha, t - 5, t + 2, 5);
                printTrellis("Backward", beta, t - 2, t + 5, 5);
                printStateVector("Scaling", scale);
                // do it again with debugging
                // printAllTrellises = true;
                // forwardAlgorithm(doc, true);
                for (int k = 0; k < states.length; k++) {
                  if (states[k].emit != null) {
                    System.err.println("P(" + ttw.word() + "|s=" + k + ") = " + states[k].emit.get(ttw.word()));
                  }
                }
              }
            } // if sanityCheck
          } // for times t
        }
      } // for each held out document
      return (expectedEmissions);
    }

    /**
     * Work out shrinkage and unseen parameters.
     * Work out shrinkage (linear interpolation) weights for emissions
     * between state-specific and more general models.
     * Set for each state the percentage of the time that they are generating
     * seen versus unseen words based on expectations for being in different
     * states and whether words in the held-out data were seen in the training
     * data.  These two operations are done together for efficiency, since
     * they both involved traversal of the held out corpus.
     */
    private void estimateShrinkUnseen(Corpus docs, boolean useSingletons, boolean shrinkage, boolean unseenProb) {
      if (verbose) {
        System.err.println("estimateShrinkUnseen: unseenProb is " + unseenProb);
      }

      ShrinkedEmitMap[] semits = new ShrinkedEmitMap[numStates];

      if (shrinkage) {
        for (int i = State.BKGRNDIDX; i < states.length; i++) {
          if (!(states[i].emit instanceof ConstantEmitMap)) {
            if (false) {
              System.err.println("**** estimateShrinkUnseen ****");
              System.err.println("i: " + i);
              System.err.println("semits.length: " + semits.length);
              System.err.println("states.length: " + states.length);
              System.err.println("targetParents.length: " + targetParents.length);
              System.err.println("states[i].type: " + states[i].type);
            }

            semits[i] = new ShrinkedEmitMap(states[i].emit, targetParents[states[i].type], uniform);
          }
        }

        // start using the ShrinkedEmitMaps (need this to measure LL change)
        for (int i = State.BKGRNDIDX; i < states.length; i++) {
          if (!(states[i].emit instanceof ConstantEmitMap)) {
            states[i].emit = semits[i];
          }
        }
      }

      NumberFormat nf = new DecimalFormat("0.000"); // easier way!

      if (spillEmissions) {
        System.err.println("State Emissions before Reestimation");
        for (int i = State.BKGRNDIDX; i < states.length; i++) {
          if (!(states[i].emit instanceof ConstantEmitMap)) {
            System.err.println();
            System.err.println("*** State " + i + " ****");
            states[i].emit.printEmissions(new PrintWriter(System.err, true), false);
          }
        }
      }

      // seen/unseen expectations for each state
      double[] totalSeen = new double[numStates];
      double[] totalUnseen = new double[numStates];
      double seenSmoothing = 1.0; // false counts to add to seen and unseen mass

      // shrinkage expectations for each state
      double[] beta1 = new double[numStates];
      double[] beta2 = new double[numStates];
      double[] beta3 = new double[numStates];
      double shrinkageSmoothing = 0.0001; // give small nonzero weight in case state is unused

      int iterations = 0;
      double maxChange; // max parameter change from last iteration
      double lastLogLikelihood = logLikelihood(docs, true, true); // logLike after last round of EM
      if (verbose) {
        System.err.println("logLike before unseen estimation: " + lastLogLikelihood);
      }
      do { // iterations loop
        iterations++;
        if (verbose) {
          System.err.println("Unseen estimation iteration " + iterations);
        }

        maxChange = 0.0; // measures whether parameters have converged

        // E-step
        ClassicCounter<String>[] expectedEmissions = hmmt.computeExpectedEmissions(docs, useSingletons);
        for (int i = 0; i < states.length; i++) {
          if (states[i].emit == null || states[i].emit instanceof ConstantEmitMap) {
            continue;
          }

          // seen/unseen expectatations for each state
          if (unseenProb) {

            // pull real emit map out of shrinked emit map if necessary
            EmitMap emit = states[i].emit;
            if (emit instanceof ShrinkedEmitMap) {
              emit = ((ShrinkedEmitMap) emit).getBase();
            }

            // do one iteration of EM for seenP
            maxChange = Math.max(maxChange, emit.tuneParameters(expectedEmissions[i], getHMM()));
          }

          // shrinkage expectations
          if (shrinkage) {
            //E-step
            beta1[i] = beta2[i] = beta3[i] = shrinkageSmoothing;
            boolean seenBung = false; // only used for sanity check
            for (Iterator iter = expectedEmissions[i].keySet().iterator(); iter.hasNext();) {
              String word = (String) iter.next();
              double weight = expectedEmissions[i].getCount(word);
              beta1[i] += weight * semits[i].get1(word) / semits[i].get(word);
              beta2[i] += weight * semits[i].get2(word) / semits[i].get(word);
              beta3[i] += weight * semits[i].get3(word) / semits[i].get(word);
              if (sanityCheck && !seenBung && SloppyMath.isVeryDangerous(beta1[i])) {
                seenBung = true;
                System.err.println("Dangerous: beta1 is " + beta1[i]);
                //System.err.println("Doc is " + d + " of " + hsize + "; " +
                //"state i is " + i + " time is " + t + "; ttw is " +
                //word + (seenInTraining ? " [seen]": " [unseen]"));
                //System.err.print("pSGT="+nf.format(pSGT[i])+"; ");
                System.err.println("get1: " + semits[i].get1(word) + "; get2: " + semits[i].get2(word) + "; get3: " + semits[i].get3(word));
              }
            }

            // M-step
            double sumBeta = beta1[i] + beta2[i] + beta3[i];
            beta1[i] /= sumBeta;
            beta2[i] /= sumBeta;
            beta3[i] /= sumBeta;
            maxChange = Math.max(maxChange, Math.abs(beta1[i] - semits[i].lambda1));
            semits[i].lambda1 = beta1[i];
            maxChange = Math.max(maxChange, Math.abs(beta2[i] - semits[i].lambda2));
            semits[i].lambda2 = beta2[i];
            maxChange = Math.max(maxChange, Math.abs(beta3[i] - semits[i].lambda3));
            semits[i].lambda3 = beta3[i];
            if (verbose) {
              System.err.println("Shrink reest state " + i + " iter " + iterations + ". Max ch = " + nf.format(maxChange) + ", new lambdas: " + nf.format(beta1[i]) + ", " + nf.format(beta2[i]) + ", " + nf.format(beta3[i]));
            }
          }
        } // for states i

        // stop when log likelihood or parameters converge
        if (convergenceOnLikelihood) {
          double logLike = logLikelihood(docs, true, true);
          // computes % change in log likelihood since last iteration
          // want: both numerator and denominator negative --> positive
          double logLikeImprove = (lastLogLikelihood == 0.0) ? 0.0 : (lastLogLikelihood - logLike) / lastLogLikelihood;
          if (verbose) {
            System.err.println("logLike after " + iterations + " iterations: " + logLike + " (" + (logLikeImprove * 100) + "% change)");
          }
          if (logLikeImprove < LL_CONVERGE_SIZE) {
            if (verbose) {
              System.err.println("estimateShrinkUnseen: log likelihood converged after " + iterations + " iterations");
            }
            break; // LL converged
          }
          lastLogLikelihood = logLike; // save for next iter
        } else {
          if (verbose) {
            System.err.println("Max Change = " + nf.format(maxChange));
          }
          if (maxChange <= CONVERGE_SIZE) {
            if (verbose) {
              System.err.println("estimateShrinkUnsen: params converged after " + iterations + " iterations");
            }
            break; // params converged
          }
        }
      } while (iterations < MAX_SHR_ITER);

      if (iterations == MAX_SHR_ITER && verbose) // estimation was halted
      {
        System.err.println("estimateShrinkUnseen: max iterations exceeded");
      }
    }

    /**
     * Performs standard baum-welch training over the full set of
     * training documents until convergence.
     * Now complicated to save each iteration.
     */
    public void forwardBackward() {
      boolean converged = false;
      boolean likelihoodConverged = false;
      int numIterations = 0;
      List params = new ArrayList(); // double[] of params for each iteration
      List f1s = new ArrayList(); // double (training F1) for each iteration
      double lastConstrainedLogLikelihood = 0.0;
      double logLikeImprove = 0.0;
      double lastLogLikeDiff = 0.0;

      // keep reestimating until convergence or a fixed upper bound
      while (numIterations < MAX_ITER) {
        checkNormalized();
        if (josephStuff) {
          params.add(getParams());
        }
        double logLike = expectations(true, true);

        if (numIterations > 0) {
          // computes % change in log likelihood since last iteration
          // want: both numerator and denominator negative --> positive
          // new: to stop us stopping while easing off a saddle, don't stop
          // if below threshold but diff is increasing
          logLikeImprove = (lastConstrainedLogLikelihood == 0.0) ? 0.0 : (lastConstrainedLogLikelihood - logLike) / lastConstrainedLogLikelihood;
          likelihoodConverged = logLikeImprove < LL_CONVERGE_SIZE &&
                                (lastConstrainedLogLikelihood - logLike) < lastLogLikeDiff;
          lastLogLikeDiff = lastConstrainedLogLikelihood - logLike;
          if (logLikeImprove < -LL_CONVERGE_SIZE) {
            System.err.println("expectations: EM MONOTONICITY FAILURE!");
          }
        }
        if (verbose) {
          NumberFormat nf = NumberFormat.getNumberInstance();
          nf.setMaximumFractionDigits(3);
          System.err.print("Train loglike after " + numIterations + " joint reest. log P(O,C|mu) = " + nf.format(logLike));
          if (numIterations > 0) {
            System.err.print(" [" + nf.format(logLikeImprove * 100) + "% change]");
          }
          System.err.println();
          System.err.println("Training CLL: " + nf.format(logConditionalLikelihood(trainDocs)));
        }
        // keeps likelihood from this iteration for future comparison
        lastConstrainedLogLikelihood = logLike;

        if (josephStuff && verbose) {
          System.err.println("log likelihood on training data after " + numIterations + " iterations of joint training: " + logLike);
          System.err.println("conditional log likelihood on training data after " + numIterations + " iterations of joint training: " + logConditionalLikelihood(trainDocs));
          double f1 = new HMMTester(getHMM()).test(trainDocs, null, true, false, false);
          System.err.println("F1 on training data after " + numIterations + " iterations of joint training: " + f1);
          f1s.add(new Double(f1));
          if (testDocs != null) {
            System.err.println("log likelihood on test data after " + numIterations + " iterations of joint training: " + logLikelihood(testDocs, true, true));
            System.err.println("conditional log likelihood on test data after " + numIterations + " iterations of joint training: " + logConditionalLikelihood(testDocs));
            System.err.println("F1 on test data after " + numIterations + " iterations of joint training: " + new HMMTester(getHMM()).test(testDocs, null, true, false, false));
          }
        }

        if (numIterations >= MIN_ITER && convergenceOnLikelihood && likelihoodConverged) {
          // break out as soon as expectation doesn't change enough
          converged = true;
          break;
        }

        boolean parametersConverged = maximize();
        numIterations++;
        if (verbose) {
          System.err.println("Parameter reestimate " + (numIterations) + ". Max Change = " + globalMaxChange);
          printTransitions();
        }

        if (numIterations >= MIN_ITER && !convergenceOnLikelihood && parametersConverged) {
          // break out as soon as parameters don't change enough
          converged = true;
          break;
        }

      } // while not converged or halted

      if (verbose) {
        if (converged) {
          System.err.println("Converged after " + numIterations + " iterations.");
        } else {
          System.err.println("Stopping after " + MAX_ITER + " iterations.");
        }
      }

      if (josephStuff) {
        int bestIteration = 0;
        double bestF1 = Double.NEGATIVE_INFINITY;
        int window = 1; // number of entries on each side in moving average
        for (int i = window; i < numIterations - window; i++) {
          double avgF1 = 0; // average F1 over the window
          for (int j = i - window; j <= i + window; j++) {
            avgF1 += ((Double) f1s.get(j)).doubleValue();
          }
          avgF1 /= (window * 2 + 1);
          if (avgF1 > bestF1) {
            bestF1 = avgF1;
            bestIteration = i;
          }
        }
        System.err.println("Using parameters from iteration " + bestIteration + " of " + numIterations + " (best training F1)");
        getHMM().setParams((double[]) params.get(bestIteration));

        // save 4 sets of params to train conditionally from
        startCondParams[0] = (double[]) params.get(0); // before any EM
        startCondParams[1] = (double[]) params.get(1); // after one round of EM
        startCondParams[2] = (double[]) params.get(bestIteration); // iter of EM with best training F1
        startCondParams[3] = (double[]) params.get(params.size() - 1); // end of EM (LL converged)
      }
    }

    // JS: TAKE ME OUT - params from EM to train cond from (0 iters, 1, best, all)
    public double[][] startCondParams = new double[4][];


    /**
     * Calculate the expectations of state transitions and emissions
     * and leave them in the totalP, totalFrom etc. arrays.
     * Constrained to respectTypes or not as set.
     *
     * @param useScaling True means to use scaling coefficients. This is vital
     *                   for all but toy problems, as otherwise numerical underflow occurs.
     * @return logLikelihood of the data under this configuration
     */
    private double expectations(boolean useScaling, boolean respectTypes) {
      if (respectTypes) {
        badDocs = new HashSet();
      }

      // for doing state transition expectations over mutiple trainDocs
      // expectation of i->j transitions gamma_ij
      totalP = new double[numStates][numStates];
      // total expected movements out of a state: gamma_i
      totalFrom = new double[numStates];

      // summed mass for all outputs from this state class
      totalTargetOD = new double[targetFields.length]; // inits to 0.0

      // mass of seen words for each state
      totalSeenP = new double[numStates]; // inits to 0.0
      // mass of seen words in shrinked state-type classes
      totalParentSeenP = new double[targetFields.length]; // inits to 0

      for (int i = 0; i < numStates; i++) {
        totalOP[i] = new ClassicCounter();
        featureMaps[i] = new FeatureMap(feat);
      }

      for (int i = 0; i < targetFields.length; i++) {
        totalTargetOP[i] = new ClassicCounter();
        parentFeatureMaps[i] = new FeatureMap(feat);
      }

      double logLike = 0.0;

      // go through each document
      for (int d = 0, tsize = trainDocs.size(); d < tsize; d++) {

        if (!respectTypes && badDocs.contains(Integer.valueOf(d))) {
          if (verbose) {
            System.err.println("Skipping doc " + d + " because constrained expectations couldn't generate it");
          }
          continue;
        }

        Document doc = (Document) trainDocs.get(d);
        // have start, end, and one state per word emitted
        // so times 1 .. docSize correspond to observations 0 to docSize - 1
        int numTimes = doc.size() + 2;

        // one can get rid of these arrays if one doesn't want to support
        // non-scaled usage, as can then directly sum into totalP!
        double[][] thisP = new double[numStates][numStates];
        double[] thisFrom = new double[numStates];

        // System.err.println("numTimes is " + numTimes + "; doc.size() is " +
        //          doc.size() + "; numStates is " + numStates);

        // the alphas and beta go to all zeroes when this HMM CAN'T produce this
        // document in this case, we do not change any of the probabilities
        // (though in a way this is wierd: means 0 likelihood of corpus...).
        boolean zeroed = !forwardAlgorithm(doc, useScaling, respectTypes);
        if (printAllTrellises) {
          printTrellis("Forward for doc " + d, alpha, 0, 9, 6);
          if (useScaling) {
            printStateVector("Scaling for doc " + d, scale);
          }
        }

        if (!zeroed) {
          // Baum-Welch or EM
          backwardAlgorithm(doc, useScaling, respectTypes);
          if (printAllTrellises) {
            printTrellis("Backward for doc " + d, beta, numTimes - 10, numTimes - 1, 6);
          }
          for (int t = 0; t < numTimes - 1; t++) {
            TypedTaggedWord ttw = null;
            if (t < numTimes - 2) {
              ttw = (TypedTaggedWord) doc.get(t);  // (t+1)-1
            }

            double sumAll = 0.0;
            for (int i = 0; i < numStates; i++) {
              double sumTerm = 0.0;   // sanity check XXXX debug
              for (int j = 0; j < numStates; j++) {
                double term;
                if (ttw == null) {
                  if (states[j].emit == null) {
                    // we're going to end state
                    term = alpha[i][t] * states[i].transition[j] * beta[j][t + 1];
                  } else {
                    term = 0.0;
                  }
                } else if (ttw.type() != states[j].type || states[j].emit == null) {
                  term = 0.0;
                } else {
                  term = alpha[i][t] * states[i].transition[j] * states[j].emit.get(ttw.word()) * beta[j][t + 1];
                }
                if (useScaling) {
                  term *= scale[t + 1];
                }
                thisP[i][j] += term;
                sumTerm += term;
                if (sanityCheck && Double.isNaN(term)) {
                  System.err.println("term is nan");
                  System.err.println("alpha[" + i + "][" + t + "]=" + alpha[i][t]);
                  System.err.println("transition: " + states[i].transition[j]);
                  // JS: TODO: TAKE ME OUT - diagnosing a null pointer exception with ghost town states
                  if (states[j].emit == null) {
                    System.err.println("YIKES!! state " + j + " has null emit map");
                    System.exit(1);
                  }
                  System.err.println("emit (" + ttw.word() + "): " + states[j].emit.get(ttw.word()));
                  System.err.println("beta: " + beta[j][t + 1]);
                }
                if (false && term != 0.0) {
                  System.err.println("Doc " + d + " time " + t + " trans " + i + "->" + j + " = " + term);
                }
              } // for state j
              double tfTerm = alpha[i][t] * beta[i][t];
              thisFrom[i] += tfTerm;

              if (sanityCheck && Math.abs(sumTerm - tfTerm) > TOLERANCE) {
                System.err.println("SumTerm is " + sumTerm + "; alpha beta is " + tfTerm + " for t=" + t + ", state i=" + i);
                System.err.println("SumTerm is Sum_j thisP[i][j][t] " + "alphaBeta is P[i][t]");
                printStateVector("thisP(" + i + "--> .) = ", thisP[i]);
                System.err.println();
              }
              sumAll += tfTerm;

              // Now do emissions, on earlier observation
              TypedTaggedWord ttwi = null;
              if (t > 0) {
                ttwi = (TypedTaggedWord) doc.get(t - 1);
              }
              if (i > State.STARTIDX && ttwi != null && !(states[i].emit instanceof ConstantEmitMap)) {
                String ttwiWord = ttwi.word();
                // for observation probs
                totalOP[i].incrementCount(ttwiWord, tfTerm);

                // js: TODO: use held out data as well?? (where would it come from)
                if (unseenMode == UNSEENMODE_HOLD_OUT_MASS && /*featureSource==SOURCE_SINGLETONS && */
                        vocab.getCount(ttwiWord) == 1) {
                  featureMaps[i].addToCount(ttwiWord, tfTerm);
                  parentFeatureMaps[states[i].type].addToCount(ttwiWord, tfTerm);
                  // System.err.println("State " + i + "; type is " +
                  //                    states[i].type + "; time is " + t +
                  //                    "; doc is " + d);
                } else {
                  totalSeenP[i] += tfTerm;
                  totalParentSeenP[states[i].type] += tfTerm;
                }

                // shrinkage, do parent calculations (no unseens on parent)
                if (sanityCheck && (states[i].type < 0 || states[i].type >= totalTargetOP.length)) {
                  System.err.println("Bad: states[i].type: " + states[i].type);
                  System.err.println(" totalTargetOP:" + totalTargetOP.length);
                }
                totalTargetOP[states[i].type].incrementCount(ttwiWord, tfTerm);
                totalTargetOD[states[i].type] += tfTerm;
                // denominator: mass of all times we were in i is totalFrom[i]
                // this might need changing if had no end.

              } // if has emission
            } // for states i
            if (sanityCheck && useScaling) {
              if (Math.abs(sumAll - 1.0) > TOLERANCE) {
                System.err.println("expectations bung: for time " + t + " sum_i alpha(i)beta(i) = " + sumAll);
              }
            }
          } // for each time t
          // copy to totals
          // if no scaling, factor in document likelihood
          double docLikelihood = lastDocumentLogLikelihood(useScaling);
          // System.err.println("  Document " + d + " loglike is " +
          //                 docLikelihood);   // XXXX DEBUG
          logLike += docLikelihood;
          if (!useScaling) {
            for (int i = 0; i < numStates; i++) {
              for (int j = 0; j < numStates; j++) {
                thisP[i][j] /= docLikelihood;
              }
              thisFrom[i] /= docLikelihood;
            }
          }
          if (printAllTrellises) {
            printTrellis("E(thisP i-->j)", thisP, 0, numStates - 1);
            printStateVector("thisFrom i", thisFrom);
          }
          for (int i = 0; i < numStates; i++) {
            for (int j = 0; j < numStates; j++) {
              totalP[i][j] += thisP[i][j];
            }
            totalFrom[i] += thisFrom[i];
          }
        } else {
          badDocs.add(Integer.valueOf(d)); // skip this for unconstrained
          if (verbose) {
            System.err.println("HMM.expectations: warning: " + "couldn't generate document " + d + ".");
          }
          if (printAllTrellises) {
            for (int t = 0; t < doc.size(); t++) {
              TypedTaggedWord ttw = (TypedTaggedWord) (doc.get(t));
              System.err.print(ttw.word() + ' ');
            }
            System.err.println();
          }
        }
      } // for each document d

      if (badDocs.size() > 0 && verbose) {
        System.err.println("Parameters couldn't generate " + badDocs.size() + " documents.");
      }

      if (printAllTrellises) {
        System.err.println("Expectations for state changes");
        for (int i = 0; i < totalP.length; i++) {
          printStateVector("E(" + i + "--> .)" + i, totalP[i]);
        }
        printStateVector("E(state)", totalFrom);
      }
      return logLike;
    }


    private boolean maximize() {
      // now at last we have the expectations; do the M step
      double maxChange = 0.0;

      // reestimate transitions
      for (int i = 0; i < numStates; i++) {
        if (totalFrom[i] != 0.0) {
          // if we never left this state on the corpus -- this is normally
          // only true for the end state -- then, in any case, we then don't
          // change the parameters

          for (int j = 0; j < numStates; j++) {
            if (sanityCheck && SloppyMath.isVeryDangerous(totalP[i][j])) {
              System.err.println("Dangerous: totalP[" + i + "][" + j + "] is " + totalP[i][j] + "; totalFrom[i] = " + totalFrom[i]);
            }
            // TODO: optionally use entropic max
            double newVal = (totalP[i][j] + pseudoTransitionsCount) / (totalFrom[i] + pseudoTransitionsCount * (numStates - 1));

            // for convergence testing
            double change = Math.abs(states[i].transition[j] - newVal);
            if (change > maxChange) {
              maxChange = change;
            }

            states[i].transition[j] = newVal;
            // System.err.println("newval " + i +", " + j + ": " + newVal);
          }
        }
      }

      for (int i = State.BKGRNDIDX; i < states.length; i++) {
        // skip constant emit states
        if (states[i].emit instanceof ConstantEmitMap) {
          continue;
        }


        // if on this iteration nothing was seen in this state, it must be
        // dead: replace it with a constant emit map.
        if (totalFrom[i] != 0.0) {
          ClassicCounter expectedEmissions = new ClassicCounter();
          Counters.addInPlace(expectedEmissions, totalOP[i]);
          expectedEmissions.removeAll(Counters.keysBelow(expectedEmissions, 0.000001)); // prune way-low counts
          for (Object key : expectedEmissions.keySet()) {
            expectedEmissions.incrementCount(key, pseudoEmissionsCount);
          }
          double change = states[i].emit.tuneParameters(expectedEmissions, getHMM());
          if (change > maxChange) {
            maxChange = change;
          }

          // js: TODO: DON'T DO THIS WHEN RETRAINING!!
          if (unseenMode == UNSEENMODE_HOLD_OUT_MASS) {
            // if retraining, get uem from shrinked emit map (otherwise just use it)
            UnseenEmitMap uem = null; // only used to set seenP if needed
            if (unseenMode == UNSEENMODE_HOLD_OUT_MASS) {
              if (states[i].emit instanceof UnseenEmitMap) {
                uem = (UnseenEmitMap) states[i].emit;
              } else {
                uem = (UnseenEmitMap) ((ShrinkedEmitMap) states[i].emit).getBase();
              }
            }

            // even if there were no unseen tokens, you want to set the
            // FeatureMap, because other code later will refer to it
            // In particular, in estimateShrinkUnseen(Corpus)
            // This is redone later in estimateShrinkUnseen
            // System.err.println("State "+i+" seen tokens " + totalSeenP[i]);
            uem.setFeatureMap(featureMaps[i], trainDocs.wordCount());
          }
        } else {
          // the state has become a dead state (never entered).
          // empty out its emissions
          states[i].emit = new ConstantEmitMap("_GHOST_TOWN_");
        } // if has emissions or uninitialized
      } // for (non-start/stop) states

      // maximize the state type shrinkage emission estimates
      for (Iterator it = vocab.keySet().iterator(); it.hasNext();) {
        String s = (String) it.next();
        for (int i = 0; i < targetFields.length; i++) {
          if (totalTargetOD[i] != 0.0) {
            targetParents[i].set(s, totalTargetOP[i].getCount(s) / totalTargetOD[i]);
            // targetParents[i].seenP = totalParentSeenP[i]/totalTargetOD[i];
          }

          /*if(parentFeatureMaps[i].getTotal() != 0)
            targetParents[i].setFeatureMap(parentFeatureMaps[i]);*/
        }
      } // for vocab

      globalMaxChange = maxChange;
      return maxChange < CONVERGE_SIZE;
    }


    /**
     * Return the log likelihood of the last document that was run
     * through the forwardAlgorithm().  Normally, this only requires
     * having run the forwardAlgorithm() first, but if you have
     * <code>sanityCheck</code> true, then you should also have run
     * backwardAlgorithm() on the last document for the consistency
     * checking to be valid.
     */
    private double lastDocumentLogLikelihood(boolean useScaling) {
      double docLike = 0.0;
      int numTimes = alpha[0].length;

      if (useScaling) {
        // calculate likelihood: P(observation sequence|model parameters)
        // P(O|u) = 1/Ct where Ct is product of scale[t]; see scale reference:
        // http://www.media.mit.edu/~rahimi/rabiner/rabiner-errata/

        for (int t = 0; t < numTimes; t++) {
          docLike -= Math.log(scale[t]);
        }
      } else {
        if (sanityCheck) {
          double[] docLikelihood = new double[numTimes];
          for (int t = 0; t < numTimes; t++) {
            // calculate from alpha-beta -- any time should be the same
            for (int i = 0; i < numStates; i++) {
              docLikelihood[t] += alpha[i][t] * beta[i][t];
            } // for state
          } // for time
          docLike = docLikelihood[0];
          // assertion checking
          for (int t = 1; t < numTimes; t++) {
            if (Math.abs(docLikelihood[t] - docLike) > TOLERANCE) {
              System.err.println("logLikelihood bung: for time " + t + " likelihood is " + docLikelihood[t] + "; for time 0, " + docLike);
            }
          }
        } else {
          int lastTime = numTimes - 1;
          for (int j = 0; j < states.length; j++) {
            docLike += alpha[j][lastTime];
          }
        }
        docLike = Math.log(docLike);
      }
      if (printAllTrellises) {
        System.err.println("" + docLike);
      }
      return docLike;
    }


    /**
     * Calculate the loglikelihood of the passed in corpus according to
     * to the model (stored in class variables).
     * This now returns Double.NEGATIVE_INFINITY if there is at least one
     * document in the Corpus that the model cannot generate.
     *
     * @param observedDocs The corpus to work out the log likelihood of
     * @param useScaling   True means use scaling coefficients
     * @param respectTypes whether to constrain paths to only those that
     *                     respect type-sequence constraints
     * @return log P(trainDocs|model)
     */
    public double logLikelihood(Corpus observedDocs, boolean useScaling, boolean respectTypes) {

      if (respectTypes) {
        badDocs = new HashSet();
      }

      // mark rare words as UNK as per the training vocab
      if (unseenMode == UNSEENMODE_UNK_LOW_COUNTS) {
        observedDocs = new UnknownWordCollapser(vocab.keySet(), unkModel == UNKMODEL_FEATURAL_DECOMP, feat).processCorpus(observedDocs);
      }

      double loglike = 0.0;

      for (int d = 0, size = observedDocs.size(); d < size; d++) {

        if (!respectTypes && badDocs.contains(Integer.valueOf(d))) {
          if (verbose) {
            System.err.println("Skipping doc " + d + " because constrained logLikelihood couldn't generate it");
          }
          continue;
        }

        Document doc = (Document) observedDocs.get(d);
        forwardAlgorithm(doc, useScaling, respectTypes);
        if (sanityCheck) {
          // so lastDocumentLogLikelihood consistency checking is valid
          backwardAlgorithm(doc, useScaling, respectTypes);
        }
        double docLike = lastDocumentLogLikelihood(useScaling);
        if (!Double.isNaN(docLike)) {
          loglike += docLike;
        } else if (!ignoreUngeneratableDocs) {
          if (verbose) {
            System.err.print("Document " + d + " likelihood is NaN");
            /*
            for (int t = 0, dsize = doc.size(); t < dsize; t++) {
              TypedTaggedWord ttw = (TypedTaggedWord) (doc.get(t));
              System.err.print(ttw.word() + ' ');
            }
            System.err.println();
            */
          }
          return Double.NEGATIVE_INFINITY;
        } else {
          badDocs.add(Integer.valueOf(d)); // skip this for unconstrained
        }
      }
      return loglike;
    }


    private boolean forwardAlgorithm(Document doc, boolean useScaling) {
      return forwardAlgorithm(doc, useScaling, true);
    }


    /**
     * Run the forward algorithm on the passed in document, storing the
     * computed alpha paramters in the global alpha[][] array, and,
     * if doing scaling, the corresponding scale factors in the array scale.
     * The vectors in
     * these arrays will be reallocated to have size doc.size() + 2 so as to
     * have start and stop states, and one for each emission.  The global
     * state array states is also used.
     * If respectTypes is true, this does a form of restricted EM where
     * transitions are only allowed
     * when state types match.  It reverts to normal EM if all states have
     * the same type, or respectTypes is false. <br>
     * alpha[i][t] = P(o_1, ... o_t, X_t=i|mu) <br>
     * alpha[i] and scale are reallocated as arrays of the size of the
     * document (plus one for start and end) on each call. <br>
     * This can be run independently of the backwardAlgorithm.
     *
     * @return true iff the HMM can generate the document (otherwise, the
     *         document should be excluded in updating the HMM, etc.
     */
    private boolean forwardAlgorithm(Document doc, boolean useScaling, boolean respectTypes) {
      boolean canGenerate = true;
      int numTimes = doc.size() + 2;

      // Base case of recurrence for forward algorithm
      // we must start in State.STARTIDX for alphas.
      for (int i = 0; i < states.length; i++) {
        alpha[i] = new double[numTimes];   // it's initialized to zero
      }
      alpha[State.STARTIDX][0] = 1.0;

      if (useScaling) {
        scale = new double[numTimes];
        scale[0] = 1.0;
      }

      // forward algorithm recurrence
      for (int t = 1; t < numTimes; t++) {
        TypedTaggedWord ttw = null;
        if (t < numTimes - 1) {
          // document index is offset by one because of start state
          ttw = (TypedTaggedWord) doc.get(t - 1);
        }
        double alphaSum = 0.0;  // sum of alphas over all states at a given time
        for (int j = 0; j < states.length; j++) {
          // special stuff for typed HMMs -- 0 transition if type isn't right,
          // gives restricted baum-welch.  If all states are of same type,
          // get general forward algorithm
          // when we can't generate observation from this state
          if (ttw != null && (respectTypes && states[j].type != ttw.type() || states[j].emit == null)) {
            alpha[j][t] = 0.0;
          } else if (ttw == null && states[j].emit != null) {
            alpha[j][t] = 0.0;
          } else {
            double sum = 0.0;
            for (int i = 0; i < states.length; i++) {
              if (printAllTrellises && alpha[i][t - 1] * states[i].transition[j] != 0.0) {
                System.err.println("Adding to pre-alpha[" + j + "][" + t + "] from state " + i + ": " + alpha[i][t - 1] * states[i].transition[j]);
              }
              sum += alpha[i][t - 1] * states[i].transition[j];
            }
            if (ttw == null) {
              // doing the finish state
              if (printAllTrellises && sum != 0.0) {
                System.err.println("No emission, Alpha[s=" + j + "][t=" + t + "] = " + sum);
              }
              alpha[j][t] = sum;
            } else {
              if (printAllTrellises && sum != 0.0) {
                System.err.println("Pre-alpha is " + sum);
                System.err.println("Emission probability for " + ttw.word() + " is " + states[j].emit.get(ttw.word()));
                System.err.println("Alpha[s=" + j + "][t=" + t + "] = " + sum * states[j].emit.get(ttw.word()));
              }
              alpha[j][t] = sum * states[j].emit.get(ttw.word());
            }
          }
          alphaSum += alpha[j][t];
        }
        if (alphaSum == 0) {
          canGenerate = false;
          if (verbose) {
            System.err.println("forwardAlgorithm: No path at time " + t + " of " + numTimes + " (word: " + ((ttw == null) ? "NULL" : ttw.word()) + ")");
            if (numTimes < 10) {
              System.err.println("  Document is: " + doc);
            }
          }

          if (false && spillEmissions) {
            for (int i = 0; i < states.length; i++) {
              if (ttw != null && states[i].emit != null) {
                System.err.println("P(ttw|s=" + i + ") = " + states[i].emit.get(ttw.word()));
              }
            }
          }
        }

        if (useScaling) {
          for (int i = 0; i < states.length; i++) {
            // change alpha-tilde to alpha-hat
            alpha[i][t] = alpha[i][t] / alphaSum;
          }
          scale[t] = 1 / alphaSum;
          if (printAllTrellises) {
            System.err.println("alphaTildeSum = " + alphaSum + "; C[" + t + "] = " + scale[t]);
          }
        }
      }
      return canGenerate;
    }


    private void backwardAlgorithm(Document doc, boolean useScaling) {
      backwardAlgorithm(doc, useScaling, true);
    }


    /**
     * Run the backward algorithm on the passed in document, storing the
     * computed beta paramters in the global beta[][] array.
     * If <code>useScaling</code> is <code>true</code>, this uses
     * the scale factors computed during running the forwardAlgorithm, and
     * stored in the array scale, so calls to backwardAlgorithm must be
     * paired with and after calls to forwardAlgorithm.  The vectors in
     * beta will be reallocated to have size doc.size() + 2 so as to
     * have start and stop states, and one for each emission.  The global
     * state array <code>states</code> is also used.
     * Calculates:<p><center>
     * beta[i][t] = P(o_{t+1}, ..., o_T|X_t = i)
     * </center><p>
     * modulo scaling.
     * <p/>
     * Currently assumes finish state.  Should generalize it so one doesn't
     * have to have a finish state.
     * beta[i][0] = P(o_1, ... o_T|X_0 = i).  Thus, given use of start state
     * (1), beta[1][0] = P(O|mu).
     */
    private void backwardAlgorithm(Document doc, boolean useScaling, boolean respectTypes) {
      int numTimes = doc.size() + 2;

      // Base case of recurrence for backward.
      // For the base case, one is giving the proability of nothing given
      // that you are in state i at time (numTimes-1), so that's 1 regardless
      for (int i = 0; i < states.length; i++) {
        beta[i] = new double[numTimes];  // all initialized to 0.0
        beta[i][numTimes - 1] = 1.0;
      }

      // backward
      for (int t = numTimes - 2; t >= 0; t--) {
        // we're looking forward so that we start with no real observation,
        // but there is one at the end.
        TypedTaggedWord ttw = null;
        if (t < numTimes - 2) {
          ttw = (TypedTaggedWord) doc.get(t);   // (t+1)-1
        }
        for (int i = 0; i < states.length; i++) {
          double sum = 0.0;
          for (int j = 0; j < states.length; j++) {
            if (ttw == null) {
              if (j == 0) {
                // end symbol always generated with prob 1
                sum += states[i].transition[j] * beta[j][t + 1];
              } else {
                // add 0 to sum
              }
            } else if (respectTypes && states[j].type != ttw.type() || states[j].emit == null) {
              // add 0 to sum
            } else {
              sum += states[i].transition[j] * states[j].emit.get(ttw.word()) * beta[j][t + 1];
            }
          }
          // System.err.println("For t = " + t + ", betaInter[" + i + "] = " +
          //                 sum);
          // System.err.println("Scale[" + (t+1) + "] = " + scale[t+1] +
          //                 ", beta-hat = " + (sum * scale[t+1]));
          if (useScaling) {
            sum *= scale[t + 1];
          }
          beta[i][t] = sum;
        }
      }
    }

    public Corpus getTrainDocs() {
      return (trainDocs);
    }

    /**
     * ***************************
     * GRADIENT DESCENT CODE
     * ******************************
     */
    // total number of transition and emission params being used for CG
    private int numParams;

    // state x state matrix of whether the transition is non-zero
    private boolean[][] nonZeroTransitions;
    // state x vocab.size matrix of whether the emission is non-zero
    private boolean[][] nonZeroEmissions;

    // denominator for penalty terms
    private int sigmaSquared = 2;

    /**
     * Sets the denominator for the conditional penalty terms.
     */
    public void setSigmaSquared(int sigmaSquared) {
      this.sigmaSquared = sigmaSquared;
    }

    /**
     * Returns the first derivative of the negative log conditional probability
     * for the given params. Params are logged in this function, so code uses
     * them exponentiated.
     * <p/>
     * d_-Log[P(c|w)]/d_xi = -(E_c[xi]-E_u[xi]) where E_c and E_u are expectations
     * under constrained and unconstrained forward algorithm. A penalty term
     * is added for high params.
     */
    public double[] derivativeAt(double[] x) {
      //System.err.print("*** derivAt:");
      //for(int i=0;i<x.length;i++) System.err.print(" "+Math.exp(x[i]));
      //System.err.println();        // sticks the given params into the HMM
      applyParams(x);

      // deriv with respect to each param
      double[] deriv = new double[numParams];

      // expectations (constrained and unconstrained) for each param
      double[] expc = new double[numParams];
      double[] expu = new double[numParams];

      // constrained expectations
      double constrLogLike = expectations(true, true);
      if (verbose) {
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(5);
        System.err.println("Constrained train loglikelihood log P(O|mu) = " + nf.format(constrLogLike));
      }

      int index = 0;
      for (int i = 0; i < numStates; i++) {
        if (i == State.FINISHIDX) {
          continue;
        }
        for (int j = 0; j < numStates; j++) {
          if (nonZeroTransitions[i][j]) {
            expc[index++] = totalP[i][j];
          }
        }
        if (i != State.FINISHIDX && i != State.STARTIDX && !(states[i].emit instanceof ConstantEmitMap)) {
          int j = 0;
          for (Iterator iter = vocab.keySet().iterator(); iter.hasNext(); j++) {
            String emit = (String) iter.next();
            if (nonZeroEmissions[i][j]) {
              expc[index++] = totalOP[i].getCount(emit);
            }
          }
        }
      }

      // unconstrained expectations
      double unconstrLogLike = expectations(true, false);
      if (verbose) {
        NumberFormat nf = NumberFormat.getNumberInstance();
        nf.setMaximumFractionDigits(5);
        System.err.println("Unconstrained train loglikelihood log P(O|mu) = " + nf.format(unconstrLogLike));
      }
      index = 0;
      for (int i = 0; i < numStates; i++) {
        if (i == State.FINISHIDX) {
          continue;
        }
        for (int j = 0; j < numStates; j++) {
          if (nonZeroTransitions[i][j]) {
            expu[index++] = totalP[i][j];
          }
        }
        if (i != State.FINISHIDX && i != State.STARTIDX && !(states[i].emit instanceof ConstantEmitMap)) {
          int j = 0;
          for (Iterator iter = vocab.keySet().iterator(); iter.hasNext(); j++) {
            String emit = (String) iter.next();
            if (nonZeroEmissions[i][j]) {
              expu[index++] = totalOP[i].getCount(emit);
            }
          }
        }
      }

      // calculates the derivative for each param
      for (int i = 0; i < numParams; i++) {
        deriv[i] = -(expc[i] - expu[i]);
      }

      /*
              // penalty to keep probs near 1.0;
              for(int i=0;i<numParams;i++)
                  deriv[i]+=2*x[i]/sigmaSquared;
      */
      // penalty to keep probs uniform
      index = 0;
      for (int i = 0; i < numStates; i++) {
        if (i == State.FINISHIDX) {
          continue; // finish state params are fixed
        }
        State s = states[i]; // current state

        // calculates what uniform transitions for this state should be
        int numNonZeroTransitions = 0;
        for (int j = 0; j < numStates; j++) {
          if (nonZeroTransitions[i][j]) {
            numNonZeroTransitions++;
          }
        }
        double meanTransition = Math.log(1.0 / numNonZeroTransitions);

        // penalizes transitions for this state that deviate from uniform
        for (int j = 0; j < s.transition.length; j++) {
          if (nonZeroTransitions[i][j]) {
            deriv[index] += 2 * (x[index++] - meanTransition) / sigmaSquared;
          }
        }

        // penalizes emissions for this state that deviate from uniform
        if (i != State.FINISHIDX && i != State.STARTIDX && !(s.emit instanceof ConstantEmitMap)) {
          // calculates what uniform emissions for this state should be
          int numNonZeroEmissions = 0;
          for (int j = 0; j < vocab.size(); j++) {
            if (nonZeroEmissions[i][j]) {
              numNonZeroEmissions++;
            }
          }
          double meanEmission = Math.log(1.0 / numNonZeroEmissions);

          for (int j = 0; j < vocab.size(); j++) {
            if (nonZeroEmissions[i][j]) {
              deriv[index] += 2 * (x[index++] - meanEmission) / sigmaSquared;
            }
          }
        }
      }

      return (deriv);
    }

    /**
     * Returns the number of parameters to operate on for this minimization.
     * This number is computed by {@link #initialParams} the first time it's called.
     */
    public int domainDimension() {
      return (numParams);
    }


    /**
     * Calculates and returns Log[P(c|w)] for the given Corpus.
     * This is the conditional probability of generating the correct sequence
     * of state types for each word given the corpus of words.
     */
    public double logConditionalLikelihood(Corpus observedDocs) {
      // computes Log[Sum_t P(c,w,t)] = Log[P(c,w)]
      double constrainedLogLikelihood = logLikelihood(observedDocs, true, true);
      if (monitor) {
        System.err.println(" - constrained log likelihood: " + constrainedLogLikelihood);
      }
      // computes Log[Sum_t_c P(c,w,t)] = Log[P(w)]
      double unconstrainedLogLikelihood = logLikelihood(observedDocs, true, false);
      if (monitor) {
        System.err.println(" - unconstrained log likelihood: " + unconstrainedLogLikelihood);
      }
      // computes P(c|w) = P(c,w)/P(w)
      double logConditionalProb = (constrainedLogLikelihood - unconstrainedLogLikelihood);
      if (monitor) {
        System.err.println(" - negative log conditional prob: " + (-logConditionalProb));
      }

      return (logConditionalProb);
    }


    /**
     * Returns the negative log conditional probability -Log[P(c|w)] for the given params.
     * Params are logged in this function, so HMM code uses them exponentiated.
     * A penalty for large params is added and the function should be minimized.
     * <p/>
     * P(c|w) = likelihood_constrained/likelihood_unconstrained
     */
    public double valueAt(double[] x) {
      //System.err.print("*** valueAt:");
      //for(int i=0;i<x.length;i++) System.err.print(" "+Math.exp(x[i]));
      //System.err.println();

      // sticks the given params into the HMM
      applyParams(x);

      // computes -Log[P(c|w)] for the training corpus
      double negativeLogConditionalProb = -logConditionalLikelihood(trainDocs);

      // applies a penalty P(c|w) - 1/2 Sum_i[(x_i)^2]
      // NOTE: right now penalty just tries to keep param near 0, but a more
      // clever implementation might try to keep them at a uniform dist
      // (e.g. 1/N where N is either numStates or vocab.size())
      // to do this, you'd take (x-m)^2 where m = log(1/N)
      double penalty = 0;
      /*
              // penalty to keep probs near 1.0
              for(int i=0;i<numParams;i++)
                  penalty+=x[i]*x[i]/2;
      */
      // penalty to keep probs uniform
      int index = 0;
      for (int i = 0; i < numStates; i++) {
        if (i == State.FINISHIDX) {
          continue; // finish state params are fixed
        }
        State s = states[i]; // current state

        // calculates what uniform transitions for this state should be
        int numNonZeroTransitions = 0;
        for (int j = 0; j < numStates; j++) {
          if (nonZeroTransitions[i][j]) {
            numNonZeroTransitions++;
          }
        }
        double meanTransition = Math.log(1.0 / (numNonZeroTransitions));

        // penalizes transitions for this state that deviate from uniform
        for (int j = 0; j < s.transition.length; j++) {
          if (nonZeroTransitions[i][j]) {
            penalty += Math.pow(x[index++] - meanTransition, 2) / sigmaSquared;
          }
        }

        // penalizes emissions for this state that deviate from uniform
        if (i != State.FINISHIDX && i != State.STARTIDX && !(s.emit instanceof ConstantEmitMap)) {
          // calculates what uniform emissions for this state should be
          int numNonZeroEmissions = 0;
          for (int j = 0; j < vocab.size(); j++) {
            if (nonZeroEmissions[i][j]) {
              numNonZeroEmissions++;
            }
          }
          double meanEmission = Math.log(1.0 / numNonZeroEmissions);

          for (int j = 0; j < vocab.size(); j++) {
            if (nonZeroEmissions[i][j]) {
              penalty += Math.pow(x[index++] - meanEmission, 2) / sigmaSquared;
            }
          }
        }
      }
      if (monitor) {
        System.err.println(" - penalty: " + penalty);
      }
      negativeLogConditionalProb += penalty;
      if (monitor) {
        System.err.println(" - negative log conditional prob + penalty: " + negativeLogConditionalProb);
      }

      if (monitor) {
        System.err.println("--> value = " + negativeLogConditionalProb);
      }
      return (negativeLogConditionalProb);
    }

    /**
     * Takes the given CG params and injects them into the current HMM model.
     * The variables in x are mapped to each state, one at a time, as [trans][emit]
     * so the whole vector is [[t0][e0][t1][e1]...[tn][en]] where the length of each
     * t is numStates and the length of each e is vocab.size(). Exceptions are no
     * representation of (fixed) finish state, no emissions for start state, and no
     * transitions to start state are included in vector. Each parameter is
     * exponentiated before being applied to the HMM model.
     * <p/>
     * NOTE: Currently there is no normalization, even though nothing guarantees that CG will give normalized params
     * NOTE: This assumes that every emitmap stores every word in vocab, and there's no unseen stuff (just UNK words)
     */
    public void applyParams(double[] x) {
      int index = 0; // index in x array

      for (int i = 0; i < numStates; i++) {
        if (i == State.FINISHIDX) {
          continue; // finish state params are fixed
        }
        State s = states[i]; // current state

        // sets the (non-zero) transitions for this state
        for (int j = 0; j < s.transition.length; j++) {
          if (nonZeroTransitions[i][j]) {
            s.transition[j] = Math.exp(x[index++]);
          }
        }

        // sets the emissions for each word in this state
        if (i != State.FINISHIDX && i != State.STARTIDX && !(s.emit instanceof ConstantEmitMap)) {
          int j = 0;
          for (Iterator iter = vocab.keySet().iterator(); iter.hasNext(); j++) {
            String emit = (String) iter.next();
            if (nonZeroEmissions[i][j]) {
              s.emit.set(emit, Math.exp(x[index++]));
            }
          }
        }
      }
    }

    /**
     * Returns the initial position for gradient descent based on the initialized HMM.
     * Also sets up the number of parameters to use and the mapping between the parameter
     * vector and the HMM. Each HMM parameter is logged before being returned.
     *
     * @see #applyParams
     */
    public double[] initialParams() {
      // records which states have non-zero transitions
      nonZeroTransitions = new boolean[numStates][numStates];
      for (int i = 0; i < numStates; i++) {
        for (int j = 0; j < numStates; j++) {
          nonZeroTransitions[i][j] = (states[i].transition[j] != 0);
        }
      }

      // records which states have non-zero emissions
      nonZeroEmissions = new boolean[numStates][vocab.size()];
      for (int i = 0; i < numStates; i++) {
        if (i != State.FINISHIDX && i != State.STARTIDX && !(states[i].emit instanceof ConstantEmitMap)) {
          int j = 0;
          for (Iterator iter = vocab.keySet().iterator(); iter.hasNext(); j++) {
            nonZeroEmissions[i][j] = (states[i].emit.get((String) iter.next()) != 0);
          }
        }
      }

      // records the total number of params to use for CG
      // we have all the non-zero transitions and non-zero emissions
      numParams = 0;
      for (int i = 0; i < numStates; i++) {
        for (int j = 0; j < numStates; j++) {
          if (nonZeroTransitions[i][j]) {
            numParams++;
          }
        }
        for (int j = 0; j < vocab.size(); j++) {
          if (nonZeroEmissions[i][j]) {
            numParams++;
          }
        }
      }

      // put logs of all params in vector for initial values
      double[] x = new double[numParams];
      int index = 0;
      for (int i = 0; i < numStates; i++) {
        if (i == State.FINISHIDX) {
          continue; // finish state params are fixed
        }
        State s = states[i]; // current state

        // gets the (non-zero) transitions for this state
        for (int j = 0; j < s.transition.length; j++) {
          if (nonZeroTransitions[i][j]) {
            x[index++] = Math.log(s.transition[j]);
          }
        }

        // gets the emissions for each (non-zero) word in this state (if it has 'em)
        if (i != State.FINISHIDX && i != State.STARTIDX && !(s.emit instanceof ConstantEmitMap)) {
          int j = 0;
          for (Iterator iter = vocab.keySet().iterator(); iter.hasNext(); j++) {
            String emit = (String) iter.next();
            if (nonZeroEmissions[i][j]) {
              x[index++] = Math.log(s.emit.get(emit));
            }
          }
        }
      }

      if (verbose) {
        System.err.println("sigma-squared: " + sigmaSquared);
      }

      //System.err.print("*** initialParams:");
      //for(int i=0;i<x.length;i++) System.err.print(" "+Math.exp(x[i]));
      //System.err.println();
      return (x);
    }

    /**
     * ***************************
     * Entropic Prior MAP code
     * ***************************
     */

    public void trainEntropicPriorMAP() {
      boolean converged = false;
      int numIterations = 0;

      double lastLogPosterior = 0.0;
      double improve = 0.0;

      // keep reestimating until convergence or a fixed upper bound
      while (numIterations < MAX_ITER) {
        checkNormalized();

        expectations(true, true);
        double logPosterior = entropicLogPosterior();
        if (numIterations > 0) {
          // computes % change in the a posteriori with the entropic prior
          // since the last iteration
          improve = (lastLogPosterior == 0.0) ? 0.0 : (lastLogPosterior - logPosterior) / lastLogPosterior;
          converged = improve < LL_CONVERGE_SIZE;
        }
        if (verbose) {
          NumberFormat nf = NumberFormat.getNumberInstance();
          nf.setMaximumFractionDigits(3);
          System.err.print("Log posterior after " + numIterations + " iterations: " + nf.format(logPosterior));
          if (numIterations > 0) {
            System.err.print(" [" + nf.format(improve * 100) + "% change]");
          }
          System.err.println();
        }
        lastLogPosterior = logPosterior;

        // iterate at least 5 times
        if (numIterations >= 5 && converged) {
          converged = true;
          break;
        }

        estimateMAP();
        numIterations++;
        //printProbs();

      } // while not converged or halted

      if (verbose) {
        if (converged) {
          System.err.println("Converged after " + numIterations + " iterations.");
        } else {
          System.err.println("Stopping after " + MAX_ITER + " iterations.");
        }
      }
    }

    public void estimateMAP() {
      double lambda = 0.0;
      double sumLogSquared = 0.0;
      // initialize lambda to (-sum w_i - <log w>)
      // interpreted <log w> as ||log w||, that is the sqrt of the sum of the
      // squares of the logs
      for (int i = 0; i < totalP.length; i++) {
        for (int j = 0; j < totalP[i].length; j++) {
          lambda -= totalP[i][j];
          if (totalP[i][j] > 0) {
            double log = Math.log(totalP[i][j]);
            sumLogSquared += log * log;
          }
        }
      }
      for (int i = 0; i < totalOP.length; i++) {
        for (Iterator iter = vocab.keySet().iterator(); iter.hasNext();) {
          String word = (String) iter.next();
          lambda -= totalOP[i].getCount(word);
          if (totalOP[i].getCount(word) > 0) {
            double log = Math.log(totalOP[i].getCount(word));
            sumLogSquared += log * log;
          }
        }
      }
      lambda -= Math.sqrt(sumLogSquared);

      System.err.println("Initial lambda: " + lambda);

      // calculate parameters (theta) given lambda, normalize theta, calculate
      // lambda given theta, repeat until convergence

      // HN TODO: fix to iterate until convergence or a higher threshold
      for (int k = 0; k < 5; k++) {
        // compute parameters (theta) for a given lambda
        // theta_i = -w_i / W(-w_i*e^(1+lambda))
        // we convert -w_i*e^(1+lambda) to
        // -e^(1+lambda+log w_i) -> -e^-(-1-lambda-log w_i) in order to use
        // a special recurrence for evaluating the Lambert W function

        // reestimate transitions
        for (int i = 0; i < numStates; i++) {
          if (totalFrom[i] != 0.0) {
            for (int j = 0; j < numStates; j++) {
              double newVal;
              if (totalP[i][j] != 0.0) {
                newVal = -totalP[i][j] / lambertW(-1 - lambda - Math.log(totalP[i][j]));
              } else {
                newVal = 0.0;
              }

              if (newVal > 0.00001) {
                states[i].transition[j] = newVal;
              } else {
                states[i].transition[j] = 0.0;
              }

            }
            ArrayMath.normalize(states[i].transition);
          }
        }

        // reestimate emissions
        for (int i = State.BKGRNDIDX; i < states.length; i++) {
          // skip constant emit states
          if (states[i].emit instanceof ConstantEmitMap) {
            continue;
          }

          if (totalFrom[i] != 0.0) {
            for (Iterator it = vocab.keySet().iterator(); it.hasNext();) {
              String s = (String) it.next();

              double newVal;
              if (totalOP[i].getCount(s) != 0.0) {
                newVal = -totalOP[i].getCount(s) / lambertW(-1 - lambda - Math.log(totalOP[i].getCount(s)));
              } else {
                newVal = 0.0;
              }

              //HN TODO: more sophisticated pruning
              if (newVal > 0.00001) {
                states[i].emit.set(s, newVal);
              } else {
                states[i].emit.set(s, 0.0);
              }
              //System.err.println("P("+s+"|"+i+")="+totalOP[i].getCount(s));
              //System.err.println("P("+s+"|"+i+")="+newVal);

            } // for vocab keyset iterator
            Counters.normalize(states[i].emit.getCounter());
          } else {
            // the state has become a dead state (never entered).
            // empty out its emissions
            states[i].emit = new ConstantEmitMap("_GHOST_TOWN_");
          } // if has emissions or uninitialized
        } // for (non-start/stop) states

        // compute lambda given theta
        // 0 = w_i / theta_i + log theta_i + 1 + lambda ->
        // lambda = -w_i / theta_i - log theta_i - 1

        // since the lambdas aren't guaranteed to be the same for all of
        // the w_i equations, take the average
        double sumLambda = 0.0;
        // lambda is only finite for non-zero parameters, numNonZero counts them
        int numNonZero = 0;

        for (int i = 0; i < numStates; i++) {
          if (totalFrom[i] != 0.0) {
            for (int j = 0; j < numStates; j++) {
              if (states[i].transition[j] < 0.0) {
                System.err.println("transition[" + i + "][" + j + "] less than zero!");
                //System.err.println("totalOP["+i+"]["+j+"]="+totalP[i][j]);
                System.err.println("transition[" + i + "][" + j + "]=" + states[i].transition[j]);
                System.exit(1);
              }
              if (states[i].transition[j] != 0.0) {
                sumLambda += -totalP[i][j] / states[i].transition[j] - Math.log(states[i].transition[j]) - 1;

                numNonZero++;

              }
            }
          }
        }
        for (int i = State.BKGRNDIDX; i < states.length; i++) {
          // skip constant emit states
          if (states[i].emit instanceof ConstantEmitMap) {
            continue;
          }

          if (totalFrom[i] != 0.0) {
            for (Iterator it = vocab.keySet().iterator(); it.hasNext();) {
              String s = (String) it.next();
              if (states[i].emit.get(s) < 0.0) {
                System.err.println("emission[" + i + "][" + s + "] less than zero!");
                System.exit(1);
              }
              if (states[i].emit.get(s) != 0.0) {
                sumLambda += -totalOP[i].getCount(s) / states[i].emit.get(s) - Math.log(states[i].emit.get(s)) - 1;
                numNonZero++;
              }
            }
          }
        }
        if (numNonZero == 0) {
          System.err.println("No nonzero parameters?");
          System.exit(1);
        }
        sumLambda /= numNonZero;
        double lambdaChange = Math.abs(lambda - sumLambda);
        lambda = sumLambda;
        //System.err.println("lambda: "+lambda);
        if (lambdaChange < CONVERGE_SIZE) {
          if (verbose) {
            System.err.println("Lambda converged");
          }
          break;
        }
      } // end for k
    }

    /**
     * Returns the log posterior of the model given the data using the entropic
     * prior.  Specifically, computes:
     * log \product[theta_i^(w_i+theta_i)] -> \sum[(w_i+theta_i) log (theta_i)]
     * This is the value that we seek to maximize in trainEntropicPriorMap
     */
    public double entropicLogPosterior() {
      double logPosterior = 0.0;

      // iterate over transition parameters
      for (int i = 0; i < numStates; i++) {
        for (int j = 0; j < numStates; j++) {
          if (states[i].transition[j] != 0.0) {
            logPosterior += (totalP[i][j] + states[i].transition[j]) * Math.log(states[i].transition[j]);
          }
        }
      }

      // iterate over emission parameters
      for (int i = State.BKGRNDIDX; i < states.length; i++) {
        // skip constant emit states
        if (states[i].emit instanceof ConstantEmitMap) {
          continue;
        }

        if (totalFrom[i] != 0.0) {
          for (Iterator it = vocab.keySet().iterator(); it.hasNext();) {
            String s = (String) it.next();

            if (states[i].emit.get(s) != 0.0) {
              logPosterior += (totalOP[i].getCount(s) + states[i].emit.get(s)) * Math.log(states[i].emit.get(s));
            }
          }
        }
      }
      return logPosterior;
    }

    /**
     * Computes the W_-1(z) real branch of the Lambert W function for z=-e^-x,
     * which gives the solution desired in the MAP estimator, using recurrence
     * equations (38) and (39) defined in Appendix A of Brand 98.
     */
    public double lambertW(double x) {
      // w_0 = -x (initial value)
      double w = -x;
      double w_prime, diff;

      //System.err.println("lambertW - w0: "+w);
      // w_(j+1) = -x - log |w_j|
      // iterate until convergence
      for (int i = 0; i < 100; i++) { // HN TODO: fix this, should converge really fast
        w_prime = -x - Math.log(Math.abs(w));

        diff = Math.abs((w_prime - w) / w);
        //System.err.println("w: "+w_prime+" ("+nf.format(diff*100)+"% change)");
        if (diff < TOLERANCE) {
          //System.err.println("lambertW converged");
          //System.err.println("retVal: "+w_prime);
          return w_prime;
        }
        w = w_prime;
      }
      System.err.println("MAX ITER REACHED");
      return w; // never should reach here
    }
  } // class HMMTrainer

  /**
   * Prints out progressive updates during conditional HMM training.
   */
  private class HMMConditionalTrainingMonitorFunction implements edu.stanford.nlp.optimization.Function {
    private HMMTrainer trainer;
    private int numIterations;

    public HMMConditionalTrainingMonitorFunction(HMMTrainer trainer) {
      this.trainer = trainer;
      numIterations = 0;
    }

    public int domainDimension() {
      return (trainer.domainDimension());
    }

    public double valueAt(double[] x) {
      numIterations++;
      monitor = true;
      System.err.println("negative log likelihood plus penalty after " + numIterations + " iterations of conditional training: " + trainer.valueAt(x));
      monitor = false;
      if (josephStuff) {
        System.err.println("log likelihood on training data after " + numIterations + " iterations of conditional training: " + trainer.logLikelihood(trainer.getTrainDocs(), true, true));
        System.err.println("conditional log likelihood on training data after " + numIterations + " iterations of conditional training: " + trainer.logConditionalLikelihood(trainer.getTrainDocs()));
        System.err.println("F1 on training data after " + numIterations + " iterations of conditional training: " + new HMMTester(getHMM()).test(trainer.getTrainDocs(), null, true, false, false));
        if (testDocs != null) {
          System.err.println("log likelihood on test data after " + numIterations + " iterations of conditional training: " + trainer.logLikelihood(testDocs, true, true));
          System.err.println("conditional log likelihood on test data after " + numIterations + " iterations of conditional training: " + trainer.logConditionalLikelihood(testDocs));
          System.err.println("F1 on test data after " + numIterations + " iterations of conditional training: " + new HMMTester(getHMM()).test(testDocs, null, true, false, false));
        }
      }
      System.err.println("HMM probs after " + numIterations + " iterations of conditional training: ");
      printProbs();
      return (42); // never cause premature termination (value taken from CGRunner--hitchhiker's ref??)
    }

  }

  /**
   * Constrains total probability mass to a fixed quantity.
   */
  private class HMMConditionalTrainingMassPenaltyFunction implements DiffFunction {
    private HMMTrainer trainer;
    private int idealTotalMass;

    public HMMConditionalTrainingMassPenaltyFunction(HMMTrainer trainer, int numStates) {
      this.trainer = trainer;
      // total mass for joint training (transitions + emissions)
      idealTotalMass = (numStates - 1) + (numStates - 2);
    }

    public int domainDimension() {
      return (trainer.domainDimension());
    }

    /**
     * Returns the ideal value for the total probability mass in the HMM.
     * This is the mass that would be used in a joint estimate.
     */
    public int getIdealTotalMass() {
      return (idealTotalMass);
    }

    /**
     * Returns the difference between the current total mass and ideal total mass.
     * f[x] = Sum_i[exp(x[i])] - idealTotalMass
     */
    public double valueAt(double[] x) {
      double currentTotalMass = 0;
      for (int i = 0; i < x.length; i++) {
        currentTotalMass += Math.exp(x[i]);
      }
      return (currentTotalMass - idealTotalMass);
    }

    /**
     * Returns the partial derivatives of the penalty function.
     * d_f[x]/d_xi = exp(xi)
     */
    public double[] derivativeAt(double[] x) {
      double[] deriv = new double[x.length];
      for (int i = 0; i < x.length; i++) {
        deriv[i] = Math.exp(x[i]);
      }
      return (deriv);
    }
  }

  // TAKE ME OUT - HACK FOR TESTING CONDITIONAL TRAINING VS ITERATIONS
  public boolean monitor = false;

  public HMM getHMM() {
    return (this);
  }

  public void setTestCorpus(Corpus testDocs) {
    this.testDocs = testDocs;
  }

  private Corpus testDocs;

  /**
   * This is just for testing the forward and backward algorithms.
   * You have to run it on a very short document, so that the unscaled
   * version doesn't underflow.
   *
   * @param args Command line arguments (if none default is used)<br>
   *             Usage: <code>java edu.stanford.nlp.ie.hmm.HMM [filename [field]]</code>
   */
  public static void main(String[] args) {
    String filename = "/u/nlp/data/iedata/hmmtest";
    String field = "location";
    if (args.length > 0) {
      filename = args[0];
      if (args.length > 1) {
        field = args[1];
      }
    }
    new HMM(REGULAR_HMM).unitTestTraining1(new Corpus(filename, field));
    new HMM(Structure.fsnlpHMM(), REGULAR_HMM).unitTestTraining2();
  }
}
