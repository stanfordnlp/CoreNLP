package edu.stanford.nlp.patterns.surface;

import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.patterns.surface.ConstantsAndVariables;
import edu.stanford.nlp.patterns.surface.Data;
import edu.stanford.nlp.patterns.surface.PatternsAnnotations;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.logging.Redwood;

public class ApplyPatterns implements Callable<Pair<TwoDimensionalCounter<Pair<String, String>, SurfacePattern>, CollectionValuedMap<String, Integer>>> {
  String label;
  Counter<SurfacePattern> patterns;
  List<String> sentids;
  boolean usePatternResultAsLabel;
  Set<String> alreadyIdentifiedWords;
  boolean restrictToMatched;
  boolean useGoogleNgrams;
  boolean removeStopWordsFromSelectedPhrases;
  boolean removePhrasesWithStopWords;
  ConstantsAndVariables constVars;
  Set<String> ignoreWords;

  public ApplyPatterns(List<String> sentids, Counter<SurfacePattern> patterns, Set<String> commonEngWords, boolean usePatternResultAsLabel, Set<String> alreadyIdentifiedWords,
      boolean restrictToMatched, String label, boolean removeStopWordsFromSelectedPhrases, boolean removePhrasesWithStopWords, ConstantsAndVariables cv) {
    this.patterns = patterns;
    this.usePatternResultAsLabel = usePatternResultAsLabel;
    this.alreadyIdentifiedWords = alreadyIdentifiedWords;
    this.restrictToMatched = restrictToMatched;
    this.sentids = sentids;
    this.label = label;
    this.removeStopWordsFromSelectedPhrases = removeStopWordsFromSelectedPhrases;
    this.removePhrasesWithStopWords = removePhrasesWithStopWords;
    this.constVars = cv;
  }

  @Override
  public Pair<TwoDimensionalCounter<Pair<String, String>, SurfacePattern>, CollectionValuedMap<String, Integer>> call() throws Exception {
    CollectionValuedMap<String, Integer> tokensMatchedPattern = new CollectionValuedMap<String, Integer>();
    //Redwood.log(Redwood.FORCE, "applypatterns", "Applying the patterns");
    // CollectionValuedMap<String, String> patForWord = new
    // CollectionValuedMap<String, String>();
    // Counter<String> rawFreq = new ClassicCounter<String>();
    TwoDimensionalCounter<Pair<String, String>, SurfacePattern> allFreq = new TwoDimensionalCounter<Pair<String, String>, SurfacePattern>();

    for (String sentid : sentids) {
      List<CoreLabel> sent = Data.sents.get(sentid);
      // for (CoreLabel l : sent)
      // rawFreq.incrementCount(l.word());
      for (SurfacePattern pat : patterns.keySet()) {
        String patternStr = pat.toString();

        TokenSequencePattern p = TokenSequencePattern.compile(constVars.env.get(label), patternStr);
        if (pat == null || p == null)
          throw new RuntimeException("why is the pattern " + pat + " null?");

        TokenSequenceMatcher m = p.getMatcher(sent);
        while (m.find()) {

          int s = m.start("$term");
          int e = m.end("$term");
          // String st = "";
          // for(CoreLabel l: sent)
          // st += " " + l.word() + ":"+l.tag();
          // System.out.println("matched " + sent.subList(s, e) +
          // "  because of " + p + " from sent " + st);

          // if (e - s > 1) {
          // Redwood.log(Redwood.DBG, channelNameLogger,
          // "Extracted multiword " + sent.subList(s,
          // e) + " from pattern " + pat);
          // }

          String phrase = "";
          String phraseLemma = "";
          boolean useWordNotLabeled = false;
          boolean doNotUse = false;
          for (int i = s; i < e; i++) {
            CoreLabel l = sent.get(i);
            l.set(PatternsAnnotations.MatchedPattern.class, true);
            if (restrictToMatched) {
              tokensMatchedPattern.add(sentid, i);
            }
            for (Entry<Class, Object> ig : constVars.ignoreWordswithClassesDuringSelection.get(label).entrySet()) {
              if (l.containsKey(ig.getKey()) && l.get(ig.getKey()).equals(ig.getValue())) {
                doNotUse = true;
              }
            }
            boolean containsStop = containsStopWord(l, constVars.getCommonEngWords(), constVars.ignoreWordRegex, ignoreWords);
            if (removePhrasesWithStopWords && containsStop) {
              doNotUse = true;
            } else {
              if (!containsStop || !removeStopWordsFromSelectedPhrases) {
                // TODO: remove answerAnntoation
                if (label == null || l.get(CoreAnnotations.AnswerAnnotation.class) == null || !l.get(constVars.answerClass.get(label)).equals(label.toString())) {
                  useWordNotLabeled = true;
                }
                phrase += " " + l.word();
                phraseLemma += " " + l.lemma();

              }
            }
          }
          if (!doNotUse && useWordNotLabeled) {
            phrase = phrase.trim();
            phraseLemma = phraseLemma.trim();
            // if (e - s > 1) {
            // Redwood.log(Redwood.DBG, channelNameLogger, "Adding " + str);
            // }
            // if (justify) {
            // if (!justificationForTokens.containsKey(phrase))
            // justificationForTokens.put(phrase, new
            // ClassicCounter<SurfacePattern>());
            // justificationForTokens.get(phrase).incrementCount(pat);
            // }
            allFreq.incrementCount(new Pair<String, String>(phrase, phraseLemma), pat, 1.0);
          }
        }
      }
    }

    return new Pair<TwoDimensionalCounter<Pair<String, String>, SurfacePattern>, CollectionValuedMap<String, Integer>>(allFreq, tokensMatchedPattern);
  }

  boolean containsStopWord(CoreLabel l, Set<String> commonEngWords, Pattern ignoreWordRegex, Set<String> ignoreWords) {
    // if(useWordResultCache.containsKey(l.word()))
    // return useWordResultCache.get(l.word());

    if ((commonEngWords.contains(l.lemma()) || commonEngWords.contains(l.word())) || (ignoreWordRegex != null && ignoreWordRegex.matcher(l.lemma()).matches())
        || (ignoreWords !=null && (ignoreWords.contains(l.lemma()) || ignoreWords.contains(l.word())))) {
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
