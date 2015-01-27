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
 * @author Grace Muzny
 */
public class QuoteAnnotator implements Annotator {

  private final boolean VERBOSE;
  private final boolean DEBUG = false;

  public QuoteAnnotator() {
    this(true);
  }

  public QuoteAnnotator(boolean verbose) {
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
//    if (VERBOSE) {
//      System.err.print("Adding Quote annotation...");
//    }
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);

    // TODO: the following
    // Pre-process to make word terminal apostrophes specially encoded (Jones' dog)
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);

    List<Pair<Integer, Integer>> overall = getQuotes(text);

    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);

    List<CoreMap> cmQuotes = getCoreMapQuotes(overall, tokens, text, docID);

    // add quotes to document
    annotation.set(CoreAnnotations.QuotationsAnnotation.class, cmQuotes);


//    if (annotation.containsKey(CoreAnnotations.TokensAnnotation.class)) {
//      // get text and tokens from the document
//      String text = annotation.get(CoreAnnotations.TextAnnotation.class);
//      String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
//      List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
//      // System.err.println("Tokens are: " + tokens);
//
//
//      boolean inQuote = false;
//      boolean newline = false;
//      for (CoreLabel token: tokens) {
//        // both `` and '' can be the starts or the ends of quotes
//        if (isAnyQuote(token)) {
//          // If we weren't in a quote before we probably are now
//          if (!inQuote) {
//            inQuote = true;
//          }
//        }
//      }
//
//      if (VERBOSE) {
//        System.err.println("done. Output: " + tokens);
//      }
//
//      // assemble the quote annotations
//      int tokenOffset = 0;
//      int lineNumber = 0;
//      // section annotations to mark sentences with
//      CoreMap sectionAnnotations = null;
//      List<CoreMap> quotes = new ArrayList<CoreMap>();
//      List<List<CoreLabel>> quoteTokensOverall = process(tokens);
//      for (List<CoreLabel> quoteTokens: quoteTokensOverall) {
//        if (quoteTokens.isEmpty()) {
//          continue;
//        }
//
//        // get the quote text from the first and last character offsets
//        int begin = quoteTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
//        int last = quoteTokens.size() - 1;
//        int end = quoteTokens.get(last).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
//        String quoteText = text.substring(begin, end);
//
//        // create a quote annotation with text and token offsets
//        Annotation quote = new Annotation(quoteText);
//        quote.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
//        quote.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
//        quote.set(CoreAnnotations.TokensAnnotation.class, quoteTokens);
//        quote.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
//        tokenOffset += quoteTokens.size();
//        quote.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset);
//        quote.set(CoreAnnotations.SentenceIndexAnnotation.class, quotes.size());
//
//
//        // Annotate sentence with section information.
//        // Assume section start and end appear as first and last tokens of sentence
//        CoreLabel quoteStartToken = quoteTokens.get(0);
//        CoreLabel quoteEndToken = quoteTokens.get(quoteTokens.size() - 1);
//
//        CoreMap sectionStart = quoteStartToken.get(CoreAnnotations.SectionStartAnnotation.class);
//        if (sectionStart != null) {
//          // Section is started
//          sectionAnnotations = sectionStart;
//        }
//        if (sectionAnnotations != null) {
//          // transfer annotations over to quote
//          ChunkAnnotationUtils.copyUnsetAnnotations(sectionAnnotations, quote);
//        }
//        String sectionEnd = quoteEndToken.get(CoreAnnotations.SectionEndAnnotation.class);
//        if (sectionEnd != null) {
//          sectionAnnotations = null;
//        }
//
//        if (docID != null) {
//          quote.set(CoreAnnotations.DocIDAnnotation.class, docID);
//        }
//
//        int index = 1;
//        for (CoreLabel token : quoteTokens) {
//          token.setIndex(index++);
//          token.setSentIndex(quotes.size());
//          if (docID != null) {
//            token.setDocID(docID);
//          }
//        }
//
//        // add the sentence to the list
//        quotes.add(quote);
//      }
//
//      // add the quotations annotations to the document
//      annotation.set(CoreAnnotations.QuotationsAnnotation.class, quotes);
//    } else {
//      throw new RuntimeException("unable to find tokens in: " + annotation);
//    }
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
      while(currTok < tokens.size() && tokens.get(currTok).beginPosition() < begin) {
        currTok++;
      }
      int i = currTok;
      while(i < tokens.size() && tokens.get(i).endPosition() <= end) {
        quoteTokens.add(tokens.get(i));
        i++;
      }

      // create a quote annotation with text and token offsets
      int currQuoteSize = cmQuotes.size();
      Annotation quote = makeQuote(text, begin, end, quoteTokens,
          currQuoteSize, tokenOffset, docID);
      tokenOffset += quoteTokens.size();

      // add quote in
      cmQuotes.add(quote);
    }

    // embed quotes
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
        }
      }
      cmQuote.set(CoreAnnotations.QuotationsAnnotation.class, embeddedQuotes);
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

    quote.set(CoreAnnotations.TokensAnnotation.class, quoteTokens);
    quote.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
    quote.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset + quoteTokens.size());
    quote.set(CoreAnnotations.SentenceIndexAnnotation.class, currQuoteSize);

    int index = 1;
    for (CoreLabel token : quoteTokens) {
      token.setIndex(index++);
      token.setSentIndex(currQuoteSize);
      if (docID != null) {
        token.setDocID(docID);
      }
    }
    return quote;
  }

  // I'd like to try out a recursive method to see if that works!
  public static List<Pair<Integer, Integer>> getQuotes(String text) {
    return recursiveQuotes(text, 0, null);
  }

  // I'd like to try out a recursive method to see if that works!
  public static List<Pair<Integer, Integer>> recursiveQuotes(String text, int offset, String prevQuote) {
//    System.out.println("recurse: " + text);
    Map<String, List<Pair<Integer, Integer>>> quotesMap = new HashMap<>();
    int start = -1;
    int end = -1;
    String quote = null;
    for (int i = 0 ; i < text.length(); i++) {
      // Either I'm not in any quote or this one matches
      // the kind that I am.
      String c = text.substring(i, i + 1);
      // opening
      if ((start < 0) && !matchesPrevQuote(c, prevQuote) &&
          ((c.equals("'") && isSingleQuoteStart(text, i)) ||
            (c.equals("\"")))) {
        start = i;
        quote = text.substring(start, start + 1);
        // closing
      } else if (start >= 0 && end < 0 && c.equals(quote) &&
          ((c.equals("'") && isSingleQuoteEnd(text, i)) ||
           (c.equals("\"") && isDoubleQuoteEnd(text, i)))) {
        end = i + 1;
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
    }

    // TODO: determine if we want to be more strict w/ single quotes than double
    // if we reached then end and we have an open quote, and it isn't single
    // close it
    if (start >= 0 && start < text.length() - 2) {
      if (!quotesMap.containsKey(quote)) {
        quotesMap.put(quote, new ArrayList<>());
      }
      quotesMap.get(quote).add(new Pair(start, text.length()));
    } else if (start >= 0) {
      System.err.println("WARNING: unmatched single quote at end of file!");
    }

    // recursively look for embedded quotes in these ones
    List<Pair<Integer, Integer>> embedded = new ArrayList<>();
    List<Pair<Integer, Integer>> quotes = new ArrayList<>();
    for (String qKind : quotesMap.keySet()) {
      for (Pair<Integer, Integer> q : quotesMap.get(qKind)) {
        if (q.first() < q.second() - 2) {
          embedded = recursiveQuotes(text.substring(q.first() + 1, q.second() - 1), q.first() + 1 + offset, qKind);
        }
        quotes.add(new Pair(q.first() + offset, q.second() + offset));
      }
    }
    for (Pair<Integer, Integer> e : embedded) {
      quotes.add(new Pair(e.first() + offset, e.second() + offset));
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
    return (isWhitespaceOrPunct(next) &&
            !isSingleQuote(next));
  }

  public static boolean isWhitespaceOrPunct(String c) {
    return c.matches("[\\s\\p{Punct}]");
  }

  public static boolean isSingleQuote(String c) {
    return c.matches("[']");
  }

  @Override
  public Set<Requirement> requires() {
    //TODO: probably remove this
    return Collections.singleton(TOKENIZE_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(QUOTE_REQUIREMENT);
  }

}
