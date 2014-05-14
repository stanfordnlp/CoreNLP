package edu.stanford.nlp.patterns.surface;

import java.util.List;
import java.util.Map;

import edu.stanford.nlp.io.IOUtils;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.ClassicCounter;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.CollectionUtils;
import edu.stanford.nlp.util.Execution.Option;
import edu.stanford.nlp.util.logging.Redwood;

public class Data {
  public static double ratioDomainNgramFreqWithDataFreq = 1;
  static public Counter<String> rawFreq = null;
  public static Map<String, List<CoreLabel>> sents = null;
  public static Counter<String> processedDataFreq = null;
  public static Counter<String> domainNGramRawFreq = new ClassicCounter<String>();;

  public static double ratioGoogleNgramFreqWithDataFreq = 1;

  @Option(name = "googleNGramsFile")
  public static String googleNGramsFile = null;

  @Option(name = "domainNGramsFile")
  public static String domainNGramsFile = null;

  public static Counter<String> googleNGram = new ClassicCounter<String>();

  public static void computeRawFreqIfNull(int numWordsCompound) {
    if (Data.rawFreq == null) {
      Data.rawFreq = new ClassicCounter<String>();
      for (List<CoreLabel> l : sents.values()) {
        List<List<CoreLabel>> ngrams = CollectionUtils.getNGrams(l, 1, numWordsCompound);
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
        getRatioGoogleNgramFreqWithDataFreq();
      if (domainNGramRawFreq != null && domainNGramRawFreq.size() > 0)
        ratioDomainNgramFreqWithDataFreq = domainNGramRawFreq.totalCount() / Data.rawFreq.totalCount();
    }
  }

  public static double getRatioGoogleNgramFreqWithDataFreq() {
    ratioGoogleNgramFreqWithDataFreq = googleNGram.totalCount() / Data.rawFreq.totalCount();
    Redwood.log(Redwood.FORCE, "Data", "ratioGoogleNgramFreqWithDataFreq is " + ratioGoogleNgramFreqWithDataFreq);
    return ratioGoogleNgramFreqWithDataFreq;

  }

  public static void loadGoogleNGrams() {
    if (googleNGram == null || googleNGram.size() == 0) {
      for (String line : IOUtils.readLines(googleNGramsFile)) {
        String[] t = line.split("\t");
        googleNGram.setCount(t[0], Double.valueOf(t[1]));
      }
      Redwood.log(Redwood.FORCE, "Data", "loading freq from google ngram file " + googleNGramsFile);
    }
  }

  public static void loadDomainNGrams() {
    assert(domainNGramsFile != null);
    if (domainNGramRawFreq == null || domainNGramRawFreq.size() == 0) {
      for (String line : IOUtils.readLines(domainNGramsFile)) {
        String[] t = line.split("\t");
        domainNGramRawFreq.setCount(t[0], Double.valueOf(t[1]));
      }
      Redwood.log(Redwood.FORCE, "Data", "loading freq from domain ngram file " + domainNGramsFile);
    }
  }
}
