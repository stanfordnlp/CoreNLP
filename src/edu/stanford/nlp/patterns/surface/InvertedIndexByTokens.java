package edu.stanford.nlp.patterns.surface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CollectionValuedMap;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;
import edu.stanford.nlp.util.logging.Redwood;

/**
 * Creates an inverted index of (word or lemma) => {file1 => {sentid1,
 * sentid2,.. }, file2 => {sentid1, sentid2, ...}}.
 *
 * (Commented out FileBackedCache because it currrently doesnt support changing
 * the values)
 *
 * @author Sonal Gupta (sonalg@stanford.edu)
 *
 */
public class InvertedIndexByTokens extends SentenceIndex implements Serializable{

  private static final long serialVersionUID = 1L;

  Map<String, Set<String>> index;

  public InvertedIndexByTokens(Properties props, Set<String> stopWords, Set<String> specialWords, Function<CoreLabel, Map<String, String>> transformSentenceToString) {
    super(props, stopWords, specialWords, transformSentenceToString);
    Execution.fillOptions(this, props);
    index = new HashMap<String, Set<String>>();

  }


  @Override
  public void add(Map<String, List<CoreLabel>> sents, boolean addProcessedText) {
//    if(filenamePrefix != null)
//      filename = filenamePrefix+ (filenamePrefix.endsWith("/")?"":"/")+filename;
//
    //TODO: finish this

    //HashSet<Class> classesToIndex =new HashSet<Class>(constVars.getGeneralizeClasses().values());
    //classesToIndex.add(PatternsAnnotations.ProcessedTextAnnotation.class);

    for (Map.Entry<String, List<CoreLabel>> sEn : sents.entrySet()) {
       add(sEn.getValue(), sEn.getKey(), addProcessedText);
    }
    System.out.println("done adding. Size is " + size() + "  and number of words in inv index is " + index.size());
  }

  @Override
  public void add(List<CoreLabel> sent, String sentId, boolean addProcessedText){
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
        String val  =Token.getKeyForClass(PatternsAnnotations.ProcessedTextAnnotation.class) +":"+ l.get(PatternsAnnotations.ProcessedTextAnnotation.class);
        add(val, sentId);
      }

//      for(Class gn: classesToIndex){
//        Object b  = l.get(gn);
//        if(b != null && !b.toString().equals(constVars.backgroundSymbol)){
//          String key = Token.getKeyForClass(gn)+":"+b.toString();
//          add(key, sentId);
//        }
//      }

    }
  }

  void add(String w, String sentid){
    Set<String> sentids = index.get(w);

    if (sentids == null) {
      sentids = new HashSet<String>();
    }

    sentids.add(sentid);

    index.put(w, sentids);
  }
//  public Set<String> getFileSentIds(String word) {
//    return index.get(word);
//  }

  String combineKeyValue(String key, String value){
    return key+":"+value;
  }

  public Set<String> getFileSentIds(CollectionValuedMap<String, String> relevantWords) {
    Set<String> sentids = new HashSet<String>();
    for (Map.Entry<String, Collection<String>> en : relevantWords.entrySet()) {
      for(String en2: en.getValue()){
      String w = combineKeyValue(en.getKey(), en2);
      Set<String> st = index.get(w);
      if (st == null)
        throw new RuntimeException("How come the index does not have sentences for " + w);
      sentids.addAll(st);
    }}
    return sentids;
  }

  //returns for each pattern, list of sentence ids
  public Map<Integer, Set<String>> getFileSentIdsFromPats(Collection<Integer> pats, Index<SurfacePattern> index) {
    Map<Integer, Set<String>> sents = new HashMap<Integer, Set<String>>();
    for(Integer pat: pats){
      Set<String> ids = getFileSentIds(getRelevantWords(index.get(pat)));
      Redwood.log(ConstantsAndVariables.extremedebug, "For pattern with index " + pat + " extracted the following sentences from the index " + ids);
      sents.put(pat, ids);
    }
    return sents;
  }

  //The last variable is not really used!
  public static InvertedIndexByTokens createIndex(Map<String, List<CoreLabel>> sentences, Properties props, Set<String> stopWords, Set<String> specialWords, String dir, Function<CoreLabel, Map<String, String>> transformCoreLabeltoString) {
    InvertedIndexByTokens inv = new InvertedIndexByTokens(props, stopWords, specialWords, transformCoreLabeltoString);

    if(sentences != null && sentences.size() > 0)
      inv.add(sentences, true);
    System.out.println("Created index with size " + inv.size());
    return inv;
  }

  @Override
  public Map<Integer, Set<String>> queryIndex(Collection<Integer> patterns, ConcurrentHashIndex<SurfacePattern> patternIndex) {
    Map<Integer, Set<String>> sentSentids = getFileSentIdsFromPats(patterns, patternIndex);
//    Map<Integer, Map<String, List<CoreLabel>>> sents = new HashMap<Integer, Map<String, List<CoreLabel>>>();
//    for(Map.Entry<Integer, Set<String>> en: sentSentids.entrySet()){
//      sents.put(en.getKey(), getSentences(en.getValue()));
//    }
    return sentSentids;
  }


//  public void saveIndex(String dir) throws IOException {
//    BufferedWriter w = new BufferedWriter(new FileWriter(dir + "/param.txt"));
//    w.write(String.valueOf(convertToLowercase) + "\n");
//    w.write(String.valueOf(this.batchProcessSents) + "\n");
//    w.write(this.filenamePrefix+"\n");
//    w.close();
//    IOUtils.writeObjectToFile(this.stopWords, dir + "/stopwords.ser");
//    IOUtils.writeObjectToFile(this.specialWords, dir + "/specialwords.ser");
//    // if (!filebacked)
//    IOUtils.writeObjectToFile(index, dir + "/map.ser");
//
//  }
//
//  public static InvertedIndexByTokens loadIndex(String dir) {
//    try {
//      List<String> lines = IOUtils.linesFromFile(dir + "/param.txt");
//      boolean lc = Boolean.parseBoolean(lines.get(0));
//      boolean batchProcessSents = Boolean.parseBoolean(lines.get(1));
//      String filenameprefix = lines.get(2);
//
//      if(filenameprefix.equals("null"))
//        filenameprefix = null;
//
//      Set<String> stopwords = IOUtils.readObjectFromFile(dir + "/stopwords.ser");
//      Set<String> specialwords = IOUtils.readObjectFromFile(dir + "/specialwords.ser");
//      Map<String, Hashtable<String, Set<String>>> index = null;
//      // if (!filebacked)
//      index = IOUtils.readObjectFromFile(dir + "/map.ser");
//      // else
//      // index = new FileBackedCache<StringwithConsistentHashCode,
//      // Hashtable<String, Set<String>>>(dir + "/cache", numfilesindiskbacked);
//      return new InvertedIndexByTokens(index, lc, stopwords, specialwords, batchProcessSents, filenameprefix);
//    } catch (Exception e) {
//      throw new RuntimeException("Cannot load the inverted index. " + e);
//    }
//  }


}
