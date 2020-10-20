package edu.stanford.nlp.naturalli;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasIndex;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.process.TSVSentenceProcessor;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.UniversalEnglishGrammaticalStructureFactory;
import edu.stanford.nlp.util.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * A script to convert a TSV dump from our KBP sentences table into a Turk-task ready clause splitting dataset.
 *
 * @author Gabor Angeli
 */
public class CreateClauseDataset implements TSVSentenceProcessor  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(CreateClauseDataset.class);

  @ArgumentParser.Option(name="in", gloss="The input to read from")
  private static InputStream in = System.in;


  private CreateClauseDataset() {} // static methods class


  private static Span toSpan(List<? extends HasIndex> chunk) {
    int min = Integer.MAX_VALUE;
    int max = -1;
    for (HasIndex word : chunk) {
      min = Math.min(word.index() - 1, min);
      max = Math.max(word.index(), max);
    }
    assert min >= 0;
    assert max < Integer.MAX_VALUE && max > 0;
    return new Span(min, max);
  }

  @Override
  public void process(long id, Annotation doc) {
    CoreMap sentence = doc.get(CoreAnnotations.SentencesAnnotation.class).get(0);
    SemanticGraph depparse = sentence.get(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class);
    log.info("| " + sentence.get(CoreAnnotations.TextAnnotation.class));

    // Get all valid subject spans
    BitSet consumedAsSubjects = new BitSet();
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection") List<Span> subjectSpans = new ArrayList<>();
    NEXTNODE: for (IndexedWord head : depparse.topologicalSort()) {
      // Check if the node is a noun/pronoun
      if (head.tag().startsWith("N") || head.tag().equals("PRP")) {
        // Try to get the NP chunk
        Optional<List<IndexedWord>> subjectChunk = segmenter.getValidChunk(depparse, head, segmenter.VALID_SUBJECT_ARCS, Optional.empty(), true);
        if (subjectChunk.isPresent()) {
          // Make sure it's not already a member of a larger NP
          for (IndexedWord tok : subjectChunk.get()) {
            if (consumedAsSubjects.get(tok.index())) {
              continue NEXTNODE;  // Already considered. Continue to the next node.
            }
          }
          // Register it as an NP
          for (IndexedWord tok : subjectChunk.get()) {
            consumedAsSubjects.set(tok.index());
          }
          // Add it as a subject
          subjectSpans.add(toSpan(subjectChunk.get()));
        }
      }
    }
  }


  /**
   * The pattern for traces which are potential subjects
   */
  private static final Pattern TRACE_TARGET_PATTERN = Pattern.compile("(NP-.*)-([0-9]+)");

  /**
   * The pattern for trace markers.
   */
  private static final Pattern TRACE_SOURCE_PATTERN = Pattern.compile(".*\\*-([0-9]+)");

  /**
   * The converter from constituency to dependency trees.
   */
  private static final UniversalEnglishGrammaticalStructureFactory parser = new UniversalEnglishGrammaticalStructureFactory();

  /**
   * The OpenIE segmenter to use.
   */
  private static final RelationTripleSegmenter segmenter = new RelationTripleSegmenter();

  /**
   * The natural logic annotator for marking polarity.
   */
  private static final NaturalLogicAnnotator natlog = new NaturalLogicAnnotator();

  /**
   * Parse a given constituency tree into a dependency graph.
   *
   * @param tree The constituency tree, in Penn Treebank style.
   * @return The dependency graph for the tree.
   */
  private static SemanticGraph parse(Tree tree) {
    return new SemanticGraph(parser.newGrammaticalStructure(tree).typedDependenciesCollapsed());
  }

  /**
   * Create a dataset of subject/object pairs, such that a sequence of splits that segments this
   * subject and object is a correct sequence.
   *
   * @param depparse The dependency parse of the sentence.
   * @param traceTargets The set of spans corresponding to targets of traces.
   * @param traceSources The set of indices in a sentence corresponding to the sources of traces.
   * @return A dataset of subject/object spans.
   */
  @SuppressWarnings("UnusedParameters")
  private static Collection<Pair<Span, Span>> subjectObjectPairs(SemanticGraph depparse,
                                                                 List<CoreLabel> tokens,
                                                                 Map<Integer, Span> traceTargets,
                                                                 Map<Integer, Integer> traceSources) {
//    log(StringUtils.join(tokens.stream().map(CoreLabel::word), " "));
    List<Pair<Span, Span>> data = new ArrayList<>();
    for (SemgrexPattern vpPattern : segmenter.VP_PATTERNS) {
      SemgrexMatcher matcher = vpPattern.matcher(depparse);
      while (matcher.find()) {
        // Get the verb and object
        IndexedWord verb = matcher.getNode("verb");
        IndexedWord object = matcher.getNode("object");
        if (verb != null && object != null) {
          // See if there is already a subject attached
          boolean hasSubject = false;
          for (SemanticGraphEdge edge : depparse.outgoingEdgeIterable(verb)) {
            if (edge.getRelation().toString().contains("subj")) {
              hasSubject = true;
            }
          }
          for (SemanticGraphEdge edge : depparse.outgoingEdgeIterable(object)) {
            if (edge.getRelation().toString().contains("subj")) {
              hasSubject = true;
            }
          }
          if (!hasSubject) {
            // Get the spans for the verb and object
            Optional<List<IndexedWord>> verbChunk = segmenter.getValidChunk(depparse, verb, segmenter.VALID_ADVERB_ARCS, Optional.empty(), true);
            Optional<List<IndexedWord>> objectChunk = segmenter.getValidChunk(depparse, object, segmenter.VALID_OBJECT_ARCS, Optional.empty(), true);
            if (verbChunk.isPresent() && objectChunk.isPresent()) {
              verbChunk.get().sort(Comparator.comparingInt(IndexedWord::index));
              objectChunk.get().sort(Comparator.comparingInt(IndexedWord::index));
              // Find a trace
              int traceId = -1;
              Span verbSpan = toSpan(verbChunk.get());
              Span traceSpan = Span.fromValues(verbSpan.start() - 1, verbSpan.end() + 1);
              for (Map.Entry<Integer, Integer> entry : traceSources.entrySet()) {
                if (traceSpan.contains(entry.getValue())) {
                  traceId = entry.getKey();
                }
              }
              //noinspection StatementWithEmptyBody
              if (traceId < 0) {
                // Register the VP as an unknown VP
//                List<CoreLabel> vpChunk = new ArrayList<>();
//                vpChunk.addAll(verbChunk.get());
//                vpChunk.addAll(objectChunk.get());
//                Collections.sort(vpChunk, (a, b) -> a.index() - b.index());
//                debug("could not find trace for " + vpChunk);
              } else {
                // Add the obj chunk
                Span subjectSpan = traceTargets.get(traceId);
                Span objectSpan = toSpan(objectChunk.get());
                if (subjectSpan != null) {
//                  debug("(" +
//                      StringUtils.join(tokens.subList(subjectSpan.start(), subjectSpan.end()).stream().map(CoreLabel::word), " ") + "; " +
//                      verb.word() + "; " +
//                      StringUtils.join(tokens.subList(objectSpan.start(), objectSpan.end()).stream().map(CoreLabel::word), " ") +
//                      ")");
                  data.add(Pair.makePair(subjectSpan, objectSpan));
                }
              }
            }
          }
        }
      }
    }

    // Run vanilla pattern splits
    for (SemgrexPattern vpPattern : segmenter.VERB_PATTERNS) {
      SemgrexMatcher matcher = vpPattern.matcher(depparse);
      while (matcher.find()) {
        // Get the verb and object
        IndexedWord subject = matcher.getNode("subject");
        IndexedWord object = matcher.getNode("object");
        if (subject != null && object != null) {
          Optional<List<IndexedWord>> subjectChunk = segmenter.getValidChunk(depparse, subject, segmenter.VALID_SUBJECT_ARCS, Optional.empty(), true);
          Optional<List<IndexedWord>> objectChunk = segmenter.getValidChunk(depparse, object, segmenter.VALID_OBJECT_ARCS, Optional.empty(), true);
          if (subjectChunk.isPresent() && objectChunk.isPresent()) {
            Span subjectSpan = toSpan(subjectChunk.get());
            Span objectSpan = toSpan(objectChunk.get());
            data.add(Pair.makePair(subjectSpan, objectSpan));
          }
        }
      }
    }

    return data;
  }

  /**
   * Collect all the possible targets for traces. This is limited to NP-style traces.
   *
   * @param root The tree to search in. This is a recursive function.
   * @return The set of trace targets. The key is the id of the trace, the value is the span of the target of the trace.
   */
  private static Map<Integer, Span> findTraceTargets(Tree root) {
    Map<Integer, Span> spansInTree = new HashMap<>(4);

    Matcher m = TRACE_TARGET_PATTERN.matcher(root.label().value() == null ? "NULL" : root.label().value());
    if (m.matches()) {
      int index = Integer.parseInt(m.group(2));
      spansInTree.put(index, Span.fromPair(root.getSpan()).toExclusive());
    }
    for (Tree child : root.children()) {
      spansInTree.putAll(findTraceTargets(child));
    }
    return spansInTree;
  }

  /**
   * Collect all the trace markers in the sentence.
   *
   * @param root The tree to search in. This is a recursive function.
   * @return A map of trace sources. The key is hte id of the trace, the value is the index of the trace's source in the sentence.
   */
  private static Map<Integer, Integer> findTraceSources(Tree root) {
    Map<Integer, Integer> spansInTree = new HashMap<>(4);

    Matcher m = TRACE_SOURCE_PATTERN.matcher(root.label().value() == null ? "NULL" : root.label().value());
    if (m.matches()) {
      int index = Integer.parseInt(m.group(1));
      spansInTree.put(index, ((CoreLabel) root.label()).index() - 1);
    }
    for (Tree child : root.children()) {
      spansInTree.putAll(findTraceSources(child));
    }
    return spansInTree;
  }

  /**
   * Count the number of extractions in the given dataset. That is, the sum count of the pair spans
   * for each sentence.
   *
   * @param data The dataset.
   * @return The number of extractions in the datasets..
   */
  private static int countDatums(List<Pair<CoreMap, Collection<Pair<Span,Span>>>> data) {
    int count = 0;
    for (Pair<CoreMap, Collection<Pair<Span, Span>>> datum : data) {
      count += datum.second.size();
    }
    return count;
  }

  /**
   * Process all the trees in the given directory. For example, the WSJ section of the Penn Treebank.
   *
   * @param name The name of the directory we are processing.
   * @param directory The directory we are processing.
   * @return A dataset of subject/object pairs in the trees in the directory.
   *         This is a list of sentences, such that each sentence has a collection of pairs of spans.
   *         Each pair of spans is a subject/object span pair that constitutes a valid extraction.
   * @throws IOException
   */
  private static List<Pair<CoreMap, Collection<Pair<Span, Span>>>> processDirectory(String name, File directory) throws IOException {
    forceTrack("Processing " + name);

    // Prepare the files to iterate over
    Iterable<File> files = IOUtils.iterFilesRecursive(directory, "mrg");
    int numTreesProcessed = 0;
    List<Pair<CoreMap, Collection<Pair<Span, Span>>>> trainingData = new ArrayList<>(1024);

    // Iterate over the files
    for (File file : files) {
//      log(file);
      TreeReader reader = new PennTreeReader(IOUtils.readerFromFile(file));
      Tree tree;
      while ( (tree = reader.readTree()) != null ) {
        try {
          // Prepare the tree
          tree.indexSpans();
          tree.setSpans();

          // Get relevant information from sentence
          List<CoreLabel> tokens = tree.getLeaves().stream()
              .map(leaf -> (CoreLabel) leaf.label())
//            .filter(leaf -> !TRACE_SOURCE_PATTERN.matcher(leaf.word()).matches() && !leaf.tag().equals("-NONE-"))
              .collect(Collectors.toList());
          SemanticGraph graph = parse(tree);
          Map<Integer, Span> targets = findTraceTargets(tree);
          Map<Integer, Integer> sources = findTraceSources(tree);

          // Create a sentence object
          CoreMap sentence = new ArrayCoreMap(4) {{
            set(CoreAnnotations.TokensAnnotation.class, tokens);
            set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, graph);
            set(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class, graph);
            set(SemanticGraphCoreAnnotations.EnhancedPlusPlusDependenciesAnnotation.class, graph);
          }};
          natlog.doOneSentence(null, sentence);

          // Generate training data
          Collection<Pair<Span, Span>> trainingDataFromSentence = subjectObjectPairs(graph, tokens, targets, sources);
          trainingData.add(Pair.makePair(sentence, trainingDataFromSentence));

          // Debug print
          numTreesProcessed += 1;
          if (numTreesProcessed % 100 == 0) {
            log("[" + new DecimalFormat("00000").format(numTreesProcessed) + "] " + countDatums(trainingData) + " known extractions");
          }
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
    }

    // End
    log("" + numTreesProcessed + " trees processed yielding " + countDatums(trainingData) + " known extractions");
    endTrack("Processing " + name);
    return trainingData;
  }


  /**
   * The main entry point of the code.
   */
  public static void main(String[] args) throws IOException {
    forceTrack("Processing treebanks");
    List<Pair<CoreMap, Collection<Pair<Span, Span>>>> trainingData = new ArrayList<>();
    trainingData.addAll(processDirectory("WSJ", new File("/home/gabor/lib/data/penn_treebank/wsj")));
    trainingData.addAll(processDirectory("Brown", new File("/home/gabor/lib/data/penn_treebank/brown")));
    endTrack("Processing treebanks");

    forceTrack("Training");
    log("dataset size: " + trainingData.size());
    ClauseSplitter.train(
        trainingData.stream(),
        new File("/home/gabor/tmp/clauseSearcher.ser.gz"),
        new File("/home/gabor/tmp/clauseSearcherData.tab.gz"));
    endTrack("Training");




//    Execution.fillOptions(CreateClauseDataset.class, args);
//
//    new CreateClauseDataset().runAndExit(in, System.err, code -> code);
  }
}
