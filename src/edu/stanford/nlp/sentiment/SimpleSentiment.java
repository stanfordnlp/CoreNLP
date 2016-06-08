package edu.stanford.nlp.sentiment;


import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.QNMinimizer;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Lazy;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * A simple sentiment classifier, inspired by Sida's Naive Bayes SVM
 * paper.
 * The main goal of this class is to avoid the parse tree requirement of
 * the RNN approach at: {@link SentimentPipeline}.
 *
 * @author <a href="mailto:angeli@cs.stanford.edu">Gabor Angeli</a>
 */
public class SimpleSentiment {
  /**
   * A logger for this class.
   */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SimpleSentiment.class);

  /** An appropriate pipeline object for featurizing training data */
  private static Lazy<StanfordCoreNLP> pipeline = Lazy.of(() -> {
    Properties props = new Properties();
    props.setProperty("annotators", "tokenize,ssplit,pos,lemma");
    props.setProperty("language", "english");
    props.setProperty("ssplit.isOneSentence", "true");
    props.setProperty("tokenize.class", "PTBTokenizer");
    props.setProperty("tokenize.language", "en");
    return new StanfordCoreNLP(props);
  });


  /**
   * A single datum (presumably read from a training file) that encodes
   * a sentence and an associated sentiment value.
   */
  private static class SentimentDatum {
    /** The sentence to classify. */
    public final String sentence;
    /** The sentiment class of the sentence */
    public final String sentiment;

    /** The trivial constructor */
    private SentimentDatum(String sentence, String sentiment) {
      this.sentence = sentence;
      this.sentiment = sentiment;
    }

    /** Annotate this datum, and return it as a CoreMap. */
    CoreMap asCoreMap() {
      Annotation ann = new Annotation(sentence);
      pipeline.get().annotate(ann);
      return ann.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    }
  }

  /** A simple regex for alpha words. That is, words matching [a-zA-Z] */
  private static final Pattern alpha = Pattern.compile("[a-zA-Z]+");


  /**
   * The underlying classifier we have trained to detect sentiment.
   */
  private final Classifier<String, String> impl;


  /**
   * Featurize a given sentence.
   *
   * @param sentence The sentence to featurize.
   *
   * @return A counter encoding the featurized sentence.
   */
  private static Counter<String> featurize(CoreMap sentence) {
    ClassicCounter<String> features = new ClassicCounter<>();
    String lastLemma = "^";
    for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
      features.incrementCount(token.lemma());
      String lemma = token.lemma();
      if (alpha.matcher(lemma).matches()) {
        features.incrementCount(lastLemma + "__" + lemma);
        lastLemma = lemma;
      }
    }
    features.incrementCount(lastLemma + "__$");
    return features;
  }


  /**
   * Create a new sentiment classifier object.
   * This is really just a shallow wrapper around a classifier...
   *
   * @param impl The classifier doing the heavy lifting.
   */
  private SimpleSentiment(Classifier<String, String> impl) {
    this.impl = impl;
  }


  /**
   * Get the sentiment of a sentence.
   *
   * @param sentence The sentence as a core map.
   *                 POS tags and Lemmas are a prerequisite.
   *                 See {@link edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation} and
   *                 {@link edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation}.
   *
   * @return The sentiment class of this sentence.
   */
  public String classify(CoreMap sentence) {
    Counter<String> features = featurize(sentence);
    RVFDatum<String, String> datum = new RVFDatum<>(features);
    return impl.classOf(datum);
  }


  /**
   * @see SimpleSentiment#classify(CoreMap)
   */
  public String classify(String text) {
    Annotation ann = new Annotation(text);
    pipeline.get().annotate(ann);
    CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    Counter<String> features = featurize(sentence);
    RVFDatum<String, String> datum = new RVFDatum<>(features);
    return impl.classOf(datum);
  }


  /**
   * Train a sentiment model from a set of data.
   *
   * @param data The data to train the model from.
   * @param modelLocation An optional location to save the model.
   *                      Note that this stream will be closed in this method,
   *                      and should not be written to thereafter.
   *
   * @return A sentiment classifier, ready to use.
   */
  @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "ConstantConditions"})
  public static SimpleSentiment train(
      Stream<SentimentDatum> data,
      Optional<OutputStream> modelLocation) {

    // Some useful variables configuring how we train
    boolean useL1 = true;
    double sigma = 1.0;
    int featureCountThreshold = 0;

    // Featurize the data
    forceTrack("Featurizing");
    RVFDataset<String, String> dataset = new RVFDataset<>();
    data.parallel().unordered()
        .map(datum -> new RVFDatum<>(featurize(datum.asCoreMap()), datum.sentiment))
        .sequential()
        .forEach(dataset::add);
    endTrack("Featurizing");

    // Train the classifier
    forceTrack("Training");
    if (featureCountThreshold > 1) {
      dataset.applyFeatureCountThreshold(featureCountThreshold);
    }
    LinearClassifierFactory<String, String> factory = new LinearClassifierFactory<>();
    factory.setVerbose(true);
    try {
      factory.setMinimizerCreator(() -> {
        QNMinimizer minimizer =  new QNMinimizer();
        if (useL1) {
          minimizer.useOWLQN(true, 1 / (sigma * sigma));
        } else {
          factory.setSigma(sigma);
        }
        return minimizer;
      });
    } catch (Exception ignored) {}
    factory.setSigma(sigma);
    LinearClassifier<String, String> classifier = factory.trainClassifier(dataset);

    // Optionally save the model
    modelLocation.ifPresent(stream -> {
      try {
        ObjectOutputStream oos = new ObjectOutputStream(stream);
        oos.writeObject(classifier);
        oos.close();
      } catch (IOException e) {
        log.err("Could not save model to stream!");
      }
    });
    endTrack("Training");

    // Evaluate the model
    forceTrack("Evaluating");
    factory.setVerbose(false);
    double sumAccuracy = 0.0;
    int numFolds = 10;
    for (int fold = 0; fold < numFolds; ++fold) {
      Pair<GeneralDataset<String, String>, GeneralDataset<String, String>> trainTest = dataset.splitOutFold(fold, numFolds);
      sumAccuracy += factory.trainClassifier(trainTest.first).evaluateAccuracy(trainTest.second);
    }
    double aveAccuracy = sumAccuracy / ((double) numFolds);
    log.info("----------");
    log.info("" + numFolds + "-fold accuracy: " + aveAccuracy);
    log.info("----------");
    endTrack("Evaluating");

    // Return
    return new SimpleSentiment(classifier);
  }



  private static Stream<SentimentDatum> imdb(String path, String label) {
    return StreamSupport.stream(
        IOUtils.iterFilesRecursive(new File(path)).spliterator(), true)
        .map(x -> {
          try {
            return new SentimentDatum(IOUtils.slurpFile(x), label);
          } catch (IOException e) {
            throw new RuntimeIOException(e);
          }
        });
  }


  public static void main(String[] args) {
    RedwoodConfiguration.standard().apply();
    startTrack("main");

    Stream<SentimentDatum> data = Stream.concat(
        Stream.concat(
            imdb("/users/gabor/tmp/aclImdb/train/pos", "POSITIVE"),
            imdb("/users/gabor/tmp/aclImdb/train/neg", "NEGATIVE")),
        Stream.concat(
            imdb("/users/gabor/tmp/aclImdb/test/pos", "POSITIVE"),
            imdb("/users/gabor/tmp/aclImdb/test/neg", "NEGATIVE")
        ));

    SimpleSentiment classifier = SimpleSentiment.train(data, Optional.empty());
    log.info(classifier.classify("I think life is great"));
    endTrack("main");

    // 85.8
  }

}
