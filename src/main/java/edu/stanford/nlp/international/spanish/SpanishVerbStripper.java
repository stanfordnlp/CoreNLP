package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

import java.io.BufferedReader;
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
public final class SpanishVerbStripper implements Serializable  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SpanishVerbStripper.class);

  // The following three classes of verb forms can carry attached
  // pronouns:
  //
  //   - Infinitives
  //   - Gerunds
  //   - Affirmative imperatives

  /**
   * A struct describing the result of verb stripping.
   */
  public static class StrippedVerb {
    private String stem;
    private String originalStem;
    private List<String> pronouns;

    public StrippedVerb(String originalStem, List<String> pronouns) {
      this.originalStem = originalStem;
      this.pronouns = pronouns;
    }

    public void setStem(String stem) {
      this.stem = stem;
    }

    /**
     * Return the normalized stem of the verb -- the way it would appear in
     * isolation without attached pronouns.
     *
     * Here are example mappings from original verb to normalized stem:
     *
     * <ul>
     *   <li>sentaos -&gt; sentad</li>
     *   <li>vámonos -&gt; vamos</li>
     * </ul>
     */
    public String getStem() { return stem; }

    /**
     * Returns the original stem of the verb, simply split off from pronouns.
     * (Contrast with {@link #getStem()}, which returns a normalized form.)
     */
    public String getOriginalStem() { return originalStem; }

    public List<String> getPronouns() { return pronouns; }
  }

  /* HashMap of singleton instances */
  private static final Map<String, SpanishVerbStripper> instances = new HashMap<>();

  private final HashMap<String, String> dict;

  private static final String DEFAULT_DICT =
    "edu/stanford/nlp/international/spanish/enclitic-inflections.data";

  /** Any attached pronouns. The extra grouping around this pattern allows it to be used in String concatenations. */
  private static final String PATTERN_ATTACHED_PRONOUNS =
    "(?:(?:[mts]e|n?os|les?)(?:l[oa]s?)?|l[oa]s?)$";

  private static final Pattern pTwoAttachedPronouns =
    Pattern.compile("([mts]e|n?os|les?)(l[eoa]s?)$");

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
   * The input file must be a list of whitespace-separated verb-lemma-POS triples, one verb
   * form per line.
   *
   * @param dictPath the path to the dictionary file
   */
  private static HashMap<String, String> setupDictionary(String dictPath) {
    HashMap<String, String> dictionary = new HashMap<>();
    BufferedReader br = null;
    try {
      br = IOUtils.readerFromString(dictPath);
      for (String line; (line = br.readLine()) != null; ) {
        String[] words = line.trim().split("\\s");
        if (words.length < 3) {
          System.err.printf("SpanishVerbStripper: adding words to dict, missing fields, ignoring line: %s%n", line);
        } else {
          dictionary.put(words[0], words[2]);
        }
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (IOException e) {
      log.info("Could not load Spanish data file " + dictPath);
    } finally {
      IOUtils.closeIgnoringExceptions(br);
    }
    return dictionary;
  }

  @SuppressWarnings("unchecked")
  private static final Pair<Pattern, String>[] accentFixes = new Pair[] {
    new Pair(Pattern.compile("á"), "a"),
    new Pair(Pattern.compile("é"), "e"),
    new Pair(Pattern.compile("í"), "i"),
    new Pair(Pattern.compile("ó"), "o"),
    new Pair(Pattern.compile("ú"), "u")
  };

  // CONSTRUCTOR

  /** Access via the singleton-like getInstance() methods. */
  private SpanishVerbStripper(String dictPath) {
    dict = setupDictionary(dictPath);
  }

  /**
   * Singleton pattern function for getting a default verb stripper.
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
  private static final Set<String> accentedInfinitives = new HashSet<>(Arrays.asList(
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

  private static final Pattern nosse = Pattern.compile("nos|se");

  /**
   * Validate and normalize the given verb stripper result.
   *
   * Returns <tt>true</tt> if the given data is a valid pairing of verb form
   * and clitic pronoun(s).
   *
   * May modify <tt>pair</tt> in place in order to make the pair valid.
   * For example, if the pair <tt>(senta, os)</tt> is provided, this
   * method will return <tt>true</tt> and modify the pair to be
   * <tt>(sentad, os)</tt>.
   */
  private boolean normalizeStrippedVerb(StrippedVerb verb) {
    String normalized = removeAccents(verb.getOriginalStem());
    String firstPron = verb.getPronouns().get(0).toLowerCase();

    // Look up verb in dictionary.
    String verbKey = normalized.toLowerCase();
    String pos = dict.get(verbKey);
    boolean valid = false;

    // System.out.println(verbKey + " " + dict.containsKey(verbKey + 's'));

    // Validate resulting split verb and normalize the new form at the same
    // time.
    if (pos != null) {
      // Check not invalid combination of verb root and pronoun.
      // (If we combine a second-person plural imperative and the
      // second person plural object pronoun, we expect to see an
      // elided verb root, not the normal one that's in the
      // dictionary.)
      valid = ! (pos.equals("VMM02P0") && firstPron.equalsIgnoreCase("os"));
    } else if (firstPron.equalsIgnoreCase("os") && dict.containsKey(verbKey + 'd')) {
      // Special case: de-elide elided verb root in the case of a second
      // person plural imperative + second person object pronoun
      //
      // (e.g., given (senta, os), return (sentad, os))
      normalized = normalized + getCase(normalized, 'd');
      valid = true;
    } else if (nosse.matcher(firstPron).matches() && dict.containsKey(verbKey + 's')) {
      // Special case: de-elide elided verb root in the case of a first
      // person plural imperative + object pronoun
      //
      // (vámo, nos) -> (vámos, nos)
      normalized = normalized + getCase(normalized, 's');
      valid = true;
    }

    if (valid) {
      // Update normalized form.
      verb.setStem(normalized);
      return true;
    }

    return false;
  }

  /**
   * Separate attached pronouns from the given verb.
   *
   * @param word A valid Spanish verb with clitic pronouns attached.
   * @param pSuffix A pattern to match these attached pronouns.
   * @return A {@link StrippedVerb} instance or <tt>null</tt> if no attached
   *         pronouns were found.
   */
  private StrippedVerb stripSuffix(String word, Pattern pSuffix) {
    Matcher m = pSuffix.matcher(word);
    if (m.find()) {
      String stripped = word.substring(0, m.start());

      List<String> attached = new ArrayList<>();
      for (int i = 0; i < m.groupCount(); i++)
        attached.add(m.group(i + 1));

      return new StrippedVerb(stripped, attached);
    }

    return null;
  }

  /**
   * Attempt to separate attached pronouns from the given verb.
   *
   * @param verb Spanish verb
   * @return Returns a StrippedVerb struct/tuple <tt>(originalStem, normalizedStem, pronouns)</tt>,
   *         or <tt>null</tt> if no pronouns could be located and separated.
   *         <ul>
               <li><tt>originalStem</tt>: The verb stem simply split from the following pronouns.</li>
   *           <li><tt>normalizedStem</tt>: The verb stem normalized to dictionary form, i.e. in the
   *                   form it would appear with the same conjugation but no pronouns.</li>
   *           <li><tt>pronouns</tt>: Pronouns which were attached to the verb.</li>
   *         </ul>
   */
  public StrippedVerb separatePronouns(String verb) {
    StrippedVerb result;

    // Try to strip just one pronoun first
    result = stripSuffix(verb, pOneAttachedPronoun);
    if (result != null && normalizeStrippedVerb(result)) {
      return result;
    }

    // Now two
    result = stripSuffix(verb, pTwoAttachedPronouns);
    if (result != null && normalizeStrippedVerb(result)) {
      return result;
    }

    return null;
  }

  /**
   * Remove attached pronouns from a strippable Spanish verb form. (Use
   * {@link #isStrippable(String)} to determine if a word is a
   * strippable verb.)
   *
   * Converts, e.g.,
   * <ul>
   *   <li> decírmelo -&gt; decir
   *   <li> mudarse -&gt; mudar
   *   <li> contándolos -&gt; contando
   *   <li> hazlo -&gt; haz
   * </ul>
   *
   * @return A verb form stripped of attached pronouns, or <tt>null</tt>
   *           if no pronouns were located / stripped.
   */
  public String stripVerb(String verb) {
    StrippedVerb separated = separatePronouns(verb);
    if (separated != null) {
      return separated.getStem();
    }
    return null;
  }

  private static final long serialVersionUID = -4780144226395772354L;

}
