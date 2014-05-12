package edu.stanford.nlp.patterns.surface;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.logging.Redwood;

public class CreatePatterns {

  @Option(name = "usePOS4Pattern")
  public boolean usePOS4Pattern = true;

  @Option(name = "minWindow4Pattern")
  public int minWindow4Pattern = 2;

  @Option(name = "maxWindow4Pattern")
  public int maxWindow4Pattern = 4;

  @Option(name = "usePreviousContext")
  public boolean usePreviousContext = true;

  @Option(name = "useNextContext")
  public boolean useNextContext = false;;

  @Option(name = "numMinStopWordsToAdd")
  public int numMinStopWordsToAdd = 3;

  @Option(name = "allowedTagsInitials")
  public String allowedTagsInitialsStr = "N,J";
  public List<String> allowedTagsInitials = null;

  @Option(name = "useFillerWordsInPat")
  public boolean useFillerWordsInPat = true;

  @Option(name = "addPatWithoutPOS")
  public boolean addPatWithoutPOS = true;

  @Option(name = "useStopWordsBeforeTerm")
  public boolean useStopWordsBeforeTerm = false;

  String channelNameLogger = "createpatterns";
  ConstantsAndVariables constVars;

  public CreatePatterns(Properties props, ConstantsAndVariables constVars) throws IOException {
    this.constVars = constVars;
    Execution.fillOptions(ConstantsAndVariables.class, props);
    constVars.setUp(props);
    setUp(props);
  }

  void setUp(Properties props) {
    Execution.fillOptions(this, props);
    
    allowedTagsInitials = Arrays.asList(allowedTagsInitialsStr.split(","));
    if (!addPatWithoutPOS && !this.usePOS4Pattern) {
      throw new RuntimeException("addPatWithoutPOS and usePOS4Pattern both cannot be false ");
    }
  }

  boolean doNotUse(String word, Set<String> stopWords) {
    if (stopWords.contains(word.toLowerCase()) || constVars.ignoreWordRegex.matcher(word).matches())
      return true;
    else
      return false;

  }

  @SuppressWarnings({ "unchecked" })
  public Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>> getContext(String label, List<CoreLabel> sent, int i) {

    Set<SurfacePattern> prevpatterns = new HashSet<SurfacePattern>();
    Set<SurfacePattern> nextpatterns = new HashSet<SurfacePattern>();
    Set<SurfacePattern> prevnextpatterns = new HashSet<SurfacePattern>();

    String fulltag = sent.get(i).tag();
    String tag = fulltag.substring(0, Math.min(fulltag.length(), 2));

    for (int maxWin = 1; maxWin <= maxWindow4Pattern; maxWin++) {
      List<String> previousTokens = new ArrayList<String>();
      String originalPrevStr = "", originalNextStr = "";
      List<String> nextTokens = new ArrayList<String>();

      int numStopWordsprev = 0, numStopWordsnext = 0;
      int numPrevTokensSpecial = 0, numNextTokensSpecial = 0;
      boolean useprev = false, usenext = false;

      if (usePreviousContext) {
        int j = Math.max(0, i - 1);
        int numTokens = 0;
        while (numTokens < maxWin && j >= 0) {
          // for (int j = Math.max(i - maxWin, 0); j < i; j++) {
          CoreLabel tokenj = sent.get(j);
          // do not use this word in context consideration
          if (useFillerWordsInPat && constVars.fillerWords.contains(tokenj.word().toLowerCase())) {
            j--;
            continue;
          }
          if (!tokenj.containsKey(constVars.answerClass.get(label))) {
            throw new RuntimeException("how come the class " + constVars.answerClass.get(label) + " for token " + tokenj.word() + " in " + sent + " is not set");
          }
          boolean isLabeledO = tokenj.get(constVars.answerClass.get(label)).equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
          if (!isLabeledO) {
            numPrevTokensSpecial++;
            previousTokens.add(0, "[{answer:" + tokenj.get(constVars.answerClass.get(label)).toString() + "}]");
            originalPrevStr = tokenj.get(constVars.answerClass.get(label)).toString() + " " + originalPrevStr;
          } else if (tokenj.word().startsWith("http")) {
            useprev = false;
            previousTokens.clear();
            originalPrevStr = "";
            break;
          } else{// if (!tokenj.word().matches("[.,?]")) {
            String str = SurfacePattern.getContextStr(tokenj);
            previousTokens.add(0, str);
            originalPrevStr = tokenj.lemma() + " " + originalPrevStr;
            if (doNotUse(tokenj.lemma(), constVars.stopWords)) {
              numStopWordsprev++;
            }
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
          // do not use this word in context consideration
          if (useFillerWordsInPat && constVars.fillerWords.contains(tokenj.word().toLowerCase())) {
            j++;
            continue;
          }
          if (!tokenj.containsKey(constVars.answerClass.get(label))) {
            throw new RuntimeException("how come the dict annotation for token " + tokenj.word() + " in " + sent + " is not set");
          }
          boolean isLabeledO = tokenj.get(constVars.answerClass.get(label)).equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
          if (!isLabeledO) {
            numNextTokensSpecial++;
            nextTokens.add("[{answer:" + tokenj.get(constVars.answerClass.get(label)).toString() + "}]");
            originalNextStr += " " + tokenj.get(constVars.answerClass.get(label)).toString();
          } else if (tokenj.word().startsWith("http")) {
            usenext = false;
            nextTokens.clear();
            originalNextStr = "";
            break;
          } else if (!tokenj.word().matches("[.,?()]")) {
            String str = SurfacePattern.getContextStr(tokenj);
            nextTokens.add(str);
            originalNextStr += " " + tokenj.lemma();
            if (doNotUse(tokenj.lemma(), constVars.stopWords)) {
              numStopWordsnext++;
            }
          }
          j++;
          numTokens++;
        }
      }
      String prevContext = null, nextContext = null;

      int numNonSpecialPrevTokens = previousTokens.size() - numPrevTokensSpecial;
      int numNonSpecialNextTokens = nextTokens.size() - numNextTokensSpecial;

      String fw = " ";
      if (useFillerWordsInPat)
        fw = " $FILLER{0,2} ";

      String sw = "";
      if (useStopWordsBeforeTerm) {
        sw = " $STOPWORD{0,2} ";
      }

      if (previousTokens.size() >= minWindow4Pattern && (numStopWordsprev < numNonSpecialPrevTokens || numNonSpecialPrevTokens > numMinStopWordsToAdd)) {

        prevContext = StringUtils.join(previousTokens, fw);
        String str = prevContext + fw + sw;
        PatternToken twithoutPOS = null;
        if (addPatWithoutPOS) {
          twithoutPOS = new PatternToken(tag, false, constVars.numWordsCompound> 1, constVars.numWordsCompound);
          // twithoutPOS.setPreviousContext(sw);
        }

        PatternToken twithPOS = null;
        if (usePOS4Pattern) {
          twithPOS = new PatternToken(tag, true, constVars.numWordsCompound > 1, constVars.numWordsCompound);
          // twithPOS.setPreviousContext(sw);
        }

        if (isASCII(prevContext)) {
          if (previousTokens.size() >= minWindow4Pattern) {
            if (twithoutPOS != null) {
              SurfacePattern pat = new SurfacePattern(str, twithoutPOS, "", originalPrevStr, "");
              prevpatterns.add(pat);
            }
            if (twithPOS != null) {
              SurfacePattern patPOS = new SurfacePattern(str, twithPOS, "", originalPrevStr, "");
              prevpatterns.add(patPOS);
            }
          }
          useprev = true;
        }
      }

      if (nextTokens.size() > 0 && (numStopWordsnext < numNonSpecialNextTokens || numNonSpecialNextTokens > numMinStopWordsToAdd)) {
        nextContext = StringUtils.join(nextTokens, fw);
        String str = "";

        PatternToken twithoutPOS = null;
        if (addPatWithoutPOS) {
          twithoutPOS = new PatternToken(tag, false, constVars.numWordsCompound > 1, constVars.numWordsCompound);
          // twithoutPOS.setNextContext(sw);
        }
        PatternToken twithPOS = null;
        if (usePOS4Pattern) {
          twithPOS = new PatternToken(tag, true, constVars.numWordsCompound > 1, constVars.numWordsCompound);
          // twithPOS.setNextContext(sw);
        }
        str += sw + fw + nextContext;

        if (nextTokens.size() >= minWindow4Pattern) {
          if (twithoutPOS != null) {
            SurfacePattern pat = new SurfacePattern("", twithoutPOS, str, "", originalNextStr);
            nextpatterns.add(pat);
          }
          if (twithPOS != null) {
            SurfacePattern patPOS = new SurfacePattern("", twithPOS, str, "", originalNextStr);
            nextpatterns.add(patPOS);
          }

        }
        usenext = true;

      }

      if (useprev && usenext) {
        String strprev = prevContext + fw + sw;

        PatternToken twithoutPOS = null;
        if (addPatWithoutPOS) {
          twithoutPOS = new PatternToken(tag, false, constVars.numWordsCompound > 1, constVars.numWordsCompound);
          // twithoutPOS.setNextContext(sw);
          // twithoutPOS.setPreviousContext(sw);
        }

        PatternToken twithPOS = null;
        if (usePOS4Pattern) {
          twithPOS = new PatternToken(tag, true, constVars.numWordsCompound > 1, constVars.numWordsCompound);
          // twithPOS.setNextContext(sw);
          // twithPOS.setPreviousContext(sw);
        }

        String strnext = sw + fw + nextContext;
        if (previousTokens.size() + nextTokens.size() >= minWindow4Pattern) {

          if (twithoutPOS != null) {
            SurfacePattern pat = new SurfacePattern(strprev, twithoutPOS, strnext, originalPrevStr, originalNextStr);
            prevnextpatterns.add(pat);
          }

          if (twithPOS != null) {
            SurfacePattern patPOS = new SurfacePattern(strprev, twithPOS, strnext, originalPrevStr, originalNextStr);
            prevnextpatterns.add(patPOS);
          }
        }

      }
    }

    Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>> patterns = new Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>(prevpatterns,
        nextpatterns, prevnextpatterns);
    return patterns;
  }

  public static boolean isASCII(String text) {

    Charset charset = Charset.forName("US-ASCII");
    String checked = new String(text.getBytes(charset), charset);
    return checked.equals(text);// && !text.contains("+") &&
                                // !text.contains("*");// && !
                                // text.contains("$") && !text.contains("\"");

  }

  Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>> getAllPatterns(String label, Map<String, List<CoreLabel>> sents) throws InterruptedException, ExecutionException {

    Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>> patternsForEachToken = new HashMap<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>();
    List<String> keyset = new ArrayList<String>(sents.keySet());

    int num = 0;
    if (constVars.numThreads == 1)
      num = keyset.size();
    else
      num = keyset.size() / (constVars.numThreads - 1);
    ExecutorService executor = Executors.newFixedThreadPool(constVars.numThreads);
    Redwood.log(Redwood.FORCE, channelNameLogger, "keyset size is " + keyset.size());
    List<Future<Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>>> list = new ArrayList<Future<Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>>>();
    for (int i = 0; i < constVars.numThreads; i++) {
      Redwood.log(Redwood.FORCE, channelNameLogger, "assigning from " + i * num + " till " + Math.min(keyset.size(), (i + 1) * num));

      Callable<Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>> task = null;
      List<String> ids = keyset.subList(i * num, Math.min(keyset.size(), (i + 1) * num));
      task = new CreatePatternsThread(label, sents, ids);

      Future<Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>> submit = executor.submit(task);
      list.add(submit);
    }

    // Now retrieve the result

    for (Future<Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>> future : list) {
      patternsForEachToken.putAll(future.get());
    }
    executor.shutdown();
    return patternsForEachToken;
  }

  public class CreatePatternsThread implements Callable<Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>> {
    
    String label;
    // Class otherClass;
    Map<String, List<CoreLabel>> sents;
    List<String> sentIds;

    public CreatePatternsThread(String label, Map<String, List<CoreLabel>> sents, List<String> sentIds) {
      
      this.label = label;
      // this.otherClass = otherClass;
      this.sents = sents;
      this.sentIds = sentIds;
    }

    @Override
    public Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>> call() throws Exception {
      Map<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>> patternsForTokens = new HashMap<String, Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>>();

      for (String id : sentIds) {
        List<CoreLabel> sent = sents.get(id);

        Map<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>> p = new HashMap<Integer, Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>>();
        for (int i = 0; i < sent.size(); i++) {
          p.put(i, new Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>>(new HashSet<SurfacePattern>(), new HashSet<SurfacePattern>(),
              new HashSet<SurfacePattern>()));
          CoreLabel token = sent.get(i);
          // do not create patterns around stop words!
          if (doNotUse(token.word(), constVars.stopWords)) {
            continue;
          }
          boolean use = false;
          String tag = token.tag();
          if (allowedTagsInitials == null || allowedTagsInitials.get(0).equals("*"))
            use = true;
          else {
            for (String s : allowedTagsInitials) {
              if (tag.startsWith(s)) {
                use = true;
                break;
              }
            }
          }

          if (use) {
            Triple<Set<SurfacePattern>, Set<SurfacePattern>, Set<SurfacePattern>> pat = getContext(label, sent, i);
            p.put(i, pat);
          }
        }
        patternsForTokens.put(id, p);
      }
      return patternsForTokens;
    }

  }
}