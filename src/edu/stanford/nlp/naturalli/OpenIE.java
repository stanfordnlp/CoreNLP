package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A simple OpenIE system based on valid Natural Logic deletions of a sentence.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class OpenIE implements Annotator {

  private static enum Optimization { GENERAL, KB }

  @Execution.Option(name="openie.optimize_for", gloss="{General, KB}: Optimize the system for particular tasks (e.g., knowledge base completion tasks -- try to make the subject and object coherent named entities).")
  private Optimization optimizeFor = Optimization.GENERAL;

  @Execution.Option(name="openie.splitter.model", gloss="The location of the clause splitting model.")
  private String splitterModel = "edu/stanford/nlp/naturalli/clauseSearcherModel.ser.gz";

  @Execution.Option(name="openie.splitter.threshold", gloss="The minimum threshold for accepting a clause.")
  private double splitterThreshold = 0.5;

  @Execution.Option(name="openie.max_entailments_per_clause", gloss="The maximum number of entailments allowed per sentence of input.")
  private int entailmentsPerSentence = 100;

  @Execution.Option(name="openie.affinity_models", gloss="The directory (or classpath directory) containing the affinity models for pp/obj attachments.")
  private String affinityModels = "edu/stanford/nlp/naturalli/";

  @Execution.Option(name="openie.affinity_probability_cap", gloss="The directory (or classpath directory) containing the affinity models for pp/obj attachments.")
  private double affinityProbabilityCap = 1.0 / 3.0;

  private final NaturalLogicWeights weights;

  public final Function<SemanticGraph, ClauseSplitterSearchProblem> clauseSplitter;

  public final Function<SemanticGraph, ForwardEntailerSearchProblem> forwardEntailer;

  /** Create a new OpenIE system, with default properties */
  @SuppressWarnings("UnusedDeclaration")
  public OpenIE() {
    this(new Properties());
  }

  /**
   * Create a ne OpenIE system, based on the given properties.
   * @param props The properties to parametrize the system with.
   */
  public OpenIE(Properties props) {
    // Scrape only the relevant properties
    Properties myProps = new Properties();
    props.keySet().stream().filter(key -> key.toString().startsWith("openie.")).forEach(key -> myProps.setProperty(key.toString(), props.getProperty(key.toString())));
    // Fill the properties
    Execution.fillOptions(this, myProps);
    // Create the components
    try {
      this.weights = new NaturalLogicWeights(affinityModels, affinityProbabilityCap);
      clauseSplitter = ClauseSplitter.load(splitterModel);
    } catch (IOException e) {
      throw new RuntimeIOException("Could not load clause splitter model at: " + splitterModel);
    }
    forwardEntailer = new ForwardEntailer(entailmentsPerSentence, weights);
  }

  public List<SentenceFragment> clausesInSentence(SemanticGraph tree) {
    return clauseSplitter.apply(tree).topClauses(splitterThreshold);

  }

  public List<SentenceFragment> clausesInSentence(CoreMap sentence) {
    return clausesInSentence(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
  }

  public List<SentenceFragment> entailmentsFromClause(SentenceFragment fragment) {
    return forwardEntailer.apply(fragment.parseTree).search()
        .stream().map(x -> x.changeScore(x.score * fragment.score)).collect(Collectors.toList());
  }

  public List<SentenceFragment> entailmentsFromClauses(Collection<SentenceFragment> fragments) {
    return fragments.stream().flatMap(x -> entailmentsFromClause(x).stream()).collect(Collectors.toList());
  }

  public Optional<RelationTriple> relationInFragment(SentenceFragment fragment) {
    return RelationTriple.segment(fragment.parseTree, Optional.of(fragment.score)).map(rel -> { switch(optimizeFor) {
      case GENERAL:
        return rel;
      case KB:
        throw new IllegalStateException("Cannot optimize for KB with this function -- use the annotate() method instead");
      default:
        throw new IllegalStateException("Unknown enum constant: " + optimizeFor);
    }});
  }

  public List<RelationTriple> relationsInFragments(Collection<SentenceFragment> fragments) {
    return fragments.stream().map(this::relationInFragment).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
  }

  private Optional<RelationTriple> relationInFragment(SentenceFragment fragment, CoreMap sentence, Map<CoreLabel, List<CoreLabel>> canonicalMentionMap) {
    return RelationTriple.segment(fragment.parseTree, Optional.of(fragment.score)).flatMap(rel -> {
      switch (optimizeFor) {
        case GENERAL:
          return Optional.of(rel);
        case KB:
          return RelationTriple.optimizeForKB(rel, sentence, canonicalMentionMap);
        default:
          throw new IllegalStateException("Unknown enum constant: " + optimizeFor);
      }
    });
  }

  private List<RelationTriple> relationsInFragments(Collection<SentenceFragment> fragments, CoreMap sentence, Map<CoreLabel, List<CoreLabel>> canonicalMentionMap) {
    return fragments.stream().map(x -> relationInFragment(x, sentence, canonicalMentionMap)).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
  }


  public List<RelationTriple> relationsInClause(SentenceFragment clause) {
    return relationsInFragments(entailmentsFromClause(clause));
  }

  public List<RelationTriple> relationsInSentence(CoreMap sentence) {
    return relationsInFragments(entailmentsFromClauses(clausesInSentence(sentence)));
  }

  /**
   * <p>
   *   Annotate a single sentence.
   * </p>
   * <p>
   *   This annotator will, in particular, set the {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.EntailedSentencesAnnotation}
   *   and {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.RelationTriplesAnnotation} annotations.
   * </p>
   */
  @SuppressWarnings("unchecked")
  public void annotateSentence(CoreMap sentence, Map<CoreLabel, List<CoreLabel>> canonicalMentionMap) {
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (tokens.size() > 63) {
      System.err.println("Very long sentence (>63 tokens); " + this.getClass().getSimpleName() + " is not attempting to extract relations.");
      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, Collections.EMPTY_LIST);
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, Collections.EMPTY_LIST);
    } else {
      List<SentenceFragment> fragments = entailmentsFromClauses(clausesInSentence(sentence));
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, fragments);
      List<RelationTriple> extractions = relationsInFragments(fragments, sentence, canonicalMentionMap);
      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, extractions);
    }
  }


  /**
   * {@inheritDoc}
   *
   * <p>
   *   This annotator will, in particular, set the {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.EntailedSentencesAnnotation}
   *   and {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.RelationTriplesAnnotation} annotations.
   * </p>
   */
  @Override
  public void annotate(Annotation annotation) {
    // Accumulate Coref data
    Map<Integer, CorefChain> corefChains = annotation.get(CorefCoreAnnotations.CorefChainAnnotation.class);
    Map<CoreLabel, List<CoreLabel>> canonicalMentionMap = new IdentityHashMap<>();
    if (corefChains != null) {
      for (CorefChain chain : corefChains.values()) {
        // Metadata
        List<CoreLabel> canonicalMention = null;
        double canonicalMentionScore = Double.NEGATIVE_INFINITY;
        Set<CoreLabel> tokensToMark = new HashSet<>();
        List<CorefChain.CorefMention> mentions = chain.getMentionsInTextualOrder();
        // Iterate over mentions
        for (int i = 0; i < mentions.size(); ++i) {
          // Get some data on this mention
          Pair<List<CoreLabel>, Double> info = grokCorefMention(annotation, mentions.get(i));
          // Figure out if it should be the canonical mention
          double score = info.second + ((double) i) / ((double) mentions.size()) + (mentions.get(i) == chain.getRepresentativeMention() ? 1.0 : 0.0);
          if (canonicalMention == null || score > canonicalMentionScore) {
            canonicalMention = info.first;
            canonicalMentionScore = score;
          }
          // Register the participating tokens
          tokensToMark.addAll(info.first);
        }
        // Mark the tokens as coreferent
        assert canonicalMention != null;
        for (CoreLabel token : tokensToMark) {
          canonicalMentionMap.put(token, canonicalMention);
        }
      }
    }

    // Annotate each sentence
    annotation.get(CoreAnnotations.SentencesAnnotation.class).forEach(x -> this.annotateSentence(x, canonicalMentionMap));
  }

  /** {@inheritDoc} */
  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(Annotator.OPENIE_REQUIREMENT);
  }

  /** {@inheritDoc} */
  @Override
  public Set<Requirement> requires() {
    return Collections.singleton(Annotator.NATLOG_REQUIREMENT);
  }

  /**
   * A utility to get useful information out of a CorefMention. In particular, it returns the CoreLabels which are
   * associated with this mention, and it returns a score for how much we think this mention should be the canonical
   * mention.
   *
   * @param doc The document this mention is referenced into.
   * @param mention The mention itself.
   * @return A pair of the tokens in the mention, and a score for how much we like this mention as the canonical mention.
   */
  private static Pair<List<CoreLabel>, Double> grokCorefMention(Annotation doc, CorefChain.CorefMention mention) {
    List<CoreLabel> tokens = doc.get(CoreAnnotations.SentencesAnnotation.class).get(mention.sentNum - 1).get(CoreAnnotations.TokensAnnotation.class);
    List<CoreLabel> mentionAsTokens = tokens.subList(mention.startIndex - 1, mention.endIndex - 1);
    // Try to assess this mention's NER type
    Counter<String> nerVotes = new ClassicCounter<>();
    mentionAsTokens.stream().filter(token -> token.ner() != null && !"O".equals(token.ner())).forEach(token -> nerVotes.incrementCount(token.ner()));
    String ner = Counters.argmax(nerVotes, (o1, o2) -> o1 == null ? 0 : o1.compareTo(o2));
    double nerCount = nerVotes.getCount(ner);
    double nerScore = nerCount * nerCount / ((double) mentionAsTokens.size());
    // Return
    return Pair.makePair(mentionAsTokens, nerScore);
  }

  /**
   * An entry method for annotating standard in with OpenIE extractions.
   */
  public static void main(String[] args) {
    // Initialize prerequisites
    Properties props = StringUtils.argsToProperties(args);
    props.setProperty("annotators", "tokenize,ssplit,pos,depparse,natlog,openie");
    props.setProperty("ssplit.isOneSentence", "true");
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

    // Run extractor
    Scanner in = new Scanner(System.in);
    while (in.hasNext()) {
      String line = in.nextLine();
      Annotation ann = new Annotation(line);
      pipeline.annotate(ann);
      Collection<RelationTriple> extractions = ann.get(CoreAnnotations.SentencesAnnotation.class).get(0).get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
      if (extractions.isEmpty()) {
        System.err.println("No extractions for: " + line);
      }
      extractions.forEach(System.out::println);
    }
  }






//  public List<RelationTriple> relationsInClause(SemanticGraph tree) {
//    if (tree.size() == 0) {
//      if (tree.getRoots().size() == 0) {
//        System.err.println("WARNING: empty tree passed to " + this.getClass().getSimpleName() + ".relationInClause()");
//      }
//      return Collections.emptyList();
//    }
//    // Set the index mapping
//    List<IndexedWord> vertices = tree.vertexListSorted();
//    if (vertices.size() >= 64) {
//      return Collections.emptyList();
//    }
//    byte[] indexToMaskIndex = new byte[vertices.get(vertices.size() - 1).index()];
//    byte i = 0;
//    for (IndexedWord vertex : vertices) {
//      indexToMaskIndex[vertex.index() - 1] = i;
//      i += 1;
//    }
//    // Run the search
//    List<SearchResult> results = search(null, tree, indexToMaskIndex);
//    // Process the result
//    List<RelationTriple> triples = new ArrayList<>();
//    Optional<RelationTriple> rootExtraction = RelationTriple.segment(tree, Optional.empty());
//    if (rootExtraction.isPresent()) {
//      triples.add(rootExtraction.get());
//    }
//    for (SearchResult result : results) {
//      Optional<RelationTriple> extraction = RelationTriple.segment(result.tree, Optional.of(result.confidence));
//      if (extraction.isPresent()) {
//        triples.add(extraction.get());
//      }
//    }
//    return triples;
//  }
//
//  /**
//   * <p>
//   *   Annotate a single sentence.
//   * </p>
//   * <p>
//   *   This annotator will, in particular, set the {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.EntailedSentencesAnnotation}
//   *   and {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.RelationTriplesAnnotation} annotations.
//   * </p>
//   */
//  @SuppressWarnings("unchecked")
//  public void annotateSentence(CoreMap sentence, Map<CoreLabel, List<CoreLabel>> canonicalMentionMap) {
//    SemanticGraph fullTree = new SemanticGraph(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
//    cleanTree(fullTree);
//    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
//    if (tokens.size() > 63) {
//      System.err.println("Very long sentence (>63 tokens); " + this.getClass().getSimpleName() + " is not attempting to extract relations.");
//      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, Collections.EMPTY_LIST);
//      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, Collections.EMPTY_LIST);
//    } else {
//      List<SemanticGraph> clauses = coarseClauseSplitting(fullTree);
//      Collection<SentenceFragment> fragments = new ArrayList<>();
//      List<RelationTriple> extractions = new ArrayList<>();
//      // Add clauses
//      if (clauses.size() > 1) {
//        for (SemanticGraph tree : clauses) {
//          fragments.add(new SentenceFragment(tree, false));
//          Optional<RelationTriple> extraction = RelationTriple.segment(tree, Optional.empty());
//          if (extraction.isPresent()) {
//            extractions.add(extraction.get());
//          }
//        }
//      }
//      // Add search results
//      for (SemanticGraph tree : clauses) {
//        if (tree.size() > 0) {
//          // Set the index mapping
//          byte[] indexToMaskIndex = new byte[tokens.size()];
//          byte i = 0;
//          for (IndexedWord vertex : tree.vertexListSorted()) {
//            indexToMaskIndex[vertex.index() - 1] = i;
//            i += 1;
//          }
//          // Run the search
//          List<SearchResult> results = search(tokens, tree, indexToMaskIndex);
//          // Process the results
//          for (SearchResult result : results) {
//            SentenceFragment fragment = new SentenceFragment(result.tree, false);
//            fragments.add(fragment);
//            Optional<RelationTriple> extraction = RelationTriple.segment(result.tree, Optional.of(result.confidence));
//            if (extraction.isPresent()) {
//              extractions.add(extraction.get());
//            }
//          }
//        }
//      }
//      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, fragments);
//      switch (OPTIMIZE_FOR) {
//        case GENERAL:
//          Collections.sort(extractions);
//          sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, extractions);
//          break;
//        case KB:
//          List<RelationTriple> triples = extractions.stream().map(x -> RelationTriple.optimizeForKB(x, sentence, canonicalMentionMap)).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
//          Collections.sort(triples);
//          sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, triples);
//          break;
//        default:
//          throw new IllegalStateException("Unknown enum constant: " + OPTIMIZE_FOR);
//      }
//    }
//  }
//
//  /**
//   * A simple utility function for just getting a list of relation triples from a sentence.
//   * Calls {@link OpenIE#annotate(edu.stanford.nlp.pipeline.Annotation)} on the backend.
//   */
//  @SuppressWarnings("UnusedDeclaration")
//  public Collection<RelationTriple> relationsForSentence(CoreMap sentence) {
//    annotateSentence(sentence, new IdentityHashMap<>());
//    return sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
//  }
}
