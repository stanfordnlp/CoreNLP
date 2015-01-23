package edu.stanford.nlp.patterns;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.*;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.logging.Redwood;

public class Data {
  public static double ratioDomainNgramFreqWithDataFreq = 1;
  static public Counter<String> rawFreq = null;
  public static List<File> sentsFiles = null;

  //when using batch processing, map from sentid to the file that has that sentence
  public static Map<String, File> sentId2File = null;

  public static List<String> fileNamesUsedToComputeRawFreq = new ArrayList<String>();
  public static Map<String, DataInstance> sents = null;
  public static Counter<String> processedDataFreq = null;
  public static Counter<String> domainNGramRawFreq = new ClassicCounter<String>();;

  public static double ratioGoogleNgramFreqWithDataFreq = 1;

  @Option(name = "googleNGramsFile")
  public static String googleNGramsFile = null;

  @Option(name = "domainNGramsFile")
  public static String domainNGramsFile = null;

  public static Counter<String> googleNGram = new ClassicCounter<String>();

  public static Map<String, Map<String, List<Integer>>> matchedTokensForEachPhrase = new ConcurrentHashMap<>();

  public static void computeRawFreqIfNull(Map<String, DataInstance> sents, int numWordsCompound) {
      for (DataInstance l : sents.values()) {
        List<List<CoreLabel>> ngrams = CollectionUtils.getNGrams(l.getTokens(), 1, numWordsCompound);
        for (List<CoreLabel> n : ngrams) {
          String s = "";
          for (CoreLabel c : n) {
            // if (useWord(c, commonEngWords, ignoreWordRegex)) {
            s += " " + c.word();
            // }
          }
          if (!s.trim().isEmpty())
            Data.rawFreq.incrementCount(s.trim());
        }
      }
      if (googleNGram != null && googleNGram.size() > 0)
        setRatioGoogleNgramFreqWithDataFreq();
      if (domainNGramRawFreq != null && domainNGramRawFreq.size() > 0)
        ratioDomainNgramFreqWithDataFreq = domainNGramRawFreq.totalCount() / Data.rawFreq.totalCount();
    
  }

  public static void setRatioGoogleNgramFreqWithDataFreq() {
    ratioGoogleNgramFreqWithDataFreq = googleNGram.totalCount() / Data.rawFreq.totalCount();
    Redwood.log(ConstantsAndVariables.minimaldebug, "Data", "ratioGoogleNgramFreqWithDataFreq is " + ratioGoogleNgramFreqWithDataFreq);
    //return ratioGoogleNgramFreqWithDataFreq;

  }

  public static void loadGoogleNGrams() {
    if (googleNGram == null || googleNGram.size() == 0) {
      for (String line : IOUtils.readLines(googleNGramsFile)) {
        String[] t = line.split("\t");
        googleNGram.setCount(t[0], Double.valueOf(t[1]));
      }
      Redwood.log(ConstantsAndVariables.minimaldebug, "Data", "loading freq from google ngram file " + googleNGramsFile);
    }
  }

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
