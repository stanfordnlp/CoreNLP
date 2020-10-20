package edu.stanford.nlp.ie.machinereading;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;

import edu.stanford.nlp.pipeline.Annotation;

// TODO make this an abstract class instead so setLoggerLevel doesn't have to be implemented by all subclasses.
// also, should add the load() method here -- though it can't be static, so maybe we need a different approach.
// Extractors should have a logger as an instance attribute.

public interface Extractor extends Serializable {
  /**
   * Trains one extractor model using the given dataset
   * 
   * @param dataset
   *          dataset to train from (this should already have annotations and
   *          will typically be created by a reader)
   */
  public void train(Annotation dataset);

  /**
   * Annotates the given dataset with the current model This works in place,
   * i.e., it adds ExtractionObject objects to the sentences in the dataset To
   * make sure you are not messing with gold annotation create a copy of the
   * ExtractionDataSet first!
   * 
   * @param dataset
   *          dataset to annotate
   */
  public void annotate(Annotation dataset);

  /**
   * Serializes this extractor to a file
   * 
   * @param path
   *          where to save the extractor
   * 
   */
  public void save(String path) throws IOException;
  
  public void setLoggerLevel(Level level);
}
