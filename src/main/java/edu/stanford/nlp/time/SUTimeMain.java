package edu.stanford.nlp.time;
import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.io.ReaderInputStream;
import edu.stanford.nlp.io.TeeStream;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.MatchedExpression;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.stats.PrecisionRecallStats;
import edu.stanford.nlp.util.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.LogManager;
import java.util.regex.Pattern;


/**
 * Main program for testing SUTime.
 * <br>
 * Processing a text string:
 * <pre>
 * -in.type TEXT
 * -date YYYY-MM-dd
 * -i &lt;text&gt;
 * -o &lt;output file&gt;
 * </pre>
 *
 * Processing a text file:
 * <pre>
 * -in.type TEXTFILE
 * -date YYYY-MM-dd
 * -i input.txt
 * -o &lt;output file&gt;
 * </pre>
 *
 * Running on Timebank
 * <pre>
 * -in.type TIMEBANK_CSV
 * -i timebank.csv
 * -tempeval2.dct dct.txt
 * -o &lt;output directory&gt;
 * -eval &lt;evaluation script&gt;
 * </pre>
 *
 * Evaluating on Tempeval2
 * <pre>
 * -in.type TEMPEVAL2
 * -i &lt;directory with english data&gt;
 * -o &lt;output directory&gt;
 * -eval &lt;evaluation script&gt;
 * -tempeval2.dct dct file (with document creation times)
 *
 * TEMPEVAL2 (download from http://timeml.org/site/timebank/timebank.html)
 * Evaluation is token based.
 *
 * TRAINING (english):
 *
 * GUTIME:
 * precision   0.88
 * recall      0.71
 * f1-measure  0.79
 * accuracy    0.98
 * attribute type       0.92
 * attribute value      0.31   // LOW SCORE here is due to difference in format (no -,: in date)
 *
 * After fixing some formats for GUTIME:
 *   (GUTIME syntax is inconsistent at times (1991W 8WE, 19980212EV)
 * attribute value      0.67
 *
 * SUTIME:
 * Default: sutime.teRelHeurLevel=NONE, restrictToTimex3=false
 * precision   0.873
 * recall      0.897
 * f1-measure  0.885
 * accuracy    0.991
 *
 *                                P      R    F1
 * attribute type       0.918 | 0.751 0.802 0.776
 * attribute value      0.762 | 0.623 0.665 0.644
 *
 *                                        P      R    F1
 * mention attribute type       0.900 | 0.780 0.833 0.805
 * mention attribute value      0.742 | 0.643 0.687 0.664
 *
 * sutime.teRelHeurLevel=MORE, restrictToTimex3=true
 * precision   0.876
 * recall      0.889
 * f1-measure  0.882
 * accuracy    0.991
 *                                P      R    F1
 * attribute type       0.918 | 0.744 0.798 0.770
 * attribute value      0.776 | 0.629 0.675 0.651
 *
 *                                        P      R    F1
 * mention attribute type       0.901 | 0.780 0.836 0.807
 * mention attribute value      0.750 | 0.649 0.696 0.672
 *
 * ------------------------------------------------------------------------------
 * TEST (english):
 *
 * GUTIME:
 * precision   0.89
 * recall      0.79
 * f1-measure  0.84
 * accuracy    0.99
 *
 * attribute type       0.95
 * attribute value      0.68
 *
 * SUTIME:
 * Default: sutime.teRelHeurLevel=NONE, restrictToTimex3=false
 * precision   0.878
 * recall      0.963
 * f1-measure  0.918
 * accuracy    0.996
 *
 *                                P      R    F1
 * attribute type       0.953 | 0.820 0.904 0.860
 * attribute value      0.791 | 0.680 0.750 0.713
 *
 *                                        P      R    F1
 * mention attribute type       0.954 | 0.837 0.923 0.878
 * mention attribute value      0.781 | 0.686 0.756 0.720
 *
 * sutime.teRelHeurLevel=MORE, restrictToTimex3=true
 * precision   0.881
 * recall      0.963
 * f1-measure  0.920
 * accuracy    0.995
 *                                P      R    F1
 * attribute type       0.959 | 0.821 0.910 0.863
 * attribute value      0.818 | 0.699 0.776 0.736
 *
 *                                        P      R    F1
 * mention attribute type       0.961 | 0.844 0.936 0.888
 * mention attribute value      0.803 | 0.705 0.782 0.742
 *
 * </pre>
 * @author Angel Chang
 */
public class SUTimeMain  {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels log = Redwood.channels(SUTimeMain.class);
  protected static String PYTHON = null;


  private SUTimeMain() {} // static class


  /*
   * Other Time corpora: (see also http://timeml.org/site/timebank/timebank.html)
   * LDC2006T08 TimeBank 1.2 (Uses TIMEX3)
   * LDC2005T07 ACE Time Normalization (TERN) 2004 English Training Data v 1.0 (Uses TIMEX2)
   *   GUTime achieved .85, .78, and .82 F-measure for timex2, text, and val fields
   * LDC2010T18 ACE Time Normalization (TERN) 2004 English Evaluation Data V1.0
   */
  ////////////////////////////////////////////////////////////////////////////////////////

  private static class EvalStats {
    PrecisionRecallStats prStats = new PrecisionRecallStats();
//    PrecisionRecallStats tokenPrStats = new PrecisionRecallStats();
    PrecisionRecallStats valPrStats = new PrecisionRecallStats();
    PrecisionRecallStats estPrStats = new PrecisionRecallStats();
  }

  private static class TimebankTimex {
    String timexId;
    String timexVal;
    String timexOrigVal;
    String timexStr;
    int tid;

    private TimebankTimex(String timexId, String timexVal, String timexOrigVal, String timexStr) {
      this.timexId = timexId;
      this.timexVal = timexVal;
      this.timexOrigVal = timexOrigVal;
      this.timexStr = timexStr;
      if (timexId != null && timexId.length() > 0) {
        tid = Integer.parseInt(timexId);
      }
    }
  }

  private static class TimebankSent {
    boolean initialized = false;
    String docId;
    @SuppressWarnings("unused")
    String docFilename;
    String docPubDate;
    String sentId;
    String text;
    List<TimebankTimex> timexes = new ArrayList<>();

    List<String> origItems = new ArrayList<>();

    public boolean add(String item) {
      String[] fields = item.split("\\s*\\|\\s*", 9);
      String docId = fields[0];
      String docFilename = fields[1];
      String docPubDate = fields[2];
      String sentId = fields[3];
      String sent = fields[8];
      if (initialized) {
        // check compatibility;
        if (!docId.equals(this.docId) || !sentId.equals(this.sentId)) {
          return false;
        }
      } else {
        this.docId = docId;
        this.docFilename = docFilename;
        this.docPubDate = docPubDate;
        this.sentId = sentId;
        this.text = sent;
        initialized = true;
      }

      origItems.add(item);
      String timexId = fields[4];
      String timexVal = fields[5];
      String timexOrigVal = fields[6];
      String timexStr = fields[7];
      if (timexId != null && timexId.length() > 0) {
        timexes.add(new TimebankTimex(timexId, timexVal, timexOrigVal, timexStr));
      }
      return true;
    }


  }

//Overall: PrecisionRecallStats[tp=877,fp=199,fn=386,p=0.82  (877/1076),r=0.69  (877/1263),f1=0.75]
//Value: PrecisionRecallStats[tp=229,fp=199,fn=1034,p=0.54  (229/428),r=0.18  (229/1263),f1=0.27]

  // Process one item from timebank CSV file
  private static void processTimebankCsvSent(AnnotationPipeline pipeline, TimebankSent sent, PrintWriter pw, EvalStats evalStats)
  {
    if (sent != null) {
      Collections.sort(sent.timexes, (o1, o2) -> {
        if (o1.tid == o2.tid) { return 0; }
        else return (o1.tid < o2.tid)? -1:1;
      });
      pw.println();
      for (String item:sent.origItems) {
        pw.println("PROC |" + item);
      }
      Annotation annotation = new Annotation(sent.text);
      annotation.set(CoreAnnotations.DocDateAnnotation.class, sent.docPubDate);
      pipeline.annotate(annotation);

      List<CoreMap> timexes = annotation.get(TimeAnnotations.TimexAnnotations.class);
      int i = 0;
      for (CoreMap t:timexes) {
        String[] newFields;
        if (sent.timexes.size() > i) {
          String res;
          TimebankTimex goldTimex = sent.timexes.get(i);
          Timex guessTimex = t.get(TimeAnnotations.TimexAnnotation.class);
          String s1 = goldTimex.timexStr.replaceAll("\\s+", "");
          String s2 = guessTimex.text().replaceAll("\\s+", "");
          if (s1.equals(s2)) {
            evalStats.estPrStats.incrementTP();
            res = "OK";
          } else {
            evalStats.estPrStats.incrementFP();
            evalStats.estPrStats.incrementFN();
            res = "BAD";
          }
          newFields = new String[] { res, goldTimex.timexId, goldTimex.timexVal, goldTimex.timexOrigVal, goldTimex.timexStr,
                  t.get(TimeAnnotations.TimexAnnotation.class).toString() };
          i++;
        } else {
          newFields = new String[] { "NONE" , t.get(TimeAnnotations.TimexAnnotation.class).toString()};
          evalStats.estPrStats.incrementFP();
        }
        pw.println("GOT | "+ StringUtils.join(newFields, "|"));
      }
      for (; i < sent.timexes.size(); i++) {
        evalStats.estPrStats.incrementFN();
      }

      i = 0;
      int lastIndex = 0;
      for (TimebankTimex goldTimex:sent.timexes) {
          int index = sent.text.indexOf(goldTimex.timexStr, lastIndex);
          int endIndex = index + goldTimex.timexStr.length();
          boolean found = false;
          for (; i < timexes.size(); i++) {
            CoreMap t = timexes.get(i);
            if (t.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) >= endIndex) {
              break;
            } else {
              if (t.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) >= index) {
                found = true;
                evalStats.prStats.incrementTP();
                if (goldTimex.timexOrigVal.equals(t.get(TimeAnnotations.TimexAnnotation.class).value())) {
                  evalStats.valPrStats.incrementTP();
                } else {
                  evalStats.valPrStats.incrementFN();
                }
              } else {
                evalStats.prStats.incrementFP();
                evalStats.valPrStats.incrementFP();
              }
            }
          }
          if (!found)  {
            evalStats.prStats.incrementFN();
            evalStats.valPrStats.incrementFN();
          }
          lastIndex = endIndex;
      }

      for (; i < timexes.size(); i++) {
        evalStats.prStats.incrementFP();
        evalStats.valPrStats.incrementFP();
      }
    }
  }



  // Process CSV file with just timebank sentences with time expressions
  public static void processTimebankCsv(AnnotationPipeline pipeline, String in, String out, String eval) throws IOException {
    BufferedReader br = IOUtils.readerFromString(in);
    PrintWriter pw = (out != null)? IOUtils.getPrintWriter(out):new PrintWriter(System.out);
    String line;
//    boolean dataStarted = false;
    boolean dataStarted = true;
    TimebankSent sent = new TimebankSent();
    String item = null;
    EvalStats evalStats = new EvalStats();
    line = br.readLine(); // Skip first line
    while ((line = br.readLine()) != null) {
      if (line.trim().length() == 0) continue;
      if (dataStarted) {
        if (line.contains("|")) {
          if (item != null) {
            boolean addOld = sent.add(item);
            if (!addOld) {
              processTimebankCsvSent(pipeline, sent, pw, evalStats);
              sent = new TimebankSent();
              sent.add(item);
            }
          }
          item = line;
        } else {
          item += " " + line;
        }
      } else {
        if (line.matches("#+ BEGIN DATA #+")) {
          dataStarted = true;
        }
      }
    }
    if (item != null) {
      boolean addOld = sent.add(item);
      if (!addOld) {
        processTimebankCsvSent(pipeline, sent, pw, evalStats);
        sent = new TimebankSent();
        sent.add(item);
      }
      processTimebankCsvSent(pipeline, sent, pw, evalStats);
    }
    br.close();
    if (out != null) { pw.close(); }
    System.out.println("Estimate: " + evalStats.estPrStats.toString(2));
    System.out.println("Overall: " + evalStats.prStats.toString(2));
    System.out.println("Value: " + evalStats.valPrStats.toString(2));
  }

  private static String joinWordTags(List<? extends CoreMap> l, String glue, int start, int end) {
    return StringUtils.join(l, glue, in -> in.get(CoreAnnotations.TextAnnotation.class) + '/' + in.get(CoreAnnotations.PartOfSpeechAnnotation.class), start, end);
  }

  private static void processTempEval2Doc(AnnotationPipeline pipeline, Annotation docAnnotation,
                                          Map<String, List<TimexAttributes>> timexMap,
                                          PrintWriter extPw, PrintWriter attrPw, PrintWriter debugPw,
                                          PrintWriter attrDebugPwGold, PrintWriter attrDebugPw) {
    pipeline.annotate(docAnnotation);
    String docId = docAnnotation.get(CoreAnnotations.DocIDAnnotation.class);
    String docDate = docAnnotation.get(CoreAnnotations.DocDateAnnotation.class);
    List<CoreMap> sents = docAnnotation.get(CoreAnnotations.SentencesAnnotation.class);

    if (timexMap != null) {
      List<TimexAttributes> golds = updateTimexText(timexMap, docAnnotation);
      if (attrDebugPwGold != null && golds != null) {
        for (TimexAttributes g:golds) {
          String[] newFields = { docId, docDate,
                  String.valueOf(g.sentIndex),
                  String.valueOf(g.tokenStart),
                  String.valueOf(g.tokenEnd),
                  /*g.tid, */ g.type, g.value, g.text, g.context };
          attrDebugPwGold.println(StringUtils.join(newFields, "\t"));
        }
      }
    }
    if (attrDebugPw != null) {
      for (CoreMap sent:sents) {
        List<CoreMap> timexes = sent.get(TimeAnnotations.TimexAnnotations.class);
        if (timexes != null) {
          for (CoreMap t:timexes) {
            Timex timex = t.get(TimeAnnotations.TimexAnnotation.class);
            int sentIndex = sent.get(CoreAnnotations.SentenceIndexAnnotation.class);
            int sentTokenStart = sent.get(CoreAnnotations.TokenBeginAnnotation.class);
            int tokenStart;
            int tokenEnd;
            if (t.containsKey(CoreAnnotations.TokenBeginAnnotation.class)) {
              tokenStart = t.get(CoreAnnotations.TokenBeginAnnotation.class) - sentTokenStart;
              tokenEnd = t.get(CoreAnnotations.TokenEndAnnotation.class) - sentTokenStart;
            } else {
              CoreMap cm = ChunkAnnotationUtils.getAnnotatedChunkUsingCharOffsets(docAnnotation,
                      t.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                      t.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
              tokenStart = cm.get(CoreAnnotations.TokenBeginAnnotation.class) - sentTokenStart;
              tokenEnd = cm.get(CoreAnnotations.TokenEndAnnotation.class) - sentTokenStart;
            }
            String context = joinWordTags(sent.get(CoreAnnotations.TokensAnnotation.class), " ", tokenStart-3, tokenEnd+3);
            String[] newFields = { docId, docDate,
                String.valueOf(sentIndex),
                String.valueOf(tokenStart), String.valueOf(tokenEnd),
                /*timex.tid(), */ timex.timexType(), timex.value(), timex.text(), context};
            attrDebugPw.println(StringUtils.join(newFields, "\t"));
          }
        }
      }
    }
    if (debugPw != null) {
      List<CoreMap> timexes = docAnnotation.get(TimeAnnotations.TimexAnnotations.class);
      for (CoreMap t:timexes) {
        String[] newFields = { docId, docDate, t.get(TimeAnnotations.TimexAnnotation.class).toString() };
        debugPw.println("GOT | "+ StringUtils.join(newFields, "|"));
      }
    }
    if (extPw != null || attrPw != null) {
     for (CoreMap sent:sents) {
      int sentTokenBegin = sent.get(CoreAnnotations.TokenBeginAnnotation.class);
      for (CoreMap t:sent.get(TimeAnnotations.TimexAnnotations.class)) {
        Timex tmx = t.get(TimeAnnotations.TimexAnnotation.class);
        List<CoreLabel> tokens = t.get(CoreAnnotations.TokensAnnotation.class);
        int tokenIndex = 0;
        if (tokens == null) {
          CoreMap cm = ChunkAnnotationUtils.getAnnotatedChunkUsingCharOffsets(docAnnotation,
                  t.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
                  t.get(CoreAnnotations.CharacterOffsetEndAnnotation.class));
          tokens = cm.get(CoreAnnotations.TokensAnnotation.class);
          tokenIndex = cm.get(CoreAnnotations.TokenBeginAnnotation.class);
        } else {
          tokenIndex = t.get(CoreAnnotations.TokenBeginAnnotation.class);
        }
        tokenIndex = tokenIndex - sentTokenBegin;
        String sentenceIndex = String.valueOf(sent.get(CoreAnnotations.SentenceIndexAnnotation.class));
        int tokenCount = 0;
        for (@SuppressWarnings("unused") CoreLabel token:tokens) {
          String[] extFields = {
                  docId,
                  sentenceIndex,
                  String.valueOf(tokenIndex),
                  "timex3",
                  tmx.tid(),
                  "1"};
          String extString = StringUtils.join(extFields, "\t");
          if (extPw != null) extPw.println(extString);
          if (attrPw != null /* && tokenCount == 0 */) {
            String[] attrFields = {
                  "type",
                  tmx.timexType(),
            };
            attrPw.println(extString + "\t" + StringUtils.join(attrFields, "\t"));
            if (tmx.value() != null) {
              String val = tmx.value();
              // Fix up expression values (needed for GUTime)
              if (useGUTime) {
                if ("TIME".equals(tmx.timexType())) {
                  if (val.matches("T\\d{4}")) {
                    val = "T" + val.substring(1,3) + ":" + val.substring(3,5);
                  }
                } else if ("DATE".equals(tmx.timexType())) {
                  if (val.matches("\\d{8}T.*")) {
                    val = val.substring(0,4) + "-" + val.substring(4,6) + "-" + val.substring(6);
                  } else if (val.matches("\\d{8}")) {
                    val = val.substring(0,4) + "-" + val.substring(4,6) + "-" + val.substring(6,8);
                  } else if (val.matches("\\d\\d\\d\\d..")) {
                    val = val.substring(0,4) + "-" + val.substring(4,6);
                  } else if (val.matches("[0-9X]{4}W[0-9X]{2}.*")) {
                    if (val.length() > 7) {
                      val = val.substring(0,4) + "-" + val.substring(4,7) + "-" + val.substring(7);
                    } else {
                      val = val.substring(0,4) + "-" + val.substring(4,7);
                    }
                  }
                }
              } /*else {
                // SUTIME
                if ("DATE".equals(tmx.timexType())) {
                  if (val.matches("\\d\\d\\dX")) {
                    val = val.substring(0,3);  // Convert 199X to 199
                  }
                }
              }   */
              attrFields[0] = "value";
              attrFields[1] = val;

              attrPw.println(extString + "\t" + StringUtils.join(attrFields, "\t"));
            }
          }
          tokenIndex++;
          tokenCount++;
        }
      }
     }
    }
  }

  private static CoreLabelTokenFactory tokenFactory = new CoreLabelTokenFactory();

  private static CoreMap wordsToSentence(List<String> sentWords) {
    String sentText = StringUtils.join(sentWords, " ");
    Annotation sentence = new Annotation(sentText);
    List<CoreLabel> tokens = new ArrayList<>(sentWords.size());
    for (String text:sentWords) {
      CoreLabel token = tokenFactory.makeToken();
      token.set(CoreAnnotations.TextAnnotation.class, text);
      tokens.add(token);
    }
    sentence.set(CoreAnnotations.TokensAnnotation.class, tokens);
    return sentence;
  }

  public static Annotation sentencesToDocument(String documentID, String docDate, List<CoreMap> sentences) {
    String docText = ChunkAnnotationUtils.getTokenText(sentences, CoreAnnotations.TextAnnotation.class);
    Annotation document = new Annotation(docText);
    document.set(CoreAnnotations.DocIDAnnotation.class, documentID);
    document.set(CoreAnnotations.DocDateAnnotation.class, docDate);
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

  private static class TimexAttributes {
    public String tid;
    public int sentIndex;
    public int tokenStart;
    public int tokenEnd;
    public String text;
    public String type;
    public String value;
    public String context;

    public TimexAttributes(String tid, int sentIndex, int tokenIndex) {
      this.tid = tid;
      this.sentIndex = sentIndex;
      this.tokenStart = tokenIndex;
      this.tokenEnd = tokenIndex + 1;
    }
  }

  private static TimexAttributes findTimex(Map<String,List<TimexAttributes>> timexMap, String docId, String tid) {
    // Find entry
    List<TimexAttributes> list = timexMap.get(docId);
    for (TimexAttributes timex:list) {
      if (timex.tid.equals(tid)) {
        return timex;
      }
    }
    return null;
  }

  private static List<TimexAttributes> updateTimexText(Map<String,List<TimexAttributes>> timexMap, Annotation docAnnotation) {
    // Find entry
    String docId = docAnnotation.get(CoreAnnotations.DocIDAnnotation.class);
    List<CoreMap> sents = docAnnotation.get(CoreAnnotations.SentencesAnnotation.class);
    List<TimexAttributes> list = timexMap.get(docId);
    if (list != null) {
      for (TimexAttributes timex:list) {
        CoreMap sent = sents.get(timex.sentIndex);
        List<CoreLabel> tokens = sent.get(CoreAnnotations.TokensAnnotation.class);
        timex.text = StringUtils.joinWords(tokens, " ", timex.tokenStart, timex.tokenEnd);
        timex.context = joinWordTags(tokens, " ", timex.tokenStart - 3, timex.tokenEnd + 3);

/*        StringBuilder sb = new StringBuilder("");
        for (int i = timex.tokenStart; i < timex.tokenEnd; i++) {
          if (sb.length() > 0) { sb.append(" "); }
          sb.append(tokens.get(i).word());
        }
        timex.text = sb.toString();

        // Get context
        sb.setLength(0);
        int c1 = Math.max(0, timex.tokenStart - 3);
        int c2 = Math.min(tokens.size(), timex.tokenEnd + 3);
        for (int i = c1; i < c2; i++) {
          if (sb.length() > 0) { sb.append(" "); }
          sb.append(tokens.get(i).word());
        }
        timex.context = sb.toString();             */

      }
      return list;
    }
    return null;
  }

  private static Map<String,List<TimexAttributes>> readTimexAttrExts(String extentsFile, String attrsFile) throws IOException {
    Map<String,List<TimexAttributes>> timexMap = Generics.newHashMap();
    BufferedReader extBr = IOUtils.readerFromString(extentsFile);
    String line;
    String lastDocId = null;
    TimexAttributes lastTimex = null;
    while ((line = extBr.readLine()) != null) {
      if (line.trim().isEmpty()) continue;
      // Simple tab delimited file
      String[] fields = line.split("\t");
      String docName = fields[0];
      int sentNo = Integer.parseInt(fields[1]);
      int tokenNo = Integer.parseInt(fields[2]);
      String tid = fields[4];

      if (lastDocId != null && lastDocId.equals(docName) && lastTimex != null && lastTimex.tid.equals(tid)) {
        // Expand previous
        assert(lastTimex.sentIndex == sentNo);
        lastTimex.tokenEnd = tokenNo + 1;
      } else {
        lastDocId = docName;
        lastTimex = new TimexAttributes(tid, sentNo, tokenNo);
        List<TimexAttributes> list = timexMap.get(docName);
        if (list == null) {
          timexMap.put(docName, list = new ArrayList<>());
        }
        list.add(lastTimex);
      }
    }
    extBr.close();

    BufferedReader attrBr = IOUtils.readerFromString(attrsFile);
    while ((line = attrBr.readLine()) != null) {
      if (line.trim().length() == 0) continue;
      // Simple tab delimited file
      String[] fields = line.split("\t");
      String docName = fields[0];
      int sentNo = Integer.parseInt(fields[1]);
      int tokenNo = Integer.parseInt(fields[2]);
      String tid = fields[4];
      String attrname = fields[6];
      String attrvalue = fields[7];

      // Find entry
      TimexAttributes timex = findTimex(timexMap, docName, tid);
      assert(timex.sentIndex == sentNo);
      assert(timex.tokenStart <= tokenNo && timex.tokenEnd > tokenNo);

      switch (attrname) {
        case "type":
          assert (timex.type == null || timex.type.equals(attrvalue));
          timex.type = attrvalue;
          break;
        case "value":
          assert (timex.value == null || timex.value.equals(attrvalue));
          timex.value = attrvalue;
          break;
        default:
          throw new RuntimeException("Error processing " + attrsFile + ":" +
              "Unknown attribute " + attrname + ": from line " + line);
      }
    }
    attrBr.close();
    return timexMap;
  }

  public static void processTempEval2Tab(AnnotationPipeline pipeline, String in, String out, Map<String,String> docDates) throws IOException
  {
    Map<String,List<TimexAttributes>> timexMap = readTimexAttrExts(in  + "/timex-extents.tab", in  + "/timex-attributes.tab");
    BufferedReader br = IOUtils.readerFromString(in  + "/base-segmentation.tab");
    PrintWriter debugPw = IOUtils.getPrintWriter(out + "/timex-debug.out");
    PrintWriter attrPw = IOUtils.getPrintWriter(out + "/timex-attrs.res.tab");
    PrintWriter extPw = IOUtils.getPrintWriter(out + "/timex-extents.res.tab");
    PrintWriter attrDebugPwGold = IOUtils.getPrintWriter(out + "/timex-attrs.debug.gold.tab");
    PrintWriter attrDebugPw = IOUtils.getPrintWriter(out + "/timex-attrs.debug.res.tab");
    String line;
    String curDocName = null;
    int curSentNo = -1;
    List<String> tokens = null;
    List<CoreMap> sentences = null;
    while ((line = br.readLine()) != null) {
      if (line.trim().length() == 0) continue;
      // Simple tab delimited file
      String[] fields = line.split("\t");
      String docName = fields[0];
      int sentNo = Integer.parseInt(fields[1]);
      //int tokenNo = Integer.parseInt(fields[2]);
      String tokenText = fields[3];

      // Create little annotation with sentences and tokens
      if (!docName.equals(curDocName)) {
        if (curDocName != null) {
          // Process document
          CoreMap lastSentence = wordsToSentence(tokens);
          sentences.add(lastSentence);
          Annotation docAnnotation = sentencesToDocument(curDocName, docDates.get(curDocName), sentences);
          processTempEval2Doc(pipeline, docAnnotation, timexMap, extPw, attrPw, debugPw, attrDebugPwGold, attrDebugPw);
          curDocName = null;
        }
        // New doc
        tokens = new ArrayList<>();
        sentences = new ArrayList<>();
      } else if (curSentNo != sentNo) {
        CoreMap lastSentence = wordsToSentence(tokens);
        sentences.add(lastSentence);
        tokens = new ArrayList<>();
      }
      tokens.add(tokenText);
      curDocName = docName;
      curSentNo = sentNo;
    }
    if (curDocName != null) {
      // Process document
      CoreMap lastSentence = wordsToSentence(tokens);
      sentences.add(lastSentence);
      Annotation docAnnotation = sentencesToDocument(curDocName, docDates.get(curDocName), sentences);
      processTempEval2Doc(pipeline, docAnnotation, timexMap, extPw, attrPw, debugPw, attrDebugPwGold, attrDebugPw);
      curDocName = null;
    }
    br.close();
    extPw.close();
    attrPw.close();
    debugPw.close();
    attrDebugPwGold.close();
    attrDebugPw.close();
  }

  public static void processTempEval2(AnnotationPipeline pipeline, String in, String out, String eval, String dct) throws IOException, ParseException
  {
    Map<String,String> docDates = (dct != null)? IOUtils.readMap(dct):IOUtils.readMap(in + "/dct.txt");
    if (requiredDocDateFormat != null) {
      // convert from yyyyMMdd to requiredDocDateFormat
      DateFormat defaultFormatter = new SimpleDateFormat("yyyyMMdd");
      DateFormat requiredFormatter = new SimpleDateFormat(requiredDocDateFormat);
      for (Map.Entry<String, String> docDateEntry : docDates.entrySet()) {
        Date date = defaultFormatter.parse(docDateEntry.getValue());
        docDates.put(docDateEntry.getKey(), requiredFormatter.format(date));
      }
    }
    processTempEval2Tab(pipeline, in, out, docDates);
    if (eval != null) {
      List<String> command = new ArrayList<>();
      if (PYTHON != null) {
        command.add(PYTHON);
      }
      command.add(eval);
      command.add(in + "/base-segmentation.tab");
      command.add(in + "/timex-extents.tab");
      command.add(out + "/timex-extents.res.tab");
      command.add(in + "/timex-attributes.tab");
      command.add(out + "/timex-attrs.res.tab");
      ProcessBuilder pb = new ProcessBuilder(command);
      FileOutputStream evalFileOutput = new FileOutputStream(out + "/scores.txt");
      Writer output = new OutputStreamWriter(
              new TeeStream(System.out, evalFileOutput));
      SystemUtils.run(pb, output, null);
      evalFileOutput.close();
    }
  }

  public static void processTempEval3(AnnotationPipeline pipeline, String in, String out, String evalCmd) throws Exception
  {
    // Process files
    File inFile = new File(in);
    if (inFile.isDirectory()) {
      // input is a directory - process files in directory
      Pattern teinputPattern = Pattern.compile("\\.(TE3input|tml)$");
      Iterable<File> files = IOUtils.iterFilesRecursive(inFile, teinputPattern);
      File outDir = new File(out);
      outDir.mkdirs();
      for (File file: files) {
        String inputFilename = file.getAbsolutePath();
        String outputFilename = inputFilename.replace(in, out).replace(".TE3input", "");
        if (!outputFilename.equalsIgnoreCase(inputFilename)) {
          //System.out.println(inputFilename + " => " + outputFilename);
          processTempEval3File(pipeline, inputFilename, outputFilename);
        } else {
          log.info("ABORTING: Input file and output is the same - " + inputFilename);
          System.exit(-1);
        }
      }
    } else {
      // input is a file - process file
      processTempEval3File(pipeline, in, out);
    }
    // Evaluate
    if (evalCmd != null) {
      // TODO: apply eval command
    }
  }

  public static void processTempEval3File(AnnotationPipeline pipeline, String in, String out) throws Exception {
    // Process one tempeval file
    Document doc = edu.stanford.nlp.util.XMLUtils.readDocumentFromFile(in);
    Node timemlNode = XMLUtils.getNode(doc, "TimeML");
    Node docIdNode = XMLUtils.getNode(timemlNode, "DOCID");
    Node dctNode = XMLUtils.getNode(timemlNode, "DCT");
    Node dctTimexNode = XMLUtils.getNode(dctNode, "TIMEX3");
    Node titleNode = XMLUtils.getNode(timemlNode, "TITLE");
    Node extraInfoNode = XMLUtils.getNode(timemlNode, "EXTRA_INFO");
    Node textNode = XMLUtils.getNode(timemlNode, "TEXT");
    String date = XMLUtils.getAttributeValue(dctTimexNode, "value");
    String text = textNode.getTextContent();
    Annotation annotation = textToAnnotation(pipeline, text, date);
    Element annotatedTextElem = annotationToTmlTextElement(annotation);

    Document annotatedDoc = XMLUtils.createDocument();
    Node newTimemlNode = annotatedDoc.importNode(timemlNode, false);
    if(docIdNode != null){
        newTimemlNode.appendChild(annotatedDoc.importNode(docIdNode, true));
    }
    newTimemlNode.appendChild(annotatedDoc.importNode(dctNode, true));
    if (titleNode != null) {
      newTimemlNode.appendChild(annotatedDoc.importNode(titleNode, true));
    }
    if (extraInfoNode != null) {
      newTimemlNode.appendChild(annotatedDoc.importNode(extraInfoNode, true));
    }
    newTimemlNode.appendChild(annotatedDoc.adoptNode(annotatedTextElem));
    annotatedDoc.appendChild(newTimemlNode);

    PrintWriter pw = (out != null)? IOUtils.getPrintWriter(out):new PrintWriter(System.out);
    String string = XMLUtils.documentToString(annotatedDoc);
    pw.println(string);
    pw.flush();
    if (out != null) pw.close();
  }

  private static String requiredDocDateFormat;
  private static boolean useGUTime = false;

  public static AnnotationPipeline getPipeline(Properties props, boolean tokenize) throws Exception {
//    useGUTime = Boolean.parseBoolean(props.getProperty("gutime", "false"));
    AnnotationPipeline pipeline = new AnnotationPipeline();
    if (tokenize) {
      pipeline.addAnnotator(new TokenizerAnnotator(false, "en"));
      pipeline.addAnnotator(new WordsToSentencesAnnotator(false));
    }
    pipeline.addAnnotator(new POSTaggerAnnotator(false));
//    pipeline.addAnnotator(new NumberAnnotator(false));
//    pipeline.addAnnotator(new QuantifiableEntityNormalizingAnnotator(false, false));
    String timeAnnotator = props.getProperty("timeAnnotator", "sutime");
    switch (timeAnnotator) {
      case "gutime":
        useGUTime = true;
        pipeline.addAnnotator(new GUTimeAnnotator("gutime", props));
        break;
      case "heideltime":
        requiredDocDateFormat = "yyyy-MM-dd";
        pipeline.addAnnotator(new HeidelTimeAnnotator("heideltime", props));
        break;
      case "sutime":
        pipeline.addAnnotator(new TimeAnnotator("sutime", props));
        break;
      default:
        throw new IllegalArgumentException("Unknown timeAnnotator: " + timeAnnotator);
    }
    return pipeline;
  }

  enum InputType { TEXTFILE, TEXT, TIMEBANK_CSV, TEMPEVAL2, TEMPEVAL3 }

  private static void configLogger(String out) throws IOException {
    File outDir = new File(out);
    if (!outDir.exists()) {
      outDir.mkdirs();
    }
    StringBuilder sb = new StringBuilder();
    sb.append("handlers=java.util.logging.ConsoleHandler, java.util.logging.FileHandler\n");
    sb.append(".level=SEVERE\n");
    sb.append("edu.stanford.nlp.level=INFO\n");
    sb.append("java.util.logging.ConsoleHandler.level=SEVERE\n");
    sb.append("java.util.logging.FileHandler.formatter=java.util.logging.SimpleFormatter\n");
    sb.append("java.util.logging.FileHandler.level=INFO\n");
    sb.append("java.util.logging.FileHandler.pattern=" + out + "/err.log" + "\n");
    LogManager.getLogManager().readConfiguration(new ReaderInputStream(new StringReader(sb.toString())));
  }

  private static List<Node> createTimexNodes(String str, Integer charBeginOffset, List<CoreMap> timexAnns) {
    List<ValuedInterval<CoreMap,Integer>> timexList = new ArrayList<>(timexAnns.size());
    for (CoreMap timexAnn:timexAnns) {
      timexList.add(new ValuedInterval<>(timexAnn,
              MatchedExpression.COREMAP_TO_CHAR_OFFSETS_INTERVAL_FUNC.apply(timexAnn)));
    }
    Collections.sort(timexList, HasInterval.CONTAINS_FIRST_ENDPOINTS_COMPARATOR );
    return createTimexNodesPresorted(str, charBeginOffset, timexList);
  }

  private static List<Node> createTimexNodesPresorted(String str, Integer charBeginOffset, List<ValuedInterval<CoreMap,Integer>> timexList) {
    if (charBeginOffset == null) charBeginOffset = 0;
    List<Node> nodes = new ArrayList<>();
    int previousEnd = 0;
    List<Element> timexElems = new ArrayList<>();
    List<ValuedInterval<CoreMap,Integer>> processed = new ArrayList<>();
    CollectionValuedMap<Integer, ValuedInterval<CoreMap,Integer>> unprocessed =
            new CollectionValuedMap<>(CollectionFactory.<ValuedInterval<CoreMap, Integer>>arrayListFactory());
    for (ValuedInterval<CoreMap,Integer> v:timexList) {
      CoreMap timexAnn = v.getValue();
      int begin = timexAnn.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class) - charBeginOffset;
      int end = timexAnn.get(CoreAnnotations.CharacterOffsetEndAnnotation.class) - charBeginOffset;
      if (begin >= previousEnd) {
        // Add text
        nodes.add(XMLUtils.createTextNode(str.substring(previousEnd, begin)));
        // Add timex
        Timex timex = timexAnn.get(TimeAnnotations.TimexAnnotation.class);
        Element timexElem = timex.toXmlElement();
        nodes.add(timexElem);
        previousEnd = end;

        // For handling nested timexes
        processed.add(v);
        timexElems.add(timexElem);
      } else {
        unprocessed.add(processed.size()-1, v);
      }
    }
    if (previousEnd < str.length()) {
      nodes.add(XMLUtils.createTextNode(str.substring(previousEnd)));
    }
    for (Integer i:unprocessed.keySet()) {
      ValuedInterval<CoreMap, Integer> v = processed.get(i);
      String elemStr = v.getValue().get(CoreAnnotations.TextAnnotation.class);
      int charStart = v.getValue().get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      List<Node> innerElems = createTimexNodesPresorted(elemStr, charStart, (List<ValuedInterval<CoreMap, Integer>>) unprocessed.get(i));
      Element timexElem = timexElems.get(i);
      XMLUtils.removeChildren(timexElem);
      for (Node n:innerElems) {
        timexElem.appendChild(n);
      }
    }
    return nodes;
  }

  public static void processTextFile(AnnotationPipeline pipeline, String in, String out, String date) throws IOException {
    String text = IOUtils.slurpFile(in);
    PrintWriter pw = (out != null)? IOUtils.getPrintWriter(out):new PrintWriter(System.out);
    String string = textToAnnotatedXml(pipeline, text, date);
    pw.println(string);
    pw.flush();
    if (out != null) pw.close();
  }

  public static void processText(AnnotationPipeline pipeline, String text, String out, String date) throws IOException {
    PrintWriter pw = (out != null)? IOUtils.getPrintWriter(out):new PrintWriter(System.out);
    String string = textToAnnotatedXml(pipeline, text, date);
    pw.println(string);
    pw.flush();
    if (out != null) pw.close();
  }

  public static String textToAnnotatedXml(AnnotationPipeline pipeline, String text, String date) {
    Annotation annotation = textToAnnotation(pipeline, text, date);
    Document xmlDoc = annotationToXmlDocument(annotation);
    return XMLUtils.documentToString(xmlDoc);
  }

  public static Element annotationToTmlTextElement(Annotation annotation) {
    List<CoreMap> timexAnnsAll = annotation.get(TimeAnnotations.TimexAnnotations.class);
    Element textElem = XMLUtils.createElement("TEXT");
    List<Node> timexNodes = createTimexNodes(
            annotation.get(CoreAnnotations.TextAnnotation.class),
            annotation.get(CoreAnnotations.CharacterOffsetBeginAnnotation.class),
            timexAnnsAll);
    for (Node node:timexNodes) {
      textElem.appendChild(node);
    }
    return textElem;
  }

  public static Document annotationToXmlDocument(Annotation annotation) {
    Element dateElem = XMLUtils.createElement("DATE");
    dateElem.setTextContent(annotation.get(CoreAnnotations.DocDateAnnotation.class));
    Element textElem = annotationToTmlTextElement(annotation);

    Element docElem = XMLUtils.createElement("DOC");
    docElem.appendChild(dateElem);
    docElem.appendChild(textElem);

    // Create document and import elements into this document....
    Document doc = XMLUtils.createDocument();
    doc.appendChild(doc.importNode(docElem, true));
    return doc;
  }

  public static Annotation textToAnnotation(AnnotationPipeline pipeline, String text, String date) {
    Annotation annotation = new Annotation(text);
    annotation.set(CoreAnnotations.DocDateAnnotation.class, date);
    pipeline.annotate(annotation);
    return annotation;
  }

  public static void main(String[] args) throws Exception {
    // Process arguments
    Properties props = StringUtils.argsToProperties(args);

    String in = props.getProperty("i");
    String date = props.getProperty("date");
    String dct = props.getProperty("tempeval2.dct");
    String out = props.getProperty("o");
    String inputTypeStr = props.getProperty("in.type", InputType.TEXT.name());
    String eval = props.getProperty("eval");
    PYTHON = props.getProperty("python", PYTHON);
    InputType inputType = InputType.valueOf(inputTypeStr);
    AnnotationPipeline pipeline;
    switch (inputType) {
      case TEXT:
        pipeline = getPipeline(props, true);
        processText(pipeline, in, out, date);
        break;
      case TEXTFILE:
        pipeline = getPipeline(props, true);
        processTextFile(pipeline, in, out, date);
        break;
      case TIMEBANK_CSV:
        configLogger(out);
        pipeline = getPipeline(props, true);
        processTimebankCsv(pipeline, in, out, eval);
        break;
      case TEMPEVAL2:
        configLogger(out);
        pipeline = getPipeline(props, false);
        processTempEval2(pipeline, in, out, eval, dct);
        break;
      case TEMPEVAL3:
        pipeline = getPipeline(props, true);
        processTempEval3(pipeline, in, out, eval);
        break;
    }
  }

}
