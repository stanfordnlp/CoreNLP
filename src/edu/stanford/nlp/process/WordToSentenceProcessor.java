package edu.stanford.nlp.process;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Document;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.MultiTokenTag;
import edu.stanford.nlp.ling.tokensregex.SequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.SequencePattern;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Generics;

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
 * ')'.
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
public class WordToSentenceProcessor<IN> implements ListProcessor<IN, List<IN>> {

  // todo [cdm Aug 2012]: This should be unified with the PlainTextIterator
  // in DocumentPreprocessor, perhaps by making this one implement Iterator.
  // (DocumentProcessor once used to use this class, but now doesn't....)

  public enum NewlineIsSentenceBreak { NEVER, ALWAYS, TWO_CONSECUTIVE }

  public static final String DEFAULT_BOUNDARY_REGEX = "\\.|[!?]+";
  public static final Set<String> DEFAULT_BOUNDARY_FOLLOWERS = Collections.unmodifiableSet(Generics.newHashSet(
          Arrays.asList(")", "]", "}", "\"", "'", "''", "\u2019", "\u201D", "-RRB-", "-RSB-", "-RCB-", ")", "]", "}")));
  public static final Set<String> DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD = Collections.unmodifiableSet(Generics.newHashSet(
          Arrays.asList(WhitespaceLexer.NEWLINE, PTBLexer.NEWLINE_TOKEN)));

  private static final boolean DEBUG = false;


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
   * Set of tokens (Strings) that qualify as tokens that can follow
   * what normally counts as an end of sentence token, and which are
   * attributed to the preceding sentence.  For example ")" coming after
   * a period.
   */
  private final Set<String> sentenceBoundaryFollowers;

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

  private final boolean allowEmptySentences;

  public static NewlineIsSentenceBreak stringToNewlineIsSentenceBreak(String name) {
    if ("always".equals(name)) {
      return NewlineIsSentenceBreak.ALWAYS;
    } else if ("never".equals(name)) {
      return NewlineIsSentenceBreak.NEVER;
    } else if (name != null && name.contains("two")) {
      return NewlineIsSentenceBreak.TWO_CONSECUTIVE;
    } else {
      throw new IllegalArgumentException("Not a valid NewlineIsSentenceBreak name");
    }
  }

  /** This is a sort of hacked in other way to end sentences.
   *  Tokens with the ForcedSentenceEndAnnotation set to true
   *  will also end a sentence.
   */
  @SuppressWarnings("OverlyStrongTypeCast")
  private boolean isForcedEndToken(IN o) {
    if (o instanceof CoreMap) {
      Boolean forcedEndValue =
              ((CoreMap)o).get(CoreAnnotations.ForcedSentenceEndAnnotation.class);
      return forcedEndValue != null && forcedEndValue;
    } else {
      return false;
    }
  }

  @SuppressWarnings("OverlyStrongTypeCast")
  private String getString(IN o) {
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

  private static boolean matches(List<Pattern> patterns, String word) {
    for(Pattern p: patterns){
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

  @Override
  public List<List<IN>> process(List<? extends IN> words) {
    if (isOneSentence) {
      // put all the words in one sentence
      List<List<IN>> sentences = Generics.newArrayList();
      sentences.add(new ArrayList<IN>(words));
      return sentences;
    } else {
      return wordsToSentences(words);
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
   * @see #WordToSentenceProcessor(String, Set, Set, Set, String, NewlineIsSentenceBreak, SequencePattern, Set, boolean, boolean)
   */
  public List<List<IN>> wordsToSentences(List<? extends IN> words) {
    IdentityHashMap<Object, Boolean> isSentenceBoundary = new IdentityHashMap<Object, Boolean>();
    if (sentenceBoundaryMultiTokenPattern != null) {
      // Do initial pass using tokensregex to identify multi token patterns that need to be matched
      // and add the last token to our table of sentence boundary tokens
      SequenceMatcher<? super IN> matcher = sentenceBoundaryMultiTokenPattern.getMatcher(words);
      while (matcher.find()) {
        List nodes = matcher.groupNodes();
        if (nodes != null && nodes.size() > 0) {
          isSentenceBoundary.put(nodes.get(nodes.size() - 1), true);
        }
      }
    }

    // Split tokens into sentences!!!
    List<List<IN>> sentences = Generics.newArrayList();
    List<IN> currentSentence = new ArrayList<IN>();
    List<IN> lastSentence = null;
    boolean insideRegion = false;
    boolean inWaitForForcedEnd = false;
    boolean lastTokenWasNewline = false;
    for (IN o: words) {
      String word = getString(o);
      boolean forcedEnd = isForcedEndToken(o);

      boolean inMultiTokenExpr = false;
      boolean discardToken = false;
      if (o instanceof CoreMap) {
        // Hacky stuff to ensure sentence breaks do not happen in certain cases
        CoreMap cm = (CoreMap) o;
        Boolean forcedUntilEndValue = cm.get(CoreAnnotations.ForcedSentenceUntilEndAnnotation.class);
        if (!forcedEnd) {
          if (forcedUntilEndValue != null && forcedUntilEndValue)
            inWaitForForcedEnd = true;
          else {
            MultiTokenTag mt = cm.get(CoreAnnotations.MentionTokenAnnotation.class);
            if (mt != null && !mt.isEnd()) {
              // In the middle of a multi token mention, make sure sentence is not ended here
              inMultiTokenExpr = true;
            }
          }
        }
      }
      if (tokenPatternsToDiscard != null) {
        discardToken = matchesTokenPatternsToDiscard(word);
      }

      if (DEBUG) {
        EncodingPrintWriter.err.println("Word is " + word, "UTF-8");
      }
      if (sentenceRegionBeginPattern != null && ! insideRegion) {
        if (DEBUG) {
          System.err.println("  outside region; deleted");
        }
        if (sentenceRegionBeginPattern.matcher(word).matches()) {
          insideRegion = true;
          if (DEBUG) {
            System.err.println("  entering region");
          }
        }
        lastTokenWasNewline = false;
        continue;
      }

      if (lastSentence != null && currentSentence.isEmpty() && sentenceBoundaryFollowers.contains(word)) {
        if (!discardToken) lastSentence.add(o);
        if (DEBUG) {
          System.err.println(discardToken? "discarded":"  added to last sentence");
        }
        lastTokenWasNewline = false;
        continue;
      }

      boolean newSent = false;
      String debugText = (discardToken)? "discarded":"added to current";
      if (inWaitForForcedEnd && !forcedEnd) {
        if (!discardToken) currentSentence.add(o);
        if (DEBUG) {
          System.err.println("  is in wait for forced end; " + debugText);
        }
      } else if (inMultiTokenExpr && !forcedEnd) {
        if (!discardToken) currentSentence.add(o);
        if (DEBUG) {
          System.err.println("  is in multi token expr; " + debugText);
        }
      } else if (sentenceBoundaryToDiscard.contains(word)) {
        if (newlineIsSentenceBreak == NewlineIsSentenceBreak.ALWAYS) {
          newSent = true;
        } else if (newlineIsSentenceBreak == NewlineIsSentenceBreak.TWO_CONSECUTIVE) {
          if (lastTokenWasNewline) {
            newSent = true;
          }
        }
        lastTokenWasNewline = true;
        if (DEBUG) {
          System.err.println("  discarded sentence boundary");
        }
      } else {
        lastTokenWasNewline = false;
        if (xmlBreakElementsToDiscard != null && matchesXmlBreakElementToDiscard(word)) {
          newSent = true;
          if (DEBUG) {
            System.err.println("  is XML break element; discarded");
          }
        } else if (sentenceRegionEndPattern != null && sentenceRegionEndPattern.matcher(word).matches()) {
          insideRegion = false;
          newSent = true;
          // Marked sentence boundaries
        } else if (isSentenceBoundary.containsKey(o) && isSentenceBoundary.get(o)) {
          if (!discardToken) currentSentence.add(o);
          if (DEBUG) {
            System.err.println("  is sentence boundary (matched multi-token pattern); " + debugText);
          }
          newSent = true;
        } else if (sentenceBoundaryTokenPattern.matcher(word).matches()) {
          if (!discardToken) currentSentence.add(o);
          if (DEBUG) {
            System.err.println("  is sentence boundary; " + debugText);
          }
          newSent = true;
        } else if (forcedEnd) {
          if (!discardToken) currentSentence.add(o);
          inWaitForForcedEnd = false;
          newSent = true;
          if (DEBUG) {
            System.err.println("  annotated to be the end of a sentence; " + debugText);
          }
        } else {
          if (!discardToken) currentSentence.add(o);
          if (DEBUG) {
            System.err.println("  " + debugText);
          }
        }
      }

      if (newSent && (!currentSentence.isEmpty() || allowEmptySentences)) {
        if (DEBUG) {
          System.err.println("  beginning new sentence");
        }
        sentences.add(currentSentence);
        // adds this sentence now that it's complete
        lastSentence = currentSentence;
        currentSentence = new ArrayList<IN>(); // clears the current sentence
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


  /**
   * Create a {@code WordToSentenceProcessor} using a sensible default
   * list of tokens for sentence ending for English/Latin writing systems.
   * The default set is: {".","?","!"} and
   * any combination of ! or ?, as in !!!?!?!?!!!?!!?!!!.
   * A sequence of two or more consecutive line breaks is taken as a paragraph break
   * which also splits sentences. This is the usual constructor for sentence
   * breaking reasonable text, which uses hard-line breaking, so two
   * blank lines indicate a paragraph break.
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
   * This one just massages the above 3 constructors to the maximal constructor.
   *
   * @param boundaryTokenRegex The set of boundary tokens
   * @param newlineIsSentenceBreak Strategy for treating newlines as sentence breaks
   * @param isOneSentence Whether to treat whole text as one sentence
   *                      (if true, the other two parameters are ignored).
   */
  private WordToSentenceProcessor(String boundaryTokenRegex,
                                 NewlineIsSentenceBreak newlineIsSentenceBreak,
                                 boolean isOneSentence) {
    this(boundaryTokenRegex, DEFAULT_BOUNDARY_FOLLOWERS, DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD,
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
   * The allowed set of boundary followers is:
   * {")", "]", "}", "\"", "'", "''", "’", "”", "-RRB-", "-RSB-", "-RCB-", ")", "]", "}"}.
   * The default set of discarded separator tokens includes the
   * newline tokens used by WhitespaceLexer and PTBLexer.
   *
   * @param boundaryTokenRegex The set of boundary tokens. If null, use default.
   * @param boundaryToDiscard The set of regex for sentence boundary tokens that should be discarded.
   *                          If null, use default.
   * @param xmlBreakElementsToDiscard xml element names like "p", which will be recognized,
   *                                  treated as sentence ends, and discarded.
   *                                  If null, use none.
   * @param newlineIsSentenceBreak Strategy for counting line ends (boundaryToDiscard) as sentence ends.
   */
  public WordToSentenceProcessor(String boundaryTokenRegex,
                                 Set<String> boundaryToDiscard, Set<String> xmlBreakElementsToDiscard,
                                 NewlineIsSentenceBreak newlineIsSentenceBreak) {
    this(boundaryTokenRegex == null ? DEFAULT_BOUNDARY_REGEX : boundaryTokenRegex,
            DEFAULT_BOUNDARY_FOLLOWERS,
            boundaryToDiscard == null || boundaryToDiscard.isEmpty() ? DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD : boundaryToDiscard,
            xmlBreakElementsToDiscard == null ? Collections.<String>emptySet() : xmlBreakElementsToDiscard,
            null, newlineIsSentenceBreak, null, null, false, false);
  }

  /**
   * Flexibly set the set of acceptable sentence boundary tokens, but with
   * a default set of allowed boundary following tokens. Also can set sentence boundary
   * to discard tokens and xmlBreakElementsToDiscard and set the treatment of newlines
   * (boundaryToDiscard) as sentence ends.
   *
   * This one is convenient in allowing any of the first 3 arguments to be null,
   * and then the usual defaults are substituted for it.
   * The allowed set of boundary followers is:
   * {")", "]", "}", "\"", "'", "''", "’", "”", "-RRB-", "-RSB-", "-RCB-", ")", "]", "}"}.
   * The default set of discarded separator tokens includes the
   * newline tokens used by WhitespaceLexer and PTBLexer.
   *
   * @param boundaryTokenRegex The set of boundary tokens. If null, use default.
   * @param boundaryToDiscard The set of regex for sentence boundary tokens that should be discarded.
   *                          If null, use default.
   * @param xmlBreakElementsToDiscard xml element names like "p", which will be recognized,
   *                                  treated as sentence ends, and discarded.
   *                                  If null, use none.
   * @param newlineIsSentenceBreak Strategy for counting line ends (boundaryToDiscard) as sentence ends.
   */
  public WordToSentenceProcessor(String boundaryTokenRegex,
                                 Set<String> boundaryToDiscard, Set<String> xmlBreakElementsToDiscard,
                                 NewlineIsSentenceBreak newlineIsSentenceBreak,
                                 SequencePattern<? super IN> sentenceBoundaryMultiTokenPattern,
                                 Set<String> tokenRegexesToDiscard) {
    this(boundaryTokenRegex == null ? DEFAULT_BOUNDARY_REGEX : boundaryTokenRegex,
            DEFAULT_BOUNDARY_FOLLOWERS,
            boundaryToDiscard == null || boundaryToDiscard.isEmpty() ? DEFAULT_SENTENCE_BOUNDARIES_TO_DISCARD : boundaryToDiscard,
            xmlBreakElementsToDiscard == null ? Collections.<String>emptySet() : xmlBreakElementsToDiscard,
            null, newlineIsSentenceBreak, sentenceBoundaryMultiTokenPattern, tokenRegexesToDiscard, false, false);
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
    this("", Collections.<String>emptySet(), boundaryToDiscard, null, null,
            NewlineIsSentenceBreak.ALWAYS, null, null, false, true);
  }

  /**
   * Flexibly set parameters for converting a list of tokens into sentences.
   * The whole enchilada.
   *
   * @param boundaryTokenRegex Tokens that match this regex will end a
   *                           sentence, but are retained at the end of
   *                           the sentence.
   * @param boundaryFollowers This is a Set of String that are matched with
   *                          .equals() which are allowed to be tacked onto
   *                          the end of a sentence after a sentence boundary
   *                          token, for example ")".
   * @param boundariesToDiscard This is normally used for newline tokens if
   *                            they are included in the tokenization. They
   *                            may end the sentence (depending on the setting
   *                            of newlineIsSentenceBreak), but at any rate
   *                            are deleted from sentences in the output.
   * @param xmlBreakElementsToDiscard These are elements like "p" or "sent",
   *                                  which will be wrapped into regex for
   *                                  approximate XML matching. They will be
   *                                  deleted in the output, and will always
   *                                  trigger a sentence boundary.
   */
  public WordToSentenceProcessor(String boundaryTokenRegex, Set<String> boundaryFollowers,
                                 Set<String> boundariesToDiscard, Set<String> xmlBreakElementsToDiscard,
                                 String regionElementRegex, NewlineIsSentenceBreak newlineIsSentenceBreak,
                                 SequencePattern<? super IN> sentenceBoundaryMultiTokenPattern,
                                 Set<String> tokenRegexesToDiscard,
                                 boolean isOneSentence, boolean allowEmptySentences) {
    sentenceBoundaryTokenPattern = Pattern.compile(boundaryTokenRegex);
    sentenceBoundaryFollowers = Collections.unmodifiableSet(boundaryFollowers);
    sentenceBoundaryToDiscard = Collections.unmodifiableSet(boundariesToDiscard);
    if (xmlBreakElementsToDiscard == null || xmlBreakElementsToDiscard.isEmpty()) {
      this.xmlBreakElementsToDiscard = null;
    } else {
      this.xmlBreakElementsToDiscard = new ArrayList<Pattern>(xmlBreakElementsToDiscard.size());
      for (String s: xmlBreakElementsToDiscard) {
        String regex = "<\\s*(?:/\\s*)?(?:" + s + ")(?:\\s+[^>]+?|\\s*(?:/\\s*)?)>";
        // System.err.println("Regex is |" + regex + "|");
        // todo: Historically case insensitive, but maybe better and more proper to make case sensitive?
        this.xmlBreakElementsToDiscard.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
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
    this.isOneSentence = isOneSentence;
    this.allowEmptySentences = allowEmptySentences;
    if (sentenceBoundaryMultiTokenPattern != null) {
      this.sentenceBoundaryMultiTokenPattern = sentenceBoundaryMultiTokenPattern;
    } else {
      this.sentenceBoundaryMultiTokenPattern = null;
    }
    if (tokenRegexesToDiscard != null) {
      this.tokenPatternsToDiscard = new ArrayList<Pattern>(tokenRegexesToDiscard.size());
      for (String s: tokenRegexesToDiscard) {
        this.tokenPatternsToDiscard.add(Pattern.compile(s));
      }
    } else {
      this.tokenPatternsToDiscard = null;
    }

    if (DEBUG) {
      EncodingPrintWriter.err.println("WordToSentenceProcessor: boundaryTokens=" + boundaryTokenRegex, "UTF-8");
      EncodingPrintWriter.err.println("  boundaryFollowers=" + boundaryFollowers, "UTF-8");
      EncodingPrintWriter.err.println("  boundariesToDiscard=" + boundariesToDiscard, "UTF-8");
      EncodingPrintWriter.err.println("  xmlBreakElementsToDiscard=" + xmlBreakElementsToDiscard, "UTF-8");
      EncodingPrintWriter.err.println("  regionBeginPattern=" + sentenceRegionBeginPattern, "UTF-8");
      EncodingPrintWriter.err.println("  regionEndPattern=" + sentenceRegionEndPattern, "UTF-8");
      EncodingPrintWriter.err.println("  newlineIsSentenceBreak=" + newlineIsSentenceBreak, "UTF-8");
      EncodingPrintWriter.err.println("  sentenceBoundaryMultiTokenPattern=" + sentenceBoundaryMultiTokenPattern, "UTF-8");
      EncodingPrintWriter.err.println("  tokenPatternsToDiscard=" + tokenPatternsToDiscard, "UTF-8");
      EncodingPrintWriter.err.println("  isOneSentence=" + isOneSentence, "UTF-8");
      EncodingPrintWriter.err.println("  allowEmptySentences=" + allowEmptySentences, "UTF-8");
    }
  }

}
