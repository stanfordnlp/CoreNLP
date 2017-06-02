package edu.stanford.nlp.pipeline;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.SegmenterCoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.PropertiesUtils;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * This class will add segmentation information to an Annotation.
 * It assumes that the original document is a List of sentences under the
 * {@code SentencesAnnotation.class} key, and that each sentence has a
 * {@code TextAnnotation.class key}. This Annotator adds corresponding
 * information under a {@code CharactersAnnotation.class} key prior to segmentation,
 * and a {@code TokensAnnotation.class} key with value of a List of CoreLabel
 * after segmentation.
 *
 * @author Pi-Chuan Chang
 */
public class ChineseSegmenterAnnotator implements Annotator  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(ChineseSegmenterAnnotator.class);

  private static final String DEFAULT_MODEL_NAME = "segment";

  private static final String DEFAULT_SEG_LOC =
    "/u/nlp/data/gale/segtool/stanford-seg/classifiers-2010/05202008-ctb6.processed-chris6.lex.gz";

  private static final String DEFAULT_SER_DICTIONARY =
    "/u/nlp/data/gale/segtool/stanford-seg/classifiers/dict-chris6.ser.gz";

  private static final String DEFAULT_SIGHAN_CORPORA_DICT =
    "/u/nlp/data/gale/segtool/stanford-seg/releasedata";


  private final AbstractSequenceClassifier<?> segmenter;
  private final boolean VERBOSE;


  public ChineseSegmenterAnnotator() {
    this(DEFAULT_SEG_LOC, false);
  }

  public ChineseSegmenterAnnotator(boolean verbose) {
    this(DEFAULT_SEG_LOC, verbose);
  }

  public ChineseSegmenterAnnotator(String segLoc, boolean verbose) {
    this(segLoc, verbose, DEFAULT_SER_DICTIONARY, DEFAULT_SIGHAN_CORPORA_DICT);
  }

  public ChineseSegmenterAnnotator(String segLoc, boolean verbose, String serDictionary, String sighanCorporaDict) {
    this(DEFAULT_MODEL_NAME,
            PropertiesUtils.asProperties(
                    DEFAULT_MODEL_NAME + ".serDictionary", serDictionary,
                    DEFAULT_MODEL_NAME + ".sighanCorporaDict", sighanCorporaDict,
                    DEFAULT_MODEL_NAME + ".verbose", Boolean.toString(verbose),
                    DEFAULT_MODEL_NAME + ".model", segLoc));
  }

  public ChineseSegmenterAnnotator(String name, Properties props) {
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
    // don't write very much, because the CRFClassifier already reports loading
    if (VERBOSE) {
      log.info("Loading Segmentation Model ... ");
    }
    try {
      segmenter = CRFClassifier.getClassifier(model, modelProps);
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

  /** This is based on the "SGML2" pattern from PTBLexer.flex. */
  private static final Pattern xmlPattern =
          Pattern.compile("<([!?][A-Za-z-][^>\r\n]*|[A-Za-z][A-Za-z0-9_:.-]*([ ]+([A-Za-z][A-Za-z0-9_:.-]*|[A-Za-z][A-Za-z0-9_:.-]*[ ]*=[ ]*('[^'\r\n]*'|\"[^\"\r\n]*\"|[A-Za-z][A-Za-z0-9_:.-]*)))*[ ]*/?|/[A-Za-z][A-Za-z0-9_:.-]*)[ ]*>");

  /** This gets the TextAnnotation and creates a CharactersAnnotation, where, roughly,
   *  the text has been separated into one character non-whitespace tokens with ChineseCharAnnotation, and with
   *  a ChineseSegAnnotation marking the ones after whitespace, so that there will definitely
   *  be word segmentation there. In 2016, two improvements were added: Handling non-BMP characters
   *  correctly and not splitting on whitespace in the same types of XML places that are recognized by
   *  English PTBTokenizer.
   *
   *  @param annotation The annotation to process
   */
  private static void splitCharacters(CoreMap annotation) {
    String origText = annotation.get(CoreAnnotations.TextAnnotation.class);
    boolean seg = true;
    List<CoreLabel> charTokens = new ArrayList<>();
    int length = origText.length();

    int xmlStartOffset = Integer.MAX_VALUE;
    int xmlEndOffset = -1;
    Matcher m = xmlPattern.matcher(origText);
    if (m.find()) {
      xmlStartOffset = m.start();
      xmlEndOffset = m.end();
    }

    for (int offset = 0, cpCharCount; offset < length; offset += cpCharCount) {
      int cp = origText.codePointAt(offset);
      cpCharCount = Character.charCount(cp);
      CoreLabel wi = new CoreLabel();
      String charString = origText.substring(offset, offset + cpCharCount); // new Java 8 substring, don't need to copy.

      if (offset == xmlEndOffset) {
        // reset with another search
        m = xmlPattern.matcher(origText);
        if (m.find(offset)) {
          xmlStartOffset = m.start();
          xmlEndOffset = m.end();
        }
      }
      boolean skipCharacter = false;
      boolean isXMLCharacter = false;
      // first two cases are for XML region
      if (offset == xmlStartOffset) {
        seg = true;
        isXMLCharacter = true;
      } else if (offset > xmlStartOffset && offset < xmlEndOffset) {
        seg = false;
        isXMLCharacter = true;
      } else if (Character.isSpaceChar(cp) || Character.isISOControl(cp)) {
        // if this word is a whitespace or a control character, set 'seg' to true for next character
        seg = true;
        skipCharacter = true;
      }
      if ( ! skipCharacter) {
        // if this character is a character, put it in as a CoreLabel and set seg to false for next word
        wi.set(CoreAnnotations.ChineseCharAnnotation.class, charString);
        if (seg) {
          wi.set(CoreAnnotations.ChineseSegAnnotation.class, "1");
        } else {
          wi.set(CoreAnnotations.ChineseSegAnnotation.class, "0");
        }
        if (isXMLCharacter) {
          if (Character.isSpaceChar(cp) || Character.isISOControl(cp)) {
            // We mark XML whitespace with a special tag because later they will be handled differently
            // than non-whitespace XML characters. This is because the segmenter eats whitespaces...
            wi.set(SegmenterCoreAnnotations.XMLCharAnnotation.class, "whitespace");
          } else {
            wi.set(SegmenterCoreAnnotations.XMLCharAnnotation.class, "1");
          }
        } else {
          wi.set(SegmenterCoreAnnotations.XMLCharAnnotation.class, "0");
        }
        wi.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, offset);
        wi.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, (offset + cpCharCount));
        charTokens.add(wi);
        seg = false;
      }
    }

    annotation.set(SegmenterCoreAnnotations.CharactersAnnotation.class, charTokens);
  }

  private void runSegmentation(CoreMap annotation) {
    //0 2
    // A BC D E
    // 1 10 1 1
    // 0 12 3 4
    // 0, 0+1 ,

    String text = annotation.get(CoreAnnotations.TextAnnotation.class); // the original text String
    List<CoreLabel> sentChars = annotation.get(SegmenterCoreAnnotations.CharactersAnnotation.class); // the way it was divided by splitCharacters
    List<CoreLabel> tokens = new ArrayList<>();
    annotation.set(CoreAnnotations.TokensAnnotation.class, tokens);

    // Run the segmenter! On the whole String. It knows not about the splitting into chars.
    // Can we change this to have it run directly on the already existing list of tokens. That would help, no?
    List<String> words = segmenter.segmentString(text);
    if (VERBOSE) {
      log.info(text + "--->" + words);
    }

    int pos = 0; // This is used to index sentChars, the output from splitCharacters
    StringBuilder xmlbuffer = new StringBuilder();
    int xmlbegin = -1;
    for (String w : words) {
      CoreLabel fl = sentChars.get(pos);

      if (!fl.get(SegmenterCoreAnnotations.XMLCharAnnotation.class).equals("0")) {
        // found an XML character
        while (fl.get(SegmenterCoreAnnotations.XMLCharAnnotation.class).equals("whitespace")) {
          // Print whitespaces into the XML buffer and move on until the next non-whitespace character is found
          // and we're in sync with segmenter output again
          xmlbuffer.append(" ");
          pos += 1;
          fl = sentChars.get(pos);
        }

        xmlbuffer.append(w);
        pos += w.length();
        if (xmlbegin < 0) xmlbegin = fl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        continue;
      } else {
        if (xmlbuffer.length() > 0) {
          // Form the XML token
          String xmltag = xmlbuffer.toString();
          CoreLabel token = new CoreLabel();
          token.setWord(xmltag);
          token.setValue(xmltag);
          token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, xmlbegin);
          CoreLabel fl1 = sentChars.get(pos - 1);
          token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, fl1.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
          tokens.add(token);

          // Clean up and prepare for the next XML tag
          xmlbegin = -1;
          xmlbuffer = new StringBuilder();
        }
      }
      fl.set(CoreAnnotations.ChineseSegAnnotation.class, "1");
      if (w.isEmpty()) {
        continue; // [cdm 2016:] surely this shouldn't happen!
      }
      CoreLabel token = new CoreLabel();
      token.setWord(w);
      token.setValue(w);
      token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, fl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
      pos += w.length();
      fl = sentChars.get(pos - 1);
      token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, fl.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
      tokens.add(token);
    }

    if (xmlbuffer.length() > 0) {
      // Form the last XML token, if any
      String xmltag = xmlbuffer.toString();
      CoreLabel token = new CoreLabel();
      token.setWord(xmltag);
      token.setValue(xmltag);
      token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, xmlbegin);
      CoreLabel fl1 = sentChars.get(pos - 1);
      token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, fl1.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
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
