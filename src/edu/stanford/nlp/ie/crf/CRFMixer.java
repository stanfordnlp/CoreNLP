package edu.stanford.nlp.ie.crf;

import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Timing;

import java.util.List;
import java.util.Properties;

/**
 * Combines several different CRFClassifers by mixing their weights
 *  (currently uses uniform weights)
 *
 * Usage: java edu.stanford.nlp.ie.crf.CRFMixer -loadClassifiers <classifierPath>
 *        [-serializeTo <binaryModelPath> | -serializeToText <gzippedTextModelPath>]
 *
 * @author Angel Chang
 */
public class CRFMixer {

  CRFClassifier combinedModel;

  public CRFMixer() {};

  public CRFMixer(List<String> paths) { addModels(paths); }

  public void addModel(CRFClassifier crf, double weight)
  {
    if (combinedModel == null) {
      combinedModel = new CRFClassifier(crf);
      combinedModel.scaleWeights(weight);
    } else {
      combinedModel.combine(crf, weight);
    }
  }

  public void addModel(String modelFile, double weight) {
    try {
      CRFClassifier crf = CRFClassifier.getClassifier(modelFile);
      addModel(crf,weight);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void addModels(List<String> paths) {
    double weight = 1.0/paths.size();
    for (String path:paths) {
      addModel(path, weight);
    }
  }

  public CRFClassifier getModel()
  {
    return combinedModel;
  }

  public static void main(String[] args) throws Exception {
    Timing timer = new Timing();
    StringUtils.printErrInvocationString("CRFMixer", args);
    Properties props = StringUtils.argsToProperties(args);
    String loadClassifiers = props.getProperty("loadClassifiers");
    if (loadClassifiers == null) {
      System.err.println("Please specify -loadClassifiers");
      System.exit(-1);
    }
    String serializeTo = props.getProperty("serializeTo");
    String serializeToText = props.getProperty("serializeToText");
    if (serializeTo == null && serializeToText == null) {
      System.err.println("Please specify -serializeTo or -serializeToText");
      System.exit(-1);
    }
    List<String> paths = StringUtils.split(loadClassifiers, "[ ,;\t]");
    CRFMixer crfMixer = new CRFMixer(paths);
    CRFClassifier crf = crfMixer.getModel();
    if (serializeTo != null) {
      crf.serializeClassifier(serializeTo);
    }
    if (serializeToText != null) {
      crf.serializeTextClassifier(serializeToText);
    }
    long elapsedMs = timer.stop();
    System.err.println("Time to combine " + paths.size() + " models: " +
            Timing.toSecondsString(elapsedMs) + " seconds");
  }
}
