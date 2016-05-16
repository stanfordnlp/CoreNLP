package edu.stanford.nlp.pipeline; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An annotator which picks quotations out of the given text. Allows
 * for embedded quotations so long as they are either directed unicode quotes or are
 * of a different type of quote than the outer quotations
 * (e.g. "'Gadzooks' is what he said to me" is legal whereas
 * "They called me "Danger" when I was..." is illegal).
 * Uses regular-expression-like rules to find quotes and does not
 * depend on the tokenizer, which allows quotes like ''Tis true!' to be
 * correctly identified.
 *
 * Considers regular ascii ("", '', ``'', and `') as well as "smart" and
 * international quotation marks as follows:
 * “”,‘’, «», ‹›, 「」, 『』, „”, and ‚’.
 *
 * Note: extracts everything within these pairs as a whole quote segment, which may or may
 * not be the desired behaviour for texts that use different formatting styles than
 * standard english ones.
 *
 * There are a number of options that can be passed to the quote annotator to
 * customize its' behaviour:
 * <ul>
 *   <li>singleQuotes: "true" or "false", indicating whether or not to consider ' tokens
 *    to be quotation marks (default=false).</li>
 *   <li>maxLength: maximum character length of quotes to consider (default=-1).</li>
 *   <li>asciiQuotes: "true" or "false", indicating whether or not to convert all quotes
 *   to ascii quotes before processing (can help when there are errors in quote directionality)
 *   (default=false).</li>
 *   <li>allowEmbeddedSame: "true" or "false" indicating whether or not to allow smart/directed
 *   (everything except " and ') quotes of the same kind to be embedded within one another
 *   (default=false).</li>
 * </ul>
 *
 * The annotator adds a QuotationsAnnotation to the Annotation
 * which returns a List<CoreMap> that
 * contain the following information:
 * <ul>
 *  <li>CharacterOffsetBeginAnnotation</li>
 *  <li>CharacterOffsetEndAnnotation</li>
 *  <li>QuotationIndexAnnotation</li>
 *  <li>QuotationsAnnotation (if there are embedded quotes)</li>
 *  <li>TokensAnnotation (if the tokenizer is run before the quote annotator)</li>
 *  <li>TokenBeginAnnotation (if the tokenizer is run before the quote annotator)</li>
 *  <li>TokenEndAnnotation (if the tokenizer is run before the quote annotator)</li>
 *  <li>SentenceBeginAnnotation (if the sentence splitter has bee run before the quote annotator)</li>
 *  <li>SentenceEndAnnotation (if the sentence splitter has bee run before the quote annotator)</li>
 * </ul>
 *
 * @author Grace Muzny
 */
public class QuoteAnnotator implements Annotator  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(QuoteAnnotator.class);

  private final boolean VERBOSE;
  private final boolean DEBUG = false;

  // whether or not to consider single single quotes as quote-marking
  public boolean USE_SINGLE = false;
  // max length to consider for quotes
  public int MAX_LENGTH = -1;
  // whether to convert unicode quotes to non-unicode " and '
  // before processing
  public boolean ASCII_QUOTES = false;
  // Whether or not to allow quotes of the same type embedded inside of each other
  public boolean ALLOW_EMBEDDED_SAME = false;

  // TODO: implement this
//  public boolean closeUnclosedQuotes = false;
  //TODO: add directed quote/unicode quote understanding capabilities.
  // will need substantial logic, probably, as quotation mark conventions
  // vary widely.
  public static final Map<String, String> DIRECTED_QUOTES;
  static {
    Map<String, String> tmp = Generics.newHashMap();
    tmp.put("“", "”");  // directed double inward
    tmp.put("‘", "’");  // directed single inward
    tmp.put("«", "»");  // guillemets
    tmp.put("‹","›");  // single guillemets
    tmp.put("「", "」");  // cjk brackets
    tmp.put("『", "』");  // cjk brackets
    tmp.put("„","”");  // directed double down/up left pointing
    tmp.put("‚","’");  // directed single down/up left pointing
    tmp.put("``","''");  // double latex -- single latex quotes don't belong here!
    DIRECTED_QUOTES = Collections.unmodifiableMap(tmp);
  }

  /** Return a QuoteAnnotator that isolates quotes denoted by the
   * ASCII characters " and '. If an unclosed quote appears, by default,
   * this quote will not be counted as a quote.
   *
   *  @param s String that is ignored but allows for creation of the
   *           QuoteAnnotator via a customAnnotatorClass
   *
   *  @param  props Properties object that contains the customizable properties
   *                 attributes.
   *  @return A QuoteAnnotator.
   */
  public QuoteAnnotator(String s, Properties props) {
    this(props, false);
  }

  /** Return a QuoteAnnotator that isolates quotes denoted by the
   * ASCII characters " and ' as well as a variety of smart and international quotes.
   * If an unclosed quote appears, by default, this quote will not be counted as a quote.
   *
   *  @param  props Properties object that contains the customizable properties
   *                 attributes.
   *  @return A QuoteAnnotator.
   */
  public QuoteAnnotator(Properties props) {
    this(props, false);
  }

  /** Return a QuoteAnnotator that isolates quotes denoted by the
   * ASCII characters " and '. If an unclosed quote appears, by default,
   * this quote will not be counted as a quote.
   *
   *  @param props Properties object that contains the customizable properties
   *                 attributes.
   *  @param verbose whether or not to output verbose information.
   *  @return A QuoteAnnotator.
   */
  public QuoteAnnotator(Properties props, boolean verbose) {
    USE_SINGLE = Boolean.parseBoolean(props.getProperty("singleQuotes", "false"));
    MAX_LENGTH = Integer.parseInt(props.getProperty("maxLength", "-1"));
    ASCII_QUOTES = Boolean.parseBoolean(props.getProperty("asciiQuotes", "false"));
    ALLOW_EMBEDDED_SAME = Boolean.parseBoolean(props.getProperty("allowEmbeddedSame", "false"));

    VERBOSE = verbose;
    Timing timer = null;
    if (VERBOSE) {
      timer = new Timing();
      log.info("Preparing quote annotator...");
    }

    if (VERBOSE) {
      timer.stop("done.");
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);

    // TODO: the following, if you want the quote annotator to get these truly correct
    // Pre-process to make word terminal apostrophes specially encoded (Jones' dog)
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);


    String quotesFrom = text;
    if (ASCII_QUOTES) {
      quotesFrom = replaceUnicode(text);
    }
    List<Pair<Integer, Integer>> overall = getQuotes(quotesFrom);

    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);

    List<CoreMap> cmQuotes = getCoreMapQuotes(overall, tokens, sentences, text, docID);

    // add quotes to document
    annotation.set(CoreAnnotations.QuotationsAnnotation.class, cmQuotes);

  }

  // Stolen from PTBLexer
  private static final Pattern asciiSingleQuote = Pattern.compile("&apos;|[\u0091\u2018\u0092\u2019\u201A\u201B\u2039\u203A']");
  private static final Pattern asciiDoubleQuote = Pattern.compile("&quot;|[\u0093\u201C\u0094\u201D\u201E\u00AB\u00BB\"]");

  private static String asciiQuotes(String in) {
    String s1 = in;
    s1 = asciiSingleQuote.matcher(s1).replaceAll("'");
    s1 = asciiDoubleQuote.matcher(s1).replaceAll("\"");
    return s1;
  }

  public static String replaceUnicode(String text) {
    return asciiQuotes(text);
  }

  public static Comparator<CoreMap> getQuoteComparator() {
   return new Comparator<CoreMap>() {
     @Override
     public int compare(CoreMap o1, CoreMap o2) {
       int s1 = o1.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
       int s2 = o2.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
       return s1 - s2;
     }
   };
  }

  public static List<CoreMap> getCoreMapQuotes(List<Pair<Integer, Integer>> quotes,
                                               List<CoreLabel> tokens,
                                               List<CoreMap> sentences,
                                              String text, String docID) {
    List<CoreMap> cmQuotes = Generics.newArrayList();
    for (Pair<Integer, Integer> p : quotes) {
      int begin = p.first();
      int end = p.second();

      // find the tokens for this quote
      List<CoreLabel> quoteTokens = new ArrayList<>();
      int tokenOffset = -1;
      int currTok = 0;
      if (tokens != null) {
        while (currTok < tokens.size() && tokens.get(currTok).beginPosition() < begin) {
          currTok++;
        }
        int i = currTok;
        tokenOffset = i;
        while (i < tokens.size() && tokens.get(i).endPosition() <= end) {
          quoteTokens.add(tokens.get(i));
          i++;
        }
      }

      // find the sentences for this quote
      int beginSentence = -1;
      int endSentence = -1;
      if (sentences != null) {
        for (CoreMap sentence : sentences) {
          int sentBegin = sentence.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
          int sentEnd = sentence.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
          int sentIndex = sentence.get(CoreAnnotations.SentenceIndexAnnotation.class);
          if (sentBegin <= begin) {
            beginSentence = sentIndex;
          }
          if (sentEnd >= end && endSentence < 0) {
            endSentence = sentIndex;
          }
        }
      }

      // create a quote annotation with text and token offsets
      Annotation quote = makeQuote(text.substring(begin, end), begin, end, quoteTokens,
          tokenOffset, beginSentence, endSentence, docID);

      // add quote in
      cmQuotes.add(quote);
    }

    // sort quotes by beginning index
    Comparator<CoreMap> quoteComparator = getQuoteComparator();
    Collections.sort(cmQuotes, quoteComparator);

    // embed quotes
    List<CoreMap> toRemove = new ArrayList<>();
    for (CoreMap cmQuote : cmQuotes) {
      int start = cmQuote.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int end = cmQuote.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      // See if we need to embed a quote
      List<CoreMap> embeddedQuotes = new ArrayList<>();
      for (CoreMap cmQuoteComp : cmQuotes) {
        int startComp = cmQuoteComp.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        int endComp = cmQuoteComp.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        if (start < startComp && end >= endComp) {
          // p contains comp
          embeddedQuotes.add(cmQuoteComp);
          // now we want to remove it from the top-level quote list
          toRemove.add(cmQuoteComp);
        }
      }
      cmQuote.set(CoreAnnotations.QuotationsAnnotation.class, embeddedQuotes);
    }

    // Remove all the quotes that we want to.
    for (CoreMap r : toRemove) {
      // remove that quote from the overall list
      cmQuotes.remove(r);
    }

    // Set the quote index annotations properly
    setQuoteIndices(cmQuotes);
    return cmQuotes;
  }

  private static void setQuoteIndices(List<CoreMap> topLevel) {
    List<CoreMap> level = topLevel;
    int index = 0;
    while (!level.isEmpty()) {
      List<CoreMap> nextLevel = Generics.newArrayList();
      for (CoreMap quote : level) {
        quote.set(CoreAnnotations.QuotationIndexAnnotation.class, index);
        List<CoreLabel> quoteTokens = quote.get(CoreAnnotations.TokensAnnotation.class);
        if (quoteTokens != null) {
          for (CoreLabel qt : quoteTokens) {
            qt.set(CoreAnnotations.QuotationIndexAnnotation.class, index);
          }
        }
        index++;
        if (quote.get(CoreAnnotations.QuotationsAnnotation.class) != null) {
          nextLevel.addAll(quote.get(CoreAnnotations.QuotationsAnnotation.class));
        }
      }
      level = nextLevel;
    }
  }

  public static Annotation makeQuote(String surfaceForm, int begin, int end,
                                     List<CoreLabel> quoteTokens,
                                     int tokenOffset,
                                     int sentenceBeginIndex,
                                     int sentenceEndIndex,
                                     String docID) {
    Annotation quote = new Annotation(surfaceForm);
    // create a quote annotation with text and token offsets
    quote.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
    quote.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
    if (docID != null) {
      quote.set(CoreAnnotations.DocIDAnnotation.class, docID);
    }

    if (quoteTokens != null) {
      quote.set(CoreAnnotations.TokensAnnotation.class, quoteTokens);
      quote.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
      quote.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset + quoteTokens.size() - 1);
    }
    quote.set(CoreAnnotations.SentenceBeginAnnotation.class, sentenceBeginIndex);
    quote.set(CoreAnnotations.SentenceEndAnnotation.class, sentenceEndIndex);

    return quote;
  }

  public List<Pair<Integer, Integer>> getQuotes(String text) {
    return recursiveQuotes(text, 0, null);
  }

  public List<Pair<Integer, Integer>> recursiveQuotes(String text, int offset, String prevQuote) {
    Map<String, List<Pair<Integer, Integer>>> quotesMap = new HashMap<>();
    int start = -1;
    int end = -1;
    String quote = null;
    int directed = 0;
    for (int i = 0 ; i < text.length(); i++) {
      // Either I'm not in any quote or this one matches
      // the kind that I am.
      String c = text.substring(i, i + 1);

      if (c.equals("`") && i < text.length() - 1 &&
          text.charAt(i + 1) == '`') {
        c += text.charAt(i + 1);
      } else if (c.equals("'") && (quote != null && (quote.equals("``") || quote.equals("`")))) {
        // we want to ignore it if unless is is the beginning of the
        // last set of ' of the proper length
        int curr = i;
        while (curr < text.length() && text.charAt(curr) == '\'') {
          curr++;
        }
        if (i == curr - quote.length() ||
            (directed > 0 && i == curr - (directed * quote.length()))) {
          for (int a = i + 1; a < i + quote.length(); a++) {
            c += text.charAt(a);
          }
        } else {
          continue;
        }
      }

      if (DIRECTED_QUOTES.containsKey(quote) &&
          DIRECTED_QUOTES.get(quote).equals(c)) {
          // closing
          directed--;
      }

      // opening
      if ((start < 0) && !matchesPrevQuote(c, prevQuote) &&
          (((isSingleQuoteWithUse(c) || c.equals("`")) && isSingleQuoteStart(text, i)) ||
            (c.equals("\"") || DIRECTED_QUOTES.containsKey(c)))) {
        start = i;
        quote = c;
      // closing
      } else if ((start >= 0 && end < 0) &&
          ((c.equals(quote) &&
           (((c.equals("'") || c.equals("`")) && isSingleQuoteEnd(text, i)) ||
            (c.equals("\"") && isDoubleQuoteEnd(text, i)))) ||
           (c.equals("'") && quote.equals("`") && isSingleQuoteEnd(text, i)) ||  // latex quotes are kind of problematic
           (DIRECTED_QUOTES.containsKey(quote) &&
               DIRECTED_QUOTES.get(quote).equals(c) &&
           directed == 0))) {
        end = i + c.length();
      }

      if (DIRECTED_QUOTES.containsKey(c) &&
          c.equals(quote)) {
        // opening of this kind of directed quote
        directed++;
      }

      if (start >= 0 && end > 0) {
        if (!quotesMap.containsKey(quote)) {
          quotesMap.put(quote, new ArrayList<>());
        }
        quotesMap.get(quote).add(new Pair(start, end));
        start = -1;
        end = -1;
        quote = null;
      }

      if (c.length() > 1) {
        i += c.length() - 1;
      }

      // forget about this quote
      if (MAX_LENGTH > 0 && start >= 0 &&
          i - start > MAX_LENGTH) {
        // go back to the right index after start
        i = start + quote.length();

        start = -1;
        end = -1;
        quote = null;
      }
    }

//    // TODO: determine if we want to be more strict w/ single quotes than double
//    // answer: we do want to.
//    // if we reached then end and we have an open quote, close it
//    if (closeUnclosedQuotes && start >= 0 && start < text.length() - 2) {
//      if (!quotesMap.containsKey(quote)) {
//        quotesMap.put(quote, new ArrayList<>());
//      }
//      quotesMap.get(quote).add(new Pair(start, text.length()));
//    } else
    if (start >= 0 && start < text.length() - 3) {
      String warning = text;
      if (text.length() > 150) {
        warning = text.substring(0, 150) + "...";
      }
      log.info("WARNING: unmatched quote of type " +
          quote + " found at index " + start + " in text segment: " + warning);
    }

    // recursively look for embedded quotes in these ones
    List<Pair<Integer, Integer>> quotes = Generics.newArrayList();
    // If I didn't find any quotes, but did find a quote-beginning, try again,
    // but without the part of the text before the single quote
    if (quotesMap.isEmpty() && start >= 0 && start < text.length() - 3) {
      String toPass = text.substring(start + quote.length(), text.length());
      List<Pair<Integer, Integer>> embedded = recursiveQuotes(toPass, offset, null);
      for (Pair<Integer, Integer> e : embedded) {
        quotes.add(new Pair(e.first() + start + quote.length(),
            e.second() + start + 1));
      }
    } else {
      for (String qKind : quotesMap.keySet()) {
        for (Pair<Integer, Integer> q : quotesMap.get(qKind)) {
          if (q.second() - q.first() >= qKind.length() * 2) {
            String toPass = text.substring(q.first() + qKind.length(),
                q.second() - qKind.length());
            String qKindToPass = null;
            if (!(DIRECTED_QUOTES.containsKey(qKind) || qKind.equals("`"))
                    || !ALLOW_EMBEDDED_SAME) {
              qKindToPass = qKind;
            }
            List<Pair<Integer, Integer>> embedded = recursiveQuotes(toPass,
                q.first() + qKind.length() + offset, qKindToPass);
            for (Pair<Integer, Integer> e : embedded) {
              // don't add offset here because the
              // recursive method already added it
              if (e.second() - e.first() > 2) {
                quotes.add(new Pair(e.first(), e.second()));
              }
            }
          }
          quotes.add(new Pair(q.first() + offset, q.second() + offset));
        }
      }
    }

    return quotes;
  }

  private boolean isSingleQuoteWithUse(String c) {
    return c.equals("'") && USE_SINGLE;
  }

  private static boolean matchesPrevQuote(String c, String prev) {
    return prev != null && prev.equals(c);
  }

  private static boolean isSingleQuoteStart(String text, int i) {
    if (i == 0) return true;
    String prev = text.substring(i - 1, i);
    return isWhitespaceOrPunct(prev);
  }

  private static boolean isSingleQuoteEnd(String text, int i) {
    if (i == text.length() - 1) return true;
    String next = text.substring(i + 1, i + 2);
    return isWhitespaceOrPunct(next);
  }

  private static boolean isDoubleQuoteEnd(String text, int i) {
    if (i == text.length() - 1) return true;
    String next = text.substring(i + 1, i + 2);
    if (i == text.length() - 2 && isWhitespaceOrPunct(next)) {
      return true;
    }
    String nextNext = text.substring(i + 2, i + 3);
    return ((isWhitespaceOrPunct(next) &&
           !isSingleQuote(next)) || (isSingleQuote(next) && isWhitespaceOrPunct(nextNext)));
  }

  public static boolean isWhitespaceOrPunct(String c) {
    Pattern punctOrWhite = Pattern.compile("[\\s\\p{Punct}]", Pattern.UNICODE_CHARACTER_CLASS);
    Matcher m = punctOrWhite.matcher(c);
    return m.matches();
  }

  public static boolean isSingleQuote(String c) {
    return c.equals("'");
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.EMPTY_SET;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return Collections.singleton(CoreAnnotations.QuotationsAnnotation.class);
  }

}
