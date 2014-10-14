package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables;
import edu.stanford.nlp.patterns.surface.SurfacePattern.Genre;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.TypesafeMap;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.logging.Redwood;

public class CreatePatterns {

  /**
   * Use POS tag restriction in the target term: One of this and
   * <code>addPatWithoutPOS</code> has to be true.
   */
  @Option(name = "usePOS4Pattern")
  public boolean usePOS4Pattern = true;

  /**
   * Add patterns without POS restriction as well: One of this and
   * <code>usePOS4Pattern</code> has to be true.
   */
  @Option(name = "addPatWithoutPOS")
  public boolean addPatWithoutPOS = true;

  /**
   * Consider contexts longer or equal to these many tokens.
   */
  @Option(name = "minWindow4Pattern")
  public int minWindow4Pattern = 2;

  /**
   * Consider contexts less than or equal to these many tokens -- total of left
   * and right contexts be can double of this.
   */
  @Option(name = "maxWindow4Pattern")
  public int maxWindow4Pattern = 4;

  /**
   * Consider contexts on the left of a token.
   */
  @Option(name = "usePreviousContext")
  public boolean usePreviousContext = true;

  /**
   * Consider contexts on the right of a token.
   */
  @Option(name = "useNextContext")
  public boolean useNextContext = false;;

  /**
   * If the whole (either left or right) context is just stop words, add the
   * pattern only if number of tokens is equal or more than this. This is get
   * patterns like "I am on X" but ignore "on X".
   */
  @Option(name = "numMinStopWordsToAdd")
  public int numMinStopWordsToAdd = 3;


  /**
   * Ignore words like "a", "an", "the" when matching a pattern.
   */
  @Option(name = "useFillerWordsInPat")
  public boolean useFillerWordsInPat = true;

  /**
   * allow to match stop words before a target term. This is to match something
   * like "I am on some X" if the pattern is "I am on X"
   */
  @Option(name = "useStopWordsBeforeTerm")
  public boolean useStopWordsBeforeTerm = false;


  //String channelNameLogger = "createpatterns";

  ConstantsAndVariables constVars;

  Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>> patternsForEachToken ;

  public CreatePatterns(Properties props, ConstantsAndVariables constVars)
      throws IOException {
    this.constVars = constVars;
    Execution.fillOptions(ConstantsAndVariables.class, props);
    constVars.setUp(props);
    setUp(props);
  }

  void setUp(Properties props) {
    Execution.fillOptions(this, props);
    if (!addPatWithoutPOS && !this.usePOS4Pattern) {
      throw new RuntimeException(
          "addPatWithoutPOS and usePOS4Pattern both cannot be false ");
    }
  }

  boolean doNotUse(String word, Set<String> stopWords) {
    if (stopWords.contains(word.toLowerCase())
        || constVars.ignoreWordRegex.matcher(word).matches())
      return true;
    else
      return false;

  }

  Triple<Boolean, String, String> getContextTokenStr(CoreLabel tokenj) {
    String strgeneric = "";
    String strOriginal = "";
    boolean isLabeledO = true;
    for (Entry<String, Class<? extends TypesafeMap.Key<String>>> e : constVars.getAnswerClass().entrySet()) {
      if (!tokenj.get(e.getValue()).equals(constVars.backgroundSymbol)) {
        isLabeledO = false;
        if (strgeneric.isEmpty()) {
          strgeneric = "{" + e.getKey() + ":" + e.getKey() + "}";
          strOriginal = e.getKey();
        } else {
          strgeneric += " | " + "{" + e.getKey() + ":" + e.getKey() + "}";
          strOriginal += "|" + e.getKey();
        }
      }
    }

    for (Entry<String, Class> e : constVars.getGeneralizeClasses().entrySet()) {
      if (!tokenj.get(e.getValue()).equals(constVars.backgroundSymbol)) {
        isLabeledO = false;
        if (strgeneric.isEmpty()) {
          strgeneric = "{" + e.getKey() + ":" + tokenj.get(e.getValue()) + "}";
          strOriginal = e.getKey();
        } else {
          strgeneric += " | " + "{" + e.getKey() + ":"
              + tokenj.get(e.getValue()) + "}";
          strOriginal += "|" + e.getKey();
        }
      }
    }

    if (constVars.useContextNERRestriction) {
      String nerTag = tokenj
          .get(CoreAnnotations.NamedEntityTagAnnotation.class);
      if (nerTag != null
          && !nerTag.equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL)) {
        isLabeledO = false;
        if (strgeneric.isEmpty()) {
          strgeneric = "{ner:" + nerTag + "}";
          strOriginal = nerTag;
        } else {
          strgeneric += " | " + "{ner:" + nerTag + "}";
          strOriginal += "|" + nerTag;
        }
      }
    }

    return new Triple<Boolean, String, String>(isLabeledO, strgeneric,
        strOriginal);
  }

  public Triple<Set<Integer>, Set<Integer>, Set<Integer>> getContext(
     List<CoreLabel> sent, int i) {

    Set<Integer> prevpatterns = new HashSet<Integer>();
    Set<Integer> nextpatterns = new HashSet<Integer>();
    Set<Integer> prevnextpatterns = new HashSet<Integer>();
    CoreLabel token = sent.get(i);
    String tag = null;
    if (usePOS4Pattern) {
      String fulltag = token.tag();
      tag = fulltag.substring(0, Math.min(fulltag.length(), 2));
    }
    String nerTag = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
    for (int maxWin = 1; maxWin <= maxWindow4Pattern; maxWin++) {
      List<String> previousTokens = new ArrayList<String>();
      List<String> originalPrev = new ArrayList<String>(), originalNext = new ArrayList<String>();
      List<String> nextTokens = new ArrayList<String>();

      int numStopWordsprev = 0, numStopWordsnext = 0;
      // int numPrevTokensSpecial = 0, numNextTokensSpecial = 0;
      int numNonStopWordsNext = 0, numNonStopWordsPrev = 0;
      boolean useprev = false, usenext = false;


      PatternToken twithoutPOS = null;
      if (addPatWithoutPOS) {
        twithoutPOS = new PatternToken(tag, false,
            constVars.numWordsCompound > 1, constVars.numWordsCompound,
            nerTag, constVars.useTargetNERRestriction, constVars.useTargetParserParentRestriction, token.get(CoreAnnotations.GrandparentAnnotation.class));
      }

      PatternToken twithPOS = null;
      if (usePOS4Pattern) {
        twithPOS = new PatternToken(tag, true,
            constVars.numWordsCompound > 1, constVars.numWordsCompound,
            nerTag, constVars.useTargetNERRestriction, constVars.useTargetParserParentRestriction, token.get(CoreAnnotations.GrandparentAnnotation.class));
      }

      if (usePreviousContext) {
        // int j = Math.max(0, i - 1);
        int j = i - 1;
        int numTokens = 0;
        while (numTokens < maxWin && j >= 0) {
          // for (int j = Math.max(i - maxWin, 0); j < i; j++) {
          CoreLabel tokenj = sent.get(j);

          String tokenjStr;
          if (constVars.useLemmaContextTokens)
            tokenjStr = tokenj.lemma();
          else
            tokenjStr = tokenj.word();

          // do not use this word in context consideration
          if (useFillerWordsInPat
              && constVars.fillerWords.contains(tokenj.word().toLowerCase())) {
            j--;
            continue;
          }
//          if (!tokenj.containsKey(constVars.answerClass.get(label))) {
//            throw new RuntimeException("how come the class "
//                + constVars.answerClass.get(label) + " for token "
//                + tokenj.word() + " in " + sent + " is not set");
//          }

          Triple<Boolean, String, String> tr = this.getContextTokenStr(tokenj);
          boolean isLabeledO = tr.first;
          String strgeneric = tr.second;
          String strOriginal = tr.third;

          if (!isLabeledO) {
            // numPrevTokensSpecial++;
            previousTokens.add(0, "[" + strgeneric + "]");
            // previousTokens.add(0,
            // "[{answer:"
            // + tokenj.get(constVars.answerClass.get(label)).toString()
            // + "}]");
            originalPrev.add(0, strOriginal);
            numNonStopWordsPrev++;
          } else if (tokenj.word().startsWith("http")) {
            useprev = false;
            previousTokens.clear();
            originalPrev.clear();
            break;
          } else {
            String str = SurfacePattern.getContextStr(tokenj,
                constVars.useLemmaContextTokens,
                constVars.matchLowerCaseContext);
            previousTokens.add(0, str);
            originalPrev.add(0, tokenjStr);
            if (doNotUse(tokenjStr, constVars.getStopWords())) {
              numStopWordsprev++;
            } else
              numNonStopWordsPrev++;
          }
          numTokens++;
          j--;
        }
      }

      if (useNextContext) {
        int numTokens = 0;
        int j = i + 1;
        while (numTokens < maxWin && j < sent.size()) {
          // for (int j = i + 1; j < sent.size() && j <= i + maxWin; j++) {
          CoreLabel tokenj = sent.get(j);

          String tokenjStr;
          if (constVars.useLemmaContextTokens)
            tokenjStr = tokenj.lemma();
          else
            tokenjStr = tokenj.word();

          // do not use this word in context consideration
          if (useFillerWordsInPat
              && constVars.fillerWords.contains(tokenj.word().toLowerCase())) {
            j++;
            continue;
          }
//          if (!tokenj.containsKey(constVars.answerClass.get(label))) {
//            throw new RuntimeException(
//                "how come the dict annotation for token " + tokenj.word()
//                    + " in " + sent + " is not set");
//          }

          Triple<Boolean, String, String> tr = this.getContextTokenStr(tokenj);
          boolean isLabeledO = tr.first;
          String strgeneric = tr.second;
          String strOriginal = tr.third;

          // boolean isLabeledO = tokenj.get(constVars.answerClass.get(label))
          // .equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
          if (!isLabeledO) {
            // numNextTokensSpecial++;
            numNonStopWordsNext++;
            nextTokens.add("[" + strgeneric + "]");
            // nextTokens.add("[{" + label + ":"
            // + tokenj.get(constVars.answerClass.get(label)).toString()
            // + "}]");
            originalNext.add(strOriginal);
            // originalNextStr += " "
            // + tokenj.get(constVars.answerClass.get(label)).toString();
          } else if (tokenj.word().startsWith("http")) {
            usenext = false;
            nextTokens.clear();
            originalNext.clear();
            break;
          } else {// if (!tokenj.word().matches("[.,?()]")) {
            String str = SurfacePattern.getContextStr(tokenj,
                constVars.useLemmaContextTokens,
                constVars.matchLowerCaseContext);
            nextTokens.add(str);
            originalNext.add(tokenjStr);
            if (doNotUse(tokenjStr, constVars.getStopWords())) {
              numStopWordsnext++;
            } else
              numNonStopWordsNext++;
          }
          j++;
          numTokens++;
        }
      }
      // String prevContext = null, nextContext = null;

      // int numNonSpecialPrevTokens = previousTokens.size()
      // - numPrevTokensSpecial;
      // int numNonSpecialNextTokens = nextTokens.size() - numNextTokensSpecial;

      String fw = "";
      if (useFillerWordsInPat)
        fw = " $FILLER{0,2} ";

      String sw = "";
      if (useStopWordsBeforeTerm) {
        sw = " $STOPWORD{0,2} ";
      }

      String[] prevContext = null;
      String[] prevOriginalArr = null;
      // if (previousTokens.size() >= minWindow4Pattern
      // && (numStopWordsprev < numNonSpecialPrevTokens ||
      // numNonSpecialPrevTokens > numMinStopWordsToAdd)) {
      if (previousTokens.size() >= minWindow4Pattern
          && (numNonStopWordsPrev > 0 || numStopWordsprev > numMinStopWordsToAdd)) {

        // prevContext = StringUtils.join(previousTokens, fw);

        List<String> prevContextList = new ArrayList<String>();
        List<String> prevOriginal = new ArrayList<String>();
        for (String p : previousTokens) {
          prevContextList.add(p);
          if (!fw.isEmpty())
            prevContextList.add(fw.trim());
        }

        // add fw and sw to the the originalprev
        for (String p : originalPrev) {
          prevOriginal.add(p);
          if (!fw.isEmpty())
            prevOriginal.add(" FW ");
        }

        if (!sw.isEmpty()) {
          prevContextList.add(sw.trim());
          prevOriginal.add(" SW ");
        }

        // String str = prevContext + fw + sw;


        if (isASCII(StringUtils.join(prevOriginal))) {
          prevContext = prevContextList.toArray(new String[0]);
          prevOriginalArr = prevOriginal.toArray(new String[0]);
          if (previousTokens.size() >= minWindow4Pattern) {
            if (twithoutPOS != null) {
              SurfacePattern pat = new SurfacePattern(prevContext, twithoutPOS,
                  null, Genre.PREV);
              prevpatterns.add(constVars.patternIndex.addToIndex(pat));
            }
            if (twithPOS != null) {
              SurfacePattern patPOS = new SurfacePattern(prevContext, twithPOS,
                  null, Genre.PREV);
              prevpatterns.add(constVars.patternIndex.addToIndex(patPOS));
            }
          }
          useprev = true;
        }
      }

      String[] nextContext = null;
      String [] nextOriginalArr = null;
      // if (nextTokens.size() > 0
      // && (numStopWordsnext < numNonSpecialNextTokens ||
      // numNonSpecialNextTokens > numMinStopWordsToAdd)) {
      if (nextTokens.size() > 0
          && (numNonStopWordsNext > 0 || numStopWordsnext > numMinStopWordsToAdd)) {
        // nextContext = StringUtils.join(nextTokens, fw);
        List<String> nextContextList = new ArrayList<String>();

        List<String> nextOriginal = new ArrayList<String>();

        if (!sw.isEmpty()) {
          nextContextList.add(sw.trim());
          nextOriginal.add(" SW ");
        }

        for (String n : nextTokens) {
          if (!fw.isEmpty())
            nextContextList.add(fw);
          nextContextList.add(n);
        }

        for (String n : originalNext) {
          if (!fw.isEmpty())
            nextOriginal.add(" FW ");
          nextOriginal.add(n);
        }

        if (nextTokens.size() >= minWindow4Pattern) {
          nextContext = nextContextList.toArray(new String[0]);
          nextOriginalArr =  nextOriginal.toArray(new String[0]);
          if (twithoutPOS != null) {
            SurfacePattern pat = new SurfacePattern(null, twithoutPOS,
                nextContext, Genre.NEXT);
            nextpatterns.add(constVars.patternIndex.addToIndex(pat));
          }
          if (twithPOS != null) {
            SurfacePattern patPOS = new SurfacePattern(null, twithPOS,
                nextContext, Genre.NEXT);
            nextpatterns.add(constVars.patternIndex.addToIndex(patPOS));
          }

        }
        usenext = true;

      }

      if (useprev && usenext) {
        // String strprev = prevContext + fw + sw;

        // String strnext = sw + fw + nextContext;
        if (previousTokens.size() + nextTokens.size() >= minWindow4Pattern) {

          if (twithoutPOS != null) {
            SurfacePattern pat = new SurfacePattern(prevContext, twithoutPOS,
                nextContext, Genre.PREVNEXT);
            prevnextpatterns.add(constVars.patternIndex.addToIndex(pat));
          }

          if (twithPOS != null) {
            SurfacePattern patPOS = new SurfacePattern(prevContext, twithPOS,
                nextContext, Genre.PREVNEXT);
            prevnextpatterns.add(constVars.patternIndex.addToIndex(patPOS));
          }
        }

      }
    }

    Triple<Set<Integer>, Set<Integer>, Set<Integer>> patterns = new Triple<Set<Integer>, Set<Integer>, Set<Integer>>(
        prevpatterns, nextpatterns, prevnextpatterns);
    // System.out.println("For word " + sent.get(i) + " in sentence " + sent +
    // " prev patterns are " + prevpatterns);
    // System.out.println("For word " + sent.get(i) + " in sentence " + sent +
    // " next patterns are " + nextpatterns);
    // System.out.println("For word " + sent.get(i) + " in sentence " + sent +
    // " prevnext patterns are " + prevnextpatterns);
    return patterns;
  }

  public static boolean isASCII(String text) {

    Charset charset = Charset.forName("US-ASCII");
    String checked = new String(text.getBytes(charset), charset);
    return checked.equals(text);// && !text.contains("+") &&
                                // !text.contains("*");// && !
                                // text.contains("$") && !text.contains("\"");

  }

  public Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>> getPatternsForEachToken(){
    return patternsForEachToken;
  }

  public Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>> getAllPatterns(Map<String, List<CoreLabel>> sents)
      throws InterruptedException, ExecutionException {

    patternsForEachToken = new HashMap<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>();
    List<String> keyset = new ArrayList<String>(sents.keySet());

    int num = 0;
    if (constVars.numThreads == 1)
      num = keyset.size();
    else
      num = keyset.size() / (constVars.numThreads);
    ExecutorService executor = Executors
        .newFixedThreadPool(constVars.numThreads);

    Redwood.log(ConstantsAndVariables.extremedebug, "Computing all patterns. keyset size is " + keyset.size() + ". Assigning " + num + " values to each thread");
    List<Future<Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>>> list = new ArrayList<Future<Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>>>();
    for (int i = 0; i < constVars.numThreads; i++) {

      int from = i * num;
      int to = -1;
      if(i == constVars.numThreads -1)
        to = keyset.size();
      else
       to =Math.min(keyset.size(), (i + 1) * num);
//
//      Redwood.log(ConstantsAndVariables.extremedebug, "assigning from " + i * num
//          + " till " + Math.min(keyset.size(), (i + 1) * num));

      Callable<Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>> task = null;
      List<String> ids = keyset.subList(from ,to);
      task = new CreatePatternsThread(sents, ids);

      Future<Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>> submit = executor
          .submit(task);
      list.add(submit);
    }

    // Now retrieve the result

    for (Future<Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>> future : list) {
      try{
        patternsForEachToken.putAll(future.get());
      } catch(Exception e){
        executor.shutdownNow();
        throw new RuntimeException(e);
      }
    }
    executor.shutdown();
    Redwood.log(ConstantsAndVariables.extremedebug, "Done computing all patterns");

    return patternsForEachToken;
  }

  public class CreatePatternsThread
      implements
      Callable<Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>> {

    //String label;
    // Class otherClass;
    Map<String, List<CoreLabel>> sents;
    List<String> sentIds;

    public CreatePatternsThread(Map<String, List<CoreLabel>> sents, List<String> sentIds) {

      //this.label = label;
      // this.otherClass = otherClass;
      this.sents = sents;
      this.sentIds = sentIds;
    }

    @Override
    public Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>> call() throws Exception {
      Map<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>> patternsForTokens = new HashMap<String, Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>>();

      for (String id : sentIds) {
        List<CoreLabel> sent = sents.get(id);

        Map<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>> p = new HashMap<Integer, Triple<Set<Integer>, Set<Integer>, Set<Integer>>>();
        for (int i = 0; i < sent.size(); i++) {
          p.put(
              i,
              new Triple<Set<Integer>, Set<Integer>, Set<Integer>>(
                  new HashSet<Integer>(), new HashSet<Integer>(),
                  new HashSet<Integer>()));
          CoreLabel token = sent.get(i);
          // do not create patterns around stop words!
          if (doNotUse(token.word(), constVars.getStopWords())) {
            continue;
          }
          Triple<Set<Integer>, Set<Integer>, Set<Integer>> pat = getContext(sent, i);
          p.put(i, pat);

        }
        patternsForTokens.put(id, p);
      }
      return patternsForTokens;
    }

  }
}