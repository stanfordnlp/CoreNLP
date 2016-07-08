package edu.stanford.nlp.pipeline;

import java.util.*;

import edu.stanford.nlp.international.arabic.process.ArabicSegmenter;
import edu.stanford.nlp.ling.ChineseCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.WordSegmenter;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
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
  private static Redwood.RedwoodChannels log = Redwood.channels(ArabicSegmenterAnnotator.class);

  private WordSegmenter segmenter;
  private final boolean VERBOSE;

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

  private void doOneSentence(CoreMap annotation) {
    splitCharacters(annotation);
    runSegmentation(annotation);
  }

  private static void splitCharacters(CoreMap annotation) {
    String origText = annotation.get(CoreAnnotations.TextAnnotation.class);

    boolean seg = true;
    List<CoreLabel> words = new ArrayList<>();

    for (int i = 0; i < origText.length(); i++) {
      CoreLabel wi = new CoreLabel();
      char[] ca = {origText.charAt(i)};
      String wordString = new String(ca);

      // if this word is a whitespace or a control character, set 'seg' to true for next word, and break
      if (Character.isWhitespace(origText.charAt(i)) || Character.isISOControl(origText.charAt(i))) {
        seg = true;
      } else {
        // if this word is a word, put it as a feature label and set seg to false for next word
        wi.set(CoreAnnotations.ChineseCharAnnotation.class, wordString);
        if (seg) {
          wi.set(CoreAnnotations.ChineseSegAnnotation.class, "1");
        } else {
          wi.set(CoreAnnotations.ChineseSegAnnotation.class, "0");
        }
        wi.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, i);
        wi.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, (i + 1));
        words.add(wi);
        seg = false;
      }
    }

    annotation.set(ChineseCoreAnnotations.CharactersAnnotation.class, words);
  }

  private void runSegmentation(CoreMap annotation) {
    //0 2
    // A BC D E
    // 1 10 1 1
    // 0 12 3 4
    // 0, 0+1 ,

    String text = annotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreLabel> sentChars = annotation.get(ChineseCoreAnnotations.CharactersAnnotation.class);
    List<CoreLabel> tokens = new ArrayList<>();
    annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);

    List<HasWord> words = segmenter.segment(text);
    if (VERBOSE) {
      log.info(text);
      log.info("--->");
      log.info(words);
    }

    int pos = 0;
    for (HasWord w : words) {
      String word = w.word();
      CoreLabel fl = sentChars.get(pos);
      fl.set(CoreAnnotations.ChineseSegAnnotation.class, "1");
      if (word.isEmpty()) {
        continue;
      }
      CoreLabel token = new CoreLabel();
      token.setWord(word);
      token.setValue(word);
      token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, fl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
      pos += word.length();
      fl = sentChars.get(pos - 1);
      token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, fl.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
      tokens.add(token);
    }
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
