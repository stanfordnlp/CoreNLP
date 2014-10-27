package edu.stanford.nlp.patterns.surface;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by sonalg on 10/22/14.
 */
public class PatternsForEachTokenInMemory extends PatternsForEachToken {
  public static ConcurrentHashMap<String, Map<Integer, Set<Integer>>> patternsForEachToken = null;

  public PatternsForEachTokenInMemory(Properties props, Map<String, Map<Integer, Set<Integer>>> pats) {
    Execution.fillOptions(this, props);

    if(patternsForEachToken == null)
      patternsForEachToken = new ConcurrentHashMap<String, Map<Integer, Set<Integer>>>();

    if (pats != null)
      addPatterns(pats);
  }

  public PatternsForEachTokenInMemory(Properties props)  {
    this(props, null);
  }

  @Override
  public void addPatterns(String sentId, Map<Integer, Set<Integer>> patterns) {
    if (!patternsForEachToken.containsKey(sentId))
      patternsForEachToken.put(sentId, new ConcurrentHashMap<Integer, Set<Integer>>());
    patternsForEachToken.get(sentId).putAll(patterns);

  }

  @Override
  public void addPatterns(Map<String, Map<Integer, Set<Integer>>> pats) {
    for (Map.Entry<String, Map<Integer, Set<Integer>>> en : pats.entrySet()) {
      addPatterns(en.getKey(), en.getValue());
    }
  }

  @Override
  public Map<Integer, Set<Integer>> getPatternsForAllTokens(String sentId) {
    return patternsForEachToken.containsKey(sentId) ? patternsForEachToken.get(sentId) : Collections.emptyMap();
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
  public Map<String, Map<Integer, Set<Integer>>> getPatternsForAllTokens(Collection<String> sampledSentIds) {
    Map<String, Map<Integer, Set<Integer>>> pats = new HashMap<String, Map<Integer, Set<Integer>>>();
    for(String s: sampledSentIds){
      pats.put(s, getPatternsForAllTokens(s));
    }
    return pats;
  }

  @Override
  public void close() {
    //nothing to do
  }


  public boolean writePatternsIfInMemory(String allPatternsFile) {
    try {
      IOUtils.writeObjectToFile(this.patternsForEachToken, allPatternsFile);
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