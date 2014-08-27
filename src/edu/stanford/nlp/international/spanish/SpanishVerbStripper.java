package edu.stanford.nlp.international.spanish;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;

import edu.stanford.nlp.util.Pair;

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

  /* TODO: FIGURE THIS OUT!! */
  private static final String DICT_PATH = "data/edu/stanford/nlp/international/spanish/enclitic-inflections.data";

  private static HashMap<String, String> dict;

  private static final String PATTERN_ATTACHED_PRONOUNS =
    "(?:(?:(?:[mts]e|n?os|les?)(?:l[oa]s?)?)|l[oa]s?)$";

  private static final Pattern pTwoAttachedPronouns =
    Pattern.compile("(?:([mts]e|n?os|les?)(l[eoa]s?)?)$");

  private static final Pattern pOneAttachedPronoun =
    Pattern.compile("([mts]e|n?os|les?|l[oa]s?)$");

  /**
   * Matches infinitives and gerunds with attached pronouns.
   * Original: Pattern.compile("(?:[aeiáéí]r|[áé]ndo)" + PATTERN_ATTACHED_PRONOUNS);
   */
  private static final Pattern pStrippable =
    Pattern.compile("(?:[aeiáéí]r|[áé]ndo|[aeáé]n?|[aeiáéí](?:d(?!os)|(?=os)))" + PATTERN_ATTACHED_PRONOUNS);

  /**
   * Matches irregular imperatives:
   * decir = di, hacer = haz, ver = ve, poner = pon, salir = sal,
   * ser = sé, tener = ten, venir = ven
   * And id + os = idos, not ios
   */
  private static final Pattern pIrregulars =
    Pattern.compile("^(?:d[ií]|h[aá]z|v[eé]|p[oó]n|s[aá]l|sé|t[eé]n|v[eé]n|(?:id(?=os$)))" + PATTERN_ATTACHED_PRONOUNS);

  static {
    try {
      dict = new HashMap<String, String>();
      BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(DICT_PATH), "UTF-8"));
      String line = br.readLine();
      for(; line != null; line = br.readLine()) {
        String[] words = line.trim().split("\\s");
        if(words.length < 3) {
          System.err.printf("SpanishVerbStripper: addings words to dict, missing word, ignoring line");
        }
        dict.put(words[0], words[2]);
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      throw new RuntimeException("Could not load Spanish data file " + DICT_PATH);
    } catch (IOException e) {
      throw new RuntimeException("Could not load Spanish data file " + DICT_PATH);
    }
  }

  /**
   * Matches the attached pronouns on a verb known to be strippable (see
   * {@link #isStrippable(String)}).
   */
  private static final Pattern pAttachedPronouns =
    Pattern.compile(PATTERN_ATTACHED_PRONOUNS);

  @SuppressWarnings("unchecked")
  private static final Pair<Pattern, String>[] accentFixes = new Pair[] {
    //new Pair(Pattern.compile("ár$"), "ar"),
    //new Pair(Pattern.compile("ér$"), "er"),
    //new Pair(Pattern.compile("ír$"), "ir"),
    //new Pair(Pattern.compile("ándo$"), "ando"),
    //new Pair(Pattern.compile("(?<=[iy])éndo$"), "endo"),
    new Pair(Pattern.compile("á"), "a"),
    new Pair(Pattern.compile("é"), "e"),
    new Pair(Pattern.compile("í"), "i"),
    new Pair(Pattern.compile("ó"), "o"),
    new Pair(Pattern.compile("ú"), "u")
  };

  /**
   * The verbs in this set have accents in their infinitive forms;
   * don't remove the accents when stripping pronouns!
   */
  private static final Set<String> accentedInfinitives = new HashSet<String>() {{
    add("desleír");
    add("desoír");
    add("embaír");
    add("engreír");
    add("entreoír");
    add("freír");
    add("oír");
    add("refreír");
    add("reír");
    add("sofreír");
    add("sonreír");
  }};

  /**
   * Determine if the given word is a verb which needs to be stripped.
   */
  public static boolean isStrippable(String word) {
    return (pStrippable.matcher(word).find() || pIrregulars.matcher(word).find());
  }

  public static String removeAccents(String word) {
    if (accentedInfinitives.contains(word))
      return word;

    String stripped = word;
    for (Pair<Pattern, String> accentFix : accentFixes)
      stripped = accentFix.first().matcher(stripped)
        .replaceAll(accentFix.second());
    return stripped;
  }

  /**
   * Examines the given verb pair and returns <tt>true</tt> if it is a
   * valid pairing of verb form and clitic pronoun(s).
   *
   * May modify <tt>pair</tt> in place in order to make the pair valid.
   * For example, if the pair <tt>(senta, os)</tt> is provided, this
   * method will return <tt>true</tt> and modify the pair to be
   * <tt>(sentad, os)</tt>.
   */
  private static boolean validateVerbPair(Pair<String, List<String>> pair) {
    // Catch: if we have a second-person plural imperative with the
    // pronoun 'os' attached, the terminal 'd' of the imperative *must*
    // be elided. (Words like "sentados" look like imperatives if we
    // don't enforce this rule!)
    List<String> pronouns = pair.second();
    boolean hasOs = pronouns.size() > 0 && pronouns.get(0).equalsIgnoreCase("os");

    String stripped = pair.first().toLowerCase();
    String verbPos = dict.get(stripped);
    if (verbPos != null) {
      if (verbPos.equals("VMM02P0") && hasOs)
        // Not possible!
        return false;

      return true;
    }

    // Try `word + 'd'` as well for cases like 'sentaos'; stripped this
    // becomes 'senta', and we only have the form 'sentad' in the
    // dictionary
    if (hasOs) {
      verbPos = dict.get(stripped + 'd');
      if (verbPos != null && verbPos.equals("VMM02P0")) {
        // Write the change to the original pair
        pair.setFirst(stripped + 'd');
        return true;
      }
    }

    return false;
  }

  /**
   * Separate attached pronouns from the given verb.
   *
   * @param word A valid Spanish verb with clitic pronouns attached.
   * @param pSuffix A pattern to match these attached pronouns.
   * @return A pair containing the verb (pronouns removed by the given
   *           pattern) and a list of the pronouns which were attached
   *           to the verb.
   */
  private static Pair<String, List<String>> stripSuffix(String word,
                                                        Pattern pSuffix) {
    Matcher m = pSuffix.matcher(word);
    if(m.find()) {
      String stripped = word.substring(0, m.start());
      stripped = removeAccents(stripped);

      List<String> attached = new ArrayList<String>();
      for (int i = 0; i < m.groupCount(); i++)
        attached.add(m.group(i + 1));

      return new Pair<String, List<String>>(stripped, attached);
    }

    return null;
  }

  /**
   * Attempt to separate attached pronouns from the given verb.
   *
   * @param verb Spanish verb
   * @return A pair containing the verb (pronouns removed) and a list of
   *           the pronouns which were attached to the verb, or
   *           <tt>null</tt> if no pronouns could be located and
   *           separated.
   */
  public static Pair<String, List<String>> separatePronouns(String verb) {
    Pair<String, List<String>> separated;

    // Try to strip just one pronoun first
    separated = stripSuffix(verb, pOneAttachedPronoun);
    if (separated != null && validateVerbPair(separated))
      return separated;

    // Now two
    separated = stripSuffix(verb, pTwoAttachedPronouns);
    if (separated != null && validateVerbPair(separated))
      return separated;

    return null;
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
   *
   * @return A verb form stripped of attached pronouns, or <tt>null</tt>
   *           if no pronouns were located / stripped.
   */
  public static String stripVerb(String verb) {
    Pair<String, List<String>> separated = separatePronouns(verb);
    if (separated != null)
      return separated.first();

    return null;
  }

}
