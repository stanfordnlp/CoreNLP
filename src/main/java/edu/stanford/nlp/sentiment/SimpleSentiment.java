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
import edu.stanford.nlp.simple.Document;
import edu.stanford.nlp.simple.SentimentClass;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Lazy;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
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
    public final SentimentClass sentiment;

    /** The trivial constructor */
    private SentimentDatum(String sentence, SentimentClass sentiment) {
      this.sentence = sentence;
      this.sentiment = sentiment;
    }

    /** Annotate this datum, and return it as a CoreMap. */
    CoreMap asCoreMap() {
      Annotation ann;
      if ("".equals(sentence.trim())) {
        switch (sentiment) {
          case VERY_POSITIVE:
            ann = new Annotation("cats are super awesome!");
            break;
          case POSITIVE:
            ann = new Annotation("cats are great");
            break;
          case NEUTRAL:
            ann = new Annotation("cats have tails");
            break;
          case NEGATIVE:
            ann = new Annotation("cats suck");
            break;
          case VERY_NEGATIVE:
            ann = new Annotation("cats are literally the worst, I can't even.");
            break;
          default:
            throw new IllegalStateException();
        }
      } else {
        ann = new Annotation(sentence);
      }
      pipeline.get().annotate(ann);
      return ann.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    }
  }

  /** A simple regex for alpha words. That is, words matching [a-zA-Z] */
  private static final Pattern alpha  = Pattern.compile("[a-zA-Z]+");
  /** A simple regex for number tokens. That is, words matching [0-9] */
  private static final Pattern number = Pattern.compile("[0-9]+");


  /**
   * The underlying classifier we have trained to detect sentiment.
   */
  private final Classifier<SentimentClass, String> impl;


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
      String lemma = token.lemma().toLowerCase();
      if (number.matcher(lemma).matches()) {
        features.incrementCount("**num**");
      } else {
        features.incrementCount(lemma);

      }
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
  private SimpleSentiment(Classifier<SentimentClass, String> impl) {
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
  public SentimentClass classify(CoreMap sentence) {
    Counter<String> features = featurize(sentence);
    RVFDatum<SentimentClass, String> datum = new RVFDatum<>(features);
    return impl.classOf(datum);
  }


  /**
   * @see SimpleSentiment#classify(CoreMap)
   */
  public SentimentClass classify(String text) {
    Annotation ann = new Annotation(text);
    pipeline.get().annotate(ann);
    CoreMap sentence = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    Counter<String> features = featurize(sentence);
    RVFDatum<SentimentClass, String> datum = new RVFDatum<>(features);
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
    int featureCountThreshold = 5;

    // Featurize the data
    forceTrack("Featurizing");
    RVFDataset<SentimentClass, String> dataset = new RVFDataset<>();
    AtomicInteger datasize = new AtomicInteger(0);
    Counter<SentimentClass> distribution = new ClassicCounter<>();
    data.unordered().parallel()
        .map(datum -> {
          if (datasize.incrementAndGet() % 10000 == 0) {
            log("Added " + datasize.get() + " datums");
          }
          return new RVFDatum<>(featurize(datum.asCoreMap()), datum.sentiment);
        })
        .forEach(x -> {
            synchronized (dataset) {
              distribution.incrementCount(x.label());
              dataset.add(x);
          }
        });
    endTrack("Featurizing");

    // Print label distribution
    startTrack("Distribution");
    for (SentimentClass label : SentimentClass.values()) {
      log(String.format("%7d", (int) distribution.getCount(label)) + "   " + label);
    }
    endTrack("Distribution");

    // Train the classifier
    forceTrack("Training");
    if (featureCountThreshold > 1) {
      dataset.applyFeatureCountThreshold(featureCountThreshold);
    }
    dataset.randomize(42L);
    LinearClassifierFactory<SentimentClass, String> factory = new LinearClassifierFactory<>();
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
    LinearClassifier<SentimentClass, String> classifier = factory.trainClassifier(dataset);

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
    Counter<SentimentClass> sumP = new ClassicCounter<>();
    Counter<SentimentClass> sumR = new ClassicCounter<>();
    int numFolds = 4;
    for (int fold = 0; fold < numFolds; ++fold) {
      Pair<GeneralDataset<SentimentClass, String>, GeneralDataset<SentimentClass, String>> trainTest = dataset.splitOutFold(fold, numFolds);
      LinearClassifier<SentimentClass, String> foldClassifier = factory.trainClassifierWithInitialWeights(trainTest.first, classifier);  // convex objective, so this should be OK
      sumAccuracy += foldClassifier.evaluateAccuracy(trainTest.second);
      for (SentimentClass label : SentimentClass.values()) {
        Pair<Double, Double> pr = foldClassifier.evaluatePrecisionAndRecall(trainTest.second, label);
        sumP.incrementCount(label, pr.first);
        sumP.incrementCount(label, pr.second);
      }
    }
    DecimalFormat df = new DecimalFormat("0.000%");
    log.info("----------");
    double aveAccuracy = sumAccuracy / ((double) numFolds);
    log.info("" + numFolds + "-fold accuracy: " + df.format(aveAccuracy));
    log.info("");
    for (SentimentClass label : SentimentClass.values()) {
      double p = sumP.getCount(label) / numFolds;
      double r = sumR.getCount(label) / numFolds;
      log.info(label + " (P)  = " + df.format(p));
      log.info(label + " (R)  = " + df.format(r));
      log.info(label + " (F1) = " + df.format(2 * p * r / (p + r)));
      log.info("");
    }
    log.info("----------");
    endTrack("Evaluating");

    // Return
    return new SimpleSentiment(classifier);
  }



  private static Stream<SentimentDatum> imdb(String path, SentimentClass label) {
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


  private static Stream<SentimentDatum> stanford(String path) {
    return StreamSupport.stream(
        IOUtils.readLines(path).spliterator(), true
    ).map(line -> {
      String[] fields = line.split("\t");
      if (fields.length < 4 || "Sentiment".equalsIgnoreCase(fields[3]) ||
          fields[2].equals("")) {
        return new SentimentDatum("Cats have tails", SentimentClass.NEUTRAL);
      } else {
        String text = fields[2];
        int sentiment = Integer.parseInt(fields[3]);
        return new SentimentDatum(text, SentimentClass.fromInt(sentiment));
      }
    });
  }

  private static Stream<SentimentDatum> twitter(String path) {
    return StreamSupport.stream(
        IOUtils.readLines(path).spliterator(), true
    ).map(line -> {
      List<String> fields = Arrays.asList(line.split(","));
      if (fields.size() < 3 || "Sentiment".equalsIgnoreCase(fields.get(1)) ||
          fields.get(3).equals("")) {
        return new SentimentDatum("Cats have tails", SentimentClass.NEUTRAL);
      } else {
        int sentiment = Integer.parseInt(fields.get(1));
        String text = StringUtils.join(fields.subList(3, fields.size()), ",");
        return new SentimentDatum(text, SentimentClass.fromInt(sentiment));
      }
    });
  }


  private static Stream<SentimentDatum> unlabelled(String path) throws IOException {
    return StreamSupport.stream(
        IOUtils.iterFilesRecursive(new File(path)).spliterator(), true)
        .flatMap(x -> new Document(IOUtils.slurpReader(IOUtils.readerFromFile(x)))
            .sentences()
            .stream()
            .map(y -> new SentimentDatum(y.text(), SentimentClass.NEUTRAL)));
  }


  public static void main(String[] args) throws IOException {
    RedwoodConfiguration.standard().apply();
    startTrack("main");

    // Read the data
    Stream<SentimentDatum> data =
        Stream.concat(
            Stream.concat(
                Stream.concat(
                    imdb("/users/gabor/tmp/aclImdb/train/pos", SentimentClass.POSITIVE),
                    imdb("/users/gabor/tmp/aclImdb/train/neg", SentimentClass.NEGATIVE)),
                Stream.concat(
                    imdb("/users/gabor/tmp/aclImdb/test/pos", SentimentClass.POSITIVE),
                    imdb("/users/gabor/tmp/aclImdb/test/neg", SentimentClass.NEGATIVE)
                )
            ),
            Stream.concat(
                Stream.concat(
                    stanford("/users/gabor/tmp/train.tsv"),
                    stanford("/users/gabor/tmp/test.tsv")
                ),
                Stream.concat(
                    twitter("/users/gabor/tmp/twitter.csv"),
                    unlabelled("/users/gabor/tmp/wikipedia")
                )
            )
        );


    // Train the model
    OutputStream stream = IOUtils.getFileOutputStream("/users/gabor/tmp/model.ser.gz");
    SimpleSentiment classifier = SimpleSentiment.train(data, Optional.of(stream));
    stream.close();

    log.info(classifier.classify("I think life is great"));
    endTrack("main");

    // 85.8
  }

}
