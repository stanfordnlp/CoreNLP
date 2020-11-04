package edu.stanford.nlp.coref.neural;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.NeuralUtils;
import org.ejml.simple.SimpleMatrix;

/**
 * Stores the weights and implements the matrix operations used by a {@link NeuralCorefAlgorithm}
 * @author Kevin Clark
 */
public class NeuralCorefModel implements Serializable {
  private static final long serialVersionUID = 2139427931784505653L;

  private SimpleMatrix antecedentMatrix;
  private SimpleMatrix anaphorMatrix;
  private SimpleMatrix pairFeaturesMatrix;
  private SimpleMatrix pairwiseFirstLayerBias;
  private List<SimpleMatrix> anaphoricityModel;
  private List<SimpleMatrix> pairwiseModel;
  private Embedding wordEmbeddings;

  public NeuralCorefModel(SimpleMatrix antecedentMatrix, SimpleMatrix anaphorMatrix,
                          SimpleMatrix pairFeaturesMatrix, SimpleMatrix pairwiseFirstLayerBias,
                          List<SimpleMatrix> anaphoricityModel, List<SimpleMatrix> pairwiseModel,
                          Embedding wordEmbeddings) {
    this.antecedentMatrix = antecedentMatrix;
    this.anaphorMatrix = anaphorMatrix;
    this.pairFeaturesMatrix = pairFeaturesMatrix;
    this.pairwiseFirstLayerBias = pairwiseFirstLayerBias;
    this.anaphoricityModel = anaphoricityModel;
    this.pairwiseModel = pairwiseModel;
    this.wordEmbeddings = wordEmbeddings;
  }

  /*
  // TODO: remove when ejml 0.38 is upgraded
  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    in.defaultReadObject();

    antecedentMatrix = new SimpleMatrix(antecedentMatrix);
    anaphorMatrix = new SimpleMatrix(anaphorMatrix);
    pairFeaturesMatrix = new SimpleMatrix(pairFeaturesMatrix);
    pairwiseFirstLayerBias = new SimpleMatrix(pairwiseFirstLayerBias);
    anaphoricityModel = anaphoricityModel.stream()
      .map(x->new SimpleMatrix(x))
      .collect(Collectors.toList());
    pairwiseModel = pairwiseModel.stream()
      .map(x->new SimpleMatrix(x))
      .collect(Collectors.toList());
  }
  */

  /*
  private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    antecedentMatrix = ConvertModels.toMatrix(ErasureUtils.uncheckedCast(in.readObject()));
    anaphorMatrix = ConvertModels.toMatrix(ErasureUtils.uncheckedCast(in.readObject()));
    pairFeaturesMatrix = ConvertModels.toMatrix(ErasureUtils.uncheckedCast(in.readObject()));
    pairwiseFirstLayerBias = ConvertModels.toMatrix(ErasureUtils.uncheckedCast(in.readObject()));

    List<List<List<Double>>> tmp = ErasureUtils.uncheckedCast(in.readObject());
    anaphoricityModel = (tmp.stream()
                         .map(x->ConvertModels.toMatrix(x))
                         .collect(Collectors.toList()));
    tmp = ErasureUtils.uncheckedCast(in.readObject());
    pairwiseModel = (tmp.stream()
                     .map(x->ConvertModels.toMatrix(x))
                     .collect(Collectors.toList()));

    wordEmbeddings = ErasureUtils.uncheckedCast(in.readObject());
  }
  */

  /*
  private void writeObject(ObjectOutputStream out)
    throws IOException
  {
    out.writeObject(ConvertModels.fromMatrix(antecedentMatrix));
    out.writeObject(ConvertModels.fromMatrix(anaphorMatrix));
    out.writeObject(ConvertModels.fromMatrix(pairFeaturesMatrix));
    out.writeObject(ConvertModels.fromMatrix(pairwiseFirstLayerBias));

    out.writeObject(anaphoricityModel.stream()
                    .map(x->ConvertModels.fromMatrix(x))
                    .collect(Collectors.toList()));
    out.writeObject(pairwiseModel.stream()
                    .map(x->ConvertModels.fromMatrix(x))
                    .collect(Collectors.toList()));

    out.writeObject(wordEmbeddings);
  }
  */


  public double getAnaphoricityScore(SimpleMatrix mentionEmbedding,
      SimpleMatrix anaphoricityFeatures) {
    return score(NeuralUtils.concatenate(mentionEmbedding, anaphoricityFeatures),
        anaphoricityModel);
  }

  public double getPairwiseScore(SimpleMatrix antecedentEmbedding, SimpleMatrix anaphorEmbedding,
      SimpleMatrix pairFeatures) {
    SimpleMatrix firstLayerOutput = NeuralUtils.elementwiseApplyReLU(
        antecedentEmbedding
          .plus(anaphorEmbedding)
          .plus(pairFeaturesMatrix.mult(pairFeatures))
          .plus(pairwiseFirstLayerBias));
    return score(firstLayerOutput, pairwiseModel);
  }

  private static double score(SimpleMatrix features, List<SimpleMatrix> weights) {
    for (int i = 0; i < weights.size(); i += 2) {
      features = weights.get(i).mult(features).plus(weights.get(i + 1));
      if (weights.get(i).numRows() > 1) {
        features = NeuralUtils.elementwiseApplyReLU(features);
      }
    }
    return features.elementSum();
  }

  public SimpleMatrix getAnaphorEmbedding(SimpleMatrix mentionEmbedding) {
    return anaphorMatrix.mult(mentionEmbedding);
  }

  public SimpleMatrix getAntecedentEmbedding(SimpleMatrix mentionEmbedding) {
    return antecedentMatrix.mult(mentionEmbedding);
  }

  // note that we assume the user won't be hostile and change the
  // values using these accessors
  public Embedding getWordEmbeddings() {
    return wordEmbeddings;
  }

  public SimpleMatrix getAntecedentMatrix() {
    return antecedentMatrix;
  }

  public SimpleMatrix getAnaphorMatrix() {
    return anaphorMatrix;
  }

  public SimpleMatrix getPairFeaturesMatrix() {
    return pairFeaturesMatrix;
  }

  public SimpleMatrix getPairwiseFirstLayerBias() {
    return pairwiseFirstLayerBias;
  }

  public List<SimpleMatrix> getAnaphoricityModel() {
    return Collections.unmodifiableList(anaphoricityModel);
  }

  public List<SimpleMatrix> getPairwiseModel() {
    return Collections.unmodifiableList(pairwiseModel);
  }
}
