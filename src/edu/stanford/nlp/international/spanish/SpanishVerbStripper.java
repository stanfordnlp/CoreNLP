package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.util.Pair;
import java.util.regex.Pattern;

/**
 * Provides a utility function for removing attached pronouns from
 * Spanish verb forms.
 */
public class SpanishVerbStripper {

  // The following three classes of verb forms can carry attached
  // pronouns:
  //
  //   - Infinitives
  //   - Gerunds
  //   - Affirmative imperatives

  private static final String PATTERN_ATTACHED_PRONOUNS =
    "(?:(?:(?:[mts]e|n?os|les?)(?:l[oa]s?)?)|l[oa]s?)$";

  /**
   * Matches infinitives and gerunds with attached pronouns.
   */
  private static final Pattern pStrippable =
    Pattern.compile("(?:[aeiáéí]r|[áé]ndo)" + PATTERN_ATTACHED_PRONOUNS);

  /**
   * Matches the attached pronouns on a verb known to be strippable (see
   * {@link #isStrippable(String)}).
   */
  private static final Pattern pAttachedPronouns =
    Pattern.compile(PATTERN_ATTACHED_PRONOUNS);

  @SuppressWarnings("unchecked")
  private static final Pair<Pattern, String>[] accentFixes = new Pair[] {
    new Pair(Pattern.compile("ár$"), "ar"),
    new Pair(Pattern.compile("ér$"), "er"),
    new Pair(Pattern.compile("ír$"), "ir"),
    new Pair(Pattern.compile("ándo$"), "ando"),
    new Pair(Pattern.compile("(?<=[iy])éndo$"), "endo"),
  };

  /**
   * Determine if the given word is a verb which needs to be stripped.
   */
  public static boolean isStrippable(String word) {
    return pStrippable.matcher(word).find();
  }

  /**
   * Remove attached pronouns from a strippable Spanish verb form. (Use
   * {@link #isStrippable(String)} to determine if a word is a
   * strippable verb.)
   *
   * Converts e.g.
   *
   *   - decírmelo -> decir
   *   - mudarse -> mudar
   *   - contándolos -> contando
   *   - hazlo -> haz
   */
  public static String stripVerb(String verb) {
    // Remove pronouns
    String stripped = pAttachedPronouns.matcher(verb).replaceAll("");

    // Remove accent
    for (Pair<Pattern, String> accentFix : accentFixes)
      stripped = accentFix.first().matcher(stripped)
        .replaceAll(accentFix.second());

    return stripped;
  }

}
