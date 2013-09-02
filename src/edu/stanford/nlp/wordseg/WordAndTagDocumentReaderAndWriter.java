package edu.stanford.nlp.wordseg;

import static edu.stanford.nlp.trees.international.pennchinese.ChineseUtils.WHITE;
import static edu.stanford.nlp.trees.international.pennchinese.ChineseUtils.WHITEPLUS;

import java.io.File;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.CharAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.D2_LBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.D2_LEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.D2_LMiddleAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LBeginAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LEndAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LMiddleAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.OriginalCharAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PositionAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ShapeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SpaceBeforeAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.UBlockAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.UTypeAnnotation;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.LineIterator;
import edu.stanford.nlp.objectbank.ObjectBank;
import edu.stanford.nlp.process.ChineseDocumentToSentenceProcessor;
import edu.stanford.nlp.util.Characters;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;
import edu.stanford.nlp.util.StringUtils;

/**
 * DocumentReader modified from Sighan2005DocumentReaderAndWriter to use "NR".
 * Now, instead of 2 classes (0 and 1), there are 4 classes (NR-0, NR-1, WORD-0, WORD-1)
 */
public class WordAndTagDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final boolean DEBUG = false;
  private static final boolean DEBUG_MORE = false;

  private ChineseDocumentToSentenceProcessor cdtos;
  private ChineseDictionary cdict, cdict2;
  private SeqClassifierFlags flags;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;

  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  public void init(SeqClassifierFlags flags) {
    this.flags = flags;
    factory = LineIterator.getFactory(new CTBDocumentParser());
    if (DEBUG) { EncodingPrintWriter.err.println("WordAndTagDocRandW: using normalization file " + flags.normalizationTable, "UTF-8"); }
    cdtos = new ChineseDocumentToSentenceProcessor(flags.normalizationTable);

    if (flags.dictionary != null) {
      String[] dicts = flags.dictionary.split(",");
      cdict = new ChineseDictionary(dicts, cdtos, flags.expandMidDot);
    }
    if (flags.dictionary2 != null) {
      String[] dicts2 = flags.dictionary2.split(",");
      cdict2 = new ChineseDictionary(dicts2, cdtos, flags.expandMidDot);
    }
  }


  public class CTBDocumentParser implements Function<String,List<CoreLabel>> {
    private String defaultMap = "char=0,answer=1";
    public String[] map = StringUtils.mapStringToArray(defaultMap);


    public List<CoreLabel> apply(String line) {
      if (line == null) {
        return null;
      }

      //Matcher tagMatcher = tagPattern.matcher(line);
      //line = tagMatcher.replaceAll("");
      line = line.trim();
      String[] toks = line.split("[\\s\\p{Zs}]+");
      String patternStr = "(.*)_([A-Z\\-]+)$";
      Pattern pattern = Pattern.compile(patternStr);
      String[] words = new String[toks.length];
      String[] postags = new String[toks.length];
      for (int i = 0; i < toks.length; i++) {
        String tok = toks[i];
        // Compile and use regular expression
        Matcher matcher = pattern.matcher(tok);
        boolean matchFound = matcher.find();

        if (matchFound) {
          String word = matcher.group(1);
          String tag = matcher.group(2);
          words[i] = word;
          postags[i] = tag;
        } else {
          //throw new RuntimeException("The token "+tok+" in this line '"+line+"' is not in the format of WORD_TAG");
          // at test time, the input won't really have a _POS suffix!
          words[i] = tok;
          postags[i] = "WORD";
        }
      }
      line = StringUtils.join(words," ");


      String[] postagsOnChars = new String[line.length()];
      int ptrTag = 0;
      for (int i = 0; i < line.length(); i++) {
        char ch = line.charAt(i);
        if (ch==' ') { ptrTag++; }
        else { postagsOnChars[i] = postags[ptrTag]; }
      }
      if (ptrTag != toks.length-1) { throw new RuntimeException("When tagging chars with POS, the length is not the same?"); }

      List<CoreLabel> lwi = new ArrayList<CoreLabel>();
      String origLine = line;
      if (DEBUG) EncodingPrintWriter.err.println("ORIG: " + line, "UTF-8");
      line = cdtos.normalization(origLine);
      if (DEBUG) EncodingPrintWriter.err.println("NORM: " + line, "UTF-8");
      int origIndex = 0;
      int position = 0;

      StringBuilder nonspaceLineSB = new StringBuilder();

      for (int i = 0, len = line.length(); i < len; i++) {
        char ch = line.charAt(i);
        CoreLabel wi = new CoreLabel();
        String wordString = Character.toString(ch);
        if ( ! Character.isWhitespace(ch) && ! Character.isISOControl(ch)) {
          wi.set(CharAnnotation.class, intern(wordString));
          nonspaceLineSB.append(wordString);

          while (Character.isWhitespace(origLine.charAt(origIndex)) || Character.isISOControl(origLine.charAt(origIndex))) {
            origIndex++;
          }

          wordString = Character.toString(origLine.charAt(origIndex));
          wi.set(OriginalCharAnnotation.class, intern(wordString));

          // put in a word shape
          if (flags.useShapeStrings) {
            wi.set(ShapeAnnotation.class, shapeOf(wordString));
          }
          if (flags.useUnicodeType || flags.useUnicodeType4gram || flags.useUnicodeType5gram) {
            wi.set(UTypeAnnotation.class, Character.getType(ch));
          }
          if (flags.useUnicodeBlock) {
            wi.set(UBlockAnnotation.class, Characters.unicodeBlockStringOf(ch));
          }

          origIndex++;

          String tag = postagsOnChars[i];
          String category;
          if (tag.equals("NR")) {
            category = "NR";
          } else {
            category = "WORD";
          }
          StringBuilder one = new StringBuilder();
          one.append(category).append("-1");
          StringBuilder zero = new StringBuilder();
          zero.append(category).append("-0");

          if (i == 0) { // first character of a sentence (a line)
            wi.set(AnswerAnnotation.class, one.toString());
            wi.set(SpaceBeforeAnnotation.class, "1");
          } else if (Character.isWhitespace(line.charAt(i - 1)) || Character.isISOControl(line.charAt(i-1))) {
            wi.set(AnswerAnnotation.class, one.toString());
            wi.set(SpaceBeforeAnnotation.class, "1");
          } else {
            wi.set(AnswerAnnotation.class, zero.toString());
            wi.set(SpaceBeforeAnnotation.class, "0");
          }
          wi.set(PositionAnnotation.class, intern(String.valueOf((position))));
          position++;
          if (DEBUG_MORE) EncodingPrintWriter.err.println(wi.toString(), "UTF-8");
          lwi.add(wi);
        }
      }
      if (flags.dictionary != null) {
        String nonspaceLine = nonspaceLineSB.toString();
        addDictionaryFeatures(cdict, LBeginAnnotation.class, LMiddleAnnotation.class, LEndAnnotation.class, nonspaceLine, lwi);
      }

      if (flags.dictionary2 != null) {
        String nonspaceLine = nonspaceLineSB.toString();
        addDictionaryFeatures(cdict2, D2_LBeginAnnotation.class, D2_LMiddleAnnotation.class, D2_LEndAnnotation.class, nonspaceLine, lwi);
      }
      return lwi;
    }
  }

  /** Calculates a character shape for Chinese. */
  private String shapeOf(String input) {
    return ChineseUtils.shapeOf(input, flags.augmentedDateChars,
                                flags.useMidDotShape);
  }


  private static void addDictionaryFeatures(ChineseDictionary dict, Class<? extends CoreAnnotation<String>> lbeginFieldName, Class<? extends CoreAnnotation<String>> lmiddleFieldName, Class<? extends CoreAnnotation<String>> lendFieldName, String nonspaceLine, List<CoreLabel> lwi) {
    int lwiSize = lwi.size();
    if (lwiSize != nonspaceLine.length()) { throw new RuntimeException(); }
    int[] lbegin = new int[lwiSize];
    int[] lmiddle = new int[lwiSize];
    int[] lend = new int[lwiSize];
    for (int i = 0; i < lwiSize; i++) {
      lbegin[i] = lmiddle[i] = lend[i] = 0;
    }
    for (int i = 0; i < lwiSize; i++) {
      for (int leng = ChineseDictionary.MAX_LEXICON_LENGTH; leng >= 1; leng--) {
        if (i+leng-1 < lwiSize) {
          if (dict.contains(nonspaceLine.substring(i, i+leng))) {
            // lbegin
            if (leng > lbegin[i]) {
              lbegin[i] = leng;
            }
            // lmid
            int last = i+leng-1;
            if (leng==ChineseDictionary.MAX_LEXICON_LENGTH) { last+=1; }
            for (int mid = i+1; mid < last; mid++) {
              if (leng > lmiddle[mid]) {
                lmiddle[mid] = leng;
              }
            }
            // lend
            if (leng<ChineseDictionary.MAX_LEXICON_LENGTH) {
              if (leng > lend[i+leng-1]) {
                lend[i+leng-1] = leng;
              }
            }
          }
        }
      }
    }
    for (int i = 0; i < lwiSize; i++) {
      StringBuilder sb = new StringBuilder();
      sb.append(lbegin[i]);
      if (lbegin[i]==ChineseDictionary.MAX_LEXICON_LENGTH) {
        sb.append("+");
      }
      lwi.get(i).set(lbeginFieldName, sb.toString());

      sb = new StringBuilder();
      sb.append(lmiddle[i]);
      if (lmiddle[i]==ChineseDictionary.MAX_LEXICON_LENGTH) {
        sb.append("+");
      }
      lwi.get(i).set(lmiddleFieldName, sb.toString());

      sb = new StringBuilder();
      sb.append(lend[i]);
      if (lend[i]==ChineseDictionary.MAX_LEXICON_LENGTH) {
        sb.append("+");
      }
      lwi.get(i).set(lendFieldName, sb.toString());

      //System.err.println(lwi.get(i));
    }
  }

  public void printAnswers(List<CoreLabel> doc, PrintWriter pw) {
    if (flags.printNR) {
      //System.err.println("printing out segmented text with NR tag");
      pw.print(printAnswersWithNR(doc));
    } else {
      //System.err.println("printing out segmented text without NR tag (only segmented text)");
      pw.print(printAnswersWithoutNR(doc));
    }
    pw.println();
  }


  public String printAnswersWithNR(List<CoreLabel> doc) {
    String segtext = printAnswersWithoutNR(doc);
    List<String> isNR = new ArrayList<String>();
    for (CoreLabel c : doc) {
      String label = c.get(AnswerAnnotation.class);
      if (label.startsWith("NR")) {
        isNR.add("NR");
      } else {
        isNR.add("O");
      }
    }

    char[] chars = segtext.toCharArray();
    int ptr = 0;
    String seg = "1";
    List<String> annotatedChars = new ArrayList<String>();
    List<String> annotationOnly = new ArrayList<String>();


    char prevC = ' ';
    String prevLabel = "";

    for (char c : chars) {
      if (prevC==' ') {
        seg = "1";
      } else {
        seg = "0";
      }
      if (c==' ') {
        prevC = c;
        continue;
      }

      StringBuilder sb = new StringBuilder();
      //sb.append(c).append("/").append(ptr).append("-").append(isNR.get(ptr)).append("-").append(seg);
      sb.append(c).append("/").append(isNR.get(ptr)).append("-").append(seg);

      StringBuilder ann = new StringBuilder();
      ann.append(isNR.get(ptr)).append("-").append(seg);
      String curLabel = ann.toString();
      if ((curLabel.equals("NR-0") && prevLabel.startsWith("O")) ||
          (curLabel.equals("O-0") && prevLabel.startsWith("NR"))) {
        //throw new RuntimeException("wrong label seq : "+prevLabel+" "+curLabel);
        //System.err.println("DEBUG wrong label seq : "+prevLabel+" "+curLabel);
        //System.err.println("DEBUG wrong char seq : "+prevC+" "+c);
        //System.err.println("DEBUG Sentence = "+segtext);
      }

      annotatedChars.add(sb.toString());
      ptr++;
      prevLabel = curLabel;
      prevC = c;
    }
    if (ptr != doc.size()) {
      System.err.println("segtext=");
      System.err.println(segtext);
      System.err.println("ptr="+ptr);
      System.err.println("doc.size="+doc.size());
      System.err.println("doc=");
      System.err.println(doc);

      throw new RuntimeException("??");
    }
    return StringUtils.join(annotatedChars, " ");
  }

///////////////////////////////////////////////////////////////////////////////////
  public String printAnswersWithoutNR(List<CoreLabel> doc) {
    return ChineseStringUtils.combineSegmentedSentence(doc, flags);
  }


  private static String intern(String s) {
    return s.trim().intern();
  }

  private static final long serialVersionUID = 3260295150250263237L;

}
