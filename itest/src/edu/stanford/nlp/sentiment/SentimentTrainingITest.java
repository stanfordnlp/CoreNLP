package edu.stanford.nlp.sentiment;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import edu.stanford.nlp.trees.Tree;

/**
 * Tests that the gradient check for the sentiment tool passes.
 */
public class SentimentTrainingITest {

  private static final String TRAIN_PATH = "data/edu/stanford/nlp/sentiment/onesent.txt";

  @Test
  public void testGradientCheck() {
    List<Tree> trainingTrees = SentimentUtils.readTreesWithGoldLabels(TRAIN_PATH);
    RNNOptions op = new RNNOptions();
    op.numHid = 5;
    SentimentModel model = new SentimentModel(op, trainingTrees);
    Assert.assertTrue("Gradient check failed with random seed of " + op.randomSeed,
            SentimentTraining.runGradientCheck(model, trainingTrees));
  }

  /**
   * Because the regularizations are typically set to be 0.001 of the
   * total cost, it is important to test those gradients with the reg
   * values turned up a lot.
   */
  @Test
  public void testRegularizationGradientCheck() {
    List<Tree> trainingTrees = SentimentUtils.readTreesWithGoldLabels(TRAIN_PATH);
    RNNOptions op = new RNNOptions();
    op.numHid = 5;
    op.trainOptions.regTransformMatrix = 10.0;
    op.trainOptions.regTransformTensor = 10.0;
    op.trainOptions.regClassification = 10.0;
    op.trainOptions.regWordVector = 10.0;
    SentimentModel model = new SentimentModel(op, trainingTrees);
    Assert.assertTrue("Gradient check failed with random seed of " + op.randomSeed,
            SentimentTraining.runGradientCheck(model, trainingTrees));
  }

}

