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
  public static Pattern DOCUMENT_LINE = Pattern.compile("^# newdoc");
  public static Pattern MWT_LINE = Pattern.compile("^[0-9]+-[0-9]+.*");
  public static Pattern TOKEN_LINE = Pattern.compile("^[0-9]+\t.*");
  public static Pattern EMPTY_LINE = Pattern.compile("^[0-9]+[.][0-9]+\t.*");

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
        if (classShorthandToFull.containsKey(className))
          className = classShorthandToFull.get(className) + className;
        Class clazz = Class.forName(className);
        extraColumns.put(extraColumnIndex, clazz);
      }
    }
    columnCount += extraColumns.size();
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
  public class CoNLLUDocument {
    /**
     * sentences for this doc
     **/
    public List<CoNLLUSentence> sentences = new ArrayList<>();

    /**
     * doc metadata
     **/
    public HashMap<String, String> docData = new HashMap<>();

    /**
     * full doc text
     **/
    public String docText = "";

    public CoNLLUDocument() {
      sentences.add(new CoNLLUSentence());
    }

    /**
     * Get the last sentence
     **/
    public CoNLLUSentence lastSentence() {
      return sentences.get(sentences.size() - 1);
    }
  }

  /**
   * class to store info for a CoNLL-U sentence
   **/
  public class CoNLLUSentence {

    // the token lines
    public List<String> tokenLines = new ArrayList<>();
    // in case the enhanced dependencies have empty words
    public List<String> emptyLines = new ArrayList<>();
    // data for the sentence contained in # key values
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
     * Process line for current sentence.  Return true if processing empty line (indicating sentence end)
     **/
    public boolean processLine(String line) {
      if (COMMENT_LINE.matcher(line).matches()) {
        addSentenceData(line);
      } else if (MWT_LINE.matcher(line).matches()) {
        addMWTData(line);
      } else if (TOKEN_LINE.matcher(line).matches()) {
        tokenLines.add(line);
      } else if (EMPTY_LINE.matcher(line).matches()) {
        emptyLines.add(line);
      } else {
        return true;
      }
      return false;
    }

    /**
     * Add sentence data for this sentence
     **/
    public void addSentenceData(String sentenceDataLine) {
      if (COMMENT_LINE.matcher(sentenceDataLine).matches() && sentenceDataLine.contains("=")) {
        String[] keyAndValue = sentenceDataLine.substring(1).split("=");
        String key = sentenceDataLine.substring(1, sentenceDataLine.indexOf('='));
        String value = sentenceDataLine.substring(sentenceDataLine.indexOf('='));
        sentenceData.put(key, value);
      }
      comments.add(sentenceDataLine);
    }

    /**
     * Add mwt data for this mwt line
     **/
    void addMWTData(String mwtDataLine) {
      String[] mwtFields = mwtDataLine.split("\t");
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
    // set up iterable
    BufferedReader reader = IOUtils.readerFromString(filePath);
    Iterable<String> lines = IOUtils.getLineIterable(reader, false);
    List<CoNLLUDocument> docs = new ArrayList<>();
    docs.add(new CoNLLUDocument());
    // process lines
    for (String line : lines) {
      // if start of a new doc, reset for a new doc
      if (DOCUMENT_LINE.matcher(line).matches()) {
        // since the next sentence gets added to the previous doc
        // (see below), we'll need to remove that
        if (docs.size() > 0) {
          docs.get(docs.size() - 1).sentences.remove(docs.get(docs.size() - 1).sentences.size() - 1);
        }
        // the new document comes prebuilt with a blank sentence, so,
        // no need to add one here
        docs.add(new CoNLLUDocument());
      }
      // read in current line
      boolean endSentence = docs.get(docs.size() - 1).lastSentence().processLine(line);
      // if sentence is over, add sentence to doc, reset for new sentence
      if (endSentence) {
        docs.get(docs.size() - 1).sentences.add(new CoNLLUSentence());
      }
    }
    // remove the empty last sentence of the last document
    docs.get(docs.size() - 1).sentences.remove(docs.get(docs.size() - 1).sentences.size() - 1);
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
    for (CoreMap sentence : finalAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIdx);
      if (sentenceIdx > 0) {
        CoreMap previousSentence = finalAnnotation.get(CoreAnnotations.SentencesAnnotation.class).get(sentenceIdx-1);
        List<CoreLabel> previousTokens = previousSentence.get(CoreAnnotations.TokensAnnotation.class);
        CoreLabel previousToken = previousTokens.get(previousTokens.size() - 1);
        String previousAfter = previousToken.get(CoreAnnotations.AfterAnnotation.class);
        sentence.get(CoreAnnotations.TokensAnnotation.class).get(0).set(CoreAnnotations.BeforeAnnotation.class, previousAfter);
      }
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        token.set(CoreAnnotations.TokenBeginAnnotation.class, documentIdx);
        token.set(CoreAnnotations.TokenEndAnnotation.class, documentIdx + 1);
        tokens.add(token);
        documentIdx++;
      }
      sentenceIdx++;
    }
    // make sure to set docText AFTER all the above processing
    // the doc.docText is derived from the sentences (not the comments)
    finalAnnotation.set(CoreAnnotations.TextAnnotation.class, doc.docText);
    return finalAnnotation;
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
    List<String> fields = Arrays.asList(line.split("\t"));
    CoreLabel cl = new CoreLabel();
    cl.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIdx);

    String indexField = fields.get(CoNLLU_IndexField);
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

    cl.setWord(fields.get(CoNLLU_WordField));
    cl.setValue(fields.get(CoNLLU_WordField));
    cl.setOriginalText(fields.get(CoNLLU_WordField));
    cl.setIsNewline(false);

    if (!fields.get(CoNLLU_LemmaField).equals("_"))
      cl.setLemma(fields.get(CoNLLU_LemmaField));

    if (!fields.get(CoNLLU_UPOSField).equals("_"))
      cl.set(CoreAnnotations.CoarseTagAnnotation.class, fields.get(CoNLLU_UPOSField));

    final String xpos = fields.get(CoNLLU_XPOSField);
    if (!xpos.equals("_"))
      cl.setTag(xpos);

    if (!fields.get(CoNLLU_FeaturesField).equals("_")) {
      CoNLLUFeatures features = new CoNLLUFeatures(fields.get(CoNLLU_FeaturesField));
      cl.set(CoreAnnotations.CoNLLUFeats.class, features);
    }
    for (int extraColumnIdx = 10; extraColumnIdx < columnCount && extraColumnIdx < fields.size();
         extraColumnIdx++) {
      cl.set(extraColumns.get(extraColumnIdx), fields.get(extraColumnIdx));
    }

    // LinkedHashMap because we care about trying to preserve the order of the keys
    // for later if we output the document in conllu
    // (although this doesn't put SpaceAfter in a canonical order)
    Map<String, String> miscKeyValues = new LinkedHashMap<>();
    if (!fields.get(CoNLLU_MiscField).equals("_")) {
      Arrays.stream(fields.get(CoNLLU_MiscField).split("\\|")).forEach(
        kv -> miscKeyValues.put(kv.split("=", 2)[0], kv.split("=")[1]));
    }

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
      Map<String, String> mwtKeyValues = new LinkedHashMap<>();
      if (miscInfo != null && !miscInfo.equals("_")) {
        Arrays.stream(miscInfo.split("\\|")).forEach(
          kv -> mwtKeyValues.put(kv.split("=", 2)[0], kv.split("=")[1]));
      }

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
    // create CoreLabels
    List<CoreLabel> coreLabels = new ArrayList<CoreLabel>();
    for (String line : lines) {
      CoreLabel cl = convertLineToCoreLabel(sentence, line, sentenceIdx);
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
          doc.docText += sentence.mwtTokens.get(processedMWTTokens);
          cl.setEndPosition(doc.docText.length());
          lastMWTCharBegin = cl.beginPosition();
          lastMWTCharEnd = cl.endPosition();
          // add after for this MWT by getting after of last CoreLabel for this MWT
          doc.docText += coreLabels.get(sentence.mwtLastCoreLabels.get(processedMWTTokens)).after();
          // move on to next MWT
          processedMWTTokens += 1;
        } else {
          cl.setBeginPosition(lastMWTCharBegin);
          cl.setEndPosition(lastMWTCharEnd);
        }
        cl.setIsMWT(true);
      } else {
        cl.setBeginPosition(doc.docText.length());
        doc.docText += cl.word();
        cl.setEndPosition(doc.docText.length());
        doc.docText += cl.after();
      }
    }

    List<CoreLabel> emptyLabels = new ArrayList<CoreLabel>();
    for (String line : sentence.emptyLines) {
      CoreLabel cl = convertLineToCoreLabel(sentence, line, sentenceIdx);
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
    Map<String, IndexedWord> graphNodes = new HashMap<>();
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
    for (int i = 0; i < lines.size(); i++) {
      List<String> fields = Arrays.asList(lines.get(i).split("\t"));
      // track whether any of these lines signify there is an enhanced graph
      hasEnhanced = hasEnhanced || !fields.get(CoNLLU_EnhancedField).equals("_");
      IndexedWord dependent = graphNodes.get(fields.get(CoNLLU_IndexField));
      if (fields.get(CoNLLU_GovField).equals("0")) {
        // no edges for the ROOT node
        graphRoots.add(dependent);
      } else {
        IndexedWord gov = graphNodes.get(fields.get(CoNLLU_GovField));
        GrammaticalRelation reln = GrammaticalRelation.valueOf(Language.UniversalEnglish, fields.get(CoNLLU_RelnField));
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

      List<String> allLines = new ArrayList<>();
      allLines.addAll(lines);
      allLines.addAll(sentence.emptyLines);
      for (String line : allLines) {
        List<String> fields = Arrays.asList(line.split("\t"));
        IndexedWord dependent = graphNodes.get(fields.get(CoNLLU_IndexField));
        String[] arcs = fields.get(CoNLLU_EnhancedField).split("[|]");
        for (String arc : arcs) {
          String[] arcPieces = arc.split(":", 2);
          if (arcPieces[0].equals("0")) {
            enhancedRoots.add(dependent);
          } else {
            IndexedWord gov = graphNodes.get(arcPieces[0]);
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
