package edu.stanford.nlp.patterns.dep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.patterns.CandidatePhrase;
import edu.stanford.nlp.patterns.ConstantsAndVariables;
import edu.stanford.nlp.patterns.DataInstance;
import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.patterns.PatternsAnnotations;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.TwoDimensionalCounter;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.IntPair;
import edu.stanford.nlp.util.Pair;
import edu.stanford.nlp.util.Triple;

/**
 * Applying Dependency patterns to sentences.
 *
 * @author sonalg
 * @version 11/1/14
 */
public class ApplyDepPatterns <E extends Pattern>  implements Callable<Pair<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>>> {

  private String label;
  private Map<SemgrexPattern, E> patterns;
  private List<String> sentids;
  private boolean removeStopWordsFromSelectedPhrases;
  private boolean removePhrasesWithStopWords;
  private ConstantsAndVariables constVars;
  private Map<String, DataInstance> sents; // = null;


  public ApplyDepPatterns(Map<String, DataInstance> sents, List<String> sentids, Map<SemgrexPattern, E> patterns, String label, boolean removeStopWordsFromSelectedPhrases, boolean removePhrasesWithStopWords, ConstantsAndVariables cv) {
    this.sents = sents;
    this.patterns = patterns;
    this.sentids = sentids;
    this.label = label;
    this.removeStopWordsFromSelectedPhrases = removeStopWordsFromSelectedPhrases;
    this.removePhrasesWithStopWords = removePhrasesWithStopWords;
    this.constVars = cv;
  }

  @Override
  public Pair<TwoDimensionalCounter<CandidatePhrase, E>, CollectionValuedMap<E, Triple<String, Integer, Integer>>> call()
          throws Exception {
    // CollectionValuedMap<String, Integer> tokensMatchedPattern = new
    // CollectionValuedMap<String, Integer>();

    TwoDimensionalCounter<CandidatePhrase, E> allFreq = new TwoDimensionalCounter<>();
    CollectionValuedMap<E, Triple<String, Integer, Integer>> matchedTokensByPat = new CollectionValuedMap<>();

    for (String sentid : sentids) {
      DataInstance sent = sents.get(sentid);
      List<CoreLabel> tokens = sent.getTokens();
      for (Map.Entry<SemgrexPattern, E> pEn : patterns.entrySet()) {

        if (pEn.getKey() == null)
          throw new RuntimeException("why is the pattern " + pEn + " null?");

        SemanticGraph graph = ((DataInstanceDep) sent).getGraph();
        //SemgrexMatcher m = pEn.getKey().matcher(graph);
        //TokenSequenceMatcher m = pEn.getKey().matcher(sent);

//        //Setting this find type can save time in searching - greedy and reluctant quantifiers are not enforced
//        m.setFindType(SequenceMatcher.FindType.FIND_ALL);

        //Higher branch values makes the faster but uses more memory
        //m.setBranchLimit(5);

        Collection<ExtractedPhrase> matched = getMatchedTokensIndex(graph, pEn.getKey(), sent, label);

        for (ExtractedPhrase match : matched) {


          int s = match.startIndex;
          int e = match.endIndex  + 1;

          String phrase = "";
          String phraseLemma = "";
          boolean useWordNotLabeled = false;
          boolean doNotUse = false;

          //find if the neighboring words are labeled - if so - club them together
          if(constVars.clubNeighboringLabeledWords) {
            for (int i = s - 1; i >= 0; i--) {
              if (tokens.get(i).get(constVars.getAnswerClass().get(label)).equals(label) && (e - i + 1) <= PatternFactory.numWordsCompoundMapped.get(label)) {
                s = i;
                //System.out.println("for phrase " + match + " clubbing earlier word. new s is " + s);
              } else break;
            }

            for (int i = e; i < tokens.size(); i++) {
              if (tokens.get(i).get(constVars.getAnswerClass().get(label)).equals(label) && (i-s + 1) <= PatternFactory.numWordsCompoundMapped.get(label)) {
                e = i;
                //System.out.println("for phrase " + match + " clubbing next word. new e is " + e);
              } else break;
            }
          }

          //to make sure we discard phrases with stopwords in between, but include the ones in which stop words were removed at the ends if removeStopWordsFromSelectedPhrases is true
          boolean[] addedindices = new boolean[e-s];
          // Arrays.fill(addedindices, false); // get for free on array initialization


          for (int i = s; i < e; i++) {
            CoreLabel l = tokens.get(i);
            l.set(PatternsAnnotations.MatchedPattern.class, true);

            if(!l.containsKey(PatternsAnnotations.MatchedPatterns.class) || l.get(PatternsAnnotations.MatchedPatterns.class) == null)
              l.set(PatternsAnnotations.MatchedPatterns.class, new HashSet<>());

            Pattern pSur = pEn.getValue();
            assert pSur != null : "Why is " + pEn.getValue() + " not present in the index?!";
            assert l.get(PatternsAnnotations.MatchedPatterns.class) != null : "How come MatchedPatterns class is null for the token. The classes in the key set are " + l.keySet();
            l.get(PatternsAnnotations.MatchedPatterns.class).add(pSur);

            for (Map.Entry<Class, Object> ig : constVars.getIgnoreWordswithClassesDuringSelection()
                    .get(label).entrySet()) {
              if (l.containsKey(ig.getKey())
                      && l.get(ig.getKey()).equals(ig.getValue())) {
                doNotUse = true;
              }
            }
            boolean containsStop = containsStopWord(l, constVars.getCommonEngWords(), PatternFactory.ignoreWordRegex);
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
          if (!doNotUse && useWordNotLabeled) {

            matchedTokensByPat.add(pEn.getValue(), new Triple<>(
                    sentid, s, e - 1));
            if (useWordNotLabeled) {
              phrase = phrase.trim();
              phraseLemma = phraseLemma.trim();
              allFreq.incrementCount(CandidatePhrase.createOrGet(phrase,phraseLemma, match.getFeatures()), pEn.getValue(), 1.0);
            }
          }
        }
      }
    }
    return new Pair<>(allFreq, matchedTokensByPat);


  }

  private Predicate<CoreLabel> matchingWordRestriction = new Predicate<CoreLabel>(){
    @Override
    public boolean test(CoreLabel coreLabel) {
      return matchedRestriction(coreLabel, label);
    }
  };

  private Collection<ExtractedPhrase> getMatchedTokensIndex(SemanticGraph graph, SemgrexPattern pattern, DataInstance sent, String label) {


    //TODO: look at the ignoreCommonTags flag
    ExtractPhraseFromPattern extract = new ExtractPhraseFromPattern(false, PatternFactory.numWordsCompoundMapped.get(label));
    Collection<IntPair> outputIndices = new ArrayList<>();
    boolean findSubTrees = true;
    List<CoreLabel> tokensC = sent.getTokens();
    //TODO: see if you can get rid of this (only used for matchedGraphs)

    List<String> tokens = tokensC.stream().map(x -> x.word()).collect(Collectors.toList());

    List<String> outputPhrases = new ArrayList<>();

    List<ExtractedPhrase> extractedPhrases = new ArrayList<>();

    Function<Pair<IndexedWord, SemanticGraph>, Counter<String>> extractFeatures = new Function<Pair<IndexedWord, SemanticGraph>, Counter<String>>() {
      @Override
      public Counter<String> apply(Pair<IndexedWord, SemanticGraph> indexedWordSemanticGraphPair) {
        //TODO: make features;
        Counter<String> feat = new ClassicCounter<>();
        IndexedWord vertex = indexedWordSemanticGraphPair.first();
        SemanticGraph graph = indexedWordSemanticGraphPair.second();
        List<Pair<GrammaticalRelation, IndexedWord>> pt = graph.parentPairs(vertex);
        for(Pair<GrammaticalRelation, IndexedWord> en: pt) {
          feat.incrementCount("PARENTREL-" + en.first());
        }
        return feat;
      }
    };

    extract.getSemGrexPatternNodes(graph, tokens, outputPhrases, outputIndices,
            pattern, findSubTrees, extractedPhrases, constVars.matchLowerCaseContext, matchingWordRestriction);


    /*
    //TODO: probably a bad idea to add ALL ngrams
    Collection<ExtractedPhrase> outputIndicesMaxPhraseLen = new ArrayList<ExtractedPhrase>();
    for(IntPair o: outputIndices){
      int min = o.get(0);
      int max = o.get(1);

      for (int i = min; i <= max ; i++) {

        CoreLabel t = tokensC.get(i);
        String phrase = t.word();
        if(!matchedRestriction(t, label))
          continue;
        for (int ngramSize = 1; ngramSize < PatternFactory.numWordsCompound; ++ngramSize) {
          int j = i + ngramSize - 1;
          if(j > max)
            break;

          CoreLabel tokenj = tokensC.get(j);

          if(ngramSize > 1)
            phrase += " " + tokenj.word();

          if (matchedRestriction(tokenj, label)) {
            outputIndicesMaxPhraseLen.add(new ExtractedPhrase(i, j, phrase));
            //outputIndicesMaxPhraseLen.add(new IntPair(i, j));
          }
        }
      }
    }*/
    //System.out.println("extracted phrases are " + extractedPhrases + " and output indices are " + outputIndices);
    return extractedPhrases;
  }

  private boolean matchedRestriction(CoreLabel coreLabel, String label) {
    boolean use = false;
    if(PatternFactory.useTargetNERRestriction){

      for(String s: constVars.allowedNERsforLabels.get(label)){
        if(coreLabel.get(CoreAnnotations.NamedEntityTagAnnotation.class).matches(s)){
          use = true;
          break;
        }
      }
    } else {
      //System.out.println("not matching NER");
      use = true;
    }

    if(use){
      String tag = coreLabel.tag();
      if (constVars.allowedTagsInitials != null && constVars.allowedTagsInitials.containsKey(label)) {
        for (String allowed : constVars.allowedTagsInitials.get(label)) {
          if (tag.startsWith(allowed)) {
            use = true;
            break;
          }
          use = false;
        }
      }
    }
    if(constVars.debug >= 4)
      if(use)
        System.out.println(coreLabel.word() + " matched restriction " + (PatternFactory.useTargetNERRestriction ? constVars.allowedNERsforLabels.get(label) : "") + "and" + PatternFactory.useTargetNERRestriction + " and " + (constVars.allowedTagsInitials != null ? constVars.allowedTagsInitials.get(label) :""));
      else
        System.out.println(coreLabel.word() + " did not matched restrict " + (PatternFactory.useTargetNERRestriction ? constVars.allowedNERsforLabels.get(label) : "") + "and" + PatternFactory.useTargetNERRestriction + " and " + (constVars.allowedTagsInitials != null ? constVars.allowedTagsInitials.get(label) :""));
    return use;
  }


  private static boolean  containsStopWord(CoreLabel l, Set<String> commonEngWords, java.util.regex.Pattern ignoreWordRegex) {
    // if(useWordResultCache.containsKey(l.word()))
    // return useWordResultCache.get(l.word());

    if ((commonEngWords != null && (commonEngWords.contains(l.lemma()) || commonEngWords.contains(l.word()))) || (ignoreWordRegex != null && ignoreWordRegex.matcher(l.lemma()).matches())){
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


