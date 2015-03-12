package edu.stanford.nlp.naturalli;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
  EQUIVALENT(0, true, false),
  FORWARD_ENTAILMENT(1, true, false),
  REVERSE_ENTAILMENT(2, false, false),
  NEGATION(3, false, true),
  ALTERNATION(4, false, true),
  COVER(5, false, false),
  INDEPENDENCE(6, false, false), ;

  public final int fixedIndex;
  public final boolean isEntailed, isNegated;

  NaturalLogicRelation(int fixedIndex, boolean isEntailed, boolean isNegated) {
    this.fixedIndex = fixedIndex;
    this.isEntailed = isEntailed;
    this.isNegated = isNegated;
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

  private static final Map<String, NaturalLogicRelation> insertArcToNaturalLogicRelation = Collections.unmodifiableMap(new HashMap<String, NaturalLogicRelation>() {{
    put("acomp", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("advcl", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("advmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("agent", NaturalLogicRelation.INDEPENDENCE);  //
    put("amod", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("appos", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("aux", NaturalLogicRelation.INDEPENDENCE);  // he left -/-> he should leave
    put("auxpass", NaturalLogicRelation.INDEPENDENCE);  // some cat adopts -/-> some cat got adopted
    put("ccomp", NaturalLogicRelation.INDEPENDENCE);  // interesting project here... "he said x" -> "x"?
    put("cc", NaturalLogicRelation.REVERSE_ENTAILMENT);  // match dep_conj
    put("conj_and\\/or", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("conj_and", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("conj_both", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("conj_but", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("conj_nor", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("conj_or", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("conj_plus", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
    put("conj", NaturalLogicRelation.REVERSE_ENTAILMENT);  // match dep_cc
    put("conj_x", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("cop", NaturalLogicRelation.INDEPENDENCE);  //
    put("csubj", NaturalLogicRelation.INDEPENDENCE);  // don't drop subjects.
    put("csubjpass", NaturalLogicRelation.INDEPENDENCE);  // as above
    put("dep", NaturalLogicRelation.INDEPENDENCE);  //
    put("det", NaturalLogicRelation.FORWARD_ENTAILMENT);  // todo(gabor) better treatment of generics?
    put("discourse", NaturalLogicRelation.EQUIVALENT);  //
    put("dobj", NaturalLogicRelation.REVERSE_ENTAILMENT);  // but, "he studied NLP at Stanford" -> "he studied NLP"
    put("expl", NaturalLogicRelation.EQUIVALENT);  // though we shouldn't see this...
    put("goeswith", NaturalLogicRelation.EQUIVALENT);  // also shouldn't see this
    put("infmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  // deprecated into vmod
    put("iobj", NaturalLogicRelation.REVERSE_ENTAILMENT);  // she gave me a raise -> she gave a raise
    put("mark", NaturalLogicRelation.REVERSE_ENTAILMENT);  // he says that you like to swim -> he says you like to swim
    put("mwe", NaturalLogicRelation.INDEPENDENCE);  // shouldn't see this
    put("neg", NaturalLogicRelation.NEGATION);  //
    put("nn", NaturalLogicRelation.INDEPENDENCE);  //
    put("npadvmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  // "9 months after his election, <main clause>"
    put("nsubj", NaturalLogicRelation.INDEPENDENCE);  //
    put("nsubjpass", NaturalLogicRelation.INDEPENDENCE);  //
    put("number", NaturalLogicRelation.INDEPENDENCE);  //
    put("num", NaturalLogicRelation.INDEPENDENCE);  // gets a bit too vague if we allow deleting this? "he served three terms" -?-> "he served terms"
    put("op", NaturalLogicRelation.INDEPENDENCE);  //
    put("parataxis", NaturalLogicRelation.INDEPENDENCE);  // or, reverse?
    put("partmod", NaturalLogicRelation.REVERSE_ENTAILMENT);  // deprecated into vmod
    put("pcomp", NaturalLogicRelation.INDEPENDENCE);  // though, not so in collapsed dependencies
    put("pobj", NaturalLogicRelation.INDEPENDENCE);  // must delete whole preposition
    put("possessive", NaturalLogicRelation.INDEPENDENCE);  // see dep_poss
    put("poss", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("preconj", NaturalLogicRelation.INDEPENDENCE);  // forbidden to see this
    put("predet", NaturalLogicRelation.INDEPENDENCE);  // forbidden to see this
    put("prep_aboard", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_about", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_above", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_according_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_across_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_across", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_after", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_against", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_ahead_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_along", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_alongside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_alongside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_along_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_amid", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_among", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_anti", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_apart_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_around", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_as_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_as_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_aside_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_as_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_as_per", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_as", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_as_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_at", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_away_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_based_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_because_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_before", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_behind", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_below", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_beneath", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_beside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_besides", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_between", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_beyond", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_but", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_by_means_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_aboard", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_about", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_above", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_according_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_across_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_across", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_after", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_against", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_ahead_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_along", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_alongside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_along_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_amid", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_among", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_anti", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_apart_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_around", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_as_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_as_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_aside_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_as_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_as_per", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_as", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_as_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_at", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_away_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_based_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_because_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_before", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_behind", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_below", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_beneath", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_beside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_besides", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_between", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_beyond", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_but", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_by_means_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_close_by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_close_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_compared_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_compared_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_concerning", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_considering", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_contrary_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_depending_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_despite", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_down", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_due_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_during", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_except_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_excepting", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_except", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_excluding", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_exclusive_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_far_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_followed_by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_following", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_in_accordance_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_in_addition_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_in_case_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_in_front_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_in_lieu_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_in_place_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_in", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_inside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_inside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_in_spite_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_instead_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_into", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_irrespective_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_like", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_close_by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_close_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_minus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_near", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_near_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_next_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_off_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_off", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_compared_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_compared_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_on_account_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_on_behalf_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_concerning", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_considering", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_on_top_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_onto", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_contrary_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_opposite", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_out_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_outside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_outside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_over", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_owing_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_past", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_per", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_plus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_preliminary_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_preparatory_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_previous_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_prior_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_pursuant_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_regarding", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_regardless_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_round", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_save", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_since", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_subsequent_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_such_as", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_thanks_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_than", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_through", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_together_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_toward", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_towards", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_underneath", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_under", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_unlike", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_until", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_upon", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_up", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_versus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_via", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_within", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_without", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_with_regard_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_with_respect_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prepc_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_depending_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_dep", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_despite", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_down", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_due_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_during", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_en", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_except_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_excepting", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_except", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_excluding", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_exclusive_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_followed_by", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_following", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_for", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_from", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_if", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_in_accordance_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_in_addition_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_in_case_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_including", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_in_front_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_in_lieu_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_in_place_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_in", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_inside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_inside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_in_spite_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_instead_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_into", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_irrespective_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_like", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_minus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_near", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_near_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_next_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_off_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_off", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_on_account_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_on_behalf_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_on", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_on_top_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_onto", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_opposite", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_out_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_out", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_outside_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_outside", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_over", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_owing_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_past", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_per", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_plus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_preliminary_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_preparatory_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_previous_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_prior_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_pursuant_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_regarding", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_regardless_of", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_round", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_save", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_since", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_subsequent_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_such_as", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_thanks_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_than", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_throughout", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_through", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_together_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_toward", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_towards", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_underneath", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_under", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_unlike", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_until", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_upon", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_up", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_versus", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_via", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_vs.", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_whether", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_within", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_without", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_with_regard_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_with_respect_to", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prep_with", NaturalLogicRelation.REVERSE_ENTAILMENT);  //
    put("prt", NaturalLogicRelation.INDEPENDENCE);  //
    put("punct", NaturalLogicRelation.EQUIVALENT);  //
    put("purpcl", NaturalLogicRelation.REVERSE_ENTAILMENT);  // deprecated into advmod
    put("quantmod", NaturalLogicRelation.FORWARD_ENTAILMENT);  //
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
    NaturalLogicRelation rel = insertArcToNaturalLogicRelation.get(dependencyLabel.toLowerCase());
    if (rel != null) {
      return rel;
    } else {
      System.err.println("Unknown dependency arc for NaturalLogicRelation: " + dependencyLabel);
      if (dependencyLabel.startsWith("prep_")) {
        return NaturalLogicRelation.REVERSE_ENTAILMENT;
      } else if (dependencyLabel.startsWith("conj_")) {
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
    NaturalLogicRelation rel = forDependencyInsertion(dependencyLabel);
    return insertionToDeletion(rel);
  }

}
