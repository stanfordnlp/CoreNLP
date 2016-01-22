package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.sequences.SeqClassifierFlags;

/**
 * @author Mengqiu Wang
 */
public class NonLinearSecondOrderCliquePotentialFunction implements CliquePotentialFunction {

  private final double[][] inputLayerWeights4Edge; // first index is number of hidden units in layer one, second index is the input feature indices
  private final double[][] outputLayerWeights4Edge; // first index is the output class, second index is the number of hidden units
  private final double[][] inputLayerWeights; // first index is number of hidden units in layer one, second index is the input feature indices
  private final double[][] outputLayerWeights; // first index is the output class, second index is the number of hidden units
  private double[] layerOneCache, hiddenLayerCache;
  private double[] layerOneCache4Edge, hiddenLayerCache4Edge;
  private final SeqClassifierFlags flags;

  public NonLinearSecondOrderCliquePotentialFunction(double[][] inputLayerWeights4Edge, double[][] outputLayerWeights4Edge, double[][] inputLayerWeights, double[][] outputLayerWeights, SeqClassifierFlags flags) {
    this.inputLayerWeights4Edge = inputLayerWeights4Edge;
    this.outputLayerWeights4Edge = outputLayerWeights4Edge;
    this.inputLayerWeights = inputLayerWeights;
    this.outputLayerWeights = outputLayerWeights;
    this.flags = flags;
  }

  public double[] hiddenLayerOutput(double[][] inputLayerWeights, int[] nodeCliqueFeatures, SeqClassifierFlags aFlag, double[] featureVal, int cliqueSize) {
    double[] layerCache = null;
    double[] hlCache = null;
    int layerOneSize = inputLayerWeights.length;
    if (cliqueSize > 1) {
      if (layerOneCache4Edge == null || layerOneSize != layerOneCache4Edge.length)
        layerOneCache4Edge = new double[layerOneSize];
      layerCache = layerOneCache4Edge;
    } else {
      if (layerOneCache == null || layerOneSize != layerOneCache.length)
        layerOneCache = new double[layerOneSize];
      layerCache = layerOneCache;
    }
    for (int i = 0; i < layerOneSize; i++) {
      double[] ws = inputLayerWeights[i];
      double lOneW = 0;
      double dotProd = 0;
      for (int m = 0; m < nodeCliqueFeatures.length; m++) {
        dotProd = ws[nodeCliqueFeatures[m]];
        if (featureVal != null)
          dotProd *= featureVal[m];
        lOneW += dotProd;
      }
      layerCache[i] = lOneW;
    }
    if (!aFlag.useHiddenLayer)
      return layerCache;

    // transform layer one through hidden
    if (cliqueSize > 1) {
      if (hiddenLayerCache4Edge == null || layerOneSize != hiddenLayerCache4Edge.length)
        hiddenLayerCache4Edge = new double[layerOneSize];
      hlCache = hiddenLayerCache4Edge;
    } else {
      if (hiddenLayerCache == null || layerOneSize != hiddenLayerCache.length)
        hiddenLayerCache = new double[layerOneSize];
      hlCache = hiddenLayerCache;
    }
    for (int i = 0; i < layerOneSize; i++) {
      if (aFlag.useSigmoid) {
        hlCache[i] = sigmoid(layerCache[i]);
      } else {
        hlCache[i] = Math.tanh(layerCache[i]);
      }
    }
    return hlCache;
  }
  private static double sigmoid(double x) {
    return 1 / (1 + Math.exp(-x));
  }

  @Override
  public double computeCliquePotential(int cliqueSize, int labelIndex,
      int[] cliqueFeatures, double[] featureVal, int posInSent) {
    double output = 0.0;
    double[][] inputWeights, outputWeights = null;
    if (cliqueSize > 1) {
      inputWeights = inputLayerWeights4Edge;
      outputWeights = outputLayerWeights4Edge;
    } else {
      inputWeights = inputLayerWeights;
      outputWeights = outputLayerWeights;
    }
    double[] hiddenLayer = hiddenLayerOutput(inputWeights, cliqueFeatures, flags, featureVal, cliqueSize);

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
