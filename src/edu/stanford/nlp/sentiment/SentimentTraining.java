package edu.stanford.nlp.sentiment;

import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.util.TwoDimensionalSet;

public class SentimentTraining {

  public static void executeOneTrainingBatch(SentimentModel model, List<Tree> trainingBatch, double[] sumGradSquare) {
    SentimentCostAndGradient gcFunc = new SentimentCostAndGradient(trainingBatch, model);
    double[] theta = model.paramsToVector();

    // AdaGrad
    double eps = 1e-3;
    double currCost = 0;

    // TODO: do we want to iterate multiple times per batch?
    double[] gradf = gcFunc.derivativeAt(theta);
    currCost = gcFunc.valueAt(theta);
    System.err.println("batch cost: " + currCost);
    for (int feature = 0; feature<gradf.length;feature++ ) {
      sumGradSquare[feature] = sumGradSquare[feature] + gradf[feature]*gradf[feature];
      theta[feature] = theta[feature] - (op.trainOptions.learningRate * gradf[feature]/(Math.sqrt(sumGradSquare[feature])+eps));
    } 

    dvModel.vectorToParams(theta);
    
  }

  public static void train(SentimentModel model, List<Tree> trainingTrees, List<Tree> devTrees) {
    Timing timing = new Timing();
    // TODO: these training-specific options might be better served in
    // a smaller section of the Options
    // maxTrainTimeSeconds, debugOutputSeconds, iterations, batchSize
    long maxTrainTimeMillis = model.op.maxTrainTimeSeconds * 1000;
    long nextDebugCycle = model.op.debugOutputSeconds * 1000;
    int debugCycle = 0;
    double bestAccuracy = 0.0;

    // train using AdaGrad (seemed to work best during the dvparser project)
    double[] sumGradSquare = new double[model.totalParamSize()];
    Arrays.fill(sumGradSquare, 1.0);
    
    int numBatches = trainingTrees.size() / op.batchSize + 1;
    System.err.println("Training on " + trainingTrees.size() + " trees in " + numBatches + " batches");
    System.err.println("Times through each training batch: " + op.iterations);
    for (int iter = 0; iter < op.trainOptions.dvIterations; ++iter) {
      List<Tree> shuffledSentences = new ArrayList<Tree>(sentences);
      Collections.shuffle(shuffledSentences, dvModel.rand);
      for (int batch = 0; batch < numBatches; ++batch) {
        System.err.println("======================================");
        System.err.println("Iteration " + iter + " batch " + batch);
      
        // Each batch will be of the specified batch size, except the
        // last batch will include any leftover trees at the end of
        // the list
        int startTree = batch * op.batchSize;
        int endTree = (batch + 1) * op.batchSize;
        if (endTree + op.batchSize > shuffledSentences.size()) {
          endTree = shuffledSentences.size();
        }
        
        executeOneTrainingBatch(model, shuffledSentences.subList(startTree, endTree), sumGradSquare);

        long totalElapsed = timing.report();
        System.err.println("Finished iteration " + iter + " batch " + batch + "; total training time " + totalElapsed + " ms");

        if (maxTrainTimeMillis > 0 && totalElapsed > maxTrainTimeMillis) {
          // no need to debug output, we're done now
          break;
        }

        if (nextDebugCycle > 0 && totalElapsed > nextDebugCycle) {

          // TODO:
          // evaluate the test set on our current model
          // output an intermediate model
          // output a summary of what's happened so far

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

  public static boolean runGradientCheck(SentimentModel model, List<Tree> trees) {
    SentimentCostAndGradient gcFunc = new SentimentCostAndGradient(); // TODO: fill in data from trees
    return gcFunc.gradientCheck(1000, 50, model.paramsToVector());    
  }

  public static void main(String[] args) {
    // TODO: here we process the arguments
    Options op = new Options();

    // TODO
    // read in the trees
    List<Tree> trainingTrees = null;
    List<Tree> devTrees = null;

    // TODO
    // figure out what binary productions we have in these trees
    TwoDimensionalSet<String, String> binaryRules = new TwoDimensionalSet<String, String>();

    // build an unitialized SentimentModel from the binary productions
    SentimentModel model = new SentimentModel(op, binaryRules);

    // TODO: train the model
    
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
      train(model, trainingTrees, devTrees); // TODO: add parameters for places to store intermediate models
    }
  }
}
