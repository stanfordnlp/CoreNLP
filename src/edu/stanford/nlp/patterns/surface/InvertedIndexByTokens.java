package edu.stanford.nlp.patterns.surface;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.util.Index;

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
public class InvertedIndexByTokens implements Serializable{

  private static final long serialVersionUID = 1L;

  Map<String, Hashtable<String, Set<String>>> index;
  boolean convertToLowercase;
  // boolean filebacked;
  Set<String> stopWords, specialWords;
  // static int numfilesindiskbacked = 10000;
  int numAllEntries = 0;
  boolean batchProcessSents = false;
  //String filenamePrefix = null;
  
  public InvertedIndexByTokens(boolean lc, Set<String> stopWords, Set<String> specialWords, boolean batchProcessSents, String dirName) {
    // if (filebacked)
    // index = new FileBackedCache<StringwithConsistentHashCode,
    // Hashtable<String, Set<String>>>(invertedIndexDir, numfilesindiskbacked);
    // else
    // memory mapped
    index = new HashMap<String, Hashtable<String, Set<String>>>();
    this.convertToLowercase = lc;
    this.batchProcessSents = batchProcessSents;
    // this.filebacked = filebacked;
    this.stopWords = stopWords;
    if (this.stopWords == null)
      this.stopWords = new HashSet<String>();
    this.specialWords = specialWords;
    //this.filenamePrefix = dirName;
  }

  public InvertedIndexByTokens(Map<String, Hashtable<String, Set<String>>> index, boolean lc, Set<String> stopWords,
      Set<String> specialWords, boolean batchProcessSents, String dirName) {
    this.index = index;
    this.convertToLowercase = lc;
    this.batchProcessSents = batchProcessSents;
    this.stopWords = stopWords;
    if (this.stopWords == null)
      this.stopWords = new HashSet<String>();
    this.specialWords = specialWords;
   // this.filenamePrefix = dirName;
  }

  void add(Map<String, List<CoreLabel>> sents, String filename, boolean indexLemma) {
    
//    if(filenamePrefix != null)
//      filename = filenamePrefix+ (filenamePrefix.endsWith("/")?"":"/")+filename;
//
    for (Map.Entry<String, List<CoreLabel>> sEn : sents.entrySet()) {
      for (CoreLabel l : sEn.getValue()) {
        String w = l.word();
        if (indexLemma)
          w = l.lemma();

        if (convertToLowercase)
          w = w.toLowerCase();

        w = w.replaceAll("/", "\\\\/");

        Hashtable<String, Set<String>> t = index.get(w);
        if (t == null)
          t = new Hashtable<String, Set<String>>();

        Set<String> sentids = t.get(filename);
        if (sentids == null) {
          sentids = new HashSet<String>();
        }
        numAllEntries = numAllEntries - sentids.size();
        sentids.add(sEn.getKey());
        t.put(filename, sentids);
        numAllEntries = numAllEntries + sentids.size();
        index.put(w, t);
      }
    }

  }

  public Map<String, Set<String>> getFileSentIds(String word) {
    return index.get(word);
  }

  public Map<String, Set<String>> getFileSentIds(Set<String> words) {
    Hashtable<String, Set<String>> sentids = new Hashtable<String, Set<String>>();
    for (String w : words) {
      Hashtable<String, Set<String>> st = index.get(w);
      if (st == null)
        throw new RuntimeException("How come the index does not have sentences for " + w);
      for (Map.Entry<String, Set<String>> en : st.entrySet()) {
        if (!sentids.containsKey(en.getKey())) {
          sentids.put(en.getKey(), new HashSet<String>());
        }

        sentids.get(en.getKey()).addAll(en.getValue());
      }
    }

    return sentids;
  }

  public Map<String, Set<String>> getFileSentIdsFromPats(Set<Integer> pats, Index<SurfacePattern> index) {
    Set<String> relevantWords = new HashSet<String>();
    for (Integer pindex : pats) {
      SurfacePattern p = index.get(pindex);
      Set<String> relwordsThisPat = new HashSet<String>();
      String[] next = p.getSimplerTokensNext();
      if (next != null)
        for (String s : next) {
          s = s.trim();
          if (convertToLowercase)
            s = s.toLowerCase();
          if (!s.isEmpty())
            relwordsThisPat.add(s);
        }
      String[] prev = p.getSimplerTokensPrev();
      if (prev != null)
        for (String s : prev) {
          s = s.trim();
          if (convertToLowercase)
            s = s.toLowerCase();
          if (!s.isEmpty())
            relwordsThisPat.add(s);
        }
      boolean nonStopW = false;
      for (String w : relwordsThisPat) {
        if (!stopWords.contains(w) && !specialWords.contains(w)) {
          relevantWords.add(w);
          nonStopW = true;
        }
      }
      // If the pat contains just the stop words, add all the stop words!
      if (!nonStopW)
        relevantWords.addAll(relwordsThisPat);

    }
    relevantWords.removeAll(specialWords);
    return getFileSentIds(relevantWords);
  }

  public Set<String> getSpecialWordsList() {
    return this.specialWords;
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

  public int size() {
    return index.size();
  }
  
  public boolean isBatchProcessed(){
    return this.batchProcessSents;
  }

  public int numAllEntries() {
    return this.numAllEntries;
  }

  public Set<String> getKeySet() {
    return index.keySet();
  }
}
