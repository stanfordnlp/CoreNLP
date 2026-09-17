package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.international.Language;
import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.trees.ud.CoNLLUFeatures;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;
import java.util.regex.*;

/**
 * A class for reading in CoNLL-U data and creating Annotations.
 *
 * @author Jason Bolton
 */

public class CoNLLUReader {

  /**
   * field constants
   **/
  // TODO: read sent_id?
  public static final int CoNLLU_IndexField = 0;
  public static final int CoNLLU_WordField = 1;
  public static final int CoNLLU_LemmaField = 2;
  public static final int CoNLLU_UPOSField = 3;
  public static final int CoNLLU_XPOSField = 4;
  public static final int CoNLLU_FeaturesField = 5;
  public static final int CoNLLU_GovField = 6;
  public static final int CoNLLU_RelnField = 7;
  public static final int CoNLLU_EnhancedField = 8;
  public static final int CoNLLU_MiscField = 9;

  public int columnCount = 10;

  /**
   * patterns to match in CoNLL-U file
   **/
  public static Pattern COMMENT_LINE = Pattern.compile("^#.*");
  // a newdoc line is normally followed by an id, as in
  // "# newdoc id = reviews-091234", but the bare form is also legal.
  // the optional whitespace keeps "# newdocument" from matching
  public static Pattern DOCUMENT_LINE = Pattern.compile("^# newdoc(\\s.*)?$");
  public static Pattern MWT_LINE = Pattern.compile("^[0-9]+-[0-9]+.*");
  public static Pattern TOKEN_LINE = Pattern.compile("^[0-9]+\t.*");
  public static Pattern EMPTY_LINE = Pattern.compile("^[0-9]+[.][0-9]+\t.*");

  /** The kinds of line a CoNLL-U file is made of */
  enum LineType {
    COMMENT, MWT, TOKEN, EMPTY, OTHER
  }

  /**
   * Which kind of line this is, decided from its first few characters.
   *<br>
   * A comment starts with #.  The rest all start with an index: digits
   * followed by - for the range of an MWT, . for an empty word, or a tab
   * for a plain token.  Anything else, such as the blank line which ends
   * a sentence, is OTHER.
   */
  static LineType classifyLine(String line) {
    if (line.isEmpty()) {
      return LineType.OTHER;
    }
    if (line.charAt(0) == '#') {
      return LineType.COMMENT;
    }
    int idx = digitRunEnd(line, 0);
    if (idx == 0 || idx >= line.length()) {
      return LineType.OTHER;
    }
    char separator = line.charAt(idx);
    if (separator == '\t') {
      return LineType.TOKEN;
    }
    if (separator == '-') {
      // an MWT is digits-digits, with no constraint on what follows
      return digitRunEnd(line, idx + 1) > idx + 1 ? LineType.MWT : LineType.OTHER;
    }
    if (separator == '.') {
      // an empty word is digits.digits, and then a tab
      int end = digitRunEnd(line, idx + 1);
      if (end > idx + 1 && end < line.length() && line.charAt(end) == '\t') {
        return LineType.EMPTY;
      }
    }
    return LineType.OTHER;
  }

  /** The index just past the run of digits starting at start, which may be start itself */
  private static int digitRunEnd(String line, int start) {
    int idx = start;
    while (idx < line.length() && line.charAt(idx) >= '0' && line.charAt(idx) <= '9') {
      ++idx;
    }
    return idx;
  }

  /**
   * shorthands for CoreAnnotations
   **/
  public static HashMap<String, String> classShorthandToFull = new HashMap<>();

  static {
    classShorthandToFull.put("CoreAnnotations", "edu.stanford.nlp.ling.");
    classShorthandToFull.put("SemanticGraphCoreAnnotations", "edu.stanford.nlp.semgraph.");
    classShorthandToFull.put("SentimentCoreAnnotations", "edu.stanford.nlp.sentiment.");
  }

  /**
   * Mappings for extra columns.
   * <p>
   * Column at index x gets mapped to extraColumns.get(x)
   * <p>
   * By default index 10 = NamedEntityTagAnnotation
   * <p>
   * If any columns are specified in the properties this
   * default will be ignored
   * *
   * To specify arbitrary CoreAnnotations, use the conllu.extraColumns
   * property, which should be a comma separated list of String
   * representations of the class names
   * <p>
   * The constructor will try to interpret the String as a known
   * CoreAnnotation
   * <p>
   * e.g. SentimentCoreAnnotations.SentimentClass ->
   * edu.stanford.nlp.sentiment.SentimentCoreAnnotation.SentimentClass
   * <p>
   * But for completely custom CoreAnnotations the full class name must be used
   * <p>
   * example:
   * <p>
   * conllu.extraColumns = CoreAnnotations.TrueCaseAnnotation,CoreAnnotations.CategoryAnnotation
   */
  private HashMap<Integer, Class> extraColumns = new HashMap<>();

  public CoNLLUReader() throws ClassNotFoundException {
    this(new Properties());
  }

  public CoNLLUReader(Properties props) throws ClassNotFoundException {
    // set up defaults for extraColumns
    if (props.getProperty("conllu.extraColumns", "").equals("")) {
      extraColumns.put(10, CoreAnnotations.NamedEntityTagAnnotation.class);
    } else {
      int extraColumnIndex = 10;
      for (String className : props.getProperty("conllu.extraColumns").split(",")) {
        extraColumns.put(extraColumnIndex, findExtraColumnClass(className.trim()));
        ++extraColumnIndex;
      }
    }
    columnCount += extraColumns.size();
  }

  /**
   * The CoreAnnotation named by one piece of the conllu.extraColumns property.
   *<br>
   * A name may be a full class name, or one of the shorthands such as
   * CoreAnnotations.TrueCaseAnnotation, where the first piece names a
   * class listed in classShorthandToFull and supplies the package.
   *<br>
   * These annotations are classes nested inside another class, which
   * Class.forName wants written with a $ rather than a dot.  The name is
   * tried as given first, so that a full name which already uses a $ works
   * as well as one written the way it would be written in Java.
   */
  static Class findExtraColumnClass(String className) throws ClassNotFoundException {
    int firstDot = className.indexOf('.');
    if (firstDot >= 0) {
      String packageName = classShorthandToFull.get(className.substring(0, firstDot));
      if (packageName != null) {
        className = packageName + className.substring(0, firstDot) + '$' + className.substring(firstDot + 1);
      }
    }
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      int lastDot = className.lastIndexOf('.');
      if (lastDot < 0) {
        throw e;
      }
      return Class.forName(className.substring(0, lastDot) + '$' + className.substring(lastDot + 1));
    }
  }

  // TODO: is there a better place for this?
  public static String unescapeSpacesAfter(String escaped) {
    int idx = 0;
    StringBuilder unescaped = new StringBuilder();
    while (idx < escaped.length()) {
      if (escaped.charAt(idx) != '\\') {
        unescaped.append(escaped.charAt(idx));
        ++idx;
        continue;
      }
      if (idx + 2 <= escaped.length()) {
        String piece = escaped.substring(idx, idx + 2);
        if (piece.equals("\\s")) {
          unescaped.append(' ');
          idx += 2;
          continue;
        } else if (piece.equals("\\t")) {
          unescaped.append('\t');
          idx += 2;
          continue;
        } else if (piece.equals("\\r")) {
          unescaped.append('\r');
          idx += 2;
          continue;
        } else if (piece.equals("\\n")) {
          unescaped.append('\n');
          idx += 2;
          continue;
        } else if (piece.equals("\\p")) {
          unescaped.append('|');
          idx += 2;
          continue;
        } else if (piece.equals("\\\\")) {
          unescaped.append('\\');
          idx += 2;
          continue;
        }
      }
      if (idx + 6 <= escaped.length()) {
        String piece = escaped.substring(idx, idx + 6);
        if (piece.equals("\\u00A0")) {
          unescaped.append(' ');
          idx += 6;
          continue;
        }
      }
      unescaped.append(escaped.charAt(idx));
      ++idx;
    }
    return unescaped.toString();
  }

  public static String miscToSpaceAfter(Map<String, String> miscKeyValues) {
    String spaceAfter = miscKeyValues.get("SpaceAfter");
    if (spaceAfter != null) {
      if (spaceAfter.equals("No") || spaceAfter.equals("no")) {
        return "";
      } else if (spaceAfter.equals("No~")) {
        // a random data bug in UD 2.11 Russian-Taiga
        return "";
      } else {
        return " ";
      }
    }

    String spacesAfter = miscKeyValues.get("SpacesAfter");
    if (spacesAfter != null) {
      return unescapeSpacesAfter(spacesAfter);
    }

    return " ";
  }

  /**
   * class to store info for a CoNLL-U document
   **/
  public static class CoNLLUDocument {
    /**
     * sentences for this doc
     **/
    public List<CoNLLUSentence> sentences = new ArrayList<>();

    /**
     * full doc text
     *<br>
     * A StringBuilder, as the text is accumulated a token at a time
     **/
    public StringBuilder docText = new StringBuilder();

    public CoNLLUDocument() {
      sentences.add(new CoNLLUSentence());
    }

    /**
     * Get the last sentence
     **/
    public CoNLLUSentence lastSentence() {
      return sentences.get(sentences.size() - 1);
    }

    /**
     * True if no line has been read into this document yet
     *<br>
     * A document is built with one sentence already waiting for lines, so
     * having a single empty sentence is the same as having nothing at all.
     **/
    public boolean isEmpty() {
      return sentences.isEmpty() || (sentences.size() == 1 && sentences.get(0).isEmpty());
    }

    /**
     * Drop the sentence at the end if nothing was ever read into it
     *<br>
     * A sentence is created to catch the lines after a blank line, so at
     * the end of a document there is usually one left over with nothing in
     * it.  A file which stops without a final blank line has no such
     * leftover, and then the last sentence is real and is kept.
     **/
    public void removeTrailingEmptySentence() {
      if (!sentences.isEmpty() && lastSentence().isEmpty()) {
        sentences.remove(sentences.size() - 1);
      }
    }
  }

  /**
   * class to store info for a CoNLL-U sentence
   **/
  public static class CoNLLUSentence {

    // the token lines
    public List<String> tokenLines = new ArrayList<>();
    // in case the enhanced dependencies have empty words
    public List<String> emptyLines = new ArrayList<>();
    // data for the sentence contained in # key values
    // "# sent_id = weblog-0003" is stored as sent_id -> weblog-0003
    public HashMap<String, String> sentenceData = new HashMap<>();
    // all of the comments, including the ones that showed up in sentenceData
    public List<String> comments = new ArrayList<>();
    // map indices in token list to mwt data if there is any
    HashMap<Integer, Integer> mwtData = new HashMap<>();
    // mwt tokens
    List<String> mwtTokens = new ArrayList<>();
    // mwt misc info
    List<String> mwtMiscs = new ArrayList<>();
    // indexes of last CoreLabel for each MWT
    List<Integer> mwtLastCoreLabels = new ArrayList<>();

    /**
     * How to refer to this sentence when reporting a line which cannot be read
     **/
    public String description() {
      String sentId = sentenceData.get("sent_id");
      return sentId == null ? "a sentence with no sent_id" : "sentence " + sentId;
    }

    /**
     * True if no line has been read into this sentence yet
     **/
    public boolean isEmpty() {
      return tokenLines.isEmpty() && emptyLines.isEmpty() && comments.isEmpty() && mwtTokens.isEmpty();
    }

    /**
     * True if any word has been read into this sentence
     *<br>
     * Comments on their own are not a sentence: CoNLL-U gives every
     * sentence at least one word.
     **/
    public boolean hasWords() {
      return !tokenLines.isEmpty() || !emptyLines.isEmpty();
    }

    /**
     * Process line for current sentence.  Return true if processing empty line (indicating sentence end)
     **/
    public boolean processLine(String line) {
      return processLine(line, classifyLine(line));
    }

    /**
     * Process a line whose kind has already been decided by the caller
     **/
    public boolean processLine(String line, LineType lineType) {
      switch (lineType) {
      case COMMENT:
        addSentenceData(line);
        return false;
      case MWT:
        addMWTData(line);
        return false;
      case TOKEN:
        tokenLines.add(line);
        return false;
      case EMPTY:
        emptyLines.add(line);
        return false;
      default:
        return true;
      }
    }

    /**
     * Add sentence data for this sentence
     **/
    public void addSentenceData(String sentenceDataLine) {
      int equals = sentenceDataLine.indexOf('=');
      if (equals >= 0 && !sentenceDataLine.isEmpty() && sentenceDataLine.charAt(0) == '#') {
        // the # and the spaces around the = are how the line is written,
        // not part of the key or of the value.  only the first = is a
        // separator, so a value may contain one of its own
        String key = sentenceDataLine.substring(1, equals).trim();
        String value = sentenceDataLine.substring(equals + 1).trim();
        sentenceData.put(key, value);
      }
      comments.add(sentenceDataLine);
    }

    /**
     * Add mwt data for this mwt line
     **/
    void addMWTData(String mwtDataLine) {
      String[] mwtFields = mwtDataLine.split("\t");
      checkColumnCount(mwtFields, this);
      String[] mwtRange = mwtFields[CoNLLU_IndexField].split("-");
      String mwtText = mwtFields[CoNLLU_WordField];
      int mwtStart = Integer.parseInt(mwtRange[0]);
      int mwtEnd = Integer.parseInt(mwtRange[1]);
      for (int i = mwtStart - 1; i < mwtEnd; i++) {
        mwtData.put(i, mwtTokens.size());
      }
      mwtTokens.add(mwtText);
      mwtMiscs.add(mwtFields[CoNLLU_MiscField]);
      mwtLastCoreLabels.add(mwtEnd - 1);
    }
  }

  /**
   * Read a CoNLL-U file and generate a list of Annotations
   **/
  public List<Annotation> readCoNLLUFile(String filePath) throws IOException {
    List<CoNLLUDocument> docs = readCoNLLUFileCreateCoNLLUDocuments(filePath);
    return docs.stream().map(doc -> convertCoNLLUDocumentToAnnotation(doc)).collect(Collectors.toList());
  }

  /**
   * Read a CoNLL-U file and generate a list of CoNLLUDocument objects
   **/
  public List<CoNLLUDocument> readCoNLLUFileCreateCoNLLUDocuments(String filePath) throws IOException {
    List<CoNLLUDocument> docs = new ArrayList<>();
    docs.add(new CoNLLUDocument());
    // the reader is closed here rather than left for the garbage collector
    // to get to, since reading a directory of treebanks would otherwise
    // hold a file open for each one of them
    try (BufferedReader reader = IOUtils.readerFromString(filePath)) {
      // process lines
      for (String line : IOUtils.getLineIterable(reader, false)) {
        LineType lineType = classifyLine(line);
        // if start of a new doc, reset for a new doc
        // only a comment can be a newdoc line, so the rest are not tested at all
        if (lineType == LineType.COMMENT && DOCUMENT_LINE.matcher(line).matches()) {
          CoNLLUDocument current = docs.get(docs.size() - 1);
          // a newdoc at the very top of the file names the document which is
          // already open, rather than asking for another one after it
          if (!current.isEmpty()) {
            // the sentence waiting for lines belongs to neither document
            current.removeTrailingEmptySentence();
            // the new document comes prebuilt with a blank sentence, so,
            // no need to add one here
            docs.add(new CoNLLUDocument());
          }
        }
        // read in current line
        boolean endSentence = docs.get(docs.size() - 1).lastSentence().processLine(line, lineType);
        // if sentence is over, add sentence to doc, reset for new sentence
        // a run of blank lines, or comments which no words follow, do not
        // make a sentence of their own: a second blank line finds a sentence
        // with nothing in it and leaves it to be filled, and comments carry
        // over to the next sentence which does have words
        if (endSentence && docs.get(docs.size() - 1).lastSentence().hasWords()) {
          docs.get(docs.size() - 1).sentences.add(new CoNLLUSentence());
        }
      }
    }
    docs.get(docs.size() - 1).removeTrailingEmptySentence();
    return docs;
  }

  /**
   * Convert a CoNLLUDocument into an Annotation
   * The convention is that a CoNLLU document represents a list of sentences,
   * one sentence per line, separated by newline.
   **/
  public Annotation convertCoNLLUDocumentToAnnotation(CoNLLUDocument doc) {
    Annotation finalAnnotation = new Annotation("");
    // build sentences
    List<CoreMap> sentences = new ArrayList<>();
    for (CoNLLUSentence sent : doc.sentences) {
      // pass in the sentences.size() so we can build the CoreLabels with the correct sentIndex()
      // this way, we don't mess up the hashCodes later
      sentences.add(convertCoNLLUSentenceToCoreMap(doc, sent, sentences.size()));
    }
    // set sentences
    finalAnnotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
    // build document wide CoreLabels list
    // TODO: should we set document annotation?
    List<CoreLabel> tokens = new ArrayList<>();
    finalAnnotation.set(CoreAnnotations.TokensAnnotation.class, tokens);
    int documentIdx = 0;
    int sentenceIdx = 0;
    for (CoreMap sentence : sentences) {
      sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIdx);
      List<CoreLabel> sentenceTokens = sentence.get(CoreAnnotations.TokensAnnotation.class);
      // a sentence with no words has no text to hand over, and nothing to
      // hand it to.  such a sentence is not legal CoNLL-U, but a file with
      // stray comments in it can still produce one
      if (sentenceIdx > 0 && !sentenceTokens.isEmpty()) {
        CoreMap previousSentence = sentences.get(sentenceIdx - 1);
        List<CoreLabel> previousTokens = previousSentence.get(CoreAnnotations.TokensAnnotation.class);
        if (!previousTokens.isEmpty()) {
          CoreLabel previousToken = previousTokens.get(previousTokens.size() - 1);
          String previousAfter = previousToken.get(CoreAnnotations.AfterAnnotation.class);
          sentenceTokens.get(0).set(CoreAnnotations.BeforeAnnotation.class, previousAfter);
        }
      }
      for (CoreLabel token : sentenceTokens) {
        token.set(CoreAnnotations.TokenBeginAnnotation.class, documentIdx);
        token.set(CoreAnnotations.TokenEndAnnotation.class, documentIdx + 1);
        tokens.add(token);
        documentIdx++;
      }
      sentenceIdx++;
    }
    // make sure to set docText AFTER all the above processing
    // the doc.docText is derived from the sentences (not the comments)
    finalAnnotation.set(CoreAnnotations.TextAnnotation.class, doc.docText.toString());
    return finalAnnotation;
  }

  /**
   * Check that a line has the ten columns CoNLL-U gives every line.
   *<br>
   * A line is recognized as a token, an MWT or an empty word by how it
   * starts, so one which is cut short gets that far and then falls over on
   * whichever column is missing.  Saying which line it was, and which
   * sentence, is the difference between a fixable report and a puzzle.
   */
  static void checkColumnCount(String[] fields, CoNLLUSentence sentence) {
    if (fields.length < CoNLLU_MiscField + 1) {
      // the columns are shown separated by a written out \t rather than by
      // the tabs themselves, since the point of the message is to show
      // where the columns of the line actually are
      throw new IllegalArgumentException("Cannot read a CoNLL-U line with " + fields.length +
                                         " columns, expected at least " + (CoNLLU_MiscField + 1) +
                                         ", in " + sentence.description() + ": |" +
                                         String.join("\\t", fields) + "|");
    }
  }

  /**
   * Parse a bar separated misc field, such as SpaceAfter=No|Gloss=cat, into its key value pairs.
   *<br>
   * A LinkedHashMap, since the order of the keys is kept if the document
   * is written back out as CoNLL-U.  A piece with no = in it is skipped.
   * A value may itself contain =, and keeps it.
   */
  public static Map<String, String> parseKeyValues(String field) {
    Map<String, String> keyValues = new LinkedHashMap<>();
    if (field == null || field.equals("_")) {
      return keyValues;
    }
    int start = 0;
    while (start <= field.length()) {
      int end = field.indexOf('|', start);
      if (end < 0) {
        end = field.length();
      }
      int equals = field.indexOf('=', start);
      if (equals >= 0 && equals < end) {
        keyValues.put(field.substring(start, equals), field.substring(equals + 1, end));
      }
      start = end + 1;
    }
    return keyValues;
  }

  public static final String rebuildMisc(Map<String, String> miscKeyValues) {
    if (miscKeyValues.size() == 0) {
      return null;
    }

    // rebuild the misc, since we have removed the SpaceAfter, SpacesAfter, and SpacesBefore
    StringBuilder misc = new StringBuilder();
    for (Map.Entry<String, String> entry : miscKeyValues.entrySet()) {
      if (misc.length() > 0) {
        misc.append("|");
      }
      misc.append(entry.getKey());
      misc.append("=");
      misc.append(entry.getValue());
    }
    return misc.toString();
  }

  /**
   * Convert a single ten column CoNLLU line into a CoreLabel
   */
  public CoreLabel convertLineToCoreLabel(CoNLLUSentence sentence, String line, int sentenceIdx) {
    return convertLineToCoreLabel(sentence, line.split("\t"), sentenceIdx);
  }

  /**
   * Convert the already split fields of a ten column CoNLLU line into a CoreLabel
   */
  public CoreLabel convertLineToCoreLabel(CoNLLUSentence sentence, String[] fields, int sentenceIdx) {
    checkColumnCount(fields, sentence);
    // a CoNLL-U token ends up with roughly twenty annotations, so the
    // CoreLabel is built wide enough to hold them without regrowing
    CoreLabel cl = new CoreLabel(24);
    cl.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIdx);

    String indexField = fields[CoNLLU_IndexField];
    int sentenceTokenIndex;
    boolean isEmpty;
    if (indexField.indexOf('.') >= 0) {
      isEmpty = true;
      String[] indexPieces = indexField.split("[.]", 2);
      sentenceTokenIndex = Integer.valueOf(indexPieces[0]);
      cl.setIndex(sentenceTokenIndex);
      int emptyIndex = Integer.valueOf(indexPieces[1]);
      cl.set(CoreAnnotations.EmptyIndexAnnotation.class, emptyIndex);
    } else {
      isEmpty = false;
      sentenceTokenIndex = Integer.valueOf(indexField);
      cl.setIndex(sentenceTokenIndex);
    }

    cl.setWord(fields[CoNLLU_WordField]);
    cl.setValue(fields[CoNLLU_WordField]);
    cl.setOriginalText(fields[CoNLLU_WordField]);
    cl.setIsNewline(false);

    if (!fields[CoNLLU_LemmaField].equals("_"))
      cl.setLemma(fields[CoNLLU_LemmaField]);

    if (!fields[CoNLLU_UPOSField].equals("_"))
      cl.set(CoreAnnotations.CoarseTagAnnotation.class, fields[CoNLLU_UPOSField]);

    final String xpos = fields[CoNLLU_XPOSField];
    if (!xpos.equals("_"))
      cl.setTag(xpos);

    if (!fields[CoNLLU_FeaturesField].equals("_")) {
      CoNLLUFeatures features = new CoNLLUFeatures(fields[CoNLLU_FeaturesField]);
      cl.set(CoreAnnotations.CoNLLUFeats.class, features);
    }
    for (int extraColumnIdx = 10; extraColumnIdx < columnCount && extraColumnIdx < fields.length;
         extraColumnIdx++) {
      cl.set(extraColumns.get(extraColumnIdx), fields[extraColumnIdx]);
    }

    // LinkedHashMap because we care about trying to preserve the order of the keys
    // for later if we output the document in conllu
    // (although this doesn't put SpaceAfter in a canonical order)
    Map<String, String> miscKeyValues = parseKeyValues(fields[CoNLLU_MiscField]);

    // SpacesBefore on a word that isn't the first in a document will
    // be replaced with the SpacesAfter from the previous token later
    String spacesBefore = miscKeyValues.get("SpacesBefore");
    if (spacesBefore != null) {
      cl.setBefore(unescapeSpacesAfter(spacesBefore));
      miscKeyValues.remove("SpacesBefore");
    }

    // handle the MWT info and after text
    if (isEmpty) {
      // don't set an after for empty tokens
      // empty tokens are not considered part of MWT
      cl.setIsMWT(false);
      cl.setIsMWTFirst(false);
    } else if (sentence.mwtData.containsKey(sentenceTokenIndex - 1)) {
      String miscInfo = sentence.mwtMiscs.get(sentence.mwtData.get(sentenceTokenIndex - 1));
      Map<String, String> mwtKeyValues = parseKeyValues(miscInfo);

      // set MWT text
      cl.set(CoreAnnotations.MWTTokenTextAnnotation.class,
             sentence.mwtTokens.get(sentence.mwtData.get(sentenceTokenIndex - 1)));
      cl.setIsMWT(true);
      // check if first
      if (sentence.mwtData.containsKey(sentenceTokenIndex - 2) &&
          sentence.mwtData.get(sentenceTokenIndex-2).equals(sentence.mwtData.get(sentenceTokenIndex-1))) {
        cl.setIsMWTFirst(false);
      } else {
        cl.setIsMWTFirst(true);

        // if we are first, look for SpacesBefore
        String mwtSpacesBefore = mwtKeyValues.get("SpacesBefore");
        if (mwtSpacesBefore != null) {
          cl.setBefore(unescapeSpacesAfter(mwtSpacesBefore));
        }
      }
      // SpaceAfter / SpacesAfter should only apply to the last word in an MWT
      // all other words are treated as implicitly having SpaceAfter=No
      if (sentence.mwtData.containsKey(sentenceTokenIndex) &&
          sentence.mwtData.get(sentenceTokenIndex).equals(sentence.mwtData.get(sentenceTokenIndex-1))) {
        // is there a next word MWT?
        // and it's the same MWT as this word?
        // then we aren't last, and SpaceAfter="" is implicitly true
        cl.setAfter("");
      } else {
        String spaceAfter = miscToSpaceAfter(mwtKeyValues);
        cl.setAfter(spaceAfter);
      }
      if (cl.isMWTFirst()) {
        mwtKeyValues.remove("SpaceAfter");
        mwtKeyValues.remove("SpacesAfter");
        mwtKeyValues.remove("SpacesBefore");

        String mwtMisc = rebuildMisc(mwtKeyValues);
        if (mwtMisc != null) {
          cl.set(CoreAnnotations.MWTTokenMiscAnnotation.class, mwtMisc);
        }
      }
    } else {
      cl.setIsMWT(false);
      cl.setIsMWTFirst(false);

      String spaceAfter = miscToSpaceAfter(miscKeyValues);
      cl.setAfter(spaceAfter);
    }
    miscKeyValues.remove("SpaceAfter");
    miscKeyValues.remove("SpacesAfter");
    String misc = rebuildMisc(miscKeyValues);
    if (misc != null) {
      cl.set(CoreAnnotations.CoNLLUMisc.class, misc);
    }
    return cl;
  }

  /**
   * Convert a list of CoNLL-U token lines into a sentence CoreMap
   **/
  public CoreMap convertCoNLLUSentenceToCoreMap(CoNLLUDocument doc, CoNLLUSentence sentence, int sentenceIdx) {
    List<String> lines = sentence.tokenLines;
    // each line is split once here, and the fields are then reused for the
    // CoreLabel and for the basic and enhanced graphs
    List<String[]> tokenFields = new ArrayList<>(lines.size());
    for (String line : lines) {
      tokenFields.add(line.split("\t"));
    }
    // create CoreLabels
    List<CoreLabel> coreLabels = new ArrayList<CoreLabel>(tokenFields.size());
    for (String[] fields : tokenFields) {
      CoreLabel cl = convertLineToCoreLabel(sentence, fields, sentenceIdx);
      coreLabels.add(cl);
    }
    for (int i = 1 ; i < coreLabels.size() ; i++) {
      // all words should match the after of the previous token
      coreLabels.get(i).set(CoreAnnotations.BeforeAnnotation.class,
                            coreLabels.get(i - 1).get(CoreAnnotations.AfterAnnotation.class));
    }
    // handle MWT tokens and build the final sentence text
    int sentenceCharBegin = doc.docText.length();
    int processedMWTTokens = 0;
    // for MWT created CoreLabels set all of them to the character offsets of the MWT
    int lastMWTCharBegin = -1;
    int lastMWTCharEnd = -1;
    for (CoreLabel cl : coreLabels) {
      // check if this CoreLabel was derived from an MWT
      if (sentence.mwtData.containsKey(cl.index() - 1)) {
        if (sentence.mwtData.get(cl.index() - 1) == processedMWTTokens) {
          // add this MWT to the doc text
          cl.setBeginPosition(doc.docText.length());
          doc.docText.append(sentence.mwtTokens.get(processedMWTTokens));
          cl.setEndPosition(doc.docText.length());
          lastMWTCharBegin = cl.beginPosition();
          lastMWTCharEnd = cl.endPosition();
          // add after for this MWT by getting after of last CoreLabel for this MWT
          doc.docText.append(coreLabels.get(sentence.mwtLastCoreLabels.get(processedMWTTokens)).after());
          // move on to next MWT
          processedMWTTokens += 1;
        } else {
          cl.setBeginPosition(lastMWTCharBegin);
          cl.setEndPosition(lastMWTCharEnd);
        }
        cl.setIsMWT(true);
      } else {
        cl.setBeginPosition(doc.docText.length());
        doc.docText.append(cl.word());
        cl.setEndPosition(doc.docText.length());
        doc.docText.append(cl.after());
      }
    }

    List<String[]> emptyFields = new ArrayList<>(sentence.emptyLines.size());
    for (String line : sentence.emptyLines) {
      emptyFields.add(line.split("\t"));
    }
    List<CoreLabel> emptyLabels = new ArrayList<CoreLabel>(emptyFields.size());
    for (String[] fields : emptyFields) {
      CoreLabel cl = convertLineToCoreLabel(sentence, fields, sentenceIdx);
      emptyLabels.add(cl);
    }

    // build sentence CoreMap with full text
    Annotation sentenceCoreMap = new Annotation(doc.docText.substring(sentenceCharBegin).trim());
    // add tokens
    sentenceCoreMap.set(CoreAnnotations.TokensAnnotation.class, coreLabels);
    // add empty tokens, if any exist
    if (emptyLabels.size() > 0) {
      sentenceCoreMap.set(CoreAnnotations.EmptyTokensAnnotation.class, emptyLabels);
    }

    // to build the basic SemanticGraph, first, prebuild the
    // IndexedWords that will make up the basic graph
    // (and possibly the enhanced graph)
    Map<String, IndexedWord> graphNodes = new HashMap<>(coreLabels.size() + emptyLabels.size());
    for (CoreLabel label : coreLabels) {
      String index = Integer.toString(label.index());
      graphNodes.put(index, new IndexedWord(label));
    }
    for (CoreLabel empty : emptyLabels) {
      String index = empty.index() + "." + empty.get(CoreAnnotations.EmptyIndexAnnotation.class);
      graphNodes.put(index, new IndexedWord(empty));
    }

    boolean hasEnhanced = false;
    // build SemanticGraphEdges for a basic graph
    List<SemanticGraphEdge> graphEdges = new ArrayList<>();
    List<IndexedWord> graphRoots = new ArrayList<>();
    for (String[] fields : tokenFields) {
      // track whether any of these lines signify there is an enhanced graph
      hasEnhanced = hasEnhanced || !fields[CoNLLU_EnhancedField].equals("_");
      IndexedWord dependent = graphNodes.get(fields[CoNLLU_IndexField]);
      if (fields[CoNLLU_GovField].equals("0")) {
        // no edges for the ROOT node
        graphRoots.add(dependent);
      } else {
        IndexedWord gov = graphNodes.get(fields[CoNLLU_GovField]);
        if (gov == null) {
          throw new IllegalArgumentException("Word " + fields[CoNLLU_IndexField] + " of " +
                                             sentence.description() + " has the head " +
                                             fields[CoNLLU_GovField] +
                                             ", which is not a word of this sentence");
        }
        GrammaticalRelation reln = GrammaticalRelation.valueOf(Language.UniversalEnglish, fields[CoNLLU_RelnField]);
        graphEdges.add(new SemanticGraphEdge(gov, dependent, reln, 1.0, false));
      }
    }
    // build SemanticGraph
    SemanticGraph depParse = SemanticGraphFactory.makeFromEdges(graphEdges, graphRoots);
    // add dependency graph
    sentenceCoreMap.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, depParse);

    if (hasEnhanced) {
      List<SemanticGraphEdge> enhancedEdges = new ArrayList<>();
      List<IndexedWord> enhancedRoots = new ArrayList<>();

      List<String[]> allFields = new ArrayList<>(tokenFields.size() + emptyFields.size());
      allFields.addAll(tokenFields);
      allFields.addAll(emptyFields);
      for (String[] fields : allFields) {
        String enhancedField = fields[CoNLLU_EnhancedField];
        // a word can have no enhanced dependencies of its own while other
        // words in the same sentence do.  it simply gets no incoming edge
        if (enhancedField.equals("_")) {
          continue;
        }
        IndexedWord dependent = graphNodes.get(fields[CoNLLU_IndexField]);
        String[] arcs = enhancedField.split("[|]");
        for (String arc : arcs) {
          String[] arcPieces = arc.split(":", 2);
          if (arcPieces[0].equals("0")) {
            enhancedRoots.add(dependent);
          } else {
            if (arcPieces.length < 2) {
              throw new IllegalArgumentException("Cannot parse the enhanced dependency |" + arc +
                                                 "| of word " + fields[CoNLLU_IndexField] + " of " +
                                                 sentence.description() +
                                                 ": expected a head and a relation separated by :");
            }
            IndexedWord gov = graphNodes.get(arcPieces[0]);
            if (gov == null) {
              throw new IllegalArgumentException("The enhanced dependency |" + arc + "| of word " +
                                                 fields[CoNLLU_IndexField] + " of " +
                                                 sentence.description() + " has the head " +
                                                 arcPieces[0] + ", which is not a word of this sentence");
            }
            GrammaticalRelation reln = GrammaticalRelation.valueOf(Language.UniversalEnglish, arcPieces[1]);
            enhancedEdges.add(new SemanticGraphEdge(gov, dependent, reln, 1.0, false));
          }
        }
      }
      SemanticGraph enhancedParse = SemanticGraphFactory.makeFromEdges(enhancedEdges, enhancedRoots);
      sentenceCoreMap.set(SemanticGraphCoreAnnotations.EnhancedDependenciesAnnotation.class, enhancedParse);
    }

    sentenceCoreMap.set(CoreAnnotations.CommentsAnnotation.class, sentence.comments);
    return sentenceCoreMap;
  }

}
