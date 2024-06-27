//
// StanfordCoreNLP -- a suite of NLP tools
// Copyright (c) 2009-2011 The Board of Trustees of
// The Leland Stanford Junior University. All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 1A
//    Stanford CA 94305-9010
//    USA
//

package edu.stanford.nlp.dcoref;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.classify.LogisticClassifier;
import edu.stanford.nlp.dcoref.CorefChain.CorefMention;
import edu.stanford.nlp.dcoref.Dictionaries.MentionType;
import edu.stanford.nlp.dcoref.ScorerBCubed.BCubedType;
import edu.stanford.nlp.dcoref.sievepasses.DeterministicCorefSieve;
import edu.stanford.nlp.dcoref.sievepasses.ExactStringMatch;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.io.StringOutputStream;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.NewlineLogFormatter;


/**
 * Multi-pass Sieve coreference resolution system (see EMNLP 2010 paper).
 * <p>
 * The main entry point for API is coref(Document document).
 * The output is a map from CorefChain ID to corresponding CorefChain.
 *
 * @author Jenny Finkel
 * @author Mihai Surdeanu
 * @author Karthik Raghunathan
 * @author Heeyoung Lee
 * @author Sudarshan Rangarajan
 */
public class SieveCoreferenceSystem  {

  /** A logger for this class. Still uses j.u.l currently. */
  public static final Logger logger = Logger.getLogger(SieveCoreferenceSystem.class.getName());

  /**
   * If true, we score the output of the given test document
   * Assumes gold annotations are available
   */
  private final boolean doScore;

  /**
   * If true, we do post processing.
   */
  private final boolean doPostProcessing;

  /**
   * maximum sentence distance between two mentions for resolution (-1: no constraint on distance)
   */
  private final int maxSentDist;

  /**
   * automatically set by looking at sieves
   */
  private final boolean useSemantics;

  /**
   * Singleton predictor from Recasens, de Marneffe, and Potts (NAACL 2013)
   */
  private final boolean useSingletonPredictor;

  /** flag for replicating CoNLL result */
  private final boolean replicateCoNLL;

  /** Path for the official CoNLL scorer  */
  public final String conllMentionEvalScript;

  /** flag for optimizing ordering of sieves */
  private final boolean optimizeSieves;
  /** Constraints on sieve order */
  private List<Pair<Integer,Integer>> sievesKeepOrder;

  /** Final score to use for sieve optimization (default is pairwise.Precision) */
  private final String optimizeScoreType;
  /** More useful break down of optimizeScoreType */
  private final boolean optimizeConllScore;
  private final String optimizeMetricType;
  private final CorefScorer.SubScoreType optimizeSubScoreType;

  /**
   * Array of sieve passes to be used in the system
   * Ordered from highest precision to lowest!
   */
  /** Not final because may change when running optimize sieve ordering but otherwise should stay fixed */
  private /*final */DeterministicCorefSieve [] sieves;
  private /*final*/ String [] sieveClassNames;

  /**
   * Dictionaries of all the useful goodies (gender, animacy, number etc. lists)
   */
  private final Dictionaries dictionaries;

  /**
   * Semantic knowledge: WordNet
   */
  private final Semantics semantics;

  private LogisticClassifier<String, String> singletonPredictor;

  // Below are member variables used for scoring (not thread safe)

  /** Current sieve index */
  private int currentSieve;

  /** counter for links in passes ({@code Pair<correct links, total links>})  */
  private List<Pair<Integer, Integer>> linksCountInPass;

  /** Scores for each pass */
  private List<CorefScorer> scorePairwise;
  private List<CorefScorer> scoreBcubed;
  private List<CorefScorer> scoreMUC;

  private List<CorefScorer> scoreSingleDoc;

  /** Additional scoring stats */
  private int additionalCorrectLinksCount;
  private int additionalLinksCount;

  public SieveCoreferenceSystem(Properties props) throws Exception {
    // initialize required fields
    currentSieve = -1;

    //
    // construct the sieve passes
    //
    String sievePasses = props.getProperty(Constants.SIEVES_PROP, Constants.SIEVEPASSES);
    sieveClassNames = sievePasses.trim().split(",\\s*");
    sieves = new DeterministicCorefSieve[sieveClassNames.length];
    for(int i = 0; i < sieveClassNames.length; i ++){
      sieves[i] = (DeterministicCorefSieve) Class.forName("edu.stanford.nlp.dcoref.sievepasses."+sieveClassNames[i]).getConstructor().newInstance();
      sieves[i].init(props);
    }

    //
    // create scoring framework
    //
    doScore = Boolean.parseBoolean(props.getProperty(Constants.SCORE_PROP, "false"));

    //
    // setting post processing
    //
    doPostProcessing = Boolean.parseBoolean(props.getProperty(Constants.POSTPROCESSING_PROP, "false"));

    //
    // setting singleton predictor
    //
    useSingletonPredictor = Boolean.parseBoolean(props.getProperty(Constants.SINGLETON_PROP, "true"));

    //
    // setting maximum sentence distance between two mentions for resolution (-1: no constraint on distance)
    //
    maxSentDist = Integer.parseInt(props.getProperty(Constants.MAXDIST_PROP, "-1"));

    //
    // set useWordNet
    //
    useSemantics = sievePasses.contains("AliasMatch") || sievePasses.contains("LexicalChainMatch");

    // flag for replicating CoNLL result
    replicateCoNLL = Boolean.parseBoolean(props.getProperty(Constants.REPLICATECONLL_PROP, "false"));
    conllMentionEvalScript = props.getProperty(Constants.CONLL_SCORER, Constants.conllMentionEvalScript);

    // flag for optimizing sieve ordering
    optimizeSieves = Boolean.parseBoolean(props.getProperty(Constants.OPTIMIZE_SIEVES_PROP, "false"));
    optimizeScoreType = props.getProperty(Constants.OPTIMIZE_SIEVES_SCORE_PROP, "pairwise.Precision");

    // Break down of the optimize score type
    String[] validMetricTypes = { "muc", "pairwise", "bcub", "ceafe", "ceafm", "combined" };
    String[] parts = optimizeScoreType.split("\\.");
    optimizeConllScore = parts.length > 2 && "conll".equalsIgnoreCase(parts[2]);
    optimizeMetricType = parts[0];
    boolean optimizeMetricTypeOk = false;
    for (String validMetricType : validMetricTypes) {
      if (validMetricType.equalsIgnoreCase(optimizeMetricType)) {
        optimizeMetricTypeOk = true;
        break;
      }
    }
    if (!optimizeMetricTypeOk) {
      throw new IllegalArgumentException("Invalid metric type for " +
              Constants.OPTIMIZE_SIEVES_SCORE_PROP + " property: " + optimizeScoreType);
    }
    optimizeSubScoreType = CorefScorer.SubScoreType.valueOf(parts[1]);

    if (optimizeSieves) {
      String keepSieveOrder = props.getProperty(Constants.OPTIMIZE_SIEVES_KEEP_ORDER_PROP);
      if (keepSieveOrder != null) {
        String[] orderings = keepSieveOrder.split("\\s*,\\s*");
        sievesKeepOrder = new ArrayList<>();
        String firstSieveConstraint = null;
        String lastSieveConstraint = null;
        for (String ordering:orderings) {
          // Convert ordering constraints from string
          Pair<Integer,Integer> p = fromSieveOrderConstraintString(ordering, sieveClassNames);
          // Do initial check of sieves order, can only have one where the first is ANY (< 0), and one where second is ANY (< 0)
          if (p.first() < 0 && p.second() < 0) {
            throw new IllegalArgumentException("Invalid ordering constraint: " + ordering);
          } else if (p.first() < 0) {
            if (lastSieveConstraint != null) {
              throw new IllegalArgumentException("Cannot have these two ordering constraints: " + lastSieveConstraint + "," + ordering);
            }
            lastSieveConstraint = ordering;
          } else if (p.second() < 0) {
            if (firstSieveConstraint != null) {
              throw new IllegalArgumentException("Cannot have these two ordering constraints: " + firstSieveConstraint + "," + ordering);
            }
            firstSieveConstraint = ordering;
          }
          sievesKeepOrder.add(p);
        }
      }
    }

    if(doScore){
      initScorers();
    }

    //
    // load all dictionaries
    //
    dictionaries = new Dictionaries(props);
    semantics = (useSemantics)? new Semantics(dictionaries) : null;

    if(useSingletonPredictor){
      singletonPredictor = getSingletonPredictorFromSerializedFile(props.getProperty(Constants.SINGLETON_MODEL_PROP, DefaultPaths.DEFAULT_DCOREF_SINGLETON_MODEL));
    }
  }

  public void initScorers() {
    linksCountInPass = new ArrayList<>();
    scorePairwise = new ArrayList<>();
    scoreBcubed = new ArrayList<>();
    scoreMUC = new ArrayList<>();
    for (String sieveClassName : sieveClassNames) {
      scorePairwise.add(new ScorerPairwise());
      scoreBcubed.add(new ScorerBCubed(BCubedType.Bconll));
      scoreMUC.add(new ScorerMUC());
      linksCountInPass.add(new Pair<>(0, 0));
    }
  }

  public boolean doScore() { return doScore; }
  public Dictionaries dictionaries() { return dictionaries; }
  public Semantics semantics() { return semantics; }
  public String sieveClassName(int sieveIndex)  {
    return (sieveIndex >= 0 && sieveIndex < sieveClassNames.length)? sieveClassNames[sieveIndex]:null; }

  /**
   * Needs the following properties:
   *  -props 'Location of coref.properties'
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    Properties props = StringUtils.argsToProperties(args);
    initializeAndRunCoref(props);
  }

  /** Returns the name of the log file that this method writes. */
  public static String initializeAndRunCoref(Properties props) throws Exception {
    String timeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-").replaceAll(":", "-");

    //
    // initialize logger
    //
    String logFileName = props.getProperty(Constants.LOG_PROP, "log.txt");
    if (logFileName.endsWith(".txt")) {
      logFileName = logFileName.substring(0, logFileName.length()-4) +"_"+ timeStamp+".txt";
    } else {
      logFileName = logFileName + "_"+ timeStamp+".txt";
    }
    try {
      FileHandler fh = new FileHandler(logFileName, false);
      logger.addHandler(fh);
      logger.setLevel(Level.FINE);
      fh.setFormatter(new NewlineLogFormatter());
    } catch (SecurityException | IOException e) {
      throw new RuntimeException("Cannot initialize logger!", e);
    }

    logger.fine(timeStamp);
    logger.fine(props.toString());
    Constants.printConstants(logger);

    // initialize coref system
    SieveCoreferenceSystem corefSystem = new SieveCoreferenceSystem(props);

    // MentionExtractor extracts MUC, ACE, or CoNLL documents
    MentionExtractor mentionExtractor;
    if (props.containsKey(Constants.MUC_PROP)){
      mentionExtractor = new MUCMentionExtractor(corefSystem.dictionaries, props,
                                                 corefSystem.semantics, corefSystem.singletonPredictor);
    } else if(props.containsKey(Constants.ACE2004_PROP) || props.containsKey(Constants.ACE2005_PROP)) {
      mentionExtractor = new ACEMentionExtractor(corefSystem.dictionaries, props,
                                                 corefSystem.semantics, corefSystem.singletonPredictor);
    } else if (props.containsKey(Constants.CONLL2011_PROP)) {
      mentionExtractor = new CoNLLMentionExtractor(corefSystem.dictionaries, props,
                                                   corefSystem.semantics, corefSystem.singletonPredictor);
    } else {
      throw new RuntimeException("No input file specified!");
    }

    if (!Constants.USE_GOLD_MENTIONS) {
      // Set mention finder
      String mentionFinderClass = props.getProperty(Constants.MENTION_FINDER_PROP);
      if (mentionFinderClass != null) {
        String mentionFinderPropFilename = props.getProperty(Constants.MENTION_FINDER_PROPFILE_PROP);
        CorefMentionFinder mentionFinder;
        if (mentionFinderPropFilename != null) {
          Properties mentionFinderProps = new Properties();
          try (FileInputStream fis = new FileInputStream(mentionFinderPropFilename)) {
            mentionFinderProps.load(fis);
          }
          mentionFinder = (CorefMentionFinder) Class.forName(mentionFinderClass).getConstructor(Properties.class).newInstance(mentionFinderProps);
        } else {
          mentionFinder = (CorefMentionFinder) Class.forName(mentionFinderClass).newInstance();
        }
        mentionExtractor.setMentionFinder(mentionFinder);
      }
      if (mentionExtractor.mentionFinder == null) {
        logger.warning("No mention finder specified, but not using gold mentions");
      }
    }

    if (corefSystem.optimizeSieves && corefSystem.sieves.length > 1) {
      corefSystem.optimizeSieveOrdering(mentionExtractor, props, timeStamp);
    }

    runAndScoreCoref(corefSystem, mentionExtractor, props, timeStamp);
    logger.info("done");
    String endTimeStamp = Calendar.getInstance().getTime().toString().replaceAll("\\s", "-");
    logger.fine(endTimeStamp);

    return logFileName;
  }

  public static double runAndScoreCoref(SieveCoreferenceSystem corefSystem,
                                        MentionExtractor mentionExtractor,
                                        Properties props,
                                        String timeStamp) throws Exception
  {
    // prepare conll output
    PrintWriter writerGold = null;
    PrintWriter writerPredicted = null;
    PrintWriter writerPredictedCoref = null;

    String conllOutputMentionGoldFile = null;
    String conllOutputMentionPredictedFile = null;
    String conllOutputMentionCorefPredictedFile = null;
    String conllMentionEvalFile = null;
    String conllMentionEvalErrFile = null;
    String conllMentionCorefEvalFile = null;
    String conllMentionCorefEvalErrFile = null;

    if(Constants.PRINT_CONLL_OUTPUT || corefSystem.replicateCoNLL) {
      String conllOutput = props.getProperty(Constants.CONLL_OUTPUT_PROP, "conlloutput");
      conllOutputMentionGoldFile = conllOutput + "-"+timeStamp+".gold.txt";
      conllOutputMentionPredictedFile = conllOutput +"-"+timeStamp+ ".predicted.txt";
      conllOutputMentionCorefPredictedFile = conllOutput +"-"+timeStamp+ ".coref.predicted.txt";
      conllMentionEvalFile = conllOutput +"-"+timeStamp+ ".eval.txt";
      conllMentionEvalErrFile = conllOutput +"-"+timeStamp+ ".eval.err.txt";
      conllMentionCorefEvalFile = conllOutput +"-"+timeStamp+ ".coref.eval.txt";
      conllMentionCorefEvalErrFile = conllOutput +"-"+timeStamp+ ".coref.eval.err.txt";
      logger.info("CONLL MENTION GOLD FILE: " + conllOutputMentionGoldFile);
      logger.info("CONLL MENTION PREDICTED FILE: " + conllOutputMentionPredictedFile);
      logger.info("CONLL MENTION EVAL FILE: " + conllMentionEvalFile);
      if (!Constants.SKIP_COREF) {
        logger.info("CONLL MENTION PREDICTED WITH COREF FILE: " + conllOutputMentionCorefPredictedFile);
        logger.info("CONLL MENTION WITH COREF EVAL FILE: " + conllMentionCorefEvalFile);
      }
      writerGold = new PrintWriter(new FileOutputStream(conllOutputMentionGoldFile));
      writerPredicted = new PrintWriter(new FileOutputStream(conllOutputMentionPredictedFile));
      writerPredictedCoref = new PrintWriter(new FileOutputStream(conllOutputMentionCorefPredictedFile));
    }

    mentionExtractor.resetDocs();
    if (corefSystem.doScore()) {
      corefSystem.initScorers();
    }

    //
    // Parse one document at a time, and do single-doc coreference resolution in each.
    //
    // In one iteration, orderedMentionsBySentence contains a list of all
    // mentions in one document. Each mention has properties (annotations):
    // its surface form (Word), NER Tag, POS Tag, Index, etc.
    //

    while(true) {

      Document document = mentionExtractor.nextDoc();
      if(document==null) break;

      if(!props.containsKey(Constants.MUC_PROP)) {
        printRawDoc(document, true);
        printRawDoc(document, false);
      }
      printDiscourseStructure(document);

      if(corefSystem.doScore()){
        document.extractGoldCorefClusters();
      }

      if(Constants.PRINT_CONLL_OUTPUT || corefSystem.replicateCoNLL) {
        // Not doing coref - print conll output here
        printConllOutput(document, writerGold, true);
        printConllOutput(document, writerPredicted, false);
      }

      // run mention detection only
      if(Constants.SKIP_COREF) {
        continue;
      }

      corefSystem.coref(document);  // Do Coreference Resolution

      if(corefSystem.doScore()){
        //Identifying possible coreferring mentions in the corpus along with any recall/precision errors with gold corpus
        corefSystem.printTopK(logger, document, corefSystem.semantics);

        logger.fine("pairwise score for this doc: ");
        corefSystem.scoreSingleDoc.get(corefSystem.sieves.length-1).printF1(logger);
        logger.fine("accumulated score: ");
        corefSystem.printF1(true);
        logger.fine("\n");
      }
      if(Constants.PRINT_CONLL_OUTPUT || corefSystem.replicateCoNLL){
        printConllOutput(document, writerPredictedCoref, false, true);
      }
    }

    double finalScore = 0;
    if(Constants.PRINT_CONLL_OUTPUT || corefSystem.replicateCoNLL) {
      writerGold.close();
      writerPredicted.close();
      writerPredictedCoref.close();

      //if(props.containsKey(Constants.CONLL_SCORER)) {
      if (corefSystem.conllMentionEvalScript != null) {
        //        runConllEval(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionPredictedFile, conllMentionEvalFile, conllMentionEvalErrFile);

        String summary = getConllEvalSummary(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionPredictedFile);
        logger.info("\nCONLL EVAL SUMMARY (Before COREF)");
        printScoreSummary(summary, logger, false);

        if (!Constants.SKIP_COREF) {
          //          runConllEval(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionCorefPredictedFile, conllMentionCorefEvalFile, conllMentionCorefEvalErrFile);
          summary = getConllEvalSummary(corefSystem.conllMentionEvalScript, conllOutputMentionGoldFile, conllOutputMentionCorefPredictedFile);
          logger.info("\nCONLL EVAL SUMMARY (After COREF)");
          printScoreSummary(summary, logger, true);
          printFinalConllScore(summary);
          if (corefSystem.optimizeConllScore) {
            finalScore = getFinalConllScore(summary, corefSystem.optimizeMetricType, corefSystem.optimizeSubScoreType.toString());
          }
        }
      }
    }

    if (!corefSystem.optimizeConllScore && corefSystem.doScore()) {
      finalScore = corefSystem.getFinalScore(corefSystem.optimizeMetricType, corefSystem.optimizeSubScoreType);
    }
    String scoresFile = props.getProperty(Constants.SCORE_FILE_PROP);
    if (scoresFile != null) {
      PrintWriter pw = IOUtils.getPrintWriter(scoresFile);
      pw.println((new DecimalFormat("#.##")).format(finalScore));
      pw.close();
    }

    if (corefSystem.optimizeSieves) {
      logger.info("Final reported score for sieve optimization " + corefSystem.optimizeScoreType + " : " + finalScore);
    }
    return finalScore;
  }

  /** Run and score coref distributed */
  public static void runAndScoreCorefDist(String runDistCmd, Properties props, String propsFile) throws Exception {
    PrintWriter pw = IOUtils.getPrintWriter(propsFile);
    props.store(pw, null);
    pw.close();
    /* Run coref job in a distributed manner, score is written to file */
    List<String> cmd = new ArrayList<>();
    cmd.addAll(Arrays.asList(runDistCmd.split("\\s+")));
    cmd.add("-props");
    cmd.add(propsFile);
    ProcessBuilder pb = new ProcessBuilder(cmd);
    // Copy environment variables over
    Map<String,String> curEnv = System.getenv();
    Map<String,String> pbEnv = pb.environment();
    pbEnv.putAll(curEnv);

    logger.info("Running distributed coref:" + StringUtils.join(pb.command(), " "));
    StringWriter outSos = new StringWriter();
    StringWriter errSos = new StringWriter();
    PrintWriter out = new PrintWriter(new BufferedWriter(outSos));
    PrintWriter err = new PrintWriter(new BufferedWriter(errSos));
    SystemUtils.run(pb, out, err);
    out.close();
    err.close();
    String outStr = outSos.toString();
    String errStr = errSos.toString();
    logger.info("Finished distributed coref: " + runDistCmd + ", props=" + propsFile);
    logger.info("Output: " + outStr);
    if (errStr.length() > 0) {
      logger.info("Error: " + errStr);
    }
  }

  static boolean waitForFiles(File workDir, FileFilter fileFilter, int howMany) throws InterruptedException {
    logger.info("Waiting until we see " + howMany + " " + fileFilter + " files in directory " + workDir + "...");
    int seconds = 0;
    while (true) {
      File[] checkFiles = workDir.listFiles(fileFilter);

      // we found the required number of .check files
      if (checkFiles != null && checkFiles.length >= howMany) {
        logger.info("Found " + checkFiles.length  + " " + fileFilter + " files. Continuing execution.");
        break;
      }

      // sleep for while before the next check
      Thread.sleep(Constants.MONITOR_DIST_CMD_FINISHED_WAIT_MILLIS);
      seconds += Constants.MONITOR_DIST_CMD_FINISHED_WAIT_MILLIS / 1000;
      if (seconds % 600 == 0) {
        double minutes = seconds / 60;
        logger.info("Still waiting... " + minutes + " minutes have passed.");
      }
    }
    return true;
  }

  private static int fromSieveNameToIndex(String sieveName, String[] sieveNames)
  {
    if ("*".equals(sieveName)) return -1;
    for (int i = 0; i < sieveNames.length; i++) {
      if (sieveNames[i].equals(sieveName)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Invalid sieve name: " + sieveName);
  }

  private static Pair<Integer,Integer> fromSieveOrderConstraintString(String s, String[] sieveNames)
  {
    String[] parts = s.split("<");
    if (parts.length == 2) {
      String first = parts[0].trim();
      String second = parts[1].trim();
      int a = fromSieveNameToIndex(first, sieveNames);
      int b = fromSieveNameToIndex(second, sieveNames);
      return new Pair<>(a, b);
    } else {
      throw new IllegalArgumentException("Invalid sieve ordering constraint: " + s);
    }
  }

  private static String toSieveOrderConstraintString(Pair<Integer,Integer> orderedSieveIndices, String[] sieveNames)
  {
    String first = (orderedSieveIndices.first() < 0)? "*":sieveNames[orderedSieveIndices.first()];
    String second = (orderedSieveIndices.second() < 0)? "*":sieveNames[orderedSieveIndices.second()];
    return first + " < " + second;
  }

  /**
   * Given a set of sieves, select an optimal ordering for the sieves
   * by iterating over sieves, and selecting the one that gives the best score and
   *   adding sieves one at a time until no more sieves left
   */
  public void optimizeSieveOrdering(MentionExtractor mentionExtractor, Properties props, String timestamp) throws Exception
  {
    logger.info("=============SIEVE OPTIMIZATION START ====================");
    logger.info("Optimize sieves using score: " + optimizeScoreType);
    FileFilter scoreFilesFilter = new FileFilter() {
      @Override
      public boolean accept(File file) {
        return file.getAbsolutePath().endsWith(".score");
      }
      public String toString() {
        return ".score";
      }
    };
    Pattern scoreFilePattern = Pattern.compile(".*sieves\\.(\\d+)\\.(\\d+).score");
    String runDistributedCmd = props.getProperty(Constants.RUN_DIST_CMD_PROP);
    String mainWorkDirPath = props.getProperty(Constants.RUN_DIST_CMD_WORK_DIR, "workdir") + "-" + timestamp + File.separator;
    DeterministicCorefSieve[] origSieves = sieves;
    String[] origSieveNames = sieveClassNames;
    Set<Integer> remainingSieveIndices = Generics.newHashSet();
    for (int i = 0; i < origSieves.length; i++) {
      remainingSieveIndices.add(i);
    }
    List<Integer> optimizedOrdering = new ArrayList<>();
    while (!remainingSieveIndices.isEmpty()) {
      // initialize array of current sieves
      int curSievesNumber = optimizedOrdering.size();
      sieves = new DeterministicCorefSieve[curSievesNumber+1];
      sieveClassNames = new String[curSievesNumber+1];
      for (int i = 0; i < curSievesNumber; i++) {
        sieves[i] = origSieves[optimizedOrdering.get(i)];
        sieveClassNames[i] = origSieveNames[optimizedOrdering.get(i)];
      }
      logger.info("*** Optimizing Sieve ordering for pass " + curSievesNumber + " ***");
      // Get list of sieves that we can pick from for the next sieve
      Set<Integer> selectableSieveIndices = new TreeSet<>(remainingSieveIndices);
      // Based on ordering constraints remove sieves from options
      if (sievesKeepOrder != null) {
        for (Pair<Integer,Integer> ko:sievesKeepOrder) {
          if (ko.second() < 0) {
            if (remainingSieveIndices.contains(ko.first())) {
              logger.info("Restrict selection to " + origSieveNames[ko.first()] + " because of constraint " +
                      toSieveOrderConstraintString(ko, origSieveNames));
              selectableSieveIndices = Generics.newHashSet(1);
              selectableSieveIndices.add(ko.first());
              break;
            }
          } else if (ko.first() < 0 && remainingSieveIndices.size() > 1) {
            if (remainingSieveIndices.contains(ko.second())) {
              logger.info("Remove selection " + origSieveNames[ko.second()] + " because of constraint " +
                    toSieveOrderConstraintString(ko, origSieveNames));
              selectableSieveIndices.remove(ko.second());
            }
          } else if (remainingSieveIndices.contains(ko.first())) {
            if (remainingSieveIndices.contains(ko.second())) {
              logger.info("Remove selection " + origSieveNames[ko.second()] + " because of constraint " +
                    toSieveOrderConstraintString(ko, origSieveNames));
              selectableSieveIndices.remove(ko.second());
            }
          }
        }
      }
      if (selectableSieveIndices.isEmpty()) {
        throw new RuntimeException("Unable to find sieve ordering to satisfy all ordering constraints!!!!");
      }

      int selected = -1;
      if (selectableSieveIndices.size() > 1) {
        // Go through remaining sieves and see how well they do
        List<Pair<Double,Integer>> scores = new ArrayList<>();
        if (runDistributedCmd != null) {
          String workDirPath = mainWorkDirPath + curSievesNumber + File.separator;
          File workDir = new File(workDirPath);
          workDir.mkdirs();
          workDirPath = workDir.getAbsolutePath() + File.separator;
          // Start jobs
          for (int potentialSieveIndex:selectableSieveIndices) {
            String sieveSelectionId = curSievesNumber + "." + potentialSieveIndex;
            String jobDirPath = workDirPath + sieveSelectionId + File.separator;
            File jobDir = new File(jobDirPath);
            jobDir.mkdirs();
            Properties newProps = new Properties();
            for (String key:props.stringPropertyNames()) {
              String value = props.getProperty(key);
              value = value.replaceAll("\\$\\{JOBDIR\\}",jobDirPath);
              newProps.setProperty(key, value);
            }
            // try this sieve and see how well it works
            sieves[curSievesNumber] = origSieves[potentialSieveIndex];
            sieveClassNames[curSievesNumber] = origSieveNames[potentialSieveIndex];
            newProps.setProperty(Constants.OPTIMIZE_SIEVES_PROP, "false");
            newProps.setProperty(Constants.SCORE_PROP, "true");
            newProps.setProperty(Constants.SIEVES_PROP, StringUtils.join(sieveClassNames,","));
            newProps.setProperty(Constants.LOG_PROP, jobDirPath + "sieves." + sieveSelectionId + ".log");
            newProps.setProperty(Constants.SCORE_FILE_PROP, workDirPath + "sieves." + sieveSelectionId + ".score");
            if (Constants.PRINT_CONLL_OUTPUT || replicateCoNLL) {
              newProps.setProperty(Constants.CONLL_OUTPUT_PROP,  jobDirPath + "sieves." + sieveSelectionId + ".conlloutput");
            }
            String distCmd = newProps.getProperty(Constants.RUN_DIST_CMD_PROP, runDistributedCmd);
            runAndScoreCorefDist(distCmd, newProps, workDirPath + "sieves." + sieveSelectionId + ".props");
          }
          // Wait for jobs to finish and collect scores
          waitForFiles(workDir, scoreFilesFilter,selectableSieveIndices.size());
          // Get scores
          File[] scoreFiles = workDir.listFiles(scoreFilesFilter);
          for (File file:scoreFiles) {
            Matcher m = scoreFilePattern.matcher(file.getName());
            if (m.matches()) {
              int potentialSieveIndex = Integer.parseInt(m.group(2));
              String text = IOUtils.slurpFile(file);
              double score = Double.parseDouble(text);
              // keeps scores so we can select best score and log them
              scores.add(new Pair<>(score, potentialSieveIndex));
            } else {
              throw new RuntimeException("Bad score file name: " + file);
            }
          }
        } else {
          for (int potentialSieveIndex:selectableSieveIndices) {
            // try this sieve and see how well it works
            sieves[curSievesNumber] = origSieves[potentialSieveIndex];
            sieveClassNames[curSievesNumber] = origSieveNames[potentialSieveIndex];
            logger.info("Trying sieve " + curSievesNumber + "="+ sieveClassNames[curSievesNumber] + ": ");
            logger.info(" Trying sieves: " + StringUtils.join(sieveClassNames,","));

            double score = runAndScoreCoref(this, mentionExtractor, props, timestamp);
            // keeps scores so we can select best score and log them
            scores.add(new Pair<>(score, potentialSieveIndex));
            logger.info(" Trying sieves: " + StringUtils.join(sieveClassNames,","));
            logger.info(" Trying sieves score: " + score);
          }
        }
        // Select bestScore
        double bestScore = -1;
        for (Pair<Double,Integer> p:scores) {
          if (selected < 0 || p.first() > bestScore) {
            bestScore = p.first();
            selected = p.second();
          }
        }
        // log ordered scores
        Collections.sort(scores);
        Collections.reverse(scores);
        logger.info("Ordered sieves");
        for (Pair<Double,Integer> p:scores) {
          logger.info("Sieve optimization pass " + curSievesNumber
                  + " scores: Sieve=" + origSieveNames[p.second()] + ", score=" + p.first());
        }
      } else {
        // Only one sieve
        logger.info("Only one choice for next sieve");
        selected = selectableSieveIndices.iterator().next();
      }
      // log sieve we are adding
      sieves[curSievesNumber] = origSieves[selected];
      sieveClassNames[curSievesNumber] = origSieveNames[selected];
      logger.info("Adding sieve " + curSievesNumber + "="+ sieveClassNames[curSievesNumber] + " to existing sieves: ");
      logger.info(" Current Sieves: " + StringUtils.join(sieveClassNames,","));
      // select optimal sieve and add it to our optimized ordering
      optimizedOrdering.add(selected);
      remainingSieveIndices.remove(selected);
    }
    logger.info("Final Sieve Ordering: " + StringUtils.join(sieveClassNames, ","));
    logger.info("=============SIEVE OPTIMIZATION DONE ====================");
  }

  /**
   * Extracts coreference clusters.
   * This is the main API entry point for coreference resolution.
   * Return a map from CorefChain ID to corresponding CorefChain.
   * @throws Exception
   */
  public Map<Integer, CorefChain> coref(Document document) throws Exception {

    // Multi-pass sieve coreference resolution
    for (int i = 0; i < sieves.length ; i++){
      currentSieve = i;
      DeterministicCorefSieve sieve = sieves[i];
      // Do coreference resolution using this pass
      coreference(document, sieve);
    }

    // post processing (e.g., removing singletons, appositions for conll)
    if((!Constants.USE_GOLD_MENTIONS && doPostProcessing) || replicateCoNLL) postProcessing(document);

    // coref system output: CorefChain
    Map<Integer, CorefChain> result = Generics.newHashMap();
    for(CorefCluster c : document.corefClusters.values()) {
      result.put(c.clusterID, new CorefChain(c, document.positions));
    }

    return result;
  }

  public Map<Integer, edu.stanford.nlp.coref.data.CorefChain> corefReturnHybridOutput(Document document) throws Exception {

    // Multi-pass sieve coreference resolution
    for (int i = 0; i < sieves.length ; i++){
      currentSieve = i;
      DeterministicCorefSieve sieve = sieves[i];
      // Do coreference resolution using this pass
      coreference(document, sieve);
    }

    // post processing (e.g., removing singletons, appositions for conll)
    if((!Constants.USE_GOLD_MENTIONS && doPostProcessing) || replicateCoNLL) postProcessing(document);

    // coref system output: edu.stanford.nlp.hcoref.data.CorefChain
    Map<Integer, edu.stanford.nlp.coref.data.CorefChain> result = Generics.newHashMap();

    for(CorefCluster c : document.corefClusters.values()) {
      // build mentionsMap and represents
      Map<IntPair, Set<edu.stanford.nlp.coref.data.CorefChain.CorefMention>> mentionsMap = Generics.newHashMap();
      IntPair keyPair = new IntPair(0,0);
      mentionsMap.put(keyPair, new HashSet<>());
      Mention represents = null;
      edu.stanford.nlp.coref.data.CorefChain.CorefMention representsHybridVersion = null;
      for (Mention mention : c.getCorefMentions()) {
        // convert dcoref CorefMention to hcoref CorefMention
        //IntPair mentionPosition = new IntPair(mention.sentNum, mention.headIndex);
        IntTuple mentionPosition = document.positions.get(mention);
        CorefMention dcorefMention = new CorefMention(mention, mentionPosition);
        // tokens need the hcoref version of CorefClusterIdAnnotation
        mention.headWord.set(edu.stanford.nlp.coref.CorefCoreAnnotations.CorefClusterIdAnnotation.class,
                mention.corefClusterID);
        // drop the dcoref version of CorefClusterIdAnnotation
        mention.headWord.remove(CorefCoreAnnotations.CorefClusterIdAnnotation.class);
        // make the hcoref mention
        edu.stanford.nlp.coref.data.CorefChain.CorefMention hcorefMention =
                new edu.stanford.nlp.coref.data.CorefChain.CorefMention(
                        edu.stanford.nlp.coref.data.Dictionaries.MentionType.valueOf(dcorefMention.mentionType.name()),
                        edu.stanford.nlp.coref.data.Dictionaries.Number.valueOf(dcorefMention.number.name()),
                        edu.stanford.nlp.coref.data.Dictionaries.Gender.valueOf(dcorefMention.gender.name()),
                        edu.stanford.nlp.coref.data.Dictionaries.Animacy.valueOf(dcorefMention.animacy.name()),
                        dcorefMention.startIndex,
                        dcorefMention.endIndex,
                        dcorefMention.headIndex,
                        dcorefMention.corefClusterID,
                        dcorefMention.mentionID,
                        dcorefMention.sentNum,
                        dcorefMention.position,
                        dcorefMention.mentionSpan);
        mentionsMap.get(keyPair).add(hcorefMention);
        if (mention.moreRepresentativeThan(represents)) {
          represents = mention;
          representsHybridVersion = hcorefMention;
        }
      }
      edu.stanford.nlp.coref.data.CorefChain hybridCorefChain =
              new edu.stanford.nlp.coref.data.CorefChain(c.clusterID, mentionsMap, representsHybridVersion);
      result.put(c.clusterID, hybridCorefChain);
    }

    return result;
  }

  /**
   * Do coreference resolution using one sieve pass.
   *
   * @param document An extracted document
   * @throws Exception
   */
  private void coreference(
      Document document,
      DeterministicCorefSieve sieve) throws Exception {

    //Redwood.forceTrack("Coreference: sieve " + sieve.getClass().getSimpleName());
    logger.finer("Coreference: sieve " + sieve.getClass().getSimpleName());
    List<List<Mention>> orderedMentionsBySentence = document.getOrderedMentions();
    Map<Integer, CorefCluster> corefClusters = document.corefClusters;
    Set<Mention> roleSet = document.roleSet;

    logger.finest("ROLE SET (Skip exact string match): ------------------");
    for(Mention m : roleSet){
      logger.finest("\t"+m.spanToString());
    }
    logger.finest("-------------------------------------------------------");

    additionalCorrectLinksCount = 0;
    additionalLinksCount = 0;

    for (int sentI = 0; sentI < orderedMentionsBySentence.size(); sentI++) {
      List<Mention> orderedMentions = orderedMentionsBySentence.get(sentI);

      for (int mentionI = 0; mentionI < orderedMentions.size(); mentionI++) {

        Mention m1 = orderedMentions.get(mentionI);

        // check for skip: first mention only, discourse salience
        if(sieve.skipThisMention(document, m1, corefClusters.get(m1.corefClusterID), dictionaries)) {
          continue;
        }

        LOOP:
          for (int sentJ = sentI; sentJ >= 0; sentJ--) {
            List<Mention> l = sieve.getOrderedAntecedents(sentJ, sentI, orderedMentions, orderedMentionsBySentence, m1, mentionI, corefClusters, dictionaries);
            if(maxSentDist != -1 && sentI - sentJ > maxSentDist) continue;

            // Sort mentions by length whenever we have two mentions beginning at the same position and having the same head
            for(int i = 0; i < l.size(); i++) {
              for(int j = 0; j < l.size(); j++) {
                if(l.get(i).headString.equals(l.get(j).headString) &&
                    l.get(i).startIndex == l.get(j).startIndex &&
                    l.get(i).sameSentence(l.get(j)) && j > i &&
                    l.get(i).spanToString().length() > l.get(j).spanToString().length()) {
                  logger.finest("FLIPPED: "+l.get(i).spanToString()+"("+i+"), "+l.get(j).spanToString()+"("+j+")");
                  l.set(j, l.set(i, l.get(j)));
                }
              }
            }

            for (Mention m2 : l) {
              // m2 - antecedent of m1                   l

              // Skip singletons according to the singleton predictor
              // (only for non-NE mentions)
              // Recasens, de Marneffe, and Potts (NAACL 2013)
              if (m1.isSingleton && m1.mentionType != MentionType.PROPER && m2.isSingleton && m2.mentionType != MentionType.PROPER) continue;
              if (m1.corefClusterID == m2.corefClusterID) continue;
              CorefCluster c1 = corefClusters.get(m1.corefClusterID);
              CorefCluster c2 = corefClusters.get(m2.corefClusterID);
              if (c2 == null) {
                logger.warning("NO corefcluster id " + m2.corefClusterID);
              }
              assert(c1 != null);
              assert(c2 != null);

              if (sieve.useRoleSkip()) {
                if (m1.isRoleAppositive(m2, dictionaries)) {
                  roleSet.add(m1);
                } else if (m2.isRoleAppositive(m1, dictionaries)) {
                  roleSet.add(m2);
                }
                continue;
              }

              if (sieve.coreferent(document, c1, c2, m1, m2, dictionaries, roleSet, semantics)) {

                // print logs for analysis
                if (doScore()) {
                  printLogs(c1, c2, m1, m2, document, currentSieve);
                }

                int removeID = c1.clusterID;
                CorefCluster.mergeClusters(c2, c1);
                document.mergeIncompatibles(c2, c1);
                document.mergeAcronymCache(c2, c1);
//                logger.warning("Removing cluster " + removeID + ", merged with " + c2.getClusterID());
                corefClusters.remove(removeID);
                break LOOP;
              }
            }
          } // End of "LOOP"
      }
    }

    // scoring
    if(doScore()){
      scoreMUC.get(currentSieve).calculateScore(document);
      scoreBcubed.get(currentSieve).calculateScore(document);
      scorePairwise.get(currentSieve).calculateScore(document);
      if(currentSieve==0) {
        scoreSingleDoc = new ArrayList<>();
        scoreSingleDoc.add(new ScorerPairwise());
        scoreSingleDoc.get(currentSieve).calculateScore(document);
        additionalCorrectLinksCount = (int) scoreSingleDoc.get(currentSieve).precisionNumSum;
        additionalLinksCount = (int) scoreSingleDoc.get(currentSieve).precisionDenSum;
      } else {
        scoreSingleDoc.add(new ScorerPairwise());
        scoreSingleDoc.get(currentSieve).calculateScore(document);
        additionalCorrectLinksCount = (int) (scoreSingleDoc.get(currentSieve).precisionNumSum - scoreSingleDoc.get(currentSieve-1).precisionNumSum);
        additionalLinksCount = (int) (scoreSingleDoc.get(currentSieve).precisionDenSum - scoreSingleDoc.get(currentSieve-1).precisionDenSum);
      }
      linksCountInPass.get(currentSieve).setFirst(linksCountInPass.get(currentSieve).first() + additionalCorrectLinksCount);
      linksCountInPass.get(currentSieve).setSecond(linksCountInPass.get(currentSieve).second() + additionalLinksCount);

      printSieveScore(document, sieve);
    }
    //Redwood.endTrack("Coreference: sieve " + sieve.getClass().getSimpleName());
  }

  /** Remove singletons, appositive, predicate nominatives, relative pronouns */
  private static void postProcessing(Document document) {
    Set<Mention> removeSet = Generics.newHashSet();
    Set<Integer> removeClusterSet = Generics.newHashSet();

    for(CorefCluster c : document.corefClusters.values()){
      Set<Mention> removeMentions = Generics.newHashSet();
      for(Mention m : c.getCorefMentions()) {
        if(Constants.REMOVE_APPOSITION_PREDICATENOMINATIVES
            && ((m.appositions!=null && m.appositions.size() > 0)
                || (m.predicateNominatives!=null && m.predicateNominatives.size() > 0)
                || (m.relativePronouns!=null && m.relativePronouns.size() > 0))){
          removeMentions.add(m);
          removeSet.add(m);
          m.corefClusterID = m.mentionID;
        }
      }
      c.corefMentions.removeAll(removeMentions);
      if(Constants.REMOVE_SINGLETONS && c.getCorefMentions().size()==1) {
        removeClusterSet.add(c.clusterID);
      }
    }
    for (int removeId : removeClusterSet){
      document.corefClusters.remove(removeId);
    }
    for(Mention m : removeSet){
      document.positions.remove(m);
    }
  }

  public static LogisticClassifier<String, String> getSingletonPredictorFromSerializedFile(String serializedFile) {
    try {
      ObjectInputStream ois = IOUtils.readStreamFromString(serializedFile);
      Object o = ois.readObject();
      if (o instanceof LogisticClassifier<?, ?>) {
        return (LogisticClassifier<String, String>) o;
      }
      throw new ClassCastException("Wanted SingletonPredictor, got " + o.getClass());
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  /** Remove singleton clusters */
  public static List<List<Mention>> filterMentionsWithSingletonClusters(Document document, List<List<Mention>> mentions)
  {

    List<List<Mention>> res = new ArrayList<>(mentions.size());
    for (List<Mention> ml:mentions) {
      List<Mention> filtered = new ArrayList<>();
      for (Mention m:ml) {
        CorefCluster cluster = document.corefClusters.get(m.corefClusterID);
        if (cluster != null && cluster.getCorefMentions().size() > 1) {
          filtered.add(m);
        }
      }
      res.add(filtered);
    }
    return res;
  }
  public static void runConllEval(String conllMentionEvalScript,
      String goldFile, String predictFile, String evalFile, String errFile) throws IOException {
    ProcessBuilder process = new ProcessBuilder(conllMentionEvalScript, "all", goldFile, predictFile);
    PrintWriter out = new PrintWriter(new FileOutputStream(evalFile));
    PrintWriter err = new PrintWriter(new FileOutputStream(errFile));
    SystemUtils.run(process, out, err);
    out.close();
    err.close();
  }

  public static String getConllEvalSummary(String conllMentionEvalScript,
      String goldFile, String predictFile) throws IOException {
    ProcessBuilder process = new ProcessBuilder(conllMentionEvalScript, "all", goldFile, predictFile, "none");
    StringOutputStream errSos = new StringOutputStream();
    StringOutputStream outSos = new StringOutputStream();
    PrintWriter out = new PrintWriter(outSos);
    PrintWriter err = new PrintWriter(errSos);
    SystemUtils.run(process, out, err);
    out.close();
    err.close();
    String summary = outSos.toString();
    String errStr = errSos.toString();
    if ( ! errStr.isEmpty()) {
      summary += "\nERROR: " + errStr;
    }
    Pattern pattern = Pattern.compile("\\d+\\.\\d\\d\\d+");
    DecimalFormat df = new DecimalFormat("#.##");
    Matcher matcher = pattern.matcher(summary);
    while(matcher.find()) {
      String number = matcher.group();
      summary = summary.replaceFirst(number, df.format(Double.parseDouble(number)));
    }
    return summary;
  }

  /** Print logs for error analysis */
  public void printTopK(Logger logger, Document document, Semantics semantics) {

    List<List<Mention>> orderedMentionsBySentence = document.getOrderedMentions();
    Map<Integer, CorefCluster> corefClusters = document.corefClusters;
    Map<Mention, IntTuple> positions = document.allPositions;
    Map<Integer, Mention> golds = document.allGoldMentions;

    logger.fine("=======ERROR ANALYSIS=========================================================");

    // Temporary sieve for getting ordered antecedents
    DeterministicCorefSieve tmpSieve = new ExactStringMatch();
    for (int i = 0 ; i < orderedMentionsBySentence.size(); i++) {
      List<Mention> orderedMentions = orderedMentionsBySentence.get(i);
      for (int j = 0 ; j < orderedMentions.size(); j++) {
        Mention m = orderedMentions.get(j);
        logger.fine("=========Line: "+i+"\tmention: "+j+"=======================================================");
        logger.fine(m.spanToString()+"\tmentionID: "+m.mentionID+"\tcorefClusterID: "+m.corefClusterID+"\tgoldCorefClusterID: "+m.goldCorefClusterID);
        CorefCluster corefCluster = corefClusters.get(m.corefClusterID);
        if (corefCluster != null) {
          corefCluster.printCorefCluster(logger);
        } else {
          logger.finer("CANNOT find coref cluster for cluster " + m.corefClusterID);
        }
        logger.fine("-------------------------------------------------------");

        boolean oneRecallErrorPrinted = false;
        boolean onePrecisionErrorPrinted = false;
        boolean alreadyChoose = false;

        for (int sentJ = i; sentJ >= 0; sentJ--) {
          List<Mention> l = tmpSieve.getOrderedAntecedents(sentJ, i, orderedMentions, orderedMentionsBySentence, m, j, corefClusters, dictionaries);

          // Sort mentions by length whenever we have two mentions beginning at the same position and having the same head
          for(int ii = 0; ii < l.size(); ii++) {
            for(int jj = 0; jj < l.size(); jj++) {
              if(l.get(ii).headString.equals(l.get(jj).headString) &&
                  l.get(ii).startIndex == l.get(jj).startIndex &&
                  l.get(ii).sameSentence(l.get(jj)) && jj > ii &&
                  l.get(ii).spanToString().length() > l.get(jj).spanToString().length()) {
                logger.finest("FLIPPED: "+l.get(ii).spanToString()+"("+ii+"), "+l.get(jj).spanToString()+"("+jj+")");
                l.set(jj, l.set(ii, l.get(jj)));
              }
            }
          }

          logger.finest("Candidates in sentence #"+sentJ+" for mention: "+m.spanToString());
          for(int ii = 0; ii < l.size(); ii ++){
            logger.finest("\tCandidate #"+ii+": "+l.get(ii).spanToString());
          }

          for (Mention antecedent : l) {
            boolean chosen = (m.corefClusterID == antecedent.corefClusterID);
            IntTuple src = new IntTuple(2);
            src.set(0,i);
            src.set(1,j);

            IntTuple ant = positions.get(antecedent);
            if(ant==null) continue;
            //correct=(chosen==goldLinks.contains(new Pair<IntTuple, IntTuple>(src,ant)));
            boolean coreferent = golds.containsKey(m.mentionID)
            && golds.containsKey(antecedent.mentionID)
            && (golds.get(m.mentionID).goldCorefClusterID == golds.get(antecedent.mentionID).goldCorefClusterID);
            boolean correct = (chosen == coreferent);

            String chosenness = chosen ? "Chosen" : "Not Chosen";
            String correctness = correct ? "Correct" : "Incorrect";
            logger.fine("\t" + correctness +"\t\t" + chosenness + "\t"+antecedent.spanToString());
            CorefCluster mC = corefClusters.get(m.corefClusterID);
            CorefCluster aC = corefClusters.get(antecedent.corefClusterID);

            if(chosen && !correct && !onePrecisionErrorPrinted && !alreadyChoose)  {
              onePrecisionErrorPrinted = true;
              printLinkWithContext(logger, "\nPRECISION ERROR ", src, ant, document, semantics);
              logger.fine("END of PRECISION ERROR LOG");
            }

            if(!chosen && !correct && !oneRecallErrorPrinted && (!alreadyChoose || (alreadyChoose && onePrecisionErrorPrinted))) {
              oneRecallErrorPrinted = true;
              printLinkWithContext(logger, "\nRECALL ERROR ", src, ant, document, semantics);

              logger.finer("cluster info: ");
              if (mC != null) {
                mC.printCorefCluster(logger);
              } else {
                logger.finer("CANNOT find coref cluster for cluster " + m.corefClusterID);
              }
              logger.finer("----------------------------------------------------------");
              if (aC != null) {
                aC.printCorefCluster(logger);
              } else {
                logger.finer("CANNOT find coref cluster for cluster " + m.corefClusterID);
              }
              logger.finer("");
              logger.fine("END of RECALL ERROR LOG");
            }
            if(chosen) alreadyChoose = true;
          }
        }
        logger.fine("\n");
      }
    }
    logger.fine("===============================================================================");
  }

  public void printF1(boolean printF1First) {
    scoreMUC.get(sieveClassNames.length - 1).printF1(logger, printF1First);
    scoreBcubed.get(sieveClassNames.length - 1).printF1(logger, printF1First);
    scorePairwise.get(sieveClassNames.length - 1).printF1(logger, printF1First);
  }

  private void printSieveScore(Document document, DeterministicCorefSieve sieve) {
    logger.fine("===========================================");
    logger.fine("pass"+currentSieve+": "+ sieve.flagsToString());
    scoreMUC.get(currentSieve).printF1(logger);
    scoreBcubed.get(currentSieve).printF1(logger);
    scorePairwise.get(currentSieve).printF1(logger);
    logger.fine("# of Clusters: "+document.corefClusters.size() + ",\t# of additional links: "+additionalLinksCount
        +",\t# of additional correct links: "+additionalCorrectLinksCount
        +",\tprecision of new links: "+1.0*additionalCorrectLinksCount/additionalLinksCount);
    logger.fine("# of total additional links: "+linksCountInPass.get(currentSieve).second()
        +",\t# of total additional correct links: "+linksCountInPass.get(currentSieve).first()
        +",\taccumulated precision of this pass: "+1.0*linksCountInPass.get(currentSieve).first()/linksCountInPass.get(currentSieve).second());
    logger.fine("--------------------------------------");
  }
  /** Print coref link info */
  private static void printLink(Logger logger, String header, IntTuple src, IntTuple dst, List<List<Mention>> orderedMentionsBySentence) {
    Mention srcMention = orderedMentionsBySentence.get(src.get(0)).get(src.get(1));
    Mention dstMention = orderedMentionsBySentence.get(dst.get(0)).get(dst.get(1));
    if(src.get(0)==dst.get(0)) {
      logger.fine(header + ": ["+srcMention.spanToString()+"](id="+srcMention.mentionID
          +") in sent #"+src.get(0)+" => ["+dstMention.spanToString()+"](id="+dstMention.mentionID+") in sent #"+dst.get(0) + " Same Sentence");
    } else {
      logger.fine(header + ": ["+srcMention.spanToString()+"](id="+srcMention.mentionID
          +") in sent #"+src.get(0)+" => ["+dstMention.spanToString()+"](id="+dstMention.mentionID+") in sent #"+dst.get(0));
    }
  }

  protected static void printList(Logger logger, String... args)  {
    StringBuilder sb = new StringBuilder();
    for (String arg : args) {
      sb.append(arg);
      sb.append('\t');
    }
    logger.fine(sb.toString());
  }

  /** print a coref link information including context and parse tree */
  private static void printLinkWithContext(Logger logger,
      String header,
      IntTuple src,
      IntTuple dst,
      Document document,
      Semantics semantics
  ) {
    List<List<Mention>> orderedMentionsBySentence = document.getOrderedMentions();
    List<List<Mention>> goldOrderedMentionsBySentence = document.goldOrderedMentionsBySentence;

    Mention srcMention = orderedMentionsBySentence.get(src.get(0)).get(src.get(1));
    Mention dstMention = orderedMentionsBySentence.get(dst.get(0)).get(dst.get(1));
    List<CoreLabel> srcSentence = srcMention.sentenceWords;
    List<CoreLabel> dstSentence = dstMention.sentenceWords;

    printLink(logger, header, src, dst, orderedMentionsBySentence);

    printList(logger, "Mention:" + srcMention.spanToString(),
        "Gender:" + srcMention.gender.toString(),
        "Number:" + srcMention.number.toString(),
        "Animacy:" + srcMention.animacy.toString(),
        "Person:" + srcMention.person.toString(),
        "NER:" + srcMention.nerString,
        "Head:" + srcMention.headString,
        "Type:" + srcMention.mentionType.toString(),
        "utter: "+srcMention.headWord.get(CoreAnnotations.UtteranceAnnotation.class),
        "speakerID: "+srcMention.headWord.get(CoreAnnotations.SpeakerAnnotation.class),
        "twinless:" + srcMention.twinless);
    logger.fine("Context:");

    String p = "";
    for(int i = 0; i < srcSentence.size(); i++) {
      if (i == srcMention.startIndex) {
        p += "[";
      }
      if (i == srcMention.endIndex) {
        p += "]";
      }
      p += srcSentence.get(i).word() + " ";
    }
    logger.fine(p);

    StringBuilder golds = new StringBuilder();
    golds.append("Gold mentions in the sentence:\n");
    Counter<Integer> mBegin = new ClassicCounter<>();
    Counter<Integer> mEnd = new ClassicCounter<>();

    for(Mention m : goldOrderedMentionsBySentence.get(src.get(0))){
      mBegin.incrementCount(m.startIndex);
      mEnd.incrementCount(m.endIndex);
    }
    List<CoreLabel> l = document.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(src.get(0)).get(CoreAnnotations.TokensAnnotation.class);
    for(int i = 0 ; i < l.size() ; i++){
      for(int j = 0; j < mEnd.getCount(i); j++){
        golds.append("]");
      }
      for(int j = 0; j < mBegin.getCount(i); j++){
        golds.append("[");
      }
      golds.append(l.get(i).get(CoreAnnotations.TextAnnotation.class));
      golds.append(" ");
    }
    logger.fine(golds.toString());

    printList(logger, "\nAntecedent:" + dstMention.spanToString(),
        "Gender:" + dstMention.gender.toString(),
        "Number:" + dstMention.number.toString(),
        "Animacy:" + dstMention.animacy.toString(),
        "Person:" + dstMention.person.toString(),
        "NER:" + dstMention.nerString,
        "Head:" + dstMention.headString,
        "Type:" + dstMention.mentionType.toString(),
        "utter: "+dstMention.headWord.get(CoreAnnotations.UtteranceAnnotation.class),
        "speakerID: "+dstMention.headWord.get(CoreAnnotations.SpeakerAnnotation.class),
        "twinless:" + dstMention.twinless);
    logger.fine("Context:");

    p = "";
    for(int i = 0; i < dstSentence.size(); i++) {
      if (i == dstMention.startIndex) {
        p += "[";
      }
      if (i == dstMention.endIndex) {
        p += "]";
      }
      p += dstSentence.get(i).word() + " ";
    }
    logger.fine(p);

    golds = new StringBuilder();
    golds.append("Gold mentions in the sentence:\n");
    mBegin = new ClassicCounter<>();
    mEnd = new ClassicCounter<>();

    for(Mention m : goldOrderedMentionsBySentence.get(dst.get(0))){
      mBegin.incrementCount(m.startIndex);
      mEnd.incrementCount(m.endIndex);
    }
    l = document.annotation.get(CoreAnnotations.SentencesAnnotation.class).get(dst.get(0)).get(CoreAnnotations.TokensAnnotation.class);
    for(int i = 0 ; i < l.size() ; i++){
      for(int j = 0; j < mEnd.getCount(i); j++){
        golds.append("]");
      }
      for(int j = 0; j < mBegin.getCount(i); j++){
        golds.append("[");
      }
      golds.append(l.get(i).get(CoreAnnotations.TextAnnotation.class));
      golds.append(" ");
    }
    logger.fine(golds.toString());

    logger.finer("\nMention:: --------------------------------------------------------");
    try {
      logger.finer(srcMention.dependency.toString());
    } catch (Exception e){} //throw new RuntimeException(e);}
    logger.finer("Parse:");
    logger.finer(formatPennTree(srcMention.contextParseTree));
    logger.finer("\nAntecedent:: -----------------------------------------------------");
    try {
      logger.finer(dstMention.dependency.toString());
    } catch (Exception e){} //throw new RuntimeException(e);}
    logger.finer("Parse:");
    logger.finer(formatPennTree(dstMention.contextParseTree));
  }
  /** For printing tree in a better format */
  private static String formatPennTree(Tree parseTree)	{
    String treeString = parseTree.pennString();
    treeString = treeString.replaceAll("\\[TextAnnotation=", "");
    treeString = treeString.replaceAll("(NamedEntityTag|Value|Index|PartOfSpeech)Annotation.+?\\)", ")");
    treeString = treeString.replaceAll("\\[.+?\\]", "");
    return treeString;
  }

  /** Print pass results */
  private static void printLogs(CorefCluster c1, CorefCluster c2, Mention m1,
      Mention m2, Document document, int sieveIndex) {
    Map<Mention, IntTuple> positions = document.positions;
    List<List<Mention>> orderedMentionsBySentence = document.getOrderedMentions();
    List<Pair<IntTuple, IntTuple>> goldLinks = document.getGoldLinks();

    IntTuple p1 = positions.get(m1);
    assert(p1 != null);
    IntTuple p2 = positions.get(m2);
    assert(p2 != null);

    int menDist = 0;
    for (int i = p2.get(0) ; i<= p1.get(0) ; i++){
      if(p1.get(0)==p2.get(0)) {
        menDist = p1.get(1)-p2.get(1);
        break;
      }
      if(i==p2.get(0)) {
        menDist += orderedMentionsBySentence.get(p2.get(0)).size()-p2.get(1);
        continue;
      }
      if(i==p1.get(0)) {
        menDist += p1.get(1);
        continue;
      }
      if(p2.get(0)<i && i < p1.get(0)) menDist += orderedMentionsBySentence.get(i).size();
    }
    String correct = (goldLinks.contains(new Pair<>(p1, p2)))? "\tCorrect" : "\tIncorrect";
    logger.finest("\nsentence distance: "+(p1.get(0)-p2.get(0))+"\tmention distance: "+menDist + correct);

    if(!goldLinks.contains(new Pair<>(p1, p2))){
      logger.finer("-------Incorrect merge in pass"+sieveIndex+"::--------------------");
      c1.printCorefCluster(logger);
      logger.finer("--------------------------------------------");
      c2.printCorefCluster(logger);
      logger.finer("--------------------------------------------");
    }
    logger.finer("antecedent: "+m2.spanToString()+"("+m2.mentionID+")\tmention: "+m1.spanToString()+"("+m1.mentionID+")\tsentDistance: "+Math.abs(m1.sentNum-m2.sentNum)+"\t"+correct+" Pass"+sieveIndex+":");
  }

  private static void printDiscourseStructure(Document document) {
    logger.finer("DISCOURSE STRUCTURE==============================");
    logger.finer("doc type: "+document.docType);
    int previousUtterIndex = -1;
    String previousSpeaker = "";
    StringBuilder sb = new StringBuilder();
    for(CoreMap s : document.annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      for(CoreLabel l : s.get(CoreAnnotations.TokensAnnotation.class)) {
        int utterIndex = l.get(CoreAnnotations.UtteranceAnnotation.class);
        String speaker = l.get(CoreAnnotations.SpeakerAnnotation.class);
        String word = l.get(CoreAnnotations.TextAnnotation.class);
        if(previousUtterIndex!=utterIndex) {
          try {
            int previousSpeakerID = Integer.parseInt(previousSpeaker);
            logger.finer("\n<utter>: "+previousUtterIndex + " <speaker>: "+document.allPredictedMentions.get(previousSpeakerID).spanToString());
          } catch (Exception e) {
            logger.finer("\n<utter>: "+previousUtterIndex + " <speaker>: "+previousSpeaker);
          }

          logger.finer(sb.toString());
          sb.setLength(0);
          previousUtterIndex = utterIndex;
          previousSpeaker = speaker;
        }
        sb.append(" ").append(word);
      }
      sb.append("\n");
    }
    try {
      int previousSpeakerID = Integer.parseInt(previousSpeaker);
      logger.finer("\n<utter>: "+previousUtterIndex + " <speaker>: "+document.allPredictedMentions.get(previousSpeakerID).spanToString());
    } catch (Exception e) {
      logger.finer("\n<utter>: "+previousUtterIndex + " <speaker>: "+previousSpeaker);
    }
    logger.finer(sb.toString());
    logger.finer("END OF DISCOURSE STRUCTURE==============================");
  }

  private static void printScoreSummary(String summary, Logger logger, boolean afterPostProcessing) {
    String[] lines = summary.split("\n");
    if(!afterPostProcessing) {
      for(String line : lines) {
        if(line.startsWith("Identification of Mentions")) {
          logger.info(line);
          return;
        }
      }
    } else {
      StringBuilder sb = new StringBuilder();
      for(String line : lines) {
        if(line.startsWith("METRIC")) sb.append(line);
        if(!line.startsWith("Identification of Mentions") && line.contains("Recall")) {
          sb.append(line).append("\n");
        }
      }
      logger.info(sb.toString());
    }
  }
  /** Print average F1 of MUC, B^3, CEAF_E */
  private static void printFinalConllScore(String summary) {
    Pattern f1 = Pattern.compile("Coreference:.*F1: (.*)%");
    Matcher f1Matcher = f1.matcher(summary);
    double[] F1s = new double[5];
    int i = 0;
    while (f1Matcher.find()) {
      F1s[i++] = Double.parseDouble(f1Matcher.group(1));
    }
    double finalScore = (F1s[0]+F1s[1]+F1s[3])/3;
    logger.info("Final conll score ((muc+bcub+ceafe)/3) = " + (new DecimalFormat("#.##")).format(finalScore));
  }

  private static double getFinalConllScore(String summary, String metricType, String scoreType) {
    // metricType can be muc, bcub, ceafm, ceafe or combined
    // Expects to match metricType muc, bcub, ceafm, ceafe
    // Will not match the BLANC metrics (coref links, noncoref links, overall)
    Pattern pattern = Pattern.compile("METRIC\\s+(.*):Coreference:.*" + scoreType + ":\\s*(\\([ 0-9./]*\\))?\\s*(\\d+(\\.\\d+)?)%");
    Matcher matcher = pattern.matcher(summary);
    double[] scores = new double[5];
    String[] names = new String[5];
    int i = 0;
    while (matcher.find()) {
      names[i] = matcher.group(1);
      scores[i] = Double.parseDouble(matcher.group(3));
      i++;
    }
    metricType = metricType.toLowerCase();
    if ("combined".equals(metricType)) {
      double finalScore = (scores[0]+scores[1]+scores[3])/3;
      logger.info("Final conll score ((muc+bcub+ceafe)/3) " + scoreType + " = " + finalScore);
      return finalScore;
    } else {
      if ("bcubed".equals(metricType)) {
        metricType = "bcub";
      }
      for (i = 0; i < names.length; i++) {
        if (names[i] != null && names[i].equals(metricType)) {
          double finalScore = scores[i];
          logger.info("Final conll score (" + metricType + ") " + scoreType + " = " + finalScore);
          return finalScore;
        }
      }
      throw new IllegalArgumentException("Invalid metricType:" + metricType);
    }
  }

  /** Returns final selected score */
  private double getFinalScore(String metricType, CorefScorer.SubScoreType subScoreType) {
    metricType = metricType.toLowerCase();
    int passIndex = sieveClassNames.length - 1;
    String scoreDesc = metricType;
    double finalScore;
    switch (metricType) {
      case "combined":
        finalScore = (scoreMUC.get(passIndex).getScore(subScoreType)
            + scoreBcubed.get(passIndex).getScore(subScoreType)
            + scorePairwise.get(passIndex).getScore(subScoreType)) / 3;
        scoreDesc = "(muc + bcub + pairwise)/3";
        break;
      case "muc":
        finalScore = scoreMUC.get(passIndex).getScore(subScoreType);
        break;
      case "bcub":
      case "bcubed":
        finalScore = scoreBcubed.get(passIndex).getScore(subScoreType);
        break;
      case "pairwise":
        finalScore = scorePairwise.get(passIndex).getScore(subScoreType);
        break;
      default:
        throw new IllegalArgumentException("Invalid sub score type:" + subScoreType);
    }
    logger.info("Final score (" + scoreDesc + ") " + subScoreType + " = " + (new DecimalFormat("#.##")).format(finalScore));
    return finalScore;
  }

  public static void printConllOutput(Document document, PrintWriter writer, boolean gold) {
    printConllOutput(document, writer, gold, false);
  }

  public static void printConllOutput(Document document, PrintWriter writer, boolean gold, boolean filterSingletons) {
    List<List<Mention>> orderedMentions;
    if (gold) {
      orderedMentions = document.goldOrderedMentionsBySentence;
    } else {
      orderedMentions = document.predictedOrderedMentionsBySentence;
    }
    if (filterSingletons) {
      orderedMentions = filterMentionsWithSingletonClusters(document, orderedMentions);
    }
    printConllOutput(document, writer, orderedMentions, gold);
  }

  private static void printConllOutput(Document document, PrintWriter writer, List<List<Mention>> orderedMentions, boolean gold) {
    Annotation anno = document.annotation;
    List<List<String[]>> conllDocSentences = document.conllDoc.sentenceWordLists;
    String docID = anno.get(CoreAnnotations.DocIDAnnotation.class);
    StringBuilder sb = new StringBuilder();
    sb.append("#begin document ").append(docID).append("\n");
    List<CoreMap> sentences = anno.get(CoreAnnotations.SentencesAnnotation.class);
    for(int sentNum = 0 ; sentNum < sentences.size() ; sentNum++){
      List<CoreLabel> sentence = sentences.get(sentNum).get(CoreAnnotations.TokensAnnotation.class);
      List<String[]> conllSentence = conllDocSentences.get(sentNum);
      Map<Integer,Set<Mention>> mentionBeginOnly = Generics.newHashMap();
      Map<Integer,Set<Mention>> mentionEndOnly = Generics.newHashMap();
      Map<Integer,Set<Mention>> mentionBeginEnd = Generics.newHashMap();

      for(int i=0 ; i<sentence.size(); i++){
        mentionBeginOnly.put(i, new LinkedHashSet<>());
        mentionEndOnly.put(i, new LinkedHashSet<>());
        mentionBeginEnd.put(i, new LinkedHashSet<>());
      }

      for(Mention m : orderedMentions.get(sentNum)) {
        if(m.startIndex==m.endIndex-1) {
          mentionBeginEnd.get(m.startIndex).add(m);
        } else {
          mentionBeginOnly.get(m.startIndex).add(m);
          mentionEndOnly.get(m.endIndex-1).add(m);
        }
      }

      for(int i=0 ; i<sentence.size(); i++){
        StringBuilder sb2 = new StringBuilder();
        for(Mention m : mentionBeginOnly.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append("(").append(corefClusterId);
        }
        for(Mention m : mentionBeginEnd.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append("(").append(corefClusterId).append(")");
        }
        for(Mention m : mentionEndOnly.get(i)){
          if (sb2.length() > 0) {
            sb2.append("|");
          }
          int corefClusterId = (gold)? m.goldCorefClusterID:m.corefClusterID;
          sb2.append(corefClusterId).append(")");
        }
        if(sb2.length() == 0) sb2.append("-");

        String[] columns = conllSentence.get(i);
        for(int j = 0 ; j < columns.length-1 ; j++){
          String column = columns[j];
          sb.append(column).append("\t");
        }
        sb.append(sb2).append("\n");
      }
      sb.append("\n");
    }

    sb.append("#end document").append("\n");
    //    sb.append("#end document ").append(docID).append("\n");

    writer.print(sb.toString());
    writer.flush();
  }

  /** Print raw document for analysis */
  private static void printRawDoc(Document document, boolean gold) throws FileNotFoundException {
    List<CoreMap> sentences = document.annotation.get(CoreAnnotations.SentencesAnnotation.class);
    List<List<Mention>> allMentions;
    if (gold) {
      allMentions = document.goldOrderedMentionsBySentence;
    } else {
      allMentions = document.predictedOrderedMentionsBySentence;
    }
    //    String filename = document.annotation.get()

    StringBuilder doc = new StringBuilder();
    int previousOffset = 0;

    for(int i = 0 ; i<sentences.size(); i++) {
      CoreMap sentence = sentences.get(i);
      List<Mention> mentions = allMentions.get(i);

      List<CoreLabel> t = sentence.get(CoreAnnotations.TokensAnnotation.class);
      String[] tokens = new String[t.size()];
      for(CoreLabel c : t) {
        tokens[c.index()-1] = c.word();
      }
      if(previousOffset+2 < t.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)) {
        doc.append("\n");
      }
      previousOffset = t.get(t.size()-1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      Counter<Integer> startCounts = new ClassicCounter<>();
      Counter<Integer> endCounts = new ClassicCounter<>();
      Map<Integer, Set<Mention>> endMentions = Generics.newHashMap();
      for (Mention m : mentions) {
        startCounts.incrementCount(m.startIndex);
        endCounts.incrementCount(m.endIndex);
        if(!endMentions.containsKey(m.endIndex)) endMentions.put(m.endIndex, Generics.<Mention>newHashSet());
        endMentions.get(m.endIndex).add(m);
      }
      for (int j = 0 ; j < tokens.length; j++){
        if(endMentions.containsKey(j)) {
          for(Mention m : endMentions.get(j)){
            int corefChainId =  (gold)? m.goldCorefClusterID: m.corefClusterID;
            doc.append("]_").append(corefChainId);
          }
        }
        for (int k = 0 ; k < startCounts.getCount(j) ; k++) {
          if (doc.length() > 0 && doc.charAt(doc.length()-1) != '[') doc.append(" ");
          doc.append("[");
        }
        if (doc.length() > 0 && doc.charAt(doc.length()-1)!='[') doc.append(" ");
        doc.append(tokens[j]);
      }
      if(endMentions.containsKey(tokens.length)) {
        for(Mention m : endMentions.get(tokens.length)){
          int corefChainId =  (gold)? m.goldCorefClusterID: m.corefClusterID;
          doc.append("]_").append(corefChainId); //append("_").append(m.mentionID);
        }
      }

      doc.append("\n");
    }
    logger.fine(document.annotation.get(CoreAnnotations.DocIDAnnotation.class));
    if (gold) {
      logger.fine("New DOC: (GOLD MENTIONS) ==================================================");
    } else {
      logger.fine("New DOC: (Predicted Mentions) ==================================================");
    }
    logger.fine(doc.toString());
  }
  public static List<Pair<IntTuple, IntTuple>> getLinks(
      Map<Integer, CorefChain> result) {
    List<Pair<IntTuple, IntTuple>> links = new ArrayList<>();
    CorefChain.CorefMentionComparator comparator = new CorefChain.CorefMentionComparator();

    for (CorefChain c : result.values()) {
      List<CorefMention> s = c.getMentionsInTextualOrder();
      for (CorefMention m1 : s) {
        for (CorefMention m2 : s) {
          if (comparator.compare(m1, m2)==1) {
            links.add(new Pair<>(m1.position, m2.position));
          }
        }
      }
    }
    return links;
  }

  public static void debugPrintMentions(PrintStream out, String tag, List<List<Mention>> mentions) {
    for(int i = 0; i < mentions.size(); i ++){
     out.println(tag + " SENTENCE " + i);
     for(int j = 0; j < mentions.get(i).size(); j ++){
       Mention m = mentions.get(i).get(j);
       String ms = "(" + m.mentionID + "," + m.originalRef + "," + m.corefClusterID
               + ",[" + m.startIndex + "," + m.endIndex +"]" + ") ";
       out.print(ms);
     }
     out.println();
   }
  }

  public static boolean checkClusters(Logger logger, String tag, Document document) {
    List<List<Mention>> mentions = document.getOrderedMentions();
    boolean clustersOk = true;
    for (List<Mention> mentionCluster : mentions) {
      for (Mention m : mentionCluster) {
        String ms = "(" + m.mentionID + "," + m.originalRef + "," + m.corefClusterID
                + ",[" + m.startIndex + "," + m.endIndex + "]" + ") ";
        CorefCluster cluster = document.corefClusters.get(m.corefClusterID);
        if (cluster == null) {
          logger.warning(tag + ": Cluster not found for mention: " + ms);
          clustersOk = false;
        } else if (!cluster.getCorefMentions().contains(m)) {
          logger.warning(tag + ": Cluster does not contain mention: " + ms);
          clustersOk = false;
        }
      }
    }
    return clustersOk;
  }

}
