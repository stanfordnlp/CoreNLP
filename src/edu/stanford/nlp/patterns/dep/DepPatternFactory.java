package edu.stanford.nlp.patterns.dep;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.patterns.CandidatePhrase;
import edu.stanford.nlp.patterns.DataInstance;
import edu.stanford.nlp.patterns.PatternFactory;
import edu.stanford.nlp.patterns.PatternsAnnotations;
import edu.stanford.nlp.patterns.surface.Token;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.Pair;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by Sonal Gupta on 10/31/14.
 */
public class DepPatternFactory extends PatternFactory{

  @ArgumentParser.Option(name="ignoreRels")
  static String ignoreRels = "";

  @ArgumentParser.Option(name="upDepth")
  static int upDepth = 2;

  @ArgumentParser.Option(name="allowedTagsForTrigger")
  static String allowedTagsForTrigger = ".*";


  static Set<Pattern> allowedTagPatternForTrigger = new HashSet<>();
  static Set<GrammaticalRelation> ignoreRelsSet = new HashSet<>();

  static public void setUp(Properties props){
    ArgumentParser.fillOptions(DepPatternFactory.class, props);
    ArgumentParser.fillOptions(PatternFactory.class, props);
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
    SemanticGraph graph = ((DataInstanceDep)sent).getGraph();
    Map<Integer, Set<DepPattern>> pats4Sent = new HashMap<>();
    if (graph == null || graph.isEmpty()){
      System.out.println("graph is empty or null!");
      return null;
    }

    Set<IndexedWord> allNodes;
    try {
      allNodes = graph.getLeafVertices();
    } catch (IllegalArgumentException i) {
      return null;
    }

    for (IndexedWord w : allNodes) {
      //because index starts at 1!!!!
      pats4Sent.put(w.index() -1,  getContext(w, graph, commonWords, sent));
    }
    return pats4Sent;
  }

  static public DepPattern patternToDepPattern(Pair<IndexedWord, GrammaticalRelation> p, DataInstance sent) {

    Token token = new Token(PatternFactory.PatternType.DEP);
    CoreLabel backingLabel = sent.getTokens().get(p.first().index() -1);
    assert backingLabel.containsKey(PatternsAnnotations.ProcessedTextAnnotation.class) : "the keyset are " + backingLabel.toString(CoreLabel.OutputFormat.ALL);
    token.addORRestriction(PatternsAnnotations.ProcessedTextAnnotation.class, backingLabel.get(PatternsAnnotations.ProcessedTextAnnotation.class));
    return new DepPattern(token, p.second());

  }

  private static boolean ifIgnoreRel(GrammaticalRelation rel) {
    if(ignoreRelsSet.contains(rel))
      return true;
    else
      return false;
  }

  static Set<DepPattern> getContext(IndexedWord w, SemanticGraph graph, Set<CandidatePhrase> stopWords, DataInstance sent){
    Set<DepPattern> patterns = new HashSet<>();
    IndexedWord node = w;
    int depth = 1;
    while (depth <= upDepth) {
      IndexedWord parent = graph.getParent(node);
      if (parent == null)
        break;
      GrammaticalRelation rel = graph.reln(parent, node);
      for (Pattern tagPattern : allowedTagPatternForTrigger) {
        if (tagPattern.matcher(parent.tag()).matches()) {
          if (!ifIgnoreRel(rel) && !stopWords.contains(CandidatePhrase.createOrGet(parent.word())) && parent.word().length() > 1) {
            Pair<IndexedWord, GrammaticalRelation> pattern = new Pair<>(parent, rel);
            DepPattern patterndep = patternToDepPattern(pattern, sent);
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
    try{
      IndexedWord w = graph.getNodeByIndex(i+1);
      return getContext(w, graph, stopWords, sent);}catch(IllegalArgumentException e){
      return Collections.emptySet();
    }
  }
}
