package edu.stanford.nlp.dcoref; 
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.ChunkAnnotationUtils;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.*;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Read _conll file format from CoNLL2011.  See http://conll.bbn.com/index.php/data.html.
 *
 * CoNLL2011 files are in /u/scr/nlp/data/conll-2011/v0/data/
 *    dev
 *    train
 * Contains *_auto_conll files (auto generated) and _gold_conll (hand labelled), default reads _gold_conll
 * There is also /u/scr/nlp/data/conll-2011/v0/conll.trial which has *.conll files (parse has _ at end)
 *
 * Column 	Type 	Description
 * 1   	Document ID 	This is a variation on the document filename
 * 2   	Part number 	Some files are divided into multiple parts numbered as 000, 001, 002, ... etc.
 * 3   	Word number
 * 4   	Word itself
 * 5   	Part-of-Speech
 * 6   	Parse bit 	This is the bracketed structure broken before the first open parenthesis in the parse, and the word/part-of-speech leaf replaced with a *. The full parse can be created by substituting the asterix with the "([pos] [word])" string (or leaf) and concatenating the items in the rows of that column.
 * 7   	Predicate lemma 	The predicate lemma is mentioned for the rows for which we have semantic role information. All other rows are marked with a "-"
 * 8   	Predicate Frameset ID 	This is the PropBank frameset ID of the predicate in Column 7.
 * 9   	Word sense 	This is the word sense of the word in Column 3.
 * 10   	Speaker/Author 	This is the speaker or author name where available. Mostly in Broadcast Conversation and Web Log data.
 * 11   	Named Entities 	These columns identifies the spans representing various named entities.
 * 12:N   	Predicate Arguments 	There is one column each of predicate argument structure information for the predicate mentioned in Column 7.
 * N   	Coreference 	Coreference chain information encoded in a parenthesis structure.
 *
 * @author Angel Chang
 */
public class CoNLL2011DocumentReader  {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(CoNLL2011DocumentReader.class);

  private static final int FIELD_LAST = -1;

  private static final int FIELD_DOC_ID = 0;
  private static final int FIELD_PART_NO = 1;
  private static final int FIELD_WORD_NO = 2;
  private static final int FIELD_WORD = 3;
  private static final int FIELD_POS_TAG = 4;
  private static final int FIELD_PARSE_BIT = 5;
//  private static final int FIELD_PRED_LEMMA = 6;
//  private static final int FIELD_PRED_FRAMESET_ID = 7;
//  private static final int FIELD_WORD_SENSE = 8;
  private static final int FIELD_SPEAKER_AUTHOR = 9;
  private static final int FIELD_NER_TAG = 10;
//  private static final int FIELD_PRED_ARGS = 11;  // Predicate args follow...
  private static final int FIELD_COREF = FIELD_LAST;  // Last field

  private static final int FIELDS_MIN = 12;  // There should be at least 13 fields

  private DocumentIterator docIterator;
//  private String filepath;
  protected final List<File> fileList;
  private int curFileIndex;
  private final Options options;

  public static final Logger logger = Logger.getLogger(CoNLL2011DocumentReader.class.getName());

  public CoNLL2011DocumentReader(String filepath)
  {
    this(filepath, new Options());
  }

  public CoNLL2011DocumentReader(String filepath, Options options)
  {
//    this.filepath = filepath;
    this.fileList = getFiles(filepath, options.filePattern);
    this.options = options;
    if (options.sortFiles) {
      Collections.sort(this.fileList);
    }
    curFileIndex = 0;
    logger.info("Reading " + fileList.size() + " CoNll2011 files from " + filepath);
  }

  private static List<File> getFiles(String filepath, Pattern filter)
  {
    Iterable<File> iter = IOUtils.iterFilesRecursive(new File(filepath), filter);
    List<File> fileList = new ArrayList<>();
    for (File f:iter) {
      fileList.add(f);
    }
    Collections.sort(fileList);
    return fileList;
  }

  public void reset() {
    curFileIndex = 0;
    if (docIterator != null) {
      docIterator.close();
      docIterator = null;
    }
  }

  public Document getNextDocument()
  {
    try {
      if (curFileIndex >= fileList.size()) return null;  // DONE!
      File curFile = fileList.get(curFileIndex);
      if (docIterator == null) {
        docIterator = new DocumentIterator(curFile.getAbsolutePath(), options);
      }
      while ( ! docIterator.hasNext()) {
        logger.info("Processed " + docIterator.docCnt + " documents in " + curFile.getAbsolutePath());
        docIterator.close();
        curFileIndex++;
        if (curFileIndex >= fileList.size()) {
          return null;  // DONE!
        }
        curFile = fileList.get(curFileIndex);
        docIterator = new DocumentIterator(curFile.getAbsolutePath(), options);
      }
      Document next = docIterator.next();
      SieveCoreferenceSystem.logger.fine("Reading document: " + next.getDocumentID());
      return next;
    } catch (IOException ex) {
      throw new RuntimeIOException(ex);
    }
  }

  public void close()
  {
    IOUtils.closeIgnoringExceptions(docIterator);
  }

  public static class NamedEntityAnnotation implements CoreAnnotation<CoreMap> {
    public Class<CoreMap> getType() {
      return CoreMap.class;
    }
  }

  public static class CorefMentionAnnotation implements CoreAnnotation<CoreMap> {
    public Class<CoreMap> getType() {
      return CoreMap.class;
    }
  }

  /** Flags **/
  public static class Options {
    public boolean useCorefBIOESEncoding = false; // Marks Coref mentions with prefix
                                                  // B- begin, I- inside, E- end, S- single
    public boolean annotateTokenCoref = true;    // Annotate token with CorefAnnotation
                                                 // If token belongs to multiple clusters
                                                 // coref clusterid are separted by '|'
    public boolean annotateTokenSpeaker = true;  // Annotate token with SpeakerAnnotation
    public boolean annotateTokenPos = true;      // Annotate token with PartOfSpeechAnnotation
    public boolean annotateTokenNer = true;      // Annotate token with NamedEntityTagAnnotation

    public boolean annotateTreeCoref = false;     // Annotate tree with CorefMentionAnnotation
    public boolean annotateTreeNer = false;       // Annotate tree with NamedEntityAnnotation

    public String backgroundNerTag = "O";        // Background NER tag

    protected String fileFilter;
    protected Pattern filePattern;
    protected boolean sortFiles;

    public Options() {
      this(".*_gold_conll$");      // _gold_conll or _auto_conll   or .conll
    }

    public Options(String filter) {
      fileFilter = filter;
      filePattern = Pattern.compile(fileFilter);
    }

    public void setFilter(String filter) {
      fileFilter = filter;
      filePattern = Pattern.compile(fileFilter);
    }
  }

  public static class Document {
    String documentIdPart;
    String documentID;
    String partNo;
    List<List<String[]>> sentenceWordLists = new ArrayList<>();

    Annotation annotation;
    CollectionValuedMap<String,CoreMap> corefChainMap;
    List<CoreMap> nerChunks;

    public String getDocumentID() {
      return documentID;
    }

    public void setDocumentID(String documentID) {
      this.documentID = documentID;
    }

    public String getPartNo() {
      return partNo;
    }

    public void setPartNo(String partNo) {
      this.partNo = partNo;
    }

    public List<List<String[]>> getSentenceWordLists() {
      return sentenceWordLists;
    }

    public void addSentence(List<String[]> sentence) {
      this.sentenceWordLists.add(sentence);
    }

    public Annotation getAnnotation() {
      return annotation;
    }

    public void setAnnotation(Annotation annotation) {
      this.annotation = annotation;
    }

    public CollectionValuedMap<String,CoreMap> getCorefChainMap()
    {
      return corefChainMap;
    }
  }

  private static String getField(String[] fields, int pos)
  {
    if (pos == FIELD_LAST) {
      return fields[fields.length - 1];
    } else {
      return fields[pos];
    }
  }

  private static String concatField(List<String[]> sentWords, int pos)
  {
    StringBuilder sb = new StringBuilder();
    for (String[] fields:sentWords) {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(getField(fields, pos));
    }
    return sb.toString();
  }

  /** Helper iterator **/
  private static class DocumentIterator extends AbstractIterator<Document> implements Closeable {

    private static final Pattern delimiterPattern = Pattern.compile("\\s+");
    private static final LabeledScoredTreeReaderFactory treeReaderFactory =
            new LabeledScoredTreeReaderFactory((TreeNormalizer) null);

    private final Options options;

    // State
    String filename;
    BufferedReader br;
    Document nextDoc;
    int lineCnt = 0;
    int docCnt = 0;

    public DocumentIterator(String filename, Options options) throws IOException {
      this.options = options;
      this.filename = filename;
      this.br = IOUtils.readerFromString(filename);
      nextDoc = readNextDocument();
    }

    @Override
    public boolean hasNext() {
      return nextDoc != null;
    }

    @Override
    public Document next() {
      if (nextDoc == null) {
        throw new NoSuchElementException("DocumentIterator exhausted.");
      }
      Document curDoc = nextDoc;
      nextDoc = readNextDocument();
      return curDoc;
    }

    private static final Pattern starPattern = Pattern.compile("\\*");

    private static Tree wordsToParse(List<String[]> sentWords)
    {
      StringBuilder sb = new StringBuilder();
      for (String[] fields:sentWords) {
        if (sb.length() > 0) {
          sb.append(' ');
        }

        String str = fields[FIELD_PARSE_BIT].replace("NOPARSE", "X");
        String tagword = "(" + fields[FIELD_POS_TAG] + " " + fields[FIELD_WORD] + ")";
        // Replace stars
        int si = str.indexOf('*');
        sb.append(str.substring(0, si));
        sb.append(tagword);
        sb.append(str.substring(si+1));
        si = str.indexOf('*', si+1);
        if (si >= 0) {
          logger.warning(" Parse bit with multiple *: " + str);
        }
      }
      String parseStr = sb.toString();
      return Tree.valueOf(parseStr, treeReaderFactory);
    }


    private static List<Triple<Integer,Integer,String>> getCorefSpans(List<String[]> sentWords)
    {
      return getLabelledSpans(sentWords, FIELD_COREF, HYPHEN, true);
    }

    private static List<Triple<Integer,Integer,String>> getNerSpans(List<String[]> sentWords)
    {
      return getLabelledSpans(sentWords, FIELD_NER_TAG, ASTERISK, false);
    }


    private static final String ASTERISK = "*";
    private static final String HYPHEN = "-";

    private static List<Triple<Integer,Integer,String>> getLabelledSpans(List<String[]> sentWords, int fieldIndex,
                                                                         String defaultMarker, boolean checkEndLabel)
    {
      List<Triple<Integer,Integer,String>> spans = new ArrayList<>();
      Stack<Triple<Integer,Integer, String>> openSpans = new Stack<>();
      boolean removeStar = (ASTERISK.equals(defaultMarker));
      for (int wordPos = 0; wordPos < sentWords.size(); wordPos++) {
        String[] fields = sentWords.get(wordPos);
        String val = getField(fields, fieldIndex);
        if (!defaultMarker.equals(val)) {
          int openParenIndex = -1;
          int lastDelimiterIndex = -1;
          for (int j = 0; j < val.length(); j++) {
            char c = val.charAt(j);
            boolean isDelimiter = false;
            if (c == '(' || c == ')' || c == '|') {
              if (openParenIndex >= 0) {
                String s = val.substring(openParenIndex+1, j);
                if (removeStar) {
                  s = starPattern.matcher(s).replaceAll("");
                }
                openSpans.push(new Triple<>(wordPos, -1, s));
                openParenIndex = -1;
              }
              isDelimiter = true;
            }
            if (c == '(') {
              openParenIndex = j;
            } else if (c == ')') {
              Triple<Integer, Integer, String> t = openSpans.pop();
              if (checkEndLabel) {
                // NOTE: end parens may cross (usually because mention either start or end on the same token
                // and it is just an artifact of the ordering
                String s = val.substring(lastDelimiterIndex+1, j);
                if (!s.equals(t.third())) {
                  Stack<Triple<Integer,Integer, String>> saved = new Stack<>();
                  while (!s.equals(t.third())) {
                    // find correct match
                    saved.push(t);
                    if (openSpans.isEmpty()) {
                      throw new RuntimeException("Cannot find matching labelled span for " + s);
                    }
                    t = openSpans.pop();
                  }
                  while (!saved.isEmpty()) {
                    openSpans.push(saved.pop());
                  }
                  assert(s.equals(t.third()));
                }
              }
              t.setSecond(wordPos);
              spans.add(t);
            }
            if (isDelimiter) {
              lastDelimiterIndex = j;
            }
          }
          if (openParenIndex >= 0) {
            String s = val.substring(openParenIndex+1, val.length());
            if (removeStar) {
              s = starPattern.matcher(s).replaceAll("");
            }
            openSpans.push(new Triple<>(wordPos, -1, s));
          }
        }
      }
      if (openSpans.size() != 0) {
        throw new RuntimeException("Error extracting labelled spans for column " + fieldIndex + ": "
                + concatField(sentWords, fieldIndex));
      }
      return spans;
    }

    private CoreMap wordsToSentence(List<String[]> sentWords)
    {
      String sentText = concatField(sentWords, FIELD_WORD);
      Annotation sentence = new Annotation(sentText);
      Tree tree = wordsToParse(sentWords);
      sentence.set(TreeCoreAnnotations.TreeAnnotation.class, tree);
      List<Tree> leaves = tree.getLeaves();
      // Check leaves == number of words
      assert(leaves.size() == sentWords.size());
      List<CoreLabel> tokens = new ArrayList<>(leaves.size());
      sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
      for (int i = 0; i < sentWords.size(); i++) {
        String[] fields = sentWords.get(i);
        int wordPos = Integer.parseInt(fields[FIELD_WORD_NO]);
        assert(wordPos == i);
        Tree leaf = leaves.get(i);
        CoreLabel token = (CoreLabel) leaf.label();
        tokens.add(token);
        if (options.annotateTokenSpeaker) {
          String speaker = fields[FIELD_SPEAKER_AUTHOR].replace("_", " ");
          if (!HYPHEN.equals(speaker)) {
            token.set(CoreAnnotations.SpeakerAnnotation.class, speaker);
          }
        }
      }
      if (options.annotateTokenPos) {
        for (Tree leaf:leaves) {
          CoreLabel token = (CoreLabel) leaf.label();
          token.set(CoreAnnotations.PartOfSpeechAnnotation.class, leaf.parent(tree).value());
        }
      }
      if (options.annotateTokenNer) {
        List<Triple<Integer,Integer,String>> nerSpans = getNerSpans(sentWords);
        for (Triple<Integer,Integer,String> nerSpan:nerSpans) {
          int startToken = nerSpan.first();
          int endToken = nerSpan.second(); /* inclusive */
          String label = nerSpan.third();
          for (int i = startToken; i <= endToken; i++) {
            Tree leaf = leaves.get(i);
            CoreLabel token = (CoreLabel) leaf.label();
            String oldLabel = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
            if (oldLabel != null) {
              logger.warning("Replacing old named entity tag " + oldLabel + " with " + label);
            }
            token.set(CoreAnnotations.NamedEntityTagAnnotation.class, label);
          }
        }
        for (CoreLabel token:tokens) {
          if (!token.containsKey(CoreAnnotations.NamedEntityTagAnnotation.class)) {
            token.set(CoreAnnotations.NamedEntityTagAnnotation.class, options.backgroundNerTag);
          }
        }
      }
      if (options.annotateTokenCoref) {
        List<Triple<Integer,Integer,String>> corefSpans = getCorefSpans(sentWords);
        for (Triple<Integer,Integer,String> corefSpan:corefSpans) {
          int startToken = corefSpan.first();
          int endToken = corefSpan.second(); /* inclusive */
          String label = corefSpan.third();
          for (int i = startToken; i <= endToken; i++) {
            Tree leaf = leaves.get(i);
            CoreLabel token = (CoreLabel) leaf.label();
            String curLabel = label;
            if (options.useCorefBIOESEncoding) {
              String prefix;
              if (startToken == endToken) {
                prefix = "S-";
              } else if (i == startToken) {
                prefix = "B-";
              } else if (i == endToken) {
                prefix = "E-";
              } else {
                prefix = "I-";
              }
              curLabel = prefix + label;
            }
            String oldLabel = token.get(CorefCoreAnnotations.CorefAnnotation.class);
            if (oldLabel != null) {
              curLabel = oldLabel + "|" + curLabel;
            }
            token.set(CorefCoreAnnotations.CorefAnnotation.class, curLabel);
          }
        }
      }
      return sentence;
    }

    public static Annotation sentencesToDocument(String documentID, List<CoreMap> sentences)
    {
      String docText = null;
      Annotation document = new Annotation(docText);
      document.set(CoreAnnotations.DocIDAnnotation.class, documentID);
      document.set(CoreAnnotations.SentencesAnnotation.class, sentences);


      // Accumulate docTokens and label sentence with overall token begin/end, and sentence index annotations
      List<CoreLabel> docTokens = new ArrayList<>();
      int sentenceIndex = 0;
      int tokenBegin = 0;
      for (CoreMap sentenceAnnotation:sentences) {
        List<CoreLabel> sentenceTokens = sentenceAnnotation.get(CoreAnnotations.TokensAnnotation.class);
        docTokens.addAll(sentenceTokens);

        int tokenEnd = tokenBegin + sentenceTokens.size();
        sentenceAnnotation.set(CoreAnnotations.TokenBeginAnnotation.class, tokenBegin);
        sentenceAnnotation.set(CoreAnnotations.TokenEndAnnotation.class, tokenEnd);
        sentenceAnnotation.set(CoreAnnotations.SentenceIndexAnnotation.class, sentenceIndex);
        sentenceIndex++;
        tokenBegin = tokenEnd;
      }
      document.set(CoreAnnotations.TokensAnnotation.class, docTokens);

      // Put in character offsets
      int i = 0;
      for (CoreLabel token:docTokens) {
        String tokenText = token.get(CoreAnnotations.TextAnnotation.class);
        token.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, i);
        i+=tokenText.length();
        token.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, i);
        i++; // Skip space
      }
      for (CoreMap sentenceAnnotation:sentences) {
        List<CoreLabel> sentenceTokens = sentenceAnnotation.get(CoreAnnotations.TokensAnnotation.class);
        sentenceAnnotation.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class,
                sentenceTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class));
        sentenceAnnotation.set(CoreAnnotations.CharacterOffsetEndAnnotation.class,
                sentenceTokens.get(sentenceTokens.size()-1).get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
      }

      return document;
    }

    private static Tree getLowestCommonAncestor(Tree root, int startToken, int endToken)
    {
      Tree leftLeaf = Trees.getLeaf(root, startToken);
      Tree rightLeaf = Trees.getLeaf(root, endToken);
      // todo [cdm 2013]: It might be good to climb certain unaries here, like VP or S under NP, but it's not good to climb all unaries (e.g., NP under FRAG)
      return Trees.getLowestCommonAncestor(leftLeaf, rightLeaf, root);
    }

    private static Tree getTreeNonTerminal(Tree root, int startToken, int endToken, boolean acceptPreTerminals)
    {
      Tree t = getLowestCommonAncestor(root, startToken, endToken);
      if (t.isLeaf()) {
        t = t.parent(root);
      }
      if (!acceptPreTerminals && t.isPreTerminal()) {
        t = t.parent(root);
      }
      return t;
    }

    public void annotateDocument(Document document)
    {
      List<CoreMap> sentences = new ArrayList<>(document.sentenceWordLists.size());
      for (List<String[]> sentWords:document.sentenceWordLists) {
        sentences.add(wordsToSentence(sentWords));
      }

      Annotation docAnnotation = sentencesToDocument(document.documentIdPart /*document.documentID + "." + document.partNo */, sentences);
      document.setAnnotation(docAnnotation);

      // Do this here so we have updated character offsets and all
      CollectionValuedMap<String, CoreMap> corefChainMap = new CollectionValuedMap<>(CollectionFactory.<CoreMap>arrayListFactory());
      List<CoreMap> nerChunks = new ArrayList<>();
      for (int i = 0; i < sentences.size(); i++) {
        CoreMap sentence = sentences.get(i);
        Tree tree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
        tree.setSpans();
        List<String[]> sentWords = document.sentenceWordLists.get(i);

        // Get NER chunks
        List<Triple<Integer,Integer,String>> nerSpans = getNerSpans(sentWords);
        for (Triple<Integer,Integer,String> nerSpan:nerSpans) {
          int startToken = nerSpan.first();
          int endToken = nerSpan.second(); /* inclusive */
          String label = nerSpan.third();
          CoreMap nerChunk = ChunkAnnotationUtils.getAnnotatedChunk(sentence, startToken, endToken+1);
          nerChunk.set(CoreAnnotations.NamedEntityTagAnnotation.class, label);
          nerChunk.set(CoreAnnotations.SentenceIndexAnnotation.class, sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
          nerChunks.add(nerChunk);
          Tree t = getTreeNonTerminal(tree, startToken, endToken, true);
          if (t.getSpan().getSource() == startToken && t.getSpan().getTarget() == endToken) {
            nerChunk.set(TreeCoreAnnotations.TreeAnnotation.class, t);
            if (options.annotateTreeNer) {
              Label tlabel = t.label();
              if (tlabel instanceof CoreLabel) {
                ((CoreLabel) tlabel).set(NamedEntityAnnotation.class, nerChunk);
              }
            }
          }
        }

        List<Triple<Integer,Integer,String>> corefSpans = getCorefSpans(sentWords);
        for (Triple<Integer,Integer,String> corefSpan:corefSpans) {
          int startToken = corefSpan.first();
          int endToken = corefSpan.second(); /* inclusive */
          String corefId = corefSpan.third();
          CoreMap mention = ChunkAnnotationUtils.getAnnotatedChunk(sentence, startToken, endToken+1);
          mention.set(CorefCoreAnnotations.CorefAnnotation.class, corefId);
          mention.set(CoreAnnotations.SentenceIndexAnnotation.class, sentence.get(CoreAnnotations.SentenceIndexAnnotation.class));
          corefChainMap.add(corefId, mention);
          Tree t = getTreeNonTerminal(tree, startToken, endToken, true);
          mention.set(TreeCoreAnnotations.TreeAnnotation.class, t);
          if (options.annotateTreeCoref) {
            Label tlabel = t.label();
            if (tlabel instanceof CoreLabel) {
              ((CoreLabel) tlabel).set(CorefMentionAnnotation.class, mention);
            }
          }
        }

      }
      document.corefChainMap = corefChainMap;
      document.nerChunks = nerChunks;
    }

    private static final String docStart = "#begin document ";
    private static final int docStartLength = docStart.length();

    public Document readNextDocument() {
      try {
        List<String[]> curSentWords = new ArrayList<>();
        Document document = null;
        for (String line; (line = br.readLine()) != null; ) {
          lineCnt++;
          line = line.trim();
          if (line.length() != 0) {
            if (line.startsWith(docStart)) {
              // Start of new document
              if (document != null) {
                logger.warning("Unexpected begin document at line (\" + filename + \",\" + lineCnt + \")");
              }
              document = new Document();
              document.documentIdPart = line.substring(docStartLength);
            } else if (line.startsWith("#end document")) {
              annotateDocument(document);
              docCnt++;
              return document;
              // End of document
            } else {
              assert document != null;
              String[] fields = delimiterPattern.split(line);
              if (fields.length < FIELDS_MIN) {
                throw new RuntimeException("Unexpected number of field " + fields.length +
                        ", expected >= " + FIELDS_MIN + " for line (" + filename + "," + lineCnt + "): " + line);
              }
              String curDocId = fields[FIELD_DOC_ID];
              String partNo = fields[FIELD_PART_NO];
              if (document.getDocumentID() == null) {
                document.setDocumentID(curDocId);
                document.setPartNo(partNo);
              } else {
                // Check documentID didn't suddenly change on us
                assert(document.getDocumentID().equals(curDocId));
                assert(document.getPartNo().equals(partNo));
              }
              curSentWords.add(fields);
            }
          } else {
            // Current sentence has ended, new sentence is about to be started
            if (curSentWords.size() > 0) {
              assert document != null;
              document.addSentence(curSentWords);
              curSentWords = new ArrayList<>();
            }
          }
        }
      } catch (IOException ex) {
        throw new RuntimeIOException(ex);
      }
      return null;
    }

    public void close() {
      IOUtils.closeIgnoringExceptions(br);
    }

  } // end static class DocumentIterator

  public static void usage()
  {
    log.info("java edu.stanford.nlp.dcoref.CoNLL2011DocumentReader [-ext <extension to match>] -i <inputpath> -o <outputfile>");
  }

  public static Pair<Integer,Integer> getMention(Integer index, String corefG, List<CoreLabel> sentenceAnno) {

    Integer i = -1;
    Integer end = index;
    for (CoreLabel newAnno : sentenceAnno) {
      i += 1;
      if (i > index) {
        String corefS = newAnno.get(CorefCoreAnnotations.CorefAnnotation.class);
        if (corefS != null) {
          String[] allC = corefS.split("\\|");
          if (Arrays.asList(allC).contains(corefG)) {
            end = i;
          } else {
            break;
          }
        } else {
          break;
        }
      }
    }
    return Pair.makePair(index, end);
  }

  public static boolean include(Map<Pair<Integer,Integer>,String> sentenceInfo,
                                Pair<Integer,Integer> mention,
                                String corefG) {
    Set<Pair<Integer,Integer>> keys = sentenceInfo.keySet();
    for (Pair<Integer, Integer> key : keys) {
      String corefS = sentenceInfo.get(key);
      if (corefS != null && corefS.equals(corefG)) {
        if (key.first < mention.first && key.second.equals(mention.second)) {
          return true;
        }
      }
    }
    return false;
  }

  public static void writeTabSep(PrintWriter pw, CoreMap sentence, CollectionValuedMap<String,CoreMap> chainmap)
  {
    HeadFinder headFinder = new ModCollinsHeadFinder();

    List<CoreLabel> sentenceAnno = sentence.get(CoreAnnotations.TokensAnnotation.class);

    Tree sentenceTree = sentence.get(TreeCoreAnnotations.TreeAnnotation.class);
    Map<Pair<Integer,Integer>,String> sentenceInfo = Generics.newHashMap();

    Set<Tree> sentenceSubTrees = sentenceTree.subTrees();
    sentenceTree.setSpans();
    Map<Pair<Integer,Integer>,Tree> treeSpanMap = Generics.newHashMap();
    Map<Pair<Integer,Integer>,List<Tree>> wordSpanMap = Generics.newHashMap();

    for (Tree ctree : sentenceSubTrees) {
      IntPair span = ctree.getSpan();
      if (span != null) {
        treeSpanMap.put(Pair.makePair(span.getSource(), span.getTarget()), ctree);
        wordSpanMap.put(Pair.makePair(span.getSource(), span.getTarget()), ctree.getLeaves());
      }
    }

    String[][] finalSentence;
    finalSentence = new String [sentenceAnno.size()][];
    Map<Pair<Integer,Integer>,String> allHeads = Generics.newHashMap();

    int index = -1;
    for (CoreLabel newAnno : sentenceAnno) {
      index += 1;
      String word = newAnno.word();
      String tag = newAnno.tag();
      String cat = newAnno.ner();
      String coref = newAnno.get(CorefCoreAnnotations.CorefAnnotation.class);
      finalSentence[index] = new String[4];
      finalSentence[index][0] = word;
      finalSentence[index][1] = tag;
      finalSentence[index][2] = cat;
      finalSentence[index][3] = coref;

      if (coref == null) {
        sentenceInfo.put(Pair.makePair(index, index), coref);
        finalSentence[index][3] = "O";

      } else {
        String[] allC = coref.split("\\|");
        for (String corefG : allC) {
          Pair<Integer, Integer> mention = getMention(index, corefG, sentenceAnno);

          if ( ! include(sentenceInfo, mention, corefG)) {
            // find largest NP in mention
            sentenceInfo.put(mention, corefG);
            Tree mentionTree = treeSpanMap.get(mention);
            String head = null;
            if (mentionTree != null) {
              head = mentionTree.headTerminal(headFinder).nodeString();
            } else if (mention.first.equals(mention.second)) {
              head = word;
            }
            allHeads.put(mention, head);
          }
        }

        if (allHeads.values().contains(word)) {
          finalSentence[index][3] = "MENTION";
        } else {
          finalSentence[index][3] = "O";
        }
      }
    }
    for (int i=0;i<finalSentence.length;i++){
      String[] wordInfo = finalSentence[i];
      if (i<finalSentence.length-1){
        String[] nextWordInfo = finalSentence[i+1];
        if (nextWordInfo[3].equals("MENTION") && nextWordInfo[0].equals("'s")){
          wordInfo[3] = "MENTION";
          finalSentence[i+1][3] = "O";
        }
      }
      pw.println(wordInfo[0] + "\t" + wordInfo[1] + "\t" + wordInfo[2] + "\t" + wordInfo[3]);
    }

    pw.println("");

  }

  public static class CorpusStats
  {
    IntCounter<String> mentionTreeLabelCounter = new IntCounter<>();
    IntCounter<String> mentionTreeNonPretermLabelCounter = new IntCounter<>();
    IntCounter<String> mentionTreePretermNonPretermNoMatchLabelCounter = new IntCounter<>();
    IntCounter<String> mentionTreeMixedLabelCounter = new IntCounter<>();
    IntCounter<Integer> mentionTokenLengthCounter = new IntCounter<>();
    IntCounter<Integer> nerMentionTokenLengthCounter = new IntCounter<>();
    int mentionExactTreeSpan = 0;
    int nonPretermSpanMatches = 0;
    int totalMentions = 0;
    int nestedNerMentions = 0;
    int nerMentions = 0;

    public void process(Document doc)
    {
      List<CoreMap> sentences = doc.getAnnotation().get(CoreAnnotations.SentencesAnnotation.class);
      for (String id:doc.corefChainMap.keySet()) {
        Collection<CoreMap> mentions = doc.corefChainMap.get(id);
        for (CoreMap m:mentions) {
          CoreMap sent = sentences.get(m.get(CoreAnnotations.SentenceIndexAnnotation.class));
          Tree root = sent.get(TreeCoreAnnotations.TreeAnnotation.class);
          Tree t = m.get(TreeCoreAnnotations.TreeAnnotation.class);
          Tree npt = t;
          Tree npt2 = t;
          if (npt.isPreTerminal()) {
            npt = npt.parent(root);
          }
          int sentTokenStart = sent.get(CoreAnnotations.TokenBeginAnnotation.class);
          int tokenStart = m.get(CoreAnnotations.TokenBeginAnnotation.class) - sentTokenStart;
          int tokenEnd = m.get(CoreAnnotations.TokenEndAnnotation.class) - sentTokenStart;
          int length = tokenEnd - tokenStart;
          mentionTokenLengthCounter.incrementCount(length);
          // Check if exact span
          IntPair span = t.getSpan();
          if (span != null) {
            if (span.getSource() == tokenStart && span.getTarget() == tokenEnd - 1) {
              mentionExactTreeSpan++;
            } else {
              logger.info("Tree span is " + span + ", tree node is " + t);
              logger.info("Mention span is " + tokenStart + " " + (tokenEnd - 1) + ", mention is " + m);
            }
          } else {
            logger.warning("No span for " + t);
          }
          IntPair nptSpan = npt.getSpan();
          if (nptSpan.getSource() == tokenStart && nptSpan.getTarget() == tokenEnd - 1) {
            nonPretermSpanMatches++;
            npt2 = npt;
          } else {
            mentionTreePretermNonPretermNoMatchLabelCounter.incrementCount(t.label().value());
            logger.info("NPT: Tree span is " + span + ", tree node is " + npt);
            logger.info("NPT: Mention span is " + tokenStart + " " + (tokenEnd - 1) + ", mention is " + m);
            Label tlabel = t.label();
            if (tlabel instanceof CoreLabel) {
              CoreMap mention = ((CoreLabel) tlabel).get(CorefMentionAnnotation.class);
              String corefClusterId = mention.get(CorefCoreAnnotations.CorefAnnotation.class);
              Collection<CoreMap> clusteredMentions = doc.corefChainMap.get(corefClusterId);
              for (CoreMap m2:clusteredMentions) {
                logger.info("NPT: Clustered mention " + m2.get(CoreAnnotations.TextAnnotation.class));
              }
            }

          }
          totalMentions++;
          mentionTreeLabelCounter.incrementCount(t.label().value());
          mentionTreeNonPretermLabelCounter.incrementCount(npt.label().value());
          mentionTreeMixedLabelCounter.incrementCount(npt2.label().value());
          Label tlabel = t.label();
          if (tlabel instanceof CoreLabel) {
            if (((CoreLabel) tlabel).containsKey(NamedEntityAnnotation.class)) {
              // walk up tree
              nerMentions++;
              nerMentionTokenLengthCounter.incrementCount(length);

              Tree parent = t.parent(root);
              while (parent != null) {
                Label plabel = parent.label();
                if (plabel instanceof CoreLabel) {
                  if (((CoreLabel) plabel).containsKey(NamedEntityAnnotation.class)) {
                    logger.info("NER Mention: " + m);
                    CoreMap parentNerChunk = ((CoreLabel) plabel).get(NamedEntityAnnotation.class);
                    logger.info("Nested inside NER Mention: " + parentNerChunk);
                    logger.info("Nested inside NER Mention parent node: " + parent);
                    nestedNerMentions++;
                    break;
                  }
                }
                parent = parent.parent(root);
              }
            }
          }
        }
      }
    }

    private static void appendFrac(StringBuilder sb, String label, int num, int den)
    {
      double frac = ((double) num)/ den;
      sb.append(label).append("\t").append(frac).append("\t(").append(num).append("/").append(den).append(")");
    }

    private static <E> void appendIntCountStats(StringBuilder sb, String label, IntCounter<E> counts)
    {
      sb.append(label).append("\n");
      List<E> sortedKeys = Counters.toSortedList(counts);
      int total = counts.totalIntCount();
      for (E key:sortedKeys) {
        int count = counts.getIntCount(key);
        appendFrac(sb, key.toString(), count, total);
        sb.append("\n");
      }
    }

    public String toString()
    {
      StringBuilder sb = new StringBuilder();
      appendIntCountStats(sb, "Mention Tree Labels (no preterminals)", mentionTreeNonPretermLabelCounter);
      sb.append("\n");
      appendIntCountStats(sb, "Mention Tree Labels (with preterminals)", mentionTreeLabelCounter);
      sb.append("\n");
      appendIntCountStats(sb, "Mention Tree Labels (preterminals with parent span not match)", mentionTreePretermNonPretermNoMatchLabelCounter);
      sb.append("\n");
      appendIntCountStats(sb, "Mention Tree Labels (mixed)", mentionTreeMixedLabelCounter);
      sb.append("\n");
      appendIntCountStats(sb, "Mention Lengths", mentionTokenLengthCounter);
      sb.append("\n");
      appendFrac(sb, "Mention Exact Non Preterm Tree Span", nonPretermSpanMatches, totalMentions);
      sb.append("\n");
      appendFrac(sb, "Mention Exact Tree Span", mentionExactTreeSpan, totalMentions);
      sb.append("\n");
      appendFrac(sb, "NER", nerMentions, totalMentions);
      sb.append("\n");
      appendFrac(sb, "Nested NER", nestedNerMentions, totalMentions);
      sb.append("\n");
      appendIntCountStats(sb, "NER Mention Lengths", nerMentionTokenLengthCounter);
      return sb.toString();
    }

  }

  /** Reads and dumps output, mainly for debugging. */
  public static void main(String[] args) throws IOException {
    Properties props = StringUtils.argsToProperties(args);
    boolean debug = Boolean.parseBoolean(props.getProperty("debug", "false"));
    String filepath = props.getProperty("i");
    String outfile = props.getProperty("o");
    if (filepath == null || outfile == null) {
      usage();
      System.exit(-1);
    }
    PrintWriter fout = new PrintWriter(outfile);
    logger.info("Writing to " + outfile);
    String ext = props.getProperty("ext");
    Options options;
    if (ext != null) {
      options = new Options(".*" + ext + "$");
    } else {
      options = new Options();
    }
    options.annotateTreeCoref = true;
    options.annotateTreeNer = true;
    CorpusStats corpusStats = new CorpusStats();
    CoNLL2011DocumentReader reader = new CoNLL2011DocumentReader(filepath, options);
    int docCnt = 0;
    int sentCnt = 0;
    int tokenCnt = 0;
    for (Document doc; (doc = reader.getNextDocument()) != null; ) {
      corpusStats.process(doc);
      docCnt++;
      Annotation anno = doc.getAnnotation();
      if (debug) System.out.println("Document " + docCnt + ": " + anno.get(CoreAnnotations.DocIDAnnotation.class));
      for (CoreMap sentence:anno.get(CoreAnnotations.SentencesAnnotation.class)) {
        if (debug) System.out.println("Parse: " + sentence.get(TreeCoreAnnotations.TreeAnnotation.class));
        if (debug) System.out.println("Sentence Tokens: " + StringUtils.join(sentence.get(CoreAnnotations.TokensAnnotation.class), ","));
        writeTabSep(fout,sentence,doc.corefChainMap);
        sentCnt++;
        tokenCnt += sentence.get(CoreAnnotations.TokensAnnotation.class).size();
      }
      if (debug) {
        for (CoreMap ner:doc.nerChunks) {
          System.out.println("NER Chunk: " + ner);
        }
        for (String id:doc.corefChainMap.keySet()) {
          System.out.println("Coref: " + id + " = " + StringUtils.join(doc.corefChainMap.get(id), ";"));
        }
      }
    }
    fout.close();
    System.out.println("Total document count: " + docCnt);
    System.out.println("Total sentence count: " + sentCnt);
    System.out.println("Total token count: " + tokenCnt);
    System.out.println(corpusStats);
  }

}
