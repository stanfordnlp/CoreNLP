package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations;
import edu.stanford.nlp.ie.util.RelationTriple;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * A simple OpenIE system based on valid Natural Logic deletions of a sentence.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("FieldCanBeLocal")
public class OpenIE implements Annotator {


  @Execution.Option(name="openie.pp_affinity", gloss="A tab separated file of 'verb  preposition  affinity' values, where affinity is between 0 and 1")
  private String PP_AFFINITY = "/home/gabor/workspace/naturalli/etc/pp_affinity.tsv.gz"; //"edu/stanford/nlp/naturalli/pp_affinity.tab";
  @Execution.Option(name="openie.dobj_affinity", gloss="A tab separated file of 'verb  dobj_affinity' values, where affinity is between 0 and 1")
  private String DOBJ_AFFINITY = "/home/gabor/workspace/naturalli/etc/dobj_affinity.tsv.gz"; // "edu/stanford/nlp/naturalli/dobj_affinity.tab";
  @Execution.Option(name="openie.max_results_per_clause", gloss="The maximum number of results to return for a single segmented clause")
  private int MAX_RESULTS_PER_CLAUSE = 100;
  private static enum Optimization { GENERAL, KB }
  @Execution.Option(name="openie.optimize_for", gloss="{General, KB}: Optimize the system for particular tasks (e.g., knowledge base completion tasks -- try to make the subject and object coherent named entities).")
  private Optimization OPTIMIZE_FOR = Optimization.GENERAL;

  private final NaturalLogicWeights WEIGHTS;

  /** Create a new OpenIE system, with default properties */
  @SuppressWarnings("UnusedDeclaration")
  public OpenIE() {
    this(new Properties());
  }

  /**
   * Create a ne OpenIE system, based on the given properties.
   * @param props The properties to parameterize the system with.
   */
  public OpenIE(Properties props) {
    Execution.fillOptions(this, props);
    this.WEIGHTS = new NaturalLogicWeights(PP_AFFINITY, DOBJ_AFFINITY);
  }

  /**
   * A result from the search over possible shortenings of the sentence.
   */
  private static class SearchResult {
    public final SemanticGraph tree;
    public final List<String> deletedEdges;
    public final double confidence;

    private SearchResult(SemanticGraph tree, List<String> deletedEdges, double confidence) {
      this.tree = tree;
      this.deletedEdges = deletedEdges;
      this.confidence = confidence;
    }

    @Override
    public String toString() {
      return StringUtils.join(tree.vertexListSorted().stream().map(IndexedWord::word), " ");
    }
  }

  /**
   * A state in the search, denoting a partial shortening of the sentence.
   */
  private static class SearchState {
    public final long deletionMask;
    public final int currentIndex;
    public final SemanticGraph tree;
    public final String lastDeletedEdge;
    public final SearchState source;
    public final double score;

    private SearchState(long deletionMask, int currentIndex, SemanticGraph tree, String lastDeletedEdge, SearchState source, double score) {
      this.deletionMask = deletionMask;
      this.currentIndex = currentIndex;
      this.tree = tree;
      this.lastDeletedEdge = lastDeletedEdge;
      this.source = source;
      this.score = score;
    }
  }

  /**
   * The search algorithm, starting with a full sentence and iteratively shortening it to its entailed sentences.
   * @param sentenceOrNull The sentence to begin with.
   * @param originalTree The original tree of the sentence we are beginning with
   * @param indexToMaskIndex In case the sentence is longer than 64 words, we can map back just to the raw clause.
   * @return A list of search results, corresponding to shortenings of the sentence.
   */
  private List<SearchResult> search(List<CoreLabel> sentenceOrNull, SemanticGraph originalTree,
                                    byte[] indexToMaskIndex) {
    // Pre-process the tree
    originalTree = new SemanticGraph(originalTree);
    // (remove common determiners)
    List<String> determinerRemovals = new ArrayList<>();
    for (IndexedWord vertex : originalTree.getLeafVertices()) {
      if (vertex.word().equalsIgnoreCase("the") || vertex.word().equalsIgnoreCase("a") ||
          vertex.word().equalsIgnoreCase("an")) {
        originalTree.removeVertex(vertex);
        determinerRemovals.add("det");
      }
    }
    // (cut conj_and nodes)
    Set<SemanticGraphEdge> andsToAdd = new HashSet<>();
    for (IndexedWord vertex : originalTree.vertexSet()) {
      if( originalTree.inDegree(vertex) > 1 ) {
        SemanticGraphEdge conjAnd = null;
        for (SemanticGraphEdge edge : originalTree.incomingEdgeIterable(vertex)) {
          if (edge.getRelation().toString().equals("conj_and")) {
            conjAnd = edge;
          }
        }
        if (conjAnd != null) {
          originalTree.removeEdge(conjAnd);
          andsToAdd.add(conjAnd);
        }
      }
    }
    // (find secondary edges)
    Set<SemanticGraphEdge> secondaryEdges = classifySecondaryEdges(originalTree);

    // Outputs
    List<SearchResult> results = new ArrayList<>();
    if (!determinerRemovals.isEmpty()) {
      if (andsToAdd.isEmpty()) {
        double score = Math.pow(WEIGHTS.deletionProbability(null, "det"), (double) determinerRemovals.size());
        assert !Double.isNaN(score);
        assert !Double.isInfinite(score);
        results.add(new SearchResult(originalTree, determinerRemovals, score));
      } else {
        SemanticGraph treeWithAnds = new SemanticGraph(originalTree);
        for (SemanticGraphEdge and : andsToAdd) {
          treeWithAnds.addEdge(and.getGovernor(), and.getDependent(), and.getRelation(), Double.NEGATIVE_INFINITY, false);
        }
        results.add(new SearchResult(treeWithAnds, determinerRemovals,
            Math.pow(WEIGHTS.deletionProbability(null, "det"), (double) determinerRemovals.size())));
      }
    }

    // Initialize the search
    List<IndexedWord> topologicalVertices = originalTree.topologicalSort();
    if (topologicalVertices.isEmpty()) {
      return results;
    }
    Stack<SearchState> fringe = new Stack<>();
    fringe.push(new SearchState(0l, 0, originalTree, null, null, 1.0));

    // Start the search
    while (!fringe.isEmpty()) {
      if (results.size() >= MAX_RESULTS_PER_CLAUSE) { return results; }
      SearchState state = fringe.pop();
      IndexedWord currentWord = topologicalVertices.get(state.currentIndex);

      // Push the case where we don't delete
      int nextIndex = state.currentIndex + 1;
      while (nextIndex < topologicalVertices.size()) {
        IndexedWord nextWord = topologicalVertices.get(nextIndex);
        if (  ((state.deletionMask >>> (indexToMaskIndex[nextWord.index() - 1])) & 0x1l) == 0) {
          fringe.push(new SearchState(state.deletionMask, nextIndex, state.tree, null, state, state.score));
          break;
        } else {
          nextIndex += 1;
        }
      }

      // Check if we can delete this subtree
      boolean canDelete = state.tree.getFirstRoot() != currentWord;
      for (SemanticGraphEdge edge : state.tree.incomingEdgeIterable(currentWord)) {
        Polarity tokenPolarity = Polarity.DEFAULT;
        if (sentenceOrNull != null) {
          // Get token information
          CoreLabel token = sentenceOrNull.get(edge.getDependent().index() - 1);
          tokenPolarity = token.get(NaturalLogicAnnotations.PolarityAnnotation.class);
          if (tokenPolarity == null) {
            tokenPolarity = Polarity.DEFAULT;
          }
        }
        // Get the relation for this deletion
        NaturalLogicRelation lexicalRelation = NaturalLogicRelation.forDependencyDeletion(edge.getRelation().toString());
        NaturalLogicRelation projectedRelation = tokenPolarity.projectLexicalRelation(lexicalRelation);
        // Make sure this is a valid entailment
        if (!projectedRelation.isEntailed) { canDelete = false; }
      }

      if (canDelete) {
        // Register the deletion
        long newMask = state.deletionMask;
        SemanticGraph treeWithDeletions = new SemanticGraph(state.tree);
        for (IndexedWord vertex : state.tree.descendants(currentWord)) {
          treeWithDeletions.removeVertex(vertex);
          newMask |= (0x1l << (indexToMaskIndex[vertex.index() - 1]));
          assert indexToMaskIndex[vertex.index() - 1] < 64;
          assert ((newMask >>> (indexToMaskIndex[vertex.index() - 1])) & 0x1l) == 1;
        }
        SemanticGraph resultTree = new SemanticGraph(treeWithDeletions);
        for (SemanticGraphEdge edge : andsToAdd) {
          if (resultTree.containsVertex(edge.getGovernor()) && resultTree.containsVertex(edge.getDependent())) {
            resultTree.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), Double.NEGATIVE_INFINITY, false);
          }
        }
        // Compute the score of the sentence
        double newScore = state.score;
        for (SemanticGraphEdge edge : state.tree.incomingEdgeIterable(currentWord)) {
          String relationString = edge.getRelation().toString();
          double multiplier = WEIGHTS.deletionProbability(
              edge.getGovernor().word().toLowerCase(),
              relationString,
              secondaryEdges.contains(edge)
          );
          assert !Double.isNaN(multiplier);
          assert !Double.isInfinite(multiplier);
          newScore *= multiplier;
        }
        // Register the result
        results.add(new SearchResult(resultTree,
            aggregateDeletedEdges(state, state.tree.incomingEdgeIterable(currentWord), determinerRemovals),
            newScore));

        // Push the state with this subtree deleted
        nextIndex = state.currentIndex + 1;
        while (nextIndex < topologicalVertices.size()) {
          IndexedWord nextWord = topologicalVertices.get(nextIndex);
          if (  ((newMask >>> (indexToMaskIndex[nextWord.index() - 1])) & 0x1l) == 0) {
            assert treeWithDeletions.containsVertex(topologicalVertices.get(nextIndex));
            fringe.push(new SearchState(newMask, nextIndex, treeWithDeletions, null, state, newScore));
            break;
          } else {
            nextIndex += 1;
          }
        }
      }
    }

    // Return
    return results;
  }

  /**
   * A simple heuristic to classify whether an edge is a secondary edge of the given type.
   * For example, in:
   * <pre>
   *   In foreign policy, Obama ended military involvement in Iraq.
   * </pre>
   *
   * The first 'in' ('in foreign policy') is to be considered a secondary edge of the type 'prep_in'.
   *
   * @param graph The graph to classify
   *
   * @return The edges in the graph which are to be considered secondary edges.
   */
  private Set<SemanticGraphEdge> classifySecondaryEdges(SemanticGraph graph) {
    Set<SemanticGraphEdge> secondaryEdges = new HashSet<>();
    for (IndexedWord root : graph.vertexSet()) {
      Map<String,List<SemanticGraphEdge>> edgesByType = new HashMap<>();
      boolean hasDirectObject = false;
      for (SemanticGraphEdge edge : graph.outgoingEdgeIterable(root)) {
        String type = edge.getRelation().toString();
        if (type.startsWith("prep")) {
          if (!edgesByType.containsKey(type)) {
            edgesByType.put(type, new ArrayList<>());
          }
          edgesByType.get(type).add(edge);
        }
        if (type.equals("dobj")) {
          hasDirectObject = true;
        }
      }
      for (Map.Entry<String, List<SemanticGraphEdge>> entry : edgesByType.entrySet()) {
        List<SemanticGraphEdge> edges = entry.getValue();
        if (hasDirectObject) {
          // If we have a dobj, all prep_* edges are secondary
          for (SemanticGraphEdge e : edges) {
            secondaryEdges.add(e);
          }
        } else if (entry.getValue().size() > 1) {
          // Candidate for a secondary edge (i.e., more than one outgoing edge of the given type)
          Collections.sort(edges, (o1, o2) -> {
            if (o1.getDependent().index() < root.index()) {
              return -1;
            } else if (o2.getDependent().index() < root.index()) {
              return 1;
            } else {
              return o1.getDependent().index() - o2.getDependent().index();
            }
          });
          // Register secondary edges
          for (int i = 1; i < edges.size(); ++i) {
            secondaryEdges.add(edges.get(i));
          }
        } else if (edges.get(0).getDependent().index() < root.index()) {
          secondaryEdges.add(edges.get(0));
        }
      }
    }
    return secondaryEdges;
  }

  /**
   * Backtrace from a search state, collecting all of the deleted edges used to get there.
   * @param state The final search state.
   * @param justDeleted  The edges we have just deleted.
   * @param otherEdges  Other deletions we want to register
   * @return A list of deleted edges for that search state.
   */
  private static List<String> aggregateDeletedEdges(SearchState state, Iterable<SemanticGraphEdge> justDeleted, Iterable<String> otherEdges) {
    List<String> rtn = new ArrayList<>();
    for (SemanticGraphEdge edge : justDeleted) {
      rtn.add(edge.getRelation().toString());
    }
    for (String edge : otherEdges) {
      rtn.add(edge);
    }
    while (state != null) {
      if (state.lastDeletedEdge != null) {
        rtn.add(state.lastDeletedEdge);
      }
      state = state.source;
    }
    return rtn;
  }

  /** The pattern for a clause to be split off of the sentence */
  private static final List<SemgrexPattern> CLAUSE_PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
    String clauseBreakers = "vmod|rcmod|[cxpa]comp|partmod|appos|infmod|prepc.*|advcl|purpcl|conj(_and|but|plus)?|prep_.*";
    add(SemgrexPattern.compile("{$} ?>/.subj(pass)?/ {}=subject >/" + clauseBreakers + "/ ( {pos:/V.*/}=clause ?>/.subj(pass)?/ {}=clausesubj )"));
    add(SemgrexPattern.compile("{$} ?>/.subj(pass)?/ {}=subject >/.obj|prep.*/ ( !{pos:/N*/} >/" + clauseBreakers + "/ ( {pos:/V.*/}=clause ?>/.subj(pass)?/ {}=clausesubj ) )"));
  }});

  private static final SemgrexPattern LIMITED_CC_COLLAPSE
      = SemgrexPattern.compile("{}=root >/.*/=rel ( {}=a >/conj_.*/ {}=b )");

  /**
   * Do some limited CC collapsing.
   * @param tree The tree to perform the collapsing on.
   * @return The same tree. <b>THIS IS AN IN PLACE FUNCTION</b>
   */
  private static SemanticGraph tweakCC(SemanticGraph tree) {
    SemgrexMatcher matcher = LIMITED_CC_COLLAPSE.matcher(tree);
    List<SemanticGraphEdge> edgesToAdd = new ArrayList<>();  // Avoid a concurrent modification exception
    while (matcher.find()) {
      edgesToAdd.add(new SemanticGraphEdge(matcher.getNode("root"), matcher.getNode("b"),
          GrammaticalRelation.valueOf(GrammaticalRelation.Language.Any, matcher.getRelnString("rel")),
          Double.NEGATIVE_INFINITY, false));
    }
    for (SemanticGraphEdge edge : edgesToAdd) {
      tree.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), edge.getWeight(), edge.isExtra());
    }
    return tree;
  }

  /**
   * Split a given subtree off of the main tree.
   * This will do two things:
   *
   * <ul>
   *   <li>It will return a {@link edu.stanford.nlp.semgraph.SemanticGraph} consisting of the subtree split off.</li>
   *   <li>It will delete all the nodes in the original tree which were split off into the subtree. </li>
   * </ul>
   * @param tree The original tree; this will be smaller when we return.
   * @param subtreeRoot The root of the subtree we are splitting off.
   * @param subjectOrNull An optional subject to clone into the split subtree. This will appear in both trees.
   * @return The split off tree.
   */
  private SemanticGraph splitOffTree(SemanticGraph tree, IndexedWord subtreeRoot, IndexedWord subjectOrNull) {
    SemanticGraph subtree = new SemanticGraph();
    subtree.addRoot(subtreeRoot);
    // Initialize the search
    Stack<IndexedWord> fringe = new Stack<>();
    for (IndexedWord child : tree.getChildren(subtreeRoot)) {
      fringe.add(child);
    }
    // Run the search
    Set<Integer> seen = new HashSet<>();
    while (!fringe.isEmpty()) {
      IndexedWord node = fringe.pop();
      if (seen.contains(node.index())) {
        continue;
      }
      seen.add(node.index());
      subtree.addVertex(node);
      for (SemanticGraphEdge incomingEdge : tree.incomingEdgeIterable(node)) {
        subtree.addEdge(incomingEdge.getGovernor(), incomingEdge.getDependent(), incomingEdge.getRelation(), incomingEdge.getWeight(), incomingEdge.isExtra());
      }
      for (IndexedWord child : tree.getChildren(node)) {
        if (child.index() != node.index()) {  // wat...?
          fringe.add(child);
        }
      }
    }
    // Delete from original tree
    for (IndexedWord vertex : subtree.vertexSet()) {
      tree.removeVertex(vertex);
    }
    tree.removeVertex(subtreeRoot);
    // Optionally clone the subject
    if (subjectOrNull != null) {
      subtree.addVertex(subjectOrNull);
      for (SemanticGraphEdge incomingEdge : tree.incomingEdgeIterable(subjectOrNull)) {
        subtree.addEdge(subtreeRoot, subjectOrNull, incomingEdge.getRelation(), incomingEdge.getWeight(), incomingEdge.isExtra());
      }
    }
    // Return
    return subtree;
  }

  /**
   * Split a tree into constituent clauses
   * @param rawTree The tree to split into clauses.
   * @return A list of clauses in this sentence.
   */
  private List<SemanticGraph> coarseClauseSplitting(SemanticGraph rawTree) {
    List<SemanticGraph> clauses = new ArrayList<>();
    List<SemanticGraph> toRecurse = new ArrayList<>();
    SemanticGraph original = new SemanticGraph(rawTree);
    for (SemgrexPattern pattern : CLAUSE_PATTERNS) {
      boolean foundMatch = true;
      while (foundMatch) {
        SemgrexMatcher matcher = pattern.matcher(original);
        if (matcher.find()) {  // Note(gabor): Can't do 'while' here or else we risk a ConcurrentModificationException
          IndexedWord subjectOrNull = matcher.getNode("subject");
          IndexedWord clauseRoot = matcher.getNode("clause");
          IndexedWord clauseSubjectOrNull = matcher.getNode("clausesubj");
          SemanticGraph clause;
          if (clauseSubjectOrNull != null || subjectOrNull == null) {
            // Case: independent clause; no need to copy the subject
            clause = splitOffTree(original, clauseRoot, null);
          } else {
            // Case: copy subject from main clause
            //noinspection ConstantConditions
            assert subjectOrNull != null;
            clause = splitOffTree(original, clauseRoot, subjectOrNull);
          }
          if (original.isEmpty()) {
            clauses.add(clause);
          } else {
            toRecurse.add(clause);
          }
        } else {
          foundMatch = false;
        }
      }
    }
    // Recursive case: recurse on clauses
    // Note(gabor): This should be outside of the pattern matching loop, or else we risk a ConcurrentModificationException.
    for (SemanticGraph clause : toRecurse) {
      clauses.addAll(coarseClauseSplitting(clause));
    }
    // Base case: just add the original tree
    if (original.vertexSet().size() > 0) {
      clauses.add(tweakCC(original));
    }
    // Return
    return clauses;
  }

  /**
   * Fix some bizarre peculiarities with certain trees.
   * So far, these include:
   * <ul>
   *   <li>Sometimes there's a node from a word to itself. This seems wrong.</li>
   * </ul>
   * @param tree The tree to clean (in place!)
   */
  private static void cleanTree(SemanticGraph tree) {
    // Clean nodes
    List<IndexedWord> toDelete = new ArrayList<>();
    for (IndexedWord vertex : tree.vertexSet()) {
      // Clean punctuation
      char tag = vertex.backingLabel().tag().charAt(0);
      if (tag == '.' || tag == ',' || tag == '(' || tag == ')' || tag == ':') {
        toDelete.add(vertex);
      }
    }
    for (IndexedWord v : toDelete) { tree.removeVertex(v); }
    // Clean edges
    Iterator<SemanticGraphEdge> iter = tree.edgeIterable().iterator();
    while (iter.hasNext()) {
      SemanticGraphEdge edge = iter.next();
      if (edge.getDependent().index() == edge.getGovernor().index()) {
        // Clean self-edges
        iter.remove();
      } else if (edge.getRelation().toString().equals("punct")) {
        // Clean punctuation (again)
        iter.remove();
      }
    }
  }

  public List<RelationTriple> relationInClause(SemanticGraph tree) {
    if (tree.size() == 0) {
      if (tree.getRoots().size() == 0) {
        System.err.println("WARNING: empty tree passed to " + this.getClass().getSimpleName() + ".relationInClause()");
      }
      return Collections.emptyList();
    }
    // Set the index mapping
    List<IndexedWord> vertices = tree.vertexListSorted();
    if (vertices.size() >= 64) {
      return Collections.emptyList();
    }
    byte[] indexToMaskIndex = new byte[vertices.get(vertices.size() - 1).index()];
    byte i = 0;
    for (IndexedWord vertex : vertices) {
      indexToMaskIndex[vertex.index() - 1] = i;
      i += 1;
    }
    // Run the search
    List<SearchResult> results = search(null, tree, indexToMaskIndex);
    // Process the result
    List<RelationTriple> triples = new ArrayList<>();
    Optional<RelationTriple> rootExtraction = RelationTriple.segment(tree, Optional.empty());
    if (rootExtraction.isPresent()) {
      triples.add(rootExtraction.get());
    }
    for (SearchResult result : results) {
      Optional<RelationTriple> extraction = RelationTriple.segment(result.tree, Optional.of(result.confidence));
      if (extraction.isPresent()) {
        triples.add(extraction.get());
      }
    }
    return triples;
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
    SemanticGraph fullTree = new SemanticGraph(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
    cleanTree(fullTree);
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (tokens.size() > 63) {
      System.err.println("Very long sentence (>63 tokens); " + this.getClass().getSimpleName() + " is not attempting to extract relations.");
      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, Collections.EMPTY_LIST);
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, Collections.EMPTY_LIST);
    } else {
      List<SemanticGraph> clauses = coarseClauseSplitting(fullTree);
      Collection<SentenceFragment> fragments = new ArrayList<>();
      List<RelationTriple> extractions = new ArrayList<>();
      // Add clauses
      if (clauses.size() > 1) {
        for (SemanticGraph tree : clauses) {
          fragments.add(new SentenceFragment(tree, false));
          Optional<RelationTriple> extraction = RelationTriple.segment(tree, Optional.empty());
          if (extraction.isPresent()) {
            extractions.add(extraction.get());
          }
        }
      }
      // Add search results
      for (SemanticGraph tree : clauses) {
        if (tree.size() > 0) {
          // Set the index mapping
          byte[] indexToMaskIndex = new byte[sentence.size()];
          byte i = 0;
          for (IndexedWord vertex : tree.vertexListSorted()) {
            indexToMaskIndex[vertex.index() - 1] = i;
            i += 1;
          }
          // Run the search
          List<SearchResult> results = search(tokens, tree, indexToMaskIndex);
          // Process the results
          for (SearchResult result : results) {
            SentenceFragment fragment = new SentenceFragment(result.tree, false);
            fragments.add(fragment);
            Optional<RelationTriple> extraction = RelationTriple.segment(result.tree, Optional.of(result.confidence));
            if (extraction.isPresent()) {
              extractions.add(extraction.get());
            }
          }
        }
      }
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, fragments);
      switch (OPTIMIZE_FOR) {
        case GENERAL:
          Collections.sort(extractions);
          sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, extractions);
        case KB:
          List<RelationTriple> triples = extractions.stream().map(x -> RelationTriple.optimizeForKB(x, sentence, canonicalMentionMap)).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList());
          Collections.sort(triples);
          sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, triples);
      }
    }
  }

  /**
   * A simple utility function for just getting a list of relation triples from a sentence.
   * Calls {@link OpenIE#annotate(edu.stanford.nlp.pipeline.Annotation)} on the backend.
   */
  @SuppressWarnings("UnusedDeclaration")
  public Collection<RelationTriple> relationsForSentence(CoreMap sentence) {
    annotateSentence(sentence, new IdentityHashMap<>());
    return sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
  }

  /**
   * A utility to get useful information out of a CorefMention. In particular, it reutnrs the CoreLabels which are
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
    for (CoreLabel token : mentionAsTokens) {
      if (token.ner() != null && !"O".equals(token.ner())) {
        nerVotes.incrementCount(token.ner());
      }
    }
    String ner = Counters.argmax(nerVotes, (o1, o2) -> o1 == null ? 0 : o1.compareTo(o2));
    double nerCount = nerVotes.getCount(ner);
    double nerScore = nerCount * nerCount / ((double) mentionAsTokens.size());
    // Return
    return Pair.makePair(mentionAsTokens, nerScore);
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
      for (RelationTriple extraction : extractions) {
        System.out.println(extraction);
      }
    }
  }
}
