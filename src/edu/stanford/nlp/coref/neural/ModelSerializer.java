package edu.stanford.nlp.coref.neural;

import java.nio.file.Paths;
import java.util.List;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.neural.Embedding;
import edu.stanford.nlp.neural.NeuralUtils;
import edu.stanford.nlp.util.ArgumentParser;

public class ModelSerializer {
  @ArgumentParser.Option(name="language", gloss="What language settings were used to train the model")
  private static String LANGUAGE = "english";

  @ArgumentParser.Option(name="name", gloss="Model name")
  private static String NAME = "default";

  @ArgumentParser.Option(name="path", gloss="Directory where the saved matrices can be found")
  private static String DATA_PATH =
    "data/models/reward_rescaling/exported_weights";

  private static final String PRETRAINED = "vectors_pretrained_all";
  private static final String TUNED = "vectors_learned";
  private static final String ANAPHORICITY = "anaphoricity_weights";
  private static final String PAIRWISE = "pairwise_weights";

  public static void main(String[] args) throws Exception {
    ArgumentParser.fillOptions(ModelSerializer.class, args);

    final String baseDir;
    final String savedLanguageParticle;
    if (LANGUAGE != null && !LANGUAGE.equals("")) {
      baseDir = Paths.get(DATA_PATH, LANGUAGE).toString();
      savedLanguageParticle = LANGUAGE + "-";
    } else {
      baseDir = DATA_PATH;
      savedLanguageParticle = "";
    }

    final String pretrainedFilename = Paths.get(baseDir, PRETRAINED).toString();
    final String tunedFilename;
    final String anaphoricityFilename;
    final String pairwiseFilename;
    final String savedModelFilename;
    final String savedEmbeddingFilename = Paths.get(DATA_PATH, savedLanguageParticle + "embeddings.ser.gz").toString();
    if (NAME != null && !NAME.equals("")) {
      tunedFilename = Paths.get(baseDir, NAME + "_" + TUNED).toString();
      anaphoricityFilename = Paths.get(baseDir, NAME + "_" + ANAPHORICITY).toString();
      pairwiseFilename = Paths.get(baseDir, NAME + "_" + PAIRWISE).toString();
      savedModelFilename = Paths.get(DATA_PATH, savedLanguageParticle + "model-" + NAME + ".ser.gz").toString();
    } else {
      tunedFilename = Paths.get(baseDir, TUNED).toString();
      anaphoricityFilename = Paths.get(baseDir, ANAPHORICITY).toString();
      pairwiseFilename = Paths.get(baseDir, PAIRWISE).toString();
      savedModelFilename = Paths.get(DATA_PATH, savedLanguageParticle + "model.ser.gz").toString();
    }

    System.out.println("Loading pretrain wv from  " + pretrainedFilename);
    System.out.println("Loading tuned wv from     " + tunedFilename);
    System.out.println("Loading anaphoricity from " + anaphoricityFilename);
    System.out.println("Loading pairwise from     " + pairwiseFilename);
    System.out.println("Saving model to           " + savedModelFilename);
    System.out.println("Saving embedding to       " + savedEmbeddingFilename);

    Embedding staticWordEmbeddings = new Embedding(pretrainedFilename);
    Embedding tunedWordEmbeddings = new Embedding(tunedFilename);

    List<SimpleMatrix> anaphoricityModel = NeuralUtils.loadTextMatrices(anaphoricityFilename);
    SimpleMatrix anaBias = anaphoricityModel.remove(anaphoricityModel.size() - 1);
    SimpleMatrix anaScale = anaphoricityModel.remove(anaphoricityModel.size() - 1);
    anaphoricityModel.add(anaScale.mult(new SimpleMatrix(new double[][] {{-0.3}})));
    anaphoricityModel.add(anaBias.mult(new SimpleMatrix(new double[][] {{-0.3}}))
                          .plus(new SimpleMatrix(new double[][] {{-1}})));

    List<SimpleMatrix> pairwiseModel = NeuralUtils.loadTextMatrices(pairwiseFilename);
    SimpleMatrix antecedentMatrix = pairwiseModel.remove(0);
    SimpleMatrix anaphorMatrix = pairwiseModel.remove(0);
    SimpleMatrix pairFeaturesMatrix = pairwiseModel.remove(0);
    SimpleMatrix pairwiseFirstLayerBias = pairwiseModel.remove(0);

    NeuralCorefModel ncf = new NeuralCorefModel(antecedentMatrix, anaphorMatrix, pairFeaturesMatrix,
                                                pairwiseFirstLayerBias, anaphoricityModel, pairwiseModel, tunedWordEmbeddings);
    IOUtils.writeObjectToFile(ncf, savedModelFilename);
    IOUtils.writeObjectToFile(staticWordEmbeddings, savedEmbeddingFilename);
  }
}
