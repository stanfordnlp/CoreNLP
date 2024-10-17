// Stanford Parser -- a probabilistic lexicalized NL CFG parser
// Copyright (c) 2002 - 2014 The Board of Trustees of
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
// along with this program.  If not, see http://www.gnu.org/licenses/ .
//
// For more information, bug reports, fixes, contact:
//    Christopher Manning
//    Dept of Computer Science, Gates 2A
//    Stanford CA 94305-9020
//    USA
//    parser-support@lists.stanford.edu
//    https://nlp.stanford.edu/software/srparser.html

package edu.stanford.nlp.parser.shiftreduce;

import java.io.FileFilter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasTag;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.common.ParserGrammar;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.common.ParserUtils;
import edu.stanford.nlp.parser.lexparser.BinaryHeadFinder;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TreebankLangParserParams;
import edu.stanford.nlp.parser.lexparser.TreeBinarizer;
import edu.stanford.nlp.parser.metrics.EvaluateTreebank;
import edu.stanford.nlp.parser.metrics.ParserQueryEval;
import edu.stanford.nlp.parser.metrics.Eval;
import edu.stanford.nlp.tagger.common.Tagger;
import edu.stanford.nlp.trees.BasicCategoryTreeTransformer;
import edu.stanford.nlp.trees.CompositeTreeTransformer;
import edu.stanford.nlp.trees.HeadFinder;
import edu.stanford.nlp.trees.LabeledScoredTreeNode;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TreeCoreAnnotations;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.HashIndex;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ReflectionLoading;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.concurrent.MulticoreWrapper;
import edu.stanford.nlp.util.concurrent.ThreadsafeProcessor;
import edu.stanford.nlp.util.logging.Redwood;


/**
 * A shift-reduce constituency parser.
 * Overview and description available at
 * https://nlp.stanford.edu/software/srparser.shtml
 *
 * @author John Bauer
 */
public class ShiftReduceParser extends ParserGrammar implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ShiftReduceParser.class);

  final ShiftReduceOptions op;

  PerceptronModel model;

  public ShiftReduceParser(ShiftReduceOptions op) {
    this(op, null);
  }

  public ShiftReduceParser(ShiftReduceOptions op, PerceptronModel model) {
    this.op = op;
    this.model = model;
  }

  /*
  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    ObjectInputStream.GetField fields = in.readFields();
    op = ErasureUtils.uncheckedCast(fields.get("op", null));

    Index<Transition> transitionIndex = ErasureUtils.uncheckedCast(fields.get("transitionIndex", null));
    Set<String> knownStates = ErasureUtils.uncheckedCast(fields.get("knownStates", null));
    Set<String> rootStates = ErasureUtils.uncheckedCast(fields.get("rootStates", null));
    Set<String> rootOnlyStates = ErasureUtils.uncheckedCast(fields.get("rootOnlyStates", null));

    FeatureFactory featureFactory = ErasureUtils.uncheckedCast(fields.get("featureFactory", null));
    Map<String, Weight> featureWeights = ErasureUtils.uncheckedCast(fields.get("featureWeights", null));
    this.model = new PerceptronModel(op, transitionIndex, knownStates, rootStates, rootOnlyStates, featureFactory, featureWeights);
  }
  */

  @Override
  public Options getOp() {
    return op;
  }

  @Override
  public TreebankLangParserParams getTLPParams() {
    return op.tlpParams;
  }

  @Override
  public TreebankLanguagePack treebankLanguagePack() {
    return getTLPParams().treebankLanguagePack();
  }

  private static final String[] BEAM_FLAGS = { "-beamSize", "4" };

  @Override
  public String[] defaultCoreNLPFlags() {
    if (op.trainOptions().beamSize > 1) {
      return ArrayUtils.concatenate(getTLPParams().defaultCoreNLPFlags(), BEAM_FLAGS);
    } else {
      // TODO: this may result in some options which are useless for
      // this model, such as -retainTmpSubcategories
      return getTLPParams().defaultCoreNLPFlags();
    }
  }

  /**
   * Return an unmodifiableSet containing the known states (including binarization)
   */
  public Set<String> knownStates() {
    return Collections.unmodifiableSet(model.knownStates);
  }

  /** Return the Set of POS tags used in the model. */
  public Set<String> tagSet() {
    return model.tagSet();
  }

  @Override
  public boolean requiresTags() {
    return true;
  }

  @Override
  public ParserQuery parserQuery() {
    return new ShiftReduceParserQuery(this);
  }

  @Override
  public Tree parse(String sentence) {
    if (!getOp().testOptions.preTag) {
      throw new UnsupportedOperationException("Can only parse raw text if a tagger is specified, as the ShiftReduceParser cannot produce its own tags");
    }
    return super.parse(sentence);
  }

  @Override
  public Tree parse(List<? extends HasWord> sentence) {
    ShiftReduceParserQuery pq = new ShiftReduceParserQuery(this);
    if (pq.parse(sentence)) {
      return pq.getBestParse();
    }
    return ParserUtils.xTree(sentence);
  }

  @Override
  public Tree parseTree(List<? extends HasWord> sentence) {
    ShiftReduceParserQuery pq = new ShiftReduceParserQuery(this);
    if (pq.parse(sentence)) {
      return pq.getBestParse();
    }
    return null;
  }


  /** TODO: add an eval which measures transition accuracy? */
  @Override
  public List<Eval> getExtraEvals() {
    return Collections.emptyList();
  }

  @Override
  public List<ParserQueryEval> getParserQueryEvals() {
    if (op.testOptions().recordBinarized == null &&
        op.testOptions().recordDebinarized == null &&
        !op.testOptions().recordTransitionTypes) {
      return Collections.emptyList();
    }
    List<ParserQueryEval> evals = Generics.newArrayList();
    if (op.testOptions().recordBinarized != null) {
      evals.add(new TreeRecorder(TreeRecorder.Mode.BINARIZED, op.testOptions().recordBinarized));
    }
    if (op.testOptions().recordDebinarized != null) {
      evals.add(new TreeRecorder(TreeRecorder.Mode.DEBINARIZED, op.testOptions().recordDebinarized));
    }
    if (op.testOptions().recordTransitionTypes) {
      evals.add(new TransitionTypeEval());
    }
    return evals;
  }

  public static State initialStateFromGoldTagTree(Tree tree) {
    return initialStateFromTaggedSentence(tree.taggedYield());
  }

  public static State initialStateFromTaggedSentence(List<? extends HasWord> words) {
    List<Tree> preterminals = Generics.newArrayList();
    for (int index = 0; index < words.size(); ++index) {
      HasWord hw = words.get(index);

      CoreLabel wordLabel;
      String tag;
      if (hw instanceof CoreLabel) {
        wordLabel = (CoreLabel) hw;
        tag = wordLabel.tag();
      } else {
        wordLabel = new CoreLabel();
        wordLabel.setValue(hw.word());
        wordLabel.setWord(hw.word());
        if (!(hw instanceof HasTag)) {
          throw new IllegalArgumentException("Expected tagged words");
        }
        tag = ((HasTag) hw).tag();
        wordLabel.setTag(tag);
      }
      if (tag == null) {
        throw new IllegalArgumentException("Input word not tagged");
      }
      CoreLabel tagLabel = new CoreLabel();
      tagLabel.setValue(tag);

      // Index from 1.  Tools downstream from the parser expect that
      // Internally this parser uses the index, so we have to
      // overwrite incorrect indices if the label is already indexed
      wordLabel.setIndex(index + 1);
      tagLabel.setIndex(index + 1);

      LabeledScoredTreeNode wordNode = new LabeledScoredTreeNode(wordLabel);
      LabeledScoredTreeNode tagNode = new LabeledScoredTreeNode(tagLabel);
      tagNode.addChild(wordNode);

      // TODO: can we get away with not setting these on the wordLabel?
      wordLabel.set(TreeCoreAnnotations.HeadWordLabelAnnotation.class, wordLabel);
      wordLabel.set(TreeCoreAnnotations.HeadTagLabelAnnotation.class, tagLabel);
      tagLabel.set(TreeCoreAnnotations.HeadWordLabelAnnotation.class, wordLabel);
      tagLabel.set(TreeCoreAnnotations.HeadTagLabelAnnotation.class, tagLabel);

      preterminals.add(tagNode);
    }
    return new State(preterminals);
  }

  private static final String[] BASIC_TRAINING_OPTIONS = { "-forceTags", "-debugOutputFrequency", "1", "-quietEvaluation" };
  private static final String[] FORCE_TAGS = { "-forceTags" };

  public static ShiftReduceOptions buildTrainingOptions(String tlppClass, String[] args) {
    ShiftReduceOptions op = new ShiftReduceOptions();
    op.setOptions(BASIC_TRAINING_OPTIONS);
    if (tlppClass != null) {
      op.tlpParams = ReflectionLoading.loadByReflection(tlppClass);
    }
    op.setOptions(args);

    if (op.trainOptions.randomSeed == 0) {
      op.trainOptions.randomSeed = System.nanoTime();
      log.info("Random seed not set by options, using " + op.trainOptions.randomSeed);
    }
    return op;
  }

  public Treebank readTreebank(String treebankPath, FileFilter treebankFilter) {
    log.info("Loading trees from " + treebankPath);
    Treebank treebank = op.tlpParams.memoryTreebank();
    treebank.loadPath(treebankPath, treebankFilter);
    log.info("Read in " + treebank.size() + " trees from " + treebankPath);
    return treebank;
  }

  public List<Tree> readBinarizedTreebank(String treebankPath, FileFilter treebankFilter) {
    Treebank treebank = readTreebank(treebankPath, treebankFilter);
    List<Tree> filtered = filterTreebank(treebank);
    List<Tree> binarized = binarizeTreebank(filtered, op);
    log.info("Converted trees to binarized format");
    return binarized;
  }

  /**
   * If an internal node goes directly to a leaf, that is an illegal tree.
   * Otherwise, accept the tree.
   * <br>
   * Example:
   * <pre>(ROOT (sentence (S (morfema.pronominal (PRON Se)) -----(sn (spec (PRON los)) grup.nom)----- (grup.verb (PROPN trago')) (sn (spec (PROPN la)) (grup.nom (DET tierra)))) (NOUN ....) (S (sadv (grup.adv (PROPN ya))) (neg (ADV no)) (grup.verb (ADV viven)) (sp (prep (VERB en)) (sn (grup.nom (ADP Cuba)))) (PROPN ...)) (conj (PUNCT y)) (S (sp (prep (CCONJ a)) (sn (grup.nom (ADP nadie)))) (sn (grup.nom (PRON le))) (grup.verb (PRON importa)) (sn (spec (VERB la)) (grup.nom (S (relatiu (DET que)) (grup.verb (PRON esten) (gerundi (VERB pasando))))))) (VERB ....)))</pre>
   */
  public static boolean checkLeafBranching(Tree tree) {
    if (tree == null) {
      return false;
    }
    if (tree.isLeaf() || tree.isPreTerminal()) {
      return true;
    }
    for (Tree child : tree.children()) {
      if (!checkLeafBranching(child)) {
        return false;
      }
      if (child.isLeaf()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Some trees in the English datasets have a binary transition at
   * the top, which we don't like as it teaches the parser to
   * sometimes make binary transitions in normal trees
   */
  public static boolean checkRootTransition(Tree tree) {
    return (tree.numChildren() == 1);
  }

  /**
   * Filters any trees which are obviously unacceptable for the sr parser training.
   * <br>
   * Disallowed: <ul>
   * <li> trees where internal nodes go directly to leaves instead of preterminals.
   * <li> trees which don't start with a unary transition
   * </ul>
   */
  public List<Tree> filterTreebank(Treebank treebank) {
    List<Tree> filteredTrees = new ArrayList<>();
    for (Tree tree : treebank) {
      if (!checkLeafBranching(tree)) {
        log.error("Found an illegal tree, skipping (leaf in internal node): " + tree);
      } else if (!checkRootTransition(tree)) {
        log.error("Found an illegal tree, skipping (non-unary root production): " + tree);
      } else {
        filteredTrees.add(tree);
      }
    }
    return filteredTrees;
  }

  public static List<Tree> binarizeTreebank(Iterable<Tree> treebank, Options op) {
    TreeBinarizer binarizer = TreeBinarizer.simpleTreeBinarizer(op.tlpParams.headFinder(), op.tlpParams.treebankLanguagePack());
    BasicCategoryTreeTransformer basicTransformer = new BasicCategoryTreeTransformer(op.langpack());
    CompositeTreeTransformer transformer = new CompositeTreeTransformer();
    transformer.addTransformer(binarizer);
    transformer.addTransformer(basicTransformer);

    List<Tree> transformedTrees = new ArrayList<>();
    for (Tree tree : treebank) {
      transformedTrees.add(transformer.transformTree(tree));
    }

    HeadFinder binaryHeadFinder = new BinaryHeadFinder(op.tlpParams.headFinder());
    List<Tree> binarizedTrees = new ArrayList<>();
    for (Tree tree : transformedTrees) {
      if (!tree.isBinarized()) {
        log.warn("Found a tree which was not properly binarized.  So-called binarized tree is as follows:\n" +
                 tree.pennString());
        continue;
      }
      Trees.convertToCoreLabels(tree);
      tree.percolateHeadAnnotations(binaryHeadFinder);
      // Index from 1.  Tools downstream expect index from 1, so for
      // uses internal to the srparser we have to renormalize the
      // indices, with the result that here we have to index from 1
      tree.indexLeaves(1, true);
      binarizedTrees.add(tree);
    }
    return binarizedTrees;
  }

  public static Set<String> findKnownStates(List<Tree> binarizedTrees) {
    Set<String> knownStates = Generics.newHashSet();
    for (Tree tree : binarizedTrees) {
      findKnownStates(tree, knownStates);
    }
    return Collections.unmodifiableSet(knownStates);
  }

  public static void findKnownStates(Tree tree, Set<String> knownStates) {
    if (tree.isLeaf() || tree.isPreTerminal()) {
      return;
    }
    if (!ShiftReduceUtils.isTemporary(tree)) {
      knownStates.add(tree.value());
    }
    for (Tree child : tree.children()) {
      findKnownStates(child, knownStates);
    }
  }


  // TODO: factor out the retagging?
  public static void redoTags(Tree tree, Tagger tagger) {
    List<Word> words = tree.yieldWords();
    List<TaggedWord> tagged = tagger.apply(words);
    List<Label> tags = tree.preTerminalYield();
    if (tags.size() != tagged.size()) {
      throw new AssertionError("Tags are not the same size");
    }
    for (int i = 0; i < tags.size(); ++i) {
      tags.get(i).setValue(tagged.get(i).tag());
    }
  }

  private static class RetagProcessor implements ThreadsafeProcessor<Tree, Tree> {
    Tagger tagger;

    public RetagProcessor(Tagger tagger) {
      this.tagger = tagger;
    }

    @Override
    public Tree process(Tree tree) {
      redoTags(tree, tagger);
      return tree;
    }

    @Override
    public RetagProcessor newInstance() {
      // already threadsafe
      return this;
    }
  }

  public static void redoTags(List<Tree> trees, Tagger tagger, int nThreads) {
    if (nThreads == 1) {
      for (Tree tree : trees) {
        redoTags(tree, tagger);
      }
    } else {
      MulticoreWrapper<Tree, Tree> wrapper = new MulticoreWrapper<>(nThreads, new RetagProcessor(tagger));
      for (Tree tree : trees) {
        wrapper.put(tree);
      }
      wrapper.join();
      // trees are changed in place
    }
  }

  /**
   * Get all of the states which occur at the root, even if they occur
   * elsewhere in the tree.  Useful for knowing when you can Finalize
   * a tree
   */
  private static Set<String> findRootStates(List<Tree> trees) {
    Set<String> roots = Generics.newHashSet();
    for (Tree tree : trees) {
      roots.add(tree.value());
    }
    return Collections.unmodifiableSet(roots);
  }

  /**
   * Get all of the states which *only* occur at the root.  Useful for
   * knowing which transitions can't be done internal to the tree
   */
  private static Set<String> findRootOnlyStates(List<Tree> trees, Set<String> rootStates) {
    Set<String> rootOnlyStates = Generics.newHashSet(rootStates);
    for (Tree tree : trees) {
      for (Tree child : tree.children()) {
        findRootOnlyStatesHelper(child, rootStates, rootOnlyStates);
      }
    }
    return Collections.unmodifiableSet(rootOnlyStates);
  }

  private static void findRootOnlyStatesHelper(Tree tree, Set<String> rootStates, Set<String> rootOnlyStates) {
    rootOnlyStates.remove(tree.value());
    for (Tree child : tree.children()) {
      findRootOnlyStatesHelper(child, rootStates, rootOnlyStates);
    }
  }

  private void verifyTransitions(List<TrainingExample> trainingData) {
    for (TrainingExample train : trainingData) {
      State state = initialStateFromGoldTagTree(train.binarizedTree);
      List<Transition> transitions = train.transitions;
      for (int j = 0; j < transitions.size(); ++j) {
        if (!transitions.get(j).isLegal(state, null)) {
          System.err.println("Transition list for a gold tree is illegal!");
          System.err.println("  " + train.binarizedTree);
          System.err.println("  " + transitions);
          System.err.println("  First illegal transition: " + j + ": " + transitions.get(j));
          System.err.println("  State at this time: " + state);
          break;
        }
        state = transitions.get(j).apply(state);
      }
    }
  }

  private void train(List<Pair<String, FileFilter>> trainTreebankPath,
                     Pair<String, FileFilter> devTreebankPath,
                     String serializedPath) {
    log.info("Training method: " + op.trainOptions().trainingMethod);
    log.debug("Headfinder used to binarize trees: " + getTLPParams().headFinder().getClass());

    List<Tree> binarizedTrees = Generics.newArrayList();
    for (Pair<String, FileFilter> treebank : trainTreebankPath) {
      binarizedTrees.addAll(readBinarizedTreebank(treebank.first(), treebank.second()));
    }

    int nThreads = op.trainOptions.trainingThreads;
    nThreads = nThreads <= 0 ? Runtime.getRuntime().availableProcessors() : nThreads;

    Tagger tagger = null;
    if (op.testOptions.preTag) {
      Timing retagTimer = new Timing();
      tagger = Tagger.loadModel(op.testOptions.taggerSerializedFile);
      log.info("Retagging with tagger model: " + op.testOptions.taggerSerializedFile);
      log.info("Known tags in the tagger model: " + tagger.tagSet());
      redoTags(binarizedTrees, tagger, nThreads);
      log.info("Tags in training set: " + Trees.uniqueTags(binarizedTrees));
      retagTimer.done("Retagging");
    }

    Set<String> knownStates = findKnownStates(binarizedTrees);
    Set<String> rootStates = findRootStates(binarizedTrees);
    Set<String> rootOnlyStates = findRootOnlyStates(binarizedTrees, rootStates);

    log.info("Known states: " + new TreeSet<String>(knownStates));
    log.info("States which occur at the root: " + rootStates);
    log.info("States which only occur at the root: " + rootOnlyStates);

    Timing transitionTimer = new Timing();
    List<TrainingExample> trainingData = CreateTransitionSequence.createTransitionSequences(binarizedTrees, op.compoundUnaries, rootStates, rootOnlyStates);
    Index<Transition> transitionIndex = new HashIndex<>();
    for (TrainingExample example : trainingData) {
      transitionIndex.addAll(example.transitions);
    }

    verifyTransitions(trainingData);

    transitionTimer.done("Converting trees into transition lists");
    log.info("Number of transitions: " + transitionIndex.size());

    Random random = new Random(op.trainOptions.randomSeed);

    Treebank devTreebank = null;
    if (devTreebankPath != null) {
      devTreebank = readTreebank(devTreebankPath.first(), devTreebankPath.second());
    }

    this.model = PerceptronModel.trainModel(this.op, transitionIndex, knownStates, rootStates, rootOnlyStates, this.model,
                                            serializedPath, tagger, random, trainingData, devTreebank, nThreads);
  }

  @Override
  public void setOptionFlags(String... flags) {
    op.setOptions(flags);
  }

  public static ShiftReduceParser loadModel(String path, String... extraFlags) {
    ShiftReduceParser parser = IOUtils.readObjectAnnouncingTimingFromURLOrClasspathOrFileSystem(
            log, "Loading parser from serialized file", path);
    if (extraFlags.length > 0) {
      parser.setOptionFlags(extraFlags);
    }
    return parser;
  }

  public void saveModel(String path) {
    try {
      IOUtils.writeObjectToFile(this, path);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    }
  }

  public static void main(String[] args) {
    List<String> remainingArgs = Generics.newArrayList();

    List<Pair<String, FileFilter>> trainTreebankPath = null;
    Pair<String, FileFilter> testTreebankPath = null;
    Pair<String, FileFilter> devTreebankPath = null;

    String serializedPath = null;

    String tlppClass = null;

    String continueTraining = null;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-trainTreebank")) {
        if (trainTreebankPath == null) {
          trainTreebankPath = Generics.newArrayList();
        }
        trainTreebankPath.add(ArgUtils.getTreebankDescription(args, argIndex, "-trainTreebank"));
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
      } else if (args[argIndex].equalsIgnoreCase("-testTreebank")) {
        testTreebankPath = ArgUtils.getTreebankDescription(args, argIndex, "-testTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
      } else if (args[argIndex].equalsIgnoreCase("-devTreebank")) {
        devTreebankPath = ArgUtils.getTreebankDescription(args, argIndex, "-devTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
      } else if (args[argIndex].equalsIgnoreCase("-serializedPath") || args[argIndex].equalsIgnoreCase("-model")) {
        serializedPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-tlpp")) {
        tlppClass = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-continueTraining")) {
        continueTraining = args[argIndex + 1];
        argIndex += 2;
      } else {
        remainingArgs.add(args[argIndex]);
        ++argIndex;
      }
    }

    String[] newArgs = new String[remainingArgs.size()];
    newArgs = remainingArgs.toArray(newArgs);

    if (trainTreebankPath == null && serializedPath == null) {
      throw new IllegalArgumentException("Must specify a treebank to train from with -trainTreebank or a parser to load with -serializedPath");
    }

    ShiftReduceParser parser = null;

    if (trainTreebankPath != null) {
      log.info("Training ShiftReduceParser");
      log.info("Initial arguments:");
      log.info("   " + StringUtils.join(args));
      if (continueTraining != null) {
        parser = ShiftReduceParser.loadModel(continueTraining, ArrayUtils.concatenate(BASIC_TRAINING_OPTIONS, newArgs));
      } else {
        ShiftReduceOptions op = buildTrainingOptions(tlppClass, newArgs);
        parser = new ShiftReduceParser(op);
      }
      Timing trainingTimer = new Timing();
      parser.train(trainTreebankPath, devTreebankPath, serializedPath);
      trainingTimer.done("Overall training process");
      parser.saveModel(serializedPath);
    }

    if (serializedPath != null && parser == null) {
      parser = ShiftReduceParser.loadModel(serializedPath, ArrayUtils.concatenate(FORCE_TAGS, newArgs));
    }

    //parser.outputStats();

    if (testTreebankPath != null) {
      log.info("Loading test trees from " + testTreebankPath.first());
      Treebank testTreebank = parser.op.tlpParams.memoryTreebank();
      testTreebank.loadPath(testTreebankPath.first(), testTreebankPath.second());
      log.info("Loaded " + testTreebank.size() + " trees");

      EvaluateTreebank evaluator = new EvaluateTreebank(parser.op, null, parser);
      evaluator.testOnTreebank(testTreebank);

      // log.info("Input tree: " + tree);
      // log.info("Debinarized tree: " + query.getBestParse());
      // log.info("Parsed binarized tree: " + query.getBestBinarizedParse());
      // log.info("Predicted transition sequence: " + query.getBestTransitionSequence());
    }
  }


  private static final long serialVersionUID = 1;

}

