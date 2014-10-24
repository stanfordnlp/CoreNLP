package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

/**
 * Created by sonalg on 10/15/14.
 */
public abstract class SentenceIndex {

  Set<String> stopWords;

  //TODO: implement this
  int numAllSentences = 0;

  @Option(name="batchProcessSents")
  //Only for in memory non-lucene index
  boolean batchProcessSents = false;

//  @Option(name="matchLowerCaseContext")
//  boolean matchLowerCaseContext = false;
//
//  //TODO: implement this
//  @Option(name="useLemmaContextTokens")
//  boolean useLemmaContextTokens = false;
Function<CoreLabel, Map<String, String>>  transformCoreLabeltoString;


  public SentenceIndex(Properties props, Set<String> stopWords, Function<CoreLabel, Map<String, String>>  transformCoreLabeltoString) {
    this.stopWords = stopWords;
    this.transformCoreLabeltoString = transformCoreLabeltoString;
  }


  public boolean isBatchProcessed(){
    return this.batchProcessSents;
  }

  public int size() {
    return this.numAllSentences;
  }


  /**
   * addProcessedText is true when inserting sentences for the first time
   * @param sents
   * @param addProcessedText
   */
  public abstract void add(Map<String, List<CoreLabel>> sents, boolean addProcessedText);

//  protected CollectionValuedMap<String, String> getRelevantWords(Set<Integer> pats, Index<SurfacePattern> patternIndex){
//    CollectionValuedMap<String, String> relwords = new CollectionValuedMap<String, String>();
//    for(Integer p : pats)
//    relwords.addAll(getRelevantWords(patternIndex.get(p)));
//    return relwords;
//  }

  protected CollectionValuedMap<String, String> getRelevantWords(SurfacePattern pat){
    CollectionValuedMap<String, String> relwordsThisPat = new CollectionValuedMap<String, String>();
    Token[] next = pat.getNextContext();
    getRelevantWords(next, relwordsThisPat);
    Token[] prev = pat.getPrevContext();
    getRelevantWords(prev, relwordsThisPat);
    return relwordsThisPat;
  }

  /*
  returns className->list_of_relevant_words in relWords
   */
  private void getRelevantWords(Token[] t, CollectionValuedMap<String, String> relWords){
    if (t != null)
      for (Token s : t) {
        Map<String, String> str = s.classORRestrictionsAsString();
        if (str != null){
          relWords.addAll(str);
          }
      }
  }

//  protected Set<String> getRelevantWords(SurfacePattern pat){
//
//      Set<String> relwordsThisPat = new HashSet<String>();
//      String[] next = pat.getSimplerTokensNext();
//      if (next != null)
//        for (String s : next) {
//          s = s.trim();
//          if (matchLowerCaseContext)
//            s = s.toLowerCase();
//          if (!s.isEmpty() & !stopWords.contains(s) && !specialWords.contains(s))
//            relwordsThisPat.add(s);
//        }
//      String[] prev = pat.getSimplerTokensPrev();
//      if (prev != null)
//        for (String s : prev) {
//          s = s.trim();
//          if (matchLowerCaseContext)
//            s = s.toLowerCase();
//          if (!s.isEmpty() & !stopWords.contains(s) && !specialWords.contains(s))
//            relwordsThisPat.add(s);
//        }
//
//    return relwordsThisPat;
//  }

  //TODO: what if someone calls with SentenceIndex.class?
  public static SentenceIndex createIndex(Class<? extends SentenceIndex> indexClass, Map<String, List<CoreLabel>> sents, Properties props, Set<String> stopWords,
                                          String indexDirectory, Function<CoreLabel, Map<String, String>> transformCoreLabeltoString)  {
    try{
      Execution.fillOptions(SentenceIndex.class, props);
      Method m = indexClass.getMethod("createIndex", Map.class, Properties.class, Set.class, String.class, Function.class);
      SentenceIndex index = (SentenceIndex) m.invoke(null, new Object[]{sents, props, stopWords, indexDirectory, transformCoreLabeltoString});
      return index;
    }catch(NoSuchMethodException e){
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }


  public abstract Map<Integer, Set<String>> queryIndex(Collection<Integer> patterns, ConcurrentHashIndex<SurfacePattern> patternIndex);

  public void setUp(Properties props) {
    Execution.fillOptions(this, props);
  }

  protected abstract void add(List<CoreLabel> value, String sentId, boolean addProcessedText);

  public abstract void finishUpdating();

  public abstract void update(List<CoreLabel> value, String key);
}
