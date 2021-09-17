package edu.stanford.nlp.parser.dvparser;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.parser.common.ArgUtils;
import edu.stanford.nlp.parser.common.ParserQuery;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TrainOptions;
import edu.stanford.nlp.parser.metrics.EvaluateTreebank;
import edu.stanford.nlp.trees.CompositeTreeTransformer;
import edu.stanford.nlp.trees.TreeTransformer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.Treebank;
import edu.stanford.nlp.trees.Trees;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.ScoredObject;
import edu.stanford.nlp.util.Timing;

/**
 * @author John Bauer &amp; Richard Socher
 */
public class DVParser  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(DVParser.class);
  final DVModel dvModel;
  final LexicalizedParser parser;
  final Options op;

  public Options getOp() {
    return op;
  }

  public DVModel getDVModel() {
    return dvModel;
  }

  public LexicalizedParser getBaseParser() {
    return parser;
  }

  private static final NumberFormat NF = new DecimalFormat("0.00");
  private static final NumberFormat FILENAME = new DecimalFormat("0000");

  public static List<Tree> getTopParsesForOneTree(LexicalizedParser parser, int dvKBest, Tree tree,
                                                  TreeTransformer transformer) {
    ParserQuery pq = parser.parserQuery();
    List<Word> sentence = tree.yieldWords();
    // Since the trees are binarized and otherwise manipulated, we
    // need to chop off the last word in order to remove the end of
    // sentence symbol
    if (sentence.size() <= 1) {
      return null;
    }
    sentence = sentence.subList(0, sentence.size() - 1);
    if (!pq.parse(sentence)) {
      log.info("Failed to use the given parser to reparse sentence \"" + sentence + "\"");
      return null;
    }
    List<Tree> parses = new ArrayList<>();
    List<ScoredObject<Tree>> bestKParses = pq.getKBestPCFGParses(dvKBest);
    for (ScoredObject<Tree> so : bestKParses) {
      Tree result = so.object();
      if (transformer != null) {
        result = transformer.transformTree(result);
      }
      parses.add(result);
    }
    return parses;
  }

  static IdentityHashMap<Tree, List<Tree>> getTopParses(LexicalizedParser parser, Options op,
                                                        Collection<Tree> trees, TreeTransformer transformer,
                                                        boolean outputUpdates) {
    IdentityHashMap<Tree, List<Tree>> topParses = new IdentityHashMap<>();
    for (Tree tree : trees) {
      List<Tree> parses = getTopParsesForOneTree(parser, op.trainOptions.dvKBest, tree, transformer);
      topParses.put(tree, parses);
      if (outputUpdates && topParses.size() % 10 == 0) {
        log.info("Processed " + topParses.size() + " trees");
      }
    }
    if (outputUpdates) {
      log.info("Finished processing " + topParses.size() + " trees");
    }
    return topParses;
  }

  IdentityHashMap<Tree, List<Tree>> getTopParses(List<Tree> trees, TreeTransformer transformer) {
    return getTopParses(parser, op, trees, transformer, false);
  }

  public void train(List<Tree> sentences, IdentityHashMap<Tree, byte[]> compressedParses, Treebank testTreebank, String modelPath, String resultsRecordPath) throws IOException {
    // process:
    //   we come up with a cost and a derivative for the model
    //   we always use the gold tree as the example to train towards
    //   every time through, we will look at the top N trees from
    //     the LexicalizedParser and pick the best one according to
    //     our model (at the start, this is essentially random)
    // we use QN to minimize the cost function for the model
    // to do this minimization, we turn all of the matrices in the
    //   DVModel into one big Theta, which is the set of variables to
    //   be optimized by the QN.

    Timing timing = new Timing();
    long maxTrainTimeMillis = op.trainOptions.maxTrainTimeSeconds * 1000;
    int batchCount = 0;
    int debugCycle = 0;
    double bestLabelF1 = 0.0;

    if (op.trainOptions.useContextWords) {
      for (Tree tree : sentences) {
        Trees.convertToCoreLabels(tree);
        tree.setSpans();
      }
    }

    // for AdaGrad
    double[] sumGradSquare = new double[dvModel.totalParamSize()];
    Arrays.fill(sumGradSquare, 1.0);

    int numBatches = sentences.size() / op.trainOptions.batchSize + 1;
    log.info("Training on " + sentences.size() + " trees in " + numBatches + " batches");
    log.info("Times through each training batch: " + op.trainOptions.trainingIterations);
    log.info("QN iterations per batch: " + op.trainOptions.qnIterationsPerBatch);
    for (int iter = 0; iter < op.trainOptions.trainingIterations; ++iter) {
      List<Tree> shuffledSentences = new ArrayList<>(sentences);
      Collections.shuffle(shuffledSentences, dvModel.rand);
      for (int batch = 0; batch < numBatches; ++batch) {
        ++batchCount;
        // This did not help performance
        //log.info("Setting AdaGrad's sum of squares to 1...");
        //Arrays.fill(sumGradSquare, 1.0);

        log.info("======================================");
        log.info("Iteration " + iter + " batch " + batch);

        // Each batch will be of the specified batch size, except the
        // last batch will include any leftover trees at the end of
        // the list
        int startTree = batch * op.trainOptions.batchSize;
        int endTree = (batch + 1) * op.trainOptions.batchSize;
        if (endTree > shuffledSentences.size()) {
          endTree = shuffledSentences.size();
        }

        executeOneTrainingBatch(shuffledSentences.subList(startTree, endTree), compressedParses, sumGradSquare);

        long totalElapsed = timing.report();
        log.info("Finished iteration " + iter + " batch " + batch + "; total training time " + totalElapsed + " ms");

        if (maxTrainTimeMillis > 0 && totalElapsed > maxTrainTimeMillis) {
          // no need to debug output, we're done now
          break;
        }

        if (op.trainOptions.debugOutputFrequency > 0 && batchCount % op.trainOptions.debugOutputFrequency == 0) {
          log.info("Finished " + batchCount + " total batches, running evaluation cycle");
          // Time for debugging output!
          double tagF1 = 0.0;
          double labelF1 = 0.0;
          if (testTreebank != null) {
            EvaluateTreebank evaluator = new EvaluateTreebank(attachModelToLexicalizedParser());
            evaluator.testOnTreebank(testTreebank);
            labelF1 = evaluator.getLBScore();
            tagF1 = evaluator.getTagScore();
            if (labelF1 > bestLabelF1) {
              bestLabelF1 = labelF1;
            }
            log.info("Best label f1 on dev set so far: " + NF.format(bestLabelF1));
          }

          String tempName = null;
          if (modelPath != null) {
            tempName = modelPath;
            if (modelPath.endsWith(".ser.gz")) {
              tempName = modelPath.substring(0, modelPath.length() - 7) + "-" + FILENAME.format(debugCycle) + "-" + NF.format(labelF1) + ".ser.gz";
            }
            saveModel(tempName);
          }

          String statusLine = ("CHECKPOINT:" +
                               " iteration " + iter +
                               " batch " + batch +
                               " labelF1 " + NF.format(labelF1) +
                               " tagF1 " + NF.format(tagF1) +
                               " bestLabelF1 " + NF.format(bestLabelF1) +
                               " model " + tempName +
                               op.trainOptions +
                               " word vectors: " + op.lexOptions.wordVectorFile +
                               " numHid: " + op.lexOptions.numHid);
          log.info(statusLine);
          if (resultsRecordPath != null) {
            FileWriter fout = new FileWriter(resultsRecordPath, true); // append
            fout.write(statusLine);
            fout.write("\n");
            fout.close();
          }

          ++debugCycle;
        }
      }
      long totalElapsed = timing.report();

      if (maxTrainTimeMillis > 0 && totalElapsed > maxTrainTimeMillis) {
        // no need to debug output, we're done now
        log.info("Max training time exceeded, exiting");
        break;
      }
    }
  }

  private static final int MINIMIZER = 3;

  public void executeOneTrainingBatch(List<Tree> trainingBatch, IdentityHashMap<Tree, byte[]> compressedParses, double[] sumGradSquare) {
    Timing convertTiming = new Timing();
    convertTiming.doing("Converting trees");
    IdentityHashMap<Tree, List<Tree>> topParses = CacheParseHypotheses.convertToTrees(trainingBatch, compressedParses, op.trainOptions.trainingThreads);
    convertTiming.done();

    DVParserCostAndGradient gcFunc = new DVParserCostAndGradient(trainingBatch, topParses, dvModel, op);
    double[] theta = dvModel.paramsToVector();

    //maxFuncIter = 10;
    // 1: QNMinimizer, 2: SGD
    switch (MINIMIZER) {
    case (1): {
      QNMinimizer qn = new QNMinimizer(op.trainOptions.qnEstimates, true);
      qn.useMinPackSearch();
      qn.useDiagonalScaling();
      qn.terminateOnAverageImprovement(true);
      qn.terminateOnNumericalZero(true);
      qn.terminateOnRelativeNorm(true);

      theta = qn.minimize(gcFunc, op.trainOptions.qnTolerance, theta, op.trainOptions.qnIterationsPerBatch);
      break;
    }
    case 2:{
      //Minimizer smd = new SGDMinimizer();    	double tol = 1e-4;    	theta = smd.minimize(gcFunc,tol,theta,op.trainOptions.qnIterationsPerBatch);
      double lastCost = 0, currCost = 0;
      boolean firstTime = true;
      for(int i = 0; i < op.trainOptions.qnIterationsPerBatch; i++){
        //gcFunc.calculate(theta);
        double[] grad = gcFunc.derivativeAt(theta);
        currCost = gcFunc.valueAt(theta);
        log.info("batch cost: " + currCost);
        //    		if(!firstTime){
        //    			if(currCost > lastCost){
        //    				System.out.println("HOW IS FUNCTION VALUE INCREASING????!!! ... still updating theta");
        //    			}
        //    			if(Math.abs(currCost - lastCost) < 0.0001){
        //    				System.out.println("function value is not decreasing. stop");
        //    			}
        //    		}else{
        //    			firstTime = false;
        //    		}
        lastCost = currCost;
        ArrayMath.addMultInPlace(theta, grad, -1*op.trainOptions.learningRate);
      }
      break;
    }
    case 3:{
      // AdaGrad
      double eps = 1e-3;
      double currCost = 0;
      for(int i = 0; i < op.trainOptions.qnIterationsPerBatch; i++){
        double[] gradf = gcFunc.derivativeAt(theta);
        currCost = gcFunc.valueAt(theta);
        log.info("batch cost: " + currCost);
        for (int feature =0; feature<gradf.length;feature++ ) {
          sumGradSquare[feature] = sumGradSquare[feature] + gradf[feature]*gradf[feature];
          theta[feature] = theta[feature] - (op.trainOptions.learningRate * gradf[feature]/(Math.sqrt(sumGradSquare[feature])+eps));
        }
      }
      break;
    }
    default: {
      throw new IllegalArgumentException("Unsupported minimizer " + MINIMIZER);
    }
    }


    dvModel.vectorToParams(theta);
  }

  public DVParser(DVModel model, LexicalizedParser parser) {
    this.parser = parser;
    this.op = parser.getOp();
    this.dvModel = model;
  }

  public DVParser(LexicalizedParser parser) {
    this.parser = parser;
    this.op = parser.getOp();

    if (op.trainOptions.randomSeed == 0) {
      op.trainOptions.randomSeed = System.nanoTime();
      log.info("Random seed not set, using randomly chosen seed of " + op.trainOptions.randomSeed);
    } else {
      log.info("Random seed set to " + op.trainOptions.randomSeed);
    }

    log.info("Word vector file: " + op.lexOptions.wordVectorFile);
    log.info("Size of word vectors: " + op.lexOptions.numHid);
    log.info("Number of hypothesis trees to train against: " + op.trainOptions.dvKBest);
    log.info("Number of trees in one batch: " + op.trainOptions.batchSize);
    log.info("Number of iterations of trees: " + op.trainOptions.trainingIterations);
    log.info("Number of qn iterations per batch: " + op.trainOptions.qnIterationsPerBatch);
    log.info("Learning rate: " + op.trainOptions.learningRate);
    log.info("Delta margin: " + op.trainOptions.deltaMargin);
    log.info("regCost: " + op.trainOptions.regCost);
    log.info("Using unknown word vector for numbers: " + op.trainOptions.unknownNumberVector);
    log.info("Using unknown dashed word vector heuristics: " + op.trainOptions.unknownDashedWordVectors);
    log.info("Using unknown word vector for capitalized words: " + op.trainOptions.unknownCapsVector);
    log.info("Using unknown number vector for Chinese words: " + op.trainOptions.unknownChineseNumberVector);
    log.info("Using unknown year vector for Chinese words: " + op.trainOptions.unknownChineseYearVector);
    log.info("Using unknown percent vector for Chinese words: " + op.trainOptions.unknownChinesePercentVector);
    log.info("Initial matrices scaled by: " + op.trainOptions.scalingForInit);
    log.info("Training will use " + op.trainOptions.trainingThreads + " thread(s)");
    log.info("Context words are " + ((op.trainOptions.useContextWords) ? "on" : "off"));
    log.info("Model will " + ((op.trainOptions.dvSimplifiedModel) ? "" : "not ") + "be simplified");

    this.dvModel = new DVModel(op, parser.stateIndex, parser.ug, parser.bg);

    if (dvModel.unaryTransform.size() != dvModel.unaryScore.size()) {
      throw new AssertionError("Unary transform and score size not the same");
    }
    if (dvModel.binaryTransform.size() != dvModel.binaryScore.size()) {
      throw new AssertionError("Binary transform and score size not the same");
    }
  }

  public boolean runGradientCheck(List<Tree> sentences, IdentityHashMap<Tree, byte[]> compressedParses) {
    log.info("Gradient check: converting " + sentences.size() + " compressed trees");
    IdentityHashMap<Tree, List<Tree>> topParses = CacheParseHypotheses.convertToTrees(sentences, compressedParses, op.trainOptions.trainingThreads);
    log.info("Done converting trees");
    DVParserCostAndGradient gcFunc = new DVParserCostAndGradient(sentences, topParses, dvModel, op);
    return gcFunc.gradientCheck(1000, 50, dvModel.paramsToVector());
  }

  public static TreeTransformer buildTrainTransformer(Options op) {
    CompositeTreeTransformer transformer = LexicalizedParser.buildTrainTransformer(op);
    return transformer;
  }

  public LexicalizedParser attachModelToLexicalizedParser() {
    LexicalizedParser newParser = LexicalizedParser.copyLexicalizedParser(parser);
    DVModelReranker reranker = new DVModelReranker(dvModel);
    newParser.reranker = reranker;
    return newParser;
  }

  public void saveModel(String filename) {
    log.info("Saving serialized model to " + filename);
    LexicalizedParser newParser = attachModelToLexicalizedParser();
    newParser.saveParserToSerialized(filename);
    log.info("... done");
  }

  public static DVParser loadModel(String filename, String[] args) {
    log.info("Loading serialized model from " + filename);
    DVParser dvparser;
    try {
      dvparser = IOUtils.readObjectFromURLOrClasspathOrFileSystem(filename);
      dvparser.op.setOptions(args);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
    log.info("... done");
    return dvparser;
  }

  public static DVModel getModelFromLexicalizedParser(LexicalizedParser parser) {
    if (!(parser.reranker instanceof DVModelReranker)) {
      throw new IllegalArgumentException("This parser does not contain a DVModel reranker");
    }
    DVModelReranker reranker = (DVModelReranker) parser.reranker;
    return reranker.getModel();
  }

  public static void help() {
    log.info("Options supplied by this file:");
    log.info("  -model <name>: When training, the name of the model to save.  Otherwise, the name of the model to load.");
    log.info("  -parser <name>: When training, the LexicalizedParser to use as the base model.");
    log.info("  -cachedTrees <name>: The name of the file containing a treebank with cached parses.  See CacheParseHypotheses.java");
    log.info("  -treebank <name> [filter]: A treebank to use instead of cachedTrees.  Trees will be reparsed.  Slow.");
    log.info("  -testTreebank <name> [filter]: A treebank for testing the model.");
    log.info("  -train: Run training over the treebank, testing on the testTreebank.");
    log.info("  -continueTraining <name>: The name of a file to continue training.");
    log.info("  -nofilter: Rules for the parser will not be filtered based on the training treebank.");
    log.info("  -runGradientCheck: Run a gradient check.");
    log.info("  -resultsRecord: A file for recording info on intermediate results");
    log.info();
    log.info("Options overlapping the parser:");
    log.info("  -trainingThreads <int>: How many threads to use when training.");
    log.info("  -dvKBest <int>: How many hypotheses to use from the underlying parser.");
    log.info("  -trainingIterations <int>: When training, how many times to go through the train set.");
    log.info("  -regCost <double>: How large of a cost to put on regularization.");
    log.info("  -batchSize <int>: How many trees to use in each batch of the training.");
    log.info("  -qnIterationsPerBatch <int>: How many steps to take per batch.");
    log.info("  -qnEstimates <int>: Parameter for qn optimization.");
    log.info("  -qnTolerance <double>: Tolerance for early exit when optimizing a batch.");
    log.info("  -debugOutputFrequency <int>: How frequently to score a model when training and write out intermediate models.");
    log.info("  -maxTrainTimeSeconds <int>: How long to train before terminating.");
    log.info("  -randomSeed <long>: A starting point for the random number generator.  Setting this should lead to repeatable results, even taking into account randomness.  Otherwise, a new random seed will be picked.");
    log.info("  -wordVectorFile <name>: A filename to load word vectors from.");
    log.info("  -numHid: The size of the matrices.  In most circumstances, should be set to the size of the word vectors.");
    log.info("  -learningRate: The rate of optimization when training");
    log.info("  -deltaMargin: How much we punish trees for being incorrect when training");
    log.info("  -(no)unknownNumberVector: Whether or not to use a word vector for unknown numbers");
    log.info("  -(no)unknownDashedWordVectors: Whether or not to split unknown dashed words");
    log.info("  -(no)unknownCapsVector: Whether or not to use a word vector for unknown words with capitals");
    log.info("  -dvSimplifiedModel: Use a greatly dumbed down DVModel");
    log.info("  -scalingForInit: How much to scale matrices when creating a new DVModel");
    log.info("  -baseParserWeight: A weight to give the original LexicalizedParser when testing (0.2 seems to work well for English)");
    log.info("  -unkWord: The vector representing unknown word in the word vectors file");
    log.info("  -transformMatrixType: A couple different methods for initializing transform matrices");
    log.info("  -(no)trainWordVectors: whether or not to train the word vectors along with the matrices.  True by default");
  }

  /**
   * An example command line for training a new parser:
   * <br>
   *  nohup java -mx6g edu.stanford.nlp.parser.dvparser.DVParser -cachedTrees /scr/nlp/data/dvparser/wsj/cached.wsj.train.simple.ser.gz -train -testTreebank  /afs/ir/data/linguistic-data/Treebank/3/parsed/mrg/wsj/22 2200-2219 -debugOutputFrequency 400 -nofilter -trainingThreads 5 -parser /u/nlp/data/lexparser/wsjPCFG.nocompact.simple.ser.gz -trainingIterations 40 -batchSize 25 -model /scr/nlp/data/dvparser/wsj/wsj.combine.v2.ser.gz -unkWord "*UNK*" -dvCombineCategories &gt; /scr/nlp/data/dvparser/wsj/wsj.combine.v2.out 2&gt;&amp;1 &amp;
   */
  public static void main(String[] args)
    throws IOException, ClassNotFoundException
  {
    if (args.length == 0) {
      help();
      System.exit(2);
    }

    log.info("Running DVParser with arguments:");
    for (String arg : args) {
      log.info("  " + arg);
    }
    log.info();

    String parserPath = null;
    String trainTreebankPath = null;
    FileFilter trainTreebankFilter = null;
    String cachedTrainTreesPath = null;

    boolean runGradientCheck = false;
    boolean runTraining = false;

    String testTreebankPath = null;
    FileFilter testTreebankFilter = null;

    String initialModelPath = null;
    String modelPath = null;

    boolean filter = true;

    String resultsRecordPath = null;

    List<String> unusedArgs = new ArrayList<>();

    // These parameters can be null or 0 if the model was not
    // serialized with the new parameters.  Setting the options at the
    // command line will override these defaults.
    // TODO: if/when we integrate back into the main branch and
    // rebuild models, we can get rid of this
    List<String> argsWithDefaults = new ArrayList<>(Arrays.asList(new String[]{
            "-wordVectorFile", Options.LexOptions.DEFAULT_WORD_VECTOR_FILE,
            "-dvKBest", Integer.toString(TrainOptions.DEFAULT_K_BEST),
            "-batchSize", Integer.toString(TrainOptions.DEFAULT_BATCH_SIZE),
            "-trainingIterations", Integer.toString(TrainOptions.DEFAULT_TRAINING_ITERATIONS),
            "-qnIterationsPerBatch", Integer.toString(TrainOptions.DEFAULT_QN_ITERATIONS_PER_BATCH),
            "-regCost", Double.toString(TrainOptions.DEFAULT_REGCOST),
            "-learningRate", Double.toString(TrainOptions.DEFAULT_LEARNING_RATE),
            "-deltaMargin", Double.toString(TrainOptions.DEFAULT_DELTA_MARGIN),
            "-unknownNumberVector",
            "-unknownDashedWordVectors",
            "-unknownCapsVector",
            "-unknownchinesepercentvector",
            "-unknownchinesenumbervector",
            "-unknownchineseyearvector",
            "-unkWord", "*UNK*",
            "-transformMatrixType", "DIAGONAL",
            "-scalingForInit", Double.toString(TrainOptions.DEFAULT_SCALING_FOR_INIT),
            "-trainWordVectors",
    }));
    argsWithDefaults.addAll(Arrays.asList(args));
    args = argsWithDefaults.toArray(new String[argsWithDefaults.size()]);

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-parser")) {
        parserPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-testTreebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-testTreebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        testTreebankPath = treebankDescription.first();
        testTreebankFilter = treebankDescription.second();
      } else if (args[argIndex].equalsIgnoreCase("-treebank")) {
        Pair<String, FileFilter> treebankDescription = ArgUtils.getTreebankDescription(args, argIndex, "-treebank");
        argIndex = argIndex + ArgUtils.numSubArgs(args, argIndex) + 1;
        trainTreebankPath = treebankDescription.first();
        trainTreebankFilter = treebankDescription.second();
      } else if (args[argIndex].equalsIgnoreCase("-cachedTrees")) {
        cachedTrainTreesPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-runGradientCheck")) {
        runGradientCheck = true;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-train")) {
        runTraining = true;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-model")) {
        modelPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-nofilter")) {
        filter = false;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-continueTraining")) {
        runTraining = true;
        filter = false;
        initialModelPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-resultsRecord")) {
        resultsRecordPath = args[argIndex + 1];
        argIndex += 2;
      } else {
        unusedArgs.add(args[argIndex++]);
      }
    }

    if (parserPath == null && modelPath == null) {
      throw new IllegalArgumentException("Must supply either a base parser model with -parser or a serialized DVParser with -model");
    }

    if (!runTraining && modelPath == null && !runGradientCheck) {
      throw new IllegalArgumentException("Need to either train a new model, run the gradient check or specify a model to load with -model");
    }

    String[] newArgs = unusedArgs.toArray(new String[unusedArgs.size()]);
    DVParser dvparser = null;
    LexicalizedParser lexparser = null;
    if (initialModelPath != null) {
      lexparser = LexicalizedParser.loadModel(initialModelPath, newArgs);
      DVModel model = getModelFromLexicalizedParser(lexparser);
      dvparser = new DVParser(model, lexparser);
    } else if (runTraining || runGradientCheck) {
      lexparser = LexicalizedParser.loadModel(parserPath, newArgs);
      dvparser = new DVParser(lexparser);
    } else if (modelPath != null) {
      lexparser = LexicalizedParser.loadModel(modelPath, newArgs);
      DVModel model = getModelFromLexicalizedParser(lexparser);
      dvparser = new DVParser(model, lexparser);
    }

    List<Tree> trainSentences = new ArrayList<>();
    IdentityHashMap<Tree, byte[]> trainCompressedParses = Generics.newIdentityHashMap();

    if (cachedTrainTreesPath != null) {
      for (String path : cachedTrainTreesPath.split(",")) {
        List<Pair<Tree, byte[]>> cache = IOUtils.readObjectFromFile(path);

        for (Pair<Tree, byte[]> pair : cache) {
          trainSentences.add(pair.first());
          trainCompressedParses.put(pair.first(), pair.second());
        }

        log.info("Read in " + cache.size() + " trees from " + path);
      }
    }

    if (trainTreebankPath != null) {
      // TODO: make the transformer a member of the model?
      TreeTransformer transformer = buildTrainTransformer(dvparser.getOp());

      Treebank treebank = dvparser.getOp().tlpParams.memoryTreebank();;
      treebank.loadPath(trainTreebankPath, trainTreebankFilter);
      treebank = treebank.transform(transformer);
      log.info("Read in " + treebank.size() + " trees from " + trainTreebankPath);

      CacheParseHypotheses cacher = new CacheParseHypotheses(dvparser.parser);
      CacheParseHypotheses.CacheProcessor processor = new CacheParseHypotheses.CacheProcessor(cacher, lexparser, dvparser.op.trainOptions.dvKBest, transformer);
      for (Tree tree : treebank) {
        trainSentences.add(tree);
        trainCompressedParses.put(tree, processor.process(tree).second);
        //System.out.println(tree);
      }

      log.info("Finished parsing " + treebank.size() + " trees, getting " + dvparser.op.trainOptions.dvKBest + " hypotheses each");
    }

    if ((runTraining || runGradientCheck) && filter) {
      log.info("Filtering rules for the given training set");
      dvparser.dvModel.setRulesForTrainingSet(trainSentences, trainCompressedParses);
      log.info("Done filtering rules; " + dvparser.dvModel.numBinaryMatrices + " binary matrices, " + dvparser.dvModel.numUnaryMatrices + " unary matrices, " + dvparser.dvModel.wordVectors.size() + " word vectors");
    }

    //dvparser.dvModel.printAllMatrices();

    Treebank testTreebank = null;
    if (testTreebankPath != null) {
      log.info("Reading in trees from " + testTreebankPath);
      if (testTreebankFilter != null) {
        log.info("Filtering on " + testTreebankFilter);
      }
      testTreebank = dvparser.getOp().tlpParams.memoryTreebank();;
      testTreebank.loadPath(testTreebankPath, testTreebankFilter);
      log.info("Read in " + testTreebank.size() + " trees for testing");
    }

//    runGradientCheck= true;
    if (runGradientCheck) {
      log.info("Running gradient check on " + trainSentences.size() + " trees");
      dvparser.runGradientCheck(trainSentences, trainCompressedParses);
    }

    if (runTraining) {
      log.info("Training the RNN parser");
      log.info("Current train options: " + dvparser.getOp().trainOptions);
      dvparser.train(trainSentences, trainCompressedParses, testTreebank, modelPath, resultsRecordPath);
      if (modelPath != null) {
        dvparser.saveModel(modelPath);
      }
    }

    if (testTreebankPath != null) {
      EvaluateTreebank evaluator = new EvaluateTreebank(dvparser.attachModelToLexicalizedParser());
      evaluator.testOnTreebank(testTreebank);
    }


    log.info("Successfully ran DVParser");
  }

}
