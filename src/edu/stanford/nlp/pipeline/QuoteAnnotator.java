package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Timing;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An annotator which picks quotations out of the given text. Allows
 * for embedded quotations so long as they are of a different type of
 * quote than the outer quotations (e.g. "'Gadzooks' is what he said to me"
 * is legal whereas "They called me "Danger" when I was..." is
 * illegal.) Uses regular-expression-like rules to find quotes and does not
 * depend on the tokenizer, which allows quotes like ''Tis true!' to be
 * correctly identified.
 *
 * Only considers " and ' characters presently (1/23/2015).
 *
 * @author Grace Muzny
 */
public class QuoteAnnotator implements Annotator {

  private final boolean VERBOSE;
  private final boolean DEBUG = false;

  //TODO: add directed quote/unicode quote understanding capabilities.
  // will need substantial logic, probably, as quotation mark conventions
  // vary widely.
  public static final Map<String, String> DIRECTED_QUOTES;
  static {
    Map<String, String> tmp = new HashMap<>();
    tmp.put("“", "”");  // directed double inward
    tmp.put("‘", "’");  // directed single inward
    tmp.put("«", "»");  // guillemets
    tmp.put("‹","›");  // single guillemets
    tmp.put("「", "」");  // cjk brackets
    tmp.put("『", "』");  // cjk brackets
    tmp.put("„","”");  // directed double down/up left pointing
    tmp.put("‚","’");  // directed single down/up left pointing
    tmp.put("``","''");  // directed double latex style
    tmp.put("`","'");  // directed single latex style
    DIRECTED_QUOTES = Collections.unmodifiableMap(tmp);
  }
  public static final String[] QUOTES = {"\"", "'", "’"};

  // TODO: implement this
  public final boolean closeUnclosedQuotes = false;

  /** Return a QuoteAnnotator that isolates quotes denoted by the
   * ASCII characters " and '. If an unclosed quote appears, by default,
   * this quote will not be counted as a quote.
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
    VERBOSE = verbose;
    Timing timer = null;
    if (VERBOSE) {
      timer = new Timing();
      System.err.print("Preparing quote annotator...");
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

    List<Pair<Integer, Integer>> overall = getQuotes(text);

    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);

    List<CoreMap> cmQuotes = getCoreMapQuotes(overall, tokens, text, docID);

    // add quotes to document
    annotation.set(CoreAnnotations.QuotationsAnnotation.class, cmQuotes);

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
                                              String text, String docID) {
    List<CoreMap> cmQuotes = new ArrayList<>();
    int tokenOffset = 0;
    int currTok = 0;
    for (Pair<Integer, Integer> p : quotes) {
      int begin = p.first();
      int end = p.second();

      // find the tokens for this quote
      List<CoreLabel> quoteTokens = new ArrayList<>();
      if (tokens != null) {
        while (currTok < tokens.size() && tokens.get(currTok).beginPosition() < begin) {
          currTok++;
        }
        int i = currTok;
        while (i < tokens.size() && tokens.get(i).endPosition() <= end) {
          quoteTokens.add(tokens.get(i));
          i++;
        }
      }

      // create a quote annotation with text and token offsets
      int currQuoteSize = cmQuotes.size();
      Annotation quote = makeQuote(text, begin, end, quoteTokens,
          currQuoteSize, tokenOffset, docID);
      tokenOffset += quoteTokens.size();

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
    return cmQuotes;
  }

  public static Annotation makeQuote(String text, int begin, int end,
                                     List<CoreLabel> quoteTokens,
                                     int currQuoteSize, int tokenOffset,
                                     String docID) {
    // create a quote annotation with text and token offsets
    Annotation quote = new Annotation(text.substring(begin, end));
    quote.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
    quote.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
    if (docID != null) {
      quote.set(CoreAnnotations.DocIDAnnotation.class, docID);
    }

    if (quoteTokens != null) {
      quote.set(CoreAnnotations.TokensAnnotation.class, quoteTokens);
      quote.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
      quote.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset + quoteTokens.size());
    }
    quote.set(CoreAnnotations.SentenceIndexAnnotation.class, currQuoteSize);

    if (quoteTokens != null) {
      int index = 1;
      for (CoreLabel token : quoteTokens) {
        token.setIndex(index++);
        token.setSentIndex(currQuoteSize);
        if (docID != null) {
          token.setDocID(docID);
        }
      }
    }
    return quote;
  }

  public static List<Pair<Integer, Integer>> getQuotes(String text) {
    return recursiveQuotes(text, 0, null);
  }

  public static List<Pair<Integer, Integer>> recursiveQuotes(String text, int offset, String prevQuote) {
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
      } else if (c.equals("'") && (quote != null && quote.equals("``")) &&
          i < text.length() - 1 &&
          text.charAt(i + 1) == '\'') {
        c += text.charAt(i + 1);
      }

      if (DIRECTED_QUOTES.containsKey(quote)) {
        if (DIRECTED_QUOTES.get(quote).equals(c)) {
          // closing
          directed--;
        }
      }

      // opening
      if ((start < 0) && !matchesPrevQuote(c, prevQuote) &&
          ((c.equals("'") && isSingleQuoteStart(text, i)) ||
            (c.equals("\"") || DIRECTED_QUOTES.containsKey(c)))) {
        start = i;
        quote = c;
        // closing
      } else if ((start >= 0 && end < 0) &&
          ((c.equals(quote) &&
          ((c.equals("'") && isSingleQuoteEnd(text, i)) ||
           (c.equals("\"") && isDoubleQuoteEnd(text, i)))) ||
           (DIRECTED_QUOTES.containsKey(quote) &&
               DIRECTED_QUOTES.get(quote).equals(c) &&
           directed == 0))) {
        end = i + c.length();
      }

      if (DIRECTED_QUOTES.containsKey(quote)) {
        if (DIRECTED_QUOTES.containsKey(c) &&
            c.equals(quote)) {
          // opening of this kind of directed quote
          directed++;
        }
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
    if (start >= 0) {
      String warning = text;
      if (text.length() > 150) {
        warning = text.substring(0, 150) + "...";
      }
      System.err.println("WARNING: unmatched quote of type " +
          quote + " found at index " + start + " in text segment: " + warning);
    }

    // recursively look for embedded quotes in these ones
    List<Pair<Integer, Integer>> quotes = new ArrayList<>();
    // If I didn't find any quotes, but did find a quote-beginning, try again,
    // but without the part of the text before the single quote
    if (quotesMap.isEmpty() && start >= 0) {
      String toPass = text.substring(start + quote.length(), text.length());//  - (quote.length() - 1));
      List<Pair<Integer, Integer>> embedded = recursiveQuotes(toPass, offset, null);
      for (Pair<Integer, Integer> e : embedded) {
        quotes.add(new Pair(e.first() + offset + start + quote.length(),
            e.second() + offset + start + 1));
      }
    } else {
      for (String qKind : quotesMap.keySet()) {
        for (Pair<Integer, Integer> q : quotesMap.get(qKind)) {
          if (q.first() < q.second() - qKind.length() * 2) {
            String toPass = text.substring(q.first() + qKind.length(),
                q.second() - qKind.length());
            String qKindToPass = DIRECTED_QUOTES.containsKey(qKind) ? null : qKind;
            List<Pair<Integer, Integer>> embedded = recursiveQuotes(toPass,
                q.first() + qKind.length() + offset, qKindToPass);
            for (Pair<Integer, Integer> e : embedded) {
              // don't add offset here because the
              // recursive method already added it
              quotes.add(new Pair(e.first(), e.second()));
            }
          }
          quotes.add(new Pair(q.first() + offset, q.second() + offset));
        }
      }
    }

    return quotes;
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
    return c.matches("[\\s\\p{Punct}]");
  }

  public static boolean isSingleQuote(String c) {
    return c.matches("[']");
  }

  @Override
  public Set<Requirement> requires() {
    return Collections.emptySet();
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(QUOTE_REQUIREMENT);
  }

}
