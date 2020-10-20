package edu.stanford.nlp.patterns.surface;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.MultiPatternMatcher;
import edu.stanford.nlp.ling.tokensregex.SequenceMatchResult;
import edu.stanford.nlp.ling.tokensregex.SequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.patterns.*;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.CoreMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;


/** @author Sonal Gupta */
public class ApplyPatternsMulti<E extends Pattern> implements Callable<Pair<TwoDimensionalCounter<Pair<String, String>, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>>> {

  private final String label;
  private final Map<TokenSequencePattern, E> patterns;
  private final List<String> sentids;
  private final boolean removeStopWordsFromSelectedPhrases;
  private final boolean removePhrasesWithStopWords;
  private final ConstantsAndVariables constVars;
  //Set<String> ignoreWords;
  private final MultiPatternMatcher<CoreMap> multiPatternMatcher;
  private final Map<String, DataInstance> sents;

  public ApplyPatternsMulti(Map<String, DataInstance> sents, List<String> sentids, Map<TokenSequencePattern, E> patterns, String label, boolean removeStopWordsFromSelectedPhrases, boolean removePhrasesWithStopWords, ConstantsAndVariables cv) {
    this.sents = sents;
    this.patterns = patterns;
    multiPatternMatcher = TokenSequencePattern.getMultiPatternMatcher(patterns.keySet());
    this.sentids = sentids;
    this.label = label;
    this.removeStopWordsFromSelectedPhrases = removeStopWordsFromSelectedPhrases;
    this.removePhrasesWithStopWords = removePhrasesWithStopWords;
    this.constVars = cv;
  }

  @Override
  public Pair<TwoDimensionalCounter<Pair<String, String>, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>> call() throws Exception {

    //CollectionValuedMap<String, Integer> tokensMatchedPattern = new CollectionValuedMap<String, Integer>();
    CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat = new CollectionValuedMap<>();

    TwoDimensionalCounter<Pair<String, String>, E> allFreq = new TwoDimensionalCounter<>();
    for (String sentid : sentids) {
      List<CoreLabel> sent = sents.get(sentid).getTokens();

      //FIND_ALL is faster than FIND_NONOVERLAP
      Iterable<SequenceMatchResult<CoreMap>> matched = multiPatternMatcher.find(sent, SequenceMatcher.FindType.FIND_ALL);

      for (SequenceMatchResult<CoreMap> m: matched) {
        int s = m.start("$term");
        int e = m.end("$term");
        E matchedPat = patterns.get(m.pattern());
        matchedTokensByPat.add(matchedPat, new Triple<>(sentid, s, e));
        String phrase = "";
        String phraseLemma = "";
        boolean useWordNotLabeled = false;
        boolean doNotUse = false;

        //find if the neighboring words are labeled - if so - club them together
        if(constVars.clubNeighboringLabeledWords) {
          for (int i = s - 1; i >= 0; i--) {
            if (!sent.get(i).get(constVars.getAnswerClass().get(label)).equals(label)) {
              s = i + 1;
              break;
            }
          }
          for (int i = e; i < sent.size(); i++) {
            if (!sent.get(i).get(constVars.getAnswerClass().get(label)).equals(label)) {
              e = i;
              break;
            }
          }
        }

        //to make sure we discard phrases with stopwords in between, but include the ones in which stop words were removed at the ends if removeStopWordsFromSelectedPhrases is true
        boolean[] addedindices = new boolean[e-s];
        // Arrays.fill(addedindices, false); // unneeded as done on initialization

        for (int i = s; i < e; i++) {
          CoreLabel l = sent.get(i);
          l.set(PatternsAnnotations.MatchedPattern.class, true);

          if(!l.containsKey(PatternsAnnotations.MatchedPatterns.class))
            l.set(PatternsAnnotations.MatchedPatterns.class, new HashSet<>());
          l.get(PatternsAnnotations.MatchedPatterns.class).add(matchedPat);

          // if (restrictToMatched) {
          // tokensMatchedPattern.add(sentid, i);
          // }
          for (Entry<Class, Object> ig : constVars.getIgnoreWordswithClassesDuringSelection().get(label).entrySet()) {
            if (l.containsKey(ig.getKey()) && l.get(ig.getKey()).equals(ig.getValue())) {
              doNotUse = true;
            }
          }
          boolean containsStop = containsStopWord(l, constVars.getCommonEngWords(), PatternFactory.ignoreWordRegex);
          if (removePhrasesWithStopWords && containsStop) {
            doNotUse = true;
          } else {
            if (!containsStop || !removeStopWordsFromSelectedPhrases) {

              if (label == null || l.get(constVars.getAnswerClass().get(label)) == null || !l.get(constVars.getAnswerClass().get(label)).equals(label.toString())) {
                useWordNotLabeled = true;
              }
              phrase += " " + l.word();
              phraseLemma += " " + l.lemma();
              addedindices[i-s] = true;
            }
          }
        }

        for(int i =0; i < addedindices.length; i++){
          if(i > 0 && i < addedindices.length -1 && addedindices[i-1] == true && addedindices[i] == false && addedindices[i+1] == true){
            doNotUse = true;
            break;
          }
        }

        if (!doNotUse && useWordNotLabeled) {
          phrase = phrase.trim();
          phraseLemma = phraseLemma.trim();

          allFreq.incrementCount(new Pair<>(phrase, phraseLemma), matchedPat, 1.0);
        }
      }

//      for (SurfacePattern pat : patterns.keySet()) {
//        String patternStr = pat.toString();
//
//        TokenSequencePattern p = TokenSequencePattern.compile(constVars.env.get(label), patternStr);
//        if (pat == null || p == null)
//          throw new RuntimeException("why is the pattern " + pat + " null?");
//
//        TokenSequenceMatcher m = p.getMatcher(sent);
//        while (m.find()) {
//
//          int s = m.start("$term");
//          int e = m.end("$term");
//
//          String phrase = "";
//          String phraseLemma = "";
//          boolean useWordNotLabeled = false;
//          boolean doNotUse = false;
//          for (int i = s; i < e; i++) {
//            CoreLabel l = sent.get(i);
//            l.set(PatternsAnnotations.MatchedPattern.class, true);
//            if (restrictToMatched) {
//              tokensMatchedPattern.add(sentid, i);
//            }
//            for (Entry<Class, Object> ig : constVars.ignoreWordswithClassesDuringSelection.get(label).entrySet()) {
//              if (l.containsKey(ig.getKey()) && l.get(ig.getKey()).equals(ig.getValue())) {
//                doNotUse = true;
//              }
//            }
//            boolean containsStop = containsStopWord(l, constVars.getCommonEngWords(), constVars.ignoreWordRegex, ignoreWords);
//            if (removePhrasesWithStopWords && containsStop) {
//              doNotUse = true;
//            } else {
//              if (!containsStop || !removeStopWordsFromSelectedPhrases) {
//
//                if (label == null || l.get(constVars.answerClass.get(label)) == null || !l.get(constVars.answerClass.get(label)).equals(label.toString())) {
//                  useWordNotLabeled = true;
//                }
//                phrase += " " + l.word();
//                phraseLemma += " " + l.lemma();
//
//              }
//            }
//          }
//          if (!doNotUse && useWordNotLabeled) {
//            phrase = phrase.trim();
//            phraseLemma = phraseLemma.trim();
//            allFreq.incrementCount(new Pair<String, String>(phrase, phraseLemma), pat, 1.0);
//          }
//        }
//      }
    }

    return new Pair<>(allFreq, matchedTokensByPat);
  }

  private static boolean  containsStopWord(CoreLabel l, Set<String> commonEngWords, java.util.regex.Pattern ignoreWordRegex) {
    // if(useWordResultCache.containsKey(l.word()))
    // return useWordResultCache.get(l.word());

    if ((commonEngWords.contains(l.lemma()) || commonEngWords.contains(l.word())) || (ignoreWordRegex != null && ignoreWordRegex.matcher(l.lemma()).matches())){
        //|| (ignoreWords !=null && (ignoreWords.contains(l.lemma()) || ignoreWords.contains(l.word())))) {
      // useWordResultCache.putIfAbsent(l.word(), false);
      return true;
    }
    //
    // if (l.word().length() >= minLen4Fuzzy) {
    // try {
    // String matchedFuzzy = NoisyLabelSentences.containsFuzzy(commonEngWords,
    // l.word(), minLen4Fuzzy);
    // if (matchedFuzzy != null) {
    // synchronized (commonEngWords) {
    // commonEngWords.add(l.word());
    // System.out.println("word is " + l.word() + " and matched fuzzy with " +
    // matchedFuzzy);
    // }
    // useWordResultCache.putIfAbsent(l.word(), false);
    // return false;
    // }
    // } catch (Exception e) {
    // e.printStackTrace();
    // System.out.println("Exception " + " while fuzzy matching " + l.word());
    // }
    // }
    // useWordResultCache.putIfAbsent(l.word(), true);
    return false;
  }

}
