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


  @SuppressWarnings("UnusedDeclaration")
  public OpenIE() {}

  public OpenIE(Properties props) {
    Execution.fillOptions(this, props);
  }

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

  private static List<SearchResult> search(List<CoreLabel> sentence, SemanticGraph originalTree) {

    // Pre-process the tree
    originalTree = new SemanticGraph(originalTree);
    // (remove common determiners)
    for (IndexedWord vertex : originalTree.getLeafVertices()) {
      if (vertex.word().equalsIgnoreCase("the") || vertex.word().equalsIgnoreCase("a") ||
          vertex.word().equalsIgnoreCase("an")) {
        originalTree.removeVertex(vertex);
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
        if (  ((state.deletionMask >>> (nextWord.index() - 1)) & 0x1) == 0) {
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
          newMask |= (0x1 << (vertex.index() - 1));
        }
        SemanticGraph resultTree = new SemanticGraph(treeWithDeletions);
        for (SemanticGraphEdge edge : andsToAdd) {
          if (resultTree.containsVertex(edge.getGovernor()) && resultTree.containsVertex(edge.getDependent())) {
            resultTree.addEdge(edge.getGovernor(), edge.getDependent(), edge.getRelation(), Double.NEGATIVE_INFINITY, false);
          }
        }
        results.add(new SearchResult(resultTree, aggregateDeletedEdges(state, state.tree.incomingEdgeIterable(currentWord))));

        // Push the state with this subtree deleted
        nextIndex = state.currentIndex + 1;
        while (nextIndex < topologicalVertices.size()) {
          IndexedWord nextWord = topologicalVertices.get(nextIndex);
          if (  ((newMask >>> (nextWord.index() - 1)) & 0x1) == 0) {
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

  private static List<String> aggregateDeletedEdges(SearchState state, Iterable<SemanticGraphEdge> semanticGraphEdges) {
    List<String> rtn = new ArrayList<>();
    for (SemanticGraphEdge edge : semanticGraphEdges) {
      rtn.add(edge.getRelation().getShortName());
    }
    while (state != null) {
      if (state.lastDeletedEdge != null) {
        rtn.add(state.lastDeletedEdge);
      }
      state = state.source;
    }
    return rtn;
  }


  @SuppressWarnings("unchecked")
  public void annotateSentence(CoreMap sentence) {
    SemanticGraph tree = new SemanticGraph(sentence.get(SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation.class));
    List<CoreLabel> tokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
    if (tokens.size() > 63) {
      System.err.println("Very long sentence (>63 tokens); " + this.getClass().getSimpleName() + " is not attempting to extract relations.");
      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, Collections.EMPTY_LIST);
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, Collections.EMPTY_LIST);
    } else {
      Collection<SentenceFragment> fragments = new ArrayList<>();
      Collection<RelationTriple> extractions = new ArrayList<>();
      for (SearchResult result : search(tokens, tree)) {
        SentenceFragment fragment = new SentenceFragment(result.tree, false);
        fragments.add(fragment);
        Optional<RelationTriple> extraction = RelationTriple.segment(result.tree);
        if (extraction.isPresent()) {
          extractions.add(extraction.get());
        }
      }
      sentence.set(NaturalLogicAnnotations.EntailedSentencesAnnotation.class, fragments);
      sentence.set(NaturalLogicAnnotations.RelationTriplesAnnotation.class, extractions);
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  public Collection<RelationTriple> relationsForSentence(CoreMap sentence) {
    annotateSentence(sentence);
    return sentence.get(NaturalLogicAnnotations.RelationTriplesAnnotation.class);
  }

  @Override
  public void annotate(Annotation annotation) {
    annotation.get(CoreAnnotations.SentencesAnnotation.class).forEach(this::annotateSentence);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(Annotator.OPENIE_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requires() {
    return Collections.singleton(Annotator.NATLOG_REQUIREMENT);
  }


  public static void main(String[] args) {
    // Initialize prerequisites
    Properties props = StringUtils.argsToProperties(args);
    OpenIE extractor = new OpenIE(props);
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
      for (RelationTriple extraction : extractions) {
        System.out.println(extraction);
      }
    }
  }
}
