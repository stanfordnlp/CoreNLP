package edu.stanford.nlp.neural;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.neural.SimpleTensor;
import edu.stanford.nlp.sentiment.RNNOptions;
import edu.stanford.nlp.sentiment.SentimentModel;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TwoDimensionalMap;

public class ConvertModels {

  public enum Stage {
    OLD, NEW
  }

  /**
   * Transform this map into a new map using the given function
   */
  public static <K1, K2, V, V2> TwoDimensionalMap<K1, K2, V2> transform2DMap(TwoDimensionalMap<K1, K2, V> in, 
                                                                             Function<V, V2> function) {
    // TODO: reuse the map factories (it needs a cast)
    // however we currently are using this for Sentiment, which we know needs a TreeMap
    TwoDimensionalMap<K1, K2, V2> out = TwoDimensionalMap.treeMap();
    out.addAll(in, function);
    return out;
  }

  public static List<List<Double>> fromMatrix(SimpleMatrix in) {
    List<List<Double>> out = new ArrayList<>();

    for (int i = 0; i < in.numRows(); ++i) {
      out.add(new ArrayList<>());
      for (int j = 0; j < in.numCols(); ++j) {
        out.get(i).add(in.get(i, j));
      }
    }

    return out;
  }

  public static List<List<List<Double>>> fromTensor(SimpleTensor in) {
    List<List<List<Double>>> out = new ArrayList<>();

    for (int i = 0; i < in.numSlices(); ++i) {
      out.add(fromMatrix(in.getSlice(i)));
    }

    return out;
  }

  public static SimpleMatrix toMatrix(List<List<Double>> in) {
    if (in.size() == 0) {
      throw new IllegalArgumentException("Input array with 0 rows");
    }
    if (in.get(0).size() == 0) {
      throw new IllegalArgumentException("Input array with 0 columns");
    }
    for (int i = 1; i < in.size(); ++i) {
      if (in.get(i).size() != in.get(0).size()) {
        throw new IllegalArgumentException("Input array with uneven columns");
      }
    }

    SimpleMatrix out = new SimpleMatrix(in.size(), in.get(0).size());
    for (int i = 0; i < in.size(); ++i) {
      List<Double> row = in.get(i);
      for (int j = 0; j < row.size(); ++j) {
        out.set(i, j, row.get(j));
      }
    }

    return out;
  }

  public static SimpleTensor toTensor(List<List<List<Double>>> in) {
    int numSlices = in.size();
    SimpleMatrix[] slices = new SimpleMatrix[numSlices];
    for (int i = 0; i < numSlices; ++i) {
      slices[i] = toMatrix(in.get(i));
    }
    return new SimpleTensor(slices);
  }

  public static <K, V, V2> Map<K, V2> transformMap(Map<K, V> in, Function<V, V2> function) {
    Map<K, V2> transformed = Generics.newTreeMap();
    for (K k : in.keySet()) {
      transformed.put(k, function.apply(in.get(k)));
    }
    return transformed;
  }


  public static void writeSentiment(SentimentModel model, ObjectOutputStream out)
    throws IOException
  {
    Function<SimpleMatrix, List<List<Double>>> f = (SimpleMatrix x) -> fromMatrix(x);

    out.writeObject(transform2DMap(model.binaryTransform, f));
    out.writeObject(transform2DMap(model.binaryTensors, (SimpleTensor x) -> fromTensor(x)));
    out.writeObject(transform2DMap(model.binaryClassification, f));

    out.writeObject(transformMap(model.unaryClassification, f));
    out.writeObject(transformMap(model.wordVectors, f));

    out.writeObject(model.op);
  }

  public static SentimentModel readSentiment(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    Function<List<List<Double>>, SimpleMatrix> f = (List<List<Double>> x) -> toMatrix(x);

    TwoDimensionalMap<String, String, List<List<Double>>> map2dSM = 
      ErasureUtils.uncheckedCast(in.readObject());
    TwoDimensionalMap<String, String, SimpleMatrix> binaryTransform = transform2DMap(map2dSM, f);

    TwoDimensionalMap<String, String, List<List<List<Double>>>> map2dST = 
      ErasureUtils.uncheckedCast(in.readObject());
    TwoDimensionalMap<String, String, SimpleTensor> binaryTensor = 
      transform2DMap(map2dST, (x) -> toTensor(x));

    map2dSM = ErasureUtils.uncheckedCast(in.readObject());
    TwoDimensionalMap<String, String, SimpleMatrix> binaryClassification = transform2DMap(map2dSM, f);

    Map<String, List<List<Double>>> map = ErasureUtils.uncheckedCast(in.readObject());
    Map<String, SimpleMatrix> unaryClassification = transformMap(map, f);

    map = ErasureUtils.uncheckedCast(in.readObject());
    Map<String, SimpleMatrix> wordVectors = transformMap(map, f);

    RNNOptions op = ErasureUtils.uncheckedCast(in.readObject());

    return new SentimentModel(binaryTransform, binaryTensor, binaryClassification,
                              unaryClassification, wordVectors, op);
  }

  public static void main(String[] args) throws IOException, ClassNotFoundException {
    Properties props = StringUtils.argsToProperties(args);

    Stage stage;
    try {
      stage = Stage.valueOf(props.getProperty("stage"));
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException("Please specify -stage, either OLD or NEW");
    }

    if (!props.containsKey("input")) {
      throw new IllegalArgumentException("Please specify -input");
    }

    if (!props.containsKey("output")) {
      throw new IllegalArgumentException("Please specify -output");
    }

    if (stage == Stage.OLD) {
      SentimentModel model = SentimentModel.loadSerialized(props.getProperty("input"));
      ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(props.getProperty("output")));
      writeSentiment(model, out);
      out.close();
    } else {
      ObjectInputStream in = new ObjectInputStream(new FileInputStream(props.getProperty("input")));
      SentimentModel model = readSentiment(in);
      model.saveSerialized(props.getProperty("output"));
    }
  }
}

