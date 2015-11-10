package edu.stanford.nlp.loglinear.storage;

import edu.stanford.nlp.loglinear.model.GraphicalModel;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Created on 10/17/15.
 * @author keenon
 * <p>
 * The idea here is pretty straightforward, but requires some explanation.
 * <p>
 * GraphicalModels are great for storing lots of metadata about the model, though storing full featurizations can be a
 * bit slow.
 * <p>
 * With a ModelBatch, you can get your models from anywhere, and after running LENSE on them (which will add lots of
 * annotations, potentially) you can write those models to disk in a big fat batch. Those models you've stored can be
 * stored without featurizing them, as long as you keep enough metadata to be able to featurize later. Then when you
 * load a batch from disk to run simulations, you can try out different feature sets and gameplayers, all while keeping
 * the beautifully precomputed metadata for the model (including instructions for querying, and the query logs).
 */
public class ModelBatch extends ArrayList<GraphicalModel> {
  /**
   * Creates an empty ModelBatch
   */
  public ModelBatch() {
  }

  /**
   * This loads a model batch from a file, then closes the file handler. Just a convenience.
   *
   * @param filename the file to load from
   * @throws IOException
   */
  public ModelBatch(String filename) throws IOException {
    this(filename, (model) -> {
    });
  }

  /**
   * This loads a model batch from a file, then closes the file handler. Just a convenience.
   *
   * @param filename   the file to load from
   * @param featurizer a function that gets run on every GraphicalModel, and has a chance to edit them (eg by adding
   *                   or changing features)
   * @throws IOException
   */
  public ModelBatch(String filename, Consumer<GraphicalModel> featurizer) throws IOException {
    InputStream is = new FileInputStream(filename);
    readFrom(is, featurizer);
    is.close();
  }

  /**
   * Load a batch of models from disk, without specifying a function to re-featurize those models.
   *
   * @param inputStream the inputstream to load from
   */
  public ModelBatch(InputStream inputStream) throws IOException {
    this(inputStream, (model) -> {
    });
  }

  /**
   * Load a batch of models from disk, while running the function "featurizer" on each of the models before adding it
   * to the batch. This gives the loader a chance to experiment with new featurization techniques.
   *
   * @param inputStream the input stream to load from
   * @param featurizer  a function that gets run on every GraphicalModel, and has a chance to edit them (eg by adding
   *                    or changing features)
   */
  public ModelBatch(InputStream inputStream, Consumer<GraphicalModel> featurizer) throws IOException {
    readFrom(inputStream, featurizer);
  }

  /**
   * Load a batch of models from disk, while running the function "featurizer" on each of the models before adding it
   * to the batch. This gives the loader a chance to experiment with new featurization techniques.
   *
   * @param inputStream the input stream to load from
   * @param featurizer  a function that gets run on every GraphicalModel, and has a chance to edit them (eg by adding
   *                    or changing features)
   */
  private void readFrom(InputStream inputStream, Consumer<GraphicalModel> featurizer) throws IOException {
    GraphicalModel read;
    while ((read = GraphicalModel.readFromStream(inputStream)) != null) {
      featurizer.accept(read);
      add(read);
    }
  }

  /**
   * Convenience function to write the current state of the modelBatch out to a file, including all factors.
   * <p>
   * WARNING: These files can get quite large, if you're using large embeddings as features.
   *
   * @param filename the file to write the batch to
   * @throws IOException
   */
  public void writeToFile(String filename) throws IOException {
    FileOutputStream fos = new FileOutputStream(filename);
    writeToStream(fos);
    fos.close();
  }

  /**
   * Convenience function to write the current state of the modelBatch out to a file, without factors.
   *
   * @param filename the file to write the batch to
   * @throws IOException
   */
  public void writeToFileWithoutFactors(String filename) throws IOException {
    FileOutputStream fos = new FileOutputStream(filename);
    writeToStreamWithoutFactors(fos);
    fos.close();
  }

  /**
   * This writes the entire batch, including all factors, to the given output stream.
   * <p>
   * WARNING: These files can get quite large, if you're using large embeddings as features.
   *
   * @param outputStream the outputstream to write our files to
   * @throws IOException
   */
  public void writeToStream(OutputStream outputStream) throws IOException {
    for (GraphicalModel model : this) {
      model.writeToStream(outputStream);
    }
  }

  /**
   * This writes the whole batch, WITHOUT FACTORS, which means that anyone loading this batch will need to include
   * their own featurizer. Make sure that you have sufficient metadata to be able to do full featurizations.
   *
   * @param outputStream the outputstream to write our files to
   * @throws IOException
   */
  public void writeToStreamWithoutFactors(OutputStream outputStream) throws IOException {
    Set<GraphicalModel.Factor> emptySet = new HashSet<>();
    for (GraphicalModel model : this) {
      Set<GraphicalModel.Factor> cachedFactors = model.factors;
      model.factors = emptySet;
      model.writeToStream(outputStream);
      model.factors = cachedFactors;
    }
  }
}
