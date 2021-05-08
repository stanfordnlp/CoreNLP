package edu.stanford.nlp.process;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.MultiTokenTag;
import edu.stanford.nlp.ling.tokensregex.SequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.SequencePattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Transforms a List of words into a List of Lists of words (that is, a List
 * of sentences), by grouping the words.  The word stream is assumed to
 * already be adequately tokenized, and this class just divides the List into
 * sentences, perhaps discarding some separator tokens as it goes.
 * <p>
 * The main behavior is to look for sentence ending tokens like "." or "?!?",
 * and to split after them and any following sentence closers like ")".
 * Overlaid on this is an overall choice of state: The WordToSentenceProcessor
 * can be a non-splitter, which always returns one sentence. Otherwise, the
 * WordToSentenceProcessor will also split based on paragraphs using one of
 * these three states: (1) Ignore line breaks in splitting sentences,
 * (2) Treat each line as a separate paragraph, or (3) Treat two consecutive
 * line breaks as marking the end of a paragraph. The details of sentence
 * breaking within paragraphs is controlled based on the following three
 * variables:
 * <ul>
 * <li>sentenceBoundaryTokens are tokens that are left in a sentence, but are
 * to be regarded as ending a sentence.  A canonical example is a period.
 * If two of these follow each other, the second will be a sentence
 * consisting of only the sentenceBoundaryToken.
 * <li>sentenceBoundaryFollowers are tokens that are left in a sentence, and
 * which can follow a sentenceBoundaryToken while still belonging to
 * the previous sentence.  They cannot begin a sentence (except at the
 * beginning of a document).  A canonical example is a close parenthesis
 * ')'. The default (English) set is in DEFAULT_BOUNDARY_FOLLOWERS_REGEX.
 * <li>sentenceBoundaryToDiscard are tokens which separate sentences and
 * which should be thrown away.  In web documents, a typical example would
 * be a '{@code <p>}' tag.  If two of these follow each other, they are
 * coalesced: no empty Sentence is output.  The end-of-file is not
 * represented in this Set, but the code behaves as if it were a member.
 * <li>regionElementRegex A regular expression for element names containing
 * a sentence region. Only tokens in such elements will be included in
 * sentences. The start and end tags themselves are not included in the
 * sentence.
 * </ul>
 *
 * Instances of this class are now immutable. ☺
 *
 * @author Joseph Smarr (jsmarr@stanford.edu)
 * @author Christopher Manning
 * @author Teg Grenager (grenager@stanford.edu)
 * @author Sarah Spikes (sdspikes@cs.stanford.edu) (Templatization)
 *
 * @param <IN> The type of the tokens in the sentences
 */
public class WordToSentenceProcessor<IN> implements ListProcessor<IN, List<IN>>  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(WordToSentenceProcessor.class);

  /** Turning this on is good for debugging sentence splitting. */
  private static final boolean DEBUG = false;

  // todo [cdm Aug 2012]: This should be unified with the PlainTextIterator
  // in DocumentPreprocessor, perhaps by making this one implement Iterator.
  // (DocumentProcessor once used to use this class, but now doesn't....)

  public enum NewlineIsSentenceBreak { NEVER, ALWAYS, TWO_CONSECUTIVE }

  /** Default pattern for sentence ending punctuation. Now Chinese-friendly as well as English. */
  public static final String DEFAULT_BOUNDARY_REGEX = "[.。]|[!?！？]+";

  /** Pe = Close_Punctuation (close brackets), Pf = Final_Punctuation (close quotes);
   *  add straight quotes, PTB escaped right brackets (-RRB-, etc.), greater than as close angle bracket,
   *  and those forms in full width range.
   */
  public static final String DEFAULT_BOUNDARY_FOLLOWERS_REGEX = "[\\p{Pe}\\p{Pf}\"'>＂＇＞)}\\]]|''|’’|-R[CRS]B-";

  public static final Set<String> DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD = Collections.unmodifiableSet(
          Generics.newHashSet(Arrays.asList(WhitespaceLexer.NEWLINE, PTBTokenizer.getNewlineToken())));

  /**
   * Regex for tokens (Strings) that qualify as sentence-final tokens.
   */
  private final Pattern sentenceBoundaryTokenPattern;

  /**
   * Regex for multi token sequences that qualify as sentence-final tokens.
   * (i.e. use if you want to sentence split on 2 or more newlines)
   */
  private final SequencePattern<? super IN> sentenceBoundaryMultiTokenPattern;

  /**
   * Regex for tokens (Strings) that qualify as tokens that can follow
   * what normally counts as an end of sentence token, and which are
   * attributed to the preceding sentence.  For example ")" coming after
   * a period.
   */
  private final Pattern sentenceBoundaryFollowersPattern;

  /**
   * List of regex Pattern that are sentence boundaries to be discarded.
   * This is normally newline tokens or representations of them.
   */
  private final Set<String> sentenceBoundaryToDiscard;

  /** Patterns that match the start and end tags of XML elements. These will
   *  be discarded, but taken to mark a sentence boundary.
   *  The value will be null if there are no such elements being used
   *  (for efficiency).
   */
  private final List<Pattern> xmlBreakElementsToDiscard;

  /**
   * List of regex Patterns that are not to be treated as sentence boundaries but should be discarded
   * (i.e. these may have been used with context to identify sentence boundaries but are not needed any more)
   */
  private final List<Pattern> tokenPatternsToDiscard;

  private final Pattern sentenceRegionBeginPattern;

  private final Pattern sentenceRegionEndPattern;

  private final NewlineIsSentenceBreak newlineIsSentenceBreak;

  private final boolean isOneSentence;

  /** Whether to output empty sentences. */
  private final boolean allowEmptySentences;

  public static NewlineIsSentenceBreak stringToNewlineIsSentenceBreak(String name) {
    if ("always".equals(name)) {
      return NewlineIsSentenceBreak.ALWAYS;
    } else if ("never".equals(name)) {
      return NewlineIsSentenceBreak.NEVER;
    } else if (name != null && name.contains("two")) {
      return NewlineIsSentenceBreak.TWO_CONSECUTIVE;
    } else {
      throw new IllegalArgumentException("Not a valid NewlineIsSentenceBreak name: '" + name + "' (should be one of 'always', 'never', 'two')");
    }
  }

  /** This is a sort of hacked in other way to end sentences.
   *  Tokens with the ForcedSentenceEndAnnotation set to true
   *  will also end a sentence.
   */
  @SuppressWarnings("OverlyStrongTypeCast")
  private static boolean isForcedEndToken(Object o) {
    if (o instanceof CoreMap) {
      Boolean forcedEndValue =
              ((CoreMap)o).get(CoreAnnotations.ForcedSentenceEndAnnotation.class);
      String originalText = ((CoreMap) o).get(CoreAnnotations.OriginalTextAnnotation.class);
      return (forcedEndValue != null && forcedEndValue) ||
          (originalText != null && originalText.equals("\u2029"));
    } else {
      return false;
    }
  }

  @SuppressWarnings("OverlyStrongTypeCast")
  private static String getString(Object o) {
    if (o instanceof HasWord) {
      HasWord h = (HasWord) o;
      return h.word();
    } else if (o instanceof String) {
      return (String) o;
    } else if (o instanceof CoreMap) {
      return ((CoreMap) o).get(CoreAnnotations.TextAnnotation.class);
    } else {
      throw new RuntimeException("Expected token to be either Word or String.");
    }
  }

  @SuppressWarnings("Convert2streamapi")
  private static boolean matches(List<Pattern> patterns, String word) {
    for (Pattern p: patterns) {
      Matcher m = p.matcher(word);
      if (m.matches()) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesXmlBreakElementToDiscard(String word) {
    return matches(xmlBreakElementsToDiscard, word);
  }

  private boolean matchesTokenPatternsToDiscard(String word) {
    return matches(tokenPatternsToDiscard, word);
  }

  /**
   * Returns a List of Lists where each element is built from a run
   * of Words in the input Document. Specifically, reads through each word in
   * the input document and breaks off a sentence after finding a valid
   * sentence boundary token or end of file.
   * Note that for this to work, the words in the
   * input document must have been tokenized with a tokenizer that makes
   * sentence boundary tokens their own tokens (e.g., {@link PTBTokenizer}).
   *
   * @param words A list of already tokenized words (must implement HasWord or be a String).
   * @return A list of sentences.
   * @see #WordToSentenceProcessor(String, String, Set, Set, String, NewlineIsSentenceBreak, SequencePattern, Set, boolean, boolean)
   */
  // todo [cdm 2016]: Should really sort out generics here so don't need to have extra list copying
  @Override
  public List<List<IN>> process(List<? extends IN> words) {
    if (isOneSentence) {
      // put all the words in one sentence
      List<List<IN>> sentences = Generics.newArrayList();
      sentences.add(new ArrayList<>(words));
      return sentences;
    } else {
      return wordsToSentences(words);
    }
  }


  /** At present this only tries to avoid adding a straight single/double quote to a sentence when it doesn't plausibly
   *  go there and should go with the next sentence.  It does this by checking for odd number of that quote type.
   *
   *  @param lastSentence The last sentence to which you might want to add the word
   *  @return Whether it's plausible to add because there was an open quote
   */
  private boolean plausibleToAdd(List<IN> lastSentence, String word) {
    if ( ! word.equals("\"") && !word.equals("'")) {
      return true;
    }
    int singleQuoteCount = 0;
    int doubleQuoteCount = 0;
    for (IN lastWord : lastSentence) {
      String lastStr = ((Label) lastWord).value();
      if (lastStr.equals("'"))
        singleQuoteCount += 1;
      if (lastStr.equals("\""))
        doubleQuoteCount += 1;
    }
    if (word.equals("\"") && (doubleQuoteCount % 2 != 0)) {
      return true;
    } else if (word.equals("'") && (singleQuoteCount % 2 != 0)) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns a List of Lists where each element is built from a run
   * of Words in the input Document. Specifically, reads through each word in
   * the input document and breaks off a sentence after finding a valid
   * sentence boundary token or end of file.
   * Note that for this to work, the words in the
   * input document must have been tokenized with a tokenizer that makes
   * sentence boundary tokens their own tokens (e.g., {@link PTBTokenizer}).
   *
   * @param words A list of already tokenized words (must implement HasWord or be a String).
   * @return A list of sentences.
   * @see #WordToSentenceProcessor(String, String, Set, Set, String, NewlineIsSentenceBreak, SequencePattern, Set, boolean, boolean)
   */
  @SuppressWarnings("ConstantConditions")
  private List<List<IN>> wordsToSentences(List<? extends IN> words) {
    IdentityHashMap<Object, Boolean> isSentenceBoundary = null; // is null unless used by sentenceBoundaryMultiTokenPattern

    if (DEBUG) { log.info("Cutting up: " + words); }
    if (sentenceBoundaryMultiTokenPattern != null) {
      if (DEBUG) { log.info("  checking for tokensregex pattern: " + sentenceBoundaryMultiTokenPattern); }
      // Do initial pass using TokensRegex to identify multi token patterns that need to be matched
      // and add the last token of a match to our table of sentence boundary tokens.
      isSentenceBoundary = new IdentityHashMap<>();
      SequenceMatcher<? super IN> matcher = sentenceBoundaryMultiTokenPattern.getMatcher(words);
      while (matcher.find()) {
        List<? super IN> nodes = matcher.groupNodes();
        if (nodes != null && ! nodes.isEmpty()) {
          if (DEBUG) { log.info("    found match at: " + nodes); }
          isSentenceBoundary.put(nodes.get(nodes.size() - 1), true);
        }
      }
    }

    // Split tokens into sentences!!!
    List<List<IN>> sentences = Generics.newArrayList();
    List<IN> currentSentence = new ArrayList<>();
    List<IN> lastSentence = null;
    boolean insideRegion = false;
    boolean inWaitForForcedEnd = false;
    boolean lastTokenWasNewline = false;
    boolean lastSentenceEndForced = false;

    for (IN o: words) {
      String word = getString(o);
      boolean forcedEnd = isForcedEndToken(o);
      // if (DEBUG) { if (forcedEnd) { log.info("Word is " + word + "; marks forced end of sentence [cont.]"); } }

      boolean inMultiTokenExpr = false;
      boolean discardToken = false;
      if (o instanceof CoreMap) {
        // Hacky stuff to ensure sentence breaks do not happen in certain cases
        CoreMap cm = (CoreMap) o;
        if ( ! forcedEnd) {
          Boolean forcedUntilEndValue = cm.get(CoreAnnotations.ForcedSentenceUntilEndAnnotation.class);
          if (forcedUntilEndValue != null && forcedUntilEndValue) {
            // if (DEBUG) { log.info("Word is " + word + "; starting wait for forced end of sentence [cont.]"); }
            inWaitForForcedEnd = true;
          } else {
            MultiTokenTag mt = cm.get(CoreAnnotations.MentionTokenAnnotation.class);
            if (mt != null && ! mt.isEnd()) {
              // In the middle of a multi token mention, make sure sentence is not ended here
              // if (DEBUG) { log.info("Word is " + word + "; inside multi-token mention [cont.]"); }
              inMultiTokenExpr = true;
            }
          }
        }
      }

      if (tokenPatternsToDiscard != null) {
        discardToken = matchesTokenPatternsToDiscard(word);
      }

      if (sentenceRegionBeginPattern != null && ! insideRegion) {
        if (DEBUG) { log.info("Word is " + word + "; outside region; deleted"); }
        if (sentenceRegionBeginPattern.matcher(word).matches()) {
          insideRegion = true;
          if (DEBUG) { log.info("  entering region"); }
        }
        lastTokenWasNewline = false;
        continue;
      }

      if ( ! lastSentenceEndForced && lastSentence != null && currentSentence.isEmpty() &&
              ! lastTokenWasNewline && sentenceBoundaryFollowersPattern.matcher(word).matches() &&
              plausibleToAdd(lastSentence, word)) {
        if ( ! discardToken) {
          lastSentence.add(o);
        }
        if (DEBUG) {
          log.info("Word is " + word + (discardToken ? "discarded":"  added to last sentence"));
        }
        lastTokenWasNewline = false;
        continue;
      }

      boolean newSentForced = false;
      boolean newSent = false;
      String debugText = (discardToken)? "discarded": "added to current";
      if (inWaitForForcedEnd && ! forcedEnd) {
        if (sentenceBoundaryToDiscard.contains(word)) {
          // there can be newlines even in something to keep together
          discardToken = true;
        }
        if ( ! discardToken) currentSentence.add(o);
        if (DEBUG) { log.info("Word is " + word + "; in wait for forced end; " + debugText); }
      } else if (inMultiTokenExpr && ! forcedEnd) {
        if ( ! discardToken) currentSentence.add(o);
        if (DEBUG) { log.info("Word is " + word + "; in multi token expr; " + debugText); }
      } else if (sentenceBoundaryToDiscard.contains(word)) {
        if (forcedEnd) {
          // sentence boundary can easily be forced end
          inWaitForForcedEnd = false;
          newSentForced = true;
        } else if (newlineIsSentenceBreak == NewlineIsSentenceBreak.ALWAYS) {
          newSentForced = true;
        } else if (newlineIsSentenceBreak == NewlineIsSentenceBreak.TWO_CONSECUTIVE && lastTokenWasNewline) {
          newSentForced = true;
        }
        lastTokenWasNewline = true;
        if (DEBUG) {
          log.info("Word is " + word + "; a discarded sentence boundary; newSentForced=" + newSentForced);
        }
      } else {
        lastTokenWasNewline = false;
        Boolean isb;
        if (xmlBreakElementsToDiscard != null && matchesXmlBreakElementToDiscard(word)) {
          newSentForced = true;
          if (DEBUG) { log.info("Word is " + word + "; is XML break element; discarded"); }
        } else if (sentenceRegionEndPattern != null && sentenceRegionEndPattern.matcher(word).matches()) {
          insideRegion = false;
          newSentForced = true;
          // Marked sentence boundaries
        } else if ((isSentenceBoundary != null) && ((isb = isSentenceBoundary.get(o)) != null) && isb) {
          if (!discardToken) currentSentence.add(o);
          if (DEBUG) {
            log.info("Word is " + word + "; is sentence boundary (matched multi-token pattern); " + debugText);
          }
          newSent = true;
        } else if (sentenceBoundaryTokenPattern.matcher(word).matches()) {
          if ( ! discardToken) { currentSentence.add(o); }
          if (DEBUG) { log.info("Word is " + word + "; is sentence boundary; " + debugText); }
          newSent = true;
        } else if (forcedEnd) {
          if ( ! discardToken) { currentSentence.add(o); }
          inWaitForForcedEnd = false;
          newSentForced = true;
          if (DEBUG) { log.info("Word is " + word + "; annotated to be the end of a sentence; " + debugText); }
        } else {
          if ( ! discardToken) currentSentence.add(o);
          // chris added this next test in 2017; a bit weird, but KBP setup doesn't have newline in sentenceBoundary patterns, just in toDiscard
          if (AbstractTokenizer.NEWLINE_TOKEN.equals(word)) {
            lastTokenWasNewline = true;
          }
          if (DEBUG) { log.info("Word is " + word + "; " + debugText); }
        }
      }

      if ((newSentForced || newSent) && ( ! currentSentence.isEmpty() || allowEmptySentences)) {
        sentences.add(currentSentence);
        // adds this sentence now that it's complete
        lastSentenceEndForced = ((lastSentence == null || lastSentence.isEmpty()) && lastSentenceEndForced) || newSentForced;
        lastSentence = currentSentence;
        currentSentence = new ArrayList<>(); // clears the current sentence
        if (DEBUG) {
          String debugWhy = newSentForced ? " because forced" : " due to regular sentence end";
          String debugState = "; lastSentenceEndForced=" + lastSentenceEndForced;
          log.info("  beginning new sentence" + debugWhy + debugState);
        }
      } else if (newSentForced) {
        lastSentenceEndForced = true;
        if (DEBUG) { log.info("  lastSentenceEndForced=" + lastSentenceEndForced); }
      }
    }

    // add any words at the end, even if there isn't a sentence
    // terminator at the end of file
    if ( ! currentSentence.isEmpty()) {
      sentences.add(currentSentence); // adds last sentence
    }

    return sentences;
  }

  public <L, F> Document<L, F, List<IN>> processDocument(Document<L, F, IN> in) {
    Document<L, F, List<IN>> doc = in.blankDocument();
    doc.addAll(process(in));
    return doc;
  }

  /* ---------- Constructors --------- */

  /**
   * Create a {@code WordToSentenceProcessor} using a sensible default
   * list of tokens for sentence ending for English/Latin writing systems.
   * The default set is: {".","?","!"} and
   * any combination of ! or ?, as in !!!?!?!?!!!?!!?!!!.
   * A sequence of two or more consecutive line breaks is taken as a paragraph break
   * which also splits sentences. This is the usual constructor for sentence
   * breaking reasonable text, which uses hard-line breaking, so two
   * blank lines indicate a paragraph break.
   * People commonly use this constructor.
   */
  public WordToSentenceProcessor() {
    this(false);
  }

  /**
   * Create a {@code WordToSentenceProcessor} using a sensible default
   * list of tokens for sentence ending for English/Latin writing systems.
   * The default set is: {".","?","!"} and
   * any combination of ! or ?, as in !!!?!?!?!!!?!!?!!!.
   * You can specify the treatment of newlines as sentence breaks as one
   * of ignored, every newline is a sentence break, or only two or more
   * consecutive newlines are a sentence break.
   *
   * @param newlineIsSentenceBreak Strategy for treating newlines as
   *                               paragraph breaks.
   */
  public WordToSentenceProcessor(NewlineIsSentenceBreak newlineIsSentenceBreak) {
    this(DEFAULT_BOUNDARY_REGEX, newlineIsSentenceBreak, false);
  }

  /**
   * Create a {@code WordToSentenceProcessor} which never breaks the input
   * into multiple sentences. If the argument is true, the input stream
   * is always output as one sentence. (If it is false, this is
   * equivalent to the no argument constructor, so why use this?)
   *
   * @param isOneSentence Marker argument: true means to treat input
   *                      as one sentence
   */
  public WordToSentenceProcessor(boolean isOneSentence) {
    this(DEFAULT_BOUNDARY_REGEX, NewlineIsSentenceBreak.TWO_CONSECUTIVE, isOneSentence);
  }

  /**
   * Set the set of Strings that will mark the end of a sentence,
   * and which will be discarded after doing so.
   * This constructor is used for, and usually only for, doing
   * one-sentence-per-line sentence splitting.  Since in such cases, you
   * generally want to strictly preserve the set of lines in the input,
   * it preserves empty lines as empty sentences in the output.
   *
   * @param boundaryToDiscard A Set of String that will be matched
   *                          with .equals() and will mark an
   *                          end of sentence and be discarded.
   */
  public WordToSentenceProcessor(Set<String> boundaryToDiscard) {
    this("", "", boundaryToDiscard, null, null,
            NewlineIsSentenceBreak.ALWAYS, null, null, false, true);
  }

  /**
   * Create a basic {@code WordToSentenceProcessor} specifying just a few top-level options.
   *
   * @param boundaryTokenRegex The set of boundary tokens
   * @param newlineIsSentenceBreak Strategy for treating newlines as sentence breaks
   * @param isOneSentence Whether to treat whole text as one sentence
   *                      (if true, the other two parameters are ignored).
   */
  public WordToSentenceProcessor(String boundaryTokenRegex,
                                 NewlineIsSentenceBreak newlineIsSentenceBreak,
                                 boolean isOneSentence) {
    this(boundaryTokenRegex, DEFAULT_BOUNDARY_FOLLOWERS_REGEX, DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD,
            null, null, newlineIsSentenceBreak, null, null, isOneSentence, false);
  }

  /**
   * Flexibly set the set of acceptable sentence boundary tokens, but with
   * a default set of allowed boundary following tokens. Also can set sentence boundary
   * to discard tokens and xmlBreakElementsToDiscard and set the treatment of newlines
   * (boundaryToDiscard) as sentence ends.
   *
   * This one is convenient in allowing any of the first 3 arguments to be null,
   * and then the usual defaults are substituted for it.
   * The allowed set of boundary followers is the regex: "[\\p{Pe}\\p{Pf}'\"]|''|-R[CRS]B-".
   * The default set of discarded separator tokens includes the
   * newline tokens used by WhitespaceLexer and PTBLexer.
   *
   * @param boundaryTokenRegex The regex of boundary tokens. If null, use default.
   * @param boundaryFollowersRegex The regex of boundary following tokens. If null, use default.
   *                               These are tokens which should normally be added on to the current sentence
   *                               even after something normally sentence ending has been seen. For example,
   *                               typically a close parenthesis or close quotes goes with the current sentence,
   *                               even after a period or question mark have been seen.
   * @param boundaryToDiscard The set of regex for sentence boundary tokens that should be discarded.
   *                          If null, use default.
   * @param xmlBreakElementsToDiscard xml element names like "p", which will be recognized,
   *                                  treated as sentence ends, and discarded.
   *                                  If null, use none.
   * @param newlineIsSentenceBreak Strategy for counting line ends (boundaryToDiscard) as sentence ends.
   */
  public WordToSentenceProcessor(String boundaryTokenRegex,
                                 String boundaryFollowersRegex,
                                 Set<String> boundaryToDiscard, Set<String> xmlBreakElementsToDiscard,
                                 NewlineIsSentenceBreak newlineIsSentenceBreak,
                                 SequencePattern<? super IN> sentenceBoundaryMultiTokenPattern,
                                 Set<String> tokenRegexesToDiscard) {
    this(boundaryTokenRegex == null ? DEFAULT_BOUNDARY_REGEX : boundaryTokenRegex,
            boundaryFollowersRegex == null ? DEFAULT_BOUNDARY_FOLLOWERS_REGEX: boundaryFollowersRegex,
            boundaryToDiscard == null || boundaryToDiscard.isEmpty() ? DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD : boundaryToDiscard,
            xmlBreakElementsToDiscard == null ? Collections.emptySet() : xmlBreakElementsToDiscard,
            null, newlineIsSentenceBreak, sentenceBoundaryMultiTokenPattern, tokenRegexesToDiscard, false, false);
  }

  /**
   * Configure all parameters for converting a list of tokens into sentences.
   * The whole enchilada.
   *
   * @param boundaryTokenRegex Tokens that match this regex will end a
   *                           sentence, but are retained at the end of
   *                           the sentence. Substantive value must be supplied.
   * @param boundaryFollowersRegex This is a Set of String that are matched with
   *                               .equals() which are allowed to be tacked onto
   *                               the end of a sentence after a sentence boundary
   *                               token, for example ")". Substantive value must be supplied.
   * @param boundariesToDiscard This is normally used for newline tokens if
   *                            they are included in the tokenization. They
   *                            may end the sentence (depending on the setting
   *                            of newlineIsSentenceBreak), but at any rate
   *                            are deleted from sentences in the output.
   *                            Substantive value must be supplied.
   * @param xmlBreakElementsToDiscard These are elements like "p" or "sent",
   *                                  which will be wrapped into regex for
   *                                  approximate XML matching. They will be
   *                                  deleted in the output, and will always
   *                                  trigger a sentence boundary.
   *                                  May be null; means discard none.
   * @param regionElementRegex XML element name regex to delimit regions processed.
   *                           Tokens outside one of these elements are discarded.
   *                           May be null; means to not filter by regions
   * @param newlineIsSentenceBreak How to treat newlines. Must have substantive value.
   * @param sentenceBoundaryMultiTokenPattern A TokensRegex multi-token pattern for finding boundaries.
   *                                          May be null; means that there are no such patterns.
   * @param tokenRegexesToDiscard Regex for tokens to discard.
   *                              May be null; means that no tokens are discarded in this way.
   * @param isOneSentence Whether to treat whole of input as one sentence regardless.
   *                      Must have substantive value. Overrides anything else.
   * @param allowEmptySentences Whether to allow empty sentences to be output
   *                            Must have substantive value. Often suppressed, but don't want that in things like
   *                            strict one-sentence-per-line mode.
   */
  public WordToSentenceProcessor(String boundaryTokenRegex, String boundaryFollowersRegex,
                                 Set<String> boundariesToDiscard, Set<String> xmlBreakElementsToDiscard,
                                 String regionElementRegex, NewlineIsSentenceBreak newlineIsSentenceBreak,
                                 SequencePattern<? super IN> sentenceBoundaryMultiTokenPattern,
                                 Set<String> tokenRegexesToDiscard,
                                 boolean isOneSentence, boolean allowEmptySentences) {
    sentenceBoundaryTokenPattern = Pattern.compile(boundaryTokenRegex);
    sentenceBoundaryFollowersPattern = Pattern.compile(boundaryFollowersRegex);
    sentenceBoundaryToDiscard = Collections.unmodifiableSet(boundariesToDiscard);
    if (xmlBreakElementsToDiscard == null || xmlBreakElementsToDiscard.isEmpty()) {
      this.xmlBreakElementsToDiscard = null;
    } else {
      this.xmlBreakElementsToDiscard = new ArrayList<>(xmlBreakElementsToDiscard.size());
      for (String s: xmlBreakElementsToDiscard) {
        String regex = "<\\s*(?:/\\s*)?(?:" + s + ")(?:\\s+[^>]+?|\\s*(?:/\\s*)?)>";
        // log.info("Regex is |" + regex + "|");
        // todo: Historically case insensitive, but maybe better and more proper to make case sensitive?
        this.xmlBreakElementsToDiscard.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
      }
    }
    if (regionElementRegex != null) {
      sentenceRegionBeginPattern = Pattern.compile("<\\s*(?:" + regionElementRegex + ")(?:\\s+[^>]+?)?>");
      sentenceRegionEndPattern = Pattern.compile("<\\s*/\\s*(?:" + regionElementRegex + ")\\s*>");
    } else {
      sentenceRegionBeginPattern = null;
      sentenceRegionEndPattern = null;
    }
    this.newlineIsSentenceBreak = newlineIsSentenceBreak;
    this.sentenceBoundaryMultiTokenPattern = sentenceBoundaryMultiTokenPattern;
    if (tokenRegexesToDiscard != null) {
      this.tokenPatternsToDiscard = new ArrayList<>(tokenRegexesToDiscard.size());
      for (String s: tokenRegexesToDiscard) {
        this.tokenPatternsToDiscard.add(Pattern.compile(s));
      }
    } else {
      this.tokenPatternsToDiscard = null;
    }
    this.isOneSentence = isOneSentence;
    this.allowEmptySentences = allowEmptySentences;

    if (DEBUG) {
      log.info("WordToSentenceProcessor: boundaryTokens=" + boundaryTokenRegex);
      log.info("  boundaryFollowers=" + boundaryFollowersRegex);
      log.info("  boundariesToDiscard=" + boundariesToDiscard);
      log.info("  xmlBreakElementsToDiscard=" + xmlBreakElementsToDiscard);
      log.info("  regionBeginPattern=" + sentenceRegionBeginPattern);
      log.info("  regionEndPattern=" + sentenceRegionEndPattern);
      log.info("  newlineIsSentenceBreak=" + newlineIsSentenceBreak);
      log.info("  sentenceBoundaryMultiTokenPattern=" + sentenceBoundaryMultiTokenPattern);
      log.info("  tokenPatternsToDiscard=" + tokenPatternsToDiscard);
      log.info("  isOneSentence=" + isOneSentence);
      log.info("  allowEmptySentences=" + allowEmptySentences);
      log.info(new Exception("above WordToSentenceProcessor invoked from here:"));
    }
  }

}
