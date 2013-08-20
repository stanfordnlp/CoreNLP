package edu.stanford.nlp.process;


import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.StringUtils;


import edu.stanford.nlp.ling.HasWord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Takes a HasWord or String and returns an Americanized version of it.
 * Optionally, it does some month/day name normalization to capitalized.
 * This is deterministic spelling conversion, and so cannot deal with
 * certain cases involving complex ambiguities, but it can do most of the
 * simple case of English to American conversion.
 * <p>
 * <i>This list is still quite incomplete, but does some of the
 * most common cases found when running our parser or doing biomedical
 * processing. to expand this list, we should probably look at:</i>
 * <code>http://wordlist.sourceforge.net/</code> or
 * <code>http://home.comcast.net/~helenajole/Harry.html</code>.
 *
 * @author Christopher Manning
 */
public class Americanize implements Function<HasWord,HasWord> {

  /** Whether to capitalize month and day names. The default is true. */
  private final boolean capitalizeTimex;

  public static final int DONT_CAPITALIZE_TIMEX = 1;

  /** No word shorter in length than this is changed by Americanize */
  private static final int MINIMUM_LENGTH_CHANGED = 4;
  /** No word shorter in length than this can match a Pattern */
  private static final int MINIMUM_LENGTH_PATTERN_MATCH = 6;

  public Americanize() {
    this(0);
  }

  /** Make an object for Americanizing spelling.
   *
   * @param flags An integer representing bit flags. At present the only
   *      recognized flag is DONT_CAPITALIZE_TIMEX = 1 which suppresses
   *      capitalization of days of the week and months.
   */
  public Americanize(int flags) {
    capitalizeTimex = (flags & DONT_CAPITALIZE_TIMEX) == 0;
  }


  /**
   * Americanize the HasWord or String coming in.
   *
   * @param w A HasWord or String to covert to American if needed.
   * @return Either the input or an Americanized version of it.
   */
  @Override
  public HasWord apply(HasWord w) {
    String str = w.word();
    String outStr = americanize(str, capitalizeTimex);
    if (!outStr.equals(str)) {
      w.setWord(outStr);
    }
    return w;
  }


  /**
   * Convert the spelling of a word from British to American English.
   * This is deterministic spelling conversion, and so cannot deal with
   * certain cases involving complex ambiguities, but it can do most of the
   * simple cases of English to American conversion. Month and day names will
   * be capitalized unless you have changed the default setting.
   *
   * @param str The String to be Americanized
   * @return The American spelling of the word.
   */
  public static String americanize(String str) {
    return americanize(str, true);
  }


  /**
   * Convert the spelling of a word from British to American English.
   * This is deterministic spelling conversion, and so cannot deal with
   * certain cases involving complex ambiguities, but it can do most of the
   * simple cases of English to American conversion.
   *
   * @param str The String to be Americanized
   * @param capitalizeTimex Whether to capitalize time expressions like month names in return value
   * @return The American spelling of the word.
   */
  public static String americanize(String str, boolean capitalizeTimex) {
    // System.err.println("str is |" + str + "|");
    // System.err.println("timexMapping.contains is " +
    //            timexMapping.containsKey(str));
    // No ver short words are changed, so short circuit them
    int length = str.length();
    if (length < MINIMUM_LENGTH_CHANGED) {
      return str;
    }
    String result;
    if (capitalizeTimex) {
      result = timexMapping.get(str);
      if (result != null) {
        return result;
      }
    }
    result = mapping.get(str);
    if (result != null) {
      return result;
    }

    if (length < MINIMUM_LENGTH_PATTERN_MATCH) {
      return str;
    }
    // first do one disjunctive regex and return unless matches. Faster!
    // (But still allocates matcher each time; avoiding this would make this class not threadsafe....)
    if ( ! disjunctivePattern.matcher(str).find()) {
      return str;
    }
    for (int i = 0; i < pats.length; i++) {
      Matcher m = pats[i].matcher(str);
      if (m.find()) {
        Pattern ex = excepts[i];
        if (ex != null) {
          Matcher me = ex.matcher(str);
          if (me.find()) {
            continue;
          }
        }
        // System.err.println("Replacing " + word + " with " +
        //             pats[i].matcher(word).replaceAll(reps[i]));
        return m.replaceAll(reps[i]);
      }
    }
    return str;
  }

  private static final String[] patStrings = { "haem(at)?o", "aemia$", "([lL])eukaem",
          "programme(s?)$", "^([a-z]{3,})our(s?)$",

  };

  private static final Pattern[] pats = new Pattern[patStrings.length];

  private static final Pattern disjunctivePattern;

  static {
    StringBuilder foo = new StringBuilder();
    for (int i = 0, len = pats.length; i < len; i++) {
      pats[i] = Pattern.compile(patStrings[i]);
      if (i > 0) {
        foo.append('|');
      }
      foo.append("(?:");
      // Remove groups from String before appending for speed
      foo.append(patStrings[i].replaceAll("[()]", ""));
      foo.append(')');
    }
    disjunctivePattern = Pattern.compile(foo.toString());
  }

  private static final String[] OUR_EXCEPTIONS = {
    "abatjour", "beflour", "bonjour",
    "calambour", "carrefour", "cornflour", "contour",
    "de[tv]our", "dortour", "dyvour", "downpour",
    "giaour", "glamour", "holour", "inpour", "outpour",
    "pandour", "paramour", "pompadour", "recontour", "repour", "ryeflour",
    "sompnour",
    "tambour", "troubadour", "tregetour", "velour"
  };

  private static final Pattern[] excepts = {
    null, null, null, null,
    Pattern.compile(StringUtils.join(OUR_EXCEPTIONS, "|"))
  };

  private static final String[] reps = {
    "hem$1o", "emia", "$1eukem", "program$1", "$1or$2"
  };


  /** Do some normalization and British -> American mapping!
   *  Notes:
   *  <ul>
   *  <li>in PTB, you get dialogue not dialog, 17 times to 1.
   *  <li>We don't in general deal with capitalized words, only a couple of cases like Labour, Defence for the department.
   *  </ul>
   */
  private static final String[] converters = {"anaesthetic", "analogue", "analogues", "analyse", "analysed", "analysing", /* not analyses NNS */
                                                          "armoured", "cancelled", "cancelling", "candour", "capitalise", "capitalised", "capitalisation", "centre", "chimaeric", "clamour", "coloured", "colouring", "colourful", "defence", "Defence", /* "dialogue", "dialogues", */ "discolour", "discolours", "discoloured", "discolouring", "encyclopaedia", "endeavour", "endeavours", "endeavoured", "endeavouring", "fervour", "favour", "favours", "favoured", "favouring", "favourite", "favourites", "fibre", "fibres", "finalise", "finalised", "finalising", "flavour", "flavours", "flavoured", "flavouring", "glamour", "grey", "harbour", "harbours", "homologue", "homologues", "honour", "honours", "honoured", "honouring", "honourable", "humour", "humours", "humoured", "humouring", "kerb", "labelled", "labelling", "labour", "Labour", "labours", "laboured", "labouring", "leant", "learnt", "localise", "localised", "manoeuvre", "manoeuvres", "maximise", "maximised", "maximising", "meagre", "minimise", "minimised", "minimising", "modernise", "modernised", "modernising", "misdemeanour", "misdemeanours", "neighbour", "neighbours", "neighbourhood", "neighbourhoods", "oestrogen", "oestrogens", "organisation", "organisations", "penalise", "penalised", "popularise", "popularised", "popularises", "popularising", "practise", "practised", "pressurise", "pressurised", "pressurises", "pressurising", "realise", "realised", "realising", "realises", "recognise", "recognised", "recognising", "recognises", "rumoured", "rumouring", "savour", "savours", "savoured", "savouring", "splendour", "splendours", "theatre", "theatres", "titre", "titres", "travelled", "travelling" };

  private static final String[] converted = {"anesthetic", "analog", "analogs", "analyze", "analyzed", "analyzing", "armored", "canceled", "canceling", "candor", "capitalize", "capitalized", "capitalization", "center", "chimeric", "clamor", "colored", "coloring", "colorful", "defense", "Defense", /* "dialog", "dialogs", */ "discolor", "discolors", "discolored", "discoloring", "encyclopedia", "endeavor", "endeavors", "endeavored", "endeavoring", "fervor", "favor", "favors", "favored", "favoring", "favorite", "favorites", "fiber", "fibers", "finalize", "finalized", "finalizing", "flavor", "flavors", "flavored", "flavoring", "glamour", "gray", "harbor", "harbors", "homolog", "homologs", "honor", "honors", "honored", "honoring", "honorable", "humor", "humors", "humored", "humoring", "curb", "labeled", "labeling", "labor", "Labor", "labors", "labored", "laboring", "leaned", "learned", "localize", "localized", "maneuver", "maneuvers", "maximize", "maximized", "maximizing", "meager", "minimize", "minimized", "minimizing", "modernize", "modernized", "modernizing", "misdemeanor", "misdemeanors", "neighbor", "neighbors", "neighborhood", "neighborhoods", "estrogen", "estrogens", "organization", "organizations", "penalize", "penalized", "popularize", "popularized", "popularizes", "popularizing", "practice", "practiced", "pressurize", "pressurized", "pressurizes", "pressurizing", "realize", "realized", "realizing", "realizes", "recognize", "recognized", "recognizing", "recognizes", "rumored", "rumoring", "savor", "savors", "savored", "savoring", "splendor", "splendors", "theater", "theaters", "titer", "titers", "traveled", "traveling" };

  private static final String[] timexConverters = {"january", "february", /* not "march" ! */
                                                               "april", /* Not "may"! */ "june", "july", "august", "september", "october", "november", "december", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};

  private static final String[] timexConverted = {"January", "February", /* not "march" ! */
                                                              "April", /* Not "may"! */ "June", "July", "August", "September", "October", "November", "December", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};

  private static final Map<String,String> mapping = Generics.newHashMap();

  private static final Map<String,String> timexMapping = Generics.newHashMap();


  // static initialization block
  static {
    if (converters.length != converted.length || timexConverters.length != timexConverted.length || pats.length != reps.length || pats.length != excepts.length) {
      throw new RuntimeException("Americanize: Bad initialization data");
    }
    for (int i = 0; i < converters.length; i++) {
      mapping.put(converters[i], converted[i]);
    }
    for (int i = 0; i < timexConverters.length; i++) {
      timexMapping.put(timexConverters[i], timexConverted[i]);
    }
  }


  @Override
  public String toString() {
    return ("Americanize[capitalizeTimex is " + capitalizeTimex +
            "; " + "mapping has " + mapping.size() + " mappings; " +
            "timexMapping has " + timexMapping.size() + " mappings]");
  }


  /**
   * Americanize and print the command line arguments.
   * This main method is just for debugging.
   *
   * @param args Command line arguments: a list of words
   */
  public static void main(String[] args) throws IOException {
    System.err.println(new Americanize());
    System.err.println();

    if (args.length == 0) { // stdin -> stdout:
      BufferedReader buf = new BufferedReader(new InputStreamReader(System.in));
      String line;
      while((line = buf.readLine()) != null) {
        for(String w : line.split("\\s+")) {
          System.out.print(Americanize.americanize(w));
          System.out.print(' ');
        }
        System.out.println();
      }
      buf.close();
    }

    for (String arg : args) {
      System.out.print(arg);
      System.out.print(" --> ");
      System.out.println(americanize(arg));
    }
  }

}
