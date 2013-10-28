package edu.stanford.nlp.parser.dvparser;

import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.parser.lexparser.ArgUtils;
import edu.stanford.nlp.parser.lexparser.EvaluateTreebank;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.ParserQuery;
import edu.stanford.nlp.parser.lexparser.Options;
import edu.stanford.nlp.parser.lexparser.TrainOptions;
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
public class DVParser {
  DVModel dvModel;
  LexicalizedParser parser;
  Options op;

  public Options getOp() {
    return op;
  }

  DVModel getDVModel() {
    return dvModel;
  }

  private static final NumberFormat NF = new DecimalFormat("0.00");
  private static final NumberFormat FILENAME = new DecimalFormat("0000");

  static public List<Tree> getTopParsesForOneTree(LexicalizedParser parser, int dvKBest, Tree tree,
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
      System.err.println("Failed to use the given parser to reparse sentence \"" + sentence + "\"");
      return null;
    }
    List<Tree> parses = new ArrayList<Tree>();
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
    IdentityHashMap<Tree, List<Tree>> topParses = new IdentityHashMap<Tree, List<Tree>>();
    for (Tree tree : trees) {
      List<Tree> parses = getTopParsesForOneTree(parser, op.trainOptions.dvKBest, tree, transformer);
      topParses.put(tree, parses);
      if (outputUpdates && topParses.size() % 10 == 0) {
        System.err.println("Processed " + topParses.size() + " trees");
      }
    }
    if (outputUpdates) {
      System.err.println("Finished processing " + topParses.size() + " trees");
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
    long nextDebugCycle = op.trainOptions.debugOutputSeconds * 1000;
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
    
    int numBatches = sentences.size() / op.trainOptions.dvBatchSize + 1;
    System.err.println("Training on " + sentences.size() + " trees in " + numBatches + " batches");
    System.err.println("Times through each training batch: " + op.trainOptions.dvIterations);
    System.err.println("QN iterations per batch: " + op.trainOptions.qnIterationsPerBatch);
    for (int iter = 0; iter < op.trainOptions.dvIterations; ++iter) {
      List<Tree> shuffledSentences = new ArrayList<Tree>(sentences);
      Collections.shuffle(shuffledSentences, dvModel.rand);
      for (int batch = 0; batch < numBatches; ++batch) {
        // This did not help performance
        //System.err.println("Setting AdaGrad's sum of squares to 1...");
        //Arrays.fill(sumGradSquare, 1.0);

        System.err.println("======================================");
        System.err.println("Iteration " + iter + " batch " + batch);
      
        // Each batch will be of the specified batch size, except the
        // last batch will include any leftover trees at the end of
        // the list
        int startTree = batch * op.trainOptions.dvBatchSize;
        int endTree = (batch + 1) * op.trainOptions.dvBatchSize;
        if (endTree + op.trainOptions.dvBatchSize > shuffledSentences.size()) {
          endTree = shuffledSentences.size();
        }
        
        executeOneTrainingBatch(shuffledSentences.subList(startTree, endTree), compressedParses, sumGradSquare);

        long totalElapsed = timing.report();
        System.err.println("Finished iteration " + iter + " batch " + batch + "; total training time " + totalElapsed + " ms");

        if (maxTrainTimeMillis > 0 && totalElapsed > maxTrainTimeMillis) {
          // no need to debug output, we're done now
          break;
        }

        if (nextDebugCycle > 0 && totalElapsed > nextDebugCycle) {
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
            System.err.println("Best label f1 on dev set so far: " + NF.format(bestLabelF1));
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
          System.err.println(statusLine);
          if (resultsRecordPath != null) {
            FileWriter fout = new FileWriter(resultsRecordPath, true); // append
            fout.write(statusLine);
            fout.write("\n");
            fout.close();
          }

          ++debugCycle;
          nextDebugCycle = timing.report() + op.trainOptions.debugOutputSeconds * 1000;
        }
      }
      long totalElapsed = timing.report();
      
      if (maxTrainTimeMillis > 0 && totalElapsed > maxTrainTimeMillis) {
        // no need to debug output, we're done now
        System.err.println("Max training time exceeded, exiting");
        break;
      }
    }
  }

  static final int MINIMIZER = 3;

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
    }
    case 2:{
    	//Minimizer smd = new SGDMinimizer();    	double tol = 1e-4;    	theta = smd.minimize(gcFunc,tol,theta,op.trainOptions.qnIterationsPerBatch);
    	double lastCost = 0, currCost = 0;
    	boolean firstTime = true;
    	for(int i = 0; i < op.trainOptions.qnIterationsPerBatch; i++){
    		//gcFunc.calculate(theta);
    		double[] grad = gcFunc.derivativeAt(theta);
    		currCost = gcFunc.valueAt(theta);
    		System.err.println("batch cost: " + currCost);
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
    }
    case 3:{
    	// AdaGrad
    	double eps = 1e-3;
    	double currCost = 0;
    	for(int i = 0; i < op.trainOptions.qnIterationsPerBatch; i++){
    		double[] gradf = gcFunc.derivativeAt(theta);
    		currCost = gcFunc.valueAt(theta);
    		System.err.println("batch cost: " + currCost);
    	    for (int feature =0; feature<gradf.length;feature++ ) {
    	    	sumGradSquare[feature] = sumGradSquare[feature] + gradf[feature]*gradf[feature];
    	        theta[feature] = theta[feature] - (op.trainOptions.learningRate * gradf[feature]/(Math.sqrt(sumGradSquare[feature])+eps));
    	      }    		
    	} 
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

    if (op.trainOptions.dvSeed == 0) {
      op.trainOptions.dvSeed = (new Random()).nextLong();
      System.err.println("Random seed not set, using randomly chosen seed of " + op.trainOptions.dvSeed);
    } else {
      System.err.println("Random seed set to " + op.trainOptions.dvSeed);
    }

    System.err.println("Word vector file: " + op.lexOptions.wordVectorFile);
    System.err.println("Size of word vectors: " + op.lexOptions.numHid);
    System.err.println("Number of hypothesis trees to train against: " + op.trainOptions.dvKBest);
    System.err.println("Number of trees in one batch: " + op.trainOptions.dvBatchSize);
    System.err.println("Number of iterations of trees: " + op.trainOptions.dvIterations);
    System.err.println("Number of qn iterations per batch: " + op.trainOptions.qnIterationsPerBatch);
    System.err.println("Learning rate: " + op.trainOptions.learningRate);
    System.err.println("Delta margin: " + op.trainOptions.deltaMargin);
    System.err.println("regCost: " + op.trainOptions.regCost);
    System.err.println("Using unknown word vector for numbers: " + op.trainOptions.unknownNumberVector);
    System.err.println("Using unknown dashed word vector heuristics: " + op.trainOptions.unknownDashedWordVectors);
    System.err.println("Using unknown word vector for capitalized words: " + op.trainOptions.unknownCapsVector);
    System.err.println("Using unknown number vector for Chinese words: " + op.trainOptions.unknownChineseNumberVector);
    System.err.println("Using unknown year vector for Chinese words: " + op.trainOptions.unknownChineseYearVector);
    System.err.println("Using unknown percent vector for Chinese words: " + op.trainOptions.unknownChinesePercentVector);
    System.err.println("Initial matrices scaled by: " + op.trainOptions.scalingForInit);
    System.err.println("Training will use " + op.trainOptions.trainingThreads + " thread(s)");
    System.err.println("Context words are " + ((op.trainOptions.useContextWords) ? "on" : "off"));
    System.err.println("Model will " + ((op.trainOptions.dvSimplifiedModel) ? "" : "not ") + "be simplified");

    this.dvModel = new DVModel(op, parser.stateIndex, parser.ug, parser.bg);

    if (dvModel.unaryTransform.size() != dvModel.unaryScore.size()) {
      throw new AssertionError("Unary transform and score size not the same");
    }
    if (dvModel.binaryTransform.size() != dvModel.binaryScore.size()) {
      throw new AssertionError("Binary transform and score size not the same");
    }
  }
  
  public boolean runGradientCheck(List<Tree> sentences, IdentityHashMap<Tree, byte[]> compressedParses) {
    System.err.println("Gradient check: converting " + sentences.size() + " compressed trees");
    IdentityHashMap<Tree, List<Tree>> topParses = CacheParseHypotheses.convertToTrees(sentences, compressedParses, op.trainOptions.trainingThreads);
    System.err.println("Done converting trees");
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
    System.err.println("Saving serialized model to " + filename);
    LexicalizedParser newParser = attachModelToLexicalizedParser();
    newParser.saveParserToSerialized(filename);
    System.err.println("... done");
  }

  public static DVParser loadModel(String filename, String[] args) {
    System.err.println("Loading serialized model from " + filename);
    DVParser dvparser;
    try {
      dvparser = IOUtils.readObjectFromFile(filename);
      dvparser.op.setOptions(args);
    } catch (IOException e) {
      throw new RuntimeIOException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeIOException(e);
    }
    System.err.println("... done");
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
    System.err.println("Options supplied by this file:");
    System.err.println("  -model <name>: When training, the name of the model to save.  Otherwise, the name of the model to load.");
    System.err.println("  -parser <name>: When training, the LexicalizedParser to use as the base model.");
    System.err.println("  -cachedTrees <name>: The name of the file containing a treebank with cached parses.  See CacheParseHypotheses.java");
    System.err.println("  -treebank <name> [filter]: A treebank to use instead of cachedTrees.  Trees will be reparsed.  Slow.");
    System.err.println("  -testTreebank <name> [filter]: A treebank for testing the model.");
    System.err.println("  -train: Run training over the treebank, testing on the testTreebank.");
    System.err.println("  -continueTraining <name>: The name of a file to continue training.");
    System.err.println("  -nofilter: Rules for the parser will not be filtered based on the training treebank.");
    System.err.println("  -runGradientCheck: Run a gradient check.");
    System.err.println("  -resultsRecord: A file for recording info on intermediate results");
    System.err.println();
    System.err.println("Options overlapping the parser:");
    System.err.println("  -trainingThreads <int>: How many threads to use when training.");
    System.err.println("  -dvKBest <int>: How many hypotheses to use from the underlying parser.");
    System.err.println("  -dvIterations <int>: When training, how many times to go through the train set.");
    System.err.println("  -regCost <double>: How large of a cost to put on regularization.");
    System.err.println("  -dvBatchSize <int>: How many trees to use in each batch of the training.");
    System.err.println("  -qnIterationsPerBatch <int>: How many steps to take per batch.");
    System.err.println("  -qnEstimates <int>: Parameter for qn optimization.");
    System.err.println("  -qnTolerance <double>: Tolerance for early exit when optimizing a batch.");
    System.err.println("  -debugOutputSeconds <int>: How frequently to score a model when training and write out intermediate models.");
    System.err.println("  -maxTrainTimeSeconds <int>: How long to train before terminating.");
    System.err.println("  -dvSeed <long>: A starting point for the random number generator.  Setting this should lead to repeatable results, even taking into account randomness.  Otherwise, a new random seed will be picked.");
    System.err.println("  -wordVectorFile <name>: A filename to load word vectors from.");
    System.err.println("  -numHid: The size of the matrices.  In most circumstances, should be set to the size of the word vectors.");
    System.err.println("  -learningRate: The rate of optimization when training");
    System.err.println("  -deltaMargin: How much we punish trees for being incorrect when training");
    System.err.println("  -(no)unknownNumberVector: Whether or not to use a word vector for unknown numbers");
    System.err.println("  -(no)unknownDashedWordVectors: Whether or not to split unknown dashed words");
    System.err.println("  -(no)unknownCapsVector: Whether or not to use a word vector for unknown words with capitals");
    System.err.println("  -dvSimplifiedModel: Use a greatly dumbed down DVModel");
    System.err.println("  -scalingForInit: How much to scale matrices when creating a new DVModel");
    System.err.println("  -lpWeight: A weight to give the original LexicalizedParser when testing (0.2 seems to work well)");
    System.err.println("  -unkWord: The vector representing unknown word in the word vectors file");
    System.err.println("  -transformMatrixType: A couple different methods for initializing transform matrices");
  }

  public static void main(String[] args) 
    throws IOException, ClassNotFoundException
  {
    if (args.length == 0) {
      help();
      System.exit(2);
    }

    System.err.println("Running DVParser with arguments:");
    for (int i = 0; i < args.length; ++i) {
      System.err.print("  " + args[i]);
    }
    System.err.println();

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

    List<String> unusedArgs = new ArrayList<String>();

    // These parameters can be null or 0 if the model was not
    // serialized with the new parameters.  Setting the options at the
    // command line will override these defaults.
    // TODO: if/when we integrate back into the main branch and
    // rebuild models, we can get rid of this
    List<String> argsWithDefaults = new ArrayList<String>(Arrays.asList(new String[] { 
          "-wordVectorFile", Options.LexOptions.DEFAULT_WORD_VECTOR_FILE,
          "-dvKBest", Integer.toString(TrainOptions.DEFAULT_K_BEST),
          "-dvBatchSize", Integer.toString(TrainOptions.DEFAULT_BATCH_SIZE),
          "-dvIterations", Integer.toString(TrainOptions.DEFAULT_DV_ITERATIONS),
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
          "-unkWord", "UNK",
          "-transformMatrixType", "DIAGONAL",
          "-scalingForInit", Double.toString(TrainOptions.DEFAULT_SCALING_FOR_INIT)
        } ));
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
      lexparser = LexicalizedParser.loadModel(initialModelPath, newArgs);
      DVModel model = getModelFromLexicalizedParser(lexparser);
      dvparser = new DVParser(model, lexparser);
    }

    List<Tree> trainSentences = new ArrayList<Tree>();
    IdentityHashMap<Tree, byte[]> trainCompressedParses = Generics.newIdentityHashMap();

    if (cachedTrainTreesPath != null) {
      for (String path : cachedTrainTreesPath.split(",")) {
        List<Pair<Tree, byte[]>> cache = IOUtils.readObjectFromFile(path);

        for (Pair<Tree, byte[]> pair : cache) {
          trainSentences.add(pair.first());
          trainCompressedParses.put(pair.first(), pair.second());
        }
        
        System.err.println("Read in " + cache.size() + " trees from " + path);
      }
    } 

    if (trainTreebankPath != null) {
      // TODO: make the transformer a member of the model?
      TreeTransformer transformer = buildTrainTransformer(dvparser.getOp());

      Treebank treebank = dvparser.getOp().tlpParams.memoryTreebank();;
      treebank.loadPath(trainTreebankPath, trainTreebankFilter);
      treebank = treebank.transform(transformer);
      System.err.println("Read in " + treebank.size() + " trees from " + trainTreebankPath);

      CacheParseHypotheses cacher = new CacheParseHypotheses(dvparser.parser);
      CacheParseHypotheses.CacheProcessor processor = new CacheParseHypotheses.CacheProcessor(cacher, lexparser, dvparser.op.trainOptions.dvKBest, transformer);
      for (Tree tree : treebank) {
        trainSentences.add(tree);
        trainCompressedParses.put(tree, processor.process(tree).second);
        //System.out.println(tree);
      }

      System.err.println("Finished parsing " + treebank.size() + " trees, getting " + dvparser.op.trainOptions.dvKBest + " hypotheses each");
    }

    if ((runTraining || runGradientCheck) && filter) {
      System.err.println("Filtering rules for the given training set");
      dvparser.dvModel.setRulesForTrainingSet(trainSentences, trainCompressedParses);
      System.err.println("Done filtering rules; " + dvparser.dvModel.numBinaryMatrices + " binary matrices, " + dvparser.dvModel.numUnaryMatrices + " unary matrices, " + dvparser.dvModel.wordVectors.size() + " word vectors");
    }

    //dvparser.dvModel.printAllMatrices();

    Treebank testTreebank = null;
    if (testTreebankPath != null) {
      System.err.println("Reading in trees from " + testTreebankPath);
      if (testTreebankFilter != null) {
        System.err.println("Filtering on " + testTreebankFilter);
      }
      testTreebank = dvparser.getOp().tlpParams.memoryTreebank();;
      testTreebank.loadPath(testTreebankPath, testTreebankFilter);
      System.err.println("Read in " + testTreebank.size() + " trees for testing");
    }
     
//    runGradientCheck= true;
    if (runGradientCheck) {
      System.err.println("Running gradient check on " + trainSentences.size() + " trees");
      dvparser.runGradientCheck(trainSentences, trainCompressedParses);
    } 

    if (runTraining) {
      System.err.println("Training the RNN parser");
      dvparser.train(trainSentences, trainCompressedParses, testTreebank, modelPath, resultsRecordPath);
      if (modelPath != null) {
        dvparser.saveModel(modelPath);
      }
    }

    if (testTreebankPath != null) {
      EvaluateTreebank evaluator = new EvaluateTreebank(dvparser.attachModelToLexicalizedParser());
      evaluator.testOnTreebank(testTreebank);
    }
    
    
    System.err.println("Successfully ran DVParser");
  }

  private static final long serialVersionUID = 1;
}
