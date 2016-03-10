package edu.stanford.nlp.parser.dvparser; 
import edu.stanford.nlp.util.logging.Redwood;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.util.CollectionUtils;
import java.util.function.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.TwoDimensionalMap;
import edu.stanford.nlp.util.TwoDimensionalSet;

/**
 * Given a list of input DVParser models, this tool will output a new
 * DVParser which is the average of all of those models.  Sadly, this
 * does not actually seem to help; the resulting model is generally
 * worse than the input models, and definitely worse than the models
 * used in combination.
 *
 * @author John Bauer
 */
public class AverageDVModels  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(AverageDVModels.class);
  public static TwoDimensionalSet<String, String> getBinaryMatrixNames(List<TwoDimensionalMap<String, String, SimpleMatrix>> maps) {
    TwoDimensionalSet<String, String> matrixNames = new TwoDimensionalSet<>();
    for (TwoDimensionalMap<String, String, SimpleMatrix> map : maps) {
      for (TwoDimensionalMap.Entry<String, String, SimpleMatrix> entry : map) {
        matrixNames.add(entry.getFirstKey(), entry.getSecondKey());
      }
    }
    return matrixNames;
  }

  public static Set<String> getUnaryMatrixNames(List<Map<String, SimpleMatrix>> maps) {
    Set<String> matrixNames = Generics.newHashSet();
    for (Map<String, SimpleMatrix> map : maps) {
      for (Map.Entry<String, SimpleMatrix> entry : map.entrySet()) {
        matrixNames.add(entry.getKey());
      }
    }
    return matrixNames;
  }

  public static TwoDimensionalMap<String, String, SimpleMatrix> averageBinaryMatrices(List<TwoDimensionalMap<String, String, SimpleMatrix>> maps) {
    TwoDimensionalMap<String, String, SimpleMatrix> averages = TwoDimensionalMap.treeMap();
    for (Pair<String, String> binary : getBinaryMatrixNames(maps)) {
      int count = 0;
      SimpleMatrix matrix = null;
      for (TwoDimensionalMap<String, String, SimpleMatrix> map : maps) {
        if (!map.contains(binary.first(), binary.second())) {
          continue;
        }
        SimpleMatrix original = map.get(binary.first(), binary.second());
        ++count;
        if (matrix == null) {
          matrix = original;
        } else {
          matrix = matrix.plus(original);
        }
      }
      matrix = matrix.divide(count);
      averages.put(binary.first(), binary.second(), matrix);
    }
    return averages;
  }

  public static Map<String, SimpleMatrix> averageUnaryMatrices(List<Map<String, SimpleMatrix>> maps) {
    Map<String, SimpleMatrix> averages = Generics.newTreeMap();
    for (String name : getUnaryMatrixNames(maps)) {
      int count = 0;
      SimpleMatrix matrix = null;
      for (Map<String, SimpleMatrix> map : maps) {
        if (!map.containsKey(name)) {
          continue;
        }
        SimpleMatrix original = map.get(name);
        ++count;
        if (matrix == null) {
          matrix = original;
        } else {
          matrix = matrix.plus(original);
        }
      }
      matrix = matrix.divide(count);
      averages.put(name, matrix);
    }
    return averages;
  }

  /**
   * Command line arguments for this program:
   * <br>
   * -output: the model file to output
   * -input: a list of model files to input
   */
  public static void main(String[] args) {
    String outputModelFilename = null;
    List<String> inputModelFilenames = Generics.newArrayList();
    
    for (int argIndex = 0; argIndex < args.length; ) {
      if (args[argIndex].equalsIgnoreCase("-output")) {
        outputModelFilename = args[argIndex + 1];
        argIndex += 2;
      } else if (args[argIndex].equalsIgnoreCase("-input")) {
        for (++argIndex; argIndex < args.length && !args[argIndex].startsWith("-"); ++argIndex) {
          inputModelFilenames.addAll(Arrays.asList(args[argIndex].split(",")));
        }
      } else {
        throw new RuntimeException("Unknown argument " + args[argIndex]);
      }
    }

    if (outputModelFilename == null) {
      log.info("Need to specify output model name with -output");
      System.exit(2);
    }

    if (inputModelFilenames.size() == 0) {
      log.info("Need to specify input model names with -input");
      System.exit(2);
    }

    log.info("Averaging " + inputModelFilenames);
    log.info("Outputting result to " + outputModelFilename);

    LexicalizedParser lexparser = null;
    List<DVModel> models = Generics.newArrayList();
    for (String filename : inputModelFilenames) {
      LexicalizedParser parser = LexicalizedParser.loadModel(filename);
      if (lexparser == null) {
        lexparser = parser;
      }
      models.add(DVParser.getModelFromLexicalizedParser(parser));
    }

    List<TwoDimensionalMap<String, String, SimpleMatrix>> binaryTransformMaps =
      CollectionUtils.transformAsList(models, model -> model.binaryTransform);

    List<TwoDimensionalMap<String, String, SimpleMatrix>> binaryScoreMaps =
      CollectionUtils.transformAsList(models, model -> model.binaryScore);

    List<Map<String, SimpleMatrix>> unaryTransformMaps =
      CollectionUtils.transformAsList(models, model -> model.unaryTransform);

    List<Map<String, SimpleMatrix>> unaryScoreMaps =
      CollectionUtils.transformAsList(models, model -> model.unaryScore);

    List<Map<String, SimpleMatrix>> wordMaps =
      CollectionUtils.transformAsList(models, model -> model.wordVectors);

    TwoDimensionalMap<String, String, SimpleMatrix> binaryTransformAverages = averageBinaryMatrices(binaryTransformMaps);
    TwoDimensionalMap<String, String, SimpleMatrix> binaryScoreAverages = averageBinaryMatrices(binaryScoreMaps);
    Map<String, SimpleMatrix> unaryTransformAverages = averageUnaryMatrices(unaryTransformMaps);
    Map<String, SimpleMatrix> unaryScoreAverages = averageUnaryMatrices(unaryScoreMaps);
    Map<String, SimpleMatrix> wordAverages = averageUnaryMatrices(wordMaps);

    DVModel newModel = new DVModel(binaryTransformAverages, unaryTransformAverages,
                                   binaryScoreAverages, unaryScoreAverages,
                                   wordAverages, lexparser.getOp());
    DVParser newParser = new DVParser(newModel, lexparser);
    newParser.saveModel(outputModelFilename);
  }
}
