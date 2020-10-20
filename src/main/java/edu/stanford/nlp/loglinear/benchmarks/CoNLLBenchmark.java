package edu.stanford.nlp.loglinear.benchmarks;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.loglinear.inference.CliqueTree;
import edu.stanford.nlp.loglinear.learning.AbstractBatchOptimizer;
import edu.stanford.nlp.loglinear.learning.BacktrackingAdaGradOptimizer;
import edu.stanford.nlp.loglinear.learning.LogLikelihoodDifferentiableFunction;
import edu.stanford.nlp.loglinear.model.ConcatVector;
import edu.stanford.nlp.loglinear.model.ConcatVectorNamespace;
import edu.stanford.nlp.loglinear.model.GraphicalModel;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created on 8/26/15.
 * @author keenon
 * <p>
 * This loads the CoNLL dataset and 300 dimensional google word embeddings and trains a model on the data using binary
 * and unary factors. This is a nice explanation of why it is key to have ConcatVector as a datastructure, since there
 * is no need to specify the number of words in advance anywhere, and data structures will happily resize with a minimum
 * of GCC wastage.
 */
public class CoNLLBenchmark   {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CoNLLBenchmark.class);

  static final String DATA_PATH = "/u/nlp/data/ner/conll/";

  Map<String, double[]> embeddings = new HashMap<>();

  public static void main(String[] args) throws Exception {
    new CoNLLBenchmark().benchmarkOptimizer();
  }

  public void benchmarkOptimizer() throws Exception {
    List<CoNLLSentence> train = getSentences(DATA_PATH + "conll.iob.4class.train");
    List<CoNLLSentence> testA = getSentences(DATA_PATH + "conll.iob.4class.testa");
    List<CoNLLSentence> testB = getSentences(DATA_PATH + "conll.iob.4class.testb");

    List<CoNLLSentence> allData = new ArrayList<>();
    allData.addAll(train);
    allData.addAll(testA);
    allData.addAll(testB);

    Set<String> tagsSet = new HashSet<>();
    for (CoNLLSentence sentence : allData) for (String nerTag : sentence.ner) tagsSet.add(nerTag);
    List<String> tags = new ArrayList<>();
    tags.addAll(tagsSet);

    embeddings = getEmbeddings(DATA_PATH + "google-300-trimmed.ser.gz", allData);

    log.info("Making the training set...");

    ConcatVectorNamespace namespace = new ConcatVectorNamespace();

    int trainSize = train.size();
    GraphicalModel[] trainingSet = new GraphicalModel[trainSize];
    for (int i = 0; i < trainSize; i++) {
      if (i % 10 == 0) {
        log.info(i + "/" + trainSize);
      }
      trainingSet[i] = generateSentenceModel(namespace, train.get(i), tags);
    }

    log.info("Training system...");

    AbstractBatchOptimizer opt = new BacktrackingAdaGradOptimizer();

    // This training call is basically what we want the benchmark for. It should take 99% of the wall clock time
    ConcatVector weights = opt.optimize(trainingSet, new LogLikelihoodDifferentiableFunction(), namespace.newWeightsVector(), 0.01, 1.0e-5, false);

    log.info("Testing system...");

    // Evaluation method lifted from the CoNLL 2004 perl script

    Map<String, Double> correctChunk = new HashMap<>();
    Map<String, Double> foundCorrect = new HashMap<>();
    Map<String, Double> foundGuessed = new HashMap<>();
    double correct = 0.0;
    double total = 0.0;

    for (CoNLLSentence sentence : testA) {
      GraphicalModel model = generateSentenceModel(namespace, sentence, tags);
      int[] guesses = new CliqueTree(model, weights).calculateMAP();
      String[] nerGuesses = new String[guesses.length];
      for (int i = 0; i < guesses.length; i++) {
        nerGuesses[i] = tags.get(guesses[i]);
        if (nerGuesses[i].equals(sentence.ner.get(i))) {
          correct++;
          correctChunk.put(nerGuesses[i], correctChunk.getOrDefault(nerGuesses[i], 0.) + 1);
        }
        total++;
        foundCorrect.put(sentence.ner.get(i), foundCorrect.getOrDefault(sentence.ner.get(i), 0.) + 1);
        foundGuessed.put(nerGuesses[i], foundGuessed.getOrDefault(nerGuesses[i], 0.) + 1);
      }
    }

    log.info("\nSystem results:\n");

    log.info("Accuracy: " + (correct / total) + "\n");

    for (String tag : tags) {
      double precision = foundGuessed.getOrDefault(tag, 0.0) == 0 ? 0.0 : correctChunk.getOrDefault(tag, 0.0) / foundGuessed.get(tag);
      double recall = foundCorrect.getOrDefault(tag, 0.0) == 0 ? 0.0 : correctChunk.getOrDefault(tag, 0.0) / foundCorrect.get(tag);
      double f1 = (precision + recall == 0.0) ? 0.0 : (precision * recall * 2) / (precision + recall);
      log.info(tag + " (" + foundCorrect.getOrDefault(tag, 0.0).intValue() + ")");
      log.info("\tP:" + precision + " (" + correctChunk.getOrDefault(tag, 0.0).intValue() + "/" + foundGuessed.getOrDefault(tag, 0.0).intValue() + ")");
      log.info("\tR:" + recall + " (" + correctChunk.getOrDefault(tag, 0.0).intValue() + "/" + foundCorrect.getOrDefault(tag, 0.0).intValue() + ")");
      log.info("\tF1:" + f1);
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  // GENERATING MODELS
  ////////////////////////////////////////////////////////////////////////////////////////////

  private static String getWordShape(String string) {
    if (string.toUpperCase().equals(string) && string.toLowerCase().equals(string)) return "no-case";
    if (string.toUpperCase().equals(string)) return "upper-case";
    if (string.toLowerCase().equals(string)) return "lower-case";
    if (string.length() > 1 && Character.isUpperCase(string.charAt(0)) && string.substring(1).toLowerCase().equals(string.substring(1)))
      return "capitalized";
    return "mixed-case";
  }

  public GraphicalModel generateSentenceModel(ConcatVectorNamespace namespace, CoNLLSentence sentence, List<String> tags) {
    GraphicalModel model = new GraphicalModel();

    for (int i = 0; i < sentence.token.size(); i++) {

      // Add the training label

      Map<String, String> metadata = model.getVariableMetaDataByReference(i);

      metadata.put(LogLikelihoodDifferentiableFunction.VARIABLE_TRAINING_VALUE, "" + tags.indexOf(sentence.ner.get(i)));

      metadata.put("TOKEN", "" + sentence.token.get(i));
      metadata.put("POS", "" + sentence.pos.get(i));
      metadata.put("CHUNK", "" + sentence.npchunk.get(i));
      metadata.put("TAG", "" + sentence.ner.get(i));
    }

    CoNLLFeaturizer.annotate(model, tags, namespace, embeddings);

    assert (model.factors != null);
    for (GraphicalModel.Factor f : model.factors) {
      assert (f != null);
    }

    return model;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  // LOADING DATA FROM FILES
  ////////////////////////////////////////////////////////////////////////////////////////////

  public static class CoNLLSentence {
    public List<String> token = new ArrayList<>();
    public List<String> ner = new ArrayList<>();
    public List<String> pos = new ArrayList<>();
    public List<String> npchunk = new ArrayList<>();

    public CoNLLSentence(List<String> token, List<String> ner, List<String> pos, List<String> npchunk) {
      this.token = token;
      this.ner = ner;
      this.pos = pos;
      this.npchunk = npchunk;
    }
  }

  public List<CoNLLSentence> getSentences(String filename) throws IOException {
    List<CoNLLSentence> sentences = new ArrayList<>();

    List<String> tokens = new ArrayList<>();
    List<String> ner = new ArrayList<>();
    List<String> pos = new ArrayList<>();
    List<String> npchunk = new ArrayList<>();

    BufferedReader br = new BufferedReader(new FileReader(filename));

    String line;
    while ((line = br.readLine()) != null) {
      String[] parts = line.split("\t");
      if (parts.length == 4) {
        tokens.add(parts[0]);
        pos.add(parts[1]);
        npchunk.add(parts[2]);
        String tag = parts[3];
        if (tag.contains("-")) {
          ner.add(tag.split("-")[1]);
        } else {
          ner.add(tag);
        }
        if (parts[0].equals(".")) {
          sentences.add(new CoNLLSentence(tokens, ner, pos, npchunk));
          tokens = new ArrayList<>();
          ner = new ArrayList<>();
          pos = new ArrayList<>();
          npchunk = new ArrayList<>();
        }
      }
    }

    return sentences;
  }

  @SuppressWarnings("unchecked")
  public Map<String, double[]> getEmbeddings(String cacheFilename, List<CoNLLSentence> sentences) throws IOException, ClassNotFoundException {
    File f = new File(cacheFilename);
    Map<String, double[]> trimmedSet;

    if (!f.exists()) {
      trimmedSet = new HashMap<>();

      Map<String, double[]> massiveSet = loadEmbeddingsFromFile("../google-300.txt");
      log.info("Got massive embedding set size " + massiveSet.size());

      for (CoNLLSentence sentence : sentences) {
        for (String token : sentence.token) {
          if (massiveSet.containsKey(token)) {
            trimmedSet.put(token, massiveSet.get(token));
          }
        }
      }
      log.info("Got trimmed embedding set size " + trimmedSet.size());

      f.createNewFile();
      ObjectOutputStream oos = new ObjectOutputStream(new GZIPOutputStream(new FileOutputStream(cacheFilename)));
      oos.writeObject(trimmedSet);
      oos.close();

      log.info("Wrote trimmed set to file");
    } else {
      ObjectInputStream ois = new ObjectInputStream(new GZIPInputStream(new FileInputStream(cacheFilename)));
      trimmedSet = (Map<String, double[]>) ois.readObject();
      ois.close();
    }

    return trimmedSet;
  }

  private static Map<String, double[]> loadEmbeddingsFromFile(String filename) throws IOException {
    Map<String, double[]> embeddings = new HashMap<>();

    BufferedReader br = new BufferedReader(new FileReader(filename));

    int readLines = 0;

    String line = br.readLine();
    while ((line = br.readLine()) != null) {
      String[] parts = line.split(" ");

      if (parts.length == 302) {
        String token = parts[0];
        double[] embedding = new double[300];
        for (int i = 1; i < parts.length - 1; i++) {
          embedding[i - 1] = Double.parseDouble(parts[i]);
        }
        embeddings.put(token, embedding);
      }

      readLines++;
      if (readLines % 10000 == 0) {
        log.info("Read " + readLines + " lines");
      }
    }

    return embeddings;
  }
}