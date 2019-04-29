package edu.stanford.nlp.naturalli; 

import edu.stanford.nlp.util.Trilean;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The catalog of the seven Natural Logic relations.
 * Set-theoretically, if we assume A and B are two sets (e.g., denotations),
 * and D is the universe of discourse,
 * then the relations between A and B are defined as follows:
 *
 * <ul>
 *   <li>Equivalence: A = B</li>
 *   <li>Forward entailment: A \\subset B</li>
 *   <li>Reverse entailment: A \\supset B</li>
 *   <li>Negation: A \\intersect B = \\empty \\land A \\union B = D </li>
 *   <li>Alternation: A \\intersect B = \\empty </li>
 *   <li>Cover: A \\union B = D </li>
 * </ul>
 *
 * @author Gabor Angeli
 */
@SuppressWarnings("UnusedDeclaration")
public enum NaturalLogicRelation {
  EQUIVALENT         (0, true,  false, true,  false),
  FORWARD_ENTAILMENT (1, true,  false, false, false),
  REVERSE_ENTAILMENT (2, false, false, true,  false),
  NEGATION           (3, false, true,  false, true),
  ALTERNATION        (4, false, true,  false, false),
  COVER              (5, false, false, false, true),
  INDEPENDENCE       (6, false, false, false, false),
  ;

  /**
   * A fixed index for this relation, so that it can be serialized more efficiently.
   * DO NOT CHANGE THIS INDEX or you will break existing serialization, and probably a bunch of other stuff too.
   * Otherwise, the index is arbitrary.
   */
  public final int fixedIndex;
  /**
   * Determines whether this relation maintains the truth of a fact in a true context.
   * So, if the premise is true, and this relation is applied, the conclusion remains true.
   */
  public final boolean maintainsTruth;
  /**
   * Determines whether this relation negates the truth of a fact in a true context.
   * So, if the premise is true, and this relation is applied, the conclusion becomes false.
   */
  public final boolean negatesTruth;
  /**
   * Determines whether this relation maintains the falsehood of a false fact.
   * So, if the premise is false, and this relation is applied, the conclusion remains false.
   */
  public final boolean maintainsFalsehood;
  /**
   * Determines whether this relation negates the truth of a fact in a false context.
   * So, if the premise is false, and this relation is applied, the conclusion becomes true.
   */
  public final boolean negatesFalsehood;

  NaturalLogicRelation(int fixedIndex, boolean maintainsTruth, boolean negatesTruth, boolean maintainsFalsehood, boolean negatesFalsehood) {
    this.fixedIndex = fixedIndex;
    this.maintainsTruth = maintainsTruth;
    this.negatesTruth = negatesTruth;
    this.maintainsFalsehood = maintainsFalsehood;
    this.negatesFalsehood = negatesFalsehood;
  }

  protected static NaturalLogicRelation byFixedIndex(int index) {
    switch (index) {
      case 0: return EQUIVALENT;
      case 1: return FORWARD_ENTAILMENT;
      case 2: return REVERSE_ENTAILMENT;
      case 3: return NEGATION;
      case 4: return ALTERNATION;
      case 5: return COVER;
      case 6: return INDEPENDENCE;
      default: throw new IllegalArgumentException("Unknown index for Natural Logic relation: " + index);
    }
  }

  /**
   * The MacCartney "join table" -- this determines the transitivity of entailment if we chain two relations together.
   * These should already be projected up through the sentence, so that the relations being joined are relations between
   * <i>sentences</i> rather than relations between <i>lexical items</i> (see {@link Polarity#projectLexicalRelation(NaturalLogicRelation)},
   * set by {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotator} using the {@link edu.stanford.nlp.naturalli.NaturalLogicAnnotations.PolarityAnnotation}).
   * @param other The relation to join this relation with.
   * @return The new joined relation.
   */
  public NaturalLogicRelation join(NaturalLogicRelation other) {
    switch (this) {
      case EQUIVALENT:
        return other;
      case FORWARD_ENTAILMENT:
        switch (other) {
          case EQUIVALENT:
          case FORWARD_ENTAILMENT:
            return FORWARD_ENTAILMENT;
          case NEGATION:
          case ALTERNATION:
            return COVER;
          case REVERSE_ENTAILMENT:
          case COVER:
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case REVERSE_ENTAILMENT:
        switch (other) {
          case EQUIVALENT:
          case REVERSE_ENTAILMENT:
            return REVERSE_ENTAILMENT;
          case NEGATION:
          case COVER:
            return COVER;
          case FORWARD_ENTAILMENT:
          case ALTERNATION:
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case NEGATION:
        switch (other) {
          case EQUIVALENT:
            return NEGATION;
          case FORWARD_ENTAILMENT:
            return COVER;
          case REVERSE_ENTAILMENT:
            return ALTERNATION;
          case NEGATION:
            return EQUIVALENT;
          case ALTERNATION:
            return REVERSE_ENTAILMENT;
          case COVER:
            return FORWARD_ENTAILMENT;
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case ALTERNATION:
        switch (other) {
          case EQUIVALENT:
          case REVERSE_ENTAILMENT:
            return ALTERNATION;
          case NEGATION:
          case COVER:
            return FORWARD_ENTAILMENT;
          case FORWARD_ENTAILMENT:
          case ALTERNATION:
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case COVER:
        switch (other) {
          case EQUIVALENT:
          case FORWARD_ENTAILMENT:
            return COVER;
          case NEGATION:
          case ALTERNATION:
            return REVERSE_ENTAILMENT;
          case REVERSE_ENTAILMENT:
          case COVER:
          case INDEPENDENCE:
            return INDEPENDENCE;
        }
      case INDEPENDENCE:
        return INDEPENDENCE;
    }
    throw new IllegalStateException("[should be impossible]: Incomplete join table for " + this + " joined with " + other);
  }

  /**
   * Implements the finite state automata of composing the truth value of a sentence with a natural logic relation being
   * applied.
   * @param initialTruthValue The truth value of the premise (the original sentence).
   * @return The truth value of the consequent -- that is, the sentence once it's been modified with this relation.
   *         A value of {@link Trilean#UNKNOWN} indicates that natural logic cannot either confirm or disprove the truth
   *         of the consequent.
   */
  public Trilean applyToTruthValue(boolean initialTruthValue) {
    if (initialTruthValue) {
      if (maintainsTruth) {
        return Trilean.TRUE;
      } else if (negatesTruth) {
        return Trilean.FALSE;
      } else {
        return Trilean.UNKNOWN;
      }
    } else {
      if (maintainsFalsehood) {
        return Trilean.FALSE;
      } else if (negatesFalsehood) {
        return Trilean.TRUE;
      } else {
        return Trilean.UNKNOWN;
      }
    }
  }

  private static final Map<String, NaturalLogicRelation> insertArcToNaturalLogicRelation = Collections.unmodifiableMap(new HashMap<String, NaturalLogicRelation>() {{
    put("acomp", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("advcl", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("acl", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("acl:relcl", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("advmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("agent", NaturalLogicRelation.INDEPENDENCE);  //
    put("amod", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("appos", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("aux", NaturalLogicRelation.INDEPENDENCE);  // he left -/-> he should leave
    put("aux:pass", NaturalLogicRelation.INDEPENDENCE);  // some cat adopts -/-> some cat got adopted
    put("comp", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("ccomp", NaturalLogicRelation.REVERSE_ENTAILMENT);  // interesting project here... "he said x" -> "x"?
    put("cc", NaturalLogicRelation.REVERSE_ENTAILMENT);  // match dep_conj
    put("compound", NaturalLogicRelation.INDEPENDENCE);  //
    put("flat", NaturalLogicRelation.INDEPENDENCE);  //
    put("mwe", NaturalLogicRelation.INDEPENDENCE);  //
    put("conj:and\\/or", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("conj:and", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("conj:both", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("conj:but", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("conj:nor", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("conj:or", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("conj:plus", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("conj", NaturalLogicRelation.REVERSE_ENTAILMENT);  // match dep_cc
    put("conj_x", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("cop", NaturalLogicRelation.INDEPENDENCE);  //
    put("csubj", NaturalLogicRelation.REVERSE_ENTAILMENT);  // clausal subject is split by clauses
    put("csubj:pass", NaturalLogicRelation.INDEPENDENCE);  // as above
    put("dep", NaturalLogicRelation.REVERSE_ENTAILMENT);  // allow cutting these off, else we just miss a bunch of sentences
    put("det", NaturalLogicRelation.FORWARD_ENTAILMENT);  // todo(gabor) better treatment of generics?
    put("discourse", NaturalLogicRelation.EQUIVALENT);  //
    put("obj", NaturalLogicRelation.REVERSE_ENTAILMENT);  // but, "he studied NLP at Stanford" -> "he studied NLP"
    put("expl", NaturalLogicRelation.EQUIVALENT);  // though we shouldn't see this...
    put("goeswith", NaturalLogicRelation.EQUIVALENT);  // also shouldn't see this
    put("infmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  // deprecated into vmod
    put("iobj", NaturalLogicRelation.REVERSE_ENTAILMENT);  // she gave me a raise -> she gave a raise
    put("mark", NaturalLogicRelation.REVERSE_ENTAILMENT);  // he says that you like to swim -> he says you like to swim
    put("mwe", NaturalLogicRelation.INDEPENDENCE);  // shouldn't see this
    put("neg", NaturalLogicRelation.NEGATION);  //
    put("nn", NaturalLogicRelation.INDEPENDENCE);  //
    put("npadvmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  // "9 months after his election, <main clause>"
    put("nsubj", NaturalLogicRelation.REVERSE_ENTAILMENT);  // Note[gabor]: Only true for _duplicate_ nsubj relations. @see NaturalLogicWeights.
    put("nsubj:pass", NaturalLogicRelation.INDEPENDENCE);  //
    put("number", NaturalLogicRelation.INDEPENDENCE);  //
    put("nummod", NaturalLogicRelation.INDEPENDENCE);  // gets a bit too vague if we allow deleting this? "he served three terms" -?-> "he served terms"
    put("op", NaturalLogicRelation.INDEPENDENCE);  //
    put("parataxis", NaturalLogicRelation.REVERSE_ENTAILMENT);  // we split on clauses on this
    put("partmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  // deprecated into vmod
    put("pcomp", NaturalLogicRelation.INDEPENDENCE);  // though, not so in collapsed dependencies
    put("pobj", NaturalLogicRelation.INDEPENDENCE);  // must delete whole preposition
    put("possessive", NaturalLogicRelation.INDEPENDENCE);  // see dep_poss
    put("poss", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("nmod:poss", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("preconj", NaturalLogicRelation.INDEPENDENCE);  // forbidden to see this
    put("predet", NaturalLogicRelation.INDEPENDENCE);  // forbidden to see this
    put("case", NaturalLogicRelation.INDEPENDENCE);  //
    put("nmod:aboard", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:about", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:above", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:according_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:across_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:across", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:after", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:against", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:ahead_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:along", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:alongside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:alongside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:along_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:amid", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:among", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:anti", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:apart_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:around", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:as_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:as_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:aside_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:as_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:as_per", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:as", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:as_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:at", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:away_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:based_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:because_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:before", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:behind", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:below", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:beneath", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:beside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:besides", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:between", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:beyond", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:but", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:by_means_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:depending_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:dep", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:despite", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:down", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:due_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:during", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:en", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:except_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:excepting", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:except", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:excluding", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:exclusive_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:followed_by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:following", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:if", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:in_accordance_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:in_addition_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:in_case_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:including", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:in_front_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:in_lieu_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:in_place_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:in", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:inside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:inside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:in_spite_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:instead_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:into", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:irrespective_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:like", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:minus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:near", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:near_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:next_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:off_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:off", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:on_account_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:on_behalf_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:on_top_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:onto", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:opposite", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:out_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:out", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:outside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:outside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:over", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:owing_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:past", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:per", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:plus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:preliminary_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:preparatory_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:previous_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:prior_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:pursuant_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:regarding", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:regardless_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:round", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:save", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:since", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:subsequent_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:such_as", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:thanks_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:than", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:throughout", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:through", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:together_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:toward", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:towards", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:underneath", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:under", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:unlike", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:until", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:upon", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:up", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:versus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:via", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:vs.", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:whether", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:within", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:without", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:with_regard_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:with_respect_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("nmod:with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:aboard", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:about", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:above", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:according_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:across_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:across", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:after", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:against", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:ahead_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:along", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:alongside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:alongside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:along_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:amid", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:among", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:anti", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:apart_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:around", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:as_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:as_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:aside_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:as_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:as_per", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:as", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:as_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:at", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:away_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:based_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:because_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:before", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:behind", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:below", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:beneath", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:beside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:besides", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:between", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:beyond", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:but", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:by_means_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:depending_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:dep", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:despite", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:down", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:due_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:during", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:en", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:except_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:excepting", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:except", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:excluding", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:exclusive_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:followed_by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:following", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:if", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:in_accordance_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:in_addition_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:in_case_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:including", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:in_front_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:in_lieu_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:in_place_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:in", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:inside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:inside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:in_spite_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:instead_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:into", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:irrespective_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:like", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:minus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:near", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:near_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:next_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:off_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:off", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:on_account_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:on_behalf_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:on_top_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:onto", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:opposite", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:out_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:out", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:outside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:outside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:over", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:owing_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:past", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:per", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:plus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:preliminary_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:preparatory_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:previous_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:prior_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:pursuant_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:regarding", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:regardless_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:round", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:save", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:since", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:subsequent_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:such_as", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:thanks_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:than", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:throughout", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:through", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:together_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:toward", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:towards", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:underneath", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:under", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:unlike", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:until", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:upon", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:up", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:versus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:via", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:vs.", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:whether", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:within", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:without", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:with_regard_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:with_respect_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("obl:with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prt", NaturalLogicRelation.INDEPENDENCE);  //
    put("punct", NaturalLogicRelation.EQUIVALENT);  //
    put("purpcl", NaturalLogicRelation.REVERSE_ENTAILMENT);  // deprecated into advmod
    put("quantmod", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("ref", NaturalLogicRelation.REVERSE_ENTAILMENT);  // Delete thigns like 'which' referring back to a subject.
    put("rcmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  // "there are great tenors --rcmod--> who are modest"
    put("root", NaturalLogicRelation.INDEPENDENCE);  // err.. never delete
    put("tmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("vmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("xcomp", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
  }});

  /**
   * Returns whether this is a known dependency arc.
   */
  public static boolean knownDependencyArc(String dependencyLabel) {
    return insertArcToNaturalLogicRelation.containsKey(dependencyLabel.toLowerCase());
  }

  /**
   * Returns the natural logic relation corresponding to the given dependency arc being inserted into a sentence.
   */
  public static NaturalLogicRelation forDependencyInsertion(String dependencyLabel) {
    return forDependencyInsertion(dependencyLabel, true);
  }

  /**
   * Returns the natural logic relation corresponding to the given dependency arc being inserted into a sentence.
   * @param dependencyLabel The label we are checking the relation for.
   * @param isSubject Whether this is on the subject side of a relation (e.g., for CONJ_OR edges)
   */
  public static NaturalLogicRelation forDependencyInsertion(String dependencyLabel, boolean isSubject) {
    return forDependencyInsertion(dependencyLabel, isSubject, Optional.empty());
  }

  /**
   * Returns the natural logic relation corresponding to the given dependency arc being inserted into a sentence.
   * @param dependencyLabel The label we are checking the relation for.
   * @param isSubject Whether this is on the subject side of a relation (e.g., for CONJ_OR edges)
   * @param dependent The dependent word of the dependency label.
   */
  public static NaturalLogicRelation forDependencyInsertion(String dependencyLabel, boolean isSubject,
                                                            Optional<String> dependent) {
    if (!isSubject) {
      switch (dependencyLabel) {
        // 'or' in the object position behaves as and.
        case "conj:or":
        case "conj:nor":
          return forDependencyInsertion("conj:and", false);
        case "cc:preconj":
          if (dependent.isPresent() && "neither".equalsIgnoreCase(dependent.get())) {
            return INDEPENDENCE;
          } else {
            return REVERSE_ENTAILMENT;
          }
      }
    }
    NaturalLogicRelation rel = insertArcToNaturalLogicRelation.get(dependencyLabel.toLowerCase());
    if (rel != null) {
      return rel;
    } else {
//      log.info("Unknown dependency arc for NaturalLogicRelation: " + dependencyLabel);
      if (dependencyLabel.startsWith("nmod:")) {
        return NaturalLogicRelation.REVERSE_ENTAILMENT;
      } else if (dependencyLabel.startsWith("conj")) {
        return NaturalLogicRelation.REVERSE_ENTAILMENT;
      } else if (dependencyLabel.startsWith("advcl")) {
        return NaturalLogicRelation.REVERSE_ENTAILMENT;
      } else {
        return NaturalLogicRelation.INDEPENDENCE;
      }
    }
  }

  private static NaturalLogicRelation insertionToDeletion(NaturalLogicRelation insertionRel) {
    switch (insertionRel) {
      case EQUIVALENT: return EQUIVALENT;
      case FORWARD_ENTAILMENT: return REVERSE_ENTAILMENT;
      case REVERSE_ENTAILMENT: return FORWARD_ENTAILMENT;
      case NEGATION: return NEGATION;
      case ALTERNATION: return COVER;
      case COVER: return ALTERNATION;
      case INDEPENDENCE: return INDEPENDENCE;
      default:
        throw new IllegalStateException("Unhandled natural logic relation: " + insertionRel);
    }
  }

  /**
   * Returns the natural logic relation corresponding to the given dependency arc being deleted from a sentence.
   */
  public static NaturalLogicRelation forDependencyDeletion(String dependencyLabel) {
    return forDependencyDeletion(dependencyLabel, true);
  }

  /**
   * Returns the natural logic relation corresponding to the given dependency arc being deleted from a sentence.
   * @param dependencyLabel The label we are checking the relation for
   * @param isSubject Whether this is on the subject side of a relation (e.g., for CONJ_OR edges)
   */
  public static NaturalLogicRelation forDependencyDeletion(String dependencyLabel, boolean isSubject) {
    NaturalLogicRelation rel = forDependencyInsertion(dependencyLabel, isSubject);
    return insertionToDeletion(rel);
  }

  /**
   * Returns the natural logic relation corresponding to the given dependency arc being deleted from a sentence.
   * @param dependencyLabel The label we are checking the relation for
   * @param isSubject Whether this is on the subject side of a relation (e.g., for CONJ_OR edges)
   * @param dependent The dependent word of the dependency label.
   */
  public static NaturalLogicRelation forDependencyDeletion(String dependencyLabel, boolean isSubject,
                                                           Optional<String> dependent) {
    NaturalLogicRelation rel = forDependencyInsertion(dependencyLabel, isSubject, dependent);
    return insertionToDeletion(rel);
  }

}
