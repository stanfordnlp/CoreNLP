package edu.stanford.nlp.ie.util;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.PriorityQueue;

import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;

/**
 * A (subject, relation, object) triple; e.g., as used in the KBP challenges or in OpenIE systems.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")
public class RelationTriple implements Comparable<RelationTriple>, Iterable<CoreLabel> {
  /** The subject (first argument) of this triple */
  public final List<CoreLabel> subject;
  /** The relation (second argument) of this triple */
  public final List<CoreLabel> relation;
  /** The object (third argument) of this triple */
  public final List<CoreLabel> object;
  /** An optional score (confidence) for this triple */
  public final double confidence;

  /**
   * Create a new triple with known values for the subject, relation, and object.
   * For example, "(cats, play with, yarn)"
   * @param subject The subject of this triple; e.g., "cats".
   * @param relation The relation of this triple; e.g., "play with".
   * @param object The object of this triple; e.g., "yarn".
   */
  public RelationTriple(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object,
                        double confidence) {
    this.subject = subject;
    this.relation = relation;
    this.object = object;
    this.confidence = confidence;
  }

  /**
   * @see edu.stanford.nlp.ie.util.RelationTriple#RelationTriple(java.util.List, java.util.List, java.util.List, double)
   */
  public RelationTriple(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object) {
    this(subject, relation, object, 1.0);
  }

  /**
   * Returns all the tokens in the extraction, in the order subject then relation then object.
   */
  public List<CoreLabel> allTokens() {
    List<CoreLabel> allTokens = new ArrayList<>();
    allTokens.addAll(subject);
    allTokens.addAll(relation);
    allTokens.addAll(object);
    return allTokens;
  }

  /** The subject of this relation triple, as a String */
  public String subjectGloss() {
    return StringUtils.join(subject.stream().map(CoreLabel::word), " ");
  }

  /** The head of the subject of this relation triple. */
  public CoreLabel subjectHead() {
    return subject.get(subject.size() - 1);
  }

  /**
   * The subject of this relation triple, as a String of the subject's lemmas.
   * This method will additionally strip out punctuation as well.
   */
   public String subjectLemmaGloss() {
    return StringUtils.join(subject.stream().filter(x -> !x.tag().matches("[\\.\\?,:;'\"!]")).map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  /** The object of this relation triple, as a String */
  public String objectGloss() {
    return StringUtils.join(object.stream().map(CoreLabel::word), " ");
  }

  /** The head of the object of this relation triple. */
  public CoreLabel objectHead() {
    return object.get(object.size() - 1);
  }

  /**
   * The object of this relation triple, as a String of the object's lemmas.
   * This method will additionally strip out punctuation as well.
   */
  public String objectLemmaGloss() {
    return StringUtils.join(object.stream().filter(x -> !x.tag().matches("[\\.\\?,:;'\"!]")).map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  /**
   * The relation of this relation triple, as a String
   */
  public String relationGloss() {
    return StringUtils.join(relation.stream().map(CoreLabel::word), " ");
  }

  /**
   * The relation of this relation triple, as a String of the relation's lemmas.
   * This method will additionally strip out punctuation as well, and lower-cases the relation.
   */
  public String relationLemmaGloss() {
    return StringUtils.join(relation.stream()
        .filter(x -> !x.tag().matches("[\\.\\?,:;'\"!]") && (x.lemma() == null || !x.lemma().matches("[\\.,;'\"\\?!]"))).map(x -> x.lemma() == null ? x.word() : x.lemma()), " ").toLowerCase();
  }

  /** A textual representation of the confidence. */
  public String confidenceGloss() {
    return new DecimalFormat("0.000").format(confidence);
  }

  protected Pair<Integer, Integer> getSpan(List<CoreLabel> tokens, Function<CoreLabel, Integer> toMin, Function<CoreLabel, Integer> toMax) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (CoreLabel token : tokens) {
      min = Math.min(min, toMin.apply(token));
      max = Math.max(max, toMax.apply(token) + 1);
    }
    return Pair.makePair(min, max);
  }

  public Pair<Integer, Integer> subjectTokenSpan() {
    return getSpan(subject, x -> x.index() - 1, x -> x.index() - 1);
  }

  public Pair<Integer, Integer> objectTokenSpan() {
    return getSpan(object, x -> x.index() - 1, x -> x.index() - 1);
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
    return "" + this.confidence + "\t" + subjectGloss() + "\t" + relationGloss() + "\t" + objectGloss();
  }

  /** Print a description of this triple, formatted like the ReVerb outputs. */
  public String toReverbString(String docid, CoreMap sentence) {
    return docid + "\t" +
        relation.get(0).sentIndex() + "\t" +
        subjectGloss().replace('\t', ' ') + "\t" +
        relationGloss().replace('\t', ' ') + "\t" +
        objectGloss().replace('\t', ' ') + "\t" +
        (subject.get(0).index() - 1) + "\t" +
        subject.get(subject.size() - 1).index() + "\t" +
        (relation.get(0).index() - 1) + "\t" +
        relation.get(relation.size() - 1).index() + "\t" +
        (object.get(0).index() - 1) + "\t" +
        object.get(object.size() - 1).index() + "\t" +
        confidenceGloss() + "\t" +
        StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class).stream().map(x -> x.word().replace('\t', ' ').replace(" ", "")), " ") + "\t" +
        StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class).stream().map(CoreLabel::tag), " ") + "\t" +
        subjectLemmaGloss().replace('\t', ' ') + "\t" +
        relationLemmaGloss().replace('\t', ' ') + "\t" +
        objectLemmaGloss().replace('\t', ' ');
  }

  @Override
  public int compareTo(RelationTriple o) {
    if (this.confidence < o.confidence) {
      return -1;
    } else if (this.confidence > o.confidence) {
      return 1;
    } else {
      return 0;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public Iterator<CoreLabel> iterator() {
    return CollectionUtils.concatIterators(subject.iterator(), relation.iterator(), object.iterator());
  }


  /**
   * A {@link edu.stanford.nlp.ie.util.RelationTriple}, but with the tree saved as well.
   */
  public static class WithTree extends RelationTriple {
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
    public WithTree(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object, SemanticGraph tree,
                    double confidence) {
      super(subject, relation, object, confidence);
      this.sourceTree = new SemanticGraph(tree);
    }

    /** The head of the subject of this relation triple. */
    public CoreLabel subjectHead() {
      if (subject.size() == 1) { return subject.get(0); }
      Span subjectSpan = Span.fromValues(subject.get(0).index(), subject.get(subject.size() - 1).index());
      for (int i = subject.size() - 1; i >= 0; --i) {
        for (SemanticGraphEdge edge : sourceTree.incomingEdgeIterable(new IndexedWord(subject.get(i)))) {
          if (edge.getGovernor().index() < subjectSpan.start() || edge.getGovernor().index() >= subjectSpan.end()) {
            return subject.get(i);
          }
        }
      }
      return subject.get(subject.size() - 1);
    }

    /** The head of the object of this relation triple. */
    public CoreLabel objectHead() {
      if (object.size() == 1) { return object.get(0); }
      Span objectSpan = Span.fromValues(object.get(0).index(), object.get(object.size() - 1).index());
      for (int i = object.size() - 1; i >= 0; --i) {
        for (SemanticGraphEdge edge : sourceTree.incomingEdgeIterable(new IndexedWord(object.get(i)))) {
          if (edge.getGovernor().index() < objectSpan.start() || edge.getGovernor().index() >= objectSpan.end()) {
            return object.get(i);
          }
        }
      }
      return object.get(object.size() - 1);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SemanticGraph> asDependencyTree() {
      return Optional.of(sourceTree);
    }
  }
}
