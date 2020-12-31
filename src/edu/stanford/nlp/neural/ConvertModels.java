package edu.stanford.nlp.neural;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;

import org.ejml.simple.SimpleMatrix;

import edu.stanford.nlp.coref.fastneural.FastNeuralCorefModel;
import edu.stanford.nlp.coref.neural.EmbeddingExtractor;
import edu.stanford.nlp.coref.neural.NeuralCorefModel;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.parser.dvparser.DVModel;
import edu.stanford.nlp.parser.dvparser.DVModelReranker;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.sentiment.RNNOptions;
import edu.stanford.nlp.sentiment.SentimentModel;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.ErasureUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.TwoDimensionalMap;

public class ConvertModels {

  public enum Stage {
    OLD, NEW
  }

  public enum Model {
    SENTIMENT, DVPARSER, COREF, EMBEDDING, FASTCOREF
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
    // this may not work for all map types, but it should suffice for
    // TreeMap or HashMap, which are the likely candidates
    final Map<K, V2> transformed;
    try {
      transformed = ErasureUtils.uncheckedCast(in.getClass().getConstructor().newInstance());
    } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
    for (Map.Entry<K, V> entry : in.entrySet()) {
      transformed.put(entry.getKey(), function.apply(entry.getValue()));
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

  public static void writeParser(LexicalizedParser model, DVModelReranker reranker, ObjectOutputStream out)
    throws IOException
  {
    out.writeObject(model);
    
    Function<SimpleMatrix, List<List<Double>>> f = (SimpleMatrix x) -> fromMatrix(x);

    DVModel dvmodel = reranker.getModel();
    out.writeObject(transform2DMap(dvmodel.binaryTransform, f));
    out.writeObject(transformMap(dvmodel.unaryTransform, f));
    out.writeObject(transform2DMap(dvmodel.binaryScore, f));
    out.writeObject(transformMap(dvmodel.unaryScore, f));
    out.writeObject(transformMap(dvmodel.wordVectors, f));
  }

  public static LexicalizedParser readParser(ObjectInputStream in) 
    throws IOException, ClassNotFoundException
  {
    LexicalizedParser model = ErasureUtils.uncheckedCast(in.readObject());

    Function<List<List<Double>>, SimpleMatrix> f = (x) -> toMatrix(x);

    TwoDimensionalMap<String, String, List<List<Double>>> map2dSM = 
      ErasureUtils.uncheckedCast(in.readObject());
    TwoDimensionalMap<String, String, SimpleMatrix> binaryTransform = transform2DMap(map2dSM, f);

    Map<String, List<List<Double>>> map = ErasureUtils.uncheckedCast(in.readObject());
    Map<String, SimpleMatrix> unaryTransform = transformMap(map, f);

    map2dSM = ErasureUtils.uncheckedCast(in.readObject());
    TwoDimensionalMap<String, String, SimpleMatrix> binaryScore = transform2DMap(map2dSM, f);

    map = ErasureUtils.uncheckedCast(in.readObject());
    Map<String, SimpleMatrix> unaryScore = transformMap(map, f);

    map = ErasureUtils.uncheckedCast(in.readObject());
    Map<String, SimpleMatrix> wordVectors = transformMap(map, f);

    DVModel dvModel = new DVModel(binaryTransform, unaryTransform, binaryScore,
                                  unaryScore, wordVectors, model.getOp());
    DVModelReranker reranker = new DVModelReranker(dvModel);
    model.reranker = reranker;
    return model;
  }

  public static void writeEmbedding(Embedding embedding, ObjectOutputStream out)
    throws IOException
  {
    Function<SimpleMatrix, List<List<Double>>> f = (SimpleMatrix x) -> fromMatrix(x);
    Map<String, SimpleMatrix> vectors = embedding.getWordVectors();
    Map<String, List<List<Double>>> newVectors = transformMap(vectors, f);
    out.writeObject(newVectors);
  }

  public static Embedding readEmbedding(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {

    Function<List<List<Double>>, SimpleMatrix> f = (x) -> toMatrix(x);
    Map<String, List<List<Double>>> map = ErasureUtils.uncheckedCast(in.readObject());
    Map<String, SimpleMatrix> vectors = transformMap(map, f);
    return new Embedding(vectors);
  }

  public static void writeCoref(NeuralCorefModel model, ObjectOutputStream out)
    throws IOException
  {
    Function<SimpleMatrix, List<List<Double>>> f = (SimpleMatrix x) -> fromMatrix(x);
    out.writeObject(fromMatrix(model.getAntecedentMatrix()));
    out.writeObject(fromMatrix(model.getAnaphorMatrix()));
    out.writeObject(fromMatrix(model.getPairFeaturesMatrix()));
    out.writeObject(fromMatrix(model.getPairwiseFirstLayerBias()));
    out.writeObject(CollectionUtils.transformAsList(model.getAnaphoricityModel(), f));
    out.writeObject(CollectionUtils.transformAsList(model.getPairwiseModel(), f));

    Map<String, SimpleMatrix> vectors = model.getWordEmbeddings().getWordVectors();
    Map<String, List<List<Double>>> newVectors = transformMap(vectors, f);
    out.writeObject(newVectors);
  }

  public static NeuralCorefModel readCoref(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    Function<List<List<Double>>, SimpleMatrix> f = (x) -> toMatrix(x);
    SimpleMatrix antecedentMatrix = toMatrix(ErasureUtils.uncheckedCast(in.readObject()));
    SimpleMatrix anaphorMatrix = toMatrix(ErasureUtils.uncheckedCast(in.readObject()));
    SimpleMatrix pairFeaturesMatrix = toMatrix(ErasureUtils.uncheckedCast(in.readObject()));
    SimpleMatrix pairwiseFirstLayerBias = toMatrix(ErasureUtils.uncheckedCast(in.readObject()));

    List<SimpleMatrix> anaphoricityModel = CollectionUtils.transformAsList(ErasureUtils.uncheckedCast(in.readObject()), f);
    List<SimpleMatrix> pairwiseModel = CollectionUtils.transformAsList(ErasureUtils.uncheckedCast(in.readObject()), f);

    Map<String, List<List<Double>>> vectorsDoubles = ErasureUtils.uncheckedCast(in.readObject());
    Map<String, SimpleMatrix> vectors = transformMap(vectorsDoubles, f);
    Embedding embedding = new Embedding(vectors);

    NeuralCorefModel model = new NeuralCorefModel(antecedentMatrix, anaphorMatrix,
                                                  pairFeaturesMatrix, pairwiseFirstLayerBias,
                                                  anaphoricityModel, pairwiseModel,
                                                  embedding);
    return model;
  }

  public static void writeFastCoref(FastNeuralCorefModel model, ObjectOutputStream out)
    throws IOException
  {
    Function<SimpleMatrix, List<List<Double>>> f = (SimpleMatrix x) -> fromMatrix(x);

    EmbeddingExtractor embedding = model.getEmbeddingExtractor();
    out.writeObject(embedding.isConll());
    Embedding staticEmbedding = embedding.getStaticWordEmbeddings();
    if (staticEmbedding == null) {
      out.writeObject(false);
    } else {
      out.writeObject(true);
      writeEmbedding(staticEmbedding, out);
    }
    writeEmbedding(embedding.getTunedWordEmbeddings(), out);
    out.writeObject(embedding.getNAEmbedding());

    out.writeObject(model.getPairFeatureIds());
    out.writeObject(model.getMentionFeatureIds());
    out.writeObject(CollectionUtils.transformAsList(model.getAllWeights(), f));
  }

  public static FastNeuralCorefModel readFastCoref(ObjectInputStream in)
    throws IOException, ClassNotFoundException
  {
    Function<List<List<Double>>, SimpleMatrix> f = (x) -> toMatrix(x);

    boolean conll = ErasureUtils.uncheckedCast(in.readObject());
    boolean hasStatic = ErasureUtils.uncheckedCast(in.readObject());
    Embedding staticEmbedding = (hasStatic) ? readEmbedding(in) : null;
    Embedding tunedEmbedding = readEmbedding(in);
    String naEmbedding = ErasureUtils.uncheckedCast(in.readObject());

    EmbeddingExtractor embedding = new EmbeddingExtractor(conll, staticEmbedding, tunedEmbedding, naEmbedding);

    Map<String, Integer> pairFeatures = ErasureUtils.uncheckedCast(in.readObject());
    Map<String, Integer> mentionFeatures = ErasureUtils.uncheckedCast(in.readObject());
    List<SimpleMatrix> weights = CollectionUtils.transformAsList(ErasureUtils.uncheckedCast(in.readObject()), f);

    return new FastNeuralCorefModel(embedding, pairFeatures, mentionFeatures, weights);
  }

  /**
   * This program converts a sentiment model or an RNN parser model
   * from EJML v23, used by CoreNLP 3.9.2, to a more recent version of
   * EJML, such as v38.  The reason for this is that the EJML v31
   * update changed the serialization of SimpleMatrix in a way that
   * broke all the old models, so for years we never bit the bullet of
   * upgrading.  This script handles the upgrade of our models which
   * use SimpleMatrix.  It needs to be done in two steps.
   * <br>
   * The first conversion turns a model into a file with lists of
   * doubles in place of the SimpleMatrix objects used in ejml.
   * <br>
   * The second conversion turns the lists of doubles back into a new
   * SentimentModel or RNN parser.
   * <br>
   * In between the two steps you should replace the EJML version you
   * are using with 0.38, although no one is judging you if you
   * aimlessly convert back and forth using the same EJML version.
   * <br>
   * Concrete steps:
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage OLD -model SENTIMENT -input edu/stanford/nlp/models/sentiment/sentiment.ser.gz -output sentiment.INT.ser.gz</code> 
   * <br>
   * ... update EJML library to v38 or a later
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage NEW -model SENTIMENT -input sentiment.INT.ser.gz -output sentiment.38.ser.gz</code> 
   * <br>
   * Congratulations, your old model will now work with EJML v38.
   * <br>
   * To upgrade an RNN model (did anyone train this themselves?) use <code>-model DVPARSER</code>
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage OLD -model DVPARSER -input /u/nlp/data/lexparser/chineseRNN.e21.ser.gz -output /u/nlp/data/lexparser/chineseRNN.INT.ser.gz</code>
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage NEW -model DVPARSER -input /u/nlp/data/lexparser/chineseRNN.INT.ser.gz -output /u/nlp/data/lexparser/chineseRNN.e38.ser.gz</code>
   * <br>
   * To upgrade a neural coref model, use <code>-model COREF</code>
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage OLD -model COREF -input /scr/nlp/data/coref/models/neural/english/english-model-default.ser.gz -output /scr/nlp/data/coref/models/neural/english/english-model-default.INT.ser.gz</code>
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage NEW -model COREF -input /scr/nlp/data/coref/models/neural/english/english-model-default.INT.ser.gz -output /scr/nlp/data/coref/models/neural/english/english-model-default.e39.ser.gz</code>
   * <br>
   * Neural coref models ship with a separate embedding file, although these are also embedded in the model itself.  To upgrade this, use <code>-model EMBEDDING</code>
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage OLD -model EMBEDDING -input /scr/nlp/data/coref/models/neural/english/english-embeddings.e38.ser.gz -output /scr/nlp/data/coref/models/neural/english/english-embeddings.INT.ser.gz</code>
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage NEW -model EMBEDDING -input /scr/nlp/data/coref/models/neural/english/english-embeddings.INT.ser.gz -output /scr/nlp/data/coref/models/neural/english/english-embeddings.e39.ser.gz</code>
   * <br>
   * There is another coref model which isn't used in corenlp, but it might be in the future.  To upgrade this, use <code>-model FASTCOREF</code>
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage OLD -model FASTCOREF -input /scr/nlp/data/coref/models/fastneural/fast-english-model.e38.ser.gz -output /scr/nlp/data/coref/models/fastneural/fast-english-model.INT.ser.gz</code>
   * <br>
   * <code> java edu.stanford.nlp.neural.ConvertModels -stage NEW -model FASTCOREF -input /scr/nlp/data/coref/models/fastneural/fast-english-model.INT.ser.gz -output /scr/nlp/data/coref/models/fastneural/fast-english-model.e39.ser.gz</code>
   * <br>
   *
   * @author <a href=horatio@gmail.com>John Bauer</a>
   */
  public static void main(String[] args) throws IOException, ClassNotFoundException, InstantiationException, NoSuchMethodException {
    Properties props = StringUtils.argsToProperties(args);

    final Stage stage;
    try {
      stage = Stage.valueOf(props.getProperty("stage").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException("Please specify -stage, either OLD or NEW");
    }

    final Model modelType;
    try {
      modelType = Model.valueOf(props.getProperty("model").toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new IllegalArgumentException("Please specify -model, either SENTIMENT, DVPARSER, EMBEDDING, COREF, FASTCOREF");
    }

    if (!props.containsKey("input")) {
      throw new IllegalArgumentException("Please specify -input");
    }

    if (!props.containsKey("output")) {
      throw new IllegalArgumentException("Please specify -output");
    }

    String inputPath = props.getProperty("input");
    String outputPath = props.getProperty("output");

    if (modelType == Model.SENTIMENT) {
      if (stage == Stage.OLD) {
        SentimentModel model = SentimentModel.loadSerialized(inputPath);
        ObjectOutputStream out = IOUtils.writeStreamFromString(outputPath);
        writeSentiment(model, out);
        out.close();
      } else {
        ObjectInputStream in = IOUtils.readStreamFromString(inputPath);
        SentimentModel model = readSentiment(in);
        in.close();
        model.saveSerialized(outputPath);
      }
    } else if (modelType == Model.DVPARSER) {
      if (stage == Stage.OLD) {
        LexicalizedParser model = LexicalizedParser.loadModel(inputPath);
        if (model.reranker == null) {
          System.out.println("Nothing to do for " + inputPath);
        } else {
          DVModelReranker reranker = (DVModelReranker) model.reranker; // will barf if not successful
          model.reranker = null;
          ObjectOutputStream out = IOUtils.writeStreamFromString(outputPath);
          writeParser(model, reranker, out);
          out.close();
        }
      } else {
        ObjectInputStream in = IOUtils.readStreamFromString(inputPath);
        LexicalizedParser model = readParser(in);
        in.close();
        model.saveParserToSerialized(outputPath);
      }
    } else if (modelType == Model.EMBEDDING) {
      if (stage == Stage.OLD) {
        Embedding embedding = ErasureUtils.uncheckedCast(IOUtils.readObjectFromURLOrClasspathOrFileSystem(inputPath));
        ObjectOutputStream out = IOUtils.writeStreamFromString(outputPath);
        writeEmbedding(embedding, out);
        out.close();
      } else {
        ObjectInputStream in = IOUtils.readStreamFromString(inputPath);
        Embedding embedding = readEmbedding(in);
        in.close();
        IOUtils.writeObjectToFile(embedding, outputPath);
      }
    } else if (modelType == Model.COREF) {
      if (stage == Stage.OLD) {
        NeuralCorefModel model = ErasureUtils.uncheckedCast(IOUtils.readObjectFromURLOrClasspathOrFileSystem(inputPath));
        ObjectOutputStream out = IOUtils.writeStreamFromString(outputPath);
        writeCoref(model, out);
        out.close();
      } else {
        ObjectInputStream in = IOUtils.readStreamFromString(inputPath);
        NeuralCorefModel model = readCoref(in);
        in.close();
        IOUtils.writeObjectToFile(model, outputPath);
      }
    } else if (modelType == Model.FASTCOREF) {
      if (stage == Stage.OLD) {
        FastNeuralCorefModel model = ErasureUtils.uncheckedCast(IOUtils.readObjectFromURLOrClasspathOrFileSystem(inputPath));
        ObjectOutputStream out = IOUtils.writeStreamFromString(outputPath);
        writeFastCoref(model, out);
        out.close();
      } else {
        ObjectInputStream in = IOUtils.readStreamFromString(inputPath);
        FastNeuralCorefModel model = readFastCoref(in);
        in.close();
        IOUtils.writeObjectToFile(model, outputPath);
      }
    } else {
      throw new IllegalArgumentException("Unknown model type " + modelType);
    }
  }
}

