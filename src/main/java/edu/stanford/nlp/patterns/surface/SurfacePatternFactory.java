package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.CandidatePhrase;
import edu.stanford.nlp.patterns.ConstantsAndVariables;
import edu.stanford.nlp.patterns.DataInstance;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.sequences.SeqClassifierFlags;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.StringUtils;
import edu.stanford.nlp.util.Triple;

import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by sonalg on 10/27/14.
 */
public class SurfacePatternFactory extends PatternFactory {

  /**
   * Use POS tag restriction in the target term: One of this and
   * <code>addPatWithoutPOS</code> has to be true.
   */
  @ArgumentParser.Option(name = "usePOS4Pattern")
  public static boolean usePOS4Pattern = true;

  /**
   * Use first two letters of the POS tag
   */
  @ArgumentParser.Option(name="useCoarsePOS")
  public static boolean useCoarsePOS = true;

  /**
   * Add patterns without POS restriction as well: One of this and
   * <code>usePOS4Pattern</code> has to be true.
   */
  @ArgumentParser.Option(name = "addPatWithoutPOS")
  public static boolean addPatWithoutPOS = true;

  /**
   * Consider contexts longer or equal to these many tokens.
   */
  @ArgumentParser.Option(name = "minWindow4Pattern")
  public static int minWindow4Pattern = 2;

  /**
   * Consider contexts less than or equal to these many tokens -- total of left
   * and right contexts be can double of this.
   */
  @ArgumentParser.Option(name = "maxWindow4Pattern")
  public static int maxWindow4Pattern = 4;

  /**
   * Consider contexts on the left of a token.
   */
  @ArgumentParser.Option(name = "usePreviousContext")
  public static boolean usePreviousContext = true;

  /**
   * Consider contexts on the right of a token.
   */
  @ArgumentParser.Option(name = "useNextContext")
  public static boolean useNextContext = false;;

  /**
   * If the whole (either left or right) context is just stop words, add the
   * pattern only if number of tokens is equal or more than this. This is get
   * patterns like "I am on X" but ignore "on X".
   */
  @ArgumentParser.Option(name = "numMinStopWordsToAdd")
  public static int numMinStopWordsToAdd = 3;


  /**
   * Adds the parent's tag from the parse tree to the target phrase in the patterns
   */
  @ArgumentParser.Option(name = "useTargetParserParentRestriction")
  public static boolean useTargetParserParentRestriction = false;

  /**
   * If the NER tag of the context tokens is not the background symbol,
   * generalize the token with the NER tag
   */
  @ArgumentParser.Option(name = "useContextNERRestriction")
  public static boolean useContextNERRestriction = false;

  /**
   * Ignore words like "a", "an", "the" when matching a pattern.
   */
  @ArgumentParser.Option(name = "useFillerWordsInPat")
  public static boolean useFillerWordsInPat = true;



  public static enum Genre {
    PREV, NEXT, PREVNEXT
  };

  static Token fw, sw;

  public static void setUp(Properties props){
    ArgumentParser.fillOptions(PatternFactory.class, props);
    ArgumentParser.fillOptions(SurfacePatternFactory.class, props);
    ArgumentParser.fillOptions(SurfacePattern.class, props);

    if (!addPatWithoutPOS && !usePOS4Pattern) {
      throw new RuntimeException(
        "addPatWithoutPOS and usePOS4Pattern both cannot be false ");
    }

    fw = new Token(PatternType.SURFACE);
    if (useFillerWordsInPat) {
      fw.setEnvBindRestriction("$FILLER");
      fw.setNumOcc(0,2);
    }
    sw = new Token(PatternType.SURFACE);
    if (useStopWordsBeforeTerm) {
      sw.setEnvBindRestriction("$STOPWORD");
      sw.setNumOcc(0, 2);
    }
  }


  public static Set<SurfacePattern> getContext(List<CoreLabel> sent, int i, Set<CandidatePhrase> stopWords) {


    Set<SurfacePattern> prevpatterns = new HashSet<>();
    Set<SurfacePattern> nextpatterns = new HashSet<>();
    Set<SurfacePattern> prevnextpatterns = new HashSet<>();
    CoreLabel token = sent.get(i);
    String tag = null;
    if (usePOS4Pattern) {
      String fulltag = token.tag();
      if(useCoarsePOS)
        tag = fulltag.substring(0, Math.min(fulltag.length(), 2));
      else
        tag = fulltag;
    }
    String nerTag = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);
    for (int maxWin = 1; maxWin <= maxWindow4Pattern; maxWin++) {
      List<Token> previousTokens = new ArrayList<>();
      List<String> originalPrev = new ArrayList<>(), originalNext = new ArrayList<>();
      List<Token> nextTokens = new ArrayList<>();

      int numStopWordsprev = 0, numStopWordsnext = 0;
      // int numPrevTokensSpecial = 0, numNextTokensSpecial = 0;
      int numNonStopWordsNext = 0, numNonStopWordsPrev = 0;
      boolean useprev = false, usenext = false;


      PatternToken twithoutPOS = null;
      //TODO: right now using numWordsCompoundMax.
      if (addPatWithoutPOS) {
        twithoutPOS = new PatternToken(tag, false,
          numWordsCompoundMax > 1, numWordsCompoundMax,
          nerTag, useTargetNERRestriction, useTargetParserParentRestriction, token.get(CoreAnnotations.GrandparentAnnotation.class));
      }

      PatternToken twithPOS = null;
      if (usePOS4Pattern) {
        twithPOS = new PatternToken(tag, true,
          numWordsCompoundMax > 1, numWordsCompoundMax,
          nerTag, useTargetNERRestriction, useTargetParserParentRestriction, token.get(CoreAnnotations.GrandparentAnnotation.class));
      }

      if (usePreviousContext) {
        // int j = Math.max(0, i - 1);
        int j = i - 1;
        int numTokens = 0;
        while (numTokens < maxWin && j >= 0) {
          // for (int j = Math.max(i - maxWin, 0); j < i; j++) {
          CoreLabel tokenj = sent.get(j);

          String tokenjStr;
          if (useLemmaContextTokens)
            tokenjStr = tokenj.lemma();
          else
            tokenjStr = tokenj.word();

          // do not use this word in context consideration
          if (useFillerWordsInPat
            && fillerWords.contains(tokenj.word().toLowerCase())) {
            j--;
            continue;
          }
//          if (!tokenj.containsKey(answerClass.get(label))) {
//            throw new RuntimeException("how come the class "
//                + answerClass.get(label) + " for token "
//                + tokenj.word() + " in " + sent + " is not set");
//          }

          Triple<Boolean, Token, String> tr = getContextTokenStr(tokenj);
          boolean isLabeledO = tr.first;
          Token strgeneric = tr.second;
          String strOriginal = tr.third;

          if (!isLabeledO) {
            // numPrevTokensSpecial++;
            previousTokens.add(0, strgeneric);
            // previousTokens.add(0,
            // "[{answer:"
            // + tokenj.get(answerClass.get(label)).toString()
            // + "}]");
            originalPrev.add(0, strOriginal);
            numNonStopWordsPrev++;
          } else if (tokenj.word().startsWith("http")) {
            useprev = false;
            previousTokens.clear();
            originalPrev.clear();
            break;
          } else {
            Token str = SurfacePattern.getContextToken(tokenj);
            previousTokens.add(0, str);
            originalPrev.add(0, tokenjStr);
            if (doNotUse(tokenjStr, stopWords)) {
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
          if (useLemmaContextTokens)
            tokenjStr = tokenj.lemma();
          else
            tokenjStr = tokenj.word();

          // do not use this word in context consideration
          if (useFillerWordsInPat
            && fillerWords.contains(tokenj.word().toLowerCase())) {
            j++;
            continue;
          }
//          if (!tokenj.containsKey(answerClass.get(label))) {
//            throw new RuntimeException(
//                "how come the dict annotation for token " + tokenj.word()
//                    + " in " + sent + " is not set");
//          }

          Triple<Boolean, Token, String> tr = getContextTokenStr(tokenj);
          boolean isLabeledO = tr.first;
          Token strgeneric = tr.second;
          String strOriginal = tr.third;

          // boolean isLabeledO = tokenj.get(answerClass.get(label))
          // .equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL);
          if (!isLabeledO) {
            // numNextTokensSpecial++;
            numNonStopWordsNext++;
            nextTokens.add(strgeneric);
            // nextTokens.add("[{" + label + ":"
            // + tokenj.get(answerClass.get(label)).toString()
            // + "}]");
            originalNext.add(strOriginal);
            // originalNextStr += " "
            // + tokenj.get(answerClass.get(label)).toString();
          } else if (tokenj.word().startsWith("http")) {
            usenext = false;
            nextTokens.clear();
            originalNext.clear();
            break;
          } else {// if (!tokenj.word().matches("[.,?()]")) {
            Token str = SurfacePattern.getContextToken(tokenj);
            nextTokens.add(str);
            originalNext.add(tokenjStr);
            if (doNotUse(tokenjStr, stopWords)) {
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



      Token[] prevContext = null;
      //String[] prevContext = null;
      //String[] prevOriginalArr = null;
      // if (previousTokens.size() >= minWindow4Pattern
      // && (numStopWordsprev < numNonSpecialPrevTokens ||
      // numNonSpecialPrevTokens > numMinStopWordsToAdd)) {
      if (previousTokens.size() >= minWindow4Pattern
        && (numNonStopWordsPrev > 0 || numStopWordsprev > numMinStopWordsToAdd)) {

        // prevContext = StringUtils.join(previousTokens, fw);

        List<Token> prevContextList = new ArrayList<>();
        List<String> prevOriginal = new ArrayList<>();
        for (Token p : previousTokens) {
          prevContextList.add(p);
          if (!fw.isEmpty())
            prevContextList.add(fw);
        }

        // add fw and sw to the the originalprev
        for (String p : originalPrev) {
          prevOriginal.add(p);
          if (!fw.isEmpty())
            prevOriginal.add(" FW ");
        }

        if (!sw.isEmpty()) {
          prevContextList.add(sw);
          prevOriginal.add(" SW ");
        }

        // String str = prevContext + fw + sw;


        if (isASCII(StringUtils.join(prevOriginal))) {
          prevContext = prevContextList.toArray(new Token[0]);
          //prevOriginalArr = prevOriginal.toArray(new String[0]);
          if (previousTokens.size() >= minWindow4Pattern) {
            if (twithoutPOS != null) {
              SurfacePattern pat = new SurfacePattern(prevContext, twithoutPOS,
                null, Genre.PREV);
              prevpatterns.add(pat);
            }
            if (twithPOS != null) {
              SurfacePattern patPOS = new SurfacePattern(prevContext, twithPOS,
                null, Genre.PREV);
              prevpatterns.add(patPOS);
            }
          }
          useprev = true;
        }
      }

      Token[] nextContext = null;
      //String [] nextOriginalArr = null;
      // if (nextTokens.size() > 0
      // && (numStopWordsnext < numNonSpecialNextTokens ||
      // numNonSpecialNextTokens > numMinStopWordsToAdd)) {
      if (nextTokens.size() > 0
        && (numNonStopWordsNext > 0 || numStopWordsnext > numMinStopWordsToAdd)) {
        // nextContext = StringUtils.join(nextTokens, fw);
        List<Token> nextContextList = new ArrayList<>();

        List<String> nextOriginal = new ArrayList<>();

        if (!sw.isEmpty()) {
          nextContextList.add(sw);
          nextOriginal.add(" SW ");
        }

        for (Token n : nextTokens) {
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
          nextContext = nextContextList.toArray(new Token[0]);
          //nextOriginalArr =  nextOriginal.toArray(new String[0]);
          if (twithoutPOS != null) {
            SurfacePattern pat = new SurfacePattern(null, twithoutPOS,
              nextContext, Genre.NEXT);
            nextpatterns.add(pat);
          }
          if (twithPOS != null) {
            SurfacePattern patPOS = new SurfacePattern(null, twithPOS,
              nextContext, Genre.NEXT);
            nextpatterns.add(patPOS);
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
            prevnextpatterns.add(pat);
          }

          if (twithPOS != null) {
            SurfacePattern patPOS = new SurfacePattern(prevContext, twithPOS,
              nextContext, Genre.PREVNEXT);
            prevnextpatterns.add(patPOS);
          }
        }

      }
    }

//    Triple<Set<Integer>, Set<Integer>, Set<Integer>> patterns = new Triple<Set<Integer>, Set<Integer>, Set<Integer>>(
//        prevpatterns, nextpatterns, prevnextpatterns);
    // System.out.println("For word " + sent.get(i) + " in sentence " + sent +
    // " prev patterns are " + prevpatterns);
    // System.out.println("For word " + sent.get(i) + " in sentence " + sent +
    // " next patterns are " + nextpatterns);
    // System.out.println("For word " + sent.get(i) + " in sentence " + sent +
    // " prevnext patterns are " + prevnextpatterns);
    //getPatternIndex().finishCommit();
    return CollectionUtils.unionAsSet(prevpatterns, nextpatterns, prevnextpatterns);
  }



  static Triple<Boolean, Token, String> getContextTokenStr(CoreLabel tokenj) {
    Token strgeneric = new Token(PatternType.SURFACE);
    String strOriginal = "";
    boolean isLabeledO = true;
//    for (Entry<String, Class<? extends TypesafeMap.Key<String>>> e : getAnswerClass().entrySet()) {
//      if (!tokenj.get(e.getValue()).equals(backgroundSymbol)) {
//        isLabeledO = false;
//        if (strOriginal.isEmpty()) {
//          strOriginal = e.getKey();
//        } else {
//          strOriginal += "|" + e.getKey();
//        }
//        strgeneric.addRestriction(e.getKey(), e.getKey());
//      }
//    }

    for (Map.Entry<String, Class> e : ConstantsAndVariables.getGeneralizeClasses().entrySet()) {
      if(!tokenj.containsKey(e.getValue()) || tokenj.get(e.getValue()) == null)
        throw new RuntimeException(" Why does the token not have the class " + e.getValue() + " set? Existing classes " + tokenj.toString(CoreLabel.OutputFormat.ALL));


      if (!tokenj.get(e.getValue()).equals(ConstantsAndVariables.backgroundSymbol)) {
        isLabeledO = false;
        if (strOriginal.isEmpty()) {

          strOriginal = e.getKey();
        } else {

          strOriginal += "|" + e.getKey();
        }
        strgeneric.addORRestriction(e.getValue(), e.getKey());
      }
    }

    if (useContextNERRestriction) {
      String nerTag = tokenj
        .get(CoreAnnotations.NamedEntityTagAnnotation.class);
      if (nerTag != null
        && !nerTag.equals(SeqClassifierFlags.DEFAULT_BACKGROUND_SYMBOL)) {
        isLabeledO = false;
        if (strOriginal.isEmpty()) {

          strOriginal = nerTag;
        } else {

          strOriginal += "|" + nerTag;
        }
        strgeneric.addORRestriction(CoreAnnotations.NamedEntityTagAnnotation.class, nerTag);
      }
    }

    return new Triple<>(isLabeledO, strgeneric,
            strOriginal);
  }

  public static boolean isASCII(String text) {

    Charset charset = Charset.forName("US-ASCII");
    String checked = new String(text.getBytes(charset), charset);
    return checked.equals(text);// && !text.contains("+") &&
    // !text.contains("*");// && !
    // text.contains("$") && !text.contains("\"");

  }

  public static Map<Integer, Set> getPatternsAroundTokens(DataInstance sent, Set<CandidatePhrase> stopWords) {
    Map<Integer, Set> p = new HashMap<>();
    List<CoreLabel> tokens = sent.getTokens();
    for (int i = 0; i < tokens.size(); i++) {
//          p.put(
//              i,
//              new Triple<Set<Integer>, Set<Integer>, Set<Integer>>(
//                  new HashSet<Integer>(), new HashSet<Integer>(),
//                  new HashSet<Integer>()));
      p.put(i, new HashSet<SurfacePattern>());
      CoreLabel token = tokens.get(i);
      // do not create patterns around stop words!
      if (PatternFactory.doNotUse(token.word(), stopWords)) {
        continue;
      }

      Set<SurfacePattern> pat = getContext(sent.getTokens(), i, stopWords);
      p.put(i, pat);

    }
    return p;
  }
}
