package edu.stanford.nlp.naturalli;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.AnnotationPipeline;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.IterableIterator;
import edu.stanford.nlp.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * TODO(gabor) JavaDoc
 *
 * @author Gabor Angeli
 */
public class Util {

  /**
   * TODO(gabor) JavaDoc
   *
   * @param tokens
   * @param span
   * @return
   */
  public static String guessNER(List<CoreLabel> tokens, Span span) {
    Counter<String> nerGuesses = new ClassicCounter<>();
    for (int i : span) {
      nerGuesses.incrementCount(tokens.get(i).ner());
    }
    nerGuesses.remove("O");
    nerGuesses.remove(null);
    if (nerGuesses.size() > 0 && Counters.max(nerGuesses) >= span.size() / 2) {
      return Counters.argmax(nerGuesses);
    } else {
      return "O";
    }
  }

  /**
   * TODO(gabor) JavaDoc
   *
   * @param tokens
   * @return
   */
  public static String guessNER(List<CoreLabel> tokens) {
    return guessNER(tokens, new Span(0, tokens.size()));
  }

  /**
   * TODO(gabor) JavaDoc
   *
   * @param tokens
   * @param seed
   * @return
   */
  public static Span extractNER(List<CoreLabel> tokens, Span seed) {
    if (seed == null) {
      return new Span(0, 1);
    }
    if (tokens.get(seed.start()).ner() == null) {
      return seed;
    }
    int begin = seed.start();
    while (begin > 0 && tokens.get(begin - 1).ner().equals(tokens.get(seed.start()).ner())) {
      begin -= 1;
    }
    int end = seed.end() - 1;
    while (end < tokens.size() - 1 && tokens.get(end + 1).ner().equals(tokens.get(seed.end() - 1).ner())) {
      end += 1;
    }
    return Span.fromValues(begin, end + 1);
  }

  /**
   * TODO(gabor) JavaDoc
   *
   * @param sentence
   * @param pipeline
   */
  public static void annotate(CoreMap sentence, AnnotationPipeline pipeline) {
    Annotation ann = new Annotation(StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class), " "));
    ann.set(CoreAnnotations.TokensAnnotation.class, sentence.get(CoreAnnotations.TokensAnnotation.class));
    ann.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
    pipeline.annotate(ann);
  }

  /**
   * Fix some bizarre peculiarities with certain trees.
   * So far, these include:
   * <ul>
   * <li>Sometimes there's a node from a word to itself. This seems wrong.</li>
   * </ul>
   *
   * @param tree The tree to clean (in place!).
   * @return A list of extra edges, which are valid but were removed.
   */
  public static List<SemanticGraphEdge> cleanTree(SemanticGraph tree) {
    // Clean nodes
    List<IndexedWord> toDelete = new ArrayList<>();
    for (IndexedWord vertex : tree.vertexSet()) {
      // Clean punctuation
      char tag = vertex.backingLabel().tag().charAt(0);
      if (tag == '.' || tag == ',' || tag == '(' || tag == ')' || tag == ':') {
        if (!tree.outgoingEdgeIterator(vertex).hasNext()) {  // This should really never happen, but it does.
          toDelete.add(vertex);
        }
      }
    }
    toDelete.forEach(tree::removeVertex);

    // Clean edges
    Iterator<SemanticGraphEdge> iter = tree.edgeIterable().iterator();
    while (iter.hasNext()) {
      SemanticGraphEdge edge = iter.next();
      if (edge.getDependent().index() == edge.getGovernor().index()) {
        // Clean self-edges
        iter.remove();
      } else if (edge.getRelation().toString().equals("punct")) {
        // Clean punctuation (again)
        if (!tree.outgoingEdgeIterator(edge.getDependent()).hasNext()) {  // This should really never happen, but it does.
          iter.remove();
        }
      }
    }

    // Remove extra edges
    List<SemanticGraphEdge> extraEdges = new ArrayList<>();
    for (SemanticGraphEdge edge : tree.edgeIterable()) {
      if (edge.isExtra()) {
        if (tree.incomingEdgeList(edge.getDependent()).size() > 1) {
          extraEdges.add(edge);
        }
      }
    }
    extraEdges.forEach(tree::removeEdge);
    // Add apposition edges (simple coref)
    for (SemanticGraphEdge extraEdge : new ArrayList<>(extraEdges)) {  // note[gabor] prevent concurrent modification exception
      for (SemanticGraphEdge candidateAppos : tree.incomingEdgeIterable(extraEdge.getDependent())) {
        if (candidateAppos.getRelation().toString().equals("appos")) {
          extraEdges.add(new SemanticGraphEdge(extraEdge.getGovernor(), candidateAppos.getGovernor(), extraEdge.getRelation(), extraEdge.getWeight(), extraEdge.isExtra()));
        }
      }
      for (SemanticGraphEdge candidateAppos : tree.outgoingEdgeIterable(extraEdge.getDependent())) {
        if (candidateAppos.getRelation().toString().equals("appos")) {
          extraEdges.add(new SemanticGraphEdge(extraEdge.getGovernor(), candidateAppos.getDependent(), extraEdge.getRelation(), extraEdge.getWeight(), extraEdge.isExtra()));
        }
      }
    }

    // Brute force ensure tree
    // Remove incoming edges from roots
    List<SemanticGraphEdge> rootIncomingEdges = new ArrayList<>();
    for (IndexedWord root : tree.getRoots()) {
      for (SemanticGraphEdge incomingEdge : tree.incomingEdgeIterable(root)) {
        rootIncomingEdges.add(incomingEdge);
      }
    }
    rootIncomingEdges.forEach(tree::removeEdge);
    // Loop until it becomes a tree.
    boolean changed = true;
    while (changed) {  // I just want trees to be trees; is that so much to ask!?
      changed = false;
      List<IndexedWord> danglingNodes = new ArrayList<>();
      List<SemanticGraphEdge> invalidEdges = new ArrayList<>();

      for (IndexedWord vertex : tree.vertexSet()) {
        // Collect statistics
        Iterator<SemanticGraphEdge> incomingIter = tree.incomingEdgeIterator(vertex);
        boolean hasIncoming = incomingIter.hasNext();
        boolean hasMultipleIncoming = false;
        if (hasIncoming) {
          incomingIter.next();
          hasMultipleIncoming = incomingIter.hasNext();
        }

        // Register actions
        if (!hasIncoming && !tree.getRoots().contains(vertex)) {
          danglingNodes.add(vertex);
        } else {
          if (hasMultipleIncoming) {
            for (SemanticGraphEdge edge : new IterableIterator<>(incomingIter)) {
              invalidEdges.add(edge);
            }
          }
        }
      }

      // Perform actions
      for (IndexedWord vertex : danglingNodes) {
        tree.removeVertex(vertex);
        changed = true;
      }
      for (SemanticGraphEdge edge : invalidEdges) {
        tree.removeEdge(edge);
        changed = true;
      }
    }

    // Return
    assert isTree(tree);
    return extraEdges;
  }

  /**
   * A little utility function to make sure a SemanticGraph is a tree.
   * @param tree The tree to check.
   * @return True if this {@link edu.stanford.nlp.semgraph.SemanticGraph} is a tree (versus a DAG, or Graph).
   */
  public static boolean isTree(SemanticGraph tree) {
    for (IndexedWord vertex : tree.vertexSet()) {
      if (tree.getRoots().contains(vertex)) {
        if (tree.incomingEdgeIterator(vertex).hasNext()) {
          return false;
        }
      } else {
        Iterator<SemanticGraphEdge> iter = tree.incomingEdgeIterator(vertex);
        if (!iter.hasNext()) {
          return false;
        }
        iter.next();
        if (iter.hasNext()) {
          return false;
        }
      }
    }
    return true;
  }
}
