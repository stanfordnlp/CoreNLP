package edu.stanford.nlp.coref.neural;

import java.util.List;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.NeuralUtils;

public class ModelSerializer {
  private static final String LANGUAGE = "chinese";
  private static final String NAME = "default";
  private static final String DATA_PATH =
      "/Users/kevinclark/Programming/research/coref/neural/";

  public static void main(String[] args) throws Exception {
    Embedding staticWordEmbeddings = new Embedding(DATA_PATH  + LANGUAGE + "/" +  "vectors_pretrained_all");
    Embedding tunedWordEmbeddings = new Embedding(DATA_PATH  + LANGUAGE + "/" + NAME + "_vectors_learned");

    List<SimpleMatrix> anaphoricityModel = NeuralUtils.loadTextMatrices(
        DATA_PATH + LANGUAGE + "/" + NAME + "_anaphoricity_weights");
    SimpleMatrix anaBias = anaphoricityModel.remove(anaphoricityModel.size() - 1);
    SimpleMatrix anaScale = anaphoricityModel.remove(anaphoricityModel.size() - 1);
    anaphoricityModel.add(anaScale.mult(new SimpleMatrix(new double[][] {{-0.3}})));
    anaphoricityModel.add(anaBias.mult(new SimpleMatrix(new double[][] {{-0.3}}))
        .plus(new SimpleMatrix(new double[][] {{-1}})));

    List<SimpleMatrix> pairwiseModel = NeuralUtils.loadTextMatrices(
        DATA_PATH  + LANGUAGE + "/" + NAME + "_pairwise_weights");
    SimpleMatrix antecedentMatrix = pairwiseModel.remove(0);
    SimpleMatrix anaphorMatrix = pairwiseModel.remove(0);
    SimpleMatrix pairFeaturesMatrix = pairwiseModel.remove(0);
    SimpleMatrix pairwiseFirstLayerBias = pairwiseModel.remove(0);

    NeuralCorefModel ncf = new NeuralCorefModel(antecedentMatrix, anaphorMatrix, pairFeaturesMatrix,
        pairwiseFirstLayerBias, anaphoricityModel, pairwiseModel, tunedWordEmbeddings);
    IOUtils.writeObjectToFile(ncf, DATA_PATH + LANGUAGE + "-model-" + NAME + ".ser.gz");
    IOUtils.writeObjectToFile(staticWordEmbeddings, DATA_PATH + LANGUAGE + "-embeddings.ser.gz");
  }
}
