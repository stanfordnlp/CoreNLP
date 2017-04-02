package edu.stanford.nlp.wordseg;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import edu.stanford.nlp.fsm.DFSA;
import edu.stanford.nlp.fsm.DFSAState;
import edu.stanford.nlp.fsm.DFSATransition;
import edu.stanford.nlp.io.EncodingPrintWriter;
import edu.stanford.nlp.ling.CoreAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.objectbank.LineIterator;
import edu.stanford.nlp.process.ChineseDocumentToSentenceProcessor;
import edu.stanford.nlp.sequences.DocumentReaderAndWriter;
import edu.stanford.nlp.sequences.LatticeWriter;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.trees.international.pennchinese.ChineseUtils;
import edu.stanford.nlp.util.Characters;

import java.util.function.Function;


import edu.stanford.nlp.util.logging.Redwood;

import edu.stanford.nlp.util.Generics;
import edu.stanford.nlp.util.MutableInteger;
import edu.stanford.nlp.util.StringUtils;

/**
 * DocumentReader for Chinese segmentation task. (Sighan bakeoff 2005)
 * Reads in characters and labels them as 1 or 0 (word START or NONSTART).
 *
 * Note: maybe this can do less interning, since some is done in
 * ObjectBankWrapper, but this also calls trim() as it works....
 *
 * @author Pi-Chuan Chang
 * @author Michel Galley (Viterbi search graph printing)
 */
public class Sighan2005DocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel>, LatticeWriter<CoreLabel, String, Integer> /* Serializable */ {

  private static final long serialVersionUID = 3260295150250263237L;

  private static final Redwood.RedwoodChannels logger = Redwood.channels(Sighan2005DocumentReaderAndWriter.class);

  private static final boolean DEBUG = false;
  private static final boolean DEBUG_MORE = false;

  // year, month, day chars.  Sometime try adding \u53f7 and see if it helps...
  private static final Pattern dateChars = Pattern.compile("[\u5E74\u6708\u65E5]");
  // year, month, day chars.  Adding \u53F7 and seeing if it helps...
  private static final Pattern dateCharsPlus = Pattern.compile("[\u5E74\u6708\u65E5\u53f7]");
  // number chars (Chinese and Western).
  // You get U+25CB circle masquerading as zero in mt data - or even in Sighan 2003 ctb
  // add U+25EF for good measure (larger geometric circle)
  private static final Pattern numberChars = Pattern.compile("[0-9\uff10-\uff19" +
        "\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4E5D\u5341" +
        "\u96F6\u3007\u767E\u5343\u4E07\u4ebf\u5169\u25cb\u25ef\u3021-\u3029\u3038-\u303A]");
  // A-Za-z, narrow and full width
  private static final Pattern letterChars = Pattern.compile("[A-Za-z\uFF21-\uFF3A\uFF41-\uFF5A]");
  private static final Pattern periodChars = Pattern.compile("[\ufe52\u2027\uff0e.\u70B9]");

  // two punctuation classes for Low and Ng style features.
  private final Pattern separatingPuncChars = Pattern.compile("[]!\"(),;:<=>?\\[\\\\`{|}~^\u3001-\u3003\u3008-\u3011\u3014-\u301F\u3030" +
        "\uff3d\uff01\uff02\uff08\uff09\uff0c\uff1b\uff1a\uff1c\uff1d\uff1e\uff1f" +
        "\uff3b\uff3c\uff40\uff5b\uff5c\uff5d\uff5e\uff3e]");
  private final Pattern ambiguousPuncChars = Pattern.compile("[-#$%&'*+/@_\uff0d\uff03\uff04\uff05\uff06\uff07\uff0a\uff0b\uff0f\uff20\uff3f]");
  private final Pattern midDotPattern = Pattern.compile(ChineseUtils.MID_DOT_REGEX_STR);

  private ChineseDocumentToSentenceProcessor cdtos;
  private ChineseDictionary cdict, cdict2;
  private SeqClassifierFlags flags;
  private IteratorFromReaderFactory<List<CoreLabel>> factory;

  @Override
  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  @Override
  public void init(SeqClassifierFlags flags) {
    this.flags = flags;
    factory = LineIterator.getFactory(new CTBDocumentParser());
    if (DEBUG) EncodingPrintWriter.err.println("Sighan2005DocRandW: using normalization file " + flags.normalizationTable, "UTF-8");
    // pichuan : flags.normalizationTable is null --> i believe this is replaced by some java class??
    // (Thu Apr 24 11:10:42 2008)
    cdtos = new ChineseDocumentToSentenceProcessor(flags.normalizationTable);

    if (flags.dictionary != null) {
      String[] dicts = flags.dictionary.split(",");
      cdict = new ChineseDictionary(dicts, cdtos, flags.expandMidDot);
    }
    if (flags.serializedDictionary != null) {
      String dict = flags.serializedDictionary;
      cdict = new ChineseDictionary(dict, cdtos, flags.expandMidDot);
    }

    if (flags.dictionary2 != null) {
      String[] dicts2 = flags.dictionary2.split(",");
      cdict2 = new ChineseDictionary(dicts2, cdtos, flags.expandMidDot);
    }
  }


  class CTBDocumentParser implements Function<String,List<CoreLabel>>, Serializable {
    private static final long serialVersionUID = 3260297180259462337L;

    private String defaultMap = "char=0,answer=1";
    public String[] map = StringUtils.mapStringToArray(defaultMap);


    @Override
    public List<CoreLabel> apply(String line) {
      if (line == null) {
        return null;
      }

      // logger.info("input: " + line);

      //Matcher tagMatcher = tagPattern.matcher(line);
      //line = tagMatcher.replaceAll("");
      line = line.trim();

      List<CoreLabel> lwi = new ArrayList<>();
      String origLine = line;
      if (DEBUG) EncodingPrintWriter.err.println("ORIG: " + line, "UTF-8");
      line = cdtos.normalization(origLine);
      if (DEBUG) EncodingPrintWriter.err.println("NORM: " + line, "UTF-8");
      int origIndex = 0;
      int position = 0;

      StringBuilder nonspaceLineSB = new StringBuilder();

      for (int index = 0, len = line.length(); index < len; index++) {
        char ch = line.charAt(index);
        CoreLabel wi = new CoreLabel();
        if ( ! Character.isWhitespace(ch) && ! Character.isISOControl(ch)) {
          String wordString = Character.toString(ch);
          wi.set(CoreAnnotations.CharAnnotation.class, intern(wordString));
          nonspaceLineSB.append(wordString);

          // non-breaking space is skipped as well
          while (Character.isWhitespace(origLine.charAt(origIndex)) || Character.isISOControl(origLine.charAt(origIndex)) || (origLine.charAt(origIndex) == '\u00A0')) {
            origIndex++;
          }

          wordString = Character.toString(origLine.charAt(origIndex));
          wi.set(CoreAnnotations.OriginalCharAnnotation.class, intern(wordString));

          // put in a word shape
          if (flags.useShapeStrings) {
            wi.set(CoreAnnotations.ShapeAnnotation.class, shapeOf(wordString));
          }
          if (flags.useUnicodeType || flags.useUnicodeType4gram || flags.useUnicodeType5gram) {
            wi.set(CoreAnnotations.UTypeAnnotation.class, Character.getType(ch));
          }
          if (flags.useUnicodeBlock) {
            wi.set(CoreAnnotations.UBlockAnnotation.class, Characters.unicodeBlockStringOf(ch));
          }

          origIndex++;

          if (index == 0) { // first character of a sentence (a line)
            wi.set(CoreAnnotations.AnswerAnnotation.class, "1");
            wi.set(CoreAnnotations.SpaceBeforeAnnotation.class, "1");
            wi.set(CoreAnnotations.GoldAnswerAnnotation.class, "1");
          } else if (Character.isWhitespace(line.charAt(index - 1)) || Character.isISOControl(line.charAt(index - 1))) {
            wi.set(CoreAnnotations.AnswerAnnotation.class, "1");
            wi.set(CoreAnnotations.SpaceBeforeAnnotation.class, "1");
            wi.set(CoreAnnotations.GoldAnswerAnnotation.class, "1");
          } else {
            wi.set(CoreAnnotations.AnswerAnnotation.class, "0");
            wi.set(CoreAnnotations.SpaceBeforeAnnotation.class, "0");
            wi.set(CoreAnnotations.GoldAnswerAnnotation.class, "0");
          }
          wi.set(CoreAnnotations.PositionAnnotation.class, intern(String.valueOf((position))));
          position++;
          if (DEBUG_MORE) EncodingPrintWriter.err.println(wi.toString(), "UTF-8");
          lwi.add(wi);
        }
      }
      if (flags.dictionary != null || flags.serializedDictionary != null) {
        String nonspaceLine = nonspaceLineSB.toString();
        addDictionaryFeatures(cdict, CoreAnnotations.LBeginAnnotation.class, CoreAnnotations.LMiddleAnnotation.class, CoreAnnotations.LEndAnnotation.class, nonspaceLine, lwi);
      }

      if (flags.dictionary2 != null) {
        String nonspaceLine = nonspaceLineSB.toString();
        addDictionaryFeatures(cdict2, CoreAnnotations.D2_LBeginAnnotation.class, CoreAnnotations.D2_LMiddleAnnotation.class, CoreAnnotations.D2_LEndAnnotation.class, nonspaceLine, lwi);
      }
      // logger.info("output: " + lwi.size());
      return lwi;
    }
  }

  /** Calculates a character shape for Chinese. */
  private String shapeOf(String input) {
    String shape;
    if (flags.augmentedDateChars && Sighan2005DocumentReaderAndWriter.dateCharsPlus.matcher(input).matches()) {
      shape = "D";
    } else if (Sighan2005DocumentReaderAndWriter.dateChars.matcher(input).matches()) {
      shape = "D";
    } else if (Sighan2005DocumentReaderAndWriter.numberChars.matcher(input).matches()) {
      shape = "N";
    } else if (Sighan2005DocumentReaderAndWriter.letterChars.matcher(input).matches()) {
      shape = "L";
    } else if (Sighan2005DocumentReaderAndWriter.periodChars.matcher(input).matches()) {
      shape = "P";
    } else if (separatingPuncChars.matcher(input).matches()) {
      shape = "S";
    } else if (ambiguousPuncChars.matcher(input).matches()) {
      shape = "A";
    } else if (flags.useMidDotShape && midDotPattern.matcher(input).matches()) {
      shape = "M";
    } else {
      shape = "C";
    }
    return shape;
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

      //logger.info(lwi.get(i));
    }
  }

  @Override
  public void printAnswers(List<CoreLabel> doc, PrintWriter pw) {
    String ansStr = ChineseStringUtils.combineSegmentedSentence(doc, flags);
    pw.print(ansStr);
    pw.println();
  }


  private static String intern(String s) {
    return s.trim().intern();
  }

  @Override
  public void printLattice(DFSA<String, Integer> tagLattice, List<CoreLabel> doc, PrintWriter out) {
    CoreLabel[] docArray = doc.toArray(new CoreLabel[doc.size()]);
    // Create answer lattice:
    MutableInteger nodeId = new MutableInteger(0);
    DFSA<String, Integer> answerLattice = new DFSA<>(null);
    DFSAState<String, Integer> aInitState = new DFSAState<>(nodeId.intValue(), answerLattice);
    answerLattice.setInitialState(aInitState);
    Map<DFSAState<String, Integer>,DFSAState<String, Integer>> stateLinks = Generics.newHashMap();
    // Convert binary lattice into word lattice:
    tagLatticeToAnswerLattice
      (tagLattice.initialState(), aInitState, new StringBuilder(""), nodeId, 0, 0.0, stateLinks, answerLattice, docArray);
    try {
      answerLattice.printAttFsmFormat(out);
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Recursively builds an answer lattice (Chinese words) from a Viterbi search graph
   * of binary predictions. This function does a limited amount of post-processing:
   * preserve white spaces of the input, and not segment between two latin characters or
   * between two digits. Consequently, the probabilities of all paths in answerLattice
   * may not sum to 1 (they do sum to 1 if no post processing applies).
   *
   * @param tSource Current node in Viterbi search graph.
   * @param aSource Current node in answer lattice.
   * @param answer Partial word starting at aSource.
   * @param nodeId Currently unused node identifier for answer graph.
   * @param pos Current position in docArray.
   * @param cost Current cost of answer.
   * @param stateLinks Maps nodes of the search graph to nodes in answer lattice
   * (when paths of the search graph are recombined, paths of the answer lattice should be
   *  recombined as well, if at word boundary).
   */
  private void tagLatticeToAnswerLattice
         (DFSAState<String, Integer> tSource, DFSAState<String, Integer> aSource, StringBuilder answer,
          MutableInteger nodeId, int pos, double cost,
          Map<DFSAState<String, Integer>,DFSAState<String, Integer>> stateLinks,
          DFSA<String, Integer> answerLattice, CoreLabel[] docArray) {
    // Add "1" prediction after the end of the sentence, if applicable:
    if(tSource.isAccepting() && tSource.continuingInputs().isEmpty()) {
      tSource.addTransition
        (new DFSATransition<>("", tSource, new DFSAState<>(-1, null), "1", "", 0));
    }
    // Get current label, character, and prediction:
    CoreLabel curLabel = (pos < docArray.length) ? docArray[pos] : null;
    String curChr = null, origSpace = null;
    if(curLabel != null) {
      curChr = curLabel.get(CoreAnnotations.OriginalCharAnnotation.class);
      assert(curChr.length() == 1);
      origSpace = curLabel.get(CoreAnnotations.SpaceBeforeAnnotation.class);
    }
    // Get set of successors in search graph:
    Set<String> inputs = tSource.continuingInputs();
    // Only keep most probable transition out of initial state:
    String answerConstraint = null;
    if(pos == 0) {
      double minCost = Double.POSITIVE_INFINITY;
      // DFSATransition<String, Integer> bestTransition = null;
      for (String predictSpace : inputs) {
        DFSATransition<String, Integer> transition = tSource.transition(predictSpace);
        double transitionCost = transition.score();
        if (transitionCost < minCost) {
          if (predictSpace != null) {
            logger.info(String.format("mincost (%s): %e -> %e%n", predictSpace, minCost, transitionCost));
            minCost = transitionCost;
            answerConstraint = predictSpace;
          }
        }
      }
    }
    // Follow along each transition:
    for (String predictSpace : inputs) {
      DFSATransition<String, Integer> transition = tSource.transition(predictSpace);
      DFSAState<String, Integer> tDest = transition.target();
      DFSAState<String, Integer> newASource = aSource;
      //logger.info(String.format("tsource=%s tdest=%s asource=%s pos=%d predictSpace=%s%n", tSource, tDest, newASource, pos, predictSpace));
      StringBuilder newAnswer = new StringBuilder(answer.toString());
      int answerLen = newAnswer.length();
      String prevChr = (answerLen > 0) ? newAnswer.substring(answerLen-1) : null;
      double newCost = cost;
      // Ignore paths starting with zero:
      if(answerConstraint != null && !answerConstraint.equals(predictSpace)) {
        logger.info(String.format("Skipping transition %s at pos 0.%n", predictSpace));
        continue;
      }
      // Ignore paths not consistent with input segmentation:
      if(flags.keepAllWhitespaces && "0".equals(predictSpace) && "1".equals(origSpace)) {
          logger.info(String.format("Skipping non-boundary at pos %d, since space in the input.%n",pos));
          continue;
      }
      // Ignore paths adding segment boundaries between two latin characters, or between two digits:
      // (unless already present in original input)
      if("1".equals(predictSpace) && "0".equals(origSpace) && prevChr != null && curChr != null) {
        char p = prevChr.charAt(0), c = curChr.charAt(0);
        if (ChineseStringUtils.isLetterASCII(p) &&
            ChineseStringUtils.isLetterASCII(c)) {
          logger.info(String.format("Not hypothesizing a boundary at pos %d, since between two ASCII letters (%s and %s).%n",
            pos,prevChr,curChr));
          continue;
        }
        if(ChineseUtils.isNumber(p) && ChineseUtils.isNumber(c)) {
          logger.info(String.format("Not hypothesizing a boundary at pos %d, since between two numeral characters (%s and %s).%n",
            pos,prevChr,curChr));
          continue;
        }
      }
      // If predictSpace==1, create a new transition in answer search graph:
      if ("1".equals(predictSpace)) {
        if (newAnswer.toString().length() > 0) {
          // If answer destination node visited before, create a new edge and leave:
          if(stateLinks.containsKey(tSource)) {
            DFSAState<String, Integer> aDest = stateLinks.get(tSource);
            newASource.addTransition
              (new DFSATransition<>("", newASource, aDest, newAnswer.toString(), "", newCost));
            //logger.info(String.format("new transition: asource=%s adest=%s edge=%s%n", newASource, aDest, newAnswer));
            continue;
          }
          // If answer destination node not visited before, create it + new edge:
          nodeId.incValue(1);
          DFSAState<String, Integer> aDest = new DFSAState<>(nodeId.intValue(), answerLattice, 0.0);
          stateLinks.put(tSource,aDest);
          newASource.addTransition(new DFSATransition<>("", newASource, aDest, newAnswer.toString(), "", newCost));
          //logger.info(String.format("new edge: adest=%s%n", newASource, aDest, newAnswer));
          //logger.info(String.format("new transition: asource=%s adest=%s edge=%s%n%n%n", newASource, aDest, newAnswer));
          // Reached an accepting state:
          if(tSource.isAccepting()) {
            aDest.setAccepting(true);
            continue;
          }
          // Start new answer edge:
          newASource = aDest;
          newAnswer = new StringBuilder();
          newCost = 0.0;
        }
      }
      assert(curChr != null);
      newAnswer.append(curChr);
      newCost += transition.score();
      if (newCost < flags.searchGraphPrune ||
          ChineseStringUtils.isLetterASCII(curChr.charAt(0)))
        tagLatticeToAnswerLattice(tDest, newASource, newAnswer, nodeId, pos+1, newCost, stateLinks, answerLattice, docArray);
    }
  }

}
