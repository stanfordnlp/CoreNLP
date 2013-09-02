package edu.stanford.nlp.ie.hmm;

import edu.stanford.nlp.ie.Corpus;
import edu.stanford.nlp.io.FileArrayList;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.stats.PRStatsManager;
import edu.stanford.nlp.util.Pair;

import java.io.*;
import java.net.InetAddress;
import java.util.*;

/**
 * Command-line utility for training and testing HMM performance. Can be used
 * to load and test a pre-trained HMM or to train and test a number of
 * pre-defined HMMs. <br>
 * <br>
 * <h3>Quick-Start guide to Extractors</h3>
 * Although the Extractor properties allow you great flexibility in training
 * your HMMs, the list of properties can be quite daunting (see below).
 * This section highlights the most important properties in order for you to get
 * an HMM extractor up and running quickly.<br>
 * <br>
 * Minimally, you must specify the path to a <b>dataFile</b> containing the corpus
 * to train on.  A fraction of the documents from the same file can also be used
 * to test the Extractor following training.  This is specified by <b>cvSlices</b>,
 * which defaults to 10.  This means that 1/10th of the documents will be held out
 * for testing.<br>
 * <br>
 * Next, you must specify the <b>targetFields</b> that you want the HMM to extract. <br>
 * <br>
 * You must also specify the <b>hmmType</b> of the type of extractor you want to create.
 * The most commonly used modes are <tt>s-merged</tt> for single-field extractors
 * and <tt>merged</tt> for multi-field extractors.  Both of these modes allow you
 * some flexibility in specifying the transition structure of your HMM.  This includes
 * control over the number of context states preceding and following each target
 * and their topology, as well as the number/topology of the target states.<br>
 * <br>
 * <b>contextType</b> is used to specify the topology of the context states.  For
 * single-field HMMs, <tt>fixed</tt> will provide prefix and suffix chains of length
 * specified by <b>ncs</b>, and is the most commonly used option.  For multi-field
 * HMMs with many fields <tt>flexible</tt> is a better alternative because it models
 * transitions between specific pairs of targets by inserting intermediate context
 * states. <br>
 * <br>
 * For modeling target phrases, <b>nts</b> determines the number of target states.
 * Setting <b>targetType</b> to <tt>chain</tt> arranges these states in a chain
 * (with skip-aheads), and produces the best empirical results currently. <br>
 * <br>
 * Finally, you may want to save your HMMs to disk.  In order for Extractor to
 * serialize your HMM, you need only specify an <b>hmmOutfilePrefix</b>. <br>
 * <br>
 * A sample properties file for a single-field Extractor might look like: <br>
 * <br>
 * <p/>
 * <code><pre>description=basic single-field Extractors
 * dataFile=/u/nlp/data/iedata/acquisitions.txt
 * targetFields=purchaser
 * <p/>
 * hmmType=s-merged
 * contextType=fixed
 * ncs=3
 * targetType=chain
 * nts=4
 * cvFolds=1
 * cvSlices=10
 * hmmOutfilePrefix=tmp/purchaser</pre></code>
 * <p/>
 * A sample Properties file for a basic multi-field Extractor might look like: <br>
 * <br>
 * <code><pre>description=basic multi-field Extractor
 * dataFile=/u/nlp/data/iedata/acquisitions.txt
 * targetFields=purchaser acquired dlramt
 * <p/>
 * hmmType=merged
 * contextType=flexible
 * targetType=chain
 * nts=4
 * cvFolds=1
 * cvSlices=10</pre></code>
 * <p/>
 * Control of how to train and test is governed by a properties
 * file (as stored from {@link Properties java.util.Properties}), with the following
 * available options:
 * <table border=1 cellpadding=3 cellspacing=0 style="font: 8pt monospace">
 * <tr bgcolor="#ff9fff"><th>property</th><th>values</th><th>default</th></tr>
 * <p/>
 * <tr><td colspan=3 bgcolor="#9fffff">Universal extractor properties</td></tr>
 * <tr><td>description</td><td>text describing this properties file</td></td><td>&nbsp;</td></tr>
 * <tr><td>dataFile</td><td>filename of corpus to train/test on</td><td>&nbsp;</td></tr>
 * <tr><td>testFile</td><td>optional separate test file to use (instead of splitting up dataFile; cvFolds/cvSlices will be ignored)</td><td>&nbsp;</td></tr>
 * <tr><td>basedir</td><td>base directory of separate document files if they're not in a corpus (alternative to dataFile)</td><td>&nbsp;</td></tr>
 * <tr><td>splitdir</td><td>dir with files for explicit train/test splits (relative to basedir)</td><td>&nbsp;</td></tr>
 * <tr><td>targetFields</td><td>space-separated list of target fields</td></td><td>&nbsp;</td></tr>
 * <tr><td>singleMatchStrategy</td><td>best_match (return highest-prob extracted text), first_match (return earliest extracted text)</td><td>best_match</td></tr>
 * <p/>
 * <tr><td colspan="3" bgcolor="#9fffff">HMM training properties</td></tr>
 * <tr><td>hmmType</td><td>merged (make HMM for all fields at once),
 * s-merged (make merged HMM for one field at a time),
 * simple11 (one prefix, suffix, target, and background state),
 * learned (learn single-field HMM structure via structure learning),
 * context (context HMM for multiple targets, training only),
 * s-context (separate context HMMs for each target, training only),
 * target (target HMMs for each target, training only),
 * simple34, ergodic, jim, chris, chris2, frmc</td>
 * <td>&nbsp;</td></tr>
 * <tr><td>uniformTransSmoothing</td><td>fraction of uniform distribution to mix in with predefined hmmType</td><td>0.0</td></tr>
 * <tr><td>trainType</td><td>joint, conditional</td><td>joint</td></td></tr>
 * <tr><td>numCandidates</td><td>number of attempts to train each HMM (single candidate with highest likelihood is kept)</td><td>1</td></tr>
 * <tr><td>candidateEvaluation</td><td>what metric to use for picking the best candidate HMM (<tt>held_out_f1</tt>, <tt>likelihood</tt>)</td><td>held_out_f1</td></tr>
 * <tr><td>heldOutFraction</td><td>fraction of training data to hold out for evaluating candidate HMMs if candidateEvaluation=held_out_f1 (best candidate gets retrained on all training data)</td><td>0.25</td></tr>
 * <tr><td>targetType (can also be specified per target by <target>.targetType</td><td>chain, ergodic, learned (learning by searching structure space), learned2 (learning by starting with large ergodic structure and pruning based on KL-divergence), lchain (learns best chain length)</td><td>chain</td></td></tr>
 * <tr><td>nts (can also be specified per target by <target>.nts</td><td># of target states</td><td>4</td></tr>
 * <tr><td>nbs</td><td>Total number of background states when hmmType=ergodic or contextType=ergodic</td><td>8</td></tr>
 * <tr><td>contextType</td><td>fixed (uses <tt>ncs</tt> and single global
 * background), learned (structure learning), flexible (single global
 * background, and uses
 * <tt>clInitThreshold</tt> to connect some targets), ergodic (all
 * states are connected, uses <tt>nbs</tt>)</td><td>fixed</td></td></tr>
 * <tr><td>ncs</td><td># of prefix/suffix states around each target for
 * fixed context s-merged/merged HMMs. </td><td>3</td></tr>
 * <tr><td>clInitThreshold</td><td>Threshold used to determine the maximum
 * separation of two targets under which an in-between context state is
 * added during context learning
 * (contextType=learned, flexible)</td><td>5</td></tr>
 * <tr><td>slTerminate</td><td>Termination condition for structure learning:
 * stopEarly (stop when MDL score decreases), maxDepth
 * (stop when max depth has been reached)</td><td>maxDepth</td></tr>
 * <tr><td>slMaxDepth</td><td>Maximum depth to search before terminating in
 * structure learning</td><td>20</td></tr>
 * <tr><td>sigmaSquared</td><td>denominator of conditional penalty term
 * (bigger = weaker penalty)</td><td>10000</td></tr>
 * <p/>
 * <tr><td colspan="3" bgcolor="#9fffff">Prior smoothing properties</td></tr>
 * <tr><td>pseudoTransitionsCount</td><td>real number: this many pseudo counts
 * are divided uniformly among the transitions for each state, giving
 * a smoothing prior</td><td>0.0</td></tr>
 * <tr><td>pseudoEmissionsCount</td><td>real number: this many pseudo counts
 * are divided uniformly among the vocab for each state, giving
 * a smoothing prior</td><td>0.0</td></tr>
 * <tr><td>pseudoUnknownsCount</td><td>real number: this many pseudo counts
 * are divided uniformly among the uknown feature bundle emissions
 * for each state, giving
 * a smoothing prior [not yet]</td><td>0.0</td></tr>
 * <tr><td>initEmissions</td><td>whether to initialize the emissions based
 * on the training data</td><td>true</td></tr>
 * <p/>
 * <tr><td colspan="3" bgcolor="#9fffff">Unknown word properties</td></tr>
 * <tr><td>unseenMode</td><td>nonexistent, unk_low_counts, hold_out_mass, use_char_ngrams</td>
 * <td>hold_out_mass</td></tr>
 * <tr><td>unkModel</td><td>single_unk, featural_decomp</td><td>featural_decomp</td></tr>
 * <tr><td>unseenProbSource</td><td>where the probability of seeing an unseen word are computed from (singletons, held_out)</td><td>held_out</td></tr>
 * <tr><td>featureSource</td><td>where the probabilities of the featural decomposition of unseen words is computed from (singletons, held_out)</td><td>singletons</td></tr>
 * <tr><td>shrinkage</td><td>whether to use shrinkage</td><td>&nbsp;</td></tr>
 * <tr><td>unkFeature</td><td>What class to use for featural decomposition. Must extend edu.stanford.nlp.ie.hmm.Feature interface.</td><td>edu.stanford.nlp.process.NumAndCapFeature</td></tr>
 * <tr><td>maxNGramLength</td><td>Max length of char n-gram if using use_char_ngrams</td><td>6</td></tr>
 * <p/>
 * <tr><td colspan="3" bgcolor="#9fffff">Cross-validation/testing properties</td></tr>
 * <tr><td>cvSlices</td><td># sections to divide corpus into for cross-validation (0=train on entire corpus)</td><td>10</td></tr>
 * <tr><td>cvFolds</td><td># iterations of cross validation to run </td><td>cvSlices</td></tr>
 * <tr><td>startFold</td><td># of first fold to run (1-cvSlices)</td><td>1</td></tr>
 * <tr><td>bestAnswerOnly</td><td>whether to work in slot-filler mode (true) or
 * strict scoring mode (false)</td><td>true</td></tr>
 * <tr><td>viterbiPrintMode</td><td>How to present the results of viterbi decoding (if verbose).
 * Can either be presented as rows of number sequences for guessed/correct types and states (num_sequences) or words labeled with types and states (typed_words)</td><td>typed_words</td></tr>
 * <tr><td>showHMMGraph</td><td>whether to pop up graphs of the trained HMM for each fold</td><td>&nbsp;</td></tr>
 * <tr><td>hmmOutfilePrefix</td><td>file name prefix to serialize trained HMM(s) to
 * (fold number and .hmm will be appended, no serialization will be performed
 * if this property is omitted)</td><td>&nbsp;</td></tr>
 * <tr><td colspan="3" bgcolor="#9fffff">Corpus properties</td></tr>
 * <tr><td>discardHtml</td><td>whether to discard html tags</td><td>true</td></tr>
 * <tr><td colspan="3" bgcolor="#9fffff">HMM loading properties</td></tr>
 * <tr><td>hmmFile (can also be specified per target by <target>.hmmFile, use context.hmmFile to specify a serialized context HMM)</td><td>filename of serialized hmm to load and test on</td><td>&nbsp;</td></tr>
 * </table>
 * If <tt>hmmType</tt> is provided, it is assumed that this is a train/test extraction.
 * Otherwise, it is assumed to be a load/test extraction, in which case <tt>hmmFile</tt>
 * must be provided.
 * <p/>
 * If <tt>dataFile</tt> is provided, the corpus is read from a single file and split
 * internally for training and testing. Alternatively, you can specify <tt>splitdir</tt>,
 * which should contains files called <tt>train.#</tt> and <tt>test.#</tt> where <tt>#</tt>
 * goes from 1-cvSlices. These files should list files to use for training and testing in the
 * given fold, one per line. File paths are relative to <tt>basedir</tt> or absolute if it's null.
 * Alternatively, you can specify <tt>testFile</tt> and then <tt>dataFile</tt> will just be used
 * for training and <tt>testFile</tt> will be used for testing (this implicitly sets the number
 * of folds to 1 so that the entire training file is used once).
 * <p/>
 * These properties are the same ones used by {@link HMM}.
 *
 * @author Huy Nguyen (htnguyen@stanford.edu)
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @see #main
 * @see #getStructure
 * @see MergeTrainer#mergeTrain
 */
public class Extractor {
  // name of aggregate stats stored in PRStatsManager
  private static final String overallStatsName = "OVERALL";

  private static final boolean josephAtPlay = false;

  // defaults for all properties (created the first time it's called)
  private static Properties defaultProperties = null;

  // private constructor to prevent direct instantiation
  private Extractor() {
  }

  /**
   * Old Extractor code to create various structure types.
   * <p>Valid hmm types are:
   * <ul>
   * <li><tt><b>simple01</b></tt> - bare minimal HMM with one bg and one target
   * <li><tt><b>simple11</b></tt> - simple minimal default HMM structure,
   * with one prefix, suffix, and target.
   * <li><tt><b>simple34</b></tt> - more complex structure, built by
   * <tt>Structure</tt>, involving 3 prefixes, 3 suffixes, and 4 (2x2) targets.
   * <li><tt><b>ergodic</b></tt> - ergodic (fully connected) structure with
   * background and target state numbers given by nbs and nts properties
   * (if set; default is 8 background and 4 target states)
   * <li><tt><b>jim</b></tt> - {@link Structure#jimDefaultStates jim default structure}
   * <li><tt><b>chris</b></tt> - {@link Structure#chrisDefaultStates chris default structure}
   * <li><tt><b>chris2</b></tt> - {@link Structure#chrisDefaultStates2 chris default structure 2}
   * <li><tt><b>frmc</b></tt> - {@link Structure#frmcComplexStructure Freitag & McCallum complex structure}
   * </ul>
   * Throws an IllegalArgumentException if the type is not valid.
   */
  public static Structure getStructure(String hmmType, Properties props) {
    Structure struc = null;
    if ("simple01".equals(hmmType)) {
      struc = new Structure();
      struc.giveSimplest();
      struc.initializeTransitions(); // enrich the structure
    } else if ("simple11".equals(hmmType)) {
      struc = new Structure();
      struc.giveDefault();
      struc.initializeTransitions(); // enrich the structure
    } else if ("simple34".equals(hmmType)) {
      struc = new Structure();
      struc.giveDefault();
      struc.lengthenPrefix(0);
      struc.lengthenPrefix(0);
      struc.lengthenTarget(0);
      struc.addTarget(2);
      struc.lengthenSuffix(0);
      struc.lengthenSuffix(0);
      struc.initializeTransitions(); // enrich the structure
    } else if ("ergodic".equals(hmmType)) {
      struc = new Structure();
      int nbs = Integer.parseInt(props.getProperty("nbs"));
      int nts = Integer.parseInt(props.getProperty("nts"));
      int[] stateTypes = new int[nbs + nts];
      for (int i = 0; i < nbs; i++) {
        stateTypes[i] = Structure.BACKGROUND_TYPE;
      }
      for (int i = nbs; i < nbs + nts; i++) {
        stateTypes[i] = Structure.TARGET_TYPE;
      }
      struc.giveErgodic(stateTypes);
    } else if ("jim".equals(hmmType)) {
      struc = new Structure(Structure.jimDefaultStates());
    } else if ("chris".equals(hmmType)) {
      struc = new Structure(Structure.chrisDefaultStates());
    } else if ("chris2".equals(hmmType)) {
      struc = new Structure(Structure.chrisDefaultStates2());
    } else if ("frmc".equals(hmmType)) {
      struc = Structure.frmcComplexStructure();
    } else {
      throw(new IllegalArgumentException("Invalid hmm type: " + hmmType));
    }

    // mixes in uniform smoothing as per properties (default: do nothing)
    double frac = Double.parseDouble(props.getProperty("uniformTransSmoothing"));
    struc.addUniformSmoothing(frac);

    return (struc);
  }

  /**
   * Returns suitable default properties for use with Extractor and HMM.
   * See the class comment for details on which defaults have been chosen.
   * A shared Properties is returned for all calls of this method, so don't
   * change what you get (just use this as the default in the Properties
   * constructor).
   */
  public static Properties getDefaultProperties() {
    if (defaultProperties == null) {
      defaultProperties = new Properties();

      // training
      defaultProperties.setProperty("trainType", "joint");
      defaultProperties.setProperty("targetType", "chain");
      defaultProperties.setProperty("nts", "4");
      defaultProperties.setProperty("nbs", "8");
      defaultProperties.setProperty("contextType", "fixed");
      defaultProperties.setProperty("ncs", "3");
      defaultProperties.setProperty("numCandidates", "1");
      defaultProperties.setProperty("candidateEvaluation", "held_out_f1");
      defaultProperties.setProperty("heldOutFraction", "0.25");
      defaultProperties.setProperty("uniformTransSmoothing", "0.0");
      defaultProperties.setProperty("bestAnswerOnly", "true");
      defaultProperties.setProperty("singleMatchStrategy", "best_match");
      defaultProperties.setProperty("slMaxDepth", "20");

      // cv (cvFolds defaults to cvSlices)
      defaultProperties.setProperty("cvSlices", "10");
      defaultProperties.setProperty("startFold", "1");

      defaultProperties.setProperty("initEmissions", "true");

      // unknown words
      defaultProperties.setProperty("unkFeature", "edu.stanford.nlp.process.NumAndCapFeature");
      defaultProperties.setProperty("unseenMode", "hold_out_mass");
      defaultProperties.setProperty("unkModel", "featural_decomp");
      defaultProperties.setProperty("unseenProbSource", "held_out");
      defaultProperties.setProperty("featureSource", "singletons");
      defaultProperties.setProperty("maxNGramLength", "6");

      // shrinkage
      //defaultProperties.setProperty("shrinkage","on");
      defaultProperties.setProperty("pseudoTransitionsCount", "0.0");
      defaultProperties.setProperty("pseudoEmissionsCount", "0.0");
      defaultProperties.setProperty("pseudoUnknownsCount", "0.0");

      // conditional training specific
      defaultProperties.setProperty("sigmaSquared", "10000");

      // context learning specific
      defaultProperties.setProperty("clInitThreshold", "5");
      defaultProperties.setProperty("discardHtml", "true");
    }
    return (defaultProperties);
  }

  /**
   * Runs the extractor utility (either in train or load mode).
   * <p/>
   * Usage: <code>java edu.stanford.nlp.ie.hmm.Extractor [-v]
   * propertiesfile</code>
   *
   * @param args Command line arguments, as above
   */
  public static void main(String[] args) {
    Structure.rand = new Random(0); // JS: TAKE ME OUT - HACK TO KEEP THINGS DETERMINISTIC
    if (args.length == 0) {
      dieUsage(null); // minimal requirements
    }

    int arg = 0; // current arg index

    boolean verbose = false; // whether to print out debug info
    if (args[arg].equals("-v")) {
      verbose = true;
      arg++;
    }

    // prints header identifying hostname and start date
    if (verbose) {
      String hostname;
      try {
        hostname = InetAddress.getLocalHost().getHostName();
      } catch (Exception e) {
        hostname = null;
      }
      System.err.print("Started Extractor");
      if (hostname != null) {
        System.err.print(" on " + hostname);
      }
      System.err.println(" at " + new Date());
    }

    if (arg == args.length) {
      dieUsage("must specify properties file");
    }
    Properties props = new Properties(getDefaultProperties());
    try {
      props.load(new FileInputStream(args[arg]));
    } catch (Exception e) {
      dieUsage("error loading properties file: " + e);
    }

    // thought this might be useful for stored printouts
    if (props.getProperty("description") != null) {
      System.err.println("Description of properties used: " + props.getProperty("description"));
    }
    if (verbose) {
      props.list(System.err); // prints all key/value pairs
    }

    // data file for training/testing
    String dataFilename = props.getProperty("dataFile");

    // space-separated list of target fields
    String allTargetFields = props.getProperty("targetFields");
    if (allTargetFields == null || allTargetFields.length() == 0) {
      dieUsage("must specify at least one target field");
    }
    String[] targetFields = allTargetFields.split(" ");

    boolean bestAnswerOnly = Boolean.parseBoolean(props.getProperty("bestAnswerOnly"));
    boolean useFirstAnswers = "first_match".equals(props.getProperty("singleMatchStrategy"));

    // discard html unless false is explicitly specified
    boolean discardHtml = !"false".equals(props.getProperty("discardHtml"));

    String hmmType = props.getProperty("hmmType");
    if (hmmType != null) {
      // whether to train a merged HMM for all fields
      boolean useMerged = hmmType.equals("merged");
      // whether to train a separate merged HMM for each field
      boolean useSMerged = hmmType.equals("s-merged");
      // whether to use StructureLearner to learn the HMM structure
      boolean useLearned = hmmType.equals("learned");

      // gets predefined structure for non-merged hmm types
      Structure structure = null; // structure to train this hmm with
      if (!useMerged && !useSMerged && !useLearned && !hmmType.equals("target") && !hmmType.equals("context") && !hmmType.equals("s-context")) {
        try {
          structure = getStructure(hmmType, props);
        } catch (IllegalArgumentException e) {
          dieUsage("invalid hmmType: " + hmmType);
        }
      }

      // number of pieces to split up corpus into (use one slice for testing in each run of cv)
      int numSlices = Integer.parseInt(props.getProperty("cvSlices"));

      boolean runTest = (numSlices > 0); // whether to test or just train

      String testFilename = props.getProperty("testFile");
      if (testFilename != null) {
        numSlices = 0;
        runTest = true; // numSlices is 0, but we have a test file so use it
      }

      if (hmmType.equals("target") || hmmType.equals("context") || hmmType.equals("s-context")) {
        // since these are partial HMMs, you can't really test them
        runTest = false;
      }

      // number of cross-validation iterations to run
      // (numSlices if not present or blank)
      int numFolds, startFold, endFold;
      if (numSlices == 0) { // set some sensible values in this case
        numFolds = 1;
        startFold = 1;
        endFold = 1;
      } else {
        numFolds = numSlices;
        if (props.getProperty("cvFolds") != null) {
          numFolds = Integer.parseInt(props.getProperty("cvFolds"));
        }
        startFold = Integer.parseInt(props.getProperty("startFold"));
        if (startFold + numFolds - 1 > numSlices) {
          numFolds = numSlices - startFold + 1; // don't run off the end
        }
        endFold = startFold + numFolds - 1; // inclusive
      }

      // dir containing train.# and test.# files that list documents in
      // predefined splits (paths relative to basedir)
      File splitdir = null;
      if (props.getProperty("splitdir") != null) {
        splitdir = new File(props.getProperty("splitdir"));
      }
      File basedir = null;
      if (props.getProperty("basedir") != null) {
        basedir = new File(props.getProperty("basedir"));
      }

      if (verbose) {
        switch (numSlices) {
          case 0:
            System.err.println("Training on entire corpus");
            break;
          case 1:
            System.err.println("WARNING: 1 slice -> testing on entire corpus");
            break;
          default:
            System.err.println("Testing on 1/" + numSlices + " of the corpus for each fold");
        }
      }

      double delta = (numSlices == 0 ? 0 : 1.0 / numSlices); // percent of corpus to move over after each fold (0 slices -> train on entire corpus)
      int numTests = ((useMerged || hmmType.equals("context")) ? 1 : targetFields.length); // do one test for merged or separate tests for each field normally

      int numCandidates = Integer.parseInt(props.getProperty("numCandidates")); // number of candidate HMMs to train each time (best one is selected)
      boolean trainConditionally = props.getProperty("trainType").equals("conditional");
      boolean evaluateOnLikelihood = props.getProperty("candidateEvaluation").equals("likelihood"); // evaluate candidates on training likelihood or held out f1
      double heldOutFraction; // held out fraction for f1 candidate eval
      if (numCandidates == 1 || evaluateOnLikelihood) {
        heldOutFraction = 0.0;
      } else {
        heldOutFraction = Double.parseDouble(props.getProperty("heldOutFraction"));
      }

      if (hmmType.equals("target") || hmmType.equals("context") || hmmType.equals("s-context")) {
        // since we can't test these partial HMMs, must evaluate
        // candidates on likelihood
        evaluateOnLikelihood = true;
      }

      PRStatsManager<String> manager = new PRStatsManager<String>();
      List<Pair<String, Double>> spread = new LinkedList<Pair<String, Double>>();
      for (int i = 0; i < numTests; i++) {
        String[] curTargets;
        // all targets at once for merged, one at a time otherwise
        if (useMerged || hmmType.equals("context")) {
          curTargets = targetFields;
        } else {
          // trains and tests an HMM on the current field
          System.err.println("Training and testing on " + targetFields[i] + " field...");
          curTargets = new String[]{targetFields[i]};
        }
        Corpus data = null;
        if (splitdir == null) {
          // reads in all files in basedir
          if (basedir != null) {
            if (!basedir.isDirectory()) {
              dieUsage(basedir + "is not a directory");
            }
            File[] dataFiles = basedir.listFiles(new FileFilter() {
              public boolean accept(File file) {
                // ignore subdirectories
                return file.isFile();
              }
            });
            ArrayList<File> dataFileList = new ArrayList<File>();
            for (int j = 0; j < dataFiles.length; j++) {
              dataFileList.add(dataFiles[j]);
            }
            data = new Corpus(curTargets);
            data.load(dataFileList, FreitagIECollectionIterator.factory(curTargets, discardHtml));
          } else {
            // load corpus once and use it for all folds
            if (dataFilename == null) {
              dieUsage("must specify a datafile");
            }
            if (!new File(dataFilename).canRead()) {
              dieUsage("cannot read datafile: " + dataFilename);
            }
            data = new Corpus(dataFilename, curTargets, discardHtml);
          }
        }
        
        
        for (int j = startFold; j <= endFold; j++) {
          // does the current train/test cv fold
          if (startFold != 0 || numFolds > 1) {
            System.err.println("------ Fold " + j + " of " + endFold + ": ------");
          }
          double offset = (j - 1) * delta; // start of current test portion (starts from 0)
          Corpus trainDocs, heldOutDocs, testDocs;
          if (testFilename != null) {
            // use test file property if provided (dataFile is entirely for training)
            if (verbose) {
              System.err.println("Using separate test file: " + testFilename);
            }
            Corpus[] corpora = (Corpus[]) data.split(0, new double[]{1.0 - heldOutFraction, heldOutFraction});
            trainDocs = corpora[0];
            heldOutDocs = corpora[1];
            testDocs = new Corpus(testFilename, curTargets);
          } else if (splitdir == null) {
            // splits up data from dataFile into train/held-out/test
            double trainFraction = 1.0 - delta;
            Corpus[] corpora = (Corpus[]) data.split(offset, new double[]{trainFraction * (1.0 - heldOutFraction), trainFraction * heldOutFraction, delta});
            trainDocs = corpora[0];
            heldOutDocs = corpora[1];
            testDocs = corpora[2];
          } else {
            // training docs for this fold are listed in train.#
            File trainFile = new File(splitdir, "train." + j);
            FileArrayList trainFiles = new FileArrayList(trainFile, basedir);
            trainDocs = new Corpus(curTargets);

            // test docs for this fold are listed in train.#
            File testFile = new File(splitdir, "test." + j);
            FileArrayList testFiles = new FileArrayList(testFile, basedir);
            testDocs = new Corpus(curTargets);

            try {
              if (verbose) {
                System.err.println("Reading in " + trainFiles.size() + " training files from " + trainFile);
              }
              trainDocs.load(trainFiles, FreitagIECollectionIterator.factory(curTargets));
            } catch (Exception e) {
              System.err.println("Error reading training docs");
              e.printStackTrace();
            }

            // splits off part of the training corpus for held out data
            Corpus[] corpora = (Corpus[]) trainDocs.split(0, new double[]{1.0 - heldOutFraction, heldOutFraction});
            trainDocs = corpora[0];
            heldOutDocs = corpora[1];

            try {
              if (verbose) {
                System.err.println("Reading in " + testFiles.size() + " test files from " + testFile);
              }
              testDocs.load(testFiles, FreitagIECollectionIterator.factory(curTargets));
            } catch (Exception e) {
              System.err.println("Error reading training docs");
              e.printStackTrace();
            }
          }
          System.err.println("Training HMM of type " + hmmType + " on " + trainDocs.size() + " docs...");
          HMM hmm = null; // best candidate hmm
          double bestScore = Double.NEGATIVE_INFINITY; // best score of any candidate hmm so far
          if (verbose && numCandidates > 1) {
            System.err.print("Training " + numCandidates + " candidate HMMs, evaluated on ");
            if (evaluateOnLikelihood) {
              System.err.println("training " + (trainConditionally ? "conditional" : "joint") + " likelihood");
            } else {
              System.err.println("held out F1 (" + (heldOutFraction * 100) + "% of training data)");
            }
          }
          for (int k = 0; k < numCandidates; k++) {
            if (verbose && numCandidates > 1) {
              System.err.println("Training candidate HMM " + (k + 1) + " of " + numCandidates);
            }
            HMM candidateHMM;
            if (useMerged || useSMerged) {
              // merged hmm for all fields
              candidateHMM = MergeTrainer.mergeTrain(trainDocs, props, verbose);
            } else if (useLearned) {
              structure = new StructureLearner().learnStructure(trainDocs, props, verbose);
              candidateHMM = new HMM(structure, HMM.REGULAR_HMM);
            } else if (hmmType.equals("context") || hmmType.equals("s-context")) {
              candidateHMM = new ContextTrainer().train(trainDocs, props, curTargets, verbose);
            } else if (hmmType.equals("target")) {
              candidateHMM = new TargetTrainer().train(trainDocs, targetFields[i], props, verbose);
            } else {
              // hmm for current field
              candidateHMM = new HMM(structure, HMM.REGULAR_HMM);
              if (josephAtPlay) {
                candidateHMM.setTestCorpus(testDocs); // TAKE ME OUT - HACK FOR MEASURING CONDITIONAL TRAINING THROUGH ITERATIONS
              }
              candidateHMM.train(trainDocs, props, verbose);
            }


            double candidateScore = 0;
            if (numCandidates > 1) {
              if (evaluateOnLikelihood) {
                candidateScore = trainConditionally ? candidateHMM.logConditionalLikelihood(trainDocs) : candidateHMM.logLikelihood(trainDocs);
              } else {
                candidateScore = new HMMTester(candidateHMM).test(heldOutDocs, props, bestAnswerOnly, useFirstAnswers, false);
              }
              if (verbose) {
                System.err.println("Score for candidate HMM " + (k + 1) + " of " + numCandidates + ": " + candidateScore);
              }
            }
            if (k == 0 || candidateScore > bestScore) {
              hmm = candidateHMM;
              bestScore = candidateScore;
              if (verbose && numCandidates > 1) {
                System.err.println("Candidate " + (k + 1) + " is best HMM so far");
              }
            }
          }
          if (hmm == null) {
            throw(new IllegalStateException("No candidate HMM was selected!"));
          }

          if (!evaluateOnLikelihood && heldOutDocs.size() > 0) {
            if (verbose) {
              System.err.println("Retraining best candidate HMM on held out data");
            }
            props.setProperty("initEmissions", "false"); // start where training left off
            trainDocs.addAll(heldOutDocs); // combine training data
            hmm.train(trainDocs, new Corpus(), props, verbose);
            props.setProperty("initEmissions", "true"); // revert to normal
          }

          try {
            //            if (props.getProperty("showHMMGraph") != null) {
            //              HMMGrapher.showHMMGraph(hmm, "Fold " + j + " of " + endFold);
            //            }
            if (props.getProperty("hmmOutfilePrefix") != null) {
              String prefix = props.getProperty("hmmOutfilePrefix");
              if (hmmType.equals("context")) {
                prefix += ".context";
              } else if (hmmType.equals("s-context")) {
                prefix += "." + targetFields[i] + ".context";
              } else if (hmmType.equals("target")) {
                prefix += "." + targetFields[i];
              }

              serializeHMM(hmm, prefix, numFolds == 1 ? 0 : j);
            }
          } catch (Exception e) {
            e.printStackTrace();
          }

          if (runTest) {
            // test current hmm and record stats for later summary
            System.err.println("Testing HMM on " + testDocs.size() + " documents...");
            HMMTester hmmt = new HMMTester(hmm);
            hmmt.test(testDocs, props, bestAnswerOnly, useFirstAnswers, verbose);
            manager.addStats(overallStatsName, hmmt.getAggregateStats());
            for (int t = 0; t < curTargets.length; t++) {
              manager.addStats(curTargets[t], hmmt.getTargetFieldStats(curTargets[t]));
            }
          }
        }
        if (numFolds > 1 && runTest && !useMerged) {
          double averageF1 = manager.getAverageFMeasure(targetFields[i]);
          System.err.println("Average F1 for " + targetFields[i] + " across " + numFolds + " folds: " + averageF1);
          spread.add(new Pair<String, Double>(targetFields[i], new Double(averageF1)));
        }
      }
      if ((useMerged || targetFields.length > 1) && runTest) {
        double grandAverageF1 = manager.getAverageFMeasure(overallStatsName);
        System.err.println("------ Summary for all fields: ------");
        System.err.println("Grand Average F1 across " + targetFields.length + " fields: " + grandAverageF1);
        for (int i = 0; i < targetFields.length; i++) {
          System.err.println("Grand Average F1 for " + targetFields[i] + ": " + manager.getAverageFMeasure(targetFields[i]));
        }

        // "Spreadsheet" output
        System.err.println("Spreadsheet-friendly summary:");
        Pair<String, Double> avePair = new Pair<String, Double>("Average", new Double(grandAverageF1));
        spread.add(avePair);
        for (Iterator<Pair<String, Double>> i = spread.iterator(); i.hasNext();) {
          Pair p = i.next();
          System.err.print(p.first());
          if (i.hasNext()) {
            System.err.print("\t");
          } else {
            System.err.println();
          }
        }
        for (Iterator<Pair<String, Double>> i = spread.iterator(); i.hasNext();) {
          Pair p = i.next();
          System.err.print(p.second());
          if (i.hasNext()) {
            System.err.print("\t");
          } else {
            System.err.println();
          }
        }
      } else if (numFolds == 1 && runTest) {
        double grandAverageF1 = manager.getAverageFMeasure(overallStatsName);
        System.err.println("Average F1 for " + targetFields[0] + ": " + grandAverageF1);
      }
    } else if (props.getProperty("hmmFile") != null) {
      File hmmFile = new File(props.getProperty("hmmFile")); // serialized hmm to load
      if (!hmmFile.canRead()) {
        dieUsage("cannot read hmmFile: " + hmmFile);
      }

      try {
        // reads in the HMM and tests it against the corpus
        System.err.println("Loading HMM from " + hmmFile + "...");
        HMM hmm = (HMM) new ObjectInputStream(new FileInputStream(hmmFile)).readObject();
        if (verbose) {
          hmm.printProbs(); // print out hmm if -v was specified
        }
        Corpus data = new Corpus(dataFilename, targetFields, discardHtml);
        if (data.size() > 0) {
          HMMTester hmmt = new HMMTester(hmm);
          hmmt.test(data, props, bestAnswerOnly, useFirstAnswers, verbose); // runs the HMM on the corpus
          System.err.println("Average F1 across " + targetFields.length + " fields: " + hmmt.getAggregateStats().getFMeasure());
          for (int i = 0; i < targetFields.length; i++) {
            System.err.println("Average F1 for " + targetFields[i] + ": " + hmmt.getTargetFieldStats(targetFields[i]).getFMeasure());
          }
        } else {
          // reads in the raw text from datafile and pulls out the best answers without evaluating
          System.err.println("Extracting best answers from text in " + dataFilename + "...");
          String text = IOUtils.slurpFile(new File(dataFilename));
          Map bestAnswerByType = useFirstAnswers ? hmm.firstAnswers(text) : hmm.bestAnswers(text);
          Iterator iter = bestAnswerByType.keySet().iterator();
          while (iter.hasNext()) {
            Integer type = (Integer) iter.next();
            System.err.println("Best answer for type " + type + ": " + bestAnswerByType.get(type));
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      dieUsage("must specify -train or -load");
    }
  }

  /**
   * Attempts to serialize the given HMM to a file with the given outfile prefix
   * followed by the given fold number followed by .hmm. Returns silently if
   * an error occurs during serialization. If fold is 0 the fold number is
   * omitted from the name entirely.
   */
  public static void serializeHMM(HMM hmm, String outfilePrefix, int fold) {
    try {
      String outfile = outfilePrefix;
      if (fold > 0) {
        outfile += "." + fold;
      }
      outfile += ".hmm";
      System.err.println("Serializing HMM to " + outfile + "...");
      ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(outfile));
      oos.writeObject(hmm);
      oos.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Prints usage info (with the given extra message unless it's null) and exits.
   */
  private static void dieUsage(String extraMessage) {
    System.err.println("Usage: java Extractor [-v] propertiesfile");
    if (extraMessage != null) {
      System.err.println();
      System.err.println(extraMessage);
    }
    System.exit(1);
  }
}
