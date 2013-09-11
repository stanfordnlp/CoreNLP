package edu.stanford.nlp.ie.ner;

import edu.stanford.nlp.classify.*;
import edu.stanford.nlp.ling.BasicDatum;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.process.WordShapeClassifier;
import edu.stanford.nlp.sequences.BeamBestSequenceFinder;
import edu.stanford.nlp.sequences.BestSequenceFinder;
import edu.stanford.nlp.sequences.SequenceModel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CircleList;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.HashIndex;

import java.io.*;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * ***<B>OBSOLETE</B>***<BR>
 * This class remains for historical purposes only.  If you want to do
 * NER, or other sequence classification, see {@link CMMClassifier}.
 * <BR><BR>
 * Does Named Entity classification.
 * Crucial places to look are <code>makeDatum(List, int)</code>, where
 * features are defined, and in <code>main(String[])</code> where flags
 * are set for what to do.
 *
 * @author Dan Klein
 */
public class NGramStringClassifier {

  static final String BOUNDARY = "*BOUNDARY*";

  static class LineInfo {
    String word = "";
    String tag = "";
    String chunk = "";
    String answer = "";
    String line = "";
    String type = "";
    Collection<String> features = null;
  }

  static class GazetteInfo {
    String feature = "";
    int loc = 0;
    String[] words = new String[0];
  }

  static <X> void display(List<X> guessLabelList, List<X> trueLabelList) {
    Index<X> labelIndex = new HashIndex<X>();
    labelIndex.addAll(guessLabelList);
    labelIndex.addAll(trueLabelList);
    int[] guessLabels = new int[guessLabelList.size()];
    int[] trueLabels = new int[trueLabelList.size()];
    for (int i = 0; i < guessLabelList.size(); i++) {
      guessLabels[i] = labelIndex.indexOf(guessLabelList.get(i));
    }
    for (int i = 0; i < trueLabelList.size(); i++) {
      trueLabels[i] = labelIndex.indexOf(trueLabelList.get(i));
    }
    int numClasses = labelIndex.size();
    int[] p = new int[numClasses];
    int[] r = new int[numClasses];
    int[] c = new int[numClasses];
    for (int i = 0; i < trueLabels.length; i++) {
      if (guessLabels[i] == trueLabels[i]) {
        c[guessLabels[i]]++;
      }
      p[guessLabels[i]]++;
      r[trueLabels[i]]++;
    }
    int tp = 0;
    int tr = 0;
    int tc = 0;
    for (int i = 0; i < numClasses; i++) {
      if (labelIndex.get(i).toString().equals("O")) {
        continue;
      }
      tp += p[i];
      tr += r[i];
      tc += c[i];
      System.out.println("For class: " + labelIndex.get(i));
      dispPRF(c[i], p[i], r[i]);
    }
    System.out.println("Total:");
    dispPRF(tc, tp, tr);
  }

  static void dispPRF(int c, int p, int r) {
    double prec = (double) c / (double) p;
    double rec = (double) c / (double) r;
    double f1 = 2.0 / (1.0 / prec + 1.0 / rec);
    System.out.println(" P: " + nice(prec));
    System.out.println(" R: " + nice(rec));
    System.out.println(" F: " + nice(f1));
  }

  static String nice(double x) {
    return "" + ((int) (x * 10000.0)) / 10000.0;
  }

  static List<String> toLabels(List<Datum<String, String>> dc) {
    List<String> l = new ArrayList<String>();
    for (Datum<String, String> d : dc) {
      l.add(d.label());
    }
    return l;
  }

  static List<String> test(Classifier<String, String> c, List<Datum<String, String>> test) {
    List<String> guess = new ArrayList<String>();
    for (Datum<String, String> example : test) {
      guess.add(c.classOf(example));
    }
    return guess;
  }

  static Map<String, Collection<String>> wordToGazetteEntries = 
    new HashMap<String, Collection<String>>();
  static Map<String, Collection<GazetteInfo>> wordToGazetteInfos = new HashMap<String, Collection<GazetteInfo>>();
  static Pattern p = Pattern.compile("^(\\S+)\\s+(.+)$");

  static void readGazette(BufferedReader in) throws IOException {
    while (in.ready()) {
      String line = in.readLine();
      Matcher m = p.matcher(line);
      if (m.matches()) {
        String type = intern(m.group(1));
        String phrase = m.group(2);
        String[] words = phrase.split(" ");
        for (int i = 0; i < words.length; i++) {
          String word = intern(words[i]);
          if (sloppyGazette) {
            Collection<String> entries = wordToGazetteEntries.get(word);
            if (entries == null) {
              entries = new HashSet<String>();
              wordToGazetteEntries.put(word, entries);
            }
            String feature = intern(type + "-GAZ" + words.length);
            entries.add(feature);
          }
          if (cleanGazette) {
            Collection<GazetteInfo> infos = wordToGazetteInfos.get(word);
            if (infos == null) {
              infos = new HashSet<GazetteInfo>();
              wordToGazetteInfos.put(word, infos);
            }
            GazetteInfo info = new GazetteInfo();
            info.loc = i;
            info.words = words;
            info.feature = intern(type + "-GAZ" + words.length);
            infos.add(info);
          }
        }
      }
    }
  }

  static List<LineInfo> readLines(BufferedReader in) throws IOException {
    List<LineInfo> l = new ArrayList<LineInfo>();
    while (in.ready()) {
      String line = in.readLine();
      LineInfo li = makeLineInfo(line);
      l.add(li);
    }
    if (useEnds) {
      endify(l);
    }
    return l;
  }

  static List<Datum<String, String>> readExamples(BufferedReader in) throws IOException {
    List<LineInfo> l = readLines(in);
    if (useReverse) {
      Collections.reverse(l);
    }
    makeAnswerArrays(l);
    List<Datum<String, String>> dList = new ArrayList<Datum<String, String>>();
    if (printFeatures != null) {
      newFeaturePrinter(printFeatures, "train");
    }
    for (int i = 0; i < l.size(); i++) {
      Datum<String, String> d = makeDatum(l, i);
      dList.add(d);
    }
    if (printFeatures != null) {
      closeFeaturePrinter();
    }
    return dList;
  }

  static class Scorer implements SequenceModel {
    Classifier<String, String> classifier = null;
    int[] tagArray = null;
    String[] answerArray = null;
    Index<String> tagIndex = null;
    List<LineInfo> lineInfos = null;
    int pre = 0;
    int post = 0;
    Set legalTags = null;

    void buildTagArray() {
      tagArray = new int[tagIndex.size()];
      answerArray = new String[tagIndex.size()];
      for (int i = 0; i < tagIndex.size(); i++) {
        tagArray[i] = i;
        answerArray[i] = tagIndex.get(i);
      }
    }

    public int length() {
      return lineInfos.size() - pre - post;
    }

    public int leftWindow() {
      return pre;
    }

    public int rightWindow() {
      return post;
    }

    public int[] getPossibleValues(int pos) {
      if (pos == 0 || pos == lineInfos.size() - 1) {
        int[] a = new int[1];
        a[0] = tagIndex.indexOf("O");
        return a;
      }
      if (tagArray == null) {
        buildTagArray();
      }
      return tagArray;
    }

    public double[] getConditionalDistribution(int[] sequence, int pos) {
      throw new UnsupportedOperationException();
    }

    public double scoreOf(int[] sequence) {
      throw new UnsupportedOperationException();
    }

    double[] scoreCache = null;
    int[] lastWindow = null;
    int lastPos = -1;

    public double scoreOf(int[] tags, int pos) {
      if (false) {
        return scoresOf(tags, pos)[tags[pos]];
      }
      if (lastWindow == null) {
        lastWindow = new int[leftWindow() + rightWindow() + 1];
        Arrays.fill(lastWindow, -1);
      }
      boolean match = (pos == lastPos);
      for (int i = pos - leftWindow(); i <= pos + rightWindow(); i++) {
        if (i == pos) {
          continue;
        }
        match &= tags[i] == lastWindow[i - pos + leftWindow()];
      }
      if (!match) {
        scoreCache = scoresOf(tags, pos);
        for (int i = pos - leftWindow(); i <= pos + rightWindow(); i++) {
          lastWindow[i - pos + leftWindow()] = tags[i];
        }
        lastPos = pos;
      }
      return scoreCache[tags[pos]];
    }

    int percent = -1;
    int num = 0;
    long secs = System.currentTimeMillis();
    long hit = 0;
    long tot = 0;

    public double[] scoresOf(int[] tags, int pos) {
      tot++;
      int p = (100 * pos) / length();
      if (p > percent) {
        long secs2 = System.currentTimeMillis();
        percent = p;
        System.err.println(p + "%   " + (num * 1000 / (secs2 - secs)) + " hits per sec, legal=" + ((100 * hit) / tot) + "% [hit=" + hit + ", tot=" + tot + "]");
        num = 0;
        secs = secs2;
      }
      String[] answers = new String[1 + leftWindow() + rightWindow()];
      String[] pre = new String[leftWindow()];
      for (int i = 0; i < 1 + leftWindow() + rightWindow(); i++) {
        int absPos = pos - leftWindow() + i;
        answers[i] = tagIndex.get(tags[absPos]);
        //LineInfo li = lineInfos.get(i);
        LineInfo li = lineInfos.get(absPos);
        li.answer = answers[i];
        if (i < leftWindow()) {
          pre[i] = answers[i];
        }
      }
      double[] scores = new double[tagIndex.size()];
      //System.out.println("Considering: "+Arrays.asList(pre));
      if (!legalTags.contains(Arrays.asList(pre))) {
        //System.out.println("Rejecting: "+Arrays.asList(pre));
        Arrays.fill(scores, -1000);// Double.NEGATIVE_INFINITY;
        return scores;
      }
      num++;
      hit++;
      Datum<String, String> d = makeDatum(lineInfos, pos);
      Counter<String> c = classifier.scoresOf(d);
      //System.out.println("Pos "+pos+" hist "+Arrays.asList(pre)+" result "+c);
      //System.out.println(c);
      if (false && justify) {
        System.out.println("Considering position " + pos + ", word is " + (lineInfos.get(pos)).word);
        //System.out.println("Datum is "+d.asFeatures());
        System.out.println("History: " + Arrays.asList(pre));
      }
      for (Iterator i = c.keySet().iterator(); i.hasNext();) {
        Object o = i.next();
        int t = tagIndex.indexOf(o.toString());
        if (t > -1) {
          int[] tA = getPossibleValues(pos);
          for (int j = 0; j < tA.length; j++) {
            if (tA[j] == t) {
              scores[j] = c.getCount(o);
              if (false && justify) {
                System.out.println("Label " + o + " got score " + scores[j]);
              }
            }
          }
        }
      }
      // normalize?
      if (normalize) {
        double tot = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < scores.length; i++) {
          tot = addLPs(tot, scores[i]);
        }
        for (int i = 0; i < scores.length; i++) {
          scores[i] -= tot;
        }
      }
      return scores;
    }

    static double abs(double x) {
      return (x >= 0.0 ? x : -x);
    }

    double addLPs(double x, double y) {
      double max = (x > y ? x : y);
      if (max == Double.NEGATIVE_INFINITY) {
        return max;
      }
      if (abs(x - y) > 20.0) {
        return max;
      }
      return Math.log(Math.exp(x - max) + Math.exp(y - max)) + max;
    }

    Scorer(List<LineInfo> lineInfos, Index<String> tagIndex, Classifier<String, String> classifier, int pre, int post, Set legalTags) {
      System.err.println("Built Scorer: pre=" + pre + " post=" + post);
      this.pre = pre;
      this.post = post;
      this.lineInfos = lineInfos;
      this.tagIndex = tagIndex;
      this.classifier = classifier;
      this.legalTags = legalTags;
      /*
      for (Iterator i=legalTags.iterator(); i.hasNext();) {
	List l = (List)i.next();
	if (l.size() <= 2)
	  System.out.println(l);
      }
      */
    }
  }

  static void readAndTestExampleSeq(LinearClassifier<String, String> c, BufferedReader in) throws IOException {
    List<LineInfo> l = readLines(in);
    if (useReverse) {
      Collections.reverse(l);
    }
    Index<String> tagIndex = new HashIndex<String>();
    for (int i = 0; i < l.size(); i++) {
      LineInfo lineInfo = l.get(i);
      tagIndex.add(lineInfo.answer);
    }
    SequenceModel ts = new Scorer(l, tagIndex, c, (!useTaggySequences ? (usePrevSequences ? 1 : 0) : maxLeft), (useNextSequences ? 1 : 0), answerArrays);
    BestSequenceFinder ti = new BeamBestSequenceFinder(beamSize, true, true);
    int[] tags = ti.bestSequence(ts);
    for (int i = 0; i < l.size(); i++) {
      LineInfo lineInfo = l.get(i);
      String answer = tagIndex.get(tags[i]);
      lineInfo.answer = answer;
    }
    if (justify) {
      c.dump();
      for (int i = 0; i < l.size(); i++) {
        LineInfo lineInfo = l.get(i);
        System.out.println(lineInfo.line + " " + lineInfo.answer);
        System.out.println("Position is: " + i);
        c.justificationOf(makeDatum(l, i));
      }
    }
    if (useEnds) {
      deEndify(l);
    }
    if (useReverse) {
      Collections.reverse(l);
    }
    for (int i = 0; i < l.size(); i++) {
      LineInfo lineInfo = l.get(i);
      if (!lineInfo.word.equals(BOUNDARY)) {
        System.out.println(lineInfo.line + " " + lineInfo.answer);
      } else {
        System.out.println(lineInfo.line);
      }
    }
  }

  static void readAndTestExamples(Classifier<String, String> c, BufferedReader in) throws IOException {
    List<LineInfo> l = readLines(in);
    for (int i = 0; i < l.size(); i++) {
      Datum<String, String> d = makeDatum(l, i);
      String answer = c.classOf(d);
      LineInfo lineInfo = l.get(i);
      lineInfo.answer = answer;
    }
    for (int i = 0; i < l.size(); i++) {
      LineInfo lineInfo = l.get(i);
      if (!lineInfo.word.equals(BOUNDARY)) {
        System.out.println(lineInfo.line + " " + lineInfo.answer);
      } else {
        System.out.println(lineInfo.line);
      }
    }
  }

  static Pattern q = Pattern.compile("^(\\S+)\\s*(\\S*)\\s*(\\S*)\\s+(\\S+)$");
  static Pattern q2 = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)$");

  static LineInfo makeLineInfo(String line) {
    Matcher m = q.matcher(line);
    Matcher m2 = q2.matcher(line);
    LineInfo li = new LineInfo();
    li.line = line;
    if (m2.matches()) {
      li.word = intern(m2.group(1));
      if (useLemmas) {
        li.word = intern(m2.group(2));
      }
      li.tag = intern(m2.group(3));
      li.chunk = intern(m2.group(4));
      li.answer = intern(m2.group(5));
      if (li.answer.length() > 2 && !useEnds) {
        li.answer = intern("I-" + li.answer.substring(2, li.answer.length()));
      }
    } else if (m.matches()) {
      li.word = intern(m.group(1));
      li.tag = intern(m.group(2));
      li.chunk = intern(m.group(3));
      li.answer = intern(m.group(4));
      if (li.answer.length() > 2 && !useEnds) {
        li.answer = intern("I-" + li.answer.substring(2, li.answer.length()));
      }
    } else {
      li.word = BOUNDARY;
      li.answer = "O";
    }
    li.word = fix(li.word);
    if (useChrisWordTypes) {
      li.type = WordShapeClassifier.wordShape(li.word, WordShapeClassifier.WORDSHAPECHRIS1, knownLCwords);
    } else if (useBetterWordTypes) {
      li.type = WordShapeClassifier.wordShape(li.word, WordShapeClassifier.WORDSHAPEDAN2, knownLCwords);
    } else {
      li.type = WordShapeClassifier.wordShape(li.word, WordShapeClassifier.WORDSHAPEDAN1, knownLCwords);
    }
    return li;
  }

  static String fix(String word) {
    if (word.equals("Monday") || word.equals("Tuesday") || word.equals("Wednesday") || word.equals("Thursday") || word.equals("Friday") || word.equals("Saturday") || word.equals("Sunday") || word.equals("January") || word.equals("February") || word.equals("March") || word.equals("April") || word.equals("May") || word.equals("June") || word.equals("July") || word.equals("August") || word.equals("September") || word.equals("October") || word.equals("November") || word.equals("December")) {
      return word.toLowerCase();
    }
    return word;
  }

  static void endify(List<LineInfo> lineInfos) {
    lineInfos = new CircleList<LineInfo>(lineInfos);
    int k = lineInfos.size();
    String[] newAnswers = new String[k];
    for (int i = 0; i < k; i++) {
      LineInfo c = lineInfos.get(i);
      LineInfo p = lineInfos.get(i - 1);
      LineInfo n = lineInfos.get(i + 1);
      if (c.answer.length() > 1 && c.answer.charAt(1) == '-') {
        String base = c.answer.substring(2, c.answer.length());
        String pBase = (p.answer.length() > 2 ? p.answer.substring(2, p.answer.length()) : p.answer);
        String nBase = (n.answer.length() > 2 ? n.answer.substring(2, n.answer.length()) : n.answer);
        boolean isFirst = (!base.equals(pBase)) || c.answer.charAt(0) == 'B';
        boolean isLast = (!base.equals(nBase)) || n.answer.charAt(0) == 'B';
        if (isFirst && isLast) {
          newAnswers[i] = intern("S-" + base);
        }
        if ((!isFirst) && isLast) {
          newAnswers[i] = intern("E-" + base);
        }
        if (isFirst && (!isLast)) {
          newAnswers[i] = intern("B-" + base);
        }
        if ((!isFirst) && (!isLast)) {
          newAnswers[i] = intern("I-" + base);
        }
        //System.out.println("Looked at "+p.answer+" ["+c.answer+"] "+n.answer+" and chose "+newAnswers[i]);
      } else {
        newAnswers[i] = c.answer;
      }
    }
    for (int i = 0; i < k; i++) {
      LineInfo c = lineInfos.get(i);
      c.answer = newAnswers[i];
    }
  }

  static void deEndify(List<LineInfo> lineInfos) {
    if (noDeEndify) {
      return;
    }
    int k = lineInfos.size();
    String[] newAnswers = new String[k];
    for (int i = 0; i < k; i++) {
      LineInfo c = lineInfos.get(i);
      LineInfo p = lineInfos.get((i - 1 + k) % k);
//      LineInfo n = lineInfos.get((i + 1) % k);
      if (c.answer.length() > 1 && c.answer.charAt(1) == '-') {
        String base = c.answer.substring(2, c.answer.length());
        String pBase = (p.answer.length() <= 2 ? p.answer : p.answer.substring(2, p.answer.length()));
        boolean isSecond = (base.equals(pBase));
        boolean isStart = (c.answer.charAt(0) == 'B' || c.answer.charAt(0) == 'S');
        if (isSecond && isStart) {
          newAnswers[i] = intern("B-" + base);
        } else {
          newAnswers[i] = intern("I-" + base);
        }
        //newAnswers[i] = c.answer;
        //System.err.println("I looked at "+c.answer+" (after "+p.answer+") and decided on "+newAnswers[i]);
      } else {
        newAnswers[i] = c.answer;
      }
    }
    for (int i = 0; i < k; i++) {
      LineInfo c = lineInfos.get(i);
      c.answer = newAnswers[i];
    }
  }

  static Set<List<String>> answerArrays = new HashSet<List<String>>();

  static void makeAnswerArrays(List<LineInfo> lineInfos) {
    int k = lineInfos.size();
    for (int start = 0; start < k; start++) {
      for (int diff = 1; diff < 5 && start + diff <= k; diff++) {
        String[] seq = new String[diff];
        for (int i = start; i < start + diff; i++) {
          seq[i - start] = lineInfos.get(i).answer;
        }
        answerArrays.add(Arrays.asList(seq));
      }
    }
  }

  static Map<String, Collection<String>> wordToSubstrings = new HashMap<String, Collection<String>>();

  static Datum<String, String> makeDatum(List<LineInfo> info, int loc) {
    List<LineInfo> cInfo = new CircleList<LineInfo>(info);
    LineInfo c = cInfo.get(loc);
    LineInfo n = cInfo.get(loc + 1);
    LineInfo n2 = cInfo.get(loc + 2);
    LineInfo p = cInfo.get(loc - 1);
    LineInfo p2 = cInfo.get(loc - 2);
    LineInfo p3 = cInfo.get(loc - 3);
    List<String> features = new ArrayList<String>();
    Collection<String> cachableFeatures = new ArrayList<String>();
    boolean cached = (c.features != null);
    if (cached) {
      cachableFeatures = c.features;
    }
    if (!cached) {
      cachableFeatures.add(c.word);
      if (useTags) {
        cachableFeatures.add(intern2(c.tag + "-TAG"));
      }
      if (usePrev) {
        cachableFeatures.add(intern2(p.word + "-PREV"));
        if (useTags) {
          cachableFeatures.add(intern2(p.tag + "-PTAG"));
        }
        if (useWordPairs) {
          cachableFeatures.add(intern2(c.word + "-" + p.word + "-CPREV"));
        }
      }
      if (useSymTags) {
        cachableFeatures.add(intern2(p.tag + "-" + c.tag + "-" + n.tag + "-PCNTAGS"));
        cachableFeatures.add(intern2(c.tag + "-" + n.tag + "-CNTAGS"));
        cachableFeatures.add(intern2(p.tag + "-" + c.tag + "-PCTAGS"));
      }
      if (useSymWordPairs) {
        cachableFeatures.add(intern2(p.word + "-" + n.word + "-SWORDS"));
      }
    }
    if (usePrev) {
      if (useSequences && usePrevSequences) {
        features.add(intern2(p.answer + "-PSEQ"));
        features.add(intern2(c.word + "-" + p.answer + "-PSEQW"));
      }
    }
    if (!cached) {
      if (useNext) {
        cachableFeatures.add(intern2(n.word + "-NEXT"));
        if (useTags) {
          cachableFeatures.add(intern2(n.tag + "-NTAG"));
        }
        if (useWordPairs) {
          cachableFeatures.add(intern2(c.word + "-" + n.word + "-CNEXT"));
        }
      }
    }
    if (useNext) {
      if (useSequences && useNextSequences) {
        features.add(intern2(n.answer + "-NSEQ"));
        features.add(intern2(c.word + "-" + n.answer + "-NSEQW"));
      }
    }
    if (useNext && usePrev) {
      if (useSequences && usePrevSequences && useNextSequences) {
        features.add(intern2(p.answer + "-" + n.answer + "-PNSEQ"));
        features.add(intern2(c.word + "-" + p.answer + "-" + n.answer + "-PNSEQW"));
      }
    }
    if (useTaggySequences) {
      features.add(intern2(p.answer + "-" + p.tag + "-" + c.tag + "-TS"));
      features.add(intern2(p2.answer + "-" + p.answer + "-PPSEQ"));
      features.add(intern2(p2.answer + "-" + p2.tag + "-" + p.answer + "-" + p.tag + "-" + c.tag + "-TTS"));
      if (maxLeft >= 3) {
        features.add(intern2(p3.answer + "-" + p3.tag + "-" + p2.answer + "-" + p2.tag + "-" + p.answer + "-" + p.tag + "-" + c.tag + "-TTTS"));
        features.add(intern2(p3.answer + "-" + p2.answer + "-" + p.answer + "-PPPSEQ"));
      }
    }
    if (useTypeySequences) {
      features.add(intern2(c.type + "-" + p.answer + "-TPS2"));
      features.add(intern2(n.type + "-" + p.answer + "-TNS1"));
      features.add(intern2(p.answer + "-" + p.type + "-" + c.type + "-TPS"));
      features.add(intern2(p2.answer + "-" + p2.type + "-" + p.answer + "-" + p.type + "-" + c.type + "-TTPS"));
    }
    //features.add("###");
    //features.add(answer);
    Collection<String> subs = wordToSubstrings.get(c.word);
    if (subs == null) {
      subs = new ArrayList<String>();
      if (useNGrams) {
        String word = "<" + c.word + ">";
        for (int i = Math.min(0, word.length()); i < word.length(); i++) {
          for (int j = i + 2; j <= word.length(); j++) {
            if (noMidNGrams && i != 0 && j != word.length()) {
              continue;
            }
            subs.add(intern("#" + word.substring(i, j) + "#"));
          }
        }
      }
      wordToSubstrings.put(c.word, subs);
    }
    features.addAll(subs);
    if (!cached) {
      if (useGazettes) {
        if (sloppyGazette) {
          Collection<String> entries = wordToGazetteEntries.get(c.word);
          if (entries != null) {
            cachableFeatures.addAll(entries);
          }
        }
        if (cleanGazette) {
          //System.out.println("WORD: "+c.word);
          Collection infos = (Collection) wordToGazetteInfos.get(c.word);
          if (infos != null) {
            for (Iterator i = infos.iterator(); i.hasNext();) {
              GazetteInfo gInfo = (GazetteInfo) i.next();
              boolean ok = true;
              /*
              String str = "";
              for (int x=0; x<gInfo.words.length; x++)
          str = str + " " + gInfo.words[x];
              if (gInfo.words.length > 1)
          System.out.println("TRYING: "+str);
              */
              for (int gLoc = 0; gLoc < gInfo.words.length; gLoc++) {
                ok &= gInfo.words[gLoc].equals((cInfo.get(loc + gLoc - gInfo.loc)).word);
              }
              if (ok) {
                cachableFeatures.add(gInfo.feature);
                //if (gInfo.words.length > 1)
                //  System.out.println("MATCHED: "+str+" FEATURE: "+gInfo.feature);
              }
            }
          }
        }
      }
      if (useWordTypes) {
        cachableFeatures.add(intern2(c.type + "-TYPE"));
        if (useTypeSeqs) {
          String cType = c.type;
          String pType = p.type;
          String nType = n.type;
          cachableFeatures.add(intern2(pType + "-PTYPE"));
          cachableFeatures.add(intern2(nType + "-NTYPE"));
          cachableFeatures.add(intern2(p.word + "..." + cType + "-PW_CTYPE"));
          cachableFeatures.add(intern2(cType + "..." + n.word + "-NW_CTYPE"));
          cachableFeatures.add(intern2(pType + "..." + cType + "-PCTYPE"));
          cachableFeatures.add(intern2(cType + "..." + nType + "-CNTYPE"));
          cachableFeatures.add(intern2(pType + "..." + cType + "..." + nType + "-PCNTYPE"));
          if (useTypeSeqs2) {
            // XXXX This is a bit ugly -- mixes shape classification types
            String p2Type = WordShapeClassifier.wordShape(p2.word, WordShapeClassifier.WORDSHAPEDAN2, knownLCwords);
            cachableFeatures.add(intern2(p.answer + "-" + pType + "-" + cType + "-TYPES"));
            cachableFeatures.add(intern2(p2.answer + "-" + p2Type + "-" + p.answer + "-" + pType + "-" + cType + "-TYPETYPES"));
          }
        }
      }
      if (useLastRealWord) {
        if (p.word.length() <= 3) {
          cachableFeatures.add(intern2(p2.word + "..." + c.type + "-PPW_CTYPE"));
        }
      }
      if (useNextRealWord) {
        if (n.word.length() <= 3) {
          cachableFeatures.add(intern2(n2.word + "..." + c.type + "-NNW_CTYPE"));
        }
      }
      if (useOccurencePatterns) {
        cachableFeatures.addAll(occurencePatterns((CircleList) cInfo, loc));
      }
    }
    if (!cached) {
      c.features = cachableFeatures;
    }
    features.addAll(cachableFeatures);
    if (printFeatures != null) {
      printFeatures(c, features);
    }
    Datum<String, String> d = new BasicDatum<String, String>(features, c.answer);
    //System.out.println(features.size()+" features.");
    return d;
  }

  private static PrintWriter cliqueWriter;

  private static void newFeaturePrinter(String prefix, String suffix) {
    if (cliqueWriter != null) {
      closeFeaturePrinter();
    }
    try {
      cliqueWriter = new PrintWriter(new FileOutputStream(prefix + "." + suffix), true);
    } catch (IOException ioe) {
      cliqueWriter = null;
    }
  }

  private static void closeFeaturePrinter() {
    cliqueWriter.close();
    cliqueWriter = null;
    printFeatures = null;   // will only print first lot of stuff
  }

  private static void printFeatures(LineInfo wi, Collection features) {
    if (cliqueWriter != null) {
      cliqueWriter.print(wi.word + " " + wi.tag + " " + wi.answer);
      cliqueWriter.print("\t");
      boolean first = true;
      for (Iterator it = features.iterator(); it.hasNext();) {
        String feat = (String) it.next();
        if (first) {
          first = false;
        } else {
          cliqueWriter.print(" ");
        }
        cliqueWriter.print(feat);
      }
      cliqueWriter.println();
    }
  }

  static boolean isNameCase(String str) {
    if (str.length() < 2) {
      return false;
    }
    if (!Character.isUpperCase(str.charAt(0))) {
      return false;
    }
    for (int i = 1; i < str.length(); i++) {
      if (Character.isUpperCase(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  static boolean noUpperCase(String str) {
    if (str.length() < 1) {
      return false;
    }
    for (int i = 0; i < str.length(); i++) {
      if (Character.isUpperCase(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  static boolean hasLetter(String str) {
    if (str.length() < 1) {
      return false;
    }
    for (int i = 0; i < str.length(); i++) {
      if (Character.isLetter(str.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  static int reverse(int i) {
    return (useReverse ? -1 * i : i);
  }

  static Collection<String> occurencePatterns(CircleList cInfo, int loc) {
    // features on last Cap
    String word = ((LineInfo) cInfo.get(loc)).word;
    String nWord = ((LineInfo) cInfo.get(loc + reverse(1))).word;
    String pWord = ((LineInfo) cInfo.get(loc - reverse(1))).word;
    Set<String> l = new HashSet<String>();
    //System.err.println(word+" "+nWord);
    if (!(isNameCase(word) && noUpperCase(nWord) && hasLetter(nWord) && hasLetter(pWord) && !pWord.equals(BOUNDARY))) {
      return Collections.singletonList("NO-OCCURENCE-PATTERN");
    }
    //System.err.println("LOOKING");
    if (isNameCase(pWord) && ((LineInfo) cInfo.get(loc - reverse(1))).tag.equals("NNP")) {
      for (int jump = 3; jump < 150; jump++) {
        if (((LineInfo) cInfo.get(loc + reverse(jump))).word.equals(word)) {
          if (((LineInfo) cInfo.get(loc + reverse(jump - 1))).word.equals(pWord)) {
            l.add("XY-NEXT-OCCURENCE-XY");
          } else {
            l.add("XY-NEXT-OCCURENCE-Y");
          }
        }
      }
      for (int jump = -3; jump > -150; jump--) {
        if (((LineInfo) cInfo.get(loc + reverse(jump))).word.equals(word)) {
          if (((LineInfo) cInfo.get(loc + reverse(jump - 1))).word.equals(pWord)) {
            l.add("XY-PREV-OCCURENCE-XY");
          } else {
            l.add("XY-PREV-OCCURENCE-Y");
          }
        }
      }
    } else {
      for (int jump = 3; jump < 150; jump++) {
        if (((LineInfo) cInfo.get(loc + reverse(jump))).word.equals(word)) {
          if (isNameCase(((LineInfo) cInfo.get(loc + reverse(jump - 1))).word) && ((LineInfo) cInfo.get(loc + reverse(jump - 1))).tag.equals("NNP")) {
            l.add("X-NEXT-OCCURENCE-YX");
            //System.err.println(((LineInfo)cInfo.get(loc+reverse(jump-1))).word);
          } else if (isNameCase(((LineInfo) cInfo.get(loc + reverse(jump + 1))).word) && ((LineInfo) cInfo.get(loc + reverse(jump + 1))).tag.equals("NNP")) {
            //System.err.println(((LineInfo)cInfo.get(loc+reverse(jump+1))).word);
            l.add("X-NEXT-OCCURENCE-XY");
          } else {
            l.add("X-NEXT-OCCURENCE-X");
          }
        }
      }
      for (int jump = -3; jump > -150; jump--) {
        if (((LineInfo) cInfo.get(loc + jump)).word.equals(word)) {
          if (isNameCase(((LineInfo) cInfo.get(loc + reverse(jump + 1))).word) && ((LineInfo) cInfo.get(loc + reverse(jump + 1))).tag.equals("NNP")) {
            l.add("X-PREV-OCCURENCE-YX");
            //System.err.println(((LineInfo)cInfo.get(loc+reverse(jump+1))).word);
          } else if (isNameCase(((LineInfo) cInfo.get(loc + reverse(jump - 1))).word) && ((LineInfo) cInfo.get(loc + reverse(jump - 1))).tag.equals("NNP")) {
            l.add("X-PREV-OCCURENCE-XY");
            //System.err.println(((LineInfo)cInfo.get(loc+reverse(jump-1))).word);
          } else {
            l.add("X-PREV-OCCURENCE-X");
          }
        }
      }
    }
    if (!l.isEmpty()) {
      //System.err.println(pWord+" "+word+" "+nWord+" "+l);
    }
    return l;
  }

  static LinearClassifier<String, String> makeClassifier(Reference<List<Datum<String, String>>> ref) {
    if (useNB) {
      return (new NBLinearClassifierFactory<String, String>(sigma)).trainClassifier(ref);
    } else {
      LinearClassifierFactory<String, String> lcf = new LinearClassifierFactory<String, String>((!zippy ? (useSum ? 1e-6 : 1e-4) : 1.0), useSum, (useHuber ? LogPrior.LogPriorType.HUBER.ordinal() : LogPrior.LogPriorType.QUADRATIC.ordinal()), sigma, epsilon, -1);
      lcf.useConjugateGradientAscent();
      LinearClassifier<String, String> lc = lcf.trainClassifier(ref);
      return lc;
    }
  }


  static Set<String> knownLCwords = new HashSet<String>();

  static void mineLCWords(List<LineInfo> lineInfos) {
    for (LineInfo li : lineInfos) {
      String word = li.word;
      knownLCwords.add(word);
    }
  }


  static String intern(String s) {
    if (intern) {
      return s.intern();
    }
    return s;
  }

  static String intern2(String s) {
    if (intern2) {
      return s.intern();
    }
    return s;
  }

  static boolean useNGrams = false;
  static boolean usePrev = false;
  static boolean useNext = false;
  static boolean useTags = false;
  static boolean useWordPairs = false;
  static boolean useGazettes = false;
  static boolean useWordTypes = false;
  static boolean useSequences = false;
  static boolean usePrevSequences = false;
  static boolean useNextSequences = false;
  static boolean useTaggySequences = false;
  static boolean useEnds = false;
  static boolean useGazettePhrases = false;

  static boolean useSum = false;
  static boolean zippy = false;

  static boolean useSymTags = false;
  /**
   * useSymWordPairs Has a small negative effect.
   */
  static boolean useSymWordPairs = false;

  static boolean intern = false;
  static boolean intern2 = false;
  static boolean selfTest = false;

  static boolean sloppyGazette = false;
  static boolean cleanGazette = false;

  static boolean noMidNGrams = false;
  static boolean useReverse = false;
  static boolean noDeEndify = false;

  static boolean useLemmas = false;

  static boolean useNB = false;
  static boolean useBetterWordTypes = false;
  static boolean useChrisWordTypes = false;
  static boolean useTypeSeqs = false;
  static boolean useTypeSeqs2 = false;
  static boolean useLastRealWord = false;
  static boolean useNextRealWord = false;
  static boolean useOccurencePatterns = false;
  static boolean useKnownLC = false;
  static boolean useTypeySequences = false;

  static boolean justify = false;

  static boolean normalize = false;

  static boolean useHuber = false;
  static double sigma = 1.0;
  static double epsilon = 0.01;

  static int beamSize = 30;

  static int maxLeft = 2;

  static String printFeatures = null;


  public static void main(String[] args) throws FileNotFoundException, IOException {
    System.err.print("NGramStringClassifier invoked at " + new Date() + " with arguments:");
    for (int i = 0; i < args.length; i++) {
      System.err.print(" " + args[i]);
    }
    System.err.println();

    String trainFile = null;
    String testFile = null;
    List<String> gazettes = new ArrayList<String>();
    for (int i = 0; i < args.length; i++) {
      if (args[i].equalsIgnoreCase("-left3")) {
        maxLeft = 3;
        continue;
      }
      if (args[i].equalsIgnoreCase("-normalize")) {
        normalize = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-noNormalize")) {
        normalize = false;
        continue;
      }
      if (args[i].equalsIgnoreCase("-symTags")) {
        useSymTags = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-selfTest")) {
        selfTest = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-zippy")) {
        zippy = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-sloppy")) {
        sloppyGazette = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-clean")) {
        cleanGazette = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-ends")) {
        useEnds = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-tagSeqs")) {
        useTaggySequences = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-intern")) {
        intern = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-ngrams")) {
        useNGrams = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-printFeatures")) {
        printFeatures = args[++i];
        continue;
      }
      if (args[i].equalsIgnoreCase("-prev")) {
        usePrev = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-next")) {
        useNext = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-tags")) {
        useTags = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-wordPairs")) {
        useWordPairs = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-sequences")) {
        useSequences = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-pSeqs")) {
        useSequences = true;
        usePrevSequences = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-nSeqs")) {
        useSequences = true;
        useNextSequences = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-train")) {
        trainFile = args[i + 1];
        i++;
        continue;
      }
      if (args[i].equalsIgnoreCase("-test")) {
        testFile = args[i + 1];
        i++;
        continue;
      }
      if (args[i].equalsIgnoreCase("-gazette")) {
        useGazettes = true;
        //sloppyGazette = true;
        //cleanGazette = true;
        gazettes.add(args[i + 1]);
        i++;
        continue;
      }
      if (args[i].equalsIgnoreCase("-scl")) {
        useSum = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-submit")) {
        useTaggySequences = true;
        useNGrams = true;
        usePrev = true;
        useNext = true;
        useTags = true;
        useWordPairs = true;
        useWordTypes = true;
        useSequences = true;
        usePrevSequences = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-noMidNGrams")) {
        noMidNGrams = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-midNGrams")) {
        noMidNGrams = false;
        continue;
      }
      if (args[i].equalsIgnoreCase("-symWordPairs")) {
        useSymWordPairs = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-reverse")) {
        useReverse = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-huber")) {
        useHuber = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-smooth")) {
        sigma = Double.parseDouble(args[i + 1]);
        i++;
        continue;
      }
      if (args[i].equalsIgnoreCase("-epsilon")) {
        epsilon = Double.parseDouble(args[i + 1]);
        i++;
        continue;
      }
      if (args[i].equalsIgnoreCase("-beam")) {
        beamSize = Integer.parseInt(args[i + 1]);
        i++;
        continue;
      }
      if (args[i].equalsIgnoreCase("-retainEntitySubclassification")) {
        noDeEndify = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-lemmas")) {
        useLemmas = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-nb")) {
        useNB = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-wordtypes")) {
        useWordTypes = true;
        useBetterWordTypes = false;
        useChrisWordTypes = false;
        continue;
      }
      if (args[i].equalsIgnoreCase("-wordtypes2")) {
        useWordTypes = true;
        useBetterWordTypes = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-chrisWordTypes")) {
        useWordTypes = true;
        useBetterWordTypes = false;
        useChrisWordTypes = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-typeseqs")) {
        useTypeSeqs = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-typeseqs2")) {
        useTypeSeqs = true;
        useTypeSeqs2 = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-typeseqs3")) {
        useTypeSeqs = true;
        useTypeSeqs2 = true;
        useTypeySequences = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-realword")) {
        useLastRealWord = true;
        useNextRealWord = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-justify")) {
        justify = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-occurence")) {
        useOccurencePatterns = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-known")) {
        useKnownLC = true;
        continue;
      }
      if (args[i].equalsIgnoreCase("-macro")) {
        // submit
        useTaggySequences = true;
        useNGrams = true;
        usePrev = true;
        useNext = true;
        useTags = true;
        useWordPairs = true;
        useWordTypes = true;
        useSequences = true;
        usePrevSequences = true;
        // nomidngrams
        noMidNGrams = true;
        // reverse
        useReverse = true;
        // typeseqs3
        useTypeSeqs = true;
        useTypeSeqs2 = true;
        useTypeySequences = true;
        // wordtypes2
        useWordTypes = true;
        useBetterWordTypes = true;
        // occurence
        useOccurencePatterns = true;
        // realword
        useLastRealWord = true;
        useNextRealWord = true;
        // known
        useKnownLC = true;
        // smooth
        sigma = 3.0;
        // normalize
        normalize = true;
        continue;
      }
      System.err.println("ERROR: Unknown option: " + args[i]);
      System.exit(0);
    }
    if (trainFile == null || testFile == null) {
      System.err.println("usage: java edu.stanford.nlp.ie.ner.NGramStringClassifier -train trainFile -test testFile [-submit|-macro|-smooth sigma|...]");
      System.exit(0);
    }
    for (Iterator<String> gI = gazettes.iterator(); gI.hasNext();) {
      String gazetteFile = gI.next();
      readGazette(new BufferedReader(new FileReader(gazetteFile)));
    }
    if (useKnownLC) {
      mineLCWords(readLines(new BufferedReader(new FileReader(trainFile))));
      mineLCWords(readLines(new BufferedReader(new FileReader(testFile))));
    }
    List<Datum<String, String>> train = readExamples(new BufferedReader(new FileReader(trainFile)));
    Reference<List<Datum<String, String>>> ref = new WeakReference<List<Datum<String, String>>>(train);
    train = null;
    LinearClassifier<String, String> classifier = makeClassifier(ref);
    System.err.println("Built the following classifier: " + classifier);
    if (selfTest) {
      List<Datum<String, String>> test = readExamples(new BufferedReader(new FileReader(testFile)));
      List<String> trueLabels = toLabels(test);
      List<String> guessLabels = test(classifier, test);
      display(guessLabels, trueLabels);
    } else {
      if (!useSequences) {
        readAndTestExamples(classifier, new BufferedReader(new FileReader(testFile)));
      } else {
        readAndTestExampleSeq(classifier, new BufferedReader(new FileReader(testFile)));
      }
    }
  }
}
