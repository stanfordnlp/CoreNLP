package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ie.util.RelationTriple;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.Annotator;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.trees.GrammaticalRelation;
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

  /**
   * A pattern for rewriting "NN_1 is a JJ NN_2" --> NN_1 is JJ"
   */
  private static SemgrexPattern adjectivePattern = SemgrexPattern.compile("{}=obj >nsubj {}=subj >cop {}=be >det {word:/an?/} >amod {}=adj ?>/prep_.*/=prep {}=pobj");

  @Execution.Option(name="optimizefor", gloss="{General, KB}: Optimize the system for particular tasks (e.g., knowledge base completion tasks -- try to make the subject and object coherent named entities).")
  private Optimization optimizeFor = Optimization.GENERAL;

  @Execution.Option(name="splitter.model", gloss="The location of the clause splitting model.")
  private String splitterModel = "edu/stanford/nlp/naturalli/clauseSplitterModel.ser.gz";

  @Execution.Option(name="splitter.nomodel", gloss="If true, don't load a clause splitter model. This is primarily useful for training.")
  private boolean noModel = false;

  @Execution.Option(name="splitter.threshold", gloss="The minimum threshold for accepting a clause.")
  private double splitterThreshold = 0.5;

  @Execution.Option(name="max_entailments_per_clause", gloss="The maximum number of entailments allowed per sentence of input.")
  private int entailmentsPerSentence = 100;

  @Execution.Option(name="ignoreaffinity", gloss="If true, don't use the affinity models for dobj and pp attachment.")
  private boolean ignoreAffinity = false;

  @Execution.Option(name="affinity_models", gloss="The directory (or classpath directory) containing the affinity models for pp/obj attachments.")
  private String affinityModels = "edu/stanford/nlp/naturalli/";

  @Execution.Option(name="affinity_probability_cap", gloss="The affinity to consider 1.0")
  private double affinityProbabilityCap = 1.0 / 3.0;

  @Execution.Option(name="triple.strict", gloss="If true, only generate triples if the entire fragment has been consumed.")
  private boolean consumeAll = true;

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
    // Fill the properties
    Execution.fillOptions(this, props);
    // Create the components
    try {
      this.weights = ignoreAffinity ? new NaturalLogicWeights(affinityProbabilityCap) : new NaturalLogicWeights(affinityModels, affinityProbabilityCap);
      if (noModel) {
        System.err.println("Not loading a splitter model");
        clauseSplitter = new ClauseSplitter() {
          @Override
          public ClauseSplitterSearchProblem apply(SemanticGraph semanticGraph) {
            return new ClauseSplitterSearchProblem(semanticGraph);
          }
        };
      } else {
        clauseSplitter = ClauseSplitter.load(splitterModel);
      }
    } catch (IOException e) {
      throw new RuntimeIOException("Could not load clause splitter model at " + splitterModel + ": " + e.getMessage());
    }
    forwardEntailer = new ForwardEntailer(entailmentsPerSentence, weights);
  }

  public List<SentenceFragment> clausesInSentence(SemanticGraph tree) {
    return clauseSplitter.apply(tree).topClauses(splitterThreshold);

  }

  public List<SentenceFragment> clausesInSentence(CoreMap sentence) {
    return clausesInSentence(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
  }

  @SuppressWarnings("unchecked")
  public List<SentenceFragment> entailmentsFromClause(SentenceFragment clause) {
    if (clause.parseTree.size() == 0) {
      return Collections.EMPTY_LIST;
    } else {
      // Get the forward entailments
      List<SentenceFragment> list = forwardEntailer.apply(clause.parseTree).search()
          .stream().map(x -> x.changeScore(x.score * clause.score)).collect(Collectors.toList());
      list.add(clause);

      // A special case for adjective entailments
      List<SentenceFragment> adjFragments = new ArrayList<>();
      SemgrexMatcher matcher = adjectivePattern.matcher(clause.parseTree);
      OUTER: while (matcher.find()) {
        // (get nodes)
        IndexedWord subj = matcher.getNode("subj");
        IndexedWord be = matcher.getNode("be");
        IndexedWord adj = matcher.getNode("adj");
        IndexedWord obj = matcher.getNode("obj");
        IndexedWord pobj = matcher.getNode("pobj");
        String prep = matcher.getRelnString("prep");
        // (if the adjective, or any earlier adjective, is privative, then all bets are off)
        for (SemanticGraphEdge edge : clause.parseTree.outgoingEdgeIterable(obj)) {
          if ("amod".equals(edge.getRelation().toString()) && edge.getDependent().index() <= adj.index() &&
              Util.PRIVATIVE_ADJECTIVES.contains(edge.getDependent().word().toLowerCase())) {
            continue OUTER;
          }
        }
        // (create the core tree)
        SemanticGraph tree = new SemanticGraph();
        tree.addRoot(adj);
        tree.addVertex(subj);
        tree.addVertex(be);
        tree.addEdge(adj, be, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "cop"), Double.NEGATIVE_INFINITY, false);
        tree.addEdge(adj, subj, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, "nsubj"), Double.NEGATIVE_INFINITY, false);
        // (add pp attachment, if it existed)
        if (pobj != null) {
          assert prep != null;
          tree.addEdge(adj, pobj, GrammaticalRelation.valueOf(GrammaticalRelation.Language.English, prep), Double.NEGATIVE_INFINITY, false);
        }
        // (add tree)
        adjFragments.add(new SentenceFragment(tree, false));
      }
      list.addAll(adjFragments);
      return list;
    }
  }

  public List<SentenceFragment> entailmentsFromClauses(Collection<SentenceFragment> clauses) {
    List<SentenceFragment> entailments = new ArrayList<>();
    for (SentenceFragment clause : clauses) {
      entailments.addAll(entailmentsFromClause(clause));
    }
    return entailments;
  }

  public Optional<RelationTriple> relationInFragment(SentenceFragment fragment) {
    return RelationTriple.segment(fragment.parseTree, Optional.of(fragment.score), consumeAll).map(rel -> { switch(optimizeFor) {
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
    return RelationTriple.segment(fragment.parseTree, Optional.of(fragment.score), consumeAll).flatMap(rel -> {
      switch (optimizeFor) {
        case GENERAL:
          return Optional.of(rel);
        case KB:
          return RelationTriple.optimizeForKB(rel, Optional.of(sentence), canonicalMentionMap);
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
    } else if (tokens.size() < 2) {
      System.err.println("Very short sentence (<2 tokens); " + this.getClass().getSimpleName() + " is skipping it.");
      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, Collections.EMPTY_LIST);
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, Collections.EMPTY_LIST);
    } else {
      List<SentenceFragment> clauses = clausesInSentence(sentence);
      List<SentenceFragment> fragments = entailmentsFromClauses(clauses);
      fragments.add(new SentenceFragment(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class), false));
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
