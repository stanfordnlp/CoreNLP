package edu.stanford.nlp.ie;


import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.optimization.*;
import edu.stanford.nlp.pipeline.DefaultPaths;
import edu.stanford.nlp.simple.Sentence;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.logging.Redwood;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * A relation extractor to work with Victor's new KBP data.
 */
@SuppressWarnings("FieldCanBeLocal")
public class KBPStatisticalExtractor implements KBPRelationExtractor, Serializable {
  private static final long serialVersionUID = 1L;

  @ArgumentParser.Option(name="train", gloss="The dataset to train on")
  public static File TRAIN_FILE = new File("train.conll");

  @ArgumentParser.Option(name="test", gloss="The dataset to test on")
  public static File TEST_FILE = new File("test.conll");

  @ArgumentParser.Option(name="model", gloss="The dataset to test on")
  public static String MODEL_FILE = DefaultPaths.DEFAULT_KBP_CLASSIFIER;

  @ArgumentParser.Option(name="predictions", gloss="Dump model predictions to this file")
  public static Optional<String> PREDICTIONS = Optional.empty();

  private enum MinimizerType{ QN, SGD, HYBRID, L1 }
  @ArgumentParser.Option(name="minimizer", gloss="The minimizer to use for training the classifier")
  private static MinimizerType minimizer = MinimizerType.L1;

  @ArgumentParser.Option(name="feature_threshold", gloss="The minimum number of times to see a feature to count it")
  private static int FEATURE_THRESHOLD = 0;

  @ArgumentParser.Option(name="sigma", gloss="The regularizer for the classifier")
  private static double SIGMA = 1.0;


  private static final Redwood.RedwoodChannels log = Redwood.channels(KBPStatisticalExtractor.class);


  /**
   * A list of triggers for top employees.
   */
  private static final Set<String> TOP_EMPLOYEE_TRIGGERS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("executive");
    add("chairman");
    add("president");
    add("chief");
    add("head");
    add("general");
    add("ceo");
    add("officer");
    add("founder");
    add("found");
    add("leader");
    add("vice");
    add("king");
    add("prince");
    add("manager");
    add("host");
    add("minister");
    add("adviser");
    add("boss");
    add("chair");
    add("ambassador");
    add("shareholder");
    add("star");
    add("governor");
    add("investor");
    add("representative");
    add("dean");
    add("commissioner");
    add("deputy");
    add("commander");
    add("scientist");
    add("midfielder");
    add("speaker");
    add("researcher");
    add("editor");
    add("chancellor");
    add("fellow");
    add("leadership");
    add("diplomat");
    add("attorney");
    add("associate");
    add("striker");
    add("pilot");
    add("captain");
    add("banker");
    add("mayer");
    add("premier");
    add("producer");
    add("architect");
    add("designer");
    add("major");
    add("advisor");
    add("presidency");
    add("senator");
    add("specialist");
    add("faculty");
    add("monitor");
    add("chairwoman");
    add("mayor");
    add("columnist");
    add("mediator");
    add("prosecutor");
    add("entrepreneur");
    add("creator");
    add("superstar");
    add("commentator");
    add("principal");
    add("operative");
    add("businessman");
    add("peacekeeper");
    add("investigator");
    add("coordinator");
    add("knight");
    add("lawmaker");
    add("justice");
    add("publisher");
    add("playmaker");
    add("moderator");
    add("negotiator");
  }});


  /**
   * <p>
   *   Often, features fall naturally into <i>feature templates</i> and their associated value.
   *   For example, unigram features have a feature template of unigram, and a feature value of the word
   *   in question.
   * </p>
   *
   * <p>
   *   This method is a convenience convention for defining these feature template / value pairs.
   *   The advantage of using the method is that it allows for easily finding the feature template for a
   *   given feature value; thus, you can do feature selection post-hoc on the String features by splitting
   *   out certain feature templates.
   * </p>
   *
   * <p>
   *   Note that spaces in the feature value are also replaced with a special character, mostly out of
   *   paranoia.
   * </p>
   *
   * @param features The feature counter we are updating.
   * @param featureTemplate The feature template to add a value to.
   * @param featureValue The value of the feature template. This is joined with the template, so it
   *                     need only be unique within the template.
   */
  private static void indicator(Counter<String> features, String featureTemplate, String featureValue) {
    features.incrementCount(featureTemplate + "ℵ" + featureValue.replace(' ', 'ˑ'));
  }

  /**
   * Get information from the span between the two mentions.
   * Canonically, get the words in this span.
   * For instance, for "Obama was born in Hawaii", this would return a list
   * "was born in" if the selector is {@code CoreLabel::token};
   * or "be bear in" if the selector is {@code CoreLabel::lemma}.
   *
   * @param input The featurizer input.
   * @param selector The field to compute for each element in the span. A good default is {@code CoreLabel::word} or {@code CoreLabel::token}
   * @param <E> The type of element returned by the selector.
   *
   * @return A list of elements between the two mentions.
   */
  @SuppressWarnings("unchecked")
  private  static <E> List<E> spanBetweenMentions(KBPInput input, Function<CoreLabel, E> selector) {
    List<CoreLabel> sentence = input.sentence.asCoreLabels(Sentence::lemmas, Sentence::nerTags);
    Span subjSpan = input.subjectSpan;
    Span objSpan = input.objectSpan;

    // Corner cases
    if (Span.overlaps(subjSpan, objSpan)) {
      return Collections.emptyList();
    }

    // Get the range between the subject and object
    int begin = subjSpan.end();
    int end = objSpan.start();
    if (begin > end) {
      begin = objSpan.end();
      end = subjSpan.start();
    }
    if (begin > end) {
      throw new IllegalArgumentException("Gabor sucks at logic and he should feel bad about it: " + subjSpan + " and " + objSpan);
    } else if (begin == end) {
      return Collections.emptyList();
    }

    // Compute the return value
    List<E> rtn = new ArrayList<>();
    for (int i = begin; i < end; ++i) {
      rtn.add(selector.apply(sentence.get(i)));
    }
    return rtn;
  }

  /**
   * <p>
   *   Span features often only make sense if the subject and object are positioned at the correct ends of the span.
   *   For example, "x is the son of y" and "y is the son of x" have the same span feature, but mean different things
   *   depending on where x and y are.
   * </p>
   *
   * <p>
   *   This is a simple helper to position a dummy subject and object token appropriately.
   * </p>
   *
   * @param input The featurizer input.
   * @param feature The span feature to augment.
   *
   * @return The augmented feature.
   */
  private static String withMentionsPositioned(KBPInput input, String feature) {
    if (input.subjectSpan.isBefore(input.objectSpan)) {
      return "+__SUBJ__ " + feature + " __OBJ__";
    } else {
      return "__OBJ__ " + feature + " __SUBJ__";
    }
  }

  @SuppressWarnings("UnusedParameters")
  private static void denseFeatures(KBPInput input, Sentence sentence, ClassicCounter<String> feats) {
    boolean subjBeforeObj = input.subjectSpan.isBefore(input.objectSpan);

    // Type signature
    indicator(feats, "type_signature", input.subjectType + "," + input.objectType);

    // Relative position
    indicator(feats, "subj_before_obj", subjBeforeObj ? "y" : "n");
  }

  @SuppressWarnings("UnusedParameters")
  private static void surfaceFeatures(KBPInput input, Sentence simpleSentence, ClassicCounter<String> feats) {
    List<String> lemmaSpan = spanBetweenMentions(input, CoreLabel::lemma);
    List<String> nerSpan = spanBetweenMentions(input, CoreLabel::ner);
    List<String> posSpan = spanBetweenMentions(input, CoreLabel::tag);

    // Unigram features of the sentence
    List<CoreLabel> tokens = input.sentence.asCoreLabels(Sentence::lemmas, Sentence::nerTags);
    for (CoreLabel token : tokens) {
      indicator(feats, "sentence_unigram", token.lemma());
    }

    // Full lemma span ( -0.3 F1 )
//    if (lemmaSpan.size() <= 5) {
//      indicator(feats, "full_lemma_span", withMentionsPositioned(input, StringUtils.join(lemmaSpan, " ")));
//    }

    // Lemma n-grams
    String lastLemma = "_^_";
    for (String lemma : lemmaSpan) {
      indicator(feats, "lemma_bigram", withMentionsPositioned(input, lastLemma + " " + lemma));
      indicator(feats, "lemma_unigram", withMentionsPositioned(input, lemma));
      lastLemma = lemma;
    }
    indicator(feats, "lemma_bigram", withMentionsPositioned(input, lastLemma + " _$_"));

    // NER + lemma bi-grams
    for (int i = 0; i < lemmaSpan.size() - 1; ++i) {
      if (!"O".equals(nerSpan.get(i)) && "O".equals(nerSpan.get(i + 1)) && "IN".equals(posSpan.get(i + 1))) {
        indicator(feats, "ner/lemma_bigram", withMentionsPositioned(input, nerSpan.get(i) + " " + lemmaSpan.get(i + 1)));
      }
      if (!"O".equals(nerSpan.get(i + 1)) && "O".equals(nerSpan.get(i)) && "IN".equals(posSpan.get(i))) {
        indicator(feats, "ner/lemma_bigram", withMentionsPositioned(input, lemmaSpan.get(i) + " " + nerSpan.get(i + 1)));
      }
    }

    // Distance between mentions
    String distanceBucket = ">10";
    if (lemmaSpan.size() == 0) {
      distanceBucket = "0";
    } else if (lemmaSpan.size() <= 3) {
      distanceBucket = "<=3";
    } else if (lemmaSpan.size() <= 5) {
      distanceBucket = "<=5";
    } else if (lemmaSpan.size() <= 10) {
      distanceBucket = "<=10";
    } else if (lemmaSpan.size() <= 15) {
      distanceBucket = "<=15";
    }
    indicator(feats, "distance_between_entities_bucket", distanceBucket);

    // Punctuation features
    int numCommasInSpan = 0;
    int numQuotesInSpan = 0;
    int parenParity = 0;
    for (String lemma : lemmaSpan) {
      if (lemma.equals(",")) { numCommasInSpan += 1; }
      if (lemma.equals("\"") || lemma.equals("``") || lemma.equals("''")) {
        numQuotesInSpan += 1;
      }
      if (lemma.equals("(") || lemma.equals("-LRB-")) { parenParity += 1; }
      if (lemma.equals(")") || lemma.equals("-RRB-")) { parenParity -= 1; }
    }
    indicator(feats, "comma_parity", numCommasInSpan % 2 == 0 ? "even" : "odd");
    indicator(feats, "quote_parity", numQuotesInSpan % 2 == 0 ? "even" : "odd");
    indicator(feats, "paren_parity", "" + parenParity);

    // Is broken by entity
    Set<String> intercedingNERTags = nerSpan.stream().filter(ner -> !ner.equals("O")).collect(Collectors.toSet());
    if (!intercedingNERTags.isEmpty()) {
      indicator(feats, "has_interceding_ner", "t");
    }
    for (String ner : intercedingNERTags) {
      indicator(feats, "interceding_ner", ner);
    }

    // Left and right context
    List<CoreLabel> sentence = input.sentence.asCoreLabels(Sentence::nerTags);
    if (input.subjectSpan.start() == 0) {
      indicator(feats, "subj_left", "^");
    } else {
      indicator(feats, "subj_left", sentence.get(input.subjectSpan.start() - 1).lemma());
    }
    if (input.subjectSpan.end() == sentence.size()) {
      indicator(feats, "subj_right", "$");
    } else {
      indicator(feats, "subj_right", sentence.get(input.subjectSpan.end()).lemma());
    }
    if (input.objectSpan.start() == 0) {
      indicator(feats, "obj_left", "^");
    } else {
      indicator(feats, "obj_left", sentence.get(input.objectSpan.start() - 1).lemma());
    }
    if (input.objectSpan.end() == sentence.size()) {
      indicator(feats, "obj_right", "$");
    } else {
      indicator(feats, "obj_right", sentence.get(input.objectSpan.end()).lemma());
    }

    // Skip-word patterns
    if (lemmaSpan.size() == 1 && input.subjectSpan.isBefore(input.objectSpan)) {
      String left = input.subjectSpan.start() == 0 ? "^" : sentence.get(input.subjectSpan.start() - 1).lemma();
      indicator(feats, "X<subj>Y<obj>", left + "_" + lemmaSpan.get(0));
    }
  }


  private static void dependencyFeatures(KBPInput input, Sentence sentence, ClassicCounter<String> feats) {
    int subjectHead = sentence.algorithms().headOfSpan(input.subjectSpan);
    int objectHead = sentence.algorithms().headOfSpan(input.objectSpan);

//    indicator(feats, "subject_head", sentence.lemma(subjectHead));
//    indicator(feats, "object_head", sentence.lemma(objectHead));
    if (input.objectType.isRegexNERType) {
      indicator(feats, "object_head", sentence.lemma(objectHead));
    }

    // Get the dependency path
    List<String> depparsePath = sentence.algorithms().dependencyPathBetween(subjectHead, objectHead, Optional.of(Sentence::lemmas));

    // Chop out appos edges
    if (depparsePath.size() > 3) {
      List<Integer> apposChunks = new ArrayList<>();
      for (int i = 1; i < depparsePath.size() - 1; ++i) {
        if ("-appos->".equals(depparsePath.get(i))) {
          if (i != 1) {
            apposChunks.add(i - 1);
          }
          apposChunks.add(i);
        } else if ("<-appos-".equals(depparsePath.get(i))) {
          if (i < depparsePath.size() - 1) {
            apposChunks.add(i + 1);
          }
          apposChunks.add(i);
        }
      }
      Collections.sort(apposChunks);
      for (int i = apposChunks.size() - 1; i >= 0; --i) {
        depparsePath.remove(i);
      }
    }

    // Dependency path distance buckets
    String distanceBucket = ">10";
    if (depparsePath.size() == 3) {
      distanceBucket = "<=3";
    } else if (depparsePath.size() <= 5) {
      distanceBucket = "<=5";
    } else if (depparsePath.size() <= 7) {
      distanceBucket = "<=7";
    } else if (depparsePath.size() <= 9) {
      distanceBucket = "<=9";
    } else if (depparsePath.size() <= 13) {
      distanceBucket = "<=13";
    } else if (depparsePath.size() <= 17) {
      distanceBucket = "<=17";
    }
    indicator(feats, "parse_distance_between_entities_bucket", distanceBucket);

    // Add the path features
    if (depparsePath.size() > 2 && depparsePath.size() <= 7) {
//      indicator(feats, "deppath", StringUtils.join(depparsePath.subList(1, depparsePath.size() - 1), ""));
//      indicator(feats, "deppath_unlex", StringUtils.join(depparsePath.subList(1, depparsePath.size() - 1).stream().filter(x -> x.startsWith("-") || x.startsWith("<")), ""));
      indicator(feats, "deppath_w/tag",
          sentence.posTag(subjectHead) + StringUtils.join(depparsePath.subList(1, depparsePath.size() - 1), "") + sentence.posTag(objectHead));
      indicator(feats, "deppath_w/ner",
          input.subjectType + StringUtils.join(depparsePath.subList(1, depparsePath.size() - 1), "") + input.objectType);
    }

    // Add the edge features
    //noinspection Convert2streamapi
    for (String node : depparsePath) {
      if (!node.startsWith("-") && !node.startsWith("<-")) {
        indicator(feats, "deppath_word", node);
      }
    }
    for (int i = 0; i < depparsePath.size() - 1; ++i) {
      indicator(feats, "deppath_edge", depparsePath.get(i) + depparsePath.get(i + 1));
    }
    for (int i = 0; i < depparsePath.size() - 2; ++i) {
      indicator(feats, "deppath_chunk", depparsePath.get(i) + depparsePath.get(i + 1) + depparsePath.get(i + 2));
    }
  }


  @SuppressWarnings("UnusedParameters")
  private static void relationSpecificFeatures(KBPInput input, Sentence sentence, ClassicCounter<String> feats) {
    if (input.objectType.equals(KBPRelationExtractor.NERTag.NUMBER)) {
      // Bucket the object value if it is a number
      // This is to prevent things like "age:9000" and to soft penalize "age:one"
      // The following features are extracted:
      //   1. Whether the object parses as a number (should always be true)
      //   2. Whether the object is an integer
      //   3. If the object is an integer, around what value is it (bucketed around common age values)
      //   4. Was the number spelled out, or written as a numeric number
      try {
        Number number = NumberNormalizer.wordToNumber(input.getObjectText());
        if (number != null) {
          indicator(feats, "obj_parsed_as_num", "t");
          if (number.equals(number.intValue())) {
            indicator(feats, "obj_isint", "t");
            int numAsInt = number.intValue();
            String bucket = "<0";
            if (numAsInt == 0) {
              bucket = "0";
            } else if (numAsInt == 1) {
              bucket = "1";
            } else if (numAsInt < 5) {
              bucket = "<5";
            } else if (numAsInt < 18) {
              bucket = "<18";
            } else if (numAsInt < 25) {
              bucket = "<25";
            } else if (numAsInt < 50) {
              bucket = "<50";
            } else if (numAsInt < 80) {
              bucket = "<80";
            } else if (numAsInt < 125) {
              bucket = "<125";
            } else if (numAsInt >= 100) {
              bucket = ">125";
            }
            indicator(feats, "obj_number_bucket", bucket);
          } else {
            indicator(feats, "obj_isint", "f");
          }
          if (input.getObjectText().replace(",", "").equalsIgnoreCase(number.toString())) {
            indicator(feats, "obj_spelledout_num", "f");
          } else {
            indicator(feats, "obj_spelledout_num", "t");
          }
        } else {
          indicator(feats, "obj_parsed_as_num", "f");
        }
      } catch (NumberFormatException e) {
        indicator(feats, "obj_parsed_as_num", "f");
      }
      // Special case dashes and the String "one"
      if (input.getObjectText().contains("-")) {
        indicator(feats, "obj_num_has_dash", "t");
      } else {
        indicator(feats, "obj_num_has_dash", "f");
      }
      if (input.getObjectText().equalsIgnoreCase("one")) {
        indicator(feats, "obj_num_is_one", "t");
      } else {
        indicator(feats, "obj_num_is_one", "f");
      }
    }

    if (
        (input.subjectType == KBPRelationExtractor.NERTag.PERSON && input.objectType.equals(KBPRelationExtractor.NERTag.ORGANIZATION)) ||
            (input.subjectType == KBPRelationExtractor.NERTag.ORGANIZATION && input.objectType.equals(KBPRelationExtractor.NERTag.PERSON))
        ) {
      // Try to capture some denser features for employee_of
      // These are:
      //   1. Whether a TITLE tag occurs either before, after, or inside the relation span
      //   2. Whether a top employee trigger occurs either before, after, or inside the relation span
      Span relationSpan = Span.union(input.subjectSpan, input.objectSpan);
      // (triggers before span)
      for (int i = Math.max(0, relationSpan.start() - 5); i < relationSpan.start(); ++i) {
        if ("TITLE".equals(sentence.nerTag(i))) {
          indicator(feats, "title_before", "t");
        }
        if (TOP_EMPLOYEE_TRIGGERS.contains(sentence.word(i).toLowerCase())) {
          indicator(feats, "top_employee_trigger_before", "t");
        }
      }
      // (triggers after span)
      for (int i = relationSpan.end(); i < Math.min(sentence.length(), relationSpan.end()); ++i) {
        if ("TITLE".equals(sentence.nerTag(i))) {
          indicator(feats, "title_after", "t");
        }
        if (TOP_EMPLOYEE_TRIGGERS.contains(sentence.word(i).toLowerCase())) {
          indicator(feats, "top_employee_trigger_after", "t");
        }
      }
      // (triggers inside span)
      for (int i : relationSpan) {
        if ("TITLE".equals(sentence.nerTag(i))) {
          indicator(feats, "title_inside", "t");
        }
        if (TOP_EMPLOYEE_TRIGGERS.contains(sentence.word(i).toLowerCase())) {
          indicator(feats, "top_employee_trigger_inside", "t");
        }
      }
    }
  }

  public static Counter<String> features(KBPInput input) {
    
    // Get useful variables
    ClassicCounter<String> feats = new ClassicCounter<>();
    if (Span.overlaps(input.subjectSpan, input.objectSpan) || input.subjectSpan.size() == 0 || input.objectSpan.size() == 0) {
      return new ClassicCounter<>();
    }

    // Actually featurize
    denseFeatures(input, input.sentence, feats);
    surfaceFeatures(input, input.sentence, feats);
    dependencyFeatures(input, input.sentence, feats);
    relationSpecificFeatures(input, input.sentence, feats);

    return feats;
  }


  /**
   * Create a classifier factory
   * @param <L> The label class of the factory
   * @return A factory to minimize a classifier against.
   */
  private static <L> LinearClassifierFactory<L, String> initFactory(double sigma) {
    LinearClassifierFactory<L,String> factory = new LinearClassifierFactory<>();
    Factory<Minimizer<DiffFunction>> minimizerFactory;
    switch(minimizer) {
      case QN:
        minimizerFactory = () -> new QNMinimizer(15);
        break;
      case SGD:
        minimizerFactory = () -> new SGDMinimizer<>(sigma, 100, 1000);
        break;
      case HYBRID:
        factory.useHybridMinimizerWithInPlaceSGD(100, 1000, sigma);
        minimizerFactory = () -> {
          SGDMinimizer<DiffFunction> firstMinimizer = new SGDMinimizer<>(sigma, 50, 1000);
          QNMinimizer secondMinimizer = new QNMinimizer(15);
          return new HybridMinimizer(firstMinimizer, secondMinimizer, 50);
        };
        break;
      case L1:
        minimizerFactory = () -> {
          try {
            return MetaClass.create("edu.stanford.nlp.optimization.OWLQNMinimizer").createInstance(sigma);
          } catch (Exception e) {
            log.err("Could not create l1 minimizer! Reverting to l2.");
            return new QNMinimizer(15);
          }
        };
        break;
      default:
        throw new IllegalStateException("Unknown minimizer: " + minimizer);
    }
    factory.setMinimizerCreator(minimizerFactory);
    return factory;
  }


  /**
   * Train a multinomial classifier off of the provided dataset.
   * @param dataset The dataset to train the classifier off of.
   * @return A classifier.
   */
  public static Classifier<String, String> trainMultinomialClassifier(
      GeneralDataset<String, String> dataset,
      int featureThreshold,
      double sigma) {
    // Set up the dataset and factory
    log.info("Applying feature threshold (" + featureThreshold + ")...");
    dataset.applyFeatureCountThreshold(featureThreshold);
    log.info("Randomizing dataset...");
    dataset.randomize(42l);
    log.info("Creating factory...");
    LinearClassifierFactory<String,String> factory = initFactory(sigma);

    // Train the final classifier
    log.info("BEGIN training");
    LinearClassifier<String, String> classifier = factory.trainClassifier(dataset);
    log.info("END training");

    // Debug
    Accuracy trainAccuracy = new Accuracy();
    for (Datum<String, String> datum : dataset) {
      String guess = classifier.classOf(datum);
      trainAccuracy.predict(Collections.singleton(guess), Collections.singleton(datum.label()));
    }
    log.info("Training accuracy:");
    log.info(trainAccuracy.toString());
    log.info("");

    // Return the classifier
    return classifier;
  }


  /**
   * The implementing classifier of this extractor.
   */
  public final Classifier<String, String> classifier;

  /**
   * Create a new KBP relation extractor, from the given implementing classifier.
   * @param classifier The implementing classifier.
   */
  public KBPStatisticalExtractor(Classifier<String, String> classifier) {
    this.classifier = classifier;
  }


  /**
   * Score the given input, returning both the classification decision and the
   * probability of that decision.
   * Note that this method will not return a relation which does not type check.
   *
   *
   * @param input The input to classify.
   * @return A pair with the relation we classified into, along with its confidence.
   */
  public Pair<String,Double> classify(KBPInput input) {
    RVFDatum<String, String> datum = new RVFDatum<>(features(input));
    Counter<String> scores =  classifier.scoresOf(datum);
    Counters.expInPlace(scores);
    Counters.normalize(scores);
    String best = Counters.argmax(scores);
    // While it doesn't type check, continue going down the list.
    // NO_RELATION is always an option somewhere in there, so safe to keep going...
    while (!NO_RELATION.equals(best) && scores.size() > 1 &&
        (!KBPRelationExtractor.RelationType.fromString(best).get().validNamedEntityLabels.contains(input.objectType) ||
         RelationType.fromString(best).get().entityType != input.subjectType) ) {
      scores.remove(best);
      Counters.normalize(scores);
      best = Counters.argmax(scores);
    }
    return Pair.makePair(best, scores.getCount(best));
  }


  public static void main(String[] args) throws IOException, ClassNotFoundException {
    RedwoodConfiguration.standard().apply();  // Disable SLF4J crap.
    ArgumentParser.fillOptions(KBPStatisticalExtractor.class, args);  // Fill command-line options

    // Load the test (or dev) data
    forceTrack("Test data");
    List<Pair<KBPInput, String>> testExamples = KBPRelationExtractor.readDataset(TEST_FILE);
    log.info("Read " + testExamples.size() + " examples");
    endTrack("Test data");

    // If we can't find an existing model, train one
    if (!IOUtils.existsInClasspathOrFileSystem(MODEL_FILE)) {
      forceTrack("Training data");
      List<Pair<KBPInput, String>> trainExamples = KBPRelationExtractor.readDataset(TRAIN_FILE);
      log.info("Read " + trainExamples.size() + " examples");
      log.info("" + trainExamples.stream().map(Pair::second).filter(NO_RELATION::equals).count() + " are " + NO_RELATION);
      endTrack("Training data");

      // Featurize + create the dataset
      forceTrack("Creating dataset");
      RVFDataset<String, String> dataset = new RVFDataset<>();
      final AtomicInteger i = new AtomicInteger(0);
      long beginTime = System.currentTimeMillis();
      trainExamples.stream().parallel().forEach(example -> {
        if (i.incrementAndGet() % 1000 == 0) {
          log.info("[" + Redwood.formatTimeDifference(System.currentTimeMillis() - beginTime) +
              "] Featurized " + i.get() + " / " + trainExamples.size() + " examples");
        }
        Counter<String> features = features(example.first);  // This takes a while per example
        synchronized (dataset) {
          dataset.add(new RVFDatum<>(features, example.second));
        }
      });
      trainExamples.clear();  // Free up some memory
      endTrack("Creating dataset");

      // Train the classifier
      log.info("Training classifier:");
      Classifier<String, String> classifier = trainMultinomialClassifier(dataset, FEATURE_THRESHOLD, SIGMA);
      dataset.clear();  // Free up some memory

      // Save the classifier
      IOUtils.writeObjectToFile(new KBPStatisticalExtractor(classifier), MODEL_FILE);
    }

    // Read either a newly-trained or pre-trained model
    Object model = IOUtils.readObjectFromURLOrClasspathOrFileSystem(MODEL_FILE);
    KBPStatisticalExtractor classifier;
    if (model instanceof Classifier) {
      //noinspection unchecked
      classifier = new KBPStatisticalExtractor((Classifier<String, String>) model);
    } else {
      classifier = ((KBPStatisticalExtractor) model);
    }

    // Evaluate the model
    classifier.computeAccuracy(testExamples.stream(), PREDICTIONS.map(x -> {
      try {
        return "stdout".equalsIgnoreCase(x) ? System.out : new PrintStream(new FileOutputStream(x));
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    }));
  }

}
