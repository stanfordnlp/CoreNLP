package edu.stanford.nlp.naturalli;

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
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;

/**
 * A simple OpenIE system based on valid Natural Logic deletions of a sentence.
 *
 * @author Gabor Angeli
 */
public class OpenIE implements Annotator {


  /** Create a new OpenIE system, with default properties */
  @SuppressWarnings("UnusedDeclaration")
  public OpenIE() {}

  /**
   * Create a ne OpenIE system, based on the given properties.
   * @param props The properties to parameterize the system with.
   */
  public OpenIE(Properties props) {
    Execution.fillOptions(this, props);
  }

  /**
   * A result from the search over possible shortenings of the sentence.
   */
  private static class SearchResult {
    public final SemanticGraph tree;
    public final List<String> deletedEdges;

    private SearchResult(SemanticGraph tree, List<String> deletedEdges) {
      this.tree = tree;
      this.deletedEdges = deletedEdges;
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

    private SearchState(long deletionMask, int currentIndex, SemanticGraph tree, String lastDeletedEdge, SearchState source) {
      this.deletionMask = deletionMask;
      this.currentIndex = currentIndex;
      this.tree = tree;
      this.lastDeletedEdge = lastDeletedEdge;
      this.source = source;
    }
  }

  /**
   * The search algorithm, starting with a full sentence and iteratively shortening it to its entailed sentences.
   * @param sentence The sentence to begin with.
   * @param originalTree The original tree of the sentence we are beginning with
   * @return A list of search results, corresponding to shortenings of the sentence.
   */
  private static List<SearchResult> search(List<CoreLabel> sentence, SemanticGraph originalTree) {
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

    // Outputs
    List<SearchResult> results = new ArrayList<>();
    if (!determinerRemovals.isEmpty()) {
      if (andsToAdd.isEmpty()) {
        results.add(new SearchResult(originalTree, determinerRemovals));
      } else {
        SemanticGraph treeWithAnds = new SemanticGraph(originalTree);
        for (SemanticGraphEdge and : andsToAdd) {
          treeWithAnds.addEdge(and.getGovernor(), and.getDependent(), and.getRelation(), Double.NEGATIVE_INFINITY, false);
        }
        results.add(new SearchResult(treeWithAnds, determinerRemovals));
      }
    }

    // Initialize the search
    List<IndexedWord> topologicalVertices = originalTree.topologicalSort();
    Stack<SearchState> fringe = new Stack<>();
    fringe.push(new SearchState(0l, 0, originalTree, null, null));

    // Start the search
    while (!fringe.isEmpty()) {
      SearchState state = fringe.pop();
      IndexedWord currentWord = topologicalVertices.get(state.currentIndex);

      // Push the case where we don't delete
      int nextIndex = state.currentIndex + 1;
      while (nextIndex < topologicalVertices.size()) {
        IndexedWord nextWord = topologicalVertices.get(nextIndex);
        if (  ((state.deletionMask >>> (nextWord.index() - 1)) & 0x1l) == 0) {
          fringe.push(new SearchState(state.deletionMask, nextIndex, state.tree, null, state));
          break;
        } else {
          nextIndex += 1;
        }
      }

      // Check if we can delete this subtree
      boolean canDelete = state.tree.getFirstRoot() != currentWord;
      for (SemanticGraphEdge edge : state.tree.incomingEdgeIterable(currentWord)) {
        // Get token information
        CoreLabel token = sentence.get(edge.getDependent().index() - 1);
        Polarity tokenPolarity = token.get(NaturalLogicAnnotations.PolarityAnnotation.class);
        // Get the relation for this deletion
        NaturalLogicRelation lexicalRelation;
        if (edge.getRelation().getShortName().endsWith("obj")) {
          boolean isIntransitive = false;
          for (SemanticGraphEdge sibling : state.tree.outgoingEdgeIterable(edge.getGovernor())) {
            if (sibling.getRelation().getShortName().equals("prep") &&
              (INTRANSITIVE_PREPOSITIONS.contains(sibling.getRelation().getSpecific()) ||
                  INTRANSITIVE_PREPOSITIONS.contains(sibling.getTarget().word().toLowerCase())) ) {
              isIntransitive = true; // TODO(gabor) better intransitive checking
            }
          }
          if (isIntransitive) {
            lexicalRelation = NaturalLogicRelation.FORWARD_ENTAILMENT;
          } else {
            lexicalRelation = NaturalLogicRelation.forDependencyDeletion(edge.getRelation().toString());
          }
        } else {
          lexicalRelation = NaturalLogicRelation.forDependencyDeletion(edge.getRelation().toString());
        }
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
          newMask |= (0x1l << (vertex.index() - 1));
          assert vertex.index() <= 64;
          assert ((newMask >>> (vertex.index() - 1)) & 0x1l) == 1;
        }
        SemanticGraph resultTree = new SemanticGraph(treeWithDeletions);
        for (SemanticGraphEdge edge : andsToAdd) {
          if (resultTree.containsVertex(edge.getGovernor()) && resultTree.containsVertex(edge.getDependent())) {
            resultTree.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), Double.NEGATIVE_INFINITY, false);
          }
        }
        results.add(new SearchResult(resultTree,
            aggregateDeletedEdges(state, state.tree.incomingEdgeIterable(currentWord), determinerRemovals)));

        // Push the state with this subtree deleted
        nextIndex = state.currentIndex + 1;
        while (nextIndex < topologicalVertices.size()) {
          IndexedWord nextWord = topologicalVertices.get(nextIndex);
          if (  ((newMask >>> (nextWord.index() - 1)) & 0x1l) == 0) {
            assert treeWithDeletions.containsVertex(topologicalVertices.get(nextIndex));
            fringe.push(new SearchState(newMask, nextIndex, treeWithDeletions, null, state));
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
  private static final List<SemgrexPattern> CLAUSE_PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>(){{
    String clauseBreakers = "vmod|prepc.*|advcl|conj(_and)?|prep_.*";
    add(SemgrexPattern.compile("{$} ?>/.subj(pass)?/ {}=subject >/"+clauseBreakers+"/ ( {pos:/V.*/}=clause ?>/.subj(pass)?/ {}=clausesubj )"));
    add(SemgrexPattern.compile("{$} ?>/.subj(pass)?/ {}=subject >/.obj|prep.*/ ( {} >/"+clauseBreakers+"/ ( {pos:/V.*/}=clause ?>/.subj(pass)?/ {}=clausesubj ) )"));
  }});

  private static final SemgrexPattern LIMITED_CC_COLLAPSE
      = SemgrexPattern.compile("{}=root >/.*/=rel ( {}=a >/conj_.*/ {}=b )");

  private static final Set<String> INTRANSITIVE_PREPOSITIONS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("at");
  }});

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
    while (!fringe.isEmpty()) {
      IndexedWord node = fringe.pop();
      subtree.addVertex(node);
      for (SemanticGraphEdge incomingEdge : tree.incomingEdgeIterable(node)) {
        subtree.addEdge(incomingEdge.getGovernor(), incomingEdge.getDependent(), incomingEdge.getRelation(), incomingEdge.getWeight(), incomingEdge.isExtra());
      }
      for (IndexedWord child : tree.getChildren(node)) {
        fringe.add(child);
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
    SemanticGraph original = null;
    for (SemgrexPattern pattern : CLAUSE_PATTERNS) {
      SemgrexMatcher matcher = pattern.matcher(original != null ? original : rawTree);
      while (matcher.find()) {
        if (original == null) {
          original = new SemanticGraph(rawTree);
        }
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
        clauses.addAll(coarseClauseSplitting(clause));
      }
    }
    // Base case: just add the original tree
    if (clauses.isEmpty()) {
      clauses.add(tweakCC(rawTree));
    } else if (original != null && original.vertexSet().size() > 0) {
      clauses.add(tweakCC(original));
    }
    // Return
    return clauses;
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
  public void annotateSentence(CoreMap sentence) {
    SemanticGraph fullTree = new SemanticGraph(sentence.get(SemanticGraphCoreAnnotations.CollapsedDependenciesAnnotation.class));
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (tokens.size() > 63) {
      System.err.println("Very long sentence (>63 tokens); " + this.getClass().getSimpleName() + " is not attempting to extract relations.");
      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, Collections.EMPTY_LIST);
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, Collections.EMPTY_LIST);
    } else {
      List<SemanticGraph> clauses = coarseClauseSplitting(fullTree);
      Collection<SentenceFragment> fragments = new ArrayList<>();
      Collection<RelationTriple> extractions = new ArrayList<>();
      // Add clauses
      if (clauses.size() > 1) {
        for (SemanticGraph tree : clauses) {
          fragments.add(new SentenceFragment(tree, false));
          Optional<RelationTriple> extraction = RelationTriple.segment(tree);
          if (extraction.isPresent()) {
            extractions.add(extraction.get());
          }
        }
      }
      // Add search results
      for (SemanticGraph tree : clauses) {
        List<SearchResult> results = search(tokens, tree);
        for (SearchResult result : results) {
          SentenceFragment fragment = new SentenceFragment(result.tree, false);
          fragments.add(fragment);
          Optional<RelationTriple> extraction = RelationTriple.segment(result.tree);
          if (extraction.isPresent()) {
            extractions.add(extraction.get());
          }
        }
      }
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, fragments);
      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, extractions);
    }
  }

  /**
   * A simple utility function for just getting a list of relation triples from a sentence.
   * Calls {@link OpenIE#annotate(edu.stanford.nlp.pipeline.Annotation)} on the backend.
   */
  @SuppressWarnings("UnusedDeclaration")
  public Collection<RelationTriple> relationsForSentence(CoreMap sentence) {
    annotateSentence(sentence);
    return sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
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
    annotation.get(CoreAnnotations.SentencesAnnotation.class).forEach(this::annotateSentence);
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
    props.setProperty("annotators", "tokenize,ssplit,parse,natlog,openie");
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
