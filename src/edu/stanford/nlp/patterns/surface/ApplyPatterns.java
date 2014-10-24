package edu.stanford.nlp.patterns.surface;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.SequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

public class ApplyPatterns    implements  Callable<Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>>> {
  String label;
  Map<TokenSequencePattern, Integer> patterns;
  List<String> sentids;
  boolean removeStopWordsFromSelectedPhrases;
  boolean removePhrasesWithStopWords;
  ConstantsAndVariables constVars;
  Map<String, List<CoreLabel>> sents = null;


  public ApplyPatterns(Map<String, List<CoreLabel>> sents, List<String> sentids, Map<TokenSequencePattern, Integer> patterns, String label, boolean removeStopWordsFromSelectedPhrases, boolean removePhrasesWithStopWords, ConstantsAndVariables cv) {
    this.sents = sents;
    this.patterns = patterns;
    this.sentids = sentids;
    this.label = label;
    this.removeStopWordsFromSelectedPhrases = removeStopWordsFromSelectedPhrases;
    this.removePhrasesWithStopWords = removePhrasesWithStopWords;
    this.constVars = cv;
}

  @Override
  public Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>> call()
      throws Exception {
    // CollectionValuedMap<String, Integer> tokensMatchedPattern = new
    // CollectionValuedMap<String, Integer>();

    TwoDimensionalCounter<Pair<String, String>, Integer> allFreq = new TwoDimensionalCounter<Pair<String, String>, Integer>();
    CollectionValuedMap<Integer, Triple<String, Integer, Integer>> matchedTokensByPat = new CollectionValuedMap<Integer, Triple<String, Integer, Integer>>();
    for (String sentid : sentids) {
      List<CoreLabel> sent = sents.get(sentid);
      for (Entry<TokenSequencePattern, Integer> pEn : patterns.entrySet()) {

        if (pEn.getKey() == null)
          throw new RuntimeException("why is the pattern " + pEn + " null?");

        TokenSequenceMatcher m = pEn.getKey().getMatcher(sent);

//        //Setting this find type can save time in searching - greedy and reluctant quantifiers are not enforced
//        m.setFindType(SequenceMatcher.FindType.FIND_ALL);

        //Higher branch values makes the faster but uses more memory
        m.setBranchLimit(5);

        while (m.find()) {

          int s = m.start("$term");
          int e = m.end("$term");

          String phrase = "";
          String phraseLemma = "";
          boolean useWordNotLabeled = false;
          boolean doNotUse = false;

          //to make sure we discard phrases with stopwords in between, but include the ones in which stop words were removed at the ends if removeStopWordsFromSelectedPhrases is true
          boolean[] addedindices = new boolean[e-s];
          Arrays.fill(addedindices, false);

          for (int i = s; i < e; i++) {
            CoreLabel l = sent.get(i);
            l.set(PatternsAnnotations.MatchedPattern.class, true);

            if(!l.containsKey(PatternsAnnotations.MatchedPatterns.class) || l.get(PatternsAnnotations.MatchedPatterns.class) == null)
              l.set(PatternsAnnotations.MatchedPatterns.class, new HashSet<SurfacePattern>());
            SurfacePattern pSur = constVars.getPatternIndex().get(pEn.getValue());
            assert pSur != null : "Why is " + pEn.getValue() + " not present in the index?!";
            l.get(PatternsAnnotations.MatchedPatterns.class).add(pSur);

            for (Entry<Class, Object> ig : constVars.getIgnoreWordswithClassesDuringSelection()
                .get(label).entrySet()) {
              if (l.containsKey(ig.getKey())
                  && l.get(ig.getKey()).equals(ig.getValue())) {
                doNotUse = true;
              }
            }
            boolean containsStop = containsStopWord(l,
                constVars.getCommonEngWords(), constVars.ignoreWordRegex);
            if (removePhrasesWithStopWords && containsStop) {
              doNotUse = true;
            } else {
              if (!containsStop || !removeStopWordsFromSelectedPhrases) {

                if (label == null
                    || l.get(constVars.getAnswerClass().get(label)) == null
                    || !l.get(constVars.getAnswerClass().get(label)).equals(
                        label.toString())) {
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

            matchedTokensByPat.add(pEn.getValue(), new Triple<String, Integer, Integer>(
                sentid, s, e -1 ));
            if (useWordNotLabeled) {
              phrase = phrase.trim();
              phraseLemma = phraseLemma.trim();
              allFreq.incrementCount(new Pair<String, String>(phrase,
                  phraseLemma), pEn.getValue(), 1.0);
            }
          }
        }
      }
    }
    return new Pair<TwoDimensionalCounter<Pair<String, String>, Integer>, CollectionValuedMap<Integer, Triple<String, Integer, Integer>>>(allFreq, matchedTokensByPat);


  }

  boolean  containsStopWord(CoreLabel l, Set<String> commonEngWords, Pattern ignoreWordRegex) {
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
