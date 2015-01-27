package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.MultiTokenTag;
import edu.stanford.nlp.ling.tokensregex.SequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.process.ListProcessor;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
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

  public static final String PATTERN_SINGLE = "(^|\\s)(('(?:(?!'\\s).|\\n)+)(?='([\\s\\p{Punct}]|$))')";
  public static final String PATTERN_DOUBLE = "(?:(^|\\s))((\"(?:(?!\"\\s).|\\n)+)(?=\"([\\s\\p{Punct}]|$))\")";
  public static final int GROUP_NUM = 2;


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
    if (VERBOSE) {
      System.err.print("Adding Quote annotation...");
    }
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreMap> quotes = new ArrayList<CoreMap>();

    // TODO: the following
    // Pre-process to make word terminal apostrophes specially encoded (Jones' dog)
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
//    System.out.println(tokens);

    List<Pair<Integer, Integer>> singleQuotesQuotes = extractDirectSingleQuotes(text);
    List<Pair<Integer, Integer>> doubleQuotesQuotes = extractDirectDoubleQuotes(text);

    String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);

    addCoreMapQuotes(singleQuotesQuotes, quotes, text, docID);
    addCoreMapQuotes(doubleQuotesQuotes, quotes, text, docID);

    // add quotes to document
    annotation.set(CoreAnnotations.QuotationsAnnotation.class, quotes);


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


  public static void addCoreMapQuotes(List<Pair<Integer, Integer>> addThese,
                                      List<CoreMap> toThese,
                                      String text, String docID) {
    for (Pair<Integer, Integer> p : addThese) {
      // get the quote text from the first and last character offsets
      int begin = p.first();
      int end = p.second();
      String quoteText = text.substring(begin, end);

      // create a quote annotation with text and token offsets
      Annotation quote = new Annotation(quoteText);
      quote.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
      quote.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
      if (docID != null) {
        quote.set(CoreAnnotations.DocIDAnnotation.class, docID);
      }
      // add quote in
      toThese.add(quote);
    }
  }


  public static List<Pair<Integer, Integer>> extractDirectQuotes(String text, String pattern, int groupNum) {
    Pattern p = Pattern.compile(pattern);
    List<Pair<Integer, Integer>> quotes = new ArrayList<>();

    Matcher m = p.matcher(text);

    while(m.find()) {
      int start = m.start();
      int end = m.end();
//      System.out.println(m.groupCount());
//      System.out.println("1:" + m.group(1));
//      System.out.println("2:" + m.group(2));
//      System.out.println("3:" + m.group(3));
//      System.out.println(m.group());
      if (groupNum >= 0) {
        String q = m.group(groupNum);
        String whole = m.group();
        start = whole.indexOf(q) + m.start();
        end = start + q.length();
      }
      if (start != end) {
        quotes.add(new Pair(start, end));
      }
    }
    return quotes;
  }

  public static List<Pair<Integer, Integer>> extractDirectSingleQuotes(String text) {
    return extractDirectQuotes(text, PATTERN_SINGLE, GROUP_NUM);
  }

  public static List<Pair<Integer, Integer>> extractDirectDoubleQuotes(String text) {
//    return extractDirectQuotes(text, PATTERN_DOUBLE, GROUP_NUM);
    return doubleQuotes(text);
  }

  public static List<Pair<Integer, Integer>> doubleQuotes(String text) {
    List<Pair<Integer, Integer>> quotes = new ArrayList<>();

    int start = -1;
    int end = -1;
    for (int i = 0 ; i < text.length(); i++) {
      if (text.charAt(i) == '"') {
        // opening
        if (start < 0) {
          start = i;
        // closing
        } else if (end < 0 &&
            (i == text.length() - 1 ||
            isWhitespaceOrPunct(text.charAt(i + 1)))) {
          end = i + 1;
          System.out.println("Set: " + text.substring(start, end));
        }
      }
      if (start >= 0 && end > 0) {
        quotes.add(new Pair(start, end));
        start = -1;
        end = -1;
      }
    }
    return quotes;
  }

  public static boolean isWhitespaceOrPunct(char c) {
    String s = "" + c;
    return s.matches("[\\s\\p{Punct}]");
  }

  private static boolean isQuoteBeginner(CoreLabel token) {
    return token.word().equals("``") || token.word().equals("`");
  }

  private static boolean isAnyQuote(CoreLabel token) {
    return token.word().equals("``") || token.word().equals("''") || token.word().equals("'") || token.word().equals("`");
  }

  private static boolean isNewline(CoreLabel token) {
    //TODO replace with appropriate constant
    return token.word().equals("*NL*");
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
