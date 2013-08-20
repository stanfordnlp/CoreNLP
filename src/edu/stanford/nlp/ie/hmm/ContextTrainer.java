package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;

import java.util.Properties;

/**
 * Trains a context HMM on the contexts of the given target states,
 * representing each target state as atomic.  This utility trains on the
 * entire given training data and builds an HMM with one background state and
 * then transitions between it and the various target states in a potentially
 * fully connected model.  This will work well on data which is dense with
 * states (bibliographic entries, addresses, ...), but doesn't do detailed
 * state context modeling (in terms of prefix and suffix style background
 * states) of the kind needed to do well on loosely structured natural
 * language data.
 *
 * @author Huy Nguyen (htnguyen@cs.stanford.edu)
 */
public class ContextTrainer {
  /**
   * These variables are used when doing structure learning
   */
  private double bestResult; // the mdlScore of the best structure
  Structure argBest; // the best structure
  private boolean improving;
  private boolean oneWorse;
  private HMM best;

  public ContextTrainer() {
  }

  /**
   * Trains a context HMM for the given <tt>targetFields</tt> on the
   * <tt>trainDocs</tt> according to the properties specified by
   * <tt>props</tt>.
   *
   * @param trainDocs    the training corpus
   * @param props        the properties used to train the context HMM.
   *                     See the {@link Extractor} javadoc for more details
   * @param targetFields the target fields that will be merged with this context
   * @param verbose      if true, prints full output
   * @see Extractor
   */
  public HMM train(Corpus trainDocs, Properties props, String[] targetFields, boolean verbose) {
    String contextType = props.getProperty("contextType");
    int numContextStates = Integer.parseInt(props.getProperty("ncs"));
    int numBackgroundStates = Integer.parseInt(props.getProperty("nbs"));
    String trainType = props.getProperty("trainType");
    boolean trainNow = !trainType.equals("conditional");

    if (verbose) {
      System.err.println("Training the context HMM");
      // cdm Feb 2002: really don't need to print props on each fold
      // System.err.println("Training the context HMM using the following properties:");
      // props.list(System.err);
    }

    HMM chmm = null;

    if (verbose) {
      System.err.println("Context type: " + contextType);
    }

    Corpus contextDocs = new Corpus(trainDocs); // copy for context only
    contextDocs.isolateContext();

    if (contextType.equals("learned")) {
      chmm = learnContextHMM(contextDocs, targetFields, props, verbose);
    } else if (contextType.equals("ergodic")) {
      int stateTypes[] = new int[numBackgroundStates + targetFields.length];
      // add background states
      for (int i = 0; i < numBackgroundStates; i++) {
        stateTypes[i] = Structure.BACKGROUND_TYPE;
      }
      // add a state for each target type
      for (int i = 0; i < targetFields.length; i++) {
        stateTypes[i + numBackgroundStates] = i + 1;
      }
      Structure structure = new Structure();
      structure.giveErgodic(stateTypes);

      chmm = new HMM(structure, HMM.CONTEXT_HMM);
    } else if (contextType.equals("flexible")) {
      int threshold = Integer.parseInt(props.getProperty("clInitThreshold"));
      MultiStructure ms = new MultiStructure(targetFields, contextDocs, threshold);
      ms.initializeTransitions(MultiStructure.LEARNING_INIT);
      chmm = new HMM(ms, HMM.CONTEXT_HMM);
    } else { // chain of prefix/suffix round each state
      if (verbose) {
        System.err.println("Num context states: " + numContextStates);
      }
      MultiStructure ms = new MultiStructure(targetFields, numContextStates);
      ms.initializeTransitions(); // enrich the structure for training
      chmm = new HMM(ms, HMM.CONTEXT_HMM);
    }
    // don't train until after merging if using conditional training, don't retrain if context is learned
    if (trainNow && !contextType.equals("learned")) {
      chmm.train(contextDocs, props, verbose);
    }
    // must set the target fields explicitly since we don't train
    chmm.setTargetFields(contextDocs.getTargetFields());

    return chmm;
  }

  /**
   * performs a greedy search in the structure space to find a good
   * structure for the context given the training docs.
   */
  public HMM learnContextHMM(Corpus trainDocs, String[] targetFields, Properties props, boolean verbose) {
    Corpus contextDocs = new Corpus(trainDocs);
    contextDocs.isolateContext();
    if (props == null) {
      props = Extractor.getDefaultProperties();
    }
    int threshold = Integer.parseInt(props.getProperty("clInitThreshold"));

    MultiStructure start = new MultiStructure(targetFields, contextDocs, threshold);
    start.initializeTransitions(MultiStructure.LEARNING_INIT); // enrich structure for training

    HMM hmm = new HMM(start, HMM.CONTEXT_HMM);
    if (verbose) {
      hmm.printTransitions();
    }
    hmm.train(contextDocs, props, verbose);
    double logLike = hmm.logLikelihood(contextDocs);
    double result = hmm.mdlScore(contextDocs, logLike);
    if (verbose) {
      System.err.println("..............................");
      System.err.println("Initial structure:");
      System.err.println(start);
      // note that calculation below mixes training and held out data
      // sensible??
      System.err.println("log likelihood = " + logLike);
      System.err.println("mdl score = " + result);
    }

    bestResult = result;
    argBest = start;
    int depth = 0;
    MultiStructure current = start;

    improving = true;
    oneWorse = false;

    while (improving && depth < 20) {
      if (verbose) {
        System.err.println("expanding Nodes...");
      }
      current = expandNode(current, contextDocs, props, verbose);
      depth++;
    }

    best = new HMM(argBest, HMM.CONTEXT_HMM);
    best.train(contextDocs, props, verbose);

    if (verbose) {
      System.err.println();
      System.err.println("Search concluded");
      System.err.println(argBest);
      System.err.println("Likelihood= " + bestResult);
      System.err.println("Depth reached " + depth);
    }

    return (best);
  }


  /**
   * Does all the HMM-expanding operations to parent structure.
   *
   * @return The best child structure (after one greedy search step)
   */
  private MultiStructure expandNode(MultiStructure parent, Corpus train, Properties props, boolean verbose) {
    HMM hmm;
    HMM bestHmm;
    double max = Double.NEGATIVE_INFINITY;
    double result;
    int argmax = -1;

    MultiStructure[] children = generateChildren(parent, train, verbose);

    bestHmm = null;
    for (int k = 0; k < children.length; k++) {
      children[k].initializeTransitions(); // enrich structure for training
      System.err.println("operation " + k);
      hmm = new HMM(children[k], HMM.CONTEXT_HMM);
      if (verbose) {
        hmm.printTransitions();
      }
      hmm.train(train, props, verbose);
      double logLike = hmm.logLikelihood(train);
      result = hmm.mdlScore(train, logLike);
      if (verbose) {
        System.err.println("..............................");
        // note that calculation below mixes training and held out data
        // sensible??
        System.err.println("log likelihood = " + logLike);
        System.err.println("mdl score: " + result);
        System.err.println("..............................");
      }
      if (result > max) {
        max = result;
        argmax = k;
      }
    }

    if (verbose) {
      System.err.println("---------------------------------------------------");
      System.err.println("Done expanding");
      System.err.println("---------------------------------------------------");
      System.err.println("Max was " + argmax);
      System.err.println("likelihood score = " + max);
      if (argmax >= 0) {
        System.err.println("State structure (rows are sequences)");
        System.err.println(children[argmax]);
      }
      System.err.println("---------------------------------------------------");
    }

    if (argmax < 0) {
      improving = false;
      return null;
    } else if (max > bestResult) {
      bestResult = max;
      argBest = children[argmax];
      oneWorse = false;
    } else if (oneWorse) {
      improving = false;
    } else {
      oneWorse = true;
    }
    return children[argmax];
  }


  // helper method that generates all of the children structures
  private MultiStructure[] generateChildren(MultiStructure parent, Corpus train, boolean verbose) {
    System.err.println("generating children");
    // subtract 1 for (Background) in targetFields
    int numTargets = train.getTargetFields().length - 1;

    int numOps = parent.numBaseContexts(); // number of new context chains
    // to add
    numOps += parent.numContextChains(); // number of context chains to lengthen
    numOps += numTargets; // number of new prefix chains to add
    numOps += parent.numPrefixes(); // number of prefix chains to lengthen
    numOps += numTargets; // number of new suffix chains to add
    numOps += parent.numSuffixes(); // number of suffix chains to lengthen
    MultiStructure[] children = new MultiStructure[numOps];

    if (verbose) {
      System.err.println();
      System.err.println("Expanding structure: " + numOps + " children to check");
      System.err.println("-------------------");
    }

    int baseIndex = 0;
    for (int i = 0; i < numOps; i++) {
      children[i] = new MultiStructure(parent);
    }

    for (int i = 0; i < parent.numBaseContexts(); i++) {
      if (verbose) {
        System.err.println("Operation " + (baseIndex + i) + ": adding disjunction at context " + i);
      }
      children[baseIndex + i].addContext(i);
    }
    baseIndex += parent.numBaseContexts();
    for (int i = 0; i < parent.numContextChains(); i++) {
      if (verbose) {
        System.err.println("Operation " + (baseIndex + i) + ": lengthening context " + i);
      }
      children[baseIndex + i].lengthenContext(i);
    }
    baseIndex += parent.numBaseContexts();

    for (int i = 0; i < numTargets; i++) {
      if (verbose) {
        System.err.println("Operation " + (baseIndex + i) + ": adding prefix to target " + i);
      }
      children[baseIndex + i].addPrefix(i);
    }
    baseIndex += numTargets;

    for (int i = 0; i < parent.numPrefixes(); i++) {
      if (verbose) {
        System.err.println("Operation " + (baseIndex + i) + ": lengthening prefix " + i);
      }
      children[baseIndex + i].lengthenPrefix(i);
    }
    baseIndex += parent.numPrefixes();

    for (int i = 0; i < numTargets; i++) {
      if (verbose) {
        System.err.println("Operation " + (baseIndex + i) + ": adding suffix to target " + i);
      }
      children[baseIndex + i].addSuffix(i);
    }
    baseIndex += numTargets;

    for (int i = 0; i < parent.numSuffixes(); i++) {
      if (verbose) {
        System.err.println("Operation " + (baseIndex + i) + ": lengthening suffix " + i);
      }
      children[baseIndex + i].lengthenSuffix(i);
    }

    return children;
  }

  private static void dieUsage() {
    System.err.println("Usage: java edu.stanford.nlp.ie.hmm." + "ContextTrainer [-ncs numContextStates] trainingFile hmmFile targetField ...");
    System.err.println("  or: java edu.stanford.nlp.ie.hmm." + "ContextTrainer -cc [-ncs numContextStates] trainingFile targetField ...");
  }
}
