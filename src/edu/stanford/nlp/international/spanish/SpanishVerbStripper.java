package edu.stanford.nlp.international.spanish;

import edu.stanford.nlp.util.Pair;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.HashMap;
import java.io.*;

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
  private static final String DICT_PATH = "data/enclitic-inflections.data";
 
  private static HashMap<String, String> dict;

  private static final String PATTERN_ATTACHED_PRONOUNS =
    "(?:(?:(?:[mts]e|n?os|les?)(?:l[oa]s?)?)|l[oa]s?)$";

  private static final Pattern pTwoAttachedPronouns =
    Pattern.compile("(?:(?:[mts]e|n?os|les?)(?:l[oa]s?)?)$");

  private static final Pattern pOneAttachedPronoun =
    Pattern.compile("(?:[mts]e|n?os|les?|l[oa]s?)$");

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
        if(words.length < 2) {
          System.err.printf("SpanishVerbStripper: addings words to dict, missing word, ignoring line");
        }
        dict.put(words[0], words[1]);
      }
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
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
   * Determine if the given word is a verb which needs to be stripped.
   */
  public static boolean isStrippable(String word) {
    return (pStrippable.matcher(word).find() || pIrregulars.matcher(word).find());
  }

  public static String removeAccents(String word) {
    String stripped = word;
    for (Pair<Pattern, String> accentFix : accentFixes)
      stripped = accentFix.first().matcher(stripped)
        .replaceAll(accentFix.second());
    return stripped;
  }

  /**
   * Returns whether @word is a valid form of a valid dictionary verb.
   */
  private static boolean isVerb(String word) {
    return dict.containsKey(word);
  }

  /**
   * Strips the suffix matched by @pSuffix off of @word and returns
   * the stripped word, null if unable to strip.
   */
  private static String stripSuffix(String word, Pattern pSuffix) {
    Matcher m = pSuffix.matcher(word);
    if(m.find()) {
      String stripped = word.substring(0, m.start());
      System.out.println(m.start());
      return removeAccents(stripped);
    }
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
   */
  public static String stripVerb(String verb) {
    String stripped = stripSuffix(verb, pOneAttachedPronoun);
    if(stripped == null || (!isVerb(stripped) && !isVerb(stripped + "d"))) {
      System.out.println(stripped);
      stripped = stripSuffix(verb, pTwoAttachedPronouns);
      if(stripped == null || (!isVerb(stripped) && !isVerb(stripped + "d"))) {
        System.out.println(stripped);
        return null;
      }
    }
    return stripped;
  }

}
