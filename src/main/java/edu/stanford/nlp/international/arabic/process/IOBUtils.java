package edu.stanford.nlp.international.arabic.process; 

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.arabic.ArabicMorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification;
import edu.stanford.nlp.international.morph.MorphoFeatureSpecification.MorphoFeatureType;
import edu.stanford.nlp.international.morph.MorphoFeatures;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SentenceUtils;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharacterOffsetEndAnnotation;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * A class for converting strings to input suitable for processing by
 * an IOB sequence model.
 *
 * @author Spence Green
 * @author Will Monroe
 */
public class IOBUtils  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(IOBUtils.class);

  // Training token types.
  private enum TokenType { BeginMarker, EndMarker, BothMarker, NoMarker }

  // Label inventory
  public static final String BeginSymbol = "BEGIN";
  public static final String ContinuationSymbol = "CONT";
  public static final String NosegSymbol = "NOSEG";
  public static final String RewriteSymbol = "REW";
  
  /** @deprecated use RewriteSymbol instead */
  public static final String RewriteTahSymbol = "REWTA";
  /** @deprecated use RewriteSymbol instead */
  public static final String RewriteTareefSymbol = "REWAL";

  private static final String BoundarySymbol = ".##.";
  private static final String BoundaryChar = ".#.";

  // Patterns for tokens that should not be segmented.
  private static final Pattern isPunc = Pattern.compile("\\p{Punct}+");
  private static final Pattern isDigit = Pattern.compile("\\p{Digit}+");
  private static final Pattern notUnicodeArabic = Pattern.compile("\\P{InArabic}+");

  // Sets of known clitics for tagging when reconstructing the segmented sequences.
  private static final Set<String> arPrefixSet;
  private static final Set<String> arSuffixSet;
  static {
    String arabicPrefixString = "ل ف و م ما ح حا ه ها ك ب س";
    arPrefixSet = Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(arabicPrefixString.split("\\s+"))));
    String arabicSuffixString = "ل و ما ه ها هم هن نا كم تن تم ى ي هما ك ب ش";
    arSuffixSet = Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(arabicSuffixString.split("\\s+"))));
  }

  // Only static methods
  private IOBUtils() {}

  public static String getBoundaryCharacter() { return BoundaryChar; }

  /**
   * Convert a String to a list of characters suitable for labeling in an IOB
   * segmentation model.
   *
   * @param tokenList
   * @param segMarker
   * @param applyRewriteRules add rewrite labels (for training data)
   */
  public static List<CoreLabel> StringToIOB(List<CoreLabel> tokenList,
                                            Character segMarker,
                                            boolean applyRewriteRules) {
    return StringToIOB(tokenList, segMarker, applyRewriteRules, false, null, null);
  }

  /**
   * Convert a String to a list of characters suitable for labeling in an IOB
   * segmentation model.
   *
   * @param tokenList
   * @param segMarker
   * @param applyRewriteRules add rewrite labels (for training data)
   * @param tf a TokenizerFactory returning ArabicTokenizers (for determining original segment boundaries)
   * @param origText the original string before tokenization (for determining original segment boundaries)
   */
  public static List<CoreLabel> StringToIOB(List<CoreLabel> tokenList,
                                            Character segMarker,
                                            boolean applyRewriteRules,
                                            TokenizerFactory<CoreLabel> tf,
                                            String origText) {
    return StringToIOB(tokenList, segMarker, applyRewriteRules, false, tf, origText);
  }
  
  /**
   * Convert a String to a list of characters suitable for labeling in an IOB
   * segmentation model.
   *
   * @param tokenList
   * @param segMarker
   * @param applyRewriteRules add rewrite labels (for training data)
   * @param stripRewrites revert training data to old Green and DeNero model (remove
   *    rewrite labels but still rewrite to try to preserve raw text)
   */
  public static List<CoreLabel> StringToIOB(List<CoreLabel> tokenList,
                                            Character segMarker,
                                            boolean applyRewriteRules,
                                            boolean stripRewrites) {
    return StringToIOB(tokenList, segMarker, applyRewriteRules, stripRewrites, null, null);
  }

  /**
   * Convert a String to a list of characters suitable for labeling in an IOB
   * segmentation model.
   *
   * @param tokenList
   * @param segMarker
   * @param applyRewriteRules add rewrite labels (for training data)
   * @param stripRewrites revert training data to old Green and DeNero model (remove
   *    rewrite labels but still rewrite to try to preserve raw text)
   * @param tf a TokenizerFactory returning ArabicTokenizers (for determining original segment boundaries)
   * @param origText the original string before tokenization (for determining original segment boundaries)
   */
  public static List<CoreLabel> StringToIOB(List<CoreLabel> tokenList,
                                            Character segMarker,
                                            boolean applyRewriteRules,
                                            boolean stripRewrites,
                                            TokenizerFactory<CoreLabel> tf,
                                            String origText) {
    List<CoreLabel> iobList = new ArrayList<>(tokenList.size() * 7 + tokenList.size());
    final String strSegMarker = String.valueOf(segMarker);

    boolean addWhitespace = false;
    final int numTokens = tokenList.size();
    String lastToken = "";
    String currentWord = "";
    int wordStartIndex = 0;
    for (CoreLabel cl : tokenList) {
      // What type of token is this
      if (addWhitespace) {
        fillInWordStatistics(iobList, currentWord, wordStartIndex);
        currentWord = "";
        wordStartIndex = iobList.size() + 1;

        iobList.add(createDatum(cl, BoundaryChar, BoundarySymbol));
        final CoreLabel boundaryDatum = iobList.get(iobList.size() - 1);
        boundaryDatum.setIndex(0);
        boundaryDatum.setWord("");
        addWhitespace = false;
      }

      String token = cl.word();
      TokenType tokType = getTokenType(token, strSegMarker);
      token = stripSegmentationMarkers(token, tokType);
      assert token.length() != 0;

      if (shouldNotSegment(token)) {
        iobList.add(createDatum(cl, token, NosegSymbol));
        addWhitespace = true;

      } else {
        // Iterate over the characters in the token
        tokenToDatums(iobList, cl, token, tokType, cl, lastToken, applyRewriteRules, stripRewrites, tf, origText);
        addWhitespace = (tokType == TokenType.BeginMarker || tokType == TokenType.NoMarker);
      }
      currentWord += token;
      lastToken = token;
    }
    fillInWordStatistics(iobList, currentWord, wordStartIndex);
    return iobList;
  }

  /**
   * Loops back through all the datums inserted for the most recent word
   * and inserts statistics about the word they are a part of. This needs to
   * be post hoc because the CoreLabel lists coming from testing data sets
   * are pre-segmented (so treating each of those CoreLabels as a "word" lets
   * us cheat and get 100% classification accuracy by just looking at whether
   * we're at the beginning of a "word"). 
   * 
   * @param iobList
   * @param currentWord
   * @param wordStartIndex
   */
  private static void fillInWordStatistics(List<CoreLabel> iobList,
      String currentWord, int wordStartIndex) {
    for (int j = wordStartIndex; j < iobList.size(); j++) {
      CoreLabel tok = iobList.get(j);
      tok.setIndex(j - wordStartIndex);
      tok.setWord(currentWord);
    }
  }

  /**
   * Convert token to a sequence of datums and add to iobList.
   *
   * @param iobList
   * @param token
   * @param tokType
   * @param tokenLabel
   * @param lastToken
   * @param applyRewriteRules
   * @param tf a TokenizerFactory returning ArabicTokenizers (for determining original segment boundaries)
   * @param origText the original string before tokenization (for determining original segment boundaries)
   */
  private static void tokenToDatums(List<CoreLabel> iobList,
                                CoreLabel cl,
                                String token,
                                TokenType tokType, 
                                CoreLabel tokenLabel,
                                String lastToken,
                                boolean applyRewriteRules,
                                boolean stripRewrites,
                                TokenizerFactory<CoreLabel> tf,
                                String origText) {

    if (token.isEmpty()) return;
    String lastLabel = ContinuationSymbol;
    String firstLabel = BeginSymbol;
    String rewritten = cl.get(ArabicDocumentReaderAndWriter.RewrittenArabicAnnotation.class);
    boolean crossRefRewrites = true;
    if (rewritten == null) {
      rewritten = token;
      crossRefRewrites = false;
    } else {
      rewritten = stripSegmentationMarkers(rewritten, tokType);
    }

    if (applyRewriteRules) {
      // Apply Arabic-specific re-write rules
      String rawToken = tokenLabel.word();
      String tag = tokenLabel.tag();
      MorphoFeatureSpecification featureSpec = new ArabicMorphoFeatureSpecification();
      featureSpec.activate(MorphoFeatureType.NGEN);
      featureSpec.activate(MorphoFeatureType.NNUM);
      featureSpec.activate(MorphoFeatureType.DEF);
      featureSpec.activate(MorphoFeatureType.TENSE);
      MorphoFeatures features = featureSpec.strToFeatures(tag);

      // Rule #1 : ت --> ة
      if (features.getValue(MorphoFeatureType.NGEN).equals("F") &&
          features.getValue(MorphoFeatureType.NNUM).equals("SG") &&
          rawToken.endsWith("ت-") &&
          !stripRewrites) {
        lastLabel = RewriteSymbol;
      } else if (rawToken.endsWith("ة-")) {
        assert token.endsWith("ة");
        token = token.substring(0, token.length() - 1) + "ت";
        lastLabel = RewriteSymbol;
      }

      // Rule #2 : لل --> ل ال
      if (lastToken.equals("ل") &&
          features.getValue(MorphoFeatureType.DEF).equals("D")) {
        if (rawToken.startsWith("-ال")) {
          if (!token.startsWith("ا"))
            log.info("Bad REWAL: " + rawToken + " / " + token);
          token = token.substring(1);
          rewritten = rewritten.substring(1);
          if (!stripRewrites)
            firstLabel = RewriteSymbol;
        } else if (rawToken.startsWith("-ل")) {
          if (!token.startsWith("ل"))
            log.info("Bad REWAL: " + rawToken + " / " + token);
          if (!stripRewrites)
            firstLabel = RewriteSymbol;
        } else {
          log.info("Ignoring REWAL: " + rawToken + " / " + token);
        }
      }
      
      // Rule #3 : ي --> ى
      // Rule #4 : ا --> ى
      if (rawToken.endsWith("ى-")) {
        if (features.getValue(MorphoFeatureType.TENSE) != null) {
          // verb: ى becomes ا
          token = token.substring(0, token.length() - 1) + "ا";
        } else {
          // assume preposition:
          token = token.substring(0, token.length() - 1) + "ي";
        }
        if (!stripRewrites)
          lastLabel = RewriteSymbol;
      } else if (rawToken.equals("علي-") || rawToken.equals("-علي-")) {
        if (!stripRewrites)
          lastLabel = RewriteSymbol;
      }
    }

    String origWord;
    if (origText == null) {
      origWord = tokenLabel.word();
    } else {
      origWord = origText.substring(cl.beginPosition(), cl.endPosition());
    }
    int origIndex = 0;
    while (origIndex < origWord.length() && isDeletedCharacter(origWord.charAt(origIndex), tf)) {
      ++origIndex;
    }

    // Create datums and add to iobList
    if (token.isEmpty())
      log.info("Rewriting resulted in empty token: " + tokenLabel.word());
    String firstChar = String.valueOf(token.charAt(0));
    // Start at 0 to make sure we include the whole token according to the tokenizer
    iobList.add(createDatum(cl, firstChar, firstLabel, 0, origIndex + 1));
    final int numChars = token.length();
    if (crossRefRewrites && rewritten.length() != numChars) {
      System.err.printf("Rewritten annotation doesn't have correct length: %s>>>%s%n", token, rewritten);
      crossRefRewrites = false;
    }

    ++origIndex;
    for (int j = 1; j < numChars; ++j, ++origIndex) {
      while (origIndex < origWord.length() && isDeletedCharacter(origWord.charAt(origIndex), tf)) {
        ++origIndex;
      }
      if (origIndex >= origWord.length()) {
        origIndex = origWord.length() - 1;
      }

      String charLabel = (j == numChars-1) ? lastLabel : ContinuationSymbol;
      String thisChar = String.valueOf(token.charAt(j));
      if (crossRefRewrites && !String.valueOf(rewritten.charAt(j)).equals(thisChar))
        charLabel = RewriteSymbol;
      if (charLabel == ContinuationSymbol && thisChar.equals("ى") && j != numChars - 1)
        charLabel = RewriteSymbol; // Assume all mid-word alef maqsura are supposed to be yah
      iobList.add(createDatum(cl, thisChar, charLabel, origIndex, origIndex + 1));
    }

    // End at endPosition to make sure we include the whole token according to the tokenizer
    if (!iobList.isEmpty()) {
      iobList.get(iobList.size() - 1).setEndPosition(cl.endPosition());
    }
  }

  private static boolean isDeletedCharacter(char ch, TokenizerFactory<CoreLabel> tf) {
    List<CoreLabel> tokens = tf.getTokenizer(new StringReader(Character.toString(ch))).tokenize();
    return tokens.isEmpty();
  }

  /**
   * Identify tokens that should not be segmented.
   */
  private static boolean shouldNotSegment(String token) {
    return (isDigit.matcher(token).find() ||
            isPunc.matcher(token).find() ||
            notUnicodeArabic.matcher(token).find());
  }

  /**
   * Strip segmentation markers.
   */
  private static String stripSegmentationMarkers(String tok, TokenType tokType) {
    int beginOffset = (tokType == TokenType.BeginMarker || tokType == TokenType.BothMarker) ? 1 : 0;
    int endOffset = (tokType == TokenType.EndMarker || tokType == TokenType.BothMarker) ? tok.length()-1 : tok.length();
    return tokType == TokenType.NoMarker ? tok : tok.substring(beginOffset, endOffset);
  }

  private static CoreLabel createDatum(CoreLabel cl, String token, String label) {
    int endOffset = cl.get(CharacterOffsetEndAnnotation.class) - cl.get(CharacterOffsetBeginAnnotation.class);
    return createDatum(cl, token, label, 0, endOffset);
  }

  /**
   * Create a datum from a string. The CoreAnnotations must correspond to those used by
   * SequenceClassifier. The following annotations are copied from the provided
   * CoreLabel cl, if present:
   *    DomainAnnotation
   * startOffset and endOffset will be added to the {@link CharacterOffsetBeginAnnotation} of
   * the {@link CoreLabel} cl to give the {@link CharacterOffsetBeginAnnotation} and
   * {@link CharacterOffsetEndAnnotation} of the resulting datum.
   */
  private static CoreLabel createDatum(CoreLabel cl, String token, String label, int startOffset, int endOffset) {
    CoreLabel newTok = new CoreLabel();
    newTok.set(CoreAnnotations.TextAnnotation.class, token);
    newTok.set(CoreAnnotations.CharAnnotation.class, token);
    newTok.set(CoreAnnotations.AnswerAnnotation.class, label);
    newTok.set(CoreAnnotations.GoldAnswerAnnotation.class, label);
    newTok.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        cl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) + startOffset);
    newTok.set(CoreAnnotations.CharacterOffsetEndAnnotation.class,
        cl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) + endOffset);
    if (cl != null && cl.containsKey(CoreAnnotations.DomainAnnotation.class))
      newTok.set(CoreAnnotations.DomainAnnotation.class,
                 cl.get(CoreAnnotations.DomainAnnotation.class));
    return newTok;
  }

  /**
   * Deterministically classify a token.
   */
  private static TokenType getTokenType(String token, String segMarker) {
    if (segMarker == null || token.equals(segMarker)) {
      return TokenType.NoMarker;
    }

    TokenType tokType = TokenType.NoMarker;
    boolean startsWithMarker = token.startsWith(segMarker);
    boolean endsWithMarker = token.endsWith(segMarker);

    if (startsWithMarker && endsWithMarker) {
      tokType = TokenType.BothMarker;

    } else if (startsWithMarker) {
      tokType = TokenType.BeginMarker;

    } else if (endsWithMarker) {
      tokType = TokenType.EndMarker;
    }
    return tokType;
  }

  /**
   * This version is for turning an unsegmented string to an IOB input, i.e.,
   * for processing raw text.
   */
  public static List<CoreLabel> StringToIOB(String string) {
    return StringToIOB(string, null);
  }

  public static List<CoreLabel> StringToIOB(String str, Character segMarker) {
    // Whitespace tokenization
    List<CoreLabel> toks = SentenceUtils.toCoreLabelList(str.trim().split("\\s+"));
    return StringToIOB(toks, segMarker, false);
  }

  /**
   * Convert a list of labeled characters to a String. Include segmentation markers
   * for prefixes and suffixes in the string, and add a space at segmentations.
   */
  public static String IOBToString(List<CoreLabel> labeledSequence, String prefixMarker, String suffixMarker) {
    return IOBToString(labeledSequence, prefixMarker, suffixMarker, true, true, 0, labeledSequence.size());
  }

  /**
   * Convert a list of labeled characters to a String. Include segmentation markers
   * for prefixes and suffixes in the string, and add a space at segmentations.
   */
  public static String IOBToString(List<CoreLabel> labeledSequence, String prefixMarker, String suffixMarker,
      int startIndex, int endIndex) {
    return IOBToString(labeledSequence, prefixMarker, suffixMarker, true, true, startIndex, endIndex);
  }

  /**
   * Convert a list of labeled characters to a String. Include segmentation markers
   * (but no spaces) at segmentation boundaries.
   */
  public static String IOBToString(List<CoreLabel> labeledSequence, String segmentationMarker) {
    return IOBToString(labeledSequence, segmentationMarker, null, false, true, 0, labeledSequence.size());
  }

  /**
   * Convert a list of labeled characters to a String. Preserve the original (unsegmented) text.
   */
  public static String IOBToString(List<CoreLabel> labeledSequence) {
    return IOBToString(labeledSequence, null, null, false, false, 0, labeledSequence.size());
  }

  private static String IOBToString(List<CoreLabel> labeledSequence,
      String prefixMarker, String suffixMarker, boolean addSpace, boolean applyRewrites,
      int startIndex, int endIndex) {
    StringBuilder sb = new StringBuilder();
    String lastLabel = "";
    final boolean addPrefixMarker = prefixMarker != null && prefixMarker.length() > 0;
    final boolean addSuffixMarker = suffixMarker != null && suffixMarker.length() > 0;
    if (addPrefixMarker || addSuffixMarker)
      annotateMarkers(labeledSequence);
    for (int i = startIndex; i < endIndex; ++i) {
      CoreLabel labeledChar = labeledSequence.get(i);
      String token = labeledChar.get(CoreAnnotations.CharAnnotation.class);
      if (addPrefixMarker && token.equals(prefixMarker))
        token = "#pm#";
      if (addSuffixMarker && token.equals(suffixMarker))
        token = "#sm#";
      String label = labeledChar.get(CoreAnnotations.AnswerAnnotation.class);
      if (token.equals(BoundaryChar)) {
        sb.append(" ");

      } else if (label.equals(BeginSymbol)) {
        if (lastLabel.equals(ContinuationSymbol) || lastLabel.equals(BeginSymbol) ||
            lastLabel.equals(RewriteSymbol)) {
          if (addPrefixMarker && (!addSpace || addPrefixMarker(i, labeledSequence))) {
            sb.append(prefixMarker);
          }
          if (addSpace) {
            sb.append(" ");
          }
          if (addSuffixMarker && (!addSpace || addSuffixMarker(i, labeledSequence))) {
            sb.append(suffixMarker);
          }
        }
        sb.append(token);

      } else if (label.equals(ContinuationSymbol) || label.equals(BoundarySymbol)) {
        sb.append(token);

      } else if (label.equals(NosegSymbol)) {
        if ( ! lastLabel.equals(BoundarySymbol) && addSpace) {
          sb.append(" ");
        }
        sb.append(token);

      } else if (label.equals(RewriteSymbol) || label.equals("REWAL") || label.equals("REWTA")) {
        switch (token) {
          case "ت":
          case "ه":
            sb.append(applyRewrites ? "ة" : token);
            break;
          case "ل":
            sb.append((addPrefixMarker ? prefixMarker : "") +
                (addSpace ? " " : "") +
                (applyRewrites ? "ال" : "ل"));
            break;
          case "ي":
          case "ا":
            sb.append(applyRewrites ? "ى" : token);
            break;
          case "ى":
            sb.append(applyRewrites ? "ي" : token);
            break;
          default:
            // Nonsense rewrite predicted by the classifier--just assume CONT
            sb.append(token);
            break;
        }
      } else {
        throw new RuntimeException("Unknown label: " + label);
      }
      lastLabel = label;
    }
    return sb.toString().trim();
  }
  
  private static class PrefixMarkerAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }
  
  private static class SuffixMarkerAnnotation implements CoreAnnotation<Boolean> {
    @Override
    public Class<Boolean> getType() {
      return Boolean.class;
    }
  }
  
  private static void annotateMarkers(List<CoreLabel> labeledSequence) {
    StringBuilder segment = new StringBuilder();
    List<String> segments = CollectionUtils.makeList();
    int wordBegin = 0;
    for (int i = 0; i < labeledSequence.size(); i++) {
      String token = labeledSequence.get(i).get(CoreAnnotations.CharAnnotation.class);
      String label = labeledSequence.get(i).get(CoreAnnotations.AnswerAnnotation.class);
      switch (label) {
        case BeginSymbol:
          if (i != wordBegin) {
            segments.add(segment.toString());
            segment.setLength(0);
          }
          segment.append(token);
          break;
        case BoundarySymbol:
          segments.add(segment.toString());
          segment.setLength(0);
          annotateMarkersOnWord(labeledSequence, wordBegin, i, segments);
          segments.clear();
          wordBegin = i + 1;
          break;
        default:
          segment.append(token);
          break;
      }
    }
    segments.add(segment.toString());
    annotateMarkersOnWord(labeledSequence, wordBegin, labeledSequence.size(), segments);
  }

  private static void annotateMarkersOnWord(List<CoreLabel> labeledSequence,
      int wordBegin, int wordEnd, List<String> segments) {
    Pair<Integer, Integer> headBounds = getHeadBounds(segments);
    int currentIndex = 0;
    
    for (int i = wordBegin; i < wordEnd; i++) {
      String label = labeledSequence.get(i).get(CoreAnnotations.AnswerAnnotation.class);
      labeledSequence.get(i).set(PrefixMarkerAnnotation.class, Boolean.FALSE);
      labeledSequence.get(i).set(SuffixMarkerAnnotation.class, Boolean.FALSE);
      if (label.equals(BeginSymbol)) {
        // Add prefix markers for BEGIN characters up to and including the start of the head
        // (but don't add prefix markers if there aren't any prefixes)
        if (currentIndex <= headBounds.first && currentIndex != 0)
          labeledSequence.get(i).set(PrefixMarkerAnnotation.class, Boolean.TRUE);
        
        // Add suffix markers for BEGIN characters starting one past the end of the head
        // (headBounds.second is one past the end, no need to add one)
        if (currentIndex >= headBounds.second)
          labeledSequence.get(i).set(SuffixMarkerAnnotation.class, Boolean.TRUE);
        
        currentIndex++;
      }
    }
  }

  private static Pair<Integer, Integer> getHeadBounds(List<String> segments) {
    final int NOT_FOUND = -1;
    int potentialSuffix = segments.size() - 1;
    int nonSuffix = NOT_FOUND;
    int potentialPrefix = 0;
    int nonPrefix = NOT_FOUND;
    // Heuristic algorithm for finding the head of a segmented word:
    while (true) {
      /* Alternate considering suffixes and prefixes (starting with suffix).
       * 
       * If the current segment is a known Arabic {suffix|prefix}, mark it as
       * such. Otherwise, stop considering tokens from that direction.
       */ 
      if (nonSuffix == NOT_FOUND){
        if (arSuffixSet.contains(segments.get(potentialSuffix)))
          potentialSuffix--;
        else
          nonSuffix = potentialSuffix;
      }
      if (potentialSuffix < potentialPrefix)
        break;
      
      if (nonPrefix == NOT_FOUND) {
        if (arPrefixSet.contains(segments.get(potentialPrefix)))
          potentialPrefix++;
        else
          nonPrefix = potentialPrefix;
      }
      if (potentialSuffix < potentialPrefix || (nonSuffix != NOT_FOUND && nonPrefix != NOT_FOUND))
        break;
    }
    
    /* Once we have exhausted all known prefixes and suffixes, take the longest
     * segment that remains to be the head. Break length ties by picking the first one.
     * 
     * Note that in some cases, no segments will remain (e.g. b# +y), so a
     * segmented word may have zero or one heads, but never more than one.
     */
    if (potentialSuffix < potentialPrefix) {
      // no head--start and end are index of first suffix
      if (potentialSuffix + 1 != potentialPrefix)
        throw new RuntimeException("Suffix pointer moved too far!");
      return Pair.makePair(potentialSuffix + 1, potentialSuffix + 1);
    } else {
      int headIndex = nonPrefix;
      for (int i = nonPrefix + 1; i <= nonSuffix; i++) {
        if (segments.get(i).length() > segments.get(headIndex).length())
          headIndex = i;
      }
      return Pair.makePair(headIndex, headIndex + 1);
    }
  }

  private static boolean addPrefixMarker(int focus, List<CoreLabel> labeledSequence) {
    return labeledSequence.get(focus).get(PrefixMarkerAnnotation.class).booleanValue();
  }

  private static boolean addSuffixMarker(int focus, List<CoreLabel> labeledSequence) {
    return labeledSequence.get(focus).get(SuffixMarkerAnnotation.class).booleanValue();
  }

  public static void labelDomain(List<CoreLabel> tokenList, String domain) {
    for (CoreLabel cl : tokenList) {
      cl.set(CoreAnnotations.DomainAnnotation.class, domain);
    }
  }

  public static List<IntPair> TokenSpansForIOB(List<CoreLabel> labeledSequence) {
    List<IntPair> spans = CollectionUtils.makeList();

    String lastLabel = "";
    boolean inToken = false;
    int tokenStart = 0;
    final int sequenceLength = labeledSequence.size();
    for (int i = 0; i < sequenceLength; ++i) {
      CoreLabel labeledChar = labeledSequence.get(i);
      String token = labeledChar.get(CoreAnnotations.CharAnnotation.class);
      String label = labeledChar.get(CoreAnnotations.AnswerAnnotation.class);
      if (token.equals(BoundaryChar)) {
        if (inToken) {
          spans.add(new IntPair(tokenStart, i));
        }
        inToken = false;
      } else {
        switch(label) {
          case BeginSymbol:
            if (lastLabel.equals(ContinuationSymbol) || lastLabel.equals(BeginSymbol) ||
                lastLabel.equals(RewriteSymbol)) {
              if (inToken) {
                spans.add(new IntPair(tokenStart, i));
              }
              inToken = true;
              tokenStart = i;
            } else if (!inToken) {
              inToken = true;
              tokenStart = i;
            }
            break;

          case ContinuationSymbol:
            if (!inToken) {
              inToken = true;
              tokenStart = i;
            }
            break;

          case BoundarySymbol:
          case NosegSymbol:
            if (inToken) {
              spans.add(new IntPair(tokenStart, i));
            }
            inToken = true;
            tokenStart = i;
            break;

          case RewriteSymbol:
          case "REWAL":
          case "REWTA":
            if (token.equals("ل")) {
              if (inToken) {
                spans.add(new IntPair(tokenStart, i));
              }
              inToken = true;
              tokenStart = i;
            } else if (!inToken) {
              inToken = true;
              tokenStart = i;
            }
            break;
        }
      }
      lastLabel = label;
    }

    if (inToken) {
      spans.add(new IntPair(tokenStart, sequenceLength));
    }

    return spans;
  }

}
