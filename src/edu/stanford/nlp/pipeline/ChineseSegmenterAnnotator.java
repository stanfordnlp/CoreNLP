package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.ChineseCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.Timing;

/**
 * This class will add Segmentation information to an
 * Annotation.  
 * It assumes that the original String or List<String> is under the Annotation.ORIG_STRING_KEY 
 * and also corresponding character level information is under Annotation.WORDS_KEY
 * and addes segmentation information to each CoreLabel,
 * in the CoreLabel.CH_SEG_KEY field.
 *
 * @author Pi-Chuan Chang
 */
public class ChineseSegmenterAnnotator implements Annotator {

  private AbstractSequenceClassifier<?> segmenter = null;

  private Timing timer = new Timing();
  private static long millisecondsAnnotating = 0;
  private boolean VERBOSE = true;
  
  private static final String DEFAULT_SEG_LOC =
    "/u/nlp/data/gale/segtool/stanford-seg/classifiers-2010/05202008-ctb6.processed-chris6.lex.gz";

  private static final String DEFAULT_SER_DICTIONARY =
    "/u/nlp/data/gale/segtool/stanford-seg/classifiers/dict-chris6.ser.gz";

  private static final String DEFAULT_SIGHAN_CORPORA_DICT =
    "/u/nlp/data/gale/segtool/stanford-seg/releasedata";

  public ChineseSegmenterAnnotator() {
    this(DEFAULT_SEG_LOC, true);
  }

  public ChineseSegmenterAnnotator(boolean verbose) {
    this(DEFAULT_SEG_LOC, verbose);
  }
  
  public ChineseSegmenterAnnotator(String segLoc, boolean verbose) {
    this(segLoc, verbose, DEFAULT_SER_DICTIONARY, DEFAULT_SIGHAN_CORPORA_DICT);
  }

  public ChineseSegmenterAnnotator(String segLoc, boolean verbose, String serDictionary, String sighanCorporaDict) {
    VERBOSE = verbose;
    Properties props = new Properties();
    props.setProperty("serDictionary", serDictionary);
    props.setProperty("sighanCorporaDict", sighanCorporaDict);
    loadModel(segLoc, props);
  }

  public ChineseSegmenterAnnotator(String name, Properties props) {
    String model = null;
    // Keep only the properties that apply to this annotator
    Properties modelProps = new Properties();
    for (String key : props.stringPropertyNames()) {
      if (key.startsWith(name + ".")) {
        // skip past name and the subsequent "."
        String modelKey = key.substring(name.length() + 1);
        if (modelKey.equals("model")) {
          model = props.getProperty(key);
        } else {
          modelProps.setProperty(modelKey, props.getProperty(key));
        }
      }
    }
    this.VERBOSE = PropertiesUtils.getBool(props, name + ".verbose", true);
    if (model == null) {
      throw new RuntimeException("Expected a property " + name + ".model");
    }
    loadModel(model, modelProps);
  }
  
  private void loadModel(String segLoc) {
    if (VERBOSE) {    
      timer.start();
      System.err.print("Loading Segmentation Model ["+segLoc+"]...");
    }
    segmenter = CRFClassifier.getClassifierNoExceptions(segLoc);
    if (VERBOSE) {    
      timer.stop("done.");
    }
  }
  
  private void loadModel(String segLoc, Properties props) {
    if (VERBOSE) {    
      timer.start();
      System.err.print("Loading Segmentation Model ["+segLoc+"]...");
    }
    try {
      segmenter = CRFClassifier.getClassifier(segLoc, props);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    if (VERBOSE) {    
      timer.stop("done.");
    }
  }
  
  public void annotate(Annotation annotation) {
    if (VERBOSE) {    
      timer.start();
      System.err.print("Adding Segmentation annotation...");
    }
    List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
    if (sentences != null) {
      for (CoreMap sentence : sentences) {
        doOneSentence(sentence);
      }
    } else {
      doOneSentence(annotation);
    }
    if (VERBOSE) {    
      millisecondsAnnotating += timer.stop("done.");
      //System.err.println("output: "+l+"\n"); 
    }    
  }

  public void doOneSentence(CoreMap annotation) {
    splitCharacters(annotation);
    runSegmentation(annotation);
  }

  public void splitCharacters(CoreMap annotation) {
    String origText = annotation.get(CoreAnnotations.TextAnnotation.class);
    
    boolean seg = true;
    List<CoreLabel> words = new ArrayList<CoreLabel>();

    for (int i = 0; i < origText.length(); i++) {
      CoreLabel wi = new CoreLabel();
      char[] ca = {origText.charAt(i)};
      String wordString = new String(ca);

      // if this word is a whitespace or a control character, set 'seg' to true for next word, and break
      if (Character.isWhitespace(origText.charAt(i)) || Character.isISOControl(origText.charAt(i))) {
        seg = true;
        continue;
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
    if (VERBOSE) {
      System.err.println("output: " + words);
    }    
  }

  public void runSegmentation(CoreMap annotation) {
    //0 2
    // A BC D E
    // 1 10 1 1
    // 0 12 3 4
    // 0, 0+1 , 
    
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreLabel> sentChars = annotation.get(ChineseCoreAnnotations.CharactersAnnotation.class);
    List<CoreLabel> tokens = new ArrayList<CoreLabel>();
    annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);

    List<String> words = segmenter.segmentString(text);
    if (VERBOSE) {
      System.err.println(text);
      System.err.println("--->");
      System.err.println(words);
    }
    
    int pos = 0;
    for (String w : words) {
      CoreLabel fl = sentChars.get(pos);
      fl.set(CoreAnnotations.ChineseSegAnnotation.class, "1");
      CoreLabel token = new CoreLabel();
      token.setWord(w);
      token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, fl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
      pos += w.length();
      fl = sentChars.get(pos - 1);
      token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, fl.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
      tokens.add(token);
    }
  }


  @Override
  public Set<Requirement> requires() {
    return Collections.emptySet();
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(TOKENIZE_REQUIREMENT);
  }
}
