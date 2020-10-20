package edu.stanford.nlp.patterns;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.ArgumentParser.Option;
import edu.stanford.nlp.util.logging.Redwood;

public class Data {
  public static double ratioDomainNgramFreqWithDataFreq = 1;
  static public Counter<CandidatePhrase> rawFreq = null;
  public static List<File> sentsFiles = null;

  //when using batch processing, map from sentid to the file that has that sentence
  public static Map<String, File> sentId2File = null;

  public static Map<String, DataInstance> sents = null;
  //save the in-memory sents to this file
  public static String inMemorySaveFileLocation= "";

  public static Counter<CandidatePhrase> processedDataFreq = null;
  public static Counter<String> domainNGramRawFreq = new ClassicCounter<>();;

  public static double ratioGoogleNgramFreqWithDataFreq = 1;

//  @Option(name = "googleNGramsFile")
//  public static String googleNGramsFile = null;

  @Option(name = "domainNGramsFile")
  public static String domainNGramsFile = null;

  static boolean usingGoogleNgram = false;

  //public static Counter<String> googleNGram = new ClassicCounter<String>();

  public static Map<String, Map<String, List<Integer>>> matchedTokensForEachPhrase = new ConcurrentHashMap<>();

  public static void computeRawFreqIfNull(int numWordsCompound, boolean batchProcess) {
    ConstantsAndVariables.DataSentsIterator iter = new ConstantsAndVariables.DataSentsIterator(batchProcess);
    while(iter.hasNext()){
      computeRawFreqIfNull(iter.next().first(), numWordsCompound);
    }

  }
  public static void computeRawFreqIfNull(Map<String, DataInstance> sents, int numWordsCompound) {
    Redwood.log(Redwood.DBG, "Computing raw freq for every 1-" + numWordsCompound + " consecutive words");
    for (DataInstance l : sents.values()) {
        List<List<CoreLabel>> ngrams = CollectionUtils.getNGrams(l.getTokens(), 1, numWordsCompound);
        for (List<CoreLabel> n : ngrams) {
          String s = "";
          for (CoreLabel c : n) {
            // if (useWord(c, commonEngWords, ignoreWordRegex)) {
            s += " " + c.word();
            // }
          }
          s = s.trim();
          if (!s.isEmpty()){
            Data.rawFreq.incrementCount(CandidatePhrase.createOrGet(s));
          }
        }
      }
      //if (googleNGram != null && googleNGram.size() > 0)
    if(usingGoogleNgram)
      setRatioGoogleNgramFreqWithDataFreq();

    if (domainNGramRawFreq != null && domainNGramRawFreq.size() > 0)
        ratioDomainNgramFreqWithDataFreq = domainNGramRawFreq.totalCount() / Data.rawFreq.totalCount();
    
  }

  public static void setRatioGoogleNgramFreqWithDataFreq() {
    ratioGoogleNgramFreqWithDataFreq = GoogleNGramsSQLBacked.getTotalCount(1)/ Data.rawFreq.totalCount();
    Redwood.log(ConstantsAndVariables.minimaldebug, "Data", "ratioGoogleNgramFreqWithDataFreq is " + ratioGoogleNgramFreqWithDataFreq);
    //return ratioGoogleNgramFreqWithDataFreq;

  }

//  public static void loadGoogleNGrams() {
//    if (googleNGram == null || googleNGram.size() == 0) {
//      for (String line : IOUtils.readLines(googleNGramsFile)) {
//        String[] t = line.split("\t");
//        googleNGram.setCount(t[0], Double.valueOf(t[1]));
//      }
//      Redwood.log(ConstantsAndVariables.minimaldebug, "Data", "loading freq from google ngram file " + googleNGramsFile);
//    }
//  }

  public static void loadDomainNGrams() {
    assert(domainNGramsFile != null);
    if (domainNGramRawFreq == null || domainNGramRawFreq.size() == 0) {
      for (String line : IOUtils.readLines(domainNGramsFile)) {
        String[] t = line.split("\t");
        domainNGramRawFreq.setCount(t[0], Double.valueOf(t[1]));
      }
      Redwood.log(ConstantsAndVariables.minimaldebug, "Data", "loading freq from domain ngram file " + domainNGramsFile);
    }
  }
}
