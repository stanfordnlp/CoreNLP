package edu.stanford.nlp.ie.util;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.ToIntFunction;

import edu.stanford.nlp.ie.machinereading.structure.Span;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.util.PriorityQueue;
import edu.stanford.nlp.util.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.err;


/**
 * A (subject, relation, object) triple; e.g., as used in the KBP challenges or in OpenIE systems.
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")
public class RelationTriple implements Comparable<RelationTriple>, Iterable<CoreLabel>, Serializable {

  private static final long serialVersionUID = 43758623469716523L;

  /** The subject (first argument) of this triple */
  public final List<CoreLabel> subject;

  /** The subject (first argument) of this triple, in its canonical mention (i.e., coref resolved) */
  public final List<CoreLabel> canonicalSubject;

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

  /** The object (third argument) of this triple, in its canonical mention (i.e., coref resolved). */
  public final List<CoreLabel> canonicalObject;

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
    this.canonicalSubject = subject;
    this.relation = relation;
    this.object = object;
    this.canonicalObject = object;
    this.confidence = confidence;
  }

  /**
   * @see edu.stanford.nlp.ie.util.RelationTriple#RelationTriple(java.util.List, java.util.List, java.util.List, double)
   */
  public RelationTriple(List<CoreLabel> subject, List<CoreLabel> relation, List<CoreLabel> object) {
    this(subject, relation, object, 1.0);
  }

  /**
   * Create a new triple with known values for the subject, relation, and object.
   * For example, "(cats, play with, yarn)"
   * @param subject The subject of this triple; e.g., "cats".
   * @param relation The relation of this triple; e.g., "play with".
   * @param object The object of this triple; e.g., "yarn".
   */
  public RelationTriple(List<CoreLabel> subject,
                        List<CoreLabel> canonicalSubject,
                        List<CoreLabel> relation,
                        List<CoreLabel> object,
                        List<CoreLabel> canonicalObject,
                        double confidence) {
    this.subject = subject;
    this.canonicalSubject = canonicalSubject;
    this.relation = relation;
    this.object = object;
    this.canonicalObject = canonicalObject;
    this.confidence = confidence;
  }

  /**
   * @see edu.stanford.nlp.ie.util.RelationTriple#RelationTriple(java.util.List, java.util.List, java.util.List, double)
   */
  public RelationTriple(List<CoreLabel> subject,
                        List<CoreLabel> canonicalSubject,
                        List<CoreLabel> relation,
                        List<CoreLabel> canonicalObject,
                        List<CoreLabel> object) {
    this(subject, canonicalSubject, relation, object, canonicalObject, 1.0);
  }

  /**
   * Returns all the tokens in the extraction, in the order subject then relation then object.
   */
  public List<CoreLabel> allTokens() {
    List<CoreLabel> allTokens = new ArrayList<>();
    allTokens.addAll(canonicalSubject);
    allTokens.addAll(relation);
    allTokens.addAll(canonicalObject);
    return allTokens;
  }

  /** The subject of this relation triple, as a String */
  public String subjectGloss() {
    return StringUtils.join(canonicalSubject.stream().map(CoreLabel::word), " ");
  }

  /** The head of the subject of this relation triple. */
  public CoreLabel subjectHead() {
    return subject.get(subject.size() - 1);
  }

  /** The entity link of the subject */
  public String subjectLink() {
    return subjectLemmaGloss();
  }

  /**
   * The subject of this relation triple, as a String of the subject's lemmas.
   * This method will additionally strip out punctuation as well.
   */
   public String subjectLemmaGloss() {
    return StringUtils.join(canonicalSubject.stream().filter(x -> !x.tag().matches("[.?,:;'\"!]")).map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  /** The object of this relation triple, as a String */
  public String objectGloss() {
    return StringUtils.join(canonicalObject.stream().map(CoreLabel::word), " ");
  }

  /** The head of the object of this relation triple. */
  public CoreLabel objectHead() {
    return object.get(object.size() - 1);
  }

  /** The entity link of the subject */
  public String objectLink() {
    return objectLemmaGloss();
  }

  /**
   * The object of this relation triple, as a String of the object's lemmas.
   * This method will additionally strip out punctuation as well.
   */
  public String objectLemmaGloss() {
    return StringUtils.join(canonicalObject.stream().filter(x -> !x.tag().matches("[.?,:;'\"!]")).map(x -> x.lemma() == null ? x.word() : x.lemma()), " ");
  }

  /**
   * The relation of this relation triple, as a String
   */
  public String relationGloss() {
    String relationGloss = (
        (prefixBe ? "is " : "")
        + StringUtils.join(relation.stream().map(CoreLabel::word), " ")
        + (suffixBe ? " is" : "")
        + (suffixOf ? " of" : "")
        + (istmod ? " at_time" : "")
    ).trim();
    // Some cosmetic tweaks
    if ("'s".equals(relationGloss)) {
      return "has";
    } else {
      return relationGloss;
    }
  }

  /**
   * The relation of this relation triple, as a String of the relation's lemmas.
   * This method will additionally strip out punctuation as well, and lower-cases the relation.
   */
  public String relationLemmaGloss() {
    // Construct a human readable relation string
    String relationGloss = (
        (prefixBe ? "be " : "")
        + StringUtils.join(relation.stream()
            .filter(x -> x.tag() == null || (!x.tag().matches("[.?,:;'\"!]") && (x.lemma() == null || !x.lemma().matches("[.,;'\"?!]"))))
            .map(x -> x.lemma() == null ? x.word() : x.lemma()),
          " ")
          .toLowerCase()
        + (suffixBe ? " be" : "")
        + (suffixOf ? " of" : "")
        + (istmod ? " at_time" : "")
    ).trim();
    // Some cosmetic tweaks
    if ("'s".equals(relationGloss)) {
      return "have";
    } else {
      return relationGloss;
    }
  }

  /** The head of the relation of this relation triple. This is usually the main verb. */
  public CoreLabel relationHead() {
    return relation.stream()
        .filter(x -> x.tag().startsWith("V"))
        .reduce((x, y) -> y)
        .orElse(relation.get(relation.size() - 1));
  }

  /** A textual representation of the confidence. */
  public String confidenceGloss() {
    return new DecimalFormat("0.000").format(confidence);
  }

  private static Pair<Integer, Integer> getSpan(List<CoreLabel> tokens, ToIntFunction<CoreLabel> toMin, ToIntFunction<CoreLabel> toMax) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (CoreLabel token : tokens) {
      min = Math.min(min, toMin.applyAsInt(token));
      max = Math.max(max, toMax.applyAsInt(token) + 1);
    }
    return Pair.makePair(min, max);
  }

  /**
   * Gets the span of the NON-CANONICAL subject.
   */
  public Pair<Integer, Integer> subjectTokenSpan() {
    return getSpan(subject, x -> x.index() - 1, x -> x.index() - 1);
  }

  /**
   *   Get a representative span for the relation expressed by this triple.
   *
   *   This is a bit more complicated than the subject and object spans, as the relation
   *   span is occasionally discontinuous.
   *   If this is the case, this method returns the largest contiguous chunk.
   *   If the relation span is empty, return the object span.
   */
  public Pair<Integer, Integer> relationTokenSpan() {
    if (relation.isEmpty()) {
      return objectTokenSpan();
    } else if (relation.size() == 1) {
      return Pair.makePair(relation.get(0).index() - 1, relation.get(0).index());
    } else {
      // Variables to keep track of the longest chunk
      int longestChunk = 0;
      int longestChunkStart = 0;
      int thisChunk = 1;
      int thisChunkStart = 0;
      // Find the longest chunk
      for (int i = 1; i < relation.size(); ++i) {
        CoreLabel token = relation.get(i);
        CoreLabel lastToken = relation.get(i - 1);
        if (lastToken.index() + 1 == token.index()) {
          thisChunk += 1;
        } else if (lastToken.index() + 2 == token.index()) {
          thisChunk += 2;  // a skip of one character is _usually_ punctuation
        } else {
          if (thisChunk > longestChunk) {
            longestChunk = thisChunk;
            longestChunkStart = thisChunkStart;
          }
          thisChunkStart = i;
          thisChunk = 1;
        }
      }
      // (subcase: the last chunk is the longest)
      if (thisChunk > longestChunk) {
        longestChunk = thisChunk;
        longestChunkStart = thisChunkStart;
      }
      // Return the longest chunk
      return Pair.makePair(
          relation.get(longestChunkStart).index() - 1,
          relation.get(longestChunkStart).index() - 1 + longestChunk
      );
    }
  }

  /**
   * Gets the span of the NON-CANONICAL object.
   */
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

  /** Print a human-readable description of this relation triple, as a tab-separated line. */
  @Override
  public String toString() {
    return String.valueOf(this.confidence) + '\t' + subjectGloss() + '\t' + relationGloss() + '\t' + objectGloss();
  }


  /** Print in the format expected by Gabriel Stanovsky and Ido Dagan, Creating a Large Benchmark for Open
   *  Information Extraction, EMNLP 2016. https://gabrielstanovsky.github.io/assets/papers/emnlp16a/paper.pdf ,
   *  with equivalence classes.
   */
  public String toQaSrlString(CoreMap sentence) {
    String equivalenceClass = subjectHead().index() + "." + relationHead().index() + '.' + objectHead().index();
    return equivalenceClass + '\t' +
        subjectGloss().replace('\t', ' ') + '\t' +
        relationGloss().replace('\t', ' ') + '\t' +
        objectGloss().replace('\t', ' ') + '\t' +
        confidence + '\t' +
        StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class).stream().map(x -> x.word().replace('\t', ' ').replace(" ", "")), " ");
  }

  /** Print a description of this triple, formatted like the ReVerb outputs. */
  @SuppressWarnings("Duplicates")
  public String toReverbString(String docid, CoreMap sentence) {
    int sentIndex = -1;
    int subjIndex = -1;
    int relationIndex = -1;
    int objIndex = -1;
    int subjIndexEnd = -1;
    int relationIndexEnd = -1;
    int objIndexEnd = -1;
    if (!relation.isEmpty()) {
      sentIndex = relation.get(0).sentIndex();
      relationIndex = relation.get(0).index() - 1;
      relationIndexEnd = relation.get(relation.size() - 1).index();
    }
    if ( ! subject.isEmpty()) {
      if (sentIndex < 0) { sentIndex = subject.get(0).sentIndex(); }
      subjIndex = subject.get(0).index() - 1;
      subjIndexEnd = subject.get(subject.size() - 1).index();
    }
    if ( ! object.isEmpty()) {
      if (sentIndex < 0) { sentIndex = subject.get(0).sentIndex(); }
      objIndex = object.get(0).index() - 1;
      objIndexEnd = object.get(object.size() - 1).index();
    }
    return (docid == null ? "no_doc_id" : docid) + '\t' +
        sentIndex + '\t' +
        subjectGloss().replace('\t', ' ') + '\t' +
        relationGloss().replace('\t', ' ') + '\t' +
        objectGloss().replace('\t', ' ') + '\t' +
        subjIndex + '\t' +
        subjIndexEnd+ '\t' +
        relationIndex + '\t' +
        relationIndexEnd + '\t' +
        objIndex + '\t' +
        objIndexEnd + '\t' +
        confidenceGloss() + '\t' +
        StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class).stream().map(x -> x.word().replace('\t', ' ').replace(" ", "")), " ") + '\t' +
        StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class).stream().map(CoreLabel::tag), " ") + '\t' +
        subjectLemmaGloss().replace('\t', ' ') + '\t' +
        relationLemmaGloss().replace('\t', ' ') + '\t' +
        objectLemmaGloss().replace('\t', ' ');
  }

  @Override
  public int compareTo(RelationTriple o) {
    return Double.compare(this.confidence, o.confidence);
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

    /**
     * Create a new triple with known values for the subject, relation, and object,
     * along with their canonical spans (i.e., resolving coreference)
     * For example, "(cats, play with, yarn)"
     */
    public WithTree(List<CoreLabel> subject,
                          List<CoreLabel> canonicalSubject,
                          List<CoreLabel> relation,
                          List<CoreLabel> object,
                          List<CoreLabel> canonicalObject,
                          double confidence,
                    SemanticGraph tree) {
      super(subject, canonicalSubject, relation, object, canonicalObject, confidence);
      this.sourceTree = tree;
    }

    /** The head of the subject of this relation triple. */
    @Override
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
    @Override
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


    /** The head of the relation of this relation triple. */
    @Override
    public CoreLabel relationHead() {
      if (relation.size() == 1) { return relation.get(0); }
      CoreLabel guess = null;
      CoreLabel newGuess = super.relationHead();
      int iters = 0;  // make sure we don't infinite loop...
      while (guess != newGuess && iters < 100) {
        guess = newGuess;
        iters += 1;
        for (SemanticGraphEdge edge : sourceTree.incomingEdgeIterable(new IndexedWord(guess))) {
          // find a node in the relation list which is a governor of the candidate root
          Optional<CoreLabel> governor = relation.stream().filter(x -> x.index() == edge.getGovernor().index()).findFirst();
          // if we found one, this is the new root. The for loop continues
          if (governor.isPresent()) {
            newGuess = governor.get();
          }
        }
      }
      // Return
      if (iters >= 100) {
        err("Likely cycle in relation tree");
      }
      return guess;
    }

    /** {@inheritDoc} */
    @Override
    public Optional<SemanticGraph> asDependencyTree() {
      return Optional.of(sourceTree);
    }
  }


  /**
   * A {@link edu.stanford.nlp.ie.util.RelationTriple}, but with both the tree and the entity
   * links saved as well.
   */
  public static class WithLink extends WithTree {
    /** The canonical entity link of the subject */
    public final Optional<String> subjectLink;
    /** The canonical entity link of the object */
    public final Optional<String> objectLink;

    /** Create a new relation triple */
    public WithLink(List<CoreLabel> subject, List<CoreLabel> canonicalSubject, List<CoreLabel> relation, List<CoreLabel> object, List<CoreLabel> canonicalObject, double confidence,
                    SemanticGraph tree,
                    String subjectLink,
                    String objectLink
                    ) {
      super(subject, canonicalSubject, relation, object, canonicalObject, confidence, tree);
      this.subjectLink = Optional.ofNullable(subjectLink);
      this.objectLink = Optional.ofNullable(objectLink);
    }

    /** {@inheritDoc} */
    @Override
    public String subjectLink() {
      return subjectLink.orElseGet(super::subjectLink);
    }

    /** {@inheritDoc} */
    @Override
    public String objectLink() {
      return objectLink.orElseGet(super::objectLink);
    }
  }

}
