package edu.stanford.nlp.pipeline; 

import edu.stanford.nlp.coref.CorefCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.LexerUtils;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.Timing;
import edu.stanford.nlp.util.logging.Redwood;

import java.util.*;
import java.util.Properties;
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
 * <p>
 * Considers regular ascii ("", '', ``'', and `') as well as "smart" and
 * international quotation marks as follows:
 * “”,‘’, «», ‹›, 「」, 『』, „”, and ‚’.
 *
 * <p>
 * Note: extracts everything within these pairs as a whole quote segment, which may or may
 * not be the desired behaviour for texts that use different formatting styles than
 * standard english ones.
 *
 * <p>
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
 *   <li>extractUnclosedQuotes: "true" or "false" indicating whether or not to extract unclosed
 *   quotes. If "true", an UnclosedQuotationsAnnotation that is structured exactly the same as
 *   the QuotationsAnnotation will be added to the document. Any nested unclosed quotations will be
 *   contained in nested UnclosedQuotationsAnnotation on the target unclosed quotation
 *   (default=false).</li>
 * </ul>
 *
 * The annotator adds a QuotationsAnnotation to the Annotation
 * which returns a List&lt;CoreMap&gt; that
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
  private static final Redwood.RedwoodChannels log = Redwood.channels(QuoteAnnotator.class);

  private final boolean VERBOSE;

  // whether or not to consider single single quotes as quote-marking
  public boolean USE_SINGLE = false;
  // max length to consider for quotes
  public int MAX_LENGTH = -1;
  // whether to convert unicode quotes to non-unicode " and '
  // before processing
  public boolean ASCII_QUOTES = false;
  // Whether or not to allow quotes of the same type embedded inside of each other
  public boolean ALLOW_EMBEDDED_SAME = false;

  // Whether or not to allow quotes of the same type embedded inside of each other
  public boolean SMART_QUOTES = false;

  // Whether or not to extract unclosed quotes
  public boolean EXTRACT_UNCLOSED = false;

  // Whether or not to perform quote attribution
  public boolean ATTRIBUTE_QUOTES = true;

  // A quote attribution annotator this annotator may use
  public QuoteAttributionAnnotator quoteAttributionAnnotator;

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
   *  @param name String that is ignored but allows for creation of the
   *           QuoteAnnotator via a customAnnotatorClass
   *
   *  @param  props Properties object that contains the customizable properties
   *                 attributes.
   */
  public QuoteAnnotator(String name, Properties props) {
    this(name, props, false);
  }

  /** Return a QuoteAnnotator that isolates quotes denoted by the
   * ASCII characters " and ' as well as a variety of smart and international quotes.
   * If an unclosed quote appears, by default, this quote will not be counted as a quote.
   *
   *  @param  props Properties object that contains the customizable properties
   *                 attributes.
   */
  public QuoteAnnotator(Properties props) {
    this("quote", props, false);
  }

  /** Return a QuoteAnnotator that isolates quotes denoted by the
   * ASCII characters " and '. If an unclosed quote appears, by default,
   * this quote will not be counted as a quote.
   *
   *  @param props Properties object that contains the customizable properties
   *                 attributes.
   *  @param verbose whether or not to output verbose information.
   */
  public QuoteAnnotator(String name, Properties props, boolean verbose) {
    USE_SINGLE = Boolean.parseBoolean(props.getProperty(name + "." + "singleQuotes", "false"));
    MAX_LENGTH = Integer.parseInt(props.getProperty(name + "." + "maxLength", "-1"));
    ASCII_QUOTES = Boolean.parseBoolean(props.getProperty(name + "." + "asciiQuotes", "false"));
    ALLOW_EMBEDDED_SAME = Boolean.parseBoolean(props.getProperty(name + "." + "allowEmbeddedSame", "false"));
    SMART_QUOTES = Boolean.parseBoolean(props.getProperty(name + "." + "smartQuotes", "false"));
    EXTRACT_UNCLOSED = Boolean.parseBoolean(props.getProperty(name + "." + "extractUnclosedQuotes", "false"));
    ATTRIBUTE_QUOTES = Boolean.parseBoolean(props.getProperty(name + "." + "attributeQuotes", "true"));

    VERBOSE = verbose;
    Timing timer = null;
    if (VERBOSE) {
      timer = new Timing();
      log.info("Preparing quote annotator...");
    }
    if (ATTRIBUTE_QUOTES)  {
      Properties relevantProperties = PropertiesUtils.extractPrefixedProperties(props,
        "quote.attribution.");
      quoteAttributionAnnotator = new QuoteAttributionAnnotator(relevantProperties);
    }
    if (VERBOSE) {
      timer.stop("done.");
    }
  }

  /** helper method for creating version of document text without xml. **/
  public static String xmlFreeText(String documentText, Annotation annotation) {
    int firstTokenCharIndex =
        annotation.get(CoreAnnotations.TokensAnnotation.class).get(0).get(
            CoreAnnotations.CharacterOffsetBeginAnnotation.class);
    // add white space for all text before first token
    String cleanedText =
        documentText.substring(0,firstTokenCharIndex).replaceAll("\\S", " ");
    int tokenIndex = 0;
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    for (CoreLabel token : tokens) {
      // add the current token's text
      cleanedText += token.originalText();
      // add whitespace for non-tokens and xml in between these tokens
      tokenIndex += 1;
      if (tokenIndex < tokens.size()) {
        CoreLabel nextToken = tokens.get(tokenIndex);
        int inBetweenStart = token.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
        int inBetweenEnd = nextToken.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        String inBetweenTokenText = documentText.substring(inBetweenStart, inBetweenEnd);
        inBetweenTokenText = inBetweenTokenText.replaceAll("\\S", " ");
        cleanedText += inBetweenTokenText;
      }
    }
    // add white space for all non-token content after last token
    cleanedText += documentText.substring(cleanedText.length()).replaceAll("\\S", " ");
    return cleanedText;
  }

  @Override
  public void annotate(Annotation annotation) {
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);
    // clear out xml content from text
    text = xmlFreeText(text, annotation);

    // TODO: the following, if you want the quote annotator to get these truly correct
    // Pre-process to make word terminal apostrophes specially encoded (Jones' dog)
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);

    String quotesFrom = text;

    if (SMART_QUOTES) {
      // we're just going to try a bunch of different things and pick
      // whichever results in the most total quotes

      // try unicode
      Pair<List<Pair<Integer, Integer>>, List<Pair<Integer, Integer>>> overall = getQuotes(quotesFrom);
      String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
      List<CoreMap> cmQuotesUnicode =
          getCoreMapQuotes(overall.first(), tokens, sentences, text, docID, false);
      List<CoreMap> cmUnclosedUnicode = null;
      if (EXTRACT_UNCLOSED) {
        cmUnclosedUnicode = getCoreMapQuotes(overall.second(), tokens, sentences, text, docID, true);
      }
      int numUnicode = countQuotes(cmQuotesUnicode);

      // try ascii
      if (ASCII_QUOTES) {
        quotesFrom = replaceUnicode(text);
      }
      overall = getQuotes(quotesFrom);
      docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
      List<CoreMap> cmQuotesAscii = getCoreMapQuotes(overall.first(), tokens, sentences, text, docID, false);
      List<CoreMap> cmUnclosedAscii = null;
      if (EXTRACT_UNCLOSED) {
        cmUnclosedAscii = getCoreMapQuotes(overall.second(), tokens, sentences, text, docID, true);
      }
      int numAsciiSingle = countQuotes(cmQuotesAscii);

      // don't allow single quotes
      USE_SINGLE = false;
      overall = getQuotes(quotesFrom);
      docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
      List<CoreMap> cmQuotesAsciiNoSingle =
          getCoreMapQuotes(overall.first(), tokens, sentences, text, docID, false);
      List<CoreMap> cmUnclosedAsciiNoSingle = null;
      if (EXTRACT_UNCLOSED) {
        cmUnclosedAsciiNoSingle = getCoreMapQuotes(overall.second(), tokens, sentences, text, docID, true);
      }
      int numAsciiNoSingle = countQuotes(cmQuotesAsciiNoSingle);

      log.info("Number of quotes + unicode - single : " + numUnicode);
      log.info("Number of quotes + ascii - single : " + numAsciiNoSingle);
      log.info("Number of quotes + ascii + single : " + numAsciiSingle);
      if (numUnicode >= numAsciiNoSingle && numUnicode > (numAsciiSingle / 2)) {
        setAnnotations(annotation, cmQuotesUnicode, cmUnclosedUnicode, "Using unicode quotes.");
      } else if (numAsciiSingle > (numAsciiNoSingle / 2)) {
        setAnnotations(annotation, cmQuotesAscii, cmUnclosedAscii, "Using ascii quotes.");
      } else {
        setAnnotations(annotation, cmQuotesAsciiNoSingle,
            cmUnclosedAsciiNoSingle, "Using ascii quotes with no single quotes.");
      }
    } else {
      if (ASCII_QUOTES) {
        quotesFrom = replaceUnicode(text);
      }
      Pair<List<Pair<Integer, Integer>>, List<Pair<Integer, Integer>>> overall =
          getQuotes(quotesFrom);

      String docID = annotation.get(CoreAnnotations.DocIDAnnotation.class);
      List<CoreMap> cmQuotes = getCoreMapQuotes(overall.first(), tokens, sentences, text, docID, false);
      List<CoreMap> cmQuotesUnclosed = getCoreMapQuotes(overall.second(), tokens, sentences, text, docID, true);

      // add quotes to document
      setAnnotations(annotation, cmQuotes, cmQuotesUnclosed, "Setting quotes.");
    }
    // if quote attribution is activated, run the quoteAttributionAnnotator
    if (ATTRIBUTE_QUOTES)
      quoteAttributionAnnotator.annotate(annotation);
  }

  private void setAnnotations(Annotation annotation,
                              List<CoreMap> quotes,
                              List<CoreMap> unclosed,
                              String message) {
    annotation.set(CoreAnnotations.QuotationsAnnotation.class, quotes);
    log.info(message);
    if (EXTRACT_UNCLOSED) {
      annotation.set(CoreAnnotations.UnclosedQuotationsAnnotation.class, unclosed);
    }
  }

  //TODO: update this so that it goes more than 1 layer deep
  private static int countQuotes(List<CoreMap> quotes) {
    int total = quotes.size();
    for (CoreMap quote : quotes) {
      List<CoreMap> innerQuotes = quote.get(CoreAnnotations.QuotationsAnnotation.class);
      if (innerQuotes != null) {
        total += innerQuotes.size();
      }
    }
    return total;
  }


  public static String replaceUnicode(String text) {
    return LexerUtils.asciiQuotes(text);
  }

  public static Comparator<CoreMap> getQuoteComparator() {
   return (o1, o2) -> {
     int s1 = o1.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
     int s2 = o2.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
     return Integer.compare(s1, s2);
   };
  }

  public static List<CoreMap> getCoreMapQuotes(List<Pair<Integer, Integer>> quotes,
                                               List<CoreLabel> tokens,
                                               List<CoreMap> sentences,
                                               String text, String docID,
                                               boolean unclosed) {
    List<CoreMap> cmQuotes = Generics.newArrayList();
    for (Pair<Integer, Integer> p : quotes) {
      int begin = p.first();
      int end = p.second();

      // find the tokens for this quote
      List<CoreLabel> quoteTokens = new ArrayList<>();
      int tokenOffset = -1;
      if (tokens != null) {
        int currTok = 0;
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

      // add quote in and filter
      // filter: quoteTokens.size() != 0
      // filter: endSentence == -1
      if (quoteTokens.size() != 0 && endSentence > -1)
        cmQuotes.add(quote);
    }

    // sort quotes by beginning index
    Comparator<CoreMap> quoteComparator = getQuoteComparator();
    cmQuotes.sort(quoteComparator);

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
      if (!unclosed) {
        cmQuote.set(CoreAnnotations.QuotationsAnnotation.class, embeddedQuotes);
      } else {
        cmQuote.set(CoreAnnotations.UnclosedQuotationsAnnotation.class, embeddedQuotes);
      }
    }

    // Remove all the quotes that we want to.
    for (CoreMap r : toRemove) {
      // remove that quote from the overall list
      cmQuotes.remove(r);
    }

    // Set the quote index annotations properly
    setQuoteIndices(cmQuotes, unclosed);
    return cmQuotes;
  }

  private static void setQuoteIndices(List<CoreMap> topLevel, boolean unclosed) {
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
        List<CoreMap> key = quote.get(CoreAnnotations.QuotationsAnnotation.class);
        if (unclosed) {
          key = quote.get(CoreAnnotations.UnclosedQuotationsAnnotation.class);
        }
        if (key != null) {
          if (!unclosed) {
            nextLevel.addAll(quote.get(CoreAnnotations.QuotationsAnnotation.class));
          } else {
            nextLevel.addAll(quote.get(CoreAnnotations.UnclosedQuotationsAnnotation.class));
          }
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

  public Pair<List<Pair<Integer, Integer>>, List<Pair<Integer, Integer>>> getQuotes(String text) {
    return recursiveQuotes(text, 0, null);
  }

  public Pair<List<Pair<Integer, Integer>>, List<Pair<Integer, Integer>>>  recursiveQuotes(String text, int offset, String prevQuote) {
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
        if (c.equals("’")) {
          if ((i == text.length() - 1 || isSingleQuoteEnd(text, i))) {
            // check to make sure that this isn't an apostrophe..
            directed--;
          }
        } else {
          // closing
          directed--;
        }
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
        quotesMap.get(quote).add(new Pair<>(start, end));
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

    // TODO: determine if we want to be more strict w/ single quotes than double
    // answer: we do want to.
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
    List<Pair<Integer, Integer>> unclosedQuotes = Generics.newArrayList();
    // If I didn't find any quotes, but did find a quote-beginning, try again,
    // but without the part of the text before the single quote
    // really this test should be whether or not start is mapped to in quotesMap
    if (!isAQuoteMapStarter(start, quotesMap) && start >= 0 && start < text.length() - 3) {
      if (EXTRACT_UNCLOSED) {
        unclosedQuotes.add(new Pair<>(start, text.length()));
      }
      String toPass = text.substring(start + quote.length());
      Pair<List<Pair<Integer, Integer>>, List<Pair<Integer, Integer>>> embedded = recursiveQuotes(toPass, offset, null);
      // these are the good quotes
      for (Pair<Integer, Integer> e : embedded.first()) {
        // offset by the amount of text we trimmed: start+quote.length()
        quotes.add(new Pair<>(e.first() + start + quote.length(),
                              e.second() + start + quote.length()));
      }
      if (EXTRACT_UNCLOSED) {
        // these are the unclosed quotes
        for (Pair<Integer, Integer> e : embedded.second()) {
          unclosedQuotes.add(new Pair<>(e.first() + start + quote.length(),
                                        e.second() + start + quote.length()));
        }
      }
    }

    // Now take care of the good quotes that we found
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
          Pair<List<Pair<Integer, Integer>>, List<Pair<Integer, Integer>>> embedded =
              recursiveQuotes(toPass, q.first() + qKind.length() + offset, qKindToPass);
          // good quotes
          for (Pair<Integer, Integer> e : embedded.first()) {
            // don't add offset here because the
            // recursive method already added it
            if (e.second() - e.first() > 2) {
              quotes.add(new Pair<>(e.first(), e.second()));
            }
          }
          // unclosed quotes
          if (EXTRACT_UNCLOSED) {
            // these are the unclosed quotes
            for (Pair<Integer, Integer> e : embedded.second()) {
              unclosedQuotes.add(new Pair<>(e.first(), e.second()));
            }
          }
        }
        quotes.add(new Pair<>(q.first() + offset, q.second() + offset));
      }
    }

    return new Pair<>(quotes, unclosedQuotes);
  }

  private static boolean isAQuoteMapStarter(int target, Map<String, List<Pair<Integer, Integer>>> quotesMap) {
    for (String k : quotesMap.keySet()) {
      for (Pair<Integer, Integer> pair : quotesMap.get(k)) {
        if (pair.first() == target) {
          return true;
        }
      }
    }
    return false;
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
    if (i == text.length() - 2) {
      if (isWhitespaceOrPunct(next)) {
        return true;
      } else {
        return false;
      }
    }
    String nextNext = text.substring(i + 2, i + 3);
    return ((isWhitespaceOrPunct(next) && !isSingleQuote(next)) ||
            (isSingleQuote(next) && isWhitespaceOrPunct(nextNext)));
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
    // set base requirements
    Set<Class<? extends CoreAnnotation>> baseRequirements =
        new HashSet<>(Arrays.asList(
            CoreAnnotations.TextAnnotation.class,
            CoreAnnotations.TokensAnnotation.class,
            CoreAnnotations.SentencesAnnotation.class,
            CoreAnnotations.CharacterOffsetBeginAnnotation.class,
            CoreAnnotations.CharacterOffsetEndAnnotation.class,
            CoreAnnotations.IsNewlineAnnotation.class,
            CoreAnnotations.OriginalTextAnnotation.class
        ));
    // add extra quote attribution requirements if necessary
    if (ATTRIBUTE_QUOTES) {
      HashSet<Class<? extends CoreAnnotation>> attributionRequirements = new HashSet<>(Arrays.asList(
          CoreAnnotations.PartOfSpeechAnnotation.class,
          CoreAnnotations.NamedEntityTagAnnotation.class,
          CoreAnnotations.MentionsAnnotation.class,
          CoreAnnotations.TokenEndAnnotation.class,
          CoreAnnotations.IndexAnnotation.class,
          CoreAnnotations.TokenBeginAnnotation.class,
          CoreAnnotations.ValueAnnotation.class,
          CoreAnnotations.SentenceIndexAnnotation.class,
          CorefCoreAnnotations.CorefChainAnnotation.class,
          CoreAnnotations.MentionsAnnotation.class,
          CoreAnnotations.EntityMentionIndexAnnotation.class,
          CoreAnnotations.CanonicalEntityMentionIndexAnnotation.class
      ));
      baseRequirements.addAll(attributionRequirements);
    }
    return baseRequirements;
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    if (ATTRIBUTE_QUOTES) {
      return new HashSet<>(Arrays.asList(
          CoreAnnotations.QuotationsAnnotation.class,
          CoreAnnotations.QuotationIndexAnnotation.class,
          QuoteAttributionAnnotator.MentionAnnotation.class,
          QuoteAttributionAnnotator.MentionBeginAnnotation.class,
          QuoteAttributionAnnotator.MentionEndAnnotation.class,
          QuoteAttributionAnnotator.MentionTypeAnnotation.class,
          QuoteAttributionAnnotator.MentionSieveAnnotation.class,
          QuoteAttributionAnnotator.SpeakerAnnotation.class,
          QuoteAttributionAnnotator.SpeakerSieveAnnotation.class,
          CoreAnnotations.ParagraphIndexAnnotation.class
      ));
    } else {
      return Collections.singleton(CoreAnnotations.QuotationsAnnotation.class);
    }
  }


  /** Helper method to recursively gather all embedded quotes. */
  public static List<CoreMap> gatherQuotes(CoreMap curr) {
    List<CoreMap> embedded = curr.get(CoreAnnotations.QuotationsAnnotation.class);
    if (embedded != null) {
      List<CoreMap> extended = Generics.newArrayList();
      for (CoreMap quote : embedded) {
        extended.addAll(gatherQuotes(quote));
      }
      extended.addAll(embedded);
      // try sorting the quotes by beginIndex
      extended.sort(Comparator.comparingInt(cm -> cm.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class)));
      return extended;
    } else {
      return Generics.newArrayList();
    }
  }

}
