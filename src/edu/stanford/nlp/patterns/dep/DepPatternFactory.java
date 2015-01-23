package edu.stanford.nlp.patterns.dep;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.patterns.CandidatePhrase;
import edu.stanford.nlp.patterns.DataInstance;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.patterns.PatternsAnnotations;
import edu.stanford.nlp.patterns.surface.Token;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.semgrex.SemgrexPattern;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Pair;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by Sonal Gupta on 10/31/14.
 */
public class DepPatternFactory extends PatternFactory{

  @Execution.Option(name="ignoreRels")
  static String ignoreRels = "";

  @Execution.Option(name="upDepth")
  static int upDepth = 2;

  @Execution.Option(name="allowedTagsForTrigger")
  static String allowedTagsForTrigger = ".*";


  static Set<Pattern> allowedTagPatternForTrigger = new HashSet<Pattern>();
  static Set<GrammaticalRelation> ignoreRelsSet = new HashSet<GrammaticalRelation>();

  static public void setUp(Properties props){
    Execution.fillOptions(DepPatternFactory.class, props);
    Execution.fillOptions(PatternFactory.class, props);
    for(String s: ignoreRels.split("[,;]")){
      ignoreRelsSet.add(GrammaticalRelation.valueOf(s));
    }
    for(String s: allowedTagsForTrigger.split("[,;]")){
      allowedTagPatternForTrigger.add(Pattern.compile(s));
    }

  }

  public static Map<Integer, Set<DepPattern>> getPatternsAroundTokens(DataInstance sent, Set<CandidatePhrase> stopWords) {

    return getPatternsForAllPhrases(sent, stopWords);
  }

  static Map<Integer, Set<DepPattern>> getPatternsForAllPhrases(DataInstance sent, Set<CandidatePhrase> commonWords)
  {

   // Map<String, Map<Integer, Set>> allPossiblePatterns = new HashMap<String, Map<Integer, Set>>();
    //Map<String, Map<Integer, Map<String, Counter<String>>>> phrasesForAllPossiblePatterns = new HashMap<String, Map<Integer, Map<String, Counter<String>>>>();




      //Map<Integer, Map<String, List<String>>> possiblePatternsForAbs = new TreeMap<Integer, Map<String, List<String>>>();
      //Map<Integer, Map<String, Counter<String>>> phrasesForPossiblePatternsForAbs = new TreeMap<Integer, Map<String, Counter<String>>>();


      SemanticGraph graph = ((DataInstanceDep)sent).getGraph();


      Map<Integer, Set<DepPattern>> pats4Sent = new HashMap<Integer, Set<DepPattern>>();
        Map<String, List<String>> mappingsForSent = new HashMap<String, List<String>>();
        Map<String, Counter<String>> phrasesForPatternForSent = new HashMap<String, Counter<String>>();

        //List<String> token = sentIdEn.getValue();
        //SemanticGraph g = depsForAbs.get(sentId);
        if (graph == null || graph.isEmpty()){
            System.out.println("graph is empty or null!");
            return null;
        }
        ArrayList<IndexedWord> seenNodes = new ArrayList<IndexedWord>();

        Set<IndexedWord> allNodes = null;
        try {
          allNodes = graph.descendants(graph.getFirstRoot());
        } catch (IllegalArgumentException i) {
          return null;
        }
        for (IndexedWord w : allNodes) {
          //List<IntPair> phraseIndices4ThisSent = new ArrayList<IntPair>();
          //List<String> phrases4ThisSent = new ArrayList<String>();
          //List<ExtractedPhrase> extractedPhrases4ThisSent = new ArrayList<ExtractedPhrase>();
          //etm.printSubGraph(g, w, false, null, token, null, "", phrases4ThisSent, phraseIndices4ThisSent, seenNodes, new ArrayList<IndexedWord>(), findSubTrees, extractedPhrases4ThisSent, null);
          // if (phrases4ThisSent.size() > 1){
          // System.err.println("phrases are " +
          // StringUtils.join(phrases4ThisSent, ";"));
          // }
//          if (phrases4ThisSent.size() == 0) {
//            if (DEBUG >= 1)
//              System.err.println("no phrase");
//            continue;
//          }

          //for (String phrase : phrases4ThisSent) {

          //  phrase = phrase.trim().toLowerCase();
            //List<String> patterns = mappingsForSent.get(phrase);
            //if (patterns == null)
            //  patterns = new ArrayList<String>();


          //because index starts at 1!!!!
          pats4Sent.put(w.index() -1,  getContext(w, graph, commonWords));

            //mappingsForSent.put(phrase, patterns);
          }

        //possiblePatternsForAbs.put(sentId, mappingsForSent);
        //phrasesForPossiblePatternsForAbs.put(sentId, phrasesForPatternForSent);

      //allPossiblePatterns.put(articleId, possiblePatternsForAbs);
    //  phrasesForAllPossiblePatterns.put(articleId, phrasesForPossiblePatternsForAbs);

    return pats4Sent;
  }

  static public DepPattern patternToDepPattern(Pair<IndexedWord, GrammaticalRelation> p) {

    Token token = new Token(PatternFactory.PatternType.DEP);
    token.addORRestriction(PatternsAnnotations.ProcessedTextAnnotation.class, p.first().backingLabel().get(PatternsAnnotations.ProcessedTextAnnotation.class));

    return new DepPattern(token, p.second());

  }

  private static boolean ifIgnoreRel(GrammaticalRelation rel) {
    if(ignoreRelsSet.contains(rel))
      return true;
    else
      return false;
  }

  static Set<DepPattern> getContext(IndexedWord w, SemanticGraph graph, Set<CandidatePhrase> stopWords){
    Set<DepPattern> patterns = new HashSet<DepPattern>();
    IndexedWord node = w;
    int depth = 1;
    while (depth <= upDepth) {
      IndexedWord parent = graph.getParent(node);
      if (parent == null)
        break;
      GrammaticalRelation rel = graph.reln(parent, node);
      for (Pattern tagPattern : allowedTagPatternForTrigger) {
        if (tagPattern.matcher(parent.tag()).matches()) {
          if (!ifIgnoreRel(rel) && !stopWords.contains(new CandidatePhrase(parent.word())) && parent.word().length() > 1) {
            Pair<IndexedWord, GrammaticalRelation> pattern = new Pair<IndexedWord, GrammaticalRelation>(parent, rel);
            DepPattern patterndep = patternToDepPattern(pattern);
            if (depth <= upDepth){
              patterns.add(patterndep);
            }

//                    if (depth <= maxDepth) {
//                      Counter<String> phrasesForPattern = phrasesForPatternForSent.get(patternStr);
//                      if (phrasesForPattern == null)
//                        phrasesForPattern = new ClassicCounter<String>();
//                      phrasesForPattern.incrementCount(phrase);
//                      phrasesForPatternForSent.put(patternStr, phrasesForPattern);
//                    }
//                    if (DEBUG >= 1)
//                      System.out.println("for phrase " + phrase + " pattern is " + patternStr);
          }
        }
      }
      node = parent;
      depth++;
    }
    return patterns;
  }

  public static Set getContext(DataInstance sent, int i, Set<CandidatePhrase> stopWords) {
    SemanticGraph graph = ((DataInstanceDep)sent).getGraph();
    //nodes are indexed from 1 -- so wrong!!
    IndexedWord w = graph.getNodeByIndex(i+1);
    return getContext(w, graph, stopWords);
  }
}
