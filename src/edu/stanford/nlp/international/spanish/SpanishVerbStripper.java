package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides a utility function for removing attached pronouns from
 * Spanish verb forms.
 *
 * @author Jon Gauthier
 * @author Ishita Prasad
 */
public final class SpanishVerbStripper implements Serializable {

  // The following three classes of verb forms can carry attached
  // pronouns:
  //
  //   - Infinitives
  //   - Gerunds
  //   - Affirmative imperatives

  /* HashMap of singleton instances */
  private static final Map<String, SpanishVerbStripper> instances = new HashMap<String, SpanishVerbStripper>();

  private HashMap<String, String> dict;

  private static final String DEFAULT_DICT =
    "edu/stanford/nlp/international/spanish/enclitic-inflections.data";

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
    Pattern.compile("(?:[aeiáéí]r|[áé]ndo|[aeáé]n?|[aeáé]mos?|[aeiáéí](?:d(?!os)|(?=os)))" + PATTERN_ATTACHED_PRONOUNS);

  /**
   * Matches irregular imperatives:
   * decir = di, hacer = haz, ver = ve, poner = pon, salir = sal,
   * ser = sé, tener = ten, venir = ven
   * And id + os = idos, not ios
   */
  private static final Pattern pIrregulars =
    Pattern.compile("^(?:d[ií]|h[aá]z|v[eé]|p[oó]n|s[aá]l|sé|t[eé]n|v[eé]n|(?:id(?=os$)))" + PATTERN_ATTACHED_PRONOUNS);

  /**
   * Sets up dictionary of valid verbs and their POS info from an input file.
   * The input file must be a list of tab-separated verb-POS pairs, one verb
   * per line.
   *
   * @param dictPath the path to the dictionary file
   */
  private void setupDictionary(String dictPath) {
    try {
      dict = new HashMap<String, String>();
      BufferedReader br = IOUtils.readerFromString(dictPath);
      for(String line; (line = br.readLine()) != null; ) {
        String[] words = line.trim().split("\\s");
        if(words.length < 3) {
          System.err.printf("SpanishVerbStripper: addings words to dict, missing word, ignoring line%n");
        } else {
          dict.put(words[0], words[2]);
        }
      }
      IOUtils.closeIgnoringExceptions(br);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      System.err.println("Could not load Spanish data file " + dictPath);
    } catch (IOException e) {
      System.err.println("Could not load Spanish data file " + dictPath);
    }
  }

  @SuppressWarnings("unchecked")
  private static final Pair<Pattern, String>[] accentFixes = new Pair[] {
    new Pair(Pattern.compile("á"), "a"),
    new Pair(Pattern.compile("é"), "e"),
    new Pair(Pattern.compile("í"), "i"),
    new Pair(Pattern.compile("ó"), "o"),
    new Pair(Pattern.compile("ú"), "u")
  };

  // CONSTRUCTORS

  private SpanishVerbStripper() {
    this(DEFAULT_DICT);
  }

  private SpanishVerbStripper(String dictPath) {
    setupDictionary(dictPath);
  }

  /**
   * Singleton pattern function for getting a default verb stripper
   */
  public static SpanishVerbStripper getInstance() {
    return getInstance(DEFAULT_DICT);
  }

  /**
   * Singleton pattern function for getting a verb stripper based on
   * the dictionary at dictPath.
   *
   * @param dictPath the path to the dictionary for this verb stripper.
   */
  public static SpanishVerbStripper getInstance(String dictPath) {
    SpanishVerbStripper svs = instances.get(dictPath);
    if (svs == null) {
      svs = new SpanishVerbStripper(dictPath);
      instances.put(dictPath, svs);
    }
    return svs;
  }

  /**
   * The verbs in this set have accents in their infinitive forms;
   * don't remove the accents when stripping pronouns!
   */
  private static final Set<String> accentedInfinitives = new HashSet<String>(Arrays.asList(
    "desleír",
    "desoír",
    "embaír",
    "engreír",
    "entreoír",
    "freír",
    "oír",
    "refreír",
    "reír",
    "sofreír",
    "sonreír"
  ));

  // STATIC FUNCTIONS

  /**
   * Determine if the given word is a verb which needs to be stripped.
   */
  public static boolean isStrippable(String word) {
    return pStrippable.matcher(word).find() || pIrregulars.matcher(word).find();
  }

  private static String removeAccents(String word) {
    if (accentedInfinitives.contains(word))
      return word;

    String stripped = word;
    for (Pair<Pattern, String> accentFix : accentFixes)
      stripped = accentFix.first().matcher(stripped)
        .replaceAll(accentFix.second());
    return stripped;
  }

  /**
   * Determines the case of the letter as if it had been part of the
   * original string
   *
   * @param letter The character whose case must be determined
   * @param original The string we are modelling the case on
   */
  private static char getCase(String original, char letter) {
    if (Character.isUpperCase(original.charAt(original.length()-1))) {
      return Character.toUpperCase(letter);
    } else {
      return Character.toLowerCase(letter);
    }
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
  private boolean validateVerbPair(Pair<String, List<String>> pair) {
    String stripped = pair.first().toLowerCase();
    String firstPron = pair.second().get(0).toLowerCase();

    String pos = dict.get(stripped);

    if (pos != null) {
      if (pos.equals("VMM02P0") && firstPron.equalsIgnoreCase("os")) {
        // Invalid combination of verb root and pronoun.
        // (If we combine a second-person plural imperative and the
        // second person plural object pronoun, we expect to see an
        // elided verb root, not the normal one that's in the
        // dictionary.)
        return false;
      }

      return true;
    }

    // Special case: de-elide elided verb root in the case of a second
    // person plural imperative + second person object pronoun
    //
    // (e.g., given (senta, os), return (sentad, os))
    if (firstPron.equalsIgnoreCase("os") && dict.containsKey(stripped + 'd')) {
      pair.setFirst(pair.first() + getCase(pair.first(), 'd'));
      return true;
    }

    // Special case: de-elide elided verb root in the case of a first
    // person plural imperative + object pronoun
    //
    // (vámo, nos) -> (vámos, nos)
    if (firstPron.matches("nos|se") && dict.containsKey(stripped + 's')) {
      pair.setFirst(pair.first() + getCase(pair.first(), 's'));
      return true;
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
    if (m.find()) {
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
  public Pair<String, List<String>> separatePronouns(String verb) {
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
   *   - decírmelo -&gt; decir
   *   - mudarse -&gt; mudar
   *   - contándolos -&gt; contando
   *   - hazlo -&gt; haz
   *
   * @return A verb form stripped of attached pronouns, or <tt>null</tt>
   *           if no pronouns were located / stripped.
   */
  public String stripVerb(String verb) {
    Pair<String, List<String>> separated = separatePronouns(verb);
    if (separated != null) {
      return separated.first();
    }
    return null;
  }

  private static final long serialVersionUID = -4780144226395772354L;

}
