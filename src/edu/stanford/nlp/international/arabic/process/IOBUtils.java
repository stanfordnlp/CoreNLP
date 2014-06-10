package edu.stanford.nlp.international.arabic.process;

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
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.Generics;

/**
 * A class for converting strings to input suitable for processing by
 * and IOB sequence model.
 *
 * @author Spence Green
 *
 */
public class IOBUtils {

  // Training token types.
  private enum TokenType { BeginMarker, EndMarker, BothMarker, NoMarker }

  // Label inventory
  private static final String BeginSymbol = "BEGIN";
  private static final String ContinuationSymbol = "CONT";
  private static final String NosegSymbol = "NOSEG";
  private static final String BoundarySymbol = ".##.";
  private static final String BoundaryChar = ".#.";
  private static final String RewriteTahSymbol = "REWTA";
  private static final String RewriteTareefSymbol = "REWAL";

  // Patterns for tokens that should not be segmented.
  private static final Pattern isPunc = Pattern.compile("\\p{Punct}+");
  private static final Pattern isDigit = Pattern.compile("\\p{Digit}+");
  private static final Pattern notUnicodeArabic = Pattern.compile("\\P{InArabic}+");

  // The set of clitics segmented in the ATBv3 training set (see the annotation guidelines).
  // We need this list for tagging the clitics when reconstructing the segmented sequences.
  private static final Set<String> arAffixSet;
  static {
    String arabicAffixString = "ل ف و ما ه ها هم هن نا كم تن تم ى ي هما ك ب م س";
    arAffixSet = Collections.unmodifiableSet(Generics.newHashSet(Arrays.asList(arabicAffixString.split("\\s+"))));
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
    List<CoreLabel> iobList = new ArrayList<CoreLabel>(tokenList.size()*7 + tokenList.size());
    final String strSegMarker = String.valueOf(segMarker);

    boolean addWhitespace = false;
    int charIndex = 0;
    final int numTokens = tokenList.size();
    String lastToken = "";
    for (int i = 0; i < numTokens; ++i) {
      if (addWhitespace) {
        iobList.add(createDatum(BoundaryChar, BoundarySymbol, charIndex++));
        addWhitespace = false;
      }

      // What type of token is this?
      String token = tokenList.get(i).word();
      TokenType tokType = getTokenType(token, strSegMarker);
      token = stripSegmentationMarkers(token, tokType);
      assert token.length() != 0;

      if (shouldNotSegment(token)) {
        iobList.add(createDatum(token, NosegSymbol, charIndex++));
        addWhitespace = true;

      } else {
        // Iterate over the characters in the token
        tokenToDatums(iobList, token, tokType, tokenList.get(i), lastToken, charIndex, applyRewriteRules);
        addWhitespace = (tokType == TokenType.BeginMarker || tokType == TokenType.NoMarker);
      }
      lastToken = token;
    }
    return iobList;
  }

  /**
   * Convert token to a sequence of datums and add to iobList.
   *
   * @param iobList
   * @param token
   * @param tokType
   * @param tokenLabel
   * @param lastToken
   * @param charIndex
   * @param applyRewriteRules
   */
  private static void tokenToDatums(List<CoreLabel> iobList,
                                String token,
                                TokenType tokType, 
                                CoreLabel tokenLabel,
                                String lastToken,
                                int charIndex,
                                boolean applyRewriteRules) {

    String lastLabel = ContinuationSymbol;
    String firstLabel = BeginSymbol;
    if (applyRewriteRules) {
      // Apply Arabic-specific re-write rules
      String rawToken = tokenLabel.word();
      String tag = tokenLabel.tag();
      MorphoFeatureSpecification featureSpec = new ArabicMorphoFeatureSpecification();
      featureSpec.activate(MorphoFeatureType.NGEN);
      featureSpec.activate(MorphoFeatureType.NNUM);
      MorphoFeatures features = featureSpec.strToFeatures(tag);

      // Rule #1 : ت --> ة
      if (features.getValue(MorphoFeatureType.NGEN).equals("F") &&
          features.getValue(MorphoFeatureType.NNUM).equals("SG") &&
          rawToken.endsWith("ت-")) {
        lastLabel = RewriteTahSymbol;
      }

      // Rule #2 : لل --> ل ال
      if (lastToken.equals("ل") && rawToken.startsWith("-ل")) {
        firstLabel = RewriteTareefSymbol;
      }
    }

    // Create datums and add to iobList
    String firstChar = String.valueOf(token.charAt(0));
    iobList.add(createDatum(firstChar, firstLabel, charIndex++));
    final int numChars = token.length();
    for (int j = 1; j < numChars; ++j) {
      String thisChar = String.valueOf(token.charAt(j));
      String charLabel = (j == numChars-1) ? lastLabel : ContinuationSymbol;
      iobList.add(createDatum(thisChar, charLabel, charIndex++));
    }
  }

  /**
   * Identify tokens that should not be segmented.
   *
   * @param token
   * @return
   */
  private static boolean shouldNotSegment(String token) {
    return (isDigit.matcher(token).find() ||
            isPunc.matcher(token).find() ||
            notUnicodeArabic.matcher(token).find());
  }

  /**
   * Strip segmentation markers.
   *
   * @param tok
   * @param tokType
   * @return
   */
  private static String stripSegmentationMarkers(String tok, TokenType tokType) {
    int beginOffset = (tokType == TokenType.BeginMarker || tokType == TokenType.BothMarker) ? 1 : 0;
    int endOffset = (tokType == TokenType.EndMarker || tokType == TokenType.BothMarker) ? tok.length()-1 : tok.length();
    return tokType == TokenType.NoMarker ? tok : tok.substring(beginOffset, endOffset);
  }

  /**
   * Create a datum from a string. The CoreAnnotations must correspond to those used by
   * SequenceClassifier.
   *
   * @param token
   * @param label
   * @param index
   * @return
   */
  private static CoreLabel createDatum(String token, String label, int index) {
    CoreLabel newTok = new CoreLabel();
    newTok.set(CoreAnnotations.CharAnnotation.class, token);
    newTok.set(CoreAnnotations.AnswerAnnotation.class, label);
    newTok.set(CoreAnnotations.GoldAnswerAnnotation.class, label);
    newTok.setIndex(index);
    return newTok;
  }

  /**
   * Deterministically classify a token.
   *
   * @param token
   * @param segMarker
   * @return
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
   *
   * @param string
   */
  public static List<CoreLabel> StringToIOB(String string) {
    return StringToIOB(string, null);
  }

  public static List<CoreLabel> StringToIOB(String str, Character segMarker) {
    // Whitespace tokenization
    List<CoreLabel> toks = Sentence.toCoreLabelList(str.trim().split("\\s+"));
    return StringToIOB(toks, segMarker, false);
  }

  /**
   * Convert a list of labeled characters to a String. Include segmentation markers
   * in the string.
   *
   * @param labeledSequence
   * @param prefixMarker
   */
  public static String IOBToString(List<CoreLabel> labeledSequence, String prefixMarker, String suffixMarker) {
    StringBuilder sb = new StringBuilder();
    String lastLabel = "";
    final boolean addPrefixMarker = prefixMarker != null && prefixMarker.length() > 0;
    final boolean addSuffixMarker = suffixMarker != null && suffixMarker.length() > 0;
    final int sequenceLength = labeledSequence.size();
    for (int i = 0; i < sequenceLength; ++i) {
      CoreLabel labeledChar = labeledSequence.get(i);
      String token = labeledChar.get(CoreAnnotations.CharAnnotation.class);
      String label = labeledChar.get(CoreAnnotations.AnswerAnnotation.class);
      if (label.equals(BeginSymbol)) {
        if (lastLabel.equals(ContinuationSymbol) || lastLabel.equals(BeginSymbol)) {
          if (addPrefixMarker && addPrefixMarker(i, labeledSequence)) {
            sb.append(prefixMarker);
          }
          sb.append(" ");
          if (addSuffixMarker && addSuffixMarker(i, labeledSequence)) {
            sb.append(suffixMarker);
          }
        }
        sb.append(token);

      } else if (label.equals(ContinuationSymbol)) {
        sb.append(token);

      } else if (label.equals(NosegSymbol)) {
        if ( ! lastLabel.equals(BoundarySymbol)) {
          sb.append(" ");
        }
        sb.append(token);

      } else if (label.equals(BoundarySymbol)) {
        sb.append(" ");

      } else if (label.equals(RewriteTahSymbol)) {
        sb.append("ة ");
        if (addSuffixMarker) sb.append(suffixMarker);

      } else if (label.equals(RewriteTareefSymbol)) {
        if (addPrefixMarker) sb.append(prefixMarker);
        sb.append(" ال");

      } else {
        throw new RuntimeException("Unknown label: " + label);
      }
      lastLabel = label;
    }
    return sb.toString().trim();
  }

  private static boolean addPrefixMarker(int focus, List<CoreLabel> labeledSequence) {
    StringBuilder sb = new StringBuilder();
    for (int i = focus-1; i >= 0; --i) {
      String token = labeledSequence.get(i).get(CoreAnnotations.CharAnnotation.class);
      String label = labeledSequence.get(i).get(CoreAnnotations.AnswerAnnotation.class);
      sb.append(token);
      if (label.equals(BeginSymbol) || label.equals(BoundarySymbol)) {
        break;
      }
    }
    return arAffixSet.contains(sb.toString());
  }

  private static boolean addSuffixMarker(int focus, List<CoreLabel> labeledSequence) {
    StringBuilder sb = new StringBuilder();
    for (int i = focus; i < labeledSequence.size(); ++i) {
      String token = labeledSequence.get(i).get(CoreAnnotations.CharAnnotation.class);
      String label = labeledSequence.get(i).get(CoreAnnotations.AnswerAnnotation.class);
      if (label.equals(BoundarySymbol)) {
        break;
      } else if (i != focus && label.equals(BeginSymbol)) {
        return false;
      }
      sb.append(token);
    }
    return arAffixSet.contains(sb.toString());
  }
}
