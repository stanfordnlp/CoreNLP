package edu.stanford.nlp.patterns;

import java.io.*;
import java.util.*;
import java.util.function.Function;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.patterns.surface.Token;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.ArgumentParser;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Creates an inverted index of (classkey:value) => {sentid1,sentid2,.. }.
 *
 *
 * @author Sonal Gupta (sonalg@stanford.edu)
 *
 */
public class InvertedIndexByTokens<E extends Pattern> extends SentenceIndex<E> implements Serializable {

  /** A logger for this class */
  private static Redwood.RedwoodChannels log = Redwood.channels(InvertedIndexByTokens.class);

  private static final long serialVersionUID = 1L;

  Map<String, Set<String>> index;

  public InvertedIndexByTokens(Properties props, Set<String> stopWords, Function<CoreLabel, Map<String, String>> transformSentenceToString) {
    super(stopWords, transformSentenceToString);
    ArgumentParser.fillOptions(this, props);
    index = new HashMap<>();
  }

  public InvertedIndexByTokens(Properties props, Set<String> stopWords, Function<CoreLabel, Map<String, String>> transformSentenceToString, Map<String, Set<String>> index) {
    super(stopWords, transformSentenceToString);
    ArgumentParser.fillOptions(this, props);
    this.index = index;
  }




  @Override
  public void add(Map<String,DataInstance> sents, boolean addProcessedText) {
    for (Map.Entry<String, DataInstance> sEn : sents.entrySet()) {
      add(sEn.getValue().getTokens(), sEn.getKey(), addProcessedText);
    }
  }

  @Override
  protected void add(List<CoreLabel> sent, String sentId, boolean addProcessedText){
    numAllSentences ++;
    for (CoreLabel l : sent) {

      //String w = l.word();
//        w = w.replaceAll("/", "\\\\/");
//        add(w, sEn.getKey());
      Map<String, String> addThis = this.transformCoreLabeltoString.apply(l);
      for(Map.Entry<String, String> en: addThis.entrySet()){
        String val = combineKeyValue(en.getKey(),en.getValue());
        add(val, sentId);
      }
      if(addProcessedText){
        String val  = Token.getKeyForClass(PatternsAnnotations.ProcessedTextAnnotation.class) +":"+ l.get(PatternsAnnotations.ProcessedTextAnnotation.class);
        if(!stopWords.contains(val.toLowerCase()))
          add(val, sentId);
      }
    }
  }

  @Override
  public void finishUpdating() {
    //nothing to do right now!
  }

  @Override
  public void update(List<CoreLabel> tokens, String sentid) {
    add(tokens, sentid, false);
  }

  void add(String w, String sentid){
    Set<String> sentids = index.get(w);

    if (sentids == null) {
      sentids = new HashSet<>();
    }

    sentids.add(sentid);

    index.put(w, sentids);
  }

  String combineKeyValue(String key, String value){
    return key+":"+value;
  }

  public Set<String> getFileSentIds(CollectionValuedMap<String, String> relevantWords) {
    Set<String> sentids = null;
    for (Map.Entry<String, Collection<String>> en : relevantWords.entrySet()) {
      for(String en2: en.getValue()){
        if(!stopWords.contains(en2.toLowerCase())){
          String w = combineKeyValue(en.getKey(), en2);
          Set<String> st = index.get(w);
          if (st == null){
            //log.info("\n\nWARNING: INDEX HAS NO SENTENCES FOR " + w);
            return Collections.emptySet();
            //throw new RuntimeException("How come the index does not have sentences for " + w);
          }
          if(sentids == null)
            sentids= st;
          else
            sentids = CollectionUtils.intersection(sentids, st);
        }
      }}
    return sentids;
  }

  //returns for each pattern, list of sentence ids
  public Map<E, Set<String>> getFileSentIdsFromPats(Collection<E> pats) {
    Map<E, Set<String>> sents = new HashMap<>();
    for(E pat: pats){
      Set<String> ids = getFileSentIds(pat.getRelevantWords());
      Redwood.log(ConstantsAndVariables.extremedebug, "For pattern with index " + pat + " extracted the following sentences from the index " + ids);
      sents.put(pat, ids);
    }
    return sents;
  }

  //The last variable is not really used!
  public static InvertedIndexByTokens createIndex(Map<String, List<CoreLabel>> sentences, Properties props, Set<String> stopWords, String dir, Function<CoreLabel, Map<String, String>> transformCoreLabeltoString) {
    InvertedIndexByTokens inv = new InvertedIndexByTokens(props, stopWords, transformCoreLabeltoString);

    if(sentences != null && sentences.size() > 0)
      inv.add(sentences, true);
    System.out.println("Created index with size " + inv.size() + ". Don't worry if it's zero and you are using batch process sents.");
    return inv;
  }

  @Override
  public Map<E, Set<String>> queryIndex(Collection<E> patterns) {
    Map<E, Set<String>> sentSentids = getFileSentIdsFromPats(patterns);
    return sentSentids;
  }

  @Override
  public void saveIndex(String dir){
    try {
      IOUtils.ensureDir(new File(dir));
      IOUtils.writeObjectToFile(index, dir + "/map.ser");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  //called by SentenceIndex.loadIndex
  public static InvertedIndexByTokens loadIndex(Properties props, Set<String> stopwords, String dir,  Function<CoreLabel, Map<String, String>> transformSentenceToString) {
    try {
      Map<String, Set<String>>  index = IOUtils.readObjectFromFile(dir + "/map.ser");
      System.out.println("Loading inverted index from " + dir);
      return new InvertedIndexByTokens(props, stopwords, transformSentenceToString, index);
    } catch (Exception e) {
      throw new RuntimeException("Cannot load the inverted index. " + e);
    }
  }


}
