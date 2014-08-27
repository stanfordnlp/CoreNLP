package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.util.Pair;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A utility for preprocessing the AnCora Spanish corpus.
 *
 * Attempts to disambiguate Spanish personal pronouns which have
 * multiple senses:
 *
 *     <em>me, te, se, nos, os</em>
 *
 * Each of these can be used as 1) an indirect object pronoun or as
 * 2) a reflexive pronoun. (<em>me, te, nos,</em> and <em>os</em> can
 * also be used as direct object pronouns.)
 *
 * For the purposes of corpus preprocessing, all we need is to
 * distinguish between the object- and reflexive-pronoun cases.
 *
 * Disambiguation is done first by (dictionary-powered) heuristics, and
 * then by brute force. The brute-force decisions are manual tags for
 * verbs with clitic pronouns which appear in the AnCora corpus.
 *
 * @author Jon Gauthier
 * @see edu.stanford.nlp.trees.international.spanish.SpanishTreeNormalizer
 */
public class SpanishPronounDisambiguator {

  public static enum PersonalPronounType {OBJECT, REFLEXIVE}

  private static final Set<String> ambiguousPersonalPronouns = new HashSet<String>() {{
    add("me"); add("te"); add("se"); add("nos"); add("os");
  }};

  /**
   * The following verbs always use ambiguous pronouns in a reflexive
   * sense in the corpus.
   */
  private static final Set<String> alwaysReflexiveVerbs = new HashSet<String>() {{
    add("comportar"); add("enterar"); add("vestir");
  }};

  /**
   * Determine if the given pronoun can have multiple senses.
   */
  public static boolean isAmbiguous(String pronoun) {
    return ambiguousPersonalPronouns.contains(pronoun);
  }

  /**
   * Determine whether the given clitic pronoun is an indirect object
   * pronoun or a reflexive pronoun.
   *
   * This method is only defined when the pronoun is one of
   *
   *     me, te, se, nos, os
   *
   * i.e., those in which the meaning is actually ambiguous.
   *
   * @param splitVerb The verb with clitics split off, as returned by
   *                  {@link edu.stanford.nlp.international.spanish.SpanishVerbStripper#separatePronouns(String)}.
   * @param pronounIdx The index of the pronoun within
   *                   {@code splitVerb.second()} which should be
   *                   disambiguated.
   * @throws java.lang.IllegalArgumentException If the given pronoun is
   *         not ambiguous, or its disambiguation is not supported.
   */
  public static PersonalPronounType disambiguatePersonalPronoun(Pair<String, List<String>> splitVerb, int pronounIdx) {
    String pronoun = splitVerb.second().get(pronounIdx).toLowerCase();
    if (!ambiguousPersonalPronouns.contains(pronoun))
      throw new IllegalArgumentException("We don't support disambiguating pronoun '" + pronoun + "'");

    String verb = splitVerb.first().toLowerCase();
    if (alwaysReflexiveVerbs.contains(verb))
      return PersonalPronounType.REFLEXIVE;

    return PersonalPronounType.OBJECT;
  }

}
