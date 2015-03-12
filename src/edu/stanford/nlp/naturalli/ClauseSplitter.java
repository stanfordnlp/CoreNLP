package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
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
public interface ClauseSplitter extends Function<SemanticGraph, ClauseSplitterSearchProblem> {

  public enum ClauseClassifierLabel {
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
    ClassifierFactory<ClauseClassifierLabel, String, Classifier<ClauseClassifierLabel,String>> classifierFactory = MetaClass.create(options.classifierFactory).createInstance();
    // Generally useful objects
    OpenIE openie = new OpenIE(new Properties() {{
      setProperty("splitter.nomodel", "true");
      setProperty("optimizefor", "GENERAL");
    }});
    Random rand = new Random(options.seed);
    WeightedDataset<ClauseClassifierLabel, String> dataset = new WeightedDataset<>();
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
      SemanticGraph tree = sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class);
      Span subjectSpan = triple.second; //Util.extractNER(tokens, triple.second);
      Span objectSpan = triple.third; //Util.extractNER(tokens, triple.third);
//      log(StringUtils.toString(tokens));
//      log("  -> " + StringUtils.toString(tokens.subList(subjectSpan.start(), subjectSpan.end())) + " :: " + StringUtils.toString(tokens.subList(objectSpan.start(), objectSpan.end())));
      // Create raw clause searcher (no classifier)
      ClauseSplitterSearchProblem problem = new ClauseSplitterSearchProblem(tree);

      // Run search
      problem.search(fragmentAndScore -> {
        // Parse the search output
        List<Counter<String>> features = fragmentAndScore.second;
        Supplier<SentenceFragment> fragmentSupplier = fragmentAndScore.third;
        SentenceFragment fragment = fragmentSupplier.get();
        // Search for extractions
        List<RelationTriple> extractions = openie.relationsInFragments(openie.entailmentsFromClause(fragment));
        Trilean correct = Trilean.FALSE;
        RelationTriple bestExtraction = null;
        String prefix = "  x ";
        for (RelationTriple extraction : extractions) {
          // Clean up the guesses
          Span subjectGuess = Span.fromValues(extraction.subject.get(0).index() - 1, extraction.subject.get(extraction.subject.size() - 1).index());
          Span objectGuess = Span.fromValues(extraction.object.get(0).index() - 1, extraction.object.get(extraction.object.size() - 1).index());
          // Check if it matches
          if ((subjectGuess.equals(subjectSpan) && objectGuess.equals(objectSpan)) ||
              (subjectGuess.equals(objectSpan) && objectGuess.equals(subjectSpan))
              ) {
            prefix = "  * ";
            correct = Trilean.TRUE;
            bestExtraction = extraction;
          } else if ( Util.nerOverlap(tokens, subjectSpan, subjectGuess) && Util.nerOverlap(tokens, objectSpan, objectGuess) ||
                      Util.nerOverlap(tokens, subjectSpan, objectGuess) && Util.nerOverlap(tokens, objectSpan, subjectGuess) ) {
            if (!correct.isTrue()) {
              prefix = "  ~ ";
              bestExtraction = extraction;
              correct = Trilean.TRUE;
            }
          } else {
            if (!correct.isTrue()) {
              prefix = "  ? ";
              bestExtraction = extraction;
              correct = Trilean.UNKNOWN;
            }
          }
        }
        // Process the datum
        if (!features.isEmpty()) {
//          log(prefix + info(fragment, tokens, tree));
//          if (bestExtraction != null) { log("    " + bestExtraction); }
          for (int i = (correct.isFalse() ? features.size() - 1 : 0); i < features.size(); ++i) {
            Counter<String> decision = features.get(i);
            // (get output label)
            ClauseClassifierLabel label;
            if (correct.isFalse()) {
              label = ClauseClassifierLabel.NOT_A_CLAUSE;
            } else {
              if (i == features.size() - 1) {
                label = ClauseClassifierLabel.CLAUSE_SPLIT;
              } else {
                label = ClauseClassifierLabel.CLAUSE_INTERM;
              }
            }
//            if (bestExtraction != null) { log("    " + label); }
            // (create datum)
            RVFDatum<ClauseClassifierLabel, String> datum = new RVFDatum<>(decision);
            datum.setLabel(label);
            // (dump datum to debug log)
            if (datasetDumpWriter.isPresent()) {
              datasetDumpWriter.get().println("" + label + "\t" + correct + "\t" +
                  StringUtils.join(decision.entrySet().stream().map(entry -> "" + entry.getKey() + "->" + entry.getValue()), ";"));
            }
            // (get datum weight)
            float weight;
            if (correct.isTrue()) {
              weight = options.positiveDatumWeight;
            } else if (correct.isUnknown()) {
              weight = options.unknownDatumWeight;
            } else {
              weight = 1.0f;
            }
            switch (label) {
              case CLAUSE_INTERM:
                weight *= options.clauseIntermWeight;
                break;
              case CLAUSE_SPLIT:
                weight *= options.clauseSplitWeight;
                break;
              default:
                weight *= 1.0f;
                break;
            }
            // (add datum to dataset)
            if (weight > 0.0) {
              if (label != ClauseClassifierLabel.NOT_A_CLAUSE || rand.nextDouble() > (1.0 - options.negativeSubsampleRatio)) {
                dataset.add(datum, weight);
              }
            }
          }
        }
        return true;
      }, new LinearClassifier<>(new ClassicCounter<>()), featurizer, 10000);
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
    Classifier<ClauseClassifierLabel,String> fullClassifier = classifierFactory.trainClassifier(dataset);
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
    dataset.randomize(options.seed);
    Util.dumpAccuracy(fullClassifier, dataset);
    endTrack("Training accuracy");

    int numFolds = 5;
    forceTrack("" + numFolds + " fold cross-validation");
    for (int fold = 0; fold < numFolds; ++fold) {
      forceTrack("Fold " + (fold + 1));
      forceTrack("Training");
      Pair<GeneralDataset<ClauseClassifierLabel, String>, GeneralDataset<ClauseClassifierLabel, String>> foldData = dataset.splitOutFold(fold, numFolds);
      Classifier<ClauseClassifierLabel, String> classifier = classifierFactory.trainClassifier(foldData.first);
      endTrack("Training");
      forceTrack("Test");
      Util.dumpAccuracy(classifier, foldData.second);
      endTrack("Test");
      endTrack("Fold " + (fold + 1));
    }
    endTrack("" + numFolds + " fold cross-validation");


    // Step 5: return factory
    return tree -> new ClauseSplitterSearchProblem(tree, Optional.of(fullClassifier), Optional.of(featurizer));
  }

  /**
   * TODO(gabor) DELETE ME
   */
  static String info(SentenceFragment fragment, List<CoreLabel> tokens, SemanticGraph tree) {
    IndexedWord node = fragment.parseTree.getFirstRoot();
    String rtn = fragment.toString() + "  ::  " + node;
    while (!node.equals(tree.getFirstRoot())) {
      SemanticGraphEdge edge = tree.incomingEdgeIterator(node).next();
      node = edge.getGovernor();
      rtn = rtn + " <-" + edge.getRelation() + "- " + node;
    }
    return rtn;
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
      long start = System.currentTimeMillis();
      System.err.print("Loading clause searcher from " + serializedModel + "...");
      Pair<Classifier<ClauseClassifierLabel,String>, Featurizer> data = IOUtils.readObjectFromURLOrClasspathOrFileSystem(serializedModel);
      ClauseSplitter rtn =  tree -> new ClauseSplitterSearchProblem(tree, Optional.of(data.first), Optional.of(data.second));
      System.err.println("done [" + Redwood.formatTimeDifference(System.currentTimeMillis() - start) + "]");
      return rtn;
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Invalid model at path: " + serializedModel, e);
    }
  }


}
