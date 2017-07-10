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
import edu.stanford.nlp.process.AbstractTokenizer;
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
          "/u/nlp/data/chinese-segmenter/stanford-seg-2010/classifiers-2013/ctb7.chris6.lex.gz";

  private static final String DEFAULT_SER_DICTIONARY =
          "//u/nlp/data/chinese-segmenter/stanford-seg-2010/classifiers-2013/dict-chris6.ser.gz";

  private static final String DEFAULT_SIGHAN_CORPORA_DICT =
          "/u/nlp/data/chinese-segmenter/stanford-seg-2010/releasedata/";

  private static final String separator = "(?:\r|\r?\n|" + System.lineSeparator() + ')';
  private static final Pattern separatorPattern = Pattern.compile(separator);


  private final AbstractSequenceClassifier<?> segmenter;
  private final boolean VERBOSE;
  private final boolean tokenizeNewline;
  private final boolean normalizeSpace;

  public ChineseSegmenterAnnotator() {
    this(DEFAULT_SEG_LOC, false);
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
    this.normalizeSpace = PropertiesUtils.getBool(props, name + ".normalizeSpace", false);
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

    // If newlines are treated as sentence split, we need to retain them in tokenization for ssplit to make use of them
    tokenizeNewline = (!props.getProperty(StanfordCoreNLP.NEWLINE_IS_SENTENCE_BREAK_PROPERTY, "never").equals("never"))
            || Boolean.valueOf(props.getProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY, "false"));
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
  private void splitCharacters(CoreMap annotation) {
    String origText = annotation.get(CoreAnnotations.TextAnnotation.class);
    boolean seg = true; // false only while inside an XML entity
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
        // Don't skip newline characters if we're tokenizing them
        // We always count \n as newline to be consistent with the implementation of ssplit
        skipCharacter = ! (tokenizeNewline && (cp == '\n' || cp == '\r' || System.lineSeparator().contains(charString)));
      }
      if ( ! skipCharacter) {
        // if this character is a normal character, put it in as a CoreLabel and set seg to false for next word
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
          } else if (offset == xmlStartOffset) {
            wi.set(SegmenterCoreAnnotations.XMLCharAnnotation.class, "beginning");
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
    } // for loop through charPoints

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
    List<String> words;
    if ( ! tokenizeNewline) {
      text = text.replaceAll("[\r\n]", "");
      words = segmenter.segmentString(text);
    } else {
      // Run the segmenter on each line so that we don't get tokens that cross line boundaries
      // Neat trick to keep delimiters from: http://stackoverflow.com/a/2206432
      String[] lines = text.split(String.format("((?<=%1$s)|(?=%1$s))", separator));

      words = new ArrayList<>();
      for (String line : lines) {
        if (separatorPattern.matcher(line).matches()) {
          // Don't segment newline tokens, keep them as-is
          words.add(line);
        } else {
          words.addAll(segmenter.segmentString(line));
        }
      }
    }
    if (VERBOSE) {
      log.info(text + "\n--->\n" + words);
    }

    // Go through everything again and make the final tokens list
    int pos = 0; // This is used to index sentChars, the output from splitCharacters
    StringBuilder xmlBuffer = new StringBuilder();
    int xmlBegin = -1;
    for (String w : words) {
      CoreLabel fl = sentChars.get(pos);

      if (fl.get(SegmenterCoreAnnotations.XMLCharAnnotation.class).equals("0")
        || fl.get(SegmenterCoreAnnotations.XMLCharAnnotation.class).equals("beginning")) {
        // Beginnings of plain text and other XML tags are good places to end an XML tag
        if (xmlBuffer.length() > 0) {
          // Form the XML token
          String xmlTag = xmlBuffer.toString();
          CoreLabel fl1 = sentChars.get(pos - 1);
          int end = fl1.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
          tokens.add(makeXmlToken(xmlTag, true, xmlBegin, end));

          // Clean up and prepare for the next XML tag
          xmlBegin = -1;
          xmlBuffer = new StringBuilder();
        }
      }

      if ( ! fl.get(SegmenterCoreAnnotations.XMLCharAnnotation.class).equals("0")) {
        // found an XML character
        while (fl.get(SegmenterCoreAnnotations.XMLCharAnnotation.class).equals("whitespace")) {
          // Print whitespaces into the XML buffer and move on until the next non-whitespace character is found
          // and we're in sync with segmenter output again
          xmlBuffer.append(' ');
          pos += 1;
          fl = sentChars.get(pos);
        }

        xmlBuffer.append(w);
        pos += w.length();
        if (xmlBegin < 0) {
          xmlBegin = fl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
        }
        continue;
      }

      fl.set(CoreAnnotations.ChineseSegAnnotation.class, "1");
      if (w.isEmpty()) {
        if (VERBOSE) { log.warn("Encountered an empty word. Shouldn't happen?"); }
        continue; // [cdm 2016:] surely this shouldn't happen!
      }
      int begin = fl.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      pos += w.length();
      if (pos - 1 >= sentChars.size()) {
        log.error("on word " + w + " at position " + (pos - w.length()) + " trying to get at position " + (pos - 1));
        log.error("last element of sentChars is " + sentChars.get(sentChars.size() - 1));
      }
      fl = sentChars.get(pos - 1);
      int end = fl.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      tokens.add(makeXmlToken(w, false, begin, end));
    }

    if (xmlBuffer.length() > 0) {
      // Form the last XML token, if any
      String xmlTag = xmlBuffer.toString();
      CoreLabel fl1 = sentChars.get(pos - 1);
      int end = fl1.get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      tokens.add(makeXmlToken(xmlTag, true, xmlBegin, end));
    }

    if (VERBOSE) {
      for (CoreLabel token : tokens) {
        log.info(token.toShorterString());
      }
    }
  }

  private CoreLabel makeXmlToken(String tokenText, boolean doNormalization, int charOffsetBegin, int charOffsetEnd) {
    CoreLabel token = new CoreLabel();
    token.setOriginalText(tokenText);

    if (separatorPattern.matcher(tokenText).matches()) {
      // Map to CoreNLP newline token
      tokenText = AbstractTokenizer.NEWLINE_TOKEN;
    } else if (doNormalization && normalizeSpace) {
      tokenText = tokenText.replace(' ', '\u00A0'); // change space to non-breaking space
    }

    token.setWord(tokenText);
    token.setValue(tokenText);
    token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, charOffsetBegin);
    token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, charOffsetEnd);
    if (VERBOSE) {
      log.info("Adding token " + token.toShorterString());
    }
    return token;
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
