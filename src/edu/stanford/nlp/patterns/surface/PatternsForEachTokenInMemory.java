package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.patterns.Pattern;
import edu.stanford.nlp.util.Execution;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by sonalg on 10/22/14.
 */
public class PatternsForEachTokenInMemory<E extends Pattern> extends PatternsForEachToken<E> {
  public static ConcurrentHashMap<String, Map<Integer, Set<? extends Pattern>>> patternsForEachToken = null;

  public PatternsForEachTokenInMemory(Properties props, Map<String, Map<Integer, Set<E>>> pats) {
    Execution.fillOptions(this, props);


    //TODO: make this atomic
    if(patternsForEachToken == null)
      patternsForEachToken = new ConcurrentHashMap<String, Map<Integer, Set<? extends Pattern>>>();

    if (pats != null)
      addPatterns(pats);
  }

  public PatternsForEachTokenInMemory(Properties props)  {
    this(props, null);
  }

  @Override
  public void addPatterns(String sentId, Map<Integer, Set<E>> patterns) {
    if (!patternsForEachToken.containsKey(sentId))
      patternsForEachToken.put(sentId, new ConcurrentHashMap<Integer, Set<? extends Pattern>>());
    patternsForEachToken.get(sentId).putAll(patterns);

  }

  @Override
  public void addPatterns(Map<String, Map<Integer, Set<E>>> pats) {
    for (Map.Entry<String, Map<Integer, Set<E>>> en : pats.entrySet()) {
      addPatterns(en.getKey(), en.getValue());
    }
  }

  @Override
  public Map<Integer, Set<E>> getPatternsForAllTokens(String sentId) {
    return (Map<Integer, Set<E>>)(patternsForEachToken.containsKey(sentId) ? patternsForEachToken.get(sentId) : Collections.emptyMap());
  }

  @Override
  public void setupSearch() {
    //nothing to do
  }

//  @Override
//  public ConcurrentHashIndex<SurfacePattern> readPatternIndex(String dir) throws IOException, ClassNotFoundException {
//    return IOUtils.readObjectFromFile(dir+"/patternshashindex.ser");
//  }
//
//  @Override
//  public void savePatternIndex(ConcurrentHashIndex<SurfacePattern> index, String dir) throws IOException {
//    if(dir != null){
//    writePatternsIfInMemory(dir+"/allpatterns.ser");
//    IOUtils.writeObjectToFile(index, dir+"/patternshashindex.ser");
//    }
//  }

  @Override
  public Map<String, Map<Integer, Set<E>>> getPatternsForAllTokens(Collection<String> sampledSentIds) {
    Map<String, Map<Integer, Set<E>>> pats = new HashMap<String, Map<Integer, Set<E>>>();
    for(String s: sampledSentIds){
      pats.put(s, getPatternsForAllTokens(s));
    }
    return pats;
  }

  @Override
  public void close() {
    //nothing to do
  }

  @Override
  public void load(String allPatternsDir) {
    try {
      addPatterns(IOUtils.readObjectFromFile(allPatternsDir+"/allpatterns.ser"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean save(String dir) {
    try {
      IOUtils.writeObjectToFile(this.patternsForEachToken, dir+"/allpatterns.ser");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return true;
  }

  @Override
  public void createIndexIfUsingDBAndNotExists() {
    //nothing to do
    return;
  }

  public boolean containsSentId(String sentId) {
    return this.patternsForEachToken.containsKey(sentId);
  }

  @Override
  public int size(){
    return this.patternsForEachToken.size();
  };
}