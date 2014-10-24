package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Created by sonalg on 10/15/14.
 */
public abstract class SentenceIndex {

  Set<String> stopWords, specialWords;

  //TODO: implement this
  int numAllSentences = 0;

  @Option(name="batchProcessSents")
  //Only for in memory non-lucene index
  boolean batchProcessSents = false;

  @Option(name="matchLowerCaseContext")
  boolean matchLowerCaseContext = false;

  //TODO: implement this
  @Option(name="useLemmaContextTokens")
  boolean useLemmaContextTokens = false;


  public SentenceIndex(Properties props, Set<String> stopWords, Set<String> specialWords) {
    System.out.println("lower case is " + matchLowerCaseContext);
    System.out.println("properties " + props);
    this.stopWords = stopWords;
    this.specialWords = specialWords;
    setUp(props);
  }

  public boolean isBatchProcessed(){
    return this.batchProcessSents;
  }

  public int size() {
    return this.numAllSentences;
  }


  public abstract void add(Map<String, List<CoreLabel>> sents);


  public Set<String> getSpecialWordsList() {
    return specialWords;
  }

  protected Set<String> getRelevantWords(Set<Integer> pats, Index<SurfacePattern> patternIndex){
    Set<String> relwords = new HashSet<String>();
    for(Integer p : pats)
    relwords.addAll(getRelevantWords(patternIndex.get(p)));
    return relwords;
  }

  protected Map<String, List<CoreLabel>> getSentences(Collection<String> sentids) {
    try{
      Map<String, List<CoreLabel>> sents = new HashMap<String, List<CoreLabel>>();
      if(batchProcessSents){
        Set<File> files = new HashSet<File>();
        for(String s: sentids){
          files.add(Data.sentId2File.get(s));
        }
        for(File f: files){

          Map<String, List<CoreLabel>> sentsf = IOUtils.readObjectFromFile(f);

          for(Map.Entry<String, List<CoreLabel>> s: sentsf.entrySet()){
            if(sentids.contains(s.getKey()))
              sents.put(s.getKey(), s.getValue());
          }
        }
      }else{
        for(Map.Entry<String, List<CoreLabel>> s: Data.sents.entrySet()){
          if(sentids.contains(s.getKey()))
            sents.put(s.getKey(), s.getValue());
        }
      }

      return sents;}catch(ClassNotFoundException e){
      throw new RuntimeException(e);
    }catch(IOException e1){
      throw new RuntimeException(e1);

    }
  }

  protected Set<String> getRelevantWords(SurfacePattern pat){

      Set<String> relwordsThisPat = new HashSet<String>();
      String[] next = pat.getSimplerTokensNext();
      if (next != null)
        for (String s : next) {
          s = s.trim();
          if (matchLowerCaseContext)
            s = s.toLowerCase();
          if (!s.isEmpty() & !stopWords.contains(s) && !specialWords.contains(s))
            relwordsThisPat.add(s);
        }
      String[] prev = pat.getSimplerTokensPrev();
      if (prev != null)
        for (String s : prev) {
          s = s.trim();
          if (matchLowerCaseContext)
            s = s.toLowerCase();
          if (!s.isEmpty() & !stopWords.contains(s) && !specialWords.contains(s))
            relwordsThisPat.add(s);
        }

    return relwordsThisPat;
  }

  //TODO: what if someone calls with SentenceIndex.class?
  public static SentenceIndex createIndex(Class<? extends SentenceIndex> indexClass, Map<String, List<CoreLabel>> sents, Properties props, Set<String> stopWords, Set<String> specialWords, String indexDirectory)  {
    try{
      Execution.fillOptions(SentenceIndex.class, props);
      Method m = indexClass.getMethod("createIndex", Map.class, Properties.class, Set.class, Set.class, String.class);
      SentenceIndex index = (SentenceIndex) m.invoke(null, new Object[]{sents, props, stopWords, specialWords, indexDirectory});
      return index;
    }catch(NoSuchMethodException e){
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }


  public abstract Map<Integer, Map<String, List<CoreLabel>>> queryIndex(Collection<Integer> patterns, ConcurrentHashIndex<SurfacePattern> patternIndex);

  public void setUp(Properties props) {
    Execution.fillOptions(this, props);
  }

}
