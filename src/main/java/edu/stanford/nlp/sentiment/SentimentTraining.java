package edu.stanford.nlp.sentiment;

import java.io.File;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.logging.Redwood;

public class SentimentTraining  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SentimentTraining.class);

  private static final NumberFormat NF = new DecimalFormat("0.00");
  private static final NumberFormat FILENAME = new DecimalFormat("0000");

  private SentimentTraining() {} // static methods

  private static void executeOneTrainingBatch(SentimentModel model, List<Tree> trainingBatch, double[] sumGradSquare) {
    SentimentCostAndGradient gcFunc = new SentimentCostAndGradient(model, trainingBatch);
    double[] theta = model.paramsToVector();

    // AdaGrad
    double eps = 1e-3;
    // TODO: do we want to iterate multiple times per batch?
    double[] gradf = gcFunc.derivativeAt(theta);
    double currCost = gcFunc.valueAt(theta);
    log.info("batch cost: " + currCost);
    for (int feature = 0; feature<gradf.length; feature++ ) {
      sumGradSquare[feature] = sumGradSquare[feature] + gradf[feature]*gradf[feature];
      theta[feature] = theta[feature] - (model.op.trainOptions.learningRate * gradf[feature]/(Math.sqrt(sumGradSquare[feature])+eps));
    }

    model.vectorToParams(theta);
  }

  public static void train(SentimentModel model, String modelPath, List<Tree> trainingTrees, List<Tree> devTrees) {
    Timing timing = new Timing();
    long maxTrainTimeMillis = model.op.trainOptions.maxTrainTimeSeconds * 1000;
    int debugCycle = 0;
    // double bestAccuracy = 0.0;

    // train using AdaGrad (seemed to work best during the dvparser project)
    double[] sumGradSquare = new double[model.totalParamSize()];
    Arrays.fill(sumGradSquare, model.op.trainOptions.initialAdagradWeight);

    int numBatches = trainingTrees.size() / model.op.trainOptions.batchSize + 1;
    log.info("Training on " + trainingTrees.size() + " trees in " + numBatches + " batches");
    log.info("Times through each training batch: " + model.op.trainOptions.epochs);
    for (int epoch = 0; epoch < model.op.trainOptions.epochs; ++epoch) {
      log.info("======================================");
      log.info("Starting epoch " + epoch);
      if (epoch > 0 && model.op.trainOptions.adagradResetFrequency > 0 &&
          (epoch % model.op.trainOptions.adagradResetFrequency == 0)) {
        log.info("Resetting adagrad weights to " + model.op.trainOptions.initialAdagradWeight);
        Arrays.fill(sumGradSquare, model.op.trainOptions.initialAdagradWeight);
      }

      List<Tree> shuffledSentences = Generics.newArrayList(trainingTrees);
      if (model.op.trainOptions.shuffleMatrices) {
        Collections.shuffle(shuffledSentences, model.rand);
      }
      for (int batch = 0; batch < numBatches; ++batch) {
        log.info("======================================");
        log.info("Epoch " + epoch + " batch " + batch);

        // Each batch will be of the specified batch size, except the
        // last batch will include any leftover trees at the end of
        // the list
        int startTree = batch * model.op.trainOptions.batchSize;
        int endTree = (batch + 1) * model.op.trainOptions.batchSize;
        if (endTree > shuffledSentences.size()) {
          endTree = shuffledSentences.size();
        }

        executeOneTrainingBatch(model, shuffledSentences.subList(startTree, endTree), sumGradSquare);

        long totalElapsed = timing.report();
        log.info("Finished epoch " + epoch + " batch " + batch + "; total training time " + totalElapsed + " ms");

        if (maxTrainTimeMillis > 0 && totalElapsed > maxTrainTimeMillis) {
          // no need to debug output, we're done now
          break;
        }

        if (batch == (numBatches - 1) && model.op.trainOptions.debugOutputEpochs > 0 && (epoch + 1) % model.op.trainOptions.debugOutputEpochs == 0) {
          double score = 0.0;
          if (devTrees != null) {
            Evaluate eval = new Evaluate(model);
            eval.eval(devTrees);
            eval.printSummary();
            score = eval.exactNodeAccuracy() * 100.0;
          }

          // output an intermediate model
          if (modelPath != null) {
            String tempPath;
            if (modelPath.endsWith(".ser.gz")) {
              tempPath = modelPath.substring(0, modelPath.length() - 7) + "-" + FILENAME.format(debugCycle) + "-" + NF.format(score) + ".ser.gz";
            } else if (modelPath.endsWith(".gz")) {
              tempPath = modelPath.substring(0, modelPath.length() - 3) + "-" + FILENAME.format(debugCycle) + "-" + NF.format(score) + ".gz";
            } else {
              tempPath = modelPath.substring(0, modelPath.length() - 3) + "-" + FILENAME.format(debugCycle) + "-" + NF.format(score);
            }
            model.saveSerialized(tempPath);
          }

          ++debugCycle;
        }
      }
      long totalElapsed = timing.report();

      if (maxTrainTimeMillis > 0 && totalElapsed > maxTrainTimeMillis) {
        log.info("Max training time exceeded, exiting");
        break;
      }
    }
  }

  public static boolean runGradientCheck(SentimentModel model, List<Tree> trees) {
    SentimentCostAndGradient gcFunc = new SentimentCostAndGradient(model, trees);
    return gcFunc.gradientCheck(model.totalParamSize(), 50, model.paramsToVector());
  }

  /** Trains a sentiment model.
   *  The -trainPath argument points to a labeled sentiment treebank.
   *  The trees in this data will be used to train the model parameters (also to seed the model vocabulary).
   *  The -devPath argument points to a second labeled sentiment treebank.
   *  The trees in this data will be used to periodically evaluate the performance of the model.
   *  We won't train on this data; it will only be used to test how well the model generalizes to unseen data.
   *  The -model argument specifies where to save the learned sentiment model.
   *
   *  @param args Command line arguments
   */
  public static void main(String[] args) {
    RNNOptions op = new RNNOptions();

    String trainPath = "sentimentTreesDebug.txt";
    String devPath = null;

    boolean runGradientCheck = false;
    boolean runTraining = false;

    boolean filterUnknown = false;

    String modelPath = null;

    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-train")) {
        runTraining = true;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-gradientcheck")) {
        runGradientCheck = true;
        argIndex++;
      } else if (args[argIndex].equalsIgnoreCase("-trainpath")) {
        trainPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-devpath")) {
        devPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-model")) {
        modelPath = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-filterUnknown")) {
        filterUnknown = true;
        argIndex++;
      } else {
        int newArgIndex = op.setOption(args, argIndex);
        if (newArgIndex == argIndex) {
          throw new IllegalArgumentException("Unknown argument " + args[argIndex]);
        }
        argIndex = newArgIndex;
      }
    }

    // read in the trees
    List<Tree> trainingTrees = SentimentUtils.readTreesWithGoldLabels(trainPath);
    log.info("Read in " + trainingTrees.size() + " training trees");
    if (filterUnknown) {
      trainingTrees = SentimentUtils.filterUnknownRoots(trainingTrees);
      log.info("Filtered training trees: " + trainingTrees.size());
    }

    List<Tree> devTrees = null;
    if (devPath != null) {
      devTrees = SentimentUtils.readTreesWithGoldLabels(devPath);
      log.info("Read in " + devTrees.size() + " dev trees");
      if (filterUnknown) {
        devTrees = SentimentUtils.filterUnknownRoots(devTrees);
        log.info("Filtered dev trees: " + devTrees.size());
      }
    }

    // TODO: binarize the trees, then collapse the unary chains.
    // Collapsed unary chains always have the label of the top node in
    // the chain
    // Note: the sentiment training data already has this done.
    // However, when we handle trees given to us from the Stanford Parser,
    // we will have to perform this step

    // build an uninitialized SentimentModel from the binary productions
    log.info("Sentiment model options:\n" + op);
    SentimentModel model = new SentimentModel(op, trainingTrees);

    if (op.trainOptions.initialMatrixLogPath != null) {
      StringUtils.printToFile(new File(op.trainOptions.initialMatrixLogPath), model.toString(), false, false, "utf-8");
    }

    // TODO: need to handle unk rules somehow... at test time the tree
    // structures might have something that we never saw at training
    // time.  for example, we could put a threshold on all of the
    // rules at training time and anything that doesn't meet that
    // threshold goes into the unk.  perhaps we could also use some
    // component of the accepted training rules to build up the "unk"
    // parameter in case there are no rules that don't meet the
    // threshold

    if (runGradientCheck) {
      runGradientCheck(model, trainingTrees);
    }

    if (runTraining) {
      train(model, modelPath, trainingTrees, devTrees);
      model.saveSerialized(modelPath);
    }
  }

}
