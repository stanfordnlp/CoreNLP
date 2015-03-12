package edu.stanford.nlp.ie.util;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.semgraph.semgrex.SemgrexMatcher;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.util.FixedPrioritiesPriorityQueue;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.util.StringUtils;

import java.util.*;

/**
 * A (subject, relation, object) triple; e.g., as used in the KBP challenges or in OpenIE systems.
 *
 * @author Gabor Angeli
 */
public class RelationTriple {
  /** The subject (first argument) of this triple */
  public final List<CoreLabel> subject;
  /** The relation (second argument) of this triple */
  public final List<CoreLabel> relation;
  /** The object (third argument) of this triple */
  public final List<CoreLabel> object;

  /**
   * Create a new triple with known values for the subject, relation, and object.
   * For example, "(cats, play with, yarn)"
   * @param subject The subject of this triple; e.g., "cats".
   * @param relation The relation of this triple; e.g., "play with".
   * @param object The object of this triple; e.g., "yarn".
   */
  public RelationTriple(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object) {
    this.subject = subject;
    this.relation = relation;
    this.object = object;
  }

  /** The subject of this relation triple, as a String */
  public String subjectGloss() {
    return StringUtils.join(subject.stream().map(CoreLabel::word), " ");
  }

  /** The object of this relation triple, as a String */
  public String objectGloss() {
    return StringUtils.join(object.stream().map(CoreLabel::word), " ");
  }

  /** The relation of this relation triple, as a String */
  public String relationGloss() {
    return StringUtils.join(relation.stream().map(CoreLabel::word), " ");
  }

  /** An optional method, returning the dependency tree this triple was extracted from */
  public Optional<SemanticGraph> asDependencyTree() {
    return Optional.empty();
  }

  /** Return the given relation triple as a flat sentence */
  public List<CoreLabel> asSentence() {
    PriorityQueue<CoreLabel> orderedSentence = new FixedPrioritiesPriorityQueue<>();
    double defaultIndex = 0.0;
    for (CoreLabel token : subject) {
      orderedSentence.add(token, token.index() >= 0 ? (double) -token.index() : -defaultIndex);
      defaultIndex += 1.0;
    }
    for (CoreLabel token : relation) {
      orderedSentence.add(token, token.index() >= 0 ? (double) -token.index() : -defaultIndex);
      defaultIndex += 1.0;
    }
    for (CoreLabel token : object) {
      orderedSentence.add(token, token.index() >= 0 ? (double) -token.index() : -defaultIndex);
      defaultIndex += 1.0;
    }
    return orderedSentence.toSortedList();
  }


  /** {@inheritDoc} */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RelationTriple)) return false;
    RelationTriple that = (RelationTriple) o;
    return object.equals(that.object) && relation.equals(that.relation) && subject.equals(that.subject);
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    int result = subject.hashCode();
    result = 31 * result + relation.hashCode();
    result = 31 * result + object.hashCode();
    return result;
  }

  /** Print a human-readable description of this relation triple, as a tab-separated line */
  @Override
  public String toString() {
    return subjectGloss() + "\t" + relationGloss() + "\t" + objectGloss();
  }

  /**
   * A {@link edu.stanford.nlp.ie.util.RelationTriple}, but with the tree saved as well.
   */
  protected static class WithTree extends RelationTriple {
    public final SemanticGraph sourceTree;

    /**
     * Create a new triple with known values for the subject, relation, and object.
     * For example, "(cats, play with, yarn)"
     *
     * @param subject  The subject of this triple; e.g., "cats".
     * @param relation The relation of this triple; e.g., "play with".
     * @param object   The object of this triple; e.g., "yarn".
     * @param tree     The tree this extraction was created from; we create a deep copy of the tree.
     */
    public WithTree(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object, SemanticGraph tree) {
      super(subject, relation, object);
      this.sourceTree = new SemanticGraph(tree);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SemanticGraph> asDependencyTree() {
      return Optional.of(sourceTree);
    }
  }

  /** A list of patterns to match relation extractions against */
  private static final List<SemgrexPattern> PATTERNS = Collections.unmodifiableList(new ArrayList<SemgrexPattern>() {{
    // { blue cats play [quietly] with yarn }
    add(SemgrexPattern.compile("{$}=verb ?>/cop/ {}=be >/nsubj(pass)?/ {}=subject >/prep/ ({}=prep >/pobj/ {}=object)"));
    // (w / collapsed dependencies)
    add(SemgrexPattern.compile("{$}=verb ?>/cop/ {}=be >/nsubj(pass)?/ {}=subject >/prepc?_.*/=prepEdge {}=object"));
    // { cats have tails }
    add(SemgrexPattern.compile("{$}=verb >/nsubj(pass)?/ {}=subject >/[di]obj/ {}=object"));
  }});

  /** A set of valid arcs denoting an entity we are interested in */
  private static final Set<String> VALID_ENTITY_ARCS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("amod"); add("nn");
  }});

  /** A set of valid arcs denoting an entity we are interested in */
  private static final Set<String> VALID_ADVERB_ARCS = Collections.unmodifiableSet(new HashSet<String>(){{
    add("amod"); add("advmod"); add("conj"); add("cc"); add("conj_and"); add("conj_or");
  }});


  /**
   * @see RelationTriple#getValidEntityChunk(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord)
   * @see RelationTriple#getValidAdverbChunk(edu.stanford.nlp.semgraph.SemanticGraph, edu.stanford.nlp.ling.IndexedWord)
   */
  private static Optional<List<CoreLabel>> getValidChunk(SemanticGraph parse, IndexedWord root, Set<String> validArcs) {
    PriorityQueue<CoreLabel> chunk = new FixedPrioritiesPriorityQueue<>();
    Queue<IndexedWord> fringe = new LinkedList<>();
    fringe.add(root);

    while (!fringe.isEmpty()) {
      root = fringe.poll();
      chunk.add(root.backingLabel(), -root.index());
      for (SemanticGraphEdge edge : parse.incomingEdgeIterable(root)) {
        if (edge.getRelation().getLongName().startsWith("conj_")) {
          CoreLabel mockAnd = new CoreLabel(root);
          String andPart = edge.getRelation().getSpecific();
          mockAnd.setWord(andPart);
          mockAnd.setLemma(andPart);
          mockAnd.setValue(andPart);
          mockAnd.setNER("O");
          mockAnd.setTag("PP");
          mockAnd.setIndex(root.index() - 1);
          chunk.add(mockAnd, -mockAnd.index());
        }
      }
      for (SemanticGraphEdge edge : parse.getOutEdgesSorted(root)) {
        if (!validArcs.contains(edge.getRelation().getShortName())) {
          return Optional.empty();
        } else {
          fringe.add(edge.getDependent());
        }
      }
    }

    return Optional.of(chunk.toSortedList());
  }

  /**
   * Get the yield of a given subtree, if it is a valid entity.
   * Otherwise, return {@link java.util.Optional#empty()}}.
   * @param parse The parse tree we are extracting a subtree from.
   * @param root The root of the subtree.
   * @return If this subtree is a valid entity, we return its yield. Otherwise, we return empty.
   */
  private static Optional<List<CoreLabel>> getValidEntityChunk(SemanticGraph parse, IndexedWord root) {
    return getValidChunk(parse, root, VALID_ENTITY_ARCS);
  }

  /**
   * Get the yield of a given subtree, if it is a adverb chunk.
   * Otherwise, return {@link java.util.Optional#empty()}}.
   * @param parse The parse tree we are extracting a subtree from.
   * @param root The root of the subtree.
   * @return If this subtree is a valid adverb, we return its yield. Otherwise, we return empty.
   */
  private static Optional<List<CoreLabel>> getValidAdverbChunk(SemanticGraph parse, IndexedWord root) {
    return getValidChunk(parse, root, VALID_ADVERB_ARCS);
  }

  /**
   * <p>
   * Try to segment this sentence as a relation triple.
   * This sentence must already match one of a few strict patterns for a valid OpenIE extraction.
   * If it does not, then no relation triple is created.
   * That is, this is <b>not</b> a relation extractor; it is just a utility to segment what is already a
   * (subject, relation, object) triple into these three parts.
   * </p>
   *
   * @param parse The sentence to process, as a dependency tree.
   * @return A relation triple, if this sentence matches one of the patterns of a valid relation triple.
   */
  public static Optional<RelationTriple> segment(SemanticGraph parse) {
    for (SemgrexPattern pattern : PATTERNS) {  // For every candidate pattern...
      SemgrexMatcher m = pattern.matcher(parse);
      if (m.matches()) {  // ... see if it matches the sentence
        // Verb
        PriorityQueue<CoreLabel> verbChunk = new FixedPrioritiesPriorityQueue<>();
        IndexedWord verb = m.getNode("verb");
        IndexedWord prep = m.getNode("prep");
        List<IndexedWord> adverbs = new ArrayList<IndexedWord>();
        for (SemanticGraphEdge edge : parse.outgoingEdgeIterable(verb)) {
          if ("advmod".equals(edge.getRelation().getShortName()) || "amod".equals(edge.getRelation().getShortName())) {
            adverbs.add(edge.getDependent());
          }
        }
        IndexedWord be = m.getNode("be");
        String prepEdge = m.getRelnString("prepEdge");
        verbChunk.add(verb.backingLabel(), -verb.index());
        int numKnownDependents = 2;  // subject and object, at minimum
        if (prep != null) { verbChunk.add(prep.backingLabel(), -prep.index()); numKnownDependents += 1; }
        if (be != null) { verbChunk.add(be.backingLabel(), -be.index()); numKnownDependents += 1; }
        // (adverbs have to be well-formed)
        if (!adverbs.isEmpty()) {
          Set<CoreLabel> adverbialModifiers = new HashSet<>();
          for (IndexedWord adv : adverbs) {
            Optional<List<CoreLabel>> adverbChunk = getValidAdverbChunk(parse, adv);
            if (adverbChunk.isPresent()) {
              for (CoreLabel token : adverbChunk.get()) {
                adverbialModifiers.add(token);
              }
            } else {
              return Optional.empty();  // Invalid adverbial phrase
            }
            numKnownDependents += 1;
          }
          for (CoreLabel adverbToken : adverbialModifiers) {
            verbChunk.add(adverbToken, -adverbToken.index());
          }
        }
        // (add preposition edge)
        if (prepEdge != null) {
          String prepPart = prepEdge.substring("prep_".length());
          CoreLabel mockPrep = new CoreLabel(verb.backingLabel());
          mockPrep.setWord(prepPart);
          mockPrep.setLemma(prepPart);
          mockPrep.setValue(prepPart);
          mockPrep.setNER("O");
          mockPrep.setTag("PP");
          mockPrep.setIndex(verb.index() + 1);
          verbChunk.add(mockPrep, -mockPrep.index());
        }
        // (check for additional edges)
        if (parse.outDegree(verb) > numKnownDependents) {
          return Optional.empty();  // Too many outgoing edges; we didn't consume them all.
        }
        List<CoreLabel> relation = verbChunk.toSortedList();

        // Subject+Object
        Optional<List<CoreLabel>> subject = getValidEntityChunk(parse, m.getNode("subject"));
        Optional<List<CoreLabel>> object = getValidEntityChunk(parse, m.getNode("object"));
        // Create relation
        if (subject.isPresent() && object.isPresent()) {  // ... and has a valid subject+object
          // Success! Found a valid extraction.
          return Optional.of(new WithTree(subject.get(), relation, object.get(), parse));
        }
      }
    }
    // Failed to match any pattern; return failure
    return Optional.empty();
  }
}
