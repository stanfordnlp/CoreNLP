package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.Index;

/**
 * @author Mengqiu Wang
 */

public class NonLinearSecondOrderCliquePotentialFunction implements CliquePotentialFunction {

  double[][] inputLayerWeights4Edge; // first index is number of hidden units in layer one, second index is the input feature indices
  double[][] outputLayerWeights4Edge; // first index is the output class, second index is the number of hidden units
  double[][] inputLayerWeights; // first index is number of hidden units in layer one, second index is the input feature indices
  double[][] outputLayerWeights; // first index is the output class, second index is the number of hidden units
  SeqClassifierFlags flags;

  public NonLinearSecondOrderCliquePotentialFunction(double[][] inputLayerWeights4Edge, double[][] outputLayerWeights4Edge, double[][] inputLayerWeights, double[][] outputLayerWeights, SeqClassifierFlags flags) {
    this.inputLayerWeights4Edge = inputLayerWeights4Edge;
    this.outputLayerWeights4Edge = outputLayerWeights4Edge;
    this.inputLayerWeights = inputLayerWeights;
    this.outputLayerWeights = outputLayerWeights;
    this.flags = flags;
  }

  @Override
  public double computeCliquePotential(int cliqueSize, int labelIndex, int[] cliqueFeatures, double[] featureVal) {
    double output = 0.0;
    double[][] inputWeights, outputWeights = null;
    if (cliqueSize > 1) {
      inputWeights = inputLayerWeights4Edge;
      outputWeights = outputLayerWeights4Edge;
    } else {
      inputWeights = inputLayerWeights;
      outputWeights = outputLayerWeights;
    }

    double[] hiddenLayer = NonLinearCliquePotentialFunction.hiddenLayerOutput(inputWeights, cliqueFeatures, flags, featureVal);
    int outputLayerSize = inputWeights.length / outputWeights[0].length;

    // transform the hidden layer to output layer through linear transformation
    if (flags.useOutputLayer) {
      double[] outputWs = null;
      if (flags.tieOutputLayer) {
        outputWs = outputWeights[0];
      } else {
        outputWs = outputWeights[labelIndex];
      }
      if (flags.softmaxOutputLayer) {
        outputWs = ArrayMath.softmax(outputWs);
      }
      for (int i = 0; i < inputWeights.length; i++) {
        if (flags.sparseOutputLayer || flags.tieOutputLayer) {
          if (i % outputLayerSize == labelIndex) {
            output += outputWs[ i / outputLayerSize ] * hiddenLayer[i];
          }
        } else {
          output += outputWs[i] * hiddenLayer[i];
        }
      }
    } else {
      output = hiddenLayer[labelIndex];
    }
    
    return output;
  }
}
