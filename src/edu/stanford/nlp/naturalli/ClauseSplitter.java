package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.GZIPOutputStream;

import edu.stanford.nlp.naturalli.ClauseSplitterSearchProblem.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * Just a convenience alias for a clause splitting search problem factory.
 * Mostly here to form a nice parallel with {@link edu.stanford.nlp.naturalli.ForwardEntailer}.
 *
 * @author Gabor Angeli
 */
public interface ClauseSplitter extends Function<SemanticGraph, ClauseSplitterSearchProblem> {

  /**
   * Train a clause searcher factory. That is, train a classifier for which arcs should be
   * new clauses.
   *
   * @param trainingData The training data. This is a stream of triples of:
   *                     <ol>
   *                       <li>The sentence containing a known extraction.</li>
   *                       <li>The span of the subject in the sentence, as a token span.</li>
   *                       <li>The span of the object in the sentence, as a token span.</li>
   *                     </ol>
   * @param featurizer The featurizer to use for this classifier.
   * @param options The training options.
   * @param modelPath The path to save the model to. This is useful for {@link ClauseSplitter#load(String)}.
   * @param trainingDataDump The path to save the training data, as a set of labeled featurized datums.
   *
   * @return A factory for creating searchers from a given dependency tree.
   */
  public static ClauseSplitter train(
      Stream<Triple<CoreMap, Span, Span>> trainingData,
      Featurizer featurizer,
      TrainingOptions options,
      Optional<File> modelPath,
      Optional<File> trainingDataDump) {
    // Parse options
    ClassifierFactory<Boolean, String, Classifier<Boolean,String>> classifierFactory = MetaClass.create(options.classifierFactory).createInstance();
    // Generally useful objects
    OpenIE openie = new OpenIE();
    Random rand = new Random(options.seed);
    WeightedDataset<Boolean, String> dataset = new WeightedDataset<>();
    AtomicInteger numExamplesProcessed = new AtomicInteger(0);
    final Optional<PrintWriter> datasetDumpWriter = trainingDataDump.map(file -> {
      try {
        return new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(trainingDataDump.get()))));
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    });

    // Step 1: Inference over training sentences
    forceTrack("Training inference");
    trainingData.forEach(triple -> {
      // Parse training datum
      CoreMap sentence = triple.first;
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      Span subjectSpan = Util.extractNER(tokens, triple.second);
      Span objectSpan = Util.extractNER(tokens, triple.third);
      // Create raw clause searcher (no classifier)
      ClauseSplitterSearchProblem problem = new ClauseSplitterSearchProblem(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
      Pointer<Boolean> anyCorrect = new Pointer<>(false);

      // Run search
      problem.search(fragmentAndScore -> {
        // Parse the search output
        List<Counter<String>> features = fragmentAndScore.second;
        Supplier<SentenceFragment> fragmentSupplier = fragmentAndScore.third;
        SentenceFragment fragment = fragmentSupplier.get();
        // Search for extractions
        List<RelationTriple> extractions = openie.relationsInFragments(openie.entailmentsFromClause(fragment));
        boolean correct = false;
        RelationTriple bestExtraction = null;
        for (RelationTriple extraction : extractions) {
          // Clean up the guesses
          Span subjectGuess = Util.extractNER(tokens, Span.fromValues(extraction.subject.get(0).index() - 1, extraction.subject.get(extraction.subject.size() - 1).index()));
          Span objectGuess = Util.extractNER(tokens, Span.fromValues(extraction.object.get(0).index() - 1, extraction.object.get(extraction.object.size() - 1).index()));
          // Check if it matches
          if ((subjectGuess.equals(subjectSpan) && objectGuess.equals(objectSpan)) ||
              (subjectGuess.equals(objectSpan) && objectGuess.equals(subjectSpan))
              ) {
            correct = true;
            anyCorrect.set(true);
            bestExtraction = extraction;
          } else if ((subjectGuess.contains(subjectSpan) && objectGuess.contains(objectSpan)) ||
              (subjectGuess.contains(objectSpan) && objectGuess.contains(subjectSpan))
              ) {
            correct = true;
            anyCorrect.set(true);
            if (bestExtraction == null) {
              bestExtraction = extraction;
            }
          } else {
            if (bestExtraction == null) {
              bestExtraction = extraction;
            }
            correct = false;
          }
        }
        // Process the datum
        if ((bestExtraction != null || fragment.length() == 1) && !features.isEmpty()) {
          for (Counter<String> decision : features) {
            // Compute datum
            RVFDatum<Boolean, String> datum = new RVFDatum<>(decision);
            datum.setLabel(correct);
            // Dump datum to debug log
            if (datasetDumpWriter.isPresent()) {
              datasetDumpWriter.get().println("" + correct + "\t" +
                  (decision == features.get(features.size() - 1)) + "\t" +
                  StringUtils.join(decision.entrySet().stream().map(entry -> "" + entry.getKey() + "->" + entry.getValue()), ";"));
            }
            // Add datum to dataset
            if (correct || rand.nextDouble() > (1.0 - options.negativeSubsampleRatio)) {  // Subsample
              dataset.add(datum, correct ? options.positiveDatumWeight : 1.0f);
            }
          }
        }
        return true;
      }, new ClassicCounter<>(), featurizer, 10000);
      // Debug info
      if (numExamplesProcessed.incrementAndGet() % 100 == 0) {
        log("processed " + numExamplesProcessed + " training sentences: " + dataset.size() + " datums");
      }
    });
    // Close dataset dump
    datasetDumpWriter.ifPresent(PrintWriter::close);
    endTrack("Training inference");

    // Step 2: Train classifier
    forceTrack("Training");
    Classifier<Boolean,String> fullClassifier = classifierFactory.trainClassifier(dataset);
    endTrack("Training");
    if (modelPath.isPresent()) {
      Pair<Classifier<Boolean,String>, Featurizer> toSave = Pair.makePair(fullClassifier, featurizer);
      try {
        IOUtils.writeObjectToFile(toSave, modelPath.get());
        log("SUCCESS: wrote model to " + modelPath.get().getPath());
      } catch (IOException e) {
        log("ERROR: failed to save model to path: " + modelPath.get().getPath());
        err(e);
      }
    }

    // Step 3: Check accuracy of classifier
    forceTrack("Training accuracy");
    dataset.randomize(options.seed);
    dumpAccuracy(fullClassifier, dataset);
    endTrack("Training accuracy");

    int numFolds = 5;
    forceTrack("" + numFolds + " fold cross-validation");
    for (int fold = 0; fold < numFolds; ++fold) {
      forceTrack("Fold " + (fold + 1));
      forceTrack("Training");
      Pair<GeneralDataset<Boolean, String>, GeneralDataset<Boolean, String>> foldData = dataset.splitOutFold(fold, numFolds);
      Classifier<Boolean, String> classifier = classifierFactory.trainClassifier(foldData.first);
      endTrack("Training");
      forceTrack("Test");
      dumpAccuracy(classifier, foldData.second);
      endTrack("Test");
      endTrack("Fold " + (fold + 1));
    }
    endTrack("" + numFolds + " fold cross-validation");


    // Step 5: return factory
    return tree -> new ClauseSplitterSearchProblem(tree, Optional.of(fullClassifier), Optional.of(featurizer));
  }



  /**
   * A helper function for training with the default featurizer and training options.
   *
   * @see ClauseSplitter#train(Stream, Featurizer, TrainingOptions, Optional, Optional)
   */
  public static ClauseSplitter train(
      Stream<Triple<CoreMap, Span, Span>> trainingData,
      File modelPath,
      File trainingDataDump) {
    // Train
    return train(trainingData, ClauseSplitterSearchProblem.DEFAULT_FEATURIZER, new TrainingOptions(), Optional.of(modelPath), Optional.of(trainingDataDump));
  }


  /**
   * Load a factory model from a given path. This can be trained with
   * {@link ClauseSplitter#train(Stream, Featurizer, TrainingOptions, Optional, Optional)}.
   *
   * @return A function taking a dependency tree, and returning a clause searcher.
   */
  public static ClauseSplitter load(String serializedModel) throws IOException {
    try {
      System.err.println("Loading clause searcher from " + serializedModel + " ...");
      Pair<Classifier<Boolean,String>, Featurizer> data = IOUtils.readObjectFromURLOrClasspathOrFileSystem(serializedModel);
      return tree -> new ClauseSplitterSearchProblem(tree, Optional.of(data.first), Optional.of(data.second));
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Invalid model at path: " + serializedModel, e);
    }
  }

  /**
   * A helper function for dumping the accuracy of the trained classifier.
   *
   * @param classifier The classifier to evaluate.
   * @param dataset The dataset to evaluate the classifier on.
   */
  public static void dumpAccuracy(Classifier<Boolean, String> classifier, GeneralDataset<Boolean, String> dataset) {
    DecimalFormat df = new DecimalFormat("0.000");
    log("size:       " + dataset.size());
    log("true count: " + StreamSupport.stream(dataset.spliterator(), false).filter(RVFDatum::label).collect(Collectors.toList()).size());
    Pair<Double, Double> pr = classifier.evaluatePrecisionAndRecall(dataset, true);
    log("precision:  " + df.format(pr.first));
    log("recall:     " + df.format(pr.second));
    log("f1:         " + df.format(2 * pr.first * pr.second / (pr.first + pr.second)));
  }


}
