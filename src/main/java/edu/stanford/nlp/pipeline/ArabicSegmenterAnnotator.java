package edu.stanford.nlp.pipeline;

import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.international.arabic.process.ArabicSegmenter;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.AbstractTokenizer;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * This class will add segmentation information to an Annotation.
 * It assumes that the original document is a List of sentences under the
 * SentencesAnnotation.class key, and that each sentence has a
 * TextAnnotation.class key. This Annotator adds corresponding
 * information under a CharactersAnnotation.class key prior to segmentation,
 * and a TokensAnnotation.class key with value of a List of CoreLabel
 * after segmentation.
 *
 * Based on the ChineseSegmenterAnnotator by Pi-Chuan Chang.
 *
 * @author Will Monroe
 */
public class ArabicSegmenterAnnotator implements Annotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ArabicSegmenterAnnotator.class);

  private ArabicSegmenter segmenter;
  private final boolean VERBOSE;
  private final boolean tokenizeNewline;
  private final boolean sentenceSplitOnTwoNewlines;

  private static final String DEFAULT_SEG_LOC =
    "/u/nlp/data/arabic-segmenter/arabic-segmenter-atb+bn+arztrain.ser.gz";

  public ArabicSegmenterAnnotator() {
    this(DEFAULT_SEG_LOC, false);
  }

  public ArabicSegmenterAnnotator(boolean verbose) {
    this(DEFAULT_SEG_LOC, verbose);
  }

  public ArabicSegmenterAnnotator(String segLoc, boolean verbose) {
    VERBOSE = verbose;
    Properties props = new Properties();
    loadModel(segLoc, props);
    tokenizeNewline = false;
    sentenceSplitOnTwoNewlines = false;
  }

  public ArabicSegmenterAnnotator(String name, Properties props) {
    String model = null;
    // Keep only the properties that apply to this annotator
    Properties modelProps = new Properties();
    String desiredKey = name + '.';
    for (String key : props.stringPropertyNames()) {
      if (key.startsWith(desiredKey)) {
        // skip past name and the subsequent "."
        String modelKey = key.substring(desiredKey.length());
        if (modelKey.equals("model")) {
          model = props.getProperty(key);
        } else {
          modelProps.setProperty(modelKey, props.getProperty(key));
        }
      }
    }
    this.VERBOSE = PropertiesUtils.getBool(props, name + ".verbose", false);
    if (model == null) {
      throw new RuntimeException("Expected a property " + name + ".model");
    }
    loadModel(model, modelProps);

    // TODO: unify with ChineseSegmenterAnnotator somehow?
    // The issue here is the Chinese segmenter returns text chunks and
    // the Arabic segmenter has a method which returns CoreLabels, so
    // the project of unifying the two into one ur-SegmenterAnnotator
    // is larger than simply ripping some code into a superclass and
    // calling it a day

    // If newlines are treated as sentence split, we need to retain them in tokenization for ssplit to make use of them
    tokenizeNewline = (!props.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY, "never").equals("never"))
            || Boolean.valueOf(props.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));

    // record whether or not sentence splitting on two newlines ; if so, need to remove single newlines
    sentenceSplitOnTwoNewlines =
        props.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY, "never").equals("two");
  }

  @SuppressWarnings("unused")
  private void loadModel(String segLoc) {
    // don't write very much, because the CRFClassifier already reports loading
    if (VERBOSE) {
      log.info("Loading segmentation model ... ");
    }
    Properties modelProps = new Properties();
    modelProps.setProperty("model", segLoc);
    segmenter = ArabicSegmenter.getSegmenter(modelProps);
  }

  private void loadModel(String segLoc, Properties props) {
    // don't write very much, because the CRFClassifier already reports loading
    if (VERBOSE) {
      log.info("Loading Segmentation Model ... ");
    }
    Properties modelProps = new Properties();
    modelProps.setProperty("model", segLoc);
    modelProps.putAll(props);
    try {
      segmenter = ArabicSegmenter.getSegmenter(modelProps);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      log.info("Adding Segmentation annotation ... ");
    }
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    if (sentences != null) {
      for (CoreMap sentence : sentences) {
        doOneSentence(sentence);
      }
    } else {
      doOneSentence(annotation);
    }
  }

  private static final String NEWLINE_REGEX = "\\R";
  private static final Pattern NEWLINE_PATTERN = Pattern.compile(NEWLINE_REGEX);

  private CoreLabel makeNewlineCoreLabel(String piece, int stringConsumed) {
    // TODO: do we need to set other stuff like PositionAnnotations, etc?
    CoreLabel newline = new CoreLabel();
    newline.setWord(AbstractTokenizer.NEWLINE_TOKEN);
    newline.setValue(AbstractTokenizer.NEWLINE_TOKEN);
    newline.set(CoreAnnotations.OriginalTextAnnotation.class, piece);
    newline.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, stringConsumed);
    newline.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, stringConsumed + piece.length());
    return newline;
  }

  private void doOneSentence(CoreMap annotation) {
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreLabel> tokens;
    if (!tokenizeNewline) {
      tokens = segmenter.segmentStringToTokenList(text);
    } else {
      tokens = new ArrayList<>();
      List<String> pieces = StringUtils.splitLinesKeepNewlines(text);
      int stringConsumed = 0;
      boolean sawNewline = false;
      boolean sawTwoNewlines = false;
      for (String piece : pieces) {
        if (NEWLINE_PATTERN.matcher(piece).matches()) {
          if (!sawNewline) {
            // Found a newline we care about
            tokens.add(makeNewlineCoreLabel(piece, stringConsumed));
            sawNewline = true;
          } else if (sentenceSplitOnTwoNewlines && !sawTwoNewlines) {
            // Guess we care about this one two
            // Others we skip
            tokens.add(makeNewlineCoreLabel(piece, stringConsumed));
            sawTwoNewlines = true;
          }
        } else {
          sawNewline = false;
          sawTwoNewlines = false;
          List<CoreLabel> pieceTokens = segmenter.segmentStringToTokenList(piece);
          for (CoreLabel label : pieceTokens) {
            // TODO: what about updating other annotations?
            // The javadoc for TokenBeginAnnotation is incredibly useless.
            // PositionAnnotation doesn't have any documentation at all.
            // It's not clear which is worse
            label.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class,
                      label.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) + stringConsumed);
            label.set(CoreAnnotations.CharacterOffsetEndAnnotation.class,
                      label.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) + stringConsumed);
          }
          tokens.addAll(pieceTokens);
        }
        stringConsumed = stringConsumed + piece.length();
      }
    }
    annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);
  }


  @Override
  public Set<Class<? extends CoreAnnotation>> requires() {
    return Collections.emptySet();
  }

  @Override
  public Set<Class<? extends CoreAnnotation>> requirementsSatisfied() {
    return new HashSet<>(Arrays.asList(
        CoreAnnotations.TextAnnotation.class,
        CoreAnnotations.TokensAnnotation.class,
        CoreAnnotations.CharacterOffsetBeginAnnotation.class,
        CoreAnnotations.CharacterOffsetEndAnnotation.class,
        CoreAnnotations.BeforeAnnotation.class,
        CoreAnnotations.AfterAnnotation.class,
        CoreAnnotations.TokenBeginAnnotation.class,
        CoreAnnotations.TokenEndAnnotation.class,
        CoreAnnotations.PositionAnnotation.class,
        CoreAnnotations.IndexAnnotation.class,
        CoreAnnotations.OriginalTextAnnotation.class,
        CoreAnnotations.ValueAnnotation.class
    ));
  }

}
