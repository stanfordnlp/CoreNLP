package edu.stanford.nlp.patterns.surface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Execution;
import edu.stanford.nlp.util.Index;
import edu.stanford.nlp.util.concurrent.ConcurrentHashIndex;

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

  public InvertedIndexByTokens(Properties props, Set<String> stopWords, Set<String> specialWords, ConstantsAndVariables constVars) {

    super(props, stopWords, specialWords, constVars);
    Execution.fillOptions(this, props);
    index = new HashMap<String, Set<String>>();
  }


  @Override
  public void add(Map<String, List<CoreLabel>> sents) {
    //TODO: take care of special words and generalizations

//    if(filenamePrefix != null)
//      filename = filenamePrefix+ (filenamePrefix.endsWith("/")?"":"/")+filename;
//
    for (Map.Entry<String, List<CoreLabel>> sEn : sents.entrySet()) {
      numAllSentences ++;
      for (CoreLabel l : sEn.getValue()) {

        //TODO: finish this
        Map<String, Class> m = constVars.getGeneralizeClasses();

        String w = l.word();
        if (useLemmaContextTokens)
          w = l.lemma();

        if (matchLowerCaseContext)
          w = w.toLowerCase();

        w = w.replaceAll("/", "\\\\/");
        add(w, sEn.getKey());
        for(Map.Entry<String, Class> gn: m.entrySet()){
          Object b  = l.get(gn.getValue());
          if(b != null && !b.toString().equals(constVars.backgroundSymbol)){
            String key = gn.getValue().toString()+":"+b.toString();
            add(key, sEn.getKey());
          }
        }

      }
    }
    System.out.println("done adding. Size is " + size() + "  and number of words in inv index is " + index.size());
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

  public Set<String> getFileSentIds(Set<String> words) {
    Set<String> sentids = new HashSet<String>();
    for (String w : words) {

      if (matchLowerCaseContext)
        w = w.toLowerCase();

      Set<String> st = index.get(w);
      if (st == null)
        throw new RuntimeException("How come the index does not have sentences for " + w);
      sentids.addAll(st);
    }
    return sentids;
  }

  //returns for each pattern, list of sentence ids
  public Map<Integer, Set<String>> getFileSentIdsFromPats(Collection<Integer> pats, Index<SurfacePattern> index) {
    Map<Integer, Set<String>> sents = new HashMap<Integer, Set<String>>();
    for(Integer pat: pats)
      sents.put(pat, getFileSentIds(getRelevantWords(index.get(pat))));
    return sents;
  }

  //The last variable is not really used!
  public static InvertedIndexByTokens createIndex(Map<String, List<CoreLabel>> sentences, Properties props, Set<String> stopWords, Set<String> specialWords, String dir, ConstantsAndVariables constVars) {
    InvertedIndexByTokens inv = new InvertedIndexByTokens(props, stopWords, specialWords, constVars);

    if(sentences != null && sentences.size() > 0)
      inv.add(sentences);
    System.out.println("Created index with size " + inv.size());
    return inv;
  }

  @Override
  public Map<Integer, Map<String, List<CoreLabel>>> queryIndex(Collection<Integer> patterns, ConcurrentHashIndex<SurfacePattern> patternIndex) {
    Map<Integer, Set<String>> sentSentids = getFileSentIdsFromPats(patterns, patternIndex);
    Map<Integer, Map<String, List<CoreLabel>>> sents = new HashMap<Integer, Map<String, List<CoreLabel>>>();
    for(Map.Entry<Integer, Set<String>> en: sentSentids.entrySet()){
      sents.put(en.getKey(), getSentences(en.getValue()));
    }
    return sents;
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
