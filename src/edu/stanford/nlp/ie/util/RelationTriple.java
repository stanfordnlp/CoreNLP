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
  /**
   * The relation (second argument) of this triple.
   * Note that this is only the part of the relation that can be grounded in the sentence itself.
   * Often, for a standalone readable relation string, you want to attach additional modifiers
   * otherwise stored in the dependnecy arc.
   * Therefore, for getting a String form of the relation, we recommend using
   * {@link RelationTriple#relationGloss} or {@link RelationTriple#relationLemmaGloss}.
   */
  public final List<CoreLabel> relation;
  /** The object (third argument) of this triple */
  public final List<CoreLabel> object;
  /** A marker for the relation expressing a tmod not grounded in a word in the sentence. */
  private boolean istmod = false;
  /** A marker for the relation expressing a prefix "be" not grounded in a word in the sentence. */
  private boolean prefixBe = false;
  /** A marker for the relation expressing a suffix "be" not grounded in a word in the sentence. */
  private boolean suffixBe = false;
  /** A marker for the relation expressing a suffix "of" not grounded in a word in the sentence. */
  private boolean suffixOf = false;
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
    return (
        (prefixBe ? "is " : "")
        + StringUtils.join(relation.stream().map(CoreLabel::word), " ")
        + (suffixBe ? " is" : "")
        + (suffixOf ? " of" : "")
        + (istmod ? " at_time" : "")
    ).trim();
  }

  /**
   * The relation of this relation triple, as a String of the relation's lemmas.
   * This method will additionally strip out punctuation as well, and lower-cases the relation.
   */
  public String relationLemmaGloss() {
    return (
        (prefixBe ? "be " : "")
        + StringUtils.join(relation.stream()
          .filter(x -> !x.tag().matches("[\\.\\?,:;'\"!]") && (x.lemma() == null || !x.lemma().matches("[\\.,;'\"\\?!]"))).map(x -> x.lemma() == null ? x.word() : x.lemma()), " ").toLowerCase()
        + (suffixBe ? " be" : "")
        + (suffixOf ? " of" : "")
        + (istmod ? " at_time" : "")
    ).trim();
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

  /**
   * <p>
   *   Get a representative span for the relation expressed by this triple.
   * </p>
   *
   * <p>
   *   This is a bit more complicated than the subject and object spans, as the relation
   *   span is occasionally discontinuous.
   *   If this is the case, this method returns the largest contiguous chunk.
   *   If the relation span is empty, return the object span.
   * </p>
   */
  public Pair<Integer, Integer> relationTokenSpan() {
    if (relation.size() == 0) {
      return objectTokenSpan();
    } else if (relation.size() == 1) {
      return Pair.makePair(relation.get(0).index() - 1, relation.get(0).index());
    } else {
      // Variables to keep track of the longest chunk
      int longestChunk = 0;
      int longestChunkStart = 0;
      int lastIndex = relation.get(0).index() - 1;
      int thisChunk = 1;
      int thisChunkStart = 0;
      // Find the longest chunk
      for (int i = 1; i < relation.size(); ++i) {
        CoreLabel token = relation.get(i);
        if (token.index() - 1 == lastIndex + 1) {
          thisChunk += 1;
        } else {
          if (thisChunk > longestChunk) {
            longestChunk = thisChunk;
            longestChunkStart = thisChunkStart;
          }
          thisChunkStart = i;
          thisChunk = 1;
        }
      }
      // Return the longest chunk
      return Pair.makePair(
          relation.get(longestChunkStart).index() - 1,
          relation.get(longestChunkStart + longestChunk).index()
      );
    }
  }

  public Pair<Integer, Integer> objectTokenSpan() {
    return getSpan(object, x -> x.index() - 1, x -> x.index() - 1);
  }

  /**
   * If true, this relation expresses a "to be" relation.
   *
   * For example, "President Obama" expresses the relation
   * (Obama; be; President).
   */
  public boolean isPrefixBe() {
    return this.prefixBe;
  }

  /**
   * Set the value of this relation triple expressing a "to be" relation.
   *
   * @param newValue The new value of this relation being a "to be" relation.
   * @return The old value of whether this relation expressed a "to be" relation.
   */
  public boolean isPrefixBe(boolean newValue) {
    boolean oldValue = this.prefixBe;
    this.prefixBe = newValue;
    return oldValue;
  }

  /**
   * If true, this relation expresses a "to be" relation (with the be at the end of the sentence).
   *
   * For example, "Tim's father Tom" expresses the relation
   * (Tim; 's father is; Tom).
   */
  public boolean isSuffixBe() {
    return this.suffixBe;
  }

  /**
   * Set the value of this relation triple expressing a "to be" relation (suffix).
   *
   * @param newValue The new value of this relation being a "to be" relation.
   * @return The old value of whether this relation expressed a "to be" relation.
   */
  public boolean isSuffixBe(boolean newValue) {
    boolean oldValue = this.suffixBe;
    this.suffixBe = newValue;
    return oldValue;
  }

  /**
   * If true, this relation has an ungrounded "of" at the end of the relation.
   *
   * For example, "United States president Barack Obama" expresses the relation
   * (Obama; is president of; United States).
   */
  public boolean isSuffixOf() {
    return this.suffixOf;
  }

  /**
   * Set the value of this triple missing an ungrounded "of" in the relation string.
   *
   * @param newValue The new value of this relation missing an "of".
   * @return The old value of whether this relation missing an "of".
   */
  public boolean isSuffixOf(boolean newValue) {
    boolean oldValue = this.suffixOf;
    this.suffixOf = newValue;
    return oldValue;
  }

  /**
   * If true, this relation expresses a tmod (temporal modifier) relation that is not grounded in
   * the sentence.
   *
   * For example, "I went to the store Friday" would otherwise yield a strange triple
   * (I; go to store; Friday).
   */
  public boolean istmod() {
    return this.istmod;
  }

  /**
   * Set the value of this relation triple expressing a tmod (temporal modifier) relation.
   *
   * @param newValue The new value of this relation being a tmod relation.
   * @return The old value of whether this relation expressed a tmod relation.
   */
  public boolean istmod(boolean newValue) {
    boolean oldValue = this.istmod;
    this.istmod = newValue;
    return oldValue;
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
    return toString().hashCode();  // Faster than checking CoreLabels
//    int result = subject.hashCode();
//    result = 31 * result + relation.hashCode();
//    result = 31 * result + object.hashCode();
//    return result;
  }

  /** Print a human-readable description of this relation triple, as a tab-separated line */
  @Override
  public String toString() {
    return "" + this.confidence + "\t" + subjectGloss() + "\t" + relationGloss() + "\t" + objectGloss();
  }

  /** Print a description of this triple, formatted like the ReVerb outputs. */
  public String toReverbString(String docid, CoreMap sentence) {
    return (docid == null ? "no_doc_id" : docid) + "\t" +
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
