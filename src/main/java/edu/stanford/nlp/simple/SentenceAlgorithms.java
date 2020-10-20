package edu.stanford.nlp.simple;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.pipeline.CoreNLPProtos;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>
 *   A set of common utility algorithms for working with sentences (e.g., finding the head of a span).
 *   These are not intended to be perfect, or even the canonical version of these algorithms.
 *   They should only be trusted for prototyping, and more careful attention should be paid in cases
 *   where the performance of the task is important or the domain is unusual.
 * </p>
 *
 * <p>
 *   For developers: this class is intended to be where <i>domain independent</i> and
 *   <i>broadly useful</i> functions on a sentence would go, rather than polluting the {@link Sentence}
 *   class itself.
 * </p>
 *
 * @author Gabor Angeli
 */
public class SentenceAlgorithms {

  /** The underlying {@link Sentence}. */
  public final Sentence sentence;

  /**
   * Create a new algorithms object, based off of a sentence.
   *
   * @see Sentence#algorithms()
   */
  public SentenceAlgorithms(Sentence impl) {
    this.sentence = impl;
  }

  /**
   * Returns a collection of keyphrases, defined as relevant noun phrases and verbs in the sentence.
   * Each token of the sentence is consumed at most once.
   *
   * What counts as a keyphrase is in general quite subjective -- this method is just one possible interpretation
   * (in particular, Gabor's interpretation).
   * Please don't rely on this method to produce exactly your interpretation of what a keyphrase is.
   *
   * @return A list of spans in the sentence, where each one corresponds to a keyphrase.
   *
   * @author Gabor Angeli
   */
  public List<Span> keyphraseSpans() {
    //
    // Implementation note:
    //   This is implemented roughly as a finite state automata, looking for sequences of nouns, adjective+nouns, verbs,
    //   and a few special cases of prepositions.
    //   The code defines a transition matrix, based on POS tags and lemmas, where at each word we update the valid next
    //   tags/words based on the current tag/word we see.
    // Note: The tag 'B' is used for the verb "to be", rather than the usual 'V' tag.
    // Note: The tag 'X' is used for proper nouns, rather than the usual 'N' tag.
    // Note: The tag 'Z' is used for possessives, rather than the usual 'P' tag.
    //

    // The output
    List<Span> spans = new ArrayList<>();
    // The marker for where the last span began
    int spanBegin = -1;
    // The expected next states
    final Set<Character> expectNextTag = new HashSet<>();
    final Set<String> expectNextLemma = new HashSet<>();
    // A special marker for when we look-ahead and only accept the last word if
    // the word after it is ok (e.g., PP attachments).
    boolean inLookahead = false;

    // The transition matrix, over POS tags.
    Consumer<Character> updateExpectation = coarseTag -> {
      if (coarseTag == 'N') {
        expectNextTag.clear();
        expectNextTag.add('N');
        expectNextTag.add('X');
        expectNextLemma.clear();
        expectNextLemma.add("of");
        expectNextLemma.add("'s");
      } else if (coarseTag == 'G') {
        expectNextTag.clear();
        expectNextTag.add('N');  // 'water freezing' is fishy, but 'freezing water' is ok.
        expectNextLemma.clear();
      } else if (coarseTag == 'X') {
        expectNextTag.clear();
        expectNextTag.add('X');
        expectNextLemma.clear();
      } else if (coarseTag == 'J') {
        expectNextTag.clear();
        expectNextTag.add('N');
        expectNextTag.add('X');
        expectNextTag.add('J');
        expectNextLemma.clear();
      } else if (coarseTag == 'V') {
        expectNextTag.clear();
        expectNextTag.add('V');
        expectNextLemma.clear();
      } else if (coarseTag == 'Z') {
        expectNextTag.clear();
        expectNextTag.add('J');
        expectNextTag.add('N');
        expectNextLemma.clear();
      } else if (coarseTag == 'I') {
        expectNextTag.clear();
        expectNextTag.add('N');
        expectNextTag.add('X');
        expectNextTag.add('J');
        expectNextLemma.clear();
      } else {
        throw new IllegalStateException("Cannot update expected next token for POS tag: " + coarseTag);
      }
    };

    // Run the FSA:
    for (int i = 0; i < sentence.length(); ++i) {
      // Get some variables
      String tag = sentence.posTag(i);
      char coarseTag = Character.toUpperCase(tag.charAt(0));
      String lemma = sentence.lemma(i).toLowerCase();
      // Tweak the tag
      if (coarseTag == 'V' && lemma.equals("be")) {
        coarseTag = 'B';
      } else if (tag.startsWith("NNP")) {
        coarseTag = 'X';
      } else if (tag.startsWith("POS")) {
        coarseTag = 'Z';
      }
      // (don't collapse 'ing' nouns)
      if (coarseTag == 'N' && sentence.word(i).endsWith("ing")) {
        coarseTag = 'G';
      }

      // Transition
      if (spanBegin < 0 && !sentence.word(i).equals("%") &&
          (coarseTag == 'N' || coarseTag == 'V' || coarseTag == 'J' || coarseTag == 'X' || coarseTag == 'G')) {
        // Case: we were not in a span, but we hit a valid start tag.
        spanBegin = i;
        updateExpectation.accept(coarseTag);
        inLookahead = false;
      } else if (spanBegin >= 0) {
        // Case: we're in a span
        if (expectNextTag.contains(coarseTag)) {
          // Case: we hit a valid expected POS tag.
          //       update the transition matrix.
          updateExpectation.accept(coarseTag);
          inLookahead = false;
        } else if (expectNextLemma.contains(lemma)) {
          // Case: we hit a valid word. Do something special.
          switch (lemma) {
            case "of":
              // These prepositions are valid to subsume into a noun phrase.
              // Update the transition matrix, and mark this as conditionally ok.
              updateExpectation.accept('I');
              inLookahead = true;
              break;
            case "'s":
              // Possessives often denote a longer compound phrase
              updateExpectation.accept('Z');
              inLookahead = true;
              break;
            default:
              throw new IllegalStateException("Unknown special lemma: " + lemma);
          }
        } else {
          // Case: We have transitioned to an 'invalid' state, and therefore the span should end.
          if (inLookahead) {
            // If we were in a lookahead token, ignore the last token (as per the lookahead definition)
            spans.add(Span.fromValues(spanBegin, i - 1));
          } else {
            // Otherwise, add the span
            spans.add(Span.fromValues(spanBegin, i));
          }
          // We may also have started a new span.
          // Check to see if we have started a new span.
          if (coarseTag == 'N' || coarseTag == 'V' || coarseTag == 'J' || coarseTag == 'X' || coarseTag == 'G') {
            spanBegin = i;
            updateExpectation.accept(coarseTag);
          } else {
            spanBegin = -1;
          }
          inLookahead = false;
        }
      }
    }

    // Add a potential last span
    if (spanBegin >= 0) {
      spans.add(Span.fromValues(spanBegin, inLookahead ? sentence.length() - 1 : sentence.length()));
    }
    // Return
    return spans;
  }


  /**
   * Get the keyphrases of the sentence as a list of Strings.
   *
   * @param toString The function to use to convert a span to a string. The canonical case is Sentence::words
   * @return A list of keyphrases, as Strings.
   *
   * @see edu.stanford.nlp.simple.SentenceAlgorithms#keyphraseSpans()
   */
  public List<String> keyphrases(Function<Sentence, List<String>> toString) {
    return keyphraseSpans().stream().map(x -> StringUtils.join(toString.apply(sentence).subList(x.start(), x.end()), " ")).collect(Collectors.toList());
  }

  /**
   * The keyphrases of the sentence, using the words of the sentence to convert a span into a keyphrase.
   * @return A list of String keyphrases in the sentence.
   *
   * @see edu.stanford.nlp.simple.SentenceAlgorithms#keyphraseSpans()
   */
  public List<String> keyphrases() {
    return keyphrases(Sentence::words);
  }

  /**
   * Get the index of the head word for a given span, based off of the dependency parse.
   *
   * @param tokenSpan The span of tokens we are finding the head of.
   * @return The head index of the given span of tokens.
   */
  public int headOfSpan(Span tokenSpan) {
    // Error checks
    if (tokenSpan.size() == 0) {
      throw new IllegalArgumentException("Cannot find head word of empty span!");
    }
    List<Optional<Integer>> governors = sentence.governors();
    if (tokenSpan.start() >= governors.size()) {
      throw new IllegalArgumentException("Span is out of range: " + tokenSpan + "; sentence: " + sentence);
    }
    if (tokenSpan.end() > governors.size()) {
      throw new IllegalArgumentException("Span is out of range: " + tokenSpan + "; sentence: " + sentence);
    }

    // Find where to start searching up the dependency tree
    int candidateStart = tokenSpan.end() - 1;
    Optional<Integer> parent;
    while ( !(parent = governors.get(candidateStart)).isPresent() ) {
      candidateStart -= 1;
      if (candidateStart < tokenSpan.start()) {
        // Case: nothing in this span has a head. Default to right-most element.
        return tokenSpan.end() - 1;
      }
    }
    int candidate = candidateStart;

    // Search up the dependency tree
    Set<Integer> seen = new HashSet<>();
    while (parent.isPresent() && parent.get() >= tokenSpan.start() && parent.get() < tokenSpan.end()) {
      candidate = parent.get();
      if (seen.contains(candidate)) {
        return candidate;
      }
      seen.add(candidate);
      parent = governors.get(candidate);
    }

    // Return
    return candidate;
  }

  /**
   * Return all the spans of a sentence. So, for example, a sentence "a b c" would return:
   * [a], [b], [c], [a b], [b c], [a b c].
   *
   * @param selector The function to apply to each token. For example, {@link Sentence#words}.
   *                 For that example, you can use <code>allSpans(Sentence::words)</code>.
   * @param maxLength The maximum length of the spans to extract. The default to extract all spans
   *                  is to set this to <code>sentence.length()</code>.
   * @param <E> The type of the element we are getting.
   *
   * @return A streaming iterable of spans for this sentence.
   */
  public <E> Iterable<List<E>> allSpans(Function<Sentence, List<E>> selector, int maxLength) {
    return () -> new Iterator<List<E>>() {
      private int length = maxLength > sentence.length() ? sentence.length() : maxLength;
      private int start = 0;
      @Override
      public boolean hasNext() {
        return length > 0;
      }
      @Override
      public List<E> next() {
        // Get the term
        List<E> rtn = selector.apply(sentence).subList(start, start + length);
        // Update the state
        start += 1;
        if (start + length > sentence.length()) {
          length -= 1;
          start = 0;
        }
        // Return
        return rtn;
      }
    };
  }

  /** @see SentenceAlgorithms#allSpans(Function, int) */
  public <E> Iterable<List<E>> allSpans(Function<Sentence, List<E>> selector) {
    return allSpans(selector, sentence.length());
  }

  /** @see SentenceAlgorithms#allSpans(Function, int) */
  public Iterable<List<String>> allSpans() {
    return allSpans(Sentence::words, sentence.length());
  }

  /**
   * Select the most common element of the given type in the given span.
   * This is useful for, e.g., finding the most likely NER span of a given span, or the most
   * likely POS tag of a given span.
   * Null entries are removed.
   *
   * @param span The span of the sentence to find the mode element in. This must be entirely contained in the sentence.
   * @param selector The property of the sentence we are getting the mode of. For example, <code>Sentence::posTags</code>
   * @param <E> The type of the element we are getting.
   * @return The most common element of the given property in the sentence.
   */
  public <E> E modeInSpan(Span span, Function<Sentence, List<E>> selector) {
    if (!Span.fromValues(0, sentence.length()).contains(span)) {
      throw new IllegalArgumentException("Span must be entirely contained in the sentence: " + span + " (sentence length=" + sentence.length() + ")");
    }
    Counter<E> candidates = new ClassicCounter<>();
    for (int i : span) {
      candidates.incrementCount(selector.apply(sentence).get(i));
    }
    candidates.remove(null);
    return Counters.argmax(candidates);
  }


  /**
   * Run a proper BFS over a dependency graph, finding the shortest path between two vertices.
   *
   * @param start The start index.
   * @param end The end index.
   * @param selector The selector to use for the word nodes.
   *
   * @return A path string, analogous to {@link #dependencyPathBetween(int, int)}
   */
  protected List<String> loopyDependencyPathBetween(int start, int end, Optional<Function<Sentence, List<String>>> selector) {
    // Find the start and end
    SemanticGraph graph = this.sentence.dependencyGraph();
    IndexedWord[] indexedWords = new IndexedWord[this.sentence.length()];
    for (IndexedWord vertex : graph.vertexSet()) {
      indexedWords[vertex.index() - 1] = vertex;
    }

    // Set up the search
    BitSet seen = new BitSet();
    int[] backpointers = new int[sentence.length()];
    Arrays.fill(backpointers, -1);
    Queue<IndexedWord> fringe = new LinkedList<>();
    fringe.add(indexedWords[start]);

    // Run the search
    while (!fringe.isEmpty()) {
      IndexedWord vertex = fringe.poll();
      int vertexIndex = vertex.index() - 1;
      if (seen.get(vertexIndex)) {
        continue;  // should not reach here
      }
      seen.set(vertexIndex);
      for (SemanticGraphEdge inEdge : graph.incomingEdgeIterable(vertex)) {
        IndexedWord governor = inEdge.getGovernor();
        int govIndex = governor.index() - 1;
        if (!seen.get(govIndex)) {
          backpointers[govIndex] = vertexIndex;
          if (govIndex == end) {
            break;
          } else {
            fringe.add(governor);
          }
        }
      }
      for (SemanticGraphEdge outEdge : graph.outgoingEdgeIterable(vertex)) {
        IndexedWord dependent = outEdge.getDependent();
        int depIndex = dependent.index() - 1;
        if (!seen.get(depIndex)) {
          backpointers[depIndex] = vertexIndex;
          if (depIndex == end) {
            break;
          } else {
            fringe.add(dependent);
          }
        }
      }
    }

    // Infer the path
    ArrayList<String> path = new ArrayList<>();
    Optional<List<String>> words = selector.map(x -> x.apply(sentence));
    int vertex = end;
    while (vertex != start) {
      // 1. Add the word
      if (words.isPresent()) {
        path.add(words.get().get(vertex));
      }
      // 2. Find the parent
      for (SemanticGraphEdge inEdge : graph.incomingEdgeIterable(indexedWords[vertex])) {
        int governor = inEdge.getGovernor().index() - 1;
        if (backpointers[vertex] == governor) {
          path.add("-" + inEdge.getRelation().toString() + "->");
          break;
        }
      }
      for (SemanticGraphEdge outEdge : graph.outgoingEdgeIterable(indexedWords[vertex])) {
        int dependent = outEdge.getDependent().index() - 1;
        if (backpointers[vertex] == dependent) {
          path.add("<-" + outEdge.getRelation().toString() + "-");
          break;
        }
      }
      // 3. Update the node
      vertex = backpointers[vertex];
    }
    words.ifPresent(strings -> path.add(strings.get(start)));
    Collections.reverse(path);
    return path;
  }


  /**
   * Find the dependency path between two words in a sentence.
   *
   * @param start The start word, 0-indexed.
   * @param end The end word, 0-indexed.
   * @param selector The selector for the strings between the path, if any. If left empty, these will be omitted from the list.
   *
   * @return A list encoding the dependency path between the vertices, suitable for inclusion as features.
   */
  @SuppressWarnings({"unchecked", "Duplicates"})
  public List<String> dependencyPathBetween(int start, int end, Optional<Function<Sentence, List<String>>> selector) {
    // Get paths from a node to the root of the sentence
    LinkedList<Integer> rootToStart = new LinkedList<>();
    LinkedList<Integer> rootToEnd = new LinkedList<>();
    int startAncestor = start;
    List<Optional<Integer>> governors = sentence.governors();
    Set<Integer> seenVertices = new HashSet<>();
    while (startAncestor >= 0 && governors.get(startAncestor).isPresent()) {
      if (seenVertices.contains(startAncestor)) {
        // Found loopiness -- revert to BFS
        return loopyDependencyPathBetween(start, end, selector);
      }
      seenVertices.add(startAncestor);
      rootToStart.addFirst(startAncestor);
      startAncestor = governors.get(startAncestor).get();
    }
    if (startAncestor == -1) {
      rootToStart.addFirst(-1);
    }
    int endAncestor = end;
    seenVertices.clear();
    while (endAncestor >= 0 && governors.get(endAncestor).isPresent()) {
      if (seenVertices.contains(endAncestor)) {
        // Found loopiness -- revert to BFS
        return loopyDependencyPathBetween(start, end, selector);
      }
      seenVertices.add(endAncestor);
      rootToEnd.addFirst(endAncestor);
      endAncestor = governors.get(endAncestor).get();
    }
    if (endAncestor == -1) {
      rootToEnd.addFirst(-1);
    }

    // Get least common node
    int leastCommonNodeIndex = (rootToStart.size() == 0 || rootToEnd.size() == 0 || !rootToStart.get(0).equals(rootToEnd.get(0))) ? -1 : 0;
    for (int i = 1; i < Math.min(rootToStart.size(), rootToEnd.size()); ++i) {
      if (rootToStart.get(i).equals(rootToEnd.get(i))) {
        leastCommonNodeIndex = i;
      }
    }

    // Construct the path
    if (leastCommonNodeIndex < 0) {
      return Collections.emptyList();
    }
    List<String> path = new ArrayList<>();
    Optional<List<String>> words = selector.map(x -> x.apply(sentence));
    for (int i = rootToStart.size() - 1; i > leastCommonNodeIndex; --i) {
      final int index = i;
      words.ifPresent(x -> path.add(x.get(rootToStart.get(index))));
      path.add("<-" + sentence.incomingDependencyLabel(rootToStart.get(i)).orElse("dep") + "-");
    }
    if (words.isPresent()) {
      path.add(words.get().get(rootToStart.get(leastCommonNodeIndex)));
    }
    for (int i = leastCommonNodeIndex + 1; i < rootToEnd.size(); ++i) {
      final int index = i;
      path.add("-" + sentence.incomingDependencyLabel(rootToEnd.get(i)).orElse("dep") + "->");
      words.ifPresent(x -> path.add(x.get(rootToEnd.get(index))));
    }
    return path;
  }

  public List<String> dependencyPathBetween(int start, int end) {
    return dependencyPathBetween(start, end, Optional.of(Sentence::words));
  }


  /**
   * A funky little helper method to interpret each token of the sentence as an HTML string, and translate it back to text.
   * Note that this is <b>in place</b>.
   */
  public void unescapeHTML() {
    // Change in the protobuf
    for (int i = 0; i < sentence.length(); ++i) {
      CoreNLPProtos.Token.Builder token = sentence.rawToken(i);
      token.setWord(StringUtils.unescapeHtml3(token.getWord()));
      token.setLemma(StringUtils.unescapeHtml3(token.getLemma()));
    }
    // Change in the annotation
    CoreMap cm = sentence.document.asAnnotation().get(CoreAnnotations.SentencesAnnotation.class).get(sentence.sentenceIndex());
    for (CoreLabel token : cm.get(CoreAnnotations.TokensAnnotation.class)) {
      token.setWord(StringUtils.unescapeHtml3(token.word()));
      token.setLemma(StringUtils.unescapeHtml3(token.lemma()));
    }
  }
}
