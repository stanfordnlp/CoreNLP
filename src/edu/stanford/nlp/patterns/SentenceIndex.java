package edu.stanford.nlp.patterns;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.ArgumentParser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

/**
 * Created by sonalg on 10/15/14.
 */
public abstract class SentenceIndex<E extends Pattern> {

  Set<String> stopWords;

  int numAllSentences = 0;

  Function<CoreLabel, Map<String, String>>  transformCoreLabeltoString;


  public SentenceIndex(Set<String> stopWords, Function<CoreLabel, Map<String, String>>  transformCoreLabeltoString) {
    this.stopWords = stopWords;
    this.transformCoreLabeltoString = transformCoreLabeltoString;
  }

  public int size() {
    return this.numAllSentences;
  }


  /**
   * addProcessedText is true when inserting sentences for the first time
   * @param sents
   * @param addProcessedText
   */
  public abstract void add(Map<String, DataInstance> sents, boolean addProcessedText);

//  protected CollectionValuedMap<String, String> getRelevantWords(Set<Integer> pats, Index<E> EIndex){
//    CollectionValuedMap<String, String> relwords = new CollectionValuedMap<String, String>();
//    for(Integer p : pats)
//    relwords.addAll(getRelevantWords(EIndex.get(p)));
//    return relwords;
//  }

//  protected CollectionValuedMap<String, String> getRelevantWords(E pat){
//    return pat.getRelevantWords();
//  }

  /*
  returns className->list_of_relevant_words in relWords
   */


//  protected Set<String> getRelevantWords(E pat){
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
    try {
      ArgumentParser.fillOptions(SentenceIndex.class, props);
      Method m = indexClass.getMethod("createIndex", Map.class, Properties.class, Set.class, String.class, Function.class);
      SentenceIndex index = (SentenceIndex) m.invoke(null, new Object[]{sents, props, stopWords, indexDirectory, transformCoreLabeltoString});
      return index;
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }


  public abstract Map<E, Set<String>> queryIndex(Collection<E> Es);//,  EIndex EIndex);

  public void setUp(Properties props) {
    ArgumentParser.fillOptions(this, props);
  }

  protected abstract void add(List<CoreLabel> value, String sentId, boolean addProcessedText);

  public abstract void finishUpdating();

  public abstract void update(List<CoreLabel> value, String key);

  public abstract void saveIndex(String dir);

  public static SentenceIndex loadIndex(Class<? extends SentenceIndex> indexClass, Properties props, Set<String> stopWords, String indexDirectory, Function<CoreLabel, Map<String, String>> transformCoreLabeltoString){
    try {
      ArgumentParser.fillOptions(SentenceIndex.class, props);
      Method m = indexClass.getMethod("loadIndex", Properties.class, Set.class, String.class, Function.class);
      SentenceIndex index = (SentenceIndex) m.invoke(null, new Object[]{props, stopWords, indexDirectory, transformCoreLabeltoString});
      return index;
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
