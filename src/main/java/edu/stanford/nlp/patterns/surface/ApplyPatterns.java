package edu.stanford.nlp.patterns.surface;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.tokensregex.TokenSequenceMatcher;
import edu.stanford.nlp.ling.tokensregex.TokenSequencePattern;
import edu.stanford.nlp.patterns.*;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Applying SurfacePattern to sentences.
 *
 * @param <E>
 * @author Sonal Gupta
 */
public class ApplyPatterns<E extends Pattern>  implements Callable<Triple<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>, Set<CandidatePhrase>>> {

  /** A logger for this class */
  private static final Redwood.RedwoodChannels logger = Redwood.channels(ApplyPatterns.class);

  private final String label;
  private final Map<TokenSequencePattern, E> patterns;
  private final List<String> sentids;
  private final boolean removeStopWordsFromSelectedPhrases;
  private final boolean removePhrasesWithStopWords;
  private final ConstantsAndVariables constVars;
  private final Map<String, DataInstance> sents;


  public ApplyPatterns(Map<String, DataInstance> sents, List<String> sentids, Map<TokenSequencePattern, E> patterns, String label, boolean removeStopWordsFromSelectedPhrases,
                       boolean removePhrasesWithStopWords, ConstantsAndVariables cv) {
    this.sents = sents;
    this.patterns = patterns;
    this.sentids = sentids;
    this.label = label;
    this.removeStopWordsFromSelectedPhrases = removeStopWordsFromSelectedPhrases;
    this.removePhrasesWithStopWords = removePhrasesWithStopWords;
    this.constVars = cv;
  }

  @Override
  public Triple<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>, Set<CandidatePhrase>> call()
    throws Exception {
    // CollectionValuedMap<String, Integer> tokensMatchedPattern = new
    // CollectionValuedMap<String, Integer>();
    try{
      Set<CandidatePhrase> alreadyLabeledPhrases = new HashSet<>();
      TwoDimensionalCounter<CandidatePhrase, E> allFreq = new TwoDimensionalCounter<>();
      CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat = new CollectionValuedMap<>();
      for (String sentid : sentids) {
        List<CoreLabel> sent = sents.get(sentid).getTokens();
        for (Entry<TokenSequencePattern, E> pEn : patterns.entrySet()) {

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

            assert e-s <= PatternFactory.numWordsCompoundMapped.get(label) : "How come the pattern " + pEn.getKey()  + " is extracting phrases longer than numWordsCompound of " + PatternFactory.numWordsCompoundMapped.get(label) + " for label " + label;

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
            // Arrays.fill(addedindices, false); // not needed as initialized false

            for (int i = s; i < e; i++) {
              CoreLabel l = sent.get(i);
              l.set(PatternsAnnotations.MatchedPattern.class, true);

              if(!l.containsKey(PatternsAnnotations.MatchedPatterns.class) || l.get(PatternsAnnotations.MatchedPatterns.class) == null)
                l.set(PatternsAnnotations.MatchedPatterns.class, new HashSet<>());

              SurfacePattern pSur = (SurfacePattern) pEn.getValue();
              assert pSur != null : "Why is " + pEn.getValue() + " not present in the index?!";
              assert l.get(PatternsAnnotations.MatchedPatterns.class) != null : "How come MatchedPatterns class is null for the token. The classes in the key set are " + l.keySet();
              l.get(PatternsAnnotations.MatchedPatterns.class).add(pSur);

              for (Entry<Class, Object> ig : constVars.getIgnoreWordswithClassesDuringSelection()
                .get(label).entrySet()) {
                if (l.containsKey(ig.getKey())
                  && l.get(ig.getKey()).equals(ig.getValue())) {
                  doNotUse = true;
                }
              }
              boolean containsStop = containsStopWord(l,
                constVars.getCommonEngWords(), PatternFactory.ignoreWordRegex);
              if (removePhrasesWithStopWords && containsStop) {
                doNotUse = true;
              } else {
                if (!containsStop || !removeStopWordsFromSelectedPhrases) {
                  if (label == null
                    || l.get(constVars.getAnswerClass().get(label)) == null
                    || !l.get(constVars.getAnswerClass().get(label)).equals(label)) {
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
            if (!doNotUse) {
              matchedTokensByPat.add(pEn.getValue(), new Triple<>(
                      sentid, s, e - 1));

              phrase = phrase.trim();
              if(!phrase.isEmpty()){
                phraseLemma = phraseLemma.trim();
                CandidatePhrase candPhrase = CandidatePhrase.createOrGet(phrase, phraseLemma);
                allFreq.incrementCount(candPhrase, pEn.getValue(), 1.0);
                if (!useWordNotLabeled)
                  alreadyLabeledPhrases.add(candPhrase);
              }
            }
          }
        }
      }
      return new Triple<>(allFreq, matchedTokensByPat, alreadyLabeledPhrases);
    } catch (Exception e) {
      logger.error(e);
      throw e;
    }
  }

  private static boolean lemmaExists(CoreLabel l) {
    return l.lemma() != null && ! l.lemma().isEmpty();

  }

  private static boolean containsStopWord(CoreLabel l, Set<String> commonEngWords, java.util.regex.Pattern ignoreWordRegex) {
    // if(useWordResultCache.containsKey(l.word()))
    // return useWordResultCache.get(l.word());

    if ((commonEngWords != null && ((lemmaExists(l) && commonEngWords.contains(l.lemma())) || commonEngWords.contains(l.word()))) || (ignoreWordRegex != null && ((lemmaExists(l) && ignoreWordRegex.matcher(l.lemma()).matches()) || ignoreWordRegex.matcher(l.word()).matches()))){
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
