package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.math.ArrayMath;
import edu.stanford.nlp.sequences.SeqClassifierFlags;

/**
 * @author Mengqiu Wang
 */
public class NonLinearCliquePotentialFunction implements CliquePotentialFunction {

  private final double[][] linearWeights;
  private final double[][] inputLayerWeights; // first index is number of hidden units in layer one, second index is the input feature indices
  private final double[][] outputLayerWeights; // first index is the output class, second index is the number of hidden units
  private final SeqClassifierFlags flags;
  private double[] layerOneCache, hiddenLayerCache;

  private static double sigmoid(double x) {
    return 1 / (1 + Math.exp(-x));
  }

  public NonLinearCliquePotentialFunction(double[][] linearWeights, double[][] inputLayerWeights, double[][] outputLayerWeights, SeqClassifierFlags flags) {
    this.linearWeights = linearWeights;
    this.inputLayerWeights = inputLayerWeights;
    this.outputLayerWeights = outputLayerWeights;
    this.flags = flags;
  }

  public double[] hiddenLayerOutput(double[][] inputLayerWeights, int[] nodeCliqueFeatures, SeqClassifierFlags aFlag, double[] featureVal) {
    int layerOneSize = inputLayerWeights.length;
    if (layerOneCache == null || layerOneSize != layerOneCache.length)
      layerOneCache = new double[layerOneSize];
    for (int i = 0; i < layerOneSize; i++) {
      double[] ws = inputLayerWeights[i];
      double lOneW = 0;
      for (int m = 0; m < nodeCliqueFeatures.length; m++) {
        double dotProd = ws[nodeCliqueFeatures[m]];
        if (featureVal != null)
          dotProd *= featureVal[m];
        lOneW += dotProd;
      }
      layerOneCache[i] = lOneW;
    }
    if (!aFlag.useHiddenLayer)
      return layerOneCache;

    // transform layer one through hidden
    if (hiddenLayerCache == null || layerOneSize != hiddenLayerCache.length)
      hiddenLayerCache = new double[layerOneSize];
    for (int i = 0; i < layerOneSize; i++) {
      if (aFlag.useSigmoid) {
        hiddenLayerCache[i] = sigmoid(layerOneCache[i]);
      } else {
        hiddenLayerCache[i] = Math.tanh(layerOneCache[i]);
      }
    }
    return hiddenLayerCache;
  }

  @Override
  public double computeCliquePotential(int cliqueSize, int labelIndex,
      int[] cliqueFeatures, double[] featureVal, int posInSent) {
    double output = 0.0;
    if (cliqueSize > 1) { // linear potential for edge cliques
      for (int cliqueFeature : cliqueFeatures) {
        output += linearWeights[cliqueFeature][labelIndex];
      }
    } else { // non-linear potential for node cliques
      double[] hiddenLayer = hiddenLayerOutput(inputLayerWeights, cliqueFeatures, flags, featureVal);
      int outputLayerSize = inputLayerWeights.length / outputLayerWeights[0].length;

      // transform the hidden layer to output layer through linear transformation
      if (flags.useOutputLayer) {
        double[] outputWs; // initialized immediately below
        if (flags.tieOutputLayer) {
          outputWs = outputLayerWeights[0];
        } else {
          outputWs = outputLayerWeights[labelIndex];
        }
        if (flags.softmaxOutputLayer) {
          outputWs = ArrayMath.softmax(outputWs);
        }
        for (int i = 0; i < inputLayerWeights.length; i++) {
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
    }
    return output;
  }

}
