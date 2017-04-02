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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

import edu.stanford.nlp.naturalli.ClauseSplitterSearchProblem.*;
import edu.stanford.nlp.util.logging.Redwood;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * Just a convenience alias for a clause splitting search problem factory.
 * Mostly here to form a nice parallel with {@link edu.stanford.nlp.naturalli.ForwardEntailer}.
 *
 * @author Gabor Angeli
 */
public interface ClauseSplitter extends BiFunction<SemanticGraph, Boolean, ClauseSplitterSearchProblem>  {

  /** A logger for this class */
  Redwood.RedwoodChannels log = Redwood.channels(ClauseSplitter.class);

  enum ClauseClassifierLabel {
    CLAUSE_SPLIT(2),
    CLAUSE_INTERM(1),
    NOT_A_CLAUSE(0);
    public final byte index;
    ClauseClassifierLabel(int val) {
      this.index = (byte) val;
    }
    /** Seriously, why would Java not have this by default? */
    @Override
    public String toString() {
      return this.name();
    }
    @SuppressWarnings("unused")
    public static ClauseClassifierLabel fromIndex(int index) {
      switch (index) {
        case 0:
          return NOT_A_CLAUSE;
        case 1:
          return CLAUSE_INTERM;
        case 2:
          return CLAUSE_SPLIT;
        default:
          throw new IllegalArgumentException("Not a valid index: " + index);
      }
    }
  }


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
   * @param modelPath The path to save the model to. This is useful for {@link ClauseSplitter#load(String)}.
   * @param trainingDataDump The path to save the training data, as a set of labeled featurized datums.
   * @param featurizer The featurizer to use for this classifier.
   *
   * @return A factory for creating searchers from a given dependency tree.
   */
  static ClauseSplitter train(
      Stream<Pair<CoreMap, Collection<Pair<Span, Span>>>> trainingData,
      Optional<File> modelPath,
      Optional<File> trainingDataDump,
      Featurizer featurizer) {

    // Parse options
    LinearClassifierFactory<ClauseClassifierLabel, String> factory = new LinearClassifierFactory<>();
    // Generally useful objects
    OpenIE openie = new OpenIE(PropertiesUtils.asProperties(
        "splitter.nomodel", "true",
        "optimizefor", "GENERAL"
    ));
    WeightedDataset<ClauseClassifierLabel, String> dataset = new WeightedDataset<>();
    AtomicInteger numExamplesProcessed = new AtomicInteger(0);
    final Optional<PrintWriter> datasetDumpWriter = trainingDataDump.map(file -> {
      try {
        return new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(trainingDataDump.get()))));
      } catch (IOException e) {
        throw new RuntimeIOException(e);
      }
    });


    // Step 1: Loop over data
    forceTrack("Training inference");
    trainingData.forEach(rawExample -> {
      // Parse training datum
      CoreMap sentence = rawExample.first;
      Collection<Pair<Span, Span>> spans = rawExample.second;
      List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class);
      // Create raw clause searcher (no classifier)
      ClauseSplitterSearchProblem problem = new ClauseSplitterSearchProblem(tree, true);

      // Run search
      problem.search(fragmentAndScore -> {
        // Parse the search callback
        List<Counter<String>> features = fragmentAndScore.second;
        SentenceFragment fragment = fragmentAndScore.third.get();

        // Search for extractions
        Set<RelationTriple> extractions = new HashSet<>(openie.relationsInFragments(openie.entailmentsFromClause(fragment)));
        Trilean correct = Trilean.FALSE;
        RELATION_TRIPLE_LOOP: for (RelationTriple extraction : extractions) {
          // Clean up the guesses
          Span subjectGuess = Span.fromValues(extraction.subject.get(0).index() - 1, extraction.subject.get(extraction.subject.size() - 1).index());
          Span objectGuess = Span.fromValues(extraction.object.get(0).index() - 1, extraction.object.get(extraction.object.size() - 1).index());
          for (Pair<Span, Span> candidateGold : spans) {
            Span subjectSpan = candidateGold.first;
            Span objectSpan = candidateGold.second;
            // Check if it matches
            if ((subjectGuess.equals(subjectSpan) && objectGuess.equals(objectSpan)) ||
                (subjectGuess.equals(objectSpan) && objectGuess.equals(subjectSpan))
                ) {
              correct = Trilean.TRUE;
              break RELATION_TRIPLE_LOOP;
            } else if (Util.nerOverlap(tokens, subjectSpan, subjectGuess) && Util.nerOverlap(tokens, objectSpan, objectGuess) ||
                Util.nerOverlap(tokens, subjectSpan, objectGuess) && Util.nerOverlap(tokens, objectSpan, subjectGuess)) {
              if (!correct.isTrue()) {
                correct = Trilean.TRUE;
                break RELATION_TRIPLE_LOOP;
              }
            } else {
              if (!correct.isTrue()) {
                correct = Trilean.UNKNOWN;
                break RELATION_TRIPLE_LOOP;
              }
            }
          }
        }

        // Process the datum
        if (!features.isEmpty()) {

          // Convert the path to datums
          List<Pair<Counter<String>, ClauseClassifierLabel>> decisionsToAddAsDatums = new ArrayList<>();
          if (correct.isTrue()) {
            // If this is a "true" path, add the k-1 decisions as INTERM and the last decision as a SPLIT
            for (int i = 0; i < features.size(); ++i) {
              if (i == features.size() - 1) {
                decisionsToAddAsDatums.add(Pair.makePair(features.get(i), ClauseClassifierLabel.CLAUSE_SPLIT));
              } else {
                decisionsToAddAsDatums.add(Pair.makePair(features.get(i), ClauseClassifierLabel.CLAUSE_INTERM));
              }
            }
          } else if (correct.isFalse()) {
            // If this is a "false" path, then we know at least the last decision was bad.
            decisionsToAddAsDatums.add(Pair.makePair(features.get(features.size() - 1), ClauseClassifierLabel.NOT_A_CLAUSE));
          } else if (correct.isUnknown()) {
            // If this is an "unknown" path, only add it if it was the result of vanilla splits
            // (check if it is a sequence of simple splits)
            boolean isSimpleSplit = false;
            for (Counter<String> feats : features) {
              if (featurizer.isSimpleSplit(feats)) {
                isSimpleSplit = true;
                break;
              }
            }
            // (if so, add it as if it were a True example)
            if (isSimpleSplit) {
              for (int i = 0; i < features.size(); ++i) {
                if (i == features.size() - 1) {
                  decisionsToAddAsDatums.add(Pair.makePair(features.get(i), ClauseClassifierLabel.CLAUSE_SPLIT));
                } else {
                  decisionsToAddAsDatums.add(Pair.makePair(features.get(i), ClauseClassifierLabel.CLAUSE_INTERM));
                }
              }
            }
          }

          // Add the datums
          for (Pair<Counter<String>, ClauseClassifierLabel> decision : decisionsToAddAsDatums) {
            // (create datum)
            RVFDatum<ClauseClassifierLabel, String> datum = new RVFDatum<>(decision.first);
            datum.setLabel(decision.second);
            // (dump datum to debug log)
            if (datasetDumpWriter.isPresent()) {
              datasetDumpWriter.get().println(decision.second + "\t" +
                  StringUtils.join(decision.first.entrySet().stream().map(entry -> entry.getKey() + "->" + entry.getValue()), ";"));
            }
            // (add datum to dataset)
            dataset.add(datum);
          }
        }
        return true;
      }, new LinearClassifier<>(new ClassicCounter<>()), Collections.emptyMap(), featurizer, 10000);

      // Debug info
      if (numExamplesProcessed.incrementAndGet() % 100 == 0) {
        log("processed " + numExamplesProcessed + " training sentences: " + dataset.size() + " datums");
      }
    });
    endTrack("Training inference");

    // Close the file
    if (datasetDumpWriter.isPresent()) {
      datasetDumpWriter.get().close();
    }

    // Step 2: Train classifier
    forceTrack("Training");
    Classifier<ClauseClassifierLabel,String> fullClassifier = factory.trainClassifier(dataset);
    endTrack("Training");
    if (modelPath.isPresent()) {
      Pair<Classifier<ClauseClassifierLabel,String>, Featurizer> toSave = Pair.makePair(fullClassifier, featurizer);
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
    dataset.randomize(42L);
    Util.dumpAccuracy(fullClassifier, dataset);
    endTrack("Training accuracy");

    int numFolds = 5;
    forceTrack(numFolds + " fold cross-validation");
    for (int fold = 0; fold < numFolds; ++fold) {
      forceTrack("Fold " + (fold + 1));
      forceTrack("Training");
      Pair<GeneralDataset<ClauseClassifierLabel, String>, GeneralDataset<ClauseClassifierLabel, String>> foldData = dataset.splitOutFold(fold, numFolds);
      Classifier<ClauseClassifierLabel, String> classifier = factory.trainClassifier(foldData.first);
      endTrack("Training");
      forceTrack("Test");
      Util.dumpAccuracy(classifier, foldData.second);
      endTrack("Test");
      endTrack("Fold " + (fold + 1));
    }
    endTrack(numFolds + " fold cross-validation");


    // Step 5: return factory
    return (tree, truth) -> new ClauseSplitterSearchProblem(tree, truth, Optional.of(fullClassifier), Optional.of(featurizer));
  }

  static ClauseSplitter train(
      Stream<Pair<CoreMap, Collection<Pair<Span, Span>>>> trainingData,
      File modelPath,
      File trainingDataDump) {
    return train(trainingData, Optional.of(modelPath), Optional.of(trainingDataDump), ClauseSplitterSearchProblem.DEFAULT_FEATURIZER);
  }


  /**
   * Load a factory model from a given path. This can be trained with
   * {@link ClauseSplitter#train(Stream, Optional, Optional, Featurizer)}.
   *
   * @return A function taking a dependency tree, and returning a clause searcher.
   */
  static ClauseSplitter load(String serializedModel) throws IOException {
    try {
      long start = System.currentTimeMillis();
      Pair<Classifier<ClauseClassifierLabel,String>, Featurizer> data = IOUtils.readObjectFromURLOrClasspathOrFileSystem(serializedModel);
      ClauseSplitter rtn =  (tree, truth) -> new ClauseSplitterSearchProblem(tree, truth, Optional.of(data.first), Optional.of(data.second));
      log.info("Loading clause splitter from " + serializedModel + " ... done [" +
              Redwood.formatTimeDifference(System.currentTimeMillis() - start) + "]");
      return rtn;
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Invalid model at path: " + serializedModel, e);
    }
  }


}
