package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;

import java.util.Properties;

/**
 * A class to learn HMM structures  for a single target field by stochastic
 * optimization.  The learner
 * performs a greedy search of the structure space using average F1 over three
 * folds cross-validation on the training corpus.  At each iteration, the
 * learner can augment the structure in the following ways:  add a new prefix,
 * suffix, or target chain, or extend an existing prefix, suffix, or target
 * chain.  The learned structure is therefore a disjunction of prefix chains,
 * followed by a disjunction of target chains, followed by a disjunction of
 * suffix chains.
 * There are two possible termination conditions for the learning algorithm,
 * specified by the <tt>slTerminate</tt> property.  If <tt>slTerminate</tt> is
 * <tt>stopEarly</tt> the learner terminates if the MDL score (Bayesian prior)
 * decreases from the previous step, or if the F1 decreases twice in a row.
 * If the termination condition is instead <tt>maxDepth</tt>, the search
 * continues to the maximum depth, and the structure with the best F1 over all
 * depths is returned.
 *
 * @author Jim McFadden
 * @author Huy Nguyen
 */
public class StructureLearner {
  private Corpus trainDocs;
  private Properties props;
  private boolean verbose;

  private Corpus[] testers;   // cross-validation sets
  private Corpus[] trainers;

  private Structure[] bestStructures; // the best structure at each iteration
  private double[] bestF1s; // F1 score of best structure at each iteration


  //private int currentTester; // index for cycling through cross-validation sets
  private int maxDepth; // maximum depth that can be reached before terminating search
  private boolean stopEarly; // whether to terminate the search early based on mdlScore
  private double bestResult; // best average F1 score from cross-validation testing
  private Structure argBest; // structure that gave bestResult above
  private double mdlScore; // the MDL score of the best structure
  private boolean improving; // whether the F1 and MDL score are improving
  private boolean oneWorse; // allows the F1 score to drop once before terminating search

  /**
   * Creates a StructureLearner
   */
  public StructureLearner() {
    testers = new Corpus[3];
    trainers = new Corpus[3];
  }

  /**
   * Learns a good HMM structure by performing a greedy search of the structure
   * space.
   * Calls {@link #learnStructure(edu.stanford.nlp.ie.Corpus,edu.stanford.nlp.ie.Corpus,Properties,boolean) learnStructure(trainDocs,null,props,verbose)}
   */
  public Structure learnStructure(Corpus trainDocs, Properties props, boolean verbose) {

    return learnStructure(trainDocs, null, props, verbose);
  }

  /**
   * Learns a good HMM structure by performing a greedy search of the structure
   * space as described in the class comment.
   * Currently does nothing with the <tt>heldOut</tt> argument...
   */
  public Structure learnStructure(Corpus trainDocs, Corpus heldOut, Properties props, boolean verbose) {
    // HN TODO: use heldout data if provided
    //if(heldOut==null) {
    //}

    this.trainDocs = trainDocs;
    this.props = props;
    this.verbose = verbose;

    stopEarly = ("stopEarly".equals(props.getProperty("slTerminate"))) ? true : false;
    maxDepth = Integer.parseInt(props.getProperty("slMaxDepth"));

    bestStructures = new Structure[maxDepth];
    bestF1s = new double[maxDepth];

    // partition the corpus for cross-validation testing
    trainers[0] = (Corpus) trainDocs.splitRange(0, .67);
    testers[0] = (Corpus) trainDocs.splitRange(.67, 1);

    trainers[1] = (Corpus) trainDocs.splitRange(.33, 1);
    testers[1] = (Corpus) trainDocs.splitRange(0, .33);

    trainers[2] = (Corpus) trainDocs.splitRange(0, .33, .66, 1);
    testers[2] = (Corpus) trainDocs.splitRange(.33, .66);

    /*
    System.err.println("Corpus sizes");
    System.err.println("------------");
    System.err.println("Train = " + train.size());
    System.err.println("Test = " + test.size());
    System.err.println("trainer 0 = " + trainers[0].size());
    System.err.println("trainer 1 = " + trainers[1].size());
    System.err.println("trainer 2 = " + trainers[2].size());
    System.err.println("tester 0 = " + testers[0].size());
    System.err.println("tester 1 = " + testers[1].size());
    System.err.println("tester 2 = " + testers[2].size());
    System.err.println("--------------------------------");
    */
    //currentTester = 0;

    if (verbose) {
      System.err.println("*** Structure learning ***");
      System.err.println("Initializing start structure...");
    }
    Structure current;
    Structure start = new Structure();
    start.giveDefault();

    start.initializeTransitions(); // enrich structure for actual train/test
    bestResult = crossValidateTest(start);
    argBest = start;
    HMM hmm = new HMM(start, HMM.REGULAR_HMM);
    if (verbose) {
      System.err.println("Training on full set of training documents to get MDL score...");
    }
    hmm.train(trainDocs, props, false);
    mdlScore = hmm.mdlScore(trainDocs);

    int depth = 0;

    current = start;

    improving = true;
    oneWorse = false;

    while (depth < maxDepth) {
      if (stopEarly && !improving) {
        break; // termination condition reached
      }
      current = expandNode(current, depth);

      depth++;
      //currentTester = (currentTester + 1) % 3;
    }
    if (verbose) {
      System.err.println("*** Structure learning completed ***");
      System.err.println("Depth reached " + depth);
    }

    // Pick the best structure found at any depth
    if (!stopEarly) {
      if (verbose) {
        System.err.println("Selecting best structure across all depths");
      }
      double bestF1 = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < bestF1s.length; i++) {
        if (bestF1s[i] > bestF1) {
          argBest = bestStructures[i];
          bestF1 = bestF1s[i];
        }
      }
    }
    if (verbose) {
      System.err.println("Best structure overall (F1=" + bestResult + "):");
      new HMM(argBest, HMM.REGULAR_HMM).printTransitions();
    }

    return argBest;
  }

  /**
   * Performs one greedy search step in the structure space.  Creates a
   * daughter structure for each possible operation on the given parent,
   * and returns the daughter with the best average F1 on the cross-validation
   * sets.  Also has the "side effect" of setting <tt>improving</tt> and
   * <tt>oneWorse</tt>, indicating whether both the MDL and F1 scores have
   * improved over the previous iteration, or whether the F1 score has actually
   * gotten worse, respectively.
   */
  private Structure expandNode(Structure parent, int depth) {
    Structure[] children;
    double max = -1;
    double result;
    int argmax = -1;

    int num_ops = 3 + parent.numPrefixes() + parent.numTargets() + parent.numSuffixes();
    children = new Structure[num_ops];

    if (verbose) {
      System.err.println("\nExpanding structure:");
      new HMM(parent, HMM.REGULAR_HMM).printTransitions();
      System.err.println(num_ops + " children to be checked");
    }

    for (int k = 0; k < num_ops; k++) {
      children[k] = doOp(parent, k);
      children[k].initializeTransitions(); // enrich structure for train/test
      result = crossValidateTest(children[k]);
      if (verbose) {
        System.err.println("cross validated mean result = " + result);
        System.err.println("..............................");
      }
      if (result > max) {
        max = result;
        argmax = k;
      }
    }

    if (verbose) {
      System.err.println("Done expanding");
      System.err.println("--------------");
      System.err.print("Max: op " + argmax);
      System.err.println(" F1: " + max);
      System.err.println("--------------");
    }

    if (stopEarly) {
      if (max > bestResult) {
        // if the structure improves performance, check to see if the
        // MDL score also improved
        HMM hmm = new HMM(children[argmax], HMM.REGULAR_HMM);
        if (verbose) {
          System.err.println("Training on full set of training documents to get MDL score...");
        }
        hmm.train(trainDocs, props, false);
        double curMdlScore = hmm.mdlScore(trainDocs);
        if (curMdlScore > mdlScore) {
          if (verbose) {
            System.err.println("MDL score improved.");
          }
          mdlScore = curMdlScore;

          bestResult = max;
          argBest = children[argmax];
          oneWorse = false;
        } else {
          if (verbose) {
            System.err.println("MDL score did not improve.");
          }
          improving = false;
        }
      } else {
        if (oneWorse) {
          improving = false;
        } else {
          oneWorse = true;
        }
      }
    }
    bestStructures[depth] = children[argmax];
    bestF1s[depth] = max;

    return children[argmax];
  }

  /**
   * Performs the given operation on the parent structure, and returns the
   * resulting daughter structure.  The operations are encoded as follows:
   * <p/>
   * 0 : add new prefix chain
   * 1 : add new target chain
   * 2 : add new suffix chain
   * 3...3+numPrefixes: extend prefix op-3
   * firstTarget...firstTarget+numTargets: extend target op-firstTarget
   * firstSuffix...firstSuffix+numSuffixes: extend suffix op-firstSuffix
   */
  private Structure doOp(Structure parent, int op) throws IllegalArgumentException {
    Structure child = parent.copy();
    int firstTarget = 3 + parent.numPrefixes();
    int firstSuffix = firstTarget + parent.numTargets();
    switch (op) {
      case 0:
        if (verbose) {
          System.err.println("0: Adding prefix...");
        }
        child.addPrefix(1);
        break;

      case 1:
        if (verbose) {
          System.err.println("1: Adding target...");
        }
        child.addTarget(1);
        break;

      case 2:
        if (verbose) {
          System.err.println("2: Adding suffix...");
        }
        child.addSuffix(1);
        break;

      default:
        if (op - 3 < parent.numPrefixes()) {
          if (verbose) {
            System.err.println(op + ": Lengthening prefix " + (op - 3) + "...");
          }
          child.lengthenPrefix(op - 3);
        } else if (op - firstTarget < parent.numTargets()) {
          if (verbose) {
            System.err.println(op + ": Lengthening target " + (op - firstTarget) + "...");
          }
          child.lengthenTarget(op - firstTarget);
        } else if (op - firstSuffix < parent.numSuffixes()) {
          if (verbose) {
            System.err.println(op + ": Lengthening suffix " + (op - firstSuffix) + "...");
          }
          child.lengthenSuffix(op - firstSuffix);
        } else {
          System.err.println("ERROR: op out of range: " + op);
          throw(new IllegalArgumentException());
        }
    }
    return child;
  }

  /**
   * Iterates over the cross validation sets, training and testing an HMM
   * for the given structure on each set.  Returns the average F1 score.
   */
  private double crossValidateTest(Structure struc) {
    int trials = testers.length;
    double totalScore = 0;
    for (int i = 0; i < trials; i++) {
      HMM hmm = new HMM(struc, HMM.REGULAR_HMM);
      hmm.train(trainers[i], props, false);
      //hmm.printProbs();
      totalScore += new HMMTester(hmm).test(testers[i]);
    }
    return totalScore / trials;
  }

  /**
   * Main method to run the StructureLearning algorithm using the default
   * properties.  Usage: <code>java StructureLearner <training file>
   * <testing file> <target field> </code>.
   * Use @link Extractor for more control.
   * parameters.
   *
   * @see Extractor
   */
  public static void main(String[] args) {
    if (args.length != 3) {
      System.out.println("Usage: java StructureLearner <training file> <testing file> <target field>\n");
      return;
    }
    String trainFile = args[0];
    String testFile = args[1];
    String targetField = args[2];
    Properties props = Extractor.getDefaultProperties();

    Corpus train = new Corpus(trainFile, targetField);
    Corpus test = new Corpus(testFile, targetField);

    StructureLearner learner = new StructureLearner();
    Structure struc = learner.learnStructure(train, test, props, true);

    HMM gb = new HMM(struc, HMM.REGULAR_HMM);
    gb.train(train, props, false);
    gb.printProbs();
    new HMMTester(gb).test(test, props, false, false, true);
  }
}
