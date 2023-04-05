package edu.stanford.nlp.pipeline;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.*;
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
  // TODO: we should handle field 8, DEPS, for an enhanced dependencies
  // doing that requires processing the empty nodes somehow
  public static final int CoNLLU_IndexField = 0;
  public static final int CoNLLU_WordField = 1;
  public static final int CoNLLU_LemmaField = 2;
  public static final int CoNLLU_UPOSField = 3;
  public static final int CoNLLU_XPOSField = 4;
  public static final int CoNLLU_GovField = 6;
  public static final int CoNLLU_RelnField = 7;
  public static final int CoNLLU_MiscField = 9;

  public int columnCount = 10;

  /**
   * patterns to match in CoNLL-U file
   **/
  public static Pattern COMMENT_LINE = Pattern.compile("^#.*");
  public static Pattern DOCUMENT_LINE = Pattern.compile("^# newdoc");
  public static Pattern MWT_LINE = Pattern.compile("^[0-9]+-[0-9]+.*");
  public static Pattern TOKEN_LINE = Pattern.compile("^[0-9]+\t.*");

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
    // data for the sentence contained in # key values
    public HashMap<String, String> sentenceData = new HashMap<>();
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
      if (COMMENT_LINE.matcher(line).matches())
        addSentenceData(line);
      else if (MWT_LINE.matcher(line).matches())
        addMWTData(line);
      else if (TOKEN_LINE.matcher(line).matches())
        tokenLines.add(line);
      else
        return true;
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
   * Read a CoNLL-U file and generate a list of CoNLL-X lines
   **/
  public List<String> readCoNLLUFileCreateCoNLLXLines(String filePath) throws IOException {
    List<CoNLLUDocument> docs = readCoNLLUFileCreateCoNLLUDocuments(filePath);
    List<String> conllXLines = new ArrayList<String>();
    for (CoNLLUDocument doc : docs) {
      for (CoNLLUSentence sentence : doc.sentences) {
        conllXLines.addAll(sentence.tokenLines);
        // add a blank line between sentences
        conllXLines.add("");
      }
    }
    return conllXLines;
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
        docs.add(new CoNLLUDocument());
        docs.get(docs.size() - 1).sentences.add(new CoNLLUSentence());
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
      sentences.add(convertCoNLLUSentenceToCoreMap(doc, sent));
    }
    // set sentences
    finalAnnotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
    // build document wide CoreLabels list
    List<CoreLabel> tokens = new ArrayList<>();
    finalAnnotation.set(CoreAnnotations.TokensAnnotation.class, tokens);
    int documentIdx = 0;
    int sentenceIdx = 0;
    for (CoreMap sentence : finalAnnotation.get(CoreAnnotations.SentencesAnnotation.class)) {
      sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIdx);
      if (sentenceIdx > 0) {
        // for now we're treating a CoNLL-U document as sentences separated by newline
        // so every sentence after the first should have a newline as the previous character
        sentence.get(CoreAnnotations.TokensAnnotation.class).get(0).setBefore("\n");
      }
      for (CoreLabel token : sentence.get(CoreAnnotations.TokensAnnotation.class)) {
        token.set(CoreAnnotations.TokenBeginAnnotation.class, documentIdx);
        token.set(CoreAnnotations.TokenEndAnnotation.class, documentIdx + 1);
        token.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIdx);
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

  /**
   * Convert a list of CoNLL-U token lines into a sentence CoreMap
   **/
  public CoreMap convertCoNLLUSentenceToCoreMap(CoNLLUDocument doc, CoNLLUSentence sentence) {
    List<String> lines = sentence.tokenLines;
    // create CoreLabels
    List<CoreLabel> coreLabels = new ArrayList<CoreLabel>();
    int sentenceTokenIndex = 1;
    for (String line : lines) {
      List<String> fields = Arrays.asList(line.split("\t"));
      CoreLabel cl = new CoreLabel();
      cl.setWord(fields.get(CoNLLU_WordField));
      cl.setValue(fields.get(CoNLLU_WordField));
      cl.setOriginalText(fields.get(CoNLLU_WordField));
      cl.setIsNewline(false);
      if (!fields.get(CoNLLU_LemmaField).equals("_"))
        cl.setLemma(fields.get(CoNLLU_LemmaField));
      if (!fields.get(CoNLLU_UPOSField).equals("_"))
        cl.setTag(fields.get(CoNLLU_UPOSField));
      for (int extraColumnIdx = 10; extraColumnIdx < columnCount && extraColumnIdx < fields.size();
           extraColumnIdx++) {
        cl.set(extraColumns.get(extraColumnIdx), fields.get(extraColumnIdx));
      }
      cl.setIndex(sentenceTokenIndex);

      /*
       * analyze MISC field for this token
       *
       * MISC should be a "|" separated list in the final column
       *
       * example: SpaceAfter=No|NER=PERSON
       *
       * supported keys:
       *
       * - SpaceAfter (e.g. No if next token is punctuation mark)
       *
       */
      if (!fields.get(CoNLLU_MiscField).equals("_")) {
        HashMap<String, String> miscKeyValues = new HashMap<>();
        Arrays.stream(fields.get(CoNLLU_MiscField).split("\\|")).forEach(
            kv -> miscKeyValues.put(kv.split("=")[0], kv.split("=")[1]));
        // unless SpaceAfter=No, add a space after this token
        if (!miscKeyValues.getOrDefault("SpaceAfter", "Yes").equals("No")) {
          cl.setAfter(" ");
        } else {
          cl.setAfter("");
        }
      } else {
        cl.setAfter(" ");
      }

      // handle the MWT info
      if (sentence.mwtData.containsKey(sentenceTokenIndex - 1)) {
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
        }
        // handle MISC info
        String miscInfo = sentence.mwtMiscs.get(sentence.mwtData.get(sentenceTokenIndex - 1));
        for (String miscKV : miscInfo.split("\\|")) {
          if (miscKV.startsWith("SpaceAfter")) {
            cl.setAfter(miscKV.split("=")[1].equals("No") ? "" : " ");
          }
        }
      } else {
        cl.setIsMWT(false);
        cl.setIsMWTFirst(false);
      }
      sentenceTokenIndex++;
      coreLabels.add(cl);
    }
    // the last token should have a newline after
    coreLabels.get(coreLabels.size() - 1).setAfter("\n");
    // set before
    coreLabels.get(0).setBefore("");
    for (int i = 1 ; i < coreLabels.size() ; i++) {
      if (coreLabels.get(i).isMWT() && !coreLabels.get(i).isMWTFirst()) {
        // if an MWT derived token and NOT the first one, match before of
        // previous ; MWT derived tokens should have same char offsets,
        // before, and after of the original token before splitting
        coreLabels.get(i).setBefore(coreLabels.get(i-1).before());
      } else {
        // standard tokens and first derived token from an MWT
        // should set before to match after of previous token
        coreLabels.get(i).setBefore(coreLabels.get(i - 1).after());
      }
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

    // build SemanticGraphEdges
    List<SemanticGraphEdge> graphEdges = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      List<String> fields = Arrays.asList(lines.get(i).split("\t"));
      // skip the ROOT node
      if (fields.get(CoNLLU_GovField).equals("0"))
        continue;
      IndexedWord dependent = new IndexedWord(coreLabels.get(i));
      IndexedWord gov = new IndexedWord(coreLabels.get(Integer.parseInt(fields.get(CoNLLU_GovField)) - 1));
      GrammaticalRelation reln = GrammaticalRelation.valueOf(fields.get(CoNLLU_RelnField));
      graphEdges.add(new SemanticGraphEdge(gov, dependent, reln, 1.0, false));
    }
    // build SemanticGraph
    SemanticGraph depParse = SemanticGraphFactory.makeFromEdges(graphEdges);
    // build sentence CoreMap with full text
    Annotation sentenceCoreMap = new Annotation(doc.docText.substring(sentenceCharBegin).trim());
    // add tokens
    sentenceCoreMap.set(CoreAnnotations.TokensAnnotation.class, coreLabels);
    // add dependency graph
    sentenceCoreMap.set(SemanticGraphCoreAnnotations.BasicDependenciesAnnotation.class, depParse);
    return sentenceCoreMap;
  }

}
