package edu.stanford.nlp.parser.lexparser;

import edu.stanford.nlp.trees.CompositeTreeTransformer;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;

import java.io.*;
import java.util.*;


/**
 * This class contains options to the parser which MUST be the SAME at
 * both training and testing (parsing) time in order for the parser to
 * work properly.  It also contains an object which stores the options
 * used by the parser at training time and an object which contains
 * default options for test use.
 *
 * @author Dan Klein
 * @author Christopher Manning
 * @author John Bauer
 */
public class Options implements Serializable {

  public Options() {
    this(new EnglishTreebankParserParams());
  }

  public Options(TreebankLangParserParams tlpParams) {
    this.tlpParams = tlpParams;
  }

  /**
   * Set options based on a String array in the style of
   * commandline flags. This method goes through the array until it ends,
   * processing options, as for {@link #setOption}.
   *
   * @param flags Array of options (or as a varargs list of arguments).
   *      The options passed in should
   *      be specified like command-line arguments, including with an initial
   *      minus sign  for example,
   *          {"-outputFormat", "typedDependencies", "-maxLength", "70"}
   * @throws IllegalArgumentException If an unknown flag is passed in
   */
  public void setOptions(String... flags) {
    setOptions(flags, 0, flags.length);
  }

  /**
   * Set options based on a String array in the style of
   * commandline flags. This method goes through the array until it ends,
   * processing options, as for {@link #setOption}.
   *
   * @param flags Array of options.  The options passed in should
   *      be specified like command-line arguments, including with an initial
   *      minus sign  for example,
   *          {"-outputFormat", "typedDependencies", "-maxLength", "70"}
   * @param startIndex The index in the array to begin processing options at
   * @param endIndexPlusOne A number one greater than the last array index at
   *      which options should be processed
   * @throws IllegalArgumentException If an unknown flag is passed in
   */
  public void setOptions(final String[] flags, final int startIndex, final int endIndexPlusOne) {
    for (int i = startIndex; i < endIndexPlusOne;) {
      i = setOption(flags, i);
    }
  }

  /**
   * Set options based on a String array in the style of
   * commandline flags. This method goes through the array until it ends,
   * processing options, as for {@link #setOption}.
   *
   * @param flags Array of options (or as a varargs list of arguments).
   *      The options passed in should
   *      be specified like command-line arguments, including with an initial
   *      minus sign  for example,
   *          {"-outputFormat", "typedDependencies", "-maxLength", "70"}
   * @throws IllegalArgumentException If an unknown flag is passed in
   */
  public void setOptionsOrWarn(String... flags) {
    setOptionsOrWarn(flags, 0, flags.length);
  }

  /**
   * Set options based on a String array in the style of
   * commandline flags. This method goes through the array until it ends,
   * processing options, as for {@link #setOption}.
   *
   * @param flags Array of options.  The options passed in should
   *      be specified like command-line arguments, including with an initial
   *      minus sign  for example,
   *          {"-outputFormat", "typedDependencies", "-maxLength", "70"}
   * @param startIndex The index in the array to begin processing options at
   * @param endIndexPlusOne A number one greater than the last array index at
   *      which options should be processed
   * @throws IllegalArgumentException If an unknown flag is passed in
   */
  public void setOptionsOrWarn(final String[] flags, final int startIndex, final int endIndexPlusOne) {
    for (int i = startIndex; i < endIndexPlusOne;) {
      i = setOptionOrWarn(flags, i);
    }
  }

  /**
   * Set an option based on a String array in the style of
   * commandline flags. The option may
   * be either one known by the Options object, or one recognized by the
   * TreebankLangParserParams which has already been set up inside the Options
   * object, and then the option is set in the language-particular
   * TreebankLangParserParams.
   * Note that despite this method being an instance method, many flags
   * are actually set as static class variables in the Train and Test
   * classes (this should be fixed some day).
   * Some options (there are many others; see the source code):
   * <ul>
   * <li> <code>-maxLength n</code> set the maximum length sentence to parse (inclusively)
   * <li> <code>-printTT</code> print the training trees in raw, annotated, and annotated+binarized form.  Useful for debugging and other miscellany.
   * <li> <code>-printAnnotated filename</code> use only in conjunction with -printTT.  Redirects printing of annotated training trees to <code>filename</code>.
   * <li> <code>-forceTags</code> when the parser is tested against a set of gold standard trees, use the tagged yield, instead of just the yield, as input.
   * </ul>
   *
   * @param flags An array of options arguments, command-line style.  E.g. {"-maxLength", "50"}.
   * @param i The index in flags to start at when processing an option
   * @return The index in flags of the position after the last element used in
   *      processing this option. If the current array position cannot be processed as a valid
   *      option, then a warning message is printed to stderr and the return value is <code>i+1</code>
   */
  public int setOptionOrWarn(String[] flags, int i) {
    int j = setOptionFlag(flags, i);
    if (j == i) {
      j = tlpParams.setOptionFlag(flags, i);
    }
    if (j == i) {
      System.err.println("WARNING! lexparser.Options: Unknown option ignored: " + flags[i]);
      j++;
    }
    return j;
  }

  /**
   * Set an option based on a String array in the style of
   * commandline flags. The option may
   * be either one known by the Options object, or one recognized by the
   * TreebankLangParserParams which has already been set up inside the Options
   * object, and then the option is set in the language-particular
   * TreebankLangParserParams.
   * Note that despite this method being an instance method, many flags
   * are actually set as static class variables in the Train and Test
   * classes (this should be fixed some day).
   * Some options (there are many others; see the source code):
   * <ul>
   * <li> <code>-maxLength n</code> set the maximum length sentence to parse (inclusively)
   * <li> <code>-printTT</code> print the training trees in raw, annotated, and annotated+binarized form.  Useful for debugging and other miscellany.
   * <li> <code>-printAnnotated filename</code> use only in conjunction with -printTT.  Redirects printing of annotated training trees to <code>filename</code>.
   * <li> <code>-forceTags</code> when the parser is tested against a set of gold standard trees, use the tagged yield, instead of just the yield, as input.
   * </ul>
   *
   * @param flags An array of options arguments, command-line style.  E.g. {"-maxLength", "50"}.
   * @param i The index in flags to start at when processing an option
   * @return The index in flags of the position after the last element used in
   *      processing this option.
   * @throws IllegalArgumentException If the current array position cannot be
   *      processed as a valid option
   */
  public int setOption(String[] flags, int i) {
    int j = setOptionFlag(flags, i);
    if (j == i) {
      j = tlpParams.setOptionFlag(flags, i);
    }
    if (j == i) {
      throw new IllegalArgumentException("Unknown option: " + flags[i]);
    }
    return j;
  }

  /**
   * Set an option in this object, based on a String array in the style of
   * commandline flags.  The option is only processed with respect to
   * options directly known by the Options object.
   * Some options (there are many others; see the source code):
   * <ul>
   * <li> <code>-maxLength n</code> set the maximum length sentence to parse (inclusively)
   * <li> <code>-printTT</code> print the training trees in raw, annotated, and annotated+binarized form.  Useful for debugging and other miscellany.
   * <li> <code>-printAnnotated filename</code> use only in conjunction with -printTT.  Redirects printing of annotated training trees to <code>filename</code>.
   * <li> <code>-forceTags</code> when the parser is tested against a set of gold standard trees, use the tagged yield, instead of just the yield, as input.
   * </ul>
   *
   * @param args An array of options arguments, command-line style.  E.g. {"-maxLength", "50"}.
   * @param i The index in args to start at when processing an option
   * @return The index in args of the position after the last element used in
   *      processing this option, or the value i unchanged if a valid option couldn't
   *      be processed starting at position i.
   */
  protected int setOptionFlag(String[] args, int i) {
    if (args[i].equalsIgnoreCase("-PCFG")) {
      doDep = false;
      doPCFG = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-dep")) {
      doDep = true;
      doPCFG = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-factored")) {
      doDep = true;
      doPCFG = true;
      testOptions.useFastFactored = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-fastFactored")) {
      doDep = true;
      doPCFG = true;
      testOptions.useFastFactored = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-noRecoveryTagging")) {
      testOptions.noRecoveryTagging = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-useLexiconToScoreDependencyPwGt")) {
      testOptions.useLexiconToScoreDependencyPwGt = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-useSmoothTagProjection")) {
      useSmoothTagProjection = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-useUnigramWordSmoothing")) {
      useUnigramWordSmoothing = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-useNonProjectiveDependencyParser")) {
      testOptions.useNonProjectiveDependencyParser = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-maxLength") && (i + 1 < args.length)) {
      testOptions.maxLength = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-MAX_ITEMS") && (i + 1 < args.length)) {
      testOptions.MAX_ITEMS = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-trainLength") && (i + 1 < args.length)) {
      // train on only short sentences
      trainOptions.trainLengthLimit = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-lengthNormalization")) {
      testOptions.lengthNormalization = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-iterativeCKY")) {
      testOptions.iterativeCKY = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-vMarkov") && (i + 1 < args.length)) {
      int order = Integer.parseInt(args[i + 1]);
      if (order <= 1) {
        trainOptions.PA = false;
        trainOptions.gPA = false;
      } else if (order == 2) {
        trainOptions.PA = true;
        trainOptions.gPA = false;
      } else if (order >= 3) {
        trainOptions.PA = true;
        trainOptions.gPA = true;
      }
      i += 2;
    } else if (args[i].equalsIgnoreCase("-vSelSplitCutOff") && (i + 1 < args.length)) {
      trainOptions.selectiveSplitCutOff = Double.parseDouble(args[i + 1]);
      trainOptions.selectiveSplit = trainOptions.selectiveSplitCutOff > 0.0;
      i += 2;
    } else if (args[i].equalsIgnoreCase("-vSelPostSplitCutOff") && (i + 1 < args.length)) {
      trainOptions.selectivePostSplitCutOff = Double.parseDouble(args[i + 1]);
      trainOptions.selectivePostSplit = trainOptions.selectivePostSplitCutOff > 0.0;
      i += 2;
    } else if (args[i].equalsIgnoreCase("-deleteSplitters") && (i+1 < args.length)) {
      String[] toDel = args[i+1].split(" *, *");
      trainOptions.deleteSplitters = Generics.newHashSet(Arrays.asList(toDel));
      i += 2;
    } else if (args[i].equalsIgnoreCase("-postSplitWithBaseCategory")) {
      trainOptions.postSplitWithBaseCategory = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-vPostMarkov") && (i + 1 < args.length)) {
      int order = Integer.parseInt(args[i + 1]);
      if (order <= 1) {
        trainOptions.postPA = false;
        trainOptions.postGPA = false;
      } else if (order == 2) {
        trainOptions.postPA = true;
        trainOptions.postGPA = false;
      } else if (order >= 3) {
        trainOptions.postPA = true;
        trainOptions.postGPA = true;
      }
      i += 2;
    } else if (args[i].equalsIgnoreCase("-hMarkov") && (i + 1 < args.length)) {
      int order = Integer.parseInt(args[i + 1]);
      if (order >= 0) {
        trainOptions.markovOrder = order;
        trainOptions.markovFactor = true;
      } else {
        trainOptions.markovFactor = false;
      }
      i += 2;
    } else if (args[i].equalsIgnoreCase("-distanceBins") && (i + 1 < args.length)) {
      int numBins = Integer.parseInt(args[i + 1]);
      if (numBins <= 1) {
        distance = false;
      } else if (numBins == 4) {
        distance = true;
        coarseDistance = true;
      } else if (numBins == 5) {
        distance = true;
        coarseDistance = false;
      } else {
        throw new IllegalArgumentException("Invalid value for -distanceBin: " + args[i+1]);
      }
      i += 2;
    } else if (args[i].equalsIgnoreCase("-noStop")) {
      genStop = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-nonDirectional")) {
      directional = false;
      i++;
    } else if (args[i].equalsIgnoreCase("-depWeight") && (i + 1 < args.length)) {
      testOptions.depWeight = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-printPCFGkBest") && (i + 1 < args.length)) {
      testOptions.printPCFGkBest = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-evalPCFGkBest") && (i + 1 < args.length)) {
      testOptions.evalPCFGkBest = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-printFactoredKGood") && (i + 1 < args.length)) {
      testOptions.printFactoredKGood = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-smoothTagsThresh") && (i + 1 < args.length)) {
      lexOptions.smoothInUnknownsThreshold = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-unseenSmooth") && (i + 1 < args.length)) {
      testOptions.unseenSmooth = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-fractionBeforeUnseenCounting") && (i + 1 < args.length)) {
      trainOptions.fractionBeforeUnseenCounting = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-hSelSplitThresh") && (i + 1 < args.length)) {
      trainOptions.HSEL_CUT = Integer.parseInt(args[i + 1]);
      trainOptions.hSelSplit = trainOptions.HSEL_CUT > 0;
      i += 2;
    } else if (args[i].equalsIgnoreCase("-nohSelSplit")) {
      trainOptions.hSelSplit = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-tagPA")) {
      trainOptions.tagPA = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noTagPA")) {
      trainOptions.tagPA = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-tagSelSplitCutOff") && (i + 1 < args.length)) {
      trainOptions.tagSelectiveSplitCutOff = Double.parseDouble(args[i + 1]);
      trainOptions.tagSelectiveSplit = trainOptions.tagSelectiveSplitCutOff > 0.0;
      i += 2;
    } else if (args[i].equalsIgnoreCase("-tagSelPostSplitCutOff") && (i + 1 < args.length)) {
      trainOptions.tagSelectivePostSplitCutOff = Double.parseDouble(args[i + 1]);
      trainOptions.tagSelectivePostSplit = trainOptions.tagSelectivePostSplitCutOff > 0.0;
      i += 2;
    } else if (args[i].equalsIgnoreCase("-noTagSplit")) {
      trainOptions.noTagSplit = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-uwm") && (i + 1 < args.length)) {
      lexOptions.useUnknownWordSignatures = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-unknownSuffixSize") && (i + 1 < args.length)) {
      lexOptions.unknownSuffixSize = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-unknownPrefixSize") && (i + 1 < args.length)) {
      lexOptions.unknownPrefixSize = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-uwModelTrainer") && (i + 1 < args.length)) {
      lexOptions.uwModelTrainer = args[i+1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-openClassThreshold") && (i + 1 < args.length)) {
      trainOptions.openClassTypesThreshold = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-unary") && i+1 < args.length) {
      trainOptions.markUnary = Integer.parseInt(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-unaryTags")) {
      trainOptions.markUnaryTags = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-mutate")) {
      lexOptions.smartMutation = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-useUnicodeType")) {
      lexOptions.useUnicodeType = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-rightRec")) {
      trainOptions.rightRec = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noRightRec")) {
      trainOptions.rightRec = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-preTag")) {
      testOptions.preTag = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-forceTags")) {
      testOptions.forceTags = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-taggerSerializedFile")) {
      testOptions.taggerSerializedFile = args[i+1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-forceTagBeginnings")) {
      testOptions.forceTagBeginnings = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noFunctionalForcing")) {
      testOptions.noFunctionalForcing = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-scTags")) {
      dcTags = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-dcTags")) {
      dcTags = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-basicCategoryTagsInDependencyGrammar")) {
      trainOptions.basicCategoryTagsInDependencyGrammar = true;
      i+= 1;
    } else if (args[i].equalsIgnoreCase("-evalb")) {
      testOptions.evalb = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-v") || args[i].equalsIgnoreCase("-verbose")) {
      testOptions.verbose = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-outputFilesDirectory") && i+1 < args.length) {
      testOptions.outputFilesDirectory = args[i+1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-outputFilesExtension") && i+1 < args.length) {
      testOptions.outputFilesExtension = args[i+1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-outputFilesPrefix") && i+1 < args.length) {
      testOptions.outputFilesPrefix = args[i+1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-outputkBestEquivocation") && i+1 < args.length) {
      testOptions.outputkBestEquivocation = args[i+1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-writeOutputFiles")) {
      testOptions.writeOutputFiles = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-printAllBestParses")) {
      testOptions.printAllBestParses = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-outputTreeFormat") || args[i].equalsIgnoreCase("-outputFormat")) {
      testOptions.outputFormat = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-outputTreeFormatOptions") || args[i].equalsIgnoreCase("-outputFormatOptions")) {
      testOptions.outputFormatOptions = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-addMissingFinalPunctuation")) {
      testOptions.addMissingFinalPunctuation = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-flexiTag")) {
      lexOptions.flexiTag = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-lexiTag")) {
      lexOptions.flexiTag = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-useSignatureForKnownSmoothing")) {
      lexOptions.useSignatureForKnownSmoothing = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-wordClassesFile")) {
      lexOptions.wordClassesFile = args[i+1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-compactGrammar")) {
      trainOptions.compactGrammar = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-markFinalStates")) {
      trainOptions.markFinalStates = args[i + 1].equalsIgnoreCase("true");
      i += 2;
    } else if (args[i].equalsIgnoreCase("-leftToRight")) {
      trainOptions.leftToRight = args[i + 1].equals("true");
      i += 2;
    } else if (args[i].equalsIgnoreCase("-cnf")) {
      forceCNF = true;
      i += 1;
    } else if(args[i].equalsIgnoreCase("-smoothRules")) {
      trainOptions.ruleSmoothing = true;
      trainOptions.ruleSmoothingAlpha = Double.valueOf(args[i+1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-nodePrune") && i+1 < args.length) {
      nodePrune = args[i+1].equalsIgnoreCase("true");
      i += 2;
    } else if (args[i].equalsIgnoreCase("-noDoRecovery")) {
      testOptions.doRecovery = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-acl03chinese")) {
      trainOptions.markovOrder = 1;
      trainOptions.markovFactor = true;
      // no increment
    } else if (args[i].equalsIgnoreCase("-wordFunction")) {
      wordFunction = ReflectionLoading.loadByReflection(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-acl03pcfg")) {
      doDep = false;
      doPCFG = true;
      // lexOptions.smoothInUnknownsThreshold = 30;
      trainOptions.markUnary = 1;
      trainOptions.PA = true;
      trainOptions.gPA = false;
      trainOptions.tagPA = true;
      trainOptions.tagSelectiveSplit = false;
      trainOptions.rightRec = true;
      trainOptions.selectiveSplit = true;
      trainOptions.selectiveSplitCutOff = 400.0;
      trainOptions.markovFactor = true;
      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = true;
      lexOptions.useUnknownWordSignatures = 2;
      lexOptions.flexiTag = true;
      // DAN: Tag double-counting is BAD for PCFG-only parsing
      dcTags = false;
      // don't increment i so it gets language specific stuff as well
    } else if (args[i].equalsIgnoreCase("-jenny")) {
      doDep = false;
      doPCFG = true;
      // lexOptions.smoothInUnknownsThreshold = 30;
      trainOptions.markUnary = 1;
      trainOptions.PA = false;
      trainOptions.gPA = false;
      trainOptions.tagPA = false;
      trainOptions.tagSelectiveSplit = false;
      trainOptions.rightRec = true;
      trainOptions.selectiveSplit = false;
//      trainOptions.selectiveSplitCutOff = 400.0;
      trainOptions.markovFactor = false;
//      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = false;
      lexOptions.useUnknownWordSignatures = 2;
      lexOptions.flexiTag = true;
      // DAN: Tag double-counting is BAD for PCFG-only parsing
      dcTags = false;
      // don't increment i so it gets language specific stuff as well
    } else if (args[i].equalsIgnoreCase("-goodPCFG")) {
      doDep = false;
      doPCFG = true;
      // op.lexOptions.smoothInUnknownsThreshold = 30;
      trainOptions.markUnary = 1;
      trainOptions.PA = true;
      trainOptions.gPA = false;
      trainOptions.tagPA = true;
      trainOptions.tagSelectiveSplit = false;
      trainOptions.rightRec = true;
      trainOptions.selectiveSplit = true;
      trainOptions.selectiveSplitCutOff = 400.0;
      trainOptions.markovFactor = true;
      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = true;
      lexOptions.useUnknownWordSignatures = 2;
      lexOptions.flexiTag = true;
      // DAN: Tag double-counting is BAD for PCFG-only parsing
      dcTags = false;
      String[] delSplit = { "-deleteSplitters", "VP^NP,VP^VP,VP^SINV,VP^SQ" };
      if (this.setOptionFlag(delSplit, 0) != 2) {
        System.err.println("Error processing deleteSplitters");
      }
      // don't increment i so it gets language specific stuff as well
    } else if (args[i].equalsIgnoreCase("-linguisticPCFG")) {
      doDep = false;
      doPCFG = true;
      // op.lexOptions.smoothInUnknownsThreshold = 30;
      trainOptions.markUnary = 1;
      trainOptions.PA = true;
      trainOptions.gPA = false;
      trainOptions.tagPA = true;        // on at the moment, but iffy
      trainOptions.tagSelectiveSplit = false;
      trainOptions.rightRec = false;    // not for linguistic
      trainOptions.selectiveSplit = true;
      trainOptions.selectiveSplitCutOff = 400.0;
      trainOptions.markovFactor = true;
      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = true;
      lexOptions.useUnknownWordSignatures = 5;   // different from acl03pcfg
      lexOptions.flexiTag = false;       // different from acl03pcfg
      // DAN: Tag double-counting is BAD for PCFG-only parsing
      dcTags = false;
      // don't increment i so it gets language specific stuff as well
    } else if (args[i].equalsIgnoreCase("-ijcai03")) {
      doDep = true;
      doPCFG = true;
      trainOptions.markUnary = 0;
      trainOptions.PA = true;
      trainOptions.gPA = false;
      trainOptions.tagPA = false;
      trainOptions.tagSelectiveSplit = false;
      trainOptions.rightRec = false;
      trainOptions.selectiveSplit = true;
      trainOptions.selectiveSplitCutOff = 300.0;
      trainOptions.markovFactor = true;
      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = true;
      trainOptions.compactGrammar = 0; /// cdm: May 2005 compacting bad for factored?
      lexOptions.useUnknownWordSignatures = 2;
      lexOptions.flexiTag = false;
      dcTags = true;
      // op.nodePrune = true;  // cdm: May 2005: this doesn't help
      // don't increment i so it gets language specific stuff as well
    } else if (args[i].equalsIgnoreCase("-goodFactored")) {
      doDep = true;
      doPCFG = true;
      trainOptions.markUnary = 0;
      trainOptions.PA = true;
      trainOptions.gPA = false;
      trainOptions.tagPA = false;
      trainOptions.tagSelectiveSplit = false;
      trainOptions.rightRec = false;
      trainOptions.selectiveSplit = true;
      trainOptions.selectiveSplitCutOff = 300.0;
      trainOptions.markovFactor = true;
      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = true;
      trainOptions.compactGrammar = 0; /// cdm: May 2005 compacting bad for factored?
      lexOptions.useUnknownWordSignatures = 5;  // different from ijcai03
      lexOptions.flexiTag = false;
      dcTags = true;
      // op.nodePrune = true;  // cdm: May 2005: this doesn't help
      // don't increment i so it gets language specific stuff as well
    } else if (args[i].equalsIgnoreCase("-chineseFactored")) {
      // Single counting tag->word rewrite is also much better for Chinese
      // Factored.  Bracketing F1 goes up about 0.7%.
      dcTags = false;
      lexOptions.useUnicodeType = true;
      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = true;
      trainOptions.markovFactor = true;
      trainOptions.HSEL_CUT = 50;
      // trainOptions.openClassTypesThreshold=1;  // so can get unseen punctuation
      // trainOptions.fractionBeforeUnseenCounting=0.0;  // so can get unseen punctuation
      // don't increment i so it gets language specific stuff as well
    } else if (args[i].equalsIgnoreCase("-arabicFactored")) {
      doDep = true;
      doPCFG = true;
      dcTags = false;   // "false" seems to help Arabic about 0.1% F1
      trainOptions.markovFactor = true;
      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = true;
      trainOptions.HSEL_CUT = 75;  // 75 bit better than 50, 100 a bit worse
      trainOptions.PA = true;
      trainOptions.gPA = false;
      trainOptions.selectiveSplit = true;
      trainOptions.selectiveSplitCutOff = 300.0;
      trainOptions.markUnary = 1;  // Helps PCFG and marginally factLB
      // trainOptions.compactGrammar = 0;  // Doesn't seem to help or only 0.05% F1
      lexOptions.useUnknownWordSignatures = 9;
      lexOptions.unknownPrefixSize = 1;
      lexOptions.unknownSuffixSize = 1;
      testOptions.MAX_ITEMS = 500000; // Arabic sentences are long enough that this helps a fraction
      // don't increment i so it gets language specific stuff as well
    } else if (args[i].equalsIgnoreCase("-frenchFactored")) {
      doDep = true;
      doPCFG = true;
      dcTags = false;   //wsg2011: Setting to false improves F1 by 0.5%
      trainOptions.markovFactor = true;
      trainOptions.markovOrder = 2;
      trainOptions.hSelSplit = true;
      trainOptions.HSEL_CUT = 75;
      trainOptions.PA = true;
      trainOptions.gPA = false;
      trainOptions.selectiveSplit = true;
      trainOptions.selectiveSplitCutOff = 300.0;
      trainOptions.markUnary = 0; //Unary rule marking bad for french..setting to 0 gives +0.3 F1
      lexOptions.useUnknownWordSignatures = 1;
      lexOptions.unknownPrefixSize = 1;
      lexOptions.unknownSuffixSize = 2;

    } else if (args[i].equalsIgnoreCase("-chinesePCFG")) {
      trainOptions.markovOrder = 2;
      trainOptions.markovFactor = true;
      trainOptions.HSEL_CUT = 5;
      trainOptions.PA = true;
      trainOptions.gPA = true;
      trainOptions.selectiveSplit = false;
      doDep = false;
      doPCFG = true;
      // Single counting tag->word rewrite is also much better for Chinese PCFG
      // Bracketing F1 is up about 2% and tag accuracy about 1% (exact by 6%)
      dcTags = false;
      // no increment
    } else if (args[i].equalsIgnoreCase("-printTT") && (i+1 < args.length)) {
      trainOptions.printTreeTransformations = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-printAnnotatedRuleCounts")) {
      trainOptions.printAnnotatedRuleCounts = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-printAnnotatedStateCounts")) {
      trainOptions.printAnnotatedStateCounts = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-printAnnotated") && (i + 1 < args.length)) {
      try {
        trainOptions.printAnnotatedPW = tlpParams.pw(new FileOutputStream(args[i + 1]));
      } catch (IOException ioe) {
        trainOptions.printAnnotatedPW = null;
      }
      i += 2;
    } else if (args[i].equalsIgnoreCase("-printBinarized") && (i + 1 < args.length)) {
      try {
        trainOptions.printBinarizedPW = tlpParams.pw(new FileOutputStream(args[i + 1]));
      } catch (IOException ioe) {
        trainOptions.printBinarizedPW = null;
      }
      i += 2;
    } else if (args[i].equalsIgnoreCase("-printStates")) {
      trainOptions.printStates = true;
      i++;
    } else if (args[i].equalsIgnoreCase("-preTransformer") && (i + 1 < args.length)) {
      String[] classes = args[i + 1].split(",");
      i += 2;
      if (classes.length == 1) {
        trainOptions.preTransformer =
          ReflectionLoading.loadByReflection(classes[0], this);
      } else if (classes.length > 1) {
        CompositeTreeTransformer composite = new CompositeTreeTransformer();
        trainOptions.preTransformer = composite;
        for (String clazz : classes) {
          TreeTransformer transformer =
            ReflectionLoading.loadByReflection(clazz, this);
          composite.addTransformer(transformer);
        }
      }
    } else if (args[i].equalsIgnoreCase("-taggedFiles") && (i + 1 < args.length)) {
      trainOptions.taggedFiles = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-predictSplits")) {
      // This is an experimental (and still in development)
      // reimplementation of Berkeley's state splitting grammar.
      trainOptions.predictSplits = true;
      trainOptions.compactGrammar = 0;
      i++;
    } else if (args[i].equalsIgnoreCase("-splitCount")) {
      trainOptions.splitCount = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-splitRecombineRate")) {
      trainOptions.splitRecombineRate = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-trainingThreads") ||
               args[i].equalsIgnoreCase("-nThreads")) {
      trainOptions.trainingThreads = Integer.parseInt(args[i + 1]);
      testOptions.testingThreads = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-testingThreads")) {
      testOptions.testingThreads = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-evals")) {
      testOptions.evals = StringUtils.stringToProperties(args[i+1], testOptions.evals);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-fastFactoredCandidateMultiplier")) {
      testOptions.fastFactoredCandidateMultiplier = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-fastFactoredCandidateAddend")) {
      testOptions.fastFactoredCandidateAddend = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-simpleBinarizedLabels")) {
      trainOptions.simpleBinarizedLabels = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noRebinarization")) {
      trainOptions.noRebinarization = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-dvKBest")) {
        trainOptions.dvKBest = Integer.parseInt(args[i + 1]);
        rerankerKBest = trainOptions.dvKBest;
        i += 2;
    } else if (args[i].equalsIgnoreCase("-regCost")) {
        trainOptions.regCost = Double.parseDouble(args[i + 1]);
        i += 2;
    } else if (args[i].equalsIgnoreCase("-dvIterations") || args[i].equalsIgnoreCase("-trainingIterations")) {
      trainOptions.trainingIterations = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-stalledIterationLimit")) {
      trainOptions.stalledIterationLimit = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-dvBatchSize") || args[i].equalsIgnoreCase("-batchSize")) {
      trainOptions.batchSize = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-qnIterationsPerBatch")) {
      trainOptions.qnIterationsPerBatch = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-qnEstimates")) {
      trainOptions.qnEstimates = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-qnTolerance")) {
      trainOptions.qnTolerance = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-debugOutputFrequency")) {
      trainOptions.debugOutputFrequency = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-maxTrainTimeSeconds")) {
      trainOptions.maxTrainTimeSeconds = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-dvSeed") || args[i].equalsIgnoreCase("-randomSeed")) {
      trainOptions.randomSeed = Long.parseLong(args[i + 1]);
      i += 2;      
    } else if (args[i].equalsIgnoreCase("-wordVectorFile")) {
      lexOptions.wordVectorFile = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-numHid")) {
      lexOptions.numHid = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-learningRate")) {
      trainOptions.learningRate = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-deltaMargin")) {
      trainOptions.deltaMargin = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-unknownNumberVector")) {
      trainOptions.unknownNumberVector = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noUnknownNumberVector")) {
      trainOptions.unknownNumberVector = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-unknownDashedWordVectors")) {
      trainOptions.unknownDashedWordVectors = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noUnknownDashedWordVectors")) {
      trainOptions.unknownDashedWordVectors = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-unknownCapsVector")) {
      trainOptions.unknownCapsVector = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noUnknownCapsVector")) {
      trainOptions.unknownCapsVector = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-unknownChineseYearVector")) {
      trainOptions.unknownChineseYearVector = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noUnknownChineseYearVector")) {
      trainOptions.unknownChineseYearVector = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-unknownChineseNumberVector")) {
      trainOptions.unknownChineseNumberVector = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noUnknownChineseNumberVector")) {
      trainOptions.unknownChineseNumberVector = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-unknownChinesePercentVector")) {
      trainOptions.unknownChinesePercentVector = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noUnknownChinesePercentVector")) {
      trainOptions.unknownChinesePercentVector = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-dvSimplifiedModel")) {
      trainOptions.dvSimplifiedModel = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-scalingForInit")) {
      trainOptions.scalingForInit = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-rerankerKBest")) {
      rerankerKBest = Integer.parseInt(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-baseParserWeight")) {
      baseParserWeight = Double.parseDouble(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-unkWord")) {
      trainOptions.unkWord = args[i + 1];
      i += 2;
    } else if (args[i].equalsIgnoreCase("-lowercaseWordVectors")) {
      trainOptions.lowercaseWordVectors = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noLowercaseWordVectors")) {
      trainOptions.lowercaseWordVectors = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-transformMatrixType")) {
      trainOptions.transformMatrixType = TrainOptions.TransformMatrixType.valueOf(args[i + 1]);
      i += 2;
    } else if (args[i].equalsIgnoreCase("-useContextWords")) {
      trainOptions.useContextWords = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noUseContextWords")) {
      trainOptions.useContextWords = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-trainWordVectors")) {
      trainOptions.trainWordVectors = true;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-noTrainWordVectors")) {
      trainOptions.trainWordVectors = false;
      i += 1;
    } else if (args[i].equalsIgnoreCase("-markStrahler")) {
      trainOptions.markStrahler = true;
      i += 1;
    }
    return i;
  }

  public static class LexOptions implements Serializable {

    /**
     * Whether to use suffix and capitalization information for unknowns.
     * Within the BaseLexicon model options have the following meaning:
     * 0 means a single unknown token.  1 uses suffix, and capitalization.
     * 2 uses a variant (richer) form of signature.  Good.
     * Use this one.  Using the richer signatures in versions 3 or 4 seems
     * to have very marginal or no positive value.
     * 3 uses a richer form of signature that mimics the NER word type
     * patterns.  4 is a variant of 2.  5 is another with more English
     * specific morphology (good for English unknowns!).
     * 6-9 are options for Arabic.  9 codes some patterns for numbers and
     * derivational morphology, but also supports unknownPrefixSize and
     * unknownSuffixSize.
     * For German, 0 means a single unknown token, and non-zero means to use
     * capitalization of first letter and a suffix of length
     * unknownSuffixSize.
     */
    public int useUnknownWordSignatures = 0;

    /**
     * RS: file for Turian's word vectors
     * The default value is an example of size 25 word vectors on the nlp machines
     */
    public static final String DEFAULT_WORD_VECTOR_FILE = "/scr/nlp/deeplearning/datasets/turian/embeddings-scaled.EMBEDDING_SIZE=25.txt";
    public String wordVectorFile = DEFAULT_WORD_VECTOR_FILE;
    /**
     * Number of hidden units in the word vectors.  As setting of 0
     * will make it try to extract the size from the data file.
     */
    public int numHid = 0;


    /**
     * Words more common than this are tagged with MLE P(t|w). Default 100. The
     * smoothing is sufficiently slight that changing this has little effect.
     * But set this to 0 to be able to use the parser as a vanilla PCFG with
     * no smoothing (not as a practical parser but for exposition or debugging).
     */
    public int smoothInUnknownsThreshold = 100;

    /**
     * Smarter smoothing for rare words.
     */
    public boolean smartMutation = false;

    /**
     * Make use of unicode code point types in smoothing.
     */
    public boolean useUnicodeType = false;

    /** For certain Lexicons, a certain number of word-final letters are
     *  used to subclassify the unknown token. This gives the number of
     *  letters.
     */
    public int unknownSuffixSize = 1;

    /** For certain Lexicons, a certain number of word-initial letters are
     *  used to subclassify the unknown token. This gives the number of
     *  letters.
     */
    public int unknownPrefixSize = 1;

    /**
     * Model for unknown words that the lexicon should use.  This is the
     * name of a class.
     */
    public String uwModelTrainer; // = null;

    /* If this option is false, then all words that were seen in the training
     * data (even once) are constrained to only have seen tags.  That is,
     * mle is used for the lexicon.
     * If this option is true, then if a word has been seen more than
     * smoothInUnknownsThreshold, then it will still only get tags with which
     * it has been seen, but rarer words will get all tags for which the
     * unknown word model (or smart mutation) does not give a score of -Inf.
     * This will normally be all open class tags.
     * If floodTags is invoked by the parser, all other tags will also be
     * given a minimal non-zero, non-infinite probability.
     */
    public boolean flexiTag = false;

    /** Whether to use signature rather than just being unknown as prior in
     *  known word smoothing.  Currently only works if turned on for English.
     */
    public boolean useSignatureForKnownSmoothing;

    /** A file of word class data which may be used for smoothing,
     *  normally instead of hand-specified signatures.
     */
    public String wordClassesFile;



    private static final long serialVersionUID = 2805351374506855632L;

    private static final String[] params = { "useUnknownWordSignatures",
                                             "smoothInUnknownsThreshold",
                                             "smartMutation",
                                             "useUnicodeType",
                                             "unknownSuffixSize",
                                             "unknownPrefixSize",
                                             "flexiTag",
                                             "useSignatureForKnownSmoothing",
                                             "wordClassesFile" };

    @Override
    public String toString() {
      return params[0] + " " + useUnknownWordSignatures + "\n" +
        params[1] + " " + smoothInUnknownsThreshold + "\n" +
        params[2] + " " + smartMutation + "\n" +
        params[3] + " " + useUnicodeType + "\n" +
        params[4] + " " + unknownSuffixSize + "\n" +
        params[5] + " " + unknownPrefixSize + "\n" +
        params[6] + " " + flexiTag + "\n" +
        params[7] + " " + useSignatureForKnownSmoothing + "\n" +
        params[8] + " " + wordClassesFile + "\n";
    }

    public void readData(BufferedReader in) throws IOException {
      for (int i = 0; i < params.length; i++) {
        String line = in.readLine();
        int idx = line.indexOf(' ');
        String key = line.substring(0, idx);
        String value = line.substring(idx + 1);
        if ( ! key.equalsIgnoreCase(params[i])) {
          System.err.println("Yikes!!! Expected " + params[i] + " got " + key);
        }
        switch (i) {
        case 0:
          useUnknownWordSignatures = Integer.parseInt(value);
          break;
        case 1:
          smoothInUnknownsThreshold = Integer.parseInt(value);
          break;
        case 2:
          smartMutation = Boolean.parseBoolean(value);
          break;
        case 3:
          useUnicodeType = Boolean.parseBoolean(value);
          break;
        case 4:
          unknownSuffixSize = Integer.parseInt(value);
          break;
        case 5:
          unknownPrefixSize = Integer.parseInt(value);
          break;
        case 6:
          flexiTag = Boolean.parseBoolean(value);
          break;
        case 7:
          useSignatureForKnownSmoothing = Boolean.parseBoolean(value);
          break;
        case 8:
          wordClassesFile = value;
          break;
        }
      }
    }

  } // end class LexOptions


  public LexOptions lexOptions = new LexOptions();

  /**
   * The treebank-specific parser parameters  to use.
   */
  public TreebankLangParserParams tlpParams;

  /**
   * @return The treebank language pack for the treebank the parser
   * is trained on.
   */
  public TreebankLanguagePack langpack() {
    return tlpParams.treebankLanguagePack();
  }


  /**
   * Forces parsing with strictly CNF grammar -- unary chains are converted
   * to XP&amp;YP symbols and back
   */
  public boolean forceCNF = false;

  /**
   * Do a PCFG parse of the sentence.  If both variables are on,
   * also do a combined parse of the sentence.
   */
  public boolean doPCFG = true;

  /**
   * Do a dependency parse of the sentence.
   */
  public boolean doDep = true;

  /**
   * if true, any child can be the head (seems rather bad!)
   */
  public boolean freeDependencies = false;

  /**
   * Whether dependency grammar considers left/right direction. Good.
   */
  public boolean directional = true;
  public boolean genStop = true;

  public boolean useSmoothTagProjection = false;
  public boolean useUnigramWordSmoothing = false;

  /**
   * Use distance bins in the dependency calculations
   */
  public boolean distance = true;
  /**
   * Use coarser distance (4 bins) in dependency calculations
   */
  public boolean coarseDistance = false;

  /**
   * "double count" tags rewrites as word in PCFG and Dep parser.  Good for
   * combined parsing only (it used to not kick in for PCFG parsing).  This
   * option is only used at Test time, but it is now in Options, so the
   * correct choice for a grammar is recorded by a serialized parser.
   * You should turn this off for a vanilla PCFG parser.
   */
  public boolean dcTags = true;

  /**
   * If true, inside the factored parser, remove any node from the final
   * chosen tree which improves the PCFG score. This was added as the
   * dependency factor tends to encourage 'deep' trees.
   */
  public boolean nodePrune = false;


  public TrainOptions trainOptions = newTrainOptions();

  /** Separated out so subclasses of Options can override */
  public TrainOptions newTrainOptions() {
    return new TrainOptions();
  }

  /**
   * Note that the TestOptions is transient.  This means that whatever
   * options get set at creation time are forgotten when the parser is
   * serialized.  If you want an option to be remembered when the
   * parser is reloaded, put it in either TrainOptions or in this
   * class itself.
   */
  public transient TestOptions testOptions = newTestOptions();

  /** Separated out so subclasses of Options can override */
  public TestOptions newTestOptions() {
    return new TestOptions();
  }


  /**
   * A function that maps words used in training and testing to new
   * words.  For example, it could be a function to lowercase text,
   * such as edu.stanford.nlp.util.LowercaseFunction (which makes the
   * parser case insensitive).  This function is applied in
   * LexicalizedParserQuery.parse and in the training methods which
   * build a new parser.
   */
  public Function<String, String> wordFunction = null;

  /**
   * If the parser has a reranker, it looks at this many trees when
   * building the reranked list.
   */
  public int rerankerKBest = 100;

  /**
   * If reranking sentences, we can use the score from the original
   * parser as well.  This tells us how much weight to give that score.
   */
  public double baseParserWeight = 0.0;

  /**
   * Making the TestOptions transient means it won't even be
   * constructed when you deserialize an Options, so we need to
   * construct it on our own when deserializing
   */
  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    in.defaultReadObject();
    testOptions = newTestOptions();
  }

  public void display() {
//    try {
      System.err.println("Options parameters:");
      writeData(new PrintWriter(System.err));
/*    } catch (IOException e) {
      e.printStackTrace();
    }*/
  }

  public void writeData(Writer w) {//throws IOException {
    PrintWriter out = new PrintWriter(w);
    StringBuilder sb = new StringBuilder();
    sb.append(lexOptions.toString());
    sb.append("parserParams ").append(tlpParams.getClass().getName()).append("\n");
    sb.append("forceCNF ").append(forceCNF).append("\n");
    sb.append("doPCFG ").append(doPCFG).append("\n");
    sb.append("doDep ").append(doDep).append("\n");
    sb.append("freeDependencies ").append(freeDependencies).append("\n");
    sb.append("directional ").append(directional).append("\n");
    sb.append("genStop ").append(genStop).append("\n");
    sb.append("distance ").append(distance).append("\n");
    sb.append("coarseDistance ").append(coarseDistance).append("\n");
    sb.append("dcTags ").append(dcTags).append("\n");
    sb.append("nPrune ").append(nodePrune).append("\n");
    out.print(sb.toString());
    out.flush();
  }


  /**
   * Populates data in this Options from the character stream.
   * @param in The Reader
   * @throws IOException If there is a problem reading data
   */
  public void readData(BufferedReader in) throws IOException {
    String line, value;
    // skip old variables if still present
    lexOptions.readData(in);
    line = in.readLine();
    value = line.substring(line.indexOf(' ') + 1);
    try {
      tlpParams = (TreebankLangParserParams) Class.forName(value).newInstance();
    } catch (Exception e) {
      IOException ioe = new IOException("Problem instantiating parserParams: " + line);
      ioe.initCause(e);
      throw ioe;
    }
    line = in.readLine();
    // ensure backwards compatibility
    if (line.matches("^forceCNF.*")) {
      value = line.substring(line.indexOf(' ') + 1);
      forceCNF = Boolean.parseBoolean(value);
      line = in.readLine();
    }
    value = line.substring(line.indexOf(' ') + 1);
    doPCFG = Boolean.parseBoolean(value);
    line = in.readLine();
    value = line.substring(line.indexOf(' ') + 1);
    doDep = Boolean.parseBoolean(value);
    line = in.readLine();
    value = line.substring(line.indexOf(' ') + 1);
    freeDependencies = Boolean.parseBoolean(value);
    line = in.readLine();
    value = line.substring(line.indexOf(' ') + 1);
    directional = Boolean.parseBoolean(value);
    line = in.readLine();
    value = line.substring(line.indexOf(' ') + 1);
    genStop = Boolean.parseBoolean(value);
    line = in.readLine();
    value = line.substring(line.indexOf(' ') + 1);
    distance = Boolean.parseBoolean(value);
    line = in.readLine();
    value = line.substring(line.indexOf(' ') + 1);
    coarseDistance = Boolean.parseBoolean(value);
    line = in.readLine();
    value = line.substring(line.indexOf(' ') + 1);
    dcTags = Boolean.parseBoolean(value);
    line = in.readLine();
    if ( ! line.matches("^nPrune.*")) {
      throw new RuntimeException("Expected nPrune, found: " + line);
    }
    value = line.substring(line.indexOf(' ') + 1);
    nodePrune = Boolean.parseBoolean(value);
    line = in.readLine(); // get rid of last line
    if (line.length() != 0) {
      throw new RuntimeException("Expected blank line, found: " + line);
    }
  }

  private static final long serialVersionUID = 4L;

} // end class Options
